package com.houseofvino.data

import com.houseofvino.core.model.DrinkStatus
import com.houseofvino.core.model.MovementDraft
import com.houseofvino.core.model.MovementKind
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Decimal
import com.houseofvino.core.money.ExchangeRate
import com.houseofvino.core.money.Money
import com.houseofvino.core.search.SearchQuery
import com.houseofvino.core.search.SearchScope
import com.houseofvino.data.repo.ConversionInput
import com.houseofvino.data.repo.InvalidInputException
import com.houseofvino.data.repo.Rating
import com.houseofvino.data.repo.RatingScale
import com.houseofvino.data.repo.TastingInput
import com.houseofvino.data.repo.WishInput
import com.houseofvino.data.repo.WishStatus
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalWishlistInsightsTest {

    @Test
    fun ratingScalesNormalise() {
        assertEquals(92, RatingScale.HUNDRED.to100(Decimal.parse("92")))
        assertEquals(88, RatingScale.TWENTY.to100(Decimal.parse("17.5")))
        assertEquals(90, RatingScale.FIVE.to100(Decimal.parse("4.5")))
        assertFailsWith<IllegalArgumentException> { Rating(Decimal.parse("21"), RatingScale.TWENTY) }
    }

    @Test
    fun tastingNotesSurviveTheBottleBeingOpenedAndTheWineRemoved() = withEnv {
        val v = vintage(wine("Rippon", "Mature Vine Pinot Noir"), 2018)
        val lot = buy(v, 1)
        val opened = inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 1, cellarId, null)).movementId
        val note = tastings.add(
            session,
            TastingInput(v, LocalDate(2026, 10, 1), Rating(Decimal.parse("94"), RatingScale.HUNDRED), nose = "Wild herbs, red cherry", palate = "Silky", aromas = listOf("cherry", "thyme"), occasion = "Anniversary", lotId = lot, movementId = opened),
        )
        wines.deleteVintage(session, v) // allowed: no bottles left
        val kept = assertNotNull(tastings.note(note))
        assertEquals(94, RatingScale.HUNDRED.to100(kept.input.rating!!.value))
        assertEquals(listOf("cherry", "thyme"), kept.input.aromas)
        // Notes are searchable in their own scope.
        assertEquals(1, search.searchTastings(session, SearchQuery("thyme", SearchScope.TASTINGS)).total)
        assertFailsWith<InvalidInputException> { tastings.add(session, TastingInput(v, LocalDate(2026, 10, 1), tannin = 7)) }
    }

    @Test
    fun wishlistItemBecomesAPurchase() = withEnv {
        val item = wishlist.add(session, WishInput("Rousseau Chambertin", producer = "Armand Rousseau", wineName = "Chambertin", vintageFrom = 2010, vintageTo = 2019, desiredQuantity = 3, targetPrice = Money(900_00, nzd), maxPrice = Money(1200_00, nzd)))
        assertEquals(1, search.searchWishlist(session, SearchQuery("rousseau", SearchScope.WISHLIST)).total)
        // Bought 2 of 3: item stays open for 1 more.
        wishlist.convertToPurchase(session, item, ConversionInput(Vintage.Year(2015), 2, cellarId, unitPrice = Money(1100_00, nzd)))
        val after = wishlist.item(item)!!
        assertEquals(1, after.input.desiredQuantity)
        assertEquals(WishStatus.WANTED, after.input.status)
        val wineId = assertNotNull(after.input.wineId)
        val v2015 = assertNotNull(wines.findVintage(wineId, Vintage.Year(2015)))
        assertEquals(2, inventory.holdings(v2015.id).sumOf { it.quantity })
        assertEquals(item, inventory.lotsOf(v2015.id).single().wishlistItemId)
        // Buying the last one closes it, adding to the same wine.
        wishlist.convertToPurchase(session, item, ConversionInput(Vintage.Year(2016), 1, cellarId))
        assertEquals(WishStatus.ACQUIRED, wishlist.item(item)!!.input.status)
        assertEquals(2, wines.vintagesOf(wineId).size)
        assertFailsWith<InvalidInputException> { wishlist.add(session, WishInput("x", targetPrice = Money(200_00, nzd), maxPrice = Money(100_00, nzd))) }
    }

    @Test
    fun insightsUseRecordedPricesValuesAndRatesOnly() = withEnv {
        val eur = Currency("EUR")
        val a = vintage(wine("Felton Road", "Block 5"), 2019, drinkFrom = 2024, drinkTo = 2034)
        val b = vintage(wine("Leflaive", "Pucelles", country = "France"), 2017, drinkFrom = 2020, drinkTo = 2027)
        val c = vintage(wine("Mystery"), 2010)
        inventory.addPurchase(session, com.houseofvino.data.repo.PurchaseInput(a, 6, cellarId, unitPrice = Money(95_00, nzd)))
        inventory.addPurchase(session, com.houseofvino.data.repo.PurchaseInput(b, 2, cellarId, unitPrice = Money(250_00, eur), extraCosts = Money(40_00, eur)))
        buy(c, 1) // no price
        valuations.addValue(session, a, 750, Money(110_00, nzd), "Auction results", LocalDate(2026, 9, 1))
        val noRates = insights.compute(session, nzd)
        assertEquals(9, noRates.bottles)
        assertEquals(1, noRates.unpricedBottles)
        assertEquals(setOf(Money(570_00, nzd), Money(500_00, eur)), noRates.costHeld.toSet())
        assertEquals(listOf(Money(500_00, eur)), noRates.costHeldConverted!!.unconverted) // no EUR rate: not guessed
        assertEquals(setOf(Money(570_00, nzd), Money(540_00, eur)), noRates.spentAllTime.toSet())
        assertEquals(6, noRates.value.valuedBottles)
        assertEquals(listOf(Money(660_00, nzd)), noRates.value.totals)
        assertEquals("Auction results", noRates.value.sources.single().first)
        assertEquals(2, noRates.windows[DrinkStatus.DRINK_SOON]) // window ends 2027, clock is 2026
        assertEquals(1, noRates.windows[DrinkStatus.UNKNOWN])
        valuations.addRate(session, ExchangeRate(eur, nzd, Decimal.parse("1.9"), LocalDate(2026, 10, 1), "ECB reference rate"))
        val withRates = insights.compute(session, nzd)
        assertEquals(Money(1520_00, nzd), withRates.costHeldConverted!!.total)
        assertTrue(withRates.costHeldConverted!!.isComplete)
        // Consumption by month counts openings that weren't undone.
        val lot = inventory.lotsOf(a).single().id
        val m = inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 2, cellarId, null)).movementId
        inventory.record(session, MovementDraft(MovementKind.CONSUME, lot, 1, cellarId, null))
        inventory.reverse(session, m)
        assertEquals(1, insights.compute(session, nzd).months.sumOf { it.opened })
        assertNull(valuations.latestValues(session).firstOrNull { it.vintageId == c })
    }
}
