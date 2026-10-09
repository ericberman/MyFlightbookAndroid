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

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Edge-to-edge support.
 *
 * Every activity is drawn edge-to-edge on every Android version (see MFBApplication, which calls
 * enableEdgeToEdge() for each activity). Android 15+ forces this anyway for apps targeting SDK 35+;
 * doing it everywhere means older devices behave the same way instead of differently.
 *
 * Call this on a screen's root view so its content stays clear of the status bar, the navigation bar
 * (bottom in portrait, side in landscape with 3-button navigation), and display cutouts.
 * The view's own XML padding is preserved. The keyboard (IME) is deliberately not handled here;
 * screens that need it handle it themselves (e.g., ActNewFlight).
 *
 * Layouts whose root uses android:fitsSystemWindows="true" already get this behavior and don't need it.
 */
fun View.padForSystemBars() {
    val left = paddingLeft
    val top = paddingTop
    val right = paddingRight
    val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
        insets
    }
}
