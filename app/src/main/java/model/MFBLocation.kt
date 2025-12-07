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
package model

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Parcelable
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.myflightbook.android.ActNewFlight
import com.myflightbook.android.MFBMain
import com.myflightbook.android.MFBlocationservice
import com.myflightbook.android.R
import model.LocSample.Companion.flightDataFromSamples
import model.LocSample.Companion.flightPathFromDB

class MFBLocation : LocationListener {
    enum class GPSQuality {
        Unknown, Poor, Good, Excellent
    }

    interface FlightEvents {
        fun takeoffDetected(l: Location, fIsNight: Boolean)
        fun landingDetected(l: Location)
        fun landingDetectedFS(fIsNight: Boolean)
        fun addNightTime(t: Double)
        fun updateStatus(
            quality: GPSQuality,
            fAirborne: Boolean,
            loc: Location?,
            fRecording: Boolean
        )

        fun shouldKeepListening(): Boolean
    }

    private var mFisenabled = true
    private var mListener: FlightEvents? = null
    private var mCurrentloc: Location? = null // last good location we've seen
    private var cSamplesSinceWaking = 0
    private var fPreviousLocWasNight = false
    private var previousLoc: Location? = null

    enum class AutoFillOptions {
        None, FlightTime, EngineTime, HobbsTime, BlockTime, FlightStartToEngineEnd
    }

    enum class NightCriteria {
        EndOfCivilTwilight, Sunset, SunsetPlus15, SunsetPlus30, SunsetPlus60
    }

    enum class NightLandingCriteria {
        SunsetPlus60, Night
    }

    private var isListening = false
    var fNoRecord = false
    private var mReceiver: BroadcastReceiver? = null
    private fun init(c: Context?) {
        if (c != null) {
            if (mReceiver == null) mReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val l = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(MFBlocationservice.EXTRA_LOCATION, Location::class.java)
                    else
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Parcelable>(MFBlocationservice.EXTRA_LOCATION)
                    if (l is Location) onLocationChanged(l)
                }
            }
        }
    }

    private fun fCheckPermissions(c: Context?): Boolean {
        return c != null && ContextCompat.checkSelfPermission(
            c,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startListening(c: Context?) {
        if (!isListening && hasGPS(c) && !MFBConstants.FAKE_GPS) {
            if (c == null || !fCheckPermissions(c)) return
            try {
                LocalBroadcastManager.getInstance(c).registerReceiver(
                    mReceiver!!,
                    IntentFilter(MFBlocationservice.ACTION_LOCATION_BROADCAST)
                )
                Log.w(
                    MFBConstants.LOG_TAG,
                    String.format(
                        "Start Listening, Isrecording = %s",
                        if (IsRecording) "Yes" else "No"
                    )
                )

                // start background service
                // We only have 5 seconds from startForegroundService to calling startForeground, so let's build the Notification here first.
                val i = Intent(c, MFBlocationservice::class.java)
                c.startForegroundService(i)
                isListening = true
            } catch (ex: IllegalArgumentException) {
                MFBUtil.alert(
                    c,
                    c.getString(R.string.errNoGPSTitle),
                    c.getString(R.string.errCantUseGPS) + ex.message
                )
            } catch (ignored: SecurityException) {
            }
        }
    }

    fun stopListening(c: Context) {
        if (isListening && hasGPS(c)) {
            if (!fCheckPermissions(c)) return
            try {
                LocalBroadcastManager.getInstance(c).unregisterReceiver(mReceiver!!)
                c.stopService(Intent(c, MFBlocationservice::class.java))
                isListening = false
            } catch (ignored: SecurityException) {
            }
        }
    }

    constructor(c: Context, fStartNow: Boolean) : super() {
        init(c)
        if (fStartNow) startListening(c)
    }

    fun setListener(l: FlightEvents?) {
        mListener = l
        informListenerOfStatus(m_lastSeenLoc)
    }

    constructor(c: Context?, l: FlightEvents?) : super() {
        mListener = l
        init(c)
        startListening(c)
    }

    fun currentLoc(): Location? {
        return m_lastSeenLoc
    }

    fun resetFlightData() {
        val db = MFBMain.mDBHelper!!.writableDatabase
        try {
            db.delete("FlightTrack", null, null)
        } catch (e: Exception) {
            Log.e(MFBConstants.LOG_TAG, "Unable to clear track in ResetFlightData")
        }
        IsRecording = false
        IsFlying = false
        HasPendingLanding = false
        informListenerOfStatus(m_lastSeenLoc)
    }

    val flightDataString: String
        get() = flightDataFromSamples(flightPathFromDB())

    private fun informListenerOfStatus(newLoc: Location?) {
        if (mListener == null) return
        if (newLoc == null) {
            mListener!!.updateStatus(GPSQuality.Unknown, IsFlying, null, IsRecording)
            return
        }
        var q = GPSQuality.Unknown
        if (newLoc.hasAccuracy()) {
            val accuracy = newLoc.accuracy
            q =
                if (accuracy <= 0.0 || accuracy > MFBConstants.MIN_ACCURACY) GPSQuality.Poor else if (accuracy < MFBConstants.MIN_ACCURACY / 2.0) GPSQuality.Excellent else GPSQuality.Good
        }
        mListener!!.updateStatus(q, IsFlying, newLoc, IsRecording)
    }

    // determines if the sample is night for purposes of logging
    private fun isNightForFlight(sst: SunriseSunsetTimes): Boolean {
        // short-circuit if we know it's day
        return if (!sst.isNight!!) false else when (NightPref) {
            NightCriteria.EndOfCivilTwilight -> sst.isCivilNight!!
            NightCriteria.Sunset -> true // since we short-circuited above, sst.IsNight must be true!
            NightCriteria.SunsetPlus15, NightCriteria.SunsetPlus30, NightCriteria.SunsetPlus60 -> sst.isWithinNightOffset!!
        }
    }

    private fun nightFlightSunsetOffset(): Int {
        return when (NightPref) {
            NightCriteria.Sunset, NightCriteria.EndOfCivilTwilight, NightCriteria.SunsetPlus60 -> 60
            NightCriteria.SunsetPlus15 -> 15
            NightCriteria.SunsetPlus30 -> 30
        }
    }

    override fun onLocationChanged(newLoc: Location) {
        val loc = LocSample(newLoc)
        val speedKts = loc.speed
        val accuracy = loc.horizontalError
        val fValidQuality = accuracy > 0 && accuracy < MFBConstants.MIN_ACCURACY
        val fValidSpeed = speedKts > 0.1
        var fValidTime = true
        m_lastSeenLoc = newLoc // always update this, even if we don't use it.
        if (mCurrentloc == null) mCurrentloc = m_lastSeenLoc

        // see if things are too tightly spaced
        // get the time interval since the last location update
        val dt = loc.timeStamp.time - mCurrentloc!!.time
        if (dt < 0) // new timestamp is prior to more recently seen one
            return
        if (IsFlying && dt < MFBConstants.MIN_SAMPLE_RATE_AIRBORNE ||
            !IsFlying && dt < MFBConstants.MIN_SAMPLE_RATE_TAXI
        ) fValidTime = fPrefRecordFlightHighRes // i.e., false unless we are recording high res./
        if (!mFisenabled) return

        // only do detection/recording with quality samples
        if (++cSamplesSinceWaking > MFBConstants.BOGUS_SAMPLE_COUNT && fValidSpeed && fValidQuality && fValidTime) {
            val sst = SunriseSunsetTimes(
                loc.timeStamp,
                loc.latitude,
                loc.longitude,
                nightFlightSunsetOffset()
            )

            // it's a good sample - use it
            mCurrentloc = newLoc
            val fIsNightForFlight = isNightForFlight(sst)
            val fIsNightForLandings =
                if (NightLandingPref == NightLandingCriteria.Night) fIsNightForFlight else sst.isFAANight!!
            if (previousLoc != null && fPreviousLocWasNight && fIsNightForFlight && fPrefAutoDetect) {
                val t = (newLoc.time - previousLoc!!.time) / 3600000.0
                if (t < .5 && mListener != null) // limit of half an hour between samples for night time
                    mListener!!.addNightTime(t)
            }
            fPreviousLocWasNight = fIsNightForFlight
            previousLoc = newLoc

            // detect takeoffs/landings  These are different speeds to prevent bouncing
            if (fPrefAutoDetect) {
                if (IsFlying) {
                    if (speedKts < MFBTakeoffSpeed.landingSpeed) {
                        IsFlying = false
                        loc.comment = "Landing detected"
                        Log.w(MFBConstants.LOG_TAG, "Landing detected...")
                        HasPendingLanding = true
                        if (mListener != null) {
                            Log.w(MFBConstants.LOG_TAG, "Notifying listener...")
                            mListener!!.landingDetected(newLoc)
                        }
                    }
                } else {
                    if (speedKts > MFBTakeoffSpeed.takeOffspeed) {
                        IsFlying = true
                        loc.comment = MFBMain.getResourceString(R.string.telemetryTakeOff)
                        HasPendingLanding = false // back in the air - can't be a FS landing
                        if (mListener != null) {
                            mListener!!.takeoffDetected(newLoc, fIsNightForLandings)
                        }
                    }
                }

                // see if we've had a full-stop landing
                if (speedKts < MFBConstants.FULL_STOP_SPEED && HasPendingLanding) {
                    if (mListener != null) mListener!!.landingDetectedFS(fIsNightForLandings)
                    Log.w(
                        MFBConstants.LOG_TAG,
                        "FS " + (if (fIsNightForLandings) "night " else "") + "landing detected"
                    )
                    loc.comment =
                        MFBMain.getResourceString(if (fIsNightForLandings) R.string.telemetryFSNight else R.string.telemetryFSLanding)
                    HasPendingLanding = false
                }
            }
            if (fPrefRecordFlight && IsRecording && !ActNewFlight.fPaused && !fNoRecord) {
                val db = MFBMain.mDBHelper!!.writableDatabase
                try {
                    val cv = ContentValues()
                    loc.toContentValues(cv)
                    val l = db.insert("FlightTrack", null, cv)
                    if (l < 0) throw Exception("Error saving to flight track")
                } catch (e: Exception) {
                    Log.e(MFBConstants.LOG_TAG, "Unable to save to flight track")
                }
            }
        }

        // rate the quality
        informListenerOfStatus(newLoc)
    }

    var isRecording: Boolean
        get() = fPrefRecordFlight && IsRecording
        set(f) {
            IsRecording = fPrefRecordFlight && f
        }

    override fun onProviderDisabled(provider: String) {
        mFisenabled = false
    }

    override fun onProviderEnabled(provider: String) {
        mFisenabled = true
    }

    companion object {
        private var m_lastSeenLoc: Location? = null // last location we've seen
        @JvmField
        var fPrefRecordFlight = false
        @JvmField
        var fPrefRecordFlightHighRes = false
        @JvmField
        var fPrefAutoDetect = false
        @JvmField
        var fPrefRoundNearestTenth = false
        @JvmField
        var fPrefAutoFillHobbs = AutoFillOptions.None
        @JvmField
        var fPrefAutoFillTime = AutoFillOptions.None
        @JvmField
        var NightPref = NightCriteria.EndOfCivilTwilight
        @JvmField
        var NightLandingPref = NightLandingCriteria.SunsetPlus60
        @JvmField
        var IsRecording = false
        @JvmField
        var IsFlying = false
        @JvmField
        var HasPendingLanding = false

        // A single shared MFBLocation for the app
        private var m_Location: MFBLocation? = null
        fun lastSeenLoc(): Location? {
            return m_lastSeenLoc
        }

        @JvmStatic
        fun hasGPS(c: Context?): Boolean {
            if (c == null) return false
            try {
                val lm = (c.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (ex: IllegalArgumentException) {
                MFBUtil.alert(c, c.getString(R.string.errNoGPSTitle), ex.message)
            }
            return false
        }

        @JvmStatic
        fun getMainLocation(): MFBLocation? {
            return m_Location
        }

        @JvmStatic
        fun setMainLocation(loc: MFBLocation?) {
            m_Location = loc
        }
    }
}