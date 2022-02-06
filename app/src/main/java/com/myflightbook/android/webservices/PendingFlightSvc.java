/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2021-2022 MyFlightbook, LLC

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

import com.myflightbook.android.marshal.MarshalDate;
import com.myflightbook.android.marshal.MarshalDouble;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;

import model.Aircraft;
import model.CannedQuery;
import model.CategoryClass;
import model.CustomPropertyType;
import model.FlightProperty;
import model.FlightQuery;
import model.LatLong;
import model.LogbookEntry;
import model.MFBImageInfo;
import model.MakeModel;
import model.PendingFlight;

public class PendingFlightSvc extends MFBSoap {
    public PendingFlightSvc() {
    }

    @Override
    public void AddMappings(SoapSerializationEnvelope e) {
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo.class);
        e.addMapping(NAMESPACE, "LatLong", LatLong.class);
        e.addMapping(NAMESPACE, "LogbookEntry", LogbookEntry.class);
        e.addMapping(NAMESPACE, "PendingFlight", PendingFlight.class);
        e.addMapping(NAMESPACE, "CustomFlightProperty", FlightProperty.class);
        e.addMapping(NAMESPACE, "Aircraft", Aircraft.class);
        e.addMapping(NAMESPACE, "MakeModel", MakeModel.class);
        e.addMapping(NAMESPACE, "CategoryClass", CategoryClass.class);
        e.addMapping(NAMESPACE, "CustomPropertyType", CustomPropertyType.class);
        e.addMapping(NAMESPACE, "FlightQuery", FlightQuery.class);
        e.addMapping(NAMESPACE, "CannedQuery", CannedQuery.class);

        MarshalDate mdt = new MarshalDate();
        mdt.register(e);
        MarshalDouble md = new MarshalDouble();
        md.register(e);
    }

    private PendingFlight[] ReadResults(SoapObject result) {
        if (result == null)
            return new PendingFlight[0];

        PendingFlight[] rgpf;
        try {
            rgpf = new PendingFlight[result.getPropertyCount()];

            for (int i = 0; i < rgpf.length; i++)
                rgpf[i] = new PendingFlight((SoapObject) result.getProperty(i));
        } catch (Exception e) {
            rgpf = new PendingFlight[0]; // don't want to show any bad data!
            setLastError(getLastError() + e.getMessage());
        }

        return rgpf;
    }

    public PendingFlight[] CreatePendingFlight(String szAuthToken, LogbookEntry le, Context c) {
        SoapObject Request = setMethod("CreatePendingFlight");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("le", le);

        return ReadResults((SoapObject) Invoke(c));
    }

    public PendingFlight[] PendingFlightsForUser(String szAuthToken, Context c) {
        SoapObject Request = setMethod("PendingFlightsForUser");
        Request.addProperty("szAuthUserToken", szAuthToken);

        return ReadResults((SoapObject) Invoke(c));
    }

    public PendingFlight[] UpdatePendingFlight(String szAuthToken, PendingFlight pf, Context c) {
        SoapObject Request = setMethod("UpdatePendingFlight");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("pf", pf);

        return ReadResults((SoapObject) Invoke(c));
    }

    public PendingFlight[] DeletePendingFlight(String szAuthToken, String idpending, Context c) {
        SoapObject Request = setMethod("DeletePendingFlight");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("idpending", idpending);

        return ReadResults((SoapObject) Invoke(c));
    }

    public PendingFlight[] CommitPendingFlight(String szAuthToken, PendingFlight pf, Context c) {
        SoapObject Request = setMethod("CommitPendingFlight");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("pf", pf);

        String szPendingID = pf.getPendingID();
        PendingFlight[] rgpf = ReadResults((SoapObject) Invoke(c));

        if (rgpf != null) {
            for (PendingFlight pfresult : rgpf) {
                if (pfresult.getPendingID().compareToIgnoreCase(szPendingID) == 0 && pfresult.szError != null && pfresult.szError.length() > 0)
                    setLastError(pfresult.szError);
            }
        }
        return rgpf;
    }
}
