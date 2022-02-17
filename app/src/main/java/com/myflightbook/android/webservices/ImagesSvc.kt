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
package com.myflightbook.android.webservices

import android.content.Context
import org.ksoap2.serialization.SoapSerializationEnvelope
import model.MFBImageInfo
import model.LatLong
import com.myflightbook.android.marshal.MarshalDouble

class ImagesSvc : MFBSoap(), Runnable {
    private var mContext: Context? = null
    override fun addMappings(e: SoapSerializationEnvelope) {
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo::class.java)
        e.addMapping(NAMESPACE, "LatLong", LatLong::class.java)
        val md = MarshalDouble()
        md.register(e)
    }

    override fun run() {
        invoke(mContext)
    }

    fun deleteImage(szAuthToken: String?, mfbii: MFBImageInfo?, c: Context?) {
        val request = setMethod("DeleteImage")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("mfbii", mfbii)
        mContext = c
        Thread(this).start()
    }

    fun updateImageAnnotation(szAuthToken: String?, mfbii: MFBImageInfo?, c: Context?) {
        val request = setMethod("UpdateImageAnnotation")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("mfbii", mfbii)
        mContext = c
        Thread(this).start()
    }
}