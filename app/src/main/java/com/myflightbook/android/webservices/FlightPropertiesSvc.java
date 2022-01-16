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
package com.myflightbook.android.webservices;

import android.content.Context;

import org.ksoap2.serialization.SoapObject;

public class FlightPropertiesSvc extends MFBSoap {

    public void DeletePropertyForFlight(String szAuthToken, int idFlight, int propId, Context c) {
        SoapObject Request = setMethod("DeletePropertyForFlight");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("idFlight", idFlight);
        Request.addProperty("propId", propId);

        Invoke(c);
    }
}