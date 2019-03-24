/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2019 MyFlightbook, LLC

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

import java.io.Serializable;


public class CurrencyStatusItem extends SoapableObject implements Serializable {
    public enum  CurrencyGroups {None, FlightExperience, FlightReview, Aircraft, AircraftDeadline, Certificates, Medical, Deadline, CustomCurrency}

    public String Attribute = "";
    public String Value = "";
    public String Status = "";
    public String Discrepancy = "";
    public int AssociatedResourceID = 0;
    public CurrencyGroups CurrencyGroup = CurrencyGroups.None;
    public FlightQuery Query = null;

    public CurrencyStatusItem(SoapObject so) {
        super();
        FromProperties(so);
    }

    public CurrencyStatusItem() { super(); }

    @Override
    public String toString() {
        return String.format("%s %s %s %s", Attribute, Value, Status, Discrepancy);
    }

    public void ToProperties(SoapObject so) {
        so.addProperty("Attribute", Attribute);
        so.addProperty("Value", Value);
        so.addProperty("Status", Status);
        so.addProperty("Discrepancy", Discrepancy);
        so.addProperty("AssociatedResourceID", AssociatedResourceID);
        so.addProperty("CurrencyGroup", CurrencyGroup);
        so.addProperty("Query", Query);
    }

    protected void FromProperties(SoapObject so) {
        Attribute = so.getProperty("Attribute").toString();
        Value = so.getProperty("Value").toString();
        Status = so.getProperty("Status").toString();

        // Optional strings come through as "anyType" if they're not actually present, so check for that.
        Discrepancy = ReadNullableString(so, "Discrepancy");

        try {
            AssociatedResourceID = Integer.parseInt(so.getPropertySafelyAsString("AssociatedResourceID"));
        }
        catch (NumberFormatException | NullPointerException ignored) {
            AssociatedResourceID = 0;
        }

        try {
            CurrencyGroup = CurrencyGroups.valueOf(so.getPropertySafelyAsString("CurrencyGroup"));
        }
        catch (IllegalArgumentException | NullPointerException ignored) {
            CurrencyGroup = CurrencyGroups.None;
        }

        if (so.hasProperty("Query")) {
            SoapObject q = (SoapObject) so.getProperty("Query");
            Query = new FlightQuery();
            Query.FromProperties(q);
        }
    }
}
