package Model;

import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by ericberman on 11/30/17.
 *
 * Concrete telemetry class that can read/write GPX
 */

public class GPX extends Telemetry {

    GPX(Uri uri) {
        super(uri);
    }

    private LocSample readTrkPoint(XmlPullParser parser) {
        String szLat = parser.getAttributeValue(null, "lat");
        String szLon = parser.getAttributeValue(null, "lon");

        return new LocSample(Double.parseDouble(szLat), Double.parseDouble(szLon), 0, 0, 1.0, "");
    }

    private void readEle(LocSample sample, XmlPullParser parser) throws IOException, XmlPullParserException {
        if (sample == null)
            return;
        sample.Alt = (int) (Double.parseDouble(readText(parser)) * MFBConstants.METERS_TO_FEET);
    }

    private void readTime(LocSample sample, XmlPullParser parser) throws IOException, XmlPullParserException {
        if (sample == null)
            return;
        try {
            sample.TimeStamp = ParseUTCDate(readText(parser));
        } catch (ParseException ignored) {
        }
    }

    private void readSpeed(LocSample sample, XmlPullParser parser) throws IOException, XmlPullParserException {
        if (sample == null)
            return;
        sample.Speed = Double.parseDouble(readText(parser)) * MFBConstants.MPS_TO_KNOTS;
    }

    public LocSample[] readFeed(XmlPullParser parser) {
        ArrayList<LocSample> lst = new ArrayList<>();

        LocSample sample = null;
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                try {
                    int eventType = parser.getEventType();
                    if (eventType == XmlPullParser.START_TAG) {
                        String name = parser.getName();
                        // Starts by looking for the entry tag
                        switch (name) {
                            case "trkpt":
                                sample = readTrkPoint(parser);
                                break;
                            case "ele":
                                readEle(sample, parser);
                                break;
                            case "time":
                                readTime(sample, parser);
                                break;
                            case "speed":
                                readSpeed(sample, parser);
                                break;
                        }
                    } else if (eventType == XmlPullParser.END_TAG) {
                        if (parser.getName().equals("trkpt")) {
                            lst.add(sample);
                            sample = null;
                        }
                    }
                }
                catch (XmlPullParserException ignored) { }
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }

        return lst.toArray(new LocSample[0]);
    }

    public static String getFlightDataStringAsGPX(LatLong[] rgloc) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        StringBuilder sb = new StringBuilder();
        // Hack - this is brute force writing, not proper generation of XML.  But it works...
        // We are also assuming valid timestamps (i.e., we're using gx:Track)
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n");
        sb.append("<gpx creator=\"http://myflightbook.com\" version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("<trk>\r\n<name />\r\n<trkseg>\r\n");

        Boolean fIsLocSample = (rgloc.length > 0 && rgloc[0] instanceof  LocSample);

        for (LatLong ll : rgloc) {
            sb.append(String.format(Locale.US, "<trkpt lat=\"%.8f\" lon=\"%.8f\">\r\n", ll.Latitude, ll.Longitude));
            if (fIsLocSample) {
                LocSample ls = (LocSample) ll;
                sb.append(String.format(Locale.US, "<ele>%.8f</ele>\r\n", ls.Alt / MFBConstants.METERS_TO_FEET));
                sb.append(String.format(Locale.US, "<time>%s</time>\r\n", sdf.format(ls.TimeStamp)));
                sb.append(String.format(Locale.US, "<speed>%.8f</speed>\r\n", ls.Speed));
            }
            sb.append(("</trkpt>\r\n"));
        }
        sb.append("</trkseg></trk></gpx>");
        return sb.toString();
    }

}
