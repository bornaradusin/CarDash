package io.github.bornaradusin.cardash.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.view.Gravity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.bornaradusin.cardash.nav.Geo
import io.github.bornaradusin.cardash.nav.NavState
import io.github.bornaradusin.cardash.nav.OfflineMaps
import io.github.bornaradusin.cardash.nav.Pos
import io.github.bornaradusin.cardash.nav.Route
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** What the map needs from outside, kept between recompositions. */
private class MapHolder {
    var map: MapLibreMap? = null
    var style: Style? = null
    var view: MapView? = null
    var ready by mutableStateOf(false)
}

/**
 * The 3D map: tilted, turning with the car, the route in the accent colour and an arrow at the next
 * turn. The car is the arrow drawn on top, a little below the middle, so more of the road ahead shows.
 */
@Composable
fun NavMap(nav: NavState, modifier: Modifier = Modifier) {
    val holder = remember { MapHolder() }
    val accent = C.red
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    holder.view = mv
                    mv.onCreate(null)
                    mv.getMapAsync { map ->
                        holder.map = map
                        map.uiSettings.apply {
                            setAllGesturesEnabled(false)
                            isCompassEnabled = false
                            isLogoEnabled = false
                            isAttributionEnabled = true
                            attributionGravity = Gravity.BOTTOM or Gravity.END
                        }
                        map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(53.35, -7.9)).zoom(6.0).build()))
                        map.setStyle(Style.Builder().fromUri(OfflineMaps.STYLE)) { style ->
                            holder.style = style
                            addLayers(style)
                            holder.ready = true
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // The car. This layer also keeps touches off the map view, so swipes and holds reach the middle box
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
            if (nav.here == null) return@Canvas
            val c = Offset(size.width / 2, size.height * CAR_Y)
            drawCircle(accent.copy(alpha = 0.28f), 26.dp.toPx(), c)
            val s = 15.dp.toPx()
            val arrow = Path().apply {
                moveTo(c.x, c.y - s)
                lineTo(c.x + s * 0.75f, c.y + s * 0.8f)
                lineTo(c.x, c.y + s * 0.35f)
                lineTo(c.x - s * 0.75f, c.y + s * 0.8f)
                close()
            }
            drawPath(arrow, Color.White)
        }
    }

    // The map view's own life follows the screen's
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            val v = holder.view ?: return@LifecycleEventObserver
            when (e) {
                Lifecycle.Event.ON_START -> v.onStart()
                Lifecycle.Event.ON_RESUME -> v.onResume()
                Lifecycle.Event.ON_PAUSE -> v.onPause()
                Lifecycle.Event.ON_STOP -> v.onStop()
                else -> Unit
            }
        }
        holder.view?.let {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) it.onStart()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) it.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.view?.let {
                it.onPause()
                it.onStop()
                it.onDestroy()
            }
            holder.view = null
            holder.map = null
            holder.style = null
        }
    }

    LaunchedEffect(holder.ready, accent) {
        val style = holder.style ?: return@LaunchedEffect
        (style.getLayer(ROUTE) as? LineLayer)?.setProperties(PropertyFactory.lineColor(accent.toArgb()))
    }
    LaunchedEffect(holder.ready, nav.route) {
        val style = holder.style ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(ROUTE_SRC) ?: return@LaunchedEffect
        val r = nav.route
        if (r == null) src.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        else src.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(r.points.map { Point.fromLngLat(it.lon, it.lat) })))
    }
    LaunchedEffect(holder.ready, nav.route, nav.next) {
        val style = holder.style ?: return@LaunchedEffect
        val line = style.getSourceAs<GeoJsonSource>(TURN_SRC) ?: return@LaunchedEffect
        val head = style.getSourceAs<GeoJsonSource>(HEAD_SRC) ?: return@LaunchedEffect
        val r = nav.route
        val step = nav.nextStep
        val piece = if (r != null && step != null && step.type != "arrive") turnPiece(r, step.index) else null
        if (piece == null || piece.size < 2) {
            line.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            head.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            line.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(piece.map { Point.fromLngLat(it.lon, it.lat) })))
            val end = piece.last()
            val f = Feature.fromGeometry(Point.fromLngLat(end.lon, end.lat))
            f.addNumberProperty("bearing", Geo.bearing(piece[piece.size - 2], end))
            head.setGeoJson(f)
        }
    }
    LaunchedEffect(holder.ready, nav.here, nav.heading) {
        val map = holder.map ?: return@LaunchedEffect
        val here = nav.here ?: return@LaunchedEffect
        val h = (holder.view?.height ?: 0).toDouble()
        // Closer in town, further out on the motorway
        val kmh = nav.speed * 3.6f
        val zoom = when {
            kmh > 100 -> 15.2
            kmh > 60 -> 15.8
            else -> 16.6
        }
        val pos = CameraPosition.Builder()
            .target(LatLng(here.lat, here.lon))
            .bearing((nav.heading ?: map.cameraPosition.bearing.toFloat()).toDouble())
            .tilt(60.0)
            .zoom(zoom)
            .padding(0.0, h * (2 * CAR_Y - 1), 0.0, 0.0)
            .build()
        map.easeCamera(CameraUpdateFactory.newCameraPosition(pos), 900)
    }
}

/** Where the car sits on the map, from the top. */
private const val CAR_Y = 0.72f

private const val ROUTE_SRC = "cardash-route"
private const val ROUTE = "cardash-route"
private const val ROUTE_CASE = "cardash-route-case"
private const val TURN_SRC = "cardash-turn"
private const val HEAD_SRC = "cardash-turn-head"
private const val ARROW = "cardash-arrow"

/** The route, the turn arrow, and buildings raised in 3D, all under the street names. */
private fun addLayers(style: Style) {
    val labels = style.layers.firstOrNull { it is SymbolLayer }?.id
    fun add(layer: org.maplibre.android.style.layers.Layer) {
        runCatching { if (labels != null) style.addLayerBelow(layer, labels) else style.addLayer(layer) }
    }
    runCatching {
        add(
            FillExtrusionLayer("cardash-3d", "openmaptiles").withSourceLayer("building").withProperties(
                PropertyFactory.fillExtrusionColor(android.graphics.Color.parseColor("#1D222B")),
                PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
                PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
                PropertyFactory.fillExtrusionOpacity(0.9f),
            ),
        )
    }
    style.addSource(GeoJsonSource(ROUTE_SRC))
    style.addSource(GeoJsonSource(TURN_SRC))
    style.addSource(GeoJsonSource(HEAD_SRC))
    add(
        LineLayer(ROUTE_CASE, ROUTE_SRC).withProperties(
            PropertyFactory.lineColor(android.graphics.Color.parseColor("#5A1B24")),
            PropertyFactory.lineWidth(14f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.6f),
        ),
    )
    add(
        LineLayer(ROUTE, ROUTE_SRC).withProperties(
            PropertyFactory.lineColor(C.red.toArgb()),
            PropertyFactory.lineWidth(8f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    // The turn arrow goes over everything
    runCatching {
        style.addLayer(
            LineLayer("cardash-turn", TURN_SRC).withProperties(
                PropertyFactory.lineColor(android.graphics.Color.WHITE),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addImage(ARROW, arrowHead())
        style.addLayer(
            SymbolLayer("cardash-turn-head", HEAD_SRC).withProperties(
                PropertyFactory.iconImage(ARROW),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }
}

/** A white arrow head, pointing up (north) before it's turned. */
private fun arrowHead(): Bitmap {
    val b = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val path = android.graphics.Path().apply {
        moveTo(20f, 4f)
        lineTo(36f, 34f)
        lineTo(4f, 34f)
        close()
    }
    c.drawPath(path, p)
    return b
}

/** The route from a little before the turn to a little after it. */
private fun turnPiece(r: Route, index: Int): List<Pos>? {
    val pts = r.points
    if (index <= 0 || index >= pts.size) return null
    val at = r.cum[index]
    val from = at - 35.0
    val to = at + 28.0
    val out = ArrayList<Pos>()
    for (i in 0 until pts.size - 1) {
        val a = r.cum[i]
        val b = r.cum[i + 1]
        if (b < from || a > to) continue
        val seg = b - a
        val p0 = if (a < from && seg > 0) Geo.along(pts[i], pts[i + 1], (from - a) / seg) else pts[i]
        val p1 = if (b > to && seg > 0) Geo.along(pts[i], pts[i + 1], (to - a) / seg) else pts[i + 1]
        if (out.isEmpty()) out += p0
        out += p1
    }
    return out
}
