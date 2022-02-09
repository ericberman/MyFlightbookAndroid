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
import com.myflightbook.android.marshal.MarshalDate
import com.myflightbook.android.marshal.MarshalDouble
import model.*
import org.ksoap2.serialization.SoapObject
import org.ksoap2.serialization.SoapSerializationEnvelope

class VisitedAirportSvc : MFBSoap() {
    override fun addMappings(e: SoapSerializationEnvelope) {
        e.addMapping(NAMESPACE, "LatLong", LatLong::class.java)
        e.addMapping(NAMESPACE, "Airport", Airport::class.java)
        val mdt = MarshalDate()
        mdt.register(e)
        val md = MarshalDouble()
        md.register(e)
    }

    fun getVisitedAirportsForUser(szAuthToken: String?, c: Context): Array<VisitedAirport> {
        val request = setMethod("VisitedAirports")
        request.addProperty("szAuthToken", szAuthToken)
        val rgva : ArrayList<VisitedAirport> = ArrayList()
        val result = invoke(c) as SoapObject?
        if (result == null)
            return arrayOf()
        val l = MFBLocation.lastSeenLoc()
        try {
            for (i in 0 until result.propertyCount) {
                val va = VisitedAirport(result.getProperty(i) as SoapObject)
                rgva.add(va)
                if (l != null && va.airport != null)
                    va.airport!!.distance = l.distanceTo(va.airport!!.location) * MFBConstants.METERS_TO_NM
            }
        } catch (e: Exception) {
            lastError += e.message
        }
        return rgva.toTypedArray()
    }
}