package com.houseofvino.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.houseofvino.core.money.Currency
import com.houseofvino.data.repo.HouseholdDetails
import com.houseofvino.ui.app.LocalGraph
import com.houseofvino.ui.components.Chip
import com.houseofvino.ui.components.Fact
import com.houseofvino.ui.components.ScreenHeader
import com.houseofvino.ui.components.SectionHeading
import com.houseofvino.ui.nav.LocalNavigator
import com.houseofvino.ui.theme.Hov
import com.houseofvino.ui.theme.Space
import kotlinx.coroutines.launch

/** Settings for this milestone: the household's home currency and what's stored where. */
@Composable
fun SettingsScreen() {
    val graph = LocalGraph.current
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var household by remember { mutableStateOf<HouseholdDetails?>(null) }
    var counts by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(Unit) {
        household = graph.db { s -> graph.households.household(s.householdId) }
        counts = graph.db { s ->
            val db = graph.store.db
            db.wineQueries.liveCount(s.householdId).executeAsOne() to db.outboxQueries.count(s.householdId).executeAsOne()
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeading("Home currency")
            Text(
                "Totals are shown in this currency. Prices keep the currency they were paid in; nothing is converted without an exchange rate you can see.",
                style = Hov.type.detail, color = Hov.colors.inkMuted, modifier = Modifier.padding(horizontal = Space.gutter),
            )
            FlowRow(Modifier.padding(Space.gutter), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Currency.COMMON.forEach { c ->
                    Chip(c.code, selected = household?.homeCurrency == c, onClick = {
                        scope.launch {
                            graph.db { s -> graph.households.setHomeCurrency(s, c) }
                            household = graph.db { s -> graph.households.household(s.householdId) }
                        }
                    })
                }
            }
            SectionHeading("Your data")
            Fact("Cellar", household?.name)
            Fact("Stored", if (household?.isLocalOnly != false) "On this device only" else "On this device and in your account")
            Fact("Wines", counts?.first?.toString())
            Fact("Changes waiting to sync", counts?.second?.toString())
            Text(
                "Accounts, sync between devices, sharing with your household, CSV import and export, and backups are being built in the next milestones. Until then everything stays on this device.",
                style = Hov.type.detail, color = Hov.colors.inkMuted, modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.m),
            )
        }
    }
}
