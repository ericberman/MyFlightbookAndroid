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

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.util.Hashtable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LatLong extends SoapableObject implements KvmSerializable {

    public double Latitude;
    public double Longitude;

    private enum FPProp {pidLat, pidLng}

    public LatLong() {

    }

    public LatLong(double lat, double lon) {
        Latitude = lat;
        Longitude = lon;
    }

    public LatLong(SoapObject o) {
        FromProperties(o);
    }

    public LatLong(Location l) {
        Latitude = l.getLatitude();
        Longitude = l.getLongitude();
    }

    static LatLong fromString(String sz) {
        Pattern p = Pattern.compile("@?([^a-zA-Z]+)([NS]) *([^a-zA-Z]+)([EW])", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sz.toUpperCase(Locale.getDefault()));
        if (m.find()) {
            try {
                LatLong ll = new LatLong();
                ll.Latitude = Double.parseDouble(m.group(1)) * ((m.group(2).compareToIgnoreCase("N") == 0) ? 1 : -1);
                ll.Longitude = Double.parseDouble(m.group(3)) * ((m.group(4).compareToIgnoreCase("E") == 0) ? 1 : -1);
                return ll.IsValid() ? ll : null;
            } catch (Exception ex) {
                return null;
            }
        } else
            return null;
    }

    public LatLng getLatLng() {
        return new LatLng(Latitude, Longitude);
    }

    private Boolean IsValid() {
        return Latitude >= -90 && Latitude <= 90 && Longitude >= -180 && Longitude <= 180;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%.8f, %.8f", Latitude, Longitude);
    }

    public String toAdHocLocString() {
        return String.format(Locale.getDefault(), "@%.4f%s%.4f%s", Math.abs(Latitude), Latitude > 0 ? "N" : "S", Math.abs(Longitude), Longitude > 0 ? "E" : "W");
    }

    public Object getProperty(int arg0) {
        FPProp f = FPProp.values()[arg0];

        switch (f) {
            case pidLat:
                return this.Latitude;
            case pidLng:
                return this.Longitude;
        }
        return null;
    }

    public int getPropertyCount() {
        return FPProp.values().length;
    }

    public void getPropertyInfo(int i, @SuppressWarnings("rawtypes") Hashtable h, PropertyInfo pi) {
        FPProp fp = FPProp.values()[i];
        switch (fp) {
            case pidLat:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "Latitude";
                break;
            case pidLng:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "Longitude";
                break;
            default:
                break;
        }
    }

    public void setProperty(int arg0, Object arg1) {
        FPProp f = FPProp.values()[arg0];
        String sz = arg1.toString();
        switch (f) {
            case pidLat:
                this.Latitude = Double.parseDouble(sz);
                break;
            case pidLng:
                this.Longitude = Double.parseDouble(sz);
            default:
                break;
        }
    }

    @Override
    public void ToProperties(SoapObject so) {
        so.addProperty("Latitude", String.format(Locale.US, "%.8f", this.Latitude));
        so.addProperty("Longitude", String.format(Locale.US, "%.8f", this.Longitude));
    }

    @Override
    public void FromProperties(SoapObject so) {
        this.Latitude = Double.parseDouble(so.getProperty("Latitude").toString());
        this.Longitude = Double.parseDouble(so.getProperty("Longitude").toString());
    }
}
