package io.github.bornaradusin.cardash

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.bornaradusin.cardash.car.Apps
import io.github.bornaradusin.cardash.car.CarNotifications
import io.github.bornaradusin.cardash.car.Media
import io.github.bornaradusin.cardash.ui.CarDash
import io.github.bornaradusin.cardash.ui.DashActions
import io.github.bornaradusin.cardash.ui.DashState
import io.github.bornaradusin.cardash.ui.Scaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val app get() = application as CarDashApp
    private lateinit var media: Media

    /** Bumped on resume, so permissions and installed apps are checked again after a trip to Settings. */
    private var resumes by mutableIntStateOf(0)
    private var hasLocation by mutableStateOf(false)

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = locationAllowed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        media = Media(this)
        hasLocation = locationAllowed()
        if (!hasLocation && savedInstanceState == null) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        val actions = DashActions(
            launch = { pkg -> Apps.launch(this, pkg) },
            setSlot = { i, pkg -> app.settings.setSlot(i, pkg) },
            playPause = { media.playPause() },
            next = { media.next() },
            previous = { media.previous() },
            scanFaults = { app.obd.scanFaults() },
            askUsbAgain = { app.obd.askAgain() },
            allowLocation = {
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            allowNotifications = {
                startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            chooseHome = {
                runCatching { startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS)) }
                    .onFailure { startActivity(Intent(AndroidSettings.ACTION_SETTINGS)) }
            },
            setName = { app.settings.setName(it) },
            setFullScreen = {
                app.settings.setFullScreen(it)
                applyFullScreen(it)
            },
        )

        setContent {
            val obd by app.obd.state.collectAsState()
            val nav by CarNotifications.nav.collectAsState()
            val now by media.now.collectAsState()
            val name by app.settings.name.collectAsState()
            val slots by app.settings.slots.collectAsState()
            val fullScreen by app.settings.fullScreen.collectAsState()
            var apps by remember { mutableStateOf(emptyList<io.github.bornaradusin.cardash.car.AppEntry>()) }
            LaunchedEffect(resumes) {
                apps = withContext(Dispatchers.IO) { Apps.all(this@MainActivity) }
            }
            val state = DashState(
                name = name,
                obd = obd,
                nav = nav,
                now = now,
                apps = apps,
                slots = slots,
                hasLocation = hasLocation,
                hasNotifications = remember(resumes) { CarNotifications.allowed(this@MainActivity) },
                fullScreen = fullScreen,
            )
            Scaled { CarDash(state, actions) }
        }
    }

    override fun onResume() {
        super.onResume()
        hasLocation = locationAllowed()
        resumes++
        app.obd.start()
        media.start()
        applyFullScreen(app.settings.fullScreen.value)
    }

    override fun onDestroy() {
        media.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The dongle was just plugged in: look for it straight away
        app.obd.start()
    }

    /** Pressing Back on Home does nothing: there's nowhere further back to go. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit

    private fun locationAllowed() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun applyFullScreen(on: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
