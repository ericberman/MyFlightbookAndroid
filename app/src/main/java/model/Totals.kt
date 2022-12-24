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

import org.ksoap2.serialization.SoapObject
import java.io.Serializable
import java.util.*

class Totals : SoapableObject, Serializable {
    enum class NumType {
        Integer, Decimal, Time, Currency
    }

    enum class TotalsGroup {
        None, CategoryClass, ICAO, Model, Capabilities, CoreFields, Properties, Total
    }

    @JvmField
    var description = ""
    @JvmField
    var value = 0.0
    @JvmField
    var subDescription = ""
    @JvmField
    var numericType = NumType.Integer
    @JvmField
    var query: FlightQuery? = null
    private var group = TotalsGroup.None
    @JvmField
    var groupName = ""

    constructor(so: SoapObject) : super() {
        fromProperties(so)
    }

    @Suppress("UNUSED")
    constructor() : super()

    override fun toString(): String {
        return String.format(Locale.getDefault(), "%s %s %.2f", description, subDescription, value)
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty("Value", value)
        so.addProperty("Description", description)
        so.addProperty("SubDescription", subDescription)
        so.addProperty("NumericType", numericType)
        so.addProperty("Query", query)
        so.addProperty("Group", group)
        so.addProperty("GroupName", groupName)
    }

    override fun fromProperties(so: SoapObject) {
        description = so.getProperty("Description").toString()
        value = so.getProperty("Value").toString().toDouble()

        // Optional strings come through as "anyType" if they're not actually present, so check for that.
        val o = so.getProperty("SubDescription")
        if (o != null && !o.toString().contains("anyType")) subDescription = o.toString()
        numericType = NumType.valueOf(so.getProperty("NumericType").toString())
        if (so.hasProperty("Query")) {
            val q = so.getProperty("Query") as SoapObject
            query = FlightQuery()
            query!!.fromProperties(q)
        }
        group = TotalsGroup.valueOf(so.getProperty("Group").toString())
        groupName = so.getPropertySafelyAsString("GroupName")
    }

    companion object {
        @JvmStatic
        fun groupTotals(rgIn: Array<Totals>?): ArrayList<ArrayList<Totals>> {
            val result = ArrayList<ArrayList<Totals>>()
            if (rgIn == null) return result
            val d = Hashtable<Int, ArrayList<Totals>>()
            for (ti in rgIn) {
                if (!d.containsKey(ti.group.ordinal))
                    d[ti.group.ordinal] = ArrayList()
                d[ti.group.ordinal]?.add(ti)
            }
            for (tg in TotalsGroup.values()) {
                if (d.containsKey(tg.ordinal)) result.add(d[tg.ordinal]!!)
            }
            return result
        }
    }
}