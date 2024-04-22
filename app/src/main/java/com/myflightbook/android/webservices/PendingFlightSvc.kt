/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2021-2024 MyFlightbook, LLC

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
import model.PendingFlight
import model.FlightProperty
import model.Aircraft
import model.MakeModel
import model.CategoryClass
import model.CustomPropertyType
import model.FlightQuery
import model.CannedQuery
import com.myflightbook.android.marshal.MarshalDouble
import org.ksoap2.serialization.SoapObject
import com.myflightbook.android.marshal.MarshalDate
import model.MFBUtil
import java.lang.Exception

class PendingFlightSvc : MFBSoap() {
    override fun addMappings(e: SoapSerializationEnvelope) {
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo::class.java)
        e.addMapping(NAMESPACE, "LatLong", LatLong::class.java)
        e.addMapping(NAMESPACE, "LogbookEntry", LogbookEntry::class.java)
        e.addMapping(NAMESPACE, "PendingFlight", PendingFlight::class.java)
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

    private fun readResults(result: SoapObject?): Array<PendingFlight> {
        if (result == null) return arrayOf()
        val rgpf: ArrayList<PendingFlight> = ArrayList()
        try {
            for (i in 0 until result.propertyCount)
                rgpf.add(PendingFlight(result.getProperty(i) as SoapObject))
        } catch (e: Exception) {
            lastError += e.message
        }
        return rgpf.toTypedArray()
    }

    fun createPendingFlight(
        szAuthToken: String?,
        le: LogbookEntry,
        c: Context?
    ): Array<PendingFlight> {

        // Issue #308: As with committing non-pending flights, save the date, since we're making a live copy
        // Since date is always local, we always pass up a UTC date that looks
        // like the date/time we want.
        val dtSave = le.dtFlight
        le.dtFlight = MFBUtil.getUTCDateFromLocalDate(le.dtFlight)

        val request = setMethod("CreatePendingFlight")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("le", le)
        val result = readResults(invoke(c) as SoapObject?)
        le.dtFlight = dtSave
        return result
    }

    fun getPendingFlightsForUser(szAuthToken: String?, c: Context?): Array<PendingFlight> {
        val request = setMethod("PendingFlightsForUser")
        request.addProperty("szAuthUserToken", szAuthToken)
        return readResults(invoke(c) as SoapObject?)
    }

    fun updatePendingFlight(
        szAuthToken: String?,
        pf: PendingFlight?,
        c: Context?
    ): Array<PendingFlight> {
        val request = setMethod("UpdatePendingFlight")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("pf", pf)
        return readResults(invoke(c) as SoapObject?)
    }

    fun deletePendingFlight(
        szAuthToken: String?,
        idpending: String?,
        c: Context?
    ): Array<PendingFlight> {
        val request = setMethod("DeletePendingFlight")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("idpending", idpending)
        return readResults(invoke(c) as SoapObject?)
    }

    fun commitPendingFlight(
        szAuthToken: String?,
        pf: PendingFlight,
        c: Context?
    ): Array<PendingFlight> {
        val request = setMethod("CommitPendingFlight")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("pf", pf)
        val szPendingID = pf.getPendingID()
        val rgpf = readResults(invoke(c) as SoapObject?)
        for (pfresult in rgpf) {
            if (pfresult.getPendingID().compareTo(
                    szPendingID,
                    ignoreCase = true
                ) == 0 && pfresult.szError.isNotEmpty()
            ) lastError = pfresult.szError
        }
        return rgpf
    }
}