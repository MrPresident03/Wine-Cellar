package com.houseofvino.ui.feature.wine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.MovementDraft
import com.houseofvino.core.model.MovementKind
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Money
import com.houseofvino.data.repo.Holding
import com.houseofvino.data.repo.MovementDetails
import com.houseofvino.ui.components.Chip
import com.houseofvino.ui.components.HovTextField
import com.houseofvino.ui.components.PrimaryButton
import com.houseofvino.ui.components.QuantityStepper
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space

data class MovementSheetRequest(val kind: MovementKind, val lotId: String?, val locationId: String?)

private enum class SheetAction(val label: String, val kind: MovementKind?) {
    OPEN("Open", MovementKind.CONSUME),
    MOVE("Move", MovementKind.TRANSFER),
    SELL("Sell", MovementKind.SELL),
    GIVE("Give away", MovementKind.GIFT_OUT),
    LOSS("Broken or spoiled", MovementKind.LOSS),
    COUNT("Correct the count", null),
}

/**
 * One sheet for every way bottles leave or move. The button always says exactly what will be
 * recorded ("Open 2 bottles").
 */
@Composable
fun MovementSheet(
    page: VintagePage,
    request: MovementSheetRequest,
    onDismiss: () -> Unit,
    onRecord: (MovementDraft, MovementDetails) -> Unit,
    onCorrect: (lotId: String, locationId: String, actual: Int) -> Unit,
) {
    val c = Hov.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var action by remember { mutableStateOf(SheetAction.entries.firstOrNull { it.kind == request.kind } ?: SheetAction.OPEN) }
    var holding by remember {
        mutableStateOf(
            page.holdings.firstOrNull { it.lotId == request.lotId && it.locationId == request.locationId }
                ?: page.holdings.singleOrNull()
                ?: page.holdings.maxByOrNull { it.quantity },
        )
    }
    var quantity by remember { mutableStateOf(1) }
    var actual by remember { mutableStateOf(holding?.quantity ?: 0) }
    var destination by remember { mutableStateOf<String?>(null) }
    var price by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val home = page.lots.firstNotNullOfOrNull { it.unitPrice?.currency } ?: Currency("NZD")

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.surfaceRaised) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.ime)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.xl),
        ) {
            Text("${page.wine.producer} ${page.vintage.vintage}", style = Hov.type.heading, color = c.ink)
            Spacer(Modifier.height(Space.m))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                SheetAction.entries.forEach { a -> Chip(a.label, selected = action == a, onClick = { action = a }) }
            }
            if (page.holdings.size > 1) {
                Spacer(Modifier.height(Space.l))
                Text("From", style = Hov.type.label, color = c.inkMuted)
                page.holdings.forEach { h ->
                    HoldingOption(h, page, selected = h == holding) {
                        holding = h
                        quantity = 1
                        actual = h.quantity
                    }
                }
            }
            val h = holding
            Spacer(Modifier.height(Space.l))
            if (h == null) {
                Text("There are no bottles of this wine left.", style = Hov.type.body, color = c.inkMuted)
            } else if (action == SheetAction.COUNT) {
                Text("How many are actually at ${page.locations[h.locationId]?.name}?", style = Hov.type.label, color = c.inkMuted)
                Spacer(Modifier.height(6.dp))
                QuantityStepper(actual, { actual = it }, min = 0, label = "Actual count")
                Spacer(Modifier.height(Space.xl))
                PrimaryButton(
                    if (actual == h.quantity) "Count is right" else "Set count to $actual",
                    onClick = { onCorrect(h.lotId, h.locationId, actual) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("How many", style = Hov.type.label, color = c.inkMuted)
                Spacer(Modifier.height(6.dp))
                QuantityStepper(quantity.coerceAtMost(h.quantity), { quantity = it }, min = 1, max = h.quantity)
                Text("${h.quantity} available here", style = Hov.type.caption, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp))

                if (action == SheetAction.MOVE) {
                    Spacer(Modifier.height(Space.l))
                    Text("To", style = Hov.type.label, color = c.inkMuted)
                    Spacer(Modifier.height(6.dp))
                    val choices = page.locations.values.filter { it.id != h.locationId }.sortedBy { it.name.lowercase() }
                    if (choices.isEmpty()) {
                        Text("Add another place first (Cellar › Places).", style = Hov.type.body, color = c.inkMuted)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        choices.forEach { l -> Chip(l.name, selected = destination == l.id, onClick = { destination = l.id }) }
                    }
                }
                if (action == SheetAction.SELL) {
                    Spacer(Modifier.height(Space.l))
                    HovTextField(
                        label = "Price per bottle (${home.code}), optional",
                        value = price,
                        onValueChange = { price = it },
                        keyboardType = KeyboardType.Decimal,
                        error = if (price.isNotBlank() && Money.parse(price, home) == null) "Enter an amount like 120.00" else null,
                    )
                }
                if (action == SheetAction.SELL || action == SheetAction.GIVE) {
                    Spacer(Modifier.height(Space.l))
                    HovTextField(label = if (action == SheetAction.SELL) "Sold to, optional" else "Given to, optional", value = counterparty, onValueChange = { counterparty = it })
                }
                Spacer(Modifier.height(Space.l))
                HovTextField(label = "Note, optional", value = note, onValueChange = { note = it }, singleLine = false, minLines = 2)
                Spacer(Modifier.height(Space.xl))
                val q = quantity.coerceAtMost(h.quantity)
                val noun = "${q} bottle${if (q == 1) "" else "s"}"
                val label = when (action) {
                    SheetAction.OPEN -> "Open $noun"
                    SheetAction.MOVE -> "Move $noun"
                    SheetAction.SELL -> "Record sale of $noun"
                    SheetAction.GIVE -> "Give away $noun"
                    SheetAction.LOSS -> "Record $noun as broken or spoiled"
                    SheetAction.COUNT -> ""
                }
                val priceMoney = if (action == SheetAction.SELL && price.isNotBlank()) Money.parse(price, home) else null
                val valid = (action != SheetAction.MOVE || destination != null) && !(action == SheetAction.SELL && price.isNotBlank() && priceMoney == null)
                PrimaryButton(
                    label,
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val kind = action.kind ?: return@PrimaryButton
                        val draft = MovementDraft(
                            kind = kind,
                            lotId = h.lotId,
                            quantity = q,
                            fromLocationId = h.locationId,
                            toLocationId = if (kind == MovementKind.TRANSFER) destination else null,
                        )
                        onRecord(draft, MovementDetails(unitPrice = priceMoney, counterparty = counterparty.ifBlank { null }, note = note.ifBlank { null }))
                    },
                )
            }
        }
    }
}

@Composable
private fun HoldingOption(h: Holding, page: VintagePage, selected: Boolean, onClick: () -> Unit) {
    val c = Hov.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable(role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) c.burgundy else c.hairline), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) c.onBurgundy else c.surfaceRaised))
        }
        Spacer(Modifier.width(Space.m))
        Text("${h.quantity} × ${BottleFormat.label(h.formatMl)} at ${page.locations[h.locationId]?.name ?: h.locationName}", style = Hov.type.body, color = c.ink)
    }
}
