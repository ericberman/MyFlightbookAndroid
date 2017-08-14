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

import org.kobjects.isodate.IsoDate;
import org.ksoap2.serialization.SoapObject;

import java.util.Date;
import java.util.Locale;

abstract class SoapableObject {

    SoapableObject() {
        super();
    }

    public SoapableObject(SoapObject so) {
        super();
        FromProperties(so);
    }

    SoapObject AddNullableDate(SoapObject so, String propName, Date dt) {
        if (dt == null)
            return so.addProperty(propName, "");
        else
            return so.addProperty(propName, IsoDate.dateToString(dt, IsoDate.DATE_TIME));
    }

    SoapObject AddDouble(SoapObject so, String propName, Double d) {
        return so.addProperty(propName, String.format(Locale.US, "%.2f", d));
    }

    Date ReadNullableDate(SoapObject so, String propName) {
        String sz = so.getProperty(propName).toString();
        if (sz == null || sz.length() == 0)
            return null;
        else
            return IsoDate.stringToDate(sz, IsoDate.DATE_TIME);
    }

    private String FromNullableString(Object o) {
        if (o != null && !o.toString().contains("anyType"))
            return o.toString();
        return "";
    }

    String ReadNullableString(SoapObject so, int index) {
        return FromNullableString(so.getProperty(index));
    }

    String ReadNullableString(SoapObject so, String propName) {
        return FromNullableString(so.getProperty(propName));
    }

    public abstract void ToProperties(SoapObject so);

    protected abstract void FromProperties(SoapObject so);
}
