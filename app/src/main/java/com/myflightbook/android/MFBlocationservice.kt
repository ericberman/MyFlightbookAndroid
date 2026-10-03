/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2018-2026 MyFlightbook, LLC

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

package com.myflightbook.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import model.MFBConstants
import model.MFBLocation

// Background location service, modeled on the code sample at http://devdeeds.com/android-location-tracking-in-background-service/; thanks!!
class MFBlocationservice : Service(), LocationListener {
    internal inner class MFBLocationCallback : LocationCallback() {
        override fun onLocationAvailability(availability: LocationAvailability) {}
        override fun onLocationResult(result: LocationResult) {
            val lst = result.locations
            if (lst.isEmpty()) {
                if (result.lastLocation != null)
                    onLocationChanged(result.lastLocation!!)
            } else {
                for (loc in lst) onLocationChanged(loc)
            }
        }
    }

    private val mLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
        .setWaitForAccurateLocation(false)
        .setMinUpdateIntervalMillis(1000)
        .setMaxUpdateDelayMillis(2000)
        .build()
    private val mLocationCallback: LocationCallback = MFBLocationCallback()
    private var mFusedLocationProvider: FusedLocationProviderClient? = null
    // Promote to a foreground service.  Returns false (and stops the service) if that isn't allowed, which can happen
    // if the app went to the background between startService() and now:
    // ForegroundServiceStartNotAllowedException (Android 12+) or SecurityException for location type (Android 14+).
    private fun startInForeground(): Boolean {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val nc = NotificationChannel(
            "mfbGPSChannelDefault1",
            "com.myflightbook.android.channel",
            NotificationManager.IMPORTANCE_LOW
        )
        nc.enableLights(false)
        nc.enableVibration(false)
        nm.createNotificationChannel(nc)
        val n = Notification.Builder(this, nc.id)
            .setContentText(getString(R.string.lblGPSRunningInBackground))
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(R.drawable.ic_gps_notification)
            .build()
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            true
        } catch (ex: Exception) {
            Log.w(MFBConstants.LOG_TAG, "Unable to start location service in foreground: " + ex.message)
            MFBLocation.getMainLocation()?.onServiceStartFailed()
            stopSelf()
            false
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!startInForeground())
            return
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mFusedLocationProvider = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationProvider!!.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()!!
        )

        // initialize with last known location.
        if (minitialLoc == null) {
            mFusedLocationProvider!!.lastLocation
                .addOnCompleteListener { task: Task<Location> ->
                    val location : Location? = if (task.isSuccessful) task.result else null
                    if (location != null) {
                        minitialLoc = location
                        onLocationChanged(location)
                    } else Log.e(MFBConstants.LOG_TAG, "No location!")
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        if (mFusedLocationProvider != null) mFusedLocationProvider!!.removeLocationUpdates(
            mLocationCallback
        )
        super.onDestroy()
    }

    //to get the location change
    override fun onLocationChanged(location: Location) {
        val intent = Intent(ACTION_LOCATION_BROADCAST).apply {
            putExtra(EXTRA_LOCATION, location)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    companion object {
        val ACTION_LOCATION_BROADCAST = MFBlocationservice::class.java.name + "LocationBroadcast"
        const val EXTRA_LOCATION = "mfbSerializedLocation"
        private const val NOTIFICATION_ID = 58235
        private var minitialLoc: Location? = null
    }
}