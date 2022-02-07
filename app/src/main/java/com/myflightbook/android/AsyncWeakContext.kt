/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2018 MyFlightbook, LLC

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
import android.content.Context
import java.lang.ref.WeakReference

internal class AsyncWeakContext<T>(c: Context?, act: T?) {
    private val mContext: WeakReference<Context>? = if (c == null) null else WeakReference(c)
    private val mCallingactivity: WeakReference<T>? = if (act == null) null else WeakReference(act)
    val context: Context?
        get() = mContext?.get()
    val callingActivity: T?
        get() {
            if (mCallingactivity == null) return null
            val obj = mCallingactivity.get()
            if (obj != null) {
                if (Activity::class.java.isAssignableFrom(obj.javaClass)) {
                    if ((obj as Activity).isFinishing) return null
                }
            }
            return obj
        }

}