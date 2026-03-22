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

import android.location.Location
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class XPlanePosition(
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var altitudeFeet: Double = 0.0,
    var groundspeedKnots: Double = 0.0,
    var headingTrue: Double = 0.0,
    var pitch: Double = 0.0,
    var roll: Double = 0.0
)

class XPlaneDataParser {

    fun parse(data: ByteArray): XPlanePosition? {
        if (data.size < 6) return null

        // Read the first 5 bytes as ASCII for the header
        return when (val header5 = data.take(5).toByteArray().toString(Charsets.US_ASCII)) {
            "XGPS2" -> parseXGPS2(data)
            "XATT2" -> parseXATT2(data)
            "DATA\u0000" -> parseXPlaneData(data)
            else -> {
                println("Unknown header: $header5")
                null
            }
        }
    }

    // -------------------------------------------------------------------------
    // XGPS2 — CSV position packet
    // Format: "XGPS2,lon,lat,altMeters,trackDeg,speedMS\n"
    // -------------------------------------------------------------------------
    private fun parseXGPS2(data: ByteArray): XPlanePosition? {
        val string = data.toString(Charsets.US_ASCII).trimEnd('\u0000', '\n')
        val parts = string.split(",")
        if (parts.size < 6 || parts[0] != "XGPS2") return null

        val lon       = parts[1].toDoubleOrNull() ?: return null
        val lat       = parts[2].toDoubleOrNull() ?: return null
        val altMeters = parts[3].toDoubleOrNull() ?: return null
        val track     = parts[4].toDoubleOrNull() ?: return null
        val speedMS   = parts[5].toDoubleOrNull() ?: return null

        return XPlanePosition(
            latitude         = lat,
            longitude        = lon,
            altitudeFeet     = altMeters * 3.28084,    // meters → feet
            groundspeedKnots = speedMS  * 1.94384,     // m/s → knots
            headingTrue      = track,
            pitch            = 0.0,
            roll             = 0.0
        )
    }

    // -------------------------------------------------------------------------
    // XATT2 — binary attitude packet
    // Layout (after 6-byte "XATT2\0" header):
    //   offset 0  : float heading (true)
    //   offset 4  : float pitch
    //   offset 8  : float roll
    // -------------------------------------------------------------------------
    private fun parseXATT2(data: ByteArray): XPlanePosition? {
        if (data.size < 18) return null
        val offset = 6  // skip "XATT2\0"
        val heading = data.leFloat(offset)
        val pitch   = data.leFloat(offset + 4)
        val roll    = data.leFloat(offset + 8)

        // Partial — caller should merge with last known position
        return XPlanePosition(
            headingTrue = heading.toDouble(),
            pitch       = pitch.toDouble(),
            roll        = roll.toDouble()
        )
    }

    // -------------------------------------------------------------------------
    // Original X-Plane DATA\0 binary (kept for backward compatibility)
    //
    // Packet layout: 5-byte header, then N × 36-byte chunks.
    // Each chunk: 4-byte int index, then 8 × 4-byte floats.
    //   Index 20 → lat (f4), lon (f8), altFt (f12)
    //   Index 21 → groundspeed knots (f20)
    //   Index 17 → pitch (f4), roll (f8), headingTrue (f12)
    // -------------------------------------------------------------------------
    private fun parseXPlaneData(data: ByteArray): XPlanePosition? {
        val position = XPlanePosition()
        var gotPosition = false
        val chunkSize = 36
        var offset = 5

        while (offset + chunkSize <= data.size) {
            val index = data.leInt32(offset)
            when (index) {
                20 -> {
                    position.latitude     = data.leFloat(offset + 4).toDouble()
                    position.longitude    = data.leFloat(offset + 8).toDouble()
                    position.altitudeFeet = data.leFloat(offset + 12).toDouble()
                    gotPosition = true
                }
                21 -> {
                    position.groundspeedKnots = data.leFloat(offset + 20).toDouble()
                }
                17 -> {
                    position.pitch       = data.leFloat(offset + 4).toDouble()
                    position.roll        = data.leFloat(offset + 8).toDouble()
                    position.headingTrue = data.leFloat(offset + 12).toDouble()
                }
            }
            offset += chunkSize
        }
        return if (gotPosition) position else null
    }

    // -------------------------------------------------------------------------
    // Convert an XPlanePosition to an Android Location object
    // -------------------------------------------------------------------------
    fun toLocation(pos: XPlanePosition): Location {
        return Location(xPlaneDataProvider).apply {
            latitude    = pos.latitude
            longitude   = pos.longitude
            altitude    = pos.altitudeFeet * 0.3048       // feet → meters
            bearing     = pos.headingTrue.toFloat()
            speed       = (pos.groundspeedKnots * 0.514444).toFloat()  // knots → m/s
            accuracy    = 5.0f
            time        = System.currentTimeMillis()
        }
    }

    companion object {
        const val xPlaneDataProvider = "xplane-simulator"
    }
}

// -----------------------------------------------------------------------------
// ByteArray extension helpers — little-endian reads (X-Plane uses LE)
// -----------------------------------------------------------------------------
private fun ByteArray.leInt32(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

private fun ByteArray.leFloat(offset: Int): Float =
    ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).float

@Suppress("unused")
private fun ByteArray.leDouble(offset: Int): Double =
    ByteBuffer.wrap(this, offset, 8).order(ByteOrder.LITTLE_ENDIAN).double

