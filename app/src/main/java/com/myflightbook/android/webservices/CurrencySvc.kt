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
import model.CurrencyStatusItem
import android.content.Context
import java.lang.Exception

class CurrencySvc : MFBSoap() {
    fun getCurrencyForUser(szAuthToken: String?, c: Context?): Array<CurrencyStatusItem> {
        val request = setMethod("GetCurrencyForUser")
        request.addProperty("szAuthToken", szAuthToken)
        val rgCsi : ArrayList<CurrencyStatusItem> = ArrayList()
        val result = invoke(c) as SoapObject?
        if (result == null) lastError = "Error retrieving currency - $lastError" else {
            try {
                for (i in 0 until result.propertyCount) {
                    rgCsi.add(CurrencyStatusItem(result.getProperty(i) as SoapObject))
                }
            } catch (e: Exception) {
                lastError += e.message
            }
        }
        return rgCsi.toTypedArray()
    }
}