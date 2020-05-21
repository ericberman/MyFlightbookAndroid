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
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatDelegate;
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

import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.CurrencySvc;
import com.myflightbook.android.WebServices.CustomPropertyTypesSvc;
import com.myflightbook.android.WebServices.RecentFlightsSvc;
import com.myflightbook.android.WebServices.TotalsSvc;
import com.myflightbook.android.WebServices.VisitedAirportSvc;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import Model.Aircraft;
import Model.Airport;
import Model.CurrencyStatusItem;
import Model.CustomPropertyType;
import Model.DecimalEdit;
import Model.FlightQuery;
import Model.LogbookEntry;
import Model.MFBConstants;
import Model.MFBImageInfo;
import Model.MFBImageInfo.PictureDestination;
import Model.MFBLocation;
import Model.MFBTakeoffSpeed;
import Model.MFBUtil;
import Model.PackAndGo;
import Model.Totals;
import Model.VisitedAirport;

public class ActOptions extends ActMFBForm implements android.view.View.OnClickListener, OnItemSelectedListener {

    private static class PackData extends AsyncTask<Void, Void, String> {
        private ProgressDialog m_pd = null;
        private final AsyncWeakContext<ActOptions> m_ctxt;

        PackData(Context c, ActOptions opt) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, opt);
        }

        @Override
        protected String doInBackground(Void... params) {
            // To be safe, update aircraft and properties.
            AircraftSvc as = new AircraftSvc();
            Aircraft[] rgac = as.AircraftForUser(AuthToken.m_szAuthToken, m_ctxt.getContext());

            CustomPropertyTypesSvc cptSvc = new CustomPropertyTypesSvc();
            CustomPropertyType[] rgcpt = cptSvc.GetCustomPropertyTypes(AuthToken.m_szAuthToken, false, m_ctxt.getContext());

            PackAndGo p = new PackAndGo(m_ctxt.getContext());

            CurrencySvc cs = new CurrencySvc();
            CurrencyStatusItem[] rgcsi = cs.CurrencyForUser(AuthToken.m_szAuthToken, m_ctxt.getContext());
            if (cs.getLastError().length() > 0)
                return cs.getLastError();
            p.updateCurrency(rgcsi);

            TotalsSvc ts = new TotalsSvc();
            Totals[] rgti = ts.TotalsForUser(AuthToken.m_szAuthToken, new FlightQuery(), m_ctxt.getContext());
            if (ts.getLastError().length() > 0)
                return ts.getLastError();
            p.updateTotals(rgti);

            RecentFlightsSvc fs = new RecentFlightsSvc();
            LogbookEntry[] rgle = fs.RecentFlightsWithQueryAndOffset(AuthToken.m_szAuthToken, new FlightQuery(), 0, -1, m_ctxt.getContext());
            if (fs.getLastError().length() > 0)
                return fs.getLastError();
            p.updateFlights(rgle);

            VisitedAirportSvc vs = new VisitedAirportSvc();
            VisitedAirport[] rgva = vs.VisitedAirportsForUser(AuthToken.m_szAuthToken, m_ctxt.getContext());
            if (vs.getLastError().length() > 0)
                return vs.getLastError();
            p.updateAirports(rgva);

            p.setLastPackDate(new Date());

            return "";
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getContext(), m_ctxt.getContext().getString(R.string.packAndGoInProgress));
        }

        protected void onPostExecute(String szErr) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            if (szErr.length() > 0) {
                MFBUtil.Alert(m_ctxt.getContext(), m_ctxt.getContext().getString(R.string.txtError), szErr);
            } else {
                ActOptions opt = m_ctxt.getCallingActivity();
                opt.updateStatus();
            }
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.options, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
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
        AddListener(R.id.btnPackAndGo);

        boolean fHasGPS = MFBLocation.HasGPS(Objects.requireNonNull(getContext()));
        if (!fHasGPS)
            MFBLocation.fPrefAutoDetect = MFBLocation.fPrefRecordFlight = MFBLocation.fPrefRecordFlightHighRes = false;

        CheckBox ck = (CheckBox) findViewById(R.id.ckAutodetect);
        ck.setOnClickListener(this);
        ck.setChecked(MFBLocation.fPrefAutoDetect);
        ck.setEnabled(fHasGPS);

        ck = (CheckBox) findViewById(R.id.ckRecord);
        ck.setOnClickListener(this);
        ck.setChecked(MFBLocation.fPrefRecordFlight);
        ck.setEnabled(fHasGPS);

        ck = (CheckBox) findViewById(R.id.ckRecordHighRes);
        ck.setOnClickListener(this);
        ck.setChecked(MFBLocation.fPrefRecordFlightHighRes);
        ck.setEnabled(fHasGPS);

        ck = (CheckBox) findViewById(R.id.ckHeliports);
        ck.setOnClickListener(this);
        ck.setChecked(Airport.fPrefIncludeHeliports);
        ck.setEnabled(fHasGPS);

        ck = (CheckBox) findViewById(R.id.ckUseHHMM);
        ck.setOnClickListener(this);
        ck.setChecked(DecimalEdit.DefaultHHMM);

        ck = (CheckBox) findViewById(R.id.ckUseLocalTime);
        ck.setOnClickListener(this);
        ck.setChecked(DlgDatePicker.fUseLocalTime);

        ck = (CheckBox) findViewById(R.id.ckRoundNearestTenth);
        ck.setOnClickListener(this);
        ck.setChecked(MFBLocation.fPrefRoundNearestTenth);

        ck = (CheckBox) findViewById(R.id.ckShowFlightImages);
        ck.setOnClickListener(this);
        ck.setChecked(ActRecentsWS.fShowFlightImages);

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
                        getString(R.string.autoHobbs),
                        getString(R.string.autoBlock),
                        getString(R.string.autoFlightStartEngineEnd)
                };

        Spinner sp = (Spinner) findViewById(R.id.spnAutoHobbs);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(Objects.requireNonNull(getActivity()), R.layout.mfbsimpletextitem, rgAutoHobbs);
        sp.setAdapter(adapter);
        sp.setSelection(MFBLocation.fPrefAutoFillHobbs.ordinal());
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.string.lblAutoFillOptions);

        sp = (Spinner) findViewById(R.id.spnAutoTime);
        adapter = new ArrayAdapter<>(getActivity(), R.layout.mfbsimpletextitem, rgAutoTotals);
        sp.setAdapter(adapter);
        sp.setSelection(MFBLocation.fPrefAutoFillTime.ordinal());
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.string.lblAutoFillOptions);

        sp = (Spinner) findViewById(R.id.spnTOSpeed);
        adapter = new ArrayAdapter<>(getActivity(), R.layout.mfbsimpletextitem, MFBTakeoffSpeed.GetDisplaySpeeds().toArray(new String[0]));
        sp.setAdapter(adapter);
        sp.setSelection(MFBTakeoffSpeed.getTakeOffSpeedIndex());
        sp.setOnItemSelectedListener(this);

        sp = (Spinner) findViewById(R.id.spnNightDef);
        adapter = new ArrayAdapter<>(getActivity(), R.layout.mfbsimpletextitem, new String[] {
                getString(R.string.lblOptNightDefinitionCivilTwilight),
                getString(R.string.lblOptNightDefinitionSunset),
                getString(R.string.lblOptNightDefinitionSunsetPlus15),
                getString(R.string.lblOptNightDefinitionSunsetPlus30),
                getString(R.string.lblOptNightDefinitionSunsetPlus60)
        });
        sp.setAdapter(adapter);
        sp.setSelection(MFBLocation.NightPref.ordinal());
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.string.lblAutoFillOptions);

        sp = (Spinner) findViewById(R.id.spnNightMode);
        adapter = new ArrayAdapter<>(getActivity(), R.layout.mfbsimpletextitem, new String[] {
                getString(R.string.lblNightModeAuto),
                getString(R.string.lblNightModeOff),
                getString(R.string.lblNightModeOn)
        });
        sp.setAdapter(adapter);
        sp.setSelection(MFBMain.NightModePref);
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.string.lblAutoFillOptions);

        sp = (Spinner) findViewById(R.id.spnNightLandingDef);
        adapter = new ArrayAdapter<>(getActivity(), R.layout.mfbsimpletextitem, new String[] {
                getString(R.string.lblOptNightLandingsSunsetPlus1hour),
                getString(R.string.lblOptNightLandingsNight)
        });
        sp.setAdapter(adapter);
        sp.setSelection(MFBLocation.NightLandingPref.ordinal());
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.string.lblAutoFillOptions);

        sp = (Spinner) findViewById(R.id.spnFlightDetail);
        adapter = new ArrayAdapter<>(getActivity(), R.layout.mfbsimpletextitem, new String[] {
                getString(R.string.lblFlightDetailLow), getString(R.string.lblFlightDetailMed), getString(R.string.lblFlightDetailHigh)
        });
        sp.setAdapter(adapter);
        sp.setSelection(ActRecentsWS.flightDetail.ordinal());
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.id.lblShowFlightTimes);

        TextView t = (TextView) findViewById(R.id.txtCopyright);
        if (MFBConstants.fIsDebug) {
            String s = String.format("%s - DEBUG (%s)",
                    t.getText().toString(),
                    String.format(Locale.getDefault(), "%s %d %d", MFBConstants.szIP, MFBTakeoffSpeed.getLandingSpeed(), MFBTakeoffSpeed.getTakeOffspeed()));
            t.setText(s);
        }
    }

    private void updateStatus() {
        // refresh sign-in status
        TextView t = (TextView) findViewById(R.id.txtSignInStatus);
        View bSignIn = findViewById(R.id.btnSignIn);
        View bSignOut = findViewById(R.id.btnSignOut);
        View bCreateAccount = findViewById(R.id.btnCreateNewAccount);
        View lblWhyAccount = findViewById(R.id.lblWhyAccount);
        if (AuthToken.FIsValid()) {
            t.setText(String.format(this.getString(R.string.statusSignedIn), AuthToken.m_szEmail));
            bSignIn.setVisibility(View.GONE);
            bSignOut.setVisibility(View.VISIBLE);
            bCreateAccount.setVisibility(View.GONE);
            lblWhyAccount.setVisibility(View.GONE);

            findViewById(R.id.headerPackAndGo).setVisibility(View.VISIBLE);
            findViewById(R.id.sectPackAndGo).setVisibility(View.VISIBLE);
        }
        else {
            t.setText(this.getString(R.string.statusNotSignedIn));
            bSignIn.setVisibility(View.VISIBLE);
            bSignOut.setVisibility(View.GONE);
            bCreateAccount.setVisibility(View.VISIBLE);
            lblWhyAccount.setVisibility(View.VISIBLE);
            findViewById(R.id.headerPackAndGo).setVisibility(View.GONE);
            findViewById(R.id.sectPackAndGo).setVisibility(View.GONE);
        }

        findViewById(R.id.btnSignOut).setVisibility(AuthToken.FIsValid() ? View.VISIBLE : View.GONE);

        t = (TextView) findViewById(R.id.lblLastPacked);
        PackAndGo p = new PackAndGo(getContext());
        Date dtLast = p.getLastPackDate();
        t.setText((dtLast == null) ? getString(R.string.packAndGoStatusNone) : String.format(Locale.getDefault(), getString(R.string.packAndGoStatusOK), DateFormat.getDateTimeInstance().format(dtLast)));
    }

    public void onResume() {
        super.onResume();
        updateStatus();
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Spinner sp = (Spinner) parent;
        int i = sp.getSelectedItemPosition();
        switch (sp.getId()) {
            case R.id.spnAutoHobbs:
                MFBLocation.fPrefAutoFillHobbs = MFBLocation.AutoFillOptions.values()[i];
                break;
            case R.id.spnAutoTime:
                MFBLocation.fPrefAutoFillTime = MFBLocation.AutoFillOptions.values()[i];
                break;
            case R.id.spnTOSpeed:
                MFBTakeoffSpeed.setTakeOffSpeedIndex(i);
                break;
            case R.id.spnNightDef:
                MFBLocation.NightPref = MFBLocation.NightCriteria.values()[i];
                break;
            case R.id.spnNightLandingDef:
                MFBLocation.NightLandingPref = MFBLocation.NightLandingCriteria.values()[i];
                break;
            case R.id.spnFlightDetail:
                ActRecentsWS.flightDetail = ActRecentsWS.FlightDetail.values()[i];
                break;
            case R.id.spnNightMode:
                if (MFBMain.NightModePref != i) {
                    MFBMain.NightModePref = i;
                    AppCompatDelegate.setDefaultNightMode(MFBMain.NightModePref);
                    if (getActivity() != null) {
                        getActivity().recreate();
                    }
                }
                break;
            default:
                break;
        }
    }

    private void ContactUs() {
        ActWebView.ViewURL(getActivity(), String.format(MFBConstants.urlContact, MFBConstants.szIP, AuthToken.m_szEmail, "Comment from Android user", MFBConstants.NightParam(getContext())));
    }

    private void ViewFacebook() {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(MFBConstants.urlFacebook));
        startActivity(i);
    }

    private void ViewTwitter() {
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(MFBConstants.urlTwitter));
        startActivity(i);
    }

    private void CleanUp() {
        boolean fOrphansFound = false;

        // Clean up any:
        //  (a) orphaned new flights (shouldn't happen, but could)
        //  (b) any flight images that are not associated with pending or new flights
        //  (c) any aircraft images that have not yet been posted

        // first make sure we're only working on one new flight at a time.
        LogbookEntry[] rgLeNew = LogbookEntry.getNewFlights();
        if (ActNewFlight.lastNewFlightID > 0) {
            for (LogbookEntry le : rgLeNew)
                if (le.idLocalDB != ActNewFlight.lastNewFlightID) {
                    Log.e(MFBConstants.LOG_TAG, String.format("DELETING FOUND ORPHANED FLIGHT: %d", le.idLocalDB));
                    le.idFlight = LogbookEntry.ID_PENDING_FLIGHT;
                    le.ToDB();
                    RecentFlightsSvc.ClearCachedFlights();
                    fOrphansFound = true;
                }
        }

        // Now look for orphaned flight image files.  Start with the known flight images
        LogbookEntry[] rgLeAll = LogbookEntry.mergeFlightLists(rgLeNew, LogbookEntry.getQueuedAndPendingFlights());
        ArrayList<String> alImages = new ArrayList<>();
        for (LogbookEntry le : rgLeAll) {
            le.getImagesForFlight();
            for (MFBImageInfo mfbii : le.rgFlightImages)
                alImages.add(mfbii.getImageFile());
        }

        // Now delete the flight images that are not in our list
        MFBImageInfo.DeleteOrphansNotInList(PictureDestination.FlightImage, alImages, Objects.requireNonNull(getActivity()));

        // Clean up any orphaned aircraft images
        // We can delete ALL aircraft images - if they weren't submitted, they aren't going to be picked up.

        // First delete all of the ones that haven't been saved to the server
        MFBImageInfo[] rgMfbiiAircraft = MFBImageInfo.getAllAircraftImages();
        for (MFBImageInfo mfbii : rgMfbiiAircraft)
            if (!mfbii.IsOnServer())
                mfbii.deleteFromDB();

        // now delete any remaining aircraft images that might be in our files.
        MFBImageInfo.DeleteOrphansNotInList(PictureDestination.AircraftImage, new ArrayList<>(), getActivity());

        //noinspection ConstantConditions
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

    private void ViewPreferences(String szURL) {
        if (!AuthToken.FIsValid()) {
            MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.statusNotSignedIn));
            return;
        }
        ActWebView.ViewURL(getActivity(), szURL);
    }

    private final int PERMISSION_REQUEST_AUTODETECT = 50382;
    private final int PERMISSION_REQUEST_RECORD = 58325;

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_AUTODETECT:
                if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    CheckBox c = (CheckBox) findViewById(R.id.ckAutodetect);
                    c.setChecked(MFBLocation.fPrefAutoDetect = true);
                }
                return;
            case PERMISSION_REQUEST_RECORD:
                if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    CheckBox c = (CheckBox) findViewById(R.id.ckRecord);
                    c.setChecked(MFBLocation.fPrefRecordFlight = true);
                }
                return;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private Boolean checkGPSPermissions(int req) {
        if (ContextCompat.checkSelfPermission(Objects.requireNonNull(getActivity()), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            return true;

        // Should we show an explanation?
        requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, req);
        return false;
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnSignIn:
                DlgSignIn d = new DlgSignIn(getActivity());
                d.setOnDismissListener((dialog) -> updateStatus());
                d.show();
                break;
            case R.id.btnSignOut:
                AuthToken.m_szAuthToken = AuthToken.m_szEmail = AuthToken.m_szPass = "";
                new AuthToken().FlushCache();
                new PackAndGo(getContext()).clearPackedData();
                MFBMain.invalidateAll();
                updateStatus();
                break;
            case R.id.btnCreateNewAccount: {
                Intent i = new Intent(v.getContext(), ActNewUser.class);
                startActivityForResult(i, 0);
            }
            break;
            case R.id.ckAutodetect: {
                CheckBox ck = (CheckBox) v;
                Boolean newState = ck.isChecked();
                if (!newState || checkGPSPermissions(PERMISSION_REQUEST_AUTODETECT))
                    MFBLocation.fPrefAutoDetect = newState;
                else
                    ck.setChecked(MFBLocation.fPrefAutoDetect = false);
            }
            break;
            case R.id.ckRecord: {
                CheckBox ck = (CheckBox) v;
                Boolean newState = ck.isChecked();
                if (!newState || checkGPSPermissions(PERMISSION_REQUEST_RECORD))
                    MFBLocation.fPrefRecordFlight = newState;
                else
                    ck.setChecked(MFBLocation.fPrefRecordFlight = false);
            }
            break;
            case R.id.ckRecordHighRes:
                MFBLocation.fPrefRecordFlightHighRes = ((CheckBox) v).isChecked();
                break;
            case R.id.ckHeliports:
                Airport.fPrefIncludeHeliports = ((CheckBox) v).isChecked();
                break;
            case R.id.ckUseHHMM:
                DecimalEdit.DefaultHHMM = ((CheckBox) v).isChecked();
                break;
            case R.id.ckUseLocalTime:
                DlgDatePicker.fUseLocalTime = ((CheckBox) v).isChecked();
                break;
            case R.id.ckRoundNearestTenth:
                MFBLocation.fPrefRoundNearestTenth = ((CheckBox) v).isChecked();
                break;
            case R.id.ckShowFlightImages:
                ActRecentsWS.fShowFlightImages = ((CheckBox) v).isChecked();
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
                ViewPreferences(MFBConstants.AuthRedirWithParams("d=donate", getContext(), false));
                break;
            case R.id.btnAdditionalOptions:
                ViewPreferences(MFBConstants.AuthRedirWithParams("d=profile", getContext()));
                break;
            case R.id.btnFAQ:
                ActWebView.ViewURL(getActivity(), MFBConstants.urlFAQ);
                break;
            case R.id.btnPackAndGo:
                new PackData(getContext(), this).execute();
                break;
            default:
                break;
        }
    }
}
