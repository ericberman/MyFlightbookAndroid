/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2019 MyFlightbook, LLC

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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import android.util.Log;

import com.myflightbook.android.MFBMain;
import com.myflightbook.android.Marshal.MarshalDate;
import com.myflightbook.android.Marshal.MarshalDouble;
import com.myflightbook.android.R;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import Model.Aircraft;
import Model.Aircraft.PilotRole;
import Model.DBCache;
import Model.LatLong;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBImageInfo.PictureDestination;

public class AircraftSvc extends MFBSoap {

    private static final String TABLENAME = "AircraftCache";

    // DB Column names
    private static final String COL_TAILNUM = "TailNumber";
    private static final String COL_AIRCRAFTID = "AircraftID";
    private static final String COL_MODELID = "ModelID";
    private static final String COL_INSTANCEID = "InstanceTypeID";
    private static final String COL_MODELNAME = "ModelCommonName";
    private static final String COL_MODELDESCRIPTION = "ModelDescription";

    private static final String COL_LASTVOR = "LastVOR";
    private static final String COL_LASTALTIMETER = "LastAltimeter";
    private static final String COL_LASTTRANSPONDER = "LastTransponder";
    private static final String COL_LASTELT = "LastELT";
    private static final String COL_LASTSTATIC = "LastStatic";
    private static final String COL_LASTANNUAL = "LastAnnual";
    private static final String COL_REGISTRATIONDUE = "RegistrationDue";
    private static final String COL_LAST100 = "Last100";
    private static final String COL_LASTOIL = "LastOilChange";
    private static final String COL_LASTENGINE = "LastNewEngine";
    private static final String COL_HIDEFROMSELECTION = "HideFromSelection";
    private static final String COL_ROLEFORPILOT = "RoleForPilot";
    private static final String COL_PUBLICNOTES = "PublicNotes";
    private static final String COL_PRIVATENOTES = "PrivateNotes";
    private static final String COL_DEFAULTIMAGE = "DefaultImage";
    private static final String COL_DEFAULTTEMPLATEIDS = "DefaultTemplateIDs";

    @Override
    public void AddMappings(SoapSerializationEnvelope e) {
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo.class);
        e.addMapping(NAMESPACE, "LatLong", LatLong.class);
        e.addMapping(NAMESPACE, "Aircraft", Aircraft.class);

        MarshalDate mdt = new MarshalDate();
        MarshalDouble md = new MarshalDouble();
        mdt.register(e);
        md.register(e);
    }

    public Aircraft[] getCachedAircraft() {
        Aircraft[] rgAc = new Aircraft[0];

        MFBImageInfo[] rgAircraftImages = MFBImageInfo.getAllAircraftImages();

        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();

        try (Cursor c = db.query(TABLENAME, null, null, null, null, null, null)) {

            if (c != null) {
                rgAc = new Aircraft[c.getCount()];
                int i = 0;

                int cAircraftID = c.getColumnIndex(COL_AIRCRAFTID);
                int cTailNum = c.getColumnIndex(COL_TAILNUM);
                int cModelId = c.getColumnIndex(COL_MODELID);
                int cInstanceId = c.getColumnIndex(COL_INSTANCEID);
                int cModelName = c.getColumnIndex(COL_MODELNAME);
                int cModelDesc = c.getColumnIndex(COL_MODELDESCRIPTION);

                int cLastVOR = c.getColumnIndex(COL_LASTVOR);
                int cLastAltimeter = c.getColumnIndex(COL_LASTALTIMETER);
                int cLastTransponder = c.getColumnIndex(COL_LASTTRANSPONDER);
                int cLastELT = c.getColumnIndex(COL_LASTELT);
                int cLastStatic = c.getColumnIndex(COL_LASTSTATIC);
                int cLastAnnual = c.getColumnIndex(COL_LASTANNUAL);
                int cRegistrationDue = c.getColumnIndex(COL_REGISTRATIONDUE);
                int cLast100 = c.getColumnIndex(COL_LAST100);
                int cLastOil = c.getColumnIndex(COL_LASTOIL);
                int cLastEngine = c.getColumnIndex(COL_LASTENGINE);

                int cHideSelection = c.getColumnIndex(COL_HIDEFROMSELECTION);
                int cRoleForPilot = c.getColumnIndex(COL_ROLEFORPILOT);

                int cPublicNotes = c.getColumnIndex(COL_PUBLICNOTES);
                int cPrivateNotes = c.getColumnIndex(COL_PRIVATENOTES);
                int cDefaultImage = c.getColumnIndex(COL_DEFAULTIMAGE);
                int cDefaultTemplateIDs = c.getColumnIndex(COL_DEFAULTTEMPLATEIDS);

                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());

                while (c.moveToNext()) {
                    Aircraft ac = new Aircraft();
                    try {
                        ac.AircraftID = c.getInt(cAircraftID);
                        ac.InstanceTypeID = c.getInt(cInstanceId);
                        ac.ModelCommonName = c.getString(cModelName);
                        ac.ModelDescription = c.getString(cModelDesc);
                        ac.ModelID = c.getInt(cModelId);
                        ac.TailNumber = c.getString(cTailNum);
                        ac.PublicNotes = c.getString(cPublicNotes);
                        ac.PrivateNotes = c.getString(cPrivateNotes);
                        ac.DefaultImage = c.getString(cDefaultImage);

                        ac.HideFromSelection = c.getInt(cHideSelection) != 0;
                        ac.RoleForPilot = PilotRole.values()[c.getInt(cRoleForPilot)];

                        ac.LastVOR = df.parse(c.getString(cLastVOR));
                        ac.LastAltimeter = df.parse(c.getString(cLastAltimeter));
                        ac.LastTransponder = df.parse(c.getString(cLastTransponder));
                        ac.LastELT = df.parse(c.getString(cLastELT));
                        ac.LastStatic = df.parse(c.getString(cLastStatic));
                        ac.LastAnnual = df.parse(c.getString(cLastAnnual));
                        ac.RegistrationDue = df.parse(c.getString(cRegistrationDue));

                        String szTemplateIDs = c.getString(cDefaultTemplateIDs);
                        String[] rgIDs = szTemplateIDs.split(" ");
                        for (String sz : rgIDs)
                            ac.DefaultTemplates.add(Integer.parseInt(sz));

                    } catch (Exception ex) {
                        Log.e(MFBConstants.LOG_TAG, "Error getting cached aircraft: " + ex.getMessage());
                    }

                    ac.Last100 = c.getDouble(cLast100);
                    ac.LastOil = c.getDouble(cLastOil);
                    ac.LastEngine = c.getDouble(cLastEngine);

                    if (rgAircraftImages != null)
                        ac.AircraftImages = MFBImageInfo.getAircraftImagesForId(ac.AircraftID, rgAircraftImages);

                    rgAc[i++] = ac;
                }
            }
        } catch (Exception ex) {
            Log.e(MFBConstants.LOG_TAG, "Error getting cached aircraft from db: " + ex.getMessage());
        }
        return rgAc;
    }

    private void updateCache(Aircraft[] rgac) {
        // note that these calls will close the db, so we do them first.
        DBCache dbc = new DBCache();
        dbc.flushCache(TABLENAME, true);
        boolean fResult = false;

        // now, we get an open db
        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();

        try {
            // I've read that multiple inserts are much faster inside a transaction.
            db.beginTransaction();
            try {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());

                for (Aircraft ac : rgac) {
                    ContentValues cv = new ContentValues();
                    cv.put(COL_AIRCRAFTID, ac.AircraftID);
                    cv.put(COL_INSTANCEID, ac.InstanceTypeID);
                    cv.put(COL_MODELID, ac.ModelID);
                    cv.put(COL_MODELDESCRIPTION, ac.ModelDescription);
                    cv.put(COL_MODELNAME, ac.ModelCommonName);
                    cv.put(COL_TAILNUM, ac.TailNumber);

                    cv.put(COL_LASTVOR, df.format(ac.LastVOR));
                    cv.put(COL_LASTALTIMETER, df.format(ac.LastAltimeter));
                    cv.put(COL_LASTTRANSPONDER, df.format(ac.LastTransponder));
                    cv.put(COL_LASTELT, df.format(ac.LastELT));
                    cv.put(COL_LASTSTATIC, df.format(ac.LastStatic));
                    cv.put(COL_LASTANNUAL, df.format(ac.LastAnnual));
                    cv.put(COL_REGISTRATIONDUE, df.format(ac.RegistrationDue));

                    cv.put(COL_LAST100, ac.Last100);
                    cv.put(COL_LASTOIL, ac.LastOil);
                    cv.put(COL_LASTENGINE, ac.LastEngine);

                    cv.put(COL_HIDEFROMSELECTION, ac.HideFromSelection);
                    cv.put(COL_ROLEFORPILOT, ac.RoleForPilot.ordinal());

                    cv.put(COL_PUBLICNOTES, ac.PublicNotes);
                    cv.put(COL_PRIVATENOTES, ac.PrivateNotes);
                    cv.put(COL_DEFAULTIMAGE, ac.DefaultImage);

                    ArrayList<String> al = new ArrayList<>();
                    for (Integer idTemplate : ac.DefaultTemplates)
                        al.add(idTemplate.toString());
                    String szTemplateIDs = TextUtils.join(" ", al);
                    cv.put(COL_DEFAULTTEMPLATEIDS, szTemplateIDs);

                    long l = db.insertOrThrow(TABLENAME, null, cv);
                    if (l < 0)
                        throw new Error("Error inserting aircraft");
                }
                db.setTransactionSuccessful();
                fResult = true;
            } catch (Exception ex) {
                this.setLastError(ex.getMessage());
                Log.e("MFAndroid", "Error updating aircraft cache: " + ex.getMessage());
            } finally {
                db.endTransaction();
            }
            // Now the aircraft are saved - save the images.
            // First, delete any existing aircraft images.
            // We can blow them all away - if we're here, we were just refreshing, shouldn't be
            // in the middle of adding a new aircraft image.
            db.delete(MFBImageInfo.TABLENAME, "idAircraft > 0", null);
            // We will write the images below (since it opens/closes the db)
        } catch (Exception e) {
            this.setLastError(e.getMessage());
        }

        if (fResult)
            dbc.updateCache(TABLENAME);

        // now persist each aircraft image.
        for (Aircraft ac : rgac) {
            if (ac.AircraftImages != null) {
                for (MFBImageInfo mfbii : ac.AircraftImages) {
                    mfbii.setPictureDestination(PictureDestination.AircraftImage);
                    mfbii.setTargetID(ac.AircraftID);
                    mfbii.toDB();
                }
            }
        }

    }

    public void FlushCache() {
        DBCache dbc = new DBCache();
        dbc.flushCache(TABLENAME, true);
    }

    private Aircraft[] ReadResults(SoapObject result, Boolean fFallbackToCache) {
        Aircraft[] rgAc = new Aircraft[0];
        try {
            rgAc = new Aircraft[result.getPropertyCount()];

            for (int i = 0; i < rgAc.length; i++)
                rgAc[i] = new Aircraft((SoapObject) result.getProperty(i));

            // if we made it here, we have successfully retrieved new values.
            // ONLY NOW should we update the cache
            updateCache(rgAc);
        } catch (Exception e) {
            setLastError(getLastError() + e.getMessage());
            if (fFallbackToCache)
                rgAc = getCachedAircraft();
        }

        return rgAc;
    }

    public Aircraft[] AircraftForUser(String szAuthToken, Context c) {
        DBCache dbc = new DBCache();
        Aircraft[] rgAc;

        DBCache.DBCacheStatus dbcs = dbc.Status(TABLENAME);

        if (dbcs == DBCache.DBCacheStatus.VALID) // return cached aircraft
        {
            rgAc = getCachedAircraft();
        } else // refresh the cache
        {
            SoapObject Request = setMethod("AircraftForUser");
            Request.addProperty("szAuthUserToken", szAuthToken);

            SoapObject result = (SoapObject) Invoke(c);
            if (result == null) {
                setLastError(getLastError());
                // just return the potentially invalid cached aircraft; it's better than nothing.
                rgAc = getCachedAircraft();
            } else {
                rgAc = ReadResults(result, (dbcs == DBCache.DBCacheStatus.VALID_BUT_RETRY));
            }
        }

        return rgAc;
    }

    public Aircraft[] AircraftForPrefix(String szAuthToken, String szPrefix, Context c) {
        SoapObject Request = setMethod("AircraftMatchingPrefix");
        Request.addProperty("szAuthToken", szAuthToken);
        Request.addProperty("szPrefix", szPrefix);
        SoapObject result = (SoapObject) Invoke(c);
        if (result == null)
            return new Aircraft[0];

        Aircraft[] rgAc = new Aircraft[result.getPropertyCount()];

        for (int i = 0; i < rgAc.length; i++)
            rgAc[i] = new Aircraft((SoapObject) result.getProperty(i));

        return rgAc;
    }

    private void UploadImagesForAircraft(Aircraft ac, Context c) {
        int i = 1;
        for (MFBImageInfo mfbii : ac.AircraftImages) {
            try {
                String szFmtUploadProgress = c.getString(R.string.prgUploadingImages);
                String szStatus = String.format(szFmtUploadProgress, i, ac.AircraftImages.length);
                if (m_Progress != null)
                    m_Progress.NotifyProgress((i * 100) / ac.AircraftImages.length, szStatus);
                // upload any images that are NOT yet on the server.
                if (!mfbii.IsOnServer()) {
                    mfbii.setKey(ac.AircraftID);
                    mfbii.setTargetID(ac.AircraftID);
                    if (!mfbii.UploadPendingImage(mfbii.getID(), c))
                        Log.w(MFBConstants.LOG_TAG, "Image Upload failed");
                    mfbii.deleteFromDB(); // clean up any files.  (Had been toDB, but we actually DON'T want to persist)
                }
            } catch (Exception ex) {
                setLastError("Image upload failed - " + ex.getLocalizedMessage());
            }
            i++;
        }
    }

    public Aircraft[] AddAircraft(String szAuthToken, Aircraft ac, Context c) {
        SoapObject Request = setMethod("AddAircraftForUser");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("szTail", ac.TailNumber);
        Request.addProperty("idModel", ac.ModelID);
        Request.addProperty("idInstanceType", ac.InstanceTypeID);

        Aircraft[] rgac = new Aircraft[0];

        SoapObject result = (SoapObject) Invoke(c);
        if (result == null)
            setLastError(getLastError());
        else {
            rgac = ReadResults(result, true);

            // Find the aircraft that was the one we just uploaded and upload its images
            if (rgac != null && rgac.length > 0 && ac.AircraftImages != null && ac.AircraftImages.length > 0) {
                String szNormalTail = ac.TailNumber.replaceAll("-", "");
                for (Aircraft acAdded : rgac)
                    if (szNormalTail.equalsIgnoreCase(acAdded.TailNumber.replaceAll("-", ""))) {
                        acAdded.AircraftImages = ac.AircraftImages;
                        UploadImagesForAircraft(acAdded, c);

                        // Now need to re-download the aircraft
                        result = (SoapObject) Invoke(c);
                        if (result == null)
                            setLastError(getLastError());
                        else
                            rgac = ReadResults(result, true);

                        break;
                    }
            }
        }

        return rgac;
    }

    public void UpdateMaintenanceForAircraft(String szAuthToken, Aircraft ac, Context c) {
        SoapObject Request = setMethod("UpdateMaintenanceForAircraftWithFlagsAndNotes");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("ac", ac);

        Invoke(c);

        // Now upload any new images.
        UploadImagesForAircraft(ac, c);
    }

    public void DeleteAircraftForUser(String szAuthToken, int idAircraft, Context c) {
        SoapObject Request = setMethod("DeleteAircraftForUser");
        Request.addProperty("szAuthUserToken", szAuthToken);
        Request.addProperty("idAircraft", idAircraft);

        SoapObject result = (SoapObject) Invoke(c);
        if (result == null)
            setLastError(getLastError());
    }
}
