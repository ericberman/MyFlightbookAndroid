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

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TableRow.LayoutParams;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.CurrencySvc;
import com.myflightbook.android.WebServices.MFBSoap;

import Model.CurrencyStatusItem;
import Model.MFBUtil;

public class ActCurrency extends ActMFBForm implements MFBMain.Invalidatable {
	private static boolean fNeedsRefresh = true;
	public static void SetNeedsRefresh(boolean f)
	{
		fNeedsRefresh = f;
	}
	
	private static CurrencyStatusItem[] m_rgcsi = null;

	private class SoapTask extends AsyncTask<Void, Void, MFBSoap>
	{
		private Context m_Context = null;
		private ProgressDialog m_pd = null;
		Object m_Result = null;
		
		SoapTask(Context c)
		{
			super();
			m_Context = c;
		}
		
		@Override
		protected MFBSoap doInBackground(Void... params) {
    		CurrencySvc cs = new CurrencySvc();
    		m_Result = cs.CurrencyForUser(AuthToken.m_szAuthToken);
    		return cs;
		}
		
		protected void onPreExecute()
		{
			m_pd = MFBUtil.ShowProgress(m_Context, m_Context.getString(R.string.prgCurrency));
		}
		
		protected void onPostExecute(MFBSoap svc)
		{    		
    		if (!isAdded() || getActivity().isFinishing())
    			return;

    		m_rgcsi = (CurrencyStatusItem[])m_Result;
			
    		if (m_rgcsi == null || svc.getLastError().length() > 0)
    		{
    			MFBUtil.Alert(m_Context, getString(R.string.txtError), svc.getLastError());
    		}
    		else
    		{
    			SetNeedsRefresh(false);
    			BindTable();
    		}
    		
			try { m_pd.dismiss();}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		this.setHasOptionsMenu(true);
		return inflater.inflate(R.layout.currency, container, false);
    }
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
        TextView tvDisclaimer = (TextView)getView().findViewById(R.id.lnkCurrencyDisclaimer);
		if (tvDisclaimer != null) {    // should never happen
			tvDisclaimer.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					ActWebView.ViewURL(getActivity(), "http://myflightbook.com/logbook/public/CurrencyDisclaimer.aspx?naked=1");
				}
			});
		}
        
        Refresh(fNeedsRefresh);
        MFBMain.registerNotifyDataChange(this);
        MFBMain.registerNotifyResetAll(this);
	}
	
	void BindTable()
	{
        TableLayout tl = (TableLayout) getView().findViewById(R.id.tblCurrency);
		if (tl != null)
			tl.removeAllViews();
		LayoutInflater l = getActivity().getLayoutInflater();

		if (m_rgcsi == null)
			return;
		
		for (CurrencyStatusItem csi : m_rgcsi)
		{
			try {
				// TableRow tr = new TableRow(this);

				TableRow tr = (TableRow) l.inflate(R.layout.currencyrow, tl, false);
				TextView tvAttribute = (TextView) tr.findViewById(R.id.txtCsiAttribute);
				TextView tvValue = (TextView) tr.findViewById(R.id.txtCsiValue);
				TextView tvDiscrepancy = (TextView) tr.findViewById(R.id.txtCsiDiscrepancy);

				tvAttribute.setText(csi.Attribute);
				tvValue.setText(csi.Value);
				tvDiscrepancy.setText(csi.Discrepancy);
				if (csi.Discrepancy.length() == 0)
					tvDiscrepancy.setVisibility(View.GONE);

				tvAttribute.setTextColor(Color.BLACK);
				tvDiscrepancy.setTextColor(Color.BLACK);
				if (csi.Status.compareTo("NotCurrent") == 0)
					tvValue.setTextColor(Color.RED);
				else if (csi.Status.compareTo("GettingClose") == 0)
					tvValue.setTextColor(Color.BLUE);
				else
					tvValue.setTextColor(Color.argb(255, 0, 128, 0));

				tl.addView(tr, new TableLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
			}
			catch (NullPointerException ex) { // should never happen.
				ex.printStackTrace();
			}
		}
	}
	
    void Refresh(Boolean fForce)
    {
    	if (AuthToken.FIsValid() && (fForce || fNeedsRefresh || m_rgcsi == null))
    	{
    		SoapTask ts = new SoapTask(getActivity());
    		ts.execute();
    	}  
    	else
    		BindTable();
    }
    
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.currencymenu, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    case R.id.menuRefresh:
	    	Refresh(true);
	    	return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}
	
	public void onResume()
	{
		Refresh(false);
		super.onResume();
	}

	public void invalidate() {
		SetNeedsRefresh(true);
	}

}
