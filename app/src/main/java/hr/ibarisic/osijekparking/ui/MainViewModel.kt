package hr.ibarisic.osijekparking.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hr.ibarisic.osijekparking.OsijekParkingApp
import hr.ibarisic.osijekparking.data.AddressResult
import hr.ibarisic.osijekparking.data.Geocoder
import hr.ibarisic.osijekparking.data.ParkingRepository
import hr.ibarisic.osijekparking.domain.EffectiveStatus
import hr.ibarisic.osijekparking.domain.GeoUtils
import hr.ibarisic.osijekparking.domain.LatLon
import hr.ibarisic.osijekparking.domain.OSIJEK_ZONE
import hr.ibarisic.osijekparking.domain.ParkingDataset
import hr.ibarisic.osijekparking.domain.ParkingSegment
import hr.ibarisic.osijekparking.domain.ParkingStatusEvaluator
import hr.ibarisic.osijekparking.domain.TextMatch
import hr.ibarisic.osijekparking.ui.map.CameraCommand
import hr.ibarisic.osijekparking.ui.map.DisplayGeoJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

sealed interface Sheet {
    data class Segment(val id: String) : Sheet
    data class Nearby(
        val address: AddressResult,
        /** Mapped segments within [NEARBY_RADIUS_M], nearest first. */
        val nearby: List<Pair<ParkingSegment, Double>>,
        /** Dataset entries on the same street (useful while geometry is still missing). */
        val sameStreet: List<ParkingSegment>,
    ) : Sheet
    data object AllSegments : Sheet
    data object About : Sheet
}

sealed interface SearchResult {
    data class Local(val segment: ParkingSegment) : SearchResult
    data class Address(val address: AddressResult) : SearchResult
}

data class SearchState(
    val query: String = "",
    val active: Boolean = false,
    val local: List<ParkingSegment> = emptyList(),
    val addresses: List<AddressResult> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** True after a submitted address search, so "no results" is shown only when meaningful. */
    val submitted: Boolean = false,
)

data class MainUiState(
    val dataset: ParkingDataset? = null,
    val loadFailed: Boolean = false,
    val now: ZonedDateTime = ZonedDateTime.now(OSIJEK_ZONE),
    val displayGeoJson: String = DisplayGeoJson.EMPTY,
    val selectedId: String? = null,
    val sheet: Sheet? = null,
    val searchPin: LatLon? = null,
    val search: SearchState = SearchState(),
)

const val NEARBY_RADIUS_M = 300.0

class MainViewModel(
    private val repository: ParkingRepository,
    private val geocoder: Geocoder,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val cameraChannel = Channel<CameraCommand>(Channel.CONFLATED)
    val cameraCommands: Flow<CameraCommand> = cameraChannel.receiveAsFlow()

    var evaluator = ParkingStatusEvaluator()
        private set

    private var searchJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            while (true) {
                val now = ZonedDateTime.now(OSIJEK_ZONE)
                val dayChanged = now.toLocalDate() != _state.value.now.toLocalDate()
                _state.update { it.copy(now = now) }
                // Effective status (e.g. PLANNED -> PAID on active_from) can change at midnight.
                if (dayChanged) rebuildMap()
                delay(60_000L - (now.second * 1000L + now.nano / 1_000_000))
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            runCatching { repository.dataset() }
                .onSuccess { ds ->
                    evaluator = ParkingStatusEvaluator(ds.extraHolidays)
                    _state.update { it.copy(dataset = ds, loadFailed = false) }
                    rebuildMap()
                }
                .onFailure { _state.update { it.copy(loadFailed = true) } }
        }
    }

    private fun rebuildMap() {
        val ds = _state.value.dataset ?: return
        val today = _state.value.now.toLocalDate()
        _state.update { it.copy(displayGeoJson = DisplayGeoJson.build(ds.mappedSegments) { s -> evaluator.effectiveStatus(s, today) }) }
    }

    fun effectiveStatus(segment: ParkingSegment): EffectiveStatus =
        evaluator.effectiveStatus(segment, _state.value.now.toLocalDate())

    fun segment(id: String): ParkingSegment? = _state.value.dataset?.segments?.firstOrNull { it.id == id }

    // --- Selection & sheets ---------------------------------------------------------------

    fun onSegmentTapped(id: String) {
        _state.update { it.copy(selectedId = id, sheet = Sheet.Segment(id)) }
    }

    /** Opens a segment from a list or search; zooms to it when it's on the map. */
    fun openSegment(segment: ParkingSegment) {
        segment.geometry?.let { cameraChannel.trySend(CameraCommand.FitGeometry(it)) }
        _state.update { it.copy(selectedId = segment.id, sheet = Sheet.Segment(segment.id), search = it.search.copy(active = false)) }
    }

    fun showSheet(sheet: Sheet) = _state.update { it.copy(sheet = sheet) }

    fun dismissSheet() = _state.update { it.copy(sheet = null, selectedId = null) }

    fun onMapTappedEmpty() {
        if (_state.value.search.active) _state.update { it.copy(search = it.search.copy(active = false)) }
        else dismissSheet()
    }

    // --- Search -----------------------------------------------------------------------------

    fun onSearchActiveChange(active: Boolean) = _state.update { it.copy(search = it.search.copy(active = active)) }

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        val segments = _state.value.dataset?.segments.orEmpty()
        val local = if (query.isBlank()) emptyList() else segments.filter { TextMatch.matches(query, it.name) }.take(8)
        _state.update {
            it.copy(search = it.search.copy(query = query, local = local, addresses = emptyList(), loading = false, failed = false, submitted = false))
        }
    }

    /** Address lookup runs on submit only (Nominatim usage policy forbids autocomplete). */
    fun onSearchSubmit() {
        val query = _state.value.search.query.trim()
        if (query.length < 3) return
        searchJob?.cancel()
        _state.update { it.copy(search = it.search.copy(loading = true, failed = false, submitted = true)) }
        searchJob = viewModelScope.launch {
            runCatching { geocoder.search(query) }
                .onSuccess { r -> _state.update { it.copy(search = it.search.copy(addresses = r, loading = false)) } }
                .onFailure { _state.update { it.copy(search = it.search.copy(loading = false, failed = true)) } }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = SearchState(), searchPin = null) }
    }

    fun onAddressSelected(address: AddressResult) {
        val ds = _state.value.dataset
        val nearby = ds?.mappedSegments.orEmpty()
            .map { it to GeoUtils.distanceMeters(address.position, it.geometry!!) }
            .filter { it.second <= NEARBY_RADIUS_M }
            .sortedBy { it.second }
        val sameStreet = address.road?.let { road -> ds?.segments.orEmpty().filter { TextMatch.sameStreet(road, it.name) } }.orEmpty()
        cameraChannel.trySend(CameraCommand.MoveTo(address.position, 17.0))
        _state.update {
            it.copy(
                searchPin = address.position,
                selectedId = null,
                sheet = Sheet.Nearby(address, nearby, sameStreet),
                search = it.search.copy(query = address.title, active = false),
            )
        }
    }

    // --- Location ---------------------------------------------------------------------------

    fun centerOnUser() {
        cameraChannel.trySend(CameraCommand.TrackUser)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as OsijekParkingApp
                MainViewModel(app.container.repository, app.container.geocoder)
            }
        }
    }
}
