package io.github.bornaradusin.cardash.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import io.github.bornaradusin.cardash.car.CarNotifications
import io.github.bornaradusin.cardash.car.NavInfo
import io.github.bornaradusin.cardash.car.NowPlaying
import io.github.bornaradusin.cardash.obd.Link
import io.github.bornaradusin.cardash.obd.ObdState
import io.github.bornaradusin.cardash.obd.Readings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the screens show, gathered by MainActivity. */
data class DashState(
    val name: String,
    val obd: ObdState,
    val nav: NavInfo?,
    val now: NowPlaying?,
    val apps: List<AppEntry>,
    val slots: List<String?>,
    val icons: List<String>,
    /** Waze's last notification, word for word, for Settings. */
    val lastWaze: String?,
    /** The chosen music player; null = YouTube Music. */
    val musicApp: String?,
    val hasNotifications: Boolean,
    val fullScreen: Boolean,
    /** Goes up each time the radio's Home button is pressed: back to the Home page. */
    val homePresses: Int,
    /** What Save radio files last said. */
    val radioFiles: String?,
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
    val setIcon: (Int, String) -> Unit,
    val setMusicApp: (String) -> Unit,
    val allowNotifications: () -> Unit,
    val chooseHome: () -> Unit,
    val setName: (String) -> Unit,
    val setFullScreen: (Boolean) -> Unit,
    val saveRadioFiles: () -> Unit,
    /** Matches fuel use to the car: what the car says it's using right now, in L/h. */
    val matchFuel: (Float) -> Unit,
    val resetFuel: () -> Unit,
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

private const val PAGE_APPS = 0
private const val PAGE_HOME = 1
private const val PAGE_GAUGES = 2

/** Three pages to swipe between (all apps, Home, gauges), and Settings from the gear. */
@Composable
fun CarDash(state: DashState, actions: DashActions) {
    val pager = rememberPagerState(initialPage = PAGE_HOME) { 3 }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }

    LaunchedEffect(state.homePresses) {
        settings = false
        if (pager.currentPage != PAGE_HOME) pager.animateScrollToPage(PAGE_HOME)
    }

    Column(
        Modifier
            .size(1280.dp, 720.dp)
            .background(Brush.radialGradient(listOf(C.bgLift, C.bg), radius = 900f)),
    ) {
        Header(
            state,
            page = if (settings) null else pager.currentPage,
            onPage = { scope.launch { settings = false; pager.animateScrollToPage(it) } },
            onName = { naming = true },
            onSettings = { settings = !settings },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (settings) {
                SettingsScreen(state, actions) { settings = false }
            } else {
                HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    when (page) {
                        PAGE_APPS -> Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            AppsScreen(state.apps) { actions.launch(it) }
                        }
                        PAGE_HOME -> Home(state, actions) { scope.launch { pager.animateScrollToPage(PAGE_APPS) } }
                        else -> Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Gauges(state.obd, actions)
                        }
                    }
                }
            }
        }
    }
    if (naming) NameDialog(state.name, onSave = { actions.setName(it); naming = false }, onDismiss = { naming = false })
}

// ---------- frame ----------

@Composable
private fun Header(state: DashState, page: Int?, onPage: (Int) -> Unit, onName: () -> Unit, onSettings: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onName).padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text(state.name, color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                Text(" //", color = C.red, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = C.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())), color = C.muted, fontSize = 13.sp)
                if (page != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(3) { i ->
                            Box(
                                Modifier
                                    .height(6.dp)
                                    .width(if (i == page) 16.dp else 6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (i == page) C.red else C.lineHi)
                                    .clickable { onPage(i) },
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (state.obd.link == Link.CONNECTED) C.good else C.lineHi))
                Text("OBD", color = C.muted, fontSize = 14.sp)
            }
            state.obd.readings.ambientC?.let {
                Text("${it.toInt()} °C", color = C.ink2, fontSize = 18.sp)
            }
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.settings, "Settings", tint = C.ink2, modifier = Modifier.size(24.dp)) }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF1A1E26)))
}

@Composable
private fun NameDialog(name: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panelHi,
        title = { Text("Name", color = C.ink) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(20) },
                singleLine = true,
                textStyle = TextStyle(color = C.ink, fontSize = 20.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.red, unfocusedBorderColor = C.lineHi, cursorColor = C.red),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save", color = C.red, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.ink2) } },
    )
}

// ---------- home ----------

@Composable
private fun Home(state: DashState, actions: DashActions, allApps: () -> Unit) {
    val r = state.obd.readings
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RpmDial(r, 430.dp)
            Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    // Oil once the radio's CAN box is read; until then, engine load
                    if (r.oilC != null) RoundGauge(Ic.oil, "Oil", r.oilC, 40f, 150f, "${r.oilC.toInt()}", "°C", warnAbove = 130f)
                    else RoundGauge(Ic.gauge, "Engine load", r.loadPct, 0f, 100f, r.loadPct?.let { "${it.toInt()}" }, "%")
                    RoundGauge(Ic.boost, "Boost", r.boostBar, 0f, 1.5f, r.boostBar?.let { fmt(it, 1) }, "bar")
                    RoundGauge(Ic.air, "Intake air", r.intakeC, 0f, 70f, r.intakeC?.let { "${it.toInt()}" }, "°C", warnAbove = 55f)
                    RoundGauge(Ic.battery, "Battery", r.volts, 11f, 15f, r.volts?.let { fmt(it, 1) }, "V", warnBelow = 11.8f)
                }
                CenterPanel(state, actions, Modifier.weight(1f).fillMaxWidth())
            }
            SpeedDial(r, state.obd.trip.meters, 430.dp)
        }
        BottomBar(state, actions, allApps)
    }
}

@Composable
private fun RpmDial(r: Readings, size: Dp) {
    Dial(
        value = r.rpm, max = 6000f, major = 1000f, minor = 500f, redFrom = 4500f,
        label = { (it / 1000).toInt().toString() }, labelSize = 0.094f,
        bottomArc = true, bottomFill = r.coolantC?.let { (it - 40f) / 90f },
        modifier = Modifier.size(size),
    ) {
        Column(Modifier.align(Alignment.TopCenter).padding(top = size * 0.55f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                r.rpm?.let { "%,d".format(Locale.US, it.toInt()) } ?: "–",
                color = C.ink, fontSize = (size.value * 0.075f).sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
            )
            Text("rpm", color = C.muted, fontSize = (size.value * 0.033f).sp)
        }
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = size * 0.72f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Ic.coolant, "Coolant", tint = C.red, modifier = Modifier.size(size * 0.055f))
            Text(r.coolantC?.let { "${it.toInt()} °C" } ?: "–", color = C.ink, fontSize = (size.value * 0.055f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SpeedDial(r: Readings, tripMeters: Double, size: Dp) {
    Dial(
        value = r.speedKmh, max = 260f, major = 20f, minor = 10f, labelSize = 0.068f, labelEvery = 40f,
        modifier = Modifier.size(size),
    ) {
        Box(
            Modifier.size(size * 0.41f).clip(CircleShape).background(Color(0xFF0A0C10)).border(2.dp, C.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    r.speedKmh?.toInt()?.toString() ?: "–",
                    color = C.ink, fontSize = (size.value * 0.17f).sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
                )
                Text("km/h", color = C.muted, fontSize = (size.value * 0.038f).sp)
            }
        }
        // The odometer from the radio's CAN box once that's read; until then, this drive's distance
        Text(
            r.odometerKm?.let { "%,d km".format(Locale.US, it.toLong()) } ?: "${fmt((tripMeters / 1000).toFloat(), 1)} km",
            color = C.ink, fontSize = (size.value * 0.058f).sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = size * 0.78f),
        )
    }
}

/** A small round gauge: a ring that fills with the value (brighter past a warning level), icon and number inside. */
@Composable
private fun RoundGauge(
    icon: ImageVector,
    name: String,
    value: Float?,
    min: Float,
    max: Float,
    text: String?,
    unit: String,
    warnAbove: Float? = null,
    warnBelow: Float? = null,
) {
    val frac = value?.let { ((it - min) / (max - min)).coerceIn(0f, 1f) } ?: 0f
    val warn = value != null && ((warnAbove != null && value > warnAbove) || (warnBelow != null && value < warnBelow))
    Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2 + 3.dp.toPx()
            val tl = Offset(inset, inset)
            val sz = Size(size.width - inset * 2, size.height - inset * 2)
            drawCircle(C.panel, size.minDimension / 2)
            drawCircle(C.lineHi, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            drawArc(C.track, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            if (value != null && frac > 0.005f) {
                val col = if (warn) Color(0xFFFF6B7A) else C.red
                drawArc(col.copy(alpha = 0.25f), 135f, 270f * frac, false, tl, sz, style = Stroke(stroke * 2.6f, cap = StrokeCap.Round))
                drawArc(col, 135f, 270f * frac, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, name, tint = if (warn) C.red else C.ink2, modifier = Modifier.size(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text ?: "–", color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                if (text != null) Text(" $unit", color = C.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

// ---------- centre: Waze's next turn, or the drive ----------

@Composable
private fun CenterPanel(state: DashState, actions: DashActions, modifier: Modifier) {
    val nav = state.nav
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(C.panel)
            .border(1.dp, if (nav != null) C.redDeep else C.line, RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        if (nav != null) {
            NavPanel(nav)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(C.panelHi)
                    .border(1.dp, C.lineHi, RoundedCornerShape(20.dp))
                    .clickable { actions.launch(CarNotifications.WAZE) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Ic.waze, null, tint = C.ink, modifier = Modifier.size(18.dp))
                    Text("Waze", color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            DrivePanel(state.obd)
        }
    }
}

private data class Turn(val icon: ImageVector?, val distance: String?, val instruction: String)

/** Waze's notification as an arrow, a distance and what to do. */
private fun turnOf(nav: NavInfo): Turn {
    val all = listOfNotNull(nav.title, nav.text).joinToString(" ")
    val distance = CarNotifications.DISTANCE.find(all)?.value
    val instruction = listOfNotNull(nav.title, nav.text)
        .map { if (distance != null) it.replace(distance, "") else it }
        .map { it.trim(' ', '·', ',', '-', '–', ':') }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
        .ifEmpty { nav.title }
    val l = all.lowercase()
    val icon = when {
        "u-turn" in l || "u turn" in l || "make a u" in l -> Ic.uTurn
        "roundabout" in l -> Ic.roundabout
        "keep left" in l || "bear left" in l || "slight left" in l -> Ic.keepLeft
        "keep right" in l || "bear right" in l || "slight right" in l || "exit" in l -> Ic.keepRight
        "left" in l -> Ic.turnLeft
        "right" in l -> Ic.turnRight
        "arriv" in l || "destination" in l -> Ic.arrive
        "straight" in l || "continue" in l -> Ic.straight
        else -> null
    }
    return Turn(icon, distance, instruction)
}

@Composable
private fun NavPanel(nav: NavInfo) {
    val context = LocalContext.current
    val turn = remember(nav) { turnOf(nav) }
    val wazeArrow = remember(nav.icon) {
        runCatching { nav.icon?.loadDrawable(context)?.toBitmap(160, 160)?.asImageBitmap() }.getOrNull()
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(110.dp).clip(RoundedCornerShape(20.dp)).background(C.redTint), contentAlignment = Alignment.Center) {
                when {
                    turn.icon != null -> Icon(turn.icon, null, tint = C.red, modifier = Modifier.size(84.dp))
                    wazeArrow != null -> Image(wazeArrow, null, Modifier.size(84.dp))
                    else -> Icon(Ic.straight, null, tint = C.red, modifier = Modifier.size(84.dp))
                }
            }
            Text(turn.distance ?: "", color = C.ink, fontSize = 52.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1)
        }
        Text(turn.instruction, color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        nav.sub?.let { Text(it, color = C.muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** With no route in Waze: what the engine is doing and this drive so far. */
@Composable
private fun DrivePanel(obd: ObdState) {
    val r = obd.readings
    val t = obd.trip
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("THIS DRIVE", color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text(clock(t.drivingMs, hours = true), color = C.ink2, fontSize = 16.sp)
        }
        Bar("Accelerator pedal", r.pedalPct, 100f, r.pedalPct?.let { "${it.toInt()} %" })
        Bar("Engine load", r.loadPct, 100f, r.loadPct?.let { "${it.toInt()} %" })
        Bar("Boost", r.boostBar?.coerceAtLeast(0f), 1.5f, r.boostBar?.let { "${fmt(it, 2)} bar" })
        // L/100 km while moving, L/h standing still
        val l100 = r.fuelL100
        Bar(
            "Fuel use",
            if (l100 != null) l100 else r.fuelLph,
            if (l100 != null) 25f else 10f,
            if (l100 != null) "${fmt(l100, 1)} L/100" else r.fuelLph?.let { "${fmt(it, 1)} L/h" },
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Average", t.avgL100?.let { fmt(it.toFloat(), 1) }, "L/100", Modifier.weight(1f))
            Stat("Fuel used", if (t.fuelL > 0) fmt(t.fuelL.toFloat(), 2) else null, "L", Modifier.weight(1f))
            Stat("Max boost", if (t.maxBoostBar > 0) fmt(t.maxBoostBar, 2) else null, "bar", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Distance", fmt((t.meters / 1000).toFloat(), 1), "km", Modifier.weight(1f))
            Stat("Avg speed", t.avgKmh.toInt().toString(), "km/h", Modifier.weight(1f))
            Stat("Top speed", t.maxKmh.toInt().toString(), "km/h", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Max RPM", if (t.maxRpm > 0) "%,d".format(Locale.US, t.maxRpm.toInt()) else null, "", Modifier.weight(1f))
            Stat("Air flow", r.mafGs?.let { fmt(it, 1) }, "g/s", Modifier.weight(1f))
            Stat("Outside", r.ambientC?.let { "${it.toInt()}" }, "°C", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Bar(name: String, value: Float?, max: Float, label: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(name, color = C.ink2, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(label ?: "–", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.track)) {
            Box(Modifier.fillMaxWidth(((value ?: 0f) / max).coerceIn(0f, 1f)).fillMaxHeight().background(C.red))
        }
    }
}

@Composable
private fun Stat(name: String, value: String?, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, color = C.muted, fontSize = 13.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value ?: "–", color = if (value == null) C.muted else C.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, maxLines = 1)
            if (value != null) Text(" $unit", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
        }
    }
}

// ---------- bottom: music and the app buttons ----------

@Composable
private fun BottomBar(state: DashState, actions: DashActions, allApps: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(88.dp).padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MusicBar(state, actions, Modifier.weight(1f))
        AppButtons(state, actions, allApps)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppButtons(state: DashState, actions: DashActions, allApps: () -> Unit) {
    var menu by remember { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var choosingIcon by remember { mutableStateOf<Int?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(4) { i ->
            val app = state.slots.getOrNull(i)?.let { pkg -> state.apps.firstOrNull { it.pkg == pkg } } ?: Apps.defaultFor(i, state.apps)
            val iconKey = state.icons.getOrNull(i) ?: "app"
            val appIcon = if (iconKey == "app") app?.icon?.let { remember(it) { it.toBitmap(96, 96).asImageBitmap() } } else null
            SquareButton(
                label = app?.label ?: Apps.slotNames[i],
                bitmap = appIcon,
                vector = if (appIcon == null) Ic.byKey(iconKey) ?: Ic.plus else null,
                dim = app == null,
                modifier = Modifier.combinedClickable(
                    onClick = { if (app != null) actions.launch(app.pkg) else picking = i },
                    onLongClick = { menu = i },
                ),
            )
        }
        SquareButton("All apps", null, Ic.apps, dim = false, accent = true, modifier = Modifier.clickable(onClick = allApps))
    }
    menu?.let { slot ->
        AlertDialog(
            onDismissRequest = { menu = null },
            containerColor = C.panelHi,
            title = { Text("This button", color = C.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Wide("Choose the app") { menu = null; picking = slot }
                    Wide("Choose the icon") { menu = null; choosingIcon = slot }
                }
            },
            confirmButton = { TextButton(onClick = { menu = null }) { Text("Cancel", color = C.ink2) } },
        )
    }
    picking?.let { slot ->
        AppPicker(state.apps, onPick = { actions.setSlot(slot, it); picking = null }, onDismiss = { picking = null })
    }
    choosingIcon?.let { slot ->
        IconPicker(onPick = { actions.setIcon(slot, it); choosingIcon = null }, onDismiss = { choosingIcon = null })
    }
}

/** An icon-only button; [label] is for TalkBack. */
@Composable
private fun SquareButton(label: String, bitmap: ImageBitmap?, vector: ImageVector?, dim: Boolean, modifier: Modifier, accent: Boolean = false) {
    Box(
        Modifier
            .size(76.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (accent) Brush.verticalGradient(listOf(C.redTint, C.redTint)) else Brush.verticalGradient(listOf(C.panelHi, C.panel)))
            .border(1.dp, if (accent) C.redDeep else C.line, RoundedCornerShape(20.dp))
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(bitmap, label, Modifier.size(38.dp))
            vector != null -> Icon(vector, label, tint = if (accent) C.red else if (dim) C.muted else C.ink, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun AppPicker(apps: List<AppEntry>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panelHi,
        title = { Text("Choose an app", color = C.ink) },
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
private fun IconPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panelHi,
        title = { Text("Choose an icon", color = C.ink) },
        text = {
            LazyVerticalGrid(GridCells.Fixed(6), Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Ic.choices, key = { it.first }) { (key, vector) ->
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(14.dp))
                            .clickable { onPick(key) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(vector, key, tint = C.ink, modifier = Modifier.size(32.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick("app") }) { Text("Use the app's own icon", color = C.ink2) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.ink2) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicBar(state: DashState, actions: DashActions, modifier: Modifier) {
    val now = state.now
    var tick by remember { mutableLongStateOf(0L) }
    var choosing by remember { mutableStateOf(false) }
    val player = remember(state.musicApp, state.apps) {
        (state.musicApp?.let { pkg -> state.apps.firstOrNull { it.pkg == pkg } } ?: Apps.defaultFor(2, state.apps))?.label
    }
    LaunchedEffect(now?.playing) {
        while (now?.playing == true) {
            tick++
            delay(1_000)
        }
    }
    if (choosing) {
        AppPicker(state.apps, onPick = { actions.setMusicApp(it); choosing = false }, onDismiss = { choosing = false })
    }
    Row(
        modifier
            .height(76.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(C.panel)
            .border(1.dp, C.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val art = remember(now?.art) { now?.art?.asImageBitmap() }
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(C.line), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Ic.music, null, tint = C.muted, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f).combinedClickable(onClick = {}, onLongClick = { choosing = true })) {
            when {
                !state.hasNotifications -> {
                    Text("Music needs notification access", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("Allow it in Settings (the gear, top right)", color = C.muted, fontSize = 13.sp, maxLines = 1)
                }
                now == null -> {
                    Text("Nothing playing", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Play starts ${player ?: "your music app"} · hold here to change", color = C.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                else -> {
                    Text(
                        listOfNotNull(now.artist, now.title).joinToString(" – "),
                        color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val pos = remember(tick, now) { now.positionNow().coerceAtLeast(0) }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val frac = if (now.durationMs > 0) (pos.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f
                        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.line)) {
                            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(C.red))
                        }
                        Text(if (now.durationMs > 0) "${clock(pos)} / ${clock(now.durationMs)}" else now.app, color = C.muted, fontSize = 12.sp)
                    }
                }
            }
        }
        RoundButton(Ic.previous, "Previous", now != null, actions.previous)
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(C.redTint)
                .border(2.dp, C.red, CircleShape)
                .clickable(onClick = actions.playPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (now?.playing == true) Ic.pause else Ic.play, if (now?.playing == true) "Pause" else "Play", tint = C.ink, modifier = Modifier.size(24.dp))
        }
        RoundButton(Ic.next, "Next", now != null, actions.next)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (enabled) C.ink else C.lineHi, modifier = Modifier.size(24.dp))
    }
}

// ---------- gauges ----------

@Composable
private fun Gauges(obd: ObdState, actions: DashActions) {
    val r = obd.readings
    val t = obd.trip
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RpmDial(r, 300.dp)
            SpeedDial(r, t.meters, 300.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusLine(obd)
            val tiles = listOf(
                Triple("Coolant", r.coolantC?.let { "${it.toInt()}" }, "°C"),
                Triple("Oil", r.oilC?.let { "${it.toInt()}" }, "°C"),
                Triple("Boost", r.boostBar?.let { fmt(it, 2) }, "bar"),
                Triple("Intake air", r.intakeC?.let { "${it.toInt()}" }, "°C"),
                Triple("Battery", r.volts?.let { fmt(it, 1) }, "V"),
                Triple("Accelerator", r.pedalPct?.let { "${it.toInt()}" }, "%"),
                Triple("Engine load", r.loadPct?.let { "${it.toInt()}" }, "%"),
                Triple("Fuel now", r.fuelL100?.let { fmt(it, 1) } ?: r.fuelLph?.let { fmt(it, 1) }, if (r.fuelL100 != null) "L/100" else "L/h"),
                Triple("Air flow", r.mafGs?.let { fmt(it, 1) }, "g/s"),
            )
            LazyVerticalGrid(
                GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(384.dp),
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
            .height(120.dp)
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
        GridCells.Fixed(7),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(apps, key = { it.pkg }) { app ->
            val icon = remember(app.pkg) { app.icon.toBitmap(112, 112).asImageBitmap() }
            Column(
                Modifier
                    .height(128.dp)
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
private fun SettingsScreen(state: DashState, actions: DashActions, close: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = close), contentAlignment = Alignment.Center) {
                Icon(Ic.back, "Back", tint = C.ink, modifier = Modifier.size(28.dp))
            }
            Text("Settings", color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "CarDash ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""}",
                color = C.muted, fontSize = 13.sp,
            )
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        textStyle = TextStyle(color = C.ink, fontSize = 18.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.red, unfocusedBorderColor = C.lineHi, cursorColor = C.red),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Wide("Save name") { actions.setName(name) }
                    Text("Tapping the name on Home changes it too.", color = C.muted, fontSize = 14.sp)
                }
                Card("Music") {
                    val player = (state.musicApp?.let { pkg -> state.apps.firstOrNull { it.pkg == pkg } } ?: Apps.defaultFor(2, state.apps))?.label
                    Text("Music player: ${player ?: "none found"}", color = C.ink, fontSize = 17.sp)
                    Text("Play on Home starts it when nothing is playing", color = C.muted, fontSize = 14.sp)
                    var choosing by remember { mutableStateOf(false) }
                    Wide("Choose the music app") { choosing = true }
                    if (choosing) AppPicker(state.apps, onPick = { actions.setMusicApp(it); choosing = false }, onDismiss = { choosing = false })
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
                Card("Permissions") {
                    Permission("Notification access", "Waze's next turn, and music controls", state.hasNotifications, actions.allowNotifications)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card("OBD dongle") {
                    StatusLine(state.obd)
                    state.obd.adapter?.let { Text("Dongle: $it${state.obd.baud?.let { b -> " at $b baud" } ?: ""}", color = C.muted, fontSize = 14.sp) }
                    state.obd.protocol?.let { Text("Car's protocol: $it", color = C.muted, fontSize = 14.sp) }
                    if (state.obd.link == Link.NEEDS_PERMISSION) Wide("Ask for USB access again", actions.askUsbAgain)
                    Text("Only one app can use the dongle at a time, so close Torque while CarDash is reading it.", color = C.muted, fontSize = 14.sp)
                    if (state.obd.log.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF05060A))
                                .verticalScroll(rememberScrollState(Int.MAX_VALUE)).padding(10.dp),
                        ) {
                            state.obd.log.forEach { Text(it, color = C.ink2, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                        }
                    }
                }
                Card("Radio's CAN box") {
                    Text(
                        "Oil temperature and the odometer come from the radio's own CAN box, not the OBD dongle. To read them, " +
                            "save the radio's apps, copy the Download/CarDash folder to a USB stick and send the files.",
                        color = C.muted, fontSize = 14.sp,
                    )
                    Wide("Save radio files", actions.saveRadioFiles)
                    state.radioFiles?.let { Text(it, color = C.ink2, fontSize = 14.sp) }
                }
                Card("Fuel use") {
                    val r = state.obd.readings
                    Text(
                        when {
                            r.fuelRateLph != null -> "The engine reports its own fuel use: ${fmt(r.fuelRateLph, 1)} L/h"
                            r.fuelLph != null -> "Now: ${fmt(r.fuelLph!!, 1)} L/h, worked out from the air flow" +
                                if (r.fuelFactor != 1f) " (matched to the car: ×${fmt(r.fuelFactor, 2)})" else ""
                            else -> "Not reading yet"
                        },
                        color = C.ink, fontSize = 16.sp,
                    )
                    Text(
                        "If it doesn't match your car's computer, idle with the engine warm, type what the car shows in L/h and tap Match.",
                        color = C.muted, fontSize = 14.sp,
                    )
                    var carSays by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = carSays,
                            onValueChange = { v -> carSays = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5) },
                            singleLine = true,
                            placeholder = { Text("0.7", color = C.muted) },
                            textStyle = TextStyle(color = C.ink, fontSize = 18.sp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.red, unfocusedBorderColor = C.lineHi, cursorColor = C.red),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.width(120.dp),
                        )
                        Text("L/h", color = C.muted, fontSize = 16.sp)
                        Box(Modifier.weight(1f)) {
                            Wide("Match") { carSays.replace(',', '.').toFloatOrNull()?.let(actions.matchFuel) }
                        }
                    }
                    if (r.fuelFactor != 1f) Wide("Undo matching", actions.resetFuel)
                }
                Card("Waze") {
                    Text(if (state.nav != null) "Navigating: shown on Home" else "Not navigating", color = C.ink, fontSize = 17.sp)
                    Text("Waze's last notification: ${state.lastWaze ?: "none yet"}", color = C.muted, fontSize = 14.sp)
                }
            }
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

private fun clock(ms: Long, hours: Boolean = false): String {
    val s = ms / 1000
    return if (hours) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
