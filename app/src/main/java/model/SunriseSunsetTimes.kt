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

import com.myflightbook.android.webservices.UTCDate.getNullDate
import kotlin.Throws
import java.lang.Exception
import java.util.*

class SunriseSunsetTimes internal constructor(
    val Date: Date,
    latitude: Double,
    longitude: Double,
    nightFlightOffset: Int
) {
    @JvmField
    var sunrise: Date
    @JvmField
    var sunset: Date
    val latitude: Double
    val longitude: Double

    // True if sun is more than 6 degrees below the horizon.
    @JvmField
    var isCivilNight: Boolean? = null

    // True if between sunset and sunrise
    @JvmField
    var isNight: Boolean? = null

    // True if between 1-hour (or offset specified by NightLandingOffset) after sunset and 1-hour before sunrise
    @JvmField
    var isFAANight: Boolean? = null

    // True if between Sunset + NightFlightOffset and Sunrise - NightFlightOffset
    @JvmField
    var isWithinNightOffset: Boolean? = null

    // The offset from sunrise/sunset (in minutes) needed for night currency (i.e., in computing IsFAANight
    // 1 hour by default (i.e., landings need to be between sunset + 1 hour and sunrise - 1 hour to count
    private val nightLandingOffset: Int

    // The offset from sunrise/sunset (in minutes) needed for night flight, if that's how night flight is computed
    // Default is 0.0, since default for night flight is IsCivilNight
    private val nightFlightOffset: Int

    constructor(dt: Date, latitude: Double, longitude: Double) : this(dt, latitude, longitude, 0)

    /// <summary>
    /// Returns the UTC time for the minutes into the day.  Note that it could be a day forward or backward from the requested day!!!
    /// </summary>
    /// <param name="dt">Requested day (only m/d/y matter)</param>
    /// <param name="minutes"></param>
    /// <returns></returns>
    private fun minutesToDate(dt: Date, minutes: Double): Date {
        val cal: Calendar = GregorianCalendar(TimeZone.getTimeZone("GMT"))
        cal.timeInMillis = dt.time

        // reset to the start of the day
        cal[cal[Calendar.YEAR], cal[Calendar.MONTH], cal[Calendar.DAY_OF_MONTH], 0, 0] = 0
        val l = cal.timeInMillis
        val milisIntoDay = (minutes * 60 * 1000).toLong()
        cal.timeInMillis = l + milisIntoDay
        return cal.time
    }

    private fun addMillis(dt: Date, millis: Long): Date {
        return Date(dt.time + millis)
    }

    private fun addHours(dt: Date, hours: Long): Date {
        return addMillis(dt, hours * 3600 * 1000)
    }

    private fun addMinutes(dt: Date, minutes: Long): Date {
        return addMillis(dt, minutes * 60 * 1000)
    }

    private fun addDays(dt: Date, days: Long): Date {
        return addHours(dt, days * 24)
    }

    //Return JD from a UTC date
    private fun getJDFromDate(dt: Date): Double {
        val cal: Calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = dt.time
        val day = cal[Calendar.DAY_OF_MONTH]
        val month = cal[Calendar.MONTH] + 1
        val year = cal[Calendar.YEAR]
        return Solar.calcJD(year, month, day)
    }

    /// <summary>
    /// Returns the sunrise/sunset times at the given location on the specified day
    /// </summary>
    /// <param name="dt">The requested date/time, utc.  Day/night will be computed based on the time</param>
    @Throws(Exception::class)
    private fun computeTimesAtLocation(dt: Date) {
        if (latitude > 90 || latitude < -90) throw Exception("Bad latitude")
        if (longitude > 180 || longitude < -180) throw Exception("Bad longitude")
        var jd = getJDFromDate(dt)
        val cal: Calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = dt.time
        val solarAngle = Solar.calcSolarAngle(
            latitude,
            longitude,
            jd,
            (cal[Calendar.HOUR_OF_DAY] * 60 + cal[Calendar.MINUTE]).toDouble()
        )
        isCivilNight = solarAngle <= -6.0
        var nosunrise = false
        val riseTimeGMT = Solar.calcSunriseUTC(jd, latitude, -longitude)
        if (java.lang.Double.isNaN(riseTimeGMT)) nosunrise = true

        // Calculate sunset for this date
        // if no sunset is found, set flag nosunset
        var nosunset = false
        val setTimeGMT = Solar.calcSunsetUTC(jd, latitude, -longitude)
        if (java.lang.Double.isNaN(setTimeGMT)) nosunset = true

        // we now know the UTC # of minutes for each. Return the UTC sunrise/sunset times
        if (!nosunrise) sunrise = minutesToDate(dt, riseTimeGMT)
        if (!nosunset) sunset = minutesToDate(dt, setTimeGMT)

        // Update daytime/nighttime
        // 3 possible scenarios:
        // (a) time is between sunrise/sunset as computed - it's daytime or FAA daytime.
        // (b) time is after the sunset - figure out the next sunrise and compare to that
        // (c) time is before sunrise - figure out the previous sunset and compare to that
        isNight = isCivilNight
        isFAANight = false
        isWithinNightOffset = false
        if (dt in sunrise..sunset) {
            // between sunrise and sunset - it's daytime no matter how you slice it; use default values (set above)
        } else if (sunset < dt) {
            // get the next sunrise.  It is night if the time is between sunset and the next sunrise
            val dtTomorrow = addDays(dt, 1)
            jd = getJDFromDate(dtTomorrow)
            val nextSunrise = Solar.calcSunriseUTC(jd, latitude, -longitude)
            if (!java.lang.Double.isNaN(nextSunrise)) {
                val dtNextSunrise = minutesToDate(dtTomorrow, nextSunrise)
                isNight =
                    dtNextSunrise > dt // we've already determined that we're after sunset, we just need to be before sunrise
                isFAANight = addMinutes(
                    sunset,
                    nightLandingOffset.toLong()
                ) <= dt && addMinutes(
                    dtNextSunrise,
                    -nightLandingOffset.toLong()
                ) >= dt
                isWithinNightOffset =
                    addMinutes(sunset, nightFlightOffset.toLong()) <= dt && addMinutes(
                        dtNextSunrise,
                        -nightFlightOffset.toLong()
                    ) >= dt
            }
        } else if (sunrise > dt) {
            // get the previous sunset.  It is night if the time is between that sunset and the sunrise
            val dtYesterday = addDays(dt, -1)
            jd = getJDFromDate(dtYesterday)
            val prevSunset = Solar.calcSunsetUTC(jd, latitude, -longitude)
            if (!java.lang.Double.isNaN(prevSunset)) {
                val dtPrevSunset = minutesToDate(dtYesterday, prevSunset)
                isNight =
                    dtPrevSunset < dt // we've already determined that we're before sunrise, we just need to be after sunset.
                isFAANight = addMinutes(
                    dtPrevSunset,
                    nightLandingOffset.toLong()
                ) <= dt && addMinutes(sunrise, -nightLandingOffset.toLong()) >= dt
                isWithinNightOffset = addMinutes(
                    dtPrevSunset,
                    nightFlightOffset.toLong()
                ) <= dt && addMinutes(sunrise, -nightFlightOffset.toLong()) >= dt
            }
        }
    }

    init {
        sunset = getNullDate()
        sunrise = sunset
        this.latitude = latitude
        this.longitude = longitude
        nightLandingOffset = 60
        this.nightFlightOffset = nightFlightOffset
        try {
            computeTimesAtLocation(Date)
        } catch (ignored: Exception) {
        }
    }
}