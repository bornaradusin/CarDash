package io.github.bornaradusin.cardash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bornaradusin.cardash.car.ServiceBook
import io.github.bornaradusin.cardash.car.ServiceItem
import io.github.bornaradusin.cardash.car.ServiceKind
import io.github.bornaradusin.cardash.car.ServiceState
import io.github.bornaradusin.cardash.car.ServiceStatus
import io.github.bornaradusin.cardash.car.Urgency
import io.github.bornaradusin.cardash.car.statusOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- the Service page ----------

/** The car's odometer, the reminder settings and every item, most urgent first. */
@Composable
internal fun ServicePage(s: ServiceState, actions: DashActions) {
    var editing by remember { mutableStateOf<ServiceItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val rows = s.sorted(today)

    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OdometerCard(s, actions)
            RemindCard(s, actions)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(C.panelHi)
                    .border(1.dp, C.lineHi, RoundedCornerShape(26.dp)).clickable { adding = true },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Ic.plus, null, tint = C.ink, modifier = Modifier.size(20.dp))
                    Text("Add an item", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SERVICE", color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                Text(
                    "Tap the pencil to change how often · Done logs it" + (s.carKm?.let { " at ${km(it.roundToInt())} km" } ?: "") + ", today",
                    color = C.muted, fontSize = 14.sp, maxLines = 1,
                )
            }
            if (rows.isEmpty()) {
                Text("Nothing to service yet: add an item.", color = C.muted, fontSize = 16.sp)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { it.item.id }) { st ->
                    ServiceRow(st, s.carKm != null, onEdit = { editing = st.item }, onDone = { actions.serviceDone(st.item.id) })
                }
            }
        }
    }

    editing?.let { item ->
        ServiceEditDialog(
            item = item, isNew = false, s = s,
            onSave = { actions.saveService(it); editing = null },
            onRemove = { actions.removeService(item.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (adding) {
        val blank = remember { ServiceItem(ServiceBook.newId(), ServiceKind.CUSTOM, "", null, null, null, null) }
        ServiceEditDialog(
            item = blank, isNew = true, s = s,
            onSave = { actions.saveService(it); adding = false },
            onRemove = { adding = false },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun OdometerCard(s: ServiceState, actions: DashActions) {
    Card("Car's odometer") {
        val known = s.carKm
        if (known != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(km(known.roundToInt()), color = C.ink, fontSize = 52.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                Text("km", color = C.muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (s.fromCan) C.good else C.lineHi))
            Text(
                when {
                    s.fromCan -> "From the car, live (CAN box)"
                    known != null -> "Counted on from OBD speed"
                    else -> "The CAN box isn't giving it"
                },
                color = C.ink2, fontSize = 15.sp,
            )
        }
        if (s.fromCan) {
            Text("Everything here counts down from the car's own kilometres, the same as the dashboard.", color = C.muted, fontSize = 14.sp)
        } else {
            var typed by remember { mutableStateOf("") }
            Text(if (known == null) "Type what the dashboard shows" else "Doesn't match the dashboard? Type what it shows", color = C.muted, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { v -> typed = v.filter { it.isDigit() }.take(7) },
                    singleLine = true,
                    placeholder = { Text(known?.let { km(it.roundToInt()) } ?: "184216", color = C.muted) },
                    textStyle = TextStyle(color = C.ink, fontSize = 20.sp),
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = Thousands,
                    modifier = Modifier.width(150.dp),
                )
                Text("km", color = C.muted, fontSize = 16.sp)
                Box(Modifier.weight(1f)) {
                    Wide("Save") {
                        typed.toLongOrNull()?.let { actions.setCarKm(it.toDouble()) }
                        typed = ""
                    }
                }
            }
            Text(
                "CarDash then adds the km it counts from the OBD speed. Pick the odometer in Settings › Radio's CAN box to follow the car exactly.",
                color = C.muted, fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun RemindCard(s: ServiceState, actions: DashActions) {
    Card("Remind me") {
        Stepper("Before it's due", "${km(s.kmBefore)} km",
            less = { actions.setRemindKm(s.kmBefore - ServiceBook.KM_BEFORE_STEP) },
            more = { actions.setRemindKm(s.kmBefore + ServiceBook.KM_BEFORE_STEP) })
        Stepper("or", "${s.daysBefore} days",
            less = { actions.setRemindDays(s.daysBefore - ServiceBook.DAYS_BEFORE_STEP) },
            more = { actions.setRemindDays(s.daysBefore + ServiceBook.DAYS_BEFORE_STEP) })
        Text("A reminder pops up on Home once an item gets this close.", color = C.muted, fontSize = 14.sp)
    }
}

@Composable
internal fun Stepper(name: String, value: String, less: () -> Unit, more: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, color = C.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        RoundIcon(Ic.minus, "Less", less)
        Text(value, color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.width(92.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        RoundIcon(Ic.plus, "More", more)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(C.panelHi).border(1.dp, C.lineHi, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = C.ink, modifier = Modifier.size(18.dp)) }
}

/** One item: what it is, how often (tap to change), how far off it is, and Done. */
@Composable
private fun ServiceRow(st: ServiceStatus, odometerKnown: Boolean, onEdit: () -> Unit, onDone: () -> Unit) {
    val item = st.item
    val overdue = st.urgency == Urgency.OVERDUE
    val due = st.due
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(shape)
            .background(if (overdue) C.redTint else C.panel)
            .border(1.dp, if (overdue) C.red else if (due) C.redDeep else C.line, shape)
            .clickable(onClick = onEdit)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(C.panelHi), contentAlignment = Alignment.Center) {
            Icon(iconOf(item.kind), null, tint = if (due) C.red else C.ink2, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.width(262.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.name, color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (overdue) Pill("OVERDUE", solid = true)
                else if (due) Pill("DUE SOON", solid = false)
            }
            Row(
                Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(C.panelHi)
                    .border(1.dp, C.lineHi, RoundedCornerShape(22.dp)).clickable(onClick = onEdit).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Ic.pencil, "Change how often", tint = C.ink2, modifier = Modifier.size(16.dp))
                Text(intervalText(item.everyKm, item.everyMonths), color = C.ink2, fontSize = 14.sp, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                leftText(st, odometerKnown),
                color = if (due) C.red else if (st.urgency == Urgency.UNSET) C.muted else C.ink,
                fontSize = 22.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1,
            )
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.track)) {
                if (st.urgency != Urgency.UNSET) {
                    Box(Modifier.fillMaxWidth(st.used.coerceIn(0f, 1f)).fillMaxHeight().background(if (due) C.red else C.good))
                }
            }
            Text(nextText(st), color = C.muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.width(100.dp).height(56.dp).clip(RoundedCornerShape(28.dp))
                .background(if (due) C.redTint else C.panelHi)
                .border(1.dp, if (due) C.red else C.lineHi, RoundedCornerShape(28.dp))
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) { Text("Done", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Pill(text: String, solid: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).background(if (solid) C.red else C.redTint)
            .border(1.dp, if (solid) C.red else C.redDeep, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) { Text(text, color = if (solid) C.bg else C.red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
}

// ---------- changing an item ----------

/** Name, how often (km and/or months) and when it was last done; Done logs it now. */
@Composable
private fun ServiceEditDialog(
    item: ServiceItem,
    isNew: Boolean,
    s: ServiceState,
    onSave: (ServiceItem) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var everyKm by remember { mutableStateOf(item.everyKm?.toString() ?: "") }
    var everyMonths by remember { mutableStateOf(item.everyMonths?.toString() ?: "") }
    var lastKm by remember { mutableStateOf(item.lastKm?.toString() ?: "") }
    var lastDay by remember { mutableStateOf(item.lastDay) }
    var picking by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    fun form() = item.copy(
        name = name.trim().take(30),
        everyKm = everyKm.toIntOrNull()?.takeIf { it > 0 },
        everyMonths = everyMonths.toIntOrNull()?.takeIf { it > 0 },
        lastKm = lastKm.toIntOrNull(),
        lastDay = lastDay,
    )
    val today = LocalDate.now()
    val preview = statusOf(form(), s.carKm, today, s.kmBefore, s.daysBefore)
    val canSave = name.isNotBlank()

    // The form, and CarDash's keyboard across the bottom of the screen while a box is being typed in
    KeyboardDialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(660.dp).heightIn(max = 700.dp).clip(RoundedCornerShape(28.dp)).background(C.panelHi)) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (isNew) "New item" else item.name, color = C.ink, fontSize = 24.sp)
            Field("Name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    singleLine = true,
                    placeholder = { Text("Thermostat, gearbox oil…", color = C.muted) },
                    textStyle = TextStyle(color = C.ink, fontSize = 18.sp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NumberField("Every", everyKm, "km", "15000", Modifier.weight(1f)) { everyKm = it }
                NumberField("or every", everyMonths, "months", "12", Modifier.weight(1f)) { everyMonths = it.take(3) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                NumberField("Last done at", lastKm, "km", s.carKm?.roundToInt()?.toString() ?: "", Modifier.weight(1f)) { lastKm = it }
                Field("on", Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, C.lineHi, RoundedCornerShape(4.dp))
                                .clickable { picking = true }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                lastDay?.let { LocalDate.ofEpochDay(it).format(DAY) } ?: "Not set",
                                color = if (lastDay == null) C.muted else C.ink, fontSize = 18.sp, modifier = Modifier.weight(1f),
                            )
                            Icon(Ic.calendar, "Choose the day", tint = C.ink2, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(56.dp))
                    }
                }
            }
            Text("Leave km or months empty to go by the other one only.", color = C.muted, fontSize = 14.sp)

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val k = preview.nextKm
                    val d = preview.nextDate
                    Text(
                        when {
                            k != null && d != null -> "Next: at ${km(k)} km or on ${d.format(DAY)}"
                            k != null -> "Next: at ${km(k)} km"
                            d != null -> "Next: on ${d.format(DAY)}"
                            else -> "Fill in how often and when it was last done"
                        },
                        color = if (k == null && d == null) C.muted else C.ink, fontSize = 16.sp,
                    )
                    if (k != null && d != null) Text("whichever comes first", color = C.muted, fontSize = 14.sp)
                }
                if (preview.urgency != Urgency.UNSET) {
                    Text(leftText(preview, s.carKm != null), color = if (preview.due) C.red else C.ink, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, maxLines = 1)
                }
            }

            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(C.redTint)
                    .border(1.dp, if (canSave) C.red else C.redDeep, RoundedCornerShape(28.dp))
                    .clickable(enabled = canSave) { onSave(form().copy(lastKm = s.carKm?.roundToInt(), lastDay = today.toEpochDay())) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Ic.check, null, tint = if (canSave) C.ink else C.muted, modifier = Modifier.size(22.dp))
                    Text(
                        "Done today" + (s.carKm?.let { ", at ${km(it.roundToInt())} km" } ?: ""),
                        color = if (canSave) C.ink else C.muted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = { if (confirmRemove) onRemove() else confirmRemove = true }) {
                        Text(if (confirmRemove) "Remove? Tap again" else "Remove item", color = if (confirmRemove) C.red else C.muted,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel", color = C.ink2, fontSize = 15.sp) }
                TextButton(onClick = { onSave(form()) }, enabled = canSave) {
                    Text("Save", color = if (canSave) C.red else C.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }
    if (picking) {
        PickDate(lastDay?.let(LocalDate::ofEpochDay), onPick = { lastDay = it.toEpochDay() }, onDismiss = { picking = false })
    }
}

@Composable
private fun Field(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = C.muted, fontSize = 13.sp)
        content()
    }
}

@Composable
private fun NumberField(label: String, value: String, unit: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
    Field(label, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(7)) },
                singleLine = true,
                placeholder = { if (hint.isNotEmpty()) Text(hint, color = C.muted) },
                textStyle = TextStyle(color = C.ink, fontSize = 18.sp),
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = Thousands,
                modifier = Modifier.weight(1f),
            )
            Text(unit, color = C.muted, fontSize = 16.sp, modifier = Modifier.width(56.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDate(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val lastMs = (today.toEpochDay() + 1) * DAY_MS - 1
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: today).toEpochDay() * DAY_MS,
        selectableDates = object : SelectableDates {
            // It was done in the past (or today), never in the future
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= lastMs
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.red, onPrimary = C.ink, surface = C.panelHi, onSurface = C.ink,
            surfaceContainerHigh = C.panelHi, onSurfaceVariant = C.muted, outline = C.lineHi, secondaryContainer = C.redTint,
        ),
    ) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / DAY_MS)) }
                    onDismiss()
                }) { Text("OK", color = C.red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.ink2) } },
        ) { DatePicker(state) }
    }
}

// ---------- the reminder on Home ----------

/** Pops up while something is due, until Done or "Remind me later" (which waits for the next drive). */
@Composable
internal fun ServiceReminder(s: ServiceState, actions: DashActions, openService: () -> Unit) {
    val due = s.sorted().filter { it.due }
    if (due.isEmpty() || s.laterDrive == s.drive) return
    Dialog(onDismissRequest = actions.serviceLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(620.dp).heightIn(max = 690.dp).clip(RoundedCornerShape(28.dp)).background(C.panelHi)
                .verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(C.redTint).border(1.dp, C.redDeep, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Ic.wrench, null, tint = C.red, modifier = Modifier.size(28.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Service needed", color = C.ink, fontSize = 26.sp)
                    s.carKm?.let { Text("Car's odometer: ${km(it.roundToInt())} km", color = C.muted, fontSize = 15.sp) }
                }
            }
            due.forEach { st ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(16.dp))
                        .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(st.item.name, color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Row {
                            Text(leftText(st, s.carKm != null), color = C.red, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(" · " + reminderText(st), color = C.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box(
                        Modifier.width(96.dp).height(52.dp).clip(RoundedCornerShape(26.dp)).background(C.panelHi)
                            .border(1.dp, C.lineHi, RoundedCornerShape(26.dp)).clickable { actions.serviceDone(st.item.id) },
                        contentAlignment = Alignment.Center,
                    ) { Text("Done", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            Text(
                "Done logs it" + (s.carKm?.let { " at ${km(it.roundToInt())} km" } ?: "") + ", today. Later asks again on the next drive.",
                color = C.muted, fontSize = 14.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = actions.serviceLater) { Text("Remind me later", color = C.ink2, fontSize = 15.sp) }
                TextButton(onClick = { actions.serviceLater(); openService() }) {
                    Text("Open Service", color = C.red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** The small "2 due" button in the header, while anything is due. */
@Composable
internal fun ServiceChip(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(C.redTint).border(1.dp, C.redDeep, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Ic.wrench, "Service", tint = C.red, modifier = Modifier.size(16.dp))
        Text("$count due", color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- wording ----------

private const val DAY_MS = 86_400_000L
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

private fun km(n: Int): String = "%,d".format(Locale.US, n)

private fun iconOf(kind: ServiceKind): ImageVector = when (kind) {
    ServiceKind.OIL -> Ic.oil
    ServiceKind.AIR_FILTER -> Ic.air
    ServiceKind.CABIN_FILTER -> Ic.filter
    ServiceKind.SPARK_PLUGS -> Ic.plug
    ServiceKind.FUEL_FILTER -> Ic.fuel
    ServiceKind.BRAKE_FLUID -> Ic.drop
    ServiceKind.CUSTOM -> Ic.wrench
}

/** "Every 15,000 km or 12 months", "Every 2 years"… */
private fun intervalText(everyKm: Int?, everyMonths: Int?): String {
    val time = everyMonths?.let { m ->
        when {
            m >= 24 && m % 12 == 0 -> "${m / 12} years"
            m == 1 -> "1 month"
            else -> "$m months"
        }
    }
    return when {
        everyKm != null && time != null -> "Every ${km(everyKm)} km or $time"
        everyKm != null -> "Every ${km(everyKm)} km"
        time != null -> "Every $time"
        else -> "Set how often"
    }
}

/** "784 km left", "36 days overdue", "5 months left"… */
private fun leftText(st: ServiceStatus, odometerKnown: Boolean): String {
    if (st.urgency == Urgency.UNSET) {
        val item = st.item
        return if ((item.lastKm != null || item.lastDay != null) && item.everyKm != null && !odometerKnown && item.everyMonths == null) "Odometer not known"
        else "Not set yet"
    }
    val kmLeft = st.kmLeft
    val nextDate = st.nextDate
    val daysLeft = st.daysLeft
    return if (st.byKm && kmLeft != null) {
        if (kmLeft >= 0) "${km(kmLeft)} km left" else "${km(-kmLeft)} km overdue"
    } else if (daysLeft != null && nextDate != null) {
        val today = LocalDate.now()
        val span = timeSpan(if (daysLeft >= 0) today else nextDate, if (daysLeft >= 0) nextDate else today)
        if (daysLeft >= 0) "$span left" else "$span overdue"
    } else "Not set yet"
}

/** Days under two months, months beyond. */
private fun timeSpan(from: LocalDate, to: LocalDate): String {
    val days = ChronoUnit.DAYS.between(from, to)
    if (days < 62) return if (days == 1L) "1 day" else "$days days"
    val months = ChronoUnit.MONTHS.between(from, to)
    return if (months == 1L) "1 month" else "$months months"
}

private fun dateText(d: LocalDate, daysLeft: Long): String = d.format(if (abs(daysLeft) < 62) DAY else MONTH)

/** "Next at 185,000 km · or by Apr 2027", "Was due 2 Oct 2026"… */
private fun nextText(st: ServiceStatus): String {
    if (st.urgency == Urgency.UNSET) return "Tap to set when it was last done"
    val k = st.nextKm
    val d = st.nextDate
    val days = st.daysLeft
    val kmLeft = st.kmLeft
    if (st.urgency == Urgency.OVERDUE) {
        if (st.byKm && k != null && kmLeft != null && kmLeft < 0) return "Was due at ${km(k)} km"
        if (d != null && days != null && days < 0) return "Was due ${d.format(DAY)}"
        if (k != null) return "Was due at ${km(k)} km"
    }
    val datePart = if (d != null && days != null) "by ${dateText(d, days)}" else null
    return when {
        k != null && datePart != null -> "Next at ${km(k)} km · or $datePart"
        k != null -> "Next at ${km(k)} km"
        datePart != null -> "Next $datePart"
        else -> ""
    }
}

/** For the reminder: where to change it. */
private fun reminderText(st: ServiceStatus): String {
    val k = st.nextKm
    val d = st.nextDate
    val days = st.daysLeft
    return when {
        st.urgency == Urgency.OVERDUE && d != null && days != null && days < 0 && !(st.byKm && (st.kmLeft ?: 0) < 0) -> "was due ${d.format(DAY)}"
        st.byKm && k != null -> if ((st.kmLeft ?: 0) < 0) "was due at ${km(k)} km" else "change at ${km(k)} km"
        d != null && days != null -> "by ${d.format(DAY)}"
        k != null -> "change at ${km(k)} km"
        else -> ""
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.red, unfocusedBorderColor = C.lineHi, cursorColor = C.red,
    focusedTextColor = C.ink, unfocusedTextColor = C.ink,
)

/** Shows typed digits with thousands commas (184,216) while keeping them plain digits. */
private object Thousands : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val digits = text.text
        val out = StringBuilder()
        val map = IntArray(digits.length + 1)
        for (i in digits.indices) {
            if (i > 0 && (digits.length - i) % 3 == 0) out.append(',')
            map[i] = out.length
            out.append(digits[i])
        }
        map[digits.length] = out.length
        return androidx.compose.ui.text.input.TransformedText(
            androidx.compose.ui.text.AnnotatedString(out.toString()),
            object : androidx.compose.ui.text.input.OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = map[offset.coerceIn(0, digits.length)]
                override fun transformedToOriginal(offset: Int): Int {
                    var i = 0
                    while (i < digits.length && map[i] < offset) i++
                    return i
                }
            },
        )
    }
}

