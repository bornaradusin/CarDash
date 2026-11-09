package io.github.bornaradusin.cardash.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PointF
import android.view.Gravity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.bornaradusin.cardash.R
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
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point

/** What the map needs from outside, kept between recompositions. */
private class MapHolder {
    var map: MapLibreMap? = null
    var style: Style? = null
    var view: MapView? = null
    var ready by mutableStateOf(false)

    /** The camera follows the car; off once the map's been moved by hand (on a map you can move). */
    var following by mutableStateOf(true)
    /** When the map was last moved by hand. */
    var touchedAt by mutableStateOf(0L)

    /** The direction of the road at the car, from the last GPS fix. */
    var roadBearing: Double? = null

    /** Where the car is (on the road), from the last GPS fix. */
    var carAt: Pos? = null

    /** Where the car is drawn on the map view, in pixels: under the camera while following, else wherever it is on the map. */
    var carScreen by mutableStateOf<PointF?>(null)

    /** The car's place on the view: a little below the middle while following, else wherever the map has it. */
    fun carPoint(): PointF? {
        val v = view ?: return null
        if (v.width == 0 || v.height == 0) return null
        if (following) return PointF(v.width / 2f, v.height * CAR_Y)
        val at = carAt ?: return null
        return map?.projection?.toScreenLocation(LatLng(at.lat, at.lon))
    }

    /** How far the car picture is turned so it lines up with the road on screen, in degrees. */
    var carAngle by mutableStateOf(0f)

    /**
     * Turns the car to the road as it's drawn under it: a point a few metres up the road, put on the
     * screen, gives the angle the road runs at there (the tilt and any camera turn still under way
     * included). Called every frame the map moves.
     */
    fun alignCar() {
        val map = map ?: return
        val car = carPoint() ?: return
        carScreen = car
        val brg = roadBearing ?: return
        val under = map.projection.fromScreenLocation(car)
        val ahead = Geo.offset(Pos(under.latitude, under.longitude), brg, 12.0)
        val p = map.projection.toScreenLocation(LatLng(ahead.lat, ahead.lon))
        val dx = p.x - car.x
        val dy = car.y - p.y
        if (dx * dx + dy * dy < 4f) return
        val raw = Math.toDegrees(kotlin.math.atan2(dx, dy).toDouble()).toFloat()
        // The shortest way round from where it is, so it never spins the long way past 180
        carAngle += ((raw - carAngle) % 360 + 540) % 360 - 180
    }
}

/**
 * The 3D map: tilted, turning with the car, the route in the accent colour and an arrow at the next
 * turn. The car is the TT from behind, drawn on top a little below the middle, so more of the road ahead shows.
 *
 * [interactive] (the map dash): moved with a finger and zoomed with two. The map stops following the
 * car then; **Follow the car** puts it back, and so does leaving it alone for 20 seconds. Otherwise the
 * map takes no touches, so swipes reach the middle box around it.
 */
@Composable
fun NavMap(nav: NavState, modifier: Modifier = Modifier, interactive: Boolean = false) {
    val holder = remember { MapHolder() }
    val accent = C.red
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val px = LocalDensity.current.density

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    holder.view = mv
                    mv.onCreate(null)
                    mv.getMapAsync { map ->
                        holder.map = map
                        map.addOnCameraMoveListener { holder.alignCar() }
                        // Moved by hand: stop following the car for now
                        map.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                holder.following = false
                                holder.touchedAt = System.currentTimeMillis()
                            }
                        }
                        map.uiSettings.apply {
                            setAllGesturesEnabled(false)
                            if (interactive) {
                                isScrollGesturesEnabled = true
                                isZoomGesturesEnabled = true
                                isRotateGesturesEnabled = true
                                isDoubleTapGesturesEnabled = true
                            }
                            isCompassEnabled = false
                            isLogoEnabled = false
                            // The map's credits (OpenStreetMap, OpenFreeMap): a small grey "i" above the
                            // Where to? button and the arrival times, not blue and half hidden under them
                            isAttributionEnabled = true
                            attributionGravity = Gravity.BOTTOM or Gravity.END
                            setAttributionTintColor(android.graphics.Color.argb(150, 154, 163, 178))
                            setAttributionMargins(0, 0, (12 * px).toInt(), (78 * px).toInt())
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
        // Dark until the map's style is in, then faded away: no white flash while it starts
        val cover by animateFloatAsState(if (holder.ready) 0f else 1f, tween(300), label = "map cover")
        if (cover > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = cover }.background(C.bg))
        // The car, seen from behind, a little below the middle (where the camera puts it while following).
        // On a map you can't move, this layer also keeps touches off the map view, so swipes and holds
        // reach the middle box
        val catcher = if (interactive) Modifier else Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
        BoxWithConstraints(Modifier.fillMaxSize().then(catcher)) {
            if (nav.here != null) {
                val w = 39.dp
                val h = w * (214f / 256f)
                // Turned with the road under it; eased, so a new GPS fix on a bend doesn't make it twitch
                val angle by animateFloatAsState(holder.carAngle, spring(stiffness = Spring.StiffnessMediumLow), label = "car angle")
                val place = if (holder.following) {
                    Modifier.align(Alignment.TopCenter).offset(y = maxHeight * CAR_Y - h / 2)
                } else {
                    // Looking around: the car stays where it is on the map, moving with it
                    Modifier.offset {
                        val p = holder.carScreen ?: PointF(-1000f, -1000f)
                        androidx.compose.ui.unit.IntOffset((p.x - w.toPx() / 2).toInt(), (p.y - h.toPx() / 2).toInt())
                    }
                }
                Image(
                    painterResource(R.drawable.car_marker), "Your car",
                    modifier = place.width(w).height(h).graphicsLayer { rotationZ = angle },
                )
            }
        }
        // Moved by hand: back to following the car
        if (interactive && !holder.following) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).height(52.dp).clip(RoundedCornerShape(26.dp))
                    .background(C.panel.copy(alpha = 0.94f)).border(1.dp, C.lineHi, RoundedCornerShape(26.dp))
                    .clickable { holder.following = true }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.Icon(Ic.map, null, tint = C.red, modifier = Modifier.size(22.dp))
                androidx.compose.material3.Text("Follow the car", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    // Left alone for 20 seconds: following again
    LaunchedEffect(holder.following, holder.touchedAt) {
        if (holder.following) return@LaunchedEffect
        delay(20_000)
        holder.following = true
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
        (style.getLayer(ROUTE_BEHIND) as? LineLayer)?.setProperties(PropertyFactory.lineColor(accent.toArgb()))
        (style.getLayer(ROUTE_CASE) as? LineLayer)?.setProperties(PropertyFactory.lineColor(C.redDeep.toArgb()))
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
    LaunchedEffect(holder.ready, nav.signals) {
        val style = holder.style ?: return@LaunchedEffect
        val src = style.getSourceAs<GeoJsonSource>(SIGNALS_SRC) ?: return@LaunchedEffect
        src.setGeoJson(FeatureCollection.fromFeatures(nav.signals.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }))
    }
    LaunchedEffect(holder.ready, nav.here, nav.heading, holder.following) {
        val map = holder.map ?: return@LaunchedEffect
        val here = nav.here ?: return@LaunchedEffect
        val h = (holder.view?.height ?: 0).toDouble()
        // The car on the road: on a route, its place on the route line; otherwise the nearest road drawn on the map
        val onRoute = nav.onRoute?.takeIf { nav.route != null }
        val road = if (onRoute != null) onRoute to nav.routeHeading else snapToRoad(map, here, nav.heading)
        val at = road?.first ?: here
        val heading = road?.second ?: nav.heading ?: map.cameraPosition.bearing.toFloat()
        holder.roadBearing = heading.toDouble()
        holder.carAt = at
        holder.alignCar()
        // Looking around: the car moves on the map, the camera stays where it was put
        if (!holder.following) return@LaunchedEffect
        // Closer in town, further out on the motorway
        val kmh = nav.speed * 3.6f
        val zoom = when {
            kmh > 100 -> 15.2
            kmh > 60 -> 15.8
            else -> 16.6
        }
        val pos = CameraPosition.Builder()
            .target(LatLng(at.lat, at.lon))
            .bearing(heading.toDouble())
            .tilt(60.0)
            .zoom(zoom)
            .padding(0.0, h * (2 * CAR_Y - 1), 0.0, 0.0)
            .build()
        map.easeCamera(CameraUpdateFactory.newCameraPosition(pos), 900)
    }
}

/**
 * The nearest road drawn on the map to where the GPS puts the car (it's a few metres out), and that
 * road's direction the way the car is going. Roads running the car's way win at junctions.
 */
private fun snapToRoad(map: MapLibreMap, here: Pos, heading: Float?): Pair<Pos, Float?>? {
    val pt = map.projection.toScreenLocation(LatLng(here.lat, here.lon))
    val box = android.graphics.RectF(pt.x - 70f, pt.y - 70f, pt.x + 70f, pt.y + 70f)
    val features = runCatching { map.queryRenderedFeatures(box, "highway_minor", "highway_major_inner", "highway_motorway_inner") }
        .getOrNull() ?: return null
    var bestAt: Pos? = null
    var bestBearing = 0f
    var bestScore = Double.MAX_VALUE
    for (f in features) {
        val lines = when (val g = f.geometry()) {
            is LineString -> listOf(g.coordinates())
            is MultiLineString -> g.coordinates()
            else -> emptyList()
        }
        for (line in lines) {
            for (i in 0 until line.size - 1) {
                val a = Pos(line[i].latitude(), line[i].longitude())
                val b = Pos(line[i + 1].latitude(), line[i + 1].longitude())
                val (t, d) = Geo.project(here, a, b)
                if (d > 25) continue
                var brg = Geo.bearing(a, b).toFloat()
                var score = d
                if (heading != null) {
                    val diff = kotlin.math.abs(((brg - heading) % 360 + 540) % 360 - 180)
                    if (diff > 90) brg = (brg + 180) % 360
                    score += minOf(diff, 180 - diff) / 3.0
                }
                if (score < bestScore) {
                    bestScore = score
                    bestAt = Geo.along(a, b, t)
                    bestBearing = brg
                }
            }
        }
    }
    val at = bestAt ?: return null
    // Turning, or the road runs another way: keep the car's own heading
    val keep = heading != null && kotlin.math.abs(((bestBearing - heading) % 360 + 540) % 360 - 180) > 35
    return at to (if (keep) heading else bestBearing)
}

/** Where the car sits on the map, from the top. */
private const val CAR_Y = 0.72f

private const val ROUTE_SRC = "cardash-route"
private const val ROUTE = "cardash-route"
private const val ROUTE_CASE = "cardash-route-case"
private const val ROUTE_BEHIND = "cardash-route-behind"
private const val TURN_SRC = "cardash-turn"
private const val HEAD_SRC = "cardash-turn-head"
private const val ARROW = "cardash-arrow"
private const val SIGNALS_SRC = "cardash-signals"
private const val SIGNAL = "cardash-signal"

/**
 * Buildings raised in 3D, the route and the turn arrow. The buildings go above every road and
 * railway (so they stand on the streets, not under them) and below the names. The route goes under
 * the buildings, with a half see-through copy over them, so behind a building it shows darker.
 */
private fun addLayers(style: Style) {
    val layers = style.layers
    val lastRoad = layers.indexOfLast { it.id.startsWith("highway") || it.id.startsWith("railway") || it.id.startsWith("road") }
    val above = layers.getOrNull(lastRoad + 1)?.id
    fun add(layer: org.maplibre.android.style.layers.Layer) {
        runCatching { if (above != null) style.addLayerBelow(layer, above) else style.addLayer(layer) }
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
    // Roads lighter than the dark map, with white edges, so they stand out at a glance
    fun recolour(id: String, colour: String) {
        (style.getLayer(id) as? LineLayer)?.setProperties(PropertyFactory.lineColor(android.graphics.Color.parseColor(colour)))
    }
    recolour("highway_minor", "#4A525F")
    recolour("highway_major_inner", "#5E6675")
    recolour("highway_motorway_inner", "#6E7685")
    recolour("highway_path", "#3A414D")
    val white = android.graphics.Color.argb(242, 255, 255, 255)
    listOf("highway_major_casing", "highway_motorway_casing").forEach { id ->
        (style.getLayer(id) as? LineLayer)?.setProperties(PropertyFactory.lineColor(white))
    }
    (style.getLayer("highway_minor") as? LineLayer)?.let { minor ->
        runCatching {
            val edge = LineLayer("cardash-minor-edge", "openmaptiles").withSourceLayer("transportation").withProperties(
                PropertyFactory.lineColor(white),
                PropertyFactory.lineWidth(
                    Expression.interpolate(Expression.exponential(1.55f), Expression.zoom(), Expression.stop(13, 3.6f), Expression.stop(20, 23.5f)),
                ),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
            minor.filter?.let { edge.setFilter(it) }
            edge.minZoom = 8f
            style.addLayerBelow(edge, "highway_minor")
        }
    }
    style.addSource(GeoJsonSource(ROUTE_SRC))
    style.addSource(GeoJsonSource(TURN_SRC))
    style.addSource(GeoJsonSource(HEAD_SRC))
    // The route on the street, under the buildings, so a building in front of it covers it
    fun addUnderBuildings(layer: org.maplibre.android.style.layers.Layer) {
        runCatching { if (style.getLayer("cardash-3d") != null) style.addLayerBelow(layer, "cardash-3d") else add(layer) }
    }
    addUnderBuildings(
        LineLayer(ROUTE_CASE, ROUTE_SRC).withProperties(
            PropertyFactory.lineColor(C.redDeep.toArgb()),
            PropertyFactory.lineWidth(14f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.6f),
        ),
    )
    addUnderBuildings(
        LineLayer(ROUTE, ROUTE_SRC).withProperties(
            PropertyFactory.lineColor(C.red.toArgb()),
            PropertyFactory.lineWidth(8f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    // ...and the same line again over the buildings, half see-through: on the open street it lands on
    // the red line and changes nothing, behind a building it shows as a darker red on the building,
    // so the route can still be followed and the building looks in front of it
    add(
        LineLayer(ROUTE_BEHIND, ROUTE_SRC).withProperties(
            PropertyFactory.lineColor(C.red.toArgb()),
            PropertyFactory.lineWidth(8f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.5f),
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
    // Traffic lights, standing up on the map
    runCatching {
        style.addSource(GeoJsonSource(SIGNALS_SRC))
        style.addImage(SIGNAL, trafficLight())
        val lights = SymbolLayer("cardash-signals", SIGNALS_SRC).withProperties(
            PropertyFactory.iconImage(SIGNAL),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )
        lights.minZoom = 14f
        style.addLayer(lights)
    }
}

/** A small traffic light: red, amber and green in a dark case with a white edge, on a short post. */
private fun trafficLight(): Bitmap {
    val d = 2f
    val b = Bitmap.createBitmap((28 * d).toInt(), (60 * d).toInt(), Bitmap.Config.ARGB_8888)
    // Drawn at twice the size, shown at the size above
    b.density = android.util.DisplayMetrics.DENSITY_XHIGH
    val c = android.graphics.Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val box = android.graphics.RectF(3 * d, 2 * d, 25 * d, 54 * d)
    p.color = android.graphics.Color.parseColor("#15181F")
    c.drawRoundRect(box, 6 * d, 6 * d, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 2 * d
    p.color = android.graphics.Color.parseColor("#EEF2F7")
    c.drawRoundRect(box, 6 * d, 6 * d, p)
    p.style = Paint.Style.FILL
    listOf("#FF3B30" to 13f, "#FFB000" to 28f, "#22C55E" to 43f).forEach { (col, y) ->
        p.color = android.graphics.Color.parseColor(col)
        c.drawCircle(14 * d, y * d, 6 * d, p)
    }
    p.color = android.graphics.Color.parseColor("#EEF2F7")
    c.drawRect(13 * d, 54 * d, 15 * d, 60 * d, p)
    return b
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
