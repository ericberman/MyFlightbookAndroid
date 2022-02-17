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
package model

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.myflightbook.android.MFBMain
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class DBCache {
    var errorString = ""

    enum class DBCacheStatus {
        VALID, VALID_BUT_RETRY, INVALID
    }

    private fun deleteTableEntry(db: SQLiteDatabase, tablename: String) {
        db.delete(CACHE_TABLE, "$COL_TABLENAME = ?", arrayOf(tablename))
    }

    fun flushCache(tablename: String, fDeleteTableToo: Boolean) {
        errorString = ""
        val db = MFBMain.mDBHelper!!.writableDatabase
        try {
            deleteTableEntry(db, tablename)
            if (fDeleteTableToo) db.delete(tablename, "", null)
        } catch (ex: Exception) {
            errorString = "Error flushing cache for table " + tablename + ex.message
            Log.e(MFBConstants.LOG_TAG, errorString)
        }
    }

    fun updateCache(tablename: String) {
        errorString = ""
        val db = MFBMain.mDBHelper!!.writableDatabase
        try {
            deleteTableEntry(db, tablename)
            val values = ContentValues()
            val dt = Date()
            val s = SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.US)
            values.put(COL_LASTREFRESH, s.format(dt))
            values.put(COL_TABLENAME, tablename)
            db.insertOrThrow(CACHE_TABLE, null, values)
        } catch (ex: Exception) {
            errorString = "Error updating cache for table " + tablename + ex.message
            Log.e(MFBConstants.LOG_TAG, errorString)
        }
    }

    fun status(tablename: String): DBCacheStatus {
        errorString = ""
        val db = MFBMain.mDBHelper!!.writableDatabase
        var dbcs = DBCacheStatus.INVALID
        try {
            db.query(
                CACHE_TABLE,
                arrayOf(COL_LASTREFRESH),
                String.format("%s = ?", COL_TABLENAME),
                arrayOf(tablename),
                null,
                null,
                null
            ).use { c ->
                if (c != null && c.moveToFirst()) {
                    val sf = SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.US)
                    val dtNow = Date()
                    val s = c.getString(0)
                    val dtLastRefresh: Date? = try {
                        sf.parse(s)
                    } catch (e: Exception) {
                        // see if this was saved with the old (locale-specific) format; try to parse using that.
                        DateFormat.getDateInstance().parse(s)
                    }
                    assert(dtLastRefresh != null)
                    val l = (dtNow.time - dtLastRefresh!!.time + ONE_HOUR) / (ONE_HOUR * 24)
                    if (l < CACHE_VALID_WINDOW) dbcs = DBCacheStatus.VALID_BUT_RETRY
                    if (l < CACHE_RETRY_WINDOW) dbcs = DBCacheStatus.VALID
                }
            }
        } catch (e: Exception) {
            errorString = "Error checking status for table " + tablename + e.message
            Log.e(MFBConstants.LOG_TAG, errorString)
            dbcs = DBCacheStatus.INVALID
        }
        return dbcs
    }

    companion object {
        private const val CACHE_TABLE = "CacheStatus"
        private const val COL_TABLENAME = "TableName"
        private const val COL_LASTREFRESH = "LastRefresh"
        private const val CACHE_VALID_WINDOW = 14
        private const val CACHE_RETRY_WINDOW = 3
        private const val ONE_HOUR = 60 * 60 * 1000L // milliseconds in an hour
    }
}