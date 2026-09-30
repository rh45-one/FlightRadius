package com.flightradius.app.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.PathParser
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.R
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.NearbyAircraft
import com.flightradius.app.ui.components.GroupedDivider
import com.flightradius.app.ui.detail.DetailKind
import com.flightradius.app.ui.components.GroupedRow
import com.flightradius.app.ui.components.GroupedSection
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.format.PLANE_PATH_DATA
import com.flightradius.app.ui.format.labelRes
import com.flightradius.app.ui.theme.CodeFeatures
import com.flightradius.app.ui.theme.NumericFeatures
import com.flightradius.app.ui.theme.extended
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconColor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.color as colorExpr
import org.maplibre.android.style.sources.GeoJsonSource

private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

private const val SRC_AIRCRAFT = "fr-aircraft"
private const val SRC_USER = "fr-user"
private const val SRC_TRACKED_RADIUS = "fr-tracked-radius"
private const val SRC_AIRSPACE = "fr-airspace"
private const val LAYER_AIRCRAFT = "fr-aircraft-layer"
private const val PLANE_IMAGE = "fr-plane"


private fun Color.hex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

private fun planeBitmap(): Bitmap {
    val size = 72
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val path = PathParser.createPathFromPathData(PLANE_PATH_DATA)
    path.transform(Matrix().apply { setScale(size / 24f, size / 24f) })
    Canvas(bmp).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    return bmp
}

private class MapColors(
    val tracked: String, val match: String, val nearby: String,
    val user: String, val userStroke: String, val halo: String, val text: String
)

private fun installStyle(style: Style, c: MapColors) {
    style.addImage(PLANE_IMAGE, planeBitmap(), true)
    style.addSource(GeoJsonSource(SRC_TRACKED_RADIUS, MapGeo.EMPTY))
    style.addSource(GeoJsonSource(SRC_AIRSPACE, MapGeo.EMPTY))
    style.addSource(GeoJsonSource(SRC_USER, MapGeo.EMPTY))
    style.addSource(GeoJsonSource(SRC_AIRCRAFT, MapGeo.EMPTY))

    style.addLayer(
        FillLayer("fr-tracked-radius-fill", SRC_TRACKED_RADIUS)
            .withProperties(fillColor(c.tracked), fillOpacity(0.08f))
    )
    style.addLayer(
        LineLayer("fr-tracked-radius-line", SRC_TRACKED_RADIUS)
            .withProperties(lineColor(c.tracked), lineWidth(2f))
    )
    style.addLayer(
        LineLayer("fr-airspace-line", SRC_AIRSPACE)
            .withProperties(
                lineColor(c.nearby), lineWidth(1.5f),
                lineDasharray(arrayOf(3f, 3f))
            )
    )
    style.addLayer(
        CircleLayer("fr-user-layer", SRC_USER).withProperties(
            circleRadius(7f), circleColor(c.user),
            circleStrokeColor(c.userStroke), circleStrokeWidth(3f)
        )
    )
    val kindColor = match(
        get("kind"),
        colorExpr(android.graphics.Color.parseColor(c.nearby)),
        org.maplibre.android.style.expressions.Expression.stop("TRACKED",
            colorExpr(android.graphics.Color.parseColor(c.tracked))),
        org.maplibre.android.style.expressions.Expression.stop("MATCH",
            colorExpr(android.graphics.Color.parseColor(c.match)))
    )
    // ~26 dp planes, ~32 dp for rule matches (the glyph is 72 px at scale 1).
    fun planeSize(scale: Float) = match(
        get("kind"),
        literal(0.9f * scale),
        org.maplibre.android.style.expressions.Expression.stop("MATCH", literal(1.1f * scale))
    )
    // Halo: a slightly larger copy underneath so planes read on light and dark tiles.
    style.addLayer(
        SymbolLayer("fr-aircraft-halo", SRC_AIRCRAFT).withProperties(
            iconImage(PLANE_IMAGE), iconSize(planeSize(1.3f)), iconColor(c.halo),
            iconRotate(get("rot")), iconRotationAlignment("map"),
            iconAllowOverlap(true), iconIgnorePlacement(true)
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_AIRCRAFT, SRC_AIRCRAFT).withProperties(
            iconImage(PLANE_IMAGE), iconSize(planeSize(1f)), iconColor(kindColor),
            iconRotate(get("rot")), iconRotationAlignment("map"),
            iconAllowOverlap(true), iconIgnorePlacement(true),
            textField(get("label")), textFont(arrayOf("Noto Sans Regular")),
            textSize(12f), textAnchor("top"), textOffset(arrayOf(0f, 1.6f)),
            textColor(c.text), textHaloColor(c.halo), textHaloWidth(1.5f),
            textOptional(true), textAllowOverlap(false)
        )
    )
}

@Composable
fun MapScreen(
    onOpenDetail: (DetailKind, String) -> Unit = { _, _ -> },
    viewModel: MapViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val focus by viewModel.focusRequest.collectAsStateWithLifecycle()
    MapScreenContent(snapshot, settings, focus, viewModel::consumeFocus, onOpenDetail)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapScreenContent(
    snapshot: MonitoringSnapshot?,
    settings: AppSettings,
    focusRequest: String? = null,
    onFocusConsumed: () -> Unit = {},
    onOpenDetail: (DetailKind, String) -> Unit = { _, _ -> }
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val colors = MapColors(
        tracked = scheme.primary.hex(),
        match = MaterialTheme.colorScheme.extended.danger.hex(),
        nearby = (if (dark) Color(0xFFD1D1D6) else Color(0xFF3A3A3C)).hex(),
        user = scheme.primary.hex(),
        userStroke = "#FFFFFF",
        halo = (if (dark) Color(0xFF000000) else Color(0xFFFFFFFF)).hex(),
        text = (if (dark) Color(0xFFFFFFFF) else Color(0xFF000000)).hex()
    )

    val mapView = remember { MapView(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var fitted by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycle, mapView) {
        var created = false
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> if (!created) { mapView.onCreate(null); created = true }
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                Lifecycle.Event.ON_DESTROY -> Unit
                else -> Unit
            }
        }
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = mapView.onLowMemory()
            @Deprecated("Deprecated in Java")
            override fun onTrimMemory(level: Int) = Unit
        }
        lifecycle.addObserver(observer)
        context.registerComponentCallbacks(callbacks)
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(callbacks)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            if (created) mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { failed = true }
        mapView.addOnDidFinishLoadingStyleListener { failed = false }
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isAttributionEnabled = true
            m.uiSettings.isLogoEnabled = true
            m.addOnMapClickListener { latLng ->
                val p = m.projection.toScreenLocation(latLng)
                val box = RectF(p.x - 40f, p.y - 40f, p.x + 40f, p.y + 40f)
                val hit = m.queryRenderedFeatures(box, LAYER_AIRCRAFT).firstOrNull()
                selected = hit?.getStringProperty("id")
                hit != null
            }
        }
    }

    LaunchedEffect(map, dark) {
        val m = map ?: return@LaunchedEffect
        styleReady = false
        m.setStyle(if (dark) STYLE_DARK else STYLE_LIGHT) { style ->
            installStyle(style, colors)
            styleReady = true
        }
    }

    val aircraftJson = remember(snapshot) { snapshot?.let { MapGeo.aircraftJson(MapGeo.aircraft(it)) } ?: MapGeo.EMPTY }
    LaunchedEffect(snapshot, styleReady, settings.globalAlertRadiusKm) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect
        val snap = snapshot
        style.getSourceAs<GeoJsonSource>(SRC_AIRCRAFT)?.setGeoJson(aircraftJson)
        if (snap == null) return@LaunchedEffect
        val fix = snap.fix
        style.getSourceAs<GeoJsonSource>(SRC_USER)?.setGeoJson(MapGeo.pointJson(fix.lat, fix.lon))
        val trackedRadius = snap.closest?.effectiveRadiusKm ?: settings.globalAlertRadiusKm
        style.getSourceAs<GeoJsonSource>(SRC_TRACKED_RADIUS)
            ?.setGeoJson(MapGeo.polygonJson(MapGeo.circle(fix.lat, fix.lon, trackedRadius)))
        style.getSourceAs<GeoJsonSource>(SRC_AIRSPACE)?.setGeoJson(
            snap.airspaceRadiusKm?.let { MapGeo.lineJson(MapGeo.circle(fix.lat, fix.lon, it)) }
                ?: MapGeo.EMPTY)
        if (!fitted) {
            fitted = true
            m.moveCamera(fitUpdate(snap))
        }
    }

    LaunchedEffect(focusRequest, snapshot, styleReady) {
        val key = focusRequest ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val target = snapshot?.let { MapGeo.aircraft(it) }?.firstOrNull { it.id == key }
        if (target != null) {
            m.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lon), 11.0), 600)
            selected = null
            fitted = true
        }
        onFocusConsumed()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        if (failed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(50),
                color = scheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                Text(
                    stringResource(R.string.map_unavailable),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        if (snapshot == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                shape = MaterialTheme.shapes.medium,
                color = scheme.surfaceContainerHigh
            ) {
                Text(
                    stringResource(R.string.map_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 44.dp),
            shape = RoundedCornerShape(50),
            color = scheme.surface.copy(alpha = 0.82f)
        ) {
            Text(
                stringResource(R.string.map_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        SmallFloatingActionButton(
            onClick = {
                val snap = snapshot ?: return@SmallFloatingActionButton
                map?.animateCamera(fitUpdate(snap), 600)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 92.dp),
            containerColor = scheme.surface,
            contentColor = scheme.primary
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.map_recenter))
        }
    }

    selected?.let { id ->
        val snap = snapshot
        val tracked = if (id.startsWith("t:")) snap?.ranked?.firstOrNull { "t:${it.aircraftId}" == id } else null
        val nearby = if (id.startsWith("n:")) snap?.nearby?.firstOrNull { "n:${it.icao24}" == id } else null
        if (tracked != null || nearby != null) {
            ModalBottomSheet(
                onDismissRequest = { selected = null },
                containerColor = scheme.background,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                MapAircraftSheet(tracked, nearby, settings.distanceUnit, onDetails = {
                    selected = null
                    if (tracked != null) onOpenDetail(DetailKind.TRACKED, tracked.aircraftId.toString())
                    else if (nearby != null) onOpenDetail(DetailKind.NEARBY, nearby.icao24)
                })
            }
        }
    }
}

private fun fitUpdate(snap: MonitoringSnapshot) =
    snap.fix.let { fix ->
        val radius = maxOf(
            snap.airspaceRadiusKm ?: 0.0,
            snap.closest?.distanceKm ?: 0.0,
            10.0
        )
        val b = MapGeo.bounds(fix.lat, fix.lon, radius * 1.1)
        CameraUpdateFactory.newLatLngBounds(
            LatLngBounds.from(b.north, b.east, b.south, b.west), 48)
    }

@Composable
private fun MapAircraftSheet(
    tracked: AircraftObservation?,
    nearby: NearbyAircraft?,
    unit: DistanceUnit,
    onDetails: () -> Unit
) {
    val title = tracked?.let { Format.callsign(it) } ?: nearby!!.displayName
    val rows = buildList {
        if (nearby != null) add(stringResource(R.string.detail_class) to stringResource(nearby.cls.labelRes()))
        val distanceKm = tracked?.distanceKm ?: nearby!!.distanceKm
        add(stringResource(R.string.detail_distance) to Format.distance(distanceKm, unit))
        val bearing = tracked?.bearingDeg ?: nearby!!.bearingDeg
        add(stringResource(R.string.detail_direction) to Format.bearingShort(bearing))
        Format.altitude(tracked?.altitudeM ?: nearby?.altitudeM, unit)?.let {
            add(stringResource(R.string.detail_altitude) to it)
        }
        Format.speed(tracked?.velocityMps ?: nearby?.velocityMps, unit)?.let {
            add(stringResource(R.string.detail_speed) to it)
        }
        nearby?.registration?.let { add(stringResource(R.string.detail_registration) to it) }
        nearby?.model?.let { add(stringResource(R.string.detail_model) to it) }
            ?: nearby?.typecode?.let { add(stringResource(R.string.detail_model) to it) }
    }
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = CodeFeatures)
        )
        GroupedSection(header = null) {
            rows.forEachIndexed { i, (label, value) ->
                if (i > 0) GroupedDivider()
                GroupedRow(
                    title = label,
                    titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleStyle = MaterialTheme.typography.bodyMedium,
                    trailing = {
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFeatureSettings = NumericFeatures)
                        )
                    }
                )
            }
        }
        androidx.compose.material3.TextButton(onClick = onDetails) {
            Text(stringResource(R.string.map_details))
        }
        Spacer(Modifier.height(24.dp))
    }
}
