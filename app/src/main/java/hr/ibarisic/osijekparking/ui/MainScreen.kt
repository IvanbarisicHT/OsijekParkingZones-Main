package hr.ibarisic.osijekparking.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.ibarisic.osijekparking.R
import hr.ibarisic.osijekparking.ui.components.AboutContent
import hr.ibarisic.osijekparking.ui.components.AllSegmentsContent
import hr.ibarisic.osijekparking.ui.components.Legend
import hr.ibarisic.osijekparking.ui.components.NearbyContent
import hr.ibarisic.osijekparking.ui.components.SearchBox
import hr.ibarisic.osijekparking.ui.components.SegmentDetail
import hr.ibarisic.osijekparking.ui.map.ParkingMap
import kotlinx.coroutines.launch

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // --- Location permission (foreground only; the app works fully without it) ---------------
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var centerAfterGrant by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("app", Context.MODE_PRIVATE) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        locationGranted = result.values.any { it }
        if (locationGranted && centerAfterGrant) vm.centerOnUser()
        centerAfterGrant = false
    }

    // Re-check when returning from system settings.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) locationGranted = hasLocationPermission(context) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // Ask once on first launch; afterwards only when the user taps the location button.
    LaunchedEffect(Unit) {
        if (!locationGranted && !prefs.getBoolean(KEY_ASKED, false)) {
            prefs.edit().putBoolean(KEY_ASKED, true).apply()
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    val msgPermission = stringResource(R.string.location_permission_denied)
    val msgSettings = stringResource(R.string.action_settings)
    val msgLocationOff = stringResource(R.string.location_disabled)

    fun onLocationClick() {
        val activity = context as Activity
        when {
            locationGranted -> {
                if (!isLocationEnabled(context)) scope.launch { snackbar.showSnackbar(msgLocationOff) }
                vm.centerOnUser()
            }
            // Never asked, or denied once: the system dialog can still be shown.
            !prefs.getBoolean(KEY_ASKED, false) ||
                LOCATION_PERMISSIONS.any { activity.shouldShowRequestPermissionRationale(it) } -> {
                prefs.edit().putBoolean(KEY_ASKED, true).apply()
                centerAfterGrant = true
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            }
            // Permanently denied: only system settings can change it.
            else -> scope.launch {
                val r = snackbar.showSnackbar(msgPermission, actionLabel = msgSettings, duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }
            }
        }
    }

    BackHandler(enabled = state.search.active) { vm.onSearchActiveChange(false) }

    val topInsetPx = with(density) { (WindowInsets.statusBars.getTop(this) + 88.dp.roundToPx()) }
    val bottomInsetPx = WindowInsets.navigationBars.getBottom(density)

    Box(Modifier.fillMaxSize()) {
        ParkingMap(
            displayGeoJson = state.displayGeoJson,
            selectedId = state.selectedId,
            searchPin = state.searchPin,
            locationPermitted = locationGranted,
            cameraCommands = vm.cameraCommands,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
            onSegmentClick = vm::onSegmentTapped,
            onEmptyClick = vm::onMapTappedEmpty,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchBox(
                state = state.search,
                onQueryChange = vm::onQueryChange,
                onActiveChange = vm::onSearchActiveChange,
                onSubmit = vm::onSearchSubmit,
                onClear = vm::clearSearch,
                onInfo = { vm.showSheet(Sheet.About) },
                onSegment = vm::openSegment,
                onAddress = vm::onAddressSelected,
                statusOf = vm::effectiveStatus,
            )
            val ds = state.dataset
            AnimatedVisibility(!state.search.active && ds != null && ds.unmappedSegments.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                if (ds != null) {
                    InfoChip(
                        text = stringResource(R.string.chip_unmapped, ds.unmappedSegments.size, ds.segments.size),
                        onClick = { vm.showSheet(Sheet.AllSegments) },
                    )
                }
            }
            AnimatedVisibility(state.loadFailed) {
                InfoChip(text = stringResource(R.string.load_failed), onClick = vm::load)
            }
        }

        Legend(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 40.dp),
        )

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { vm.showSheet(Sheet.AllSegments) },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.cd_all_locations))
            }
            FloatingActionButton(
                onClick = ::onLocationClick,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(painterResource(R.drawable.ic_my_location), stringResource(R.string.cd_my_location))
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
    }

    // --- Bottom sheets -----------------------------------------------------------------------
    val sheet = state.sheet
    val dataset = state.dataset
    if (sheet != null && dataset != null) {
        ModalBottomSheet(
            onDismissRequest = vm::dismissSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = sheet !is Sheet.Segment),
            scrimColor = Color.Black.copy(alpha = 0.12f),
        ) {
            when (sheet) {
                is Sheet.Segment -> vm.segment(sheet.id)?.let { seg ->
                    SegmentDetail(
                        segment = seg,
                        status = vm.effectiveStatus(seg),
                        chargingNow = vm.evaluator.chargingNow(seg, state.now),
                        now = state.now,
                        dataset = dataset,
                    )
                }
                is Sheet.Nearby -> NearbyContent(sheet, vm::effectiveStatus, vm::openSegment)
                Sheet.AllSegments -> AllSegmentsContent(dataset, vm::effectiveStatus, vm::openSegment)
                Sheet.About -> AboutContent(dataset)
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private const val KEY_ASKED = "location_permission_asked"

private fun hasLocationPermission(context: Context) = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun isLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(lm)
}
