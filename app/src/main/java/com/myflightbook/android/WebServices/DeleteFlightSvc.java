/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017 MyFlightbook, LLC

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
package com.myflightbook.android.WebServices;

import android.content.Context;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapPrimitive;

public class DeleteFlightSvc extends MFBSoap {

    public void DeleteFlight(String szAuthToken, int idFlight, Context c) {
        SoapObject Request = setMethod("DeleteLogbookEntry");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("idFlight", idFlight);

        SoapPrimitive result = (SoapPrimitive) Invoke(c);
        if (result == null)
            setLastError("Error deleting flight - " + getLastError());
    }
}
