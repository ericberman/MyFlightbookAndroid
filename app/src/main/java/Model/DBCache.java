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
package Model;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.myflightbook.android.MFBMain;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DBCache {

    private static final String CACHE_TABLE = "CacheStatus";
    private static final String COL_TABLENAME = "TableName";
    private static final String COL_LASTREFRESH = "LastRefresh";
    private static final int CACHE_VALID_WINDOW = 14;
    private static final int CACHE_RETRY_WINDOW = 3;
    private static final long ONE_HOUR = 60 * 60 * 1000L; // milliseconds in an hour

    public String errorString = "";

    public enum DBCacheStatus {VALID, VALID_BUT_RETRY, INVALID}

    public DBCache() {
        super();
    }

    private void deleteTableEntry(SQLiteDatabase db, String tablename) {
        db.delete(CACHE_TABLE, COL_TABLENAME + " = ?", new String[]{tablename});
    }

    public void flushCache(String tablename, Boolean fDeleteTableToo) {
        this.errorString = "";
        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();

        try {
            deleteTableEntry(db, tablename);
            if (fDeleteTableToo)
                db.delete(tablename, "", null);
        } catch (Exception ex) {
            this.errorString = "Error flushing cache for table " + tablename + ex.getMessage();
            Log.e(MFBConstants.LOG_TAG, this.errorString);
        }
    }

    public void updateCache(String tablename) {
        this.errorString = "";
        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();

        try {
            deleteTableEntry(db, tablename);
            ContentValues values = new ContentValues();
            Date dt = new Date();
            SimpleDateFormat s = new SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.US);

            values.put(COL_LASTREFRESH, s.format(dt));
            values.put(COL_TABLENAME, tablename);

            db.insertOrThrow(CACHE_TABLE, null, values);
        } catch (Exception ex) {
            this.errorString = "Error updating cache for table " + tablename + ex.getMessage();
            Log.e(MFBConstants.LOG_TAG, this.errorString);
        }
    }

    public DBCacheStatus Status(String tablename) {
        this.errorString = "";
        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        DBCacheStatus dbcs = DBCacheStatus.INVALID;

        try (Cursor c = db.query(CACHE_TABLE, new String[]{COL_LASTREFRESH}, String.format("%s = ?", COL_TABLENAME), new String[]{tablename}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                SimpleDateFormat sf = new SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.US);
                Date dtNow = new Date();

                String s = c.getString(0);

                Date dtLastRefresh;
                try {
                    dtLastRefresh = sf.parse(s);
                } catch (Exception e) {
                    // see if this was saved with the old (locale-specific) format; try to parse using that.
                    dtLastRefresh = (DateFormat.getDateInstance()).parse(s);
                }

                long l = (dtNow.getTime() - dtLastRefresh.getTime() + ONE_HOUR) / (ONE_HOUR * 24);

                if (l < CACHE_VALID_WINDOW)
                    dbcs = DBCacheStatus.VALID_BUT_RETRY;
                if (l < CACHE_RETRY_WINDOW)
                    dbcs = DBCacheStatus.VALID;
            }
        } catch (Exception e) {
            this.errorString = "Error checking status for table " + tablename + e.getMessage();
            Log.e(MFBConstants.LOG_TAG, this.errorString);
            dbcs = DBCacheStatus.INVALID;
        }

        return dbcs;
    }
}
