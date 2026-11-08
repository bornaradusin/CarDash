package io.github.bornaradusin.cardash.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.bornaradusin.cardash.car.NavInfo
import io.github.bornaradusin.cardash.nav.Geo
import io.github.bornaradusin.cardash.nav.MapArea
import io.github.bornaradusin.cardash.nav.NavState
import io.github.bornaradusin.cardash.nav.Place
import io.github.bornaradusin.cardash.nav.Places
import io.github.bornaradusin.cardash.nav.Route
import io.github.bornaradusin.cardash.nav.Step
import io.github.bornaradusin.cardash.nav.road
import io.github.bornaradusin.cardash.nav.text
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- the turn arrows ----------

/** The arrow for a step of CarDash's route. */
internal fun turnIcon(s: Step): ImageVector {
    val m = s.modifier ?: ""
    return when {
        s.type == "arrive" -> Ic.arrive
        s.type.contains("roundabout") || s.type.contains("rotary") -> Ic.roundabout
        m == "uturn" -> Ic.uTurn
        s.type == "fork" || s.type == "off ramp" || s.type == "on ramp" || s.type == "merge" ->
            if (m.contains("left")) Ic.keepLeft else if (m.contains("right")) Ic.keepRight else Ic.straight
        m == "slight left" -> Ic.keepLeft
        m == "slight right" -> Ic.keepRight
        m.contains("left") -> Ic.turnLeft
        m.contains("right") -> Ic.turnRight
        else -> Ic.straight
    }
}

/** The arrow for Google Maps' next turn, from its words when its own picture isn't there. */
private fun googleTurn(text: String): ImageVector {
    val l = text.lowercase()
    return when {
        "u-turn" in l || "u turn" in l -> Ic.uTurn
        "roundabout" in l -> Ic.roundabout
        "keep left" in l || "slight left" in l || "bear left" in l -> Ic.keepLeft
        "keep right" in l || "slight right" in l || "bear right" in l || "exit" in l -> Ic.keepRight
        "left" in l -> Ic.turnLeft
        "right" in l -> Ic.turnRight
        "arriv" in l || "destination" in l -> Ic.arrive
        else -> Ic.straight
    }
}

private fun arriveAt(leftS: Double): String =
    LocalTime.now().plusSeconds(leftS.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun minutes(s: Double): String {
    val m = (s / 60).toInt().coerceAtLeast(0)
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}

private fun minutesValue(s: Double): Pair<String, String> {
    val m = (s / 60).toInt().coerceAtLeast(0)
    return if (m < 60) "$m" to "min" else "%d:%02d".format(m / 60, m % 60) to "h"
}

private fun kmValue(m: Double): Pair<String, String> =
    if (m < 950) "${(m / 10).toInt() * 10}" to "m" else fmt((m / 1000).toFloat(), if (m < 99_500) 1 else 0) to "km"

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).clip(CircleShape).background(C.panelHi).border(1.dp, C.lineHi, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = C.ink, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun TurnBox(icon: ImageVector?, bitmap: androidx.compose.ui.graphics.ImageBitmap?, size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.18f).dp)).background(C.redTint), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(bitmap, null, Modifier.size((size * 0.76f).dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(C.red))
            icon != null -> Icon(icon, null, tint = C.red, modifier = Modifier.size((size * 0.76f).dp))
        }
    }
}

// ---------- middle box: directions ----------

/**
 * The next turn filling the middle box: the arrow big in the middle, how far, and what to do,
 * all centred; [extra] goes under it (the turn after).
 */
@Composable
private fun BigTurn(
    icon: ImageVector?,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    distance: String?,
    instruction: String,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TurnBox(icon, bitmap, 124)
        Spacer(Modifier.height(10.dp))
        if (distance != null) {
            Text(distance, color = C.ink, fontSize = 50.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1, textAlign = TextAlign.Center)
        }
        Text(
            instruction, color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 29.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        extra()
    }
}

/** Google's time left ("1 hr 5 min", "40 min") as a figure and its unit: 40 min, or 1:05 h. */
private fun googleMinutes(t: String?): Pair<String?, String> {
    t ?: return null to ""
    val h = Regex("(\\d+)\\s*(?:h|hr|hrs|hour|hours)\\b", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val m = Regex("(\\d+)\\s*(?:min|mins|minute|minutes)\\b", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    return when {
        h == 0 && m == 0 -> t.substringBefore(' ') to t.substringAfter(' ', "")
        h == 0 -> "$m" to "min"
        else -> String.format(java.util.Locale.UK, "%d:%02d", h, m) to "h"
    }
}

/** The next turn big, the one after, and when you'll get there; or Google Maps' turn; or nothing yet. */
@Composable
internal fun DirectionsPanel(state: DashState, actions: DashActions, onSearch: () -> Unit) {
    val nav = state.nav
    val google = state.google
    val step = nav.nextStep
    when {
        nav.route != null && step != null -> Column(Modifier.fillMaxSize()) {
            BigTurn(turnIcon(step), null, Geo.distanceText(nav.toNextM), step.text(), Modifier.weight(1f)) {
                nav.afterStep?.takeIf { step.type != "arrive" }?.let { then ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(turnIcon(then), null, tint = C.ink2, modifier = Modifier.size(20.dp))
                        Text("Then ${then.text().replaceFirstChar { it.lowercase() }}", color = C.ink2, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            RouteFigures(nav)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Wide("End route", actions.endRoute) }
                RoundIconButton(if (nav.muted) Ic.muted else Ic.volume, if (nav.muted) "Sound on" else "Mute", actions.toggleMute)
            }
        }
        nav.destination != null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(nav.destination.name, color = C.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(nav.error ?: "Planning the route…", color = C.ink2, fontSize = 19.sp)
            Spacer(Modifier.weight(1f))
            Wide("End route", actions.endRoute)
        }
        google != null -> GoogleDirections(google, actions)
        else -> NoRoute(onSearch)
    }
}

@Composable
private fun RouteFigures(nav: NavState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Stat("Arrive", arriveAt(nav.leftS), "", Modifier.weight(1f))
        val (t, tu) = minutesValue(nav.leftS)
        Stat("Time left", t, tu, Modifier.weight(1f))
        val (d, du) = kmValue(nav.leftM)
        Stat("Distance", d, du, Modifier.weight(1f))
    }
}

@Composable
private fun GoogleDirections(g: NavInfo, actions: DashActions) {
    val context = LocalContext.current
    val arrow = remember(g.icon) { runCatching { g.icon?.loadDrawable(context)?.toBitmap(160, 160)?.asImageBitmap() }.getOrNull() }
    Column(Modifier.fillMaxSize()) {
        BigTurn(if (arrow == null) googleTurn(g.instruction) else null, arrow, g.distance, g.instruction, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat("Arrive", g.arrive, "", Modifier.weight(1f))
            val (t, tu) = googleMinutes(g.timeLeft)
            Stat("Time left", t, tu, Modifier.weight(1f))
            val d = g.distanceLeft?.split(' ', limit = 2)
            Stat("Distance", d?.getOrNull(0), d?.getOrNull(1) ?: "", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Wide("Google Maps") { actions.launch(Places.GOOGLE_MAPS) }
    }
}

@Composable
private fun NoRoute(onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("No route", color = C.ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Where to? plans one with CarDash's own map. Navigating in Google Maps? Its turns show here.",
            color = C.ink2, fontSize = 19.sp, lineHeight = 25.sp,
        )
        Spacer(Modifier.weight(1f))
        WhereToButton(onSearch)
        Text("Hold the middle box any time for Where to?", color = C.muted, fontSize = 14.sp)
    }
}

@Composable
private fun WhereToButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(C.redTint)
            .border(1.5.dp, C.red, RoundedCornerShape(28.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.search, null, tint = C.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("Where to?", color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- middle box: the map ----------

/** The map, with the next turn on top and the arrival underneath; or Where to? when there's no route. */
@Composable
internal fun MapPanel(state: DashState, actions: DashActions, onSearch: () -> Unit, wide: Boolean) {
    val nav = state.nav
    val step = nav.nextStep
    LaunchedEffect(nav.permission) { if (!nav.permission) actions.askLocation() }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
        NavMap(nav, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    nav.route != null && step != null -> Row(
                        Modifier.then(if (wide) Modifier.widthIn(max = 460.dp) else Modifier.weight(1f))
                            .clip(RoundedCornerShape(16.dp)).background(C.panel.copy(alpha = 0.94f))
                            .border(1.dp, C.redDeep, RoundedCornerShape(16.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TurnBox(turnIcon(step), null, 52)
                        Column {
                            Text(Geo.distanceText(nav.toNextM), color = C.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1)
                            Text(step.road() ?: step.text(), color = C.ink2, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    nav.destination != null -> Chip(nav.error ?: "Planning the route…")
                    !nav.permission -> Chip("Allow location for the map")
                    !nav.located -> Chip("Finding the GPS…")
                    // The street you're on, across the whole box so it reads at a glance
                    nav.street != null -> Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(C.panel.copy(alpha = 0.92f))
                            .border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(nav.street, color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (wide) Spacer(Modifier.weight(1f))
                if (nav.route != null) RoundIconButton(if (nav.muted) Ic.muted else Ic.volume, if (nav.muted) "Sound on" else "Mute", actions.toggleMute)
            }
            Spacer(Modifier.weight(1f))
            when {
                nav.route != null -> Row(
                    Modifier.then(if (wide) Modifier.widthIn(max = 460.dp) else Modifier.fillMaxWidth())
                        .clip(RoundedCornerShape(16.dp)).background(C.panel.copy(alpha = 0.94f))
                        .border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Figure(arriveAt(nav.leftS), "arrive")
                    Figure(minutes(nav.leftS), "left")
                    Figure(Geo.distanceText(nav.leftM), "to go")
                }
                !nav.permission -> Box(Modifier.fillMaxWidth()) { Wide("Allow location", actions.allowLocation) }
                nav.destination == null -> WhereToButton(onSearch, if (wide) Modifier.widthIn(max = 360.dp) else Modifier)
            }
        }
        // The speed limit on the road you're on, red inside once you're over it
        nav.limit?.let { limit ->
            val kmh = state.obd.readings.speedKmh ?: (nav.speed * 3.6f)
            SpeedSign(limit, kmh > limit + 2, Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 12.dp))
        }
    }
}

/** A round speed-limit sign as on Irish roads: white, red ring, the number in black. */
@Composable
private fun SpeedSign(limit: Int, over: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(64.dp).clip(CircleShape).background(if (over) Color(0xFFFFD9DC) else Color.White)
            .border(7.dp, Color(0xFFE3061A), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$limit", color = Color.Black, fontSize = if (limit >= 100) 21.sp else 25.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(C.panel.copy(alpha = 0.92f)).border(1.dp, C.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, color = C.ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun Figure(value: String, name: String) {
    Column {
        Text(value, color = C.ink, fontSize = 23.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1)
        Text(name, color = C.muted, fontSize = 13.sp)
    }
}

// ---------- Where to? ----------

/** Search, the place found, and Go; Home, Work and recent places beside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WhereToScreen(state: DashState, actions: DashActions, close: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var googleSaid by remember { mutableStateOf<String?>(null) }
    val hasKey = state.googleKey.isNotBlank()
    var picked by remember { mutableStateOf<Place?>(null) }
    var preview by remember { mutableStateOf<Route?>(null) }
    var previewing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<Place?>(null) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val here = state.nav.here

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        if (!state.nav.permission) actions.askLocation()
    }
    LaunchedEffect(query) {
        val q = query.trim()
        offline = false
        googleSaid = null
        // Eircodes: looked up with Google when there's a key, otherwise straight to the Google Maps card
        if (q.length < 3 || (Places.isEircode(q) && !hasKey)) {
            results = if (Places.isEircode(q)) emptyList() else null
            searching = false
            return@LaunchedEffect
        }
        delay(400)
        searching = true
        results = runCatching { actions.search(q) }.onFailure { e ->
            if (Places.isEircode(q)) googleSaid = e.message ?: "no answer" else offline = true
        }.getOrNull() ?: emptyList()
        searching = false
    }
    LaunchedEffect(picked) {
        preview = null
        val p = picked ?: return@LaunchedEffect
        previewing = true
        preview = actions.preview(p)
        previewing = false
    }
    fun go(p: Place) {
        actions.go(p, preview.takeIf { picked == p })
        close()
    }

    // CarDash's keyboard along the bottom while typing; the places above make room for it
    Column(Modifier.fillMaxSize()) {
    Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = close), contentAlignment = Alignment.Center) {
                    Icon(Ic.back, "Back", tint = C.ink, modifier = Modifier.size(28.dp))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(80); picked = null },
                    singleLine = true,
                    placeholder = { Text("Where to? A place, an address or an Eircode", color = C.muted, fontSize = 18.sp) },
                    leadingIcon = { Icon(Ic.search, null, tint = C.ink2, modifier = Modifier.size(22.dp)) },
                    textStyle = TextStyle(color = C.ink, fontSize = 20.sp),
                    shape = RoundedCornerShape(30.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.red, unfocusedBorderColor = C.lineHi, cursorColor = C.red),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        results?.firstOrNull()?.let { picked = it }
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.weight(1f).height(62.dp).focusRequester(focus),
                )
            }
            val q = query.trim()
            val found = results
            when {
                found != null && found.isEmpty() && !searching && q.length >= 3 -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val eircode = Places.isEircode(q)
                    Text(
                        when {
                            googleSaid != null -> "Google didn't look it up: $googleSaid"
                            offline -> "No internet for CarDash's map search."
                            eircode && !hasKey -> "Eircodes are looked up in Google Maps."
                            else -> "CarDash's map search doesn't know this one."
                        },
                        color = C.ink2, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, top = 6.dp),
                    )
                    // Find it in Google Maps, then Share → CarDash: CarDash gets the place and drives there
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.redTint).border(1.dp, C.red, RoundedCornerShape(18.dp))
                            .clickable { Places.findInGoogleMaps(context, q) }.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(Ic.map, null, tint = C.red, modifier = Modifier.size(34.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Find $q in Google Maps", color = C.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("then tap Share › CarDash, and CarDash drives there", color = C.ink2, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Ic.external, null, tint = C.ink2, modifier = Modifier.size(24.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.panel).border(1.dp, C.lineHi, RoundedCornerShape(18.dp))
                            .clickable { Places.directionsInGoogleMaps(context, q) }.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Or let Google Maps drive", color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("the middle box still shows Google's next turn", color = C.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Ic.external, null, tint = C.ink2, modifier = Modifier.size(22.dp))
                    }
                    if (eircode && !hasKey) Text(
                        "Add your own Google key in Settings › Navigation and Eircodes are found right here.",
                        color = C.muted, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                found != null && found.isNotEmpty() -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(found) { p ->
                        PlaceRow(p, here, selected = p == picked, onClick = { picked = p; focusManager.clearFocus() }, onHold = { saving = p })
                    }
                }
                searching -> Text("Searching…", color = C.muted, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(6.dp))
                else -> Spacer(Modifier.weight(1f))
            }
            picked?.let { p ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.panel).border(1.dp, C.redDeep, RoundedCornerShape(20.dp))
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = C.ink2, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val r = preview
                        when {
                            r != null -> Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(minutes(r.duration), color = C.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                                Text(Geo.distanceText(r.distance), color = C.ink2, fontSize = 21.sp, modifier = Modifier.padding(bottom = 3.dp))
                                Text("arrive ${arriveAt(r.duration)}", color = C.ink2, fontSize = 21.sp, modifier = Modifier.padding(bottom = 3.dp))
                            }
                            previewing -> Text("Planning…", color = C.muted, fontSize = 20.sp)
                            here == null -> Text("Waiting for the GPS: Go plans it once there's a position", color = C.muted, fontSize = 17.sp)
                            else -> Text("Couldn't plan a route now: Go tries again", color = C.muted, fontSize = 17.sp)
                        }
                    }
                    Box(
                        Modifier.width(160.dp).height(64.dp).clip(RoundedCornerShape(32.dp)).background(C.red).clickable { go(p) },
                        contentAlignment = Alignment.Center,
                    ) { Text("Go", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Column(Modifier.width(400.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card("Saved") {
                SavedRow(Ic.home, "Home", state.home, onPick = { picked = it; query = "" })
                SavedRow(Ic.work, "Work", state.work, onPick = { picked = it; query = "" })
            }
            Card("Recent") {
                if (state.recent.isEmpty()) Text("Places you drive to show here.", color = C.muted, fontSize = 15.sp)
                state.recent.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { picked = p; query = "" }, onLongClick = { saving = p }).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(Ic.clock, null, tint = C.ink2, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = C.ink, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.detail.isNotEmpty()) Text(p.detail, color = C.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text("Hold a place to save it as Home or Work.", color = C.muted, fontSize = 14.sp)
            }
        }
    }
    CarKeyboardHere(Modifier.fillMaxWidth())
    }

    saving?.let { p ->
        AlertDialog(
            onDismissRequest = { saving = null },
            containerColor = C.panelHi,
            title = { Text(p.name, color = C.ink) },
            text = { Text("Save it as Home or Work?", color = C.ink2, fontSize = 16.sp) },
            confirmButton = {
                Row {
                    TextButton(onClick = { actions.setHome(p); saving = null }) { Text("Home", color = C.red, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { actions.setWork(p); saving = null }) { Text("Work", color = C.red, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                Row {
                    if (p in state.recent) TextButton(onClick = { actions.forgetRecent(p); saving = null }) { Text("Remove from Recent", color = C.ink2) }
                    TextButton(onClick = { saving = null }) { Text("Cancel", color = C.ink2) }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaceRow(p: Place, here: io.github.bornaradusin.cardash.nav.Pos?, selected: Boolean, onClick: () -> Unit, onHold: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) C.redTint else C.panel)
            .border(1.dp, if (selected) C.red else C.line, shape)
            .combinedClickable(onClick = onClick, onLongClick = onHold).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Ic.pin, null, tint = if (selected) C.red else C.ink2, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.detail.isNotEmpty()) Text(p.detail, color = C.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        here?.let { Text(Geo.distanceText(Geo.distance(it, p.pos)), color = C.ink2, fontSize = 17.sp) }
    }
}

@Composable
private fun SavedRow(icon: ImageVector, name: String, place: Place?, onPick: (Place) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = place != null) { place?.let(onPick) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = C.ink2, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = C.ink, fontSize = 19.sp)
            place?.let { Text(it.name, color = C.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (place == null) Text("Set: hold a place", color = C.muted, fontSize = 14.sp)
    }
}

// ---------- Settings cards ----------

private val QUICK = listOf(
    "Red" to 0xFFFF2D40, "Orange" to 0xFFFF8A1F, "Yellow" to 0xFFFFD23F,
    "Green" to 0xFF2ED47A, "Blue" to 0xFF2E8BFF, "Purple" to 0xFFA45CFF,
)

/** The accent colour: typed as red, green and blue, or picked. */
@Composable
internal fun AccentCard(state: DashState, actions: DashActions) {
    val a = state.accent
    var r by remember(a) { mutableStateOf(((a shr 16) and 0xFF).toString()) }
    var g by remember(a) { mutableStateOf(((a shr 8) and 0xFF).toString()) }
    var b by remember(a) { mutableStateOf((a and 0xFF).toString()) }
    fun apply(rs: String, gs: String, bs: String) {
        val rv = rs.toIntOrNull()?.takeIf { it in 0..255 } ?: return
        val gv = gs.toIntOrNull()?.takeIf { it in 0..255 } ?: return
        val bv = bs.toIntOrNull()?.takeIf { it in 0..255 } ?: return
        actions.setAccent((0xFF shl 24) or (rv shl 16) or (gv shl 8) or bv)
    }
    Card("Accent colour") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(C.red).border(2.dp, C.lineHi, CircleShape))
            Column(Modifier.weight(1f)) {
                Text("The colour of the needles, gauges, buttons and highlights", color = C.ink, fontSize = 17.sp)
                Text("Changes everywhere as you type", color = C.muted, fontSize = 14.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("Red", r, 0), Triple("Green", g, 1), Triple("Blue", b, 2)).forEach { (label, value, i) ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { v ->
                        val clean = v.filter { it.isDigit() }.take(3)
                        when (i) {
                            0 -> { r = clean; apply(clean, g, b) }
                            1 -> { g = clean; apply(r, clean, b) }
                            else -> { b = clean; apply(r, g, clean) }
                        }
                    },
                    label = { Text("$label (0–255)", color = C.muted, fontSize = 13.sp) },
                    singleLine = true,
                    isError = value.toIntOrNull()?.let { it !in 0..255 } ?: true,
                    textStyle = TextStyle(color = C.ink, fontSize = 20.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.red, unfocusedBorderColor = C.lineHi, cursorColor = C.red),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Quick picks", color = C.muted, fontSize = 14.sp)
            QUICK.forEach { (name, argb) ->
                val c = argb.toInt()
                val on = c == a
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color(c))
                        .border(if (on) 3.dp else 1.dp, if (on) C.ink else C.lineHi, CircleShape)
                        .clickable(onClickLabel = name) { actions.setAccent(c) },
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(C.panelHi).border(1.dp, C.lineHi, RoundedCornerShape(22.dp))
                    .clickable { actions.setAccent(C.RED.toArgb()) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Back to red", color = C.ink2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.ink, fontSize = 17.sp)
            Text(sub, color = C.muted, fontSize = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = C.ink))
    }
}

@Composable
internal fun NavigationCard(state: DashState, actions: DashActions) {
    Card("Navigation") {
        SwitchRow("Spoken directions", "\"In 300 m, turn right\", in the radio's voice", state.spoken, actions.setSpoken)
        SwitchRow("Big map on a route", "While a route is set, the map takes the RPM dial's place", state.bigMap, actions.setBigMap)
        var editingKey by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Google key for Eircodes", color = C.ink, fontSize = 17.sp)
                Text(
                    if (state.googleKey.isNotBlank()) "Set: Eircodes are found right in CarDash"
                    else "Not set: Eircodes open in Google Maps, Share › CarDash brings them back",
                    color = C.muted, fontSize = 14.sp,
                )
            }
            SmallButton(if (state.googleKey.isNotBlank()) "Change" else "Add") { editingKey = true }
        }
        if (editingKey) GoogleKeyDialog(state.googleKey, onSave = { actions.setGoogleKey(it); editingKey = false }, onDismiss = { editingKey = false })
        Text("Google Maps' turns show in the middle box while it navigates (needs notification access).", color = C.muted, fontSize = 14.sp)
    }
}

/**
 * Pasting in the driver's own Google key. It's tried with one lookup before it's kept, so a key
 * Google won't take (the Geocoding API not turned on, no billing set up) says why straight away.
 */
@Composable
private fun GoogleKeyDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val box = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { box.requestFocus() } }
    fun save() {
        val key = text.trim()
        if (key.isEmpty() || problem != null) {
            onSave(key)
            return
        }
        checking = true
        scope.launch {
            problem = runCatching { Places.google("Limerick", key) }.exceptionOrNull()?.let { it.message ?: "no answer" }
            checking = false
            if (problem == null) onSave(key)
        }
    }
    KeyboardDialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(900.dp).clip(RoundedCornerShape(28.dp)).background(C.panelHi)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Google key for Eircodes", color = C.ink, fontSize = 24.sp)
                Text(
                    "Your own key from Google Cloud, with the Geocoding API turned on. Free up to 10,000 lookups a month. How to get one: the wiki's Navigation page. Paste puts in a key you've copied.",
                    color = C.ink2, fontSize = 15.sp, lineHeight = 21.sp,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim().take(100); problem = null },
                    singleLine = true,
                    placeholder = { Text("AIza…", color = C.muted) },
                    textStyle = TextStyle(color = C.ink, fontSize = 18.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.red, unfocusedBorderColor = C.lineHi, cursorColor = C.red),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(box),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        checking -> Text("Trying it with Google…", color = C.muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        problem != null -> Text("Google said: $problem", color = C.red, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 2, modifier = Modifier.weight(1f))
                        else -> Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel", color = C.ink2) }
                    TextButton(enabled = !checking, onClick = { save() }) {
                        Text(
                            if (problem != null) "Save anyway" else if (text.isBlank() && current.isNotBlank()) "Remove" else "Save",
                            color = C.red, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** Map areas saved for no signal, and saving more. */
@Composable
internal fun OfflineMapCard(state: DashState, actions: DashActions) {
    Card("Offline map") {
        Text(
            "Saved areas work with no signal. Searching and planning a new route still need the internet; a route you've started keeps going.",
            color = C.muted, fontSize = 15.sp, lineHeight = 21.sp,
        )
        state.mapAreas.forEach { a -> AreaRow(a, actions) }
        Text("Save another area", color = C.muted, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { Wide("Around here") { actions.saveMapAround() } }
            Box(Modifier.weight(1f)) { Wide("All of Ireland") { actions.saveMapIreland() } }
            Box(Modifier.weight(1f)) { Wide("Along a route") { actions.saveMapRoute() } }
        }
        if (state.nav.here == null) Text("Around here needs a GPS position first.", color = C.muted, fontSize = 13.sp)
        if (state.nav.route == null) Text("Along a route saves the route you're on.", color = C.muted, fontSize = 13.sp)
        SwitchRow("Download on Wi-Fi only", "Waits for Wi-Fi (a phone's hotspot counts) instead of the radio's mobile data", state.wifiOnly, actions.setWifiOnly)
    }
}

@Composable
private fun AreaRow(a: MapArea, actions: DashActions) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(C.panelHi).border(1.dp, C.line, shape).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (a.complete) Ic.check else Ic.download, null, tint = if (a.complete) C.good else C.red, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(a.name, color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!a.complete) Text("${a.percent} %", color = C.ink2, fontSize = 14.sp)
            }
            if (a.complete) {
                val day = if (a.savedAt > 0) java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.UK).format(java.util.Date(a.savedAt)) else ""
                Text("Saved $day · ${a.bytes / 1_048_576} MB", color = C.muted, fontSize = 13.sp)
            } else {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.track)) {
                    Box(Modifier.fillMaxWidth(a.percent / 100f).fillMaxHeight().background(C.red))
                }
                Text(
                    when {
                        a.error != null -> "Problem: ${a.error}"
                        a.waitingForWifi -> "Waiting for Wi-Fi"
                        a.active -> "Downloading · ${a.bytes / 1_048_576} MB so far"
                        else -> "Paused"
                    },
                    color = C.muted, fontSize = 13.sp,
                )
            }
        }
        if (a.complete) {
            SmallButton("Update") { actions.updateMap(a.id) }
            SmallButton("Remove", quiet = true) { actions.removeMap(a.id) }
        } else {
            if (a.active || a.waitingForWifi) SmallButton("Pause", quiet = true) { actions.pauseMap(a.id) }
            else SmallButton("Resume") { actions.resumeMap(a.id) }
            SmallButton("Remove", quiet = true) { actions.removeMap(a.id) }
        }
    }
}

@Composable
private fun SmallButton(label: String, quiet: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .then(if (quiet) Modifier else Modifier.background(C.panel).border(1.dp, C.lineHi, RoundedCornerShape(22.dp)))
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (quiet) C.muted else C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}
