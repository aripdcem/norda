package com.aripd.norda

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.aripd.norda.core.sun.NightPolicy
import com.aripd.norda.storage.ActivityDao
import com.aripd.norda.storage.AppDatabase
import com.aripd.norda.tracking.TrackingService

/**
 * Night mode (docs/MVP.md, 3.7): the red low-light filter over every screen.
 *
 * The filter is a single view on top of the window's decor view that
 * multiplies whatever lies beneath it by a dark red: white turns red, black
 * stays black, the green and blue channels all but disappear. Dark-adapted
 * eyes keep their adaptation, and no screen needs a second palette — the map
 * tiles, the compass dial and the buttons are drawn exactly as by day and
 * pass through the filter. The view is not clickable, so touches fall
 * through to the screen beneath it.
 *
 * Every activity is handed to this object by the application's lifecycle
 * callbacks (`NordaApp`): the decision is made on each resume, and re-made
 * once a minute while a screen stays open, so dusk falling during a long
 * recording tints the screen without anyone having to leave it.
 *
 * Automatic mode needs a rough position for the sun's altitude — a hundred
 * kilometres either way moves dusk by minutes. Sources, in order: the running
 * recording's last point, the system's last known location, the last point
 * ever recorded. Two things stay outside the filter: dialogs, which live in
 * their own windows, and the system status bar, which SystemUI draws over the
 * app. Both are accepted for now.
 */
object NightMode {

    /** The multiplier: whatever lies beneath is scaled into this red. */
    val TINT = 0xFFC81E00.toInt()

    /** How often an open screen re-evaluates the sun. */
    const val RECHECK_MILLIS = 60_000L

    private const val KEY_MODE = "night_mode"
    private const val OVERLAY_TAG = "night_overlay"

    private val handler = Handler(Looper.getMainLooper())
    private var recheck: Runnable? = null

    /** Last position good enough for the sun; kept for the life of the process. */
    @Volatile
    private var cachedPosition: Pair<Double, Double>? = null

    fun mode(context: Context): NightPolicy.Mode =
        NightPolicy.Mode.parse(prefs(context).getString(KEY_MODE, null))

    fun setMode(context: Context, mode: NightPolicy.Mode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    /** Called by `NordaApp` on every activity resume. */
    fun onResume(activity: Activity) {
        cancelRecheck()
        apply(activity)
        val runnable = object : Runnable {
            override fun run() {
                apply(activity)
                handler.postDelayed(this, RECHECK_MILLIS)
            }
        }
        recheck = runnable
        handler.postDelayed(runnable, RECHECK_MILLIS)
    }

    /** Called by `NordaApp` on every activity pause: nothing ticks in the background. */
    fun onPause() {
        cancelRecheck()
    }

    /** Puts the filter on, or takes it off, according to the mode and the sun. */
    fun apply(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        val existing = decor.findViewWithTag<View>(OVERLAY_TAG)
        if (tint(activity)) {
            if (existing == null) {
                decor.addView(
                    Overlay(activity),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } else if (decor.indexOfChild(existing) != decor.childCount - 1) {
                // Anything the window added later has to stay under the filter.
                existing.bringToFront()
            }
        } else if (existing != null) {
            decor.removeView(existing)
        }
    }

    private fun tint(context: Context): Boolean {
        val mode = mode(context)
        // The position is only looked up when the decision depends on it.
        val position = if (mode == NightPolicy.Mode.AUTO) position(context) else null
        return NightPolicy.tint(mode, position?.first, position?.second, System.currentTimeMillis())
    }

    private fun position(context: Context): Pair<Double, Double>? {
        TrackingService.session?.points?.lastOrNull()?.let {
            return remember(it.latitude to it.longitude)
        }
        lastKnownFromSystem(context)?.let { return remember(it) }
        cachedPosition?.let { return it }
        return ActivityDao(AppDatabase.get(context)).lastKnownPosition()?.let { remember(it) }
    }

    private fun remember(position: Pair<Double, Double>): Pair<Double, Double> {
        cachedPosition = position
        return position
    }

    private fun lastKnownFromSystem(context: Context): Pair<Double, Double>? {
        // The permission check is deliberately inline: lint's MissingPermission
        // flow analysis cannot see inside a helper function (the repo's pattern).
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        return try {
            manager.allProviders.asSequence()
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { it.latitude to it.longitude }
        } catch (ignored: SecurityException) {
            null
        }
    }

    private fun cancelRecheck() {
        recheck?.let { handler.removeCallbacks(it) }
        recheck = null
    }

    private fun prefs(context: Context) = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** The filter itself: one window-sized rectangle, multiplied onto the screen. */
    private class Overlay(context: Context) : View(context) {

        private val paint = Paint().apply {
            color = TINT
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }

        init {
            tag = OVERLAY_TAG
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }
}
