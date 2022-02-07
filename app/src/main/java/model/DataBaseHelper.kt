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

import android.content.Context
import android.content.res.AssetManager
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.*

// thanks to http://www.reigndesign.com/blog/using-your-own-sqlite-database-in-android-applications/
// for the code here.  IMPORTANT: Follow code at top for create/insert and _id
class DataBaseHelper(context: Context, private val m_DBName: String, dbVersion: Int) :
    SQLiteOpenHelper(context, m_DBName, null, dbVersion) {
    private var myDataBase: SQLiteDatabase? = null
    private val mDBFileName: String = context.getDatabasePath(m_DBName).absolutePath
    private val mFilesDir: String = context.filesDir.path
    private val mAssetManager: AssetManager = context.assets

    /**
     * Creates a empty database on the system and rewrites it with your own
     * database.
     */
    @Throws(Error::class)
    fun createDataBase() {
        val dbExist = checkDataBase()
        var dbRead: SQLiteDatabase? = null
        if (!dbExist) {

            // By calling this method and empty database will be created into
            // the default system path
            // of your application so we are gonna be able to overwrite that
            // database with our database.
            try {
                dbRead = this.readableDatabase
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            } finally {
                if (dbRead != null && dbRead.isOpen) dbRead.close()
            }
            try {
                copyDataBase()
            } catch (e: IOException) {
                throw Error("Error copying database" + e.message)
            }
        }
    }

    /**
     * Check if the database already exist to avoid re-copying the file each
     * time you open the application.
     *
     * @return true if it exists, false if it doesn't
     */
    private fun checkDataBase(): Boolean {
        var checkDB: SQLiteDatabase? = null
        try {
            checkDB = SQLiteDatabase.openDatabase(mDBFileName, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {
            // database does't exist yet.
        } finally {
            checkDB?.close()
        }
        return checkDB != null
    }

    /**
     * Copies your database from your local assets-folder to the just created
     * empty database in the system folder, from where it can be accessed and
     * handled. This is done by transfering bytestream.
     */
    @Throws(IOException::class)
    private fun copyDataBase() {
        // Open your local db as the input stream
        val myInput: InputStream

        // Ensure that the directory exists.
        var f = File(mFilesDir)
        if (!f.exists()) {
            if (f.mkdir()) Log.v(MFBConstants.LOG_TAG, "Database Directory created")
        }

        // Open the empty db as the output stream
        val szFileName = mDBFileName
        f = File(szFileName)
        if (f.exists()) {
            if (!f.delete()) Log.v(MFBConstants.LOG_TAG, "Delete failed for copydatabase")
        }
        val myOutput: OutputStream = FileOutputStream(szFileName)
        try {
            // now put the pieces of the DB together (see above)
            val szAsset =
                if (m_DBName.compareTo(DB_NAME_MAIN) == 0) szDBNameMain else szDBNameAirports
            myInput = mAssetManager.open(szAsset)
            // transfer bytes from the inputfile to the outputfile
            val buffer = ByteArray(1024)
            var length: Int
            while (myInput.read(buffer).also { length = it } > 0) {
                myOutput.write(buffer, 0, length)
            }
            myInput.close()
        } catch (ex: Exception) {
            Log.e(MFBConstants.LOG_TAG, "Error copying database: " + ex.message)
        } finally {
            // Close the streams
            try {
                myOutput.flush()
                myOutput.close()
            } catch (ex: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex))
            }
        }
    }

    @Throws(SQLException::class)
    fun openDataBase() {

        // Open the database
        myDataBase = SQLiteDatabase.openDatabase(
            mDBFileName, null,
            SQLiteDatabase.OPEN_READONLY
        )
    }

    @Synchronized
    override fun close() {
        if (myDataBase != null) myDataBase!!.close()
        super.close()
    }

    override fun onCreate(db: SQLiteDatabase) {}
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (m_DBName.compareTo(DB_NAME_MAIN) == 0) {
            Log.e(
                MFBConstants.LOG_TAG,
                String.format("Upgrading main DB from %d to %d", oldVersion, newVersion)
            )
            // below will attempt to migrate version to version, step by step
            // Initial release was (I believe) version 3, so anything less than that
            if (oldVersion < newVersion) {
                Log.e(MFBConstants.LOG_TAG, "Slamming new main database")
                try {
                    copyDataBase() // just force an upgrade, always.  We need to force a download of aircraft again anyhow
                } catch (e: IOException) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
                }
            }
        } else  // else it is the airport DB.  This is read-only, so we can ALWAYS just slam in a new copy.
        {
            try {
                Log.e(
                    MFBConstants.LOG_TAG,
                    String.format("Upgrading airport DB from %d to %d", oldVersion, newVersion)
                )
                copyDataBase()
            } catch (e: IOException) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
        }
    }

    companion object {
        // The Android's default system path of your application database.
        // private static String DB_PATH = "/data/data/com.myflightbook.android/databases/";
        const val DB_NAME_MAIN = "mfbAndroid.sqlite"
        const val DB_NAME_AIRPORTS = "mfbAirports.sqlite"
        private const val szDBNameMain = "mfbAndroid.sqlite"
        private const val szDBNameAirports = "mfbAirport.sqlite"
    }

}