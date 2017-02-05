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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.MFBSoap;
import com.myflightbook.android.WebServices.UTCDate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Locale;

import Model.Aircraft;
import Model.Aircraft.PilotRole;
import Model.FlightQuery;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBImageInfo.PictureDestination;
import Model.MFBUtil;

public class ActEditAircraft extends ActMFBForm implements android.view.View.OnClickListener,
	DlgDatePicker.DateTimeUpdate, ActMFBForm.GallerySource {
	public final static String AIRCRAFTID = "com.myflightbook.android.aircraftID";
	public static final int BEGIN_EDIT_AIRCRAFT_REQUEST_CODE = 49521;
	public static final int RESULT_CODE_AIRCRAFT_DELETED = 19573;
	public static final int RESULT_CODE_AIRCRAFT_CHANGED = 19574;
	
	private Aircraft m_ac = null;

	private class SubmitTask extends AsyncTask<Void, String, Boolean> implements MFBSoap.MFBSoapProgressUpdate
	{
		private ProgressDialog m_pd = null;
		private AircraftSvc m_acs = null;

		@Override
		protected Boolean doInBackground(Void... params) {
			m_acs = new AircraftSvc();
			m_acs.m_Progress = this;
			m_acs.UpdateMaintenanceForAircraft(AuthToken.m_szAuthToken, m_ac);
			return (m_acs.getLastError().length() == 0);
		}

		protected void onPreExecute() {
			m_pd = MFBUtil.ShowProgress(ActEditAircraft.this, ActEditAircraft.this.getString(R.string.prgUpdatingAircraft));
		}

		protected void onPostExecute(Boolean b) {
			if (isAdded() && !getActivity().isFinishing())
			{
				if (b)
				{
					// force a refresh.
					m_acs.FlushCache();
					MFBMain.invalidateCachedTotals();	// could have updated maintenance, leading currency to be invalid.
					Intent i = new Intent();
					getActivity().setResult(RESULT_CODE_AIRCRAFT_CHANGED, i);
					finish();
				}
				else
				{
					MFBUtil.Alert(ActEditAircraft.this, getString(R.string.txtError), m_acs.getLastError());
				}
			}

			try { m_pd.dismiss();} catch (Exception e) {}
		}
		
		protected void onProgressUpdate(String... msg)
		{
			m_pd.setMessage(msg[0]);
		}

		public void NotifyProgress(int percentageComplete, String szMsg) {
			this.publishProgress(new String[] {szMsg});
		}
	}

	private class DeleteTask extends AsyncTask<Void, String, MFBSoap> implements MFBSoap.MFBSoapProgressUpdate
	{
		private ProgressDialog m_pd = null;
		private AircraftSvc m_acs = null;

		@Override
		protected MFBSoap doInBackground(Void... params) {
			m_acs = new AircraftSvc();
			m_acs.m_Progress = this;
			m_acs.DeleteAircraftForUser(AuthToken.m_szAuthToken, m_ac.AircraftID);
			return m_acs;
		}

		protected void onPreExecute() {
			m_pd = MFBUtil.ShowProgress(ActEditAircraft.this, ActEditAircraft.this.getString(R.string.prgDeletingAircraft));
		}

		protected void onPostExecute(MFBSoap acs) {
			if (!getActivity().isFinishing())
			{
				if (acs != null)
				{
					if (acs.getLastError().length() == 0)
					{
						Intent i = new Intent();
						getActivity().setResult(RESULT_CODE_AIRCRAFT_DELETED, i);
						finish();
					}
					else
						MFBUtil.Alert(ActEditAircraft.this, getString(R.string.txtError), acs.getLastError());
				}
			}

			try { m_pd.dismiss();} catch (Exception e) {}
		}
		
		protected void onProgressUpdate(String... msg)
		{
			m_pd.setMessage(msg[0]);
		}

		public void NotifyProgress(int percentageComplete, String szMsg) {
			this.publishProgress(new String[] {szMsg});
		}
	}
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.setHasOptionsMenu(true);
		return inflater.inflate(R.layout.editaircraft, container, false);
    }
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		AddListener(R.id.btnVORCheck);
		AddListener(R.id.btnTransponder);
		AddListener(R.id.btnPitotStatic);
		AddListener(R.id.btnAltimeter);
		AddListener(R.id.btnELT);
		AddListener(R.id.btnAnnual);
		AddListener(R.id.btnRegistration);
		AddListener(R.id.ckHideAircraftFromSelection);
		AddListener(R.id.rbRoleNone);
		AddListener(R.id.rbRoleCFI);
		AddListener(R.id.rbRolePIC);
		AddListener(R.id.rbRoleSIC);

		// Expand/collapse
		AddListener(R.id.acPrefsHeader);
		AddListener(R.id.acNotesHeader);
		AddListener(R.id.txtACMaintenance);

		Intent i = getActivity().getIntent();
		int idAircraft = i.getIntExtra(AIRCRAFTID, 0);
		if (idAircraft > 0)
		{
			AircraftSvc acs = new AircraftSvc();
			
			Aircraft[] rgac = acs.getCachedAircraft();
			
			for (Aircraft ac : rgac)
			{
				if (ac.AircraftID == idAircraft)
				{
					m_ac = ac;
					break;
				}
			}
		}

		toView();
	}
	
	@Override
	public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.imagemenu, menu);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case R.id.menuAddComment:
		case R.id.menuDeleteImage:
		case R.id.menuViewImage:
			return onImageContextItemSelected(item, this);
		default:
			break;
		}
		return true;
	}
	
	void toView()
	{
		if (m_ac == null)
		{
			finish();
			return;
		}
				
		((TextView) findViewById(R.id.txtTailNumber)).setText(m_ac.TailNumber);
		((TextView) findViewById(R.id.txtAircraftType)).setText(getString(Aircraft.rgidInstanceTypes[m_ac.InstanceTypeID > 0 ? m_ac.InstanceTypeID - 1 : 0]));
		((TextView) findViewById(R.id.txtAircraftMakeModel)).setText(String.format("%s%s",
				m_ac.ModelDescription,
				m_ac.ModelCommonName.length() > 0 ? String.format(" (%s)", m_ac.ModelCommonName.trim()) : ""));
		
		SetLocalDateForField(R.id.btnVORCheck, MFBUtil.LocalDateFromUTCDate(m_ac.LastVOR));
		SetLocalDateForField(R.id.btnAltimeter, MFBUtil.LocalDateFromUTCDate(m_ac.LastAltimeter));
		SetLocalDateForField(R.id.btnAnnual, MFBUtil.LocalDateFromUTCDate(m_ac.LastAnnual));
		SetLocalDateForField(R.id.btnTransponder, MFBUtil.LocalDateFromUTCDate(m_ac.LastTransponder));
		SetLocalDateForField(R.id.btnELT, MFBUtil.LocalDateFromUTCDate(m_ac.LastELT));
		SetLocalDateForField(R.id.btnPitotStatic, MFBUtil.LocalDateFromUTCDate(m_ac.LastStatic));
		SetLocalDateForField(R.id.btnRegistration, MFBUtil.LocalDateFromUTCDate(m_ac.RegistrationDue));
		
		SetDoubleForField(R.id.txt100hr, m_ac.Last100);
		SetDoubleForField(R.id.txtOilChange, m_ac.LastOil);
		SetDoubleForField(R.id.txtNewEngine, m_ac.LastEngine);
		
		((TextView) findViewById(R.id.txtPublicAircraftNotes)).setText(m_ac.PublicNotes);
		((TextView) findViewById(R.id.txtPrivateAircraftNotes)).setText(m_ac.PrivateNotes);
		
		SetCheckState(R.id.ckHideAircraftFromSelection, !m_ac.HideFromSelection);
		switch (m_ac.RoleForPilot)
		{
		case None:
			SetRadioButton(R.id.rbRoleNone);
			break;
		case PIC:
			SetRadioButton(R.id.rbRolePIC);
			break;
		case SIC: 
			SetRadioButton(R.id.rbRoleSIC);
			break;
		case CFI:
			SetRadioButton(R.id.rbRoleCFI);
			break;
		}
		
		setUpImageGallery(getGalleryID(), getImages());

		findViewById(R.id.sectMaintenance).setVisibility(m_ac.IsReal() && !m_ac.IsAnonymous() ? View.VISIBLE : View.GONE);
	}
	
	void fromView()
	{
		// dates were changed synchronously, only need the decimal values.
		m_ac.Last100 = DoubleFromField(R.id.txt100hr);
		m_ac.LastOil = DoubleFromField(R.id.txtOilChange);
		m_ac.LastEngine = DoubleFromField(R.id.txtNewEngine);
		
		m_ac.PublicNotes = StringFromField(R.id.txtPublicAircraftNotes);
		m_ac.PrivateNotes = StringFromField(R.id.txtPrivateAircraftNotes);
	}

	public void onClick(View v) {
		fromView();
		int id = v.getId();
		switch (id)
		{
		case R.id.btnVORCheck:
			if (UTCDate.IsNullDate(m_ac.LastVOR))
				m_ac.LastVOR = MFBUtil.UTCDateFromLocalDate(new Date());
			else
				SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastVOR), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
			break;
		case R.id.btnAltimeter:
			if (UTCDate.IsNullDate(m_ac.LastAltimeter))
				m_ac.LastAltimeter = MFBUtil.UTCDateFromLocalDate(new Date());
			else
				SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastAltimeter), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
			break;
		case R.id.btnAnnual:
			if (UTCDate.IsNullDate(m_ac.LastAnnual))
				m_ac.LastAnnual = MFBUtil.UTCDateFromLocalDate(new Date());
			else
				SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastAnnual), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
			break;
		case R.id.btnTransponder:
			if (UTCDate.IsNullDate(m_ac.LastTransponder))
				m_ac.LastTransponder = MFBUtil.UTCDateFromLocalDate(new Date());
			else
				SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastTransponder), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
			break;
		case R.id.btnELT:
			if (UTCDate.IsNullDate(m_ac.LastELT))
				m_ac.LastELT = MFBUtil.UTCDateFromLocalDate(new Date());
			else
				SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastELT), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
			break;
		case R.id.btnPitotStatic:
			if (UTCDate.IsNullDate(m_ac.LastStatic))
				m_ac.LastStatic = MFBUtil.UTCDateFromLocalDate(new Date());
			else
				SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.LastStatic), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
			break;
		case R.id.btnRegistration:
			if (UTCDate.IsNullDate(m_ac.RegistrationDue))
				m_ac.RegistrationDue = MFBUtil.UTCDateFromLocalDate(new Date());
			else
				SetDateTime(id, MFBUtil.LocalDateFromUTCDate(m_ac.RegistrationDue), this, DlgDatePicker.datePickMode.LOCALDATENULLABLE);
			break;
		case R.id.ckHideAircraftFromSelection:
			m_ac.HideFromSelection = !CheckState(id);
			break;
		case R.id.rbRoleNone:
			m_ac.RoleForPilot = PilotRole.None;
			break;
		case R.id.rbRolePIC:
			m_ac.RoleForPilot = PilotRole.PIC;
			break;
		case R.id.rbRoleSIC:
			m_ac.RoleForPilot = PilotRole.SIC;
			break;
		case R.id.rbRoleCFI:
			m_ac.RoleForPilot = PilotRole.CFI;
			break;
		case R.id.acNotesHeader: {
			View target = findViewById(R.id.sectACNotes);
			setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
			break;
		}
		case R.id.acPrefsHeader: {
			View target = findViewById(R.id.rbgPilotRole);
			setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
			break;
		}
		case R.id.txtACMaintenance: {
			View target = findViewById(R.id.sectACMaintenance);
			setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
			break;
		}
		}
		toView();
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == Activity.RESULT_OK)
			AddCameraImage(m_TempFilePath, false);
		else if (requestCode == SELECT_IMAGE_ACTIVITY_REQUEST_CODE && resultCode == Activity.RESULT_OK)
			AddGalleryImage(data);
	}	

	public void updateDate(int id, Date dt) {
		fromView();
		dt = MFBUtil.UTCDateFromLocalDate(dt);
		switch (id)
		{
		case R.id.btnVORCheck:
			m_ac.LastVOR = dt;
			SetLocalDateForField(R.id.btnVORCheck, m_ac.LastVOR);
			break;
		case R.id.btnAltimeter:
			m_ac.LastAltimeter = dt;
			SetLocalDateForField(R.id.btnAltimeter, m_ac.LastAltimeter);
			break;
		case R.id.btnAnnual:
			m_ac.LastAnnual = dt;
			SetLocalDateForField(R.id.btnAnnual, m_ac.LastAnnual);
			break;
		case R.id.btnTransponder:
			m_ac.LastTransponder = dt;
			SetLocalDateForField(R.id.btnTransponder, m_ac.LastTransponder);
			break;
		case R.id.btnELT:
			m_ac.LastELT = dt;
			SetLocalDateForField(R.id.btnELT, m_ac.LastELT);
			break;
		case R.id.btnPitotStatic:
			m_ac.LastStatic = dt;
			SetLocalDateForField(R.id.btnPitotStatic, m_ac.LastStatic);
			break;	
		case R.id.btnRegistration:
			m_ac.RegistrationDue = dt;
			SetLocalDateForField(R.id.btnRegistration, m_ac.RegistrationDue);
			break;
		}
		toView();
	}
	
	private void updateAircraft()
	{
		fromView();
		SubmitTask st = new SubmitTask();
		st.execute();
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.editaircraftmenu, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    case R.id.menuChoosePicture:
	    	ChoosePicture();
	    	return true;
	    case R.id.menuTakePicture:
	    	TakePicture();
	    	return true;
	    case R.id.menuUpdateAircraft:
	    	updateAircraft();
	    	return true;
	    case R.id.menuDeleteAircraft:
	    	(new DeleteTask()).execute();
	    	return true;
	    case R.id.menuViewSchedule:
			String szURL;
			try {
				szURL = String.format(Locale.US, MFBConstants.urlAircraftSchedule, MFBConstants.szIP, URLEncoder.encode(AuthToken.m_szEmail, "UTF-8"), URLEncoder.encode(AuthToken.m_szPass, "UTF-8"), m_ac.AircraftID);
				ActWebView.ViewURL(getActivity(), szURL);
			} catch (UnsupportedEncodingException e) {
			}
	    	return true;
	    case R.id.findFlights:
			FlightQuery fq = new FlightQuery();
			fq.Init();
			fq.AircraftList = new Aircraft[] { m_ac };
			Intent i = new Intent(getActivity(), RecentFlightsActivity.class);
			Bundle b = new Bundle();
			b.putSerializable(RecentFlightsActivity.REQUEST_FLIGHT_QUERY, fq);
			i.putExtras(b);
			startActivity(i);
	    	return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

	/*
	 * GallerySource methods
	 * (non-Javadoc)
	 * @see com.myflightbook.android.ActMFBForm.GallerySource#getGalleryID()
	 */
	public int getGalleryID() {
		return R.id.galImages;
	}

	public MFBImageInfo[] getImages() {
		if (m_ac == null || m_ac.AircraftImages == null)
			return new MFBImageInfo[0];
		else
			return m_ac.AircraftImages;
	}

	public void setImages(MFBImageInfo[] rgmfbii) {
		m_ac.AircraftImages = rgmfbii;
	}

	public void newImage(MFBImageInfo mfbii) {
		mfbii.setPictureDestination(PictureDestination.AircraftImage);
		mfbii.setTargetID(m_ac.AircraftID);
		mfbii.toDB();
		m_ac.AircraftImages = MFBImageInfo.getLocalImagesForId(m_ac.AircraftID, PictureDestination.AircraftImage);
	}

	public void refreshGallery() {
		setUpImageGallery(getGalleryID(), getImages());
	}

	public PictureDestination getDestination() {
		return PictureDestination.AircraftImage;
	}
}
