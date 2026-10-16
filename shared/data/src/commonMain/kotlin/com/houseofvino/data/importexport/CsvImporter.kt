package com.houseofvino.data.importexport

import com.houseofvino.core.csv.CsvTable
import com.houseofvino.core.csv.ImportField
import com.houseofvino.core.csv.ImportOptions
import com.houseofvino.core.csv.ImportRow
import com.houseofvino.core.csv.ImportRowParser
import com.houseofvino.core.csv.ParsedRow
import com.houseofvino.core.model.LocationKind
import com.houseofvino.core.model.WindowSource
import com.houseofvino.core.text.TextNormalizer
import com.houseofvino.data.Session
import com.houseofvino.data.repo.BarcodeRepository
import com.houseofvino.data.repo.InventoryRepository
import com.houseofvino.data.repo.LocationInput
import com.houseofvino.data.repo.LocationRepository
import com.houseofvino.data.repo.PurchaseInput
import com.houseofvino.data.repo.Store
import com.houseofvino.data.repo.TagRepository
import com.houseofvino.data.repo.TagTarget
import com.houseofvino.data.repo.ValuationRepository
import com.houseofvino.data.repo.VintageInput
import com.houseofvino.data.repo.WineInput
import com.houseofvino.data.repo.WineRepository

enum class RowDecision { IMPORT, SKIP }

/** How a valid row will be imported, and anything the user should decide first. */
data class PlannedRow(
    val row: ImportRow,
    val warnings: List<String>,
    /** The wine this row adds to, if one with the same producer and name already exists. */
    val existingWineId: String?,
    /** Earlier line in the same file that looks identical. */
    val duplicateOfLine: Int?,
    /** A purchase with the same wine, vintage, size, quantity, price and date is already in the cellar. */
    val alreadyImported: Boolean,
) {
    val suggested: RowDecision get() = if (duplicateOfLine != null || alreadyImported) RowDecision.SKIP else RowDecision.IMPORT
}

data class ImportPlan(
    val rows: List<PlannedRow>,
    val failed: List<ParsedRow.Failed>,
    val mapping: Map<Int, ImportField>,
    val sourceName: String,
) {
    val bottles: Int get() = rows.filter { it.suggested == RowDecision.IMPORT }.sumOf { it.row.quantity }
    val newWines: Int get() = rows.filter { it.suggested == RowDecision.IMPORT && it.existingWineId == null }
        .map { TextNormalizer.identityKey(it.row.producer) + "|" + TextNormalizer.identityKey(it.row.name) }.distinct().size
}

data class ImportResult(
    val importedRows: Int,
    val bottles: Int,
    val skippedRows: Int,
    val invalidRows: Int,
    val createdWines: Int,
    val createdPlaces: Int,
    /** Rows that were valid but failed while saving (line, reason). Everything else was saved. */
    val errors: List<Pair<Int, String>>,
)

/**
 * CSV import in two steps: [plan] reads and checks every row without changing anything, then
 * [commit] saves the rows the user kept. Each row is saved in its own transaction, so a bad row
 * can't undo good ones, and re-running a partly finished import skips rows already imported.
 */
class CsvImporter(
    private val store: Store,
    private val wines: WineRepository,
    private val inventory: InventoryRepository,
    private val locations: LocationRepository,
    private val tags: TagRepository,
    private val valuations: ValuationRepository,
    private val barcodes: BarcodeRepository,
) {
    private val db get() = store.db

    fun plan(session: Session, table: CsvTable, mapping: Map<Int, ImportField>, options: ImportOptions, sourceName: String): ImportPlan {
        val parsed = ImportRowParser.parseAll(table, mapping, options)
        val seen = mutableMapOf<String, Int>()
        val wineCache = mutableMapOf<String, String?>()
        val rows = parsed.filterIsInstance<ParsedRow.Ok>().map { ok ->
            val r = ok.row
            val key = WineRepository.identityKey(r.producer, r.name)
            val existing = wineCache.getOrPut(key) { db.wineQueries.byIdentityKey(session.householdId, key).executeAsList().firstOrNull()?.id }
            val dup = seen[r.duplicateKey]
            if (dup == null) seen[r.duplicateKey] = r.line
            PlannedRow(
                row = r,
                warnings = ok.warnings.map { it.message },
                existingWineId = existing,
                duplicateOfLine = dup,
                alreadyImported = existing != null && alreadyImported(existing, r),
            )
        }
        return ImportPlan(rows, parsed.filterIsInstance<ParsedRow.Failed>(), mapping, sourceName)
    }

    fun commit(session: Session, plan: ImportPlan, decisions: Map<Int, RowDecision> = emptyMap(), onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportResult {
        session.requireWrite()
        val toImport = plan.rows.filter { (decisions[it.row.line] ?: it.suggested) == RowDecision.IMPORT }
        var bottles = 0
        var createdWines = 0
        var createdPlaces = 0
        val errors = mutableListOf<Pair<Int, String>>()
        val today = store.clock.today()
        toImport.forEachIndexed { index, p ->
            val r = p.row
            try {
                store.write {
                    val (locationId, made) = locationFor(session, r.locationPath)
                    createdPlaces += made
                    val key = WineRepository.identityKey(r.producer, r.name)
                    val wineId = db.wineQueries.byIdentityKey(session.householdId, key).executeAsList().firstOrNull()?.id
                        ?: wines.createWine(
                            session,
                            WineInput(r.producer, r.name, r.style, r.country, r.region, r.subRegion, r.appellation, r.classification, r.grapes),
                            allowDuplicate = true,
                        ).also { createdWines++ }
                    val vintageId = wines.vintageFor(
                        session, wineId,
                        VintageInput(
                            r.vintage, alcoholPct = r.alcohol, drinkFrom = r.drinkFrom, drinkTo = r.drinkTo,
                            drinkSource = if (r.drinkFrom != null || r.drinkTo != null) WindowSource.OTHER else null,
                            drinkNote = if (r.drinkFrom != null || r.drinkTo != null) "Imported from ${plan.sourceName}" else null,
                            notes = listOfNotNull(r.notes, r.rating?.let { "Rating in ${plan.sourceName}: $it" }).joinToString("\n").ifBlank { null },
                        ),
                    )
                    inventory.addPurchase(
                        session,
                        PurchaseInput(
                            vintageId = vintageId, quantity = r.quantity, locationId = locationId, formatMl = r.formatMl,
                            unitPrice = r.unitPrice, acquiredOn = r.acquiredOn, source = r.source,
                            notes = "Imported from ${plan.sourceName}, line ${r.line}",
                        ),
                    )
                    r.valuePerBottle?.let { valuations.addValue(session, vintageId, r.formatMl, it, "Imported from ${plan.sourceName}", today) }
                    r.tags.forEach { t -> tags.attach(session, tags.obtain(session, t), TagTarget.VINTAGE, vintageId) }
                    r.barcode?.filter(Char::isDigit)?.takeIf { it.length in 8..14 }?.let { barcodes.remember(session, it, wineId, vintageId, r.formatMl) }
                }
                bottles += r.quantity
            } catch (e: Exception) {
                errors += r.line to (e.message ?: e::class.simpleName ?: "Couldn't save")
            }
            onProgress(index + 1, toImport.size)
        }
        return ImportResult(
            importedRows = toImport.size - errors.size,
            bottles = bottles,
            skippedRows = plan.rows.size - toImport.size,
            invalidRows = plan.failed.size,
            createdWines = createdWines,
            createdPlaces = createdPlaces,
            errors = errors,
        )
    }

    /** Finds the place by name path (case- and accent-insensitive), creating missing levels. */
    private fun locationFor(session: Session, path: List<String>): Pair<String, Int> {
        val all = locations.all(session).toMutableList()
        var parent: String? = null
        var made = 0
        for ((depth, name) in path.withIndex()) {
            val existing = all.firstOrNull { it.parentId == parent && TextNormalizer.fold(it.name) == TextNormalizer.fold(name) }
                ?: if (depth == 0) all.firstOrNull { TextNormalizer.fold(it.name) == TextNormalizer.fold(name) } else null
            parent = existing?.id ?: locations.create(session, LocationInput(name, if (depth == 0 && parent == null) LocationKind.SITE else LocationKind.BIN, parent)).also {
                made++
                all += locations.location(it)!!
            }
        }
        return parent!! to made
    }

    private fun alreadyImported(wineId: String, r: ImportRow): Boolean {
        val vintage = wines.findVintage(wineId, r.vintage) ?: return false
        return inventory.lotsOf(vintage.id).any { lot ->
            lot.formatMl == r.formatMl && lot.quantityAcquired == r.quantity && lot.unitPrice == r.unitPrice &&
                lot.acquiredOn == r.acquiredOn && (lot.source ?: "") == (r.source ?: "")
        }
    }
}
