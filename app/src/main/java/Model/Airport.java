/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2021 MyFlightbook, LLC

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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLngBounds;
import com.myflightbook.android.MFBMain;

import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;

public class Airport extends SoapableObject implements KvmSerializable, Serializable, Comparable<Airport> {

    private static final long serialVersionUID = 1L;

    public String AirportID;
    public String FacilityName;
    public String Type;
    public String Country;
    public String Admin1;
    private double Latitude;
    private double Longitude;
    public double Distance;
    private boolean IsPreferred;

    public static Boolean fPrefIncludeHeliports = false;

    private static final String szNavaidPrefix = "@";
    private static final String USAirportPrefix = "K";

    private enum AirportProp {pidAirportID, pidFacilityName, pidType, pidLatitude, pidLongitude, pidDistance}

    private static final int minNavaidCodeLength = 2;
    private static final int minAirportCodeLength = 3;
    private static final int maxCodeLength = 6; // because of navaids, now allow up to 5 letters.

    private static final String szRegAdHocFix = szNavaidPrefix + "\\b\\d{1,2}(?:[.,]\\d*)?[NS]\\d{1,3}(?:[.,]\\d*)?[EW]\\b";  // Must have a digit on the left side of the decimal
    private static final String szRegexAirports = String.format(Locale.US, "((?:%s)|(?:@?\\b[A-Z0-9]{%d,%d}\\b))", szRegAdHocFix, Math.min(minNavaidCodeLength, minAirportCodeLength), maxCodeLength);
    private static final String szRegexAirportSearch = "!?@?[a-zA-Z0-9]+!?";

    public Airport() {
        super();
    }

    public int compareTo(@NonNull Airport ap) {
        double d1 = Math.round(Distance * 100.0) / 100.0;
        double d2 = Math.round(ap.Distance * 100.0) / 100.0;
        if (d1 < d2)
            return -1;
        if (d1 > d2)
            return 1;
        if (IsPreferred && !ap.IsPreferred)
            return -1;
        if (!IsPreferred && ap.IsPreferred)
            return 1;

        int l1 = AirportID.length();
        int l2 = ap.AirportID.length();
        return Integer.compare(l2, l1);

    }

    @NonNull
    @Override
    public String toString() {
        return String.format("%s (%s)", AirportID, FacilityName);
    }

    public LatLong getLatLong() {
        return new LatLong(this.Latitude, this.Longitude);
    }

    public boolean IsPort() {
        return (Type.compareTo("A") == 0 ||
                Type.compareTo("H") == 0 ||
                Type.compareTo("S") == 0);
    }

    private int NavaidPriority() {
        // Airports are always highest priority (pri-0)
        if (IsPort())
            return 0;

        // Then VOR Types
        if (Type.compareTo("V") == 0 ||
                Type.compareTo("C") == 0 ||
                Type.compareTo("D") == 0 ||
                Type.compareTo("T") == 0)
            return 1;

        // Then NDB Types
        if (Type.compareTo("R") == 0 ||
                Type.compareTo("RD") == 0 ||
                Type.compareTo("M") == 0 ||
                Type.compareTo("MD") == 0 ||
                Type.compareTo("U") == 0)
            return 2;

        // Generic fix
        if (Type.compareTo("FX") == 0)
            return 3;

        return 4;
    }

    /// <summary>
    /// Does this look like a US airport?
    /// </summary>
    /// <param name="szcode">The code</param>
    /// <returns>True if it looks like a US airport</returns>
    private static boolean IsUSAirport(String szcode) {
        return szcode.length() == 4 && szcode.startsWith(USAirportPrefix);
    }

    /// <summary>
    /// To support the hack of typing "K" before an airport code in the US, we will see if Kxxx is hits on simply xxx
    /// </summary>
    /// <param name="szcode">The airport code</param>
    /// <returns>The code with the leading "K" stripped</returns>
    private static String USPrefixConvenienceAlias(String szcode) {
        return IsUSAirport(szcode) ? szcode.substring(1) : szcode;
    }

    public static String[] SplitCodes(String szRoute) {
        Pattern p = Pattern.compile(Airport.szRegexAirports, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(szRoute.toUpperCase(Locale.getDefault()));

        ArrayList<String> lst = new ArrayList<>();

        while (m.find())
            lst.add(m.group(0));
        return lst.toArray(new String[0]);
    }

    public static String[] SplitCodesSearch(String szRoute) {
        Pattern p = Pattern.compile(Airport.szRegexAirportSearch, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(szRoute.toUpperCase(Locale.getDefault()));
        ArrayList<String> lst = new ArrayList<>();

        while (m.find())
            lst.add(m.group(0));
        return lst.toArray(new String[0]);
    }

    private static Airport AdHocAirport(String sz) {
        LatLong ll = LatLong.fromString(sz);
        if (ll == null)
            return null;

        Airport ap = new Airport();
        ap.Latitude = ll.Latitude;
        ap.Longitude = ll.Longitude;
        ap.AirportID = sz.replace(szNavaidPrefix, "");
        ap.Distance = 0;
        ap.FacilityName = ll.toString();
        ap.Type = "FX";
        ap.IsPreferred = false;
        ap.Country = "";
        ap.Admin1 = "";
        return ap;
    }

    private static Airport[] AirportsFromRoute(String szRoute, Location loc) {
        Airport[] rgap = new Airport[0];

        if (szRoute == null || szRoute.length() == 0)
            return rgap;

        String[] rgCodes = SplitCodes(szRoute);

        List<Airport> lstResults = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        Pattern pAdHoc = Pattern.compile(szRegAdHocFix, Pattern.CASE_INSENSITIVE);

        for (String s : rgCodes) {
            if (pAdHoc.matcher(s).matches()) {
                Airport apAdHoc = Airport.AdHocAirport(s);
                if (apAdHoc != null)
                    lstResults.add(apAdHoc);
                continue;
            }

            if (sb.length() > 0)
                sb.append(", ");

            if (s.startsWith(szNavaidPrefix))
                s = s.substring(szNavaidPrefix.length());

            sb.append("'").append(s).append("'");

            if (Airport.IsUSAirport(s))
                sb.append(", '").append(Airport.USPrefixConvenienceAlias(s)).append("'");
        }

        String szQ = String.format("AirportID IN (%s)", sb.toString());

        lstResults.addAll(Arrays.asList(Airport.AirportsForQuery(szQ, loc)));
        return lstResults.toArray(new Airport[0]);
    }

    public static Airport[] AirportsInRouteOrder(String szRoute, Location loc) {
        String[] rgCodes = SplitCodes(szRoute);
        ArrayList<Airport> al = new ArrayList<>();

        Airport[] rgap = AirportsFromRoute(szRoute, loc);

        HashMap<String, Airport> hm = new HashMap<>();

        for (Airport ap : rgap) {
            if (ap != null) {
                if (ap.IsPort())
                    hm.put(ap.AirportID, ap);
                else {
                    String szKey = szNavaidPrefix + ap.AirportID;
                    Airport ap2 = hm.get(szKey);
                    if (ap2 == null || ap.NavaidPriority() < ap2.NavaidPriority())
                        hm.put(szKey, ap);
                }
            }
        }

        for (String szCode : rgCodes) {
            Airport ap = hm.get(szCode);

            // If null, might be a navaid - check for the @ prefix
            if (ap == null)
                ap = hm.get(szNavaidPrefix + szCode);

            // Finally, check for K-hack (Kxxx vs. xxx)
            if (ap == null)
                ap = hm.get(Airport.USPrefixConvenienceAlias(szCode));

            if (ap != null)
                al.add(ap);
        }

        return al.toArray(new Airport[0]);
    }


    private static Airport[] AirportsForQuery(String szQ, Location loc) {
        Airport[] rgap = new Airport[0];

        SQLiteDatabase db = MFBMain.mDBHelperAirports.getReadableDatabase();
        int iRow = 0;

        try (Cursor c = db.query("airports", null, szQ, null, null, null, null)) {

            if (c != null) {
                rgap = new Airport[c.getCount()];
                int colAirportId = c.getColumnIndex("AirportID");
                int colFacilityName = c.getColumnIndex("FacilityName");
                int colLat = c.getColumnIndex("Latitude");
                int colLon = c.getColumnIndex("Longitude");
                int colType = c.getColumnIndex("Type");
                int colPref = c.getColumnIndex("Preferred");
                int colCountry = c.getColumnIndex("Country");
                int colAdmin1 = c.getColumnIndex("Admin1");
                while (c.moveToNext()) {
                    Airport ap = new Airport();
                    ap.AirportID = c.getString(colAirportId);
                    ap.FacilityName = c.getString(colFacilityName);
                    ap.Latitude = c.getDouble(colLat);
                    ap.Longitude = c.getDouble(colLon);
                    ap.Type = c.getString(colType);
                    ap.IsPreferred = (c.getInt(colPref) != 0);
                    ap.Country = c.isNull(colCountry) ? "" : c.getString(colCountry);
                    ap.Admin1 = c.isNull(colAdmin1) ? "" : c.getString(colAdmin1);

                    if (loc != null) {
                        Location lAirport = new Location(loc);
                        lAirport.setLatitude(ap.Latitude);
                        lAirport.setLongitude(ap.Longitude);
                        ap.Distance = lAirport.distanceTo(loc) * MFBConstants.METERS_TO_NM;
                    } else
                        ap.Distance = 0;

                    rgap[iRow++] = ap;
                }

                // sort the list by distance
                Arrays.sort(rgap);
            }
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, "Exception querying airports:" + e.getMessage());
            // some of the airports could be null; just clear them all out
            rgap = new Airport[0];
        }

        return rgap;
    }

    public Location getLocation() {
        Location l = new Location("MFB");
        l.setLatitude(this.Latitude);
        l.setLongitude(this.Longitude);
        return l;
    }

    public static double MaxDistanceForRoute(String szRoute) {
        double dist = 0.0;
        float[] rgDistResults = new float[1];

        if (szRoute == null || szRoute.length() == 0)
            return dist;

        Airport[] rgAp = Airport.AirportsFromRoute(szRoute, null);

        int cAirports = rgAp.length;

        for (int i = 0; i < cAirports; i++) {
            Airport ap1 = rgAp[i];

            if (ap1 == null || !ap1.IsPort())
                continue;

            for (int j = i + 1; j < cAirports; j++) {
                Airport ap2 = rgAp[j];

                if (!ap2.IsPort())
                    continue;

                Location.distanceBetween(ap1.Latitude, ap1.Longitude, ap2.Latitude, ap2.Longitude, rgDistResults);
                double d = rgDistResults[0] * MFBConstants.METERS_TO_NM;
                if (d > dist)
                    dist = d;
            }
        }

        return dist;
    }

    private static Airport getClosestAirport(Location loc) {
        if (loc == null)
            return null;

        Airport[] rgap = getNearbyAirports(loc, 0.5, 0.5);
        if (rgap != null && rgap.length > 0)
            return rgap[0];
        else
            return null;
    }

    private static Airport[] getNearbyAirports(Location loc, double minLat, double minLong, double maxLat, double maxLong) {
        String szTypes = fPrefIncludeHeliports ? "('H', 'A', 'S')" : "('A', 'S')";
        String szQTemplate = "(latitude BETWEEN %.8f AND %.8f) AND (longitude BETWEEN %.8f AND %.8f) AND Type IN %s";

        String szQ = String.format(Locale.US, szQTemplate, minLat, maxLat, minLong, maxLong, szTypes);

        return Airport.AirportsForQuery(szQ, loc);
    }

    public static Airport[] getNearbyAirports(Location loc, double dLat, double dLon) {
        double minLat, maxLat, minLong, maxLong;
        double lat, lon;

        if (loc == null)
            return new Airport[0];

        lat = loc.getLatitude();
        lon = loc.getLongitude();

        // BUG: this doesn't work if we cross 180 degrees, but there are so few airports it shouldn't matter
        minLat = Math.max(lat - (dLat / 2.0), -90.0);
        maxLat = Math.min(lat + (dLat / 2.0), 90.0);
        minLong = lon - (dLon / 2.0);
        maxLong = lon + (dLon / 2.0);
        // we don't bother correcting lons below -180 or above +180 for the reason above

        return getNearbyAirports(loc, minLat, minLong, maxLat, maxLong);
    }

    public static Airport[] getNearbyAirports(Location loc, LatLngBounds llb) {
        return getNearbyAirports(loc, llb.southwest.latitude, llb.southwest.longitude, llb.northeast.latitude, llb.northeast.longitude);
    }

    public static String AppendCodeToRoute(String szRouteSoFar, String code) {
        String sz = szRouteSoFar.toUpperCase(Locale.getDefault());
        String szCode = code.toUpperCase(Locale.getDefault());

        if (sz.endsWith(szCode))
            return sz;

        if (sz.length() == 0)
            return szCode;
        else
            return sz.trim() + " " + szCode;
    }

    public static String AppendNearestToRoute(String szRouteSoFar, Location loc) {
        Airport ap = Airport.getClosestAirport(loc);
        if (ap == null)
            return szRouteSoFar;
        return AppendCodeToRoute(szRouteSoFar, ap.AirportID);
    }

    public Object getProperty(int i) {
        AirportProp apProp = AirportProp.values()[i];
        switch (apProp) {
            case pidAirportID:
                return AirportID;
            case pidFacilityName:
                return FacilityName;
            case pidType:
                return Type;
            case pidLatitude:
                return Latitude;
            case pidLongitude:
                return Longitude;
            case pidDistance:
                return Distance;
        }
        return null;
    }

    public int getPropertyCount() {
        return AirportProp.values().length;
    }

    public void getPropertyInfo(int i, Hashtable h, PropertyInfo pi) {
        AirportProp apProp = AirportProp.values()[i];
        switch (apProp) {
            case pidAirportID:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Code";
                break;
            case pidFacilityName:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Name";
                break;
            case pidType:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "FacilityTypeCode";
                break;
            case pidLatitude:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "Latitude";
                break;
            case pidLongitude:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "Longitude";
                break;
            case pidDistance:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "DistanceFromPosition";
                break;
        }
    }

    public void setProperty(int arg0, Object arg1) {
    }

    @Override
    public void ToProperties(SoapObject so) {
    }

    @Override
    public void FromProperties(SoapObject so) {
        AirportID = so.getProperty("Code").toString();
        FacilityName = so.getProperty("Name").toString();
        Type = so.getProperty("FacilityTypeCode").toString();
        Latitude = Double.parseDouble(so.getProperty("Latitude").toString());
        Longitude = Double.parseDouble(so.getProperty("Longitude").toString());
        Distance = Double.parseDouble(so.getProperty("DistanceFromPosition").toString());
    }
}
