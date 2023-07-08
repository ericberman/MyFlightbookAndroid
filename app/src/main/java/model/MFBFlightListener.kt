/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2023 MyFlightbook, LLC

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
package model

import android.app.Activity
import android.location.Location
import android.util.Log
import com.myflightbook.android.ActNewFlight
import model.Airport.Companion.appendNearestToRoute
import model.MFBLocation.FlightEvents
import model.MFBLocation.GPSQuality
import java.util.*
import kotlin.math.roundToInt

class MFBFlightListener : FlightEvents {
    private var mLenewflight: LogbookEntry? = null
    var delegate: ListenerFragmentDelegate? = null
        private set

    interface ListenerFragmentDelegate {
        fun toView()
        fun fromView()
        fun refreshDetectedFields()
        fun updateStatus(
            quality: GPSQuality,
            fAirborne: Boolean?,
            loc: Location?,
            fRecording: Boolean?
        )

        fun unPauseFlight()
        fun saveCurrentFlight()
        fun togglePausePlay()
        fun isPaused() : Boolean
        fun startEngine()
        fun stopEngine()

        fun blockOut()

        fun blockIn()
    }

    private fun appendNearest(loc: Location): Boolean {
        val szPrevRoute = mLenewflight!!.szRoute
        mLenewflight!!.szRoute = appendNearestToRoute(mLenewflight!!.szRoute, loc)
        return szPrevRoute.compareTo(mLenewflight!!.szRoute) != 0
    }

    override fun takeoffDetected(l: Location, fIsNight: Boolean) {
        if (mLenewflight == null) {
            Log.e(MFBConstants.LOG_TAG, "logbookentry is NULL in TakeoffDetected")
            return
        }

        // ignore any new takeoffs after engine stop or before engine start
        if (!mLenewflight!!.flightInProgress()) return
        var fNeedsViewRefresh = false

        // create or increment a night-takeoff property.
        if (fIsNight) {
            mLenewflight!!.addNightTakeoff()
            fNeedsViewRefresh = true
        }
        if (delegate != null) delegate!!.unPauseFlight() // don't pause in flight
        if (!mLenewflight!!.isKnownFlightStart) {
            if (delegate != null) delegate!!.fromView()
            mLenewflight!!.dtFlightStart = Date(l.time)
            fNeedsViewRefresh = true
        }
        fNeedsViewRefresh = appendNearest(l) || fNeedsViewRefresh // definitely do appendNearest
        if (fNeedsViewRefresh && delegate != null) delegate!!.refreshDetectedFields()
    }

    private fun saveInProgressFlight() {
        /*
        if (m_delegate == null) {
            m_leNewFlight.ToDB();
            if (m_leNewFlight.IsNewFlight())
                saveCurrentFlightId(a);
        } else {
                */
        if (delegate != null) {
            delegate!!.refreshDetectedFields()
            delegate!!.saveCurrentFlight()
        }
    }

    override fun landingDetected(l: Location) {
        // 3 things to do:
        // a) update flight end
        if (mLenewflight == null) {
            Log.e(MFBConstants.LOG_TAG, "logbookentry is NULL in LandingDetected	")
            return
        }

        // ignore any new landings after engine stop.
        if (!mLenewflight!!.flightInProgress()) return
        mLenewflight!!.dtFlightEnd = Date(l.time)

        // b) increment the number of landings
        mLenewflight!!.cLandings++

        // and append the nearest route
        appendNearest(l)
        Log.w(
            MFBConstants.LOG_TAG,
            String.format("Landing received, now %d", mLenewflight!!.cLandings)
        )
        saveInProgressFlight()
    }

    override fun landingDetectedFS(fIsNight: Boolean) {
        if (delegate != null) delegate!!.fromView()
        if (mLenewflight == null) {
            Log.e(MFBConstants.LOG_TAG, "logbookentry is NULL in FSLandingDetected")
            return
        }
        if (!mLenewflight!!.flightInProgress()) return
        if (fIsNight) ++mLenewflight!!.cNightLandings else ++mLenewflight!!.cFullStopLandings
        if (delegate != null) delegate!!.refreshDetectedFields()
    }

    override fun addNightTime(t: Double) {
        if (mLenewflight == null) {
            Log.e(MFBConstants.LOG_TAG, "logbookentry is NULL in AddNightTime")
            return
        }

        // Issue #195 - don't accrue night flight if paused.
        if (!mLenewflight!!.flightInProgress() || delegate != null && delegate!!.isPaused()) return
        var night = t.let { ActNewFlight.accumulatedNight += it; ActNewFlight.accumulatedNight }
        if (MFBLocation.fPrefRoundNearestTenth) night = (night * 10.0).roundToInt() / 10.0
        mLenewflight!!.decNight = night
        if (delegate != null) delegate!!.refreshDetectedFields()
    }

    override fun updateStatus(
        quality: GPSQuality,
        fAirborne: Boolean,
        loc: Location?,
        fRecording: Boolean
    ) {
        if (delegate != null) delegate!!.updateStatus(quality, fAirborne, loc, fRecording)
    }

    override fun shouldKeepListening(): Boolean {
        if (mLenewflight == null) // never started a flight, so no need to keep listening
            return false

        // Stay awake (listening to GPS) IF you have (autodetect OR recording turned) on AND
        // if the engine is running.
        val fEngineIsRunning = mLenewflight!!.flightInProgress()
        val fDoesDetect = MFBLocation.fPrefAutoDetect || MFBLocation.fPrefRecordFlight
        val fStayAwake = fEngineIsRunning && fDoesDetect
        Log.w(
            MFBConstants.LOG_TAG,
            String.format(
                "should keep listening: %s, is Recording: %s",
                if (fStayAwake) "yes" else "no",
                if (MFBLocation.IsRecording) "yes" else "no"
            )
        )
        return fStayAwake
    }

    fun saveCurrentFlightId(a: Activity?) {
        if (a == null) // shouldn't ever be, but sometimes is.
            return
        if (mLenewflight != null && mLenewflight!!.isNewFlight()) {
            val mPrefs = a.getPreferences(Activity.MODE_PRIVATE)
            val ed = mPrefs.edit()
            ed.putLong(keyInProgressId, mLenewflight!!.idLocalDB)
            ed.apply()
        }
    }

    fun getInProgressFlight(a: Activity): LogbookEntry {
        if (mLenewflight == null) {
            val lastNewFlightID = getInProgressFlightId(a)
            mLenewflight = LogbookEntry(lastNewFlightID)
            if (lastNewFlightID != mLenewflight!!.idLocalDB) // save the in-progress ID in case of crash, to prevent turds
                saveCurrentFlightId(a)
        }
        return mLenewflight!!
    }

    fun setInProgressFlight(le: LogbookEntry?): MFBFlightListener {
        mLenewflight = le
        return this
    }

    fun setDelegate(d: ListenerFragmentDelegate?): MFBFlightListener {
        delegate = d
        return this
    }

    companion object {
        private const val keyInProgressId = "idFlightInProgress"
        private fun getInProgressFlightId(a: Activity): Long {
            val mPrefs = a.getPreferences(Activity.MODE_PRIVATE)
            return mPrefs.getLong(keyInProgressId, LogbookEntry.ID_NEW_FLIGHT.toLong())
        }
    }
}