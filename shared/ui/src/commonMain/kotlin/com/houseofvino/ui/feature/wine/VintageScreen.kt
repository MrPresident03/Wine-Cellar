package com.houseofvino.ui.feature.wine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.DrinkStatus
import com.houseofvino.core.model.Grapes
import com.houseofvino.core.model.MovementKind
import com.houseofvino.core.model.Vintage
import com.houseofvino.data.repo.MovementRecord
import com.houseofvino.ui.LocalSnackbar
import com.houseofvino.ui.app.LocalGraph
import com.houseofvino.ui.components.BottleSilhouette
import com.houseofvino.ui.components.Fact
import com.houseofvino.ui.components.Hairline
import com.houseofvino.ui.components.HovIconButton
import com.houseofvino.ui.components.HovIcons
import com.houseofvino.ui.components.ListPadding
import com.houseofvino.ui.components.LoadingRows
import com.houseofvino.ui.components.MessageState
import com.houseofvino.ui.components.PrimaryButton
import com.houseofvino.ui.components.ScreenHeader
import com.houseofvino.ui.components.SecondaryButton
import com.houseofvino.ui.components.SectionHeading
import com.houseofvino.ui.components.StatusMark
import com.houseofvino.ui.components.TextAction
import com.houseofvino.ui.components.VintageFigure
import com.houseofvino.ui.format.Fmt
import com.houseofvino.ui.format.MovementText
import com.houseofvino.ui.nav.AddWinePrefill
import com.houseofvino.ui.nav.LocalNavigator
import com.houseofvino.ui.nav.Route
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space

@Composable
fun VintageScreen(vintageId: String) {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val vm = viewModel(key = "vintage-$vintageId") { VintageViewModel(graph, vintageId) }
    val ui by vm.state.collectAsState()
    var sheet by remember { mutableStateOf<MovementSheetRequest?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            val result = snackbar.showSnackbar(e.message, actionLabel = if (e.undoMovementId != null) "Undo" else null, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed && e.undoMovementId != null) vm.undo(e.undoMovementId)
        }
    }

    Column(Modifier.fillMaxSize()) {
        var menu by remember { mutableStateOf(false) }
        ScreenHeader(
            title = "",
            onBack = { nav.pop() },
            actions = {
                if (ui is VintageUi.Ready) {
                    HovIconButton(HovIcons.Edit, "Edit wine") { nav.push(Route.EditWine(vintageId)) }
                    Box {
                        HovIconButton(HovIcons.More, "More actions") { menu = true }
                        DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = Hov.colors.surfaceRaised) {
                            DropdownMenuItem(text = { Text("Add another vintage", style = Hov.type.body, color = Hov.colors.ink) }, onClick = {
                                menu = false
                                (ui as? VintageUi.Ready)?.page?.let { nav.push(Route.AddWine(AddWinePrefill(wineId = it.wine.id, producer = it.wine.producer, name = it.wine.name))) }
                            })
                            DropdownMenuItem(text = { Text("Remove from cellar", style = Hov.type.body, color = Hov.colors.danger) }, onClick = {
                                menu = false
                                confirmDelete = true
                            })
                        }
                    }
                }
            },
        )
        when (val s = ui) {
            VintageUi.Loading -> LoadingRows(4)
            VintageUi.Gone -> MessageState("This wine has been removed", "It may have been removed on another device.", primary = "Back to cellar" to { nav.pop() })
            is VintageUi.Failed -> MessageState("Couldn't load this wine", s.message, icon = HovIcons.Alert)
            is VintageUi.Ready -> VintageContent(
                s.page,
                onAction = { kind, holding -> sheet = MovementSheetRequest(kind, holding?.lotId, holding?.locationId) },
                onUndo = vm::undo,
                onOpenVintage = { nav.push(Route.Vintage(it)) },
                onAddBottles = { nav.push(Route.AddWine(AddWinePrefill(wineId = s.page.wine.id, producer = s.page.wine.producer, name = s.page.wine.name, vintage = s.page.vintage.vintage.toString()))) },
                onHistory = { nav.push(Route.History(vintageId)) },
            )
        }
    }

    val ready = ui as? VintageUi.Ready
    val request = sheet
    if (ready != null && request != null) {
        MovementSheet(
            page = ready.page,
            request = request,
            onDismiss = { sheet = null },
            onRecord = { draft, details -> vm.record(draft, details) { sheet = null } },
            onCorrect = { lot, loc, actual -> vm.correctCount(lot, loc, actual) { sheet = null } },
        )
    }
    if (confirmDelete && ready != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Hov.colors.surfaceRaised,
            title = { Text("Remove ${ready.page.wine.producer} ${ready.page.vintage.vintage}?", style = Hov.type.heading, color = Hov.colors.ink) },
            text = {
                Text(
                    if (ready.page.bottles > 0) "There are still ${Fmt.bottles(ready.page.bottles)} in the cellar. Record what happened to them first."
                    else "It disappears from the cellar. Its history and tasting notes are kept.",
                    style = Hov.type.body, color = Hov.colors.inkMuted,
                )
            },
            confirmButton = {
                if (ready.page.bottles == 0) TextAction("Remove", { confirmDelete = false; vm.delete { nav.pop() } }, color = Hov.colors.danger)
            },
            dismissButton = { TextAction(if (ready.page.bottles > 0) "OK" else "Keep", { confirmDelete = false }, color = Hov.colors.ink) },
        )
    }
}

@Composable
private fun VintageContent(
    page: VintagePage,
    onAction: (MovementKind, com.houseofvino.data.repo.Holding?) -> Unit,
    onUndo: (String) -> Unit,
    onOpenVintage: (String) -> Unit,
    onAddBottles: () -> Unit,
    onHistory: () -> Unit,
) {
    val c = Hov.colors
    val wine = page.wine
    val vintage = page.vintage
    val status = vintage.window?.status(page.currentYear) ?: DrinkStatus.UNKNOWN
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(width = 96.dp, height = 128.dp).clip(RoundedCornerShape(2.dp)).background(c.surfaceRaised).border(1.dp, c.hairline, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) { BottleSilhouette(wine.style, height = 104.dp) }
                Spacer(Modifier.width(Space.l))
                Column(Modifier.weight(1f)) {
                    VintageFigure(vintage.vintage)
                    Text(wine.producer, style = Hov.type.heading, color = c.ink, modifier = Modifier.semantics { heading() })
                    val cuvee = listOfNotNull(wine.name.ifBlank { null }, wine.classification).joinToString(" ")
                    if (cuvee.isNotEmpty()) Text(cuvee, style = Hov.type.cuvee, color = c.ink)
                    Spacer(Modifier.height(Space.xs))
                    if (wine.place.isNotEmpty()) Text(wine.place, style = Hov.type.detail, color = c.inkMuted)
                    wine.style?.let { Text(it.label, style = Hov.type.detail, color = c.inkMuted) }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                val window = vintage.window
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (window == null) "No drinking window yet" else "Drink ${window.label()}",
                        style = Hov.type.body, color = if (window == null) c.inkMuted else c.ink,
                    )
                    Spacer(Modifier.width(Space.m))
                    StatusMark(status)
                }
                if (window != null) Text("Source: ${window.source.label}${window.note?.let { " — $it" } ?: ""}", style = Hov.type.caption, color = c.inkMuted)
            }
        }
        item {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (page.bottles > 0) {
                    PrimaryButton("Open a bottle", { onAction(MovementKind.CONSUME, page.holdings.singleOrNull()) }, icon = HovIcons.Glass)
                    SecondaryButton("Move", { onAction(MovementKind.TRANSFER, page.holdings.singleOrNull()) }, icon = HovIcons.Move)
                    SecondaryButton("Sell or give", { onAction(MovementKind.SELL, page.holdings.singleOrNull()) })
                }
                SecondaryButton("Add bottles", onAddBottles, icon = HovIcons.Add)
            }
        }
        item { SectionHeading("In the cellar") }
        if (page.holdings.isEmpty()) {
            item { Text("None left. Purchases and history are kept below.", style = Hov.type.body, color = c.inkMuted, modifier = Modifier.padding(horizontal = Space.gutter)) }
        }
        items(page.holdings, key = { "${it.lotId}@${it.locationId}" }) { h ->
            Column(Modifier.fillMaxWidth().clickable { onAction(MovementKind.CONSUME, h) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                    Text("${h.quantity} × ${BottleFormat.label(h.formatMl)}", style = Hov.type.figure, color = c.ink, modifier = Modifier.width(120.dp))
                    Text(placePath(h.locationId, page), style = Hov.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    Icon(HovIcons.ChevronRight, null, tint = c.inkFaint, modifier = Modifier.size(20.dp))
                }
                Hairline(inset = Space.gutter)
            }
        }
        item { SectionHeading("Purchases") }
        items(page.lots, key = { it.id }) { lot ->
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s)) {
                val parts = listOfNotNull(
                    "${lot.quantityAcquired} × ${BottleFormat.label(lot.formatMl)}",
                    lot.acquiredOn?.let { Fmt.date(it) },
                    lot.source,
                )
                Text(parts.joinToString(", "), style = Hov.type.body, color = c.ink)
                lot.unitPrice?.let { Text("${Fmt.money(it, it.currency)} a bottle${lot.extraCosts?.let { e -> " plus ${Fmt.money(e, e.currency)} costs" } ?: ""}", style = Hov.type.detail, color = c.inkMuted) }
                if (lot.caseType.code != "none") Text(lot.caseType.label + (lot.caseSize?.let { " of $it" } ?: ""), style = Hov.type.detail, color = c.inkMuted)
            }
            Hairline(inset = Space.gutter)
        }
        item {
            SectionHeading("History") {
                if (page.history.size > 5) TextAction("See all", onHistory)
            }
        }
        items(page.history.take(5), key = { it.id }) { m -> MovementLine(m, page, canUndo = m.kind != MovementKind.REVERSAL && m.id !in page.reversed && m.syncState != "rejected", onUndo = onUndo) }
        if (page.otherVintages.isNotEmpty()) {
            item { SectionHeading("Other vintages") }
            items(page.otherVintages, key = { it.first.id }) { (v, n) ->
                Column(Modifier.fillMaxWidth().clickable { onOpenVintage(v.id) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                        VintageFigure(v.vintage, Modifier.width(Space.vintageColumn), small = true)
                        Text(if (n == 0) "None left" else Fmt.bottles(n), style = Hov.type.figure, color = if (n == 0) c.inkFaint else c.ink, modifier = Modifier.weight(1f))
                        Icon(HovIcons.ChevronRight, null, tint = c.inkFaint, modifier = Modifier.size(20.dp))
                    }
                    Hairline(inset = Space.gutter)
                }
            }
        }
        item {
            SectionHeading("About this wine")
            Fact("Grapes", Grapes.display(wine.grapes).ifBlank { null })
            Fact("Appellation", wine.appellation)
            Fact("Region", listOfNotNull(wine.region, wine.subRegion).joinToString(", ").ifBlank { null })
            Fact("Country", wine.country)
            Fact("Alcohol", vintage.alcoholPct?.let { "${it.stripTrailingZeros()}%" })
            Fact("Notes", listOfNotNull(wine.notes, vintage.notes).joinToString("\n\n").ifBlank { null })
        }
    }
}

@Composable
fun MovementLine(m: MovementRecord, page: VintagePage, canUndo: Boolean, onUndo: (String) -> Unit) {
    val c = Hov.colors
    val graph = LocalGraph.current
    val sentence = remember(m, page) {
        fun line(r: MovementRecord) = MovementText.Line(
            kind = r.kind,
            quantity = r.quantity,
            formatMl = page.lots.firstOrNull { it.id == r.lotId }?.formatMl,
            from = r.fromLocationId?.let { placeName(it, page) },
            to = r.toLocationId?.let { placeName(it, page) },
        )
        MovementText.sentence(line(m), m.reversesId?.let { id -> page.history.firstOrNull { it.id == id } }?.let(::line))
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sentence, style = Hov.type.body, color = if (m.syncState == "rejected") c.inkFaint else c.ink)
                val meta = listOfNotNull(
                    Fmt.date(m.occurredAt, graph.clock.timeZone()),
                    m.price?.let { "${Fmt.money(it, it.currency)} each" },
                    m.counterparty,
                    if (m.id in page.reversed) "undone" else null,
                    if (m.syncState == "rejected") "not accepted: ${m.rejectReason ?: "conflict"}" else null,
                    if (m.syncState == "pending" && graph.syncEnabled) "not synced yet" else null,
                )
                Text(meta.joinToString(", "), style = Hov.type.caption, color = if (m.syncState == "rejected") c.danger else c.inkMuted)
            }
            if (canUndo) TextAction("Undo", { onUndo(m.id) })
        }
        Hairline(inset = Space.gutter)
    }
}

private fun placeName(id: String, page: VintagePage) = page.locations[id]?.name ?: "a removed place"

private fun placePath(id: String, page: VintagePage): String {
    val names = mutableListOf<String>()
    var cur = page.locations[id]
    var guard = 0
    while (cur != null && guard++ < 16) {
        names += cur.name
        cur = cur.parentId?.let { page.locations[it] }
    }
    return names.reversed().joinToString(" › ").ifEmpty { "a removed place" }
}

@Suppress("unused")
private fun vintageLabel(v: Vintage) = v.toString()
