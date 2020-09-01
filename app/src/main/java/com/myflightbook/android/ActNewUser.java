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
import android.widget.Spinner;
import android.widget.TextView;

import com.myflightbook.android.WebServices.CreateUserSvc;
import com.myflightbook.android.WebServices.MFBSoap;

import java.util.Locale;

import Model.MFBConstants;
import Model.MFBUtil;
import androidx.appcompat.app.AppCompatActivity;

public class ActNewUser extends AppCompatActivity implements
        android.view.View.OnClickListener {
    private TextView txtEmail, txtEmail2, txtPass, txtPass2, txtFirst, txtLast,
            txtQ, txtA;

    private static class AddUserTask extends AsyncTask<String, Void, MFBSoap> {
        private ProgressDialog m_pd = null;
        private Object m_Result = null;
        private final AsyncWeakContext<ActNewUser> m_ctxt;

        AddUserTask(Context c, ActNewUser anu) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, anu);
        }

        @Override
        protected MFBSoap doInBackground(String... params) {
            CreateUserSvc cus = new CreateUserSvc();
            m_Result = cus.FCreateUser(params[0], params[1], params[2], params[3], params[4], params[5], m_ctxt.getContext());
            return cus;
        }

        protected void onPreExecute() {
            Context c = m_ctxt.getContext();
            m_pd = MFBUtil.ShowProgress(c, c.getString(R.string.prgCreatingAccount));
        }

        protected void onPostExecute(MFBSoap svc) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            ActNewUser anu = m_ctxt.getCallingActivity();
            if (anu == null)
                return;

            if ((Boolean) m_Result) {
                Intent i = new Intent();
                anu.setResult(RESULT_OK, i);
                anu.finish();
            } else {
                MFBUtil.Alert(anu, anu.getString(R.string.txtError), svc.getLastError());
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.newuser);
        Button b = findViewById(R.id.btnCreateUser);
        b.setOnClickListener(this);
        b = findViewById(R.id.btnCancel);
        b.setOnClickListener(this);
        b = findViewById(R.id.btnViewPrivacy);
        b.setOnClickListener(this);
        b = findViewById(R.id.btnViewTandC);
        b.setOnClickListener(this);

        txtEmail = findViewById(R.id.txtEmail);
        txtEmail2 = findViewById(R.id.txtEmail2);
        txtPass = findViewById(R.id.txtPass);
        txtPass2 = findViewById(R.id.txtPass2);
        txtFirst = findViewById(R.id.txtFirstName);
        txtLast = findViewById(R.id.txtLastName);
        txtQ = findViewById(R.id.txtQuestion);
        txtA = findViewById(R.id.txtAnswer);

        final String[] rgSampleQuestions = getResources().getStringArray(R.array.defaultSecurityQuestions);
        Spinner spinner = findViewById(R.id.spnSampleQuestions);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, rgSampleQuestions);
        adapter.setDropDownViewResource(R.layout.samplequestion);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                TextView txtQ = findViewById(R.id.txtQuestion);
                txtQ.setText(rgSampleQuestions[i]);
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                TextView txtQ = findViewById(R.id.txtQuestion);
                txtQ.setText("");
            }
        });
    }

    private Boolean FIsValid() {
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
        return true;

    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnCreateUser:
                if (FIsValid()) {
                    AddUserTask st = new AddUserTask(this, this);
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
                i.putExtra(MFBConstants.intentViewURL, String.format(Locale.US, MFBConstants.urlPrivacy, MFBConstants.szIP, MFBConstants.NightParam(this)));
                startActivityForResult(i, 0);
            }
            break;

            case R.id.btnViewTandC: {
                Intent i = new Intent(v.getContext(), ActWebView.class);
                i.putExtra(MFBConstants.intentViewURL, String.format(Locale.US, MFBConstants.urlTandC, MFBConstants.szIP, MFBConstants.NightParam(this)));
                startActivityForResult(i, 0);
            }
            break;
            default:
                break;
        }
    }
}
