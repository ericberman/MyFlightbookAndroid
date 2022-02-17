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
package model

import android.content.Context
import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by ericberman on 11/30/17.
 *
 * Concrete telemetry class that can read/write GPX
 */
class GPX internal constructor(uri: Uri?, c: Context?) : Telemetry(uri, c) {
    private fun readTrkPoint(parser: XmlPullParser): LocSample {
        val szLat = parser.getAttributeValue(null, "lat")
        val szLon = parser.getAttributeValue(null, "lon")
        return LocSample(szLat.toDouble(), szLon.toDouble(), 0, 0.0, 1.0, "")
    }

    private fun readMetaName(parser: XmlPullParser) {
        val szTail = parser.getAttributeValue(null, TELEMETRY_METADATA_TAIL)
        if (szTail != null && metaData != null) metaData!![TELEMETRY_METADATA_TAIL] = szTail
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readEle(sample: LocSample?, parser: XmlPullParser) {
        if (sample == null) return
        sample.altitude = (readText(parser).toDouble() * MFBConstants.METERS_TO_FEET).toInt()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readTime(sample: LocSample?, parser: XmlPullParser) {
        if (sample == null) return
        sample.timeStamp = parseUTCDate(readText(parser))
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readSpeed(sample: LocSample?, parser: XmlPullParser) {
        if (sample == null) return
        sample.speed = readText(parser).toDouble() * MFBConstants.MPS_TO_KNOTS
    }

    @Throws(IOException::class, XmlPullParserException::class)
    private fun readBadElfSpeed(sample: LocSample?, parser: XmlPullParser) {
        if (sample == null) return
        sample.speed = readText(parser).toDouble() * MFBConstants.MPS_TO_KNOTS
    }

    public override fun readFeed(parser: XmlPullParser?): Array<LocSample> {
        if (parser == null)
            return arrayOf()
        val lst = ArrayList<LocSample>()
        var sample: LocSample? = null
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                try {
                    val eventType = parser.eventType
                    if (eventType == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "trkpt" -> sample = readTrkPoint(parser)
                            "ele" -> readEle(sample, parser)
                            "time" -> readTime(sample, parser)
                            "speed" -> readSpeed(sample, parser)
                            "badelf:speed" -> readBadElfSpeed(sample, parser)
                            "name" -> readMetaName(parser)
                        }
                    } else if (eventType == XmlPullParser.END_TAG) {
                        if (parser.name == "trkpt") {
                            lst.add(sample!!)
                            sample = null
                        }
                    }
                } catch (e: XmlPullParserException) {
                    e.printStackTrace()
                }
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return lst.toTypedArray()
    }

    companion object {
        fun getFlightDataStringAsGPX(rgloc: Array<LatLong>): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val sb = StringBuilder()
            // Hack - this is brute force writing, not proper generation of XML.  But it works...
            // We are also assuming valid timestamps (i.e., we're using gx:Track)
            sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n")
            sb.append("<gpx creator=\"http://myflightbook.com\" version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
            sb.append("<trk>\r\n<name />\r\n<trkseg>\r\n")
            val fIsLocSample = rgloc.isNotEmpty() && rgloc[0] is LocSample
            for (ll in rgloc) {
                sb.append(
                    String.format(
                        Locale.US,
                        "<trkpt lat=\"%.8f\" lon=\"%.8f\">\r\n",
                        ll.latitude,
                        ll.longitude
                    )
                )
                if (fIsLocSample) {
                    val ls = ll as LocSample
                    sb.append(
                        String.format(
                            Locale.US,
                            "<ele>%.8f</ele>\r\n",
                            ls.altitude / MFBConstants.METERS_TO_FEET
                        )
                    )
                    sb.append(
                        String.format(
                            Locale.US,
                            "<time>%s</time>\r\n",
                            sdf.format(ls.timeStamp)
                        )
                    )
                    sb.append(String.format(Locale.US, "<speed>%.8f</speed>\r\n", ls.speed))
                }
                sb.append("</trkpt>\r\n")
            }
            sb.append("</trkseg></trk></gpx>")
            return sb.toString()
        }
    }
}