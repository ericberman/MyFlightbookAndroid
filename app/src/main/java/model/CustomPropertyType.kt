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

import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.text.TextUtils
import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import java.io.Serializable
import java.util.*
import androidx.core.content.edit

@Suppress("EnumEntryName")
class CustomPropertyType : SoapableObject, Comparable<CustomPropertyType>, Serializable,
    KvmSerializable {
    enum class CFPPropertyType {
        cfpInteger, cfpDecimal, cfpBoolean, cfpDate, cfpDateTime, cfpString, cfpCurrency
    }

    private enum class CPTPropID {
        PIDIDPropType, PIDType, PIDTitle, PIDSortKey, PIDFormatString, PIDDescription, PIDFlags, PIDFavorite, PIDPreviousValues
    }

    @JvmField
    var idPropType = -1
    @JvmField
    var szTitle = ""
    private var szSortKey = ""
    @JvmField
    var szDescription = ""
    @JvmField
    var szFormatString = ""
    @JvmField
    var cptType = CFPPropertyType.cfpInteger

    @JvmField
    var cptFlag = 0
    @JvmField
    var isFavorite = false
    @JvmField
    var previousValues : Array<String> = arrayOf()

    constructor() : super()
    constructor(so: SoapObject) : super() {
        fromProperties(so)
    }

    override fun compareTo(other: CustomPropertyType): Int {
        // Favorites go before title
        return if (isFavorite == other.isFavorite) szSortKey.compareTo(other.szSortKey) else if (isFavorite) -1 else 1
    }

    override fun toString(): String {
        return szTitle
    }

    fun fromCursor(c: Cursor) {
        idPropType = c.getInt(c.getColumnIndexOrThrow(COL_IDPROPTYPE))
        szTitle = c.getString(c.getColumnIndexOrThrow(COL_TITLE))
        szSortKey = c.getString(c.getColumnIndexOrThrow(COL_SORTKEY))
        if (szSortKey.isEmpty()) szSortKey = szTitle
        szFormatString = c.getString(c.getColumnIndexOrThrow(COL_FORMATSTRING))
        szDescription = c.getString(c.getColumnIndexOrThrow(COL_DESCRIPTION))
        cptType = CFPPropertyType.entries[c.getInt(
            c.getColumnIndexOrThrow(
                COL_TYPE
            )
        )]
        cptFlag = c.getInt(c.getColumnIndexOrThrow(COL_FLAGS))
        isFavorite = c.getInt(c.getColumnIndexOrThrow(COL_ISFAVORITE)) != 0
        val szPreviousValues = c.getString(c.getColumnIndexOrThrow(COL_PREVIOUS_VALUES))
        previousValues = szPreviousValues?.split("\t")?.toTypedArray() ?: arrayOf()
    }

    fun toContentValues(cv: ContentValues) {
        cv.put(COL_IDPROPTYPE, idPropType)
        cv.put(COL_TITLE, szTitle)
        cv.put(COL_SORTKEY, szSortKey)
        cv.put(COL_FORMATSTRING, szFormatString)
        cv.put(COL_DESCRIPTION, szDescription)
        cv.put(COL_TYPE, cptType.ordinal)
        cv.put(COL_FLAGS, cptFlag)
        cv.put(COL_ISFAVORITE, isFavorite)
        val szConcatValues = TextUtils.join("\t", previousValues)
        cv.put(COL_PREVIOUS_VALUES, szConcatValues)
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty("PropTypeID", idPropType)
        so.addProperty("Title", szTitle)
        so.addProperty("SortKey", szSortKey)
        so.addProperty("FormatString", szFormatString)
        so.addProperty("Description", szDescription)
        so.addProperty("Type", cptType.toString())
        so.addProperty("Flags", cptFlag)
        so.addProperty("IsFavorite", isFavorite)
        so.addProperty("PreviousValues", previousValues)
    }

    public override fun fromProperties(so: SoapObject) {
        idPropType = so.getProperty("PropTypeID").toString().toInt()
        szTitle = so.getProperty("Title").toString()
        szSortKey = so.getPropertySafelyAsString("SortKey")
        if (szSortKey.isEmpty()) szSortKey = szTitle
        szFormatString = so.getProperty("FormatString").toString()
        szDescription = so.getPropertySafelyAsString("Description")
        cptType = CFPPropertyType.valueOf(so.getProperty("Type").toString())
        cptFlag = so.getProperty("Flags").toString().toInt()
        isFavorite = java.lang.Boolean.parseBoolean(so.getProperty("IsFavorite").toString())
        val prevVals = so.getProperty("PreviousValues") as SoapObject
        val cVals = prevVals.propertyCount
        val rgPrev : MutableList<String> = arrayListOf()
        for (i in 0 until cVals)
            rgPrev.add(prevVals.getPropertyAsString(i))
        previousValues = rgPrev.toTypedArray()
    }

    override fun getProperty(arg0: Int): Any {
        return when (CPTPropID.entries[arg0]) {
            CPTPropID.PIDIDPropType -> idPropType
            CPTPropID.PIDTitle -> szTitle
            CPTPropID.PIDSortKey -> szSortKey
            CPTPropID.PIDFormatString -> szFormatString
            CPTPropID.PIDDescription -> szDescription
            CPTPropID.PIDFlags -> cptFlag
            CPTPropID.PIDFavorite -> isFavorite
            CPTPropID.PIDType -> cptType.toString()
            CPTPropID.PIDPreviousValues -> Vector(listOf(*previousValues))
        }
    }

    override fun getPropertyCount(): Int {
        return CPTPropID.entries.size
    }

    override fun getPropertyInfo(arg0: Int, arg1: Hashtable<*, *>?, pi: PropertyInfo) {
        when (CPTPropID.entries[arg0]) {
            CPTPropID.PIDFavorite -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "IsFavorite"
            }
            CPTPropID.PIDFlags -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "Flags"
            }
            CPTPropID.PIDFormatString -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "FormatString"
            }
            CPTPropID.PIDDescription -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Description"
            }
            CPTPropID.PIDIDPropType -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "PropTypeID"
            }
            CPTPropID.PIDTitle -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Title"
            }
            CPTPropID.PIDSortKey -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "SortKey"
            }
            CPTPropID.PIDType -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Type"
            }
            CPTPropID.PIDPreviousValues -> {
                pi.type = PropertyInfo.VECTOR_CLASS
                pi.name = "PreviousValues"
                pi.elementType = PropertyInfo()
                pi.elementType.type = PropertyInfo.STRING_CLASS
                pi.elementType.name = "string"
            }
        }
    }

    override fun setProperty(arg0: Int, arg1: Any) {
        val pid = CPTPropID.entries[arg0]
        val sz = arg1.toString()
        @Suppress("UNCHECKED_CAST")
        when (pid) {
            CPTPropID.PIDFavorite -> isFavorite = java.lang.Boolean.parseBoolean(sz)
            CPTPropID.PIDFlags -> cptFlag = sz.toInt()
            CPTPropID.PIDFormatString -> szFormatString = sz
            CPTPropID.PIDDescription -> szDescription = sz
            CPTPropID.PIDType -> cptType = CFPPropertyType.valueOf(sz)
            CPTPropID.PIDTitle -> szTitle = sz
            CPTPropID.PIDSortKey -> szSortKey = sz
            CPTPropID.PIDIDPropType -> idPropType = sz.toInt()
            CPTPropID.PIDPreviousValues -> previousValues = arg1 as Array<String>
        }
    }

    fun isLanding() : Boolean {
        return cptType == CFPPropertyType.cfpInteger && (cptFlag and 0x08000000) == 0x08000000
    }

    fun isApproach() : Boolean {
        return cptType == CFPPropertyType.cfpInteger && (cptFlag and 0x00001000) == 0x00001000
    }

    fun isTime() : Boolean {
        return cptType == CFPPropertyType.cfpDecimal && (cptFlag and 0x00200000) == 0
    }

    companion object {
        private const val serialVersionUID = 1L

        // known custom property types
        const val ID_PROP_TYPE_NIGHT_TAKEOFF = 73
        const val ID_PROP_TYPE_TACH_START = 95
        const val ID_PROP_TYPE_TACH_END = 96
        const val ID_PROP_TYPE_FLIGHT_NUM = 156
        const val ID_PROP_TYPE_BLOCK_OUT = 187
        const val ID_PROP_TYPE_BLOCK_IN = 186
        const val ID_PROP_TYPE_APPROACH_DESCRIPTION = 267
        const val ID_PROP_TYPE_FLIGHT_COST = 415
        const val ID_PROP_TYPE_FLIGHT_METER_START = 666
        const val ID_PROP_TYPE_FLIGHT_METER_END = 667
        const val ID_PROP_TYPE_LESSON_START = 668
        const val ID_PROP_TYPE_LESSON_END = 669
        const val ID_PROP_TYPE_GROUND_INSTRUCTION_GIVEN = 198
        const val ID_PROP_TYPE_GROUND_INSTRUCTION_RECEIVED = 158
        const val ID_PROP_TYPE_FUEL_AT_START = 622
        const val ID_PROP_TYPE_FUEL_AT_END = 72
        const val ID_PROP_TYPE_FUEL_CONSUMED = 71
        const val ID_PROP_TYPE_FUEL_BURN_RATE = 381

        // DB Column names
        private const val COL_IDPROPTYPE = "idPropType"
        private const val COL_TITLE = "Title"
        private const val COL_SORTKEY = "SortKey"
        private const val COL_FORMATSTRING = "FormatString"
        private const val COL_TYPE = "Type"
        private const val COL_FLAGS = "Flags"
        private const val COL_ISFAVORITE = "IsFavorite"
        private const val COL_DESCRIPTION = "Description"
        private const val COL_PREVIOUS_VALUES = "PreviousValues"
        @JvmStatic
        fun cptFromId(id: Int, rgcpt: Array<CustomPropertyType>): CustomPropertyType? {
            for (cpt in rgcpt) {
                if (cpt.idPropType == id) return cpt
            }
            return null
        }

        //region Pinned properties
        const val PREF_SHARED_PINNED_PROPS = "prefsSharedPinnedProps"
        private const val PREF_KEY_PINNED_PROPS = "keyPinnedProperties"
        @JvmStatic
        fun getPinnedProperties(pref: SharedPreferences): HashSet<Int> {
            val stringVals = pref.getStringSet(PREF_KEY_PINNED_PROPS, HashSet())
            val result = HashSet<Int>()
            for (s in Objects.requireNonNull(stringVals)) result.add(s.toInt())
            return result
        }

        fun isPinnedProperty(pref: SharedPreferences, id: Int): Boolean {
            return isPinnedProperty(getPinnedProperties(pref), id)
        }

        @JvmStatic
        fun isPinnedProperty(pinnedProps: HashSet<Int>, id: Int): Boolean {
            return pinnedProps.contains(id)
        }

        @JvmStatic
        fun setPinnedProperty(pref: SharedPreferences, id: Int) {
            val stringVals = pref.getStringSet(
                PREF_KEY_PINNED_PROPS,
                HashSet()
            )!!
            val newSet = HashSet(stringVals)
            newSet.add(String.format(Locale.US, "%d", id))
            pref.edit {
                putStringSet(PREF_KEY_PINNED_PROPS, newSet)
            }
        }

        @JvmStatic
        fun removePinnedProperty(pref: SharedPreferences, id: Int) {
            val stringVals = pref.getStringSet(PREF_KEY_PINNED_PROPS, HashSet())
            val sRemove = String.format(Locale.US, "%d", id)
            if (!Objects.requireNonNull(stringVals).contains(sRemove)) return

            // Can't modify the returned set; need to create a new one.
            val newSet = HashSet(stringVals!!)
            newSet.remove(sRemove)
            pref.edit {
                putStringSet(PREF_KEY_PINNED_PROPS, newSet)
            }
        } //endregion
    }
}