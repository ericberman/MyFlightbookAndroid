/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2020 MyFlightbook, LLC

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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.Serializable;
import java.util.Date;

public class PackAndGo {
    private Context m_context;

    private static final String keyCurrency = "packedCurrencyKey";
    private static final String keyTotals = "packedTotalsKey";
    private static final String keyFlights = "packedFlightsKey";
    private static final String keyAirports = "packedAirportsKey";
    private static final String keyCurrencyDate = "packedCurrencyDate";
    private static final String keyTotalsDate = "packedTotalsKeyDate";
    private static final String keyFlightsDate = "packedFlightsKeyDate";
    private static final String keyAirportsDate = "packedAirportsKeyDate";
    private static final String keyPackedDate = "packedAllDate";

    public PackAndGo(Context c) {
        m_context = c;
    }

    private SharedPreferences getPrefs() {
        return m_context.getSharedPreferences("packAndGo", Activity.MODE_PRIVATE);
    }

    private Serializable getForKey(String key) {
        SharedPreferences sp = getPrefs();
        return MFBUtil.deserializeFromString(sp.getString(key, null));
    }

    private void setForKey(String key, Serializable v) {
        SharedPreferences sp = getPrefs();
        SharedPreferences.Editor ed = sp.edit();
        if (v == null)
            ed.remove(key);
        else
            ed.putString(key, MFBUtil.serializeToString(v));
        ed.apply();
    }

    public void setLastPackDate(Date dt) {
        setForKey(keyPackedDate, dt);
    }

    public Date getLastPackDate() {
        return (Date) getForKey((keyPackedDate));
    }

    public void clearPackedData() {
        SharedPreferences sp = getPrefs();
        SharedPreferences.Editor ed = sp.edit();
        ed.remove(keyCurrency);
        ed.remove(keyCurrencyDate);
        ed.remove(keyTotals);
        ed.remove(keyTotalsDate);
        ed.remove(keyFlights);
        ed.remove(keyFlightsDate);
        ed.remove(keyAirports);
        ed.remove(keyAirportsDate);
        ed.remove(keyPackedDate);
        ed.apply();
    }

    // region get packed values.
    public CurrencyStatusItem[] cachedCurrency() {
        return (CurrencyStatusItem[]) getForKey(keyCurrency);
    }

    public Totals[] cachedTotals() {
        return (Totals[]) getForKey(keyTotals);
    }

    public LogbookEntry[] cachedFlights() {
        return (LogbookEntry[]) getForKey(keyFlights);
    }

    public VisitedAirport[] cachedAirports() {
        return (VisitedAirport[]) getForKey(keyAirports);
    }
    // endregion

    // region get date of last pack
    public Date lastCurrencyPackDate() {
        return (Date) getForKey(keyCurrencyDate);
    }

    public Date lastTotalsPackDate() {
        return (Date) getForKey(keyTotalsDate);
    }

    public Date lastFlightsPackDate() {
        return (Date) getForKey(keyFlightsDate);
    }

    public Date lastAirportsPackDate() {
        return (Date) getForKey(keyAirportsDate);
    }
    // endregion
    
    // region Update pack values
    public void updateCurrency(CurrencyStatusItem[] rgcs) {
        setForKey(keyCurrency, rgcs);
        setForKey(keyCurrencyDate, new Date());
    }

    public void updateTotals(Totals[] rgti) {
        setForKey(keyTotals, rgti);
        setForKey(keyTotalsDate, new Date());
    }

    public void updateFlights(LogbookEntry[] rgle) {
        setForKey(keyFlights, rgle);
        setForKey(keyFlightsDate, new Date());
    }

    public void updateAirports(VisitedAirport[] rgva) {
        setForKey(keyAirports, rgva);
        setForKey(keyAirportsDate, new Date());
    }
    // endregion
}
