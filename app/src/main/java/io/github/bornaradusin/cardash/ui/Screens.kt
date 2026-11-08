package io.github.bornaradusin.cardash.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.bornaradusin.cardash.R
import io.github.bornaradusin.cardash.Settings
import io.github.bornaradusin.cardash.car.AppEntry
import io.github.bornaradusin.cardash.car.Apps
import io.github.bornaradusin.cardash.car.CanPick
import io.github.bornaradusin.cardash.car.CanRole
import io.github.bornaradusin.cardash.car.CanScale
import io.github.bornaradusin.cardash.car.CanState
import io.github.bornaradusin.cardash.car.CanValue
import io.github.bornaradusin.cardash.car.Doors
import io.github.bornaradusin.cardash.car.NavInfo
import io.github.bornaradusin.cardash.car.NowPlaying
import io.github.bornaradusin.cardash.car.ServiceItem
import io.github.bornaradusin.cardash.car.ServiceState
import io.github.bornaradusin.cardash.nav.MapArea
import io.github.bornaradusin.cardash.nav.NavState
import io.github.bornaradusin.cardash.nav.Place
import io.github.bornaradusin.cardash.nav.Route
import io.github.bornaradusin.cardash.obd.Link
import io.github.bornaradusin.cardash.obd.ObdState
import io.github.bornaradusin.cardash.obd.Readings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the screens show, gathered by MainActivity. */
data class DashState(
    val name: String,
    val obd: ObdState,
    /** CarDash's own navigation: where the car is, and the route if there is one. */
    val nav: NavState,
    /** Google Maps' next turn while it navigates. */
    val google: NavInfo?,
    val now: NowPlaying?,
    val apps: List<AppEntry>,
    val slots: List<String?>,
    val icons: List<String>,
    /** The chosen music player; null = YouTube Music. */
    val musicApp: String?,
    val hasNotifications: Boolean,
    /** CarDash is the radio's home app (the Home button and starting the radio open it). */
    val isHomeApp: Boolean = true,
    val fullScreen: Boolean,
    /** Goes up each time the radio's Home button is pressed: back to the Home page. */
    val homePresses: Int,
    /** A shared place that couldn't be found on its own: Where to? opens with these words. */
    val searchFor: String? = null,
    /** The engine runs on diesel: fuel use is worked out from load and RPM, not air flow. */
    val diesel: Boolean,
    val can: CanState,
    val canPicks: Map<CanRole, CanPick>,
    /** The CAN box values the person has assigned, ready to show. */
    val canValues: Map<CanRole, Float>,
    /** What needs servicing, and the car's odometer it counts down from. */
    val service: ServiceState,
    /** "Slow down" across the screen over this speed, when on. */
    val speedWarnOn: Boolean,
    val speedWarnKmh: Int,
    /** "Shift up" across the screen over this rpm, when on; top gear's rpm per km/h once learnt (else 0). */
    val shiftWarnOn: Boolean,
    val shiftWarnRpm: Int,
    val topGear: Float,
    /** The gear the car is in (worked out), how many gears have been found, and how many there are. */
    val gear: Int?,
    val gearsFound: Int,
    val gearCount: Int,
    /** Each gear's rpm per km/h, 1st first, once learnt; and whether the change up / down signs show. */
    val gearRatios: List<Float>,
    /** Rolling at tickover: clutch down or out of gear. */
    val rolling: Boolean = false,
    /** Where the change up / down signs come up, gear by gear (1st first). */
    val shiftUpAt: List<Int> = Settings.SHIFT_UP_DEFAULT,
    val shiftDownAt: List<Int> = Settings.SHIFT_DOWN_DEFAULT,
    val shiftHints: Boolean,
    /** The accent colour, ARGB. */
    val accent: Int,
    val spoken: Boolean,
    /** The driver's own Google key for Eircodes; blank when there's none. */
    val googleKey: String,
    val wifiOnly: Boolean,
    val home: Place?,
    val work: Place?,
    val recent: List<Place>,
    val mapAreas: List<MapArea>,
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
    /** Matches fuel use to the car: what the car says it's using right now, in L/h. */
    val matchFuel: (Float) -> Unit,
    val resetFuel: () -> Unit,
    /** Matches fuel use to the car's trip computer: its average for this drive, L/100 km. */
    val matchFuelAverage: (Float) -> Unit,
    val setDiesel: (Boolean) -> Unit,
    val setCanPick: (CanRole, CanPick?) -> Unit,
    val uninstall: (String) -> Unit,
    val appInfo: (String) -> Unit,
    /** Adds a service item, or saves changes to one. */
    val saveService: (ServiceItem) -> Unit,
    val removeService: (String) -> Unit,
    /** Logs an item as done today, at the odometer now. */
    val serviceDone: (String) -> Unit,
    val setRemindKm: (Int) -> Unit,
    val setRemindDays: (Int) -> Unit,
    /** The odometer as typed from the dashboard, when the CAN box doesn't give it. */
    val setCarKm: (Double) -> Unit,
    /** Hides the service reminder until the next drive. */
    val serviceLater: () -> Unit,
    val setSpeedWarnOn: (Boolean) -> Unit,
    val setSpeedWarnKmh: (Int) -> Unit,
    val setShiftWarnOn: (Boolean) -> Unit,
    val setShiftWarnRpm: (Int) -> Unit,
    /** Saves top gear's rpm per km/h once it's been seen steady. */
    val setTopGear: (Float) -> Unit,
    val setGearCount: (Int) -> Unit,
    val setShiftHints: (Boolean) -> Unit,
    val setShiftUpAt: (gear: Int, rpm: Int) -> Unit,
    val setShiftDownAt: (gear: Int, rpm: Int) -> Unit,
    val resetShiftPoints: () -> Unit,
    /** Forgets the learnt gears and starts again. */
    val relearnGears: () -> Unit,
    // Navigation
    val search: suspend (String) -> List<Place>,
    val preview: suspend (Place) -> Route?,
    val go: (Place, Route?) -> Unit,
    val endRoute: () -> Unit,
    val toggleMute: () -> Unit,
    val setHome: (Place) -> Unit,
    val setWork: (Place) -> Unit,
    val forgetRecent: (Place) -> Unit,
    /** Asks for location the first time it's needed (once per start). */
    val askLocation: () -> Unit,
    /** The Allow buttons: asks again, or opens Android's settings for CarDash after a no for good. */
    val allowLocation: () -> Unit,
    /** Where to? has opened with [DashState.searchFor]. */
    val searchTaken: () -> Unit,
    val setSpoken: (Boolean) -> Unit,
    val setGoogleKey: (String) -> Unit,
    // The look
    val setAccent: (Int) -> Unit,
    // Offline map
    val saveMapAround: () -> Unit,
    val saveMapCountry: (io.github.bornaradusin.cardash.nav.Country) -> Unit,
    /** Room left on the radio for saved maps, in MB. */
    val freeMapMb: () -> Long,
    val saveMapRoute: () -> Unit,
    val pauseMap: (Long) -> Unit,
    val resumeMap: (Long) -> Unit,
    val updateMap: (Long) -> Unit,
    val removeMap: (Long) -> Unit,
    /** Saved areas to a file the person picked, and from one: what happened goes to the callback. */
    val exportMaps: (android.net.Uri, (String) -> Unit) -> Unit,
    val importMaps: (android.net.Uri, (String) -> Unit) -> Unit,
    val setWifiOnly: (Boolean) -> Unit,
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

/** The two big dials on Home. */
private val DIAL = 468.dp

/** The four small gauges on Home: this big, and this far under the divider (about 3 mm on the radio). */
private val GAUGE = 108.dp
private val GAUGE_TOP = 18.dp
private val GAUGE_GAP = 8.dp

private const val PAGE_APPS = 0
private const val PAGE_HOME = 1
private const val PAGE_GAUGES = 2
private const val PAGE_SERVICE = 3
private const val PAGES = 4

/** What the middle box on Home shows, swiped up and down: the drive's figures, the map in the box, the
 * directions alone, and the big map in the RPM dial's place too (each map with or without a route). */
internal const val VIEW_CAR = 0
internal const val VIEW_MAP = 1
internal const val VIEW_DIRECTIONS = 2
internal const val VIEW_BIG_MAP = 3

/** The middle box's views in swipe order: the directions only while there's a route to give them. */
internal fun centreViews(routed: Boolean): List<Int> =
    if (routed) listOf(VIEW_CAR, VIEW_MAP, VIEW_DIRECTIONS, VIEW_BIG_MAP) else listOf(VIEW_CAR, VIEW_MAP, VIEW_BIG_MAP)

/** A route to follow: CarDash's own (or one being planned), or Google Maps navigating. */
internal fun DashState.routed(): Boolean = nav.route != null || nav.destination != null || google != null

/** Four pages to swipe between (all apps, Home, gauges, service), and Settings from the gear. */
@Composable
fun CarDash(state: DashState, actions: DashActions) {
    val pager = rememberPagerState(initialPage = PAGE_HOME) { PAGES }
    /** The middle box's view, kept here so it survives the big map taking over and Where to? starting a route. */
    val views = centreViews(state.routed())
    val viewsNow by rememberUpdatedState(views)
    val centre = rememberPagerState(initialPage = 0) { viewsNow.size }
    // The view showing, so it stays on screen when the directions come or go with a route
    var shownView by remember { mutableIntStateOf(VIEW_CAR) }
    LaunchedEffect(centre) {
        snapshotFlow { centre.settledPage }.collect { shownView = viewsNow.getOrElse(it) { viewsNow.last() } }
    }
    LaunchedEffect(views) {
        // The directions gone with the route: the map in the box instead
        val i = views.indexOf(shownView).takeIf { it >= 0 } ?: views.indexOf(VIEW_MAP)
        if (centre.currentPage != i) centre.scrollToPage(i)
    }
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(false) }
    var whereTo by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    /** The map dash in Home's place, swiped to on the left of the screen. */
    var mapDash by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.homePresses) {
        settings = false
        whereTo = false
        if (pager.currentPage != PAGE_HOME) pager.animateScrollToPage(PAGE_HOME)
    }
    // A route starts: show the map
    val routing = state.nav.destination != null
    LaunchedEffect(routing) {
        // The map in the box; the big map is the view under the directions
        if (routing && centre.currentPage == VIEW_CAR) centre.animateScrollToPage(VIEW_MAP)
    }
    var searchText by remember { mutableStateOf("") }
    val openSearch = { settings = false; searchText = ""; whereTo = true }
    // A shared place to pick from: Where to? with its name and address typed in
    LaunchedEffect(state.searchFor) {
        val q = state.searchFor ?: return@LaunchedEffect
        settings = false
        searchText = q
        whereTo = true
        actions.searchTaken()
    }

    Column(
        Modifier
            .size(1280.dp, 720.dp)
            .background(Brush.radialGradient(listOf(C.bgLift, C.bg), radius = 900f)),
    ) {
        Header(
            state,
            page = if (settings || whereTo) null else pager.currentPage,
            onPage = { scope.launch { settings = false; whereTo = false; pager.animateScrollToPage(it) } },
            onName = { naming = true },
            onSettings = { whereTo = false; settings = !settings },
            onService = { scope.launch { settings = false; whereTo = false; pager.animateScrollToPage(PAGE_SERVICE) } },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                settings -> SettingsScreen(state, actions) { settings = false }
                whereTo -> WhereToScreen(state, actions, searchText) {
                    whereTo = false
                    scope.launch { if (pager.currentPage != PAGE_HOME) pager.scrollToPage(PAGE_HOME) }
                }
                else -> HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    when (page) {
                        PAGE_APPS -> Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            AppsScreen(state.apps, actions)
                        }
                        PAGE_HOME -> Home(state, actions, centre, openSearch, mapDash, { mapDash = it }) { scope.launch { pager.animateScrollToPage(PAGE_APPS) } }
                        PAGE_SERVICE -> ServicePage(state.service, actions)
                        else -> Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Gauges(state.obd, state.canValues, state.gear, actions)
                        }
                    }
                }
            }
        }
    }
    if (naming) NameDialog(state.name, onSave = { actions.setName(it); naming = false }, onDismiss = { naming = false })
    // Something CarDash needs isn't allowed (an update or the radio can take it away): a popup with
    // the fixes, each time the radio starts. Not now hides it until the next start; it closes itself
    // once everything's allowed. A moment's wait first, so location has been checked.
    var fixLater by rememberSaveable { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(2_500); settled = true }
    if (settled && !fixLater && !settings && missingPermissions(state)) FixNotice(state, actions, onLater = { fixLater = true })
    // Something due: a reminder, except on the Service page itself and in Settings
    if (!settings && !whereTo && pager.currentPage != PAGE_SERVICE) {
        ServiceReminder(state.service, actions) { scope.launch { pager.animateScrollToPage(PAGE_SERVICE) } }
    }
    // Too fast: across the whole screen, over everything
    // Shift up gives way to the speed warning when that's showing
    val speedy = state.speedWarnOn && (state.obd.readings.speedKmh ?: 0f) > state.speedWarnKmh - 5
    if (state.shiftWarnOn && !speedy) ShiftWarning(state.obd.readings.rpm, state.obd.readings.speedKmh, state.shiftWarnRpm, state.topGear, actions.setTopGear)
    if (state.speedWarnOn) SpeedWarning(state.obd.readings.speedKmh, state.speedWarnKmh)
}

/** CarDash isn't the radio's home app: the Home button and starting the radio open another one. */
internal fun missingPermissions(state: DashState) = !state.nav.permission || !state.hasNotifications || !state.isHomeApp

/** What CarDash needs and hasn't got, each with its fix; the ones already sorted say Allowed. */
@Composable
private fun FixNotice(state: DashState, actions: DashActions, onLater: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onLater, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(680.dp).clip(RoundedCornerShape(28.dp)).background(C.panelHi)
                .padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("CarDash is missing some permissions", color = C.ink, fontSize = 26.sp)
            Text(
                "An update or the radio can take these away. Tap Allow on each one; this goes once they're all allowed.",
                color = C.muted, fontSize = 16.sp, lineHeight = 22.sp,
            )
            Permission("Location", "Your position on the map, for directions", state.nav.permission, actions.allowLocation)
            Permission("Notification access", "Google Maps' next turn, and music controls", state.hasNotifications, actions.allowNotifications)
            Permission("Home screen", "CarDash when the radio starts and with the Home button", state.isHomeApp, actions.chooseHome, button = "Set")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                androidx.compose.material3.TextButton(onClick = onLater) { Text("Not now", color = C.ink2, fontSize = 15.sp) }
            }
        }
    }
}

/**
 * "SLOW THE FUCK DOWN" over the whole screen while over the set speed.
 * It goes once the speed is 5 km/h under again, so it doesn't flicker on and off at the line.
 * Touches go through it, so the music and app buttons still work.
 */
@Composable
private fun SpeedWarning(speedKmh: Float?, overKmh: Int) {
    // Remembers whether it's showing, between the over and under lines (not state: it only changes with the speed)
    val showing = remember { booleanArrayOf(false) }
    val v = speedKmh
    showing[0] = when {
        v == null -> false
        v > overKmh -> true
        v < overKmh - 5 -> false
        else -> showing[0]
    }
    if (!showing[0]) return
    ScreenWarning("SLOW THE FUCK DOWN", v?.toInt()?.toString() ?: "–", "km/h")
}

/**
 * "Shift up" across the whole screen while the engine is over [atRpm] (for most of a second, so a
 * gear change doesn't flash it), going once it's 300 rpm under. Not in top gear: CarDash learns
 * top gear's rpm per km/h as the lowest steady figure it sees driving over 50 km/h, and keeps it.
 */
@Composable
private fun ShiftWarning(rpm: Float?, speedKmh: Float?, atRpm: Int, topGear: Float, learnTopGear: (Float) -> Unit) {
    // Learning top gear: the same rpm per km/h for 3 seconds, driving (not coasting at idle)
    val steady = remember { Steady() }
    LaunchedEffect(rpm, speedKmh) {
        val r = rpm ?: return@LaunchedEffect
        val v = speedKmh ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (v < 50f || r < 1300f) {
            steady.since = 0L
            return@LaunchedEffect
        }
        val ratio = r / v
        if (steady.since == 0L || kotlin.math.abs(ratio - steady.ratio) / steady.ratio > 0.03f) {
            steady.ratio = ratio
            steady.since = now
        } else if (now - steady.since > 3_000L && (topGear == 0f || ratio < topGear * 0.99f)) {
            learnTopGear(ratio)
        }
    }
    val showing = remember { booleanArrayOf(false) }
    val above = remember { longArrayOf(0L) }
    val r = rpm
    val v = speedKmh
    val inTop = r != null && v != null && v > 0f && topGear > 0f && r / v <= topGear * 1.06f
    val now = System.currentTimeMillis()
    showing[0] = when {
        r == null || v == null || v < 5f || inTop -> false.also { above[0] = 0L }
        r >= atRpm -> {
            if (above[0] == 0L) above[0] = now
            showing[0] || now - above[0] > 700
        }
        r < atRpm - 300 -> false.also { above[0] = 0L }
        else -> showing[0]
    }
    if (!showing[0]) return
    ScreenWarning("SHIFT UP", r?.toInt()?.toString() ?: "–", "rpm")
}

/** The same rpm per km/h, and since when: top gear shows as the lowest one held steady. */
private class Steady {
    var ratio = 0f
    var since = 0L
}

/** A warning across the whole screen: the band in the middle, the red around it pulsing twice a second. */
@Composable
private fun ScreenWarning(title: String, figure: String, unit: String) {
    // The red above and below the band pulses twice a second for as long as it's showing
    val flash by rememberInfiniteTransition(label = "warning").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(250), RepeatMode.Reverse),
        label = "flash",
    )
    Box(Modifier.size(1280.dp, 720.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = flash }.background(C.red.copy(alpha = 0.34f)).border(6.dp, C.red))
        Column(
            Modifier.fillMaxWidth().height(278.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).graphicsLayer { alpha = flash }.background(C.red))
            Column(
                Modifier.fillMaxWidth().weight(1f).background(Color(0xE6080305)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Text(
                    title, color = C.red, fontSize = 96.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
                    letterSpacing = 2.sp, maxLines = 1,
                    style = TextStyle(shadow = Shadow(C.red.copy(alpha = 0.6f), blurRadius = 28f)),
                )
                // The figure dead centre, the unit hanging off its right
                Box {
                    Text(figure, color = C.ink, fontSize = 88.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    Text(
                        "\u00A0$unit", color = C.muted, fontSize = 26.sp, maxLines = 1, softWrap = false,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 22.dp)
                            .layout { m, c ->
                                // takes no width, so the number stays centred
                                val p = m.measure(c.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                                layout(0, p.height) { p.place(0, 0) }
                            },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp).graphicsLayer { alpha = flash }.background(C.red))
        }
    }
}

// ---------- frame ----------

@Composable
private fun Header(state: DashState, page: Int?, onPage: (Int) -> Unit, onName: () -> Unit, onSettings: () -> Unit, onService: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onName).padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text(state.name, color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                Text(" //", color = C.red, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            }
        }
        // Time, date and the page dots side by side
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = C.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())), color = C.muted, fontSize = 18.sp)
            if (page != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(PAGES) { i ->
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
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            val due = state.service.dueCount()
            if (due > 0) ServiceChip(due, onService)
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

/**
 * When to change gear in the TT's 2.0 TDI for fuel economy without lugging the dual-mass flywheel.
 * Its 350 Nm comes between 1,750 and 2,500 rpm: change up at 2,000 (2,500 when pulling hard, pedal
 * past half) so the next gear lands back on that plateau, but never into fewer than 1,500 rpm; change
 * down under 1,300 rpm with the foot down, or under 1,100 rpm at all, which is where a diesel
 * starts to judder and the flywheel takes the strain. Up is +1, down -1, with the gear to go to
 * once CarDash has learnt the gears (null before that).
 */
internal fun shiftHint(
    rpm: Float?, kmh: Float?, pedal: Float?, gear: Int?, ratios: List<Float>, rolling: Boolean = false,
    upAt: List<Int> = Settings.SHIFT_UP_DEFAULT, downAt: List<Int> = Settings.SHIFT_DOWN_DEFAULT,
): Pair<Int, Int?>? {
    if (rpm == null || kmh == null || kmh < 5f) return null
    // Rolling at tickover, clutch down or out of gear: nothing to change
    if (rolling) return null
    val known = ratios.isNotEmpty()
    // Gears learnt but none matching: changing gear, clutch down or coasting
    if (known && gear == null) return null
    val p = pedal ?: 0f
    // The foot well down: change up later, and change down sooner, for the pull it needs
    val upExtra = if (p >= 50f) 500 else 0
    val downExtra = if (p >= 30f) 200 else 0
    // Low revs only strain the engine with the foot down: off the pedal (when the car says so) there's
    // nothing to change down for
    val offPedal = pedal != null && pedal < 5f
    if (!known) {
        // Not learnt yet, so no gear: the rpm alone, the points for the middle gears
        if (rpm >= 2000f + upExtra) return 1 to null
        if (!offPedal && kmh >= 20f && rpm < 1100f + downExtra) return -1 to null
        return null
    }
    val g = gear ?: return null
    // The points you set in Settings › Driving, gear by gear
    if (g < ratios.size && rpm >= (upAt.getOrNull(g - 1) ?: 2000) + upExtra) return 1 to g + 1
    if (g > 1 && !offPedal && kmh >= 10f && rpm < (downAt.getOrNull(g - 1) ?: 1100) + downExtra) return -1 to g - 1
    return null
}

/** The hint, held for most of a second after it stops applying, so it doesn't flicker. */
@Composable
private fun rememberShiftHint(state: DashState): Pair<Int, Int?>? {
    val r = state.obd.readings
    val now = shiftHint(r.rpm, r.speedKmh, r.pedalPct, state.gear, state.gearRatios, state.rolling, state.shiftUpAt, state.shiftDownAt)
    val held = remember { arrayOfNulls<Pair<Int, Int?>>(1) }
    val since = remember { longArrayOf(0L) }
    val t = System.currentTimeMillis()
    if (now != null) {
        held[0] = now
        since[0] = t
    } else if (t - since[0] > 800) {
        held[0] = null
    }
    return held[0]
}

/**
 * The change up / change down icons, in the empty corners at the top between the big dials and the
 * middle box: change down on the left, change up on the right. Nothing there until it's time to
 * change; with the big map (no RPM dial) both sit side by side in the right-hand corner.
 */
@Composable
private fun BoxScope.ShiftHintIcons(state: DashState, big: Boolean) {
    if (!state.shiftHints) return
    val hint = rememberShiftHint(state) ?: return
    val accent = C.red
    val up = hint.first > 0
    val vector = remember(up, accent) { if (up) ShiftIcons.up(accent) else ShiftIcons.down(accent) }
    val w = if (big) 52.dp else 64.dp
    val h = w * (if (up) 480f / 360f else 450f / 360f)
    // The middle box starts at 8 + DIAL + 8 from the left and ends as far in from the right
    val x = when {
        big && up -> 1280.dp - DIAL - 16.dp + 70.dp
        big -> 1280.dp - DIAL - 16.dp + 10.dp
        up -> 1280.dp - DIAL - 16.dp + 50.dp - w / 2
        else -> DIAL + 16.dp - 50.dp - w / 2
    }
    Image(
        vector, if (up) "Change up" else "Change down",
        modifier = Modifier.align(Alignment.TopStart).offset(x = x, y = if (up) 23.dp else 29.dp).width(w).height(h),
    )
}

@Composable
private fun NameDialog(name: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    val box = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { box.requestFocus() } }
    KeyboardDialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(900.dp).clip(RoundedCornerShape(28.dp)).background(C.panelHi)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Name", color = C.ink, fontSize = 24.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(20) },
                    singleLine = true,
                    textStyle = TextStyle(color = C.ink, fontSize = 20.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.red, unfocusedBorderColor = C.lineHi, cursorColor = C.red),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(box),
                )
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel", color = C.ink2) }
                    TextButton(onClick = { onSave(text) }) { Text("Save", color = C.red, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ---------- home ----------

@Composable
private fun Home(
    state: DashState, actions: DashActions, centre: PagerState, openSearch: () -> Unit,
    mapDash: Boolean, setMapDash: (Boolean) -> Unit, allApps: () -> Unit,
) {
    val r = state.obd.readings
    val doors = remember(state.can) { Doors.from(state.can.values) }
    // The big map takes the RPM dial's place (its own view, route or not); an open door shows over it
    val views = centreViews(state.routed())
    val big = views.getOrElse(centre.currentPage) { views.last() } == VIEW_BIG_MAP && !doors.anyOpen
    Column(Modifier.fillMaxSize()) {
        // Up or down on the left of the screen: the main dash or the map dash (the middle box keeps its own swipe)
        val swipe = Modifier.leftSwipe(LEFT_SWIPE) { setMapDash(!mapDash) }
        if (mapDash) {
            MapDash(state, actions, openSearch, Modifier.weight(1f).fillMaxWidth().then(swipe))
            BottomBar(state, actions, allApps)
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth().then(swipe)) {
            // The box and the small gauges end just above the bottom bar; the dials are centred
            Row(
                Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The two big dials sit in the middle of the height: the same space above them as below
                // (the Row's 10 dp at the bottom is made up with half of it here)
                if (!big) Box(Modifier.align(Alignment.CenterVertically).offset(y = 5.dp)) { RpmDial(r, state.gear, DIAL) }
                // The box from just under the divider, the small gauges under it, just above the bottom bar
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(top = GAUGE_TOP),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CenterPanel(state, actions, doors, centre, openSearch, wide = big, modifier = Modifier.weight(1f).fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    // Wider than the box: reaches out under the edges of the dials, centred
                    Row(
                        Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true),
                        horizontalArrangement = Arrangement.spacedBy(GAUGE_GAP),
                    ) {
                        if (big) {
                            // With the big map: more small gauges rather than wider ones, RPM and coolant among them
                            RoundGauge(Ic.gauge, "RPM", r.rpm, 0f, 6000f, r.rpm?.let { "${it.toInt()}" }, "rpm", warnAbove = 4500f, diameter = GAUGE)
                            RoundGauge(Ic.coolant, "Coolant", r.coolantC, 40f, 140f, r.coolantC?.let { "${it.toInt()}" }, "°C", warnAbove = 110f, diameter = GAUGE)
                        }
                        SmallGauges(r)
                    }
                }
                Box(Modifier.align(Alignment.CenterVertically).offset(y = 5.dp)) { SpeedDial(r, state.obd.trip.meters, DIAL) }
            }
            ShiftHintIcons(state, big)
            // The speed limit (top right) and traffic lights ahead (top left), in the corners over the dials
            state.nav.limit?.let { limit ->
                val kmh = r.speedKmh ?: (state.nav.speed * 3.6f)
                SpeedSign(limit, kmh > limit + 2, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 14.dp))
            }
            state.nav.signalAheadM?.let { LightsAhead(it, Modifier.align(Alignment.TopStart).padding(top = 10.dp, start = 16.dp)) }
        }
        BottomBar(state, actions, allApps)
    }
}


/** How far in from the left a swipe up or down switches between the main dash and the map dash. */
private val LEFT_SWIPE = 300.dp

/**
 * A swipe up or down that starts within [zone] of the left edge calls [onSwipe]. It only takes over
 * once the finger is clearly going up or down, so taps and sideways swipes (the pages) go on as before,
 * and the middle box's own swipe isn't touched outside the zone.
 */
private fun Modifier.leftSwipe(zone: Dp, onSwipe: () -> Unit): Modifier = pointerInput(Unit) {
    val zonePx = zone.toPx()
    val need = 60.dp.toPx()
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x > zonePx) return@awaitEachGesture
        var claimed = false
        var dx = 0f
        var dy = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val d = change.positionChange()
            dx += d.x
            dy += d.y
            if (!claimed && abs(dy) > slop && abs(dy) > abs(dx) * 1.5f) claimed = true
            if (claimed) change.consume()
            if (!change.pressed) {
                if (claimed && abs(dy) > need) onSwipe()
                break
            }
        }
    }
}

@Composable
private fun SmallGauges(r: Readings) {
    // Oil once the radio's CAN box is read; until then, engine load. The engine icon either way
    if (r.oilC != null) RoundGauge(Ic.engine, "Oil", r.oilC, 40f, 150f, "${r.oilC.toInt()}", "°C", warnAbove = 120f, diameter = GAUGE)
    else RoundGauge(Ic.engine, "Engine load", r.loadPct, 0f, 100f, r.loadPct?.let { "${it.toInt()}" }, "%", diameter = GAUGE)
    RoundGauge(Ic.boost, "Boost", r.boostBar, 0f, 1.5f, r.boostBar?.let { fmt(it, 1) }, "bar", diameter = GAUGE)
    RoundGauge(Ic.intake, "Intake air", r.intakeC, 0f, 70f, r.intakeC?.let { "${it.toInt()}" }, "°C", warnAbove = 55f, diameter = GAUGE)
    RoundGauge(Ic.battery, "Battery", r.volts, 11f, 15f, r.volts?.let { fmt(it, 1) }, "V", warnBelow = 11.8f, diameter = GAUGE)
}

@Composable
private fun RpmDial(r: Readings, gear: Int?, size: Dp) {
    Dial(
        value = r.rpm, max = 6000f, major = 1000f, minor = 250f, mid = 500f, redFrom = 4500f,
        label = { (it / 1000).toInt().toString() }, labelSize = 0.094f,
        bottomArc = true, bottomFill = r.coolantC?.let { (it - 40f) / 100f },
        modifier = Modifier.size(size),
    ) {
        // The gear in the middle, as the speed is in the other dial (worked out: "–" between gears)
        Box(
            Modifier.size(size * 0.214f).clip(CircleShape).background(Color(0xFF0A0C10)).border(2.dp, C.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                gear?.toString() ?: "–",
                color = if (gear == null) C.muted else C.ink, fontSize = (size.value * 0.15f).sp,
                fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1, softWrap = false,
            )
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
        value = r.speedKmh, max = 280f, major = 20f, minor = 10f, labelSize = 0.068f, labelEvery = 40f, smallLabelEvery = 20f,
        modifier = Modifier.size(size),
    ) {
        Box(
            Modifier.size(size * 0.214f).clip(CircleShape).background(Color(0xFF0A0C10)).border(2.dp, C.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                r.speedKmh?.toInt()?.toString() ?: "–",
                // Small enough that 222 fits the circle
                color = C.ink, fontSize = (size.value * 0.09f).sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
                maxLines = 1, softWrap = false,
            )
        }
        // The odometer from the radio's CAN box once that's read; until then, this drive's distance. Just the number
        Text(
            r.odometerKm?.let { "%,d".format(Locale.US, it.toLong()) } ?: fmt((tripMeters / 1000).toFloat(), 1),
            color = C.ink, fontSize = (size.value * 0.066f).sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = size * 0.818f),
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
    /** The canvas below has its own size, so this one has another name. */
    diameter: Dp = 76.dp,
) {
    val frac = value?.let { ((it - min) / (max - min)).coerceIn(0f, 1f) } ?: 0f
    val warn = value != null && ((warnAbove != null && value > warnAbove) || (warnBelow != null && value < warnBelow))
    // Drawn for 76 and scaled from there
    val k = diameter / 76.dp
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx() * k
            val inset = stroke / 2 + 3.dp.toPx() * k
            val tl = Offset(inset, inset)
            val sz = Size(size.width - inset * 2, size.height - inset * 2)
            drawCircle(C.panel, size.minDimension / 2)
            drawCircle(C.lineHi, size.minDimension / 2 - 1.dp.toPx() * k, style = Stroke(1.5.dp.toPx() * k))
            drawArc(C.track, 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            if (value != null && frac > 0.005f) {
                val col = if (warn) C.redLight else C.red
                drawArc(col.copy(alpha = 0.25f), 135f, 270f * frac, false, tl, sz, style = Stroke(stroke * 2.6f, cap = StrokeCap.Round))
                drawArc(col, 135f, 270f * frac, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        // A little above the middle, clear of the gap at the bottom of the ring
        Column(Modifier.padding(bottom = 8.dp * k), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, name, tint = if (warn) C.red else C.ink2, modifier = Modifier.size(18.dp * k))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text ?: "–", color = C.ink, fontSize = (17 * k).sp, fontWeight = FontWeight.SemiBold)
                if (text != null) Text(" $unit", color = C.muted, fontSize = (11 * k).sp, modifier = Modifier.padding(bottom = 3.dp * k))
            }
        }
    }
}

// ---------- centre: the car, directions, the map; doors on top ----------

@Composable
private fun CenterPanel(
    state: DashState,
    actions: DashActions,
    doors: Doors,
    centre: PagerState,
    openSearch: () -> Unit,
    wide: Boolean,
    modifier: Modifier,
) {
    val alert = doors.anyOpen || state.nav.route != null || state.google != null
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(C.panel)
            .border(1.dp, if (alert) C.redDeep else C.line, RoundedCornerShape(22.dp))
            // Hold, on any view, for Where to?
            .pointerInput(Unit) { detectTapGestures(onLongPress = { openSearch() }) },
    ) {
        if (doors.anyOpen) {
            Box(Modifier.fillMaxSize().padding(12.dp)) { DoorsPanel(doors) }
        } else {
            val views = centreViews(state.routed())
            VerticalPager(centre, Modifier.fillMaxSize()) { page ->
                when (views.getOrElse(page) { VIEW_BIG_MAP }) {
                    VIEW_CAR -> Box(Modifier.fillMaxSize().padding(12.dp)) { DrivePanel(state.obd, state.canValues[CanRole.RANGE]) }
                    VIEW_MAP -> Box(Modifier.fillMaxSize().padding(6.dp)) { MapPanel(state, actions, openSearch, false) }
                    VIEW_DIRECTIONS -> Box(Modifier.fillMaxSize().padding(12.dp)) { DirectionsPanel(state, actions, openSearch) }
                    else -> Box(Modifier.fillMaxSize().padding(6.dp)) { MapPanel(state, actions, openSearch, wide) }
                }
            }
            // Which view is on: dots down the right edge
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(views.size) { i ->
                    Box(
                        Modifier.width(5.dp).height(if (i == centre.currentPage) 16.dp else 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i == centre.currentPage) C.red else C.lineHi),
                    )
                }
            }
        }
    }
}

/** The car from above (the user's own pictures), with whatever's open in the accent colour. */
@Composable
private fun DoorsPanel(d: Doors) {
    val left = d.frontLeft || d.rearLeft
    val right = d.frontRight || d.rearRight
    val picture = when {
        left && right && d.bonnet && d.boot -> R.drawable.car_all
        left && right && d.bonnet -> R.drawable.car_both_bonnet
        left && right && d.boot -> R.drawable.car_both_hatch
        left && right -> R.drawable.car_both
        left && d.bonnet && d.boot -> R.drawable.car_left_bonnet_hatch
        right && d.bonnet && d.boot -> R.drawable.car_right_bonnet_hatch
        left && d.bonnet -> R.drawable.car_left_bonnet
        right && d.bonnet -> R.drawable.car_right_bonnet
        left && d.boot -> R.drawable.car_left_hatch
        right && d.boot -> R.drawable.car_right_hatch
        left -> R.drawable.car_left
        right -> R.drawable.car_right
        d.bonnet && d.boot -> R.drawable.car_bonnet_hatch
        d.bonnet -> R.drawable.car_bonnet
        d.boot -> R.drawable.car_hatch
        else -> R.drawable.car_closed
    }
    val tint = remember(C.red) { accentFilter(C.red) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(d.describe(), color = C.red, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Image(
            painterResource(picture), d.describe(),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
            contentScale = ContentScale.Fit,
            colorFilter = tint,
        )
    }
}

/**
 * The pictures are drawn in red: turned to the accent colour's hue (white stays white), or left as
 * they are while the accent is red.
 */
private fun accentFilter(accent: Color): ColorFilter? {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
    val red = FloatArray(3)
    android.graphics.Color.colorToHSV(C.RED.toArgb(), red)
    var deg = hsv[0] - red[0]
    if (deg > 180) deg -= 360
    if (deg < -180) deg += 360
    if (kotlin.math.abs(deg) < 3f) return null
    val a = kotlin.math.cos(Math.toRadians(deg.toDouble())).toFloat()
    val b = kotlin.math.sin(Math.toRadians(deg.toDouble())).toFloat()
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.213f + 0.787f * a - 0.213f * b, 0.715f - 0.715f * a - 0.715f * b, 0.072f - 0.072f * a + 0.928f * b, 0f, 0f,
                0.213f - 0.213f * a + 0.143f * b, 0.715f + 0.285f * a + 0.140f * b, 0.072f - 0.072f * a - 0.283f * b, 0f, 0f,
                0.213f - 0.213f * a - 0.787f * b, 0.715f - 0.715f * a + 0.715f * b, 0.072f + 0.928f * a + 0.072f * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/**
 * What the engine is doing (the bars) and this drive so far. Numbers are big
 * enough to read at a glance; the outside temperature is in the header, so its place here shows how
 * long this drive has been.
 */
@Composable
private fun DrivePanel(obd: ObdState, rangeKm: Float?) {
    val r = obd.readings
    val t = obd.trip
    Column(Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Bar("Accelerator pedal", r.pedalPct, 100f, r.pedalPct?.let { "${it.toInt()}" }, "%")
            Bar("Engine load", r.loadPct, 100f, r.loadPct?.let { "${it.toInt()}" }, "%")
            Bar("Boost", r.boostBar?.coerceAtLeast(0f), 1.5f, r.boostBar?.let { fmt(it, 2) }, "bar")
            // L/100 km while moving, L/h standing still
            val l100 = r.fuelL100
            val fuel = l100 ?: r.fuelLph
            Bar("Fuel use", fuel, if (l100 != null) 25f else 10f, fuel?.let { fmt(it, 1) }, if (l100 != null) "L/100" else "L/h")
        }
        // The drive's figures straight under the bars
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat("Average", t.avgL100?.let { fmt(it.toFloat(), 1) }, "L/100", Modifier.weight(1f))
                Stat("Fuel used", if (t.fuelL > 0) fmt(t.fuelL.toFloat(), 2) else null, "L", Modifier.weight(1f))
                Stat("Max boost", if (t.maxBoostBar > 0) fmt(t.maxBoostBar, 2) else null, "bar", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat("Distance", fmt((t.meters / 1000).toFloat(), 1), "km", Modifier.weight(1f))
                Stat("Avg speed", t.avgKmh.toInt().toString(), "km/h", Modifier.weight(1f))
                Stat("Top speed", t.maxKmh.toInt().toString(), "km/h", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat("Max RPM", if (t.maxRpm > 0) "%,d".format(Locale.US, t.maxRpm.toInt()) else null, "", Modifier.weight(1f))
                val range = rangeKm
                if (range != null) Stat("Range", "%,d".format(Locale.US, range.toLong()), "km", Modifier.weight(1f))
                else Stat("Air flow", r.mafGs?.let { fmt(it, if (it >= 100f) 0 else 1) }, "g/s", Modifier.weight(1f))
                val minutes = t.drivingMs / 60_000
                if (minutes < 60) Stat("Driving", "$minutes", "min", Modifier.weight(1f))
                else Stat("Driving", "%d:%02d".format(minutes / 60, minutes % 60), "h", Modifier.weight(1f))
            }
        }
    }
}

/** A live reading: its name, a big number and a bar. */
@Composable
private fun Bar(name: String, value: Float?, max: Float, number: String?, unit: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(name, color = C.ink2, fontSize = 19.sp, maxLines = 1, modifier = Modifier.weight(1f).alignByBaseline())
            Text(
                number ?: "–", color = if (number == null) C.muted else C.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic, maxLines = 1, modifier = Modifier.alignByBaseline(),
            )
            if (number != null) Text(" $unit", color = C.muted, fontSize = 15.sp, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.track)) {
            Box(Modifier.fillMaxWidth(((value ?: 0f) / max).coerceIn(0f, 1f)).fillMaxHeight().background(C.red))
        }
    }
}

/** A figure for this drive: a small name over a big number. */
@Composable
internal fun Stat(name: String, value: String?, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, color = C.muted, fontSize = 17.sp, maxLines = 1)
        Row {
            Text(
                value ?: "–", color = if (value == null) C.muted else C.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline(),
            )
            if (value != null && unit.isNotEmpty()) {
                Text(" $unit", color = C.muted, fontSize = 12.sp, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
            }
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
private fun Gauges(obd: ObdState, can: Map<CanRole, Float>, gear: Int?, actions: DashActions) {
    val r = obd.readings
    val t = obd.trip
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RpmDial(r, gear, 300.dp)
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
            ) + listOf(CanRole.ODOMETER, CanRole.RANGE, CanRole.FUEL_LEVEL, CanRole.FUEL_AVG).mapNotNull { role ->
                // From the radio's CAN box, once assigned in Settings
                can[role]?.let { v -> Triple(role.title, if (role == CanRole.ODOMETER || role == CanRole.RANGE) "%,d".format(Locale.US, v.toLong()) else fmt(v, 1), role.unit) }
            }
            LazyVerticalGrid(
                GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(384.dp),
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
@OptIn(ExperimentalFoundationApi::class)
private fun AppsScreen(apps: List<AppEntry>, actions: DashActions) {
    var holding by remember { mutableStateOf<AppEntry?>(null) }
    holding?.let { app ->
        AlertDialog(
            onDismissRequest = { holding = null },
            containerColor = C.panelHi,
            title = { Text(app.label, color = C.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (app.removable) Wide("Uninstall") { holding = null; actions.uninstall(app.pkg) }
                    else Text("This app is part of the radio and can't be uninstalled; it can be turned off in App info.", color = C.muted, fontSize = 14.sp)
                    Wide("App info") { holding = null; actions.appInfo(app.pkg) }
                }
            },
            confirmButton = { TextButton(onClick = { holding = null }) { Text("Cancel", color = C.ink2) } },
        )
    }
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
                    .combinedClickable(onClick = { actions.launch(app.pkg) }, onLongClick = { holding = app })
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

/** Settings' sections, down the left, each with its own cards. */
private enum class SettingsSection(val title: String, val sub: String) {
    DRIVING("Driving", "Warnings and gears"),
    NAVIGATION("Navigation", "Map, key, offline maps"),
    CAR("Car", "OBD, CAN box, fuel"),
    LOOK("Look", "Colour, name, screen"),
    MUSIC("Music", "The music app"),
    PERMISSIONS("Permissions", "What CarDash may use"),
}

private val SettingsSection.icon: ImageVector
    get() = when (this) {
        SettingsSection.DRIVING -> Ic.gauge
        SettingsSection.NAVIGATION -> Ic.map
        SettingsSection.CAR -> Ic.car
        SettingsSection.LOOK -> Ic.star
        SettingsSection.MUSIC -> Ic.music
        SettingsSection.PERMISSIONS -> Ic.check
    }

@Composable
private fun SettingsScreen(state: DashState, actions: DashActions, close: () -> Unit) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf(SettingsSection.DRIVING) }
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
            Column(Modifier.width(232.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsSection.entries.forEach { s -> SectionTab(s, s == section) { section = s } }
            }
            // A fresh scroll for each section, from the top
            key(section) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (section) {
                        SettingsSection.DRIVING -> {
                            TwoColumns(
                                { SpeedWarningCard(state, actions); ShiftUpCard(state, actions) },
                                { GearCard(state, actions) },
                            )
                            ShiftPointsCard(state, actions)
                        }
                        SettingsSection.NAVIGATION -> TwoColumns({ NavigationCard(state, actions) }, { OfflineMapCard(state, actions) })
                        SettingsSection.CAR -> TwoColumns({ ObdCard(state, actions); FuelCard(state, actions) }, { CanCard(state, actions) })
                        SettingsSection.LOOK -> TwoColumns({ AccentCard(state, actions) }, { NameCard(state, actions); ScreenCard(state, actions) })
                        SettingsSection.MUSIC -> TwoColumns({ MusicCard(state, actions) }, { })
                        SettingsSection.PERMISSIONS -> TwoColumns({ PermissionsCard(state, actions) }, { })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTab(s: SettingsSection, chosen: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .then(if (chosen) Modifier.background(C.panelHi).border(1.dp, C.lineHi, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(if (chosen) C.red else Color.Transparent))
        Icon(s.icon, null, tint = if (chosen) C.red else C.ink2, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = if (chosen) C.ink else C.ink2, fontSize = 18.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
            Text(s.sub, color = C.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Cards side by side in two columns, each column its own height. */
@Composable
private fun TwoColumns(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { left() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { right() }
    }
}

@Composable
private fun NameCard(state: DashState, actions: DashActions) {
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
}

@Composable
private fun MusicCard(state: DashState, actions: DashActions) {
    Card("Music") {
        val player = (state.musicApp?.let { pkg -> state.apps.firstOrNull { it.pkg == pkg } } ?: Apps.defaultFor(2, state.apps))?.label
        Text("Music player: ${player ?: "none found"}", color = C.ink, fontSize = 17.sp)
        Text("Play on Home starts it when nothing is playing", color = C.muted, fontSize = 14.sp)
        var choosing by remember { mutableStateOf(false) }
        Wide("Choose the music app") { choosing = true }
        if (choosing) AppPicker(state.apps, onPick = { actions.setMusicApp(it); choosing = false }, onDismiss = { choosing = false })
    }
}

@Composable
private fun ScreenCard(state: DashState, actions: DashActions) {
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

@Composable
private fun SpeedWarningCard(state: DashState, actions: DashActions) {
    Card("Speed warning") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Warn me when I'm too fast", color = C.ink, fontSize = 17.sp)
                Text("\"SLOW THE FUCK DOWN\" across the whole screen", color = C.muted, fontSize = 14.sp)
            }
            Switch(
                checked = state.speedWarnOn,
                onCheckedChange = actions.setSpeedWarnOn,
                colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = C.ink),
            )
        }
        if (state.speedWarnOn) {
            Stepper(
                "Over", "${state.speedWarnKmh} km/h",
                less = { actions.setSpeedWarnKmh(state.speedWarnKmh - Settings.SPEED_WARN_STEP) },
                more = { actions.setSpeedWarnKmh(state.speedWarnKmh + Settings.SPEED_WARN_STEP) },
            )
            Text("It goes away once you're 5 km/h under again.", color = C.muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun GearCard(state: DashState, actions: DashActions) {
    Card("Gear") {
        Text("The gear you're in, in the middle of the RPM dial", color = C.ink, fontSize = 17.sp)
        Text("The car doesn't send it, so CarDash learns each gear's revs per km/h as you drive.", color = C.muted, fontSize = 14.sp)
        Stepper(
            "Gears", "${state.gearCount}",
            less = { actions.setGearCount(state.gearCount - 1) },
            more = { actions.setGearCount(state.gearCount + 1) },
        )
        Text(
            if (state.gearsFound >= state.gearCount) "Learnt: all ${state.gearCount} gears."
            else "Learning: ${state.gearsFound} of ${state.gearCount} found. Drive a few seconds in each gear: it shows the gear once it knows them all, or all but 1st or top.",
            color = C.muted, fontSize = 14.sp,
        )
        Wide("Learn the gears again") { actions.relearnGears() }
    }
}

@Composable
private fun ShiftUpCard(state: DashState, actions: DashActions) {
    Card("Shift up") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tell me to change up", color = C.ink, fontSize = 17.sp)
                Text("\"SHIFT UP\" across the whole screen", color = C.muted, fontSize = 14.sp)
            }
            Switch(
                checked = state.shiftWarnOn,
                onCheckedChange = actions.setShiftWarnOn,
                colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = C.ink),
            )
        }
        if (state.shiftWarnOn) {
            Stepper(
                "Over", "${state.shiftWarnRpm} rpm",
                less = { actions.setShiftWarnRpm(state.shiftWarnRpm - Settings.SHIFT_WARN_STEP) },
                more = { actions.setShiftWarnRpm(state.shiftWarnRpm + Settings.SHIFT_WARN_STEP) },
            )
            Text(
                if (state.topGear > 0f) "Not in top gear: there's no gear left to change up to."
                else "Not in top gear, once CarDash has learnt it: drive a minute over 50 km/h in top gear.",
                color = C.muted, fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun PermissionsCard(state: DashState, actions: DashActions) {
    Card("Permissions") {
        Permission("Notification access", "Google Maps' next turn, and music controls", state.hasNotifications, actions.allowNotifications)
        Permission("Location", "Your position on the map, for directions", state.nav.permission, actions.allowLocation)
        Permission("Home screen", "CarDash when the radio starts and with the Home button", state.isHomeApp, actions.chooseHome, button = "Set")
        Text("If any of these is missing when the radio starts, CarDash shows them in a popup to fix.", color = C.muted, fontSize = 14.sp)
    }
}

@Composable
private fun ObdCard(state: DashState, actions: DashActions) {
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
}

@Composable
private fun FuelCard(state: DashState, actions: DashActions) {
    Card("Fuel use") {
        val r = state.obd.readings
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Diesel engine", color = C.ink, fontSize = 17.sp)
                Text("A diesel's fuel follows engine load, not air flow", color = C.muted, fontSize = 14.sp)
            }
            Switch(
                checked = state.diesel,
                onCheckedChange = actions.setDiesel,
                colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = C.ink),
            )
        }
        Text(
            when {
                r.fuelRateLph != null -> "The engine reports its own fuel use: ${fmt(r.fuelRateLph, 1)} L/h"
                r.dieselLph != null -> "Now: ${fmt(r.dieselLph, 1)} L/h, worked out from engine load and RPM"
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
        val t = state.obd.trip
        Text(
            "More accurate: after a drive of a few km, reset the car's short-term average at the start, then type its average here. " +
                "CarDash's own for this drive: ${t.avgL100?.let { fmt(it.toFloat(), 1) + " L/100" } ?: "not enough distance yet"}.",
            color = C.muted, fontSize = 14.sp,
        )
        var carAvg by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = carAvg,
                onValueChange = { v -> carAvg = v.filter { it.isDigit() || it == '.' || it == ',' }.take(5) },
                singleLine = true,
                placeholder = { Text("8.5", color = C.muted) },
                textStyle = TextStyle(color = C.ink, fontSize = 18.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.red, unfocusedBorderColor = C.lineHi, cursorColor = C.red),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.width(120.dp),
            )
            Text("L/100", color = C.muted, fontSize = 16.sp)
            Box(Modifier.weight(1f)) {
                Wide("Match drive") { carAvg.replace(',', '.').toFloatOrNull()?.let(actions.matchFuelAverage) }
            }
        }
        Wide("Undo matching", actions.resetFuel)
    }
}

/**
 * The change up / down signs: on or off, and where they come up, gear by gear. Each point shows the
 * speed it means in that gear and the revs it lands at in the next, once the gears are learnt.
 */
@Composable
private fun ShiftPointsCard(state: DashState, actions: DashActions) {
    Card("Change up / down signs") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show the signs", color = C.ink, fontSize = 17.sp)
                Text("By the dials, only when it's time to change", color = C.muted, fontSize = 14.sp)
            }
            Switch(
                checked = state.shiftHints,
                onCheckedChange = actions.setShiftHints,
                colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = C.ink),
            )
        }
        if (state.shiftHints) {
            Text(
                "Where they come up, gear by gear. With the pedal past half way, change up waits 500 rpm more; with your foot well down, " +
                    "change down comes 200 rpm sooner. Nothing while rolling at tickover, and no change down off the pedal.",
                color = C.muted, fontSize = 14.sp, lineHeight = 20.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Gear", color = C.muted, fontSize = 13.sp, modifier = Modifier.width(56.dp))
                Text("Change up at", color = C.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("Change down under", color = C.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            }
            for (g in 1..state.gearCount) ShiftPointRow(g, state, actions)
            if (state.gearRatios.isEmpty()) Text("The speeds show once CarDash has learnt the gears.", color = C.muted, fontSize = 13.sp)
            Wide("Back to the suggested points") { actions.resetShiftPoints() }
        }
    }
}

@Composable
private fun ShiftPointRow(g: Int, state: DashState, actions: DashActions) {
    val n = state.gearCount
    val r = state.gearRatios
    val step = Settings.SHIFT_POINT_STEP
    val up = state.shiftUpAt.getOrElse(g - 1) { 2000 }
    val down = state.shiftDownAt.getOrElse(g - 1) { 1100 }
    // The speed an rpm means in this gear, and the rpm it lands at in another
    fun kmh(rpm: Int) = r.getOrNull(g - 1)?.let { (rpm / it).roundToInt() }
    fun inGear(k: Int, rpm: Int) = r.getOrNull(k - 1)?.let { other -> r.getOrNull(g - 1)?.let { (rpm * other / it).roundToInt() } }
    fun note(rpm: Int, k: Int) = listOfNotNull(kmh(rpm)?.let { "$it km/h" }, inGear(k, rpm)?.let { "${ordinal(k)} at ${grouped(it)}" }).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(ordinal(g), color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f)) {
            if (g < n) PointStep("${grouped(up)} rpm", note(up, g + 1), { actions.setShiftUpAt(g, up - step) }, { actions.setShiftUpAt(g, up + step) })
            else Text("Top gear: none", color = C.muted, fontSize = 15.sp)
        }
        Box(Modifier.weight(1f)) {
            if (g > 1) PointStep("${grouped(down)} rpm", note(down, g - 1), { actions.setShiftDownAt(g, down - step) }, { actions.setShiftDownAt(g, down + step) })
            else Text("None from 1st", color = C.muted, fontSize = 15.sp)
        }
    }
    // A change up that lands under the next gear's change-down point would bounce back and forth
    val lands = if (g < n) inGear(g + 1, up) else null
    val nextDown = state.shiftDownAt.getOrNull(g)
    if (lands != null && nextDown != null && lands < nextDown) {
        Text(
            "Changing up lands at ${grouped(lands)} rpm in ${ordinal(g + 1)}, under its change-down point: the signs would go back and forth.",
            color = C.red, fontSize = 13.sp, modifier = Modifier.padding(start = 68.dp),
        )
    }
}

/** − value + with what it means underneath. */
@Composable
private fun PointStep(value: String, note: String, less: () -> Unit, more: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundIcon(Ic.minus, "Less", less)
        Column(Modifier.width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (note.isNotEmpty()) Text(note, color = C.muted, fontSize = 12.sp, maxLines = 1)
        }
        RoundIcon(Ic.plus, "More", more)
    }
}

private fun ordinal(g: Int) = g.toString() + when {
    g % 100 in 11..13 -> "th"
    g % 10 == 1 -> "st"
    g % 10 == 2 -> "nd"
    g % 10 == 3 -> "rd"
    else -> "th"
}

private fun grouped(n: Int) = "%,d".format(java.util.Locale.UK, n)

/**
 * Everything the radio's CAN box sends, by code, live. Tapping one assigns it to something on the
 * dashboard (oil temperature, odometer…), with how to scale it.
 */
@Composable
private fun CanCard(state: DashState, actions: DashActions) {
    var picking by remember { mutableStateOf<CanValue?>(null) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Card("Radio's CAN box") {
        Text(state.can.status, color = C.ink, fontSize = 16.sp)
        if (state.canPicks.isNotEmpty()) {
            state.canPicks.forEach { (role, pick) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(role.title, color = C.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(
                        state.canValues[role]?.let { "${fmt(it, 1)} ${role.unit}" } ?: "–",
                        color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text("  code ${pick.code}", color = C.muted, fontSize = 13.sp)
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { actions.setCanPick(role, null) }, contentAlignment = Alignment.Center) {
                        Text("✕", color = C.muted, fontSize = 16.sp)
                    }
                }
            }
        }
        Text(
            "Every value the CAN box sends. Compare with the radio's Vehicle settings (oil, odometer…), tap the matching row and say what it is. " +
                "Values that just changed are red.",
            color = C.muted, fontSize = 14.sp,
        )
        val rows = state.can.values.values.sortedBy { it.code }
        if (rows.isEmpty()) Text("Nothing received yet", color = C.muted, fontSize = 14.sp)
        else Column(
            Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF05060A))
                .verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        ) {
            rows.forEach { v ->
                val fresh = clock - v.at < 3_000
                Row(
                    Modifier.fillMaxWidth().clickable { picking = v }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${v.code}", color = C.muted, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(48.dp))
                    Text(v.describe(), color = if (fresh) C.red else C.ink2, fontSize = 14.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    picking?.let { v -> CanPicker(v, onPick = { role, pick -> actions.setCanPick(role, pick); picking = null }, onDismiss = { picking = null }) }
}

@Composable
private fun CanPicker(v: CanValue, onPick: (CanRole, CanPick) -> Unit, onDismiss: () -> Unit) {
    var index by remember { mutableStateOf(if (v.ints.size == 1) 0 else -1) }
    var role by remember { mutableStateOf<CanRole?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.panelHi,
        title = { Text("Code ${v.code}", color = C.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    v.ints.isEmpty() -> Text("This one has no numbers to use: ${v.describe()}", color = C.ink2)
                    index < 0 -> {
                        Text("Which number?", color = C.ink2)
                        v.ints.forEachIndexed { i, n -> Wide("$n") { index = i } }
                    }
                    role == null -> {
                        Text("${v.ints[index]} is the…", color = C.ink2)
                        CanRole.entries.forEach { r -> Wide(r.title) { role = r } }
                    }
                    else -> {
                        val raw = v.ints[index]
                        Text("Which matches the car's ${role!!.title.lowercase()}?", color = C.ink2)
                        CanScale.entries.forEach { s ->
                            Wide("${fmt(s.apply(raw), 1)} ${role!!.unit}   (${s.title})") { onPick(role!!, CanPick(v.code, index, s)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.ink2) } },
    )
}

@Composable
internal fun Card(title: String, content: @Composable () -> Unit) {
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
internal fun Permission(name: String, why: String, granted: Boolean, ask: () -> Unit, button: String = "Allow") {
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
        ) { Text(button, color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
internal fun Wide(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(C.panelHi)
            .border(1.dp, C.lineHi, RoundedCornerShape(26.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

// ---------- formatting ----------

internal fun fmt(v: Float, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", v)

private fun clock(ms: Long, hours: Boolean = false): String {
    val s = ms / 1000
    return if (hours) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
