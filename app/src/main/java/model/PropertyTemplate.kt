/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025 MyFlightbook, LLC

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

import android.content.SharedPreferences
import model.MFBUtil.deserializeFromString
import model.MFBUtil.serializeToString
import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import java.io.Serializable
import java.util.*

class PropertyTemplate(so: SoapObject) : SoapableObject(), Comparable<PropertyTemplate>, Serializable,
    KvmSerializable {
    private enum class TemplatePropID {
        PIDTemplateID, PIDTemplateName, PIDTemplateDescription, PIDTemplateGroupAsInt, PIDTemplateGroupDisplayName, PIDTemplatePropertyTypes, PIDTemplateDefault
    }

    // region basic
    private var id = 0
    @JvmField
    var name: String = ""
    @JvmField
    var description: String = ""
    @JvmField
    var groupAsInt = 0
    @JvmField
    var groupDisplayName: String = ""
    private var propTypes: HashSet<Int>? = null
    private var isDefault: Boolean? = null
    private fun init() {
        id = -1
        groupDisplayName = ""
        description = groupDisplayName
        name = description
        propTypes = HashSet()
    }

    init {
        init()
        fromProperties(so)
    }

    override fun toString(): String {
        return String.format(
            Locale.getDefault(),
            "%d: (%s) %s %s",
            id,
            groupDisplayName,
            name,
            if (isDefault!!) " (default)" else ""
        )
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PropertyTemplate) return false
        return if (other === this) true else id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun compareTo(other: PropertyTemplate): Int {
        return if (groupAsInt == other.groupAsInt) {
            if (groupAsInt == GROUP_AUTO) // Automatic groups are always first - so if we're here, then both of us are automatic; resolve on ID inverse
                other.id.compareTo(id) else name.compareTo(other.name)
        } else groupAsInt.compareTo(other.groupAsInt)
    }

    // endregion
    // region SOAP
    override fun toProperties(so: SoapObject) {
        so.addProperty("ID", id)
        so.addProperty("Name", name)
        so.addProperty("Description", description)
        so.addProperty("GroupAsInt", groupAsInt)
        so.addProperty("GroupDisplayName", groupDisplayName)
        so.addProperty("PropertyTypes", propTypes!!.toTypedArray())
        so.addProperty("IsDefault", isDefault)
    }

    override fun fromProperties(so: SoapObject) {
        id = so.getProperty("ID").toString().toInt()
        name = so.getProperty("Name").toString()
        description = so.getPrimitivePropertyAsString("Description").toString()
        groupAsInt = so.getProperty("GroupAsInt").toString().toInt()
        groupDisplayName = so.getProperty("GroupDisplayName").toString()
        propTypes!!.clear()
        val types = so.getProperty("PropertyTypes") as SoapObject
        val cVals = types.propertyCount
        for (i in 0 until cVals) propTypes!!.add(types.getPropertyAsString(i).toInt())
        isDefault = java.lang.Boolean.parseBoolean(so.getProperty("IsDefault").toString())
    }

    override fun getProperty(arg0: Int): Any {
        return when (TemplatePropID.values()[arg0]) {
            TemplatePropID.PIDTemplateID -> id
            TemplatePropID.PIDTemplateName -> name
            TemplatePropID.PIDTemplateDescription -> description
            TemplatePropID.PIDTemplateGroupAsInt -> groupAsInt
            TemplatePropID.PIDTemplateGroupDisplayName -> groupDisplayName
            TemplatePropID.PIDTemplatePropertyTypes -> propTypes!!
            TemplatePropID.PIDTemplateDefault -> isDefault!!
        }
    }

    override fun getPropertyCount(): Int {
        return TemplatePropID.values().size
    }

    override fun getPropertyInfo(arg0: Int, arg1: Hashtable<*, *>?, pi: PropertyInfo) {
        when (TemplatePropID.values()[arg0]) {
            TemplatePropID.PIDTemplateID -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "ID"
                }
            TemplatePropID.PIDTemplateName -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Name"
            }
            TemplatePropID.PIDTemplateDescription -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Description"
            }
            TemplatePropID.PIDTemplateGroupAsInt -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "GroupAsInt"
            }
            TemplatePropID.PIDTemplateGroupDisplayName -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "GroupDisplayName"
            }
            TemplatePropID.PIDTemplatePropertyTypes -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "PropertyTypes"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.INTEGER_CLASS
                pi.elementType.name = "integer"
            }
            TemplatePropID.PIDTemplateDefault -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsDefault"
            }
        }
    }

    override fun setProperty(arg0: Int, arg1: Any) {
        val pid = TemplatePropID.values()[arg0]
        val sz = arg1.toString()
        when (pid) {
            TemplatePropID.PIDTemplateID -> {
                id = sz.toInt()
                name = sz
                description = sz
                groupAsInt = sz.toInt()
                groupDisplayName = sz
                propTypes!!.clear()
                val rgVals = arg1 as IntArray
                for (i in rgVals) propTypes!!.add(i)
                isDefault = java.lang.Boolean.parseBoolean(sz)
            }
            TemplatePropID.PIDTemplateName -> {
                name = sz
                description = sz
                groupAsInt = sz.toInt()
                groupDisplayName = sz
                propTypes!!.clear()
                val rgVals = arg1 as IntArray
                for (i in rgVals) propTypes!!.add(i)
                isDefault = java.lang.Boolean.parseBoolean(sz)
            }
            TemplatePropID.PIDTemplateDescription -> {
                description = sz
                groupAsInt = sz.toInt()
                groupDisplayName = sz
                propTypes!!.clear()
                val rgVals = arg1 as IntArray
                for (i in rgVals) propTypes!!.add(i)
                isDefault = java.lang.Boolean.parseBoolean(sz)
            }
            TemplatePropID.PIDTemplateGroupAsInt -> {
                groupAsInt = sz.toInt()
                groupDisplayName = sz
                propTypes!!.clear()
                val rgVals = arg1 as IntArray
                for (i in rgVals) propTypes!!.add(i)
                isDefault = java.lang.Boolean.parseBoolean(sz)
            }
            TemplatePropID.PIDTemplateGroupDisplayName -> {
                groupDisplayName = sz
                propTypes!!.clear()
                val rgVals = arg1 as IntArray
                for (i in rgVals) propTypes!!.add(i)
                isDefault = java.lang.Boolean.parseBoolean(sz)
            }
            TemplatePropID.PIDTemplatePropertyTypes -> {
                propTypes!!.clear()
                val rgVals = arg1 as IntArray
                for (i in rgVals) propTypes!!.add(i)
                isDefault = java.lang.Boolean.parseBoolean(sz)
            }
            TemplatePropID.PIDTemplateDefault -> isDefault = java.lang.Boolean.parseBoolean(sz)
        }
    }

    companion object {
        @Suppress("UNUSED")
        private const val serialVersionUID = 1L
        @Suppress("UNUSED")
        private const val ID_NEW = -1
        @Suppress("UNUSED")
        private const val ID_MRU = -2
        private const val ID_SIM = -3
        private const val ID_ANON = -4
        const val GROUP_AUTO = 0
        @JvmField
        var sharedTemplates: Array<PropertyTemplate>? = null
        const val PREF_KEY_TEMPLATES = "preferenceKeyTemplates"

        // endregion
        // region Retrieving/persistance
        private const val prefSharedTemplates = "prefsSharedTemplates"
        fun saveSharedTemplates(pref: SharedPreferences) {
            val e = pref.edit()
            e.putString(prefSharedTemplates, serializeToString(sharedTemplates))
            e.apply()
        }

        @JvmStatic
        fun getSharedTemplates(pref: SharedPreferences): Array<PropertyTemplate>? {
            val s = pref.getString(prefSharedTemplates, "")
            sharedTemplates = deserializeFromString<Array<PropertyTemplate>>(s)
            return sharedTemplates
        }

        @JvmStatic
        val defaultTemplates: Array<PropertyTemplate>
            get() {
                val al = ArrayList<PropertyTemplate>()
                for (pt in sharedTemplates!!) {
                    if (pt.isDefault!!) al.add(pt)
                }
                return al.toTypedArray()
            }

        private fun templateWithID(id: Int): PropertyTemplate? {
            if (sharedTemplates != null) {
                for (pt in sharedTemplates!!) {
                    if (pt.id == id) return pt
                }
            }
            return null
        }

        @JvmStatic
        fun templatesWithIDs(rgid: HashSet<Int>?): Array<PropertyTemplate> {
            val al = ArrayList<PropertyTemplate>()
            if (rgid != null) {
                for (i in rgid) {
                    val template = templateWithID(i)
                    if (template != null)
                        al.add(template)
                }
            }
            return al.toTypedArray()
        }

        @JvmStatic
        val anonTemplate: PropertyTemplate?
            get() = templateWithID(ID_ANON)
        @JvmStatic
        val simTemplate: PropertyTemplate?
            get() = templateWithID(ID_SIM)

        // endregion
        // region merging
        @JvmStatic
        fun mergeTemplates(rgpt: Array<PropertyTemplate?>?): HashSet<Int> {
            val result = HashSet<Int>()
            if (rgpt != null) {
                for (pt in rgpt) if (pt?.propTypes != null) result.addAll(
                    pt.propTypes!!
                )
            }
            return result
        } // endregion
    }
}