package com.myflightbook.android;
/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2018-2021 MyFlightbook, LLC

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

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.List;
import java.util.Objects;

import Model.MFBConstants;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// Background location service, modeled on the code sample at http://devdeeds.com/android-location-tracking-in-background-service/; thanks!!
public class mfblocationservice  extends Service implements LocationListener {

    class MFBLocationCallback extends LocationCallback {
        @Override
        public void onLocationAvailability(@NonNull LocationAvailability availability) { }

        @Override
        public void onLocationResult(@NonNull LocationResult result) {
            List<Location> lst = result.getLocations();
            if (lst.size() == 0) {
                mfblocationservice.this.onLocationChanged(result.getLastLocation());
            }
            else {
                for (Location loc : lst)
                    mfblocationservice.this.onLocationChanged(loc);
            }
        }
    }

    private final LocationRequest mLocationRequest = new LocationRequest();
    private final LocationCallback mLocationCallback = new MFBLocationCallback();
    private FusedLocationProviderClient mFusedLocationProvider;

    public static final String ACTION_LOCATION_BROADCAST = mfblocationservice.class.getName() + "LocationBroadcast";
    public static final String EXTRA_LOCATION = "mfbSerializedLocation";
    private static final int NOTIFICATION_ID = 58235;

    private static Location _initialLoc = null;

    private void startInForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel nc = new NotificationChannel("mfbGPSChannelDefault1", "com.myflightbook.android.channel", NotificationManager.IMPORTANCE_LOW);
            nc.enableLights(false);
            nc.enableVibration(false);
            if (nm != null)
                nm.createNotificationChannel(nc);
            Notification n = new Notification.Builder(this, nc.getId())
                    .setContentText(getString(R.string.lblGPSRunningInBackground))
                    .setContentTitle(getString(R.string.app_name))
                    .setSmallIcon(R.drawable.ic_gps_notification)
                    .build();
            this.startForeground(NOTIFICATION_ID, n);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mLocationRequest.setInterval(500)
                .setFastestInterval(250)
                .setMaxWaitTime(10000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mFusedLocationProvider = LocationServices.getFusedLocationProviderClient(this);
        mFusedLocationProvider.requestLocationUpdates(mLocationRequest, mLocationCallback, Objects.requireNonNull(Looper.myLooper()));

        // initialize with last known location.
        if (_initialLoc == null) {
            mFusedLocationProvider.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            _initialLoc = location;
                            onLocationChanged(location);
                        }
                    }).addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null)
                            onLocationChanged(task.getResult());
                        else
                            Log.e(MFBConstants.LOG_TAG, "No location!");
                    });
        }

        startInForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();

        //Make it stick to the notification panel so it is less prone to get cancelled by the Operating System.
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mFusedLocationProvider != null)
            mFusedLocationProvider.removeLocationUpdates(mLocationCallback);
        super.onDestroy();
    }

    //to get the location change
    @Override
    public void onLocationChanged(@NonNull Location location) {
        Intent intent = new Intent(ACTION_LOCATION_BROADCAST);
        intent.putExtra(EXTRA_LOCATION, location);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}