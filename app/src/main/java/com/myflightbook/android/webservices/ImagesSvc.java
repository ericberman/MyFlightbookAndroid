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
package com.myflightbook.android.webservices;

import android.content.Context;

import com.myflightbook.android.marshal.MarshalDouble;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;

import model.LatLong;
import model.MFBImageInfo;

public class ImagesSvc extends MFBSoap implements Runnable {

    private Context mContext;

    @Override
    public void AddMappings(SoapSerializationEnvelope e) {
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo.class);
        e.addMapping(NAMESPACE, "LatLong", LatLong.class);
        MarshalDouble md = new MarshalDouble();
        md.register(e);
    }

    public void run() {
        Invoke(mContext);
    }

    public void DeleteImage(String szAuthToken, MFBImageInfo mfbii, Context c) {
        SoapObject Request = setMethod("DeleteImage");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("mfbii", mfbii);
        mContext = c;

        new Thread(this).start();
    }

    public void UpdateImageAnnotation(String szAuthToken, MFBImageInfo mfbii, Context c) {
        SoapObject Request = setMethod("UpdateImageAnnotation");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("mfbii", mfbii);
        mContext = c;

        new Thread(this).start();
    }
}
