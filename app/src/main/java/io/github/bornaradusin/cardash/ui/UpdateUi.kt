package io.github.bornaradusin.cardash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.bornaradusin.cardash.update.UpdateState
import io.github.bornaradusin.cardash.update.Updater
import kotlin.math.roundToInt

/** Settings → Updates: the switch, and Check now (or Update, once one's found). */
@Composable
internal fun UpdatesCard(updater: Updater) {
    val state by updater.state.collectAsState()
    val auto by updater.auto.collectAsState()
    Card("Updates") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Check for updates", color = C.ink, fontSize = 17.sp)
                Text("Looks on GitHub about once a day and offers a newer CarDash, installed from here", color = C.muted, fontSize = 14.sp)
            }
            Switch(
                checked = auto,
                onCheckedChange = { updater.setAuto(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = C.ink),
            )
        }
        val ready = (state as? UpdateState.Available)?.update
        Text(
            when {
                ready != null -> "CarDash ${ready.version} is ready (you have ${updater.installedVersion})"
                state is UpdateState.UpToDate -> "You have the newest version, ${updater.installedVersion}"
                state is UpdateState.Checking -> "Looking on GitHub…"
                else -> "You have ${updater.installedVersion}"
            },
            color = C.ink2,
            fontSize = 15.sp,
        )
        if (ready != null) Wide("Update to ${ready.version}") { updater.open() } else Wide("Check now") { updater.check(manual = true) }
        Text("The download is deleted once the update is in.", color = C.muted, fontSize = 14.sp)
    }
}

/**
 * A newer CarDash: what's new, then download, check and install it. One found by itself only comes
 * up while the car is standing still, never while driving.
 */
@Composable
internal fun UpdatePrompt(updater: Updater, moving: Boolean) {
    val state by updater.state.collectAsState()
    val prompt by updater.prompt.collectAsState()

    // Back from the "install unknown apps" switch: carry on with the install
    LifecycleResumeEffect(state) {
        if (state is UpdateState.NeedsPermission) updater.resume()
        onPauseOrDispose { }
    }

    if (!prompt || (moving && state is UpdateState.Available)) return

    val title = when (val s = state) {
        is UpdateState.Available -> "CarDash ${s.update.version} is ready"
        is UpdateState.Downloading -> "CarDash ${s.update.version} is ready"
        is UpdateState.NeedsPermission -> "Allow CarDash to update itself"
        is UpdateState.Failed -> s.update?.let { "CarDash ${it.version} is ready" } ?: "Updates"
        UpdateState.Checking -> "Looking for updates…"
        UpdateState.UpToDate -> "CarDash is up to date"
        UpdateState.Idle -> "Updates"
    }

    Dialog(onDismissRequest = { updater.close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(620.dp)
                .background(C.panel, RoundedCornerShape(24.dp))
                .border(1.dp, C.lineHi, RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            when (val s = state) {
                is UpdateState.Available -> {
                    Text("You have ${updater.installedVersion}.", color = C.muted, fontSize = 14.sp)
                    if (s.update.notes.isNotBlank()) {
                        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                            Text(s.update.notes, color = C.ink2, fontSize = 16.sp, lineHeight = 22.sp)
                        }
                    }
                }
                is UpdateState.Downloading -> {
                    Text("Downloading… ${(s.progress * 100).roundToInt()}%", color = C.ink, fontSize = 17.sp)
                    LinearProgressIndicator(progress = { s.progress }, color = C.red, trackColor = C.line, modifier = Modifier.fillMaxWidth().height(6.dp))
                    Text("Android's installer opens when it's done. The download is deleted once the update is in.", color = C.muted, fontSize = 14.sp)
                }
                is UpdateState.NeedsPermission -> Text(
                    "The update is downloaded. Android asks you to allow CarDash to install apps once: turn on \"Allow from this source\", then come back here.",
                    color = C.ink2,
                    fontSize = 16.sp,
                )
                is UpdateState.Failed -> Text(s.message, color = C.red, fontSize = 16.sp)
                UpdateState.Checking -> LinearProgressIndicator(color = C.red, trackColor = C.line, modifier = Modifier.fillMaxWidth().height(6.dp))
                UpdateState.UpToDate -> Text("You have the newest version, ${updater.installedVersion}.", color = C.ink2, fontSize = 16.sp)
                UpdateState.Idle -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                when (val s = state) {
                    is UpdateState.Available -> {
                        PromptButton("Not now", Modifier.weight(1f)) { updater.later() }
                        PromptButton("Update", Modifier.weight(1f), accent = true) { updater.update() }
                    }
                    is UpdateState.Downloading -> PromptButton("Cancel", Modifier.weight(1f)) { updater.cancel() }
                    is UpdateState.NeedsPermission -> {
                        PromptButton("Not now", Modifier.weight(1f)) { updater.later() }
                        PromptButton("Allow", Modifier.weight(1f), accent = true) { updater.allowInstalls() }
                    }
                    is UpdateState.Failed -> {
                        PromptButton("Close", Modifier.weight(1f)) { updater.close() }
                        PromptButton("Try again", Modifier.weight(1f), accent = true) { if (s.update != null) updater.update() else updater.check(manual = true) }
                    }
                    else -> PromptButton("OK", Modifier.weight(1f), accent = true) { updater.close() }
                }
            }
        }
    }
}

@Composable
private fun PromptButton(label: String, modifier: Modifier, accent: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.height(52.dp).clip(shape).background(if (accent) C.red else C.panelHi)
            .border(1.dp, if (accent) C.red else C.lineHi, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}
