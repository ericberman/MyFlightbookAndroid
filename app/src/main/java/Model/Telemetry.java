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
package Model;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.util.Xml;

import com.myflightbook.android.WebServices.UTCDate;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Dictionary;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by ericberman on 11/29/17.
 *
 * Base class for telemetry that can be imported/exported.
 */

public abstract class Telemetry {
    public enum ImportedFileType { GPX, KML, CSV, Unknown }

    private Uri m_uri = null;
    private Context m_ctxt = null;
    private Pattern m_pISODate = null;
    Hashtable<String, Object> metaData;
    public static final String TELEMETRY_METADATA_TAIL = "aircraft";

    Telemetry() {}

    Telemetry(Uri uri, Context c) {
        m_uri = uri;
        m_ctxt = c;
        metaData = new Hashtable<>();
    }

    public Dictionary<String, Object> getMetaData() {
        return metaData;
    }

    LocSample[] ComputeSpeed(LocSample[] rgSamples) {
        if (rgSamples == null || rgSamples.length == 0)
            return rgSamples;

        LocSample refSample = rgSamples[0];
        refSample.Speed = 0;
        for (int i = 1; i < rgSamples.length; i++) {
            if (rgSamples[i] == null)
                break;
            long elapsedMS = (rgSamples[i].TimeStamp != null && refSample.TimeStamp != null) ? rgSamples[i].TimeStamp.getTime() - refSample.TimeStamp.getTime() : -1;

            if (elapsedMS <= 0) {
                rgSamples[i].Speed = refSample.Speed;
            } else {
                double dist = rgSamples[i].getLocation().distanceTo(refSample.getLocation());
                rgSamples[i].Speed = MFBConstants.MPS_TO_KNOTS * (dist * 1000) / elapsedMS;
                refSample = rgSamples[i];
            }
        }

        return rgSamples;
    }

    Date ParseUTCDate(String s) {
        if (m_pISODate == null) {
            m_pISODate = Pattern.compile("(\\d+)-(\\d+)-(\\d+)T? ?(\\d+):(\\d+):(\\d+\\.?\\d*)Z?", Pattern.CASE_INSENSITIVE);
        }
        Matcher m = m_pISODate.matcher(s);

        if (m.matches()) {
            int year = Integer.parseInt(Objects.requireNonNull(m.group(1)));
            int month = Integer.parseInt(Objects.requireNonNull(m.group(2)));
            int day = Integer.parseInt(Objects.requireNonNull(m.group(3)));
            int hour = Integer.parseInt(Objects.requireNonNull(m.group(4)));
            int minute = Integer.parseInt(Objects.requireNonNull(m.group(5)));
            double second = Double.parseDouble(Objects.requireNonNull(m.group(6)));

            GregorianCalendar gc = UTCDate.UTCCalendar();
            gc.set(year, month - 1, day, hour, minute, (int) second);
            return gc.getTime();
        }
        return new Date();
    }

    private static ImportedFileType TypeFromBufferedReader(BufferedReader br) throws IOException {
        ImportedFileType result = ImportedFileType.Unknown;

        String s;
        while ((s = br.readLine()) != null) {
            s = s.toUpperCase(Locale.ENGLISH);
            if (s.contains("GPX")) {
                result = ImportedFileType.GPX;
                break;
            }
            else if (s.contains("KML")) {
                result = ImportedFileType.KML;
                break;
            }
        }

        return result;
    }

    public static ImportedFileType TypeFromString(String sz) throws IOException {
        return TypeFromBufferedReader(new BufferedReader(new StringReader(sz)));
    }

    private static ImportedFileType TypeFromUri(Uri data, Context c) throws IOException {
        InputStream in = c.getContentResolver().openInputStream(data);
        return TypeFromBufferedReader(new BufferedReader(new InputStreamReader(Objects.requireNonNull(in))));
    }

    public static Telemetry TelemetryFromURL(Uri uri, Context c) throws IOException {
        if (uri == null)
            return null;

        switch (TypeFromUri(uri, c)) {
            case GPX:
                return new GPX(uri, c);
            case KML:
                return new KML(uri, c);
            default:
                return null;
        }
    }

    String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    private LocSample[] parse(InputStream s) throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(s, null);
        parser.nextTag();
        return readFeed(parser);
    }

    // Follows the instructions at https://developer.android.com/training/basics/network-ops/xml.html
    private LocSample[] parse() throws XmlPullParserException, IOException {
        try (InputStream in = m_ctxt.getContentResolver().openInputStream(m_uri)) {
            return parse(in);
        }
    }

    protected abstract LocSample[] readFeed(XmlPullParser parser);

    public LocSample[] Samples() throws IOException, XmlPullParserException {
        return parse();
    }

    public LocSample[] Samples(String sz) throws IOException, XmlPullParserException {
        return parse(new ByteArrayInputStream(sz.getBytes() ));
    }

    /*
     Returns a synthesized path between two points, even spacing, between the two timestamps.
     
     Can be used to estimate night flight, for example, or draw a great-circle path between two points.
     
     From http://www.movable-type.co.uk/scripts/latlong.html
     Formula: 	
         a = sin((1−f)⋅δ) / sin δ
         b = sin(f⋅δ) / sin δ
         x = a ⋅ cos φ1 ⋅ cos λ1 + b ⋅ cos φ2 ⋅ cos λ2
         y = a ⋅ cos φ1 ⋅ sin λ1 + b ⋅ cos φ2 ⋅ sin λ2
         z = a ⋅ sin φ1 + b ⋅ sin φ2
         φi = atan2(z, √x² + y²)
         λi = atan2(y, x)
     where f is fraction along great circle route (f=0 is point 1, f=1 is point 2), δ is the angular distance d/R between the two points.
     */
    public static LocSample[] SynthesizePath(Location llStart, Date dtStart, Location llEnd, Date dtEnd)
    {
        ArrayList<LocSample> lst = new ArrayList<>();

        if (UTCDate.IsNullDate(dtStart) || UTCDate.IsNullDate(dtEnd))
            return lst.toArray(new LocSample[0]);

        double rlat1 = Math.PI * (llStart.getLatitude() / 180.0);
        double rlon1 = Math.PI * (llStart.getLongitude() / 180.0);
        double rlat2 = Math.PI * (llEnd.getLatitude() / 180.0);
        double rlon2 = Math.PI * (llEnd.getLongitude() / 180.0);

        double dLon = rlon2 - rlon1;

        double delta = Math.atan2(Math.sin(dLon) * Math.cos(rlat2), Math.cos(rlat1) * Math.sin(rlat2) - Math.sin(rlat1) * Math.cos(rlat2) * Math.cos(dLon));
        // double delta = 2 * Math.asin(Math.sqrt(Math.Pow((Math.sin((rlat1 - rlat2) / 2)), 2) + Math.cos(rlat1) * Math.cos(rlat2) * Math.Pow(Math.sin((rlon1 - rlon2) / 2), 2)));
        double sin_delta = Math.sin(delta);

        // Compute path at 1-minute intervals, subtracting off one minute since we'll add a few "full-stop" samples below.
        long ts = (dtEnd.getTime() - dtStart.getTime()) / 1000; // time in seconds
        double minutes = (ts / 60.0) - 1;

        if (minutes > 48 * 60 || minutes <= 0)  // don't do paths more than 48 hours, or negative times.
            return lst.toArray(new LocSample[0]);

        // Add a few stopped fields at the end to make it clear that there's a full-stop.  Separate them by a few seconds each.
        LocSample[] rgPadding = new LocSample[]
                {
                        new LocSample(llEnd, 0, 0.2, new Date(dtEnd.getTime() + 3000)),
                        new LocSample(llEnd, 0, 0.2, new Date(dtEnd.getTime() + 6000)),
                        new LocSample(llEnd, 0, 0.2, new Date(dtEnd.getTime() + 9000))
                };

        // We need to derive an average speed.  But no need to compute - just assume constant speed.
        double distanceM = llStart.distanceTo(llEnd);    // distance here is in meters
        double distanceNM =  distanceM * MFBConstants.METERS_TO_NM;
        double speedMS = distanceM / ts;    // we know that ts is >= 0 because of minutes check above

        // low distance (< 1nm) is probably pattern work - just pick a decent speed.  If you actually go somewhere, then derive a speed.
        double speedKts = (distanceNM < 1.0) ? 150.0 : speedMS * MFBConstants.MPS_TO_KNOTS;

        lst.add(new LocSample(llStart, 0, speedKts, dtStart));

        for (long minute = 0; minute <= minutes; minute++)
        {
            if (distanceNM < 1.0)
                lst.add(new LocSample(llStart, 0, speedKts, new Date(dtStart.getTime() + minute * 60000)));
            else
            {
                double f = ((double)minute) / minutes;
                double a = Math.sin((1.0 - f) * delta) / sin_delta;
                double b = Math.sin(f * delta) / sin_delta;
                double x = a * Math.cos(rlat1) * Math.cos(rlon1) + b * Math.cos(rlat2) * Math.cos(rlon2);
                double y = a * Math.cos(rlat1) * Math.sin(rlon1) + b * Math.cos(rlat2) * Math.sin(rlon2);
                double z = a * Math.sin(rlat1) + b * Math.sin(rlat2);

                double rlat = Math.atan2(z, Math.sqrt(x * x + y * y));
                double rlon = Math.atan2(y, x);

                Location l = new Location(llStart);
                l.setLatitude(180 * (rlat / Math.PI));
                l.setLongitude(180 * (rlon / Math.PI));
                lst.add(new LocSample(l, 0, speedKts, new Date(dtStart.getTime() + minute * 60000)));
            }
        }

        lst.addAll(Arrays.asList(rgPadding));

        return lst.toArray(new LocSample[0]);
    }
}
