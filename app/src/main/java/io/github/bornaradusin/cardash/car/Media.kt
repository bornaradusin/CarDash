package io.github.bornaradusin.cardash.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.browse.MediaBrowser
import android.os.Bundle
import android.service.media.MediaBrowserService
import android.view.KeyEvent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NowPlaying(
    val title: String,
    val artist: String?,
    val app: String,
    val playing: Boolean,
    val durationMs: Long,
    val art: Bitmap?,
    /** Position at [at] (elapsed realtime), moving at [speed] while playing. */
    val positionMs: Long,
    val at: Long,
    val speed: Float,
) {
    fun positionNow(): Long =
        if (!playing) positionMs else positionMs + ((SystemClock.elapsedRealtime() - at) * speed).toLong()
}

/**
 * What's playing in any app (YouTube Music, Spotify, the radio's own player) and its controls,
 * through Android's media sessions. Needs the same notification access as the Waze card.
 */
class Media(private val context: Context) {

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    /** The person's music player: shown first when nothing is playing. */
    var preferred: String? = null
        set(v) {
            field = v
            if (listening) pick()
        }

    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pick()
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick() }
    private var listening = false

    /** Starts following the media apps, once notification access is allowed. */
    fun start() {
        if (listening || !CarNotifications.allowed(context)) return
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, CarNotifications.component(context), main)
            listening = true
            pick()
        }
    }

    fun stop() {
        if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChanged) }
        listening = false
        controller?.unregisterCallback(callback)
        controller = null
    }

    /** Play was pressed with nothing playing: the app being started, and until when to keep at it. */
    private var pendingApp: String? = null
    private var pendingUntil = 0L
    /** The app had to be opened for it: CarDash comes back once it plays. */
    private var opened = false
    private var pokedAt = 0L

    /** Brings CarDash back in front, after a music app had to be opened to start it. */
    var backToCarDash: (() -> Unit)? = null

    /**
     * Play / pause. With nothing playing, starts [musicApp] (YouTube Music unless the person picked
     * another), trying each way in turn until it plays: Play to the player that's there (after the radio
     * starts it's often there but not ready), the way a headset's play button does, the app's own
     * player service, and last opening the app. Once the app's player is up, Play is pressed for you
     * (it can take a while to load after the radio starts) and CarDash comes back in front.
     */
    fun playPause(musicApp: String?) {
        val c = controller
        if (c?.playbackState?.state == PlaybackState.STATE_PLAYING) {
            pendingApp = null
            c.transportControls.pause()
            return
        }
        val target = musicApp ?: c?.packageName
        pendingApp = target
        pendingUntil = SystemClock.elapsedRealtime() + 30_000
        opened = false
        if (c != null) runCatching { c.transportControls.play() } else mediaKey()
        if (target == null) return
        main.postDelayed({
            if (!stillWaiting()) return@postDelayed
            if (c != null) mediaKey()
            resume(target)
            main.postDelayed({
                if (!stillWaiting()) return@postDelayed
                context.packageManager.getLaunchIntentForPackage(target)?.let {
                    runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    opened = true
                }
                keepPoking()
            }, 2_500)
        }, 1_500)
    }

    private fun mediaKey() {
        val audio = context.getSystemService(AudioManager::class.java)
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        }
    }

    private fun stillWaiting() = pendingApp != null && SystemClock.elapsedRealtime() < pendingUntil && !isPlaying()

    /** While an app is being started: every 2 seconds, Play to its player once it's up, until it plays. */
    private fun keepPoking() {
        checkPending()
        if (pendingApp != null) main.postDelayed({ keepPoking() }, 2_000)
    }

    /** Playing now: done (and back to CarDash if the app was opened). Else Play to the app's player, if it's up yet. */
    private fun checkPending() {
        val pkg = pendingApp ?: return
        val now = SystemClock.elapsedRealtime()
        if (now > pendingUntil) {
            pendingApp = null
            return
        }
        val sessions = runCatching { manager.getActiveSessions(CarNotifications.component(context)) }.getOrNull().orEmpty()
        if (sessions.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }) {
            pendingApp = null
            if (opened) main.postDelayed({ runCatching { backToCarDash?.invoke() } }, 1_200)
            return
        }
        val s = sessions.firstOrNull { it.packageName == pkg } ?: return
        if (now - pokedAt < 2_000) return
        pokedAt = now
        runCatching { s.transportControls.play() }
    }

    private fun isPlaying(): Boolean {
        pick()
        return controller?.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    /** Asks the app's player service to carry on with what it played last (Android's playback resumption). */
    private fun resume(pkg: String) {
        val service = context.packageManager
            .queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(pkg), 0)
            .firstOrNull()?.serviceInfo ?: return
        var browser: MediaBrowser? = null
        val callbacks = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val b = browser ?: return
                runCatching { MediaController(context, b.sessionToken).transportControls.play() }
                main.postDelayed({ runCatching { b.disconnect() } }, 5_000)
            }
        }
        val hints = Bundle().apply { putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true) }
        val created = MediaBrowser(context, ComponentName(service.packageName, service.name), callbacks, hints)
        browser = created
        runCatching { created.connect() }
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    /** The app to show: the one playing, else the most recent one. */
    private fun pick() {
        val sessions = runCatching { manager.getActiveSessions(CarNotifications.component(context)) }.getOrNull().orEmpty()
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.packageName == preferred }
            ?: sessions.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, main)
        }
        publish()
        checkPending()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (c == null || title.isNullOrBlank()) {
            _now.value = null
            return
        }
        val state = c.playbackState
        val app = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName)
        _now.value = NowPlaying(
            title = title,
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            app = app,
            playing = state?.state == PlaybackState.STATE_PLAYING,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            positionMs = state?.position ?: 0,
            at = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = state?.playbackSpeed ?: 1f,
        )
    }
}
