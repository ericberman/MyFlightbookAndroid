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
package model;

import java.text.DecimalFormat;
import java.util.ArrayList;

public class MFBTakeoffSpeed {
	private final static int TOSpeedBreak = 50;
    private final static int TOLandingSpreadHigh = 15;
    private final static int TOLandingSpreadLow = 10;

    private static final int[] rgTOSpeeds = { 20, 40, 55, 70, 85, 100 };

    public final static int DefaultTakeOffIndex = 3; // 70kts

    private static int m_iTakeOffSpeed = DefaultTakeOffIndex;

    // get/set the N'th takeoff speed.
    public static int getTakeOffSpeedIndex() 
    {
        return m_iTakeOffSpeed; 
    }
    
    public static void setTakeOffSpeedIndex(int value)
    {
        if (value >= 0 && value < rgTOSpeeds.length)
            m_iTakeOffSpeed = value;
    }

    /// <summary>
    /// Currently set Take-off speed
    /// </summary>
    public static int getTakeOffspeed()
    { 
        return rgTOSpeeds[getTakeOffSpeedIndex()]; 
    }

    /// <summary>
    /// Currently set Landing speed
    /// </summary>
    public static int getLandingSpeed()
    {
        return getTakeOffspeed() - ((getTakeOffspeed() >= TOSpeedBreak) ? TOLandingSpreadHigh : TOLandingSpreadLow);
    }

    public static ArrayList<String> GetDisplaySpeeds()
    {
    	DecimalFormat df = new DecimalFormat("#,###");
    	ArrayList<String> l = new ArrayList<>();
        for (int speed : rgTOSpeeds)
        	l.add(String.format("%skts", df.format(speed)));
        return l;
    }
}
