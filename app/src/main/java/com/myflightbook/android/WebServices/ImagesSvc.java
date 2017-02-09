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

import com.myflightbook.android.Marshal.MarshalDouble;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;

import Model.LatLong;
import Model.MFBImageInfo;

public class ImagesSvc extends MFBSoap implements Runnable {

    @Override
    public void AddMappings(SoapSerializationEnvelope e) {
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo.class);
        e.addMapping(NAMESPACE, "LatLong", LatLong.class);
        MarshalDouble md = new MarshalDouble();
        md.register(e);
    }

    public void run() {
        Invoke();
    }

    public void DeleteImage(String szAuthToken, MFBImageInfo mfbii) {
        SoapObject Request = setMethod("DeleteImage");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("mfbii", mfbii);

        new Thread(this).start();
    }

    public void UpdateImageAnnotation(String szAuthToken, MFBImageInfo mfbii) {
        SoapObject Request = setMethod("UpdateImageAnnotation");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("mfbii", mfbii);

        new Thread(this).start();
    }
}
