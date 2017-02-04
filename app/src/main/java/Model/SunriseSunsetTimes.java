/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017 MyFlightbook, LLC

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
package Model;
import com.myflightbook.android.WebServices.UTCDate;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class SunriseSunsetTimes {
    public Date Sunrise;
    public Date Sunset;
    public double Latitude;
    public double Longitude;
    public Date Date;
    public double SolarAngle;

    // True if sun is more than 6 degrees below the horizon.
    public Boolean IsCivilNight;
    
    // True if between sunset and sunrise
    public Boolean IsNight;

    // True if between 1-hour after sunset and 1-hour before sunrise
    public Boolean IsFAANight;
    
    public SunriseSunsetTimes()
    {
        Date = Sunrise = Sunset = UTCDate.NullDate();
        Latitude = Longitude = 0.0;
    }

    public SunriseSunsetTimes(Date dt, double latitude, double longitude)
    {
        Date = dt;
        Sunrise = Sunset = UTCDate.NullDate();
        Latitude = latitude;
        Longitude = longitude;
        try
        {
        ComputeTimesAtLocation(dt);
        }
        catch (Exception ex)
        {}
    }

    /// <summary>
    /// Returns the UTC time for the minutes into the day.  Note that it could be a day forward or backward from the requested day!!!
    /// </summary>
    /// <param name="dt">Requested day (only m/d/y matter)</param>
    /// <param name="minutes"></param>
    /// <returns></returns>
    private Date MinutesToDate(Date dt, double minutes)
    {
    	Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
    	cal.setTimeInMillis(dt.getTime());
    	
    	// reset to the start of the day
    	cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
    	long l = cal.getTimeInMillis();
    	        
        long milisIntoDay = (long) (minutes * 60 * 1000);
        cal.setTimeInMillis(l + milisIntoDay);
        
        return cal.getTime();
    }
    
    private Date AddMillis(Date dt, long millis)
    {
    	return new Date(dt.getTime() + millis);
    }
    
    private Date AddHours(Date dt, int hours)
    {
    	return AddMillis(dt, hours * 3600 * 1000);
    }
    
    private Date AddDays(Date dt, int days)
    {
    	return AddHours(dt, days * 24);
    }
    
    //Return JD from a UTC date
    private double JDFromDate(Date dt)
    {
    	Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    	cal.setTimeInMillis(dt.getTime());
    	int day = cal.get(Calendar.DAY_OF_MONTH);
    	int month = cal.get(Calendar.MONTH) + 1;
    	int year = cal.get(Calendar.YEAR);
    	return Solar.calcJD(year, month, day);
    }

    /// <summary>
    /// Returns the sunrise/sunset times at the given location on the specified day
    /// </summary>
    /// <param name="dt">The requested date/time, utc.  Day/night will be computed based on the time</param>
    public void ComputeTimesAtLocation(Date dt) throws Exception
    {
        if (Latitude > 90 || Latitude < -90)
            throw new Exception("Bad latitude");
        if (Longitude > 180 || Longitude < -180)
            throw new Exception("Bad longitude");

        double JD = JDFromDate(dt);

        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(dt.getTime());
        SolarAngle = Solar.calcSolarAngle(Latitude, Longitude, JD, cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE));
        IsCivilNight = SolarAngle <= -6.0;

        Boolean nosunrise = false;
        double riseTimeGMT = Solar.calcSunriseUTC(JD, Latitude, -Longitude);
        if (Double.isNaN(riseTimeGMT))
            nosunrise = true;

        // Calculate sunset for this date
        // if no sunset is found, set flag nosunset
        Boolean nosunset = false;
        double setTimeGMT = Solar.calcSunsetUTC(JD, Latitude, -Longitude);
        if (Double.isNaN(setTimeGMT))
            nosunset = true;

        // we now know the UTC # of minutes for each. Return the UTC sunrise/sunset times
        if (!nosunrise)
            Sunrise = MinutesToDate(dt, riseTimeGMT);

        if (!nosunset)
            Sunset = MinutesToDate(dt, setTimeGMT);
        
        // Update daytime/nighttime
        // 3 possible scenarios:
        // (a) time is between sunrise/sunset as computed - it's daytime or FAA daytime.
        // (b) time is after the sunset - figure out the next sunrise and compare to that
        // (c) time is before sunrise - figure out the previous sunset and compare to that
        IsNight = IsCivilNight;
        IsFAANight = false;
        if (Sunrise.compareTo(dt) <= 0 && Sunset.compareTo(dt) >= 0)
        {
            // between sunrise and sunset - it's daytime no matter how you slice it; use default values (set above)
        }
        else if (Sunset.compareTo(dt) < 0)
        {
            // get the next sunrise.  It is night if the time is between sunset and the next sunrise
            Date dtTomorrow = AddDays(dt, 1);
            JD = JDFromDate(dtTomorrow);
            double nextSunrise = Solar.calcSunriseUTC(JD, Latitude, -Longitude);

            if (!Double.isNaN(nextSunrise))
            {
                Date dtNextSunrise = MinutesToDate(dtTomorrow, nextSunrise);
                IsNight = (dtNextSunrise.compareTo(dt) > 0); // we've already determined that we're after sunset, we just need to be before sunrise
                IsFAANight = (AddHours(Sunset, 1).compareTo(dt) <= 0 && AddHours(dtNextSunrise, -1).compareTo(dt) >= 0);
            }
        }
        else if (Sunrise.compareTo(dt) > 0)
        {
            // get the previous sunset.  It is night if the time is between that sunset and the sunrise
            Date dtYesterday = AddDays(dt, -1);
            JD = JDFromDate(dtYesterday);
            double prevSunset = Solar.calcSunsetUTC(JD, Latitude, -Longitude);

            if (!Double.isNaN(prevSunset))
            {
                Date dtPrevSunset = MinutesToDate(dtYesterday, prevSunset);
                IsNight = (dtPrevSunset.compareTo(dt) < 0); // we've already determined that we're before sunrise, we just need to be after sunset.
                IsFAANight = (AddHours(dtPrevSunset, 1).compareTo(dt) <= 0 && AddHours(Sunrise, -1).compareTo(dt) >= 0);
            }
        }
    }
}
