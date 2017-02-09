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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.MFBSoap;

import Model.Aircraft;
import Model.MFBConstants;
import Model.MFBUtil;

class DlgSignIn extends Dialog implements android.view.View.OnClickListener {

    private class AircraftTask extends AsyncTask<String, Void, MFBSoap> {
        private Object m_Result = null;
        private Context m_Context = null;
        private ProgressDialog m_pd = null;

        AircraftTask(Context c) {
            super();
            m_Context = c;
        }

        @Override
        protected MFBSoap doInBackground(String... params) {
            AircraftSvc as = new AircraftSvc();
            m_Result = as.AircraftForUser(AuthToken.m_szAuthToken);
            return as;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_Context, m_Context.getString(R.string.prgAircraft));
        }

        protected void onPostExecute(MFBSoap svc) {
            Aircraft[] rgac = (Aircraft[]) m_Result;
            if (rgac == null || svc.getLastError().length() > 0) {
                MFBUtil.Alert(m_Context, m_Context.getString(R.string.txtError), svc.getLastError());
            }

            dismiss();
            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }

    private class SoapTask extends AsyncTask<String, Void, MFBSoap> {
        private Context m_Context = null;
        private ProgressDialog m_pd = null;
        Object m_Result = null;
        AuthToken m_Svc = null;

        SoapTask(Context c, AuthToken at) {
            super();
            m_Context = c;
            m_Svc = at;
        }

        @Override
        protected MFBSoap doInBackground(String... params) {
            m_Result = m_Svc.Authorize(params[0], params[1]);
            return m_Svc;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_Context, m_Context.getString(R.string.prgSigningIn));
        }

        protected void onPostExecute(MFBSoap svc) {
            if (((String) m_Result).length() == 0) {
                MFBUtil.Alert(getContext(), getContext().getString(R.string.txtError), svc.getLastError());
            } else {
                MFBMain.invalidateAll();
                // now download aircraft
                AircraftTask act = new AircraftTask(m_Context);
                act.execute();
                dismiss();
            }

            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }

    private Context m_CallingContext = null;

    DlgSignIn(Context context) {
        super(context, R.style.MFBDialog);
        m_CallingContext = context;
        setContentView(R.layout.dlgsignin);
        setTitle("Please sign in to MyFlightbook");

        EditText txtUser = (EditText) findViewById(R.id.editEmail);
        EditText txtPass = (EditText) findViewById(R.id.editPass);

        if (AuthToken.m_szEmail != null)
            txtUser.setText(AuthToken.m_szEmail);
        if (AuthToken.m_szPass != null)
            txtPass.setText(AuthToken.m_szPass);
    }

    public void onCreate(Bundle savedInstanceState) {
        findViewById(R.id.btnSubmit).setOnClickListener(this);
        findViewById(R.id.btnCancel).setOnClickListener(this);
    }

    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.btnCancel) {
            dismiss();
            return;
        }

        EditText txtUser = (EditText) findViewById(R.id.editEmail);
        EditText txtPass = (EditText) findViewById(R.id.editPass);

        AuthToken at = new AuthToken();
        // make sure we don't use any existing credentials!!
        at.FlushCache();

        // Also flush any cached aircraft when we change users
        AircraftSvc ac = new AircraftSvc();
        ac.FlushCache();

        SoapTask st = new SoapTask(m_CallingContext == null ? this.getContext() : m_CallingContext, at);
        st.execute(txtUser.getText().toString(), txtPass.getText().toString());
    }
}
