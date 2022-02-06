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
package model;

import android.content.res.Resources;

import com.myflightbook.android.MFBMain;
import com.myflightbook.android.R;

import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;

import androidx.annotation.NonNull;

public class CategoryClass extends SoapableObject implements KvmSerializable, Serializable {

    private static final long serialVersionUID = 2L;

    private enum CatClassID {
        none, ASEL, AMEL, ASES, AMES, Glider, Helicopter, Gyroplane, PoweredLift, Airship, HotAirBalloon, GasBalloon,
        PoweredParachuteLand, PoweredParachuteSea, WeightShiftControlLand, WeightShiftControlSea, UnmannedAerialSystem, PoweredParaglider
    }

    private String CatClass;
    private String Category;
    private String Class;
    private int AltCatClass;
    public CatClassID IdCatClass;

    private enum CatClassProp {pidCatClass, pidCategory, pidClass, pidAltCatClass, pidCatClassId}

    private void Init(CatClassID ccid) {
        IdCatClass = ccid;
        CatClass = LocalizedString();
        Category = Class = "";
        AltCatClass = 0;
    }

	CategoryClass()
	{
		super();
		Init(CatClassID.none);
	}

	private CategoryClass(CatClassID ccid)
	{
        super();
        Init(ccid);
    }

    private String LocalizedString() {
        Resources r = MFBMain.getAppResources();

        switch (IdCatClass) {
            default:
            case none:
                return "(none)";
            case ASEL:
                return r.getString(R.string.ccASEL);
            case AMEL:
                return r.getString(R.string.ccAMEL);
            case ASES:
                return r.getString(R.string.ccASES);
            case AMES:
                return r.getString(R.string.ccAMES);
            case Glider:
                return r.getString(R.string.ccGlider);
            case Helicopter:
                return r.getString(R.string.ccHelicopter);
            case Gyroplane:
                return r.getString(R.string.ccGyroplane);
            case PoweredLift:
                return r.getString(R.string.ccPoweredLift);
            case Airship:
                return r.getString(R.string.ccAirship);
            case HotAirBalloon:
                return r.getString(R.string.ccHotAirBalloon);
            case GasBalloon:
                return r.getString(R.string.ccGasBalloon);
            case PoweredParachuteLand:
                return r.getString(R.string.ccPoweredParachuteLand);
            case PoweredParachuteSea:
                return r.getString(R.string.ccPoweredParachuteSea);
            case WeightShiftControlLand:
                return r.getString(R.string.ccWeightShiftControlLand);
            case WeightShiftControlSea:
                return r.getString(R.string.ccWeightShiftControlSea);
            case UnmannedAerialSystem:
                return r.getString(R.string.ccUAS);
            case PoweredParaglider:
                return r.getString(R.string.ccPoweredParaglider);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return LocalizedString();
    }

    public int getPropertyCount() {
        return CatClassProp.values().length;
    }

    public Object getProperty(int arg0) {
        CatClassProp ccp = CatClassProp.values()[arg0];

        switch (ccp) {
            case pidCatClass:
                return CatClass;
            case pidCategory:
                return Category;
            case pidClass:
                return Class;
            case pidAltCatClass:
                return AltCatClass;
            case pidCatClassId:
                return IdCatClass.toString();
            default:
                break;
        }
        return null;
    }

    public void getPropertyInfo(int i, Hashtable arg1, PropertyInfo pi) {
        CatClassProp ccp = CatClassProp.values()[i];
        switch (ccp) {
            // Date properties.
            case pidCatClass:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "CatClass";
                break;
            case pidCategory:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Category";
                break;
            case pidClass:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Class";
                break;
            case pidAltCatClass:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "AltCatClass";
                break;
            case pidCatClassId:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "IdCatClass";
                break;
        }
    }

    public void setProperty(int i, Object value) {
        CatClassProp ccp = CatClassProp.values()[i];
        String sz = value.toString();
        switch (ccp) {
            case pidCatClass:
                CatClass = sz;
                break;
            case pidCategory:
                Category = sz;
                break;
            case pidClass:
                Class = sz;
                break;
            case pidAltCatClass:
                AltCatClass = Integer.parseInt(sz);
                break;
            case pidCatClassId:
                IdCatClass = CatClassID.valueOf(sz);
                break;
        }
    }


    public void ToProperties(SoapObject so) {
        so.addProperty("CatClass", CatClass);
        so.addProperty("Category", Category);
        so.addProperty("Class", Class);
        so.addProperty("AltCatClass", AltCatClass);
        so.addProperty("IdCatClass", IdCatClass.toString());
    }

    public void FromProperties(SoapObject so) {
        CatClass = so.getPropertyAsString("CatClass");
        Category = so.getPropertyAsString("Category");
        Class = so.getPropertyAsString("Class");
        AltCatClass = Integer.parseInt(so.getPropertyAsString("AltCatClass"));
        IdCatClass = CatClassID.valueOf(so.getPropertyAsString("IdCatClass"));
    }

    public static CategoryClass[] AllCatClasses() {
        ArrayList<CategoryClass> lst = new ArrayList<>();
        for (CatClassID ccid : CatClassID.values())
            if (ccid != CatClassID.none)
                lst.add(new CategoryClass(ccid));
        return lst.toArray(new CategoryClass[0]);
    }
}
