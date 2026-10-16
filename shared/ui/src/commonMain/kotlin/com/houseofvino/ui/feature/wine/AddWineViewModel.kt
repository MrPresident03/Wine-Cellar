package com.houseofvino.ui.feature.wine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.houseofvino.core.model.BottleFormat
import com.houseofvino.core.model.Grapes
import com.houseofvino.core.model.Vintage
import com.houseofvino.core.model.WindowSource
import com.houseofvino.core.model.WineStyle
import com.houseofvino.core.money.Currency
import com.houseofvino.core.money.Money
import com.houseofvino.data.repo.DuplicateWineException
import com.houseofvino.data.repo.InvalidInputException
import com.houseofvino.data.repo.LocationDetails
import com.houseofvino.data.repo.PurchaseInput
import com.houseofvino.data.repo.VintageInput
import com.houseofvino.data.repo.WineDetails
import com.houseofvino.data.repo.WineInput
import com.houseofvino.ui.app.AppGraph
import com.houseofvino.ui.nav.AddWinePrefill
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class AddWineForm(
    val wineId: String? = null,
    val producer: String = "",
    val name: String = "",
    val vintage: String = "",
    val style: WineStyle? = null,
    val country: String = "",
    val region: String = "",
    val appellation: String = "",
    val classification: String = "",
    val grapes: String = "",
    val quantity: Int = 1,
    val formatMl: Int = BottleFormat.STANDARD_ML,
    val otherFormat: String = "",
    val locationId: String? = null,
    val price: String = "",
    val currency: Currency = Currency("NZD"),
    val acquiredOn: LocalDate? = null,
    val source: String = "",
    val drinkFrom: String = "",
    val drinkTo: String = "",
    val notes: String = "",
)

data class AddWineState(
    val form: AddWineForm = AddWineForm(),
    val places: List<LocationDetails> = emptyList(),
    val producerSuggestions: List<String> = emptyList(),
    val winesOfProducer: List<WineDetails> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val duplicates: List<WineDetails> = emptyList(),
    val saving: Boolean = false,
    val savedVintageId: String? = null,
    val savedMessage: String? = null,
    val currentYear: Int = 2026,
)

@OptIn(FlowPreview::class)
class AddWineViewModel(private val graph: AppGraph, prefill: AddWinePrefill?) : ViewModel() {
    private val _state = MutableStateFlow(AddWineState())
    val state: StateFlow<AddWineState> = _state.asStateFlow()
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            val session = graph.requireSession()
            val places = graph.db { graph.locations.all(it) }
            val household = graph.db { graph.households.household(session.householdId) }
            val today = graph.clock.today()
            var form = AddWineForm(
                locationId = prefill?.locationId ?: places.firstOrNull()?.id,
                currency = household?.homeCurrency ?: graph.defaultCurrency,
                acquiredOn = today,
                producer = prefill?.producer.orEmpty(),
                name = prefill?.name.orEmpty(),
                vintage = prefill?.vintage?.takeIf { it != "Unknown" }.orEmpty(),
            )
            prefill?.wineId?.let { id -> graph.db { graph.wines.wine(id) } }?.let { w -> form = form.withWine(w) }
            _state.update { it.copy(form = form, places = places, currentYear = graph.clock.currentYear()) }
        }
    }

    fun edit(change: (AddWineForm) -> AddWineForm) {
        val before = _state.value.form
        val after = change(before)
        _state.update { it.copy(form = after, errors = it.errors - changedFields(before, after)) }
        if (after.producer != before.producer && after.wineId == null) suggestProducers(after.producer)
    }

    /** Typing a new producer name after choosing an existing wine starts a new wine. */
    fun editProducer(text: String) = edit { it.copy(producer = text, wineId = null) }

    fun chooseProducer(producer: String) {
        edit { it.copy(producer = producer) }
        _state.update { it.copy(producerSuggestions = emptyList()) }
        viewModelScope.launch {
            val wines = graph.db { s -> graph.store.db.wineQueries.winesOfProducer(s.householdId, producer).executeAsList().map { w -> graph.wines.wine(w.id)!! } }
            _state.update { it.copy(winesOfProducer = wines) }
        }
    }

    fun chooseWine(w: WineDetails) {
        edit { it.withWine(w) }
        _state.update { it.copy(winesOfProducer = emptyList()) }
    }

    fun clearChosenWine() = edit { it.copy(wineId = null) }

    private fun suggestProducers(prefix: String) {
        suggestJob?.cancel()
        if (prefix.trim().length < 2) {
            _state.update { it.copy(producerSuggestions = emptyList(), winesOfProducer = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(150)
            val found = graph.db { s -> graph.wines.producerSuggestions(s, prefix) }
            _state.update { it.copy(producerSuggestions = found.filter { p -> !p.equals(prefix.trim(), ignoreCase = true) }) }
        }
    }

    fun dismissDuplicates() = _state.update { it.copy(duplicates = emptyList()) }

    /**
     * Saves wine, vintage and purchase in one transaction. [useExistingWineId] / [forceNewWine]
     * answer the duplicate question when a wine with the same name already exists.
     */
    fun save(addAnother: Boolean, useExistingWineId: String? = null, forceNewWine: Boolean = false) {
        val s = _state.value
        if (s.saving) return
        val f = s.form
        val errors = validate(f, s.currentYear)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true, duplicates = emptyList()) }
        viewModelScope.launch {
            try {
                val vintage = Vintage.parse(f.vintage, s.currentYear + 1)!!
                val format = if (f.otherFormat.isNotBlank()) BottleFormat.parse(f.otherFormat)!! else f.formatMl
                val price = f.price.takeIf { it.isNotBlank() }?.let { Money.parse(it, f.currency) }
                val vintageId = graph.db { session ->
                    graph.store.write {
                        val wineId = useExistingWineId ?: f.wineId ?: graph.wines.createWine(session, f.toWineInput(), allowDuplicate = forceNewWine)
                        val vid = graph.wines.vintageFor(
                            session,
                            wineId,
                            VintageInput(
                                vintage,
                                drinkFrom = f.drinkFrom.toIntOrNull(),
                                drinkTo = f.drinkTo.toIntOrNull(),
                                drinkSource = if (f.drinkFrom.isBlank() && f.drinkTo.isBlank()) null else WindowSource.USER,
                            ),
                        )
                        graph.inventory.addPurchase(
                            session,
                            PurchaseInput(
                                vintageId = vid,
                                quantity = f.quantity,
                                locationId = f.locationId!!,
                                formatMl = format,
                                unitPrice = price,
                                acquiredOn = f.acquiredOn,
                                source = f.source.ifBlank { null },
                            ),
                        )
                        vid
                    }
                }
                val message = "Added ${f.quantity} × ${BottleFormat.label(format)} of ${f.producer.trim()} $vintage."
                if (addAnother) {
                    _state.update {
                        it.copy(
                            saving = false,
                            savedMessage = message,
                            form = AddWineForm(locationId = f.locationId, currency = f.currency, acquiredOn = f.acquiredOn, source = f.source),
                            errors = emptyMap(),
                        )
                    }
                } else {
                    _state.update { it.copy(saving = false, savedVintageId = vintageId, savedMessage = message) }
                }
            } catch (e: DuplicateWineException) {
                _state.update { it.copy(saving = false, duplicates = e.candidates) }
            } catch (e: InvalidInputException) {
                _state.update { it.copy(saving = false, errors = mapOf(e.field to (e.message ?: "Check this"))) }
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(saving = false, errors = mapOf("form" to (e.message ?: "Couldn't save"))) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(savedMessage = null) }

    companion object {
        fun validate(f: AddWineForm, currentYear: Int): Map<String, String> = buildMap {
            if (f.producer.isBlank()) put("producer", "Enter the producer")
            if (Vintage.parse(f.vintage, currentYear + 1) == null) put("vintage", "Enter a year like 2016, NV, or leave it empty if unknown")
            if (f.quantity < 1) put("quantity", "At least one bottle")
            if (f.otherFormat.isNotBlank() && BottleFormat.parse(f.otherFormat) == null) put("format", "Enter a size like 500 ml or 1.5 L")
            if (f.locationId == null) put("location", "Choose where the bottles are")
            if (f.price.isNotBlank() && Money.parse(f.price, f.currency) == null) {
                put("price", "Enter an amount like 45.00 (${f.currency.code} has ${f.currency.minorDigits} decimal places)")
            }
            val from = f.drinkFrom.trim()
            val to = f.drinkTo.trim()
            if (from.isNotEmpty() && from.toIntOrNull()?.takeIf { it in 1900..2200 } == null) put("drinkFrom", "Enter a year")
            if (to.isNotEmpty() && to.toIntOrNull()?.takeIf { it in 1900..2200 } == null) put("drinkTo", "Enter a year")
            val fi = from.toIntOrNull()
            val ti = to.toIntOrNull()
            if (fi != null && ti != null && fi > ti) put("drinkTo", "Ends before it starts")
        }

        private fun changedFields(a: AddWineForm, b: AddWineForm): Set<String> = buildSet {
            if (a.producer != b.producer) add("producer")
            if (a.vintage != b.vintage) add("vintage")
            if (a.quantity != b.quantity) add("quantity")
            if (a.otherFormat != b.otherFormat || a.formatMl != b.formatMl) add("format")
            if (a.locationId != b.locationId) add("location")
            if (a.price != b.price || a.currency != b.currency) add("price")
            if (a.drinkFrom != b.drinkFrom) add("drinkFrom")
            if (a.drinkTo != b.drinkTo) { add("drinkTo"); add("window") }
            add("form")
        }
    }
}

private fun AddWineForm.withWine(w: WineDetails) = copy(
    wineId = w.id,
    producer = w.producer,
    name = w.name,
    style = w.style,
    country = w.country.orEmpty(),
    region = w.region.orEmpty(),
    appellation = w.appellation.orEmpty(),
    classification = w.classification.orEmpty(),
    grapes = Grapes.display(w.grapes),
)

private fun AddWineForm.toWineInput() = WineInput(
    producer = producer,
    name = name,
    style = style ?: WineStyle.guess(grapes) ?: WineStyle.guess(name),
    country = country,
    region = region,
    appellation = appellation,
    classification = classification,
    grapes = Grapes.parse(grapes),
)
