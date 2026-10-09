package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alerts.ClimateAlertWorker
import com.example.data.AuthRepository
import com.example.data.AuthState
import com.example.data.BottleDraft
import com.example.data.Cellar
import com.example.data.CellarLayout
import com.example.data.CellarRepository
import com.example.data.ClimateReading
import com.example.data.LegacyImporter
import com.example.data.PhotoChange
import com.example.data.PhotoData
import com.example.data.PhotoUtils
import com.example.data.Prefs
import com.example.data.TimeFilter
import com.example.data.UserCellarState
import com.example.data.WineBottle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class WineCellarViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = AuthRepository()
    private val repo = CellarRepository()
    private val importer = LegacyImporter(application, repo)
    private val prefs = application.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ messages (snackbars)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun message(text: String) {
        _messages.tryEmit(text)
    }

    private val onCloudError: (Exception) -> Unit = { e ->
        Log.w(TAG, "Cloud write/listen failed", e)
        message(CellarRepository.friendlyFirestoreError(e))
    }

    // ------------------------------------------------------------------ auth & cellar

    val authState: StateFlow<AuthState> = authRepo.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    val userCellar: StateFlow<UserCellarState> = authState
        .flatMapLatest { state ->
            if (state is AuthState.SignedIn) repo.observeUserCellar(state.uid) else flowOf(UserCellarState.Loading)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserCellarState.Loading)

    private val cellarId: StateFlow<String?> = userCellar
        .map { (it as? UserCellarState.Ready)?.cellarId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val cellar: StateFlow<Cellar?> = cellarId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeCellar(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bottles: StateFlow<List<WineBottle>> = cellarId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeBottles(id, onCloudError) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    fun selectTab(index: Int) {
        _activeTab.value = index
    }

    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    // ------------------------------------------------------------------ search & filters

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _agingFilter = MutableStateFlow("All")
    val agingFilter: StateFlow<String> = _agingFilter.asStateFlow()

    private val _varietalFilter = MutableStateFlow("All")
    val varietalFilter: StateFlow<String> = _varietalFilter.asStateFlow()

    private val _sortBy = MutableStateFlow(DEFAULT_SORT)
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setAgingFilter(filter: String) {
        _agingFilter.value = filter
    }

    fun setVarietalFilter(filter: String) {
        _varietalFilter.value = filter
    }

    fun setSortBy(sort: String) {
        _sortBy.value = sort
    }

    val filteredBottles: StateFlow<List<WineBottle>> = combine(
        bottles, _searchQuery, _agingFilter, _varietalFilter, _sortBy
    ) { all, query, aging, varietal, sort ->
        var result = all
        if (query.isNotBlank()) {
            result = result.filter {
                it.wineryName.contains(query, ignoreCase = true) ||
                    it.varietal.contains(query, ignoreCase = true) ||
                    it.vintage.contains(query, ignoreCase = true) ||
                    (it.classification?.contains(query, ignoreCase = true) == true)
            }
        }
        result = when (aging) {
            "Aging Only" -> result.filter { it.isAging }
            "Ready (Not Aging)" -> result.filter { !it.isAging }
            else -> result
        }
        if (varietal != "All") {
            // "Syrah / Shiraz" matches either name, so older entries like "Shiraz" still show up.
            val names = varietal.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            result = result.filter { b -> names.any { n -> b.varietal.contains(n, ignoreCase = true) } }
        }
        sortBottles(result, sort)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ------------------------------------------------------------------ climate

    private val _timeFilter = MutableStateFlow(TimeFilter.WEEK)
    val timeFilter: StateFlow<TimeFilter> = _timeFilter.asStateFlow()

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    val latestReading: StateFlow<ClimateReading?> = cellarId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeLatestReading(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val history: StateFlow<List<ClimateReading>> = combine(cellarId, _timeFilter) { id, filter -> Pair(id, filter) }
        .flatMapLatest { pair ->
            val id = pair.first
            val filter = pair.second
            if (id == null) {
                flowOf(emptyList())
            } else {
                repo.observeHistory(id, System.currentTimeMillis() - filter.millis).map { downsample(it, 300) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _alertsEnabled = MutableStateFlow(prefs.getBoolean(Prefs.ALERTS_ENABLED, false))
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    fun setAlertsEnabled(enabled: Boolean) {
        _alertsEnabled.value = enabled
        prefs.edit()
            .putBoolean(Prefs.ALERTS_ENABLED, enabled)
            .putBoolean(Prefs.ALERT_ACTIVE, false)
            .apply()
        val app = getApplication<Application>()
        if (enabled) ClimateAlertWorker.schedule(app) else ClimateAlertWorker.cancel(app)
    }

    fun setAlertThreshold(value: Double) {
        val id = cellarId.value ?: return
        val rounded = Math.round(value * 2) / 2.0
        repo.updateCellarFields(id, mapOf("alertThreshold" to rounded), onCloudError)
        prefs.edit().putBoolean(Prefs.ALERT_ACTIVE, false).apply()
    }

    // ------------------------------------------------------------------ version 1 import

    private val _legacyCount = MutableStateFlow(0)
    val legacyCount: StateFlow<Int> = _legacyCount.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    fun dismissImportResult() {
        _importResult.value = null
    }

    fun importLegacy() {
        val id = cellarId.value ?: return
        val state = authState.value as? AuthState.SignedIn ?: return
        launchImport(id, state, manual = true)
    }

    private fun launchImport(id: String, state: AuthState.SignedIn, manual: Boolean) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                val r = importer.importInto(id, state.uid, state.email)
                val found = r.imported > 0 || r.alreadyInCellar > 0
                if (found || manual) {
                    val sb = StringBuilder()
                    if (!found) {
                        sb.append("No bottles from the previous version were found on this phone.")
                    } else {
                        sb.append("Imported ${r.imported} bottle")
                        if (r.imported != 1) sb.append("s")
                        if (r.photos > 0) sb.append(" (with ${r.photos} photo${if (r.photos == 1) "" else "s"})")
                        sb.append(" from the previous version of the app.")
                        if (r.alreadyInCellar > 0) {
                            sb.append(" ${r.alreadyInCellar} were already in this cellar, so they were skipped.")
                        }
                        if (r.stillUploading) {
                            sb.append(" Some are still uploading and will reach other phones once this one is online.")
                        }
                    }
                    _importResult.value = sb.toString()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Import failed", e)
                _importResult.value = "The import didn't finish: ${CellarRepository.friendlyFirestoreError(e)}\n\n" +
                    "Your old data is untouched on this phone. You can run the import again from Settings."
            } finally {
                _importing.value = false
            }
        }
    }

    init {
        viewModelScope.launch {
            _legacyCount.value = withContext(Dispatchers.IO) {
                if (importer.hasLocalData()) importer.readLocal().size else 0
            }
        }
        if (_alertsEnabled.value) ClimateAlertWorker.schedule(application)
    }

    // ------------------------------------------------------------------ sign in / out

    fun signIn(email: String, password: String) {
        authAction { authRepo.signIn(email.trim(), password) }
    }

    fun signUp(email: String, password: String) {
        authAction { authRepo.signUp(email.trim(), password) }
    }

    fun resetPassword(email: String) {
        authAction {
            authRepo.resetPassword(email.trim())
            message("Password reset email sent to ${email.trim()}")
        }
    }

    private fun authAction(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: Exception) {
                message(AuthRepository.friendlyError(e))
            } finally {
                _busy.value = false
            }
        }
    }

    fun signOut() {
        authRepo.signOut()
        _inviteCode.value = null
        _activeTab.value = 0
        _searchQuery.value = ""
    }

    // ------------------------------------------------------------------ cellar setup & sharing

    /** Creates a new cellar for this account and copies in the bottles from version 1. */
    fun createCellar() {
        val state = authState.value as? AuthState.SignedIn ?: return
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val name = importer.legacyCellarName() ?: "My Cellar"
                val id = repo.createCellar(state.uid, state.email, name)
                launchImport(id, state, manual = false)
            } catch (e: Exception) {
                message(CellarRepository.friendlyFirestoreError(e))
            } finally {
                _busy.value = false
            }
        }
    }

    fun joinCellar(code: String) {
        val state = authState.value as? AuthState.SignedIn ?: return
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                repo.joinCellar(state.uid, state.email, code)
                _inviteCode.value = null
                message("You've joined the cellar.")
            } catch (e: IllegalArgumentException) {
                message(e.message ?: "That invite code didn't work.")
            } catch (e: Exception) {
                message(CellarRepository.friendlyFirestoreError(e))
            } finally {
                _busy.value = false
            }
        }
    }

    fun createInvite() {
        val id = cellarId.value ?: return
        val state = authState.value as? AuthState.SignedIn ?: return
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                _inviteCode.value = repo.createInvite(state.uid, id)
            } catch (e: Exception) {
                message(CellarRepository.friendlyFirestoreError(e))
            } finally {
                _busy.value = false
            }
        }
    }

    fun renameCellar(name: String) {
        val id = cellarId.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        repo.updateCellarFields(id, mapOf("name" to trimmed), onCloudError)
    }

    // ------------------------------------------------------------------ bottles

    private fun ids(): Pair<String, String>? {
        val id = cellarId.value ?: return null
        val state = authState.value as? AuthState.SignedIn ?: return null
        return Pair(id, state.uid)
    }

    fun bottleAt(row: Int, col: Int, exceptId: String? = null): WineBottle? =
        bottles.value.firstOrNull { it.gridRow == row && it.gridCol == col && it.id != exceptId }

    fun addBottle(draft: BottleDraft, photo: PhotoData?) {
        val pair = ids() ?: return
        repo.addBottle(pair.first, pair.second, draft, photo, onCloudError)
    }

    fun updateBottle(bottleId: String, draft: BottleDraft, change: PhotoChange) {
        val pair = ids() ?: return
        repo.updateBottle(pair.first, pair.second, bottleId, draft, change, onCloudError)
    }

    /** Moves a bottle; if the target slot is taken the two bottles swap places in one write. */
    fun moveBottle(bottle: WineBottle, row: Int, col: Int) {
        val pair = ids() ?: return
        if (bottle.gridRow == row && bottle.gridCol == col) return
        val occupant = bottleAt(row, col, exceptId = bottle.id)
        repo.moveBottle(pair.first, pair.second, bottle, row, col, occupant, onCloudError)
    }

    fun duplicateToNextFreeSlot(bottle: WineBottle) {
        val pair = ids() ?: return
        val slot = nextFreeSlot(bottle.gridRow, bottle.gridCol)
        if (slot == null) {
            message("There are no free slots left in the rack.")
            return
        }
        viewModelScope.launch {
            repo.duplicateBottle(pair.first, pair.second, bottle, slot.first, slot.second, onCloudError)
            message("Copied to row ${slot.first}, column ${slot.second}.")
        }
    }

    private fun nextFreeSlot(fromRow: Int, fromCol: Int): Pair<Int, Int>? {
        val taken = bottles.value.map { Pair(it.gridRow, it.gridCol) }.toSet()
        val total = CellarLayout.ROWS * CellarLayout.COLS
        val start = (fromRow - 1) * CellarLayout.COLS + (fromCol - 1)
        for (step in 1 until total) {
            val index = (start + step) % total
            val slot = Pair(index / CellarLayout.COLS + 1, index % CellarLayout.COLS + 1)
            if (slot !in taken) return slot
        }
        return null
    }

    fun deleteBottle(bottle: WineBottle) {
        val pair = ids() ?: return
        repo.deleteBottle(pair.first, bottle.id, onCloudError)
    }

    // ------------------------------------------------------------------ photos

    suspend fun processPhoto(uri: Uri): PhotoData? = withContext(Dispatchers.IO) {
        PhotoUtils.fromUri(getApplication<Application>(), uri)
    }

    suspend fun loadFullPhoto(bottleId: String): ImageBitmap? {
        val id = cellarId.value ?: return null
        val data: String = try {
            repo.getPhoto(id, bottleId)
        } catch (e: Exception) {
            null
        } ?: return null
        return withContext(Dispatchers.Default) { PhotoUtils.decode(data)?.asImageBitmap() }
    }

    companion object {
        private const val TAG = "WineCellarViewModel"
        const val DEFAULT_SORT = "Varietal (A-Z)"
        val SORT_OPTIONS = listOf(
            "Varietal (A-Z)",
            "Winery (A-Z)",
            "Vintage (Newest)",
            "Vintage (Oldest)",
            "Price (Highest)",
            "Price (Lowest)",
            "Rack Slot"
        )

        fun sortBottles(list: List<WineBottle>, sort: String): List<WineBottle> = when (sort) {
            "Winery (A-Z)" -> list.sortedBy { it.wineryName.lowercase() }
            "Vintage (Newest)" -> list.sortedByDescending { it.vintage.toIntOrNull() ?: 0 }
            "Vintage (Oldest)" -> list.sortedBy { it.vintage.toIntOrNull() ?: 9999 }
            "Price (Highest)" -> list.sortedByDescending { it.price ?: 0.0 }
            "Price (Lowest)" -> list.sortedBy { it.price ?: Double.MAX_VALUE }
            "Rack Slot" -> list.sortedWith(compareBy<WineBottle> { it.gridRow }.thenBy { it.gridCol })
            else -> list.sortedWith(compareBy<WineBottle> { it.varietal.lowercase() }.thenByDescending { it.vintage })
        }

        /** Averages readings into at most [maxPoints] buckets so long ranges still draw quickly. */
        fun downsample(readings: List<ClimateReading>, maxPoints: Int): List<ClimateReading> {
            if (readings.size <= maxPoints) return readings
            val bucket = (readings.size + maxPoints - 1) / maxPoints
            return readings.chunked(bucket).map { chunk ->
                ClimateReading(
                    timestamp = chunk[chunk.size / 2].timestamp,
                    temperature = chunk.map { it.temperature }.average().toFloat(),
                    humidity = chunk.map { it.humidity }.average().toFloat()
                )
            }
        }
    }
}
