package com.aripd.norda

import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * targetSdk 35: kenardan kenara çizim zorunlu; sistem çubuklarının boşluğunu
 * pencere değil uygulama bırakır. Her ekranın kök görünümüne uygulanır.
 */
object Insets {

    fun apply(root: View) {
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
                @Suppress("DEPRECATION")
                insets.consumeSystemWindowInsets()
            }
        }
    }
}
