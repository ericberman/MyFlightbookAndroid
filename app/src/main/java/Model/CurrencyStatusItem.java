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


public class CurrencyStatusItem extends SoapableObject
{
	public String Attribute = "";
	public String Value = "";
	public String Status = "";
	public String Discrepancy = "";
	
	public CurrencyStatusItem()
	{
		super();
	}
	
	public CurrencyStatusItem(SoapObject so)
	{
		super();
		FromProperties(so);
	}
	
	@Override
	public String toString()
	{
		return String.format("%s %s %s %s", Attribute, Value, Status, Discrepancy); 
	}
	
	public void ToProperties(SoapObject so)
	{
		so.addProperty("Attribute", Attribute);
		so.addProperty("Value", Value);
		so.addProperty("Status", Status);
		so.addProperty("Discrepancy", Discrepancy);
	}
	
	public void FromProperties(SoapObject so)
	{
		Attribute = so.getProperty("Attribute").toString();
		Value = so.getProperty("Value").toString();
		Status = so.getProperty("Status").toString();
		
		// Optional strings come through as "anyType" if they're not actually present, so check for that.
		Discrepancy = ReadNullableString(so, "Discrepancy");
	}
	
}
