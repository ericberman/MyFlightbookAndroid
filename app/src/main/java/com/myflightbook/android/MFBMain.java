/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2022 MyFlightbook, LLC

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
import android.util.Log;
import android.webkit.WebView;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.myflightbook.android.webservices.AuthToken;
import com.myflightbook.android.webservices.MFBSoap;
import com.myflightbook.android.webservices.RecentFlightsSvc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import model.Airport;
import model.CustomExceptionHandler;
import model.DataBaseHelper;
import model.DecimalEdit;
import model.GPSSim;
import model.LogbookEntry;
import model.MFBConstants;
import model.MFBFlightListener;
import model.MFBLocation;
import model.MFBTakeoffSpeed;
import model.MFBUtil;
import model.Telemetry;

public class MFBMain extends AppCompatActivity {

    interface Invalidatable {
        void invalidate();
    }

    public enum MFBTab {NewFlight, Aircraft, Recent, Currency, Totals, Visited, Training, Options}

    public static class MFBTabAdapter extends FragmentStateAdapter {
        public MFBTabAdapter(FragmentActivity f) {
            super(f);
        }

        // Return a NEW fragment instance in createFragment(int)
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            MFBTab tab = MFBTab.values()[position];
            switch (tab) {
                case NewFlight:
                    return new ActNewFlight();
                case Aircraft:
                    return new ActAircraft();
                case Recent:
                    return new ActRecentsWS();
                case Currency:
                    return new ActCurrency();
                case Totals:
                    return new ActTotals();
                case Visited:
                    return new ActVisitedAirports();
                case Training:
                    return new ActTraining();
                case Options:
                    return new ActOptions();
                default:
                    throw new RuntimeException(String.format(Locale.getDefault(), "Unknown Tab position requested %d", position));
            }
        }

        @Override
        public int getItemCount() {
            return 8;
        }
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

    static private final String m_KeysLastTab = "lastTab3";

    static private final String m_KeysShowFlightImages = "showFlightImages";
    static private final String m_KeysShowFlightTimes = "showFlightTimes2";

    static private final String m_KeysSpeedUnits = "speedUnits";
    static private final String m_KeysAltUnits = "altUnits";

    static private final String m_KeysIsFlying = "isFlying";
    static private final String m_KeysIsRecording = "isRecording";
    static private final String m_KeysHasPendingFSLanding = "hasPendingLanding";

    static private final String m_KeysTOSpeed = "takeoffspeed";
    static private final String m_KeysNightFlightOption = "nightFlightOption";
    static private final String m_KeysNightLandingOption = "nightLandingOption";

    static private final String m_KeysNightMode = "nightModeOption";
    static public int NightModePref = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

    static private final String m_TimeOfLastVacuum = "LastVacuum";

    private SharedPreferences mPrefs;
    private long mLastVacuum = 0; // ms of last vacuum

    private ViewPager2 mViewPager = null;
    private int mLastTabIndex = -1;

    public static DataBaseHelper mDBHelper = null;
    public static DataBaseHelper mDBHelperAirports = null;

    private static MFBFlightListener m_FlightEventListener = null;

    public static String versionName = "";
    public static int versionCode = 0;

    public static final String ACTION_VIEW_CURRENCY = "com.myflightbook.android.VIEWCURRENCY";
    public static final String ACTION_VIEW_TOTALS = "com.myflightbook.android.VIEWTOTALS";
    private static final String ACTION_VIEW_CURRENT = "com.myflightbook.android.VIEWCURRENT";
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
        private String exceptionError = null;

        ImportTelemetryTask(Context c, MFBMain m) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, m);
        }

        @Override
        protected LogbookEntry doInBackground(Uri... urls) {
            LogbookEntry leResult = null;
            if (urls != null && urls.length > 0) {

                try {
                    Telemetry t = Telemetry.TelemetryFromURL(urls[0], this.m_ctxt.getContext());
                    if (t != null)
                        leResult = GPSSim.ImportTelemetry(m_ctxt.getCallingActivity(), t, urls[0]);
                }
                catch (IOException ex) {
                    exceptionError = ex.getMessage();
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
                MFBUtil.Alert(c, c.getString(R.string.txtError), exceptionError == null ? c.getString(R.string.telemetryCantIdentify) : exceptionError);
            else if (le.szError != null && le.szError.length() > 0)
                MFBUtil.Alert(c, c.getString(R.string.txtError), le.szError);
            else {
                RecentFlightsSvc.ClearCachedFlights();
                MFBUtil.Alert(c, c.getString(R.string.txtSuccess), c.getString(R.string.telemetryImportSuccessful));
            }

            if (m == null)
                return;

            // clear the URL from the intent.
            Intent i = m.getIntent();
            if (i != null)
                i.setData(null);

            m.mViewPager.setCurrentItem(MFBTab.Recent.ordinal());
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
        super.onCreate(savedInstanceState);
        Log.v(MFBConstants.LOG_TAG, "onCreate: about to install splash screen");
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        Log.v(MFBConstants.LOG_TAG, "onCreate: start listening network");
        // Start listening to network change events
        MFBSoap.startListeningNetwork(getApplicationContext());

        // get cached auth credentials; restore state prior to restoring location.
        mPrefs = getPreferences(MODE_PRIVATE);

        // TOTALLY MESSED UP
        // WebView screws up night mode, but this code, for some reason, fixes it
        // see https://stackoverflow.com/questions/44035654/broken-colors-in-daynight-theme-after-loading-admob-firebase-ad
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightMode != Configuration.UI_MODE_NIGHT_NO)
            new WebView(this);

        Log.v(MFBConstants.LOG_TAG, "onCreate: set night pref");
        MFBMain.NightModePref = mPrefs.getInt(m_KeysNightMode, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(MFBMain.NightModePref);

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

        Log.v(MFBConstants.LOG_TAG, "onCreate: about to initialize database");
        initDB(true);

        Log.v(MFBConstants.LOG_TAG, "onCreate: restore state");
        RestoreState();

        // set up for web services
        AuthToken.APPTOKEN = getString(R.string.WebServiceKey);

        Log.v(MFBConstants.LOG_TAG, "onCreate: set custom handler and send error reports");
        CustomExceptionHandler ceh = new CustomExceptionHandler(getCacheDir().getAbsolutePath(), String.format(MFBConstants.urlCrashReport, MFBConstants.szIP));
        Thread.setDefaultUncaughtExceptionHandler(ceh);

        ceh.sendPendingReports();

        Log.v(MFBConstants.LOG_TAG, "onCreate: set up tabs");
        mViewPager = findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(MFBTab.values().length);
        mViewPager.setAdapter(new MFBTabAdapter(this));
        mViewPager.setUserInputEnabled(false);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        new TabLayoutMediator(tabLayout, mViewPager, (tab, position) -> {
            MFBTab tabID = MFBTab.values()[position];
            switch (tabID) {
                case NewFlight:
                    tab.setContentDescription(R.string.tabNewFlight);
                    tab.setText(R.string.tabNewFlight);
                    tab.setIcon(R.drawable.ic_tab_newflight);
                    break;
                case Recent:
                    tab.setContentDescription(R.string.tabRecent);
                    tab.setText(R.string.tabRecent);
                    tab.setIcon(R.drawable.ic_tab_recents);
                    break;
                case Aircraft:
                    tab.setContentDescription(R.string.tabAircraft);
                    tab.setText(R.string.tabAircraft);
                    tab.setIcon(R.drawable.ic_tab_aircraft);
                    break;
                case Totals:
                    tab.setContentDescription(R.string.tabTotals);
                    tab.setText(R.string.tabTotals);
                    tab.setIcon(R.drawable.ic_tab_totals);
                    break;
                case Currency:
                    tab.setContentDescription(R.string.tabCurrency);
                    tab.setText(R.string.tabCurrency);
                    tab.setIcon(R.drawable.ic_tab_currency);
                    break;
                case Visited:
                    tab.setContentDescription(R.string.tabVisited);
                    tab.setText(R.string.tabVisited);
                    tab.setIcon(R.drawable.ic_tab_visitedairport);
                    break;
                case Training:
                    tab.setContentDescription(R.string.tabTraining);
                    tab.setText(R.string.tabTraining);
                    tab.setIcon(R.drawable.ic_tab_training);
                    break;
                case Options:
                    tab.setContentDescription(R.string.tabProfile);
                    tab.setText(R.string.tabProfile);
                    tab.setIcon(R.drawable.ic_tab_profile);
                    break;
            }

            tab.setTabLabelVisibility(TabLayout.TAB_LABEL_VISIBILITY_LABELED);
        }).attach();

        AuthToken auth = new AuthToken();
        if (!auth.HasValidCache() || mLastTabIndex < 0 || mLastTabIndex >= MFBTab.values().length)
            mViewPager.setCurrentItem(MFBTab.Options.ordinal());
        else
            mViewPager.setCurrentItem(mLastTabIndex);

        Log.v(MFBConstants.LOG_TAG, "onCreate: vacuum database");
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

        Log.v(MFBConstants.LOG_TAG, "onCreate: handle any new intent");
        onNewIntent(getIntent());

        Log.v(MFBConstants.LOG_TAG, "onCreate: set up GPS");
        // Set up the GPS service, but don't start it until OnResume
        if (MFBLocation.GetMainLocation() == null)
            MFBLocation.setMainLocation(new MFBLocation(this, false));
        Log.v(MFBConstants.LOG_TAG, "onCreate: finished");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        OpenRequestedTelemetry(intent);

        // handle shortcuts
        String szAction = intent.getAction();
        if (szAction != null) {
            switch (szAction) {
                case ACTION_VIEW_CURRENCY:
                    mLastTabIndex = MFBTab.Currency.ordinal();
                    mViewPager.setCurrentItem(mLastTabIndex);
                    break;
                case ACTION_VIEW_TOTALS:
                    mLastTabIndex = MFBTab.Totals.ordinal();
                    mViewPager.setCurrentItem(mLastTabIndex);
                    break;
                case ACTION_VIEW_CURRENT:
                    mLastTabIndex = MFBTab.NewFlight.ordinal();
                    mViewPager.setCurrentItem(mLastTabIndex);
                    break;
                case ACTION_START_ENGINE:
                case ACTION_STOP_ENGINE:
                case ACTION_PAUSE_FLIGHT:
                case ACTION_RESUME_FLIGHT:
                    mLastTabIndex = MFBTab.NewFlight.ordinal();
                    mViewPager.setCurrentItem(mLastTabIndex);
                    if (MFBMain.getNewFlightListener().getDelegate() != null)
                        performActionForListener(szAction);
                    else
                        MFBMain.pendingAction = szAction;
                    break;
                default:
                    MFBMain.pendingAction = szAction;
                    break;
            }
        }
    }

    private void OpenRequestedTelemetry(Intent i) {
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
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_READ) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                OpenRequestedTelemetry(getIntent());
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
                else
                    lst.add(new ShortcutInfo.Builder(this, "viewCurrent")
                            .setShortLabel(getString(R.string.shortcutCurrentFlight))
                            .setLongLabel(getString(R.string.shortcutCurrentFlight))
                            .setIcon(Icon.createWithResource(this, R.drawable.ic_tab_newflight))
                            .setIntent(new Intent(this, MFBMain.class).setAction(ACTION_VIEW_CURRENT))
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

        if (MFBLocation.GetMainLocation() == null)
            MFBLocation.setMainLocation(new MFBLocation(this, true));

        if (MFBLocation.GetMainLocation() != null)
            MFBLocation.GetMainLocation().startListening(this);
    }

    protected void onResume() {
        super.onResume();

        Log.v(MFBConstants.LOG_TAG, "onResume: refresh authorization token, if needed");
        fIsPaused = false;

        // check to see if we need to refresh auth.
        // a) if we're already signed in, just go to the last tab.
        // b) if we're not already signed in or need a refresh, refresh as necessary
        // c) else, just go to the options tab to sign in from there.
        AuthToken auth = new AuthToken();
        new RefreshTask(getApplicationContext(), this).execute((auth));

        Log.v(MFBConstants.LOG_TAG, "onResume: start listening to GPS");
        // This is a hack, but we get a lot of crashes about too much time between startForegroundService being
        // called and startForeground being called.
        // Problem is, other tabs' OnResume may not have been called yet, so let's delay this by a few
        // seconds so that all the other startup tasks are done before we call startForegroundService.
        // Note that ActNewFlight will initialize the GPS as needed, so this call will be a no-op at that point.
        new Handler(Looper.getMainLooper()).postDelayed(this::resumeGPS, 3000);

        Log.v(MFBConstants.LOG_TAG, "onResume: finished");
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
            Class.forName("com.google.android.gms.maps.MapFragment");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static class RefreshTask extends AsyncTask<AuthToken, Void, Boolean> {
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
        }

        protected void onPostExecute(Boolean f) {
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
            ActRecentsWS.flightDetail = ActRecentsWS.FlightDetail.values()[mPrefs.getInt(m_KeysShowFlightTimes, 0)];

            ActOptions.speedUnits = ActOptions.SpeedUnits.values()[mPrefs.getInt(m_KeysSpeedUnits, 0)];
            ActOptions.altitudeUnits = ActOptions.AltitudeUnits.values()[mPrefs.getInt(m_KeysAltUnits, 0)];

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

        ed.putBoolean(m_KeysShowFlightImages, ActRecentsWS.fShowFlightImages);
        ed.putInt(m_KeysShowFlightTimes, ActRecentsWS.flightDetail.ordinal());

        ed.putInt(m_KeysAltUnits, ActOptions.altitudeUnits.ordinal());
        ed.putInt(m_KeysSpeedUnits, ActOptions.speedUnits.ordinal());

        ed.putInt(m_KeysLastTab, mLastTabIndex = mViewPager.getCurrentItem());
        ed.putLong(m_TimeOfLastVacuum, mLastVacuum);

        ed.putInt(m_KeysTOSpeed, MFBTakeoffSpeed.getTakeOffSpeedIndex());
        ed.apply();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        SaveState();
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        RestoreState();
    }

    public static MFBFlightListener getNewFlightListener() {
        if (MFBMain.m_FlightEventListener == null)
            MFBMain.m_FlightEventListener = new MFBFlightListener();
        return MFBMain.m_FlightEventListener;
    }

    protected static void performActionForListener(String action) {
        MFBFlightListener.ListenerFragmentDelegate d = MFBMain.getNewFlightListener().getDelegate();
        if (d == null)
            return;

        switch (action) {
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
    }

    public static void SetInProgressFlightActivity(Context c, MFBFlightListener.ListenerFragmentDelegate d) {
        MFBFlightListener fl = MFBMain.getNewFlightListener().setDelegate(d);
        if (MFBLocation.GetMainLocation() == null)
            MFBLocation.setMainLocation(new MFBLocation(c, fl));
        else
            MFBLocation.GetMainLocation().SetListener(fl);

        if (d != null && pendingAction != null) {
            performActionForListener(pendingAction);
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
