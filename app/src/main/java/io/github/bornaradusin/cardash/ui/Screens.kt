package io.github.bornaradusin.cardash.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.bornaradusin.cardash.car.AppEntry
import io.github.bornaradusin.cardash.car.Apps
import io.github.bornaradusin.cardash.car.NavInfo
import io.github.bornaradusin.cardash.car.NowPlaying
import io.github.bornaradusin.cardash.obd.Link
import io.github.bornaradusin.cardash.obd.ObdState
import io.github.bornaradusin.cardash.obd.Readings
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Screen { HOME, APPS, GAUGES, SETTINGS }

/** Everything the screens show, gathered by MainActivity. */
data class DashState(
    val name: String,
    val obd: ObdState,
    val nav: NavInfo?,
    val now: NowPlaying?,
    val apps: List<AppEntry>,
    val slots: List<String?>,
    val hasLocation: Boolean,
    val hasNotifications: Boolean,
    val fullScreen: Boolean,
)

/** What the screens can ask for. */
class DashActions(
    val launch: (String) -> Unit,
    val setSlot: (Int, String) -> Unit,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val scanFaults: () -> Unit,
    val askUsbAgain: () -> Unit,
    val allowLocation: () -> Unit,
    val allowNotifications: () -> Unit,
    val chooseHome: () -> Unit,
    val setName: (String) -> Unit,
    val setFullScreen: (Boolean) -> Unit,
)

/**
 * Lays everything out on a 1280×720 board and scales it to the screen, so it looks the same on any
 * radio whatever its size or density.
 */
@Composable
fun Scaled(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
        val base = LocalDensity.current
        val scale = minOf(maxWidth / 1280.dp, maxHeight / 720.dp)
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, 1f)) {
            Box(Modifier.size(1280.dp, 720.dp)) { content() }
        }
    }
}

@Composable
fun CarDash(state: DashState, actions: DashActions) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    Column(
        Modifier
            .size(1280.dp, 720.dp)
            .background(Brush.radialGradient(listOf(C.bgLift, C.bg), radius = 900f)),
    ) {
        Header(state)
        Row(Modifier.weight(1f)) {
            Rail(screen) { screen = it }
            Box(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)) {
                when (screen) {
                    Screen.HOME -> Home(state, actions) { screen = Screen.APPS }
                    Screen.APPS -> AppsScreen(state.apps) { actions.launch(it) }
                    Screen.GAUGES -> Gauges(state.obd, actions)
                    Screen.SETTINGS -> SettingsScreen(state, actions)
                }
            }
        }
    }
}

// ---------- frame ----------

@Composable
private fun Header(state: DashState) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(state.name, color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            Text(" //", color = C.red, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = C.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())), color = C.muted, fontSize = 13.sp)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.pin, "GPS", tint = if (state.hasLocation) C.ink2 else C.lineHi, modifier = Modifier.size(22.dp))
            Icon(Ic.obd, "OBD", tint = if (state.obd.link == Link.CONNECTED) C.good else C.lineHi, modifier = Modifier.size(22.dp))
            state.obd.readings.ambientC?.let {
                Box(Modifier.width(1.dp).height(26.dp).background(C.line))
                Text("${it.toInt()} °C", color = C.ink2, fontSize = 18.sp)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1E26)))
}

@Composable
private fun Rail(screen: Screen, go: (Screen) -> Unit) {
    Column(
        Modifier.width(84.dp).fillMaxHeight().padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RailButton(Ic.home, "Home", screen == Screen.HOME) { go(Screen.HOME) }
        RailButton(Ic.apps, "All apps", screen == Screen.APPS) { go(Screen.APPS) }
        RailButton(Ic.gauge, "Gauges", screen == Screen.GAUGES) { go(Screen.GAUGES) }
        Spacer(Modifier.weight(1f))
        RailButton(Ic.settings, "Settings", screen == Screen.SETTINGS) { go(Screen.SETTINGS) }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) C.redTint else Color.Transparent)
            .border(1.dp, if (selected) C.redDeep else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (selected) C.red else C.ink2, modifier = Modifier.size(28.dp))
    }
}

// ---------- home ----------

@Composable
private fun Home(state: DashState, actions: DashActions, allApps: () -> Unit) {
    val r = state.obd.readings
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(392.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RpmDial(r, Modifier.size(372.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(Ic.oil, "Oil", r.oilC?.let { "${it.toInt()}" }, "°C", Modifier.weight(1f))
                    Pill(Ic.boost, "Boost", r.boostBar?.let { fmt(it, 1) }, "bar", Modifier.weight(1f))
                    Pill(Ic.air, "Intake air", r.intakeC?.let { "${it.toInt()}" }, "°C", Modifier.weight(1f))
                    Pill(Ic.battery, "Battery", r.volts?.let { fmt(it, 1) }, "V", Modifier.weight(1f))
                }
                MapCard(state.hasLocation, r.speedKmh, state.nav, Modifier.weight(1f).fillMaxWidth())
            }
            SpeedDial(r, Modifier.size(372.dp))
        }
        AppRow(state, actions, allApps)
        MusicBar(state, actions)
    }
}

@Composable
private fun RpmDial(r: Readings, modifier: Modifier) {
    Dial(
        value = r.rpm, max = 7000f, major = 1000f, minor = 500f, redFrom = 6000f,
        label = { (it / 1000).toInt().toString() }, labelSize = 0.07f, modifier = modifier,
    ) {
        Column(Modifier.align(Alignment.Center).padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RPM", color = C.ink2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("x1000", color = C.muted, fontSize = 12.sp)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ic.coolant, "Coolant", tint = C.red, modifier = Modifier.size(20.dp))
            Text(r.coolantC?.let { "${it.toInt()} °C" } ?: "–", color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SpeedDial(r: Readings, modifier: Modifier) {
    Dial(value = r.speedKmh, max = 260f, major = 20f, minor = 10f, labelSize = 0.05f, modifier = modifier) {
        Box(Modifier.size(150.dp).clip(CircleShape).background(Color(0xFF0A0C10)).border(2.dp, C.line, CircleShape), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.speedKmh?.toInt()?.toString() ?: "–", color = C.ink, fontSize = 58.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                Text("km/h", color = C.muted, fontSize = 14.sp)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ic.fuel, "Fuel", tint = C.red, modifier = Modifier.size(20.dp))
            Text(r.fuelPct?.let { "${it.toInt()} %" } ?: "–", color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, name: String, value: String?, unit: String, modifier: Modifier) {
    Row(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(42.dp))
            .background(C.panel)
            .border(1.dp, C.line, RoundedCornerShape(42.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, name, tint = C.red, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(value ?: "–", color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (value != null) Text(" $unit", color = C.muted, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(state: DashState, actions: DashActions, allApps: () -> Unit) {
    var picking by remember { mutableStateOf<Int?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) { i ->
            val app = state.slots.getOrNull(i)?.let { pkg -> state.apps.firstOrNull { it.pkg == pkg } } ?: Apps.defaultFor(i, state.apps)
            AppTile(
                label = app?.label ?: Apps.slotNames[i],
                icon = app?.icon?.let { remember(it) { it.toBitmap(96, 96).asImageBitmap() } },
                missing = app == null,
                modifier = Modifier.weight(1f).combinedClickable(
                    onClick = { if (app != null) actions.launch(app.pkg) else picking = i },
                    onLongClick = { picking = i },
                ),
            )
        }
        Box(Modifier.weight(1f).clickable(onClick = allApps)) {
            AppTile("Apps", null, false, Modifier, Ic.apps)
        }
    }
    picking?.let { slot ->
        AppPicker(state.apps, onPick = { actions.setSlot(slot, it); picking = null }, onDismiss = { picking = null })
    }
}

@Composable
private fun AppTile(label: String, icon: ImageBitmap?, missing: Boolean, modifier: Modifier, vector: ImageVector? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(C.panelHi, C.panel)))
            .border(1.dp, C.line, RoundedCornerShape(18.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            icon != null -> Image(icon, null, Modifier.size(34.dp))
            vector != null -> Icon(vector, null, tint = C.ink2, modifier = Modifier.size(28.dp))
            else -> Icon(Ic.plus, null, tint = C.muted, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (missing) "$label · tap to choose" else label,
            color = if (missing) C.muted else C.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (missing) Color.Transparent else C.redDeep))
    }
}

@Composable
private fun AppPicker(apps: List<AppEntry>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panelHi,
        title = { Text("Choose an app for this button", color = C.ink) },
        text = {
            LazyColumn(Modifier.height(420.dp)) {
                items(apps, key = { it.pkg }) { app ->
                    val icon = remember(app.pkg) { app.icon.toBitmap(72, 72).asImageBitmap() }
                    Row(
                        Modifier.fillMaxWidth().height(60.dp).clickable { onPick(app.pkg) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Image(icon, null, Modifier.size(36.dp))
                        Text(app.label, color = C.ink, fontSize = 17.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.ink2) } },
    )
}

@Composable
private fun MusicBar(state: DashState, actions: DashActions) {
    val now = state.now
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(now?.playing) {
        while (now?.playing == true) {
            tick++
            delay(1_000)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(C.panel)
            .border(1.dp, C.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val art = remember(now?.art) { now?.art?.asImageBitmap() }
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(C.line), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Ic.music, null, tint = C.muted, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            when {
                !state.hasNotifications -> {
                    Text("Music controls need notification access", color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Allow it in Settings: it also shows Waze's next turn on the map", color = C.muted, fontSize = 14.sp)
                }
                now == null -> {
                    Text("Nothing playing", color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Start music in any app and it shows here", color = C.muted, fontSize = 14.sp)
                }
                else -> {
                    Text(
                        listOfNotNull(now.artist, now.title).joinToString(" – "),
                        color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val pos = remember(tick, now) { now.positionNow().coerceAtLeast(0) }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val frac = if (now.durationMs > 0) (pos.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.line)) {
                            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(C.red))
                        }
                        Text(
                            if (now.durationMs > 0) "${clock(pos)} / ${clock(now.durationMs)}" else now.app,
                            color = C.muted, fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        RoundButton(Ic.previous, "Previous", 60.dp, now != null, actions.previous)
        Box(
            Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(C.redTint)
                .border(2.dp, C.red, CircleShape)
                .clickable(enabled = now != null, onClick = actions.playPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (now?.playing == true) Ic.pause else Ic.play, if (now?.playing == true) "Pause" else "Play", tint = C.ink, modifier = Modifier.size(26.dp))
        }
        RoundButton(Ic.next, "Next", 60.dp, now != null, actions.next)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (enabled) C.ink else C.lineHi, modifier = Modifier.size(26.dp))
    }
}

// ---------- gauges ----------

@Composable
private fun Gauges(obd: ObdState, actions: DashActions) {
    val r = obd.readings
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RpmDial(r, Modifier.size(300.dp))
            SpeedDial(r, Modifier.size(300.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusLine(obd)
            val tiles = listOf(
                Triple("Coolant", r.coolantC?.let { "${it.toInt()}" }, "°C"),
                Triple("Oil", r.oilC?.let { "${it.toInt()}" }, "°C"),
                Triple("Boost", r.boostBar?.let { fmt(it, 2) }, "bar"),
                Triple("Intake air", r.intakeC?.let { "${it.toInt()}" }, "°C"),
                Triple("Battery", r.volts?.let { fmt(it, 1) }, "V"),
                Triple("Throttle", r.throttlePct?.let { "${it.toInt()}" }, "%"),
                Triple("Engine load", r.loadPct?.let { "${it.toInt()}" }, "%"),
                Triple("Fuel", r.fuelPct?.let { "${it.toInt()}" }, "%"),
                Triple("Outside", r.ambientC?.let { "${it.toInt()}" }, "°C"),
            )
            LazyVerticalGrid(
                GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(372.dp),
                userScrollEnabled = false,
            ) {
                items(tiles) { (name, value, unit) -> Tile(name, value, unit) }
            }
            Faults(obd, actions)
        }
    }
}

@Composable
private fun StatusLine(obd: ObdState) {
    val (text, color) = when (obd.link) {
        Link.CONNECTED -> "OBD connected · ${fmt(obd.rate, 0)} readings a second" to C.good
        Link.NO_CAR -> "Dongle connected, the car isn't answering: turn the ignition on" to C.muted
        Link.CONNECTING -> "Connecting to the dongle…" to C.muted
        Link.NEEDS_PERMISSION -> "Allow CarDash to use the USB dongle (see Settings)" to C.red
        Link.NO_DONGLE -> "No OBD dongle found on USB" to C.muted
        Link.ERROR -> "Dongle problem: ${obd.problem ?: "unknown"}. Trying again…" to C.red
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, color = C.ink2, fontSize = 15.sp)
    }
}

@Composable
private fun Tile(name: String, value: String?, unit: String) {
    Column(
        Modifier
            .height(116.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(C.panel)
            .border(1.dp, C.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, color = C.muted, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value ?: "–", color = if (value == null) C.muted else C.ink, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)
            if (value != null) Text(" $unit", color = C.muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@Composable
private fun Faults(obd: ObdState, actions: DashActions) {
    val faults = obd.faults
    Row(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(C.panel)
            .border(1.dp, C.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val ok = faults != null && faults.isEmpty()
        Icon(if (ok || faults == null) Ic.check else Ic.warn, null, tint = if (ok) C.good else if (faults == null) C.muted else C.red, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    obd.scanning -> "Reading fault codes…"
                    faults == null -> "Fault codes not read yet"
                    faults.isEmpty() -> "No fault codes"
                    else -> faults.joinToString("  ")
                },
                color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (obd.readings.milOn) {
                    true -> "The engine warning light is on"
                    false -> "Engine warning light off"
                    null -> "Read from the engine, when the dongle is connected"
                },
                color = C.muted, fontSize = 14.sp,
            )
        }
        Box(
            Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(C.panelHi)
                .border(1.dp, C.lineHi, RoundedCornerShape(28.dp))
                .clickable(enabled = obd.link == Link.CONNECTED && !obd.scanning, onClick = actions.scanFaults)
                .padding(horizontal = 26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Scan again", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- apps ----------

@Composable
private fun AppsScreen(apps: List<AppEntry>, launch: (String) -> Unit) {
    LazyVerticalGrid(
        GridCells.Fixed(6),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(apps, key = { it.pkg }) { app ->
            val icon = remember(app.pkg) { app.icon.toBitmap(112, 112).asImageBitmap() }
            Column(
                Modifier
                    .height(132.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(C.panel)
                    .border(1.dp, C.line, RoundedCornerShape(18.dp))
                    .clickable { launch(app.pkg) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(icon, null, Modifier.size(56.dp))
                Spacer(Modifier.height(10.dp))
                Text(app.label, color = C.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

// ---------- settings ----------

@Composable
private fun SettingsScreen(state: DashState, actions: DashActions) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card("Name") {
                var name by remember(state.name) { mutableStateOf(state.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = C.ink, fontSize = 18.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.red, unfocusedBorderColor = C.lineHi, cursorColor = C.red),
                    modifier = Modifier.fillMaxWidth(),
                )
                Wide("Save name") { actions.setName(name) }
            }
            Card("Screen") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Full screen", color = C.ink, fontSize = 17.sp)
                        Text("Hides the radio's bars; swipe from the edge to see them", color = C.muted, fontSize = 14.sp)
                    }
                    Switch(
                        checked = state.fullScreen,
                        onCheckedChange = actions.setFullScreen,
                        colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = C.ink),
                    )
                }
                Wide("Make CarDash the home screen", actions.chooseHome)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card("OBD dongle") {
                StatusLine(state.obd)
                state.obd.adapter?.let { Text("Dongle: $it", color = C.muted, fontSize = 14.sp) }
                state.obd.protocol?.let { Text("Car's protocol: $it", color = C.muted, fontSize = 14.sp) }
                if (state.obd.link == Link.NEEDS_PERMISSION) Wide("Ask for USB access again", actions.askUsbAgain)
                Text(
                    "Only one app can use the dongle at a time, so close Torque while CarDash is reading it.",
                    color = C.muted, fontSize = 14.sp,
                )
            }
            Card("Permissions") {
                Permission("Location", "Shows where you are on the map", state.hasLocation, actions.allowLocation)
                Permission("Notification access", "Waze's next turn on the map, and music controls", state.hasNotifications, actions.allowNotifications)
            }
            Text(
                "CarDash ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""}",
                color = C.muted, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.panel)
            .border(1.dp, C.line, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title.uppercase(), color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        content()
    }
}

@Composable
private fun Permission(name: String, why: String, granted: Boolean, ask: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = C.ink, fontSize = 17.sp)
            Text(why, color = C.muted, fontSize = 14.sp)
        }
        if (granted) Text("Allowed", color = C.good, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        else Box(
            Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.redTint).border(1.dp, C.red, RoundedCornerShape(24.dp))
                .clickable(onClick = ask).padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Allow", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Wide(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(C.panelHi)
            .border(1.dp, C.lineHi, RoundedCornerShape(26.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

// ---------- formatting ----------

private fun fmt(v: Float, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", v)

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
