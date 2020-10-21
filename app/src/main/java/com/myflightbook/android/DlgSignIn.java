/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2020 MyFlightbook, LLC

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
import com.myflightbook.android.WebServices.CustomPropertyTypesSvc;
import com.myflightbook.android.WebServices.MFBSoap;

import Model.Aircraft;
import Model.AuthResult;
import Model.CustomPropertyType;
import Model.MFBConstants;
import Model.MFBUtil;

class DlgSignIn extends Dialog implements android.view.View.OnClickListener {

    private static class RefreshCPTTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog m_pd = null;
        final boolean fAllowCache = true;
        CustomPropertyType[] m_rgcpt;
        final AsyncWeakContext<DlgSignIn> m_ctxt;

        RefreshCPTTask(Context c, DlgSignIn d) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, d);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            CustomPropertyTypesSvc cptSvc = new CustomPropertyTypesSvc();
            m_rgcpt = cptSvc.GetCustomPropertyTypes(AuthToken.m_szAuthToken, fAllowCache, m_ctxt.getContext());
            return m_rgcpt != null && m_rgcpt.length > 0;
        }

        protected void onPreExecute() {
            Context c = m_ctxt.getContext();
            if (c != null)
                m_pd = MFBUtil.ShowProgress(c, c.getString(R.string.prgCPT));
        }

        protected void onPostExecute(Boolean b) {
            DlgSignIn d = m_ctxt.getCallingActivity();
            if (d != null)
                d.dismiss();

            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }

    private static class AircraftTask extends AsyncTask<String, Void, MFBSoap> {
        private Object m_Result = null;
        private ProgressDialog m_pd = null;
        final AsyncWeakContext<DlgSignIn> m_ctxt;

        AircraftTask(Context c, DlgSignIn d) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, d);
        }

        @Override
        protected MFBSoap doInBackground(String... params) {
            AircraftSvc as = new AircraftSvc();
            m_Result = as.AircraftForUser(AuthToken.m_szAuthToken, m_ctxt.getContext());
            return as;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getContext(), m_ctxt.getContext().getString(R.string.prgAircraft));
        }

        protected void onPostExecute(MFBSoap svc) {
            Aircraft[] rgac = (Aircraft[]) m_Result;
            Context c = m_ctxt.getContext();
            DlgSignIn d = m_ctxt.getCallingActivity();
            if (c != null && (rgac == null || svc.getLastError().length() > 0)) {
                MFBUtil.Alert(c, c.getString(R.string.txtError), svc.getLastError());
                if (d != null)
                    d.dismiss();
            }

            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            RefreshCPTTask cptTask = new RefreshCPTTask(c, d);
            cptTask.execute();
        }
    }

    private static class SignInTask extends AsyncTask<String, Void, MFBSoap> {
        private ProgressDialog m_pd = null;
        AuthResult m_Result = null;
        final AuthToken m_Svc;
        final AsyncWeakContext<DlgSignIn> m_ctxt;

        SignInTask(Context c, DlgSignIn d, AuthToken at) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, d);
            m_Svc = at;
        }

        @Override
        protected MFBSoap doInBackground(String... params) {
            m_Result = m_Svc.Authorize(params[0], params[1], params[2], m_ctxt.getContext());
            return m_Svc;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getContext(), m_ctxt.getContext().getString(R.string.prgSigningIn));
        }

        protected void onPostExecute(MFBSoap svc) {
            Context c = m_ctxt.getContext();
            DlgSignIn d = m_ctxt.getCallingActivity();
            if (c == null)
                return;

            if (m_Result.authStatus == AuthResult.AuthStatus.TwoFactorCodeRequired) {
                d.findViewById(R.id.layout2FA).setVisibility(View.VISIBLE);
                d.findViewById(R.id.layoutCredentials).setVisibility(View.GONE);
            } else {
                if (m_Result.authStatus == AuthResult.AuthStatus.Success) {
                    MFBMain.invalidateAll();
                    // now download aircraft
                    AircraftTask act = new AircraftTask(c, d);
                    act.execute();
                } else  {
                    MFBUtil.Alert(c, c.getString(R.string.txtError), svc.getLastError());
                    if (d.findViewById(R.id.layout2FA).getVisibility() != View.VISIBLE)
                        d.dismiss();
                }
            }

            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }

    private final Context m_CallingContext;

    DlgSignIn(Context context) {
        super(context, R.style.MFBDialog);
        m_CallingContext = context;
        setContentView(R.layout.dlgsignin);
        setTitle("Please sign in to MyFlightbook");

        EditText txtUser = findViewById(R.id.editEmail);
        EditText txtPass = findViewById(R.id.editPass);

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

        EditText txtUser = findViewById(R.id.editEmail);
        EditText txtPass = findViewById(R.id.editPass);
        EditText txt2FA = findViewById(R.id.txt2FA);

        AuthToken at = new AuthToken();
        // make sure we don't use any existing credentials!!
        at.FlushCache();

        // Also flush any cached aircraft when we change users
        AircraftSvc ac = new AircraftSvc();
        ac.FlushCache();

        SignInTask st = new SignInTask(m_CallingContext == null ? this.getContext() : m_CallingContext, this, at);
        st.execute(txtUser.getText().toString(), txtPass.getText().toString(), txt2FA.getText().toString());
    }
}
