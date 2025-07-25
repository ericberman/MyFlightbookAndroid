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

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import java.io.Serializable
import java.util.*
import java.util.regex.Pattern
import kotlin.math.abs

open class LatLong : SoapableObject, KvmSerializable, Serializable {
    @JvmField
    var latitude = 0.0
    @JvmField
    var longitude = 0.0

    private enum class FPProp {
        PIDLat, PIDLng
    }

    constructor()
    constructor(lat: Double, lon: Double) {
        latitude = lat
        longitude = lon
    }

    constructor(o: SoapObject) {
        fromProperties(o)
    }

    constructor(l: Location) {
        latitude = l.latitude
        longitude = l.longitude
    }

    val latLng: LatLng
        get() = LatLng(latitude, longitude)

    private fun isValid(): Boolean {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
    }

    override fun toString(): String {
        return String.format(Locale.getDefault(), "%.8f, %.8f", latitude, longitude)
    }

    fun toAdHocLocString(): String {
        return String.format(
            Locale.getDefault(),
            "@%.4f%s%.4f%s",
            abs(latitude),
            if (latitude > 0) "N" else "S",
            abs(longitude),
            if (longitude > 0) "E" else "W"
        )
    }

    override fun getProperty(arg0: Int): Any {
        return when (FPProp.entries[arg0]) {
            FPProp.PIDLat -> latitude
            FPProp.PIDLng -> longitude
        }
    }

    override fun getPropertyCount(): Int {
        return FPProp.entries.size
    }

    override fun getPropertyInfo(i: Int, h: Hashtable<*, *>?, pi: PropertyInfo) {
        when (FPProp.entries[i]) {
            FPProp.PIDLat -> {
                pi.type = Double::class.java
                pi.name = "Latitude"
            }
            FPProp.PIDLng -> {
                pi.type = Double::class.java
                pi.name = "Longitude"
            }
        }
    }

    override fun setProperty(arg0: Int, arg1: Any) {
        val f = FPProp.entries[arg0]
        val sz = arg1.toString()
        when (f) {
            FPProp.PIDLat -> latitude = sz.toDouble()
            FPProp.PIDLng -> longitude = sz.toDouble()
        }
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty("Latitude", String.format(Locale.US, "%.8f", latitude))
        so.addProperty("Longitude", String.format(Locale.US, "%.8f", longitude))
    }

    public final override fun fromProperties(so: SoapObject) {
        latitude = so.getProperty("Latitude").toString().toDouble()
        longitude = so.getProperty("Longitude").toString().toDouble()
    }

    companion object {
        fun fromString(sz: String): LatLong? {
            val p = Pattern.compile(
                "@?([^a-zA-Z]+)([NS]) *([^a-zA-Z]+)([EW])",
                Pattern.CASE_INSENSITIVE
            )
            val m = p.matcher(sz.uppercase(Locale.getDefault()))
            return if (m.find()) {
                try {
                    val ll = LatLong()
                    ll.latitude = Objects.requireNonNull(m.group(1))
                        .toDouble() * if (Objects.requireNonNull(m.group(2))
                            .compareTo("N", ignoreCase = true) == 0
                    ) 1 else -1
                    ll.longitude = Objects.requireNonNull(m.group(3))
                        .toDouble() * if (Objects.requireNonNull(m.group(4))
                            .compareTo("E", ignoreCase = true) == 0
                    ) 1 else -1
                    if (ll.isValid()) ll else null
                } catch (_: Exception) {
                    null
                }
            } else null
        }
    }
}