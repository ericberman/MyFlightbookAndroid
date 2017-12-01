package Model;

import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by ericberman on 11/30/17.
 *
 * Concrete telemetry class that can read/write GPX
 */

public class KML extends Telemetry {

    public KML(Uri uri) {
        super(uri);
    }

    private void readCoord(LocSample sample, XmlPullParser parser) throws IOException, XmlPullParserException {
        String s = readText(parser);
        String[] rg = s.split(" ");
        if (rg.length > 1) {
            sample.Longitude = Double.parseDouble(rg[0]);
            sample.Latitude = Double.parseDouble(rg[1]);
            if (rg.length > 2)
                sample.Alt = (int) (Double.parseDouble(rg[2]) * MFBConstants.METERS_TO_FEET);
        }
    }

    @Override
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
                            case "when":
                                Date dt = new Date();
                                try {
                                    dt = ParseUTCDate(readText(parser));
                                } catch (ParseException ignored) {
                                }

                                sample = new LocSample(0, 0, 0, 0, 1.0, "");
                                sample.TimeStamp = dt;
                                break;
                            case "gx:coord":
                                readCoord(sample, parser);
                                lst.add(sample);
                                sample = null;
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

        return ComputeSpeed(lst.toArray(new LocSample[0]));
    }
}
