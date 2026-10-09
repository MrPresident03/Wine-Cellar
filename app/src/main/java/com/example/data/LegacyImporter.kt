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
        val stillUploading: Boolean
    )

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
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the version 1 database", e)
        }
        return out
    }

    /** Reads the version 1 cloud copy, if one exists and is still readable. */
    private suspend fun readCloud(currentEmail: String?): List<LegacyBottle> {
        val savedEmail = context.getSharedPreferences("cellar_sync_prefs", Context.MODE_PRIVATE)
            .getString("user_email", null)
        val legacyIds = listOfNotNull(savedEmail, currentEmail)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { "cellar_" + it.substringBefore("@").replace(".", "_") }
            .distinct()
        val out = mutableListOf<LegacyBottle>()
        for (legacyId in legacyIds) {
            for (doc in repo.legacyCloudBottles(legacyId)) {
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
        return out
    }

    suspend fun importInto(cellarId: String, uid: String, email: String?): Result = withContext(Dispatchers.IO) {
        val local = readLocal()
        val cloud = readCloud(email)

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
            stillUploading = !confirmed
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
