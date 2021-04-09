/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2021 MyFlightbook, LLC

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
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import com.myflightbook.android.ActNewFlight;
import com.myflightbook.android.MFBMain;
import com.myflightbook.android.R;
import com.myflightbook.android.mfblocationservice;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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

    public enum AutoFillOptions {None, FlightTime, EngineTime, HobbsTime, BlockTime, FlightStartToEngineEnd}

    public enum NightCriteria { EndOfCivilTwilight, Sunset, SunsetPlus15, SunsetPlus30, SunsetPlus60 }
    public enum NightLandingCriteria { SunsetPlus60, Night}

    static public Boolean fPrefRecordFlight = false;
    static public Boolean fPrefRecordFlightHighRes = false;
    static public Boolean fPrefAutoDetect = false;
    static public Boolean fPrefRoundNearestTenth = false;
    static public AutoFillOptions fPrefAutoFillHobbs = AutoFillOptions.None;
    static public AutoFillOptions fPrefAutoFillTime = AutoFillOptions.None;
    static public NightCriteria NightPref = NightCriteria.EndOfCivilTwilight;
    static public NightLandingCriteria NightLandingPref = NightLandingCriteria.SunsetPlus60;

    public static Boolean IsRecording = false;
    public static Boolean IsFlying = false;
    public static Boolean HasPendingLanding = false;
    private Boolean IsListening = false;

    Boolean fNoRecord = false;

    // A single shared MFBLocation for the app
    private static MFBLocation m_Location = null;

    private BroadcastReceiver mReceiver = null;

    private void Init(Context c) {
        if (c != null) {
            if (mReceiver == null)
                mReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        Parcelable l = intent.getParcelableExtra(mfblocationservice.EXTRA_LOCATION);
                        if (l instanceof Location)
                            onLocationChanged((Location) l);
                    }
                };
        }
    }

    private Boolean fCheckPermissions(Context c) {
        return c != null && ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void startListening(Context c) {
        if (!IsListening && HasGPS(c) && !MFBConstants.fFakeGPS) {
            if (!fCheckPermissions(c))
                return;

            try {
                LocalBroadcastManager.getInstance(c).registerReceiver(mReceiver, new IntentFilter(mfblocationservice.ACTION_LOCATION_BROADCAST));

                Log.w(MFBConstants.LOG_TAG, String.format("Start Listening, Isrecording = %s", IsRecording ? "Yes" : "No"));

                // start background service
                // We only have 5 seconds from startForegroundService to calling startForeground, so let's build the Notification here first.
                Intent i = new Intent(c, mfblocationservice.class);
                if (Build.VERSION.SDK_INT >= 26)
                    c.startForegroundService(i);
                else
                    c.startService(i);

                IsListening = true;
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

            try {
                LocalBroadcastManager.getInstance(c).unregisterReceiver(mReceiver);
                c.stopService(new Intent(c, mfblocationservice.class));
                IsListening = false;
            } catch (SecurityException ignored) {
            }
        }
    }

    public MFBLocation(Context c, boolean fStartNow) {
        super();
        Init(c);
        if (fStartNow)
            startListening(c);
    }

    public void SetListener(FlightEvents l) {
        m_Listener = l;
        InformListenerOfStatus(m_lastSeenLoc);
    }

    public MFBLocation(Context c, FlightEvents l) {
        super();
        m_Listener = l;
        Init(c);
        startListening(c);
    }

    public Location CurrentLoc() {
        return m_lastSeenLoc;
    }

    public static Location LastSeenLoc() {
        return m_lastSeenLoc;
    }

    public static boolean HasGPS(Context c) {
        if (c == null)
            return false;

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

    // determines if the sample is night for purposes of logging
    private boolean IsNightForFlight(SunriseSunsetTimes sst) {
        // short-circuit if we know it's day
        if (!sst.IsNight)
            return false;

        switch (NightPref) {
            case EndOfCivilTwilight:
                return sst.IsCivilNight;
            case Sunset:
                return true;    // since we short-circuited above, sst.IsNight must be true!
            case SunsetPlus15:
            case SunsetPlus30:
            case SunsetPlus60:
                return sst.IsWithinNightOffset;
        }
        return false;
    }

    private int NightFlightSunsetOffset() {
        switch (NightPref) {
            default:
            case Sunset:
            case EndOfCivilTwilight:
            case SunsetPlus60:
                return 60;
            case SunsetPlus15:
                return 15;
            case SunsetPlus30:
                return 30;
        }
    }

    public void onLocationChanged(@NonNull Location newLoc) {
        LocSample loc = new LocSample(newLoc);

        double SpeedKts = loc.Speed;
        double Accuracy = loc.HError;
        boolean fValidQuality = (Accuracy > 0 && Accuracy < MFBConstants.MIN_ACCURACY);
        boolean fValidSpeed = (SpeedKts > 0.1);
        boolean fValidTime = true;

        m_lastSeenLoc = newLoc; // always update this, even if we don't use it.

        if (m_currentLoc == null)
            m_currentLoc = m_lastSeenLoc;

        // see if things are too tightly spaced
        // get the time interval since the last location update
        long dt = loc.TimeStamp.getTime() - m_currentLoc.getTime();
        if (dt < 0) // new timestamp is prior to more recently seen one
            return;

        if ((IsFlying && dt < MFBConstants.MIN_SAMPLE_RATE_AIRBORNE) ||
                (!IsFlying && dt < MFBConstants.MIN_SAMPLE_RATE_TAXI))
            fValidTime = fPrefRecordFlightHighRes;   // i.e., false unless we are recording high res./

        if (!m_fIsEnabled)
            return;

        // only do detection/recording with quality samples
        if ((++cSamplesSinceWaking > MFBConstants.BOGUS_SAMPLE_COUNT) && fValidSpeed && fValidQuality && fValidTime) {
            SunriseSunsetTimes sst = new SunriseSunsetTimes(loc.TimeStamp, loc.Latitude, loc.Longitude, NightFlightSunsetOffset());

            // it's a good sample - use it
            m_currentLoc = newLoc;

            boolean fIsNightForFlight = IsNightForFlight(sst);
            boolean fIsNightForLandings = (NightLandingPref == NightLandingCriteria.Night) ? fIsNightForFlight : sst.IsFAANight;

            if (PreviousLoc != null && fPreviousLocWasNight && fIsNightForFlight && MFBLocation.fPrefAutoDetect) {
                double t = (newLoc.getTime() - PreviousLoc.getTime()) / 3600000.0;
                if (t < .5 && m_Listener != null)    // limit of half an hour between samples for night time
                    m_Listener.AddNightTime(t);
            }
            fPreviousLocWasNight =fIsNightForFlight;

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
                            m_Listener.TakeoffDetected(newLoc, fIsNightForLandings);
                        }
                    }
                }

                // see if we've had a full-stop landing
                if (SpeedKts < MFBConstants.FULL_STOP_SPEED && HasPendingLanding) {
                    if (m_Listener != null)
                        m_Listener.FSLandingDetected(fIsNightForLandings);
                    Log.w(MFBConstants.LOG_TAG, "FS " + (fIsNightForLandings ? "night " : "") + "landing detected");
                    loc.Comment = MFBMain.getResourceString(fIsNightForLandings ? R.string.telemetryFSNight : R.string.telemetryFSLanding);
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

    public void onProviderDisabled(@NonNull String provider) {
        m_fIsEnabled = false;
    }

    public void onProviderEnabled(@NonNull String provider) {
        m_fIsEnabled = true;
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

}
