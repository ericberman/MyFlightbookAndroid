/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2020-2025 MyFlightbook, LLC

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
import androidx.core.content.edit

class PackAndGo(private val mContext: Context) {
    private val prefs: SharedPreferences
        get() = mContext.getSharedPreferences("packAndGo", Activity.MODE_PRIVATE)

    private fun <T> getForKey(key: String): T? {
        val sp = prefs
        @Suppress("UNCHECKED_CAST")
        return deserializeFromString<Serializable>(sp.getString(key, null)) as? T?
    }

    private fun setForKey(key: String, v: Serializable?) {
        val sp = prefs
        sp.edit {
            if (v == null) remove(key) else putString(key, serializeToString(v))
        }
    }

    var lastPackDate: Date?
        get() = getForKey(KEY_PACKED_DATE) as Date?
        set(dt) {
            setForKey(KEY_PACKED_DATE, dt)
        }

    fun clearPackedData() {
        val sp = prefs
        sp.edit {
            remove(KEY_CURRENCY)
            remove(KEY_CURRENCY_DATE)
            remove(KEY_TOTALS)
            remove(KEY_TOTALS_DATE)
            remove(KEY_FLIGHTS)
            remove(KEY_FLIGHTS_DATE)
            remove(KEY_AIRPORTS)
            remove(KEY_AIRPORTS_DATE)
            remove(KEY_PACKED_DATE)
        }
    }

    // region get packed values.
    fun cachedCurrency(): Array<CurrencyStatusItem>? {
        return getForKey(KEY_CURRENCY) as Array<CurrencyStatusItem>?
    }

    fun cachedTotals(): Array<Totals>? {
        return getForKey(KEY_TOTALS) as Array<Totals>?
    }

    fun cachedFlights(): Array<LogbookEntry>? {
        return getForKey(KEY_FLIGHTS) as Array<LogbookEntry>?
    }

    fun cachedAirports(): Array<VisitedAirport>? {
        return getForKey(KEY_AIRPORTS) as Array<VisitedAirport>?
    }

    // endregion
    // region get date of last pack
    fun lastCurrencyPackDate(): Date? {
        return getForKey(KEY_CURRENCY_DATE) as Date?
    }

    fun lastTotalsPackDate(): Date? {
        return getForKey(KEY_TOTALS_DATE) as Date?
    }

    fun lastFlightsPackDate(): Date? {
        return getForKey(KEY_FLIGHTS_DATE) as Date?
    }

    fun lastAirportsPackDate(): Date? {
        return getForKey(KEY_AIRPORTS_DATE) as Date?
    }

    // endregion
    // region Update pack values
    fun updateCurrency(rgcs: Array<CurrencyStatusItem>?) {
        setForKey(KEY_CURRENCY, rgcs)
        setForKey(KEY_CURRENCY_DATE, Date())
    }

    fun updateTotals(rgti: Array<Totals>?) {
        setForKey(KEY_TOTALS, rgti)
        setForKey(KEY_TOTALS_DATE, Date())
    }

    fun updateFlights(rgle: Array<LogbookEntry>?) {
        setForKey(KEY_FLIGHTS, rgle)
        setForKey(KEY_FLIGHTS_DATE, Date())
    }

    fun updateAirports(rgva: Array<VisitedAirport>?) {
        setForKey(KEY_AIRPORTS, rgva)
        setForKey(KEY_AIRPORTS_DATE, Date())
    } // endregion

    companion object {
        private const val KEY_CURRENCY = "packedCurrencyKey"
        private const val KEY_TOTALS = "packedTotalsKey"
        private const val KEY_FLIGHTS = "packedFlightsKey"
        private const val KEY_AIRPORTS = "packedAirportsKey"
        private const val KEY_CURRENCY_DATE = "packedCurrencyDate"
        private const val KEY_TOTALS_DATE = "packedTotalsKeyDate"
        private const val KEY_FLIGHTS_DATE = "packedFlightsKeyDate"
        private const val KEY_AIRPORTS_DATE = "packedAirportsKeyDate"
        private const val KEY_PACKED_DATE = "packedAllDate"
    }
}