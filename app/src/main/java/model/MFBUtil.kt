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
package model

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.util.Base64
import android.util.Log
import androidx.fragment.app.Fragment
import com.myflightbook.android.R
import com.myflightbook.android.webservices.UTCDate.getUTCCalendar
import com.myflightbook.android.webservices.UTCDate.getUTCTimeZone
import java.io.*
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
    //endregion
    // region Progress utilities
    fun showProgress(c: Context?, szMessage: String?): ProgressDialog? {
        var pd: ProgressDialog? = null
        try {
            // we do this in a try/catch block because we can get a windowleaked crash if we rotate the device
            // in progress.  Doesn't seem to affect the overall worker thread, fortunately, but we can just
            // return the bogus progress dialog if it becomes bogus.
            // do a search on "View not attached to window manager" for details.
            pd = ProgressDialog(c, R.style.MFBProgressStyle)
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            pd.isIndeterminate
            pd.setMessage(szMessage)
            pd.show()
        } catch (ex: Exception) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex))
        }
        return pd
    }

    @JvmStatic
    fun showProgress(f: Fragment, szMessage: String?): ProgressDialog? {
        return showProgress(f.activity, szMessage)
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
        if (s == null || s.isEmpty()) return null
        try {
            val rgb = Base64.decode(s, Base64.DEFAULT)
            val bis = ByteArrayInputStream(rgb)
            val ois = ObjectInputStream(bis)
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
        return if (key != null && mHash.containsKey(key)) mHash[key] else null;
    }

    fun removeForKey(key:String?) {
        if (key != null && mHash.containsKey(key))
            mHash.remove(key)
    }
}