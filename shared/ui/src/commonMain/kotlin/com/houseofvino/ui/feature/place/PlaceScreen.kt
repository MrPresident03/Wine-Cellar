package com.houseofvino.ui.feature.place

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houseofvino.core.model.Vintage
import com.houseofvino.data.repo.Slot
import com.houseofvino.ui.LocalSnackbar
import com.houseofvino.ui.app.LocalGraph
import com.houseofvino.ui.components.Hairline
import com.houseofvino.ui.components.HovIconButton
import com.houseofvino.ui.components.HovIcons
import com.houseofvino.ui.components.ListPadding
import com.houseofvino.ui.components.LoadingRows
import com.houseofvino.ui.components.MessageState
import com.houseofvino.ui.components.ScreenHeader
import com.houseofvino.ui.components.SectionHeading
import com.houseofvino.ui.components.TextAction
import com.houseofvino.ui.components.VintageRow
import com.houseofvino.ui.components.styleColor
import com.houseofvino.ui.format.Fmt
import com.houseofvino.ui.nav.AddWinePrefill
import com.houseofvino.ui.nav.LocalNavigator
import com.houseofvino.ui.nav.Route
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space

@Composable
fun PlaceScreen(placeId: String) {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val vm = viewModel(key = "place-$placeId") { PlaceViewModel(graph, placeId) }
    val ui by vm.state.collectAsState()
    var emptySlot by remember { mutableStateOf<Slot?>(null) }
    var moving by remember { mutableStateOf<GridCell?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it.message) } }

    Column(Modifier.fillMaxSize()) {
        val ready = (ui as? PlaceUi.Ready)?.page
        ScreenHeader(
            title = ready?.place?.name ?: "",
            summary = ready?.let { p ->
                val path = p.path.dropLast(1).joinToString(" › ") { it.name }
                listOfNotNull(path.ifBlank { null }, Fmt.bottles(p.totalBottles)).joinToString(" — ")
            },
            onBack = { nav.pop() },
            actions = {
                if (ready != null) {
                    HovIconButton(HovIcons.Add, "Add wine here") { nav.push(Route.AddWine(AddWinePrefill(locationId = placeId))) }
                    HovIconButton(HovIcons.Edit, "Edit place") { nav.push(Route.EditPlace(placeId)) }
                    HovIconButton(HovIcons.Trash, "Remove place") { confirmDelete = true }
                }
            },
        )
        when (val s = ui) {
            PlaceUi.Loading -> LoadingRows(5)
            PlaceUi.Gone -> MessageState("This place has been removed", null, primary = "Back" to { nav.pop() })
            is PlaceUi.Failed -> MessageState("Couldn't load this place", s.message, icon = HovIcons.Alert)
            is PlaceUi.Ready -> LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                val p = s.page
                if (p.place.hasGrid) {
                    item {
                        SectionHeading("Rack")
                        if (moving != null) {
                            Text("Choose an empty slot for ${moving!!.producer} ${moving!!.vintage}.", style = Hov.type.detail, color = Hov.colors.burgundyBright, modifier = Modifier.padding(horizontal = Space.gutter))
                        }
                        RackGrid(
                            rows = p.place.gridRows!!, cols = p.place.gridCols!!, cells = p.grid,
                            onCell = { slot, cell ->
                                val m = moving
                                when {
                                    m != null && cell == null -> { vm.moveInGrid(m.positionId, slot); moving = null }
                                    m != null -> moving = null
                                    cell != null -> nav.push(Route.Vintage(cell.vintageId))
                                    else -> emptySlot = slot
                                }
                            },
                            onLongCell = { _, cell -> if (cell != null) moving = cell },
                        )
                        Text(
                            "Tap a bottle to open its page. Press and hold to move it to another slot.",
                            style = Hov.type.caption, color = Hov.colors.inkMuted, modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.s),
                        )
                        if (p.unplaced.isNotEmpty()) {
                            Text("${Fmt.bottles(p.unplaced.sumOf { it.unplaced })} here without a slot. Tap an empty slot to place one.", style = Hov.type.detail, color = Hov.colors.ink, modifier = Modifier.padding(horizontal = Space.gutter))
                        }
                    }
                }
                item {
                    SectionHeading("Places inside") {
                        TextAction("Add a place", { nav.push(Route.EditPlace(parentId = placeId)) })
                    }
                    if (p.children.isEmpty()) {
                        Text(
                            "Shelves, boxes or fridges within ${p.place.name}, if you want to be that precise.",
                            style = Hov.type.detail, color = Hov.colors.inkMuted, modifier = Modifier.padding(horizontal = Space.gutter),
                        )
                    }
                }
                if (p.children.isNotEmpty()) {
                    items(p.children, key = { it.first.id }) { (child, n) ->
                        Column(Modifier.fillMaxWidth().clickable { nav.push(Route.Place(child.id)) }) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                                Text(child.name, style = Hov.type.body, color = Hov.colors.ink, modifier = Modifier.weight(1f))
                                Text(Fmt.bottles(n), style = Hov.type.figure, color = Hov.colors.inkMuted)
                            }
                            Hairline(inset = Space.gutter)
                        }
                    }
                }
                item { SectionHeading("Wines") }
                if (p.wines.isEmpty()) {
                    item { Text("Nothing here yet.", style = Hov.type.body, color = Hov.colors.inkMuted, modifier = Modifier.padding(horizontal = Space.gutter)) }
                }
                items(p.wines, key = { it.vintageId }) { row -> VintageRow(row, p.currentYear, onClick = { nav.push(Route.Vintage(row.vintageId)) }) }
            }
        }
    }

    val ready = (ui as? PlaceUi.Ready)?.page
    val slot = emptySlot
    if (slot != null && ready != null) {
        ModalBottomSheet(onDismissRequest = { emptySlot = null }, containerColor = Hov.colors.surfaceRaised) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter).padding(bottom = Space.xl)) {
                Text("Slot ${slot.row + 1}–${slot.col + 1}", style = Hov.type.heading, color = Hov.colors.ink)
                Spacer(Modifier.height(Space.s))
                if (ready.unplaced.isEmpty()) {
                    Text("Every bottle here already has a slot. Add or move bottles to this place first.", style = Hov.type.body, color = Hov.colors.inkMuted)
                } else {
                    Text("Put a bottle here:", style = Hov.type.label, color = Hov.colors.inkMuted)
                    ready.unplaced.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.placeAt(u.lotId, slot); emptySlot = null }.padding(vertical = Space.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(u.vintage.toString(), style = Hov.type.vintageSmall, color = Hov.colors.ink, modifier = Modifier.width(Space.vintageColumn))
                            Text("${u.producer} ${u.name}".trim(), style = Hov.type.body, color = Hov.colors.ink, modifier = Modifier.weight(1f))
                            Text("${u.unplaced} unplaced", style = Hov.type.caption, color = Hov.colors.inkMuted)
                        }
                        Hairline()
                    }
                }
            }
        }
    }
    if (confirmDelete && ready != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Hov.colors.surfaceRaised,
            title = { Text("Remove ${ready.place.name}?", style = Hov.type.heading, color = Hov.colors.ink) },
            text = { Text(if (ready.totalBottles > 0) "Move its ${Fmt.bottles(ready.totalBottles)} somewhere else first." else "Only empty places can be removed. History that mentions it is kept.", style = Hov.type.body, color = Hov.colors.inkMuted) },
            confirmButton = { if (ready.totalBottles == 0) TextAction("Remove", { confirmDelete = false; vm.delete { nav.pop() } }, color = Hov.colors.danger) },
            dismissButton = { TextAction(if (ready.totalBottles > 0) "OK" else "Keep", { confirmDelete = false }, color = Hov.colors.ink) },
        )
    }
}

/** The rack as it hangs on the wall: one square per slot, filled bottles in their style's colour. */
@Composable
fun RackGrid(rows: Int, cols: Int, cells: Map<Pair<Int, Int>, GridCell>, onCell: (Slot, GridCell?) -> Unit, onLongCell: (Slot, GridCell?) -> Unit) {
    val c = Hov.colors
    Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.gutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.width(22.dp))
            repeat(cols) { col -> Text("${col + 1}", style = Hov.type.caption.copy(fontSize = 10.sp), color = c.inkFaint, textAlign = TextAlign.Center, modifier = Modifier.width(36.dp)) }
        }
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${row + 1}", style = Hov.type.caption.copy(fontSize = 10.sp), color = c.inkFaint, modifier = Modifier.width(22.dp))
                repeat(cols) { col ->
                    val cell = cells[row to col]
                    val fill = if (cell != null) styleColor(cell.style) else c.surface
                    val label = when (val v = cell?.vintage) {
                        is Vintage.Year -> "'" + (v.year % 100).toString().padStart(2, '0')
                        Vintage.NonVintage -> "NV"
                        else -> if (cell != null) "?" else ""
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(fill)
                            .border(1.dp, c.hairline, RoundedCornerShape(18.dp))
                            .combinedClickable(onClick = { onCell(Slot(row, col), cell) }, onLongClick = { onLongCell(Slot(row, col), cell) })
                            .semantics {
                                contentDescription = if (cell == null) "Row ${row + 1}, slot ${col + 1}, empty"
                                else "Row ${row + 1}, slot ${col + 1}, ${cell.producer} ${cell.name} ${cell.vintage}"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = Hov.type.caption.copy(fontSize = 11.sp), color = if (fill.luminance() > 0.35f) Color(0xFF241E1B) else Color(0xFFF8EFEF))
                    }
                }
            }
        }
    }
}
