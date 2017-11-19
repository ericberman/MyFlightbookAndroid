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
package com.myflightbook.android.WebServices;

import android.content.Context;

import org.ksoap2.serialization.SoapObject;

import Model.MakesandModels;

public class MakesandModelsSvc extends MFBSoap {

    public MakesandModels[] GetMakesAndModels(Context c) {
        setMethod("MakesAndModels"); // no need to save the request, since nothing goes out

        MakesandModels[] rgMM = new MakesandModels[0];

        SoapObject result = (SoapObject) Invoke(c);
        if (result == null)
            setLastError("Error retrieving makes and models - " + getLastError());
        else {
            try {
                rgMM = new MakesandModels[result.getPropertyCount()];

                for (int i = 0; i < rgMM.length; i++) {
                    rgMM[i] = new MakesandModels((SoapObject) result.getProperty(i));
                }
            } catch (Exception e) {
                setLastError(getLastError() + e.getMessage());
            }
        }

        return rgMM;
    }
}
