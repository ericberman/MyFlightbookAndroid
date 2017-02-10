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
import java.util.TimeZone;

public class LocSample extends LatLong {
    public long id = -1;
    @SuppressWarnings("WeakerAccess")
    public int Alt = 0;
    @SuppressWarnings("WeakerAccess")
    public double Speed = 0.0;
    @SuppressWarnings("WeakerAccess")
    public double HError = 0.0;
    @SuppressWarnings("WeakerAccess")
    public Date TimeStamp = new Date();
    @SuppressWarnings("WeakerAccess")
    public int TZOffset = 0;
    @SuppressWarnings("WeakerAccess")
    public String Comment = "";

    private static SimpleDateFormat m_df = null;

    private static SimpleDateFormat getUTCFormatter() {
        if (m_df == null) {
            m_df = new SimpleDateFormat(MFBConstants.TIMESTAMP, java.util.Locale.getDefault());
            m_df.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        return m_df;
    }

    public static LocSample[] flightPathFromDB() {
        ArrayList<LocSample> al = new ArrayList<>();

        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        Cursor c = null;
        try {
            c = db.query("FlightTrack", null, null, null, null, null, "TimeStamp ASC");

            while (c.moveToNext())
                al.add(new LocSample(c));
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, "Unable to retrieve pending flight telemetry data: " + e.getLocalizedMessage());
        } finally {
            if (c != null)
                c.close();
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

    public static String getFlightDataStringAsKML(LatLong[] rgloc) {

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        StringBuilder sb = new StringBuilder();
        // Hack - this is brute force writing, not proper generation of XML.  But it works...
        // We are also assuming valid timestamps (i.e., we're using gx:Track)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\">\r\n");
        sb.append("<Document>\r\n<Style id=\"redPoly\"><LineStyle><color>7f0000ff</color><width>4</width></LineStyle><PolyStyle><color>7f0000ff</color></PolyStyle></Style>\r\n");
        sb.append("<open>1</open>\r\n<visibility>1</visibility>\r\n<Placemark>\r\n\r\n<styleUrl>#redPoly</styleUrl><gx:Track>\r\n");

        if (rgloc[0] instanceof LocSample) {
            sb.append("<extrude>1</extrude>\r\n<altitudeMode>absolute</altitudeMode>\r\n");
            for (LatLong ll : rgloc) {
                LocSample l = (LocSample) ll;
                sb.append(String.format(Locale.US, "<when>%s</when>\r\n<gx:coord>%.8f %.8f %.1f</gx:coord>\r\n",
                        sdf.format(l.TimeStamp),
                        l.Longitude,
                        l.Latitude,
                        l.Alt / MFBConstants.METERS_TO_FEET
                ));
            }
        } else {
            sb.append("<altitudeMode>clampToGround</altitudeMode>\r\n");
            for (LatLong ll : rgloc) {
                sb.append(String.format(Locale.US, "<gx:coord>%.8f %.8f 0</gx:coord>\r\n",
                        ll.Longitude,
                        ll.Latitude
                ));
            }
        }
        sb.append("</gx:Track></Placemark></Document></kml>");
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
                        nf.parse(rgszRow[0]).doubleValue(),    // lat
                        nf.parse(rgszRow[1]).doubleValue(), // lon
                        nf.parse(rgszRow[2]).intValue(),    // alt
                        nf.parse(rgszRow[3]).doubleValue(), // speed
                        nf.parse(rgszRow[4]).doubleValue(), // error
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
