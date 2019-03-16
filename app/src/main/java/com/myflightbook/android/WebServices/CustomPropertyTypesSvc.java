/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2019 MyFlightbook, LLC

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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.myflightbook.android.MFBMain;

import org.ksoap2.serialization.SoapObject;

import java.util.ArrayList;
import java.util.Arrays;

import Model.CustomPropertyType;
import Model.DBCache;
import Model.MFBConstants;

public class CustomPropertyTypesSvc extends MFBSoap {
    private static final String TABLENAME = "customproptypes";

    public static CustomPropertyType[] getCachedPropertyTypes() {
        CustomPropertyType[] rgCpt = new CustomPropertyType[0];

        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();

        try (Cursor c = db.query(TABLENAME, null, null, null, null, null, null)) {

            if (c != null) {
                rgCpt = new CustomPropertyType[c.getCount()];
                int i = 0;

                while (c.moveToNext()) {
                    CustomPropertyType cpt = new CustomPropertyType();
                    cpt.FromCursor(c);
                    rgCpt[i++] = cpt;
                }

                Arrays.sort(rgCpt);
            }
        } catch (Exception ex) {
            Log.e(MFBConstants.LOG_TAG,
                    "Error getting cached CustomPropertyType from db" + Log.getStackTraceString(ex));
        }
        return rgCpt;
    }

    public static CustomPropertyType[] getSearchableProperties() {
        CustomPropertyType[] rgCpt = getCachedPropertyTypes();

        ArrayList<CustomPropertyType> lst = new ArrayList<>();
        for (CustomPropertyType cpt : rgCpt)
            if (cpt.IsFavorite)
                lst.add(cpt);

        if (lst.size() == 0)
            return rgCpt;
        else
            return lst.toArray(new CustomPropertyType[0]);
    }

    private void updateCache(CustomPropertyType[] rgcpt) {
        // note that these calls will close the db, so we do them first.
        DBCache dbc = new DBCache();
        dbc.flushCache(TABLENAME, true);
        boolean fResult = false;

        // now, we get an open db
        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();

        try {
            // I've read that multiple inserts are much faster inside a
            // transaction.
            db.beginTransaction();
            try {
                for (CustomPropertyType cpt : rgcpt) {
                    ContentValues cv = new ContentValues();
                    cpt.ToContentValues(cv);

                    long l = db.insertOrThrow(TABLENAME, null, cv);
                    if (l < 0)
                        throw new Error("Error inserting CustomPropertyType");
                }
                db.setTransactionSuccessful();
                fResult = true;
            } catch (Exception ex) {
                this.setLastError(ex.getMessage());
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            this.setLastError(e.getMessage());
        }

        if (fResult)
            dbc.updateCache(TABLENAME);
    }

    private CustomPropertyType[] ReadResults(SoapObject result) {
        CustomPropertyType[] rgcptCached = getCachedPropertyTypes();
        CustomPropertyType[] rgcpt;

        try {
            rgcpt = new CustomPropertyType[result.getPropertyCount()];

            for (int i = 0; i < rgcpt.length; i++)
                rgcpt[i] = new CustomPropertyType(
                        (SoapObject) result.getProperty(i));

            // if we made it here, we have successfully retrieved new values.
            // ONLY NOW should we update the cache AND only if we got at least as many
            // as we had originally.  (Could be the same # but favorites could have changed)
            if (rgcpt.length >= rgcptCached.length)
                updateCache(rgcpt);
        } catch (Exception e) {
            setLastError(getLastError() + e.getMessage());
            rgcpt = rgcptCached;
        }

        return rgcpt;
    }

    public DBCache.DBCacheStatus CacheStatus() {
        DBCache dbc = new DBCache();
        return dbc.Status(TABLENAME);
    }

    public CustomPropertyType[] GetCustomPropertyTypes(String szAuthToken, Boolean fAllowCache, Context c) {
        CustomPropertyType[] rgcpt = new CustomPropertyType[0];

        DBCache.DBCacheStatus dbcs = CacheStatus();

        if (dbcs == DBCache.DBCacheStatus.VALID && fAllowCache) // return cached  CustomPropertyType
        {
            rgcpt = getCachedPropertyTypes();
        } else // refresh the cache
        {
            SoapObject Request = setMethod("AvailablePropertyTypesForUser");
            Request.addProperty("szAuthUserToken", szAuthToken);

            SoapObject result = (SoapObject) Invoke(c);
            if (result == null) {
                setLastError("Failed to retrieve CustomPropertyTypes - "
                        + getLastError());
                if (dbcs == DBCache.DBCacheStatus.VALID_BUT_RETRY)
                    rgcpt = getCachedPropertyTypes();
            } else {
                rgcpt = ReadResults(result);
            }
        }

        Arrays.sort(rgcpt);
        return rgcpt;
    }
}
