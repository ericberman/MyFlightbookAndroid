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

import org.ksoap2.serialization.SoapSerializationEnvelope
import model.MFBImageInfo
import model.LatLong
import model.Aircraft
import com.myflightbook.android.marshal.MarshalDouble
import com.myflightbook.android.MFBMain
import model.Aircraft.PilotRole
import model.MFBConstants
import model.DBCache
import android.content.ContentValues
import android.content.Context
import android.text.TextUtils
import android.util.Log
import model.MFBImageInfo.PictureDestination
import com.myflightbook.android.ActRecentsWS
import org.ksoap2.serialization.SoapObject
import model.DBCache.DBCacheStatus
import com.myflightbook.android.R
import com.myflightbook.android.marshal.MarshalDate
import java.lang.Error
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class AircraftSvc : MFBSoap() {
    override fun addMappings(e: SoapSerializationEnvelope) {
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo::class.java)
        e.addMapping(NAMESPACE, "LatLong", LatLong::class.java)
        e.addMapping(NAMESPACE, "Aircraft", Aircraft::class.java)
        val mdt = MarshalDate()
        val md = MarshalDouble()
        mdt.register(e)
        md.register(e)
    }

    val cachedAircraft: Array<Aircraft>
        get() {
            val rgac : MutableList<Aircraft> = mutableListOf()
            val rgAircraftImages = MFBImageInfo.allAircraftImages
            val db = MFBMain.mDBHelper!!.writableDatabase
            try {
                db.query(TABLENAME, null, null, null, null, null, null).use { c ->
                    if (c != null) {
                        val cAircraftID = c.getColumnIndexOrThrow(COL_AIRCRAFTID)
                        val cTailNum = c.getColumnIndexOrThrow(COL_TAILNUM)
                        val cVersion = c.getColumnIndexOrThrow(COL_VERSION)
                        val cRevision = c.getColumnIndexOrThrow(COL_REVISION)
                        val cICAO = c.getColumnIndexOrThrow(COL_ICAO)
                        val cModelId = c.getColumnIndexOrThrow(COL_MODELID)
                        val cInstanceId = c.getColumnIndexOrThrow(COL_INSTANCEID)
                        val cModelName = c.getColumnIndexOrThrow(COL_MODELNAME)
                        val cModelDesc = c.getColumnIndexOrThrow(COL_MODELDESCRIPTION)
                        val cLastVOR = c.getColumnIndexOrThrow(COL_LASTVOR)
                        val cLastAltimeter = c.getColumnIndexOrThrow(COL_LASTALTIMETER)
                        val cLastTransponder = c.getColumnIndexOrThrow(COL_LASTTRANSPONDER)
                        val cLastELT = c.getColumnIndexOrThrow(COL_LASTELT)
                        val cLastStatic = c.getColumnIndexOrThrow(COL_LASTSTATIC)
                        val cLastAnnual = c.getColumnIndexOrThrow(COL_LASTANNUAL)
                        val cRegistrationDue = c.getColumnIndexOrThrow(COL_REGISTRATIONDUE)
                        val cLast100 = c.getColumnIndexOrThrow(COL_LAST100)
                        val cLastOil = c.getColumnIndexOrThrow(COL_LASTOIL)
                        val cLastEngine = c.getColumnIndexOrThrow(COL_LASTENGINE)
                        val cHideSelection = c.getColumnIndexOrThrow(COL_HIDEFROMSELECTION)
                        val cCopyPICName = c.getColumnIndexOrThrow(COL_COPYPICName)
                        val cRoleForPilot = c.getColumnIndexOrThrow(COL_ROLEFORPILOT)
                        val cPublicNotes = c.getColumnIndexOrThrow(COL_PUBLICNOTES)
                        val cPrivateNotes = c.getColumnIndexOrThrow(COL_PRIVATENOTES)
                        val cDefaultImage = c.getColumnIndexOrThrow(COL_DEFAULTIMAGE)
                        val cDefaultTemplateIDs = c.getColumnIndexOrThrow(COL_DEFAULTTEMPLATEIDS)
                        val cIsGlass = c.getColumnIndexOrThrow(COL_ISGLASS)
                        val cGlassUpgradeDate = c.getColumnIndexOrThrow(COL_GLASSUPGRADEDATE)
                        val cAvionicsUpgradeType = c.getColumnIndexOrThrow(COL_AVIONICSUPGRADETYPE)
                        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        while (c.moveToNext()) {
                            val ac = Aircraft()
                            try {
                                ac.aircraftID = c.getInt(cAircraftID)
                                ac.instanceTypeID = c.getInt(cInstanceId)
                                ac.version = c.getInt(cVersion)
                                ac.revision = c.getInt(cRevision)
                                ac.modelICAO = c.getString(cICAO)
                                ac.modelCommonName = c.getString(cModelName)
                                ac.modelDescription = c.getString(cModelDesc)
                                ac.modelID = c.getInt(cModelId)
                                ac.tailNumber = c.getString(cTailNum)
                                ac.publicNotes = c.getString(cPublicNotes)
                                ac.privateNotes = c.getString(cPrivateNotes)
                                ac.mDefaultImage = c.getString(cDefaultImage)
                                ac.hideFromSelection = c.getInt(cHideSelection) != 0
                                ac.copyPICNameWithCrossfill = c.getInt(cCopyPICName) != 0
                                ac.roleForPilot = PilotRole.values()[c.getInt(cRoleForPilot)]
                                ac.lastVOR = df.parse(c.getString(cLastVOR))
                                ac.lastAltimeter = df.parse(c.getString(cLastAltimeter))
                                ac.lastTransponder = df.parse(c.getString(cLastTransponder))
                                ac.lastELT = df.parse(c.getString(cLastELT))
                                ac.lastStatic = df.parse(c.getString(cLastStatic))
                                ac.lastAnnual = df.parse(c.getString(cLastAnnual))
                                ac.registrationDue = df.parse(c.getString(cRegistrationDue))
                                ac.last100 = c.getDouble(cLast100)
                                ac.lastOil = c.getDouble(cLastOil)
                                ac.lastEngine = c.getDouble(cLastEngine)
                                ac.isGlass = c.getInt(cIsGlass) != 0
                                val szGlassUpgrade = c.getString(cGlassUpgradeDate)
                                ac.glassUpgradeDate =
                                    if (szGlassUpgrade == null) null else df.parse(szGlassUpgrade)
                                ac.avionicsTechnologyUpgrade =
                                    Aircraft.AvionicsTechnologyType.values()[c.getInt(
                                        cAvionicsUpgradeType
                                    )]
                                val szTemplateIDs = c.getString(cDefaultTemplateIDs)
                                val rgIDs = szTemplateIDs.split(" ").toTypedArray()
                                for (sz in rgIDs) {
                                    if (sz.trim { it <= ' ' }.isNotEmpty()) ac.defaultTemplates.add(sz.toInt())
                                }
                            } catch (ex: Exception) {
                                Log.e(
                                    MFBConstants.LOG_TAG,
                                    "Error getting cached aircraft: " + ex.message
                                )
                            }
                            ac.aircraftImages =
                                MFBImageInfo.getAircraftImagesForId(ac.aircraftID, rgAircraftImages)
                            rgac.add(ac)
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e(MFBConstants.LOG_TAG, "Error getting cached aircraft from db: " + ex.message)
            }
            return rgac.toTypedArray()
        }

    private fun updateCache(rgac: Array<Aircraft?>) {
        // note that these calls will close the db, so we do them first.
        val dbc = DBCache()
        dbc.flushCache(TABLENAME, true)
        var fResult = false

        // now, we get an open db
        val db = MFBMain.mDBHelper!!.writableDatabase
        try {
            // I've read that multiple inserts are much faster inside a transaction.
            db.beginTransaction()
            try {
                val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                for (ac in rgac) {
                    val cv = ContentValues()
                    cv.put(COL_AIRCRAFTID, ac!!.aircraftID)
                    cv.put(COL_INSTANCEID, ac.instanceTypeID)
                    cv.put(COL_VERSION, ac.version)
                    cv.put(COL_REVISION, ac.revision)
                    cv.put(COL_ICAO, ac.modelICAO)
                    cv.put(COL_MODELID, ac.modelID)
                    cv.put(COL_MODELDESCRIPTION, ac.modelDescription)
                    cv.put(COL_MODELNAME, ac.modelCommonName)
                    cv.put(COL_TAILNUM, ac.tailNumber)
                    cv.put(COL_LASTVOR, if (ac.lastVOR == null) null else df.format(ac.lastVOR!!))
                    cv.put(COL_LASTALTIMETER, if (ac.lastAltimeter == null) null else df.format(ac.lastAltimeter!!))
                    cv.put(COL_LASTTRANSPONDER, if (ac.lastTransponder == null) null else df.format(ac.lastTransponder!!))
                    cv.put(COL_LASTELT, if (ac.lastELT == null) null else df.format(ac.lastELT!!))
                    cv.put(COL_LASTSTATIC, if (ac.lastStatic == null) null else df.format(ac.lastStatic!!))
                    cv.put(COL_LASTANNUAL, if (ac.lastAnnual == null) null else df.format(ac.lastAnnual!!))
                    cv.put(COL_REGISTRATIONDUE, if (ac.registrationDue == null) null else df.format(ac.registrationDue!!))
                    cv.put(COL_LAST100, ac.last100)
                    cv.put(COL_LASTOIL, ac.lastOil)
                    cv.put(COL_LASTENGINE, ac.lastEngine)
                    cv.put(COL_HIDEFROMSELECTION, ac.hideFromSelection)
                    cv.put(COL_COPYPICName, ac.copyPICNameWithCrossfill)
                    cv.put(COL_ROLEFORPILOT, ac.roleForPilot.ordinal)
                    cv.put(COL_PUBLICNOTES, ac.publicNotes)
                    cv.put(COL_PRIVATENOTES, ac.privateNotes)
                    cv.put(COL_DEFAULTIMAGE, ac.mDefaultImage)
                    cv.put(COL_ISGLASS, ac.isGlass)
                    cv.put(
                        COL_GLASSUPGRADEDATE, if (ac.glassUpgradeDate == null) null else df.format(
                            ac.glassUpgradeDate!!
                        )
                    )
                    cv.put(COL_AVIONICSUPGRADETYPE, ac.avionicsTechnologyUpgrade.ordinal)
                    val al = ArrayList<String?>()
                    for (idTemplate in ac.defaultTemplates) al.add(idTemplate.toString())
                    val szTemplateIDs = TextUtils.join(" ", al)
                    cv.put(COL_DEFAULTTEMPLATEIDS, szTemplateIDs)
                    val l = db.insertOrThrow(TABLENAME, null, cv)
                    if (l < 0) throw Error("Error inserting aircraft")
                }
                db.setTransactionSuccessful()
                fResult = true
            } catch (ex: Exception) {
                this.lastError = ex.message ?: ""
                Log.e("MFAndroid", "Error updating aircraft cache: " + ex.message)
            } finally {
                db.endTransaction()
            }
            // Now the aircraft are saved - save the images.
            // First, delete any existing aircraft images.
            // We can blow them all away - if we're here, we were just refreshing, shouldn't be
            // in the middle of adding a new aircraft image.
            db.delete(MFBImageInfo.TABLENAME, "idAircraft > 0", null)
            // We will write the images below (since it opens/closes the db)
        } catch (e: Exception) {
            this.lastError = e.message ?: ""
        }
        if (fResult) dbc.updateCache(TABLENAME)

        // now persist each aircraft image.
        for (ac in rgac) {
            if (ac!!.aircraftImages != null) {
                for (mfbii in ac.aircraftImages!!) {
                    mfbii.setPictureDestination(PictureDestination.AircraftImage)
                    mfbii.targetID = ac.aircraftID.toLong()
                    mfbii.toDB()
                }
            }
        }
        ActRecentsWS.m_rgac = null // Hack, but recents keeps a cache for performance
    }

    fun flushCache() {
        val dbc = DBCache()
        dbc.flushCache(TABLENAME, true)
    }

    private fun readResults(result: SoapObject, fFallbackToCache: Boolean): Array<Aircraft> {
        val rgAc : MutableList<Aircraft> = mutableListOf()
        try {
            for (i in 0 until result.propertyCount)
                rgAc.add(Aircraft(result.getProperty(i) as SoapObject))

            // if we made it here, we have successfully retrieved new values.
            // ONLY NOW should we update the cache
            updateCache(rgAc.toTypedArray())
        } catch (e: Exception) {
            lastError += e.message
            if (fFallbackToCache) return cachedAircraft
        }
        return rgAc.toTypedArray()
    }

    fun getAircraftForUser(szAuthToken: String?, c: Context?): Array<Aircraft> {
        val dbc = DBCache()
        val rgAc: Array<Aircraft>
        val dbcs = dbc.status(TABLENAME)
        rgAc = if (dbcs == DBCacheStatus.VALID) // return cached aircraft
        {
            cachedAircraft
        } else  // refresh the cache
        {
            val request = setMethod("AircraftForUser")
            request.addProperty("szAuthUserToken", szAuthToken)
            val result = invoke(c) as SoapObject
            readResults(result, dbcs == DBCacheStatus.VALID_BUT_RETRY)
        }
        return rgAc
    }

    fun aircraftForPrefix(szAuthToken: String?, szPrefix: String?, c: Context?): Array<Aircraft?> {
        val request = setMethod("AircraftMatchingPrefix")
        request.addProperty("szAuthToken", szAuthToken)
        request.addProperty("szPrefix", szPrefix)
        val result = invoke(c) as SoapObject
        val rgAc = arrayOfNulls<Aircraft>(result.propertyCount)
        for (i in rgAc.indices) rgAc[i] = Aircraft(result.getProperty(i) as SoapObject)
        return rgAc
    }

    private fun uploadImagesForAircraft(ac: Aircraft?, c: Context) {
        var i = 1
        if (ac?.aircraftImages == null)
            return
        for (mfbii in ac.aircraftImages!!) {
            try {
                val szFmtUploadProgress = c.getString(R.string.prgUploadingImages)
                val szStatus = String.format(szFmtUploadProgress, i, ac.aircraftImages!!.size)
                mProgress?.notifyProgress(
                    i * 100 / ac.aircraftImages!!.size,
                    szStatus
                )
                // upload any images that are NOT yet on the server.
                if (!mfbii.isOnServer()) {
                    mfbii.setKey(ac.aircraftID)
                    mfbii.targetID = ac.aircraftID.toLong()
                    if (!mfbii.uploadPendingImage(mfbii.id, c)) Log.w(
                        MFBConstants.LOG_TAG,
                        "Image Upload failed"
                    )
                    mfbii.deleteFromDB() // clean up any files.  (Had been toDB, but we actually DON'T want to persist)
                }
            } catch (ex: Exception) {
                lastError = "Image upload failed - " + ex.localizedMessage
            }
            i++
        }
    }

    fun addAircraft(szAuthToken: String?, ac: Aircraft, c: Context): Array<Aircraft> {
        val request = setMethod("AddAircraftForUser")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("szTail", ac.tailNumber)
        request.addProperty("idModel", ac.modelID)
        request.addProperty("idInstanceType", ac.instanceTypeID)
        var rgac: Array<Aircraft>
        var result = invoke(c) as SoapObject
        rgac = readResults(result, true)

        // Find the aircraft that was the one we just uploaded and upload its images
        if (rgac.isNotEmpty() && ac.aircraftImages != null && ac.aircraftImages!!.isNotEmpty()) {
            val szNormalTail = ac.tailNumber.replace("-".toRegex(), "")
            for (acAdded in rgac) if (szNormalTail.equals(
                    acAdded.tailNumber.replace(
                        "-".toRegex(),
                        ""
                    ), ignoreCase = true
                )
            ) {
                acAdded.aircraftImages = ac.aircraftImages
                uploadImagesForAircraft(acAdded, c)

                // Now need to re-download the aircraft
                result = invoke(c) as SoapObject
                rgac = readResults(result, true)
                break
            }
        }
        return rgac
    }

    fun updateMaintenanceForAircraft(szAuthToken: String?, ac: Aircraft?, c: Context) {
        val request = setMethod("UpdateMaintenanceForAircraftWithFlagsAndNotes")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("ac", ac)
        invoke(c)

        // Now upload any new images.
        uploadImagesForAircraft(ac, c)
    }

    fun deleteAircraftForUser(szAuthToken: String?, idAircraft: Int, c: Context?) {
        val request = setMethod("DeleteAircraftForUser")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("idAircraft", idAircraft)
        invoke(c) as SoapObject
    }

    companion object {
        private const val TABLENAME = "AircraftCache"

        // DB Column names
        private const val COL_TAILNUM = "TailNumber"
        private const val COL_AIRCRAFTID = "AircraftID"
        private const val COL_MODELID = "ModelID"
        private const val COL_INSTANCEID = "InstanceTypeID"
        private const val COL_VERSION = "Version"
        private const val COL_REVISION = "Revision"
        private const val COL_ICAO = "ICAO"
        private const val COL_MODELNAME = "ModelCommonName"
        private const val COL_MODELDESCRIPTION = "ModelDescription"
        private const val COL_LASTVOR = "LastVOR"
        private const val COL_LASTALTIMETER = "LastAltimeter"
        private const val COL_LASTTRANSPONDER = "LastTransponder"
        private const val COL_LASTELT = "LastELT"
        private const val COL_LASTSTATIC = "LastStatic"
        private const val COL_LASTANNUAL = "LastAnnual"
        private const val COL_REGISTRATIONDUE = "RegistrationDue"
        private const val COL_LAST100 = "Last100"
        private const val COL_LASTOIL = "LastOilChange"
        private const val COL_LASTENGINE = "LastNewEngine"
        private const val COL_HIDEFROMSELECTION = "HideFromSelection"
        private const val COL_COPYPICName = "CopyPICName"
        private const val COL_ROLEFORPILOT = "RoleForPilot"
        private const val COL_PUBLICNOTES = "PublicNotes"
        private const val COL_PRIVATENOTES = "PrivateNotes"
        private const val COL_DEFAULTIMAGE = "DefaultImage"
        private const val COL_DEFAULTTEMPLATEIDS = "DefaultTemplateIDs"
        private const val COL_ISGLASS = "IsGlass"
        private const val COL_GLASSUPGRADEDATE = "GlassUpgradeDate"
        private const val COL_AVIONICSUPGRADETYPE = "AvionicsUpgradeType"
    }
}