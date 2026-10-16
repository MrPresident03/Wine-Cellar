package com.houseofvino.data

import com.houseofvino.core.model.Vintage
import com.houseofvino.data.repo.DuplicateVintageException
import com.houseofvino.data.repo.DuplicateWineException
import com.houseofvino.data.repo.InvalidInputException
import com.houseofvino.data.repo.StockStillHeldException
import com.houseofvino.data.repo.VintageInput
import com.houseofvino.data.repo.VintageMismatchException
import com.houseofvino.data.repo.WineInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WineRepositoryTest {

    @Test
    fun createsWineAndSuggestsDuplicates() = withEnv {
        val id = wines.createWine(session, WineInput("  Château  Margaux ", "Grand Vin"))
        assertEquals("Château Margaux", wines.wine(id)!!.producer)
        // Same identity ignoring case and accents -> ask first.
        val e = assertFailsWith<DuplicateWineException> { wines.createWine(session, WineInput("chateau margaux", "grand vin")) }
        assertEquals(listOf(id), e.candidates.map { it.id })
        // Allowed when the user confirms they're different.
        val other = wines.createWine(session, WineInput("chateau margaux", "grand vin"), allowDuplicate = true)
        assertNotEquals(id, other)
    }

    @Test
    fun rejectsBlankProducer() = withEnv {
        val e = assertFailsWith<InvalidInputException> { wines.createWine(session, WineInput("   ")) }
        assertEquals("producer", e.field)
    }

    @Test
    fun vintagesAreDistinctAndReused() = withEnv {
        val w = wine("Penfolds", "Grange")
        val v2010 = vintage(w, 2010)
        val nv = vintage(w, null)
        assertFailsWith<DuplicateVintageException> { vintage(w, 2010) }
        assertEquals(v2010, wines.vintageFor(session, w, VintageInput(Vintage.Year(2010))))
        val v2012 = wines.vintageFor(session, w, VintageInput(Vintage.Year(2012)))
        assertEquals(3, wines.vintagesOf(w).size)
        assertTrue(setOf(v2010, nv, v2012).size == 3)
    }

    @Test
    fun windowValidation() = withEnv {
        val w = wine("Penfolds", "Grange")
        assertFailsWith<InvalidInputException> { wines.addVintage(session, w, VintageInput(Vintage.Year(2015), drinkFrom = 2030, drinkTo = 2025)) }
    }

    @Test
    fun differentVintagesNeedExplicitConfirmationToMerge() = withEnv {
        val w = wine("Rousseau", "Chambertin")
        val a = vintage(w, 2015)
        val b = vintage(w, 2016)
        buy(b, 3)
        assertFailsWith<VintageMismatchException> { wines.mergeVintages(session, a, b) }
        // Nothing moved.
        assertEquals(0, inventory.holdings(a).sumOf { it.quantity })
        wines.mergeVintages(session, a, b, confirmDifferentVintages = true)
        assertEquals(3, inventory.holdings(a).sumOf { it.quantity })
        assertTrue(wines.vintage(b)!!.deleted)
    }

    @Test
    fun mergingWinesMergesMatchingVintagesOnly() = withEnv {
        val keep = wine("Ponsot", "Clos de la Roche")
        val dupe = wine("Domaine Ponsot", "Clos de la Roche")
        val k15 = vintage(keep, 2015)
        val d15 = vintage(dupe, 2015)
        val d16 = vintage(dupe, 2016)
        buy(k15, 1)
        buy(d15, 2)
        buy(d16, 4)
        wines.mergeWines(session, keep, dupe)
        val vintages = wines.vintagesOf(keep)
        assertEquals(listOf(2015, 2016), vintages.map { it.vintage.yearOrNull })
        assertEquals(3, inventory.holdings(k15).sumOf { it.quantity })
        assertEquals(d16, vintages.last().id) // moved, not merged
        assertTrue(wines.wine(dupe)!!.deleted)
    }

    @Test
    fun cannotDeleteWhileBottlesRemain() = withEnv {
        val w = wine("Krug", "Grande Cuvée")
        val v = vintage(w, null)
        buy(v, 2)
        assertFailsWith<StockStillHeldException> { wines.deleteVintage(session, v) }
        assertFailsWith<StockStillHeldException> { wines.deleteWine(session, w) }
    }

    @Test
    fun producerSuggestionsArePrefixMatchesOrderedByUse() = withEnv {
        wine("Domaine Leflaive", "Puligny")
        wine("Domaine Leflaive", "Bâtard")
        wine("Domaine Leroy")
        wine("Dom Pérignon")
        assertEquals(listOf("Domaine Leflaive", "Domaine Leroy"), wines.producerSuggestions(session, "domaine"))
        assertEquals(emptyList(), wines.producerSuggestions(session, "100%"))
    }
}
