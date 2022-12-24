/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2020-2022 MyFlightbook, LLC

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

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import model.MFBUtil.deserializeFromString
import model.MFBUtil.serializeToString
import java.io.Serializable
import java.util.*

class PackAndGo(private val m_context: Context) {
    private val prefs: SharedPreferences
        get() = m_context.getSharedPreferences("packAndGo", Activity.MODE_PRIVATE)

    private fun <T> getForKey(key: String): T? {
        val sp = prefs
        @Suppress("UNCHECKED_CAST")
        return deserializeFromString<Serializable>(sp.getString(key, null)) as? T?
    }

    private fun setForKey(key: String, v: Serializable?) {
        val sp = prefs
        val ed = sp.edit()
        if (v == null) ed.remove(key) else ed.putString(key, serializeToString(v))
        ed.apply()
    }

    var lastPackDate: Date?
        get() = getForKey(keyPackedDate) as Date?
        set(dt) {
            setForKey(keyPackedDate, dt)
        }

    fun clearPackedData() {
        val sp = prefs
        val ed = sp.edit()
        ed.remove(keyCurrency)
        ed.remove(keyCurrencyDate)
        ed.remove(keyTotals)
        ed.remove(keyTotalsDate)
        ed.remove(keyFlights)
        ed.remove(keyFlightsDate)
        ed.remove(keyAirports)
        ed.remove(keyAirportsDate)
        ed.remove(keyPackedDate)
        ed.apply()
    }

    // region get packed values.
    fun cachedCurrency(): Array<CurrencyStatusItem>? {
        return getForKey(keyCurrency) as Array<CurrencyStatusItem>?
    }

    fun cachedTotals(): Array<Totals>? {
        return getForKey(keyTotals) as Array<Totals>?
    }

    fun cachedFlights(): Array<LogbookEntry>? {
        return getForKey(keyFlights) as Array<LogbookEntry>?
    }

    fun cachedAirports(): Array<VisitedAirport>? {
        return getForKey(keyAirports) as Array<VisitedAirport>?
    }

    // endregion
    // region get date of last pack
    fun lastCurrencyPackDate(): Date? {
        return getForKey(keyCurrencyDate) as Date?
    }

    fun lastTotalsPackDate(): Date? {
        return getForKey(keyTotalsDate) as Date?
    }

    fun lastFlightsPackDate(): Date? {
        return getForKey(keyFlightsDate) as Date?
    }

    fun lastAirportsPackDate(): Date? {
        return getForKey(keyAirportsDate) as Date?
    }

    // endregion
    // region Update pack values
    fun updateCurrency(rgcs: Array<CurrencyStatusItem>?) {
        setForKey(keyCurrency, rgcs)
        setForKey(keyCurrencyDate, Date())
    }

    fun updateTotals(rgti: Array<Totals>?) {
        setForKey(keyTotals, rgti)
        setForKey(keyTotalsDate, Date())
    }

    fun updateFlights(rgle: Array<LogbookEntry>?) {
        setForKey(keyFlights, rgle)
        setForKey(keyFlightsDate, Date())
    }

    fun updateAirports(rgva: Array<VisitedAirport>?) {
        setForKey(keyAirports, rgva)
        setForKey(keyAirportsDate, Date())
    } // endregion

    companion object {
        private const val keyCurrency = "packedCurrencyKey"
        private const val keyTotals = "packedTotalsKey"
        private const val keyFlights = "packedFlightsKey"
        private const val keyAirports = "packedAirportsKey"
        private const val keyCurrencyDate = "packedCurrencyDate"
        private const val keyTotalsDate = "packedTotalsKeyDate"
        private const val keyFlightsDate = "packedFlightsKeyDate"
        private const val keyAirportsDate = "packedAirportsKeyDate"
        private const val keyPackedDate = "packedAllDate"
    }
}