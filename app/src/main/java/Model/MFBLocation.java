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
package Model;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.myflightbook.android.ActNewFlight;
import com.myflightbook.android.MFBMain;
import com.myflightbook.android.R;

public class MFBLocation implements LocationListener {
    public enum GPSQuality {Unknown, Poor, Good, Excellent}

    interface FlightEvents {
        void TakeoffDetected(Location l, Boolean fIsNight);

        void LandingDetected(Location l);

        void FSLandingDetected(Boolean fIsNight);

        void AddNightTime(double t);

        void UpdateStatus(GPSQuality quality, Boolean fAirborne, Location loc, Boolean fRecording);

        boolean shouldKeepListening();
    }

    private Boolean m_fIsEnabled = true;
    private FlightEvents m_Listener = null;

    private static Location m_lastSeenLoc = null; // last location we've seen
    private Location m_currentLoc = null; // last good location we've seen
    private int cSamplesSinceWaking = 0;
    private Boolean fPreviousLocWasNight = false;
    private Location PreviousLoc = null;

    public enum AutoFillOptions {None, FlightTime, EngineTime, HobbsTime}

    static public Boolean fPrefRecordFlight = false;
    static public Boolean fPrefRecordFlightHighRes = false;
    static public Boolean fPrefAutoDetect = false;
    static public Boolean fPrefRoundNearestTenth = false;
    static public AutoFillOptions fPrefAutoFillHobbs = AutoFillOptions.None;
    static public AutoFillOptions fPrefAutoFillTime = AutoFillOptions.None;

    public static Boolean IsRecording = false;
    public static Boolean IsFlying = false;
    public static Boolean HasPendingLanding = false;
    private Boolean IsListening = false;

    Boolean fNoRecord = false;

    // A single shared MFBLocation for the app
    private static MFBLocation m_Location = null;

    private void Init(Context c) {
        if (c != null) {
            this.startListening(c);
        }

		/*
         if (MFBConstants.fFakeGPS)
		 { 
			 m_lastSeenLoc = new Location("MFB");
			 m_lastSeenLoc.setLatitude(47.906987);
			 m_lastSeenLoc.setLongitude(-122.281);
			 m_lastSeenLoc.setAltitude(500); 
		 }
		 */
    }

    private Boolean fCheckPermissions(Context c) {
        return c != null && ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void startListening(Context c) {
        if (!IsListening && HasGPS(c) && !MFBConstants.fFakeGPS) {
            if (!fCheckPermissions(c))
                return;

            try {
                LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
                assert lm != null;
                if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    return;

                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        0,
                        0, this);
                Log.w(MFBConstants.LOG_TAG, String.format("Start Listening, Isrecording = %s", IsRecording ? "Yes" : "No"));
                IsListening = true;
                if (m_lastSeenLoc == null)
                    m_lastSeenLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (m_lastSeenLoc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                    m_lastSeenLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (m_lastSeenLoc != null)
                    InformListenerOfStatus(m_lastSeenLoc);
            } catch (IllegalArgumentException ex) {
                MFBUtil.Alert(c, c.getString(R.string.errNoGPSTitle), c.getString(R.string.errCantUseGPS) + ex.getMessage());
            } catch (SecurityException ignored) {
            }
        }
    }

    public void stopListening(Context c) {
        if (IsListening && HasGPS(c)) {
            if (!fCheckPermissions(c))
                return;

            LocationManager lm = (LocationManager) c
                    .getSystemService(Context.LOCATION_SERVICE);
            try {
                assert lm != null;
                lm.removeUpdates(this);
                Log.w(MFBConstants.LOG_TAG, "Stop Listening");
                IsListening = false;
            } catch (SecurityException ignored) {
            }
        }
    }

    public MFBLocation(Context c) {
        super();
        Init(c);
    }

    public void SetListener(FlightEvents l) {
        m_Listener = l;
        InformListenerOfStatus(m_lastSeenLoc);
    }

    public MFBLocation(Context c, FlightEvents l) {
        super();
        m_Listener = l;
        Init(c);
    }

    public Location CurrentLoc() {
        return m_lastSeenLoc;
    }

    public static Location LastSeenLoc() {
        return m_lastSeenLoc;
    }

    public static boolean HasGPS(Context c) {
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            assert lm != null;
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (IllegalArgumentException ex) {
            MFBUtil.Alert(c, c.getString(R.string.errNoGPSTitle), ex.getMessage());
        }
        return false;
    }

    public static MFBLocation GetMainLocation() {
        return m_Location;
    }

    public static void setMainLocation(MFBLocation loc) {
        m_Location = loc;
    }

    public void ResetFlightData() {
        SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
        try {
            db.delete("FlightTrack", null, null);
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, "Unable to clear track in ResetFlightData");
        }
        IsRecording = false;
        IsFlying = false;
        HasPendingLanding = false;
        InformListenerOfStatus(m_lastSeenLoc);
    }

    public String getFlightDataString() {
        return LocSample.FlightDataFromSamples(LocSample.flightPathFromDB());
    }

    private void InformListenerOfStatus(Location newLoc) {
        if (m_Listener == null)
            return;

        if (newLoc == null) {
            m_Listener.UpdateStatus(GPSQuality.Unknown, IsFlying, null, IsRecording);
            return;
        }

        GPSQuality q = GPSQuality.Unknown;
        if (newLoc.hasAccuracy()) {
            float Accuracy = newLoc.getAccuracy();
            if (Accuracy <= 0.0 || Accuracy > MFBConstants.MIN_ACCURACY)
                q = GPSQuality.Poor;
            else
                q = (Accuracy < (MFBConstants.MIN_ACCURACY / 2.0)) ? GPSQuality.Excellent : GPSQuality.Good;
        }

        m_Listener.UpdateStatus(q, IsFlying, newLoc, IsRecording);
    }

    public void onLocationChanged(Location newLoc) {
        LocSample loc = new LocSample(newLoc);

        double SpeedKts = loc.Speed;
        double Accuracy = loc.HError;
        Boolean fValidQuality = (Accuracy > 0 && Accuracy < MFBConstants.MIN_ACCURACY);
        Boolean fValidSpeed = (SpeedKts > 0.1);
        Boolean fValidTime = true;

        m_lastSeenLoc = newLoc; // always update this, even if we don't use it.

        if (m_currentLoc == null)
            m_currentLoc = m_lastSeenLoc;

        // see if things are too tightly spaced
        // get the time interval since the last location update
        long dt = loc.TimeStamp.getTime() - m_currentLoc.getTime();
        if ((IsFlying && dt < MFBConstants.MIN_SAMPLE_RATE_AIRBORNE) ||
                (!IsFlying && dt < MFBConstants.MIN_SAMPLE_RATE_TAXI))
            fValidTime = fPrefRecordFlightHighRes;   // i.e., false unless we are recording high res./

        if (!m_fIsEnabled)
            return;

        // only do detection/recording with quality samples
        if ((++cSamplesSinceWaking > MFBConstants.BOGUS_SAMPLE_COUNT) && fValidSpeed && fValidQuality && fValidTime) {
            SunriseSunsetTimes sst = new SunriseSunsetTimes(loc.TimeStamp, loc.Latitude, loc.Longitude);

            // it's a good sample - use it
            m_currentLoc = newLoc;

            if (PreviousLoc != null && fPreviousLocWasNight && sst.IsCivilNight && MFBLocation.fPrefAutoDetect) {
                double t = (newLoc.getTime() - PreviousLoc.getTime()) / 3600000.0;
                if (t < .5 && m_Listener != null)    // limit of half an hour between samples for night time
                    m_Listener.AddNightTime(t);
            }
            fPreviousLocWasNight = sst.IsCivilNight;
            PreviousLoc = newLoc;

            // detect takeoffs/landings  These are different speeds to prevent bouncing
            if (MFBLocation.fPrefAutoDetect) {
                if (IsFlying) {
                    if (SpeedKts < MFBTakeoffSpeed.getLandingSpeed()) {
                        IsFlying = false;
                        loc.Comment = "Landing detected";
                        Log.w(MFBConstants.LOG_TAG, "Landing detected...");
                        HasPendingLanding = true;
                        if (m_Listener != null) {
                            Log.w(MFBConstants.LOG_TAG, "Notifying listener...");
                            m_Listener.LandingDetected(newLoc);
                        }
                    }
                } else {
                    if (SpeedKts > MFBTakeoffSpeed.getTakeOffspeed()) {
                        IsFlying = true;
                        loc.Comment = MFBMain.getResourceString(R.string.telemetryTakeOff);
                        HasPendingLanding = false; // back in the air - can't be a FS landing
                        if (m_Listener != null) {
                            m_Listener.TakeoffDetected(newLoc, sst.IsFAANight);
                        }
                    }
                }

                // see if we've had a full-stop landing
                if (SpeedKts < MFBConstants.FULL_STOP_SPEED && HasPendingLanding) {
                    if (m_Listener != null)
                        m_Listener.FSLandingDetected(sst.IsFAANight);
                    Log.w(MFBConstants.LOG_TAG, "FS " + (sst.IsFAANight ? "night " : "") + "landing detected");
                    loc.Comment = MFBMain.getResourceString(sst.IsFAANight ? R.string.telemetryFSNight : R.string.telemetryFSLanding);
                    HasPendingLanding = false;
                }
            }

            if (MFBLocation.fPrefRecordFlight && IsRecording && !ActNewFlight.fPaused && !fNoRecord) {
                SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
                try {
                    ContentValues cv = new ContentValues();
                    loc.ToContentValues(cv);
                    long l = db.insert("FlightTrack", null, cv);
                    if (l < 0)
                        throw new Exception("Error saving to flight track");
                } catch (Exception e) {
                    Log.e(MFBConstants.LOG_TAG, "Unable to save to flight track");
                }
            }
        }

        // rate the quality
        InformListenerOfStatus(newLoc);
    }

    public void setIsRecording(Boolean f) {
        IsRecording = (fPrefRecordFlight && f);
    }

    public Boolean getIsRecording() {
        return (fPrefRecordFlight && IsRecording);
    }

    public void onProviderDisabled(String provider) {
        m_fIsEnabled = false;
    }

    public void onProviderEnabled(String provider) {
        m_fIsEnabled = true;
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

}
