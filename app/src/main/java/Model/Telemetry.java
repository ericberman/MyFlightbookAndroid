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

import android.net.Uri;
import android.util.Xml;

import com.myflightbook.android.WebServices.UTCDate;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
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
    private Pattern m_pISODate = null;

    Telemetry() {}

    Telemetry(Uri uri) {
        m_uri = uri;
    }

    LocSample[] ComputeSpeed(LocSample[] rgSamples) {
        if (rgSamples == null || rgSamples.length == 0)
            return rgSamples;

        LocSample refSample = rgSamples[0];
        refSample.Speed = 0;
        for (int i = 1; i < rgSamples.length; i++) {
            long elapsedMS = rgSamples[i].TimeStamp.getTime() - refSample.TimeStamp.getTime();

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

    Date ParseUTCDate(String s) throws ParseException {
        if (m_pISODate == null) {
            m_pISODate = Pattern.compile("(\\d+)-(\\d+)-(\\d+)T? ?(\\d+):(\\d+):(\\d+\\.?\\d*)Z?", Pattern.CASE_INSENSITIVE);
        }
        Matcher m = m_pISODate.matcher(s);

        if (m.matches()) {
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            int hour = Integer.parseInt(m.group(4));
            int minute = Integer.parseInt(m.group(5));
            double second = Double.parseDouble(m.group(6));

            GregorianCalendar gc = UTCDate.UTCCalendar();
            gc.set(year, month - 1, day, hour, minute, (int) second);
            return gc.getTime();
        }
        return new Date();
    }

    private static ImportedFileType TypeFromUri(Uri data) {

        ImportedFileType result = ImportedFileType.Unknown;

        File f = new File(data.getPath());
        try (FileReader fr = new FileReader(f)) {
            try (BufferedReader br = new BufferedReader(fr)) {
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
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static Telemetry TelemetryFromURL(Uri uri) {
        if (uri == null)
            return null;

        switch (TypeFromUri(uri)) {
            case GPX:
                return new GPX(uri);
            case KML:
                return new KML(uri);
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
        File f = new File(m_uri.getPath());
        try (FileInputStream s = new FileInputStream(f)){
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(s, null);
            parser.nextTag();
            return readFeed(parser);
        }
    }

    protected abstract LocSample[] readFeed(XmlPullParser parser);

    public LocSample[] Samples() throws IOException, XmlPullParserException {
        return parse();
    }
}
