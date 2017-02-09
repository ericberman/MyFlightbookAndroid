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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.myflightbook.android.MFBMain;

import java.util.ArrayList;
import java.util.Locale;

public class CountryCode {

    private static ArrayList<CountryCode> m_allCodes = null;

    public String Prefix;
    private String LocaleCode;

    private CountryCode(Cursor c) {
        Prefix = c.getString(c.getColumnIndex("Prefix"));
        LocaleCode = c.getString(c.getColumnIndex("Locale"));

        if (Prefix == null)
            Prefix = "";
        if (LocaleCode == null)
            LocaleCode = "";
    }

    private static ArrayList<CountryCode> AllCountryCodes() {
        if (m_allCodes != null)
            return m_allCodes;

        m_allCodes = new ArrayList<>();

        SQLiteDatabase db = MFBMain.mDBHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("countrycodes", null, null, null, null, null, "id ASC");

            if (c != null && c.getCount() > 0) {
                while (c.moveToNext())
                    m_allCodes.add(new CountryCode(c));
            }
        } catch (Exception e) {
            Log.e("MFBAndroid", "Unable to retrieve pending flight telemetry data: " + e.getLocalizedMessage());
        } finally {
            if (c != null)
                c.close();
        }

        return m_allCodes;
    }

    private static CountryCode BestGuessForLocale(String szLocale) {
        ArrayList<CountryCode> rgcc = AllCountryCodes();
        if (szLocale != null && szLocale.length() > 0) {
            for (CountryCode cc : rgcc) {
                if (cc.LocaleCode.compareToIgnoreCase(szLocale) == 0)
                    return cc;
            }
        }
        return rgcc.get(0);
    }

    public static CountryCode BestGuessForCurrentLocale() {
        return BestGuessForLocale(Locale.getDefault().getCountry());
    }

    static CountryCode BestGuessPrefixForTail(String szTail) {
        CountryCode ccResult = null;
        int maxLength = 0;

        ArrayList<CountryCode> rgcc = AllCountryCodes();
        for (int i = rgcc.size() - 1; i >= 0; i--) {
            CountryCode cc = rgcc.get(i);
            String szPref = cc.Prefix;
            if (szTail.startsWith(szPref) && szPref.length() > maxLength) {
                ccResult = cc;
                maxLength = szPref.length();
            }
        }
        return ccResult;
    }
}
