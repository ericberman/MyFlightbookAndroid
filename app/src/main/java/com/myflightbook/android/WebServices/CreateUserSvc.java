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

import org.ksoap2.serialization.SoapObject;


public class CreateUserSvc extends MFBSoap {

	public Boolean FCreateUser(String szEmail, String szPass, String szFirst, String szLast,
			String szQ, String szA)
	{
		Boolean fResult = false;
		SoapObject Request = setMethod("CreateUser");
		Request.addProperty("szAppToken", AuthToken.APPTOKEN);
		Request.addProperty("szEmail", szEmail);
		Request.addProperty("szPass", szPass);
		Request.addProperty("szFirst", szFirst);
		Request.addProperty("szLast", szLast);
		Request.addProperty("szQuestion", szQ);
		Request.addProperty("szAnswer", szA);
    	
    	SoapObject result = (SoapObject) Invoke();
		if (result == null)
			setLastError("Error creating account - " + getLastError());
		else
		{
			try
			{
				String szAuthToken = result.getProperty("szAuthToken").toString();
				
				// if we get here, we have success.
				AuthToken.m_szAuthToken = szAuthToken;
				AuthToken.m_szEmail = szEmail;
				AuthToken.m_szPass = szPass;
				fResult = true;
				
				// Clear the aircraft cache because we need to reload it
				AircraftSvc ac = new AircraftSvc();
				ac.FlushCache();
			}
			catch (Exception e)
			{
				setLastError(getLastError() + e.getMessage());
			}
		}
			
    	return fResult;
	}	
}
