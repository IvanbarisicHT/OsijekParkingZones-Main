package hr.ibarisic.osijekparking.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hr.ibarisic.osijekparking.BuildConfig
import hr.ibarisic.osijekparking.domain.GeoUtils
import hr.ibarisic.osijekparking.domain.LatLon
import hr.ibarisic.osijekparking.ui.theme.ParkingColors
import kotlinx.coroutines.flow.Flow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.any
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.geometryType
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Compose wrapper around a MapLibre [MapView]. All parking data arrives as GeoJSON strings;
 * nothing here knows about specific streets.
 */
@Composable
fun ParkingMap(
    displayGeoJson: String,
    selectedId: String?,
    searchPin: LatLon?,
    locationPermitted: Boolean,
    cameraCommands: Flow<CameraCommand>,
    topInsetPx: Int,
    bottomInsetPx: Int,
    onSegmentClick: (String) -> Unit,
    onEmptyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }
    var controller by remember { mutableStateOf<MapController?>(null) }
    val onSegment by rememberUpdatedState(onSegmentClick)
    val onEmpty by rememberUpdatedState(onEmptyClick)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(GeoUtils.OSIJEK_CENTER.lat, GeoUtils.OSIJEK_CENTER.lon))
                .zoom(14.2)
                .build()
            map.uiSettings.isRotateGesturesEnabled = true
            map.uiSettings.isTiltGesturesEnabled = false
            map.setStyle(Style.Builder().fromUri(BuildConfig.MAP_STYLE_URL)) { style ->
                controller = MapController(context, map, style, density.density).also { c ->
                    map.addOnMapClickListener { latLng ->
                        val id = c.segmentAt(latLng)
                        if (id != null) onSegment(id) else onEmpty()
                        true
                    }
                }
            }
        }
        onDispose { }
    }

    val c = controller
    LaunchedEffect(c, topInsetPx, bottomInsetPx) { c?.setInsets(topInsetPx, bottomInsetPx) }
    LaunchedEffect(c, displayGeoJson) { c?.setSegments(displayGeoJson) }
    LaunchedEffect(c, selectedId) { c?.setSelected(selectedId) }
    LaunchedEffect(c, searchPin) { c?.setSearchPin(searchPin) }
    LaunchedEffect(c, locationPermitted) { if (locationPermitted) c?.enableLocation() }
    LaunchedEffect(c) { c?.let { ctl -> cameraCommands.collect(ctl::apply) } }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private class MapController(
    private val context: Context,
    private val map: MapLibreMap,
    private val style: Style,
    private val density: Float,
) {
    private val segments = GeoJsonSource(SRC_SEGMENTS, DisplayGeoJson.EMPTY)
    private val pin = GeoJsonSource(SRC_PIN, DisplayGeoJson.EMPTY)
    private var locationActive = false
    private var pendingTrack = false

    init {
        style.addSource(segments)
        style.addSource(pin)
        addLayers()
    }

    private fun addLayers() {
        val isLine = any(eq(geometryType(), literal("LineString")), eq(geometryType(), literal("MultiLineString")))
        val isPolygon = any(eq(geometryType(), literal("Polygon")), eq(geometryType(), literal("MultiPolygon")))
        val isPoint = any(eq(geometryType(), literal("Point")), eq(geometryType(), literal("MultiPoint")))
        val planned = eq(get(DisplayGeoJson.PROP_PLANNED), literal(true))
        val notPlanned = eq(get(DisplayGeoJson.PROP_PLANNED), literal(false))
        val width = Expression.interpolate(
            Expression.linear(), Expression.zoom(),
            stop(12, 2.5f), stop(15, 5f), stop(18, 10f),
        )
        val casingWidth = Expression.interpolate(
            Expression.linear(), Expression.zoom(),
            stop(12, 4f), stop(15, 7.5f), stop(18, 14f),
        )
        val selectedWidth = Expression.interpolate(
            Expression.linear(), Expression.zoom(),
            stop(12, 9f), stop(15, 13f), stop(18, 22f),
        )

        style.addLayer(FillLayer(L_FILL, SRC_SEGMENTS).withFilter(isPolygon).withProperties(
            fillColor(categoryColor()), fillOpacity(0.32f),
        ))
        style.addLayer(LineLayer(L_SELECTED, SRC_SEGMENTS).withFilter(eq(get(DisplayGeoJson.PROP_ID), literal(""))).withProperties(
            lineColor(0xFF1B1B1F.toInt()), lineOpacity(0.85f), lineWidth(selectedWidth),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer(L_CASING, SRC_SEGMENTS).withFilter(Expression.all(isLine, notPlanned)).withProperties(
            lineColor(0xFFFFFFFF.toInt()), lineWidth(casingWidth),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer(L_LINE, SRC_SEGMENTS).withFilter(Expression.all(any(isLine, isPolygon), notPlanned)).withProperties(
            lineColor(categoryColor()), lineWidth(width),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(LineLayer(L_LINE_PLANNED, SRC_SEGMENTS).withFilter(Expression.all(any(isLine, isPolygon), planned)).withProperties(
            lineColor(categoryColor()), lineWidth(width), lineOpacity(0.85f),
            lineDasharray(arrayOf(1.2f, 1.2f)), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(CircleLayer(L_POINT, SRC_SEGMENTS).withFilter(isPoint).withProperties(
            circleColor(categoryColor()), circleRadius(7f),
            circleStrokeColor(Expression.switchCase(eq(get(DisplayGeoJson.PROP_ID), literal("")), Expression.color(0xFF1B1B1F.toInt()), Expression.color(0xFFFFFFFF.toInt()))),
            circleStrokeWidth(2.5f),
        ))
        // Invisible, wide hit target so thin lines are easy to tap.
        style.addLayer(LineLayer(L_HIT, SRC_SEGMENTS).withFilter(any(isLine, isPolygon)).withProperties(
            lineColor(0xFF000000.toInt()), lineOpacity(0.01f), lineWidth(28f),
        ))
        style.addLayer(CircleLayer(L_PIN, SRC_PIN).withProperties(
            circleColor(0xFF1F4E9D.toInt()), circleRadius(9f),
            circleStrokeColor(0xFFFFFFFF.toInt()), circleStrokeWidth(3f),
        ))
    }

    private fun categoryColor(): Expression = Expression.match(
        get(DisplayGeoJson.PROP_CATEGORY),
        Expression.color(ParkingColors.Unknown.toArgb()),
        *MapCategory.entries.map { stop(it.name, Expression.color(ParkingColors.of(it).toArgb())) }.toTypedArray(),
    )

    fun setSegments(geoJson: String) = segments.setGeoJson(geoJson)

    fun setSelected(id: String?) {
        val filter = eq(get(DisplayGeoJson.PROP_ID), literal(id ?: ""))
        (style.getLayer(L_SELECTED) as? LineLayer)?.setFilter(filter)
        (style.getLayer(L_POINT) as? CircleLayer)?.setProperties(
            circleStrokeColor(Expression.switchCase(filter, Expression.color(0xFF1B1B1F.toInt()), Expression.color(0xFFFFFFFF.toInt()))),
        )
    }

    fun setSearchPin(p: LatLon?) = pin.setGeoJson(DisplayGeoJson.point(p))

    fun setInsets(topPx: Int, bottomPx: Int) {
        val gap = (8 * density).toInt()
        map.uiSettings.setCompassMargins(0, topPx + gap, gap * 2, 0)
        map.uiSettings.setLogoMargins(gap, 0, 0, bottomPx + gap)
        map.uiSettings.setAttributionMargins((92 * density).toInt(), 0, 0, bottomPx + gap)
    }

    fun segmentAt(latLng: LatLng): String? {
        val p = map.projection.toScreenLocation(latLng)
        val r = 10 * density
        return map.queryRenderedFeatures(RectF(p.x - r, p.y - r, p.x + r, p.y + r), L_POINT, L_HIT, L_FILL)
            .firstNotNullOfOrNull { it.getStringProperty(DisplayGeoJson.PROP_ID) }
    }

    @SuppressLint("MissingPermission") // Only called after the permission is granted.
    fun enableLocation() {
        if (locationActive) return
        map.locationComponent.apply {
            activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .useDefaultLocationEngine(true)
                    .build(),
            )
            isLocationComponentEnabled = true
            renderMode = RenderMode.COMPASS
            cameraMode = CameraMode.NONE
        }
        locationActive = true
        if (pendingTrack) apply(CameraCommand.TrackUser)
    }

    fun apply(cmd: CameraCommand) {
        when (cmd) {
            is CameraCommand.MoveTo -> map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(cmd.position.lat, cmd.position.lon), cmd.zoom), 700,
            )
            is CameraCommand.FitGeometry -> {
                val b = GeoUtils.bounds(cmd.geometry)
                val update = if (b.north - b.south < 1e-5 && b.east - b.west < 1e-5) {
                    CameraUpdateFactory.newLatLngZoom(LatLng(b.north, b.east), 17.5)
                } else {
                    val pad = (72 * density).toInt()
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder().include(LatLng(b.south, b.west)).include(LatLng(b.north, b.east)).build(),
                        pad, pad, pad, (320 * density).toInt(),
                    )
                }
                map.animateCamera(update, 700)
            }
            CameraCommand.TrackUser -> if (locationActive) {
                pendingTrack = false
                map.locationComponent.setCameraMode(CameraMode.TRACKING, 750L, 16.5, null, null, null)
            } else {
                pendingTrack = true
            }
        }
    }

    companion object {
        const val SRC_SEGMENTS = "parking-segments"
        const val SRC_PIN = "search-pin"
        const val L_FILL = "parking-fill"
        const val L_SELECTED = "parking-selected"
        const val L_CASING = "parking-casing"
        const val L_LINE = "parking-line"
        const val L_LINE_PLANNED = "parking-line-planned"
        const val L_POINT = "parking-point"
        const val L_HIT = "parking-hit"
        const val L_PIN = "search-pin-layer"
    }
}
