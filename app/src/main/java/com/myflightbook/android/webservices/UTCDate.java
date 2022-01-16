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
package com.myflightbook.android.webservices;

import android.content.Context;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class UTCDate {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    static public TimeZone getUTCTimeZone() {
        return TimeZone.getTimeZone("UTC");
    }

    static public GregorianCalendar UTCCalendar() {
        return new GregorianCalendar(getUTCTimeZone());
    }

    static public Date NullDate() {
        Calendar c = new GregorianCalendar(getUTCTimeZone());
        c.set(1, 0, 1, 0, 0, 0);
        return c.getTime();
    }

    static public Boolean IsNullDate(Date d) {
        Calendar c = new GregorianCalendar(getUTCTimeZone());
        c.setTime(d);
        return c.get(Calendar.YEAR) < 100;
    }

    static public String formatDate(Boolean fLocal, Date d, Context c) {
        if (fLocal) {
            java.text.DateFormat dfDate = DateFormat.getDateFormat(c);
            java.text.DateFormat dfTime = DateFormat.getTimeFormat(c);
            GregorianCalendar g = new GregorianCalendar();
            dfDate.setCalendar(g);
            dfTime.setCalendar(g);
            SimpleDateFormat sdf = new SimpleDateFormat("zzz", Locale.getDefault());
            sdf.setCalendar(g);
            return String.format("%s %s (%s)", dfDate.format(d), dfTime.format(d), sdf.format(d));
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm '(UTC)'", Locale.getDefault());
            sdf.setCalendar(UTCDate.UTCCalendar());
            return sdf.format(d);
        }
    }
}
