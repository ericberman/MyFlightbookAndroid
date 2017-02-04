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

import com.myflightbook.android.MFBMain;
import com.myflightbook.android.Marshal.MarshalDate;
import com.myflightbook.android.Marshal.MarshalDouble;
import com.myflightbook.android.R;

import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;

import java.util.Date;

import Model.FlightProperty;
import Model.LatLong;
import Model.LogbookEntry;
import Model.MFBImageInfo;
import Model.MFBUtil;
import Model.PostingOptions;

public class CommitFlightSvc extends MFBSoap {

	public CommitFlightSvc()
	{
	}

	@Override
	public void AddMappings(SoapSerializationEnvelope e)
	{
		e.addMapping(NAMESPACE, "CommitFlightWithOptionsResult", LogbookEntry.class);
		e.addMapping(NAMESPACE, "le", LogbookEntry.class);
		e.addMapping(NAMESPACE, "PostingOptions", PostingOptions.class);
		e.addMapping(NAMESPACE, "LogbookEntry", LogbookEntry.class);
		e.addMapping(NAMESPACE, "CustomFlightProperty", FlightProperty.class);
		e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo.class);
		e.addMapping(NAMESPACE, "LatLong", LatLong.class);
		
		MarshalDate mdt = new MarshalDate();
		MarshalDouble md = new MarshalDouble();
		mdt.register(e);
		md.register(e);
	}
	
	public Boolean FCommitFlight(String szAuthToken, LogbookEntry le, PostingOptions po)
	{
		SoapObject Request = setMethod("CommitFlightWithOptions");
    	Request.addProperty("szAuthUserToken", szAuthToken);
    	PropertyInfo piLe = new PropertyInfo();
    	PropertyInfo pipo = new PropertyInfo();
    	piLe.name = "le";
    	piLe.type = "LogbookEntry";
    	piLe.setValue(le);
    	piLe.namespace = NAMESPACE;
    	pipo.name = "po";
    	pipo.type = "PostingOptions";
    	pipo.setValue(po);
    	pipo.namespace = NAMESPACE;
    	Request.addProperty(piLe);
    	Request.addProperty(pipo);
    	
    	// The date of flight is done in local time; need to convert it to
    	// a UTC time that looks like the correct local time so that it records
    	// correctly over the wire (same as in iPhone version.
    	
    	// save the date, since we're making a live copy
    	Date dtSave = le.dtFlight;
    	
    	le.dtFlight = MFBUtil.UTCDateFromLocalDate(le.dtFlight);
    	
    	if (le.rgFlightImages == null)
    		le.getImagesForFlight();
    	
    	MFBImageInfo[] rgmfbii = le.rgFlightImages;    		
    	assert(rgmfbii != null); // may have zero length, but that's ok
    	
    	le.rgFlightImages = new MFBImageInfo[0];
    	SoapObject result = (SoapObject) Invoke();
		
		if (result == null)
		{
			le.rgFlightImages = rgmfbii; // restore the images.
			setLastError("Failed to save flight - " + getLastError());
		}
		else
		{
			try
			{
				LogbookEntry leReturn = new LogbookEntry();
				leReturn.FromProperties(result);
				le.szError = leReturn.szError;
				
				int iImg = 1;
				for (MFBImageInfo mfbii : rgmfbii)
				{
	    			String szFmtUploadProgress = MFBMain.GetMainContext().getString(R.string.prgUploadingImages);
					String szStatus = String.format(szFmtUploadProgress, iImg, rgmfbii.length);
					if (m_Progress != null)
						m_Progress.NotifyProgress((iImg * 100) / rgmfbii.length, szStatus);
					// skip anything that is already on the server.
					if (!mfbii.IsOnServer())
					{
						mfbii.setKey(leReturn.idFlight);
						mfbii.UploadPendingImage(mfbii.getID());
					}
					iImg++;
				}
				le.DeletePendingImagesForFlight();
			}
			catch (Exception e)
			{
				setLastError(getLastError() + e.getMessage());
			}
		}
		
		le.dtFlight = dtSave;
		
		le.szError = getLastError();
    	return getLastError().length() == 0;
	}	
}
