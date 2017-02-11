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
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import com.myflightbook.android.WebServices.CreateUserSvc;
import com.myflightbook.android.WebServices.MFBSoap;

import Model.MFBConstants;
import Model.MFBUtil;

public class ActNewUser extends Activity implements
        android.view.View.OnClickListener {
    private TextView txtEmail, txtEmail2, txtPass, txtPass2, txtFirst, txtLast,
            txtQ, txtA;
    private CheckBox ckOver14;

    private class SoapTask extends AsyncTask<String, Void, MFBSoap> {
        private Context m_Context = null;
        private ProgressDialog m_pd = null;
        private Object m_Result = null;
        MFBSoap m_Svc = null;

        SoapTask(Context c) {
            super();
            m_Context = c;
        }

        @Override
        protected MFBSoap doInBackground(String... params) {
            CreateUserSvc cus = new CreateUserSvc();
            m_Result = cus.FCreateUser(params[0], params[1], params[2], params[3], params[4], params[5]);
            return m_Svc = cus;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_Context, m_Context.getString(R.string.prgCreatingAccount));
        }

        protected void onPostExecute(MFBSoap svc) {
            if ((Boolean) m_Result) {
                Intent i = new Intent();
                setResult(RESULT_OK, i);
                finish();
            } else {
                MFBUtil.Alert(m_Context, getString(R.string.txtError), svc.getLastError());
            }

            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.newuser);
        Button b = (Button) findViewById(R.id.btnCreateUser);
        b.setOnClickListener(this);
        b = (Button) findViewById(R.id.btnCancel);
        b.setOnClickListener(this);
        b = (Button) findViewById(R.id.btnViewPrivacy);
        b.setOnClickListener(this);
        b = (Button) findViewById(R.id.btnViewTandC);
        b.setOnClickListener(this);

        txtEmail = (TextView) findViewById(R.id.txtEmail);
        txtEmail2 = (TextView) findViewById(R.id.txtEmail2);
        txtPass = (TextView) findViewById(R.id.txtPass);
        txtPass2 = (TextView) findViewById(R.id.txtPass2);
        txtFirst = (TextView) findViewById(R.id.txtFirstName);
        txtLast = (TextView) findViewById(R.id.txtLastName);
        txtQ = (TextView) findViewById(R.id.txtQuestion);
        txtA = (TextView) findViewById(R.id.txtAnswer);
        ckOver14 = (CheckBox) findViewById(R.id.ckOver14);

        final String[] rgSampleQuestions = getResources().getStringArray(R.array.defaultSecurityQuestions);
        Spinner spinner = (Spinner) findViewById(R.id.spnSampleQuestions);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, rgSampleQuestions);
        adapter.setDropDownViewResource(R.layout.samplequestion);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                TextView txtQ = (TextView) findViewById(R.id.txtQuestion);
                txtQ.setText(rgSampleQuestions[i]);
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                TextView txtQ = (TextView) findViewById(R.id.txtQuestion);
                txtQ.setText("");
            }
        });
    }

    public Boolean FIsValid() {
        if (txtEmail.getText().toString()
                .compareTo(txtEmail2.getText().toString()) != 0) {
            MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.errTypeEmailTwice));
            return false;
        }
        if (txtPass.getText().toString()
                .compareTo(txtPass2.getText().toString()) != 0) {
            MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.errTypePasswordTwice));
            return false;
        }
        if (txtQ.getText().length() == 0 || txtA.getText().length() == 0) {
            MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.errNeedQandA));
            return false;
        }
        if (!ckOver14.isChecked()) {
            MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.errMustBe14));
            return false;
        }

        return true;

    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnCreateUser:
                if (FIsValid()) {
                    SoapTask st = new SoapTask(this);
                    st.execute(txtEmail.getText().toString(), txtPass
                                    .getText().toString(), txtFirst.getText().toString(),
                            txtLast.getText().toString(),
                            txtQ.getText().toString(), txtA.getText().toString());
                }
                break;
            case R.id.btnCancel: {
                Intent i = new Intent();
                setResult(RESULT_CANCELED, i);
                finish();
            }
            break;
            case R.id.btnViewPrivacy: {
                Intent i = new Intent(v.getContext(), ActWebView.class);
                i.putExtra(MFBConstants.intentViewURL, MFBConstants.urlPrivacy);
                startActivityForResult(i, 0);
            }
            break;

            case R.id.btnViewTandC: {
                Intent i = new Intent(v.getContext(), ActWebView.class);
                i.putExtra(MFBConstants.intentViewURL, MFBConstants.urlTandC);
                startActivityForResult(i, 0);
            }
            break;
            default:
                break;
        }
    }
}
