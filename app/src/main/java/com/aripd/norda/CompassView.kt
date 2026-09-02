package com.aripd.norda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Hand-drawn dial: the dial rotates relative to true north, the indicator at
 * the top is fixed. The north needle is green, the start-point marker a golden
 * diamond. onDraw allocates nothing per frame.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var headingDeg = 0.0
    private var targetBearingDeg: Double? = null
    private var waypointBearingDeg: Double? = null
    private var cardinals: Array<String> =
        resources.getStringArray(R.array.cardinals_8).let {
            arrayOf(it[0], it[2], it[4], it[6])
        }

    private val ringPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val tickPaint = Paint().apply {
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val letterPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }
    private val needlePaint = Paint().apply { isAntiAlias = true }
    private val southPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }
    private val targetPaint = Paint().apply {
        color = Color.rgb(232, 196, 104)
        isAntiAlias = true
    }
    private val waypointStroke = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.rgb(232, 196, 104)
        isAntiAlias = true
    }
    private val indicatorPaint = Paint().apply { isAntiAlias = true }
    private val needlePath = Path()
    private val diamondPath = Path()

    init {
        val accent = context.getColor(R.color.norda_green)
        needlePaint.color = accent
        southPaint.color = accent
        ringPaint.color = Color.argb(120, 128, 128, 128)
        tickPaint.color = Color.argb(160, 128, 128, 128)
        letterPaint.color = accent
        indicatorPaint.color = accent
    }

    fun setHeading(deg: Double) {
        headingDeg = deg
        invalidate()
    }

    fun setTargetBearing(deg: Double?) {
        targetBearingDeg = deg
        invalidate()
    }

    /** Nearest waypoint's bearing — hollow diamond (the filled one is the start). */
    fun setWaypointBearing(deg: Double?) {
        waypointBearingDeg = deg
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f * 0.86f
        letterPaint.textSize = radius * 0.18f

        canvas.save()
        canvas.rotate(-headingDeg.toFloat(), cx, cy)

        canvas.drawCircle(cx, cy, radius, ringPaint)

        // A tick every 30°; letters at the cardinal points (N/E/S/W).
        for (i in 0 until 12) {
            canvas.save()
            canvas.rotate(i * 30f, cx, cy)
            if (i % 3 == 0) {
                canvas.drawText(
                    cardinals[i / 3], cx,
                    cy - radius * 0.68f + letterPaint.textSize / 3f, letterPaint
                )
                canvas.drawLine(cx, cy - radius, cx, cy - radius * 0.90f, tickPaint)
            } else {
                canvas.drawLine(cx, cy - radius, cx, cy - radius * 0.94f, tickPaint)
            }
            canvas.restore()
        }

        // North needle (filled) and the south half (a line).
        needlePath.rewind()
        needlePath.moveTo(cx, cy - radius * 0.56f)
        needlePath.lineTo(cx - radius * 0.09f, cy)
        needlePath.lineTo(cx + radius * 0.09f, cy)
        needlePath.close()
        canvas.drawPath(needlePath, needlePaint)
        canvas.drawLine(cx, cy, cx, cy + radius * 0.44f, southPaint)

        // Start marker: golden diamond at the target bearing (in the dial frame).
        targetBearingDeg?.let { bearing ->
            canvas.save()
            canvas.rotate(bearing.toFloat(), cx, cy)
            val my = cy - radius * 0.90f
            val r = radius * 0.07f
            diamondPath.rewind()
            diamondPath.moveTo(cx, my - r)
            diamondPath.lineTo(cx + r, my)
            diamondPath.lineTo(cx, my + r)
            diamondPath.lineTo(cx - r, my)
            diamondPath.close()
            canvas.drawPath(diamondPath, targetPaint)
            canvas.restore()
        }

        waypointBearingDeg?.let { bearing ->
            canvas.save()
            canvas.rotate(bearing.toFloat(), cx, cy)
            val my = cy - radius * 0.90f
            val r = radius * 0.055f
            diamondPath.rewind()
            diamondPath.moveTo(cx, my - r)
            diamondPath.lineTo(cx + r, my)
            diamondPath.lineTo(cx, my + r)
            diamondPath.lineTo(cx - r, my)
            diamondPath.close()
            canvas.drawPath(diamondPath, waypointStroke)
            canvas.restore()
        }

        canvas.restore()

        // Fixed top indicator: the direction the phone is facing.
        needlePath.rewind()
        needlePath.moveTo(cx, cy - radius - 6f)
        needlePath.lineTo(cx - radius * 0.06f, cy - radius * 1.12f - 6f)
        needlePath.lineTo(cx + radius * 0.06f, cy - radius * 1.12f - 6f)
        needlePath.close()
        canvas.drawPath(needlePath, indicatorPaint)
    }
}
