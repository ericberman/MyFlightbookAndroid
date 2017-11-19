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

import org.ksoap2.serialization.SoapObject;

import java.util.Locale;

public class Totals extends SoapableObject {
    public enum NumType {Integer, Decimal, Time, Currency}

    public String Description = "";
    public double Value = 0.0;
    public String SubDescription = "";
    public NumType NumericType = NumType.Integer;
    public FlightQuery Query = null;

    public Totals(SoapObject so) {
        super();
        FromProperties(so);
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%s %s %.2f", Description, SubDescription, Value);
    }

    public void ToProperties(SoapObject so) {
        so.addProperty("Value", Value);
        so.addProperty("Description", Description);
        so.addProperty("SubDescription", SubDescription);
        so.addProperty("NumericType", NumericType);
        so.addProperty("Query", Query);
    }

    public void FromProperties(SoapObject so) {
        Description = so.getProperty("Description").toString();
        Value = Double.parseDouble(so.getProperty("Value").toString());

        // Optional strings come through as "anyType" if they're not actually present, so check for that.
        Object o = so.getProperty("SubDescription");
        if (o != null && !o.toString().contains("anyType"))
            SubDescription = o.toString();

        NumericType = NumType.valueOf(so.getProperty("NumericType").toString());

        if (so.hasProperty("Query")) {
            SoapObject q = (SoapObject) so.getProperty("Query");
            Query = new FlightQuery();
            Query.FromProperties(q);
        }


    }


}
