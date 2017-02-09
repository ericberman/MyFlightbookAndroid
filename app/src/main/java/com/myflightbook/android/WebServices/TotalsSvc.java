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

import com.myflightbook.android.ActFlightQuery;
import com.myflightbook.android.Marshal.MarshalDate;
import com.myflightbook.android.Marshal.MarshalDouble;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;

import Model.Aircraft;
import Model.CategoryClass;
import Model.CustomPropertyType;
import Model.FlightProperty;
import Model.FlightQuery;
import Model.LatLong;
import Model.LogbookEntry;
import Model.MFBImageInfo;
import Model.MakeModel;
import Model.Totals;

public class TotalsSvc extends MFBSoap {
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

        MarshalDate mdt = new MarshalDate();
        mdt.register(e);
        MarshalDouble md = new MarshalDouble();
        md.register(e);
    }

    public Totals[] TotalsForUser(String szAuthToken) {
        SoapObject Request = setMethod("TotalsForUserWithQuery");
        Request.addProperty("szAuthToken", szAuthToken);
        Request.addProperty("fq", ActFlightQuery.GetCurrentQuery());

        Totals[] rgt = new Totals[0];

        SoapObject result = (SoapObject) Invoke();
        if (result == null)
            setLastError("Error retrieving totals - " + getLastError());
        else {
            try {
                rgt = new Totals[result.getPropertyCount()];

                for (int i = 0; i < rgt.length; i++) {
                    rgt[i] = new Totals((SoapObject) result.getProperty(i));
                }
            } catch (Exception e) {
                setLastError(getLastError() + e.getMessage());
            }
        }

        return rgt;
    }
}
