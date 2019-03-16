/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2019 MyFlightbook, LLC

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

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteReadOnlyDatabaseException;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;

import com.myflightbook.android.WebServices.AuthToken;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import Model.Airport;
import Model.CustomExceptionHandler;
import Model.DataBaseHelper;
import Model.DecimalEdit;
import Model.GPSSim;
import Model.LogbookEntry;
import Model.MFBConstants;
import Model.MFBFlightListener;
import Model.MFBLocation;
import Model.MFBTakeoffSpeed;
import Model.MFBUtil;
import Model.Telemetry;

public class MFBMain extends AppCompatActivity implements OnTabChangeListener {

    interface Invalidatable {
        void invalidate();
    }

    static private final ArrayList<Invalidatable> rgNotifyDataChanged = new ArrayList<>();
    static private final ArrayList<Invalidatable> rgNotifyResetAll = new ArrayList<>();

    // preferences keys.
    static private final String m_KeyszUser = "username";
    static private final String m_KeyszPass = "password";
    static private final String m_KeyszAuthToken = "authtoken";

    static private final String m_KeysfRecord = "recordflight";
    static private final String m_KeysfRecordHighRes = "recordFlightHighRes";
    static private final String m_KeysfAutoDetect = "autodetect";
    static private final String m_KeysfHeliports = "heliports";
    static private final String m_KeysfRoundToTenth = "roundnearesttenth";

    static private final String m_KeysAutoHobbs = "autohobbs";
    static private final String m_KeysAutoTime = "autotime";
    static private final String m_KeysUseHHMM = "UseHHMM";
    static private final String m_KeysUseLocal = "UseLocalTime";

    static private final String m_KeysHasSeenWarning = "seenWarning";
    static private final String m_KeysLastTab = "lastTab3";

    static private final String m_KeysShowFlightImages = "showFlightImages";
    static private final String m_KeysShowFlightTimes = "showFlightTimes";

    static private final String m_KeysIsFlying = "isFlying";
    static private final String m_KeysIsRecording = "isRecording";
    static private final String m_KeysHasPendingFSLanding = "hasPendingLanding";

    static private final String m_KeysTOSpeed = "takeoffspeed";
    static private final String m_KeysNightFlightOption = "nightFlightOption";
    static private final String m_KeysNightLandingOption = "nightLandingOption";

    static private final String m_KeysNightMode = "nightModeOption";
    static public int NightModePref = AppCompatDelegate.MODE_NIGHT_NO;

    static private final String m_TimeOfLastVacuum = "LastVacuum";

    private SharedPreferences mPrefs;
    private Boolean m_fSeenWarning = false;
    private long mLastVacuum = 0; // ms of last vacuum

    private TabHost mTabHost = null;
    private final HashMap<String, TabInfo> mapTabInfo = new HashMap<>();
    private TabInfo mLastTab = null;
    private int mLastTabIndex = -1;

    public static DataBaseHelper mDBHelper = null;
    public static DataBaseHelper mDBHelperAirports = null;

    private static MFBFlightListener m_FlightEventListener = null;

    public static String versionName = "";
    public static int versionCode = 0;

    public static final String ACTION_VIEW_CURRENCY = "com.myflightbook.android.VIEWCURRENCY";
    public static final String ACTION_VIEW_TOTALS = "com.myflightbook.android.VIEWTOTALS";
    private static final String ACTION_START_ENGINE = "com.myflightbook.android.STARTENGINE";
    private static final String ACTION_STOP_ENGINE = "com.myflightbook.android.STOPENGINE";
    private static final String ACTION_PAUSE_FLIGHT = "com.myflightbook.android.PAUSEFLIGHT";
    private static final String ACTION_RESUME_FLIGHT = "com.myflightbook.android.RESUMEFLIGHT";
    private static String pendingAction = null;

    private static Resources m_Resources = null;
    public static String getResourceString(int id) {
        return m_Resources == null ? "" : m_Resources.getString(id);
    }

    public static Resources getAppResources() {
        return m_Resources;
    }

    private static String m_filesPath = null;
    public static String getAppFilesPath() {return m_filesPath; }

    private static final int PERMISSION_REQUEST_READ = 3385;

    private static class ImportTelemetryTask extends AsyncTask<Uri, Void, LogbookEntry> {
        private ProgressDialog m_pd = null;
        private final AsyncWeakContext<MFBMain> m_ctxt;

        ImportTelemetryTask(Context c, MFBMain m) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, m);
        }

        @Override
        protected LogbookEntry doInBackground(Uri... urls) {
            LogbookEntry leResult = null;
            if (urls != null && urls.length > 0) {

                Telemetry t = Telemetry.TelemetryFromURL(urls[0], this.m_ctxt.getContext());

                if (t != null) {
                    try {
                        leResult = GPSSim.ImportTelemetry(m_ctxt.getCallingActivity(), t.Samples(), urls[0]);
                    } catch (IOException | XmlPullParserException e) {
                        e.printStackTrace();
                    }
                }
            }
            return leResult;
        }

        @Override
        protected void onPreExecute() {
            Context c = m_ctxt.getContext();
            if (c != null)
                m_pd = MFBUtil.ShowProgress(c, c.getString(R.string.telemetryImportProgress));
        }

        @Override
        protected void onPostExecute(LogbookEntry le) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            Context c = m_ctxt.getContext();
            MFBMain m = m_ctxt.getCallingActivity();

            if (c == null)
                return;

            if (le == null)
                MFBUtil.Alert(c, c.getString(R.string.txtError), c.getString(R.string.telemetryCantIdentify));
            else if (le.szError != null && le.szError.length() > 0)
                MFBUtil.Alert(c, c.getString(R.string.txtError), le.szError);
            else
                MFBUtil.Alert(c, c.getString(R.string.txtSuccess), c.getString(R.string.telemetryImportSuccessful));

            if (m == null)
                return;

            // clear the URL from the intent.
            Intent i = m.getIntent();
            if (i != null)
                i.setData(null);

            TabInfo ti = m.mapTabInfo.get(MFBConstants.tabRecents);
            if (ti.fragment != null)
                ((ActRecentsWS) ti.fragment).refreshRecentFlights(false);
        }
    }

    private class TabInfo {
        private final String tag;
        private final Class<?> clss;
        private final Bundle args;
        private Fragment fragment;

        TabInfo(String tag, Class<?> clazz, Bundle args) {
            this.tag = tag;
            this.clss = clazz;
            this.args = args;
        }
    }

    private class TabFactory implements TabContentFactory {

        private final Context mContext;

        TabFactory(Context context) {
            mContext = context;
        }

        public View createTabContent(String tag) {
            View v = new View(mContext);
            v.setMinimumWidth(0);
            v.setMinimumHeight(0);
            return v;
        }
    }

    private static void addTab(MFBMain activity, TabHost tabHost, TabHost.TabSpec tabSpec, TabInfo tabInfo) {
        // Attach a Tab view factory to the spec
        tabSpec.setContent(activity.new TabFactory(activity));
        String tag = tabSpec.getTag();

        // Check to see if we already have a fragment for this tab, probably
        // from a previously saved state.  If so, deactivate it, because our
        // initial state is that a tab isn't shown.
        tabInfo.fragment = activity.getSupportFragmentManager().findFragmentByTag(tag);
        if (tabInfo.fragment != null && !tabInfo.fragment.isDetached()) {
            FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
            ft.detach(tabInfo.fragment);
            ft.commit();
            activity.getSupportFragmentManager().executePendingTransactions();
        }

        tabHost.addTab(tabSpec);
    }

    @SuppressWarnings("SameReturnValue")
    private String paddedTabLabel() {
        return "";
    }

    private void refreshAuth() {
        // finally, sign in if necessary.
        // a) if we're already signed in, just go to the last tab.
        // b) if we're not already signed in or need a refresh, refresh as necessary
        // c) else, just go to the options tab to sign in from there.
        AuthToken auth = new AuthToken();
        if (!auth.HasValidCache()) {
            mTabHost.setCurrentTabByTag(MFBConstants.tabOptions);
            if (auth.FHasCredentials()) {
                RefreshTask rt = new RefreshTask(getApplicationContext(), this);
                rt.execute(auth);
            }
        }
    }

    private void initDB(Boolean fRetryOnFailure) {
        SQLiteDatabase db = null;

        // set up the sqlite db
        mDBHelper = new DataBaseHelper(this, DataBaseHelper.DB_NAME_MAIN,
                MFBConstants.DBVersionMain);
        try {
            mDBHelper.createDataBase();

            // force any upgrade.
            db = mDBHelper.getWritableDatabase();
        } catch (Error ioe) {
            MFBUtil.Alert(this, getString(R.string.txtError),
                    getString(R.string.errMainDBFailure));
        } catch (SQLiteReadOnlyDatabaseException ex) {
            // shouldn't happen, but does due to lollipop bug
            if (fRetryOnFailure)
                initDB(false);
        } finally {
            if (db != null)
                db.close();
            db = null;
        }

        mDBHelperAirports = new DataBaseHelper(this,
                DataBaseHelper.DB_NAME_AIRPORTS, MFBConstants.DBVersionAirports);
        try {
            mDBHelperAirports.createDataBase();
            // force any upgrade.
            db = mDBHelperAirports.getWritableDatabase();
        } catch (Error ioe) {
            MFBUtil.Alert(this, getString(R.string.txtError),
                    getString(R.string.errAirportDBFailure));
        } catch (SQLiteReadOnlyDatabaseException ex) {
            // shouldn't happen, but does due to lollipop bug
            if (fRetryOnFailure)
                initDB(false);
        } finally {
            if (db != null)
                db.close();
        }
    }

    //  Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // get cached auth credentials; restore state prior to restoring location.
        mPrefs = getPreferences(MODE_PRIVATE);

        // TOTALLY MESSED UP
        // WebView screws up night mode, but this code, for some reason, fixes it
        // see https://stackoverflow.com/questions/44035654/broken-colors-in-daynight-theme-after-loading-admob-firebase-ad
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightMode != AppCompatDelegate.MODE_NIGHT_NO)
            new WebView(this);

        MFBMain.NightModePref = mPrefs.getInt(m_KeysNightMode, AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(MFBMain.NightModePref);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        m_Resources = getResources();
        m_filesPath = getFilesDir().getAbsolutePath();

        // Get the version name/code for crash reports
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
        }

        initDB(true);

        RestoreState();

        // set up for web services
        AuthToken.APPTOKEN = getString(R.string.WebServiceKey);

        CustomExceptionHandler ceh = new CustomExceptionHandler(getCacheDir().getAbsolutePath(), String.format(MFBConstants.urlCrashReport, MFBConstants.szIP));
        Thread.setDefaultUncaughtExceptionHandler(ceh);

        ceh.sendPendingReports();

        // Set up the tabs
        mTabHost = findViewById(android.R.id.tabhost);
        mTabHost.setup();
        mTabHost.setBackgroundColor(0xFFffffff);
        mTabHost.getTabWidget().setDividerDrawable(null);
        TabInfo tabInfo;
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabNewFlight).setIndicator(paddedTabLabel(), ContextCompat.getDrawable(this, R.drawable.ic_tab_newflight)), (tabInfo = new TabInfo(MFBConstants.tabNewFlight, ActNewFlight.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabAircraft).setIndicator(paddedTabLabel(), ContextCompat.getDrawable(this, R.drawable.ic_tab_aircraft)), (tabInfo = new TabInfo(MFBConstants.tabAircraft, ActAircraft.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabRecents).setIndicator(paddedTabLabel(), ContextCompat.getDrawable(this, R.drawable.ic_tab_recents)), (tabInfo = new TabInfo(MFBConstants.tabRecents, ActRecentsWS.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabCurrency).setIndicator(paddedTabLabel(), ContextCompat.getDrawable(this, R.drawable.ic_tab_currency)), (tabInfo = new TabInfo(MFBConstants.tabCurrency, ActCurrency.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabTotals).setIndicator(paddedTabLabel(), ContextCompat.getDrawable(this, R.drawable.ic_tab_totals)), (tabInfo = new TabInfo(MFBConstants.tabTotals, ActTotals.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabVisitedAirports).setIndicator(paddedTabLabel(), ContextCompat.getDrawable(this, R.drawable.ic_tab_visitedairport)), (tabInfo = new TabInfo(MFBConstants.tabVisitedAirports, ActVisitedAirports.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabTraining).setIndicator(paddedTabLabel(), ContextCompat.getDrawable(this, R.drawable.ic_tab_training)), (tabInfo = new TabInfo(MFBConstants.tabTraining, ActTraining.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabOptions).setIndicator(paddedTabLabel(), ContextCompat.getDrawable(this, R.drawable.ic_tab_profile)), (tabInfo = new TabInfo(MFBConstants.tabOptions, ActOptions.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);

        mTabHost.setOnTabChangedListener(this);

        // try to restore the previous tab
        if (mLastTabIndex < mTabHost.getTabWidget().getTabCount()) {
            mTabHost.setCurrentTab(mLastTabIndex);
            if (mLastTabIndex == 0)    // hack: default tab index is already 0, so if we're restoring to first tab, need to force it.
                this.onTabChanged(MFBConstants.tabNewFlight);
        } else
            this.mTabHost.setCurrentTabByTag(MFBConstants.tabOptions);

        // Periodically (every 7 days) vacuum (compact) the database.
        long t = new Date().getTime();
        if ((t - mLastVacuum) > 1000 * 3600 * 24 * 7) {
            SQLiteDatabase db = mDBHelper.getWritableDatabase();
            try {
                Log.w(MFBConstants.LOG_TAG, "running VACUUM command");
                db.execSQL("VACUUM");
                mLastVacuum = t;
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, "VACUUM failed: " + e.getLocalizedMessage());
            }
        }

        if (!m_fSeenWarning) {
            MFBUtil.Alert(this, this.getString(R.string.errWarningTitle), this.getString(R.string.lblWarning2));
            m_fSeenWarning = true;
            SaveState();
        }

        refreshAuth();

        OpenRequestedTelemetry();

        // handle shortcuts
        Intent i = getIntent();
        String szAction = i.getAction();
        if (szAction != null) {
            switch (szAction) {
                case ACTION_VIEW_CURRENCY:
                    this.mTabHost.setCurrentTabByTag(MFBConstants.tabCurrency);
                    break;
                case ACTION_VIEW_TOTALS:
                    this.mTabHost.setCurrentTabByTag(MFBConstants.tabTotals);
                    break;
                default:
                    this.mTabHost.setCurrentTabByTag(MFBConstants.tabNewFlight);
                    MFBMain.pendingAction = szAction;
                    break;
            }
        }
    }

    private void OpenRequestedTelemetry() {
        Intent i = getIntent();
        if (i != null) {
            Uri uri = i.getData();

            if (uri != null) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_READ);
                    return;
                }

                new AlertDialog.Builder(this, R.style.MFBDialog)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.lblConfirm)
                        .setMessage(R.string.telemetryImportPrompt)
                        .setPositiveButton(R.string.lblOK, (dialog, which) -> new ImportTelemetryTask(this, this).execute(uri))
                        .setNegativeButton(R.string.lblCancel, null)
                        .show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_READ:
                if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    OpenRequestedTelemetry();
                }
                return;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setDynamicShortcuts() {
        // 4 possible dynamic shortcuts:
        // Start/stop engine
        // puase/play.
        // If engine is not started, only show start.
        // if engine is started, show stop and pause/play

        if (Build.VERSION.SDK_INT >= 25) {

            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);

            if (shortcutManager != null && MFBLocation.GetMainLocation() != null) {
                ArrayList<ShortcutInfo> lst = new ArrayList<>();

                boolean fFlightStarted = MFBMain.getNewFlightListener().shouldKeepListening();
                boolean fPaused = ActNewFlight.fPaused;
                LogbookEntry le = MFBMain.getNewFlightListener().getInProgressFlight(this);
                boolean fFlightEnded = le != null && le.isKnownEngineEnd();

                if (fFlightStarted) {
                    lst.add(new ShortcutInfo.Builder(this, "startEngine")
                            .setShortLabel(getString(R.string.shortcutStopEngine))
                            .setLongLabel(getString(R.string.shortcutStopEngine))
                            .setIcon(Icon.createWithResource(this, R.drawable.ic_action_stop))
                            .setIntent(new Intent(this, MFBMain.class).setAction(ACTION_STOP_ENGINE))
                            .build());
                    if (fPaused)
                        lst.add(new ShortcutInfo.Builder(this, "resume")
                            .setShortLabel(getString(R.string.shortcutResume))
                            .setLongLabel(getString(R.string.shortcutResume))
                            .setIcon(Icon.createWithResource(this, R.drawable.ic_action_play))
                                .setIntent(new Intent(this, MFBMain.class).setAction(ACTION_RESUME_FLIGHT))
                            .build());
                    else
                        lst.add(new ShortcutInfo.Builder(this, "pause")
                                .setShortLabel(getString(R.string.shortcutPause))
                                .setLongLabel(getString(R.string.shortcutPause))
                                .setIcon(Icon.createWithResource(this, R.drawable.ic_action_pause))
                                .setIntent(new Intent(this, MFBMain.class).setAction(ACTION_PAUSE_FLIGHT))
                                .build());
                }
                else if (!fFlightEnded)
                    lst.add(new ShortcutInfo.Builder(this, "startEngine")
                            .setShortLabel(getString(R.string.shortcutStartEngine))
                            .setLongLabel(getString(R.string.shortcutStartEngine))
                            .setIcon(Icon.createWithResource(this, R.drawable.ic_action_play))
                            .setIntent(new Intent(this, MFBMain.class).setAction(ACTION_START_ENGINE))
                            .build());

                // Now add Currency and Totals
                lst.add(new ShortcutInfo.Builder(this, "currency")
                        .setShortLabel(getString(R.string.shortcutCurrency))
                        .setLongLabel(getString(R.string.shortcutCurrency))
                        .setIcon(Icon.createWithResource(this, R.drawable.currency))
                        .setIntent(new Intent(this, MFBMain.class).setAction(ACTION_VIEW_CURRENCY))
                        .build());
                lst.add(new ShortcutInfo.Builder(this, "totals")
                        .setShortLabel(getString(R.string.shortcutTotals))
                        .setLongLabel(getString(R.string.shortcutTotals))
                        .setIcon(Icon.createWithResource(this, R.drawable.totals))
                        .setIntent(new Intent(this, MFBMain.class).setAction(ACTION_VIEW_TOTALS))
                        .build());

                shortcutManager.setDynamicShortcuts(lst);
            }
        }
    }

    private Boolean fIsPaused = false;

    protected void onPause() {
        fIsPaused = true;
        if (MFBLocation.GetMainLocation() != null) {
            // stop listening we aren't supposed to stay awake.
            if (!MFBMain.getNewFlightListener().shouldKeepListening())
                MFBLocation.GetMainLocation().stopListening(this);
        }

        // close the writeable DB, in case it is opened.
        mDBHelper.getWritableDatabase().close();

        setDynamicShortcuts();

        super.onPause();
    }

    private void resumeGPS() {
        if (fIsPaused)
            return;

        Log.e(MFBConstants.LOG_TAG, "GPSCRASH - RESUMEGPS called");
        if (MFBLocation.GetMainLocation() == null)
            MFBLocation.setMainLocation(new MFBLocation(this));
        else
            Log.e(MFBConstants.LOG_TAG, "GPSCRASH - RESUMEGPS Main Location already exists");

        if (MFBLocation.GetMainLocation() != null)
            MFBLocation.GetMainLocation().startListening(this);
    }

    protected void onResume() {
        super.onResume();

        fIsPaused = false;
        // check to see if we need to refresh auth.
        AuthToken at = new AuthToken();
        if (!at.HasValidCache()) {
            RefreshTask rt = new RefreshTask(getApplicationContext(), this);
            rt.execute(at);
        }

        // This is a hack, but we get a lot of crashes about too much time between startForegroundService being
        // called and startForeground being called.
        // Problem is, other tabs' OnResume may not have been called yet, so let's delay this by a few
        // seconds so that all the other startup tasks are done before we call startForegroundService.
        // Note that ActNewFlight will initialize the GPS as needed, so this call will be a no-op at that point.
        new Handler(Looper.getMainLooper()).postDelayed(this::resumeGPS, 3000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // close the DB's
        mDBHelper.getWritableDatabase().close();
        mDBHelperAirports.getReadableDatabase().close();
    }

    public static boolean HasMaps() {
        try {
            // hide the view button if Maps aren't available; if they are available,
            // then hide the button if this is not a new flight.
            Class.forName("com.google.android.maps.MapActivity");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static class RefreshTask extends AsyncTask<AuthToken, Void, Boolean> {
        private ProgressDialog m_pd;
        final AsyncWeakContext<MFBMain> m_ctxt;

        RefreshTask(Context c, MFBMain m) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, m);
        }

        @Override
        protected Boolean doInBackground(AuthToken... arg0) {
            return (arg0[0]).RefreshAuthorization(m_ctxt.getCallingActivity());
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getCallingActivity(), m_ctxt.getContext().getString(R.string.prgSigningIn));
        }

        protected void onPostExecute(Boolean f) {
            MFBMain m = m_ctxt.getCallingActivity();
            if (m == null)
                return;

            if (f)
                m.mTabHost.setCurrentTabByTag(m.mLastTab.tag);
            else
                m.mTabHost.setCurrentTabByTag(MFBConstants.tabOptions);
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }

    private void RestoreState() {
        try {
            AuthToken.m_szAuthToken = mPrefs.getString(m_KeyszAuthToken, "");
            AuthToken.m_szEmail = mPrefs.getString(m_KeyszUser, "");
            AuthToken.m_szPass = mPrefs.getString(m_KeyszPass, "");
            MFBLocation.fPrefAutoDetect = mPrefs.getBoolean(m_KeysfAutoDetect, false);
            MFBLocation.fPrefRecordFlight = mPrefs.getBoolean(m_KeysfRecord, false);
            MFBLocation.fPrefRecordFlightHighRes = mPrefs.getBoolean(m_KeysfRecordHighRes, false);
            MFBLocation.fPrefAutoFillHobbs = MFBLocation.AutoFillOptions.values()[mPrefs.getInt(m_KeysAutoHobbs, 0)];
            MFBLocation.fPrefAutoFillTime = MFBLocation.AutoFillOptions.values()[mPrefs.getInt(m_KeysAutoTime, 0)];
            MFBLocation.fPrefRoundNearestTenth = mPrefs.getBoolean(m_KeysfRoundToTenth, false);

            MFBLocation.IsFlying = MFBLocation.IsFlying || mPrefs.getBoolean(m_KeysIsFlying, false);
            MFBLocation.IsRecording = MFBLocation.IsRecording || mPrefs.getBoolean(m_KeysIsRecording, false);
            MFBLocation.HasPendingLanding = mPrefs.getBoolean(m_KeysHasPendingFSLanding, false);
            MFBLocation.NightPref = MFBLocation.NightCriteria.values()[mPrefs.getInt(m_KeysNightFlightOption, MFBLocation.NightCriteria.EndOfCivilTwilight.ordinal())];
            MFBLocation.NightLandingPref = MFBLocation.NightLandingCriteria.values()[mPrefs.getInt(m_KeysNightLandingOption, MFBLocation.NightLandingCriteria.SunsetPlus60.ordinal())];

            Airport.fPrefIncludeHeliports = mPrefs.getBoolean(m_KeysfHeliports, false);
            DecimalEdit.DefaultHHMM = mPrefs.getBoolean(m_KeysUseHHMM, false);
            DlgDatePicker.fUseLocalTime = mPrefs.getBoolean(m_KeysUseLocal, false);

            ActRecentsWS.fShowFlightImages = mPrefs.getBoolean(m_KeysShowFlightImages, true);
            ActRecentsWS.fShowFlightTimes = mPrefs.getBoolean(m_KeysShowFlightTimes, true);

            m_fSeenWarning = mPrefs.getBoolean(m_KeysHasSeenWarning, false);
            mLastTabIndex = mPrefs.getInt(m_KeysLastTab, 0);
            mLastVacuum = mPrefs.getLong(m_TimeOfLastVacuum, new Date().getTime());
            MFBTakeoffSpeed.setTakeOffSpeedIndex(mPrefs.getInt(m_KeysTOSpeed, MFBTakeoffSpeed.DefaultTakeOffIndex));
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
        }
    }

    private void SaveState() {
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        SharedPreferences.Editor ed = mPrefs.edit();
        ed.putString(m_KeyszAuthToken, AuthToken.m_szAuthToken);
        ed.putString(m_KeyszUser, AuthToken.m_szEmail);
        ed.putString(m_KeyszPass, AuthToken.m_szPass);

        ed.putBoolean(m_KeysfAutoDetect, MFBLocation.fPrefAutoDetect);
        ed.putBoolean(m_KeysfRecord, MFBLocation.fPrefRecordFlight);
        ed.putBoolean(m_KeysfRecordHighRes, MFBLocation.fPrefRecordFlightHighRes);

        ed.putInt(m_KeysAutoHobbs, MFBLocation.fPrefAutoFillHobbs.ordinal());
        ed.putInt(m_KeysAutoTime, MFBLocation.fPrefAutoFillTime.ordinal());
        ed.putBoolean(m_KeysfRoundToTenth, MFBLocation.fPrefRoundNearestTenth);

        ed.putBoolean(m_KeysIsFlying, MFBLocation.IsFlying);
        ed.putBoolean(m_KeysIsRecording, MFBLocation.IsRecording);
        ed.putBoolean(m_KeysHasPendingFSLanding, MFBLocation.HasPendingLanding);
        ed.putInt(m_KeysNightFlightOption, MFBLocation.NightPref.ordinal());
        ed.putInt(m_KeysNightLandingOption, MFBLocation.NightLandingPref.ordinal());

        ed.putBoolean(m_KeysfHeliports, Airport.fPrefIncludeHeliports);
        ed.putBoolean(m_KeysUseHHMM, DecimalEdit.DefaultHHMM);
        ed.putBoolean(m_KeysUseLocal, DlgDatePicker.fUseLocalTime);

        ed.putInt(m_KeysNightMode, MFBMain.NightModePref);

        ed.putBoolean(m_KeysHasSeenWarning, m_fSeenWarning);

        ed.putBoolean(m_KeysShowFlightImages, ActRecentsWS.fShowFlightImages);
        ed.putBoolean(m_KeysShowFlightTimes, ActRecentsWS.fShowFlightTimes);

        ed.putInt(m_KeysLastTab, mTabHost.getCurrentTab());
        ed.putLong(m_TimeOfLastVacuum, mLastVacuum);

        ed.putInt(m_KeysTOSpeed, MFBTakeoffSpeed.getTakeOffSpeedIndex());
        ed.apply();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        SaveState();
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        RestoreState();
    }

    public void onTabChanged(String tag) {

        TabInfo newTab = this.mapTabInfo.get(tag);
        if (mLastTab != newTab) {
            FragmentTransaction ft = this.getSupportFragmentManager().beginTransaction();
            if (mLastTab != null) {
                if (mLastTab.fragment != null) {
                    ft.detach(mLastTab.fragment);
                }
            }
            if (newTab != null) {
                if (newTab.fragment == null) {
                    newTab.fragment = Fragment.instantiate(this,
                            newTab.clss.getName(), newTab.args);
                    ft.add(R.id.realtabcontent, newTab.fragment, newTab.tag);
                } else {
                    ft.attach(newTab.fragment);
                }
            }

            mLastTab = newTab;
            ft.commit();
            this.getSupportFragmentManager().executePendingTransactions();
        }
    }

    public static MFBFlightListener getNewFlightListener() {
        if (MFBMain.m_FlightEventListener == null)
            MFBMain.m_FlightEventListener = new MFBFlightListener();
        return MFBMain.m_FlightEventListener;
    }

    public static void SetInProgressFlightActivity(Context c, MFBFlightListener.ListenerFragmentDelegate d) {
        MFBFlightListener fl = MFBMain.getNewFlightListener().setDelegate(d);
        if (MFBLocation.GetMainLocation() == null)
            MFBLocation.setMainLocation(new MFBLocation(c, fl));
        else
            MFBLocation.GetMainLocation().SetListener(fl);

        if (d != null && pendingAction != null) {
            switch (pendingAction) {
                case ACTION_PAUSE_FLIGHT:
                case ACTION_RESUME_FLIGHT:
                    d.togglePausePlay();
                    break;
                case ACTION_START_ENGINE:
                    d.startEngine();
                    break;
                case ACTION_STOP_ENGINE:
                    d.stopEngine();
                    break;
            }
            pendingAction = null;
        }
    }

    public static void registerNotifyDataChange(Invalidatable o) {
        rgNotifyDataChanged.add(o);
    }

    public static void registerNotifyResetAll(Invalidatable o) {
        rgNotifyResetAll.add(o);
    }

    public static void unregisterNotify(Invalidatable o) {
        rgNotifyDataChanged.remove(o);
        rgNotifyResetAll.remove(o);
    }

    public static void invalidateAll() {
        for (Invalidatable o : rgNotifyResetAll)
            o.invalidate();
    }

    public static void invalidateCachedTotals() {
        for (Invalidatable o : rgNotifyDataChanged)
            o.invalidate();
    }
}
