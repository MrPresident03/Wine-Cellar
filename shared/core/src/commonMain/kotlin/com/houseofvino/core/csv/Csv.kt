package com.houseofvino.core.csv

/** A parsed CSV file: the header row and the data rows, all as text. */
data class CsvTable(val headers: List<String>, val rows: List<List<String>>, val delimiter: Char) {
    /** Cell by column index, or "" when a short row doesn't have it. */
    fun cell(row: Int, col: Int): String = rows[row].getOrElse(col) { "" }
}

class CsvFormatException(message: String) : IllegalArgumentException(message)

/**
 * RFC 4180 CSV with the real-world extras spreadsheets produce: a byte-order mark, CR/LF/CRLF
 * line endings, quoted fields with embedded delimiters, quotes and newlines, and comma,
 * semicolon, tab or pipe delimiters (detected).
 */
object Csv {

    fun parse(text: String, delimiter: Char? = null): CsvTable {
        val body = text.removePrefix("﻿")
        val d = delimiter ?: detectDelimiter(body)
        val records = records(body, d)
        if (records.isEmpty()) throw CsvFormatException("The file is empty")
        val headers = records.first().map { it.trim() }
        if (headers.all { it.isEmpty() }) throw CsvFormatException("The first row should contain column names")
        val rows = records.drop(1).filterNot { r -> r.all { it.isBlank() } }
        return CsvTable(headers, rows, d)
    }

    /** Picks the delimiter that splits the first lines into the most consistent number of columns. */
    fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().filter { it.isNotBlank() }.take(10).toList()
        if (sample.isEmpty()) return ','
        return CANDIDATES.maxByOrNull { d ->
            val counts = sample.map { line -> countOutsideQuotes(line, d) }
            val first = counts.first()
            if (first == 0) -1.0 else counts.count { it == first }.toDouble() + first / 100.0
        } ?: ','
    }

    private val CANDIDATES = listOf(',', ';', '\t', '|')

    private fun countOutsideQuotes(line: String, d: Char): Int {
        var n = 0
        var quoted = false
        for (c in line) {
            if (c == '"') quoted = !quoted else if (c == d && !quoted) n++
        }
        return n
    }

    private fun records(text: String, d: Char): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var i = 0
        var quoted = false
        var fieldStarted = false
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when {
                    c == '"' && !fieldStarted -> {
                        quoted = true
                        fieldStarted = true
                    }
                    c == d -> {
                        row.add(field.toString())
                        field.clear()
                        fieldStarted = false
                    }
                    c == '\r' || c == '\n' -> {
                        if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                        row.add(field.toString())
                        out.add(row)
                        row = mutableListOf()
                        field.clear()
                        fieldStarted = false
                    }
                    else -> {
                        field.append(c)
                        fieldStarted = true
                    }
                }
            }
            i++
        }
        if (quoted) throw CsvFormatException("A quoted field is never closed (check for a stray \" character)")
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            out.add(row)
        }
        return out
    }

    /**
     * Writes CSV for spreadsheets. Text cells that start with =, +, -, @ or a tab are prefixed
     * with an apostrophe so a spreadsheet won't run them as formulas; plain numbers are left alone.
     */
    fun write(headers: List<String>, rows: List<List<String?>>, delimiter: Char = ','): String {
        val sb = StringBuilder()
        fun cell(v: String?) {
            val raw = v ?: ""
            val safe = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r" && raw.toDoubleOrNull() == null) "'$raw" else raw
            val needsQuotes = safe.any { it == delimiter || it == '"' || it == '\n' || it == '\r' } || safe != safe.trim()
            if (needsQuotes) sb.append('"').append(safe.replace("\"", "\"\"")).append('"') else sb.append(safe)
        }
        fun line(values: List<String?>) {
            values.forEachIndexed { i, v ->
                if (i > 0) sb.append(delimiter)
                cell(v)
            }
            sb.append("\r\n")
        }
        line(headers)
        rows.forEach(::line)
        return sb.toString()
    }
}

/**
 * Turns file bytes into text: honours UTF-8/UTF-16 byte-order marks, accepts UTF-8, and falls
 * back to Windows-1252 (what Excel on Windows writes) when the bytes aren't valid UTF-8.
 */
object TextDecoding {
    enum class Encoding { UTF8, UTF16LE, UTF16BE, WINDOWS1252 }

    data class Decoded(val text: String, val encoding: Encoding)

    fun decode(bytes: ByteArray): Decoded {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return Decoded(utf16(bytes, 2, littleEndian = true), Encoding.UTF16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return Decoded(utf16(bytes, 2, littleEndian = false), Encoding.UTF16BE)
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val slice = if (start == 0) bytes else bytes.copyOfRange(start, bytes.size)
        return if (isValidUtf8(slice)) Decoded(slice.decodeToString(), Encoding.UTF8) else Decoded(windows1252(slice), Encoding.WINDOWS1252)
    }

    fun isValidUtf8(b: ByteArray): Boolean {
        var i = 0
        while (i < b.size) {
            val c = b[i].toInt() and 0xFF
            val extra = when {
                c < 0x80 -> 0
                c in 0xC2..0xDF -> 1
                c in 0xE0..0xEF -> 2
                c in 0xF0..0xF4 -> 3
                else -> return false
            }
            if (i + extra >= b.size) return false
            for (k in 1..extra) {
                if ((b[i + k].toInt() and 0xC0) != 0x80) return false
            }
            i += extra + 1
        }
        return true
    }

    private fun utf16(b: ByteArray, offset: Int, littleEndian: Boolean): String {
        val sb = StringBuilder()
        var i = offset
        while (i + 1 < b.size) {
            val lo = b[i].toInt() and 0xFF
            val hi = b[i + 1].toInt() and 0xFF
            sb.append((if (littleEndian) (hi shl 8) or lo else (lo shl 8) or hi).toChar())
            i += 2
        }
        return sb.toString()
    }

    private val CP1252_80_9F = charArrayOf(
        '€', '\u0081', '‚', 'ƒ', '„', '…', '†', '‡', 'ˆ', '‰', 'Š', '‹', 'Œ', '\u008D', 'Ž', '\u008F',
        '\u0090', '‘', '’', '“', '”', '•', '–', '—', '˜', '™', 'š', '›', 'œ', '\u009D', 'ž', 'Ÿ',
    )

    private fun windows1252(b: ByteArray): String {
        val sb = StringBuilder(b.size)
        for (byte in b) {
            val v = byte.toInt() and 0xFF
            sb.append(if (v in 0x80..0x9F) CP1252_80_9F[v - 0x80] else v.toChar())
        }
        return sb.toString()
    }
}
