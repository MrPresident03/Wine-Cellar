package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.TemperatureRecord
import com.example.data.WineBottle
import com.example.data.WineCellarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimeFilter {
    DAY, WEEK, MONTH
}

class WineCellarViewModel(private val repository: WineCellarRepository) : ViewModel() {

    // Active screen navigation state
    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    // Database of wine bottles
    val bottlesState: StateFlow<List<WineBottle>> = repository.allBottles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Additional Filter states
    private val _agingFilter = MutableStateFlow("All")
    val agingFilter: StateFlow<String> = _agingFilter.asStateFlow()

    private val _varietalFilter = MutableStateFlow("All")
    val varietalFilter: StateFlow<String> = _varietalFilter.asStateFlow()

    private val _sortBy = MutableStateFlow("Varietal (A-Z)")
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    fun setAgingFilter(filter: String) {
        _agingFilter.value = filter
    }

    fun setVarietalFilter(filter: String) {
        _varietalFilter.value = filter
    }

    fun setSortBy(sort: String) {
        _sortBy.value = sort
    }

    // Filtered list of bottles
    val filteredBottlesState: StateFlow<List<WineBottle>> = combine(
        bottlesState,
        _searchQuery,
        _agingFilter,
        _varietalFilter,
        _sortBy
    ) { bottles, query, aging, varietalOpt, sortOpt ->
        var result = bottles

        // 1. Text Search query filter
        if (query.isNotBlank()) {
            result = result.filter {
                it.wineryName.contains(query, ignoreCase = true) ||
                it.varietal.contains(query, ignoreCase = true) ||
                it.vintage.contains(query, ignoreCase = true)
            }
        }

        // 2. Aging filter
        result = when (aging) {
            "Aging Only" -> result.filter { it.isAging }
            "Ready (Not Aging)" -> result.filter { !it.isAging }
            else -> result
        }

        // 3. Varietal optional filter
        if (varietalOpt != "All") {
            result = result.filter { it.varietal.equals(varietalOpt, ignoreCase = true) }
        }

        // 4. Sorting
        result = when (sortOpt) {
            "Winery (A-Z)" -> result.sortedBy { it.wineryName }
            "Vintage (Newest)" -> result.sortedByDescending { it.vintage.toIntOrNull() ?: 0 }
            "Vintage (Oldest)" -> result.sortedBy { it.vintage.toIntOrNull() ?: 9999 }
            "Price (Highest)" -> result.sortedByDescending { it.price ?: 0.0 }
            "Price (Lowest)" -> result.sortedBy { it.price ?: 999999.0 }
            else -> result.sortedWith(compareBy<WineBottle> { it.varietal }.thenByDescending { it.vintage })
        }

        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Combined temperature logs
    val temperatureRecordsState: StateFlow<List<TemperatureRecord>> = repository.allTemperatureRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Selected chart log filter
    private val _timeFilter = MutableStateFlow(TimeFilter.WEEK)
    val timeFilter: StateFlow<TimeFilter> = _timeFilter.asStateFlow()

    // Filtered temperature points based on Selection
    val filteredHistoryState: StateFlow<List<TemperatureRecord>> = combine(
        temperatureRecordsState,
        _timeFilter
    ) { records, filter ->
        if (records.isEmpty()) return@combine emptyList()

        val cutoffTime = when (filter) {
            TimeFilter.DAY -> System.currentTimeMillis() - 24 * 60 * 60 * 1000
            TimeFilter.WEEK -> System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
            TimeFilter.MONTH -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        }

        // Return records within scope, sorted chronologically
        records.filter { it.timestamp >= cutoffTime }
            .sortedBy { it.timestamp }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current latest climate reading
    val currentClimateState: StateFlow<TemperatureRecord?> = temperatureRecordsState
        .combine(_timeFilter) { records, _ ->
            // Simply take the latest available reading
            records.maxByOrNull { it.timestamp }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Microcontroller IP setting (Arduino/ESP8266)
    private val _microcontrollerIp = MutableStateFlow("192.168.1.145")
    val microcontrollerIp: StateFlow<String> = _microcontrollerIp.asStateFlow()

    // Microcontroller connection status
    private val _isMicrocontrollerConnected = MutableStateFlow(true)
    val isMicrocontrollerConnected: StateFlow<Boolean> = _isMicrocontrollerConnected.asStateFlow()

    // Low power mode state
    private val _lowPowerModeEnabled = MutableStateFlow(false)
    val lowPowerModeEnabled: StateFlow<Boolean> = _lowPowerModeEnabled.asStateFlow()

    // Threshold high alert temperature setting
    private val _alertTempThreshold = MutableStateFlow(18.0f)
    val alertTempThreshold: StateFlow<Float> = _alertTempThreshold.asStateFlow()

    // Sycn status updates
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis() - 12 * 60 * 1000) // Default 12 mins ago
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    fun selectTab(tabIndex: Int) {
        _activeTab.value = tabIndex
    }

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setMicrocontrollerIp(ip: String) {
        _microcontrollerIp.value = ip
    }

    fun toggleLowPowerMode(enabled: Boolean) {
        _lowPowerModeEnabled.value = enabled
    }

    fun setAlertThreshold(threshold: Float) {
        _alertTempThreshold.value = threshold
    }

    // Trigger Wi-Fi microcontroller sync
    fun triggerWifiSync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            // Simulate a brief wifi roundtrip delay of 800ms
            kotlinx.coroutines.delay(800)

            // Sync from repository with slight mock noise around cellar targets
            repository.syncMicrocontroller(targetTemp = 13.5f, baseHumidity = 67.5f)

            _isMicrocontrollerConnected.value = true
            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncing.value = false
        }
    }

    // Manage Bottles
    fun addBottle(winery: String, varietal: String, vintage: String, row: Int, col: Int, photoUri: String? = null, price: Double? = null, isAging: Boolean = false) {
        viewModelScope.launch {
            val bottle = WineBottle(
                wineryName = winery.trim(),
                varietal = varietal.trim(),
                vintage = vintage.trim(),
                gridRow = row,
                gridCol = col,
                photoUri = photoUri,
                price = price,
                isAging = isAging
            )
            repository.insertBottle(bottle)
        }
    }

    fun deleteBottle(bottle: WineBottle) {
        viewModelScope.launch {
            repository.deleteBottle(bottle)
        }
    }
}

class WineCellarViewModelFactory(private val repository: WineCellarRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WineCellarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WineCellarViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
