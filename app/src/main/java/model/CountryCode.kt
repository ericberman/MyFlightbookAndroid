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

import android.database.Cursor
import com.myflightbook.android.MFBMain
import android.util.Log
import java.lang.Exception
import java.util.*

class CountryCode private constructor(c: Cursor) {
    @JvmField
    var prefix: String?
    private var localeCode: String?

    companion object {
        private var m_allCodes: ArrayList<CountryCode>? = null
        private fun getAllCountryCodes(): ArrayList<CountryCode>? {
            if (m_allCodes != null) return m_allCodes
            m_allCodes = ArrayList()
            val db = MFBMain.mDBHelper!!.readableDatabase
            try {
                db.query("countrycodes", null, null, null, null, null, "id ASC").use { c ->
                    if (c != null && c.count > 0) {
                        while (c.moveToNext()) m_allCodes!!.add(CountryCode(c))
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    MFBConstants.LOG_TAG,
                    "Unable to retrieve pending flight telemetry data: " + e.localizedMessage
                )
            }
            return m_allCodes
        }

        private fun bestGuessForLocale(szLocale: String?): CountryCode {
            val rgcc = getAllCountryCodes()
            if (szLocale != null && szLocale.isNotEmpty()) {
                for (cc in rgcc!!) {
                    if (cc.localeCode!!.compareTo(szLocale, ignoreCase = true) == 0) return cc
                }
            }
            return rgcc!![0]
        }

        @JvmStatic
        fun bestGuessForCurrentLocale(): CountryCode {
            return bestGuessForLocale(Locale.getDefault().country)
        }

        @JvmStatic
        fun bestGuessPrefixForTail(szTail: String): CountryCode? {
            var ccResult: CountryCode? = null
            var maxLength = 0
            val rgcc = getAllCountryCodes()
            for (i in rgcc!!.indices.reversed()) {
                val cc = rgcc[i]
                val szPref = cc.prefix
                if (szTail.startsWith(szPref!!) && szPref.length > maxLength) {
                    ccResult = cc
                    maxLength = szPref.length
                }
            }
            return ccResult
        }
    }

    init {
        prefix = c.getString(c.getColumnIndexOrThrow("Prefix"))
        localeCode = c.getString(c.getColumnIndexOrThrow("Locale"))
        if (prefix == null) prefix = ""
        if (localeCode == null) localeCode = ""
    }
}