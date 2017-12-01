package Model;

import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;

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
        sample.Alt = (int) (Double.parseDouble(readText(parser)) * MFBConstants.METERS_TO_FEET);
    }

    private void readTime(LocSample sample, XmlPullParser parser) throws IOException, XmlPullParserException {
        try {
            sample.TimeStamp = ParseUTCDate(readText(parser));
        } catch (ParseException ignored) {
        }
    }

    private void readSpeed(LocSample sample, XmlPullParser parser) throws IOException, XmlPullParserException {
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
}
