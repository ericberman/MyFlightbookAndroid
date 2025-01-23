/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025 MyFlightbook, LLC

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
package model

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.util.Base64
import androidx.fragment.app.Fragment
import com.myflightbook.android.DlgProgress
import com.myflightbook.android.R
import com.myflightbook.android.webservices.UTCDate.getUTCCalendar
import com.myflightbook.android.webservices.UTCDate.getUTCTimeZone
import kotlinx.datetime.Clock
import java.io.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.todayIn
import kotlinx.datetime.TimeZone
import java.time.ZoneOffset
import java.util.*
import kotlin.math.abs

object MFBUtil {
    @JvmStatic
    // region Alerts
    fun alert(c: Context?, szTitle: String?, szMessage: String?) {
        if (c == null || c is Activity && c.isFinishing) return
        val builder = AlertDialog.Builder(c, R.style.MFBDialog)
        builder.setMessage(szMessage)
        builder.setTitle(szTitle)
        builder.setNegativeButton(c.getString(R.string.lblOK)) { dialog: DialogInterface, _: Int -> dialog.cancel() }
        val alert = builder.create()
        try {
            alert.show()
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun alert(f: Fragment, szTitle: String?, szMessage: String?) {
        alert(f.activity, szTitle, szMessage)
    }

    @JvmStatic
    fun showProgress(a : Context, szMessage: String?): DlgProgress {
        val dlgComment = DlgProgress(a)
        dlgComment.show()
        dlgComment.setText(szMessage)
        return dlgComment
    }

    // endregion
    // region Date utilities
    // Creates a local date that "looks" like the UTC date from which it is derived.
    // I.e., if it is a-b-c d:e UTC, this will be a-b-c d:e PST
    fun localDateFromUTCDate(dt: Date?): Date? {
        // Get the date's d/m/y in UTC
        if (dt == null)
            return null
        var c = getUTCCalendar()
        c.time = dt
        val y = c[Calendar.YEAR]
        val m = c[Calendar.MONTH]
        val d = c[Calendar.DAY_OF_MONTH]

        // Now create a date in the local timezone that looks like the UTC date
        c = GregorianCalendar()
        c[y, m, d, 0, 0] = 0
        return c.time
    }

    // Creates a UTC date that "looks" like the UTC date from which it is derived.
    // I.e., if it is a-b-c d:e PST, this will be a-b-c d:e UTC
    fun getUTCDateFromLocalDate(dt: Date): Date {
        // Get the date's d/m/y in the current locale
        var c = GregorianCalendar()
        c.time = dt
        val y = c[Calendar.YEAR]
        val m = c[Calendar.MONTH]
        val d = c[Calendar.DAY_OF_MONTH]

        // now create a bogus UTC date at midnight in what appears to be the local date
        // this is what we will send across the wire.
        c = getUTCCalendar()
        c[y, m, d, 0, 0] = 0
        return c.time
    }

    // Parses an ISO-formatted string as a UTC time.  Basically just ignores everything after the "T"
    fun utcDateTimeToLocalDate(sz : String) : LocalDate {
        return LocalDate.parse(sz.substringBefore("T"))
    }

    // Creates a localdate that represents the provided date, as expressed in local time
    // I.e., if the date is 2025-05-07T00:00Z, this produces May 6 in Seattle and May 7 in Stockholm
    fun localDateTimeToLocalDate(dt: Date) : LocalDate {
        val localDateTime = dt.toInstant().atZone(ZoneOffset.systemDefault()).toLocalDateTime()
        return LocalDate(localDateTime.year, localDateTime.monthValue, localDateTime.dayOfMonth)
    }

    // creates a UTC date that "looks" like the provided local date, in UTC.
    // I.e., if the local date is May 7 2025, this produces 2025-05-07T00:00Z
    fun localDateToUtcDateTime(dtLocal : LocalDate) : Date {
        val c = getUTCCalendar()
        c[dtLocal.year, dtLocal.month.value - 1, dtLocal.dayOfMonth, 0, 0] = 0
        return c.time
    }

    // Creates a datetime in local time for the specified date that matches the date in the current time zone
    fun localDateToLocalDateTime(dtLocal : LocalDate) : Date {
        return localDateFromUTCDate(localDateToUtcDateTime(dtLocal))!!
    }

    fun localToday() : LocalDate {
        return Clock.System.todayIn(TimeZone.currentSystemDefault())
    }

    fun addCalendarMonths(dt: Date, cMonths: Int): Date {
        val c = GregorianCalendar(getUTCTimeZone)
        c.time = dt
        // Go to the first of the month
        c.add(Calendar.DAY_OF_MONTH, 1 - c[Calendar.DAY_OF_MONTH])
        if (cMonths > 0) {
            c.add(Calendar.MONTH, cMonths + 1)
            c.add(Calendar.DATE, -1)
        } else c.add(Calendar.MONTH, cMonths)
        return c.time
    }

    fun addDays(dt: Date, cDays: Int): Date {
        val c = GregorianCalendar(getUTCTimeZone)
        c.time = dt
        c.add(Calendar.DATE, cDays)
        return c.time
    }

    @JvmStatic
    fun removeSeconds(dt: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = dt
        cal[Calendar.SECOND] = 0
        cal[Calendar.MILLISECOND] = 0
        return cal.time
    }

    @JvmStatic
    fun nowWith0Seconds(): Date {
        return removeSeconds(Date())
    }

    // endregion
    // region Latitude/Longitude strings (for EXIF)
    fun makeLatLongString(dIn: Double): String {
        var d = dIn
        d = abs(d)
        val degrees = d.toInt()
        val remainder = d - degrees
        val minutes = (remainder * 60.0).toInt()
        val seconds = ((remainder * 60.0 - minutes) * 60.0 * 1000.0).toInt()
        return "$degrees/1,$minutes/1,$seconds/1000"
    }

    // endregion
    // region Object serialization/deserialization
    @JvmStatic
    fun serializeToString(o: Serializable?): String {
        try {
            val bos = ByteArrayOutputStream()
            val oos = ObjectOutputStream(bos)
            oos.writeObject(o)
            oos.flush()
            return String(Base64.encode(bos.toByteArray(), Base64.DEFAULT))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    @JvmStatic
    fun <T> deserializeFromString(s: String?): T? {
        if (s.isNullOrEmpty()) return null
        try {
            val rgb = Base64.decode(s, Base64.DEFAULT)
            val bis = ByteArrayInputStream(rgb)
            val ois = ObjectInputStream(bis)
            @Suppress("UNCHECKED_CAST")
            return ois.readObject() as T
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    fun <T> clone(obj: Serializable?): T? {
        val s = serializeToString(obj)
        return if (s.isEmpty()) null else deserializeFromString(s)
    } // endregion

    private val mHash = HashMap<String, Any>()

    fun putCacheForKey(key : String, obj : Any) {
        mHash[key] = obj
    }

    fun getForKey(key: String?) : Any? {
        return if (key != null && mHash.containsKey(key)) mHash[key] else null
    }

    fun removeForKey(key:String?) {
        if (key != null && mHash.containsKey(key))
            mHash.remove(key)
    }
}