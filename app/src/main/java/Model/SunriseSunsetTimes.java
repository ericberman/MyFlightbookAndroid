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
package Model;

import com.myflightbook.android.WebServices.UTCDate;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class SunriseSunsetTimes {
    public Date Sunrise;
    public Date Sunset;
    @SuppressWarnings("WeakerAccess")
    public double Latitude;
    @SuppressWarnings("WeakerAccess")
    public double Longitude;
    @SuppressWarnings("WeakerAccess")
    public Date Date;

    // True if sun is more than 6 degrees below the horizon.
    Boolean IsCivilNight;

    // True if between sunset and sunrise
    Boolean IsNight;

    // True if between 1-hour (or offset specified by NightLandingOffset) after sunset and 1-hour before sunrise
    Boolean IsFAANight;

    // True if between Sunset + NightFlightOffset and Sunrise - NightFlightOffset
    Boolean IsWithinNightOffset;

    // The offset from sunrise/sunset (in minutes) needed for night currency (i.e., in computing IsFAANight
    // 1 hour by default (i.e., landings need to be between sunset + 1 hour and sunrise - 1 hour to count
    private int NightLandingOffset = 60;

    // The offset from sunrise/sunset (in minutes) needed for night flight, if that's how night flight is computed
    // Default is 0.0, since default for night flight is IsCivilNight
    private int NightFlightOffset = 0;

    SunriseSunsetTimes(Date dt, double latitude, double longitude, int nightFlightOffset) {
        Date = dt;
        Sunrise = Sunset = UTCDate.NullDate();
        Latitude = latitude;
        Longitude = longitude;
        NightLandingOffset = 60;
        NightFlightOffset = nightFlightOffset;
        try {
            ComputeTimesAtLocation(dt);
        } catch (Exception ignored) {
        }
    }

    public SunriseSunsetTimes(Date dt, double latitude, double longitude) {
        this(dt, latitude, longitude, 0);
    }

    /// <summary>
    /// Returns the UTC time for the minutes into the day.  Note that it could be a day forward or backward from the requested day!!!
    /// </summary>
    /// <param name="dt">Requested day (only m/d/y matter)</param>
    /// <param name="minutes"></param>
    /// <returns></returns>
    private Date MinutesToDate(Date dt, double minutes) {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        cal.setTimeInMillis(dt.getTime());

        // reset to the start of the day
        cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        long l = cal.getTimeInMillis();

        long milisIntoDay = (long) (minutes * 60 * 1000);
        cal.setTimeInMillis(l + milisIntoDay);

        return cal.getTime();
    }

    private Date AddMillis(Date dt, long millis) {
        return new Date(dt.getTime() + millis);
    }

    private Date AddHours(Date dt, int hours) {
        return AddMillis(dt, hours * 3600 * 1000);
    }

    private Date AddMinutes(Date dt, int minutes) {
        return AddMillis(dt, minutes * 60 * 1000);
    }

    private Date AddDays(Date dt, int days) {
        return AddHours(dt, days * 24);
    }

    //Return JD from a UTC date
    private double JDFromDate(Date dt) {
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
    private void ComputeTimesAtLocation(Date dt) throws Exception {
        if (Latitude > 90 || Latitude < -90)
            throw new Exception("Bad latitude");
        if (Longitude > 180 || Longitude < -180)
            throw new Exception("Bad longitude");

        double JD = JDFromDate(dt);

        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(dt.getTime());
        double solarAngle = Solar.calcSolarAngle(Latitude, Longitude, JD, cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE));
        IsCivilNight = solarAngle <= -6.0;

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
        IsWithinNightOffset = false;
        //noinspection StatementWithEmptyBody
        if (Sunrise.compareTo(dt) <= 0 && Sunset.compareTo(dt) >= 0) {
            // between sunrise and sunset - it's daytime no matter how you slice it; use default values (set above)
        } else if (Sunset.compareTo(dt) < 0) {
            // get the next sunrise.  It is night if the time is between sunset and the next sunrise
            Date dtTomorrow = AddDays(dt, 1);
            JD = JDFromDate(dtTomorrow);
            double nextSunrise = Solar.calcSunriseUTC(JD, Latitude, -Longitude);

            if (!Double.isNaN(nextSunrise)) {
                Date dtNextSunrise = MinutesToDate(dtTomorrow, nextSunrise);
                IsNight = (dtNextSunrise.compareTo(dt) > 0); // we've already determined that we're after sunset, we just need to be before sunrise
                IsFAANight = (AddMinutes(Sunset, NightLandingOffset).compareTo(dt) <= 0 && AddMinutes(dtNextSunrise, -NightLandingOffset).compareTo(dt) >= 0);
                IsWithinNightOffset = (AddMinutes(Sunset, NightFlightOffset).compareTo(dt) <= 0 && AddMinutes(dtNextSunrise, -NightFlightOffset).compareTo(dt) >= 0);
            }
        } else if (Sunrise.compareTo(dt) > 0) {
            // get the previous sunset.  It is night if the time is between that sunset and the sunrise
            Date dtYesterday = AddDays(dt, -1);
            JD = JDFromDate(dtYesterday);
            double prevSunset = Solar.calcSunsetUTC(JD, Latitude, -Longitude);

            if (!Double.isNaN(prevSunset)) {
                Date dtPrevSunset = MinutesToDate(dtYesterday, prevSunset);
                IsNight = (dtPrevSunset.compareTo(dt) < 0); // we've already determined that we're before sunrise, we just need to be after sunset.
                IsFAANight = (AddMinutes(dtPrevSunset, NightLandingOffset).compareTo(dt) <= 0 && AddMinutes(Sunrise, -NightLandingOffset).compareTo(dt) >= 0);
                IsWithinNightOffset = (AddMinutes(dtPrevSunset, NightFlightOffset).compareTo(dt) <= 0 && AddMinutes(Sunrise, -NightFlightOffset).compareTo(dt) >= 0);
            }
        }
    }
}
