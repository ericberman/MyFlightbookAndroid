/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2020 MyFlightbook, LLC

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

import android.content.SharedPreferences;

import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Locale;

import androidx.annotation.NonNull;

public class PropertyTemplate  extends SoapableObject implements Comparable<PropertyTemplate>, Serializable, KvmSerializable {
    private static final long serialVersionUID = 1L;

    private final static int ID_NEW = -1;
    private final static int ID_MRU = -2;
    private final static int ID_SIM = -3;
    private final static int ID_ANON = -4;

    final static int GROUP_AUTO = 0;

    public static PropertyTemplate[] sharedTemplates;

    public static final String PREF_KEY_TEMPLATES = "preferenceKeyTemplates";

    private enum templatePropID {pidTemplateID, pidTemplateName, pidTemplateDescription, pidTemplateGroupAsInt, pidTemplateGroupDisplayName, pidTemplatePropertyTypes, pidTemplateDefault}

    // region basic
    private int ID;
    public String Name;
    public String Description;
    int GroupAsInt;
    String GroupDisplayName;
    private HashSet<Integer> PropertyTypes;
    private Boolean IsDefault;

    private void init() {
        ID = -1;
        Name = Description = GroupDisplayName = "";
        PropertyTypes = new HashSet<>();
    }

    public PropertyTemplate() {
        init();
    }

    public PropertyTemplate(SoapObject so) {
        super();
        init();
        FromProperties(so);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%d: (%s) %s %s", ID, GroupDisplayName, Name, IsDefault ? " (default)" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PropertyTemplate))
            return false;
        if (o == this)
            return true;
        return ID == ((PropertyTemplate)o).ID;
    }

    @Override
    public int hashCode() {
        return ((Integer)ID).hashCode();
    }

    @Override
    public int compareTo(@NonNull PropertyTemplate o) {
        if (this.GroupAsInt == o.GroupAsInt) {
            if (this.GroupAsInt == GROUP_AUTO)    // Automatic groups are always first - so if we're here, then both of us are automatic; resolve on ID inverse
                return Integer.compare(o.ID, this.ID);
            else
                return this.Name.compareTo(o.Name);
        }
        return Integer.compare(this.GroupAsInt, o.GroupAsInt);
    }
    // endregion

    // region SOAP
    @Override
    public void ToProperties(SoapObject so) {
        so.addProperty("ID", ID);
        so.addProperty("Name", Name);
        so.addProperty("Description", Description);
        so.addProperty("GroupAsInt", GroupAsInt);
        so.addProperty("GroupDisplayName", GroupDisplayName);
        so.addProperty("PropertyTypes", PropertyTypes.toArray());
        so.addProperty("IsDefault", IsDefault);
    }

    @Override
    protected void FromProperties(SoapObject so) {
        ID = Integer.parseInt(so.getProperty("ID").toString());
        Name = so.getProperty("Name").toString();
        Description = so.getProperty("Description").toString();
        GroupAsInt = Integer.parseInt(so.getProperty("GroupAsInt").toString());
        GroupDisplayName = so.getProperty("GroupDisplayName").toString();
        PropertyTypes.clear();
        SoapObject types = (SoapObject) so.getProperty("PropertyTypes");
        int cVals = types.getPropertyCount();
        for (int i = 0; i < cVals; i++)
            PropertyTypes.add(Integer.parseInt(types.getPropertyAsString(i)));
        IsDefault = Boolean.parseBoolean(so.getProperty("IsDefault").toString());
    }

    public Object getProperty(int arg0) {
        templatePropID pid = templatePropID.values()[arg0];
        switch (pid) {
            case pidTemplateID:
                return this.ID;
            case pidTemplateName:
                return this.Name;
            case pidTemplateDescription:
                return this.Description;
            case pidTemplateGroupAsInt:
                return this.GroupAsInt;
            case pidTemplateGroupDisplayName:
                return this.GroupDisplayName;
            case pidTemplatePropertyTypes:
                return this.PropertyTypes;
            case pidTemplateDefault:
                return this.IsDefault;
        }
        return null;
    }

    public int getPropertyCount() {
        return templatePropID.values().length;
    }

    public void getPropertyInfo(int arg0, Hashtable arg1, PropertyInfo pi) {
        templatePropID pid = templatePropID.values()[arg0];
        switch (pid) {
            case pidTemplateID:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "ID";
            case pidTemplateName:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Name";
            case pidTemplateDescription:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Description";
            case pidTemplateGroupAsInt:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "GroupAsInt";
            case pidTemplateGroupDisplayName:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "GroupDisplayName";
            case pidTemplatePropertyTypes:
                pi.type = PropertyInfo.VECTOR_CLASS;
                pi.name = "PropertyTypes";
                pi.elementType = new PropertyInfo();
                pi.elementType.type = PropertyInfo.INTEGER_CLASS;
                pi.elementType.name = "integer";
            case pidTemplateDefault:
                pi.type = PropertyInfo.BOOLEAN_CLASS;
                pi.name = "IsDefault";
            default:
                break;
        }
    }

    public void setProperty(int arg0, Object arg1) {
        templatePropID pid = templatePropID.values()[arg0];
        String sz = arg1.toString();
        switch (pid) {
            case pidTemplateID:
                ID = Integer.parseInt(sz);
            case pidTemplateName:
                Name = sz;
            case pidTemplateDescription:
                Description = sz;
            case pidTemplateGroupAsInt:
                GroupAsInt = Integer.parseInt(sz);
            case pidTemplateGroupDisplayName:
                GroupDisplayName = sz;
            case pidTemplatePropertyTypes:
                PropertyTypes.clear();
                int[] rgVals = (int[]) arg1;
                for (int i : rgVals)
                    PropertyTypes.add(i);
            case pidTemplateDefault:
                IsDefault = Boolean.parseBoolean(sz);
            default:
                break;
        }
    }
    // endregion

    // region Retrieving/persistance
    private static final String prefSharedTemplates = "prefsSharedTemplates";

    public static void saveSharedTemplates(SharedPreferences pref) {
        SharedPreferences.Editor e = pref.edit();
        e.putString(prefSharedTemplates, MFBUtil.serializeToString(sharedTemplates));
        e.apply();
        e.commit();
    }

    public static PropertyTemplate[] getSharedTemplates(SharedPreferences pref) {
        String s = pref.getString(prefSharedTemplates, "");
        sharedTemplates = MFBUtil.deserializeFromString(s);
        return sharedTemplates;
    }

    public static PropertyTemplate[] getDefaultTemplates() {
        ArrayList<PropertyTemplate> al = new ArrayList<>();
        for (PropertyTemplate pt : PropertyTemplate.sharedTemplates) {
            if (pt.IsDefault)
                al.add(pt);
        }
        return al.toArray(new PropertyTemplate[0]);
    }

    private static PropertyTemplate templateWithID(int id) {
        if (sharedTemplates != null) {
            for (PropertyTemplate pt : sharedTemplates) {
                if (pt.ID == id)
                    return pt;
            }
        }
        return null;
    }

    public static PropertyTemplate[] templatesWithIDs(HashSet<Integer> rgid) {
        ArrayList<PropertyTemplate> al = new ArrayList<>();
        if (rgid != null) {
            for (int i : rgid)
                al.add(PropertyTemplate.templateWithID(i));
        }
        return al.toArray(new PropertyTemplate[0]);
    }

    public static PropertyTemplate getAnonTemplate() { return templateWithID(ID_ANON); }

    public static PropertyTemplate getSimTemplate() { return templateWithID(ID_SIM); }
    // endregion

    // region merging
    public static HashSet<Integer> mergeTemplates(PropertyTemplate[] rgpt) {
        HashSet<Integer> result = new HashSet<>();
        if (rgpt != null) {
            for (PropertyTemplate pt : rgpt)
                if (pt != null && pt.PropertyTypes != null)
                    result.addAll(pt.PropertyTypes);
        }
        return result;
    }
    // endregion

}