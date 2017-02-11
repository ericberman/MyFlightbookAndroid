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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteReadOnlyDatabaseException;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabContentFactory;

import com.myflightbook.android.WebServices.AuthToken;

import junit.framework.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import Model.Airport;
import Model.CustomExceptionHandler;
import Model.DataBaseHelper;
import Model.DecimalEdit;
import Model.MFBConstants;
import Model.MFBFlightListener;
import Model.MFBLocation;
import Model.MFBTakeoffSpeed;
import Model.MFBUtil;

public class MFBMain extends FragmentActivity implements OnTabChangeListener {

    public interface Invalidatable {
        void invalidate();
    }

    static private ArrayList<Invalidatable> rgNotifyDataChanged = new ArrayList<>();
    static private ArrayList<Invalidatable> rgNotifyResetAll = new ArrayList<>();

    // preferences keys.
    static private final String m_KeyszUser = "username";
    static private final String m_KeyszPass = "password";
    static private final String m_KeyszAuthToken = "authtoken";

    static private final String m_KeysfRecord = "recordflight";
    static private final String m_KeysfAutoDetect = "autodetect";
    static private final String m_KeysfHeliports = "heliports";
    static private final String m_KeysfRoundToTenth = "roundnearesttenth";

    static private final String m_KeysAutoHobbs = "autohobbs";
    static private final String m_KeysAutoTime = "autotime";
    static private final String m_KeysUseHHMM = "UseHHMM";
    static private final String m_KeysUseLocal = "UseLocalTime";

    static private final String m_KeysHasSeenWarning = "seenWarning";
    static private final String m_KeysLastTab = "lastTab3";

    static private final String m_KeysIsFlying = "isFlying";
    static private final String m_KeysIsRecording = "isRecording";
    static private final String m_KeysHasPendingFSLanding = "hasPendingLanding";

    static private final String m_KeysTOSpeed = "takeoffspeed";

    static private final String m_TimeOfLastVacuum = "LastVacuum";

    static final int DIALOG_SIGNIN = 0;

    static public final int SHOW_AIRCRAFT = 1;

    private SharedPreferences mPrefs;
    private Boolean m_fSeenWarning = false;
    private long mLastVacuum = 0; // ms of last vacuum

    private TabHost mTabHost = null;
    private HashMap<String, TabInfo> mapTabInfo = new HashMap<>();
    private TabInfo mLastTab = null;
    private int mLastTabIndex = -1;

    public static DataBaseHelper mDBHelper = null;
    public static DataBaseHelper mDBHelperAirports = null;

    static MFBFlightListener m_FlightEventListener = null;

    public static String versionName = "";
    public static int versionCode = 0;

    // A single shared MFBLocation for the app
    private static MFBLocation m_Location = null;

    // Access to the main context
    private static Context m_mainContext = null;

    public static Context GetMainContext() {
        return m_mainContext;
    }

    public static MFBLocation GetMainLocation() {
        return m_Location;
    }

    private class TabInfo {
        private String tag;
        private Class<?> clss;
        private Bundle args;
        private Fragment fragment;

        TabInfo(String tag, Class<?> clazz, Bundle args) {
            this.tag = tag;
            this.clss = clazz;
            this.args = args;
        }
    }

    class TabFactory implements TabContentFactory {

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

    protected String paddedTabLabel(int id) {
        return String.format(Locale.getDefault(), "  %s  ", getString(id));
    }

    protected void refreshAuth() {
        // finally, sign in if necessary.
        // a) if we're already signed in, just go to the last tab.
        // b) if we're not already signed in or need a refresh, refresh as necessary
        // c) else, just go to the options tab to sign in from there.
        AuthToken auth = new AuthToken();
        if (!auth.HasValidCache()) {
            mTabHost.setCurrentTabByTag(MFBConstants.tabOptions);
            if (auth.FHasCredentials()) {
                RefreshTask rt = new RefreshTask();
                rt.execute(auth);
            }
        }
    }

    protected void initDB(Boolean fRetryOnFailure) {
        SQLiteDatabase db = null;

        // set up the sqlite db
        mDBHelper = new DataBaseHelper(this, DataBaseHelper.DB_NAME_MAIN,
                MFBConstants.DBVersionMain);
        try {
            mDBHelper.createDataBase();

            // force any upgrade.
            db = mDBHelper.getWritableDatabase();
        } catch (IOException ioe) {
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
        } catch (IOException ioe) {
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
        setContentView(R.layout.main);

        // Get the version name/code for crash reports
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (NameNotFoundException e) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
        }

        initDB(true);

        // get cached auth credentials; restore state prior to restoring location.
        mPrefs = getPreferences(MODE_PRIVATE);
        RestoreState();

        // set up for web services
        AuthToken.APPTOKEN = getString(R.string.WebServiceKey);

        m_mainContext = this;
        if (m_Location == null) {
            m_Location = new MFBLocation();
            m_Location.SetContext(this);
        }

        CustomExceptionHandler ceh = new CustomExceptionHandler(getCacheDir().getAbsolutePath(), String.format(MFBConstants.urlCrashReport, MFBConstants.szIP));
        Thread.setDefaultUncaughtExceptionHandler(ceh);

        ceh.sendPendingReports();

        // Set up the tabs
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();
        mTabHost.getTabWidget().setDividerDrawable(null);
        TabInfo tabInfo;
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabNewFlight).setIndicator(paddedTabLabel(R.string.tabNew), ContextCompat.getDrawable(this, R.drawable.ic_tab_newflight)), (tabInfo = new TabInfo(MFBConstants.tabNewFlight, ActNewFlight.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabAircraft).setIndicator(paddedTabLabel(R.string.tabAircraft), ContextCompat.getDrawable(this, R.drawable.ic_tab_aircraft)), (tabInfo = new TabInfo(MFBConstants.tabAircraft, ActAircraft.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabRecents).setIndicator(paddedTabLabel(R.string.tabRecent), ContextCompat.getDrawable(this, R.drawable.ic_tab_recents)), (tabInfo = new TabInfo(MFBConstants.tabRecents, ActRecentsWS.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabCurrency).setIndicator(paddedTabLabel(R.string.tabCurrency), ContextCompat.getDrawable(this, R.drawable.ic_tab_currency)), (tabInfo = new TabInfo(MFBConstants.tabCurrency, ActCurrency.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabTotals).setIndicator(paddedTabLabel(R.string.tabTotals), ContextCompat.getDrawable(this, R.drawable.ic_tab_totals)), (tabInfo = new TabInfo(MFBConstants.tabTotals, ActTotals.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabVisitedAirports).setIndicator(paddedTabLabel(R.string.tabVisitedAirports), ContextCompat.getDrawable(this, R.drawable.ic_tab_visitedairport)), (tabInfo = new TabInfo(MFBConstants.tabVisitedAirports, ActVisitedAirports.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabTraining).setIndicator(paddedTabLabel(R.string.tabTraining), ContextCompat.getDrawable(this, R.drawable.ic_tab_training)), (tabInfo = new TabInfo(MFBConstants.tabTraining, ActTraining.class, savedInstanceState)));
        this.mapTabInfo.put(tabInfo.tag, tabInfo);
        MFBMain.addTab(this, this.mTabHost, this.mTabHost.newTabSpec(MFBConstants.tabOptions).setIndicator(paddedTabLabel(R.string.tabOptions), ContextCompat.getDrawable(this, R.drawable.ic_tab_profile)), (tabInfo = new TabInfo(MFBConstants.tabOptions, ActOptions.class, savedInstanceState)));
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
    }

    protected void onPause() {
        Assert.assertNotNull("m_Location is null in MFBMain.onResume()", m_Location);
        // stop listening we aren't supposed to stay awake.
        if (!MFBMain.getNewFlightListener().shouldKeepListening())
            m_Location.stopListening();

        // close the writeable DB, in case it is opened.
        mDBHelper.getWritableDatabase().close();

        super.onPause();
    }

    protected void onResume() {
        super.onResume();

        if (m_Location != null)
            m_Location.startListening();

        // check to see if we need to refresh auth.
        AuthToken at = new AuthToken();
        if (!at.HasValidCache()) {
            RefreshTask rt = new RefreshTask();
            rt.execute(at);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // close the DB's
        mDBHelper.getWritableDatabase().close();
        mDBHelperAirports.getReadableDatabase().close();
    }

    public static boolean HasCamera() {
        if (MFBMain.GetMainContext() == null)
            return false;

        PackageManager pm = MFBMain.GetMainContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    public static boolean HasGPS() {
        try {
            LocationManager lm = (LocationManager) MFBMain.GetMainContext().getSystemService(Context.LOCATION_SERVICE);
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (IllegalArgumentException ex) {
            MFBUtil.Alert(MFBMain.GetMainContext(), MFBMain.GetMainContext().getString(R.string.errNoGPSTitle), ex.getMessage());
        }
        return false;
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

    private class RefreshTask extends AsyncTask<AuthToken, Void, Boolean> {
        private ProgressDialog m_pd;

        @Override
        protected Boolean doInBackground(AuthToken... arg0) {
            return (arg0[0]).RefreshAuthorization();
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(MFBMain.this, MFBMain.this.getString(R.string.prgSigningIn));
        }

        protected void onPostExecute(Boolean f) {
            if (f)
                MFBMain.this.mTabHost.setCurrentTabByTag(mLastTab.tag);
            else
                MFBMain.this.mTabHost.setCurrentTabByTag(MFBConstants.tabOptions);
            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }

    public void SetAircraftTab() {
        mTabHost.setCurrentTabByTag(MFBConstants.tabAircraft);
    }

    private void RestoreState() {
        try {
            AuthToken.m_szAuthToken = mPrefs.getString(m_KeyszAuthToken, "");
            AuthToken.m_szEmail = mPrefs.getString(m_KeyszUser, "");
            AuthToken.m_szPass = mPrefs.getString(m_KeyszPass, "");
            MFBLocation.fPrefAutoDetect = mPrefs.getBoolean(m_KeysfAutoDetect, false);
            MFBLocation.fPrefRecordFlight = mPrefs.getBoolean(m_KeysfRecord, false);
            MFBLocation.fPrefAutoFillHobbs = MFBLocation.AutoFillOptions.values()[mPrefs.getInt(m_KeysAutoHobbs, 0)];
            MFBLocation.fPrefAutoFillTime = MFBLocation.AutoFillOptions.values()[mPrefs.getInt(m_KeysAutoTime, 0)];
            MFBLocation.fPrefRoundNearestTenth = mPrefs.getBoolean(m_KeysfRoundToTenth, false);

            MFBLocation.IsFlying = MFBLocation.IsFlying || mPrefs.getBoolean(m_KeysIsFlying, false);
            MFBLocation.IsRecording = MFBLocation.IsRecording || mPrefs.getBoolean(m_KeysIsRecording, false);
            MFBLocation.HasPendingLanding = mPrefs.getBoolean(m_KeysHasPendingFSLanding, false);

            Airport.fPrefIncludeHeliports = mPrefs.getBoolean(m_KeysfHeliports, false);
            DecimalEdit.DefaultHHMM = mPrefs.getBoolean(m_KeysUseHHMM, false);
            DlgDatePicker.fUseLocalTime = mPrefs.getBoolean(m_KeysUseLocal, false);
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

        ed.putInt(m_KeysAutoHobbs, MFBLocation.fPrefAutoFillHobbs.ordinal());
        ed.putInt(m_KeysAutoTime, MFBLocation.fPrefAutoFillTime.ordinal());
        ed.putBoolean(m_KeysfRoundToTenth, MFBLocation.fPrefRoundNearestTenth);

        ed.putBoolean(m_KeysIsFlying, MFBLocation.IsFlying);
        ed.putBoolean(m_KeysIsRecording, MFBLocation.IsRecording);
        ed.putBoolean(m_KeysHasPendingFSLanding, MFBLocation.HasPendingLanding);

        ed.putBoolean(m_KeysfHeliports, Airport.fPrefIncludeHeliports);
        ed.putBoolean(m_KeysUseHHMM, DecimalEdit.DefaultHHMM);
        ed.putBoolean(m_KeysUseLocal, DlgDatePicker.fUseLocalTime);

        ed.putBoolean(m_KeysHasSeenWarning, m_fSeenWarning);

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

	    /*
        if (!AuthToken.FIsValid())
		{
			if (tabId.compareTo(MFBConstants.tabOptions) != 0)
				mTabHost.setCurrentTabByTag(MFBConstants.tabOptions);
		}
		*/
    }

    public static MFBFlightListener getNewFlightListener() {
        if (MFBMain.m_FlightEventListener == null)
            MFBMain.m_FlightEventListener = new MFBFlightListener();
        return MFBMain.m_FlightEventListener;
    }

    public static void SetInProgressFlightActivity(MFBFlightListener.ListenerFragmentDelegate d) {
        MFBFlightListener fl = MFBMain.getNewFlightListener().setDelegate(d);
        if (m_Location == null)
            m_Location = new MFBLocation(GetMainContext(), fl);
        else
            m_Location.SetListener(fl);
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
