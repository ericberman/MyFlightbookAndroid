/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2018-2022 MyFlightbook, LLC

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

public class CannedQuerySvc extends MFBSoap {
    @Override
    public void AddMappings(SoapSerializationEnvelope e) {
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo.class);
        e.addMapping(NAMESPACE, "LatLong", LatLong.class);
        e.addMapping(NAMESPACE, "LogbookEntry", LogbookEntry.class);
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

    private CannedQuery[] QueriesFromResult(SoapObject result) {
        CannedQuery[] rgcq = new CannedQuery[result.getPropertyCount()];

        for (int i = 0; i < rgcq.length; i++) {
            rgcq[i] = new CannedQuery();
            rgcq[i].FromProperties((SoapObject) result.getProperty(i));
        }
        return rgcq;
    }

    public CannedQuery[] GetNamedQueriesForUser(String szAuthToken, Context c) {
        SoapObject Request = setMethod("GetNamedQueriesForUser");
        Request.addProperty("szAuthToken", szAuthToken);

        CannedQuery[] rgcq = new CannedQuery[0];

        SoapObject result = (SoapObject) Invoke(c);
        if (result == null)
            setLastError("Error retrieving totals - " + getLastError());
        else {
            try {
                rgcq = QueriesFromResult(result);
            } catch (Exception e) {
                setLastError(getLastError() + e.getMessage());
            }
        }

        return rgcq;
    }

    public CannedQuery[] DeleteNamedQueryForUser(String szAuthToken, CannedQuery cq, Context c) {
        SoapObject Request = setMethod("DeleteNamedQueryForUser");
        Request.addProperty("szAuthToken", szAuthToken);
        Request.addProperty("cq", cq);

        CannedQuery[] rgcq = new CannedQuery[0];

        SoapObject result = (SoapObject) Invoke(c);
        if (result == null)
            setLastError("Error retrieving totals - " + getLastError());
        else {
            try {
                rgcq = QueriesFromResult(result);
            } catch (Exception e) {
                setLastError(getLastError() + e.getMessage());
            }
        }

        return rgcq;
    }

    public CannedQuery[] AddNamedQueryForUser(String szAuthToken, String szName, FlightQuery fq, Context c) {
        SoapObject Request = setMethod("AddNamedQueryForUser");
        Request.addProperty("szAuthToken", szAuthToken);
        Request.addProperty("fq", fq);
        Request.addProperty("szName", szName);

        CannedQuery[] rgcq = new CannedQuery[0];

        SoapObject result = (SoapObject) Invoke(c);
        if (result == null)
            setLastError("Error retrieving totals - " + getLastError());
        else {
            try {
                rgcq = QueriesFromResult(result);
            } catch (Exception e) {
                setLastError(getLastError() + e.getMessage());
            }
        }

        return rgcq;
    }
}
