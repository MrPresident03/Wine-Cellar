package com.houseofvino.ui.feature.wine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houseofvino.core.model.MovementKind
import com.houseofvino.ui.LocalSnackbar
import com.houseofvino.ui.app.LocalGraph
import com.houseofvino.ui.components.HovIcons
import com.houseofvino.ui.components.ListPadding
import com.houseofvino.ui.components.LoadingRows
import com.houseofvino.ui.components.MessageState
import com.houseofvino.ui.components.ScreenHeader
import com.houseofvino.ui.nav.LocalNavigator

/** The full ledger for one vintage. "Undo" records a reversal; nothing is deleted. */
@Composable
fun HistoryScreen(vintageId: String) {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val vm = viewModel(key = "vintage-$vintageId") { VintageViewModel(graph, vintageId) }
    val ui by vm.state.collectAsState()
    LaunchedEffect(vm) {
        vm.events.collect { e ->
            val r = snackbar.showSnackbar(e.message, actionLabel = if (e.undoMovementId != null) "Undo" else null)
            if (r == SnackbarResult.ActionPerformed && e.undoMovementId != null) vm.undo(e.undoMovementId)
        }
    }
    Column(Modifier.fillMaxSize()) {
        val ready = ui as? VintageUi.Ready
        ScreenHeader(
            title = "History",
            summary = ready?.page?.let { "${it.wine.producer} ${it.vintage.vintage}" },
            onBack = { nav.pop() },
        )
        when (val s = ui) {
            VintageUi.Loading -> LoadingRows(5)
            VintageUi.Gone -> MessageState("This wine has been removed", null)
            is VintageUi.Failed -> MessageState("Couldn't load the history", s.message, icon = HovIcons.Alert)
            is VintageUi.Ready -> LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
                items(s.page.history, key = { it.id }) { m ->
                    MovementLine(m, s.page, canUndo = m.kind != MovementKind.REVERSAL && m.id !in s.page.reversed && m.syncState != "rejected", onUndo = vm::undo)
                }
            }
        }
    }
}
