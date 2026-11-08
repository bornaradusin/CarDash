package io.github.bornaradusin.cardash.ui

import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CarDash's own keyboard, in place of Android's. Every text box in the app still works as normal:
 * when one takes the focus, the request Android's keyboard would get comes here instead, and the
 * keys type into the box through the same connection a keyboard app would use. So the cursor,
 * the Search and Done keys and number boxes all behave as they do with Android's keyboard.
 */
@Stable
class CarKeys {
    /** The text box being typed into; null when none has the focus. */
    var connection by mutableStateOf<InputConnection?>(null)
        private set

    /** What kind of box it is: numbers or text, and what its action key does. */
    var info by mutableStateOf<EditorInfo?>(null)
        private set

    /** Screens and dialogs showing the keyboard in their own layout; the one along the bottom stays away then. */
    var hosts by mutableIntStateOf(0)

    fun attach(ic: InputConnection, editor: EditorInfo) {
        info = editor
        connection = ic
    }

    fun detach(ic: InputConnection) {
        if (connection === ic) {
            connection = null
            info = null
        }
    }
}

val LocalCarKeys = staticCompositionLocalOf<CarKeys?> { null }

/**
 * Keeps Android's keyboard away from everything inside, and shows CarDash's keyboard along the
 * bottom while a text box has the focus (the screen above makes room for it), unless the screen or
 * dialog with the box shows it itself ([CarKeyboardHere]).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CarKeyboardHost(content: @Composable () -> Unit) {
    val keys = remember { CarKeys() }
    CompositionLocalProvider(LocalCarKeys provides keys) {
        InterceptPlatformTextInput(
            interceptor = { request, _ ->
                // Not passed on to Android: CarDash's keys type into the box instead
                val editor = EditorInfo()
                val ic = request.createInputConnection(editor)
                keys.attach(ic, editor)
                try {
                    awaitCancellation()
                } finally {
                    keys.detach(ic)
                }
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                if (keys.connection != null && keys.hosts == 0) CarKeyboard(Modifier.fillMaxWidth())
            }
        }
    }
}

/** The keyboard in this screen's or dialog's own layout, while one of its boxes has the focus. */
@Composable
fun CarKeyboardHere(modifier: Modifier = Modifier) {
    val keys = LocalCarKeys.current ?: return
    DisposableEffect(keys) {
        keys.hosts++
        onDispose { keys.hosts-- }
    }
    if (keys.connection != null) CarKeyboard(modifier)
}

/**
 * A dialog that's typed in: its card in the middle of the screen, and CarDash's keyboard right across
 * the bottom, edge to edge, while one of its boxes has the focus. Tapping beside the card closes it.
 */
@Composable
fun KeyboardDialog(onDismissRequest: () -> Unit, card: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) { detectTapGestures { onDismissRequest() } },
                contentAlignment = Alignment.Center,
            ) {
                // Taps on the card stay on the card
                Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) { card() }
            }
            CarKeyboardHere(Modifier.fillMaxWidth())
        }
    }
}

private enum class KeyLook { Plain, Special, Lit, Action }

private const val LETTERS_2 = "qwertyuiop"
private const val LETTERS_3 = "asdfghjkl'"
private const val LETTERS_4 = "zxcvbnm-"
private const val SYMBOLS_2 = "@#&_/():;\""
private const val SYMBOLS_3 = "!?+=*%€£$'"
private const val SYMBOLS_4 = "<>[]{}~|"

/** The keys themselves: letters (with a row of numbers and a page of symbols), or just numbers. */
@Composable
fun CarKeyboard(modifier: Modifier = Modifier) {
    val keys = LocalCarKeys.current ?: return
    val ic = keys.connection ?: return
    val editor = keys.info
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val kind = (editor?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
    val numbers = kind == InputType.TYPE_CLASS_NUMBER || kind == InputType.TYPE_CLASS_PHONE
    // A box for a number with a fraction (0.8 L/h) gets a point key
    val decimal = kind == InputType.TYPE_CLASS_NUMBER && ((editor?.inputType ?: 0) and InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0
    val action = (editor?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
    val actionLabel = when (action) {
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_SEND -> "Send"
        else -> "Done"
    }
    var shift by remember(ic) { mutableStateOf(false) }
    var symbols by remember(ic) { mutableStateOf(false) }
    // A capital to start an empty box
    LaunchedEffect(ic) { shift = !numbers && ic.getTextBeforeCursor(1, 0).isNullOrEmpty() }

    fun type(s: String) {
        ic.commitText(if (shift) s.uppercase() else s, 1)
        shift = false
    }
    fun backspace() {
        if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
    }
    fun paste() {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) ic.commitText(if (numbers) text.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) } else text, 1)
    }
    fun act() {
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action)
        // Done (or no action): finished typing, the keyboard goes
        if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            focus.clearFocus()
        }
    }

    // Right across the screen, edge to edge, so the keys are as wide as they can be
    Box(
        modifier.background(Color(0xFF0B0D12)).padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (numbers) {
            // One row across: big keys, and it leaves the box being typed in plenty of room above
            KeyRow {
                "1234567890".forEach { c -> Key(c.toString(), Modifier.weight(1f)) { type(c.toString()) } }
                if (decimal) Key(".", Modifier.weight(1f)) { type(".") }
                Key(null, Modifier.weight(1.5f), icon = Ic.backspace, look = KeyLook.Special, repeat = true) { backspace() }
                Key(actionLabel, Modifier.weight(2f), look = KeyLook.Action) { act() }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyRow { "1234567890".forEach { c -> Key(c.toString(), Modifier.weight(1f)) { type(c.toString()) } } }
                KeyRow {
                    (if (symbols) SYMBOLS_2 else LETTERS_2).forEach { c ->
                        val s = c.toString()
                        Key(if (shift && !symbols) s.uppercase() else s, Modifier.weight(1f)) { type(s) }
                    }
                }
                KeyRow {
                    (if (symbols) SYMBOLS_3 else LETTERS_3).forEach { c ->
                        val s = c.toString()
                        Key(if (shift && !symbols) s.uppercase() else s, Modifier.weight(1f)) { type(s) }
                    }
                }
                KeyRow {
                    if (symbols) {
                        Key("\\", Modifier.weight(1f)) { type("\\") }
                    } else {
                        Key(null, Modifier.weight(1.5f), icon = Ic.shift, look = if (shift) KeyLook.Lit else KeyLook.Special) { shift = !shift }
                    }
                    (if (symbols) SYMBOLS_4 else LETTERS_4).forEach { c ->
                        val s = c.toString()
                        Key(if (shift && !symbols) s.uppercase() else s, Modifier.weight(1f)) { type(s) }
                    }
                    Key(null, Modifier.weight(1.5f), icon = Ic.backspace, look = KeyLook.Special, repeat = true) { backspace() }
                }
                KeyRow {
                    Key(if (symbols) "ABC" else "?#&", Modifier.weight(1.5f), look = KeyLook.Special) { symbols = !symbols }
                    Key(",", Modifier.weight(1f)) { type(",") }
                    Key("space", Modifier.weight(4f), small = true) { ic.commitText(" ", 1) }
                    Key(".", Modifier.weight(1f)) { type(".") }
                    Key("Paste", Modifier.weight(1.5f), look = KeyLook.Special, small = true) { paste() }
                    Key(actionLabel, Modifier.weight(2f), look = KeyLook.Action) { act() }
                }
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** One key: lights up while it's held; [repeat] keeps going while it's held (backspace). */
@Composable
private fun Key(
    label: String?,
    modifier: Modifier,
    icon: ImageVector? = null,
    look: KeyLook = KeyLook.Plain,
    small: Boolean = false,
    repeat: Boolean = false,
    onTap: () -> Unit,
) {
    var down by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(12.dp)
    val fill = when {
        look == KeyLook.Action -> if (down) C.redLight else C.red
        down -> C.lineHi
        look == KeyLook.Lit -> C.redTint
        look == KeyLook.Special -> C.panel
        else -> C.panelHi
    }
    Box(
        modifier
            .height(50.dp)
            .clip(shape)
            .background(fill)
            .then(
                when (look) {
                    KeyLook.Lit -> Modifier.border(1.5.dp, C.red, shape)
                    KeyLook.Action -> Modifier
                    else -> Modifier.border(1.dp, C.line, shape)
                },
            )
            // Taps that don't take the focus, so the text box keeps it
            .pointerInput(repeat) {
                detectTapGestures(
                    onPress = {
                        down = true
                        if (repeat) {
                            tap()
                            val again: Job = scope.launch {
                                delay(450)
                                while (true) {
                                    tap()
                                    delay(60)
                                }
                            }
                            tryAwaitRelease()
                            again.cancel()
                        } else if (tryAwaitRelease()) {
                            tap()
                        }
                        down = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(icon, null, tint = if (look == KeyLook.Lit) C.red else C.ink, modifier = Modifier.size(24.dp))
            label != null -> Text(
                label,
                color = if (look == KeyLook.Action) Color.Black else if (look == KeyLook.Special) C.ink2 else C.ink,
                fontSize = if (small || label.length > 2) 17.sp else 22.sp,
                fontWeight = if (look == KeyLook.Action) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
