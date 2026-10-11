package com.example.data

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Reads documents out of a Firestore offline cache file left on the phone by an older build of the
 * app (one that used a different Firebase project). The cache is a SQLite database whose rows hold
 * protobuf-encoded documents; this decodes just enough protobuf to get each document's path and
 * fields. It only ever opens those files read-only.
 */
internal object FirestoreCacheReader {
    private const val TAG = "FirestoreCacheReader"

    class Doc(val name: String, val fields: Map<String, Any?>) {
        /** e.g. "cellars/abc/bottles/xyz" */
        val path: String get() = name.substringAfter("/documents/", name)

        /** e.g. "cellars/abc/bottles" */
        val collection: String get() = path.substringBeforeLast("/", "")
    }

    // ------------------------------------------------------------------ cache files

    /** Firestore cache files in the app's databases folder, excluding the current project's. */
    fun cacheFiles(databasesDir: File?, currentProjectId: String?): List<File> =
        databasesDir?.listFiles()
            ?.filter { f ->
                f.name.startsWith("firestore.") &&
                    !f.name.endsWith("-journal") && !f.name.endsWith("-wal") && !f.name.endsWith("-shm") &&
                    (currentProjectId == null || !f.name.contains(currentProjectId))
            }
            ?.sortedBy { it.name }
            ?: emptyList()

    /** All documents in one cache file. Local unsynced changes win over the server copy. */
    fun readFile(file: File): List<Doc> {
        val docs = LinkedHashMap<String, Doc>()
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val tables = mutableSetOf<String>()
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                    while (c.moveToNext()) tables += c.getString(0)
                }
                if ("remote_documents" in tables) {
                    readBlobs(db, "SELECT contents FROM remote_documents") { blob ->
                        fromMaybeDocument(blob)?.let { docs[it.name] = it }
                    }
                }
                if ("mutations" in tables) {
                    readBlobs(db, "SELECT mutations FROM mutations") { blob ->
                        fromWriteBatch(blob).forEach { docs[it.name] = it }
                    }
                }
                if ("document_overlays" in tables) {
                    readBlobs(db, "SELECT overlay_mutation FROM document_overlays") { blob ->
                        fromWrite(blob)?.let { docs[it.name] = it }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read cache ${file.name}", e)
        }
        return docs.values.toList()
    }

    private fun readBlobs(db: SQLiteDatabase, sql: String, handle: (ByteArray) -> Unit) {
        try {
            db.rawQuery(sql, null).use { c ->
                while (c.moveToNext()) {
                    val blob = try {
                        c.getBlob(0)
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    try {
                        handle(blob)
                    } catch (e: Exception) {
                        // One unreadable row shouldn't stop the rest.
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query failed: $sql", e)
        }
    }

    // ------------------------------------------------------------------ protobuf

    private class Reader(private val buf: ByteArray) {
        private var pos = 0

        fun hasMore(): Boolean = pos < buf.size

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (pos >= buf.size) throw IllegalArgumentException("truncated varint")
                val b = buf[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift > 63) throw IllegalArgumentException("bad varint")
            }
        }

        fun fixed(byteCount: Int): Long {
            if (pos + byteCount > buf.size) throw IllegalArgumentException("truncated")
            var result = 0L
            for (i in 0 until byteCount) result = result or ((buf[pos + i].toLong() and 0xFF) shl (8 * i))
            pos += byteCount
            return result
        }

        fun bytes(): ByteArray {
            val len = varint()
            if (len < 0 || pos + len > buf.size) throw IllegalArgumentException("truncated bytes")
            val out = buf.copyOfRange(pos, pos + len.toInt())
            pos += len.toInt()
            return out
        }
    }

    /** Splits a message into (field number, value) pairs; value is a Long or a ByteArray. */
    private fun parse(bytes: ByteArray): List<Pair<Int, Any>> {
        val reader = Reader(bytes)
        val out = ArrayList<Pair<Int, Any>>()
        while (reader.hasMore()) {
            val tag = reader.varint()
            val field = (tag ushr 3).toInt()
            val raw: Any = when ((tag and 7L).toInt()) {
                0 -> reader.varint()
                1 -> reader.fixed(8)
                2 -> reader.bytes()
                5 -> reader.fixed(4)
                else -> throw IllegalArgumentException("unsupported wire type")
            }
            out += Pair(field, raw)
        }
        return out
    }

    private fun text(raw: Any): String? = (raw as? ByteArray)?.toString(Charsets.UTF_8)

    /** google.firestore.v1.Value */
    private fun decodeValue(bytes: ByteArray): Any? {
        for ((field, raw) in parse(bytes)) {
            when (field) {
                1 -> return (raw as? Long)?.let { it != 0L }
                2 -> return raw as? Long
                3 -> return (raw as? Long)?.let { java.lang.Double.longBitsToDouble(it) }
                5, 17 -> return text(raw)
                6 -> return (raw as? ByteArray)?.let { mapEntries(it, 1) }
                9 -> return (raw as? ByteArray)?.let { array ->
                    parse(array).filter { it.first == 1 }.mapNotNull { entry ->
                        (entry.second as? ByteArray)?.let { b -> decodeValue(b) }
                    }
                }
                10 -> return (raw as? ByteArray)?.let { ts ->
                    val seconds = parse(ts).firstOrNull { it.first == 1 }?.second as? Long ?: 0L
                    seconds * 1000L
                }
                11 -> return null
                18 -> return raw as? ByteArray
            }
        }
        return null
    }

    /** A protobuf map<string, Value> stored as repeated entries in [fieldNumber]. */
    private fun mapEntries(bytes: ByteArray, fieldNumber: Int): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((field, raw) in parse(bytes)) {
            if (field != fieldNumber || raw !is ByteArray) continue
            var key: String? = null
            var valueBytes: ByteArray? = null
            for ((f, x) in parse(raw)) {
                if (f == 1) key = text(x)
                if (f == 2) valueBytes = x as? ByteArray
            }
            val k = key ?: continue
            val vb = valueBytes
            out[k] = if (vb == null) null else decodeValue(vb)
        }
        return out
    }

    /** google.firestore.v1.Document: name = 1, fields = 2. */
    private fun document(bytes: ByteArray): Doc? {
        var name: String? = null
        for ((field, raw) in parse(bytes)) {
            if (field == 1) name = text(raw)
        }
        val n = name ?: return null
        if (!n.contains("/documents/")) return null
        return Doc(n, mapEntries(bytes, 2))
    }

    /** Firestore client MaybeDocument (remote_documents.contents): the document is field 2. */
    private fun fromMaybeDocument(bytes: ByteArray): Doc? {
        val raw = parse(bytes).firstOrNull { it.first == 2 }?.second as? ByteArray ?: return null
        return document(raw)
    }

    /** google.firestore.v1.Write: the updated document is field 1. */
    private fun fromWrite(bytes: ByteArray): Doc? {
        val raw = parse(bytes).firstOrNull { it.first == 1 }?.second as? ByteArray ?: return null
        return document(raw)
    }

    /** Firestore client WriteBatch: writes = 2, base_writes = 4. */
    private fun fromWriteBatch(bytes: ByteArray): List<Doc> =
        parse(bytes)
            .filter { it.first == 2 || it.first == 4 }
            .mapNotNull { entry -> (entry.second as? ByteArray)?.let { fromWrite(it) } }
}
