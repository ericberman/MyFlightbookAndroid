/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017 MyFlightbook, LLC

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
package Model;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.myflightbook.android.MFBMain;
import com.myflightbook.android.WebServices.CustomPropertyTypesSvc;
import com.myflightbook.android.WebServices.UTCDate;

import org.kobjects.isodate.IsoDate;
import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;

public class FlightProperty extends SoapableObject implements KvmSerializable {

    static final String TABLENAME = "FlightProps";

    private enum FPProp {
        pidPropId, pidFlightID, pidPropTypeId, pidIntValue,
        pidBoolValue, pidDecValue, pidDateValue, pidStringValue
    }

    private int _id = -1;
    public int idProp = -1;
    public int idFlight = -1;
    public int idPropType = -1;
    public int intValue = 0;
    public Boolean boolValue = false;
    public double decValue = 0.0;
    public Date dateValue = null;
    public String stringValue = "";
    private CustomPropertyType m_cpt = null; // not persisted, just cached.
    private static CustomPropertyType[] m_rgcptCached = null;

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat df = new SimpleDateFormat(MFBConstants.TIMESTAMP);

    public Boolean IsDefaultValue() {
        switch (m_cpt.cptType) {
            case cfpInteger:
                return intValue == 0;
            case cfpCurrency:
            case cfpDecimal:
                return decValue == 0.0;
            case cfpBoolean:
                return !boolValue;
            case cfpDate:
            case cfpDateTime:
                return dateValue == null || UTCDate.IsNullDate(dateValue);
            case cfpString:
                return stringValue.length() == 0;
        }
        return true;
    }

    @SuppressLint("SimpleDateFormat")
    public String toString(Boolean fLocal, Context c) {
        if (IsDefaultValue())
            return "";

        switch (m_cpt.cptType) {
            case cfpInteger:
                return String.format(Locale.getDefault(), "%d", intValue);
            case cfpCurrency:
                return String.format(Locale.getDefault(), "%.1f", decValue);
            case cfpDecimal:
                if (DecimalEdit.DefaultHHMM)
                    return DecimalEdit.DoubleToHHMM(decValue);
                else
                    return String.format(Locale.getDefault(), "%.1f", decValue);
            case cfpBoolean:
                return boolValue ? "Yes" : "";
            case cfpDate:
                if (dateValue != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat();
                    return sdf.format(dateValue);
                } else
                    return "";
            case cfpDateTime:
                if (dateValue != null)
                    return UTCDate.formatDate(fLocal, dateValue, c);
                else
                    return "";
            case cfpString:
                return stringValue;
        }
        return "";
    }

    @Override
    public String toString() {
        return toString(false, null);
    }

    public String labelString() {
        if (m_cpt != null)
            return m_cpt.szTitle;
        else
            return "";
    }

    public String descriptionString() {
        if (m_cpt != null && m_cpt.szDescription != null)
            return m_cpt.szDescription;
        else
            return "";
    }

    public CustomPropertyType CustomPropertyType() {
        return m_cpt;
    }

    public void RefreshPropType() {
        if (m_rgcptCached == null)
            m_rgcptCached = CustomPropertyTypesSvc.getCachedPropertyTypes();
        if (m_rgcptCached != null)
            m_cpt = CustomPropertyType.cptFromId(this.idPropType, m_rgcptCached);
    }

    public static void RefreshPropCache() {
        m_rgcptCached = CustomPropertyTypesSvc.getCachedPropertyTypes();
    }

    public String format(Boolean fLocal, Context c) {
        // try to find the custom property type if not already filled in.  This COULD miss
        if (m_cpt == null) {
            // may even need to get the cached array of property types from the DB
            if (m_rgcptCached == null)
                m_rgcptCached = CustomPropertyTypesSvc.getCachedPropertyTypes();

            if (m_rgcptCached != null)
                m_cpt = CustomPropertyType.cptFromId(this.idPropType, m_rgcptCached);
        }

        if (m_cpt != null)
            return m_cpt.szFormatString.replace("{0}", this.toString(fLocal, c));
        else
            return "";
    }

    public CustomPropertyType.CFPPropertyType getType() {
        return m_cpt.cptType;
    }

    FlightProperty() {
        super();
    }

    public FlightProperty(SoapObject so) {
        super();
        FromProperties(so);
    }

    private FlightProperty(CustomPropertyType cpt) {
        super();
        this.m_cpt = cpt;
        if (cpt != null) // defensive - should never happen
            this.idPropType = cpt.idPropType;
    }

    // returns an array of property pairs with ALL customproperty types, initialized
    // for the specified flight properties.
    public static FlightProperty[] CrossProduct(FlightProperty[] rgfpInitial, CustomPropertyType[] rgcpt) {
        // this can probably be made faster, but the lists should be small enough that it doesn't make much difference.
        int i = 0;
        FlightProperty[] rgfpReturn = new FlightProperty[rgcpt.length];

        for (CustomPropertyType cpt : rgcpt) {
            // defensive - if rgcpt has null elements (should never happen)
            if (cpt == null)
                continue;

            FlightProperty fp = null;

            if (rgfpInitial != null) {
                for (FlightProperty fpt : rgfpInitial) {
                    if (fpt.idPropType == cpt.idPropType) {
                        fp = fpt;
                        fp.m_cpt = cpt;
                        break;
                    }
                }
            }

            if (fp == null)
                fp = new FlightProperty(cpt);

            rgfpReturn[i++] = fp;
        }

        return rgfpReturn;
    }

    // return those flight properties that have non-default values.
    public static FlightProperty[] DistillList(FlightProperty[] rgfp) {
        if (rgfp == null)
            return new FlightProperty[0];

        ArrayList<FlightProperty> al = new ArrayList<>();
        for (FlightProperty fp : rgfp) {
            if (!fp.IsDefaultValue())
                al.add(fp);
        }
        return al.toArray(new FlightProperty[0]);
    }


    public Object getProperty(int arg0) {
        FPProp f = FPProp.values()[arg0];

        switch (f) {
            case pidPropId:
                return idProp;
            case pidFlightID:
                return idFlight;
            case pidPropTypeId:
                return idPropType;
            case pidIntValue:
                return intValue;
            case pidBoolValue:
                return boolValue;
            case pidDecValue:
                return decValue;
            case pidDateValue:
                return dateValue;
            case pidStringValue:
                return stringValue;
        }
        return null;
    }

    public int getPropertyCount() {
        return FPProp.values().length;
    }

    public void getPropertyInfo(int arg0, Hashtable h, PropertyInfo pi) {
        FPProp f = FPProp.values()[arg0];
        switch (f) {
            case pidPropId:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "PropID";
                break;
            case pidFlightID:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "FlightID";
                break;
            case pidPropTypeId:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "PropTypeID";
                break;
            case pidIntValue:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "IntValue";
                break;
            case pidBoolValue:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "BoolValue";
                break;
            case pidDecValue:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "DecValue";
                break;
            case pidDateValue:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "DateValue";
                break;
            case pidStringValue:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "TextValue";
                break;
        }

    }

    public void setProperty(int arg0, Object arg1) {
        FPProp f = FPProp.values()[arg0];
        String sz = arg1.toString();
        switch (f) {
            case pidPropId:
                idProp = Integer.parseInt(sz);
                break;
            case pidFlightID:
                idFlight = Integer.parseInt(sz);
                break;
            case pidPropTypeId:
                idPropType = Integer.parseInt(sz);
                break;
            case pidIntValue:
                intValue = Integer.parseInt(sz);
                break;
            case pidBoolValue:
                boolValue = Boolean.parseBoolean(sz);
                break;
            case pidDecValue:
                decValue = Double.parseDouble(sz);
                break;
            case pidDateValue:
                dateValue = IsoDate.stringToDate(sz, IsoDate.DATE_TIME);
                break;
            case pidStringValue:
                stringValue = sz;
                break;
        }
    }

    private void FromCursor(Cursor c) {
        _id = c.getInt(c.getColumnIndex("_id"));
        idProp = c.getInt(c.getColumnIndex("idProp"));
        idFlight = c.getInt(c.getColumnIndex("idFlight"));
        idPropType = c.getInt(c.getColumnIndex("idPropType"));
        intValue = c.getInt(c.getColumnIndex("IntValue"));
        boolValue = (intValue != 0);
        decValue = c.getDouble(c.getColumnIndex("DecValue"));
        try {
            dateValue = df.parse(c.getString(c.getColumnIndex("DateValue")));
        } catch (ParseException e) {
            dateValue = null;
        }
        stringValue = c.getString(c.getColumnIndex("StringValue"));
    }

    private void ToContentValues(ContentValues cv) {
        if (_id >= 0)
            cv.put("_id", _id);
        cv.put("idProp", idProp);
        cv.put("idFlight", idFlight);
        cv.put("idPropType", idPropType);
        if ((m_cpt != null && m_cpt.cptType == CustomPropertyType.CFPPropertyType.cfpBoolean) ||
                (boolValue && (intValue == 0)))
            cv.put("IntValue", 1);
        else
            cv.put("IntValue", intValue);
        cv.put("DecValue", decValue);
        cv.put("DateValue", df.format(dateValue == null ? new Date() : dateValue));
        cv.put("StringValue", stringValue);
    }

    public static FlightProperty[] FromDB(long id) {
        FlightProperty[] rgfp = new FlightProperty[0];
        if (id > 0) {
            SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
            Cursor c = null;

            try {
                c = db.query(TABLENAME, null, "idFlight = ?", new String[]{String.format(Locale.US, "%d", id)}, null, null, null);

                if (c != null) {
                    int i = 0;
                    rgfp = new FlightProperty[c.getCount()];
                    while (c.moveToNext()) {
                        FlightProperty fp = new FlightProperty();
                        fp.FromCursor(c);
                        rgfp[i++] = fp;
                    }
                } else
                    throw new Exception("Query for flightproperties from db failed!");
            } catch (Exception e) {
                Log.v(MFBConstants.LOG_TAG, "Requested flight properties not read - " + e.getMessage());
            } finally {
                if (c != null)
                    c.close();
            }
        }
        return rgfp;
    }

    public static void RewritePropertiesForFlight(long idFlight, FlightProperty[] rgfp) {
        if (idFlight > 0) {
            SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();

            try {
                // delete the existing flightproperties for this flight
                db.delete(TABLENAME, "idFlight = ?", new String[]{String.format(Locale.US, "%d", idFlight)});

                // I've read that multiple inserts are much faster inside a transaction.
                db.beginTransaction();
                try {
                    for (FlightProperty fp : rgfp) {
                        fp.idFlight = (int) idFlight;
                        ContentValues cv = new ContentValues();
                        fp.ToContentValues(cv);
                        long l = db.insertOrThrow(TABLENAME, null, cv);
                        if (l < 0)
                            throw new Exception("Error inserting flightproperty");
                    }
                    db.setTransactionSuccessful();
                } catch (Exception ex) {
                    Log.v(MFBConstants.LOG_TAG, "Error rewriting properties - " + ex.getMessage());
                } finally {
                    db.endTransaction();
                }
            } catch (Exception e) {
                Log.v(MFBConstants.LOG_TAG, "Error rewriting properties - " + e.getMessage());
            }
        }
    }

    @Override
    public void ToProperties(SoapObject so) {
        so.addProperty("PropID", idProp);
        so.addProperty("FlightID", idFlight);
        so.addProperty("PropTypeID", idPropType);
        so.addProperty("IntValue", intValue);
        so.addProperty("BoolValue", boolValue);
        so.addProperty("DecValue", decValue);
        AddNullableDate(so, "DateValue", dateValue);
        so.addProperty("TextValue", stringValue);
    }

    @Override
    public void FromProperties(SoapObject so) {
        idProp = Integer.parseInt(so.getProperty("PropID").toString());
        idFlight = Integer.parseInt(so.getProperty("FlightID").toString());
        idPropType = Integer.parseInt(so.getProperty("PropTypeID").toString());
        intValue = Integer.parseInt(so.getProperty("IntValue").toString());
        boolValue = Boolean.parseBoolean(so.getProperty("BoolValue").toString());
        decValue = Double.parseDouble(so.getProperty("DecValue").toString());
        dateValue = ReadNullableDate(so, "DateValue");
        stringValue = ReadNullableString(so, "TextValue");
    }
}
