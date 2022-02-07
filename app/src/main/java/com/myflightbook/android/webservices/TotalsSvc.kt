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
import com.myflightbook.android.marshal.MarshalDate
import model.Totals
import java.lang.Exception

class TotalsSvc : MFBSoap() {
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

    fun getTotalsForUser(szAuthToken: String?, fq: FlightQuery?, c: Context?): Array<Totals> {
        val request = setMethod("TotalsForUserWithQuery")
        request.addProperty("szAuthToken", szAuthToken)
        request.addProperty("fq", fq ?: FlightQuery())
        val rgt : ArrayList<Totals> = ArrayList()
        val result = invoke(c) as SoapObject?
        if (result == null) lastError = "Error retrieving totals - $lastError" else {
            try {
                for (i in 0 until result.propertyCount) {
                    rgt.add(Totals(result.getProperty(i) as SoapObject))
                }
            } catch (e: Exception) {
                lastError += e.message
            }
        }
        return rgt.toTypedArray()
    }
}