package io.github.bornaradusin.cardash.car

import android.content.Context
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

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    /** The app to show: the one playing, else the most recent one. */
    private fun pick() {
        val sessions = runCatching { manager.getActiveSessions(CarNotifications.component(context)) }.getOrNull().orEmpty()
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, main)
        }
        publish()
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
