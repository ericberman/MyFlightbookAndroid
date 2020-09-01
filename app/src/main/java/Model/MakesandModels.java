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

import org.ksoap2.serialization.SoapObject;

import androidx.annotation.NonNull;


public class MakesandModels extends SoapableObject {

    public String Description = "";
    public int ModelId = -1;

    private final String KEY_MODELID = "ModelID";
    private final String KEY_DESCRIPTION = "Description";

    public MakesandModels(SoapObject so) {
        super();
        FromProperties(so);
    }

    @NonNull
    @Override
    public String toString() {
        return Description;
    }

    public String getManufacturer() {
        String MANUFACTURER_BREAK = " (";
        if (Description.contains(MANUFACTURER_BREAK))
            return Description.substring(0, Description.indexOf(MANUFACTURER_BREAK));
        else
            return Description;
    }

    public static MakesandModels getMakeModelByID(int modelId, MakesandModels[] rgmm) {
        if (rgmm != null)
            for (MakesandModels mm : rgmm)
                if (mm.ModelId == modelId)
                    return mm;
        return null;
    }

    @Override
    public void ToProperties(SoapObject so) {
        so.addProperty(KEY_DESCRIPTION, Description);
        so.addProperty(KEY_MODELID, ModelId);
    }

    @Override
    protected void FromProperties(SoapObject so) {
        Description = so.getProperty(KEY_DESCRIPTION).toString();
        ModelId = Integer.parseInt(so.getProperty(KEY_MODELID).toString());
    }

}
