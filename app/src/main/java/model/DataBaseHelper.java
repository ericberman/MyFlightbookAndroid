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
package model;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// thanks to http://www.reigndesign.com/blog/using-your-own-sqlite-database-in-android-applications/
// for the code here.  IMPORTANT: Follow code at top for create/insert and _id
public class DataBaseHelper extends SQLiteOpenHelper {

    // The Android's default system path of your application database.
    // private static String DB_PATH = "/data/data/com.myflightbook.android/databases/";

    public static final String DB_NAME_MAIN = "mfbAndroid.sqlite";
    public static final String DB_NAME_AIRPORTS = "mfbAirports.sqlite";

    private final String m_DBName;
    private static final String szDBNameMain = "mfbAndroid.sqlite";
    private static final String szDBNameAirports = "mfbAirport.sqlite";

	private SQLiteDatabase myDataBase;

    private final String mDBFileName;
    private final String mFilesDir;
    private final AssetManager mAssetManager;

    /**
     * Constructor Takes and keeps a reference of the passed context in order to
     * access to the application assets and resources.
     */
    public DataBaseHelper(Context context, String szDBName, int dbVersion) {
        // NOTE: to increase the version number, change the 3rd parameter below.
        super(context, szDBName, null, dbVersion);
        m_DBName = szDBName;

        // Android doesn't like us keeping a context around in a static variable, so let's instead
        // cache in a non-static variable all of the items that we will need from the context.
        mDBFileName = context.getDatabasePath(m_DBName).getAbsolutePath();
        mFilesDir = context.getFilesDir().getPath();
        mAssetManager = context.getAssets();
    }

    /**
     * Creates a empty database on the system and rewrites it with your own
     * database.
     */
    public void createDataBase() throws Error {

        boolean dbExist = checkDataBase();
        SQLiteDatabase db_Read = null;

        if (!dbExist) {

            // By calling this method and empty database will be created into
            // the default system path
            // of your application so we are gonna be able to overwrite that
            // database with our database.
            try {
                db_Read = this.getReadableDatabase();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            } finally {
                if (db_Read != null && db_Read.isOpen())
                    db_Read.close();
            }

            try {
                copyDataBase();
            } catch (IOException e) {
                throw new Error("Error copying database" + e.getMessage());
            }
        }

    }

    /**
     * Check if the database already exist to avoid re-copying the file each
     * time you open the application.
     *
     * @return true if it exists, false if it doesn't
     */
    private boolean checkDataBase() {

        SQLiteDatabase checkDB = null;

        try {
            checkDB = SQLiteDatabase.openDatabase(mDBFileName, null, SQLiteDatabase.OPEN_READONLY);
        } catch (SQLiteException e) {
            // database does't exist yet.
        } finally {
            if (checkDB != null)
                checkDB.close();
        }

        return (checkDB != null);
    }

    /**
     * Copies your database from your local assets-folder to the just created
     * empty database in the system folder, from where it can be accessed and
     * handled. This is done by transfering bytestream.
     */
    private void copyDataBase() throws IOException {
        // Open your local db as the input stream
        InputStream myInput;

        // Ensure that the directory exists.
        File f = new File(mFilesDir);
        if (!f.exists()) {
            if (f.mkdir())
                Log.v(MFBConstants.LOG_TAG, "Database Directory created");
        }

        // Open the empty db as the output stream
        String szFileName = mDBFileName;

        f = new File(szFileName);
        if (f.exists()) {
            if (!f.delete())
                Log.v(MFBConstants.LOG_TAG, "Delete failed for copydatabase");
        }

        OutputStream myOutput = new FileOutputStream(szFileName);

        try {
            // now put the pieces of the DB together (see above)
            String szAsset = (m_DBName.compareTo(DB_NAME_MAIN) == 0) ? szDBNameMain : szDBNameAirports;

            myInput = mAssetManager.open(szAsset);
            // transfer bytes from the inputfile to the outputfile
            byte[] buffer = new byte[1024];
            int length;
            while ((length = myInput.read(buffer)) > 0) {
                myOutput.write(buffer, 0, length);
            }
            myInput.close();
        } catch (Exception ex) {
            Log.e(MFBConstants.LOG_TAG, "Error copying database: " + ex.getMessage());
        } finally {
            // Close the streams
            try {
                myOutput.flush();
                myOutput.close();
            } catch (Exception ex) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex));
            }
        }
    }

	public void openDataBase() throws SQLException {

		// Open the database
		myDataBase = SQLiteDatabase.openDatabase(mDBFileName, null,
				SQLiteDatabase.OPEN_READONLY);

	}

	@Override
	public synchronized void close() {

		if (myDataBase != null)
			myDataBase.close();

		super.close();

    }

    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (m_DBName.compareTo(DB_NAME_MAIN) == 0) {
            Log.e(MFBConstants.LOG_TAG, String.format("Upgrading main DB from %d to %d", oldVersion, newVersion));
            // below will attempt to migrate version to version, step by step
            // Initial release was (I believe) version 3, so anything less than that
            if (oldVersion < newVersion) {
                Log.e(MFBConstants.LOG_TAG, "Slamming new main database");
                try {
                    copyDataBase(); // just force an upgrade, always.  We need to force a download of aircraft again anyhow
                } catch (IOException e) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
                }
            }
        }
        else // else it is the airport DB.  This is read-only, so we can ALWAYS just slam in a new copy.
        {
            try {
                Log.e(MFBConstants.LOG_TAG, String.format("Upgrading airport DB from %d to %d", oldVersion, newVersion));
                copyDataBase();
            } catch (IOException e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }
}
