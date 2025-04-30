/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025 MyFlightbook, LLC

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
package com.myflightbook.android

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.content.res.Resources
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.MapsInitializer.Renderer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.MFBSoap
import com.myflightbook.android.webservices.MFBSoap.Companion.startListeningNetwork
import com.myflightbook.android.webservices.RecentFlightsSvc.Companion.clearCachedFlights
import kotlinx.coroutines.launch
import model.*
import model.GPSSim.Companion.importTelemetry
import model.MFBFlightListener.ListenerFragmentDelegate
import model.MFBLocation.Companion.getMainLocation
import model.MFBLocation.Companion.setMainLocation
import model.MFBTakeoffSpeed.takeOffSpeedIndex
import model.MFBUtil.alert
import model.Telemetry.Companion.telemetryFromURL
import java.io.IOException
import java.util.*

class MFBMain : AppCompatActivity(), OnMapsSdkInitializedCallback {
    interface Invalidatable {
        fun invalidate()
    }

    enum class MFBTab {
        NewFlight, Aircraft, Recent, Currency, Totals, Visited, Training, Options
    }

    class MFBTabAdapter(f: FragmentActivity?) : FragmentStateAdapter(f!!) {
        // Return a NEW fragment instance in createFragment(int)
        override fun createFragment(position: Int): Fragment {
            return when (MFBTab.values()[position]) {
                MFBTab.NewFlight -> ActNewFlight()
                MFBTab.Aircraft -> ActAircraft()
                MFBTab.Recent -> ActRecentsWS()
                MFBTab.Currency -> ActCurrency()
                MFBTab.Totals -> ActTotals()
                MFBTab.Visited -> ActVisitedAirports()
                MFBTab.Training -> ActTraining()
                MFBTab.Options -> ActOptions()
            }
        }

        override fun getItemCount(): Int {
            return 8
        }
    }

    private var mPrefs: SharedPreferences? = null
    private var mLastVacuum: Long = 0 // ms of last vacuum
    private var mViewPager: ViewPager2? = null
    private var mLastTabIndex = -1

    private fun importTelemetry(uri: Uri?) {
        if (uri == null)
            return
        val act = this as Activity
        lifecycleScope.launch {
            ActMFBForm.doAsync<MFBSoap, LogbookEntry?>(
                act,
                MFBSoap(),
                getString(R.string.telemetryImportProgress),
                { s ->
                    try {
                        val t = telemetryFromURL(uri, act)
                        if (t == null) null else importTelemetry(act, t, uri)
                    } catch (ex: IOException) {
                        s.lastError = ex.message ?: getString(R.string.telemetryCantIdentify)
                        null
                    }
                },
                {
                    s, result ->
                    when {
                        result == null -> alert(act, getString(R.string.txtError), s.lastError)
                        result.szError.isNotEmpty() -> alert(act, getString(R.string.txtError), result.szError)
                        else -> {
                            clearCachedFlights()
                            alert(act, getString(R.string.txtSuccess), getString(R.string.telemetryImportSuccessful))
                        }
                    }
                    // clear the URL from the intent.
                    val i = act.intent
                    if (i != null) i.data = null
                    mViewPager!!.currentItem = MFBTab.Recent.ordinal
                }
            )
        }
    }

    private fun initDB(fRetryOnFailure: Boolean) {
        var db: SQLiteDatabase? = null

        // set up the sqlite db
        mDBHelper = DataBaseHelper(
            this, DataBaseHelper.DB_NAME_MAIN,
            MFBConstants.DBVersionMain
        )
        try {
            mDBHelper!!.createDataBase()

            // force any upgrade.
            db = mDBHelper!!.writableDatabase
        } catch (ioe: Error) {
            alert(
                this, getString(R.string.txtError),
                getString(R.string.errMainDBFailure)
            )
        } catch (ex: SQLiteReadOnlyDatabaseException) {
            // shouldn't happen, but does due to lollipop bug
            if (fRetryOnFailure) initDB(false)
        } finally {
            db?.close()
            db = null
        }
        mDBHelperAirports = DataBaseHelper(
            this,
            DataBaseHelper.DB_NAME_AIRPORTS, MFBConstants.DBVersionAirports
        )
        try {
            mDBHelperAirports!!.createDataBase()
            // force any upgrade.
            db = mDBHelperAirports!!.writableDatabase
        } catch (ioe: Error) {
            alert(
                this, getString(R.string.txtError),
                getString(R.string.errAirportDBFailure)
            )
        } catch (ex: SQLiteReadOnlyDatabaseException) {
            // shouldn't happen, but does due to lollipop bug
            if (fRetryOnFailure) initDB(false)
        } finally {
            db?.close()
        }
    }

    //  Called when the activity is first created. */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapsInitializer.initialize(applicationContext, Renderer.LATEST, this)
        Log.v(MFBConstants.LOG_TAG, "onCreate: about to install splash screen")

        installSplashScreen()

        Log.v(MFBConstants.LOG_TAG, "onCreate: start listening network")
        // Start listening to network change events
        startListeningNetwork(applicationContext)

        // get cached auth credentials; restore state prior to restoring location.
        mPrefs = getPreferences(MODE_PRIVATE)

        // TOTALLY MESSED UP
        // WebView screws up night mode, but this code, for some reason, fixes it
        // see https://stackoverflow.com/questions/44035654/broken-colors-in-daynight-theme-after-loading-admob-firebase-ad
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightMode != Configuration.UI_MODE_NIGHT_NO) WebView(this)
        Log.v(MFBConstants.LOG_TAG, "onCreate: set night pref")
        val locPref = mPrefs
        NightModePref = locPref!!.getInt(m_KeysNightMode, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(NightModePref)
        setContentView(R.layout.main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tab_layout)) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }
        appResources = resources
        appFilesPath = filesDir.absolutePath

        // Get the version name/code for crash reports
        try {
            val packageInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                else
                    packageManager.getPackageInfo(packageName, 0)
            versionName = packageInfo.versionName ?: "Unknown"
            versionCode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    packageInfo.longVersionCode
                else
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
        }
        Log.v(MFBConstants.LOG_TAG, "onCreate: about to initialize database")
        initDB(true)
        Log.v(MFBConstants.LOG_TAG, "onCreate: restore state")
        restoreState()

        // set up for web services
        AuthToken.APPTOKEN = getString(R.string.WebServiceKey)
        Log.v(MFBConstants.LOG_TAG, "onCreate: set custom handler and send error reports")
        val ceh = CustomExceptionHandler(
            cacheDir.absolutePath,
            String.format(MFBConstants.urlCrashReport, MFBConstants.szIP),
            AuthToken.APPTOKEN
        )
        Thread.setDefaultUncaughtExceptionHandler(ceh)
        ceh.sendPendingReports()
        Log.v(MFBConstants.LOG_TAG, "onCreate: set up tabs")

        val vp = findViewById<ViewPager2>(R.id.pager)
        mViewPager = vp
        vp.offscreenPageLimit = MFBTab.values().size
        vp.adapter = MFBTabAdapter(this)
        vp.isUserInputEnabled = false
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        TabLayoutMediator(
            tabLayout,
            vp
        ) { tab: TabLayout.Tab, position: Int ->
            when (MFBTab.values()[position]) {
                MFBTab.NewFlight -> {
                    tab.setContentDescription(R.string.tabNewFlight)
                    tab.setText(R.string.tabNewFlight)
                    tab.setIcon(R.drawable.ic_tab_newflight)
                }
                MFBTab.Recent -> {
                    tab.setContentDescription(R.string.tabRecent)
                    tab.setText(R.string.tabRecent)
                    tab.setIcon(R.drawable.ic_tab_recents)
                }
                MFBTab.Aircraft -> {
                    tab.setContentDescription(R.string.tabAircraft)
                    tab.setText(R.string.tabAircraft)
                    tab.setIcon(R.drawable.ic_tab_aircraft)
                }
                MFBTab.Totals -> {
                    tab.setContentDescription(R.string.tabTotals)
                    tab.setText(R.string.tabTotals)
                    tab.setIcon(R.drawable.ic_tab_totals)
                }
                MFBTab.Currency -> {
                    tab.setContentDescription(R.string.tabCurrency)
                    tab.setText(R.string.tabCurrency)
                    tab.setIcon(R.drawable.ic_tab_currency)
                }
                MFBTab.Visited -> {
                    tab.setContentDescription(R.string.tabVisited)
                    tab.setText(R.string.tabVisited)
                    tab.setIcon(R.drawable.ic_tab_visitedairport)
                }
                MFBTab.Training -> {
                    tab.setContentDescription(R.string.tabTraining)
                    tab.setText(R.string.tabTraining)
                    tab.setIcon(R.drawable.ic_tab_training)
                }
                MFBTab.Options -> {
                    tab.setContentDescription(R.string.tabProfile)
                    tab.setText(R.string.tabProfile)
                    tab.setIcon(R.drawable.ic_tab_profile)
                }
            }
            tab.tabLabelVisibility = TabLayout.TAB_LABEL_VISIBILITY_LABELED
        }.attach()
        val auth = AuthToken()
        if (!auth.hasValidCache() || mLastTabIndex < 0 || mLastTabIndex >= MFBTab.values().size) vp.currentItem =
            MFBTab.Options.ordinal else vp.currentItem = mLastTabIndex
        Log.v(MFBConstants.LOG_TAG, "onCreate: vacuum database")
        // Periodically (every 7 days) vacuum (compact) the database.
        val t = Date().time
        if (t - mLastVacuum > 1000 * 3600 * 24 * 7) {
            val db = mDBHelper!!.writableDatabase
            try {
                Log.w(MFBConstants.LOG_TAG, "running VACUUM command")
                db.execSQL("VACUUM")
                mLastVacuum = t
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, "VACUUM failed: " + e.localizedMessage)
            }
        }
        Log.v(MFBConstants.LOG_TAG, "onCreate: handle any new intent")
        onNewIntent(intent)
        Log.v(MFBConstants.LOG_TAG, "onCreate: set up GPS")
        // Set up the GPS service, but don't start it until OnResume
        if (getMainLocation() == null) setMainLocation(MFBLocation(this, false))
        Log.v(MFBConstants.LOG_TAG, "onCreate: finished")
    }

    override fun onMapsSdkInitialized(renderer: Renderer) {
        when (renderer) {
            Renderer.LATEST -> Log.d(MFBConstants.LOG_TAG, "The latest version of the renderer is used.")
            Renderer.LEGACY -> Log.d(MFBConstants.LOG_TAG, "The legacy version of the renderer is used.")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestedTelemetry(intent)

        // handle shortcuts
        val szAction = intent.action
        if (szAction != null) {
            when (szAction) {
                ACTION_VIEW_CURRENCY -> {
                    mLastTabIndex = MFBTab.Currency.ordinal
                    mViewPager!!.currentItem = mLastTabIndex
                }
                ACTION_VIEW_TOTALS -> {
                    mLastTabIndex = MFBTab.Totals.ordinal
                    mViewPager!!.currentItem = mLastTabIndex
                }
                ACTION_VIEW_CURRENT -> {
                    mLastTabIndex = MFBTab.NewFlight.ordinal
                    mViewPager!!.currentItem = mLastTabIndex
                }
                ACTION_START_ENGINE, ACTION_STOP_ENGINE, ACTION_PAUSE_FLIGHT, ACTION_RESUME_FLIGHT -> {
                    mLastTabIndex = MFBTab.NewFlight.ordinal
                    mViewPager!!.currentItem = mLastTabIndex
                    if (newFlightListener!!.delegate != null) performActionForListener(szAction) else pendingAction =
                        szAction
                }
                else -> pendingAction = szAction
            }
        }
    }

    private fun openRequestedTelemetry(i: Intent?) {
        if (i != null) {
            val uri = i.data
            if (uri != null) {
                AlertDialog.Builder(this, R.style.MFBDialog)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.lblConfirm)
                    .setMessage(R.string.telemetryImportPrompt)
                    .setPositiveButton(R.string.lblOK) { _: DialogInterface?, _: Int -> importTelemetry(uri) }
                    .setNegativeButton(R.string.lblCancel, null)
                    .show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_READ) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openRequestedTelemetry(intent)
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun setDynamicShortcuts() {
        // 5 possible dynamic shortcuts:
        // Start/stop engine
        // Block In/Out
        // puase/play.
        // If engine is not started, only show start.
        // if engine is started, show stop and pause/play
        val shortcutManager = getSystemService(
            ShortcutManager::class.java
        )
        if (shortcutManager != null && getMainLocation() != null) {
            val lst = ArrayList<ShortcutInfo>()
            val fPaused: Boolean = ActNewFlight.fPaused
            val le = newFlightListener!!.getInProgressFlight(this)
            // 3 possible states:
            // a) a flight is in progress - offer stop engine, block in, and pause/play
            // b) a flight indicates finished - offer current flight
            // c) a flight indicates unstarted - offer start engine, block out
            if (newFlightListener!!.shouldKeepListening()) {
                // Flight could be in progress - offer engine stop and block in and pause/resume
                lst.add(
                    ShortcutInfo.Builder(this, "stopEngine")
                        .setShortLabel(getString(R.string.shortcutStopEngine))
                        .setLongLabel(getString(R.string.shortcutStopEngine))
                        .setIcon(Icon.createWithResource(this, R.drawable.ic_action_stop))
                        .setIntent(
                            Intent(this, MFBMain::class.java).setAction(
                                ACTION_STOP_ENGINE
                            )
                        )
                        .build()
                )
                lst.add(
                    ShortcutInfo.Builder(this, "blockIn")
                        .setShortLabel(getString(R.string.lblBlockIn))
                        .setLongLabel(getString(R.string.lblBlockIn))
                        .setIcon(Icon.createWithResource(this, R.drawable.ic_action_stop))
                        .setIntent(
                            Intent(this, MFBMain::class.java).setAction(
                                ACTION_BLOCK_IN
                            )
                        )
                        .build()
                )
                if (fPaused) lst.add(
                    ShortcutInfo.Builder(this, "resume")
                        .setShortLabel(getString(R.string.shortcutResume))
                        .setLongLabel(getString(R.string.shortcutResume))
                        .setIcon(Icon.createWithResource(this, R.drawable.ic_action_play))
                        .setIntent(
                            Intent(this, MFBMain::class.java).setAction(
                                ACTION_RESUME_FLIGHT
                            )
                        )
                        .build()
                ) else lst.add(
                    ShortcutInfo.Builder(this, "pause")
                        .setShortLabel(getString(R.string.shortcutPause))
                        .setLongLabel(getString(R.string.shortcutPause))
                        .setIcon(Icon.createWithResource(this, R.drawable.ic_action_pause))
                        .setIntent(
                            Intent(this, MFBMain::class.java).setAction(
                                ACTION_PAUSE_FLIGHT
                            )
                        )
                        .build()
                )
            } else if (le.isKnownEngineEnd || le.isKnownBlockIn) {
                // Flight is ready for submission
                lst.add(
                    ShortcutInfo.Builder(this, "viewCurrent")
                        .setShortLabel(getString(R.string.shortcutCurrentFlight))
                        .setLongLabel(getString(R.string.shortcutCurrentFlight))
                        .setIcon(Icon.createWithResource(this, R.drawable.ic_tab_newflight))
                        .setIntent(Intent(this, MFBMain::class.java).setAction(ACTION_VIEW_CURRENT))
                        .build()
                )
            } else {
                // Flight is waiting to be started
                lst.add(
                ShortcutInfo.Builder(this, "startEngine")
                    .setShortLabel(getString(R.string.shortcutStartEngine))
                    .setLongLabel(getString(R.string.shortcutStartEngine))
                    .setIcon(Icon.createWithResource(this, R.drawable.ic_action_play))
                    .setIntent(Intent(this, MFBMain::class.java).setAction(ACTION_START_ENGINE))
                    .build()
            )
                lst.add(
                    ShortcutInfo.Builder(this, "blockOut")
                        .setShortLabel(getString(R.string.lblBlockOut))
                        .setLongLabel(getString(R.string.lblBlockOut))
                        .setIcon(Icon.createWithResource(this, R.drawable.ic_action_play))
                        .setIntent(
                            Intent(this, MFBMain::class.java).setAction(
                                ACTION_BLOCK_OUT
                            )
                        )
                        .build()
                )
            }

            // Now add Currency and Totals regardless
            lst.add(
                ShortcutInfo.Builder(this, "currency")
                    .setShortLabel(getString(R.string.shortcutCurrency))
                    .setLongLabel(getString(R.string.shortcutCurrency))
                    .setIcon(Icon.createWithResource(this, R.drawable.currency))
                    .setIntent(
                        Intent(
                            this,
                            MFBMain::class.java
                        ).setAction(ACTION_VIEW_CURRENCY)
                    )
                    .build()
            )
            lst.add(
                ShortcutInfo.Builder(this, "totals")
                    .setShortLabel(getString(R.string.shortcutTotals))
                    .setLongLabel(getString(R.string.shortcutTotals))
                    .setIcon(Icon.createWithResource(this, R.drawable.totals))
                    .setIntent(Intent(this, MFBMain::class.java).setAction(ACTION_VIEW_TOTALS))
                    .build()
            )
            shortcutManager.dynamicShortcuts = lst
        }
    }

    private var fIsPaused = false
    override fun onPause() {
        fIsPaused = true
        if (getMainLocation() != null) {
            // stop listening we aren't supposed to stay awake.
            if (!newFlightListener!!.shouldKeepListening()) getMainLocation()!!
                .stopListening(this)
        }

        // close the writeable DB, in case it is opened.
        mDBHelper!!.writableDatabase.close()
        setDynamicShortcuts()

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val currencyAppWidget = ComponentName(this, CurrencyWidgetProvider::class.java)
        val totalsAppWidget = ComponentName(this, TotalsWidgetProvider::class.java)
        val currAppWidgetIds = appWidgetManager.getAppWidgetIds(currencyAppWidget)
        val totalsAppWidgetIds = appWidgetManager.getAppWidgetIds(totalsAppWidget)

        if (currAppWidgetIds.any()) {
            val updateIntent = Intent()
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setClassName(/* TODO: provide the application ID. For example: */ packageName,
                    "com.myflightbook.android.CurrencyWidgetProvider"
                )
            updateIntent.putExtra(CurrencyWidgetProvider.WIDGET_IDS_KEY, currAppWidgetIds)
            sendBroadcast(updateIntent)
        }

        if (totalsAppWidgetIds.any()) {
            val updateIntent = Intent()
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setClassName(/* TODO: provide the application ID. For example: */ packageName,
                    "com.myflightbook.android.CurrencyWidgetProvider"
                )
            updateIntent.putExtra(TotalsWidgetProvider.WIDGET_IDS_KEY, totalsAppWidgetIds)
            sendBroadcast(updateIntent)
        }

        super.onPause()
    }

    private fun resumeGPS() {
        if (fIsPaused) return
        if (getMainLocation() == null) setMainLocation(MFBLocation(this, true))
        if (getMainLocation() != null) getMainLocation()!!.startListening(this)
    }

    override fun onResume() {
        super.onResume()
        Log.v(MFBConstants.LOG_TAG, "onResume: refresh authorization token, if needed")
        fIsPaused = false

        // check to see if we need to refresh auth.
        // a) if we're already signed in, just go to the last tab.
        // b) if we're not already signed in or need a refresh, refresh as necessary
        // c) else, just go to the options tab to sign in from there.
        refreshAuthToken(AuthToken())
        Log.v(MFBConstants.LOG_TAG, "onResume: start listening to GPS")
        // This is a hack, but we get a lot of crashes about too much time between startForegroundService being
        // called and startForeground being called.
        // Problem is, other tabs' OnResume may not have been called yet, so let's delay this by a few
        // seconds so that all the other startup tasks are done before we call startForegroundService.
        // Note that ActNewFlight will initialize the GPS as needed, so this call will be a no-op at that point.
        Handler(Looper.getMainLooper()).postDelayed({ resumeGPS() }, 3000)
        Log.v(MFBConstants.LOG_TAG, "onResume: finished")
    }

    override fun onDestroy() {
        super.onDestroy()

        // close the DB's
        mDBHelper!!.writableDatabase.close()
        mDBHelperAirports!!.readableDatabase.close()
    }

    private fun refreshAuthToken(auth : AuthToken) {
        val act = this as Activity
        lifecycleScope.launch {
            ActMFBForm.doAsync<AuthToken, Any?>(
                act,
                auth,
                null,
                { s -> s.refreshAuthorization(act) },
                { _, _ -> }
            )
        }
    }

    private fun restoreState() {
        try {
            AuthToken.m_szAuthToken = mPrefs!!.getString(m_KeyszAuthToken, "")
            AuthToken.m_szEmail = mPrefs!!.getString(m_KeyszUser, "")!!
            AuthToken.m_szPass = mPrefs!!.getString(m_KeyszPass, "")!!
            MFBLocation.fPrefAutoDetect = mPrefs!!.getBoolean(m_KeysfAutoDetect, false)
            MFBLocation.fPrefRecordFlight = mPrefs!!.getBoolean(m_KeysfRecord, false)
            MFBLocation.fPrefRecordFlightHighRes = mPrefs!!.getBoolean(m_KeysfRecordHighRes, false)
            MFBLocation.fPrefAutoFillHobbs = MFBLocation.AutoFillOptions.values()[mPrefs!!.getInt(
                m_KeysAutoHobbs, 0
            )]
            MFBLocation.fPrefAutoFillTime = MFBLocation.AutoFillOptions.values()[mPrefs!!.getInt(
                m_KeysAutoTime, 0
            )]
            MFBLocation.fPrefRoundNearestTenth = mPrefs!!.getBoolean(m_KeysfRoundToTenth, false)
            MFBLocation.IsFlying =
                MFBLocation.IsFlying || mPrefs!!.getBoolean(m_KeysIsFlying, false)
            MFBLocation.IsRecording = MFBLocation.IsRecording || mPrefs!!.getBoolean(
                m_KeysIsRecording, false
            )
            MFBLocation.HasPendingLanding = mPrefs!!.getBoolean(m_KeysHasPendingFSLanding, false)
            MFBLocation.NightPref = MFBLocation.NightCriteria.values()[mPrefs!!.getInt(
                m_KeysNightFlightOption, MFBLocation.NightCriteria.EndOfCivilTwilight.ordinal
            )]
            MFBLocation.NightLandingPref =
                MFBLocation.NightLandingCriteria.values()[mPrefs!!.getInt(
                    m_KeysNightLandingOption, MFBLocation.NightLandingCriteria.SunsetPlus60.ordinal
                )]
            Airport.fPrefIncludeHeliports = mPrefs!!.getBoolean(m_KeysfHeliports, false)
            DecimalEdit.DefaultHHMM = mPrefs!!.getBoolean(m_KeysUseHHMM, false)
            DlgDatePicker.fUseLocalTime = mPrefs!!.getBoolean(m_KeysUseLocal, false)
            ActRecentsWS.fShowFlightImages = mPrefs!!.getBoolean(m_KeysShowFlightImages, true)
            ActRecentsWS.flightDetail = ActRecentsWS.FlightDetail.values()[mPrefs!!.getInt(
                m_KeysShowFlightTimes, 0
            )]
            ActOptions.speedUnits =
                ActOptions.SpeedUnits.values()[mPrefs!!.getInt(m_KeysSpeedUnits, 0)]
            ActOptions.altitudeUnits = ActOptions.AltitudeUnits.values()[mPrefs!!.getInt(
                m_KeysAltUnits, 0
            )]

            ActFlightMap.RouteColor = mPrefs!!.getInt(m_KeysRouteColor, Color.BLUE)
            ActFlightMap.PathColor = mPrefs!!.getInt(m_KeysPathColor, Color.RED)
            ActFlightMap.MapType = mPrefs!!.getInt(m_KeysMapType, GoogleMap.MAP_TYPE_SATELLITE)

            ActNewFlight.fShowTach = mPrefs!!.getBoolean(ActNewFlight.prefKeyShowTach, false)
            ActNewFlight.fShowHobbs = mPrefs!!.getBoolean(ActNewFlight.prefKeyShowHobbs, true)
            ActNewFlight.fShowEngine = mPrefs!!.getBoolean(ActNewFlight.prefKeyShowEngine, true)
            ActNewFlight.fShowBlock = mPrefs!!.getBoolean(ActNewFlight.prefKeyShowBlock, false)
            ActNewFlight.fShowFlight = mPrefs!!.getBoolean(ActNewFlight.prefKeyShowFlight, true)

            mLastTabIndex = mPrefs!!.getInt(m_KeysLastTab, 0)
            mLastVacuum = mPrefs!!.getLong(m_TimeOfLastVacuum, Date().time)
            takeOffSpeedIndex = mPrefs!!.getInt(m_KeysTOSpeed, MFBTakeoffSpeed.DefaultTakeOffIndex)
        } catch (e: Exception) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
        }
    }

    private fun saveState() {
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        val ed = mPrefs!!.edit()
        ed.putString(m_KeyszAuthToken, AuthToken.m_szAuthToken)
        ed.putString(m_KeyszUser, AuthToken.m_szEmail)
        ed.putString(m_KeyszPass, AuthToken.m_szPass)
        ed.putBoolean(m_KeysfAutoDetect, MFBLocation.fPrefAutoDetect)
        ed.putBoolean(m_KeysfRecord, MFBLocation.fPrefRecordFlight)
        ed.putBoolean(m_KeysfRecordHighRes, MFBLocation.fPrefRecordFlightHighRes)
        ed.putInt(m_KeysAutoHobbs, MFBLocation.fPrefAutoFillHobbs.ordinal)
        ed.putInt(m_KeysAutoTime, MFBLocation.fPrefAutoFillTime.ordinal)
        ed.putBoolean(m_KeysfRoundToTenth, MFBLocation.fPrefRoundNearestTenth)
        ed.putBoolean(m_KeysIsFlying, MFBLocation.IsFlying)
        ed.putBoolean(m_KeysIsRecording, MFBLocation.IsRecording)
        ed.putBoolean(m_KeysHasPendingFSLanding, MFBLocation.HasPendingLanding)
        ed.putInt(m_KeysNightFlightOption, MFBLocation.NightPref.ordinal)
        ed.putInt(m_KeysNightLandingOption, MFBLocation.NightLandingPref.ordinal)
        ed.putBoolean(m_KeysfHeliports, Airport.fPrefIncludeHeliports)
        ed.putBoolean(m_KeysUseHHMM, DecimalEdit.DefaultHHMM)
        ed.putBoolean(m_KeysUseLocal, DlgDatePicker.fUseLocalTime)
        ed.putInt(m_KeysNightMode, NightModePref)
        ed.putBoolean(m_KeysShowFlightImages, ActRecentsWS.fShowFlightImages)
        ed.putInt(m_KeysShowFlightTimes, ActRecentsWS.flightDetail.ordinal)
        ed.putInt(m_KeysAltUnits, ActOptions.altitudeUnits.ordinal)
        ed.putInt(m_KeysSpeedUnits, ActOptions.speedUnits.ordinal)
        ed.putInt(m_KeysLastTab, mViewPager!!.currentItem.also { mLastTabIndex = it })
        ed.putLong(m_TimeOfLastVacuum, mLastVacuum)
        ed.putInt(m_KeysTOSpeed, takeOffSpeedIndex)

        ed.putInt(m_KeysRouteColor, ActFlightMap.RouteColor)
        ed.putInt(m_KeysPathColor, ActFlightMap.PathColor)
        ed.putInt(m_KeysMapType, ActFlightMap.MapType)

        ed.putBoolean(ActNewFlight.prefKeyShowTach, ActNewFlight.fShowTach)
        ed.putBoolean(ActNewFlight.prefKeyShowHobbs, ActNewFlight.fShowHobbs)
        ed.putBoolean(ActNewFlight.prefKeyShowEngine, ActNewFlight.fShowEngine)
        ed.putBoolean(ActNewFlight.prefKeyShowBlock, ActNewFlight.fShowBlock)
        ed.putBoolean(ActNewFlight.prefKeyShowFlight, ActNewFlight.fShowFlight)

        ed.apply()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveState()
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        restoreState()
    }

    companion object {
        private val rgNotifyDataChanged = ArrayList<Invalidatable>()
        private val rgNotifyResetAll = ArrayList<Invalidatable>()

        // preferences keys.
        private const val m_KeyszUser = "username"
        private const val m_KeyszPass = "password"
        private const val m_KeyszAuthToken = "authtoken"
        private const val m_KeysfRecord = "recordflight"
        private const val m_KeysfRecordHighRes = "recordFlightHighRes"
        private const val m_KeysfAutoDetect = "autodetect"
        private const val m_KeysfHeliports = "heliports"
        private const val m_KeysfRoundToTenth = "roundnearesttenth"
        private const val m_KeysAutoHobbs = "autohobbs"
        private const val m_KeysAutoTime = "autotime"
        private const val m_KeysUseHHMM = "UseHHMM"
        private const val m_KeysUseLocal = "UseLocalTime"
        private const val m_KeysLastTab = "lastTab3"
        private const val m_KeysShowFlightImages = "showFlightImages"
        private const val m_KeysShowFlightTimes = "showFlightTimes2"
        private const val m_KeysSpeedUnits = "speedUnits"
        private const val m_KeysAltUnits = "altUnits"
        private const val m_KeysIsFlying = "isFlying"
        private const val m_KeysIsRecording = "isRecording"
        private const val m_KeysHasPendingFSLanding = "hasPendingLanding"
        private const val m_KeysTOSpeed = "takeoffspeed"
        private const val m_KeysNightFlightOption = "nightFlightOption"
        private const val m_KeysNightLandingOption = "nightLandingOption"
        private const val m_KeysNightMode = "nightModeOption"
        private const val m_KeysRouteColor = "routeColor"
        private const val m_KeysPathColor = "pathColor"
        private const val m_KeysMapType = "mapType"
        var NightModePref = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        private const val m_TimeOfLastVacuum = "LastVacuum"
        var mDBHelper: DataBaseHelper? = null
        var mDBHelperAirports: DataBaseHelper? = null
        private var m_FlightEventListener: MFBFlightListener? = null
        var versionName = ""
        var versionCode = 0L
        const val ACTION_VIEW_CURRENCY = "com.myflightbook.android.VIEWCURRENCY"
        const val ACTION_VIEW_TOTALS = "com.myflightbook.android.VIEWTOTALS"
        private const val ACTION_VIEW_CURRENT = "com.myflightbook.android.VIEWCURRENT"
        private const val ACTION_START_ENGINE = "com.myflightbook.android.STARTENGINE"
        private const val ACTION_STOP_ENGINE = "com.myflightbook.android.STOPENGINE"
        private const val ACTION_BLOCK_OUT = "com.myflightbook.android.BLOCKOUT"
        private const val ACTION_BLOCK_IN = "com.myflightbook.android.BLOCKIN"
        private const val ACTION_PAUSE_FLIGHT = "com.myflightbook.android.PAUSEFLIGHT"
        private const val ACTION_RESUME_FLIGHT = "com.myflightbook.android.RESUMEFLIGHT"
        private var pendingAction: String? = null
        var appResources: Resources? = null
            private set

        fun getResourceString(id: Int): String {
            return if (appResources == null) "" else appResources!!.getString(id)
        }

        var appFilesPath: String? = null
            private set
        private const val PERMISSION_REQUEST_READ = 3385
        fun hasMaps(): Boolean {
            return try {
                // hide the view button if Maps aren't available; if they are available,
                // then hide the button if this is not a new flight.
                Class.forName("com.google.android.gms.maps.MapFragment")
                true
            } catch (ex: Exception) {
                false
            }
        }

        val newFlightListener: MFBFlightListener?
            get() {
                if (m_FlightEventListener == null) m_FlightEventListener = MFBFlightListener()
                return m_FlightEventListener
            }

        private fun performActionForListener(action: String?) {
            val d = newFlightListener!!.delegate ?: return
            when (action) {
                ACTION_PAUSE_FLIGHT, ACTION_RESUME_FLIGHT -> d.togglePausePlay()
                ACTION_START_ENGINE -> d.startEngine()
                ACTION_STOP_ENGINE -> d.stopEngine()
                ACTION_BLOCK_IN -> d.blockIn()
                ACTION_BLOCK_OUT -> d.blockOut()
            }
        }

        fun setInProgressFlightActivity(c: Context?, d: ListenerFragmentDelegate?) {
            val fl = newFlightListener!!.setDelegate(d)
            if (getMainLocation() == null) setMainLocation(
                MFBLocation(
                    c!!,
                    fl
                )
            ) else getMainLocation()!!
                .setListener(fl)
            if (d != null && pendingAction != null) {
                performActionForListener(pendingAction)
                pendingAction = null
            }
        }

        fun registerNotifyDataChange(o: Invalidatable) {
            rgNotifyDataChanged.add(o)
        }

        fun registerNotifyResetAll(o: Invalidatable) {
            rgNotifyResetAll.add(o)
        }

        fun unregisterNotify(o: Invalidatable) {
            rgNotifyDataChanged.remove(o)
            rgNotifyResetAll.remove(o)
        }

        fun invalidateAll() {
            for (o in rgNotifyResetAll) o.invalidate()
        }

        fun invalidateCachedTotals() {
            for (o in rgNotifyDataChanged) o.invalidate()
        }
    }
}