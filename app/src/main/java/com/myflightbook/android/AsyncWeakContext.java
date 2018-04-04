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
package com.myflightbook.android;

import android.app.Activity;
import android.content.Context;

import java.lang.ref.WeakReference;

public class AsyncWeakContext<T> {
    private WeakReference<Context> m_context;
    private WeakReference<T> m_callingActivity;

    AsyncWeakContext(Context c, T act) {
        m_context = (c == null) ? null : new WeakReference<>(c);
        m_callingActivity = (act == null) ? null : new WeakReference<>(act);
    }

    public Context getContext() {
        if (m_context == null)
            return null;
        return m_context.get();
    }

    public T getCallingActivity() {
        if (m_callingActivity == null)
            return null;
        T obj = m_callingActivity.get();
        if (obj != null) {
            if (Activity.class.isAssignableFrom(obj.getClass())) {
                if (((Activity)obj).isFinishing())
                    return null;
            }
        }
        return obj;
    }
}
