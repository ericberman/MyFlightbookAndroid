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

class MakesandModels(so: SoapObject) : SoapableObject() {
    @JvmField
    var description = ""
    @JvmField
    var modelId = -1
    private val mKEYMODELID = "ModelID"
    private val mKEYDESCRIPTION = "Description"
    override fun toString(): String {
        return description
    }

    val manufacturer: String
        get() {
            val manufacturerBreak = " ("
            return if (description.contains(manufacturerBreak)) description.substring(
                0,
                description.indexOf(manufacturerBreak)
            ) else description
        }

    override fun toProperties(so: SoapObject) {
        so.addProperty(mKEYDESCRIPTION, description)
        so.addProperty(mKEYMODELID, modelId)
    }

    override fun fromProperties(so: SoapObject) {
        description = so.getProperty(mKEYDESCRIPTION).toString()
        modelId = so.getProperty(mKEYMODELID).toString().toInt()
    }

    companion object {
        @JvmStatic
        fun getMakeModelByID(modelId: Int, rgmm: Array<MakesandModels>?): MakesandModels? {
            if (rgmm != null) for (mm in rgmm) if (mm.modelId == modelId) return mm
            return null
        }
    }

    init {
        fromProperties(so)
    }
}