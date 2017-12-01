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

import android.app.Activity;
import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import com.myflightbook.android.ActNewFlight;

import java.util.Date;

public class MFBFlightListener implements MFBLocation.FlightEvents {
    private LogbookEntry m_leNewFlight = null;
    private ListenerFragmentDelegate m_delegate = null;
    private static final String keyInProgressId = "idFlightInProgress";

    public interface ListenerFragmentDelegate {
        void ToView();

        void FromView();

        void RefreshDetectedFields();

        void UpdateStatus(MFBLocation.GPSQuality quality, Boolean fAirborne, Location loc, Boolean fRecording);

        void unPauseFlight();

        void saveCurrentFlight();
    }

    public MFBFlightListener() {
    }

    private void appendNearest(Location loc) {
        m_leNewFlight.szRoute = Airport.AppendNearestToRoute(m_leNewFlight.szRoute, loc);
    }

    public void TakeoffDetected(Location l, Boolean fIsNight) {
        if (m_leNewFlight == null) {
            Log.e(MFBConstants.LOG_TAG, "logbookentry is NULL in TakeoffDetected");
            return;
        }

        // ignore any new takeoffs after engine stop or before engine start
        if (!m_leNewFlight.FlightInProgress())
            return;

        boolean fNeedsViewRefresh = false;

        // create or increment a night-takeoff property.
        if (fIsNight) {
            m_leNewFlight.AddNightTakeoff();
            fNeedsViewRefresh = true;
        }

        if (m_delegate != null)
            m_delegate.unPauseFlight();  // don't pause in flight

        if (!m_leNewFlight.isKnownFlightStart()) {
            if (m_delegate != null)
                m_delegate.FromView();
            m_leNewFlight.dtFlightStart = new Date(l.getTime());
            appendNearest(l);
            fNeedsViewRefresh = true;
        }

        if (fNeedsViewRefresh && m_delegate != null)
            m_delegate.RefreshDetectedFields();
    }

    private void SaveInProgressFlight() {
            /*
        if (m_delegate == null) {
            m_leNewFlight.ToDB();
            if (m_leNewFlight.IsNewFlight())
                saveCurrentFlightId(a);
        } else {
                */
        if (m_delegate != null) {
            m_delegate.RefreshDetectedFields();
            m_delegate.saveCurrentFlight();
        }
    }

    public void LandingDetected(Location l) {
        // 3 things to do:
        // a) update flight end
        if (m_leNewFlight == null) {
            Log.e(MFBConstants.LOG_TAG, "logbookentry is NULL in LandingDetected	");
            return;
        }

        // ignore any new landings after engine stop.
        if (!m_leNewFlight.FlightInProgress())
            return;

        m_leNewFlight.dtFlightEnd = new Date(l.getTime());

        // b) increment the number of landings
        m_leNewFlight.cLandings++;

        // and append the nearest route
        appendNearest(l);

        Log.w(MFBConstants.LOG_TAG, String.format("Landing received, now %d", m_leNewFlight.cLandings));

        SaveInProgressFlight();
    }

    public void FSLandingDetected(Boolean fIsNight) {
        if (m_delegate != null)
            m_delegate.FromView();

        if (m_leNewFlight == null) {
            Log.e(MFBConstants.LOG_TAG, "logbookentry is NULL in FSLandingDetected");
            return;
        }

        if (fIsNight)
            ++m_leNewFlight.cNightLandings;
        else
            ++m_leNewFlight.cFullStopLandings;

        if (m_delegate != null)
            m_delegate.RefreshDetectedFields();
    }

    public void AddNightTime(double t) {
        if (m_leNewFlight == null) {
            Log.e(MFBConstants.LOG_TAG, "logbookentry is NULL in AddNightTime");
            return;
        }

        double night = (ActNewFlight.accumulatedNight += t);
        if (MFBLocation.fPrefRoundNearestTenth)
            night = Math.round(night * 10.0) / 10.0;
        m_leNewFlight.decNight = night;

        if (m_delegate != null)
            m_delegate.RefreshDetectedFields();
    }

    public void UpdateStatus(MFBLocation.GPSQuality quality, Boolean fAirborne, Location loc, Boolean fRecording) {
        if (m_delegate != null)
            m_delegate.UpdateStatus(quality, fAirborne, loc, fRecording);
    }

    public boolean shouldKeepListening() {
        if (m_leNewFlight == null)    // never started a flight, so no need to keep listening
            return false;

        // Stay awake (listening to GPS) IF you have (autodetect OR recording turned) on AND
        // if the engine is running.
        boolean fEngineIsRunning = (m_leNewFlight.FlightInProgress());
        boolean fDoesDetect = (MFBLocation.fPrefAutoDetect || MFBLocation.fPrefRecordFlight);
        boolean fStayAwake = (fEngineIsRunning && fDoesDetect);

        Log.w(MFBConstants.LOG_TAG, String.format("should keep listening: %s, is Recording: %s", fStayAwake ? "yes" : "no", MFBLocation.IsRecording ? "yes" : "no"));
        return fStayAwake;
    }

    public long saveCurrentFlightId(Activity a) {
        long i = -1;

        if (a == null)  // shouldn't ever be, but sometimes is.
            return i;
        if (m_leNewFlight != null && m_leNewFlight.IsNewFlight()) {
            SharedPreferences mPrefs = a.getPreferences(Activity.MODE_PRIVATE);
            SharedPreferences.Editor ed = mPrefs.edit();
            ed.putLong(keyInProgressId, m_leNewFlight.idLocalDB);
            i = m_leNewFlight.idLocalDB;
            ed.apply();
        }
        return i;
    }

    private static long getInProgressFlightId(Activity a) {
        SharedPreferences mPrefs = a.getPreferences(Activity.MODE_PRIVATE);
        return mPrefs.getLong(keyInProgressId, LogbookEntry.ID_NEW_FLIGHT);
    }

    public LogbookEntry getInProgressFlight(Activity a) {
        if (m_leNewFlight == null) {
            long lastNewFlightID = getInProgressFlightId(a);
            m_leNewFlight = new LogbookEntry(lastNewFlightID);

            if (lastNewFlightID != m_leNewFlight.idLocalDB) // save the in-progress ID in case of crash, to prevent turds
                saveCurrentFlightId(a);
        }
        return m_leNewFlight;
    }

    public MFBFlightListener setInProgressFlight(LogbookEntry le) {
        m_leNewFlight = le;
        return this;
    }

    public MFBFlightListener setDelegate(ListenerFragmentDelegate d) {
        m_delegate = d;
        return this;
    }
}
