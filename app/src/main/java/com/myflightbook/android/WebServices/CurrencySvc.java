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
package com.myflightbook.android.WebServices;

import org.ksoap2.serialization.*;

import Model.CurrencyStatusItem;


public class CurrencySvc extends MFBSoap {

	public CurrencyStatusItem[] CurrencyForUser(String szAuthToken)
	{
		SoapObject Request = setMethod("GetCurrencyForUser");
    	Request.addProperty("szAuthToken", szAuthToken);
    	
    	CurrencyStatusItem[] rgCsi = new CurrencyStatusItem[0];
    	
    	SoapObject result = (SoapObject) Invoke();
		if (result == null)
			setLastError("Error retrieving currency - " + getLastError());
		else
		{
			try
			{
				rgCsi = new CurrencyStatusItem[result.getPropertyCount()];
				
				for (int i = 0; i < rgCsi.length; i++)
				{
					rgCsi[i] = new CurrencyStatusItem((SoapObject) result.getProperty(i));
				}
			}
			catch (Exception e)
			{
				setLastError(getLastError() + e.getMessage());
			}
		}
			
    	return rgCsi;
	}	
}
