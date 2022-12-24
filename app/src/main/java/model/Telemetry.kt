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
import android.location.Location
import android.net.Uri
import android.util.Xml
import com.myflightbook.android.webservices.UTCDate.getUTCCalendar
import com.myflightbook.android.webservices.UTCDate.isNullDate
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.*
import java.util.*
import java.util.regex.Pattern
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Created by ericberman on 11/29/17.
 *
 * Base class for telemetry that can be imported/exported.
 */
abstract class Telemetry internal constructor(uri: Uri?, c: Context?) {
    enum class ImportedFileType {
        GPX, KML, CSV, Unknown
    }

    private var mUri: Uri? = uri
    private var mCtxt: Context? = c
    private var mpISODate: Pattern? = null
    var metaData: Hashtable<String, Any>? = null

    init {
        metaData = Hashtable()
    }

    @Suppress("UNUSED")
    fun getMetaData(): Dictionary<String, Any>? {
        return metaData
    }

    fun computeSpeed(rgSamples: Array<LocSample>?): Array<LocSample>? {
        if (rgSamples == null || rgSamples.isEmpty()) return rgSamples
        var refSample = rgSamples[0]
        refSample.speed = 0.0
        for (i in 1 until rgSamples.size) {
            val elapsedMS =
                rgSamples[i].timeStamp.time - refSample.timeStamp.time
            if (elapsedMS <= 0) {
                rgSamples[i].speed = refSample.speed
            } else {
                val dist = rgSamples[i].location.distanceTo(refSample.location).toDouble()
                rgSamples[i].speed = MFBConstants.MPS_TO_KNOTS * (dist * 1000) / elapsedMS
                refSample = rgSamples[i]
            }
        }
        return rgSamples
    }

    fun parseUTCDate(s: String): Date {
        if (mpISODate == null) {
            mpISODate = Pattern.compile(
                "(\\d+)-(\\d+)-(\\d+)T? ?(\\d+):(\\d+):(\\d+\\.?\\d*)Z?",
                Pattern.CASE_INSENSITIVE
            )
        }
        val m = mpISODate!!.matcher(s)
        if (m.matches()) {
            val year = Objects.requireNonNull(m.group(1)).toInt()
            val month = Objects.requireNonNull(m.group(2)).toInt()
            val day = Objects.requireNonNull(m.group(3)).toInt()
            val hour = Objects.requireNonNull(m.group(4)).toInt()
            val minute = Objects.requireNonNull(m.group(5)).toInt()
            val second = Objects.requireNonNull(m.group(6)).toDouble()
            val gc = getUTCCalendar()
            gc[year, month - 1, day, hour, minute] = second.toInt()
            return gc.time
        }
        return Date()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    @Throws(XmlPullParserException::class, IOException::class)
    private fun parse(s: InputStream): Array<LocSample> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(s, null)
        parser.nextTag()
        return readFeed(parser)
    }

    // Follows the instructions at https://developer.android.com/training/basics/network-ops/xml.html
    @Throws(XmlPullParserException::class, IOException::class)
    private fun parse(): Array<LocSample> {
        mCtxt!!.contentResolver.openInputStream(mUri!!).use { `in` -> return parse(`in`!!) }
    }

    protected abstract fun readFeed(parser: XmlPullParser?): Array<LocSample>
    @Throws(IOException::class, XmlPullParserException::class)
    fun samples(): Array<LocSample> {
        return parse()
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun samples(sz: String): Array<LocSample> {
        return parse(ByteArrayInputStream(sz.toByteArray()))
    }

    companion object {
        const val TELEMETRY_METADATA_TAIL = "aircraft"
        @Throws(IOException::class)
        private fun typeFromBufferedReader(br: BufferedReader): ImportedFileType {
            var result = ImportedFileType.Unknown
            var s: String?
            while (br.readLine().also { s = it } != null) {
                val s2 = s!!.uppercase(Locale.ENGLISH)
                if (s2.contains("GPX")) {
                    result = ImportedFileType.GPX
                    break
                } else if (s2.contains("KML")) {
                    result = ImportedFileType.KML
                    break
                }
            }
            return result
        }

        @Throws(IOException::class)
        fun typeFromString(sz: String?): ImportedFileType {
            return typeFromBufferedReader(BufferedReader(StringReader(sz)))
        }

        @Throws(IOException::class)
        private fun typeFromUri(data: Uri, c: Context): ImportedFileType {
            val `in` = c.contentResolver.openInputStream(data)
            return typeFromBufferedReader(
                BufferedReader(
                    InputStreamReader(
                        Objects.requireNonNull(
                            `in`
                        )
                    )
                )
            )
        }

        @JvmStatic
        @Throws(IOException::class)
        fun telemetryFromURL(uri: Uri?, c: Context): Telemetry? {
            return if (uri == null) null else when (typeFromUri(uri, c)) {
                ImportedFileType.GPX -> GPX(uri, c)
                ImportedFileType.KML -> KML(uri, c)
                else -> null
            }
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
        fun synthesizePath(
            llStart: Location,
            dtStart: Date,
            llEnd: Location,
            dtEnd: Date
        ): Array<LocSample> {
            val lst = ArrayList<LocSample>()
            if (isNullDate(dtStart) || isNullDate(dtEnd)) return lst.toTypedArray()
            val rlat1 = Math.PI * (llStart.latitude / 180.0)
            val rlon1 = Math.PI * (llStart.longitude / 180.0)
            val rlat2 = Math.PI * (llEnd.latitude / 180.0)
            val rlon2 = Math.PI * (llEnd.longitude / 180.0)
            val dLon = rlon2 - rlon1
            val delta = atan2(
                sin(dLon) * cos(rlat2),
                cos(rlat1) * sin(rlat2) - sin(rlat1) * cos(rlat2) * cos(
                    dLon
                )
            )
            // double delta = 2 * Math.asin(Math.sqrt(Math.Pow((sin((rlat1 - rlat2) / 2)), 2) + Math.cos(rlat1) * Math.cos(rlat2) * Math.Pow(sin((rlon1 - rlon2) / 2), 2)));
            val sinDelta = sin(delta)

            // Compute path at 1-minute intervals, subtracting off one minute since we'll add a few "full-stop" samples below.
            val ts = (dtEnd.time - dtStart.time) / 1000 // time in seconds
            val minutes = ts / 60.0 - 1
            if (minutes > 48 * 60 || minutes <= 0) // don't do paths more than 48 hours, or negative times.
                return lst.toTypedArray()

            // Add a few stopped fields at the end to make it clear that there's a full-stop.  Separate them by a few seconds each.
            val rgPadding = arrayOf(
                LocSample(llEnd, 0, 0.2, Date(dtEnd.time + 3000)),
                LocSample(llEnd, 0, 0.2, Date(dtEnd.time + 6000)),
                LocSample(llEnd, 0, 0.2, Date(dtEnd.time + 9000))
            )

            // We need to derive an average speed.  But no need to compute - just assume constant speed.
            val distanceM = llStart.distanceTo(llEnd).toDouble() // distance here is in meters
            val distanceNM = distanceM * MFBConstants.METERS_TO_NM
            val speedMS = distanceM / ts // we know that ts is >= 0 because of minutes check above

            // low distance (< 1nm) is probably pattern work - just pick a decent speed.  If you actually go somewhere, then derive a speed.
            val speedKts = if (distanceNM < 1.0) 150.0 else speedMS * MFBConstants.MPS_TO_KNOTS
            lst.add(LocSample(llStart, 0, speedKts, dtStart))
            var minute: Long = 0
            while (minute <= minutes) {
                if (distanceNM < 1.0) lst.add(
                    LocSample(
                        llStart,
                        0,
                        speedKts,
                        Date(dtStart.time + minute * 60000)
                    )
                ) else {
                    val f = minute.toDouble() / minutes
                    val a = sin((1.0 - f) * delta) / sinDelta
                    val b = sin(f * delta) / sinDelta
                    val x =
                        a * cos(rlat1) * cos(rlon1) + b * cos(rlat2) * cos(rlon2)
                    val y =
                        a * cos(rlat1) * sin(rlon1) + b * cos(rlat2) * sin(rlon2)
                    val z = a * sin(rlat1) + b * sin(rlat2)
                    val rlat = atan2(z, sqrt(x * x + y * y))
                    val rlon = atan2(y, x)
                    val l = Location(llStart)
                    l.latitude = 180 * (rlat / Math.PI)
                    l.longitude = 180 * (rlon / Math.PI)
                    lst.add(LocSample(l, 0, speedKts, Date(dtStart.time + minute * 60000)))
                }
                minute++
            }
            lst.addAll(listOf(*rgPadding))
            return lst.toTypedArray()
        }
    }
}