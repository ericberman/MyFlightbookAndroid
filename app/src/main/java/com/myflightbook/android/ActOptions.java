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
package com.myflightbook.android;

import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.RecentFlightsSvc;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

import Model.Airport;
import Model.DecimalEdit;
import Model.LogbookEntry;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBImageInfo.PictureDestination;
import Model.MFBLocation;
import Model.MFBTakeoffSpeed;
import Model.MFBUtil;

public class ActOptions extends ActMFBForm implements android.view.View.OnClickListener, OnItemSelectedListener {
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.options, container, false);
    }
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
        AddListener(R.id.btnSignIn);
        AddListener(R.id.btnSignOut);
        AddListener(R.id.btnCreateNewAccount);
        AddListener(R.id.btnContact);
        AddListener(R.id.btnFacebook);
        AddListener(R.id.btnTwitter);
        AddListener(R.id.btnFAQ);
        AddListener(R.id.btnCleanUp);
        AddListener(R.id.btnSupport);
        AddListener(R.id.btnAdditionalOptions);
        
        boolean fHasGPS = MFBMain.HasGPS();
        if (!fHasGPS)
        	MFBLocation.fPrefAutoDetect = MFBLocation.fPrefRecordFlight = false;

        CheckBox ck = (CheckBox)findViewById(R.id.ckAutodetect);
        ck.setOnClickListener(this);
        ck.setChecked(MFBLocation.fPrefAutoDetect);
        ck.setEnabled(fHasGPS);

        ck = (CheckBox)findViewById(R.id.ckRecord);
        ck.setOnClickListener(this);
        ck.setChecked(MFBLocation.fPrefRecordFlight);
        ck.setEnabled(fHasGPS);
        
        ck = (CheckBox)findViewById(R.id.ckHeliports);
        ck.setOnClickListener(this);
        ck.setChecked(Airport.fPrefIncludeHeliports);
        ck.setEnabled(fHasGPS);
        
        ck = (CheckBox)findViewById(R.id.ckUseHHMM);
        ck.setOnClickListener(this);
        ck.setChecked(DecimalEdit.DefaultHHMM);
        
        ck = (CheckBox) findViewById(R.id.ckUseLocalTime);
        ck.setOnClickListener(this);
        ck.setChecked(DlgDatePicker.fUseLocalTime);
        
        ck = (CheckBox) findViewById(R.id.ckRoundNearestTenth);
        ck.setOnClickListener(this);
        ck.setChecked(MFBLocation.fPrefRoundNearestTenth);

        // Strings for spinner
    	String[] rgAutoHobbs = {getString(R.string.autoNone),
    			getString(R.string.autoFlight),
    			getString(R.string.autoEngine)
    	};
    	
    	String[] rgAutoTotals = 
    		{
    			getString(R.string.autoNone),
    			getString(R.string.autoFlight),
    			getString(R.string.autoEngine),
    			getString(R.string.autoHobbs)
    		};
    	
    	Spinner sp = (Spinner)findViewById(R.id.spnAutoHobbs);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), R.layout.mfbsimpletextitem, rgAutoHobbs);
		sp.setAdapter(adapter);
		sp.setSelection(MFBLocation.fPrefAutoFillHobbs.ordinal());
		sp.setOnItemSelectedListener(this);
		sp.setPromptId(R.string.lblAutoFillOptions);

        sp = (Spinner)findViewById(R.id.spnAutoTime);
		adapter = new ArrayAdapter<String>(getActivity(), R.layout.mfbsimpletextitem, rgAutoTotals);
		sp.setAdapter(adapter);
		sp.setSelection(MFBLocation.fPrefAutoFillTime.ordinal());
		sp.setOnItemSelectedListener(this);
		sp.setPromptId(R.string.lblAutoFillOptions);
		
		sp = (Spinner) findViewById(R.id.spnTOSpeed);
		adapter = new ArrayAdapter<String>(getActivity(), R.layout.mfbsimpletextitem, MFBTakeoffSpeed.GetDisplaySpeeds().toArray(new String[0]));
		sp.setAdapter(adapter);
		sp.setSelection(MFBTakeoffSpeed.getTakeOffSpeedIndex());
		sp.setOnItemSelectedListener(this);
		
		TextView t = (TextView) findViewById(R.id.txtCopyright);
		if (MFBConstants.fIsDebug)
		{
			String s = String.format("%s - DEBUG (%s)", 
					t.getText().toString(), 
					String.format("%s %d %d", MFBConstants.szIP, MFBTakeoffSpeed.getLandingSpeed(), MFBTakeoffSpeed.getTakeOffspeed()));
			t.setText(s);
		}
	}
	
    protected void updateStatus()
    {
		// refresh sign-in status
		TextView t = (TextView) findViewById(R.id.txtSignInStatus);
		if (AuthToken.FIsValid())
			t.setText(String.format(this.getString(R.string.statusSignedIn), AuthToken.m_szEmail));
		else
			t.setText(this.getString(R.string.statusNotSignedIn));    	
		
		findViewById(R.id.btnSignOut).setVisibility(AuthToken.FIsValid() ? View.VISIBLE : View.GONE);
    }
    
    public void onWindowFocusChanged(boolean fHasFocus)
    {
    	if (fHasFocus)
    		updateStatus();
    }
    
	public void onResume()
	{
		super.onResume();
		updateStatus();
	}
        
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
    {
    	Spinner sp = (Spinner) parent;
    	int i = sp.getSelectedItemPosition();
    	switch (sp.getId())
    	{
    	case R.id.spnAutoHobbs:
    		MFBLocation.fPrefAutoFillHobbs = MFBLocation.AutoFillOptions.values()[i];;
    		break;
    	case R.id.spnAutoTime:
    		MFBLocation.fPrefAutoFillTime = MFBLocation.AutoFillOptions.values()[i];;
    		break;
    	case R.id.spnTOSpeed:
    		MFBTakeoffSpeed.setTakeOffSpeedIndex(i);
    		break;
		default:
			break;
    	}
    }
    
    public void ContactUs()
    {
    	ActWebView.ViewURL(getActivity(), String.format(MFBConstants.urlContact, AuthToken.m_szEmail, "Comment from Android user"));
    }
    
    public void ViewFacebook()
    {
		Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(String.format(MFBConstants.urlFacebook, AuthToken.m_szEmail, "Comment from Android user")));
		startActivity(i);    	
    }
    
    public void ViewTwitter()
    {
		Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(String.format(MFBConstants.urlTwitter, AuthToken.m_szEmail, "Comment from Android user")));
		startActivity(i);
    }
    
    public void CleanUp()
    {		
    	boolean fOrphansFound = false;
    	
		// Clean up any:
		//  (a) orphaned new flights (shouldn't happen, but could)
		//  (b) any flight images that are not associated with pending or new flights
		//  (c) any aircraft images that have not yet been posted

		// first make sure we're only working on one new flight at a time.
		LogbookEntry[] rgLeNew = LogbookEntry.getNewFlights();
		if (ActNewFlight.lastNewFlightID > 0)
		{
			for (LogbookEntry le : rgLeNew)
				if (le.idLocalDB != ActNewFlight.lastNewFlightID)
				{
					Log.e("MFBAndroid", String.format("DELETING FOUND ORPHANED FLIGHT: %d", le.idLocalDB));
					le.idFlight = LogbookEntry.ID_PENDING_FLIGHT;
					le.ToDB();
					RecentFlightsSvc.ClearCachedFlights();
					fOrphansFound = true;
				}
		}

		// Now look for orphaned flight image files.  Start with the known flight images
		LogbookEntry[] rgLeAll = LogbookEntry.mergeFlightLists(rgLeNew, LogbookEntry.getPendingFlights());
		ArrayList<String> alImages = new ArrayList<String>();
		for (LogbookEntry le : rgLeAll)
		{
			le.getImagesForFlight();
			for (MFBImageInfo mfbii : le.rgFlightImages)
				alImages.add(mfbii.getImageFile());
		}
				
		// Now delete the flight images that are not in our list
		MFBImageInfo.DeleteOrphansNotInList(PictureDestination.FlightImage, alImages, getActivity());
		
        // Clean up any orphaned aircraft images
		// We can delete ALL aircraft images - if they weren't submitted, they aren't going to be picked up.

		// First delete all of the ones that haven't been saved to the server
		MFBImageInfo rgMfbiiAircraft[] = MFBImageInfo.getAllAircraftImages();
		for (MFBImageInfo mfbii : rgMfbiiAircraft)
			if (!mfbii.IsOnServer())
				mfbii.deleteFromDB();
		
		// now delete any remaining aircraft images that might be in our files.
		MFBImageInfo.DeleteOrphansNotInList(PictureDestination.AircraftImage, new ArrayList<String>(), getActivity());
		
		MFBUtil.Alert(this, getString(R.string.lblCleanup), getString(fOrphansFound ? R.string.errCleanupOrphansFound : R.string.txtCleanupComplete));
    }
    
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.optionsmenu, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.menuContact:
			ContactUs();
			break;
		case R.id.menuFacebook:
			ViewFacebook();
			break;
		case R.id.menuTwitter:
			ViewTwitter();
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}
    
	public void onNothingSelected(AdapterView<?> arg0) {
	}

	public void ViewPreferences(String szTemplate)
	{
		if (!AuthToken.FIsValid())
		{
			MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.statusNotSignedIn));
			return;
		}
		
		String szProtocol = MFBConstants.fIsDebug ? "http" : "https";
		String szURL;
		try {
			szURL = String.format(Locale.US, szTemplate, szProtocol, MFBConstants.szIP, URLEncoder.encode(AuthToken.m_szEmail, "UTF-8"), URLEncoder.encode(AuthToken.m_szPass, "UTF-8"));
			ActWebView.ViewURL(getActivity(), szURL);
		} catch (UnsupportedEncodingException e) {
		}
	}

	public void onClick(View v) 
	{
    	switch (v.getId())
		{
		case R.id.btnSignIn:
			DlgSignIn d = new DlgSignIn(getActivity());
			d.setOnDismissListener(new OnDismissListener() {
				public void onDismiss(DialogInterface dialog) { updateStatus(); }
			});
			d.show();
			break;
		case R.id.btnSignOut:
			AuthToken.m_szAuthToken = AuthToken.m_szEmail = AuthToken.m_szPass = "";
			new AuthToken().FlushCache();
			MFBMain.invalidateAll();
			updateStatus();
			break;
    	case R.id.btnCreateNewAccount:
    	{
			Intent i = new Intent(v.getContext(), ActNewUser.class);
			startActivityForResult(i, 0);
		}
			break;
		case R.id.ckAutodetect:
			MFBLocation.fPrefAutoDetect = ((CheckBox) v).isChecked();
			break;
		case R.id.ckRecord:
			MFBLocation.fPrefRecordFlight = ((CheckBox) v).isChecked();
			break;
		case R.id.ckHeliports:
    		Airport.fPrefIncludeHeliports = ((CheckBox)v).isChecked();
			break;
		case R.id.ckUseHHMM:
    		DecimalEdit.DefaultHHMM = ((CheckBox)v).isChecked();
			break;
		case R.id.ckUseLocalTime:
			DlgDatePicker.fUseLocalTime = ((CheckBox)v).isChecked();
			break;
		case R.id.ckRoundNearestTenth:
			MFBLocation.fPrefRoundNearestTenth = ((CheckBox) v).isChecked();
			break;
		case R.id.btnContact:
			this.ContactUs();
			break;
		case R.id.btnFacebook:
			this.ViewFacebook();
			break;
		case R.id.btnTwitter:
			this.ViewTwitter();
			break;
		case R.id.btnCleanUp:
			CleanUp();
			break;
		case R.id.btnSupport:
			ViewPreferences(MFBConstants.urlSupport);
			break;
		case R.id.btnAdditionalOptions:
			ViewPreferences(MFBConstants.urlPreferences);
			break;
		case R.id.btnFAQ:
			ActWebView.ViewURL(getActivity(), MFBConstants.urlFAQ);
			break;
		default:
			break;
		}
	}
}
