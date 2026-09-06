package com.aripd.norda.map

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Path
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.aripd.norda.R
import com.aripd.norda.core.map.ContinuousZoom
import com.aripd.norda.core.map.Overzoom
import com.aripd.norda.core.map.WebMercator
import com.aripd.norda.core.map.WebMercator.TILE_SIZE
import com.aripd.norda.core.nav.Waypoint
import com.aripd.norda.core.track.TrackPoint
import kotlin.math.floor

/**
 * Hand-drawn raster tile map (docs/MVP.md, 7.1): only the tiles intersecting
 * the viewport are drawn, bitmaps come from an LRU cache, decoding is done on
 * a single background thread. Without a package or with a tile missing, a
 * procedural grid is drawn — the track and the cursor remain visible.
 *
 * The dial discipline applies: onDraw allocates nothing per frame.
 */
class MapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** The recording screen locks the map: following yes, panning no. */
    var interactive = true

    /** Whether the center moves to each new location (recording screen). */
    var follow = false

    private var store: TileStore? = null
    private var minZoom = 3
    // Gesture ceiling; with a pack it is the pack ceiling + Overzoom.LEVELS —
    // levels above the pack are drawn by scaling the pack's top tiles (F-13).
    private var maxZoom = 16
    private var packMaxZoom = 16
    // Integer tile level and the fractional zoom it serves (F-14): tiles are
    // read at `zoom` and drawn at pxPerTile = 256 × 2^(zoomF − zoom) pixels.
    private var zoom = 14
    private var zoomF = 14.0
    private var pxPerTile = TILE_SIZE.toDouble()
    private var centerX = WebMercator.xTile(0.0, 14)
    private var centerY = WebMercator.yTile(0.0, 14)

    private var track: List<TrackPoint> = emptyList()
    private var waypoints: List<Waypoint> = emptyList()
    private var hasLocation = false
    private var locLat = 0.0
    private var locLon = 0.0

    /** Long-press on the interactive map: waypoint-adding hook (lat, lon). */
    var onLongPressLatLon: ((Double, Double) -> Unit)? = null

    private val cache = TileCache()
    private val pending = HashSet<Long>()
    private val missing = HashSet<Long>()
    private var decodeThread: HandlerThread? = null
    private var decodeHandler: Handler? = null

    private val tilePaint = Paint().apply { isFilterBitmap = true }   // smooth overzoom
    private val overzoomSrc = Rect()
    private val tileDst = RectF()
    private val gridBgEven = Paint().apply { color = Color.rgb(232, 235, 230) }
    private val gridBgOdd = Paint().apply { color = Color.rgb(222, 227, 220) }
    private val gridLine = Paint().apply {
        color = Color.rgb(200, 206, 198)
        strokeWidth = 1f
    }
    private val trackPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val markerFill = Paint().apply { isAntiAlias = true }
    private val markerRing = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.WHITE
        isAntiAlias = true
    }
    private val waypointPaint = Paint().apply {
        color = Color.rgb(197, 154, 46)
        isAntiAlias = true
    }
    private val waypointText = Paint().apply {
        color = Color.rgb(90, 70, 15)
        textAlign = Paint.Align.CENTER
        textSize = 30f
        isFakeBoldText = true
        isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, Color.WHITE)
    }
    private val trackPath = Path()
    private val waypointPath = Path()

    init {
        val accent = context.getColor(R.color.norda_green)
        trackPaint.color = accent
        markerFill.color = accent
    }

    // ---- External API ----

    fun setStore(newStore: TileStore?) {
        store?.close()
        store = newStore
        if (newStore != null) {
            minZoom = newStore.minZoom.coerceIn(0, Overzoom.MAX_ZOOM)
            packMaxZoom = newStore.maxZoom.coerceIn(minZoom, Overzoom.MAX_ZOOM)
            maxZoom = Overzoom.ceiling(packMaxZoom)
            zoomF = zoomF.coerceIn(minZoom.toDouble(), maxZoom.toDouble())
            syncLevel()
        }
        missing.clear()
        invalidate()
    }

    fun setCenter(latDeg: Double, lonDeg: Double) {
        centerX = WebMercator.xTile(lonDeg, zoom)
        centerY = WebMercator.yTile(latDeg, zoom)
        clampCenter()
        invalidate()
    }

    fun setZoom(newZoom: Int) {
        zoomF = newZoom.coerceIn(minZoom, maxZoom).toDouble()
        syncLevel()
    }

    fun setTrack(points: List<TrackPoint>) {
        track = points
        invalidate()
    }

    fun setWaypoints(list: List<Waypoint>) {
        waypoints = list
        invalidate()
    }

    fun setCurrentLocation(latDeg: Double, lonDeg: Double) {
        hasLocation = true
        locLat = latDeg
        locLon = lonDeg
        if (follow) setCenter(latDeg, lonDeg) else invalidate()
    }

    /** Fits the track to the view; must be called after layout (via post). */
    fun fitToTrack() {
        if (track.isEmpty() || width == 0 || height == 0) return
        var latMin = track[0].latitude
        var latMax = latMin
        var lonMin = track[0].longitude
        var lonMax = lonMin
        for (p in track) {
            if (p.latitude < latMin) latMin = p.latitude
            if (p.latitude > latMax) latMax = p.latitude
            if (p.longitude < lonMin) lonMin = p.longitude
            if (p.longitude > lonMax) lonMax = p.longitude
        }
        val level = WebMercator.fitZoom(latMin, lonMin, latMax, lonMax, width, height, minZoom, maxZoom)
        zoom = level
        zoomF = level.toDouble()
        pxPerTile = TILE_SIZE.toDouble()
        setCenter((latMin + latMax) / 2.0, (lonMin + lonMax) / 2.0)
    }

    // ---- Gestures ----

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
        ): Boolean {
            centerX += dx / pxPerTile
            centerY += dy / pxPerTile
            clampCenter()
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomBy(2.0, e.x, e.y)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val callback = onLongPressLatLon ?: return
            val topLeftX = centerX - width / 2.0 / pxPerTile
            val topLeftY = centerY - height / 2.0 / pxPerTile
            val lat = WebMercator.latDeg(topLeftY + e.y / pxPerTile, zoom)
            val lon = WebMercator.lonDeg(topLeftX + e.x / pxPerTile, zoom)
            callback(lat, lon)
        }
    })

    // Pinch: continuous zoom around the fingers (F-14) — no whole-level jumps.
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomBy(detector.scaleFactor.toDouble(), detector.focusX, detector.focusY)
            return true
        }
    })

    /** Magnifies by [factor] keeping the map point under (fx, fy) still. */
    private fun zoomBy(factor: Double, fx: Float, fy: Float) {
        val target = (zoomF + Math.log(factor) / Math.log(2.0))
            .coerceIn(minZoom.toDouble(), maxZoom.toDouble())
        val applied = Math.pow(2.0, target - zoomF)
        if (applied == 1.0) return
        val focusX = centerX + (fx - width / 2.0) / pxPerTile
        val focusY = centerY + (fy - height / 2.0) / pxPerTile
        centerX = ContinuousZoom.focalCenter(centerX, focusX, applied)
        centerY = ContinuousZoom.focalCenter(centerY, focusY, applied)
        zoomF = target
        syncLevel()
    }

    /** Re-derives the tile level and the on-screen tile size from zoomF. */
    private fun syncLevel() {
        val level = ContinuousZoom.level(zoomF, minZoom, maxZoom)
        if (level != zoom) applyZoom(level)
        pxPerTile = TILE_SIZE * ContinuousZoom.scale(zoomF, zoom)
        clampCenter()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactive) return false
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }

    private fun applyZoom(newZoom: Int) {
        if (newZoom == zoom) return
        val factor = Math.pow(2.0, (newZoom - zoom).toDouble())
        centerX *= factor
        centerY *= factor
        zoom = newZoom
        missing.clear()
        clampCenter()
        invalidate()
    }

    private fun clampCenter() {
        val world = WebMercator.worldTiles(zoom).toDouble()
        centerX = centerX.coerceIn(0.0, world)
        centerY = centerY.coerceIn(0.0, world)
    }

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        val world = WebMercator.worldTiles(zoom)
        val p = pxPerTile
        val size = p.toFloat()
        val topLeftX = centerX - width / 2.0 / p
        val topLeftY = centerY - height / 2.0 / p
        val firstTx = floor(topLeftX).toInt()
        val firstTy = floor(topLeftY).toInt()
        val lastTx = floor(topLeftX + width.toDouble() / p).toInt()
        val lastTy = floor(topLeftY + height.toDouble() / p).toInt()

        for (tx in firstTx..lastTx) {
            for (ty in firstTy..lastTy) {
                val sx = ((tx - topLeftX) * p).toFloat()
                val sy = ((ty - topLeftY) * p).toFloat()
                if (tx < 0 || ty < 0 || tx >= world || ty >= world) {
                    drawGridTile(canvas, sx, sy, tx, ty)
                    continue
                }
                if (zoom <= packMaxZoom) {
                    val key = TileCache.key(zoom, tx, ty)
                    val bitmap = cache.get(key)
                    if (bitmap != null) {
                        tileDst.set(sx, sy, sx + size, sy + size)
                        canvas.drawBitmap(bitmap, null, tileDst, tilePaint)
                    } else {
                        drawGridTile(canvas, sx, sy, tx, ty)
                        requestDecode(key, zoom, tx, ty)
                    }
                } else {
                    // Overzoom: read the pack's top tile and stretch the patch
                    // that covers this tile (F-13).
                    val src = Overzoom.source(zoom, tx, ty, packMaxZoom, TILE_SIZE)
                    val key = TileCache.key(src.zoom, src.x, src.y)
                    val bitmap = cache.get(key)
                    if (bitmap != null) {
                        overzoomSrc.set(src.offsetX, src.offsetY, src.offsetX + src.size, src.offsetY + src.size)
                        tileDst.set(sx, sy, sx + size, sy + size)
                        canvas.drawBitmap(bitmap, overzoomSrc, tileDst, tilePaint)
                    } else {
                        drawGridTile(canvas, sx, sy, tx, ty)
                        requestDecode(key, src.zoom, src.x, src.y)
                    }
                }
            }
        }

        drawTrack(canvas, topLeftX, topLeftY)
        drawWaypoints(canvas, topLeftX, topLeftY)
        drawMarker(canvas, topLeftX, topLeftY)
    }

    private fun drawWaypoints(canvas: Canvas, topLeftX: Double, topLeftY: Double) {
        for (w in waypoints) {
            val px = ((WebMercator.xTile(w.longitude, zoom) - topLeftX) * pxPerTile).toFloat()
            val py = ((WebMercator.yTile(w.latitude, zoom) - topLeftY) * pxPerTile).toFloat()
            if (px < -60 || py < -60 || px > width + 60 || py > height + 60) continue
            waypointPath.rewind()
            waypointPath.moveTo(px, py - 14f)
            waypointPath.lineTo(px + 14f, py)
            waypointPath.lineTo(px, py + 14f)
            waypointPath.lineTo(px - 14f, py)
            waypointPath.close()
            canvas.drawPath(waypointPath, waypointPaint)
            canvas.drawText(w.name, px, py + 44f, waypointText)
        }
    }

    private fun drawGridTile(canvas: Canvas, sx: Float, sy: Float, tx: Int, ty: Int) {
        val bg = if ((tx + ty) % 2 == 0) gridBgEven else gridBgOdd
        val size = pxPerTile.toFloat()
        canvas.drawRect(sx, sy, sx + size, sy + size, bg)
        canvas.drawLine(sx, sy, sx + size, sy, gridLine)
        canvas.drawLine(sx, sy, sx, sy + size, gridLine)
    }

    private fun drawTrack(canvas: Canvas, topLeftX: Double, topLeftY: Double) {
        if (track.size < 2) return
        trackPath.rewind()
        var first = true
        for (p in track) {
            val px = ((WebMercator.xTile(p.longitude, zoom) - topLeftX) * pxPerTile).toFloat()
            val py = ((WebMercator.yTile(p.latitude, zoom) - topLeftY) * pxPerTile).toFloat()
            if (first) {
                trackPath.moveTo(px, py)
                first = false
            } else {
                trackPath.lineTo(px, py)
            }
        }
        canvas.drawPath(trackPath, trackPaint)
    }

    private fun drawMarker(canvas: Canvas, topLeftX: Double, topLeftY: Double) {
        if (!hasLocation) return
        val px = ((WebMercator.xTile(locLon, zoom) - topLeftX) * pxPerTile).toFloat()
        val py = ((WebMercator.yTile(locLat, zoom) - topLeftY) * pxPerTile).toFloat()
        canvas.drawCircle(px, py, 14f, markerFill)
        canvas.drawCircle(px, py, 14f, markerRing)
    }

    // ---- Tile decoding ----

    private fun requestDecode(key: Long, z: Int, tx: Int, ty: Int) {
        val s = store ?: return
        if (z < s.minZoom || z > s.maxZoom) return
        if (key in missing || !pending.add(key)) return
        ensureDecoder().post {
            val data = s.tile(z, tx, ty)
            val bitmap = data?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            post {
                pending.remove(key)
                if (bitmap != null) {
                    cache.put(key, bitmap)
                    invalidate()
                } else if (missing.size < 4096) {
                    missing.add(key)
                }
            }
        }
    }

    private fun ensureDecoder(): Handler {
        decodeHandler?.let { return it }
        val thread = HandlerThread("map-decode").apply { start() }
        decodeThread = thread
        return Handler(thread.looper).also { decodeHandler = it }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        decodeThread?.quitSafely()
        decodeThread = null
        decodeHandler = null
        store?.close()
        store = null
    }
}
