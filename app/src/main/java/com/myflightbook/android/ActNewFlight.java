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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AircraftSvc;
import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.CommitFlightSvc;
import com.myflightbook.android.WebServices.CustomPropertyTypesSvc;
import com.myflightbook.android.WebServices.DeleteFlightSvc;
import com.myflightbook.android.WebServices.FlightPropertiesSvc;
import com.myflightbook.android.WebServices.MFBSoap;
import com.myflightbook.android.WebServices.RecentFlightsSvc;

import junit.framework.Assert;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import Model.Aircraft;
import Model.Airport;
import Model.CustomPropertyType;
import Model.DecimalEdit;
import Model.DecimalEdit.CrossFillDelegate;
import Model.DecimalEdit.EditMode;
import Model.FlightProperty;
import Model.GPSSim;
import Model.LatLong;
import Model.LogbookEntry;
import Model.MFBConstants;
import Model.MFBFlightListener;
import Model.MFBImageInfo;
import Model.MFBImageInfo.PictureDestination;
import Model.MFBLocation;
import Model.MFBUtil;
import Model.PostingOptions;
import Model.SunriseSunsetTimes;

public class ActNewFlight extends ActMFBForm implements android.view.View.OnClickListener, MFBFlightListener.ListenerFragmentDelegate,
        DlgDatePicker.DateTimeUpdate, PropertyEdit.PropertyListener, ActMFBForm.GallerySource, CrossFillDelegate, MFBMain.Invalidatable {

    private Aircraft[] m_rgac = null;
    private LogbookEntry m_le = null;
    private PostingOptions m_po = new PostingOptions();

    public final static String PROPSFORFLIGHTID = "com.myflightbook.android.FlightPropsID";
    public final static String PROPSFORFLIGHTEXISTINGID = "com.myflightbook.android.FlightPropsIDExisting";
    public final static String PROPSFORFLIGHTCROSSFILLVALUE = "com.myflightbook.android.FlightPropsXFill";
    public final static String TACHFORCROSSFILLVALUE = "com.myflightbook.android.TachStartXFill";
    private static final int EDIT_PROPERTIES_ACTIVITY_REQUEST_CODE = 48329;

    // current state of pause/play and accumulated night
    public static Boolean fPaused = false;
    private static long dtPauseTime = 0;
    private static long dtTimeOfLastPause = 0;
    public static double accumulatedNight = 0.0;

    // pause/play state
    static private final String m_KeysIsPaused = "flightIsPaused";
    static private final String m_KeysPausedTime = "totalPauseTime";
    static private final String m_KeysTimeOfLastPause = "timeOfLastPause";
    static private final String m_KeysAccumulatedNight = "accumulatedNight";

    // Expand state of "in the cockpit"
    static private final String m_KeyShowInCockpit = "inTheCockpit";

    public static long lastNewFlightID = LogbookEntry.ID_NEW_FLIGHT;

    private TextView txtQuality, txtStatus, txtSpeed, txtAltitude, txtSunrise, txtSunset, txtLatitude, txtLongitude;
    private ImageView imgRecording;

    private Handler m_HandlerUpdateTimer = null;
    private Runnable m_UpdateElapsedTimeTask = null;

    @SuppressLint("StaticFieldLeak")
    private class UpdateAndViewPropsTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog m_pd = null;
        FlightProperty[] rgProps = null;
        Context c = null;

        @Override
        protected Boolean doInBackground(Void... params) {
            if (c == null)
                return false;

            FlightPropertiesSvc fpSvc = new FlightPropertiesSvc();
            rgProps = fpSvc.PropertiesForFlight(AuthToken.m_szAuthToken, m_le.idFlight, c);

            return rgProps != null;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(ActNewFlight.this, ActNewFlight.this.getString(R.string.prgPropsForFlight));
            c = getContext();
            if (c == null)
                c = getActivity().getApplicationContext();
        }

        protected void onPostExecute(Boolean b) {
            if (!isAdded() || getActivity().isFinishing())
                return;

            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            m_le.ToDB(); // need to save it to the db, in case it isn't already saved.
            // and save the properties
            if (b) {
                m_le.rgCustomProperties = rgProps;
                FlightProperty.RewritePropertiesForFlight(m_le.idLocalDB, m_le.rgCustomProperties);
            }
            m_le.SyncProperties();
            setUpPropertiesForFlight();
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class DeleteTask extends AsyncTask<Void, String, MFBSoap> implements MFBSoap.MFBSoapProgressUpdate {
        private ProgressDialog m_pd = null;
        private Object m_Result = null;

        DeleteTask() {
            super();
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            DeleteFlightSvc dfs = new DeleteFlightSvc();
            dfs.m_Progress = this;
            dfs.DeleteFlight(AuthToken.m_szAuthToken, m_le.idFlight, getContext());
            m_Result = (dfs.getLastError().length() == 0);

            return dfs;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(ActNewFlight.this, ActNewFlight.this.getString(R.string.prgDeletingFlight));
        }

        protected void onPostExecute(MFBSoap svc) {
            if (!isAdded() || getActivity().isFinishing())
                return;

            if ((Boolean) m_Result) {
                RecentFlightsSvc.ClearCachedFlights();
                MFBMain.invalidateCachedTotals();
                finish();
            } else {
                MFBUtil.Alert(ActNewFlight.this, getString(R.string.txtError), svc.getLastError());
            }

            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }

        protected void onProgressUpdate(String... msg) {
            m_pd.setMessage(msg[0]);
        }

        public void NotifyProgress(int percentageComplete, String szMsg) {
            this.publishProgress(szMsg);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class SubmitTask extends AsyncTask<Void, String, MFBSoap> implements MFBSoap.MFBSoapProgressUpdate {
        private ProgressDialog m_pd = null;
        private Object m_Result = null;
        private PostingOptions m_po;
        private LogbookEntry lelocal = null;

        SubmitTask(PostingOptions po) {
            super();
            m_po = po;
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            CommitFlightSvc cf = new CommitFlightSvc();
            cf.m_Progress = this;
            m_Result = cf.FCommitFlight(AuthToken.m_szAuthToken, m_le, m_po, getContext());
            return cf;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(ActNewFlight.this, ActNewFlight.this.getString(R.string.prgSavingFlight));
            lelocal = m_le; // hold onto a reference to m_le.
        }

        protected void onPostExecute(MFBSoap svc) {
            if (isAdded() && !getActivity().isFinishing()) {
                if ((Boolean) m_Result) {
                    MFBMain.invalidateCachedTotals();

                    // success, so we our cached recents are invalid
                    RecentFlightsSvc.ClearCachedFlights();
                    DialogInterface.OnClickListener ocl;

                    Boolean fIsNew = lelocal.IsNewFlight(); // save this, since deletePendingFlight will reset the ID of the flight

                    // the flight was successfully saved, so delete any local copy regardless
                    lelocal.DeletePendingFlight();

                    if (fIsNew) {
                        // Reset the flight and we stay on this page
                        ResetFlight(true);
                        ocl = (d, id) -> d.cancel();
                    } else {
                        // no need to reset the current flight because we will finish.
                        ocl = (d, id) -> {
                            d.cancel();
                            finish();
                        };
                        lelocal = m_le = null; // so that onPause won't cause it to be saved on finish() call.
                    }
                    new AlertDialog.Builder(ActNewFlight.this.getActivity())
                            .setMessage(getString(R.string.txtSavedFlight))
                            .setTitle(getString(R.string.txtSuccess))
                            .setNegativeButton("OK", ocl)
                            .create().show();
                } else {
                    MFBUtil.Alert(ActNewFlight.this, getString(R.string.txtError), svc.getLastError());
                }
            }

            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }

        protected void onProgressUpdate(String... msg) {
            m_pd.setMessage(msg[0]);
        }

        public void NotifyProgress(int percentageComplete, String szMsg) {
            this.publishProgress(szMsg);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class GetDigitizedSigTask extends AsyncTask<String, Void, Bitmap> {
        ImageView ivDigitizedSig;

        GetDigitizedSigTask(ImageView iv) {
            this.ivDigitizedSig = iv;
        }

        protected Bitmap doInBackground(String... urls) {
            String url = urls[0];
            Bitmap bm = null;
            try {
                InputStream str = new java.net.URL(url).openStream();
                bm = BitmapFactory.decodeStream(str);
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, e.getMessage());
            }
            return bm;
        }

        protected void onPostExecute(Bitmap result) {
            ivDigitizedSig.setImageBitmap(result);
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.newflight, container, false);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (m_le == null || m_le.IsNewFlight())
            MFBMain.SetInProgressFlightActivity(getContext(), null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        AddListener(R.id.btnFlightSet);
        AddListener(R.id.btnFlightStartSet);
        AddListener(R.id.btnEngineStartSet);
        AddListener(R.id.btnFlightEndSet);
        AddListener(R.id.btnEngineEndSet);
        AddListener(R.id.btnProps);
        AddListener(R.id.btnAppendNearest);

        findViewById(R.id.btnAppendNearest).setOnLongClickListener((v) -> {
            AppendAdHoc();
            return true;
        });

        // Expand/collapse
        AddListener(R.id.txtViewInTheCockpit);
        AddListener(R.id.txtImageHeader);
        AddListener(R.id.txtPinnedPropertiesHeader);
        AddListener(R.id.txtSignatureHeader);

        enableCrossFill(R.id.txtNight);
        enableCrossFill(R.id.txtSimIMC);
        enableCrossFill(R.id.txtIMC);
        enableCrossFill(R.id.txtXC);
        enableCrossFill(R.id.txtDual);
        enableCrossFill(R.id.txtGround);
        enableCrossFill(R.id.txtCFI);
        enableCrossFill(R.id.txtSIC);
        enableCrossFill(R.id.txtPIC);
        enableCrossFill(R.id.txtHobbsStart);

        findViewById(R.id.txtTotal).setOnLongClickListener(v -> {
            Intent i = new Intent(getActivity(), ActTimeCalc.class);
            i.putExtra(ActTimeCalc.INITIAL_TIME, DoubleFromField(R.id.txtTotal));
            startActivityForResult(i, ActTimeCalc.TIME_CALC_REQUEST_CODE);
            return true;
        });

        ImageButton b = (ImageButton) findViewById(R.id.btnPausePlay);
        b.setOnClickListener(this);
        b = (ImageButton) findViewById(R.id.btnViewOnMap);
        b.setOnClickListener(this);
        b = (ImageButton) findViewById(R.id.btnAddApproach);
        b.setOnClickListener(this);

        // cache these views for speed.
        txtQuality = (TextView) findViewById(R.id.txtFlightGPSQuality);
        txtStatus = (TextView) findViewById(R.id.txtFlightStatus);
        txtSpeed = (TextView) findViewById(R.id.txtFlightSpeed);
        txtAltitude = (TextView) findViewById(R.id.txtFlightAltitude);
        txtSunrise = (TextView) findViewById(R.id.txtSunrise);
        txtSunset = (TextView) findViewById(R.id.txtSunset);
        txtLatitude = (TextView) findViewById(R.id.txtLatitude);
        txtLongitude = (TextView) findViewById(R.id.txtLongitude);
        imgRecording = (ImageView) findViewById(R.id.imgRecording);

        // get notification of hobbs changes, or at least focus changes
        OnFocusChangeListener s = (v, hasFocus) -> {
            if (!hasFocus) onHobbsChanged(v);
        };

        findViewById(R.id.txtHobbsStart).setOnFocusChangeListener(s);
        findViewById(R.id.txtHobbsEnd).setOnFocusChangeListener(s);

        Intent i = getActivity().getIntent();
        int idFlightToView = i.getIntExtra(ActRecentsWS.VIEWEXISTINGFLIGHTID, 0);
        long idLocalFlightToView = i.getLongExtra(ActRecentsWS.VIEWEXISTINGFLIGHTLOCALID, 0);
        Boolean fIsNewFlight = (idFlightToView == 0);

        Log.w(MFBConstants.LOG_TAG, String.format("ActNewFlight - onCreate - Viewing flight idflight=%d, idlocal=%d", idFlightToView, idLocalFlightToView));

        // set for no focus.
        findViewById(R.id.btnFlightSet).requestFocus();

        if (fIsNewFlight) {
            // re-use the existing in-progress flight
            m_le = MFBMain.getNewFlightListener().getInProgressFlight(getActivity());
            MFBMain.SetInProgressFlightActivity(getContext(), this);
            MFBMain.registerNotifyResetAll(this);
            SharedPreferences pref = getActivity().getPreferences(Context.MODE_PRIVATE);
            Boolean fExpandCockpit = pref.getBoolean(m_KeyShowInCockpit, true);
            setExpandedState((TextView) findViewById(R.id.txtViewInTheCockpit), findViewById(R.id.sectInTheCockpit), fExpandCockpit, false);
        } else {
            // view an existing flight
            if (idFlightToView > 0) // existing flight
            {
                m_le = RecentFlightsSvc.GetCachedFlightByID(idFlightToView);
                if (m_le == null) {
                    // Navigate back
                    MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.errCannotFindFlight));
                    finish();
                    return;
                }

                // get any existing props, if they're on the server
                if (m_le.IsExistingFlight()) {
                    if (m_le.rgCustomProperties == null) {
                        UpdateAndViewPropsTask vp = new UpdateAndViewPropsTask();
                        vp.execute();
                    } else
                        m_le.SyncProperties();
                }
            } else {
                m_le = new LogbookEntry(idLocalFlightToView);
                m_le.SyncProperties();
            }
        }

        if (m_le != null && m_le.rgFlightImages == null)
            m_le.getImagesForFlight();

        Log.w(MFBConstants.LOG_TAG, String.format("ActNewFlight - created, m_le is %s", m_le == null ? "null" : "non-null"));
    }

    private void enableCrossFill(int id) {
        DecimalEdit de = (DecimalEdit) findViewById(id);
        de.setDelegate(this);
    }

    public void CrossFillRequested(DecimalEdit sender) {
        FromView();
        if (sender.getId() == R.id.txtHobbsStart) {
            Double d = Aircraft.getHighWaterHobbsForAircraft(m_le.idAircraft);
            if (d > 0)
                sender.setDoubleValue(d);
        }
        else if (m_le.decTotal > 0)
            sender.setDoubleValue(m_le.decTotal);
        FromView();
    }

    private Aircraft[] SelectibleAircraft() {
        if (m_rgac == null)
            return null;

        List<Aircraft> lst = new ArrayList<>();
        for (Aircraft ac : m_rgac) {
            if (!ac.HideFromSelection || (m_le != null && ac.AircraftID == m_le.idAircraft))
                lst.add(ac);
        }
        return lst.toArray(new Aircraft[lst.size()]);
    }

    public void onResume() {
        // refresh the aircraft list (will be cached if we already have it)
        // in case a new aircraft has been added.
        super.onResume();

        if (!AuthToken.FIsValid()) {
            DlgSignIn d = new DlgSignIn(getActivity());
            d.show();
        }

        if (AuthToken.FIsValid()) {
            AircraftSvc acs = new AircraftSvc();
            m_rgac = acs.AircraftForUser(AuthToken.m_szAuthToken, getContext());

            Spinner spnAircraft = (Spinner) findViewById(R.id.spnAircraft);

            Aircraft[] rgFilteredAircraft = SelectibleAircraft();
            if (rgFilteredAircraft != null && rgFilteredAircraft.length > 0) {
                // Create a list of the aircraft to show, which are the ones that are not hidden OR the active one for the flight
                ArrayAdapter<Aircraft> adapter = new ArrayAdapter<>(
                        getActivity(), R.layout.mfbsimpletextitem, rgFilteredAircraft);
                spnAircraft.setAdapter(adapter);
                // need to notifydatasetchanged or else setselection doesn't
                // update correctly.
                adapter.notifyDataSetChanged();
            } else {
                spnAircraft.setPrompt(getString(R.string.errNoAircraftFoundShort));
                MFBUtil.Alert(this, getString(R.string.txtError), getString(R.string.errMustCreateAircraft));
            }
        }

        // fix up the link to the user's profile.
        AddListener(R.id.txtSocialNetworkHint);

        // Not sure why le can sometimes be empty here...
        if (m_le == null)
            m_le = MFBMain.getNewFlightListener().getInProgressFlight(getActivity());

        // show/hide GPS controls based on whether this is a new flight or existing.
        boolean fIsNewFlight = m_le.IsNewFlight();

        LinearLayout l = (LinearLayout) findViewById(R.id.sectGPS);
        l.setVisibility(fIsNewFlight ? View.VISIBLE : View.GONE);

        findViewById(R.id.btnAppendNearest).setVisibility(fIsNewFlight && MFBLocation.HasGPS(getContext()) ? View.VISIBLE : View.GONE);
        ImageButton btnViewFlight = (ImageButton) findViewById(R.id.btnViewOnMap);
        btnViewFlight.setVisibility((MFBMain.HasMaps()) ? View.VISIBLE : View.GONE);

        if (fIsNewFlight) {
            // ensure that we keep the elapsed time up to date
            updatePausePlayButtonState();
            m_HandlerUpdateTimer = new Handler();
            m_UpdateElapsedTimeTask = new Runnable() {
                public void run() {
                    updateElapsedTime();
                    m_HandlerUpdateTimer.postDelayed(this, 1000);
                }
            };
            m_HandlerUpdateTimer.postDelayed(m_UpdateElapsedTimeTask, 1000);

            // And make sure we display GPS data
            MFBMain.SetInProgressFlightActivity(getContext(), this);
        }

        setUpGalleryForFlight();

        Log.w(MFBConstants.LOG_TAG, String.format("onResume completed, landings are %d, isRecording: %s", m_le.cLandings, MFBLocation.IsRecording ? "yes" : "no"));

        RestoreState();

        // set the ensure that we are following the right numerical format
        setDecimalEditMode(R.id.txtCFI, EditMode.HHMM);
        setDecimalEditMode(R.id.txtDual, EditMode.HHMM);
        setDecimalEditMode(R.id.txtGround, EditMode.HHMM);
        setDecimalEditMode(R.id.txtIMC, EditMode.HHMM);
        setDecimalEditMode(R.id.txtNight, EditMode.HHMM);
        setDecimalEditMode(R.id.txtPIC, EditMode.HHMM);
        setDecimalEditMode(R.id.txtSIC, EditMode.HHMM);
        setDecimalEditMode(R.id.txtSimIMC, EditMode.HHMM);
        setDecimalEditMode(R.id.txtTotal, EditMode.HHMM);
        setDecimalEditMode(R.id.txtXC, EditMode.HHMM);

        // Make sure the date of flight is up-to-date
        if (!m_le.isKnownEngineStart() && !m_le.isKnownFlightStart())
            resetDateOfFlight();

        ToView();
    }

    private void setUpGalleryForFlight() {
        if (m_le.rgFlightImages == null)
            m_le.getImagesForFlight();
        setUpImageGallery(getGalleryID(), m_le.rgFlightImages, getGalleryHeader());
    }

    public void onPause() {
        super.onPause();

        // should only happen when we are returning from viewing an existing/pending flight, may have been submitted
        // either way, no need to save this
        if (m_le == null)
            return;

        FromView();
        saveCurrentFlight();
        Log.w(MFBConstants.LOG_TAG, String.format("Paused, landings are %d", m_le.cLandings));
        SaveState();

        // free up some scheduling resources.
        if (m_HandlerUpdateTimer != null)
            m_HandlerUpdateTimer.removeCallbacks(m_UpdateElapsedTimeTask);
    }

    private void RestoreState() {
        try {
            SharedPreferences mPrefs = getActivity().getPreferences(Activity.MODE_PRIVATE);

            ActNewFlight.fPaused = mPrefs.getBoolean(m_KeysIsPaused, false);
            ActNewFlight.dtPauseTime = mPrefs.getLong(m_KeysPausedTime, 0);
            ActNewFlight.dtTimeOfLastPause = mPrefs.getLong(m_KeysTimeOfLastPause, 0);
            ActNewFlight.accumulatedNight = (double) mPrefs.getFloat(m_KeysAccumulatedNight, (float) 0.0);
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
        }
    }

    private void SaveState() {
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        SharedPreferences.Editor ed = getActivity().getPreferences(Activity.MODE_PRIVATE).edit();
        ed.putBoolean(m_KeysIsPaused, ActNewFlight.fPaused);
        ed.putLong(m_KeysPausedTime, ActNewFlight.dtPauseTime);
        ed.putLong(m_KeysTimeOfLastPause, ActNewFlight.dtTimeOfLastPause);
        ed.putFloat(m_KeysAccumulatedNight, (float) ActNewFlight.accumulatedNight);
        ed.apply();
    }

    public void saveCurrentFlight() {
        if (!m_le.IsExistingFlight())
            m_le.ToDB();

        // and we only want to save the current flightID if it is a new (not pending!) flight
        if (m_le.IsNewFlight())
            MFBMain.getNewFlightListener().saveCurrentFlightId(getActivity());
    }

    private void SetLogbookEntry(LogbookEntry le) {
        m_le = le;
        saveCurrentFlight();
        if (getView() == null)
            return;

        setUpGalleryForFlight();
        ToView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        int idMenu;
        if (m_le != null) {
            if (m_le.IsExistingFlight())
                idMenu = R.menu.mfbexistingflightmenu;
            else if (m_le.IsNewFlight())
                idMenu = R.menu.mfbnewflightmenu;
            else if (m_le.IsQueuedFlight())
                idMenu = R.menu.mfbpendingflightmenu;
            else
                idMenu = R.menu.mfbpendingflightmenu;
            inflater.inflate(idMenu, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.imagemenu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Should never happen.
        if (m_le == null)
            return false;

        // Handle item selection
        int menuId = item.getItemId();
        switch (menuId) {
            case R.id.menuUploadLater:
                SubmitFlight(true);
                return true;
            case R.id.menuResetFlight:
                if (m_le.idLocalDB > 0)
                    m_le.DeletePendingFlight();
                ResetFlight(false);
                return true;
            case R.id.menuSignFlight:
                try {
                    ActWebView.ViewURL(getActivity(), String.format(Locale.US, MFBConstants.urlSign,
                            MFBConstants.fIsDebug ? "http" : "https",
                            MFBConstants.szIP,
                            m_le.idFlight,
                            URLEncoder.encode(AuthToken.m_szAuthToken, "UTF-8")));
                } catch (UnsupportedEncodingException ignored) {
                }
                return true;
            case R.id.btnDeleteFlight:
                new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.lblConfirm)
                        .setMessage(R.string.lblConfirmFlightDelete)
                        .setPositiveButton(R.string.lblOK, (dialog, which) -> {
                            if (m_le.IsPendingFlight()) {
                                m_le.DeletePendingFlight();
                                m_le = null; // clear this out since we're going to finish().
                                RecentFlightsSvc.ClearCachedFlights();
                                finish();
                            } else if (m_le.IsExistingFlight()) {
                                DeleteTask dt = new DeleteTask();
                                dt.execute();
                            }
                        })
                        .setNegativeButton(R.string.lblCancel, null)
                        .show();
                return true;
            case R.id.btnSubmitFlight:
            case R.id.btnUpdateFlight:
                SubmitFlight(false);
                return true;
            case R.id.menuTakePicture:
                takePictureClicked();
                return true;
            case R.id.menuTakeVideo:
                takeVideoClicked();
                return true;
            case R.id.menuChoosePicture:
                choosePictureClicked();
                return true;
            case R.id.menuRepeatFlight:
            case R.id.menuReverseFlight: {
                assert m_le != null;
                LogbookEntry leNew = (menuId == R.id.menuRepeatFlight) ? m_le.Clone() : m_le.CloneAndReverse();
                leNew.idFlight = LogbookEntry.ID_QUEUED_FLIGHT_UNSUBMITTED;
                leNew.ToDB();
                FlightProperty.RewritePropertiesForFlight(leNew.idLocalDB, leNew.rgCustomProperties);
                leNew.SyncProperties();
                RecentFlightsSvc.ClearCachedFlights();
                new AlertDialog.Builder(ActNewFlight.this.getActivity())
                        .setMessage(getString(R.string.txtRepeatFlightComplete))
                        .setTitle(getString(R.string.txtSuccess))
                        .setNegativeButton("OK", (d, id) -> {
                            d.cancel();
                            finish();
                        })
                        .create().show();
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuAddComment:
            case R.id.menuDeleteImage:
            case R.id.menuViewImage:
                return onImageContextItemSelected(item, this);
            default:
                break;
        }
        return true;
    }

    private final int PERMISSION_REQUEST_NEAREST = 10683;

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_NEAREST:
                if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    AppendNearest();
                }
                return;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private Boolean checkGPSPermissions(int req) {
        if (ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            return true;

        // Should we show an explanation?
        requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, req);
        return false;
    }

    //region append to route
    // NOTE: I had been doing this with an AsyncTask, but it
    // wasn't thread safe with the database.  DB is pretty fast,
    // so we can just make sure we do all DB stuff on the main thread.
    private void AppendNearest() {
        Assert.assertNotNull("No location object in AppendNearest", MFBLocation.GetMainLocation());

        if (checkGPSPermissions(PERMISSION_REQUEST_NEAREST)) {
            TextView txtRoute = (TextView) findViewById(R.id.txtRoute);
            m_le.szRoute = Airport.AppendNearestToRoute(txtRoute.getText().toString(), MFBLocation.GetMainLocation().CurrentLoc());
            txtRoute.setText(m_le.szRoute);
        }
    }

    private void AppendAdHoc() {
        Assert.assertNotNull("No location object in AppendNearest", MFBLocation.GetMainLocation());
        MFBLocation loc = MFBLocation.GetMainLocation();

        if (!checkGPSPermissions(PERMISSION_REQUEST_NEAREST))
            return;

        if (loc == null || loc.CurrentLoc() == null)
            return;
        String szAdHoc = new LatLong(loc.CurrentLoc()).toAdHocLocString();
        TextView txtRoute = (TextView) findViewById(R.id.txtRoute);
        m_le.szRoute = Airport.AppendCodeToRoute(txtRoute.getText().toString(), szAdHoc);
        txtRoute.setText(m_le.szRoute);
    }
    //endregion

    private void takePictureClicked() {
        saveCurrentFlight();
        TakePicture();
    }

    private void takeVideoClicked() {
        saveCurrentFlight();
        TakeVideo();
    }

    private void choosePictureClicked() {
        saveCurrentFlight();
        ChoosePicture();
    }

    public void onClick(View v) {
        FromView();
        int id = v.getId();
        switch (id) {
            case R.id.btnEngineStartSet:
                if (!m_le.isKnownEngineStart()) {
                    m_le.dtEngineStart = MFBUtil.nowWith0Seconds();
                    EngineStart();
                } else
                    SetDateTime(id, m_le.dtEngineStart, this, DlgDatePicker.datePickMode.UTCDATETIME);
                break;
            case R.id.btnEngineEndSet:
                if (!m_le.isKnownEngineEnd()) {
                    m_le.dtEngineEnd = MFBUtil.nowWith0Seconds();
                    EngineStop();
                } else
                    SetDateTime(id, m_le.dtEngineEnd, this, DlgDatePicker.datePickMode.UTCDATETIME);
                break;
            case R.id.btnFlightStartSet:
                if (!m_le.isKnownFlightStart()) {
                    m_le.dtFlightStart = MFBUtil.nowWith0Seconds();
                    FlightStart();
                } else
                    SetDateTime(id, m_le.dtFlightStart, this, DlgDatePicker.datePickMode.UTCDATETIME);
                break;
            case R.id.btnFlightEndSet:
                if (!m_le.isKnownFlightEnd()) {
                    m_le.dtFlightEnd = MFBUtil.nowWith0Seconds();
                    FlightStop();
                } else
                    SetDateTime(id, m_le.dtFlightEnd, this, DlgDatePicker.datePickMode.UTCDATETIME);
                break;
            case R.id.btnFlightSet:
                DlgDatePicker dlg = new DlgDatePicker(getActivity(), DlgDatePicker.datePickMode.LOCALDATEONLY, m_le.dtFlight);
                dlg.m_delegate = this;
                dlg.m_id = id;
                dlg.show();
                break;
            case R.id.btnProps:
                if (m_le.IsExistingFlight() && m_le.rgCustomProperties == null) {
                    UpdateAndViewPropsTask vp = new UpdateAndViewPropsTask();
                    vp.execute();
                } else
                    ViewPropsForFlight();
                break;
            case R.id.btnAppendNearest:
                AppendNearest();
                break;
            case R.id.btnAddApproach: {
                Intent i = new Intent(getActivity(), ActAddApproach.class);
                i.putExtra(ActAddApproach.AIRPORTSFORAPPROACHES, m_le.szRoute);
                startActivityForResult(i, ActAddApproach.APPROACH_DESCRIPTION_REQUEST_CODE);
            }
            break;
            case R.id.btnViewOnMap:
                Intent i = new Intent(getActivity(), ActFlightMap.class);
                i.putExtra(ActFlightMap.ROUTEFORFLIGHT, m_le.szRoute);
                i.putExtra(ActFlightMap.EXISTINGFLIGHTID, m_le.IsExistingFlight() ? m_le.idFlight : 0);
                i.putExtra(ActFlightMap.PENDINGFLIGHTID, m_le.IsPendingFlight() ? m_le.idLocalDB : 0);
                i.putExtra(ActFlightMap.NEWFLIGHTID, m_le.IsNewFlight() ? LogbookEntry.ID_NEW_FLIGHT : 0);
                i.putExtra(ActFlightMap.ALIASES, "");
                startActivityForResult(i, ActFlightMap.REQUEST_ROUTE);
                break;
            case R.id.btnPausePlay:
                toggleFlightPause();
                break;
            case R.id.txtViewInTheCockpit: {
                View target = findViewById(R.id.sectInTheCockpit);
                Boolean fExpandCockpit = target.getVisibility() != View.VISIBLE;

                if (m_le != null && m_le.IsNewFlight()) {
                    SharedPreferences.Editor e = getActivity().getPreferences(Context.MODE_PRIVATE).edit();
                    e.putBoolean(m_KeyShowInCockpit, fExpandCockpit);
                    e.apply();
                }
                setExpandedState((TextView) v, target, fExpandCockpit);
            }
            break;
            case R.id.txtImageHeader: {
                View target = findViewById(R.id.tblImageTable);
                setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
            }
            break;
            case R.id.txtSignatureHeader: {
                View target = findViewById(R.id.sectSignature);
                setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
            }
            break;
            case R.id.txtPinnedPropertiesHeader: {
                View target = findViewById(R.id.sectPinnedProperties);
                setExpandedState((TextView) v, target, target.getVisibility() != View.VISIBLE);
            }
            break;
            case R.id.txtSocialNetworkHint: {
                String szURLProfile = String.format(MFBConstants.urlProfile, MFBConstants.szIP, AuthToken.m_szEmail, AuthToken.m_szPass, MFBConstants.destProfile);
                ActWebView.ViewURL(getActivity(), szURLProfile);
            }
            default:
                break;
        }
        ToView();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE:
            case CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK)
                    AddCameraImage(m_TempFilePath, requestCode == CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
                break;
            case SELECT_IMAGE_ACTIVITY_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK)
                    AddGalleryImage(data);
            case EDIT_PROPERTIES_ACTIVITY_REQUEST_CODE:
                setUpPropertiesForFlight();
                break;
            case ActTimeCalc.TIME_CALC_REQUEST_CODE:
                SetDoubleForField(R.id.txtTotal, m_le.decTotal = data.getDoubleExtra(ActTimeCalc.COMPUTED_TIME, m_le.decTotal));
                break;
            case ActFlightMap.REQUEST_ROUTE:
                m_le.szRoute = data.getStringExtra(ActFlightMap.ROUTEFORFLIGHT);
                SetStringForField(R.id.txtRoute, m_le.szRoute);
                break;
            case ActAddApproach.APPROACH_DESCRIPTION_REQUEST_CODE:
                String approachDesc = data.getStringExtra(ActAddApproach.APPROACHDESCRIPTIONRESULT);
                if (approachDesc.length() > 0) {
                    m_le.szComments += " " + approachDesc;
                    SetStringForField(R.id.txtComments, m_le.szComments);

                    int cApproachesToAdd = data.getIntExtra(ActAddApproach.APPROACHADDTOTOTALSRESULT, 0);
                    if (cApproachesToAdd > 0) {
                        m_le.cApproaches += cApproachesToAdd;
                        SetIntForField(R.id.txtApproaches, m_le.cApproaches);
                    }
                }
                break;
            default:
                break;
        }
    }

    private void ViewPropsForFlight() {
        Intent i = new Intent(getActivity(), ActViewProperties.class);
        i.putExtra(PROPSFORFLIGHTID, m_le.idLocalDB);
        i.putExtra(PROPSFORFLIGHTEXISTINGID, m_le.idFlight);
        i.putExtra(PROPSFORFLIGHTCROSSFILLVALUE, m_le.decTotal);
        i.putExtra(TACHFORCROSSFILLVALUE, Aircraft.getHighWaterTachForAircraft(m_le.idAircraft));
        startActivityForResult(i, EDIT_PROPERTIES_ACTIVITY_REQUEST_CODE);
    }

    private void onHobbsChanged(View v) {
        if (m_le != null && MFBLocation.fPrefAutoFillTime == MFBLocation.AutoFillOptions.HobbsTime) {
            EditText txtHobbsStart = (EditText) findViewById(R.id.txtHobbsStart);
            EditText txtHobbsEnd = (EditText) findViewById(R.id.txtHobbsEnd);
            double newHobbsStart = DoubleFromField(R.id.txtHobbsStart);
            double newHobbsEnd = DoubleFromField(R.id.txtHobbsEnd);

            if ((v == txtHobbsStart && newHobbsStart != m_le.hobbsStart) ||
                    (v == txtHobbsEnd && newHobbsEnd != m_le.hobbsEnd))
                AutoTotals();
        }
    }

    public void updateDate(int id, Date dt) {
        FromView();

        Boolean fEngineChanged = false;
        Boolean fFlightChanged = false;

        dt = MFBUtil.removeSeconds(dt);

        switch (id) {
            case R.id.btnEngineStartSet:
                m_le.dtEngineStart = dt;
                fEngineChanged = true;
                break;
            case R.id.btnEngineEndSet:
                m_le.dtEngineEnd = dt;
                fEngineChanged = true;
                ShowRecordingIndicator();
                break;
            case R.id.btnFlightStartSet:
                m_le.dtFlightStart = dt;
                fFlightChanged = true;
                break;
            case R.id.btnFlightEndSet:
                m_le.dtFlightEnd = dt;
                fFlightChanged = true;
                break;
            case R.id.btnFlightSet:
                m_le.dtFlight = dt;
                break;
            default:
                break;
        }
        ToView();

        // keep auto-updated hobbs and totals in sync
        switch (MFBLocation.fPrefAutoFillHobbs) {
            case EngineTime:
                if (fEngineChanged)
                    AutoHobbs();
                break;
            case FlightTime:
                if (fFlightChanged)
                    AutoHobbs();
                break;
            default:
                break;
        }

        switch (MFBLocation.fPrefAutoFillTime) {
            case EngineTime:
                if (fEngineChanged)
                    AutoTotals();
                break;
            case FlightTime:
                if (fFlightChanged)
                    AutoTotals();
                break;
            case HobbsTime:
                // Should have been caught by autohobbs above
                break;
            default:
                break;
        }
    }

    private int ValidateAircraftID(int id) {
        int idAircraftToUse = -1;

        if (m_rgac != null) {
            for (Aircraft ac : m_rgac)
                if (ac.AircraftID == id)
                    idAircraftToUse = id;
        }

        return idAircraftToUse;
    }

    private void ResetFlight(Boolean fCarryHobbs) {
        // start up a new flight with the same aircraft ID and public setting.
        // first, validate that the aircraft is still OK for the user
        double hobbsEnd = m_le.hobbsEnd;
        LogbookEntry leNew = new LogbookEntry(ValidateAircraftID(m_le.idAircraft), m_le.fPublic);
        if (fCarryHobbs)
            leNew.hobbsStart = hobbsEnd;
        SetLogbookEntry(leNew);
        MFBMain.getNewFlightListener().setInProgressFlight(leNew);
        saveCurrentFlight();

        // flush any pending flight data
        MFBLocation.GetMainLocation().ResetFlightData();

        // and flush any pause/play data
        fPaused = false;
        dtPauseTime = 0;
        dtTimeOfLastPause = 0;
        ActNewFlight.accumulatedNight = 0.0;
    }

    private void SubmitFlight(Boolean fForcePending) {
        FromView();

        Activity a = getActivity();
        if (a != null && a.getCurrentFocus() != null)
            a.getCurrentFocus().clearFocus();   // force any in-progress edit to commit, particularly for properties.

        Boolean fIsNew = m_le.IsNewFlight(); // hold onto this because we can change the status.
        if (fIsNew) {
            MFBLocation.GetMainLocation().setIsRecording(false);
            ShowRecordingIndicator();
        }

        // load any pending properties from the DB into the logbookentry object itself.
        m_le.SyncProperties();

        // load the telemetry string, if it's a first submission.
        if (m_le.IsNewFlight())
            m_le.szFlightData = MFBLocation.GetMainLocation().getFlightDataString();

        // Save for later if offline or if fForcePending
        boolean fIsOnline = MFBSoap.IsOnline(getContext());
        if (fForcePending || !fIsOnline) {

            // save the flight with id of -2 if it's a new flight
            if (fIsNew)
                m_le.idFlight = (fForcePending ? LogbookEntry.ID_QUEUED_FLIGHT_UNSUBMITTED : LogbookEntry.ID_PENDING_FLIGHT);

            // Existing flights can't be saved for later.  No good reason for that except work.
            if (m_le.IsExistingFlight()) {
                new AlertDialog.Builder(ActNewFlight.this.getActivity())
                        .setMessage(getString(R.string.errNoInternetNoSave))
                        .setTitle(getString(R.string.txtError))
                        .setNegativeButton(getString(R.string.lblOK), (d, id) -> {
                            d.cancel();
                            finish();
                        })
                        .create().show();
                return;
            }

            // now save it - but check for success
            if (!m_le.ToDB()) {
                // Failure!
                // Try saving it without the flight data string, in case that was the issue
                m_le.szFlightData = "";
                if (!m_le.ToDB()) {
                    // still didn't work. give an error message and, if necessary,
                    // restore to being a new flight.
                    MFBUtil.Alert(this, getString(R.string.txtError), m_le.szError);
                    // restore the previous idFlight if we were saving a new flight.
                    if (fIsNew) {
                        m_le.idFlight = LogbookEntry.ID_NEW_FLIGHT;
                        saveCurrentFlight();
                    }
                    return;
                }
                // if we're here, then phew - saved without the string
            }

            // if we're here, save was successful (even if flight data was dropped)
            RecentFlightsSvc.ClearCachedFlights();
            MFBMain.invalidateCachedTotals();
            if (fIsNew) {
                ResetFlight(true);
                MFBUtil.Alert(this, getString(R.string.txtSuccess), getString(R.string.txtSavedPendingFlight));
            } else {
                new AlertDialog.Builder(ActNewFlight.this.getActivity())
                        .setMessage(getString(R.string.txtSavedPendingFlight))
                        .setTitle(getString(R.string.txtSuccess))
                        .setNegativeButton("OK", (d, id) -> {
                            d.cancel();
                            finish();
                        })
                        .create().show();
            }
        } else {

            new SubmitTask(m_po).execute();
        }
    }

    public void ToView() {
        if (getView() == null)
            return;

        SetStringForField(R.id.txtComments, m_le.szComments);
        SetStringForField(R.id.txtRoute, m_le.szRoute);

        SetIntForField(R.id.txtApproaches, m_le.cApproaches);
        SetIntForField(R.id.txtLandings, m_le.cLandings);
        SetIntForField(R.id.txtFSNightLandings, m_le.cNightLandings);
        SetIntForField(R.id.txtDayLandings, m_le.cFullStopLandings);

        SetCheckState(R.id.ckMyFlightbook, m_le.fPublic);
        SetCheckState(R.id.ckHold, m_le.fHold);

        SetLocalDateForField(R.id.btnFlightSet, m_le.dtFlight);

        // Engine/Flight dates
        SetUTCDateForField(R.id.btnEngineStartSet, m_le.dtEngineStart);
        SetUTCDateForField(R.id.btnEngineEndSet, m_le.dtEngineEnd);
        SetUTCDateForField(R.id.btnFlightStartSet, m_le.dtFlightStart);
        SetUTCDateForField(R.id.btnFlightEndSet, m_le.dtFlightEnd);
        SetDoubleForField(R.id.txtHobbsStart, m_le.hobbsStart);
        SetDoubleForField(R.id.txtHobbsEnd, m_le.hobbsEnd);

        SetDoubleForField(R.id.txtCFI, m_le.decCFI);
        SetDoubleForField(R.id.txtDual, m_le.decDual);
        SetDoubleForField(R.id.txtGround, m_le.decGrndSim);
        SetDoubleForField(R.id.txtIMC, m_le.decIMC);
        SetDoubleForField(R.id.txtNight, m_le.decNight);
        SetDoubleForField(R.id.txtPIC, m_le.decPIC);
        SetDoubleForField(R.id.txtSIC, m_le.decSIC);
        SetDoubleForField(R.id.txtSimIMC, m_le.decSimulatedIFR);
        SetDoubleForField(R.id.txtTotal, m_le.decTotal);
        SetDoubleForField(R.id.txtXC, m_le.decXC);

        Boolean fIsSigned = (m_le.signatureStatus != LogbookEntry.SigStatus.None);
        findViewById(R.id.sectSignature).setVisibility(fIsSigned ? View.VISIBLE : View.GONE);
        findViewById(R.id.txtSignatureHeader).setVisibility(fIsSigned ? View.VISIBLE : View.GONE);
        if (fIsSigned)
        {
            ImageView imgSigStatus = (ImageView) findViewById(R.id.imgSigState);
            switch (m_le.signatureStatus) {
                case None:
                    break;
                case Valid:
                    imgSigStatus.setImageResource(R.drawable.sigok);
                    imgSigStatus.setContentDescription(getString(R.string.cdIsSignedValid));
                    ((TextView) findViewById(R.id.txtSigState)).setText(getString(R.string.cdIsSignedValid));
                    break;
                case Invalid:
                    imgSigStatus.setImageResource(R.drawable.siginvalid);
                    imgSigStatus.setContentDescription(getString(R.string.cdIsSignedInvalid));
                    ((TextView) findViewById(R.id.txtSigState)).setText(getString(R.string.cdIsSignedInvalid));
                    break;
            }

            String szSigInfo1 = String.format(Locale.getDefault(),
                    getString(R.string.lblSignatureTemplate1),
                    DateFormat.getDateFormat(getActivity()).format(m_le.signatureDate),
                    m_le.signatureCFIName);
            String szSigInfo2 = String.format(Locale.getDefault(),
                    getString(R.string.lblSignatureTemplate2),
                    m_le.signatureCFICert,
                    DateFormat.getDateFormat(getActivity()).format(m_le.signatureCFIExpiration));
            ((TextView) findViewById(R.id.txtSigInfo1)).setText(szSigInfo1);
            ((TextView) findViewById(R.id.txtSigInfo2)).setText(szSigInfo2);
            ((TextView) findViewById(R.id.txtSigComment)).setText(m_le.signatureComments);

            ImageView ivDigitizedSig = (ImageView) findViewById(R.id.imgDigitizedSig);
            if (m_le.signatureHasDigitizedSig)
            {
                if (ivDigitizedSig.getDrawable() == null) {
                    String szURL = String.format(Locale.US, "https://%s/Logbook/Public/ViewSig.aspx?id=%d", MFBConstants.szIP, m_le.idFlight);
                    GetDigitizedSigTask gdst = new GetDigitizedSigTask(ivDigitizedSig);
                    gdst.execute(szURL);
                }
            }
            else
                ivDigitizedSig.setVisibility(View.GONE);
        }

        // Aircraft spinner
        Aircraft[] rgSelectibleAircraft = SelectibleAircraft();
        if (rgSelectibleAircraft != null) {
            Spinner sp = (Spinner) findViewById(R.id.spnAircraft);
            for (int i = 0; i < rgSelectibleAircraft.length; i++) {
                if (m_le.idAircraft == rgSelectibleAircraft[i].AircraftID) {
                    sp.setSelection(i);
                    sp.setPrompt("Current Aircraft: " + rgSelectibleAircraft[i].TailNumber);
                    break;
                }
            }
        }

        // Current properties
        if (!m_le.IsExistingFlight() && m_le.rgCustomProperties == null)
            m_le.SyncProperties();
        setUpPropertiesForFlight();

        // Posting Options
        SetCheckState(R.id.ckFacebook, m_po.m_fPostFacebook);
        SetCheckState(R.id.ckTwitter, m_po.m_fTweet);

        updateElapsedTime();
        updatePausePlayButtonState();
    }

    public void FromView() {
        if (getView() == null || m_le == null)
            return;

        // Integer fields
        m_le.cApproaches = IntFromField(R.id.txtApproaches);
        m_le.cFullStopLandings = IntFromField(R.id.txtDayLandings);
        m_le.cLandings = IntFromField(R.id.txtLandings);
        m_le.cNightLandings = IntFromField(R.id.txtFSNightLandings);

        // Double fields
        m_le.decCFI = DoubleFromField(R.id.txtCFI);
        m_le.decDual = DoubleFromField(R.id.txtDual);
        m_le.decGrndSim = DoubleFromField(R.id.txtGround);
        m_le.decIMC = DoubleFromField(R.id.txtIMC);
        m_le.decNight = DoubleFromField(R.id.txtNight);
        m_le.decPIC = DoubleFromField(R.id.txtPIC);
        m_le.decSIC = DoubleFromField(R.id.txtSIC);
        m_le.decSimulatedIFR = DoubleFromField(R.id.txtSimIMC);
        m_le.decTotal = DoubleFromField(R.id.txtTotal);
        m_le.decXC = DoubleFromField(R.id.txtXC);

        m_le.hobbsStart = DoubleFromField(R.id.txtHobbsStart);
        m_le.hobbsEnd = DoubleFromField(R.id.txtHobbsEnd);

        // Date - no-op because it should be in sync
        // Flight/Engine times - ditto

        // checkboxes
        m_le.fHold = CheckState(R.id.ckHold);
        m_le.fPublic = CheckState(R.id.ckMyFlightbook);

        // And strings
        m_le.szComments = StringFromField(R.id.txtComments);
        m_le.szRoute = StringFromField(R.id.txtRoute);

        // Aircraft spinner
        m_le.idAircraft = selectedAircraftID();

        // Posting options
        m_po.m_fPostFacebook = CheckState(R.id.ckFacebook);
        m_po.m_fTweet = CheckState(R.id.ckTwitter);
    }

    int selectedAircraftID() {
        Aircraft[] rgSelectibleAircraft = SelectibleAircraft();
        if (rgSelectibleAircraft != null && rgSelectibleAircraft.length > 0) {
            Spinner sp = (Spinner) findViewById(R.id.spnAircraft);
            return ((Aircraft) sp.getSelectedItem()).AircraftID;
        }
        return -1;
    }

    private void AutoTotals() {
        double dtHobbs = 0;
        double dtTotal = 0;
        double dtFlight = 0;
        double dtEngine = 0;

        FromView();

        // compute the flight time, in hours, if known
        if (m_le.isKnownFlightTime())
            dtFlight = (m_le.dtFlightEnd.getTime() - m_le.dtFlightStart.getTime() - totalTimePaused()) / MFBConstants.MS_PER_HOUR;

        if (m_le.isKnownEngineTime())
            dtEngine = (m_le.dtEngineEnd.getTime() - m_le.dtEngineStart.getTime() - totalTimePaused()) / MFBConstants.MS_PER_HOUR;

        // NOTE: we do NOT subtract totalTimePaused here because hobbs should already have subtracted pause time,
        // whether from being entered by user (hobbs on airplane pauses on ground or with engine stopped)
        // or from this being called by autohobbs (which has already subtracted it)
        if (m_le.hobbsStart > 0 && m_le.hobbsEnd > m_le.hobbsStart)
            dtHobbs = m_le.hobbsEnd - m_le.hobbsStart; // hobbs is already in hours

        // do autotime
        switch (MFBLocation.fPrefAutoFillTime) {
            case EngineTime:
                dtTotal = dtEngine;
                break;
            case FlightTime:
                dtTotal = dtFlight;
                break;
            case HobbsTime:
                dtTotal = dtHobbs;
                break;
            default:
                break;
        }

        if (dtTotal > 0) {
            Boolean fIsReal = true;
            Spinner sp = (Spinner) findViewById(R.id.spnAircraft);

            if (MFBLocation.fPrefRoundNearestTenth)
                dtTotal = Math.round(dtTotal * 10.0) / 10.0;

            if (m_le.idAircraft > 0 && sp.getSelectedItem() != null)
                fIsReal = (((Aircraft) sp.getSelectedItem()).InstanceTypeID == 1);

            // update totals and XC if this is a real aircraft, else ground sim
            if (fIsReal) {
                m_le.decTotal = dtTotal;
                m_le.decXC = (Airport.MaxDistanceForRoute(m_le.szRoute) > MFBConstants.NM_FOR_CROSS_COUNTRY) ? dtTotal : 0.0;
            } else
                m_le.decGrndSim = dtTotal;

            ToView();
        }
    }

    private void AutoHobbs() {
        long dtHobbs = 0;
        long dtFlight = 0;
        long dtEngine = 0;

        FromView();

        // compute the flight time, in ms, if known
        if (m_le.isKnownFlightTime())
            dtFlight = m_le.dtFlightEnd.getTime() - m_le.dtFlightStart.getTime();

        // and engine time, if known.
        if (m_le.isKnownEngineTime())
            dtEngine = m_le.dtEngineEnd.getTime() - m_le.dtEngineStart.getTime();

        if (m_le.hobbsStart > 0) {
            switch (MFBLocation.fPrefAutoFillHobbs) {
                case EngineTime:
                    dtHobbs = dtEngine;
                    break;
                case FlightTime:
                    dtHobbs = dtFlight;
                    break;
                default:
                    break;
            }

            dtHobbs -= totalTimePaused();

            if (dtHobbs > 0) {
                m_le.hobbsEnd = m_le.hobbsStart + (dtHobbs / MFBConstants.MS_PER_HOUR);
                ToView(); // sync the view to the change we just made - especially since autototals can read it.

                // if total is linked to hobbs, need to do autotime too
                if (MFBLocation.fPrefAutoFillTime == MFBLocation.AutoFillOptions.HobbsTime)
                    AutoTotals();
            }
        }
    }

    private void resetDateOfFlight() {
        if (m_le != null && m_le.isEmptyFlight()) {
            // set the date of the flight to now in local time.
            m_le.dtFlight = new Date();
            SetLocalDateForField(R.id.btnFlightSet, m_le.dtFlight);
        }
    }

    private void EngineStart() {
        // don't do any GPS stuff unless this is a new flight
        if (!m_le.IsNewFlight())
            return;

        resetDateOfFlight();

        AppendNearest();
        MFBLocation.GetMainLocation().setIsRecording(true); // will respect preference
        ShowRecordingIndicator();
        if (MFBConstants.fFakeGPS) {
            MFBLocation.GetMainLocation().stopListening(getContext());
            MFBLocation.GetMainLocation().setIsRecording(true); // will respect preference
            GPSSim gpss = new GPSSim(MFBLocation.GetMainLocation());
            gpss.FeedEvents();
        }
    }

    private void EngineStop() {
        // don't do any GPS stuff unless this is a new flight
        if (!m_le.IsNewFlight())
            return;

        AppendNearest();
        MFBLocation.GetMainLocation().setIsRecording(false);
        AutoHobbs();
        AutoTotals();
        ShowRecordingIndicator();
        unPauseFlight();
    }

    private void FlightStart() {
        // don't do any GPS stuff unless this is a new flight
        if (!m_le.IsNewFlight())
            return;

        if (!m_le.isKnownEngineStart())
            resetDateOfFlight();

        MFBLocation.GetMainLocation().setIsRecording(true);
        AppendNearest();
        unPauseFlight(); // don't pause in flight
    }

    private void FlightStop() {
        // don't do any GPS stuff unless this is a new flight
        if (!m_le.IsNewFlight())
            return;

        AppendNearest();
    }

    private void ShowRecordingIndicator() {
        Assert.assertNotNull("No location object in ShowRecordingIndicator", MFBLocation.GetMainLocation());
        imgRecording.setVisibility(MFBLocation.GetMainLocation().getIsRecording() ? View.VISIBLE : View.INVISIBLE);
    }

    private SimpleDateFormat dfSunriseSunset = null;

    public void UpdateStatus(MFBLocation.GPSQuality quality, Boolean fAirborne, Location loc,
                             Boolean fRecording) {
        ShowRecordingIndicator();

        Resources res = getResources();

        int idSzQuality;

        switch (quality) {
            case Excellent:
                idSzQuality = R.string.lblGPSExcellent;
                break;
            case Good:
                idSzQuality = R.string.lblGPSGood;
                break;
            case Poor:
                idSzQuality = R.string.lblGPSPoor;
                break;
            default:
                idSzQuality = R.string.lblGPSUnknown;
                break;
        }

        if (loc != null) {
            txtQuality.setText(res.getString(idSzQuality));
            double Speed = loc.getSpeed() * MFBConstants.MPS_TO_KNOTS;
            txtSpeed.setText(Speed < 1.0 || !loc.hasSpeed() ? res.getString(R.string.lblNoSpeed) : String.format(Locale.getDefault(), "%.1fkts", Speed));
            txtAltitude.setText((loc.hasAltitude() ? String.format(Locale.getDefault(), "%d%s", (int) Math.round(loc.getAltitude() * MFBConstants.METERS_TO_FEET), getString(R.string.lblFeet)) : getString(R.string.lblNoAltitude)));
            double lat = loc.getLatitude();
            double lon = loc.getLongitude();
            txtLatitude.setText(String.format(Locale.getDefault(), "%.5f°%s", Math.abs(lat), lat > 0 ? "N" : "S"));
            txtLongitude.setText(String.format(Locale.getDefault(), "%.5f°%s", Math.abs(lon), lon > 0 ? "E" : "W"));
            SunriseSunsetTimes sst = new SunriseSunsetTimes(new Date(loc.getTime()), lat, lon);
            if (dfSunriseSunset == null)
                dfSunriseSunset = new SimpleDateFormat("hh:mm a z", Locale.getDefault());

            txtSunrise.setText(dfSunriseSunset.format(sst.Sunrise));
            txtSunset.setText(dfSunriseSunset.format(sst.Sunset));
        }

        // don't show in-air/on-ground if we aren't actually detecting these
        if (MFBLocation.fPrefAutoDetect)
            txtStatus.setText(res.getString(fAirborne ? R.string.lblFlightInAir : R.string.lblFlightOnGround));
        else
            txtStatus.setText(res.getString(R.string.lblGPSUnknown));

        if (fAirborne)
            unPauseFlight();
    }

    // Pause/play functionality
    private long timeSinceLastPaused() {
        if (ActNewFlight.fPaused)
            return (new Date()).getTime() - ActNewFlight.dtTimeOfLastPause;
        else
            return 0;
    }

    private long totalTimePaused() {
        return ActNewFlight.dtPauseTime + timeSinceLastPaused();
    }

    private void pauseFlight() {
        ActNewFlight.dtTimeOfLastPause = (new Date()).getTime();
        ActNewFlight.fPaused = true;
    }

    public void unPauseFlight() {
        if (ActNewFlight.fPaused) {
            ActNewFlight.dtPauseTime += timeSinceLastPaused();
            ActNewFlight.fPaused = false; // do this AFTER calling [self timeSinceLastPaused]
        }
    }

    private void updateElapsedTime() {            // update the button state
        ImageButton ib = (ImageButton) findViewById(R.id.btnPausePlay);
        // pause/play should only be visible on ground with engine running (or flight start known but engine end unknown)
        Boolean fShowPausePlay = !MFBLocation.IsFlying && (m_le.isKnownEngineStart() || m_le.isKnownFlightStart()) && !m_le.isKnownEngineEnd();
        ib.setVisibility(fShowPausePlay ? View.VISIBLE : View.INVISIBLE);

        TextView txtElapsed = (TextView) findViewById(R.id.txtElapsedTime);

        if (txtElapsed != null) {
            long dtTotal;
            long dtFlight = 0;
            long dtEngine = 0;

            Boolean fIsKnownFlightStart = m_le.isKnownFlightStart();
            Boolean fIsKnownEngineStart = m_le.isKnownEngineStart();

            if (fIsKnownFlightStart) {
                if (!m_le.isKnownFlightEnd()) // in flight
                    dtFlight = (new Date()).getTime() - m_le.dtFlightStart.getTime();
                else
                    dtFlight = m_le.dtFlightEnd.getTime() - m_le.dtFlightStart.getTime();
            }

            if (fIsKnownEngineStart) {
                if (!m_le.isKnownEngineEnd())
                    dtEngine = (new Date()).getTime() - m_le.dtEngineStart.getTime();
                else
                    dtEngine = m_le.dtEngineEnd.getTime() - m_le.dtEngineStart.getTime();
            }

            // if totals mode is FLIGHT TIME, then elapsed time is based on flight time if/when it is known.
            // OTHERWISE, we use engine time (if known) or else flight time.
            if (MFBLocation.fPrefAutoFillTime == MFBLocation.AutoFillOptions.FlightTime)
                dtTotal = fIsKnownFlightStart ? dtFlight : 0;
            else
                dtTotal = fIsKnownEngineStart ? dtEngine : dtFlight;

            dtTotal -= this.totalTimePaused();
            if (dtTotal <= 0)
                dtTotal = 0; // should never happen

            // dtTotal is in milliseconds - convert it to seconds for ease of reading
            dtTotal /= 1000.0;
            String sTime = String.format(Locale.US, "%02d:%02d:%02d", (int) (dtTotal / 3600), ((((int) dtTotal) % 3600) / 60), ((int) dtTotal) % 60);
            txtElapsed.setText(sTime);
        }

        ShowRecordingIndicator();
    }

    private void updatePausePlayButtonState() {
        // update the button state
        ImageButton ib = (ImageButton) findViewById(R.id.btnPausePlay);
        ib.setImageResource(ActNewFlight.fPaused ? R.drawable.play : R.drawable.pause);
    }

    private void toggleFlightPause() {
        // don't pause or play if we're not flying/engine started
        if (m_le.isKnownFlightStart() || m_le.isKnownEngineStart()) {
            if (ActNewFlight.fPaused)
                unPauseFlight();
            else
                pauseFlight();
        } else {
            unPauseFlight();
            ActNewFlight.dtPauseTime = 0;
        }

        updateElapsedTime();
        updatePausePlayButtonState();
    }

    /*
     * (non-Javadoc)
     * @see com.myflightbook.android.ActMFBForm.GallerySource#getGalleryID()
     * GallerySource protocol
     */
    public int getGalleryID() {
        return R.id.tblImageTable;
    }

    public View getGalleryHeader() {
        return findViewById(R.id.txtImageHeader);
    }

    public MFBImageInfo[] getImages() {
        if (m_le == null || m_le.rgFlightImages == null)
            return new MFBImageInfo[0];
        else
            return m_le.rgFlightImages;
    }

    public void setImages(MFBImageInfo[] rgmfbii) {
        if (m_le == null) {
            throw new NullPointerException("m_le is null in setImages");
        }
        m_le.rgFlightImages = rgmfbii;
    }

    public void newImage(MFBImageInfo mfbii) {
        Log.w(MFBConstants.LOG_TAG, String.format("newImage called. m_le is %s", m_le == null ? "null" : "not null"));
        m_le.AddImageForflight(mfbii);
    }

    public void refreshGallery() {
        setUpGalleryForFlight();
    }

    public PictureDestination getDestination() {
        return PictureDestination.FlightImage;
    }

    public void invalidate() {
        ResetFlight(false);
    }

    private void UpdateIfChanged(int id, int value) {
        if (IntFromField(id) != value)
            SetIntForField(id, value);
    }

    // Update the fields which could possibly have changed via auto-detect
    public void RefreshDetectedFields() {
        SetUTCDateForField(R.id.btnFlightStartSet, m_le.dtFlightStart);
        SetUTCDateForField(R.id.btnFlightEndSet, m_le.dtFlightEnd);

        UpdateIfChanged(R.id.txtLandings, m_le.cLandings);
        UpdateIfChanged(R.id.txtFSNightLandings, m_le.cNightLandings);
        UpdateIfChanged(R.id.txtDayLandings, m_le.cFullStopLandings);
        SetDoubleForField(R.id.txtNight, m_le.decNight);

        SetStringForField(R.id.txtRoute, m_le.szRoute);
    }

    private void setUpPropertiesForFlight() {
        LayoutInflater l = getActivity().getLayoutInflater();
        TableLayout tl = (TableLayout) findViewById(R.id.tblPinnedProperties);
        if (tl == null)
            return;
        tl.removeAllViews();

        if (m_le == null)
            return;

        m_le.SyncProperties();

        if (m_le.rgCustomProperties == null)
            return;

        HashSet<Integer> pinnedProps = CustomPropertyType.getPinnedProperties(getActivity().getSharedPreferences(CustomPropertyType.prefSharedPinnedProps, Activity.MODE_PRIVATE));

        CustomPropertyType[] rgcptAll = CustomPropertyTypesSvc.getCachedPropertyTypes();
        if (rgcptAll == null)
            return;

        FlightProperty[] rgProps = FlightProperty.CrossProduct(m_le.rgCustomProperties, rgcptAll);

        for (FlightProperty fp : rgProps) {
            if (fp.CustomPropertyType() == null)
                fp.RefreshPropType();

            Boolean fIsPinned = CustomPropertyType.isPinnedProperty(pinnedProps, fp.idPropType);


            if (!fIsPinned && fp.IsDefaultValue())
                continue;

            TableRow tr = (TableRow) l.inflate(R.layout.cpttableitem, tl, false);
            tr.setId(View.generateViewId());

            PropertyEdit pe = tr.findViewById(R.id.propEdit);
            pe.InitForProperty(fp, tr.getId(), this, (fp.CustomPropertyType().idPropType == CustomPropertyType.idPropTypeTachStart) ? (CrossFillDelegate) sender -> {
                Double d = Aircraft.getHighWaterTachForAircraft(selectedAircraftID());
                if (d > 0)
                    sender.setDoubleValue(d);
            } : this);

            tr.findViewById(R.id.imgFavorite).setVisibility(fIsPinned ? View.VISIBLE : View.INVISIBLE);

            tl.addView(tr, new TableLayout.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

        }
    }

    //region in-line property editing
    @SuppressLint("StaticFieldLeak")
    private class DeletePropertyTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog m_pd = null;
        FlightProperty fp;

        @Override
        protected Boolean doInBackground(Void... params) {
            FlightPropertiesSvc fpsvc = new FlightPropertiesSvc();
            fpsvc.DeletePropertyForFlight(AuthToken.m_szAuthToken, fp.idFlight, fp.idProp, getContext());
            return true;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(ActNewFlight.this, ActNewFlight.this.getString(R.string.prgDeleteProp));
        }

        protected void onPostExecute(Boolean b) {
            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            setUpPropertiesForFlight();
        }
    }

    private void deleteDefaultedProperty(FlightProperty fp) {
        if (fp.idFlight > 0 && fp.IsDefaultValue()) {
            DeletePropertyTask dpt = new DeletePropertyTask();
            dpt.fp = fp;
            dpt.execute();
            return;
        }

        // Otherwise, save it
        FlightProperty[] rgProps = FlightProperty.CrossProduct(m_le.rgCustomProperties, CustomPropertyTypesSvc.getCachedPropertyTypes());
        FlightProperty[] rgfpUpdated = FlightProperty.DistillList(rgProps);
        FlightProperty.RewritePropertiesForFlight(m_le.idLocalDB, rgfpUpdated);

    }

    @Override
    public void updateProperty(int id, FlightProperty fp) {
        if (fp == null || m_le == null) // this can get called by PropertyEdit as it loses focus.
            return;

        FlightProperty[] rgProps = FlightProperty.CrossProduct(m_le.rgCustomProperties, CustomPropertyTypesSvc.getCachedPropertyTypes());
        for (int i = 0; i < rgProps.length; i++) {
            if (rgProps[i].idPropType == fp.idPropType) {
                rgProps[i] = fp;
                deleteDefaultedProperty(fp);
                FlightProperty.RewritePropertiesForFlight(m_le.idLocalDB, FlightProperty.DistillList(rgProps));
                m_le.SyncProperties();
                break;
            }
        }
    }
    //endregion
}
