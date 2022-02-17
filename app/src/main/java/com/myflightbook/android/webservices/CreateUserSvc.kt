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
import java.lang.Exception

class CreateUserSvc : MFBSoap() {
    fun fCreateUser(
        szEmail: String?, szPass: String?, szFirst: String?, szLast: String?,
        szQ: String?, szA: String?, c: Context?
    ): Boolean {
        var fResult = false
        val request = setMethod("CreateUser")
        request.addProperty("szAppToken", AuthToken.APPTOKEN)
        request.addProperty("szEmail", szEmail)
        request.addProperty("szPass", szPass)
        request.addProperty("szFirst", szFirst)
        request.addProperty("szLast", szLast)
        request.addProperty("szQuestion", szQ)
        request.addProperty("szAnswer", szA)
        val result = invoke(c) as SoapObject?
        if (result == null) lastError = "Error creating account - $lastError" else {
            try {
                // if we get here, we have success.
                AuthToken.m_szAuthToken = result.getProperty("szAuthToken").toString()
                AuthToken.m_szEmail = szEmail!!
                AuthToken.m_szPass = szPass!!
                fResult = true

                // Clear the aircraft cache because we need to reload it
                val ac = AircraftSvc()
                ac.flushCache()
            } catch (e: Exception) {
                lastError += e.message
            }
        }
        return fResult
    }
}