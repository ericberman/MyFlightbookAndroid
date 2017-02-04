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

import android.content.Context;
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
import java.util.Locale;

// thanks to http://www.reigndesign.com/blog/using-your-own-sqlite-database-in-android-applications/
// for the code here.  IMPORTANT: Follow code at top for create/insert and _id
public class DataBaseHelper extends SQLiteOpenHelper {

	// The Android's default system path of your application database.
	// private static String DB_PATH = "/data/data/com.myflightbook.android/databases/";

	public static final String DB_NAME_MAIN = "mfbAndroid.sqlite";
	public static final String DB_NAME_AIRPORTS = "mfbAirports.sqlite";
	
	private String m_DBName = "";
	private static final String szDBNameMain = "mfbAndroid.sqlite";
	private static final String szDBNameAirports = "mfbAirport.sqlite";
	
	private SQLiteDatabase myDataBase;

	private final Context myContext;

	/**
	 * Constructor Takes and keeps a reference of the passed context in order to
	 * access to the application assets and resources.
	 * 
	 * @param context
	 */
	public DataBaseHelper(Context context, String szDBName, int dbVersion) {
		// NOTE: to increase the version number, change the 3rd parameter below.
		super(context, szDBName, null, dbVersion);
		m_DBName = szDBName;
		this.myContext = context;
	}

	/**
	 * Creates a empty database on the system and rewrites it with your own
	 * database.
	 * */
	public void createDataBase() throws IOException {

		boolean dbExist = checkDataBase();
		SQLiteDatabase db_Read = null;

		if (dbExist) {
			// do nothing - database already exist
		} else {

			// By calling this method and empty database will be created into
			// the default system path
			// of your application so we are gonna be able to overwrite that
			// database with our database.
			try
			{
			db_Read = this.getReadableDatabase();
			}
			catch (Exception e)
			{}
			finally
			{
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
			checkDB = SQLiteDatabase.openDatabase(getDBFileName(), null, SQLiteDatabase.OPEN_READONLY);
		} catch (SQLiteException e) {
			// database does't exist yet.
		}
		finally
		{
			if (checkDB != null)
				checkDB.close();
		}

		return (checkDB != null);
	}

	private String getDBFileName()
	{
		File file = myContext.getDatabasePath(m_DBName);
		return file.getAbsolutePath();
	}
	
	/**
	 * Copies your database from your local assets-folder to the just created
	 * empty database in the system folder, from where it can be accessed and
	 * handled. This is done by transfering bytestream.
	 * */
	private void copyDataBase() throws IOException {
		// Open your local db as the input stream
		InputStream myInput;
		
		// Ensure that the directory exists.
		File f = new File(myContext.getFilesDir().getPath());
		if (!f.exists())
			f.mkdir();
		
		// Open the empty db as the output stream
		String szFileName = getDBFileName();
		
		f = new File(szFileName);
		if (f.exists())
			f.delete();
		
		OutputStream myOutput = new FileOutputStream(szFileName);

		try 
		{
			// now put the pieces of the DB together (see above)
			String szAsset = (m_DBName.compareTo(DB_NAME_MAIN) == 0) ? szDBNameMain : szDBNameAirports;
			
			myInput = myContext.getAssets().open(szAsset);
			// transfer bytes from the inputfile to the outputfile
			byte[] buffer = new byte[1024];
			int length;
			while ((length = myInput.read(buffer)) > 0) {
				myOutput.write(buffer, 0, length);
			}
			myInput.close();
		}
		catch (Exception ex)
		{
			Log.e("MFBAndroid", String.format(Locale.getDefault(), "Error copying database: ", ex.getMessage()));
		}
		finally
		{
			// Close the streams
			myOutput.flush();
			myOutput.close();
		}
	}

	public void openDataBase() throws SQLException {

		// Open the database
		myDataBase = SQLiteDatabase.openDatabase(getDBFileName(), null,
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
		if (m_DBName.compareTo(DB_NAME_MAIN) == 0)
		{
			Log.e("MFBAndroid", String.format("Upgrading main DB from %d to %d", oldVersion, newVersion));
			// below will attempt to migrate version to version, step by step
			// Initial release was (I believe) version 3, so anything less than that
			while (oldVersion < newVersion)
			{
				try {
					switch (oldVersion)
					{
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
						// Upgrade from 4 (plus a few internal ones) was a significant schema change for 
						// aircraft and images that we need to force a new aircraft download anyhow, so just go ahead
						// and force it for anything less than 7.n
					case 8:
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
					case 16:
						// Force an upgrade for 7->8 too (added per-user aircraft options)
						// 9->10 added country codes.
						Log.e("MFBAndroid", "Slamming new main database");
						copyDataBase(); // just force an upgrade, always.  We need to force a download of aircraft again anyhow
						oldVersion = 16; // will increment below
						break;
					default:
						break;
					}
					
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				oldVersion++;
			}
		}
		else // else it is the airport DB.  This is read-only, so we can ALWAYS just slam in a new copy.
		{
			try {
				Log.e("MFBAndroid", String.format("Upgrading airport DB from %d to %d", oldVersion, newVersion));
				copyDataBase();
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
}
