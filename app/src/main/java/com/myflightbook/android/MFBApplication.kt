/*
    MyFlightbook for Android - provides native access to MyFlightbook
    pilot's logbook
    Copyright (C) 2026 MyFlightbook, LLC

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.myflightbook.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate

class MFBApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Apply the user's night mode once, before any activity is created.  Calling setDefaultNightMode() later
        // (e.g., in MFBMain.onCreate after super.onCreate) recreates any running activity when the mode changes -
        // which, on a restore after process death, happens in the middle of restoring fragment/ViewPager2 state.
        // MFBMain uses Activity.getPreferences(), which is the shared preferences file named for the activity class.
        val prefs = getSharedPreferences(MFBMain::class.java.simpleName, MODE_PRIVATE)
        // Only follow-system, no, and yes are supported.  Older versions stored the "Automatic" spinner position (0),
        // which AppCompat interprets as the deprecated MODE_NIGHT_AUTO_TIME; treat that (and anything else) as follow-system.
        MFBMain.NightModePref = when (val mode = prefs.getInt(MFBMain.M_KEYS_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) {
            AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES -> mode
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(MFBMain.NightModePref)

        // Draw every activity edge-to-edge on every Android version. Android 15+ (targetSdk 35+) forces
        // edge-to-edge regardless, so this makes older versions match and keeps layout behavior consistent.
        // onActivityCreated runs inside the activity's super.onCreate(), i.e., before its setContentView().
        // Screens keep their content clear of the system bars via padForSystemBars() or fitsSystemWindows.
        // enableEdgeToEdge() builds the window's decor view, which locks in the activity's *current* theme.
        // MFBMain starts with the splash theme and only switches to MFBTheme in installSplashScreen(), after
        // super.onCreate(), so it calls enableEdgeToEdge() itself; doing it here would freeze the splash theme
        // (extra title bar, wrong background).
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is MFBMain) return
                (activity as? ComponentActivity)?.enableEdgeToEdge()
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
