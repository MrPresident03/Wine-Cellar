package com.houseofvino.ui.format

import com.houseofvino.core.model.MovementKind
import com.houseofvino.ui.format.MovementText.Line
import kotlin.test.Test
import kotlin.test.assertEquals

class MovementTextTest {
    private fun say(kind: MovementKind, n: Int, from: String? = null, to: String? = null, ml: Int? = 750) =
        MovementText.sentence(Line(kind, n, ml, from, to))

    @Test
    fun everyKindReadsAsASentence() {
        assertEquals("Bought 12 bottles, stored in Cellar", say(MovementKind.PURCHASE, 12, to = "Cellar"))
        assertEquals("Received 1 bottle, stored in Kitchen fridge", say(MovementKind.RECEIVE, 1, to = "Kitchen fridge"))
        assertEquals("Opened 1 bottle from Rack A", say(MovementKind.CONSUME, 1, from = "Rack A"))
        assertEquals("Sold 2 magnums from Cellar", say(MovementKind.SELL, 2, from = "Cellar", ml = 1500))
        assertEquals("Gave away 1 half bottle from Cellar", say(MovementKind.GIFT_OUT, 1, from = "Cellar", ml = 375))
        assertEquals("Lost 1 bottle, broken or spoiled, in Cellar", say(MovementKind.LOSS, 1, from = "Cellar"))
        assertEquals("Recount found 2 more bottles in Cellar", say(MovementKind.ADJUST_IN, 2, to = "Cellar"))
        assertEquals("Recount found 1 fewer bottle in Rack A", say(MovementKind.ADJUST_OUT, 1, from = "Rack A"))
        assertEquals("Moved 2 bottles from Cellar to Kitchen fridge", say(MovementKind.TRANSFER, 2, from = "Cellar", to = "Kitchen fridge"))
        assertEquals("Bought 1,200 bottles", say(MovementKind.PURCHASE, 1200))
    }

    @Test
    fun reversalsNameWhatTheyUndid() {
        val opened = Line(MovementKind.CONSUME, 1, 750, "Rack A", null)
        val undo = Line(MovementKind.REVERSAL, 1, 750, null, "Rack A")
        assertEquals("Undid “Opened 1 bottle from Rack A”", MovementText.sentence(undo, opened))
        assertEquals("Corrected an earlier entry", MovementText.sentence(undo, null))
    }

    @Test
    fun unusualSizesAreBottles() {
        assertEquals("3 bottles", MovementText.units(3, 620))
        assertEquals("1 bottle", MovementText.units(1, null))
    }
}
