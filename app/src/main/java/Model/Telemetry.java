/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2021 MyFlightbook, LLC

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
import android.net.Uri;
import android.util.Xml;

import com.myflightbook.android.WebServices.UTCDate;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private static ImportedFileType TypeFromUri(Uri data, Context c) throws IOException {

        ImportedFileType result = ImportedFileType.Unknown;

        InputStream in = c.getContentResolver().openInputStream(data);
        BufferedReader br = new BufferedReader(new InputStreamReader(Objects.requireNonNull(in)));
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

    // Follows the instructions at https://developer.android.com/training/basics/network-ops/xml.html
    private LocSample[] parse() throws XmlPullParserException, IOException {
        try (InputStream in = m_ctxt.getContentResolver().openInputStream(m_uri)) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(Objects.requireNonNull(in)))) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(in, null);
                parser.nextTag();
                return readFeed(parser);
            }
        }
    }

    protected abstract LocSample[] readFeed(XmlPullParser parser);

    public LocSample[] Samples() throws IOException, XmlPullParserException {
        return parse();
    }
}
