package com.myflightbook.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import model.MFBConstants

/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2018-2022 MyFlightbook, LLC

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
 */ // Background location service, modeled on the code sample at http://devdeeds.com/android-location-tracking-in-background-service/; thanks!!
class MFBlocationservice : Service(), LocationListener {
    internal inner class MFBLocationCallback : LocationCallback() {
        override fun onLocationAvailability(availability: LocationAvailability) {}
        override fun onLocationResult(result: LocationResult) {
            val lst = result.locations
            if (lst.size == 0) {
                onLocationChanged(result.lastLocation)
            } else {
                for (loc in lst) onLocationChanged(loc)
            }
        }
    }

    private val mLocationRequest = LocationRequest.create()
    private val mLocationCallback: LocationCallback = MFBLocationCallback()
    private var mFusedLocationProvider: FusedLocationProviderClient? = null
    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
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
            this.startForeground(NOTIFICATION_ID, n)
        }
    }

    override fun onCreate() {
        super.onCreate()
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
        mLocationRequest.setInterval(500)
            .setFastestInterval(250)
            .setMaxWaitTime(10000).priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mFusedLocationProvider = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationProvider!!.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()!!
        )

        // initialize with last known location.
        if (minitialLoc == null) {
            mFusedLocationProvider!!.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        minitialLoc = location
                        onLocationChanged(location)
                    }
                }
                .addOnCompleteListener { task: Task<Location> ->
                    if (task.isSuccessful && task.result != null) onLocationChanged(
                        task.result
                    ) else Log.e(MFBConstants.LOG_TAG, "No location!")
                }
        }
        startInForeground()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startInForeground()

        //Make it stick to the notification panel so it is less prone to get cancelled by the Operating System.
        return START_STICKY
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
        val intent = Intent(ACTION_LOCATION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    companion object {
        val ACTION_LOCATION_BROADCAST = MFBlocationservice::class.java.name + "LocationBroadcast"
        const val EXTRA_LOCATION = "mfbSerializedLocation"
        private const val NOTIFICATION_ID = 58235
        private var minitialLoc: Location? = null
    }
}