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

import org.kobjects.isodate.IsoDate
import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import java.io.Serializable
import java.util.*

class VisitedAirport(so: SoapObject) : SoapableObject(), KvmSerializable,
    Comparable<VisitedAirport>, Serializable {
    @JvmField
    var code: String = ""
    @JvmField
    var earliestDate: Date = Date()
    @JvmField
    var latestDate: Date = Date()
    @JvmField
    var numberOfVisits = 0
    @JvmField
    var airport: Airport? = null
    @JvmField
    var aliases: String? = null

    private enum class VisitedAirportProp {
        PIDCode, PIDEarliestDate, PIDLatestDate, PIDNumVisits, PIDAirport, PIDAliases
    }

    override fun toProperties(so: SoapObject) {}
    override fun fromProperties(so: SoapObject) {
        code = so.getProperty("Code").toString()
        aliases = try {
            so.getPropertyAsString("Aliases")
        } catch (e: Exception) {
            ""
        }
        earliestDate =
            IsoDate.stringToDate(so.getProperty("EarliestVisitDate").toString(), IsoDate.DATE)
        latestDate =
            IsoDate.stringToDate(so.getProperty("LatestVisitDate").toString(), IsoDate.DATE)
        numberOfVisits = so.getProperty("NumberOfVisits").toString().toInt()
        airport = Airport()
        airport!!.fromProperties((so.getProperty("Airport") as SoapObject))
    }

    override fun getProperty(i: Int): Any {
        return when (VisitedAirportProp.values()[i]) {
            VisitedAirportProp.PIDCode -> code
            VisitedAirportProp.PIDAliases -> aliases!!
            VisitedAirportProp.PIDEarliestDate -> earliestDate
            VisitedAirportProp.PIDLatestDate -> latestDate
            VisitedAirportProp.PIDNumVisits -> numberOfVisits
            VisitedAirportProp.PIDAirport -> airport!!
        }
    }

    override fun getPropertyCount(): Int {
        return VisitedAirportProp.values().size
    }

    override fun getPropertyInfo(i: Int, h: Hashtable<*, *>?, pi: PropertyInfo) {
        when (VisitedAirportProp.values()[i]) {
            VisitedAirportProp.PIDCode -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Code"
            }
            VisitedAirportProp.PIDAliases -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Aliases"
            }
            VisitedAirportProp.PIDEarliestDate -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "EarliestVisitDate"
            }
            VisitedAirportProp.PIDLatestDate -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "LatestVisitDate"
            }
            VisitedAirportProp.PIDNumVisits -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "NumberOfVisits"
            }
            VisitedAirportProp.PIDAirport -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "Airport"
            }
        }
    }

    override fun setProperty(arg0: Int, arg1: Any) {}
    override fun compareTo(other: VisitedAirport): Int {
        return airport!!.facilityName.compareTo(other.airport!!.facilityName, ignoreCase = true)
    }

    companion object {
        private const val serialVersionUID = 1L
        @JvmStatic
        fun toRoute(rgva: Array<VisitedAirport>): String {
            val sb = StringBuilder()
            for (va in rgva) sb.append(va.code).append(" ")
            return sb.toString().trim { it <= ' ' }
        }
    }

    init {
        fromProperties(so)
    }
}