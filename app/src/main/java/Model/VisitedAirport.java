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

import android.support.annotation.NonNull;

import org.kobjects.isodate.IsoDate;
import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.util.Date;
import java.util.Hashtable;

public class VisitedAirport extends SoapableObject implements KvmSerializable, Comparable<VisitedAirport> {

    public String Code;
    public Date EarliestDate;
    public Date LatestDate;
    public int NumberOfVisits;
    public Airport airport;
    public String Aliases;

    private enum VisitedAirportProp {pidCode, pidEarliestDate, pidLatestDate, pidNumVisits, pidAirport, pidAliases}

    public VisitedAirport(SoapObject so) {
        super();
        FromProperties(so);
    }

    public static String toRoute(VisitedAirport[] rgva) {
        StringBuilder sb = new StringBuilder();
        for (VisitedAirport va : rgva)
            sb.append(va.Code).append(" ");
        return sb.toString().trim();
    }

    @Override
    public void ToProperties(SoapObject so) {
    }

    @Override
    public void FromProperties(SoapObject so) {
        Code = so.getProperty("Code").toString();
        try {
            Aliases = so.getPropertyAsString("Aliases");
        } catch (Exception e) {
            Aliases = "";
        }
        EarliestDate = IsoDate.stringToDate(so.getProperty("EarliestVisitDate").toString(), IsoDate.DATE);
        LatestDate = IsoDate.stringToDate(so.getProperty("LatestVisitDate").toString(), IsoDate.DATE);
        NumberOfVisits = Integer.parseInt(so.getProperty("NumberOfVisits").toString());
        airport = new Airport();
        airport.FromProperties((SoapObject) so.getProperty("Airport"));
    }

    public Object getProperty(int i) {
        VisitedAirportProp vap = VisitedAirportProp.values()[i];
        switch (vap) {
            case pidCode:
                return Code;
            case pidAliases:
                return Aliases;
            case pidEarliestDate:
                return EarliestDate;
            case pidLatestDate:
                return LatestDate;
            case pidNumVisits:
                return NumberOfVisits;
            case pidAirport:
                return airport;
        }
        return null;
    }

    public int getPropertyCount() {
        return VisitedAirportProp.values().length;
    }

    public void getPropertyInfo(int i, @SuppressWarnings("rawtypes") Hashtable h, PropertyInfo pi) {
        VisitedAirportProp vap = VisitedAirportProp.values()[i];
        switch (vap) {
            case pidCode:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Code";
                break;
            case pidAliases:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Aliases";
                break;
            case pidEarliestDate:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "EarliestVisitDate";
                break;
            case pidLatestDate:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "LatestVisitDate";
                break;
            case pidNumVisits:
                pi.type = PropertyInfo.INTEGER_CLASS;
                pi.name = "NumberOfVisits";
                break;
            case pidAirport:
                pi.type = PropertyInfo.OBJECT_CLASS;
                pi.name = "Airport";
                break;
        }
    }

    public void setProperty(int arg0, Object arg1) {
    }

    public int compareTo(@NonNull VisitedAirport another) {
        return this.airport.FacilityName.compareToIgnoreCase(another.airport.FacilityName);
    }

}
