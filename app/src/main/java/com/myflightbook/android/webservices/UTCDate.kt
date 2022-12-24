/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2022 MyFlightbook, LLC

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
package com.myflightbook.android.webservices

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.*

object UTCDate {
    @JvmStatic
    val getUTCTimeZone: TimeZone
        get() = TimeZone.getTimeZone("UTC")

    @JvmStatic
    fun getUTCCalendar(): GregorianCalendar {
        return GregorianCalendar(getUTCTimeZone)
    }

    @JvmStatic
    fun getNullDate(): Date {
        val c: Calendar = GregorianCalendar(getUTCTimeZone)
        c[1, 0, 1, 0, 0] = 0
        return c.time
    }

    @JvmStatic
    fun isNullDate(d: Date?): Boolean {
        if (d == null)
            return true
        val c: Calendar = GregorianCalendar(getUTCTimeZone)
        c.time = d
        return c[Calendar.YEAR] < 100
    }

    @JvmStatic
    fun formatDate(fLocal: Boolean, d: Date, c: Context?): String {
        return if (fLocal) {
            val dfDate = DateFormat.getDateFormat(c)
            val dfTime = DateFormat.getTimeFormat(c)
            val g = GregorianCalendar()
            dfDate.calendar = g
            dfTime.calendar = g
            val sdf = SimpleDateFormat("zzz", Locale.getDefault())
            sdf.calendar = g
            String.format("%s %s (%s)", dfDate.format(d), dfTime.format(d), sdf.format(d))
        } else {
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm '(UTC)'", Locale.getDefault())
            sdf.calendar = getUTCCalendar()
            sdf.format(d)
        }
    }
}