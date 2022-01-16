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
import android.location.Location;

import com.myflightbook.android.marshal.MarshalDate;
import com.myflightbook.android.marshal.MarshalDouble;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;

import model.Airport;
import model.LatLong;
import model.MFBConstants;
import model.MFBLocation;
import model.VisitedAirport;

public class VisitedAirportSvc extends MFBSoap {

    @Override
    public void AddMappings(SoapSerializationEnvelope e) {
        e.addMapping(NAMESPACE, "LatLong", LatLong.class);
        e.addMapping(NAMESPACE, "Airport", Airport.class);

        MarshalDate mdt = new MarshalDate();
        mdt.register(e);
        MarshalDouble md = new MarshalDouble();
        md.register(e);
    }

    public VisitedAirport[] VisitedAirportsForUser(String szAuthToken, Context c) {
        SoapObject Request = setMethod("VisitedAirports");
        Request.addProperty("szAuthToken", szAuthToken);

        VisitedAirport[] rgva = new VisitedAirport[0];

        SoapObject result = (SoapObject) Invoke(c);
        if (result == null)
            setLastError("Error retrieving visited airports - " + getLastError());
        else {
            Location l = MFBLocation.LastSeenLoc();

            try {
                rgva = new VisitedAirport[result.getPropertyCount()];

                for (int i = 0; i < rgva.length; i++) {
                    rgva[i] = new VisitedAirport((SoapObject) result.getProperty(i));
                    if (l != null)
                        rgva[i].airport.Distance = l.distanceTo(rgva[i].airport.getLocation()) * MFBConstants.METERS_TO_NM;
                }
            } catch (Exception e) {
                setLastError(getLastError() + e.getMessage());
            }
        }

        return rgva;
    }
}
