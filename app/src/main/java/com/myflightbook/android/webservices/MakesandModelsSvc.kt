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

import org.ksoap2.serialization.SoapObject
import android.content.Context
import model.MakesandModels
import java.lang.Exception

class MakesandModelsSvc : MFBSoap() {
    fun getMakesAndModels(c: Context?): Array<MakesandModels> {
        setMethod("MakesAndModels") // no need to save the request, since nothing goes out
        val rgMM : ArrayList<MakesandModels> = ArrayList()
        val result = invoke(c) as SoapObject?
        if (result == null) lastError = "Error retrieving makes and models - $lastError" else {
            try {
                for (i in 0 until result.propertyCount) {
                    rgMM.add(MakesandModels(result.getProperty(i) as SoapObject))
                }
            } catch (e: Exception) {
                lastError += e.message
            }
        }
        return rgMM.toTypedArray()
    }
}