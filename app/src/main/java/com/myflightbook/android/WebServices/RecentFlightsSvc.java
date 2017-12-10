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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.myflightbook.android.ActCurrency;
import com.myflightbook.android.ActTotals;
import com.myflightbook.android.MFBMain;
import com.myflightbook.android.Marshal.MarshalDate;
import com.myflightbook.android.Marshal.MarshalDouble;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapPrimitive;
import org.ksoap2.serialization.SoapSerializationEnvelope;

import java.util.ArrayList;
import java.util.Arrays;

import Model.Aircraft;
import Model.CategoryClass;
import Model.CustomPropertyType;
import Model.FlightProperty;
import Model.FlightQuery;
import Model.LatLong;
import Model.LogbookEntry;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MakeModel;

public class RecentFlightsSvc extends MFBSoap {

    private static LogbookEntry[] m_CachedFlights = null;

    public RecentFlightsSvc() {

    }

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

    private LogbookEntry[] ReadResults(SoapObject result) {
        LogbookEntry[] rgLe;
        try {
            rgLe = new LogbookEntry[result.getPropertyCount()];

            for (int i = 0; i < rgLe.length; i++)
                rgLe[i] = new LogbookEntry((SoapObject) result.getProperty(i));
        } catch (Exception e) {
            rgLe = null; // don't want to show any bad data!
            setLastError(getLastError() + e.getMessage());
        }

        return rgLe;
    }

    public LogbookEntry[] RecentFlightsWithQueryAndOffset(String szAuthToken, FlightQuery fq, int offset, int limit, Context c) {
        LogbookEntry[] rgLe;

        SoapObject Request = setMethod("FlightsWithQueryAndOffset");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("fq", fq == null ? new FlightQuery() : fq);
        Request.addProperty("offset", offset);
        Request.addProperty("maxCount", limit);

        SoapObject result = (SoapObject) Invoke(c);
        if (result == null)
            rgLe = null;
        else {
            rgLe = ReadResults(result);

            // null out the properties, since these come without properties
            if (rgLe == null)
                rgLe = new LogbookEntry[0];

            for (LogbookEntry le : rgLe)
                le.rgCustomProperties = null;
        }

        ArrayList<LogbookEntry> alle = new ArrayList<>();
        if (m_CachedFlights != null)
            alle.addAll(Arrays.asList(m_CachedFlights));
        if (rgLe != null)
            alle.addAll(Arrays.asList(rgLe));

        m_CachedFlights = alle.toArray(new LogbookEntry[alle.size()]);

        ClearOrphanedExistingFlightsFromDB();

        return rgLe;
    }

    public LatLong[] FlightPathForFlight(String szAuthToken, int idFlight, Context c) {
        LatLong[] rgll = new LatLong[0];

        SoapObject Request = setMethod("FlightPathForFlight");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("idFlight", idFlight);
        SoapObject result = (SoapObject) Invoke(c);
        if (result == null)
            setLastError("Failed to get path for flight - " + getLastError());
        else {
            try {
                rgll = new LatLong[result.getPropertyCount()];

                for (int i = 0; i < rgll.length; i++)
                    rgll[i] = new LatLong((SoapObject) result.getProperty(i));
            } catch (Exception e) {
                rgll = new LatLong[0];
                setLastError(getLastError() + e.getMessage());
            }
        }

        return rgll;
    }

    public String FlightPathForFlightGPX(String szAuthToken, int idFlight, Context c) {
        SoapObject Request = setMethod("FlightPathForFlightGPX");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("idFlight", idFlight);
        SoapPrimitive result = (SoapPrimitive) Invoke(c);
        if (result == null) {
            setLastError("Failed to get GPX path for flight - " + getLastError());
            return "";
        }
        else {
            return result.toString();
        }
    }

    // Flight caching
    private static void ClearOrphanedExistingFlightsFromDB() {
        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        int[] rgLocalIDs = new int[0];
        Cursor c = null;

        try {
            c = db.query("Flights", null, "idFlight > 0", null, null, null, null);

            // get the local ID's of the orphaned entries.
            if (c != null) {
                int i = 0;
                rgLocalIDs = new int[c.getCount()];
                while (c.moveToNext())
                    rgLocalIDs[i++] = c.getInt(c.getColumnIndex("_id"));
            }
        } catch (Exception ex) {
            Log.e(MFBConstants.LOG_TAG, ex.getLocalizedMessage());
        } finally {
            if (c != null)
                c.close();
        }

        // now clean up the local ID's that were found
        for (int i : rgLocalIDs) {
            LogbookEntry le = new LogbookEntry();
            le.idLocalDB = i;
            le.DeletePendingFlight();
        }
    }

    public static Boolean HasCachedFlights() {
        return m_CachedFlights != null;
    }

    public static void ClearCachedFlights() {
        m_CachedFlights = null;
        ClearOrphanedExistingFlightsFromDB();
        // this is a bit of a hack, but it's a nice central point to invalidate totals and currency
        ActCurrency.SetNeedsRefresh(true);
        ActTotals.SetNeedsRefresh(true);
    }

    public static LogbookEntry GetCachedFlightByID(int id) {
        if (!HasCachedFlights())
            return null;

        for (LogbookEntry le : m_CachedFlights) {
            if (le.idFlight == id)
                return le;
        }

        return null;
    }
}
