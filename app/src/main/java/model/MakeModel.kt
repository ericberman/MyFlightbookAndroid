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

import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import java.io.Serializable
import java.util.*

class MakeModel : SoapableObject(), KvmSerializable, Serializable, Comparable<MakeModel> {
    var description = ""
    var makeModelId = -1
    private val keyModelID = "MakeModelID"
    private val keyDescription = "ModelName"

    private enum class MakeModelProp {
        PIDMakeModelID, PIDModelName
    }

    override fun toString(): String {
        return description
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty(keyDescription, description)
        so.addProperty(keyModelID, makeModelId)
    }

    public override fun fromProperties(so: SoapObject) {
        description = so.getProperty(keyDescription).toString()
        makeModelId = so.getProperty(keyModelID).toString().toInt()
    }

    // serialization methods
    override fun getPropertyCount(): Int {
        return MakeModelProp.values().size
    }

    override fun getProperty(i: Int): Any {
        return when (MakeModelProp.values()[i]) {
            MakeModelProp.PIDMakeModelID -> makeModelId
            MakeModelProp.PIDModelName -> description
        }
    }

    override fun setProperty(i: Int, value: Any) {
        val mmp = MakeModelProp.values()[i]
        val sz = value.toString()
        when (mmp) {
            MakeModelProp.PIDMakeModelID -> makeModelId = sz.toInt()
            MakeModelProp.PIDModelName -> description = sz
        }
    }

    override fun getPropertyInfo(i: Int, h: Hashtable<*, *>?, pi: PropertyInfo) {
        when (MakeModelProp.values()[i]) {
            MakeModelProp.PIDMakeModelID -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "MakeModelID"
            }
            MakeModelProp.PIDModelName -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "ModelName"
            }
        }
    }

    override fun compareTo(other: MakeModel): Int {
        return description.compareTo(other.description, ignoreCase = true)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}