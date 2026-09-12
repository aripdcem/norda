package com.aripd.norda

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * The application class has one job: hand every screen to `NightMode`. The
 * red filter (docs/MVP.md, 3.7) is decided on each resume, so no individual
 * screen has to know that it exists.
 */
class NordaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = NightMode.onResume(activity)
            override fun onActivityPaused(activity: Activity) = NightMode.onPause()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
