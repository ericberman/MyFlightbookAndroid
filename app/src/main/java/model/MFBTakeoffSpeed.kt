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

import java.text.DecimalFormat

object MFBTakeoffSpeed {
    private const val TOSpeedBreak = 50
    private const val TOLandingSpreadHigh = 15
    private const val TOLandingSpreadLow = 10
    private val rgTOSpeeds = intArrayOf(20, 40, 55, 70, 85, 100)
    const val DefaultTakeOffIndex = 3 // 70kts
    private var m_iTakeOffSpeed = DefaultTakeOffIndex

    // get/set the N'th takeoff speed.
    @JvmStatic
    var takeOffSpeedIndex: Int
        get() = m_iTakeOffSpeed
        set(value) {
            if (value >= 0 && value < rgTOSpeeds.size) m_iTakeOffSpeed = value
        }

    /// <summary>
    /// Currently set Take-off speed
    /// </summary>
    @JvmStatic
    val takeOffspeed: Int
        get() = rgTOSpeeds[takeOffSpeedIndex]

    /// <summary>
    /// Currently set Landing speed
    /// </summary>
    @JvmStatic
    val landingSpeed: Int
        get() = takeOffspeed - if (takeOffspeed >= TOSpeedBreak) TOLandingSpreadHigh else TOLandingSpreadLow

    @JvmStatic
    fun getDisplaySpeeds(): ArrayList<String> {
        val df = DecimalFormat("#,###")
        val l = ArrayList<String>()
        for (speed in rgTOSpeeds) l.add(String.format("%skts", df.format(speed.toLong())))
        return l
    }
}