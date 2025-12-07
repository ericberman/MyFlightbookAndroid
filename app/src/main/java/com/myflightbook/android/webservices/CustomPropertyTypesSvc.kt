/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025 MyFlightbook, LLC

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

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.myflightbook.android.MFBMain
import model.CustomPropertyType
import model.DBCache
import model.DBCache.DBCacheStatus
import model.MFBConstants
import model.PropertyTemplate
import org.ksoap2.serialization.SoapObject
import androidx.core.database.sqlite.transaction

class CustomPropertyTypesSvc : MFBSoap() {
    private fun updateCache(rgcpt: Array<CustomPropertyType>) {
        // note that these calls will close the db, so we do them first.
        val dbc = DBCache()
        dbc.flushCache(TABLENAME, true)
        var fResult = false

        // now, we get an open db
        val db = MFBMain.mDBHelper!!.writableDatabase
        try {
            // I've read that multiple inserts are much faster inside a
            // transaction.
            db.transaction {
                try {
                    for (cpt in rgcpt) {
                        val cv = ContentValues()
                        cpt.toContentValues(cv)
                        val l = insertOrThrow(TABLENAME, null, cv)
                        if (l < 0) throw Error("Error inserting CustomPropertyType")
                    }
                    fResult = true
                } catch (ex: Exception) {
                    lastError = ex.message!!
                } finally {
                }
            }
        } catch (e: Exception) {
            lastError = e.message!!
        }
        if (fResult) dbc.updateCache(TABLENAME)
    }

    private fun readResults(bundle: SoapObject, c: Context): Array<CustomPropertyType> {
        val rgcptCached = cachedPropertyTypes
        val rgcpt = ArrayList<CustomPropertyType>()
        var result = bundle.getProperty("UserProperties") as SoapObject
        try {
            for (i in 0 until result.propertyCount)
                rgcpt.add(CustomPropertyType(result.getProperty(i) as SoapObject))

            // if we made it here, we have successfully retrieved new values.
            // ONLY NOW should we update the cache AND only if we got at least as many
            // as we had originally.  (Could be the same # but favorites could have changed)
            if (rgcpt.size >= rgcptCached.size) updateCache(rgcpt.toTypedArray())
        } catch (e: Exception) {
            lastError += e.message
            rgcpt.addAll(rgcptCached)
        }

        // Now get any templates
        result = bundle.getProperty("UserTemplates") as SoapObject
        try {
            val rgpt : ArrayList<PropertyTemplate> = ArrayList()
            for (i in 0 until result.propertyCount) {
                rgpt.add(PropertyTemplate(result.getProperty(i) as SoapObject))
            }

            // if we made it here, we have successfully retrieved new values
            PropertyTemplate.sharedTemplates = rgpt.toTypedArray()
            val pref =
                c.getSharedPreferences(PropertyTemplate.PREF_KEY_TEMPLATES, Activity.MODE_PRIVATE)
            PropertyTemplate.saveSharedTemplates(pref)
        } catch (e: Exception) {
            lastError += e.message
        }
        return rgcpt.toTypedArray()
    }

    fun getCacheStatus(): DBCacheStatus {
        val dbc = DBCache()
        return dbc.status(TABLENAME)
    }

    fun getCustomPropertyTypes(
        szAuthToken: String?,
        fAllowCache: Boolean,
        c: Context
    ): Array<CustomPropertyType> {
        val rgcpt = ArrayList<CustomPropertyType>()
        val dbcs = getCacheStatus()
        if (dbcs == DBCacheStatus.VALID && fAllowCache) // return cached  CustomPropertyType
        {
            rgcpt.addAll(cachedPropertyTypes)
        } else  // refresh the cache
        {
            val request = setMethod("PropertiesAndTemplatesForUser")
            request.addProperty("szAuthUserToken", szAuthToken)
            val result = invoke(c) as SoapObject?
            if (result == null) {
                lastError = ("Failed to retrieve CustomPropertyTypes - "
                        + lastError)
                if (dbcs == DBCacheStatus.VALID_BUT_RETRY)
                    rgcpt.addAll(cachedPropertyTypes)
            } else {
                rgcpt.addAll(readResults(result, c))
            }
        }
        rgcpt.sort()
        return rgcpt.toTypedArray()
    }

    companion object {
        private const val TABLENAME = "customproptypes"
        @JvmStatic
        val cachedPropertyTypes: Array<CustomPropertyType>
            get() {
                val rgCpt = ArrayList<CustomPropertyType>()
                val db = MFBMain.mDBHelper!!.writableDatabase
                try {
                    db.query(TABLENAME, null, null, null, null, null, null).use { c ->
                        while (c.moveToNext()) {
                            val cpt = CustomPropertyType()
                            cpt.fromCursor(c)
                            rgCpt.add(cpt)
                        }
                        rgCpt.sort()
                    }
                } catch (ex: Exception) {
                    Log.e(
                        MFBConstants.LOG_TAG,
                        "Error getting cached CustomPropertyType from db" + Log.getStackTraceString(
                            ex
                        )
                    )
                }
                return rgCpt.toTypedArray()
            }
        @JvmStatic
        val searchableProperties: Array<CustomPropertyType>
            get() {
                val rgCpt = cachedPropertyTypes
                val lst = ArrayList<CustomPropertyType>()
                for (cpt in rgCpt) if (cpt.isFavorite) lst.add(cpt)
                return lst.toTypedArray()
            }
    }
}