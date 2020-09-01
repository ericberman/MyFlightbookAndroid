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

import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.io.Serializable;
import java.util.Hashtable;

import androidx.annotation.NonNull;

public class MakeModel extends SoapableObject implements KvmSerializable, Serializable, Comparable<MakeModel> {

    private static final long serialVersionUID = 1L;
    public String Description = "";
    public int MakeModelId = -1;

    private final String KEY_MODELID = "MakeModelID";
    private final String KEY_DESCRIPTION = "ModelName";

    private enum MakeModelProp {pidMakeModelID, pidModelName}

    public MakeModel() {
        super();
    }

    @NonNull
    @Override
    public String toString() {
        return Description;
    }

    @Override
    public void ToProperties(SoapObject so) {
        so.addProperty(KEY_DESCRIPTION, Description);
        so.addProperty(KEY_MODELID, MakeModelId);
    }

    @Override
    public void FromProperties(SoapObject so) {
        Description = so.getProperty(KEY_DESCRIPTION).toString();
        MakeModelId = Integer.parseInt(so.getProperty(KEY_MODELID).toString());
    }

    // serialization methods
    public int getPropertyCount() {
        return MakeModelProp.values().length;
    }

    public Object getProperty(int i) {
        MakeModelProp mmp = MakeModelProp.values()[i];
        switch (mmp) {
            case pidMakeModelID:
                return MakeModelId;
            case pidModelName:
                return Description;
            default:
                return null;
        }
    }

    public void setProperty(int i, Object value) {
        MakeModelProp mmp = MakeModelProp.values()[i];
        String sz = value.toString();
        switch (mmp) {
            case pidMakeModelID:
                MakeModelId = Integer.parseInt(sz);
                break;
            case pidModelName:
                Description = sz;
                break;
            default:
                break;
        }
    }

    public void getPropertyInfo(int i, Hashtable h, PropertyInfo pi) {
        MakeModelProp mmp = MakeModelProp.values()[i];
        switch (mmp) {
            case pidMakeModelID:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "MakeModelID";
                break;
            case pidModelName:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "ModelName";
                break;
            default:
                break;
        }
    }

    public int compareTo(@NonNull MakeModel another) {
        return this.Description.compareToIgnoreCase(another.Description);
    }
}
