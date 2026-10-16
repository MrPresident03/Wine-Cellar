package com.houseofvino.core.csv

import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.GrapeShare
import com.houseofvino.core.model.Grapes
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Decimal
import com.houseofvino.core.money.DecimalParser
import com.houseofvino.core.money.Money
import com.houseofvino.core.text.TextNormalizer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod

/** Columns House of Vino can import, with the header names other apps and spreadsheets use. */
enum class ImportField(val label: String, val aliases: List<String>) {
    PRODUCER("Producer", listOf("producer", "winery", "maker", "domaine", "chateau", "estate", "wine producer", "producer name")),
    WINE_NAME("Wine name", listOf("wine name", "wine", "name", "cuvee", "label", "designation", "wine_name", "product", "description")),
    VINEYARD("Vineyard", listOf("vineyard", "lieu dit", "climat")),
    VINTAGE("Vintage", listOf("vintage", "year", "millesime", "jahrgang", "annata", "anada")),
    STYLE("Style / colour", listOf("type", "color", "colour", "style", "category", "wine type", "regional wine style")),
    COUNTRY("Country", listOf("country", "country of origin", "origin")),
    REGION("Region", listOf("region", "area", "wine region")),
    SUB_REGION("Sub-region", listOf("subregion", "sub region", "sub-region", "district")),
    APPELLATION("Appellation", listOf("appellation", "aoc", "aop", "ava", "doc", "docg", "gi", "denomination")),
    CLASSIFICATION("Classification", listOf("classification", "cru", "quality", "pradikat")),
    GRAPES("Grapes", listOf("varietal", "variety", "grape", "grapes", "master varietal", "cepage", "varietals", "grape variety")),
    QUANTITY("Quantity", listOf("quantity", "qty", "bottles", "count", "number of bottles", "no of bottles", "in stock", "inventory", "on hand", "units")),
    FORMAT("Bottle size", listOf("size", "bottle size", "format", "volume", "ml")),
    LOCATION("Location", listOf("location", "cellar", "place", "storage", "where")),
    BIN("Bin / rack / shelf", listOf("bin", "rack", "shelf", "slot", "position", "sub location", "sublocation")),
    PRICE("Price per bottle", listOf("price", "cost", "purchase price", "price per bottle", "paid", "unit price", "bottle price", "unit cost")),
    CURRENCY("Currency", listOf("currency", "ccy", "price currency")),
    PURCHASE_DATE("Purchase date", listOf("purchase date", "date purchased", "bought", "acquired", "date acquired", "purchased", "date", "order date")),
    SOURCE("Bought from", listOf("store", "merchant", "purchased from", "supplier", "source", "vendor", "retailer", "shop", "where bought")),
    DRINK_FROM("Drink from", listOf("drink from", "begin consume", "beginconsume", "drinking window start", "drink window start", "ready from", "from")),
    DRINK_TO("Drink by", listOf("drink by", "drink to", "end consume", "endconsume", "drinking window end", "drink window end", "drink before", "to")),
    ALCOHOL("Alcohol %", listOf("abv", "alcohol", "alc", "alc %", "alcohol %", "alcohol percent")),
    NOTES("Notes", listOf("notes", "note", "comment", "comments", "personal note", "private note", "remarks")),
    VALUE("Estimated value per bottle", listOf("value", "valuation", "market value", "estimated value", "current value", "est value")),
    RATING("My rating", listOf("rating", "score", "my score", "points", "your rating", "my rating")),
    TAGS("Tags", listOf("tags", "tag", "labels", "lists")),
    BARCODE("Barcode", listOf("barcode", "upc", "ean", "gtin", "bar code"));

    companion object {
        private fun norm(s: String) = TextNormalizer.fold(s).replace("_", " ").replace(Regex("""\s+"""), " ").trim()

        /**
         * Best guess for each column. Exact alias matches win over partial ones; each field is
         * used once. Unrecognised columns map to null (ignored unless the user maps them).
         */
        fun guessMapping(headers: List<String>): Map<Int, ImportField> {
            val result = mutableMapOf<Int, ImportField>()
            val taken = mutableSetOf<ImportField>()
            val normalized = headers.map(::norm)
            // Pass 1: exact matches. Pass 2: header contains an alias of 3+ letters.
            for (pass in 1..2) {
                normalized.forEachIndexed { i, h ->
                    if (i in result || h.isEmpty()) return@forEachIndexed
                    val field = entries.firstOrNull { f ->
                        f !in taken && f.aliases.any { a -> if (pass == 1) h == a else a.length >= 4 && Regex("""\b${Regex.escape(a)}\b""").containsMatchIn(h) }
                    }
                    if (field != null) {
                        result[i] = field
                        taken += field
                    }
                }
            }
            return result
        }
    }
}

enum class DateOrder(val label: String) { DMY("Day/Month/Year"), MDY("Month/Day/Year"), YMD("Year/Month/Day") }

data class ImportOptions(
    val defaultCurrency: Currency,
    val dateOrder: DateOrder = DateOrder.DMY,
    val defaultFormatMl: Int = BottleFormat.STANDARD_ML,
    /** Used when there's no quantity column: one row = one bottle. */
    val defaultQuantity: Int = 1,
    val defaultLocation: String = "Cellar",
    val currentYear: Int = 2026,
)

/** One valid row, ready to import. */
data class ImportRow(
    val line: Int,
    val producer: String,
    val name: String,
    val vintage: Vintage,
    val style: WineStyle?,
    val country: String?,
    val region: String?,
    val subRegion: String?,
    val appellation: String?,
    val classification: String?,
    val grapes: List<GrapeShare>,
    val quantity: Int,
    val formatMl: Int,
    /** Top-level place first, e.g. ["Cellar", "Rack 3"]. */
    val locationPath: List<String>,
    val unitPrice: Money?,
    val acquiredOn: LocalDate?,
    val source: String?,
    val drinkFrom: Int?,
    val drinkTo: Int?,
    val alcohol: Decimal?,
    val notes: String?,
    val valuePerBottle: Money?,
    val rating: String?,
    val tags: List<String>,
    val barcode: String?,
) {
    /** Two rows with the same key in one file are probably the same purchase entered twice. */
    val duplicateKey: String
        get() = listOf(
            TextNormalizer.identityKey(producer), TextNormalizer.identityKey(name), vintage.toString(), formatMl.toString(),
            locationPath.joinToString("/") { TextNormalizer.fold(it) }, quantity.toString(), unitPrice?.toString().orEmpty(),
            acquiredOn?.toString().orEmpty(),
        ).joinToString("|")
}

data class RowProblem(val field: ImportField?, val message: String, val value: String? = null)

sealed interface ParsedRow {
    val line: Int

    data class Ok(override val line: Int, val row: ImportRow, val warnings: List<RowProblem>) : ParsedRow
    data class Failed(override val line: Int, val problems: List<RowProblem>) : ParsedRow
}

object ImportRowParser {

    fun parseAll(table: CsvTable, mapping: Map<Int, ImportField>, options: ImportOptions): List<ParsedRow> =
        table.rows.mapIndexed { i, r -> parse(i + 2, r, mapping, options) }

    fun parse(line: Int, cells: List<String>, mapping: Map<Int, ImportField>, o: ImportOptions): ParsedRow {
        fun v(f: ImportField): String? = mapping.entries.firstOrNull { it.value == f }?.key?.let { cells.getOrNull(it) }?.trim()?.ifEmpty { null }
        val problems = mutableListOf<RowProblem>()
        val warnings = mutableListOf<RowProblem>()

        val rawVintage = v(ImportField.VINTAGE)
        var producer = v(ImportField.PRODUCER)
        var name = v(ImportField.WINE_NAME).orEmpty()
        v(ImportField.VINEYARD)?.let { name = if (name.isEmpty()) it else "$name $it" }
        // Some apps put "2015 Producer Cuvée" in a single Wine column.
        if (producer != null && name.isNotEmpty()) {
            name = name.removePrefix(rawVintage.orEmpty()).trim()
            if (name.startsWith(producer, ignoreCase = true)) name = name.substring(producer.length).trim().trimStart(',', '-', ' ')
        }
        if (producer == null && name.isNotEmpty()) {
            producer = name
            name = ""
            warnings += RowProblem(ImportField.PRODUCER, "No producer column; used the wine name as the producer")
        }
        if (producer == null) problems += RowProblem(ImportField.PRODUCER, "Producer is missing")

        val vintage = Vintage.parse(rawVintage, o.currentYear + 1)
        if (vintage == null) problems += RowProblem(ImportField.VINTAGE, "\"$rawVintage\" isn't a year or NV", rawVintage)

        val rawQty = v(ImportField.QUANTITY)
        val quantity = if (rawQty == null) o.defaultQuantity else rawQty.replace(",", "").toDoubleOrNull()?.takeIf { it >= 0 && it == kotlin.math.floor(it) }?.toInt()
        if (quantity == null) problems += RowProblem(ImportField.QUANTITY, "\"$rawQty\" isn't a whole number of bottles", rawQty)
        else if (quantity == 0) problems += RowProblem(ImportField.QUANTITY, "Quantity is 0 — nothing to add (wishlist and history imports aren't supported from this file)", rawQty)
        else if (quantity > 10_000) problems += RowProblem(ImportField.QUANTITY, "$quantity bottles in one row looks wrong", rawQty)

        val rawFormat = v(ImportField.FORMAT)
        val format = if (rawFormat == null) o.defaultFormatMl else BottleFormat.parse(rawFormat)
        if (format == null) problems += RowProblem(ImportField.FORMAT, "\"$rawFormat\" isn't a bottle size", rawFormat)

        val currency = v(ImportField.CURRENCY)?.let { c ->
            Currency.parseOrNull(c) ?: run { problems += RowProblem(ImportField.CURRENCY, "\"$c\" isn't a currency code like NZD", c); null }
        } ?: o.defaultCurrency
        val style = DecimalParser.Style('.', ',')
        fun money(f: ImportField): Money? {
            val raw = v(f) ?: return null
            val m = Money.parse(raw, currency, style)
            if (m == null) problems += RowProblem(f, "\"$raw\" isn't an amount in ${currency.code}", raw)
            if (m != null && m.isNegative) {
                problems += RowProblem(f, "Negative amount", raw)
                return null
            }
            return m
        }
        val price = money(ImportField.PRICE)
        val value = money(ImportField.VALUE)

        val rawDate = v(ImportField.PURCHASE_DATE)
        val date = rawDate?.let { FlexibleDate.parse(it, o.dateOrder) }
        if (rawDate != null && date == null) problems += RowProblem(ImportField.PURCHASE_DATE, "\"$rawDate\" isn't a date (expected ${o.dateOrder.label})", rawDate)

        fun year(f: ImportField): Int? {
            val raw = v(f) ?: return null
            val y = raw.take(4).toIntOrNull()?.takeIf { it in 1900..2200 }
            if (y == null) warnings += RowProblem(f, "\"$raw\" isn't a year; left empty", raw)
            return y
        }
        var drinkFrom = year(ImportField.DRINK_FROM)
        var drinkTo = year(ImportField.DRINK_TO)
        if (drinkFrom != null && drinkTo != null && drinkFrom > drinkTo) {
            warnings += RowProblem(ImportField.DRINK_TO, "Drinking window ends before it starts; left empty")
            drinkFrom = null
            drinkTo = null
        }

        val alcohol = v(ImportField.ALCOHOL)?.let { raw ->
            DecimalParser.normalise(raw.removeSuffix("%").trim(), style)?.let(Decimal::parseOrNull)?.takeIf { !it.isNegative && it <= Decimal.of(100) }
                ?: run { warnings += RowProblem(ImportField.ALCOHOL, "\"$raw\" isn't a percentage; left empty", raw); null }
        }

        val location = v(ImportField.LOCATION) ?: o.defaultLocation
        val path = (listOf(location) + listOfNotNull(v(ImportField.BIN))).flatMap { it.split('>', '›', '/').map(String::trim) }.filter { it.isNotEmpty() }

        if (problems.isNotEmpty()) return ParsedRow.Failed(line, problems)
        val grapes = v(ImportField.GRAPES)?.let { Grapes.parse(it) }.orEmpty()
        val rawStyle = v(ImportField.STYLE)
        return ParsedRow.Ok(
            line,
            ImportRow(
                line = line,
                producer = producer!!.take(200),
                name = name.take(200),
                vintage = vintage!!,
                style = rawStyle?.let { WineStyle.guess(it) } ?: WineStyle.guess(grapes.joinToString(" ") { it.name }),
                country = v(ImportField.COUNTRY),
                region = v(ImportField.REGION),
                subRegion = v(ImportField.SUB_REGION),
                appellation = v(ImportField.APPELLATION),
                classification = v(ImportField.CLASSIFICATION),
                grapes = grapes,
                quantity = quantity!!,
                formatMl = format!!,
                locationPath = path.ifEmpty { listOf(o.defaultLocation) },
                unitPrice = price,
                acquiredOn = date,
                source = v(ImportField.SOURCE),
                drinkFrom = drinkFrom,
                drinkTo = drinkTo,
                alcohol = alcohol,
                notes = v(ImportField.NOTES),
                valuePerBottle = value,
                rating = v(ImportField.RATING),
                tags = v(ImportField.TAGS)?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                barcode = v(ImportField.BARCODE),
            ),
            warnings,
        )
    }
}

/** Dates as spreadsheets write them. Ambiguous numeric dates follow the chosen [DateOrder]. */
object FlexibleDate {
    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9,
        "oct" to 10, "nov" to 11, "dec" to 12,
    )

    fun parse(text: String, order: DateOrder): LocalDate? {
        val t = text.trim().lowercase()
        // ISO, possibly with a time part.
        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(t)?.let { m -> return safe(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        // Excel serial day number (1900 date system).
        t.toIntOrNull()?.let { n -> if (n in 20_000..80_000) return LocalDate(1899, 12, 30).plus(DatePeriod(days = n)) }
        // Textual months: "12 Mar 2024", "Mar 12, 2024", "12-mar-24".
        val words = t.split(Regex("""[\s,./-]+""")).filter { it.isNotEmpty() }
        if (words.size == 3) {
            val mi = words.indexOfFirst { w -> MONTHS.keys.any { w.startsWith(it) } }
            if (mi >= 0) {
                val month = MONTHS.entries.first { words[mi].startsWith(it.key) }.value
                val others = words.filterIndexed { i, _ -> i != mi }.map { it.toIntOrNull() ?: return null }
                val (a, b) = others
                val (day, year) = if (a > 31) b to a else a to b
                return safe(fullYear(year), month, day)
            }
            val nums = words.map { it.toIntOrNull() ?: return null }
            return when {
                nums[0] > 31 -> safe(fullYear(nums[0]), nums[1], nums[2])
                order == DateOrder.DMY -> safe(fullYear(nums[2]), nums[1], nums[0])
                order == DateOrder.MDY -> safe(fullYear(nums[2]), nums[0], nums[1])
                else -> safe(fullYear(nums[0]), nums[1], nums[2])
            }
        }
        return null
    }

    private fun fullYear(y: Int) = if (y < 100) (if (y < 70) 2000 + y else 1900 + y) else y

    private fun safe(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate(y, m, d) }.getOrNull()?.takeIf { y in 1900..2200 }
}
