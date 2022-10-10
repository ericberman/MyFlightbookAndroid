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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import com.myflightbook.android.MFBMain
import com.myflightbook.android.webservices.CustomPropertyTypesSvc.Companion.cachedPropertyTypes
import com.myflightbook.android.webservices.UTCDate.formatDate
import com.myflightbook.android.webservices.UTCDate.isNullDate
import model.CustomPropertyType.CFPPropertyType
import model.CustomPropertyType.Companion.cptFromId
import model.DecimalEdit.Companion.doubleToHHMM
import org.kobjects.isodate.IsoDate
import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import java.io.Serializable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class FlightProperty : SoapableObject, KvmSerializable, Serializable {
    private enum class FPProp {
        PIDPropId, PIDFlightID, PIDPropTypeId, PIDIntValue, PIDBoolValue, PIDDecValue, PIDDateValue, PIDStringValue
    }

    private var _id = -1
    @JvmField
    var idProp = ID_PROP_NEW
    @JvmField
    var idFlight = LogbookEntry.ID_NEW_FLIGHT
    @JvmField
    var idPropType = -1
    @JvmField
    var intValue = 0
    @JvmField
    var boolValue = false
    @JvmField
    var decValue = 0.0
    @JvmField
    var dateValue: Date? = null
    @JvmField
    var stringValue = ""
    private var mCpt: CustomPropertyType? = null // not persisted, just cached.
    fun isDefaultValue(): Boolean {
        return when (mCpt?.cptType) {
            CFPPropertyType.cfpInteger -> intValue == 0
            CFPPropertyType.cfpCurrency, CFPPropertyType.cfpDecimal -> decValue == 0.0
            CFPPropertyType.cfpBoolean -> !boolValue
            CFPPropertyType.cfpDate, CFPPropertyType.cfpDateTime -> dateValue == null || isNullDate(
                dateValue
            )
            CFPPropertyType.cfpString -> stringValue.isEmpty()
            null -> true
        }
    }

    fun getType() : CFPPropertyType? {
        return mCpt?.cptType
    }

    private fun toString(fLocal: Boolean, c: Context?): String {
        if (mCpt == null) return String.format(Locale.getDefault(), "(PropType %d)", idPropType)
        if (isDefaultValue()) return ""
        when (mCpt?.cptType) {
            CFPPropertyType.cfpInteger -> return String.format(Locale.getDefault(), "%d", intValue)
            CFPPropertyType.cfpCurrency -> return String.format(
                Locale.getDefault(),
                "%.1f",
                decValue
            )
            CFPPropertyType.cfpDecimal -> return if (DecimalEdit.DefaultHHMM) doubleToHHMM(decValue) else String.format(
                Locale.getDefault(),
                "%.1f",
                decValue
            )
            CFPPropertyType.cfpBoolean -> return if (boolValue) "✓" else ""
            CFPPropertyType.cfpDate -> return if (dateValue != null) {
                SimpleDateFormat.getDateInstance().format(dateValue!!)
            } else ""
            CFPPropertyType.cfpDateTime -> return if (dateValue != null) formatDate(
                fLocal,
                dateValue!!,
                c
            ) else ""
            CFPPropertyType.cfpString -> return stringValue
            null -> return ""
        }
    }

    override fun toString(): String {
        return toString(false, null)
    }

    fun labelString(): String {
        return if (mCpt != null) mCpt!!.szTitle else ""
    }

    fun descriptionString(): String {
        return if (mCpt != null) mCpt!!.szDescription else ""
    }

    fun getCustomPropertyType(): CustomPropertyType? {
        return mCpt
    }

    fun refreshPropType() {
        if (m_rgcptCached == null) m_rgcptCached = cachedPropertyTypes
        if (m_rgcptCached != null) mCpt = cptFromId(
            idPropType, m_rgcptCached!!
        )
    }

    fun format(fLocal: Boolean, fBoldValue: Boolean, c: Context?): String {
        // try to find the custom property type if not already filled in.  This COULD miss
        if (mCpt == null) {
            // may even need to get the cached array of property types from the DB
            if (m_rgcptCached == null) m_rgcptCached = cachedPropertyTypes
            if (m_rgcptCached != null) mCpt = cptFromId(
                idPropType, m_rgcptCached!!
            )
        }
        if (mCpt == null)
            return ""

        val cpt = mCpt!!
        val szFormat = cpt.szFormatString + if (cpt.cptType == CFPPropertyType.cfpBoolean) " {0}" else ""
        return szFormat.replace(
            "{0}",
            String.format(if (fBoldValue) "<b>%s</b>" else "%s", this.toString(fLocal, c))
        )
    }

    internal constructor() : super()
    constructor(so: SoapObject) : super() {
        fromProperties(so)
    }

    private constructor(cpt: CustomPropertyType?) : super() {
        mCpt = cpt
        if (cpt != null) // defensive - should never happen
            idPropType = cpt.idPropType
    }

    override fun getProperty(arg0: Int): Any {
        return when (FPProp.values()[arg0]) {
            FPProp.PIDPropId -> idProp
            FPProp.PIDFlightID -> idFlight
            FPProp.PIDPropTypeId -> idPropType
            FPProp.PIDIntValue -> intValue
            FPProp.PIDBoolValue -> boolValue
            FPProp.PIDDecValue -> decValue
            FPProp.PIDDateValue -> dateValue!!
            FPProp.PIDStringValue -> stringValue
        }
    }

    override fun getPropertyCount(): Int {
        return FPProp.values().size
    }

    override fun getPropertyInfo(arg0: Int, h: Hashtable<*, *>?, pi: PropertyInfo) {
        when (FPProp.values()[arg0]) {
            FPProp.PIDPropId -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "PropID"
            }
            FPProp.PIDFlightID -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "FlightID"
            }
            FPProp.PIDPropTypeId -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "PropTypeID"
            }
            FPProp.PIDIntValue -> {
                pi.type = PropertyInfo.INTEGER_CLASS
                pi.name = "IntValue"
            }
            FPProp.PIDBoolValue -> {
                pi.type = PropertyInfo.BOOLEAN_CLASS
                pi.name = "BoolValue"
            }
            FPProp.PIDDecValue -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "DecValue"
            }
            FPProp.PIDDateValue -> {
                pi.type = PropertyInfo.OBJECT_CLASS
                pi.name = "DateValue"
            }
            FPProp.PIDStringValue -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "TextValue"
            }
        }
    }

    override fun setProperty(arg0: Int, arg1: Any) {
        val f = FPProp.values()[arg0]
        val sz = arg1.toString()
        when (f) {
            FPProp.PIDPropId -> idProp = sz.toInt()
            FPProp.PIDFlightID -> idFlight = sz.toInt()
            FPProp.PIDPropTypeId -> idPropType = sz.toInt()
            FPProp.PIDIntValue -> intValue = sz.toInt()
            FPProp.PIDBoolValue -> boolValue = java.lang.Boolean.parseBoolean(sz)
            FPProp.PIDDecValue -> decValue = sz.toDouble()
            FPProp.PIDDateValue -> dateValue = IsoDate.stringToDate(sz, IsoDate.DATE_TIME)
            FPProp.PIDStringValue -> stringValue = sz
        }
    }

    private fun fromCursor(c: Cursor) {
        val df = SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        _id = c.getInt(c.getColumnIndexOrThrow("_id"))
        idProp = c.getInt(c.getColumnIndexOrThrow("idProp"))
        idFlight = c.getInt(c.getColumnIndexOrThrow("idFlight"))
        idPropType = c.getInt(c.getColumnIndexOrThrow("idPropType"))
        intValue = c.getInt(c.getColumnIndexOrThrow("IntValue"))
        boolValue = intValue != 0
        decValue = c.getDouble(c.getColumnIndexOrThrow("DecValue"))
        dateValue = try {
            df.parse(c.getString(c.getColumnIndexOrThrow("DateValue")))
        } catch (e: ParseException) {
            null
        }
        stringValue = c.getString(c.getColumnIndexOrThrow("StringValue"))
    }

    private fun toContentValues(cv: ContentValues) {
        val df = SimpleDateFormat(MFBConstants.TIMESTAMP, Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        if (_id >= 0) cv.put("_id", _id)
        cv.put("idProp", idProp)
        cv.put("idFlight", idFlight)
        cv.put("idPropType", idPropType)
        val cpt = mCpt
        if (cpt != null && cpt.cptType === CFPPropertyType.cfpBoolean ||
            boolValue && intValue == 0
        ) cv.put("IntValue", 1) else cv.put("IntValue", intValue)
        cv.put("DecValue", decValue)
        cv.put("DateValue", df.format(if (dateValue == null) Date() else dateValue!!))
        cv.put("StringValue", stringValue)
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty("PropID", idProp)
        so.addProperty("FlightID", idFlight)
        so.addProperty("PropTypeID", idPropType)
        so.addProperty("IntValue", intValue)
        so.addProperty("BoolValue", boolValue)
        so.addProperty("DecValue", decValue)
        addNullableDate(so, "DateValue", dateValue)
        so.addProperty("TextValue", stringValue)
    }

    public override fun fromProperties(so: SoapObject) {
        idProp = so.getProperty("PropID").toString().toInt()
        idFlight = so.getProperty("FlightID").toString().toInt()
        idPropType = so.getProperty("PropTypeID").toString().toInt()
        intValue = so.getProperty("IntValue").toString().toInt()
        boolValue = java.lang.Boolean.parseBoolean(so.getProperty("BoolValue").toString())
        decValue = so.getProperty("DecValue").toString().toDouble()
        dateValue = readNullableDate(so, "DateValue")
        stringValue = readNullableString(so, "TextValue")
    }

    companion object {
        const val TABLENAME = "FlightProps"
        const val ID_PROP_NEW = -1
        private var m_rgcptCached: Array<CustomPropertyType>? = null
        @JvmStatic
        fun refreshPropCache() {
            m_rgcptCached = cachedPropertyTypes
        }

        // returns an array of property pairs with ALL customproperty types, initialized
        // for the specified flight properties.
        @JvmStatic
        fun crossProduct(
            rgfpInitial: Array<FlightProperty>?,
            rgcpt: Array<CustomPropertyType>?
        ): Array<FlightProperty> {
            // this can probably be made faster, but the lists should be small enough that it doesn't make much difference.
            if (rgcpt == null) return arrayOf()
            val rgfpReturn = ArrayList<FlightProperty>()
            for (cpt in rgcpt) {
                // defensive - if rgcpt has null elements (should never happen)
                var fp: FlightProperty? = null
                if (rgfpInitial != null) {
                    for (fpt in rgfpInitial) {
                        if (fpt.idPropType == cpt.idPropType) {
                            fp = fpt
                            fp.mCpt = cpt
                            break
                        }
                    }
                }
                if (fp == null) fp = FlightProperty(cpt)
                rgfpReturn.add(fp)
            }
            return rgfpReturn.toTypedArray()
        }

        // return those flight properties that have non-default values.
        @JvmStatic
        fun distillList(rgfp: Array<FlightProperty>?): Array<FlightProperty> {
            if (rgfp == null) return arrayOf()
            val al = ArrayList<FlightProperty>()
            for (fp in rgfp) {
                if (!fp.isDefaultValue()) al.add(fp)
            }
            return al.toTypedArray()
        }

        @JvmStatic
        fun fromDB(id: Long): Array<FlightProperty> {
            val rgfp : MutableList<FlightProperty> = ArrayList()
            if (id > 0) {
                val db = MFBMain.mDBHelper!!.writableDatabase
                try {
                    db.query(
                        TABLENAME,
                        null,
                        "idFlight = ?",
                        arrayOf(String.format(Locale.US, "%d", id)),
                        null,
                        null,
                        null
                    ).use { c ->
                        if (c != null) {
                            while (c.moveToNext()) {
                                val fp = FlightProperty()
                                fp.fromCursor(c)
                                rgfp.add(fp)
                            }
                        } else throw Exception("Query for flightproperties from db failed!")
                    }
                } catch (e: Exception) {
                    Log.v(
                        MFBConstants.LOG_TAG,
                        "Requested flight properties not read - " + e.message
                    )
                }
            }
            return rgfp.toTypedArray()
        }

        @JvmStatic
        fun rewritePropertiesForFlight(idFlight: Long, rgfp: Array<FlightProperty>) {
            if (idFlight > 0) {
                val db = MFBMain.mDBHelper!!.writableDatabase
                try {
                    // delete the existing flightproperties for this flight
                    db.delete(
                        TABLENAME,
                        "idFlight = ?",
                        arrayOf(String.format(Locale.US, "%d", idFlight))
                    )

                    // I've read that multiple inserts are much faster inside a transaction.
                    db.beginTransaction()
                    try {
                        for (fp in rgfp) {
                            fp.idFlight = idFlight.toInt()
                            val cv = ContentValues()
                            fp.toContentValues(cv)
                            val l = db.insertOrThrow(TABLENAME, null, cv)
                            if (l < 0) throw Exception("Error inserting flightproperty")
                        }
                        db.setTransactionSuccessful()
                    } catch (ex: Exception) {
                        Log.v(MFBConstants.LOG_TAG, "Error rewriting properties - " + ex.message)
                    } finally {
                        db.endTransaction()
                    }
                } catch (e: Exception) {
                    Log.v(MFBConstants.LOG_TAG, "Error rewriting properties - " + e.message)
                }
            }
        }
    }
}