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

import android.content.ContentValues
import android.database.Cursor
import android.location.Location
import android.util.Log
import com.myflightbook.android.MFBMain
import java.io.BufferedReader
import java.io.StringReader
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class LocSample : LatLong {
    private var id: Long = -1
    @JvmField
    var altitude = 0
    @JvmField
    var speed = 0.0
    @JvmField
    var horizontalError = 0.0
    @JvmField
    var timeStamp = Date()
    private var timeZoneOffset = 0
    @JvmField
    var comment = ""

    // convert back to Meters per Second
    // Convert back to meters
    val location: Location
        get() {
            val l = Location("MFB")
            l.latitude = latitude
            l.longitude = longitude
            l.speed =
                (speed / MFBConstants.MPS_TO_KNOTS).toFloat() // convert back to Meters per Second
            l.altitude = altitude / MFBConstants.METERS_TO_FEET // Convert back to meters
            l.time = timeStamp.time
            l.accuracy = horizontalError.toFloat()
            return l
        }

    private fun fromCursor(c: Cursor) {
        id = c.getLong(c.getColumnIndexOrThrow("_id"))
        latitude = c.getDouble(c.getColumnIndexOrThrow("Lat"))
        longitude = c.getDouble(c.getColumnIndexOrThrow("Lon"))
        altitude = c.getInt(c.getColumnIndexOrThrow("Alt"))
        speed = c.getDouble(c.getColumnIndexOrThrow("Speed"))
        horizontalError = c.getDouble(c.getColumnIndexOrThrow("Error"))
        try {
            val szDate = c.getString(c.getColumnIndexOrThrow("TimeStamp"))
            if (szDate !=  null && szDate.isNotEmpty())
                timeStamp = mUTCFormatter!!.parse(szDate)!!
        } catch (ignored: ParseException) {
        }
        timeZoneOffset = c.getInt(c.getColumnIndexOrThrow("TZOffset"))
        comment = c.getString(c.getColumnIndexOrThrow("Comment"))
    }

    fun toContentValues(cv: ContentValues) {
        if (id >= 0) cv.put("_id", id)
        cv.put("Lat", latitude)
        cv.put("Lon", longitude)
        cv.put("Alt", altitude)
        cv.put("Speed", speed.toInt())
        cv.put("Error", horizontalError)
        cv.put("TimeStamp", mUTCFormatter!!.format(timeStamp))
        cv.put("TZOffset", timeZoneOffset)
        cv.put("Comment", comment)
    }

    private constructor(c: Cursor) : super() {
        fromCursor(c)
    }

    internal constructor(l: Location) : super() {
        latitude = l.latitude
        longitude = l.longitude
        altitude = (l.altitude * MFBConstants.METERS_TO_FEET).toInt()
        speed = l.speed * MFBConstants.MPS_TO_KNOTS
        horizontalError = l.accuracy.toDouble()
        timeStamp.time = l.time
        timeZoneOffset = 0
    }

    internal constructor(l: Location, altitude: Int, speed: Double, dt: Date) : super() {
        latitude = l.latitude
        longitude = l.longitude
        this.altitude = altitude
        this.speed = speed
        timeStamp = dt
        timeZoneOffset = 0
        horizontalError = (MFBConstants.MIN_ACCURACY / 2).toDouble()
    }

    internal constructor(
        lat: Double,
        lon: Double,
        alt: Int,
        speed: Double,
        error: Double,
        szDate: String?
    ) : super() {
        latitude = lat
        longitude = lon
        altitude = alt
        this.speed = speed
        horizontalError = error
        try {
            if (szDate != null && szDate.isNotEmpty())
                timeStamp = mUTCFormatter!!.parse(szDate)!!
        } catch (ignored: ParseException) {
        }
        timeZoneOffset = 0
    }

    companion object {
        private var mDf: SimpleDateFormat? = null
        private val mUTCFormatter: SimpleDateFormat?
            get() {
                if (mDf == null) {
                    mDf = SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.getDefault())
                    mDf!!.timeZone = TimeZone.getTimeZone("UTC")
                }
                return mDf
            }

        @JvmStatic
        fun flightPathFromDB(): Array<LocSample> {
            val al = ArrayList<LocSample>()
            val db = MFBMain.mDBHelper!!.writableDatabase
            try {
                db.query("FlightTrack", null, null, null, null, null, "TimeStamp ASC")
                    .use { c -> while (c.moveToNext()) al.add(LocSample(c)) }
            } catch (e: Exception) {
                Log.e(
                    MFBConstants.LOG_TAG,
                    "Unable to retrieve pending flight telemetry data: " + e.localizedMessage
                )
            }
            return al.toTypedArray()
        }

        @JvmStatic
        fun flightDataFromSamples(rgloc: Array<LocSample>): String {
            if (rgloc.isEmpty()) return ""
            val sb = StringBuilder("LAT,LON,PALT,SPEED,HERROR,DATE,TZOFFSET,COMMENT\r\n")
            for (loc in rgloc) sb.append(
                String.format(
                    Locale.US, "%.8f,%.8f,%d,%.1f,%.1f,%s,%d,%s\r\n",
                    loc.latitude,
                    loc.longitude,
                    loc.altitude,
                    loc.speed,
                    loc.horizontalError,
                    mUTCFormatter!!.format(loc.timeStamp),
                    loc.timeZoneOffset,
                    loc.comment
                )
            )
            return sb.toString()
        }

        @JvmStatic
        fun samplesFromDataString(s: String?): Array<LocSample> {
            if (s == null)
                return arrayOf()
            val r = BufferedReader(StringReader(s))
            val nf = NumberFormat.getInstance(Locale.US)
            val al = ArrayList<LocSample>()
            try {
                var szRow : String
                r.readLine() // skip the first row
                var rgszRow: Array<String?>
                while (r.readLine().also { szRow = it } != null) {
                    rgszRow = szRow.split(",").toTypedArray()
                    val l = LocSample(
                        Objects.requireNonNull(nf.parse(rgszRow[0]!!)).toDouble(),  // lat
                        Objects.requireNonNull(nf.parse(rgszRow[1]!!)).toDouble(),  // lon
                        Objects.requireNonNull(nf.parse(rgszRow[2]!!)).toInt(),  // alt
                        Objects.requireNonNull(nf.parse(rgszRow[3]!!)).toDouble(),  // speed
                        Objects.requireNonNull(nf.parse(rgszRow[4]!!)).toDouble(),  // error
                        rgszRow[5]
                    )
                    al.add(l)
                }
            } catch (ignored: Exception) {
            }
            return al.toTypedArray()
        }
    }
}