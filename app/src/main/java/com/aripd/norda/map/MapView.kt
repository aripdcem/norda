package com.aripd.norda.map

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.aripd.norda.R
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
    private var maxZoom = 16
    private var zoom = 14
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

    private val tilePaint = Paint()
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
            minZoom = newStore.minZoom.coerceIn(0, 20)
            maxZoom = newStore.maxZoom.coerceIn(minZoom, 20)
            zoom = zoom.coerceIn(minZoom, maxZoom)
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
        applyZoom(newZoom.coerceIn(minZoom, maxZoom))
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
        zoom = WebMercator.fitZoom(latMin, lonMin, latMax, lonMax, width, height, minZoom, maxZoom)
        setCenter((latMin + latMax) / 2.0, (lonMin + lonMax) / 2.0)
    }

    // ---- Gestures ----

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
        ): Boolean {
            centerX += dx / TILE_SIZE
            centerY += dy / TILE_SIZE
            clampCenter()
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            applyZoom((zoom + 1).coerceAtMost(maxZoom))
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val callback = onLongPressLatLon ?: return
            val topLeftX = centerX - width / 2.0 / TILE_SIZE
            val topLeftY = centerY - height / 2.0 / TILE_SIZE
            val lat = WebMercator.latDeg(topLeftY + e.y / TILE_SIZE, zoom)
            val lon = WebMercator.lonDeg(topLeftX + e.x / TILE_SIZE, zoom)
            callback(lat, lon)
        }
    })

    private var scaleAccumulator = 1f
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleAccumulator *= detector.scaleFactor
            if (scaleAccumulator > 1.4f) {
                applyZoom((zoom + 1).coerceAtMost(maxZoom))
                scaleAccumulator = 1f
            } else if (scaleAccumulator < 0.71f) {
                applyZoom((zoom - 1).coerceAtLeast(minZoom))
                scaleAccumulator = 1f
            }
            return true
        }
    })

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
        val topLeftX = centerX - width / 2.0 / TILE_SIZE
        val topLeftY = centerY - height / 2.0 / TILE_SIZE
        val firstTx = floor(topLeftX).toInt()
        val firstTy = floor(topLeftY).toInt()
        val lastTx = floor(topLeftX + width.toDouble() / TILE_SIZE).toInt()
        val lastTy = floor(topLeftY + height.toDouble() / TILE_SIZE).toInt()

        for (tx in firstTx..lastTx) {
            for (ty in firstTy..lastTy) {
                val sx = ((tx - topLeftX) * TILE_SIZE).toFloat()
                val sy = ((ty - topLeftY) * TILE_SIZE).toFloat()
                if (tx < 0 || ty < 0 || tx >= world || ty >= world) {
                    drawGridTile(canvas, sx, sy, tx, ty)
                    continue
                }
                val key = TileCache.key(zoom, tx, ty)
                val bitmap = cache.get(key)
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, sx, sy, tilePaint)
                } else {
                    drawGridTile(canvas, sx, sy, tx, ty)
                    requestDecode(key, zoom, tx, ty)
                }
            }
        }

        drawTrack(canvas, topLeftX, topLeftY)
        drawWaypoints(canvas, topLeftX, topLeftY)
        drawMarker(canvas, topLeftX, topLeftY)
    }

    private fun drawWaypoints(canvas: Canvas, topLeftX: Double, topLeftY: Double) {
        for (w in waypoints) {
            val px = ((WebMercator.xTile(w.longitude, zoom) - topLeftX) * TILE_SIZE).toFloat()
            val py = ((WebMercator.yTile(w.latitude, zoom) - topLeftY) * TILE_SIZE).toFloat()
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
        canvas.drawRect(sx, sy, sx + TILE_SIZE, sy + TILE_SIZE, bg)
        canvas.drawLine(sx, sy, sx + TILE_SIZE, sy, gridLine)
        canvas.drawLine(sx, sy, sx, sy + TILE_SIZE, gridLine)
    }

    private fun drawTrack(canvas: Canvas, topLeftX: Double, topLeftY: Double) {
        if (track.size < 2) return
        trackPath.rewind()
        var first = true
        for (p in track) {
            val px = ((WebMercator.xTile(p.longitude, zoom) - topLeftX) * TILE_SIZE).toFloat()
            val py = ((WebMercator.yTile(p.latitude, zoom) - topLeftY) * TILE_SIZE).toFloat()
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
        val px = ((WebMercator.xTile(locLon, zoom) - topLeftX) * TILE_SIZE).toFloat()
        val py = ((WebMercator.yTile(locLat, zoom) - topLeftY) * TILE_SIZE).toFloat()
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
