package com.houseofvino.ui.feature.cellar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houseofvino.core.search.SearchSort
import com.houseofvino.data.repo.LocationNode
import com.houseofvino.ui.app.LocalGraph
import com.houseofvino.ui.components.Chip
import com.houseofvino.ui.components.Hairline
import com.houseofvino.ui.components.HovIconButton
import com.houseofvino.ui.components.HovIcons
import com.houseofvino.ui.components.ListPadding
import com.houseofvino.ui.components.LoadingRows
import com.houseofvino.ui.components.MessageState
import com.houseofvino.ui.components.ScreenHeader
import com.houseofvino.ui.components.VintageRow
import com.houseofvino.ui.format.Fmt
import com.houseofvino.ui.nav.LocalNavigator
import com.houseofvino.ui.nav.Route
import com.houseofvino.ui.nav.Tab
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space

@Composable
fun CellarScreen() {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val vm = viewModel { CellarViewModel(graph) }
    val state by vm.state.collectAsState()
    val page = state.page
    val summary = page?.let {
        if (it.total == 0) null else "${Fmt.bottles(it.bottles)} in ${Fmt.count(it.total, "wine")}"
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Cellar",
            summary = summary,
            actions = {
                HovIconButton(HovIcons.Search, "Search the cellar") { nav.select(Tab.SEARCH) }
                HovIconButton(HovIcons.Add, "Add wine") { nav.push(Route.AddWine()) }
                HovIconButton(HovIcons.Person, "Settings") { nav.push(Route.Settings) }
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("Wines", selected = state.view == CellarView.WINES, onClick = { vm.setView(CellarView.WINES) })
            Chip("Places", selected = state.view == CellarView.PLACES, onClick = { vm.setView(CellarView.PLACES) })
            Spacer(Modifier.weight(1f))
            if (state.view == CellarView.WINES) SortMenu(state.sort, vm::setSort)
        }
        Hairline()
        when {
            state.error != null -> MessageState("Couldn't read the cellar", state.error, icon = HovIcons.Alert)
            state.loading -> LoadingRows()
            state.view == CellarView.PLACES -> PlacesList(state.places, onOpen = { nav.push(Route.Place(it)) }, onAdd = { nav.push(Route.EditPlace()) })
            page == null || page.total == 0 -> MessageState(
                title = "No wines yet",
                body = "Add the bottles you have. Each purchase keeps its price, date and place, and every bottle opened or moved is recorded.",
                primary = "Add a bottle" to { nav.push(Route.AddWine()) },
                secondary = "Set up places" to { vm.setView(CellarView.PLACES) },
            )
            else -> {
                val listState = rememberLazyListState()
                val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 >= page.items.size - 10 } }
                LaunchedEffect(nearEnd, page.items.size) { if (nearEnd) vm.loadMore() }
                LazyColumn(state = listState, contentPadding = ListPadding, modifier = Modifier.fillMaxSize()) {
                    items(page.items, key = { it.vintageId }) { row ->
                        VintageRow(row, state.currentYear, onClick = { nav.push(Route.Vintage(row.vintageId)) })
                    }
                }
            }
        }
    }
}

@Composable
fun SortMenu(sort: SearchSort, onSort: (SearchSort) -> Unit, options: List<SearchSort> = SearchSort.entries.filter { it != SearchSort.RELEVANCE }) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.heightIn(min = 40.dp).clickable { open = true }.padding(horizontal = 6.dp).semantics { contentDescription = "Sort: ${sort.label}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(HovIcons.Sort, null, tint = Hov.colors.inkMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(sort.label, style = Hov.type.label, color = Hov.colors.inkMuted)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Hov.colors.surfaceRaised) {
            options.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, style = Hov.type.body, color = if (s == sort) Hov.colors.burgundyBright else Hov.colors.ink) },
                    onClick = {
                        open = false
                        onSort(s)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlacesList(nodes: List<LocationNode>, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val flat = remember(nodes) { flatten(nodes, 0) }
    LazyColumn(contentPadding = ListPadding, modifier = Modifier.fillMaxSize()) {
        items(flat, key = { it.first.location.id }) { (node, depth) ->
            PlaceRow(node, depth, onClick = { onOpen(node.location.id) })
        }
        item {
            Row(Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(horizontal = Space.gutter, vertical = Space.l), verticalAlignment = Alignment.CenterVertically) {
                Icon(HovIcons.Add, null, tint = Hov.colors.burgundyBright, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.m))
                Text("Add a place", style = Hov.type.labelStrong, color = Hov.colors.burgundyBright)
            }
        }
    }
}

private fun flatten(nodes: List<LocationNode>, depth: Int): List<Pair<LocationNode, Int>> =
    nodes.flatMap { listOf(it to depth) + flatten(it.children, depth + 1) }

@Composable
fun PlaceRow(node: LocationNode, depth: Int, onClick: () -> Unit) {
    val indent: Dp = Space.gutter + (depth * 20).dp
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(start = indent, end = Space.gutter, top = Space.m, bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (node.location.hasGrid) HovIcons.Grid else HovIcons.Place, null, tint = Hov.colors.inkMuted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(node.location.name, style = if (depth == 0) Hov.type.heading else Hov.type.body, color = Hov.colors.ink)
                Text(node.location.kind.label, style = Hov.type.caption, color = Hov.colors.inkMuted)
            }
            Text(Fmt.bottles(node.bottlesIncludingChildren), style = Hov.type.figure, color = Hov.colors.inkMuted)
            Icon(HovIcons.ChevronRight, null, tint = Hov.colors.inkFaint, modifier = Modifier.size(20.dp))
        }
        Hairline(inset = indent)
    }
}
