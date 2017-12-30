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

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.support.annotation.NonNull;

import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

public class CustomPropertyType extends SoapableObject implements Comparable<CustomPropertyType>, Serializable, KvmSerializable {
    private static final long serialVersionUID = 1L;

    public enum CFPPropertyType {
        cfpInteger, cfpDecimal, cfpBoolean, cfpDate,
        cfpDateTime, cfpString, cfpCurrency
    }

    private enum cptPropID {pidIDPropType, pidType, pidTitle, pidSortKey, pidFormatString, pidDescription, pidFlags, pidFavorite, pidPreviousValues}

    // known custom property types
    static final int idPropTypeNightTakeOff = 73;
    static final int idPropTypeTachStart = 95;

    // DB Column names
    private static final String COL_IDPROPTYPE = "idPropType";
    private static final String COL_TITLE = "Title";
    private static final String COL_SORTKEY = "SortKey";
    private static final String COL_FORMATSTRING = "FormatString";
    private static final String COL_TYPE = "Type";
    private static final String COL_FLAGS = "Flags";
    private static final String COL_ISFAVORITE = "IsFavorite";
    private static final String COL_DESCRIPTION = "Description";
    private static final String COL_PreviousValues = "PreviousValues";

    public int idPropType = -1;
    String szTitle = "";
    private String szSortKey = "";
    String szDescription = "";
    String szFormatString = "";
    CFPPropertyType cptType = CFPPropertyType.cfpInteger;
    public int cptFlag = 0;
    public boolean IsFavorite = false;
    public String[] PreviousValues = new String[0];

    public CustomPropertyType() {
        super();
    }

    public CustomPropertyType(SoapObject so) {
        super();
        FromProperties(so);
    }

    static CustomPropertyType cptFromId(int id, CustomPropertyType[] rgcpt) {
        for (CustomPropertyType cpt : rgcpt) {
            if (cpt != null && cpt.idPropType == id)
                return cpt;
        }
        return null;
    }

    public int compareTo(@NonNull CustomPropertyType cpt) {
        // Favorites go before title
        if (this.IsFavorite == cpt.IsFavorite)
            return this.szSortKey.compareTo(cpt.szSortKey);
        else
            return this.IsFavorite ? -1 : 1;
    }

    @Override
    public String toString() {
        return this.szTitle;
    }

    public void FromCursor(Cursor c) {
        idPropType = c.getInt(c.getColumnIndex(COL_IDPROPTYPE));
        szTitle = c.getString(c.getColumnIndex(COL_TITLE));
        szSortKey = c.getString(c.getColumnIndex(COL_SORTKEY));
        szFormatString = c.getString(c.getColumnIndex(COL_FORMATSTRING));
        szDescription = (c.getString(c.getColumnIndex(COL_DESCRIPTION)));
        if (szDescription == null)
            szDescription = "";
        cptType = CFPPropertyType.values()[c.getInt(c.getColumnIndex(COL_TYPE))];
        cptFlag = c.getInt(c.getColumnIndex(COL_FLAGS));
        IsFavorite = (c.getInt(c.getColumnIndex(COL_ISFAVORITE)) != 0);
        String szPreviousValues = (c.getString(c.getColumnIndex(COL_PreviousValues)));
        PreviousValues = (szPreviousValues == null) ? new String[0] : szPreviousValues.split("\t");
    }

    public void ToContentValues(ContentValues cv) {
        cv.put(COL_IDPROPTYPE, idPropType);
        cv.put(COL_TITLE, szTitle);
        cv.put(COL_SORTKEY, szSortKey);
        cv.put(COL_FORMATSTRING, szFormatString);
        cv.put(COL_DESCRIPTION, szDescription);
        cv.put(COL_TYPE, cptType.ordinal());
        cv.put(COL_FLAGS, cptFlag);
        cv.put(COL_ISFAVORITE, IsFavorite);
        String szConcatValues = android.text.TextUtils.join("\t", PreviousValues);
        cv.put(COL_PreviousValues, szConcatValues);
    }

    @Override
    public void ToProperties(SoapObject so) {
        so.addProperty("PropTypeID", idPropType);
        so.addProperty("Title", szTitle);
        so.addProperty("SortKey", szSortKey);
        so.addProperty("FormatString", szFormatString);
        so.addProperty("Description", szDescription);
        so.addProperty("Type", cptType.toString());
        so.addProperty("Flags", cptFlag);
        so.addProperty("IsFavorite", IsFavorite);
        so.addProperty("PreviousValues", PreviousValues);
    }

    @Override
    public void FromProperties(SoapObject so) {
        idPropType = Integer.parseInt(so.getProperty("PropTypeID").toString());
        szTitle = so.getProperty("Title").toString();
        szSortKey = so.getPropertySafelyAsString("SortKey");
        szFormatString = so.getProperty("FormatString").toString();
        szDescription = ReadNullableString(so, "Description");
        cptType = CFPPropertyType.valueOf(so.getProperty("Type").toString());
        cptFlag = Integer.parseInt(so.getProperty("Flags").toString());
        IsFavorite = Boolean.parseBoolean(so.getProperty("IsFavorite").toString());
        SoapObject PrevVals = (SoapObject) so.getProperty("PreviousValues");
        int cVals = PrevVals.getPropertyCount();
        PreviousValues = new String[cVals];
        for (int i = 0; i < cVals; i++)
            PreviousValues[i] = PrevVals.getPropertyAsString(i);
    }

    public Object getProperty(int arg0) {
        cptPropID pid = cptPropID.values()[arg0];
        switch (pid) {
            case pidIDPropType:
                return this.idPropType;
            case pidTitle:
                return this.szTitle;
            case pidSortKey:
                return this.szSortKey;
            case pidFormatString:
                return this.szFormatString;
            case pidDescription:
                return this.szDescription;
            case pidFlags:
                return this.cptFlag;
            case pidFavorite:
                return this.IsFavorite;
            case pidType:
                return this.cptType.toString();
            case pidPreviousValues:
			    return new Vector<>(Arrays.asList(this.PreviousValues));
            default:
                break;
        }
        return null;
    }

    public int getPropertyCount() {
        return cptPropID.values().length;
    }

    @SuppressWarnings("rawtypes")
    public void getPropertyInfo(int arg0, Hashtable arg1, PropertyInfo pi) {
        cptPropID pid = cptPropID.values()[arg0];
        switch (pid) {
            case pidFavorite:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "IsFavorite";
                break;
            case pidFlags:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "Flags";
                break;
            case pidFormatString:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "FormatString";
                break;
            case pidDescription:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Description";
                break;
            case pidIDPropType:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "PropTypeID";
                break;
            case pidTitle:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Title";
                break;
            case pidSortKey:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "SortKey";
                break;
            case pidType:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Type";
                break;
            case pidPreviousValues:
                pi.type = PropertyInfo.VECTOR_CLASS;
                pi.name = "PreviousValues";
                pi.elementType = new PropertyInfo();
                pi.elementType.type = PropertyInfo.STRING_CLASS;
                pi.elementType.name = "string";
                break;
        }
    }

    public void setProperty(int arg0, Object arg1) {
        cptPropID pid = cptPropID.values()[arg0];
        String sz = arg1.toString();
        switch (pid) {
            case pidFavorite:
                this.IsFavorite = Boolean.parseBoolean(sz);
                break;
            case pidFlags:
                this.cptFlag = Integer.parseInt(sz);
                break;
            case pidFormatString:
                this.szFormatString = sz;
                break;
            case pidDescription:
                this.szDescription = sz;
                break;
            case pidType:
                this.cptType = CFPPropertyType.valueOf(sz);
                break;
            case pidTitle:
                this.szTitle = sz;
                break;
            case pidSortKey:
                this.szSortKey = sz;
                break;
            case pidIDPropType:
                this.idPropType = Integer.parseInt(sz);
                break;
            case pidPreviousValues:
                this.PreviousValues = (String[]) arg1;
                break;
        }
    }

    //region Pinned properties
    public static final String prefSharedPinnedProps = "prefsSharedPinnedProps";
    private static final String prefKeyPinnedProperties = "keyPinnedProperties";

    public static HashSet<Integer> getPinnedProperties(SharedPreferences pref) {
        Set<String> stringVals = pref.getStringSet(prefKeyPinnedProperties, new HashSet<>());
        HashSet<Integer> result = new HashSet<>();
        for (String s : stringVals)
            result.add(Integer.parseInt(s));
        return result;
    }

    public static Boolean isPinnedProperty(SharedPreferences pref, int id) {
        return isPinnedProperty(getPinnedProperties(pref), id);
    }

    public static Boolean isPinnedProperty(HashSet<Integer> pinnedProps, int id) {
        return pinnedProps.contains(id);
    }

    public static void setPinnedProperty(SharedPreferences pref, int id) {
        Set<String> stringVals = pref.getStringSet(prefKeyPinnedProperties, new HashSet<>());
        HashSet<String> newSet = new HashSet<>(stringVals);
        newSet.add(String.format(Locale.US, "%d", id));
        SharedPreferences.Editor e = pref.edit();
        e.putStringSet(prefKeyPinnedProperties, newSet);
        e.apply();
    }

    public static void removePinnedProperty(SharedPreferences pref, int id) {
        Set<String> stringVals = pref.getStringSet(prefKeyPinnedProperties, new HashSet<>());

        String sRemove = String.format(Locale.US, "%d", id);
        if (!stringVals.contains(sRemove))
            return;

        // Can't modify the returned set; need to create a new one.
        HashSet<String> newSet = new HashSet<>(stringVals);
        newSet.remove(sRemove);

        SharedPreferences.Editor e = pref.edit();
        e.putStringSet(prefKeyPinnedProperties, newSet);
        e.apply();
    }
    //endregion
}
