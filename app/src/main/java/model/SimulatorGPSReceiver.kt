/*
    MyFlightbook for Android - provides native access to MyFlightbook
    pilot's logbook
 Copyright (C) 2026 MyFlightbook, LLC

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
*/

package model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import com.myflightbook.android.MFBlocationservice

// ---------------------------------------------------------------------------
// SimulatorLocationReceiver
//
// Drop-in BroadcastReceiver.  Extend it or use the lambda factory below.
// ---------------------------------------------------------------------------
class SimulatorLocationReceiver(
    private val onLocation: (Location) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MFBlocationservice.ACTION_LOCATION_BROADCAST) return

        @Suppress("DEPRECATION")
        val location: Location? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(SimulatorGPSService.EXTRA_LOCATION, Location::class.java)
        } else {
            intent.getParcelableExtra(SimulatorGPSService.EXTRA_LOCATION)
        }
        location?.let { onLocation(it) }
    }
}

// ---------------------------------------------------------------------------
// Usage example — paste into your flight-recording Activity or Fragment
// ---------------------------------------------------------------------------
//
// class FlightRecordingActivity : AppCompatActivity() {
//
//     private lateinit var simulatorReceiver: SimulatorLocationReceiver
//
//     override fun onCreate(savedInstanceState: Bundle?) {
//         super.onCreate(savedInstanceState)
//
//         simulatorReceiver = SimulatorLocationReceiver { location ->
//             // Called on the main thread whenever a new position arrives
//             myLocationHandler.onNewLocation(location)
//         }
//     }
//
//     /** Call this when the user starts a flight */
//     fun startFlight() {
//         // 1. Register receiver before starting the service so we don't miss
//         //    the first packet
//         val filter = IntentFilter(SimulatorGPSService.ACTION_LOCATION_UPDATE)
//         ContextCompat.registerReceiver(
//             this, simulatorReceiver, filter,
//             ContextCompat.RECEIVER_NOT_EXPORTED   // our app only
//         )
//
//         // 2. Start the foreground service
//         ContextCompat.startForegroundService(
//             this, SimulatorGPSService.startIntent(this)
//         )
//     }
//
//     /** Call this when the user ends a flight */
//     fun stopFlight() {
//         // 1. Stop the service (closes sockets, removes notification)
//         startService(SimulatorGPSService.stopIntent(this))
//         // — or equivalently —
//         // stopService(SimulatorGPSService.startIntent(this))
//
//         // 2. Unregister the receiver
//         try { unregisterReceiver(simulatorReceiver) } catch (_: Exception) {}
//     }
//
//     override fun onDestroy() {
//         stopFlight()    // safety net — stop if activity is destroyed mid-flight
//         super.onDestroy()
//     }
// }

// ---------------------------------------------------------------------------
// AndroidManifest.xml additions (add inside <manifest> and <application>)
// ---------------------------------------------------------------------------
//
// Inside <manifest>:
//   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
//   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
//
// Inside <application>:
//   <service
//       android:name=".simulator.SimulatorGPSService"
//       android:foregroundServiceType="connectedDevice"
//       android:exported="false" />
//
// Note: foregroundServiceType="connectedDevice" is the correct type for a
// service that communicates with an external device (the simulator) over the
// network.  On Android 14+ this is mandatory; the build will fail without it.
