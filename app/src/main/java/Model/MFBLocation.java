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
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.myflightbook.android.ActNewFlight;
import com.myflightbook.android.MFBMain;
import com.myflightbook.android.R;

public class MFBLocation extends Object implements LocationListener {
	public enum GPSQuality {Unknown, Poor, Good, Excellent};

	public interface FlightEvents {
		void TakeoffDetected(Location l, Boolean fIsNight);
		void LandingDetected(Location l);
		void FSLandingDetected(Boolean fIsNight);
		void AddNightTime(double t);
		void UpdateStatus(GPSQuality quality, Boolean fAirborne, Location loc, Boolean fRecording);
		boolean shouldKeepListening();
	}
	
	private Boolean m_fIsEnabled = true;
	private Context m_Context = null;
	private FlightEvents m_Listener = null;
	
	private static Location m_lastSeenLoc = null; // last location we've seen
	private Location m_currentLoc = null; // last good location we've seen
	private int cSamplesSinceWaking = 0;
	private Boolean fPreviousLocWasNight = false;
	private Location PreviousLoc = null;

	public enum AutoFillOptions {None, FlightTime, EngineTime, HobbsTime}

	static public Boolean fPrefRecordFlight = false;
	static public Boolean fPrefAutoDetect = false;
	static public Boolean fPrefRoundNearestTenth = false;
	static public AutoFillOptions fPrefAutoFillHobbs = AutoFillOptions.None;
	static public AutoFillOptions fPrefAutoFillTime = AutoFillOptions.None;
	
	public static Boolean IsRecording = false;
	public static Boolean IsFlying = false;
	public static Boolean HasPendingLanding = false;
	private Boolean IsListening = false;
	
	static private final int FINE_LOCATION_PERMISSION = 62;

	private void Init() {
		if (m_Context != null) {
			this.startListening();
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

	private Boolean fCheckPermissions() {
		if (m_Context == null)
			return false;
	
		int gpsPermission = ContextCompat.checkSelfPermission(m_Context, Manifest.permission.ACCESS_FINE_LOCATION);
		if (gpsPermission == PackageManager.PERMISSION_GRANTED)
			return true;
		
	    // Should we show an explanation?
	    if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) m_Context, Manifest.permission.ACCESS_FINE_LOCATION)) {
	        // No explanation needed, we can request the permission.
	        ActivityCompat.requestPermissions((Activity) m_Context,
	                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
	                FINE_LOCATION_PERMISSION);
	    }
	    return false;
	}


	
	public void startListening()
	{
		if (!IsListening && MFBMain.HasGPS() && !MFBConstants.fFakeGPS)
		{
			if (!fCheckPermissions())
				return;

			try
			{
			LocationManager lm = (LocationManager) m_Context.getSystemService(Context.LOCATION_SERVICE);
			lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
					0,
					0, this);
			Log.w("MFBAndroid", String.format("Start Listening, Isrecording = %s", IsRecording ? "Yes" : "No"));
			IsListening = true;	
			if (m_lastSeenLoc == null)
				m_lastSeenLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			}
			catch (IllegalArgumentException ex)
			{
				MFBUtil.Alert(m_Context, m_Context.getString(R.string.errNoGPSTitle), m_Context.getString(R.string.errCantUseGPS) + ex.getMessage());
			}
			catch (SecurityException ex) { }
		}
	}

	public void stopListening() {
		if (IsListening && MFBMain.HasGPS()) {
			if (!fCheckPermissions())
				return;

			LocationManager lm = (LocationManager) m_Context
					.getSystemService(Context.LOCATION_SERVICE);
			try {
				lm.removeUpdates(this);
				Log.w("MFBAndroid", "Stop Listening");
				IsListening = false;
			}
			catch (SecurityException ex) { }
		}
	}

	public MFBLocation() {
		super();
		Init();
	}
	
	public void SetListener(FlightEvents l)
	{
		m_Listener = l;
	}
	
	public void SetContext(Context c)
	{
		m_Context = c;
	}

	public MFBLocation(Context c, FlightEvents l) {
		super();
		m_Context = c;
		m_Listener = l;
		Init();
	}
	
	public Location CurrentLoc()
	{
		return m_lastSeenLoc;
	}
	
	public static Location LastSeenLoc()
	{
		return m_lastSeenLoc;
	}

	public void ResetFlightData() {
		SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
		try
		{
			db.delete("FlightTrack", null, null);
		}
		catch (Exception e)
		{
			Log.e("MFBAndroid", "Unable to clear track in ResetFlightData");
		}
		finally
		{
		}
		IsRecording = false;
		IsFlying = false;
		HasPendingLanding = false;
		InformListenerOfStatus(m_lastSeenLoc);
	}

	public String getFlightDataString() {
		return LocSample.FlightDataFromSamples(LocSample.flightPathFromDB());
	}

	private void InformListenerOfStatus(Location newLoc)
	{
		if (m_Listener == null)
			return;
		
		if (newLoc == null)
		{
			m_Listener.UpdateStatus(GPSQuality.Unknown, IsFlying, newLoc, IsRecording);
			return;
		}
		
		GPSQuality q = GPSQuality.Unknown;
		if (newLoc.hasAccuracy())
		{
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
			fValidTime = false;
					
		if (!m_fIsEnabled)
			return;
		
		// only do detection/recording with quality samples
		if ((++cSamplesSinceWaking > MFBConstants.BOGUS_SAMPLE_COUNT) && fValidSpeed && fValidQuality && fValidTime)
		{
            SunriseSunsetTimes sst = new SunriseSunsetTimes(loc.TimeStamp, loc.Latitude, loc.Longitude);

			// it's a good sample - use it
			m_currentLoc = newLoc;
			
			if (PreviousLoc != null && fPreviousLocWasNight && sst.IsCivilNight && MFBLocation.fPrefAutoDetect)
			{
				double t = (newLoc.getTime() - PreviousLoc.getTime()) / 3600000.0;
				if (t < .5 && m_Listener != null)	// limit of half an hour between samples for night time
					m_Listener.AddNightTime(t);
			}
			fPreviousLocWasNight = sst.IsCivilNight;
			PreviousLoc = newLoc;
			
			// detect takeoffs/landings  These are different speeds to prevent bouncing
			if (MFBLocation.fPrefAutoDetect) 
			{
				if (IsFlying)
				{
					if (SpeedKts < MFBTakeoffSpeed.getLandingSpeed())
					{
						IsFlying = false;
						loc.Comment = "Landing detected";
						Log.w("MFBAndroid", "Landing detected...");
						HasPendingLanding = true;
						if (m_Listener != null)
						{
							Log.w("MFBAndroid", "Notifying listener...");
							m_Listener.LandingDetected(newLoc);
						}
					}
				}
				else
				{
					if (SpeedKts > MFBTakeoffSpeed.getTakeOffspeed())
					{
						IsFlying = true;
						loc.Comment = MFBMain.GetMainContext().getString(R.string.telemetryTakeOff);
						HasPendingLanding = false; // back in the air - can't be a FS landing
						if (m_Listener != null)
						{
							m_Listener.TakeoffDetected(newLoc, sst.IsFAANight);
						}
					}
				}
				
                // see if we've had a full-stop landing
                if (SpeedKts < MFBConstants.FULL_STOP_SPEED && HasPendingLanding)
                {
                    if (m_Listener != null)
                    	m_Listener.FSLandingDetected(sst.IsFAANight);
                    Log.w("MFBAndroid", "FS " + (sst.IsFAANight ? "night " : "") + "landing detected");
                    loc.Comment = MFBMain.GetMainContext().getString(sst.IsFAANight ? R.string.telemetryFSNight : R.string.telemetryFSLanding);
                    HasPendingLanding = false;
                }
			}

			if (MFBLocation.fPrefRecordFlight && IsRecording && !ActNewFlight.fPaused)
			{
				SQLiteDatabase db = MFBMain.mDBHelper.getWritableDatabase();
				try
				{
					ContentValues cv = new ContentValues();
					loc.ToContentValues(cv);
					long l = db.insert("FlightTrack", null, cv);
					if (l < 0)
						throw new Exception("Error saving to flight track");
				}
				catch (Exception e)
				{
					Log.e("MFBAndroid", "Unable to save to flight track");
				}
				finally
				{
				}
			}
		}
		
		// rate the quality
		InformListenerOfStatus(newLoc);
	}
	
	public void setIsRecording(Boolean f)
	{
		IsRecording = (fPrefRecordFlight && f);
	}
	
	public Boolean getIsRecording()
	{
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
