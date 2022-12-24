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

import kotlin.math.*

internal object Solar {
    /// <summary>
    /// Calculate the UTC Time of sunrise for the given day at the given location on earth
    /// </summary>
    /// <param name="JD">Julian day</param>
    /// <param name="latitude">Latitude of observer</param>
    /// <param name="longitude">Longitude of observer</param>
    /// <returns>Time in minutes from zero Z</returns>
    fun calcSunriseUTC(JD: Double, latitude: Double, longitude: Double): Double {
        val t = calcTimeJulianCent(JD)

        // *** Find the time of solar noon at the location, and use
        //     that declination. This is better than start of the
        //     Julian day
        val noonmin = calcSolNoonUTC(t, longitude)
        val tnoon = calcTimeJulianCent(JD + noonmin / 1440.0)

        // *** First pass to approximate sunrise (using solar noon)
        var eqTime = calcEquationOfTime(tnoon)
        var solarDec = calcSunDeclination(tnoon)
        var hourAngle = calcHourAngleSunrise(latitude, solarDec)
        var delta = longitude - radToDeg(hourAngle)
        var timeDiff = 4 * delta // in minutes of time
        var timeUTC = 720 + timeDiff - eqTime // in minutes

        // *** Second pass includes fractional jday in gamma calc
        val newt = calcTimeJulianCent(calcJDFromJulianCent(t) + timeUTC / 1440.0)
        eqTime = calcEquationOfTime(newt)
        solarDec = calcSunDeclination(newt)
        hourAngle = calcHourAngleSunrise(latitude, solarDec)
        delta = longitude - radToDeg(hourAngle)
        timeDiff = 4 * delta
        timeUTC = 720 + timeDiff - eqTime // in minutes
        return timeUTC
    }

    /// <summary>
    /// calculate the Universal Coordinated Time (UTC) of sunset for the given day at the given location on earth
    /// </summary>
    /// <param name="JD">Julian Day</param>
    /// <param name="latitude">latitude of observer in degrees</param>
    /// <param name="longitude">longitude of observer in degrees</param>
    /// <returns>time in minutes from zero Z</returns>
    fun calcSunsetUTC(JD: Double, latitude: Double, longitude: Double): Double {
        val t = calcTimeJulianCent(JD)

        // *** Find the time of solar noon at the location, and use
        //     that declination. This is better than start of the
        //     Julian day
        val noonmin = calcSolNoonUTC(t, longitude)
        val tnoon = calcTimeJulianCent(JD + noonmin / 1440.0)

        // First calculates sunrise and approx length of day
        var eqTime = calcEquationOfTime(tnoon)
        var solarDec = calcSunDeclination(tnoon)
        var hourAngle = calcHourAngleSunset(latitude, solarDec)
        var delta = longitude - radToDeg(hourAngle)
        var timeDiff = 4 * delta
        var timeUTC = 720 + timeDiff - eqTime

        // first pass used to include fractional day in gamma calc
        val newt = calcTimeJulianCent(calcJDFromJulianCent(t) + timeUTC / 1440.0)
        eqTime = calcEquationOfTime(newt)
        solarDec = calcSunDeclination(newt)
        hourAngle = calcHourAngleSunset(latitude, solarDec)
        delta = longitude - radToDeg(hourAngle)
        timeDiff = 4 * delta
        timeUTC = 720 + timeDiff - eqTime // in minutes
        return timeUTC
    }

    /// <summary>
    /// calculate the hour angle of the sun at sunrise for the latitude
    /// </summary>
    /// <param name="lat">Latitude of observer in degrees</param>
    /// <param name="solarDec">declination angle of sun in degrees</param>
    /// <returns>hour angle of sunrise in radians</returns>
    private fun calcHourAngleSunrise(lat: Double, solarDec: Double): Double {
        val latRad = degToRad(lat)
        val sdRad = degToRad(solarDec)
        return acos(
            cos(degToRad(90.833)) / (cos(latRad) * cos(sdRad)) - tan(
                latRad
            ) * tan(sdRad)
        )
    }

    /// <summary>
    /// calculate the hour angle of the sun at sunset for the latitude
    /// </summary>
    /// <param name="lat">latitude of observer in degrees</param>
    /// <param name="solarDec">declination angle of sun in degrees</param>
    /// <returns>hour angle of sunset in radians</returns>
    private fun calcHourAngleSunset(lat: Double, solarDec: Double): Double {
        val latRad = degToRad(lat)
        val sdRad = degToRad(solarDec)

        // double HAarg = (Math.cos(degToRad(90.833)) / (Math.cos(latRad) * Math.cos(sdRad)) - Math.tan(latRad) * Math.tan(sdRad));
        val ha = acos(
            cos(degToRad(90.833)) / (cos(latRad) * cos(sdRad)) - tan(latRad) * tan(
                sdRad
            )
        )
        return -ha // in radians
    }

    /// <summary>
    /// Solar angle - reverse engineered from http://www.usc.edu/dept-00/dept/architecture/mbs/tools/thermal/sun_calc.html
    /// </summary>
    /// <param name="lat">The Latitude, in degrees</param>
    /// <param name="lon">The Longitude, in degrees</param>
    /// <param name="minutes">Minutes into the day (UTC)</param>
    /// <param name="JD">The Julian Date</param>
    /// <returns>Angle of the sun, in degrees</returns>
    fun calcSolarAngle(lat: Double, lon: Double, JD: Double, minutes: Double): Double {
        val julianCentury = calcTimeJulianCent(JD + minutes / 1440.0)
        val sunDeclinationRad = degToRad(calcSunDeclination(julianCentury))
        val latRad = degToRad(lat)
        val eqOfTime = calcEquationOfTime(julianCentury)
        val trueSolarTimeMin = (minutes + eqOfTime + 4 * lon) % 1440
        val hourAngleDeg =
            if (trueSolarTimeMin / 4 < 0) trueSolarTimeMin / 4 + 180 else trueSolarTimeMin / 4 - 180
        val zenith = radToDeg(
            acos(
                sin(latRad) * sin(sunDeclinationRad) + cos(latRad) * cos(
                    sunDeclinationRad
                ) * cos(
                    degToRad(hourAngleDeg)
                )
            )
        )
        val solarElevation = 90 - zenith
        val atmRefractionDeg: Double =
            if (solarElevation > 85) 0.0 else (when {
                solarElevation > 5 -> 58.1 / tan(
                    degToRad(solarElevation)
                ) - 0.07 / tan(degToRad(solarElevation)).pow(3.0) + 0.000086 / tan(
                    degToRad(solarElevation)
                ).pow(5.0)
                solarElevation > -0.575 -> 1735 + solarElevation * (-518.2 + solarElevation * (103.4 + solarElevation * (-12.79 + solarElevation * 0.711)))
                else -> -20.772 / tan(
                    degToRad(solarElevation)
                )
            }) / 3600
        return solarElevation + atmRefractionDeg
    }

    /// <summary>
    /// calculate the Universal Coordinated Time (UTC) of solar noon for the given day at the given location on earth
    /// </summary>
    /// <param name="t">number of Julian centuries since J2000.0</param>
    /// <param name="longitude">longitude of observer in degrees</param>
    /// <returns>time in minutes from zero Z</returns>
    private fun calcSolNoonUTC(t: Double, longitude: Double): Double {
        // First pass uses approximate solar noon to calculate eqtime
        val tnoon = calcTimeJulianCent(calcJDFromJulianCent(t) + longitude / 360.0)
        var eqTime = calcEquationOfTime(tnoon)
        var solNoonUTC = 720 + longitude * 4 - eqTime // min
        val newt = calcTimeJulianCent(calcJDFromJulianCent(t) - 0.5 + solNoonUTC / 1440.0)
        eqTime = calcEquationOfTime(newt)
        // double solarNoonDec = calcSunDeclination(newt);
        solNoonUTC = 720 + longitude * 4 - eqTime // min
        return solNoonUTC
    }

    //***********************************************************************/
    //***********************************************************************/
    //*												*/
    //*This section contains subroutines used in calculating solar position */
    //*												*/
    //***********************************************************************/
    //***********************************************************************/
    /// <summary>
    /// Convert radian angle to degrees
    /// </summary>
    /// <param name="angleRad">Angle, in radians</param>
    /// <returns>Angle, in degres</returns>
    private fun radToDeg(angleRad: Double): Double {
        return 180.0 * angleRad / Math.PI
    }

    // Convert degree angle to radians
    /// <summary>
    /// Convert degrees to radians
    /// </summary>
    /// <param name="angleDeg">Angle, in degrees</param>
    /// <returns>Angle, in Radians</returns>
    private fun degToRad(angleDeg: Double): Double {
        return Math.PI * angleDeg / 180.0
    }

    /// <summary>
    /// Julian day from calendar day
    /// </summary>
    /// <param name="year">Year</param>
    /// <param name="month">Month (1-12)</param>
    /// <param name="day">Day (1-31)</param>
    /// <returns>The Julian day corresponding to the date.  Number is returned for start of day.  Fractional days should be added later.</returns>
    fun calcJD(yearIn: Int, monthIn: Int, day: Int): Double {
        var year = yearIn
        var month = monthIn
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0).toInt()
        val b = 2 - a + floor(a / 4.0).toInt()
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5
    }

    /// <summary>
    /// Convert Julian Day to centuries since J2000.0
    /// </summary>
    /// <param name="jd">Julian day to convert</param>
    /// <returns>The T value corresponding to the Julian Day</returns>
    private fun calcTimeJulianCent(jd: Double): Double {
        return (jd - 2451545.0) / 36525.0
    }

    /// <summary>
    /// convert centuries since J2000.0 to Julian Day
    /// </summary>
    /// <param name="t">number of Julian centuries since J2000.0</param>
    /// <returns>the Julian Day corresponding to the t value</returns>
    private fun calcJDFromJulianCent(t: Double): Double {
        return t * 36525.0 + 2451545.0
    }

    /// <summary>
    /// calculate the Geometric Mean Longitude of the Sun
    /// </summary>
    /// <param name="t">number of Julian centuries since J2000.0</param>
    /// <returns>the Geometric Mean Longitude of the Sun in degrees</returns>
    private fun calcGeomMeanLongSun(t: Double): Double {
        var l0 = 280.46646 + t * (36000.76983 + 0.0003032 * t)
        while (l0 > 360.0) l0 -= 360.0
        while (l0 < 0.0) l0 += 360.0
        return l0 // in degrees
    }

    /// <summary>
    /// calculate the Geometric Mean Anomaly of the Sun
    /// </summary>
    /// <param name="t">number of Julian centuries since J2000.0</param>
    /// <returns>the Geometric Mean Anomaly of the Sun in degrees</returns>
    private fun calcGeomMeanAnomalySun(t: Double): Double {
        return 357.52911 + t * (35999.05029 - 0.0001537 * t) // in degrees
    }

    /// <summary>
    /// calculate the eccentricity of earth's orbit
    /// </summary>
    /// <param name="t">number of Julian centuries since J2000.0</param>
    /// <returns>the unitless eccentricity</returns>
    private fun calcEccentricityEarthOrbit(t: Double): Double {
        return 0.016708634 - t * (0.000042037 + 0.0000001267 * t) // unitless
    }

    /// <summary>
    /// calculate the equation of center for the sun
    /// </summary>
    /// <param name="t">number of Julian centuries since J2000.0</param>
    /// <returns>in degrees</returns>
    private fun calcSunEqOfCenter(t: Double): Double {
        val m = calcGeomMeanAnomalySun(t)
        val mrad = degToRad(m)
        val sinm = sin(mrad)
        val sin2m = sin(mrad + mrad)
        val sin3m = sin(mrad + mrad + mrad)
        return sinm * (1.914602 - t * (0.004817 + 0.000014 * t)) + sin2m * (0.019993 - 0.000101 * t) + sin3m * 0.000289 // in degrees
    }

    /// <summary>
    /// calculate the true longitude of the sun
    /// </summary>
    /// <param name="t">number of Julian centuries since J2000.0</param>
    /// <returns>sun's true longitude in degrees</returns>
    private fun calcSunTrueLong(t: Double): Double {
        val l0 = calcGeomMeanLongSun(t)
        val c = calcSunEqOfCenter(t)
        return l0 + c // in degrees
    }

    /// <summary>
    /// calculate the true anamoly of the sun
    /// </summary>
    /// <param name="t">number of Julian centuries since J2000.</param>
    /// <returns>sun's true anamoly in degrees</returns>
    private fun calcSunTrueAnomaly(t: Double): Double {
        val m = calcGeomMeanAnomalySun(t)
        val c = calcSunEqOfCenter(t)
        return m + c // in degrees
    }

    /// <summary>
    /// calculate the distance to the sun in AU
    /// </summary>
    /// <param name="t"> t : number of Julian centuries since J2000.0</param>
    /// <returns>sun radius vector in AUs</returns>
    @Suppress("UNUSED")
    fun calcSunRadVector(t: Double): Double {
        val v = calcSunTrueAnomaly(t)
        val e = calcEccentricityEarthOrbit(t)
        return 1.000001018 * (1 - e * e) / (1 + e * cos(degToRad(v))) // in AUs
    }

    /// <summary>
    /// calculate the apparent longitude of the sun
    /// </summary>
    /// <param name="t"> t : number of Julian centuries since J2000.0</param>
    /// <returns>sun's apparent longitude in degrees</returns>
    private fun calcSunApparentLong(t: Double): Double {
        val o = calcSunTrueLong(t)
        val omega = 125.04 - 1934.136 * t
        return o - 0.00569 - 0.00478 * sin(degToRad(omega)) // in degrees
    }

    /// <summary>
    /// calculate the mean obliquity of the ecliptic
    /// </summary>
    /// <param name="t"> t : number of Julian centuries since J2000.0</param>
    /// <returns>mean obliquity in degrees</returns>
    private fun calcMeanObliquityOfEcliptic(t: Double): Double {
        val seconds = 21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))
        return 23.0 + (26.0 + seconds / 60.0) / 60.0 // in degrees
    }

    /// <summary>
    /// calculate the corrected obliquity of the ecliptic
    /// </summary>
    /// <param name="t"> t : number of Julian centuries since J2000.0</param>
    /// <returns>corrected obliquity in degrees</returns>
    private fun calcObliquityCorrection(t: Double): Double {
        val e0 = calcMeanObliquityOfEcliptic(t)
        val omega = 125.04 - 1934.136 * t
        return e0 + 0.00256 * cos(degToRad(omega)) // in degrees
    }

    /// <summary>
    /// calculate the right ascension of the sun
    /// </summary>
    /// <param name="t"> t : number of Julian centuries since J2000.0</param>
    /// <returns>sun's right ascension in degrees</returns>
    @Suppress("UNUSED")
    fun calcSunRtAscension(t: Double): Double {
        val e = calcObliquityCorrection(t)
        val lambda = calcSunApparentLong(t)
        val tananum = cos(degToRad(e)) * sin(degToRad(lambda))
        val tanadenom = cos(degToRad(lambda))
        return radToDeg(atan2(tananum, tanadenom)) // in degrees
    }

    /// <summary>
    /// calculate the declination of the sun
    /// </summary>
    /// <param name="t"> t : number of Julian centuries since J2000.0</param>
    /// <returns>sun's declination in degrees</returns>
    private fun calcSunDeclination(t: Double): Double {
        val e = calcObliquityCorrection(t)
        val lambda = calcSunApparentLong(t)
        val sint = sin(degToRad(e)) * sin(degToRad(lambda))
        return radToDeg(asin(sint)) // in degrees
    }

    /// <summary>
    /// calculate the difference between true solar time and mean solar time
    /// </summary>
    /// <param name="t">number of Julian centuries since J2000.0</param>
    /// <returns>equation of time in minutes of time</returns>
    private fun calcEquationOfTime(t: Double): Double {
        val epsilon = calcObliquityCorrection(t)
        val l0 = calcGeomMeanLongSun(t)
        val e = calcEccentricityEarthOrbit(t)
        val m = calcGeomMeanAnomalySun(t)
        var y = tan(degToRad(epsilon) / 2.0)
        y *= y
        val sin2l0 = sin(2.0 * degToRad(l0))
        val sinm = sin(degToRad(m))
        val cos2l0 = cos(2.0 * degToRad(l0))
        val sin4l0 = sin(4.0 * degToRad(l0))
        val sin2m = sin(2.0 * degToRad(m))
        val eqtime =
            y * sin2l0 - 2.0 * e * sinm + 4.0 * e * y * sinm * cos2l0 - 0.5 * y * y * sin4l0 - 1.25 * e * e * sin2m
        return radToDeg(eqtime) * 4.0 // in minutes of time
    }
}