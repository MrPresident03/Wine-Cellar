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
        val timestamp: Long
    )

    data class Result(
        val imported: Int,
        val alreadyInCellar: Int,
        val photos: Int,
        val stillUploading: Boolean,
        /** One line per old cloud location checked, e.g. "cellar_steven: 42 bottles". */
        val cloudNotes: List<String>
    )

    /** Last error from reading the on-phone database, for the diagnostics screen. */
    @Volatile
    private var lastLocalError: String? = null

    private val dbFile: File get() = context.getDatabasePath(DB_NAME)

    fun hasLocalData(): Boolean = dbFile.exists()

    /** The cellar name the user set in version 1, if any. */
    fun legacyCellarName(): String? =
        context.getSharedPreferences("wine_cellar_prefs", Context.MODE_PRIVATE)
            .getString("cellar_name", null)
            ?.takeIf { it.isNotBlank() }

    /** Reads every bottle from the version 1 database on this phone. Call off the main thread. */
    fun readLocal(): List<LegacyBottle> {
        val file = dbFile
        if (!file.exists()) return emptyList()
        backupOnce(file)
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
            lastLocalError = null
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the version 1 database", e)
            lastLocalError = e.toString()
        }
        return out
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

    /** A plain-text report of where version 1 data could be, for troubleshooting. */
    suspend fun diagnostics(currentEmail: String?, extraEmail: String?): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("App: ").append(context.packageName).append('\n')
        val dbDir = dbFile.parentFile
        val dbFiles = dbDir?.listFiles()?.joinToString { "${it.name} (${it.length()} B)" }
        sb.append("databases: ").append(if (dbFiles.isNullOrEmpty()) "none" else dbFiles).append('\n')
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefs = prefsDir.listFiles()?.joinToString { it.name }
        sb.append("shared_prefs: ").append(if (prefs.isNullOrEmpty()) "none" else prefs).append('\n')
        sb.append("Old app saved email: ").append(savedLegacyEmail() ?: "none").append('\n')
        sb.append("Old app cellar name: ").append(legacyCellarName() ?: "none").append('\n')
        if (dbFile.exists()) {
            sb.append("Old database tables: ").append(tableSummary()).append('\n')
        }
        val local = readLocal()
        sb.append("Old database bottles: ").append(local.size)
        lastLocalError?.let { sb.append(" (error: ").append(it).append(')') }
        sb.append('\n')
        val backup = File(context.filesDir, "v1_backup").listFiles()?.joinToString { "${it.name} (${it.length()} B)" }
        sb.append("Backup copy: ").append(if (backup.isNullOrEmpty()) "none" else backup).append('\n')
        val (_, notes) = readCloud(listOf(savedLegacyEmail(), currentEmail, extraEmail))
        if (notes.isEmpty()) sb.append("Old cloud copy: no email to look up\n")
        notes.forEach { sb.append("Old cloud ").append(it).append('\n') }
        sb.toString().trim()
    }

    private fun tableSummary(): String = try {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val tables = mutableListOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                while (c.moveToNext()) tables += c.getString(0)
            }
            tables.joinToString { t ->
                val count = try {
                    db.rawQuery("SELECT COUNT(*) FROM \"" + t.replace("\"", "") + "\"", null).use { c ->
                        if (c.moveToFirst()) c.getLong(0).toString() else "?"
                    }
                } catch (e: Exception) {
                    "?"
                }
                "$t=$count"
            }
        }
    } catch (e: Exception) {
        "couldn't open (${e.message})"
    }

    suspend fun importInto(
        cellarId: String,
        uid: String,
        email: String?,
        extraEmail: String? = null
    ): Result = withContext(Dispatchers.IO) {
        val local = readLocal()
        val (cloud, cloudNotes) = readCloud(listOf(savedLegacyEmail(), email, extraEmail))

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
            val photo = photoFile?.let { PhotoUtils.fromFile(it) }
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
