/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025 MyFlightbook, LLC

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
import android.util.Log
import com.myflightbook.android.R
import com.myflightbook.android.marshal.MarshalDate
import com.myflightbook.android.marshal.MarshalDouble
import model.*
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import org.ksoap2.serialization.SoapSerializationEnvelope

class CommitFlightSvc : MFBSoap() {
    override fun addMappings(e: SoapSerializationEnvelope) {
        e.addMapping(NAMESPACE, "CommitFlightWithOptionsResult", LogbookEntry::class.java)
        e.addMapping(NAMESPACE, "le", LogbookEntry::class.java)
        e.addMapping(NAMESPACE, "LogbookEntry", LogbookEntry::class.java)
        e.addMapping(NAMESPACE, "CustomFlightProperty", FlightProperty::class.java)
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo::class.java)
        e.addMapping(NAMESPACE, "LatLong", LatLong::class.java)
        val mdt = MarshalDate()
        val md = MarshalDouble()
        mdt.register(e)
        md.register(e)
    }

    fun checkFlight(szAuthToken: String?, le: LogbookEntry, c: Context) : Array<String> {
        val request = setMethod("CheckFlight")
        request.addProperty("szAuthUserToken", szAuthToken)
        val piLe = PropertyInfo()
        piLe.name = "le"
        piLe.type = "LogbookEntry"
        piLe.value = le
        piLe.namespace = NAMESPACE
        request.addProperty(piLe)

        val result : ArrayList<String> = ArrayList()
        val r = invoke(c) as SoapObject?
        if (r != null) {
            try {
                for (i in 0 until r.propertyCount)
                    result.add(r.getProperty(i).toString())
            } catch (e: Exception) {
                lastError += e.message
            }
        }
        return result.toTypedArray()
    }

    fun fCommitFlightForUser(szAuthToken: String?, le: LogbookEntry, c: Context): Boolean {
        val request = setMethod("CommitFlightWithOptions")
        request.addProperty("szAuthUserToken", szAuthToken)
        val piLe = PropertyInfo()
        val pipo = PropertyInfo()
        piLe.name = "le"
        piLe.type = "LogbookEntry"
        piLe.value = le
        piLe.namespace = NAMESPACE
        pipo.name = "po"
        pipo.type = "PostingOptions"
        pipo.value = null
        pipo.namespace = NAMESPACE
        request.addProperty(piLe)
        request.addProperty(pipo)

        if (le.rgFlightImages == null) le.imagesForFlight
        val rgmfbii = le.rgFlightImages
            ?: throw NullPointerException("le.rgFlightImages is null")
        le.rgFlightImages = arrayOf()
        val result = invoke(c) as SoapObject? ?: return false
        try {
            val leReturn = LogbookEntry()
            leReturn.fromProperties(result)
            le.szError = leReturn.szError
            le.idFlight = leReturn.idFlight
            var iImg = 1
            for (mfbii in rgmfbii) {
                val szFmtUploadProgress = c.getString(R.string.prgUploadingImages)
                val szStatus = String.format(szFmtUploadProgress, iImg, rgmfbii.size)
                mProgress?.notifyProgress(
                    iImg * 100 / rgmfbii.size,
                    szStatus
                )
                // skip anything that is already on the server.
                if (!mfbii.isOnServer()) {
                    mfbii.setKey(leReturn.idFlight)
                    if (!mfbii.uploadPendingImage(mfbii.id, c)) Log.w(
                        MFBConstants.LOG_TAG,
                        "Image Upload failed"
                    )
                }
                iImg++
            }
            le.deletePendingImagesForFlight()
        } catch (e: Exception) {
            lastError += e.message
        }
        le.szError = lastError
        return lastError.isEmpty()
    }
}