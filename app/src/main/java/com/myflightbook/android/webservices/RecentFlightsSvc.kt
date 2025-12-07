/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025 MyFlightbook, LLC

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
package com.myflightbook.android.webservices

import android.content.Context
import org.ksoap2.serialization.SoapSerializationEnvelope
import model.MFBImageInfo
import model.LatLong
import model.LogbookEntry
import model.FlightProperty
import model.Aircraft
import model.MakeModel
import model.CategoryClass
import model.CustomPropertyType
import model.FlightQuery
import model.CannedQuery
import com.myflightbook.android.marshal.MarshalDouble
import org.ksoap2.serialization.SoapObject
import org.ksoap2.serialization.SoapPrimitive
import android.util.Log
import com.myflightbook.android.MFBMain
import model.MFBConstants
import com.myflightbook.android.ActCurrency
import com.myflightbook.android.ActTotals
import com.myflightbook.android.marshal.MarshalDate
import model.PackAndGo
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class RecentFlightsSvc : MFBSoap() {
    override fun addMappings(e: SoapSerializationEnvelope) {
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo::class.java)
        e.addMapping(NAMESPACE, "LatLong", LatLong::class.java)
        e.addMapping(NAMESPACE, "LogbookEntry", LogbookEntry::class.java)
        e.addMapping(NAMESPACE, "CustomFlightProperty", FlightProperty::class.java)
        e.addMapping(NAMESPACE, "Aircraft", Aircraft::class.java)
        e.addMapping(NAMESPACE, "MakeModel", MakeModel::class.java)
        e.addMapping(NAMESPACE, "CategoryClass", CategoryClass::class.java)
        e.addMapping(NAMESPACE, "CustomPropertyType", CustomPropertyType::class.java)
        e.addMapping(NAMESPACE, "FlightQuery", FlightQuery::class.java)
        e.addMapping(NAMESPACE, "CannedQuery", CannedQuery::class.java)
        val mdt = MarshalDate()
        mdt.register(e)
        val md = MarshalDouble()
        md.register(e)
    }

    private fun readResults(result: SoapObject): Array<LogbookEntry> {
        val rgLe : ArrayList<LogbookEntry> = ArrayList()
        try {
            for (i in 0 until result.propertyCount)
                rgLe.add(LogbookEntry(result.getProperty(i) as SoapObject))
        } catch (e: Exception) {
            lastError += e.message
        }
        return rgLe.toTypedArray()
    }

    fun getRecentFlightsWithQueryAndOffset(
        szAuthToken: String?,
        fq: FlightQuery?,
        offset: Int,
        limit: Int,
        c: Context?
    ): Array<LogbookEntry> {
        val rgLe: ArrayList<LogbookEntry> = ArrayList()
        val request = setMethod("FlightsWithQueryAndOffset")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("fq", fq ?: FlightQuery())
        request.addProperty("offset", offset)
        request.addProperty("maxCount", limit)
        val result = invoke(c) as SoapObject? ?: return rgLe.toTypedArray()

        rgLe.addAll(readResults(result))
        // Update high-watermark hobbs/tach for the aircraft
        for (le in rgLe) {
            Aircraft.updateHobbsForAircraft(le.hobbsEnd, le.idAircraft)
            if (le.rgCustomProperties.isNotEmpty()) {
                for (fp in le.rgCustomProperties) {
                    if (fp.idPropType == CustomPropertyType.ID_PROP_TYPE_TACH_END) {
                        Aircraft.updateTachForAircraft(fp.decValue, le.idAircraft)
                        break
                    }
                    if (fp.idPropType == CustomPropertyType.ID_PROP_TYPE_FLIGHT_METER_END) {
                        Aircraft.updateFlightMeterForAircraft(fp.decValue, le.idAircraft)
                        break
                    }
                }
            }
        }

        val alle = ArrayList<LogbookEntry>()
        if (m_CachedFlights != null) alle.addAll(listOf(*m_CachedFlights!!))
        alle.addAll(rgLe)
        m_CachedFlights = alle.toTypedArray()
        clearOrphanedExistingFlightsFromDB()
        return rgLe.toTypedArray()
    }

    fun getFlightPathForFlight(szAuthToken: String?, idFlight: Int, c: Context?): Array<LatLong> {
        var rgll : Array<LatLong> = arrayOf()
        val request = setMethod("FlightPathForFlight")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("idFlight", idFlight)
        val result = invoke(c) as SoapObject?
        if (result == null) lastError = "Failed to get path for flight - $lastError" else {
            try {
                val rg : Array<LatLong?> = arrayOfNulls(result.propertyCount)
                for (i in rg.indices) rg[i] = LatLong(result.getProperty(i) as SoapObject)
                rgll = rg.requireNoNulls()
            } catch (e: Exception) {
                rgll = arrayOf()
                lastError += e.message
            }
        }
        return rgll
    }

    fun getFlightPathForFlightGPX(szAuthToken: String?, idFlight: Int, c: Context?): String {
        val request = setMethod("FlightPathForFlightGPX")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("idFlight", idFlight)
        val result = invoke(c) as SoapPrimitive?
        return if (result == null) {
            lastError = "Failed to get GPX path for flight - $lastError"
            ""
        } else {
            result.toString()
        }
    }

    companion object {
        private var m_CachedFlights: Array<LogbookEntry>? = null

        // Flight caching
        private fun clearOrphanedExistingFlightsFromDB() {
            val db = MFBMain.mDBHelper!!.writableDatabase
            var rgLocalIDs = IntArray(0)
            try {
                db.query("Flights", null, "idFlight > 0", null, null, null, null).use { c ->

                    // get the local ID's of the orphaned entries.
                    var i = 0
                    rgLocalIDs = IntArray(c.count)
                    while (c.moveToNext()) rgLocalIDs[i++] =
                        c.getInt(c.getColumnIndexOrThrow("_id"))
                }
            } catch (ex: Exception) {
                Log.e(MFBConstants.LOG_TAG, Objects.requireNonNull(ex.localizedMessage))
            }

            // now clean up the local ID's that were found
            for (i in rgLocalIDs) {
                val le = LogbookEntry()
                le.idLocalDB = i.toLong()
                le.deleteUnsubmittedFlightFromLocalDB()
            }
        }

        @JvmStatic
        fun hasCachedFlights(): Boolean {
            return m_CachedFlights != null
        }

        @JvmStatic
        fun clearCachedFlights() {
            m_CachedFlights = null
            clearOrphanedExistingFlightsFromDB()
            // this is a bit of a hack, but it's a nice central point to invalidate totals and currency
            ActCurrency.setNeedsRefresh(true)
            ActTotals.setNeedsRefresh(true)
        }

        @JvmStatic
        fun getCachedFlightByID(id: Int, c: Context?): LogbookEntry? {
            if (hasCachedFlights()) {
                for (le in m_CachedFlights!!) {
                    if (le.idFlight == id) return le
                }
            }

            // see if we have a packed flight.
            if (c != null) {
                val p = PackAndGo(c)
                if (p.lastFlightsPackDate() != null) {
                    val rgle = p.cachedFlights()
                    if (rgle != null)
                        for (le in rgle) if (le.idFlight == id) return le
                }
            }
            return null
        }
    }
}