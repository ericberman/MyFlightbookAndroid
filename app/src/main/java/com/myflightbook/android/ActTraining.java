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

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.MFBSoap;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

import Model.MFBConstants;
import Model.MFBUtil;

public class ActTraining extends ListFragment implements OnItemClickListener {

	public class TrainingItem extends Object
	{
		public int idTitle;
		public String szURLDest;
		
		public TrainingItem()
		{
			
		}
		
		public TrainingItem(int TitleID, String DestinationURL)
		{
			idTitle = TitleID;
			szURLDest = DestinationURL;
		}		
	}
	
	TrainingItem[] m_rgTrainingItems = 
			{
			new TrainingItem(R.string.lblStudents, "students"),
			new TrainingItem(R.string.lblInstructors, "instructors"),
			new TrainingItem(R.string.lblEndorsements, "endorse"),
			new TrainingItem(R.string.lbl8710, "8710"),
			new TrainingItem(R.string.lblAchievements, "badges"),
			new TrainingItem(R.string.lblRatingsProgress, "progress")
			};

	
	private class TrainingAdapter extends ArrayAdapter<TrainingItem>
	{		
		private TrainingItem[] m_rgti;
		
		public TrainingAdapter(Context c, int rid, TrainingItem[] rgti) {
			super(c, rid, rgti);
			m_rgti = rgti;
		}

		@Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.trainingitem, parent, false);
            }
            
            if (m_rgti == null)
            	return v;

            TrainingItem ti = m_rgti[position];
            
            TextView tvti = (TextView) v.findViewById(R.id.txtTrainingItem);
            tvti.setText(ActTraining.this.getString(ti.idTitle));
            
            return v;
		}
	}
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(false);
        return inflater.inflate(R.layout.training, container, false);
    }
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
	}
	
	// update the list if our array is null
	public void onResume()
	{
		super.onResume();
		populateList();
	}
	
	public void populateList()
	{
		TrainingAdapter ta = new TrainingAdapter(getActivity(), R.layout.trainingitem, m_rgTrainingItems);
		setListAdapter(ta);
		getListView().setOnItemClickListener(this);
	}
	
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		// TODO: IsOnline doesn't work from main thread.
		if (!AuthToken.FIsValid() || !MFBSoap.IsOnline())
		{
			MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.errTrainingNotAvailable));
			return;
		}
			
		String szProtocol = MFBConstants.fIsDebug ? "http" : "https";
		String szDest = m_rgTrainingItems[position].szURLDest;
		String szURL;
		try {
			szURL = String.format(Locale.US, MFBConstants.urlTraining, szProtocol, MFBConstants.szIP, URLEncoder.encode(AuthToken.m_szEmail, "UTF-8"), URLEncoder.encode(AuthToken.m_szPass, "UTF-8"), szDest);
			ActWebView.ViewURL(getActivity(), szURL);
		} catch (UnsupportedEncodingException e) {
		}
	}
}
