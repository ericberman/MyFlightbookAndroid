/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2020 MyFlightbook, LLC

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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;

import com.myflightbook.android.MFBMain;

import java.io.BufferedReader;
import java.io.StringReader;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

public class LocSample extends LatLong {
    private long id = -1;
    int Alt = 0;
    double Speed = 0.0;
    double HError = 0.0;
    Date TimeStamp = new Date();
    private int TZOffset = 0;
    String Comment = "";

    private static SimpleDateFormat m_df = null;

    private static SimpleDateFormat getUTCFormatter() {
        if (m_df == null) {
            m_df = new SimpleDateFormat(MFBConstants.TIMESTAMP, java.util.Locale.getDefault());
            m_df.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        return m_df;
    }

    public Location getLocation() {
        Location l = new Location("MFB");
        l.setLatitude(Latitude);
        l.setLongitude(Longitude);
        l.setSpeed((float) (Speed / MFBConstants.MPS_TO_KNOTS));  // convert back to Meters per Second
        l.setAltitude(Alt / MFBConstants.METERS_TO_FEET);         // Convert back to meters
        l.setTime(TimeStamp.getTime());
        l.setAccuracy((float)HError);
        return l;
    }

    public static LocSample[] flightPathFromDB() {
        ArrayList<LocSample> al = new ArrayList<>();

        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        try (Cursor c = db.query("FlightTrack", null, null, null, null, null, "TimeStamp ASC")) {

            while (c.moveToNext())
                al.add(new LocSample(c));
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, "Unable to retrieve pending flight telemetry data: " + e.getLocalizedMessage());
        }

        return al.toArray(new LocSample[0]);
    }

    static String FlightDataFromSamples(LocSample[] rgloc) {
        if (rgloc.length == 0)
            return "";

        StringBuilder sb = new StringBuilder("LAT,LON,PALT,SPEED,HERROR,DATE,TZOFFSET,COMMENT\r\n");

        for (LocSample loc : rgloc)
            sb.append(String.format(Locale.US, "%.8f,%.8f,%d,%.1f,%.1f,%s,%d,%s\r\n",
                    loc.Latitude,
                    loc.Longitude,
                    loc.Alt,
                    loc.Speed,
                    loc.HError,
                    getUTCFormatter().format(loc.TimeStamp),
                    loc.TZOffset,
                    loc.Comment));
        return sb.toString();
    }

    public static LocSample[] samplesFromDataString(String s) {
        BufferedReader r = new BufferedReader(new StringReader(s));

        NumberFormat nf = NumberFormat.getInstance(Locale.US);

        ArrayList<LocSample> al = new ArrayList<>();
        try {
            //noinspection UnusedAssignment - we are just reading the first row and ignoring it.
            String szRow = r.readLine(); // skip the first row
            String[] rgszRow;
            while ((szRow = r.readLine()) != null) {
                rgszRow = szRow.split(",");
                LocSample l = new LocSample(
                        Objects.requireNonNull(nf.parse(rgszRow[0])).doubleValue(),    // lat
                        Objects.requireNonNull(nf.parse(rgszRow[1])).doubleValue(), // lon
                        Objects.requireNonNull(nf.parse(rgszRow[2])).intValue(),    // alt
                        Objects.requireNonNull(nf.parse(rgszRow[3])).doubleValue(), // speed
                        Objects.requireNonNull(nf.parse(rgszRow[4])).doubleValue(), // error
                        rgszRow[5]);
                al.add(l);
            }
        } catch (Exception ignored) {
        }

        return al.toArray(new LocSample[0]);
    }

    private void FromCursor(Cursor c) {
        id = c.getLong(c.getColumnIndex("_id"));
        Latitude = c.getDouble(c.getColumnIndex("Lat"));
        Longitude = c.getDouble(c.getColumnIndex("Lon"));
        Alt = c.getInt(c.getColumnIndex("Alt"));
        Speed = c.getDouble(c.getColumnIndex("Speed"));
        HError = c.getDouble(c.getColumnIndex("Error"));
        try {
            TimeStamp = getUTCFormatter().parse(c.getString(c.getColumnIndex("TimeStamp")));
        } catch (ParseException ignored) {
        }
        TZOffset = c.getInt(c.getColumnIndex("TZOffset"));
        Comment = c.getString(c.getColumnIndex("Comment"));
    }

    void ToContentValues(ContentValues cv) {
        if (id >= 0)
            cv.put("_id", id);
        cv.put("Lat", Latitude);
        cv.put("Lon", Longitude);
        cv.put("Alt", Alt);
        cv.put("Speed", (int) Speed);
        cv.put("Error", HError);
        cv.put("TimeStamp", getUTCFormatter().format(TimeStamp));
        cv.put("TZOffset", TZOffset);
        cv.put("Comment", Comment);
    }

    private LocSample(Cursor c) {
        super();
        FromCursor(c);
    }

    LocSample(Location l) {
        super();
        Latitude = l.getLatitude();
        Longitude = l.getLongitude();
        Alt = (int) (l.getAltitude() * MFBConstants.METERS_TO_FEET);
        Speed = l.getSpeed() * MFBConstants.MPS_TO_KNOTS;
        HError = l.getAccuracy();
        TimeStamp.setTime(l.getTime());
        TZOffset = 0;
    }

    LocSample(double latitude, double longitude, int altitude, double speed, double error, String szDate) {
        super();
        Latitude = latitude;
        Longitude = longitude;
        Alt = altitude;
        Speed = speed;
        HError = error;
        try {
            TimeStamp = getUTCFormatter().parse(szDate);
        } catch (ParseException ignored) {
        }
        TZOffset = 0;
    }
}
