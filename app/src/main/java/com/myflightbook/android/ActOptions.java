/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2021 MyFlightbook, LLC

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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

public class ActOptions extends ActMFBForm implements android.view.View.OnClickListener, OnItemSelectedListener {

    public enum AltitudeUnits {Feet, Meters}
    public enum SpeedUnits {Knots, KmPerHour, MilesPerHour }

    public static AltitudeUnits altitudeUnits = AltitudeUnits.Feet;
    public static SpeedUnits speedUnits = SpeedUnits.Knots;
    private ActivityResultLauncher<String> mPermissionLauncher;
    private boolean fPendingAutodetect;
    private boolean fPendingRecord;

    private static class PackData extends AsyncTask<Void, Integer, String> {
        private ProgressDialog m_pd = null;
        private final AsyncWeakContext<ActOptions> m_ctxt;

        PackData(Context c, ActOptions opt) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, opt);
        }

        @Override
        protected String doInBackground(Void... params) {
            publishProgress(R.string.prgAircraft);
            // To be safe, update aircraft and properties.
            AircraftSvc as = new AircraftSvc();
            Aircraft[] rgac = as.AircraftForUser(AuthToken.m_szAuthToken, m_ctxt.getContext());

            publishProgress(R.string.prgCPT);
            CustomPropertyTypesSvc cptSvc = new CustomPropertyTypesSvc();
            CustomPropertyType[] rgcpt = cptSvc.GetCustomPropertyTypes(AuthToken.m_szAuthToken, false, m_ctxt.getContext());

            PackAndGo p = new PackAndGo(m_ctxt.getContext());

            publishProgress(R.string.prgCurrency);
            CurrencySvc cs = new CurrencySvc();
            CurrencyStatusItem[] rgcsi = cs.CurrencyForUser(AuthToken.m_szAuthToken, m_ctxt.getContext());
            if (cs.getLastError().length() > 0)
                return cs.getLastError();
            p.updateCurrency(rgcsi);

            publishProgress(R.string.prgTotals);
            TotalsSvc ts = new TotalsSvc();
            Totals[] rgti = ts.TotalsForUser(AuthToken.m_szAuthToken, new FlightQuery(), m_ctxt.getContext());
            if (ts.getLastError().length() > 0)
                return ts.getLastError();
            p.updateTotals(rgti);

            publishProgress(R.string.packAndGoInProgress);
            RecentFlightsSvc fs = new RecentFlightsSvc();
            LogbookEntry[] rgle = fs.RecentFlightsWithQueryAndOffset(AuthToken.m_szAuthToken, new FlightQuery(), 0, -1, m_ctxt.getContext());
            if (fs.getLastError().length() > 0)
                return fs.getLastError();
            p.updateFlights(rgle);

            publishProgress(R.string.prgVisitedAirports);
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

        protected void onProgressUpdate(Integer... progress) {
            m_pd.setMessage(m_ctxt.getContext().getString(progress[0]));
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

        mPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                result -> {
                    if (!result)
                        fPendingRecord = fPendingAutodetect = false;

                    ((CheckBox) findViewById(R.id.ckAutodetect)).setChecked(MFBLocation.fPrefAutoDetect = fPendingAutodetect);
                    ((CheckBox) findViewById(R.id.ckRecord)).setChecked(MFBLocation.fPrefRecordFlight = fPendingRecord);
                });

        return inflater.inflate(R.layout.options, container, false);
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

        boolean fHasGPS = MFBLocation.HasGPS(requireContext());
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireActivity(), R.layout.mfbsimpletextitem, rgAutoHobbs);
        sp.setAdapter(adapter);
        sp.setSelection(MFBLocation.fPrefAutoFillHobbs.ordinal());
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.string.lblAutoFillOptions);

        sp = (Spinner) findViewById(R.id.spnAutoTime);
        adapter = new ArrayAdapter<>(requireActivity(), R.layout.mfbsimpletextitem, rgAutoTotals);
        sp.setAdapter(adapter);
        sp.setSelection(MFBLocation.fPrefAutoFillTime.ordinal());
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.string.lblAutoFillOptions);

        sp = (Spinner) findViewById(R.id.spnTOSpeed);
        adapter = new ArrayAdapter<>(requireActivity(), R.layout.mfbsimpletextitem, MFBTakeoffSpeed.GetDisplaySpeeds().toArray(new String[0]));
        sp.setAdapter(adapter);
        sp.setSelection(MFBTakeoffSpeed.getTakeOffSpeedIndex());
        sp.setOnItemSelectedListener(this);

        sp = (Spinner) findViewById(R.id.spnNightDef);
        adapter = new ArrayAdapter<>(requireActivity(), R.layout.mfbsimpletextitem, new String[] {
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
        adapter = new ArrayAdapter<>(requireActivity(), R.layout.mfbsimpletextitem, new String[] {
                getString(R.string.lblNightModeAuto),
                getString(R.string.lblNightModeOff),
                getString(R.string.lblNightModeOn)
        });
        sp.setAdapter(adapter);
        sp.setSelection(MFBMain.NightModePref);
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.string.lblAutoFillOptions);

        sp = (Spinner) findViewById(R.id.spnNightLandingDef);
        adapter = new ArrayAdapter<>(requireActivity(), R.layout.mfbsimpletextitem, new String[] {
                getString(R.string.lblOptNightLandingsSunsetPlus1hour),
                getString(R.string.lblOptNightLandingsNight)
        });
        sp.setAdapter(adapter);
        sp.setSelection(MFBLocation.NightLandingPref.ordinal());
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.string.lblAutoFillOptions);

        sp = (Spinner) findViewById(R.id.spnFlightDetail);
        adapter = new ArrayAdapter<>(requireActivity(), R.layout.mfbsimpletextitem, new String[] {
                getString(R.string.lblFlightDetailLow), getString(R.string.lblFlightDetailMed), getString(R.string.lblFlightDetailHigh)
        });
        sp.setAdapter(adapter);
        sp.setSelection(ActRecentsWS.flightDetail.ordinal());
        sp.setOnItemSelectedListener(this);
        sp.setPromptId(R.id.lblShowFlightTimes);

        sp = (Spinner) findViewById(R.id.spnAltUnits);
        adapter = new ArrayAdapter<>(requireActivity(), R.layout.mfbsimpletextitem, new String[] {
                getString(R.string.lblOptUnitsFeet), getString(R.string.lblOptUnitsMeters)
        });
        sp.setAdapter(adapter);
        sp.setSelection(ActOptions.altitudeUnits.ordinal());
        sp.setOnItemSelectedListener(this);

        sp = (Spinner) findViewById(R.id.spnSpeedUnits);
        adapter = new ArrayAdapter<>(requireActivity(), R.layout.mfbsimpletextitem, new String[] {
                getString(R.string.lblOptUnitsKnots), getString(R.string.lblOptUnitsKPH), getString(R.string.lblOptUnitsMPH)
        });
        sp.setAdapter(adapter);
        sp.setSelection(ActOptions.speedUnits.ordinal());
        sp.setOnItemSelectedListener(this);

        t = (TextView) findViewById(R.id.txtCopyright);
        if (MFBConstants.fIsDebug) {
            String s = String.format("%s - DEBUG (%s)",
                    t.getText().toString(),
                    String.format(Locale.getDefault(), "%s %d %d", MFBConstants.szIP, MFBTakeoffSpeed.getLandingSpeed(), MFBTakeoffSpeed.getTakeOffspeed()));
            t.setText(s);
        }
    }

    public void onResume() {
        super.onResume();
        updateStatus();
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Spinner sp = (Spinner) parent;
        int i = sp.getSelectedItemPosition();
        int spid = sp.getId();
        if (spid == R.id.spnAutoHobbs)
            MFBLocation.fPrefAutoFillHobbs = MFBLocation.AutoFillOptions.values()[i];
        else if (spid == R.id.spnAutoTime)
            MFBLocation.fPrefAutoFillTime = MFBLocation.AutoFillOptions.values()[i];
        else if (spid == R.id.spnTOSpeed)
            MFBTakeoffSpeed.setTakeOffSpeedIndex(i);
        else if (spid == R.id.spnNightDef)
            MFBLocation.NightPref = MFBLocation.NightCriteria.values()[i];
        else if (spid == R.id.spnNightLandingDef)
            MFBLocation.NightLandingPref = MFBLocation.NightLandingCriteria.values()[i];
        else if (spid == R.id.spnFlightDetail)
            ActRecentsWS.flightDetail = ActRecentsWS.FlightDetail.values()[i];
        else if (spid == R.id.spnNightMode) {
            if (MFBMain.NightModePref != i) {
                MFBMain.NightModePref = i;
                AppCompatDelegate.setDefaultNightMode(MFBMain.NightModePref);
                if (getActivity() != null) {
                    getActivity().recreate();
                }
            }
        } else if (spid == R.id.spnAltUnits)
            altitudeUnits = AltitudeUnits.values()[i];
        else if (spid == R.id.spnSpeedUnits)
            speedUnits = SpeedUnits.values()[i];
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
        LogbookEntry leInProgress = MFBMain.getNewFlightListener().getInProgressFlight(requireActivity());
        if (leInProgress != null && leInProgress.idLocalDB > 0) {
            for (LogbookEntry le : rgLeNew)
                if (le.idLocalDB != leInProgress.idLocalDB) {
                    Log.e(MFBConstants.LOG_TAG, String.format("FOUND ORPHANED FLIGHT: %d", le.idLocalDB));
                    le.idFlight = LogbookEntry.ID_QUEUED_FLIGHT_UNSUBMITTED;    // put it into queued flights for review.
                    le.ToDB();
                    RecentFlightsSvc.ClearCachedFlights();
                    fOrphansFound = true;
                }
        }

        // Now look for orphaned flight image files.  Start with the known flight images
        LogbookEntry[] rgLeAll = LogbookEntry.mergeFlightLists(rgLeNew, LogbookEntry.getQueuedAndUnsubmittedFlights());
        ArrayList<String> alImages = new ArrayList<>();
        for (LogbookEntry le : rgLeAll) {
            le.getImagesForFlight();
            for (MFBImageInfo mfbii : le.rgFlightImages)
                alImages.add(mfbii.getImageFile());
        }

        // Now delete the flight images that are not in our list
        MFBImageInfo.DeleteOrphansNotInList(PictureDestination.FlightImage, alImages, requireActivity());

        // Clean up any orphaned aircraft images
        // We can delete ALL aircraft images - if they weren't submitted, they aren't going to be picked up.

        // First delete all of the ones that haven't been saved to the server
        MFBImageInfo[] rgMfbiiAircraft = MFBImageInfo.getAllAircraftImages();
        for (MFBImageInfo mfbii : rgMfbiiAircraft)
            if (!mfbii.IsOnServer())
                mfbii.deleteFromDB();

        // now delete any remaining aircraft images that might be in our files.
        MFBImageInfo.DeleteOrphansNotInList(PictureDestination.AircraftImage, new ArrayList<>(), requireActivity());

        MFBUtil.Alert(this, getString(R.string.lblCleanup), getString(fOrphansFound ? R.string.errCleanupOrphansFound : R.string.txtCleanupComplete));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.optionsmenu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        int id = item.getItemId();
        if (id == R.id.menuContact)
            ContactUs();
        else if (id == R.id.menuFacebook)
            ViewFacebook();
        else if (id == R.id.menuTwitter)
            ViewTwitter();
        else
            return super.onOptionsItemSelected(item);
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

    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnSignIn) {
            DlgSignIn d = new DlgSignIn(getActivity());
            d.setOnDismissListener((dialog) -> updateStatus());
            d.show();
        } else if (id == R.id.btnSignOut) {
            AuthToken.m_szAuthToken = AuthToken.m_szEmail = AuthToken.m_szPass = "";
            new AuthToken().FlushCache();
            new PackAndGo(getContext()).clearPackedData();
            MFBMain.invalidateAll();
            updateStatus();
        } else if (id == R.id.btnCreateNewAccount) {
            startActivity(new Intent(v.getContext(), ActNewUser.class));
        } else if (id == R.id.ckAutodetect || id == R.id.ckRecord) {
            fPendingAutodetect = (id == R.id.ckAutodetect) ? ((CheckBox) v).isChecked() : ((CheckBox) findViewById(R.id.ckAutodetect)).isChecked();
            fPendingRecord = (id == R.id.ckRecord) ? ((CheckBox) v).isChecked() :  ((CheckBox) findViewById(R.id.ckRecord)).isChecked();
            mPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (id == R.id.ckRecordHighRes)
            MFBLocation.fPrefRecordFlightHighRes = ((CheckBox) v).isChecked();
        else if (id == R.id.ckHeliports)
            Airport.fPrefIncludeHeliports = ((CheckBox) v).isChecked();
        else if (id == R.id.ckUseHHMM)
            DecimalEdit.DefaultHHMM = ((CheckBox) v).isChecked();
        else if (id == R.id.ckUseLocalTime)
            DlgDatePicker.fUseLocalTime = ((CheckBox) v).isChecked();
        else if (id == R.id.ckRoundNearestTenth)
            MFBLocation.fPrefRoundNearestTenth = ((CheckBox) v).isChecked();
        else if (id == R.id.ckShowFlightImages)
            ActRecentsWS.fShowFlightImages = ((CheckBox) v).isChecked();
        else if (id == R.id.btnContact)
            this.ContactUs();
        else if (id == R.id.btnFacebook)
            this.ViewFacebook();
        else if (id == R.id.btnTwitter)
            this.ViewTwitter();
        else if (id == R.id.btnCleanUp)
            CleanUp();
        else if (id == R.id.btnSupport)
            ViewPreferences(MFBConstants.AuthRedirWithParams("d=donate", getContext(), false));
        else if (id == R.id.btnAdditionalOptions)
            ViewPreferences(MFBConstants.AuthRedirWithParams("d=profile", getContext()));
        else if (id == R.id.btnFAQ)
            ActWebView.ViewURL(getActivity(), MFBConstants.urlFAQ);
        else if (id == R.id.btnPackAndGo)
            new PackData(getContext(), this).execute();
    }
}
