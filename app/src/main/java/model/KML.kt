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
class KML internal constructor(uri: Uri?, c: Context?) : Telemetry(uri, c) {
    @Throws(IOException::class, XmlPullParserException::class)
    private fun readCoord(sample: LocSample?, parser: XmlPullParser) {
        if (sample == null) return
        val s = readText(parser)
        val rg = s.split(" ").toTypedArray()
        if (rg.size > 1) {
            sample.longitude = rg[0].toDouble()
            sample.latitude = rg[1].toDouble()
            if (rg.size > 2) sample.altitude =
                (rg[2].toDouble() * MFBConstants.METERS_TO_FEET).toInt()
        }
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
                            "when" -> {
                                val dt = parseUTCDate(readText(parser))
                                sample = LocSample(0.0, 0.0, 0, 0.0, 1.0, "")
                                sample.timeStamp = dt
                            }
                            "gx:coord" -> if (sample != null) {
                                readCoord(sample, parser)
                                lst.add(sample)
                                sample = null
                            }
                        }
                    } else if (eventType == XmlPullParser.END_TAG) {
                        if (sample != null && parser.name == "trkpt") {
                            lst.add(sample)
                            sample = null
                        }
                    }
                } catch (ignored: XmlPullParserException) {
                }
            }
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return computeSpeed(lst.toTypedArray())!!
    }

    companion object {
        @Suppress("UNUSED")
        fun getFlightDataStringAsKML(rgloc: Array<LatLong>): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val sb = StringBuilder()
            // Hack - this is brute force writing, not proper generation of XML.  But it works...
            // We are also assuming valid timestamps (i.e., we're using gx:Track)
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\">\r\n")
            sb.append("<Document>\r\n<Style id=\"redPoly\"><LineStyle><color>7f0000ff</color><width>4</width></LineStyle><PolyStyle><color>7f0000ff</color></PolyStyle></Style>\r\n")
            sb.append("<open>1</open>\r\n<visibility>1</visibility>\r\n<Placemark>\r\n\r\n<styleUrl>#redPoly</styleUrl><gx:Track>\r\n")
            if (rgloc.isNotEmpty() && rgloc[0] is LocSample) {
                sb.append("<extrude>1</extrude>\r\n<altitudeMode>absolute</altitudeMode>\r\n")
                for (ll in rgloc) {
                    val l = ll as LocSample
                    sb.append(
                        String.format(
                            Locale.US, "<when>%s</when>\r\n<gx:coord>%.8f %.8f %.1f</gx:coord>\r\n",
                            sdf.format(l.timeStamp),
                            l.longitude,
                            l.latitude,
                            l.altitude / MFBConstants.METERS_TO_FEET
                        )
                    )
                }
            } else {
                sb.append("<altitudeMode>clampToGround</altitudeMode>\r\n")
                for (ll in rgloc) {
                    sb.append(
                        String.format(
                            Locale.US, "<gx:coord>%.8f %.8f 0</gx:coord>\r\n",
                            ll.longitude,
                            ll.latitude
                        )
                    )
                }
            }
            sb.append("</gx:Track></Placemark></Document></kml>")
            return sb.toString()
        }
    }
}