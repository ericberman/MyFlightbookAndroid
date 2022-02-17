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
import java.lang.IllegalArgumentException
import java.lang.NullPointerException
import java.lang.NumberFormatException

class CurrencyStatusItem(so: SoapObject) : SoapableObject(), Serializable {
    enum class CurrencyGroups {
        None, FlightExperience, FlightReview, Aircraft, AircraftDeadline, Certificates, Medical, Deadline, CustomCurrency
    }

    @JvmField
    var attribute = ""
    @JvmField
    var value = ""
    @JvmField
    var status = ""
    @JvmField
    var discrepancy = ""
    var associatedResourceID = 0
    var currencyGroup = CurrencyGroups.None
    var query: FlightQuery? = null

    init {
        fromProperties(so)
    }

    override fun toString(): String {
        return String.format("%s %s %s %s", attribute, value, status, discrepancy)
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty("Attribute", attribute)
        so.addProperty("Value", value)
        so.addProperty("Status", status)
        so.addProperty("Discrepancy", discrepancy)
        so.addProperty("AssociatedResourceID", associatedResourceID)
        so.addProperty("CurrencyGroup", currencyGroup)
        so.addProperty("Query", query)
    }

    override fun fromProperties(so: SoapObject) {
        attribute = so.getProperty("Attribute").toString()
        value = so.getProperty("Value").toString()
        status = so.getProperty("Status").toString()

        // Optional strings come through as "anyType" if they're not actually present, so check for that.
        discrepancy = readNullableString(so, "Discrepancy")
        associatedResourceID = try {
            so.getPropertySafelyAsString("AssociatedResourceID").toInt()
        } catch (ignored: NumberFormatException) {
            0
        } catch (ignored: NullPointerException) {
            0
        }
        currencyGroup = try {
            CurrencyGroups.valueOf(so.getPropertySafelyAsString("CurrencyGroup"))
        } catch (ignored: IllegalArgumentException) {
            CurrencyGroups.None
        } catch (ignored: NullPointerException) {
            CurrencyGroups.None
        }
        if (so.hasProperty("Query")) {
            val q = so.getProperty("Query") as SoapObject
            query = FlightQuery()
            query!!.fromProperties(q)
        }
    }
}