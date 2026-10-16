package com.houseofvino.ui.feature.search

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.DrinkStatus
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.search.SearchFilters
import com.houseofvino.core.search.SearchScope
import com.houseofvino.core.search.SearchSort
import com.houseofvino.data.search.CellarRow
import com.houseofvino.ui.app.LocalGraph
import com.houseofvino.ui.components.BottleSilhouette
import com.houseofvino.ui.components.Chip
import com.houseofvino.ui.components.Hairline
import com.houseofvino.ui.components.HovIconButton
import com.houseofvino.ui.components.HovIcons
import com.houseofvino.ui.components.HovTextField
import com.houseofvino.ui.components.ListPadding
import com.houseofvino.ui.components.LoadingRows
import com.houseofvino.ui.components.MessageState
import com.houseofvino.ui.components.PrimaryButton
import com.houseofvino.ui.components.SecondaryButton
import com.houseofvino.ui.components.TextAction
import com.houseofvino.ui.components.VintageFigure
import com.houseofvino.ui.components.VintageRow
import com.houseofvino.ui.feature.cellar.SortMenu
import com.houseofvino.ui.format.Fmt
import com.houseofvino.ui.nav.LocalNavigator
import com.houseofvino.ui.nav.Route
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space

@Composable
fun SearchScreen() {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val vm = viewModel { SearchViewModel(graph) }
    val ui by vm.state.collectAsState()
    val choices by vm.choices.collectAsState()
    var filtersOpen by remember { mutableStateOf(false) }
    var saveOpen by remember { mutableStateOf(false) }
    val q = ui.query

    Column(Modifier.fillMaxSize()) {
        // Search field as the header.
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = Space.gutter, vertical = Space.s)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(8.dp)).background(Hov.colors.surface)
                    .border(1.dp, Hov.colors.hairline, RoundedCornerShape(8.dp)).padding(horizontal = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HovIcons.Search, null, tint = Hov.colors.inkMuted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Space.s))
                Box(Modifier.weight(1f)) {
                    if (q.text.isEmpty()) Text("Producer, wine, region, grape, year…", style = Hov.type.body, color = Hov.colors.inkFaint)
                    BasicTextField(
                        value = q.text,
                        onValueChange = vm::setText,
                        singleLine = true,
                        textStyle = Hov.type.body.copy(color = Hov.colors.ink),
                        cursorBrush = SolidColor(Hov.colors.burgundyBright),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.remember() }),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search" },
                    )
                }
                if (q.text.isNotEmpty()) HovIconButton(HovIcons.Close, "Clear search", tint = Hov.colors.inkMuted) { vm.setText("") }
            }
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                SearchScope.entries.forEach { s -> Chip(s.label, selected = q.scope == s, onClick = { vm.setScope(s) }, role = Role.RadioButton) }
            }
            if (q.scope != SearchScope.DISCOVER) {
                Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.heightIn(min = 40.dp).clickable { vm.loadChoices(); filtersOpen = true }.padding(end = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(HovIcons.Filter, null, tint = if (q.filters.activeCount > 0) Hov.colors.burgundyBright else Hov.colors.inkMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (q.filters.activeCount > 0) "Filters (${q.filters.activeCount})" else "Filters", style = Hov.type.label, color = if (q.filters.activeCount > 0) Hov.colors.burgundyBright else Hov.colors.inkMuted)
                    }
                    if (q.filters.activeCount > 0) TextAction("Clear", vm::clearFilters, color = Hov.colors.inkMuted)
                    Spacer(Modifier.weight(1f))
                    SortMenu(q.sort, vm::setSort, options = SearchSort.entries)
                    if (q.scope == SearchScope.CELLAR) HovIconButton(if (ui.grid) HovIcons.ListLines else HovIcons.Grid, if (ui.grid) "Show as list" else "Show as grid", tint = Hov.colors.inkMuted) { vm.toggleGrid() }
                }
            }
        }
        Hairline()
        val blank = q.text.isBlank() && q.filters.activeCount == 0
        when (val r = ui.results) {
            null -> if (ui.error != null) MessageState("Search failed", ui.error, icon = HovIcons.Alert) else LoadingRows(4)
            SearchResults.NoCatalogue -> MessageState(
                title = "No wine catalogue is connected",
                body = "Discover searches an outside wine database. None is set up, so nothing here is invented: search your own cellar, or add a wine by hand.",
                primary = "Add a wine" to { nav.push(Route.AddWine()) },
            )
            is SearchResults.Cellar -> {
                if (blank) {
                    RecentAndSaved(ui, onRecent = vm::setText, onSaved = { vm.apply(it.query) }, onDeleteSaved = vm::deleteSaved, onClearRecent = vm::clearRecent)
                } else {
                    ResultsHeader(r.page.total, r.page.corrections, canSave = true, onSave = { saveOpen = true })
                    if (r.page.total == 0) {
                        MessageState("No matches", "Try fewer words, check the filters, or search tasting notes and the wishlist instead.")
                    } else if (ui.grid) {
                        LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                            items(r.page.items, key = { it.vintageId }) { row -> GridTile(row) { vm.remember(); nav.push(Route.Vintage(row.vintageId)) } }
                        }
                    } else {
                        LazyColumn(contentPadding = ListPadding) {
                            items(r.page.items, key = { it.vintageId }) { row -> VintageRow(row, ui.currentYear, onClick = { vm.remember(); nav.push(Route.Vintage(row.vintageId)) }) }
                            if (r.page.hasMore) item { TextAction("Show more", vm::loadMore, modifier = Modifier.padding(Space.gutter)) }
                        }
                    }
                }
            }
            is SearchResults.Tastings -> {
                ResultsHeader(r.page.total, r.page.corrections, canSave = !blank, onSave = { saveOpen = true })
                if (r.page.total == 0) {
                    MessageState(if (blank) "No tasting notes yet" else "No matches", if (blank) "Notes you write about wines will be searchable here." else "Try fewer words.")
                } else {
                    LazyColumn(contentPadding = ListPadding) {
                        items(r.page.items, key = { it.noteId }) { n ->
                            Column(Modifier.fillMaxWidth().clickable { vm.remember(); nav.push(Route.Vintage(n.vintageId)) }.padding(horizontal = Space.gutter, vertical = Space.m)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    VintageFigure(n.vintage, Modifier.width(Space.vintageColumn), small = true)
                                    Column(Modifier.weight(1f)) {
                                        Text(n.producer, style = Hov.type.heading, color = Hov.colors.ink)
                                        Text(n.tastedOn + (n.ratingValue?.let { " — ${it}/${n.ratingScale}" } ?: ""), style = Hov.type.caption, color = Hov.colors.inkMuted)
                                    }
                                }
                                n.summary?.let { Text(it, style = Hov.type.detail, color = Hov.colors.ink, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                            }
                            Hairline(inset = Space.gutter)
                        }
                    }
                }
            }
            is SearchResults.Wishlist -> {
                ResultsHeader(r.page.total, r.page.corrections, canSave = !blank, onSave = { saveOpen = true })
                if (r.page.total == 0) {
                    MessageState(if (blank) "The wishlist is empty" else "No matches", null)
                } else {
                    LazyColumn(contentPadding = ListPadding) {
                        items(r.page.items, key = { it.itemId }) { w ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m)) {
                                Text(w.title, style = Hov.type.heading, color = Hov.colors.ink)
                                Text(listOfNotNull(w.producer, "want ${w.desiredQuantity}", w.status).joinToString(", "), style = Hov.type.caption, color = Hov.colors.inkMuted)
                            }
                            Hairline(inset = Space.gutter)
                        }
                    }
                }
            }
        }
    }

    if (filtersOpen) {
        FilterSheet(q.filters, choices, onApply = { vm.setFilters(it); filtersOpen = false }, onDismiss = { filtersOpen = false })
    }
    if (saveOpen) {
        var name by remember { mutableStateOf(q.text.ifBlank { "My search" }) }
        AlertDialog(
            onDismissRequest = { saveOpen = false },
            containerColor = Hov.colors.surfaceRaised,
            title = { Text("Save this search", style = Hov.type.heading, color = Hov.colors.ink) },
            text = { HovTextField("Name", name, { name = it }) },
            confirmButton = { TextAction("Save", { vm.save(name); saveOpen = false }) },
            dismissButton = { TextAction("Cancel", { saveOpen = false }, color = Hov.colors.ink) },
        )
    }
}

@Composable
private fun ResultsHeader(total: Int, corrections: Map<String, List<String>>, canSave: Boolean, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.count(total, "match", "matches"), style = Hov.type.label, color = Hov.colors.inkMuted)
            if (corrections.isNotEmpty()) {
                Text("Including ${corrections.values.flatten().joinToString(", ")}", style = Hov.type.caption, color = Hov.colors.burgundyBright)
            }
        }
        if (canSave) TextAction("Save search", onSave)
    }
}

@Composable
private fun RecentAndSaved(ui: SearchUi, onRecent: (String) -> Unit, onSaved: (com.houseofvino.data.repo.SavedSearch) -> Unit, onDeleteSaved: (String) -> Unit, onClearRecent: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.m)) {
        if (ui.saved.isNotEmpty()) {
            Text("Saved searches", style = Hov.type.heading, color = Hov.colors.ink)
            Spacer(Modifier.height(Space.s))
            ui.saved.forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { onSaved(s) }.heightIn(min = Space.touch), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = Hov.type.body, color = Hov.colors.ink, modifier = Modifier.weight(1f))
                    HovIconButton(HovIcons.Close, "Delete saved search ${s.name}", tint = Hov.colors.inkFaint) { onDeleteSaved(s.id) }
                }
            }
            Spacer(Modifier.height(Space.l))
        }
        if (ui.recent.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", style = Hov.type.heading, color = Hov.colors.ink, modifier = Modifier.weight(1f))
                TextAction("Clear", onClearRecent, color = Hov.colors.inkMuted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                ui.recent.forEach { t -> Chip(t, selected = false, onClick = { onRecent(t) }) }
            }
        }
        if (ui.saved.isEmpty() && ui.recent.isEmpty()) {
            Text(
                "Search finds partial words and forgives small typos: “chat marg” finds Château Margaux, “2010-2015” limits vintages.",
                style = Hov.type.body, color = Hov.colors.inkMuted,
            )
        }
    }
}

@Composable
private fun GridTile(row: CellarRow, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(4.dp)).background(Hov.colors.surface).clickable(onClick = onClick).padding(Space.m)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) { BottleSilhouette(row.style, height = 100.dp) }
        Spacer(Modifier.height(Space.s))
        VintageFigure(row.vintage, small = true)
        Text(row.producer, style = Hov.type.labelStrong, color = Hov.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (row.name.isNotBlank()) Text(row.name, style = Hov.type.cuvee, color = Hov.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Fmt.bottles(row.bottles), style = Hov.type.caption, color = Hov.colors.inkMuted)
    }
}

@Composable
private fun FilterSheet(current: SearchFilters, choices: FilterChoices, onApply: (SearchFilters) -> Unit, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(current) }
    var from by remember { mutableStateOf(current.vintageFrom?.toString().orEmpty()) }
    var to by remember { mutableStateOf(current.vintageTo?.toString().orEmpty()) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Hov.colors.surfaceRaised) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter)) {
            Text("Filters", style = Hov.type.title, color = Hov.colors.ink)
            FilterGroup("Style") {
                WineStyle.entries.forEach { s -> Chip(s.label, s in f.styles, { f = f.copy(styles = f.styles.toggle(s)) }) }
            }
            FilterGroup("Drinking window") {
                DrinkStatus.entries.forEach { s -> Chip(s.label, s in f.drinkStatuses, { f = f.copy(drinkStatuses = f.drinkStatuses.toggle(s)) }) }
            }
            Text("Vintage", style = Hov.type.labelStrong, color = Hov.colors.ink, modifier = Modifier.padding(top = Space.l, bottom = Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                HovTextField("From", from, { from = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), keyboardType = KeyboardType.Number, placeholder = choices.options?.vintageRange?.first?.toString())
                HovTextField("To", to, { to = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), keyboardType = KeyboardType.Number, placeholder = choices.options?.vintageRange?.last?.toString())
            }
            Chip("Include non-vintage", f.includeNonVintage, { f = f.copy(includeNonVintage = !f.includeNonVintage) }, modifier = Modifier.padding(top = Space.s))
            choices.options?.let { o ->
                if (o.formatsMl.size > 1) FilterGroup("Bottle size") { o.formatsMl.forEach { ml -> Chip(BottleFormat.label(ml), ml in f.formatsMl, { f = f.copy(formatsMl = f.formatsMl.toggle(ml)) }) } }
                if (o.countries.isNotEmpty()) FilterGroup("Country") { o.countries.forEach { c -> Chip(c, f.countries.any { it.equals(c, true) }, { f = f.copy(countries = f.countries.toggleIgnoreCase(c)) }) } }
                if (o.regions.isNotEmpty()) FilterGroup("Region or appellation") { o.regions.take(60).forEach { r -> Chip(r, f.regions.any { it.equals(r, true) }, { f = f.copy(regions = f.regions.toggleIgnoreCase(r)) }) } }
                if (o.grapes.isNotEmpty()) FilterGroup("Grape") { o.grapes.take(60).forEach { g -> Chip(g, f.grapes.any { it.equals(g, true) }, { f = f.copy(grapes = f.grapes.toggleIgnoreCase(g)) }) } }
            }
            if (choices.places.size > 1) FilterGroup("Place (includes places inside it)") { choices.places.forEach { p -> Chip(p.name, p.id in f.locationIds, { f = f.copy(locationIds = f.locationIds.toggle(p.id)) }) } }
            if (choices.tags.isNotEmpty()) FilterGroup("Tags") { choices.tags.forEach { t -> Chip(t.name, t.id in f.tagIds, { f = f.copy(tagIds = f.tagIds.toggle(t.id)) }) } }
            FilterGroup("Collection") { Chip("Include wines no longer held", f.includeFormerlyOwned, { f = f.copy(includeFormerlyOwned = !f.includeFormerlyOwned) }) }
            Spacer(Modifier.height(Space.l))
        }
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            SecondaryButton("Reset", { f = SearchFilters(); from = ""; to = "" }, modifier = Modifier.weight(1f))
            PrimaryButton("Show results", {
                onApply(f.copy(vintageFrom = from.toIntOrNull(), vintageTo = to.toIntOrNull()))
            }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = Hov.type.labelStrong, color = Hov.colors.ink, modifier = Modifier.padding(top = Space.l, bottom = Space.s))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) { content() }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

private fun Set<String>.toggleIgnoreCase(v: String): Set<String> =
    if (any { it.equals(v, ignoreCase = true) }) filterNot { it.equals(v, ignoreCase = true) }.toSet() else this + v
