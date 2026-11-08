package io.github.bornaradusin.cardash.nav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineGeometryRegionDefinition
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionDefinition
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** A saved map area, as Settings shows it. */
data class MapArea(
    val id: Long,
    val name: String,
    val savedAt: Long,
    val complete: Boolean,
    val percent: Int,
    val bytes: Long,
    /** Downloading now (not paused, not waiting for Wi-Fi). */
    val active: Boolean,
    val waitingForWifi: Boolean,
    val error: String? = null,
)

/**
 * Map areas kept on the radio for driving without signal, through MapLibre's offline database. With
 * "Wi-Fi only", downloads wait while the radio is on its own mobile data. A phone's hotspot counts as
 * Wi-Fi: the radio joins it over Wi-Fi, though Android marks it metered.
 */
class OfflineMaps(private val context: Context, private val wifiOnly: () -> Boolean) {
    private val manager by lazy { OfflineManager.getInstance(context) }
    private val regions = HashMap<Long, OfflineRegion>()
    /** Areas the person wants downloading (as opposed to paused). */
    private val wanted = HashSet<Long>()
    private val _areas = MutableStateFlow<List<MapArea>>(emptyList())
    val areas: StateFlow<List<MapArea>> = _areas
    private var started = false
    private var onWifi = true

    fun start() {
        if (started) return
        started = true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        onWifi = unmetered(cm)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val now = wifiOrWired(caps)
                    if (now != onWifi) { onWifi = now; applyNetwork() }
                }
                override fun onLost(network: Network) {
                    onWifi = false
                    applyNetwork()
                }
            })
        }
        refresh()
    }

    private fun unmetered(cm: ConnectivityManager): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)?.let(::wifiOrWired) ?: false

    /** Wi-Fi (a phone's hotspot included) or a cable, not the radio's own SIM. */
    private fun wifiOrWired(caps: NetworkCapabilities): Boolean =
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

    private fun mayDownload() = !wifiOnly() || onWifi

    /** Whether Android gave CarDash its internet permission: an update over 1.0.1-1.0.8, which had none,
     * may not get it back on some radios. */
    private fun hasInternet() =
        context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED

    /** Wi-Fi came or went, or the setting changed: start or hold the downloads. */
    fun applyNetwork() {
        regions.values.forEach { r ->
            if (r.id in wanted) r.setDownloadState(if (mayDownload()) OfflineRegion.STATE_ACTIVE else OfflineRegion.STATE_INACTIVE)
        }
        _areas.update { list -> list.map { it.copy(active = it.id in wanted && mayDownload() && !it.complete, waitingForWifi = it.id in wanted && !mayDownload() && !it.complete) } }
    }

    private fun refresh() {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions.orEmpty().forEach { watch(it, startNow = false) }
            }
            override fun onError(error: String) = Unit
        })
    }

    /** All of the area within [km] of here. */
    fun saveAround(here: Pos, km: Double, name: String) {
        val dLat = km / 111.0
        val dLon = km / (111.0 * kotlin.math.cos(Math.toRadians(here.lat)))
        val bounds = LatLngBounds.from(here.lat + dLat, here.lon + dLon, here.lat - dLat, here.lon - dLon)
        create(OfflineTilePyramidRegionDefinition(STYLE, bounds, 6.0, MAX_ZOOM, PIXEL_RATIO), name)
    }

    /** The whole island of Ireland, up to street detail. */
    fun saveIreland() {
        val bounds = LatLngBounds.from(55.45, -5.35, 51.35, -10.7)
        create(OfflineTilePyramidRegionDefinition(STYLE, bounds, 5.0, MAX_ZOOM, PIXEL_RATIO), "All of Ireland")
    }

    /** The roads along a route (the tiles the line passes through). */
    fun saveAlong(route: Route, name: String) {
        val line = LineString.fromLngLats(route.points.map { Point.fromLngLat(it.lon, it.lat) })
        create(OfflineGeometryRegionDefinition(STYLE, line, 8.0, MAX_ZOOM, PIXEL_RATIO), name)
    }

    private fun create(def: OfflineRegionDefinition, name: String) {
        start()
        val meta = JSONObject().put("name", name).put("savedAt", System.currentTimeMillis()).toString().toByteArray()
        manager.createOfflineRegion(def, meta, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                wanted += offlineRegion.id
                watch(offlineRegion, startNow = true)
            }
            override fun onError(error: String) = Unit
        })
    }

    private fun watch(r: OfflineRegion, startNow: Boolean) {
        regions[r.id] = r
        val meta = runCatching { JSONObject(String(r.metadata)) }.getOrNull()
        val name = meta?.optString("name")?.ifEmpty { null } ?: "Map area"
        val savedAt = meta?.optLong("savedAt") ?: 0L
        if (_areas.value.none { it.id == r.id }) {
            _areas.update { it + MapArea(r.id, name, savedAt, false, 0, 0, false, false) }
        }
        r.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) = show(r.id, status)
            override fun onError(error: OfflineRegionError) {
                val why = problem(error.message, hasInternet(), error.reason)
                _areas.update { list -> list.map { if (it.id == r.id) it.copy(error = why) else it } }
            }
            override fun mapboxTileCountLimitExceeded(limit: Long) = Unit
        })
        r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                status?.let { show(r.id, it) }
            }
            override fun onError(error: String?) = Unit
        })
        if (startNow) r.setDownloadState(if (mayDownload()) OfflineRegion.STATE_ACTIVE else OfflineRegion.STATE_INACTIVE)
        applyNetwork()
    }

    /** Each area's fetched count at its last status, to tell that it moved on. */
    private val lastCount = HashMap<Long, Long>()

    private fun show(id: Long, s: OfflineRegionStatus) {
        val pct = if (s.requiredResourceCount > 0) (100 * s.completedResourceCount / s.requiredResourceCount).toInt() else 0
        if (s.isComplete) {
            wanted -= id
            regions[id]?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        }
        _areas.update { list ->
            list.map {
                if (it.id != id) it else it.copy(
                    // It moved on: a tile that failed before was fetched on a retry
                    error = if (s.isComplete || s.completedResourceCount > (lastCount[id] ?: Long.MAX_VALUE)) null else it.error,
                    complete = s.isComplete,
                    percent = if (s.isComplete) 100 else pct.coerceIn(0, 99),
                    bytes = s.completedResourceSize,
                    active = id in wanted && mayDownload() && !s.isComplete,
                    waitingForWifi = id in wanted && !mayDownload() && !s.isComplete,
                )
            }
        }
        lastCount[id] = s.completedResourceCount
    }

    fun pause(id: Long) {
        wanted -= id
        regions[id]?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        _areas.update { list -> list.map { if (it.id == id) it.copy(active = false, waitingForWifi = false) else it } }
    }

    fun resume(id: Long) {
        wanted += id
        applyNetwork()
    }

    /** Fetches the area again, for map changes since it was saved. */
    fun update(id: Long) {
        val r = regions[id] ?: return
        r.invalidate(object : OfflineRegion.OfflineRegionInvalidateCallback {
            override fun onInvalidate() {
                val meta = runCatching { JSONObject(String(r.metadata)) }.getOrDefault(JSONObject())
                meta.put("savedAt", System.currentTimeMillis())
                _areas.update { list -> list.map { if (it.id == id) it.copy(savedAt = System.currentTimeMillis(), complete = false, percent = 0) else it } }
                r.updateMetadata(meta.toString().toByteArray(), object : OfflineRegion.OfflineRegionUpdateMetadataCallback {
                    override fun onUpdate(metadata: ByteArray) = Unit
                    override fun onError(error: String) = Unit
                })
                resume(id)
            }
            override fun onError(error: String) = Unit
        })
    }

    fun remove(id: Long) {
        val r = regions[id] ?: return
        wanted -= id
        r.setDownloadState(OfflineRegion.STATE_INACTIVE)
        r.setObserver(null)
        r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                regions -= id
                _areas.update { list -> list.filterNot { it.id == id } }
            }
            override fun onError(error: String) = Unit
        })
    }

    companion object {
        /**
         * What a download error means for the person. EPERM: Android refused CarDash a connection at all,
         * which is the radio's per-app network setting or a data saver, not the map's server.
         */
        fun problem(message: String?, hasInternet: Boolean = true, reason: String? = null): String = when {
            !hasInternet ->
                "Android didn't give CarDash its internet permission when it was updated. Uninstall CarDash and " +
                    "install it again (its settings start over)"
            message == null -> "the download failed"
            message.contains("EPERM") || message.contains("Operation not permitted") ->
                "the radio doesn't let CarDash use the internet. Allow it in the radio's settings (Apps › CarDash: " +
                    "network, Wi-Fi or mobile data) and turn off any data saver for it"
            // A tile that timed out or a dropped connection: MapLibre tries it again by itself
            reason == OfflineRegionError.REASON_CONNECTION || message.contains("timeout", ignoreCase = true) ->
                "slow connection, trying again"
            else -> message
        }

        /** OpenFreeMap's dark map: free, no key. */
        const val STYLE = "https://tiles.openfreemap.org/styles/dark"
        private const val MAX_ZOOM = 14.0
        private const val PIXEL_RATIO = 1f
    }
}
