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
import org.kobjects.isodate.IsoDate
import java.util.*

abstract class SoapableObject {
    constructor() : super()
    constructor(so: SoapObject) : super() {
        fromProperties(so)
    }

    fun addNullableDate(so: SoapObject, propName: String?, dt: Date?): SoapObject {
        return if (dt == null) so.addProperty(propName, "") else so.addProperty(
            propName,
            IsoDate.dateToString(dt, IsoDate.DATE_TIME)
        )
    }

    fun addDouble(so: SoapObject, propName: String?, d: Double?): SoapObject {
        return so.addProperty(propName, String.format(Locale.US, "%.2f", d))
    }

    fun readNullableDate(so: SoapObject, propName: String?): Date? {
        val sz = so.getPropertySafelyAsString(propName)
        return if (sz.isEmpty()) null else IsoDate.stringToDate(sz, IsoDate.DATE_TIME)
    }

    private fun fromNullableString(o: Any?): String {
        return if (o != null && !o.toString().contains("anyType")) o.toString() else ""
    }

    fun readNullableString(so: SoapObject, index: Int): String {
        return fromNullableString(so.getProperty(index))
    }

    fun readNullableString(so: SoapObject, propName: String?): String {
        return fromNullableString(so.getProperty(propName))
    }

    abstract fun toProperties(so: SoapObject)
    protected abstract fun fromProperties(so: SoapObject)
}