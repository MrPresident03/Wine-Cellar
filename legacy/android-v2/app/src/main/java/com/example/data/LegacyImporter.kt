package com.example.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies the inventory from version 1 of the app into the new shared cellar.
 *
 * Version 1 kept bottles in a local database on the phone ("wine_cellar_database") and, if you had
 * signed in, a copy in the cloud under cellars/cellar_<email name>. Both are read, merged and
 * de-duplicated. The old database file is never modified or deleted, and a backup copy is made in the
 * app's private folder the first time it's read, so the import can always be run again.
 */
class LegacyImporter(private val context: Context, private val repo: CellarRepository) {

    data class LegacyBottle(
        val winery: String,
        val classification: String?,
        val varietal: String,
        val vintage: String,
        val row: Int,
        val col: Int,
        val price: Double?,
        val isAging: Boolean,
        val photoUri: String?,
        val timestamp: Long,
        /** A photo stored inside the old document itself (base64), if any. */
        val photoBase64: String? = null
    )

    data class Result(
        val imported: Int,
        val alreadyInCellar: Int,
        val photos: Int,
        val stillUploading: Boolean,
        /** One line per old cloud location checked, e.g. "cellar_steven: 42 bottles". */
        val cloudNotes: List<String>
    )


    private val dbFile: File get() = context.getDatabasePath(DB_NAME)

    fun hasLocalData(): Boolean = dbFile.exists()

    /** The cellar name the user set in version 1, if any. */
    fun legacyCellarName(): String? =
        context.getSharedPreferences("wine_cellar_prefs", Context.MODE_PRIVATE)
            .getString("cellar_name", null)
            ?.takeIf { it.isNotBlank() }

    /**
     * Reads every bottle from the version 1 database on this phone, plus a scratch copy of the backup
     * taken before this version first opened it. Call off the main thread.
     */
    fun readLocal(): List<LegacyBottle> {
        val file = dbFile
        if (!file.exists()) return emptyList()
        backupOnce(file)
        val out = mutableListOf<LegacyBottle>()
        out += readRoomDb(file)
        val backupDir = File(context.filesDir, "v1_backup")
        if (File(backupDir, DB_NAME).exists()) {
            val scratch = File(context.cacheDir, "v1_check")
            scratch.deleteRecursively()
            scratch.mkdirs()
            backupDir.listFiles()?.forEach { it.copyTo(File(scratch, it.name), overwrite = true) }
            out += readRoomDb(File(scratch, DB_NAME))
        }
        return out.distinctBy { listOf(it.winery, it.varietal, it.vintage, it.row, it.col, it.timestamp) }
    }

    private fun readRoomDb(file: File): List<LegacyBottle> {
        val out = mutableListOf<LegacyBottle>()
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("SELECT * FROM wine_bottles", null).use { c ->
                    while (c.moveToNext()) {
                        out += LegacyBottle(
                            winery = c.stringOrNull("wineryName") ?: "",
                            classification = c.stringOrNull("classification"),
                            varietal = c.stringOrNull("varietal") ?: "",
                            vintage = c.stringOrNull("vintage") ?: "",
                            row = c.intOrNull("gridRow") ?: 1,
                            col = c.intOrNull("gridCol") ?: 1,
                            price = c.doubleOrNull("price"),
                            isAging = (c.intOrNull("isAging") ?: 0) != 0,
                            photoUri = c.stringOrNull("photoUri"),
                            timestamp = c.longOrNull("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the version 1 database", e)
        }
        return out
    }

    // ------------------------------------------------------------------ offline caches of other projects

    private fun currentProjectId(): String? = try {
        com.google.firebase.FirebaseApp.getInstance().options.projectId
    } catch (e: Exception) {
        null
    }

    private fun cacheFiles(): List<File> = FirestoreCacheReader.cacheFiles(dbFile.parentFile, currentProjectId())

    /**
     * Bottles found in Firestore offline caches left by older builds that used a different Firebase
     * project (e.g. "wine-cellar-vault-default").
     */
    fun readOfflineCaches(): List<LegacyBottle> {
        val others = cacheFiles().flatMap { file ->
            FirestoreCacheReader.readFile(file).mapNotNull { toLegacyBottle(it) }
        }
        // The current project's cache may also hold the old build's "wines" folders.
        val projectId = currentProjectId()
        val current = dbFile.parentFile?.listFiles()
            ?.filter { f ->
                projectId != null && f.name.startsWith("firestore.") && f.name.contains(projectId) &&
                    !f.name.endsWith("-journal") && !f.name.endsWith("-wal") && !f.name.endsWith("-shm")
            }
            ?.flatMap { file ->
                FirestoreCacheReader.readFile(file)
                    .filter { it.collection.endsWith("wines", ignoreCase = true) }
                    .mapNotNull { toLegacyBottle(it) }
            }
            ?: emptyList()
        return others + current
    }

    /** Firestore values as plain Kotlin values (timestamps to millis, blobs to bytes). */
    private fun plain(value: Any?): Any? = when (value) {
        is com.google.firebase.Timestamp -> value.toDate().time
        is com.google.firebase.firestore.Blob -> value.toBytes()
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to plain(v) }
        is List<*> -> value.map { plain(it) }
        else -> value
    }

    /** Bottles in users/{uid}/wines (the older AI Studio build), plus a short status note. */
    private suspend fun readOldUserWines(uid: String?): Pair<List<LegacyBottle>, String?> {
        if (uid == null) return Pair(emptyList(), null)
        val (docs, error) = repo.oldUserWines(uid)
        if (error != null) return Pair(emptyList(), "users/$uid/wines: couldn't read ($error)")
        val bottles = docs.mapNotNull { d ->
            val data = d.data ?: return@mapNotNull null
            toLegacyBottle("users/$uid/wines", data.mapValues { plain(it.value) })
        }
        val keys = docs.flatMap { it.data?.keys ?: emptySet() }.distinct().take(20).joinToString(",")
        return Pair(bottles, "users/$uid/wines: ${docs.size} documents, ${bottles.size} usable [$keys]")
    }

    /** Everything from older versions that's stored on this phone. */
    fun phoneBottles(): List<LegacyBottle> = readOfflineCaches() + readLocal()

    private fun toLegacyBottle(doc: FirestoreCacheReader.Doc): LegacyBottle? = toLegacyBottle(doc.collection, doc.fields)

    private fun toLegacyBottle(collection: String, fields: Map<String, Any?>): LegacyBottle? {
        val f = fields.mapKeys { it.key.lowercase() }

        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
            when (val v = f[k]) {
                is String -> v.takeIf { it.isNotBlank() && it.length < 300 }
                is Long -> v.toString()
                is Double -> if (v == Math.floor(v)) v.toLong().toString() else v.toString()
                else -> null
            }
        }

        fun num(vararg keys: String): Double? = keys.firstNotNullOfOrNull { k ->
            when (val v = f[k]) {
                is Long -> v.toDouble()
                is Double -> v
                is String -> v.replace("$", "").replace(",", ".").trim().toDoubleOrNull()
                else -> null
            }
        }

        fun bool(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { k -> f[k] as? Boolean }

        val looksLikeBottle = collection.endsWith("bottles", ignoreCase = true) ||
            collection.endsWith("wines", ignoreCase = true) ||
            (("wineryname" in f || "winery" in f) && ("varietal" in f || "vintage" in f))
        if (!looksLikeBottle) return null

        val winery = str("wineryname", "winery", "producer", "estate", "name") ?: ""
        val varietal = str("varietal", "variety", "grape", "type") ?: ""
        if (winery.isBlank() && varietal.isBlank()) return null

        // A photo may be stored in the document itself as base64 text or raw bytes.
        var photoBase64: String? = null
        for (v in f.values) {
            val candidate = when (v) {
                is String -> if (v.length > 2000) v.substringAfter("base64,", v) else null
                is ByteArray -> if (v.size > 1500) android.util.Base64.encodeToString(v, android.util.Base64.NO_WRAP) else null
                else -> null
            }
            if (candidate != null) {
                photoBase64 = candidate
                break
            }
        }

        return LegacyBottle(
            winery = winery,
            classification = str("classification", "designation", "label"),
            varietal = varietal,
            vintage = str("vintage", "year") ?: "",
            row = num("gridrow", "row", "rackrow")?.toInt() ?: 1,
            col = num("gridcol", "col", "column", "rackcol")?.toInt() ?: 1,
            price = num("price", "cost"),
            isAging = bool("isaging", "aging") ?: false,
            photoUri = str("photouri", "photourl", "photopath", "imageuri", "imageurl", "photo", "image"),
            timestamp = num("timestamp", "createdat", "addedat", "updatedat")?.toLong() ?: System.currentTimeMillis(),
            photoBase64 = photoBase64
        )
    }

    private fun savedLegacyEmail(): String? =
        context.getSharedPreferences("cellar_sync_prefs", Context.MODE_PRIVATE)
            .getString("user_email", null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /**
     * Version 1 stored its cloud copy under "cellar_" + the part of the typed email before the @,
     * with dots turned into underscores, keeping any capital letters. We try each email as typed
     * and in lower case.
     */
    private fun legacyIdsFor(emails: List<String?>): List<String> =
        emails.filterNotNull()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { e ->
                val name = e.substringBefore("@").replace(".", "_")
                listOf("cellar_$name", "cellar_" + name.lowercase())
            }
            .distinct()

    /** Reads the version 1 cloud copies. Returns the bottles plus one note per location checked. */
    private suspend fun readCloud(emails: List<String?>): Pair<List<LegacyBottle>, List<String>> {
        val out = mutableListOf<LegacyBottle>()
        val notes = mutableListOf<String>()
        for (legacyId in legacyIdsFor(emails)) {
            val (docs, error) = repo.legacyCloudBottles(legacyId)
            notes += if (error != null) "$legacyId: couldn't read ($error)" else "$legacyId: ${docs.size} bottles"
            for (doc in docs) {
                out += LegacyBottle(
                    winery = doc.getString("wineryName") ?: "",
                    classification = doc.getString("classification"),
                    varietal = doc.getString("varietal") ?: "",
                    vintage = doc.getString("vintage") ?: "",
                    row = doc.getLong("gridRow")?.toInt() ?: 1,
                    col = doc.getLong("gridCol")?.toInt() ?: 1,
                    price = doc.getDouble("price"),
                    isAging = doc.getBoolean("isAging") ?: false,
                    photoUri = doc.getString("photoUri"),
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                )
            }
        }
        return Pair(out, notes)
    }

    suspend fun importInto(
        cellarId: String,
        uid: String,
        email: String?
    ): Result = withContext(Dispatchers.IO) {
        val (userWines, userWinesNote) = readOldUserWines(uid)
        val local = userWines + readOfflineCaches() + readLocal()
        val (legacyCloud, legacyNotes) = readCloud(listOf(savedLegacyEmail(), email))
        val cloud = legacyCloud
        val cloudNotes = listOfNotNull(userWinesNote) + legacyNotes

        val existingKeys = repo.bottlesOnce(cellarId)
            .map { key(it.wineryName, it.varietal, it.vintage, it.gridRow, it.gridCol) }
            .toSet()
        val seen = mutableSetOf<String>()
        val items = mutableListOf<ImportItem>()
        var alreadyInCellar = 0
        var photos = 0

        // Local copy first: it has the photo files. Cloud copies of the same bottle are skipped.
        for (b in local + cloud) {
            if (b.winery.isBlank() && b.varietal.isBlank()) continue
            val k = key(b.winery, b.varietal, b.vintage, b.row, b.col)
            if (k in existingKeys) {
                if (seen.add(k)) alreadyInCellar++
                continue
            }
            if (!seen.add(k)) continue

            val photoPath = b.photoUri?.takeIf { it.isNotBlank() && !it.startsWith("preset_") }
            val photoFile = photoPath?.let { File(it) }?.takeIf { it.exists() }
            val embedded = b.photoBase64?.let { PhotoUtils.decode(it) }
            val photo = when {
                embedded != null -> PhotoUtils.fromBitmap(embedded)
                photoPath != null && photoPath.startsWith("http") -> PhotoUtils.fromUrl(photoPath)
                else -> photoFile?.let { PhotoUtils.fromFile(it) }
            }
            if (photo != null) photos++

            val preset = b.photoUri?.takeIf { it.startsWith("preset_") } ?: presetForVarietal(b.varietal)
            items += ImportItem(
                draft = BottleDraft(
                    wineryName = b.winery.trim(),
                    classification = b.classification?.trim()?.ifBlank { null },
                    varietal = b.varietal.trim(),
                    vintage = b.vintage.trim(),
                    gridRow = b.row.coerceIn(1, CellarLayout.ROWS),
                    gridCol = b.col.coerceIn(1, CellarLayout.COLS),
                    price = b.price,
                    isAging = b.isAging,
                    preset = preset
                ),
                photo = photo,
                createdAt = b.timestamp
            )
        }

        val confirmed = if (items.isEmpty()) true else repo.writeImported(cellarId, uid, items)
        context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(Prefs.LEGACY_IMPORTED_INTO, cellarId)
            .apply()
        Result(
            imported = items.size,
            alreadyInCellar = alreadyInCellar,
            photos = photos,
            stillUploading = !confirmed,
            cloudNotes = cloudNotes
        )
    }

    private fun key(winery: String, varietal: String, vintage: String, row: Int, col: Int): String =
        listOf(winery.trim().lowercase(), varietal.trim().lowercase(), vintage.trim().lowercase(), row, col)
            .joinToString("|")

    private fun backupOnce(file: File) {
        val backupDir = File(context.filesDir, "v1_backup")
        if (backupDir.exists()) return
        try {
            backupDir.mkdirs()
            for (suffix in listOf("", "-wal", "-shm")) {
                val src = File(file.path + suffix)
                if (src.exists()) src.copyTo(File(backupDir, src.name), overwrite = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backup of version 1 database failed", e)
        }
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val i = getColumnIndex(column)
        return if (i < 0 || isNull(i)) null else getString(i)
    }

    private fun Cursor.intOrNull(column: String): Int? {
        val i = getColumnIndex(column)
        return if (i < 0 || isNull(i)) null else getInt(i)
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val i = getColumnIndex(column)
        return if (i < 0 || isNull(i)) null else getLong(i)
    }

    private fun Cursor.doubleOrNull(column: String): Double? {
        val i = getColumnIndex(column)
        return if (i < 0 || isNull(i)) null else getDouble(i)
    }

    companion object {
        private const val TAG = "LegacyImporter"
        private const val DB_NAME = "wine_cellar_database"
    }
}
