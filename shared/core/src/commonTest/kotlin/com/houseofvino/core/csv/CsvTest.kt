package com.houseofvino.core.csv

import com.houseofvino.core.model.Vintage
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CsvTest {
    @Test
    fun parsesQuotesNewlinesAndBom() {
        val t = Csv.parse("﻿Producer,Wine,Notes\r\n\"Domaine \"\"Ponsot\"\"\",Clos de la Roche,\"line one\nline two\"\r\nKrug,\"Grande Cuvée, 171ème\",\r\n\r\n")
        assertEquals(listOf("Producer", "Wine", "Notes"), t.headers)
        assertEquals(2, t.rows.size)
        assertEquals("Domaine \"Ponsot\"", t.cell(0, 0))
        assertEquals("line one\nline two", t.cell(0, 2))
        assertEquals("Grande Cuvée, 171ème", t.cell(1, 1))
        assertEquals("", t.cell(1, 2))
    }

    @Test
    fun detectsDelimiters() {
        assertEquals(';', Csv.parse("a;b;c\n1;2,5;3\n").delimiter)
        assertEquals('\t', Csv.parse("a\tb\n1\t2\n").delimiter)
        assertEquals(',', Csv.parse("a,b\n1,2\n").delimiter)
    }

    @Test
    fun rejectsUnclosedQuotes() {
        assertFailsWith<CsvFormatException> { Csv.parse("a,b\n\"oops,2\n") }
        assertFailsWith<CsvFormatException> { Csv.parse("") }
    }

    @Test
    fun writesSafeCsvThatRoundTrips() {
        val out = Csv.write(listOf("A", "B"), listOf(listOf("=HYPERLINK(\"x\")", "-12.50"), listOf("a,b", null), listOf("@cmd", "line\nbreak")))
        val back = Csv.parse(out)
        assertEquals("'=HYPERLINK(\"x\")", back.cell(0, 0)) // formula neutralised
        assertEquals("-12.50", back.cell(0, 1)) // numbers untouched
        assertEquals("a,b", back.cell(1, 0))
        assertEquals("'@cmd", back.cell(2, 0))
        assertEquals("line\nbreak", back.cell(2, 1))
    }

    @Test
    fun decodesCommonEncodings() {
        assertEquals("Château", TextDecoding.decode("Château".encodeToByteArray()).text)
        val cp1252 = byteArrayOf(0x43, 0x68, 0x61, 0x74, 0xE2.toByte(), 0x61, 0x75, 0x20, 0x80.toByte())
        val d = TextDecoding.decode(cp1252)
        assertEquals(TextDecoding.Encoding.WINDOWS1252, d.encoding)
        assertEquals("Chatâau €", d.text)
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41, 0x00, 0x42, 0x00)
        assertEquals("AB", TextDecoding.decode(utf16).text)
    }

    @Test
    fun guessesColumnsFromOtherApps() {
        // CellarTracker-style and generic headers.
        val m = ImportField.guessMapping(listOf("iWine", "Vintage", "Wine", "Producer", "Varietal", "Country", "Region", "Appellation", "Location", "Bin", "Size", "Quantity", "Price", "Currency", "BeginConsume", "EndConsume", "Valuation"))
        assertEquals(ImportField.VINTAGE, m[1])
        assertEquals(ImportField.WINE_NAME, m[2])
        assertEquals(ImportField.PRODUCER, m[3])
        assertEquals(ImportField.GRAPES, m[4])
        assertEquals(ImportField.BIN, m[9])
        assertEquals(ImportField.FORMAT, m[10])
        assertEquals(ImportField.QUANTITY, m[11])
        assertEquals(ImportField.DRINK_FROM, m[14])
        assertEquals(ImportField.DRINK_TO, m[15])
        assertEquals(ImportField.VALUE, m[16])
        assertNull(m[0])
        val generic = ImportField.guessMapping(listOf("Winery", "Wine name", "Year", "Qty", "Purchase Price (NZD)", "Date purchased", "Merchant"))
        assertEquals(listOf(ImportField.PRODUCER, ImportField.WINE_NAME, ImportField.VINTAGE, ImportField.QUANTITY, ImportField.PRICE, ImportField.PURCHASE_DATE, ImportField.SOURCE), (0..6).map { generic[it] })
    }

    private val options = ImportOptions(Currency("NZD"), DateOrder.DMY, currentYear = 2026)

    @Test
    fun parsesRowsAndReportsProblemsPerRow() {
        val table = Csv.parse(
            """
            Producer,Wine,Vintage,Qty,Size,Price,Date,Location,Bin
            Felton Road,Block 5,2019,6,750ml,"95.00",12/03/2024,Cellar,Rack A
            Krug,Grande Cuvée,NV,2,1.5L,,2024-05-01,,
            Bad Row,,20l9,two,huge,abc,31/02/2024,,
            "",Orphan wine,2018,1,,,,,
            Château Margaux,2015 Château Margaux,2015,1,,"1.250,00",,Cellar,
            """.trimIndent(),
        )
        val m = ImportField.guessMapping(table.headers)
        val rows = ImportRowParser.parseAll(table, m, options)
        val first = assertIs<ParsedRow.Ok>(rows[0]).row
        assertEquals(Vintage.Year(2019), first.vintage)
        assertEquals(6, first.quantity)
        assertEquals(Money(95_00, Currency("NZD")), first.unitPrice)
        assertEquals(LocalDate(2024, 3, 12), first.acquiredOn)
        assertEquals(listOf("Cellar", "Rack A"), first.locationPath)
        val krug = assertIs<ParsedRow.Ok>(rows[1]).row
        assertEquals(Vintage.NonVintage, krug.vintage)
        assertEquals(1500, krug.formatMl)
        assertEquals(listOf("Cellar"), krug.locationPath) // default place
        val bad = assertIs<ParsedRow.Failed>(rows[2])
        assertEquals(setOf(ImportField.VINTAGE, ImportField.QUANTITY, ImportField.FORMAT, ImportField.PRICE, ImportField.PURCHASE_DATE), bad.problems.mapNotNull { it.field }.toSet())
        val orphan = assertIs<ParsedRow.Ok>(rows[3])
        assertEquals("Orphan wine", orphan.row.producer)
        assertTrue(orphan.warnings.isNotEmpty())
        val margaux = assertIs<ParsedRow.Ok>(rows[4]).row
        assertEquals("", margaux.name) // "2015 Château Margaux" stripped of vintage and producer
        assertEquals(Money(1250_00, Currency("NZD")), margaux.unitPrice)
        assertEquals(5, table.rows.size)
    }

    @Test
    fun flexibleDates() {
        assertEquals(LocalDate(2024, 3, 12), FlexibleDate.parse("12/03/2024", DateOrder.DMY))
        assertEquals(LocalDate(2024, 12, 3), FlexibleDate.parse("12/03/2024", DateOrder.MDY))
        assertEquals(LocalDate(2024, 3, 12), FlexibleDate.parse("2024-03-12T10:00:00", DateOrder.MDY))
        assertEquals(LocalDate(2024, 3, 12), FlexibleDate.parse("12 Mar 2024", DateOrder.MDY))
        assertEquals(LocalDate(2024, 3, 12), FlexibleDate.parse("March 12, 2024", DateOrder.DMY))
        assertEquals(LocalDate(2024, 3, 12), FlexibleDate.parse("12.03.24", DateOrder.DMY))
        assertEquals(LocalDate(2024, 3, 12), FlexibleDate.parse("45363", DateOrder.DMY)) // Excel serial
        assertNull(FlexibleDate.parse("31/02/2024", DateOrder.DMY))
        assertNull(FlexibleDate.parse("soon", DateOrder.DMY))
    }
}
