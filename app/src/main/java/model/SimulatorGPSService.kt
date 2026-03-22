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

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.myflightbook.android.MFBlocationservice
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket

/**
 * SimulatorGPSService
 *
 * A Foreground Service that listens on UDP ports 49000 (X-Plane binary / XGPS2 / XATT2)
 * and 49002 (ForeFlight JSON) and feeds parsed positions to the rest of the app via a
 * broadcast Intent.
 *
 * Lifecycle
 * ---------
 *   Start:  context.startForegroundService(SimulatorGPSService.startIntent(context))
 *   Stop:   context.startService(SimulatorGPSService.stopIntent(context))
 *           — or —
 *           context.stopService(SimulatorGPSService.startIntent(context))
 *
 * Receiving positions
 * -------------------
 *   Register a BroadcastReceiver for ACTION_LOCATION_UPDATE:
 *
 *       val filter = IntentFilter(SimulatorGPSService.ACTION_LOCATION_UPDATE)
 *       registerReceiver(myReceiver, filter, RECEIVER_NOT_EXPORTED)
 *
 *   The Intent carries a Location parcelable under EXTRA_LOCATION.
 */
class SimulatorGPSService : Service() {

    // ------------------------------------------------------------------
    // Companion: constants and factory methods
    // ------------------------------------------------------------------
    companion object {
        private const val TAG = "SimulatorGPS"

        // Broadcast sent to the rest of the app when a new position arrives
        const val EXTRA_LOCATION         = "location"

        // Internal action used to stop the service gracefully
        private const val ACTION_STOP = "com.myflightbook.SIMULATOR_STOP"

        private const val NOTIFICATION_ID      = 1001
        private const val NOTIFICATION_CHANNEL = "sim_gps"

        private const val PORT_XPLANE    = 49000
        private const val PORT_FOREFLIGHT = 49002
        private const val BUFFER_SIZE    = 4096

        fun startIntent(ctx: Context) =
            Intent(ctx, SimulatorGPSService::class.java)

        @Suppress("unused")
        fun stopIntent(ctx: Context) =
            Intent(ctx, SimulatorGPSService::class.java).apply {
                action = ACTION_STOP
            }
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------
    private val parser      = XPlaneDataParser()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastPosition = XPlanePosition()     // merged state across XGPS2 + XATT2 packets

    private var socket49000: DatagramSocket? = null
    private var socket49002: DatagramSocket? = null

    // ------------------------------------------------------------------
    // Service lifecycle
    // ------------------------------------------------------------------

    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MFB:SimulatorGPS")
        wakeLock.acquire(10*60*1000L /*10 minutes*/)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop requested")
            stopSelf()
            return START_NOT_STICKY
        }

        // Promote to foreground immediately — required before doing any real work
        startForeground(NOTIFICATION_ID, buildNotification())

        Log.i(TAG, "Starting UDP listeners")
        startSocketListener(PORT_XPLANE)
        startSocketListener(PORT_FOREFLIGHT)

        // If the OS kills us (low memory), do NOT restart automatically.
        // The user should explicitly start a flight.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (wakeLock.isHeld) wakeLock.release()
        Log.i(TAG, "Service destroyed — closing sockets")
        serviceScope.cancel()
        socket49000?.close()
        socket49002?.close()
        super.onDestroy()
    }

    // We don't need binding; clients communicate via Intents.
    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------
    // UDP listeners — one coroutine per port
    // ------------------------------------------------------------------

    private fun startSocketListener(port: Int) {
        serviceScope.launch {
            try {
                val socket = DatagramSocket(port).also {
                    it.reuseAddress = true
                    it.broadcast   = true
                }
                when (port) {
                    PORT_XPLANE     -> socket49000 = socket
                    PORT_FOREFLIGHT -> socket49002 = socket
                }
                Log.i(TAG, "✅ Listening on UDP port $port")

                val buffer = ByteArray(BUFFER_SIZE)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    socket.receive(packet)          // blocks until data arrives
                    val data = packet.data.copyOf(packet.length)
                    parsePacket(data)
                    packet.length = buffer.size     // reset length for next receive
                }
            } catch (e: Exception) {
                if (isActive) Log.e(TAG, "❌ Port $port error: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------
    // Packet parsing — mirrors parsePacket() in GPSSimulator.swift
    // ------------------------------------------------------------------

    private fun parsePacket(data: ByteArray) {
        // 1. Try ForeFlight JSON (port 49002 sends this format)
        tryForeFlightJson(data)?.let { loc ->
            broadcastLocation(loc)
            return
        }

        // 2. Fall back to X-Plane binary / XGPS2 / XATT2 (port 49000)
        val parsed = parser.parse(data) ?: return
        val header = data.take(5).toByteArray().toString(Charsets.US_ASCII)

        if (header == "XATT2") {
            // Only update attitude; keep last known position
            synchronized(lastPosition) {
                lastPosition.headingTrue = parsed.headingTrue
                lastPosition.pitch       = parsed.pitch
                lastPosition.roll        = parsed.roll
            }
        } else {
            // XGPS2 or DATA\0 — update position fields, keep last attitude
            synchronized(lastPosition) {
                lastPosition.latitude         = parsed.latitude
                lastPosition.longitude        = parsed.longitude
                lastPosition.altitudeFeet     = parsed.altitudeFeet
                lastPosition.groundspeedKnots = parsed.groundspeedKnots
                lastPosition.headingTrue      = parsed.headingTrue
            }
        }

        // Only emit once we have a real fix
        val snap = synchronized(lastPosition) {
            if (lastPosition.latitude == 0.0 && lastPosition.longitude == 0.0) null
            else lastPosition.copy()
        } ?: return

        broadcastLocation(parser.toLocation(snap))
    }

    /**
     * Attempt to parse ForeFlight JSON.
     * Format: { "XGPS": { "Latitude": ..., "Longitude": ...,
     *                      "Altitude": ..., "Track": ..., "Speed": ... } }
     * Returns null if the data isn't valid ForeFlight JSON.
     */
    private fun tryForeFlightJson(data: ByteArray): Location? {
        return try {
            val json = JSONObject(data.toString(Charsets.UTF_8))
            val xgps = json.optJSONObject("XGPS") ?: return null

            val lat   = xgps.optDouble("Latitude",  0.0)
            val lon   = xgps.optDouble("Longitude", 0.0)
            val alt   = xgps.optDouble("Altitude",  0.0)   // feet MSL
            val track = xgps.optDouble("Track",     0.0)   // degrees true
            val speed = xgps.optDouble("Speed",     0.0)   // knots

            Location("foreflight-simulator").apply {
                latitude  = lat
                longitude = lon
                altitude  = alt * 0.3048                    // feet → meters
                bearing   = track.toFloat()
                this.speed = (speed * 0.514444).toFloat()   // knots → m/s
                accuracy  = 5.0f
                time      = System.currentTimeMillis()
            }
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Broadcast a location to the rest of the app
    // ------------------------------------------------------------------

    private var lastBroadcastTime = 0L

    private fun broadcastLocation(location: Location) {
        val now = System.currentTimeMillis()
        if (now - lastBroadcastTime < 1000) return
        lastBroadcastTime = now

        val intent = Intent(MFBlocationservice.ACTION_LOCATION_BROADCAST).apply {
            putExtra(MFBlocationservice.EXTRA_LOCATION, location)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
    // ------------------------------------------------------------------
    // Notification (required for foreground services on Android 8+)
    // ------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            "Simulator GPS",
            NotificationManager.IMPORTANCE_LOW          // silent; no sound/vibration
        ).apply {
            description = "Receiving GPS position from flight simulator"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // Tapping the notification brings the user back to the main activity
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setContentTitle("Simulator GPS active")
            .setContentText("Receiving position from flight simulator")
            .setSmallIcon(android.R.drawable.ic_menu_compass)   // replace with your own icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
