package io.github.bornaradusin.cardash.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.bornaradusin.cardash.car.Apps
import io.github.bornaradusin.cardash.car.CarNotifications
import io.github.bornaradusin.cardash.car.NavInfo
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val STYLE = "https://tiles.openfreemap.org/styles/dark"

/**
 * CarDash's own map, following the car as an arrow that points the way it's going, tilted a
 * little. Waze's next turn sits on top while Waze is navigating. A tap puts the map back on the car.
 */
@Composable
fun MapCard(
    hasLocation: Boolean,
    speedKmh: Float?,
    nav: NavInfo?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
                isTiltGesturesEnabled = false
            }
            m.cameraPosition = CameraPosition.Builder().zoom(15.5).tilt(45.0).build()
            m.setStyle(Style.Builder().fromUri(STYLE)) { s -> style = s }
            m.addOnMapClickListener {
                runCatching {
                    if (m.locationComponent.isLocationComponentActivated) m.locationComponent.cameraMode = CameraMode.TRACKING_GPS
                }
                true
            }
            map = m
        }
    }

    // The car's arrow, once location is allowed and the map has loaded
    LaunchedEffect(map, style, hasLocation) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (hasLocation) follow(context, m, s)
    }

    // Further out at speed, so what's coming is on screen
    val zoom = when {
        speedKmh == null -> 15.5
        speedKmh > 90 -> 14.0
        speedKmh > 50 -> 15.0
        else -> 16.0
    }
    LaunchedEffect(map, style, zoom) {
        val m = map ?: return@LaunchedEffect
        runCatching {
            if (m.locationComponent.isLocationComponentActivated) m.locationComponent.zoomWhileTracking(zoom, 1_500)
        }
    }

    Box(modifier.clip(RoundedCornerShape(22.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(22.dp))) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        if (!hasLocation) {
            Text(
                "Allow location in Settings to show where you are",
                color = C.muted,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }

        if (nav != null) NavBanner(nav, Modifier.align(Alignment.TopCenter).padding(12.dp))

        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(C.panel.copy(alpha = 0.9f))
                .border(1.dp, C.lineHi, RoundedCornerShape(24.dp))
                .clickable { Apps.launch(context, CarNotifications.WAZE) }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Open in Waze", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Icon(Ic.external, null, tint = C.ink, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NavBanner(nav: NavInfo, modifier: Modifier) {
    val context = LocalContext.current
    val arrow = remember(nav.icon) {
        runCatching { nav.icon?.loadDrawable(context)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.panel.copy(alpha = 0.92f))
            .border(1.dp, C.lineHi, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (arrow != null) Image(arrow, null, Modifier.size(40.dp))
        else Icon(Ic.turn, null, tint = C.red, modifier = Modifier.size(34.dp))
        Column(Modifier.weight(1f)) {
            Text(nav.title, color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            nav.text?.let { Text(it, color = C.ink2, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        nav.sub?.let {
            Text(it, color = C.muted, fontSize = 13.sp, fontStyle = FontStyle.Normal, maxLines = 2)
        }
    }
}

@SuppressLint("MissingPermission")
private fun follow(context: Context, map: MapLibreMap, style: Style) {
    runCatching {
        val component = map.locationComponent
        if (!component.isLocationComponentActivated) {
            val options = LocationComponentOptions.builder(context)
                .foregroundTintColor(C.red.toArgb())
                .bearingTintColor(C.red.toArgb())
                .accuracyAlpha(0f)
                .build()
            component.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .locationComponentOptions(options)
                    .useDefaultLocationEngine(true)
                    .build(),
            )
        }
        component.isLocationComponentEnabled = true
        component.renderMode = RenderMode.GPS
        component.cameraMode = CameraMode.TRACKING_GPS
    }
}
