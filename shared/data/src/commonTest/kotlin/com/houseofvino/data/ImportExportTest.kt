package com.houseofvino.data

import com.houseofvino.core.csv.Csv
import com.houseofvino.core.csv.DateOrder
import com.houseofvino.core.csv.ImportField
import com.houseofvino.core.csv.ImportOptions
import com.houseofvino.core.search.SearchQuery
import com.houseofvino.data.importexport.RowDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportExportTest {
    private val file = """
        Producer,Wine,Vintage,Qty,Size,Price,Date,Location,Bin,Tags,Valuation
        Felton Road,Block 5,2019,6,750,95.00,12/03/2024,House,Rack A,"Birth year, Pinot",110
        Felton Road,Block 5,2019,6,750,95.00,12/03/2024,House,Rack A,,
        Krug,Grande Cuvée,NV,2,1.5L,,2024-05-01,House,Fridge,,
        Broken,,20l9,1,,,,,,,
        Ata Rangi,Pinot Noir,2018,3,,72.50,01/06/2024,Offsite,,,
    """.trimIndent()

    private fun TestEnv.planFor(text: String) = Csv.parse(text).let { t ->
        importer.plan(session, t, ImportField.guessMapping(t.headers), ImportOptions(nzd, DateOrder.DMY, currentYear = 2026), "test.csv")
    }

    @Test
    fun planFindsErrorsAndDuplicatesWithoutChangingAnything() = withEnv {
        val plan = planFor(file)
        assertEquals(4, plan.rows.size)
        assertEquals(1, plan.failed.size)
        assertEquals(5, plan.failed.single().line) // header is line 1
        val dup = plan.rows.single { it.duplicateOfLine != null }
        assertEquals(2, dup.duplicateOfLine)
        assertEquals(RowDecision.SKIP, dup.suggested)
        assertEquals(0, search.searchCellar(session, SearchQuery()).total)
    }

    @Test
    fun commitImportsKeptRowsAndReimportSkipsThem() = withEnv {
        val result = importer.commit(session, planFor(file))
        assertEquals(3, result.importedRows)
        assertEquals(11, result.bottles)
        assertEquals(1, result.skippedRows)
        assertEquals(1, result.invalidRows)
        assertTrue(result.errors.isEmpty())
        // House > Rack A, House > Fridge and Offsite were created (Cellar already existed).
        assertEquals(setOf("Cellar", "House", "Rack A", "Fridge", "Offsite"), locations.all(session).map { it.name }.toSet())
        assertEquals(11, search.searchCellar(session, SearchQuery()).bottles)
        assertEquals(1, search.searchCellar(session, SearchQuery("birth")).total) // tags imported
        assertEquals(1, valuations.latestValues(session).size)
        // Running the same file again doesn't double the cellar.
        val again = planFor(file)
        assertTrue(again.rows.all { it.suggested == RowDecision.SKIP })
        assertEquals(0, importer.commit(session, again).importedRows)
        assertEquals(11, search.searchCellar(session, SearchQuery()).bottles)
        assertHoldingsConsistent()
    }

    @Test
    fun userCanOverrideSuggestions() = withEnv {
        val plan = planFor(file)
        val dupLine = plan.rows.single { it.duplicateOfLine != null }.row.line
        val result = importer.commit(session, plan, mapOf(dupLine to RowDecision.IMPORT))
        assertEquals(17, result.bottles)
    }

    @Test
    fun exportsRoundTripThroughCsv() = withEnv {
        importer.commit(session, planFor(file))
        val inv = Csv.parse(exporter.inventory(session))
        assertEquals(3, inv.rows.size)
        val felton = inv.rows.first { it[0] == "Felton Road" }
        assertEquals("6", felton[inv.headers.indexOf("Quantity")])
        assertEquals("95.00", felton[inv.headers.indexOf("Price per bottle")])
        assertEquals("House > Rack A", felton[inv.headers.indexOf("Location")])
        assertEquals("110.00", felton[inv.headers.indexOf("Estimated value per bottle")])
        val fin = Csv.parse(exporter.financial(session))
        assertEquals(3, fin.rows.size)
        assertEquals("570.00", fin.rows.first { it[2] == "Felton Road" }[fin.headers.indexOf("Total")])
        assertEquals(listOf("Date", "Producer"), Csv.parse(exporter.tastings(session)).headers.take(2))
        // The exported inventory can be imported into an empty cellar again.
        val reimport = TestEnv()
        try {
            val t = inv
            val p = reimport.importer.plan(reimport.session, t, ImportField.guessMapping(t.headers), ImportOptions(nzd, DateOrder.YMD, currentYear = 2026), "export.csv")
            assertEquals(0, p.failed.size, p.failed.toString())
            reimport.importer.commit(reimport.session, p)
            assertEquals(11, reimport.search.searchCellar(reimport.session, SearchQuery()).bottles)
        } finally {
            reimport.close()
        }
    }
}
