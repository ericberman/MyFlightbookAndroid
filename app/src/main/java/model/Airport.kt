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

import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.SoapObject
import android.location.Location
import android.util.Log
import com.myflightbook.android.MFBMain
import com.google.android.gms.maps.model.LatLngBounds
import org.ksoap2.serialization.PropertyInfo
import java.io.Serializable
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

class Airport : SoapableObject(), KvmSerializable, Serializable, Comparable<Airport> {
    @JvmField
    var airportID: String = ""
    @JvmField
    var facilityName: String = ""
    var facilityType: String = ""
    var country: String? = null
    var admin1: String? = null
    private var latitude = 0.0
    private var longitude = 0.0
    @JvmField
    var distance = 0.0
    private var isPreferred = false

    private enum class AirportProp {
        PIDAirportID, PIDFacilityName, PIDType, PIDLatitude, PIDLongitude, PIDDistance, PIDCountry, PIDAdmin1
    }

    override fun compareTo(other: Airport): Int {
        val d1 = (distance * 100.0).roundToInt() / 100.0
        val d2 = (other.distance * 100.0).roundToInt() / 100.0
        if (d1 < d2) return -1
        if (d1 > d2) return 1
        if (isPreferred && !other.isPreferred) return -1
        if (!isPreferred && other.isPreferred) return 1
        val l1 = airportID.length
        val l2 = other.airportID.length
        return l2.compareTo(l1)
    }

    override fun toString(): String {
        return String.format("%s (%s)", airportID, facilityName)
    }

    val latLong: LatLong
        get() = LatLong(latitude, longitude)

    fun isPort(): Boolean {
        return facilityType.compareTo("A") == 0 || facilityType.compareTo("H") == 0 || facilityType.compareTo("S") == 0
    }

    private fun navaidPriority(): Int {
        // Airports are always highest priority (pri-0)
        if (isPort()) return 0

        // Then VOR Types
        if (facilityType.compareTo("V") == 0 || facilityType.compareTo("C") == 0 || facilityType.compareTo("D") == 0 || facilityType.compareTo(
                "T"
            ) == 0
        ) return 1

        // Then NDB Types
        if (facilityType.compareTo("R") == 0 || facilityType.compareTo("RD") == 0 || facilityType.compareTo("M") == 0 || facilityType.compareTo(
                "MD"
            ) == 0 || facilityType.compareTo("U") == 0
        ) return 2

        // Generic fix
        return if (facilityType.compareTo("FX") == 0) 3 else 4
    }

    val location: Location
        get() {
            val l = Location("MFB")
            l.latitude = latitude
            l.longitude = longitude
            return l
        }

    override fun getProperty(i: Int): Any {
        return when (AirportProp.values()[i]) {
            AirportProp.PIDAirportID -> airportID
            AirportProp.PIDFacilityName -> facilityName
            AirportProp.PIDType -> facilityType
            AirportProp.PIDLatitude -> latitude
            AirportProp.PIDLongitude -> longitude
            AirportProp.PIDDistance -> distance
            AirportProp.PIDCountry -> country!!
            AirportProp.PIDAdmin1 -> admin1!!
        }
    }

    override fun getPropertyCount(): Int {
        return AirportProp.values().size
    }

    override fun getPropertyInfo(i: Int, h: Hashtable<*, *>?, pi: PropertyInfo) {
        when (AirportProp.values()[i]) {
            AirportProp.PIDAirportID -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Code"
            }
            AirportProp.PIDFacilityName -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Name"
            }
            AirportProp.PIDType -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "FacilityTypeCode"
            }
            AirportProp.PIDLatitude -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "Latitude"
            }
            AirportProp.PIDLongitude -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "Longitude"
            }
            AirportProp.PIDDistance -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "DistanceFromPosition"
            }
            AirportProp.PIDAdmin1 -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Admin1"
            }
            AirportProp.PIDCountry -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Country"
            }
        }
    }

    override fun setProperty(arg0: Int, arg1: Any) {}
    override fun toProperties(so: SoapObject) {}
    public override fun fromProperties(so: SoapObject) {
        airportID = so.getProperty("Code").toString()
        facilityName = so.getProperty("Name").toString()
        facilityType = so.getProperty("FacilityTypeCode").toString()
        latitude = so.getProperty("Latitude").toString().toDouble()
        longitude = so.getProperty("Longitude").toString().toDouble()
        distance = so.getProperty("DistanceFromPosition").toString().toDouble()
        country = so.getPropertySafelyAsString("Country")
        admin1 = so.getPropertySafelyAsString("Admin1")
    }

    companion object {
        private const val serialVersionUID = 1L
        @JvmField
        var fPrefIncludeHeliports = false
        private const val szNavaidPrefix = "@"
        private const val USAirportPrefix = "K"
        private const val minNavaidCodeLength = 2
        private const val minAirportCodeLength = 3
        private const val maxCodeLength = 6 // because of navaids, now allow up to 5 letters.
        private const val szRegAdHocFix =
            "$szNavaidPrefix\\b\\d{1,2}(?:[.,]\\d*)?[NS]\\d{1,3}(?:[.,]\\d*)?[EW]\\b" // Must have a digit on the left side of the decimal
        private val szRegexAirports = String.format(
            Locale.US, "((?:%s)|(?:@?\\b[A-Z0-9]{%d,%d}\\b))", szRegAdHocFix,
            minNavaidCodeLength.coerceAtMost(minAirportCodeLength), maxCodeLength
        )
        private const val szRegexAirportSearch = "!?@?[a-zA-Z0-9]+!?"

        /// <summary>
        /// Does this look like a US airport?
        /// </summary>
        /// <param name="szcode">The code</param>
        /// <returns>True if it looks like a US airport</returns>
        private fun isUSAirport(szcode: String): Boolean {
            return szcode.length == 4 && szcode.startsWith(USAirportPrefix)
        }

        /// <summary>
        /// To support the hack of typing "K" before an airport code in the US, we will see if Kxxx is hits on simply xxx
        /// </summary>
        /// <param name="szcode">The airport code</param>
        /// <returns>The code with the leading "K" stripped</returns>
        private fun prefixConvenienceAliasForUS(szcode: String): String {
            return if (isUSAirport(szcode)) szcode.substring(1) else szcode
        }

        @JvmStatic
        fun splitCodes(szRoute: String): Array<String> {
            val p = Pattern.compile(szRegexAirports, Pattern.CASE_INSENSITIVE)
            val m = p.matcher(szRoute.uppercase(Locale.getDefault()))
            val lst = ArrayList<String>()
            while (m.find()) lst.add(m.group(0)!!)
            return lst.toTypedArray()
        }

        fun splitCodesSearch(szRoute: String): Array<String> {
            val p = Pattern.compile(szRegexAirportSearch, Pattern.CASE_INSENSITIVE)
            val m = p.matcher(szRoute.uppercase(Locale.getDefault()))
            val lst = ArrayList<String>()
            while (m.find()) lst.add(m.group(0)!!)
            return lst.toTypedArray()
        }

        private fun adHocAirport(sz: String): Airport? {
            val ll = LatLong.fromString(sz) ?: return null
            val ap = Airport()
            ap.latitude = ll.latitude
            ap.longitude = ll.longitude
            ap.airportID = sz.replace(szNavaidPrefix, "")
            ap.distance = 0.0
            ap.facilityName = ll.toString()
            ap.facilityType = "FX"
            ap.isPreferred = false
            ap.country = ""
            ap.admin1 = ""
            return ap
        }

        private fun airportsFromRoute(szRoute: String?, loc: Location?): Array<Airport?> {
            val rgap = arrayOfNulls<Airport>(0)
            if (szRoute == null || szRoute.isEmpty()) return rgap
            val rgCodes = splitCodes(szRoute)
            val lstResults: MutableList<Airport> = ArrayList()
            val sb = StringBuilder()
            val pAdHoc = Pattern.compile(szRegAdHocFix, Pattern.CASE_INSENSITIVE)
            for (scode in rgCodes) {
                var s = scode
                if (pAdHoc.matcher(s).matches()) {
                    val apAdHoc = adHocAirport(s)
                    if (apAdHoc != null) lstResults.add(apAdHoc)
                    continue
                }
                if (sb.isNotEmpty()) sb.append(", ")
                if (s.startsWith(szNavaidPrefix)) s = s.substring(szNavaidPrefix.length)
                sb.append("'").append(s).append("'")
                if (isUSAirport(s)) sb.append(", '").append(prefixConvenienceAliasForUS(s)).append("'")
            }
            val szQ = String.format("AirportID IN (%s)", sb)
            lstResults.addAll(listOf(*airportsForQuery(szQ, loc)))
            return lstResults.toTypedArray()
        }

        @JvmStatic
        fun airportsInRouteOrder(szRoute: String, loc: Location?): Array<Airport> {
            val rgCodes = splitCodes(szRoute)
            val al = ArrayList<Airport>()
            val rgap = airportsFromRoute(szRoute, loc)
            val hm = HashMap<String?, Airport>()
            for (ap in rgap) {
                if (ap != null) {
                    if (ap.isPort()) hm[ap.airportID] = ap else {
                        val szKey = szNavaidPrefix + ap.airportID
                        val ap2 = hm[szKey]
                        if (ap2 == null || ap.navaidPriority() < ap2.navaidPriority()) hm[szKey] =
                            ap
                    }
                }
            }
            for (szCode in rgCodes) {
                var ap = hm[szCode]

                // If null, might be a navaid - check for the @ prefix
                if (ap == null) ap = hm[szNavaidPrefix + szCode]

                // Finally, check for K-hack (Kxxx vs. xxx)
                if (ap == null) ap = hm[prefixConvenienceAliasForUS(szCode)]
                if (ap != null) al.add(ap)
            }
            return al.toTypedArray()
        }

        private fun airportsForQuery(szQ: String, loc: Location?): Array<Airport> {
            val rgap: MutableList<Airport> = ArrayList()
            val db = MFBMain.mDBHelperAirports!!.readableDatabase
            try {
                db.query("airports", null, szQ, null, null, null, null).use { c ->
                    if (c != null) {
                        val colAirportId = c.getColumnIndexOrThrow("AirportID")
                        val colFacilityName = c.getColumnIndexOrThrow("FacilityName")
                        val colLat = c.getColumnIndexOrThrow("Latitude")
                        val colLon = c.getColumnIndexOrThrow("Longitude")
                        val colType = c.getColumnIndexOrThrow("Type")
                        val colPref = c.getColumnIndexOrThrow("Preferred")
                        val colCountry = c.getColumnIndexOrThrow("Country")
                        val colAdmin1 = c.getColumnIndexOrThrow("Admin1")
                        while (c.moveToNext()) {
                            val ap = Airport()
                            ap.airportID = c.getString(colAirportId)
                            ap.facilityName = c.getString(colFacilityName)
                            ap.latitude = c.getDouble(colLat)
                            ap.longitude = c.getDouble(colLon)
                            ap.facilityType = c.getString(colType)
                            ap.isPreferred = c.getInt(colPref) != 0
                            ap.country = if (c.isNull(colCountry)) "" else c.getString(colCountry)
                            ap.admin1 = if (c.isNull(colAdmin1)) "" else c.getString(colAdmin1)
                            if (loc != null) {
                                val lAirport = Location(loc)
                                lAirport.latitude = ap.latitude
                                lAirport.longitude = ap.longitude
                                ap.distance = lAirport.distanceTo(loc) * MFBConstants.METERS_TO_NM
                            } else ap.distance = 0.0
                            rgap.add(ap)
                        }

                        // sort the list by distance
                        rgap.sort()
                    }
                }
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, "Exception querying airports:" + e.message)
                // some of the airports could be null; just clear them all out
            }
            return rgap.toTypedArray()
        }

        @JvmStatic
        fun maxDistanceForRoute(szRoute: String?): Double {
            var dist = 0.0
            val rgDistResults = FloatArray(1)
            if (szRoute == null || szRoute.isEmpty()) return dist
            val rgAp = airportsFromRoute(szRoute, null)
            val cAirports = rgAp.size
            for (i in 0 until cAirports) {
                val ap1 = rgAp[i]
                if (ap1 == null || !ap1.isPort()) continue
                for (j in i + 1 until cAirports) {
                    val ap2 = rgAp[j]
                    if (!ap2!!.isPort()) continue
                    Location.distanceBetween(
                        ap1.latitude,
                        ap1.longitude,
                        ap2.latitude,
                        ap2.longitude,
                        rgDistResults
                    )
                    val d = rgDistResults[0] * MFBConstants.METERS_TO_NM
                    if (d > dist) dist = d
                }
            }
            return dist
        }

        private fun getClosestAirport(loc: Location?): Airport? {
            if (loc == null) return null
            val rgap = getNearbyAirports(loc, 0.5, 0.5)
            return if (rgap.isNotEmpty()) rgap[0] else null
        }

        private fun getNearbyAirports(
            loc: Location,
            minLat: Double,
            minLong: Double,
            maxLat: Double,
            maxLong: Double
        ): Array<Airport> {
            val szTypes = if (fPrefIncludeHeliports) "('H', 'A', 'S')" else "('A', 'S')"
            val szQTemplate =
                "(latitude BETWEEN %.8f AND %.8f) AND (longitude BETWEEN %.8f AND %.8f) AND Type IN %s"
            val szQ =
                String.format(Locale.US, szQTemplate, minLat, maxLat, minLong, maxLong, szTypes)
            return airportsForQuery(szQ, loc)
        }

        fun getNearbyAirports(loc: Location?, dLat: Double, dLon: Double): Array<Airport> {
            val minLat: Double
            val maxLat: Double
            val minLong: Double
            val maxLong: Double
            if (loc == null) return arrayOf()
            val lat: Double = loc.latitude
            val lon: Double = loc.longitude

            // BUG: this doesn't work if we cross 180 degrees, but there are so few airports it shouldn't matter
            minLat = (lat - dLat / 2.0).coerceAtLeast(-90.0)
            maxLat = (lat + dLat / 2.0).coerceAtMost(90.0)
            minLong = lon - dLon / 2.0
            maxLong = lon + dLon / 2.0
            // we don't bother correcting lons below -180 or above +180 for the reason above
            return getNearbyAirports(loc, minLat, minLong, maxLat, maxLong)
        }

        fun getNearbyAirports(loc: Location, llb: LatLngBounds): Array<Airport> {
            return getNearbyAirports(
                loc,
                llb.southwest.latitude,
                llb.southwest.longitude,
                llb.northeast.latitude,
                llb.northeast.longitude
            )
        }

        @JvmStatic
        fun appendCodeToRoute(szRouteSoFar: String, code: String?): String {
            val sz = szRouteSoFar.uppercase(Locale.getDefault())
            val szCode = code!!.uppercase(Locale.getDefault())
            if (sz.endsWith(szCode)) return sz
            return if (sz.isEmpty()) szCode else sz.trim { it <= ' ' } + " " + szCode
        }

        @JvmStatic
        fun appendNearestToRoute(szRouteSoFar: String, loc: Location?): String {
            val ap = getClosestAirport(loc) ?: return szRouteSoFar
            return appendCodeToRoute(szRouteSoFar, ap.airportID)
        }
    }
}