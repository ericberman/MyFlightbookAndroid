/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2023 MyFlightbook, LLC

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

import DlgPickColor
import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.View.OnClickListener
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.myflightbook.android.ActRecentsWS.FlightDetail
import com.myflightbook.android.webservices.*
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.RecentFlightsSvc.Companion.clearCachedFlights
import kotlinx.coroutines.launch
import model.*
import model.LogbookEntry.Companion.mergeFlightLists
import model.LogbookEntry.Companion.newFlights
import model.LogbookEntry.Companion.queuedAndUnsubmittedFlights
import model.MFBConstants.authRedirWithParams
import model.MFBConstants.nightParam
import model.MFBImageInfo.Companion.allAircraftImages
import model.MFBImageInfo.Companion.deleteOrphansNotInList
import model.MFBImageInfo.PictureDestination
import model.MFBLocation.Companion.hasGPS
import model.MFBTakeoffSpeed.getDisplaySpeeds
import model.MFBTakeoffSpeed.takeOffSpeedIndex
import model.MFBUtil.alert
import java.text.DateFormat
import java.util.*


class ActOptions : ActMFBForm(), OnClickListener, AdapterView.OnItemSelectedListener {
    enum class AltitudeUnits {
        Feet, Meters
    }

    enum class SpeedUnits {
        Knots, KmPerHour, MilesPerHour
    }

    private var mPermissionLauncher: ActivityResultLauncher<String>? = null
    private var fPendingAutodetect = false
    private var fPendingRecord = false

    private fun packData() {
        val c = requireContext()
        val progBar = findViewById(R.id.prgBarPackAndGo) as ProgressBar
        val progTxt = findViewById(R.id.lblPackProgress) as TextView

        lifecycleScope.launch {
            doAsync<AircraftSvc, String?>(requireActivity(),
                AircraftSvc(),
                null,
                { s ->
                    val updater: MFBSoap.MFBSoapProgressUpdate? =
                        s.mProgress // hold onto this for updates
                    updater?.notifyProgress(0, getString(R.string.prgAircraft))
                    s.getAircraftForUser(AuthToken.m_szAuthToken, c)
                    updater?.notifyProgress(1, getString(R.string.prgCPT))
                    CustomPropertyTypesSvc().getCustomPropertyTypes(
                        AuthToken.m_szAuthToken,
                        false,
                        c
                    )
                    val p = PackAndGo(c)
                    updater?.notifyProgress(2, getString(R.string.prgCurrency))
                    val cs = CurrencySvc()
                    val rgcsi = cs.getCurrencyForUser(AuthToken.m_szAuthToken, c)
                    cs.lastError.ifEmpty {
                        p.updateCurrency(rgcsi)
                        updater?.notifyProgress(3, getString(R.string.prgTotals))
                        val ts = TotalsSvc()
                        val rgti = ts.getTotalsForUser(AuthToken.m_szAuthToken, FlightQuery(), c)
                        ts.lastError.ifEmpty {
                            p.updateTotals(rgti)
                            updater?.notifyProgress(4, getString(R.string.packAndGoInProgress))
                            val fs = RecentFlightsSvc()
                            val rgle = fs.getRecentFlightsWithQueryAndOffset(
                                AuthToken.m_szAuthToken,
                                FlightQuery(),
                                0,
                                -1,
                                c
                            )
                            fs.lastError.ifEmpty {
                                p.updateFlights(rgle)
                                updater?.notifyProgress(5, getString(R.string.prgVisitedAirports))
                                val vs = VisitedAirportSvc()
                                val rgva = vs.getVisitedAirportsForUser(AuthToken.m_szAuthToken, c)
                                vs.lastError.ifEmpty {
                                    p.updateAirports(rgva)
                                    p.lastPackDate = Date()
                                    ""
                                }
                            }
                        }
                    }
                },
                { _, lastErr ->
                    if (lastErr.isNullOrEmpty())
                        updateStatus()
                },
                progBar,
                progTxt
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The usage of an interface lets you inject your own implementation
        val menuHost: MenuHost = requireActivity()

        // Add menu items without using the Fragment Menu APIs
        // Note how we can tie the MenuProvider to the viewLifecycleOwner
        // and an optional Lifecycle.State (here, RESUMED) to indicate when
        // the menu should be visible
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.optionsmenu, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                // Handle item selection
                when (item.itemId) {
                    R.id.menuContact -> contactUs()
                    R.id.menuFacebook -> viewFacebook()
                    else -> return false
                }
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mPermissionLauncher = registerForActivityResult(
            RequestPermission()
        ) { result: Boolean ->
            if (!result) {
                fPendingAutodetect = false
                fPendingRecord = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                result &&
                (fPendingRecord || fPendingAutodetect) && requireActivity().checkSelfPermission(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED &&
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) {
                AlertDialog.Builder(requireContext(), R.style.MFBDialog)
                    .setTitle(requireContext().packageManager.backgroundPermissionOptionLabel)
                    .setMessage(R.string.lblGPSRationale)
                    .setPositiveButton(R.string.lblOK) { _: DialogInterface?, _: Int ->
                        ActivityCompat.requestPermissions(
                            requireActivity(), arrayOf(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ), 0
                        )
                    }
                    .setNegativeButton(R.string.lblCancel) { d: DialogInterface, _: Int -> d.dismiss() }
                    .create()
                    .show()
            }
            (findViewById(R.id.ckAutodetect) as CheckBox?)!!.isChecked =
                fPendingAutodetect.also { MFBLocation.fPrefAutoDetect = it }
            (findViewById(R.id.ckRecord) as CheckBox?)!!.isChecked =
                fPendingRecord.also { MFBLocation.fPrefRecordFlight = it }
        }
        return inflater.inflate(R.layout.options, container, false)
    }

    private fun initCheckbox(id : Int, checked : Boolean, enabled : Boolean = true) {
        val ck = findViewById(id) as CheckBox
        ck.setOnClickListener(this)
        ck.isChecked = checked
        ck.isEnabled = enabled
    }

    private fun updateMapColors() {

        val vwRoute = findViewById(R.id.vwSampleRouteColor)
        with (vwRoute!!) {
            setBackgroundColor(ActFlightMap.RouteColor)
            setOnClickListener {
                val dlgPicker = DlgPickColor(requireActivity(), ActFlightMap.RouteColor) { c: Int ->
                    ActFlightMap.RouteColor = c
                    setBackgroundColor(c)
                }
                dlgPicker.show()
            }
        }

        val vwPath = findViewById(R.id.vwSamplePathColor)
        with(vwPath!!) {
            setBackgroundColor(ActFlightMap.PathColor)
            setOnClickListener {
                val dlgPicker = DlgPickColor(requireActivity(), ActFlightMap.PathColor) { c: Int ->
                    ActFlightMap.PathColor = c
                    setBackgroundColor(c)
                }
                dlgPicker.show()
            }
        }
    }

    private fun updateStatus() {
        // refresh sign-in status
        var t = findViewById(R.id.txtSignInStatus) as TextView?
        val bSignIn = findViewById(R.id.btnSignIn)
        val bSignOut = findViewById(R.id.btnSignOut)
        val bCreateAccount = findViewById(R.id.btnCreateNewAccount)
        val lblWhyAccount = findViewById(R.id.lblWhyAccount)
        if (isValid()) {
            t!!.text = String.format(this.getString(R.string.statusSignedIn), AuthToken.m_szEmail)
            bSignIn!!.visibility = View.GONE
            bSignOut!!.visibility = View.VISIBLE
            bCreateAccount!!.visibility = View.GONE
            lblWhyAccount!!.visibility = View.GONE
            findViewById(R.id.headerPackAndGo)!!.visibility = View.VISIBLE
            findViewById(R.id.sectPackAndGo)!!.visibility = View.VISIBLE
        } else {
            t!!.text = this.getString(R.string.statusNotSignedIn)
            bSignIn!!.visibility = View.VISIBLE
            bSignOut!!.visibility = View.GONE
            bCreateAccount!!.visibility = View.VISIBLE
            lblWhyAccount!!.visibility = View.VISIBLE
            findViewById(R.id.headerPackAndGo)!!.visibility = View.GONE
            findViewById(R.id.sectPackAndGo)!!.visibility = View.GONE
        }
        bSignOut.visibility = if (isValid()) View.VISIBLE else View.GONE
        t = findViewById(R.id.lblLastPacked) as TextView?
        val p = PackAndGo(requireContext())
        val dtLast = p.lastPackDate
        t!!.text =
            if (dtLast == null) getString(R.string.packAndGoStatusNone) else String.format(
                Locale.getDefault(),
                getString(R.string.packAndGoStatusOK),
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(dtLast)
            )
        addListener(R.id.btnSignIn)
        addListener(R.id.btnSignOut)
        addListener(R.id.btnCreateNewAccount)
        addListener(R.id.btnContact)
        addListener(R.id.btnFacebook)
        addListener(R.id.btnFAQ)
        addListener(R.id.btnCleanUp)
        addListener(R.id.btnSupport)
        addListener(R.id.btnAdditionalOptions)
        addListener(R.id.btnManageAccount)
        addListener(R.id.btnDeleteAccount)
        addListener(R.id.btnPackAndGo)

        val fHasGPS = hasGPS(requireContext())
        if (!fHasGPS) {
            MFBLocation.fPrefRecordFlightHighRes = false
            MFBLocation.fPrefRecordFlight = MFBLocation.fPrefRecordFlightHighRes
            MFBLocation.fPrefAutoDetect = MFBLocation.fPrefRecordFlight
        }

        initCheckbox(R.id.ckAutodetect, MFBLocation.fPrefAutoDetect, fHasGPS)
        initCheckbox(R.id.ckRecord, MFBLocation.fPrefRecordFlight, fHasGPS)
        initCheckbox(R.id.ckRecordHighRes, MFBLocation.fPrefRecordFlightHighRes, fHasGPS)
        initCheckbox(R.id.ckHeliports, Airport.fPrefIncludeHeliports, fHasGPS)
        initCheckbox(R.id.ckUseHHMM, DecimalEdit.DefaultHHMM)
        initCheckbox(R.id.ckUseLocalTime, DlgDatePicker.fUseLocalTime)
        initCheckbox(R.id.ckRoundNearestTenth, MFBLocation.fPrefRoundNearestTenth)
        initCheckbox(R.id.ckShowFlightImages, ActRecentsWS.fShowFlightImages)
        initCheckbox(R.id.ckCockpitTach, ActNewFlight.fShowTach)
        initCheckbox(R.id.ckCockpitHobbs, ActNewFlight.fShowHobbs)
        initCheckbox(R.id.ckCockpitEngine, ActNewFlight.fShowEngine)
        initCheckbox(R.id.ckCockpitBlock, ActNewFlight.fShowBlock)
        initCheckbox(R.id.ckCockpitFlight, ActNewFlight.fShowFlight)

        // Strings for spinner
        val rgAutoHobbs = arrayOf(
            getString(R.string.autoNone),
            getString(R.string.autoFlight),
            getString(R.string.autoEngine)
        )
        val rgAutoTotals = arrayOf(
            getString(R.string.autoNone),
            getString(R.string.autoFlight),
            getString(R.string.autoEngine),
            getString(R.string.autoHobbs),
            getString(R.string.autoBlock),
            getString(R.string.autoFlightStartEngineEnd)
        )

        var sp = findViewById(R.id.spnAutoHobbs) as Spinner?
        var adapter = ArrayAdapter(requireActivity(), R.layout.mfbsimpletextitem, rgAutoHobbs)
        sp!!.adapter = adapter
        sp.setSelection(MFBLocation.fPrefAutoFillHobbs.ordinal)
        sp.onItemSelectedListener = this
        sp.setPromptId(R.string.lblAutoFillOptions)
        sp = findViewById(R.id.spnAutoTime) as Spinner?
        adapter = ArrayAdapter(requireActivity(), R.layout.mfbsimpletextitem, rgAutoTotals)
        sp!!.adapter = adapter
        sp.setSelection(MFBLocation.fPrefAutoFillTime.ordinal)
        sp.onItemSelectedListener = this
        sp.setPromptId(R.string.lblAutoFillOptions)
        sp = findViewById(R.id.spnTOSpeed) as Spinner?
        adapter = ArrayAdapter(
            requireActivity(),
            R.layout.mfbsimpletextitem,
            getDisplaySpeeds().toTypedArray()
        )
        sp!!.adapter = adapter
        sp.setSelection(takeOffSpeedIndex)
        sp.onItemSelectedListener = this
        sp = findViewById(R.id.spnNightDef) as Spinner?
        adapter = ArrayAdapter(
            requireActivity(), R.layout.mfbsimpletextitem, arrayOf(
                getString(R.string.lblOptNightDefinitionCivilTwilight),
                getString(R.string.lblOptNightDefinitionSunset),
                getString(R.string.lblOptNightDefinitionSunsetPlus15),
                getString(R.string.lblOptNightDefinitionSunsetPlus30),
                getString(R.string.lblOptNightDefinitionSunsetPlus60)
            )
        )
        sp!!.adapter = adapter
        sp.setSelection(MFBLocation.NightPref.ordinal)
        sp.onItemSelectedListener = this
        sp.setPromptId(R.string.lblAutoFillOptions)
        sp = findViewById(R.id.spnNightMode) as Spinner?
        adapter = ArrayAdapter(
            requireActivity(), R.layout.mfbsimpletextitem, arrayOf(
                getString(R.string.lblNightModeAuto),
                getString(R.string.lblNightModeOff),
                getString(R.string.lblNightModeOn)
            )
        )
        sp!!.adapter = adapter
        sp.setSelection(MFBMain.NightModePref)
        sp.onItemSelectedListener = this
        sp.setPromptId(R.string.lblAutoFillOptions)
        sp = findViewById(R.id.spnNightLandingDef) as Spinner?
        adapter = ArrayAdapter(
            requireActivity(), R.layout.mfbsimpletextitem, arrayOf(
                getString(R.string.lblOptNightLandingsSunsetPlus1hour),
                getString(R.string.lblOptNightLandingsNight)
            )
        )
        sp!!.adapter = adapter
        sp.setSelection(MFBLocation.NightLandingPref.ordinal)
        sp.onItemSelectedListener = this
        sp.setPromptId(R.string.lblAutoFillOptions)
        sp = findViewById(R.id.spnFlightDetail) as Spinner?
        adapter = ArrayAdapter(
            requireActivity(), R.layout.mfbsimpletextitem, arrayOf(
                getString(R.string.lblFlightDetailLow),
                getString(R.string.lblFlightDetailMed),
                getString(R.string.lblFlightDetailHigh)
            )
        )
        sp!!.adapter = adapter
        sp.setSelection(ActRecentsWS.flightDetail.ordinal)
        sp.onItemSelectedListener = this
        sp.setPromptId(R.id.lblShowFlightTimes)
        sp = findViewById(R.id.spnAltUnits) as Spinner?
        adapter = ArrayAdapter(
            requireActivity(), R.layout.mfbsimpletextitem, arrayOf(
                getString(R.string.lblOptUnitsFeet), getString(R.string.lblOptUnitsMeters)
            )
        )
        sp!!.adapter = adapter
        sp.setSelection(altitudeUnits.ordinal)
        sp.onItemSelectedListener = this
        sp = findViewById(R.id.spnSpeedUnits) as Spinner?
        adapter = ArrayAdapter(
            requireActivity(), R.layout.mfbsimpletextitem, arrayOf(
                getString(R.string.lblOptUnitsKnots),
                getString(R.string.lblOptUnitsKPH),
                getString(R.string.lblOptUnitsMPH)
            )
        )
        sp!!.adapter = adapter
        sp.setSelection(speedUnits.ordinal)
        sp.onItemSelectedListener = this

        // These are in the same order of GoogleMap.MAP_TYPE_xx to make easy mapping because I'm lazy.
        val rgMapTypes = arrayOf(
            getString(R.string.lblMapStyleStandard),
            getString(R.string.lblMapStyleSatellite),
            getString(R.string.lblMapStyleTerrain),
            getString(R.string.lblMapStyleHybrid),
        )
        sp = findViewById(R.id.spnMapStyle) as Spinner?
        adapter = ArrayAdapter(requireActivity(), R.layout.mfbsimpletextitem, rgMapTypes)
        sp!!.adapter = adapter
        sp.onItemSelectedListener = this
        sp.setSelection(ActFlightMap.MapType - 1)

        t = findViewById(R.id.txtCopyright) as TextView
        t.text = String.format(Locale.getDefault(), "%s %s %s", getString(R.string.lblCopyright), MFBMain.versionName,
        if (MFBConstants.fIsDebug) " - DEBUG (" + MFBConstants.szIP + ")" else "")

        updateMapColors()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        val sp = parent as Spinner
        val i = sp.selectedItemPosition
        val spid = sp.id
        if (spid == R.id.spnAutoHobbs) MFBLocation.fPrefAutoFillHobbs =
            MFBLocation.AutoFillOptions.values()[i] else if (spid == R.id.spnAutoTime) MFBLocation.fPrefAutoFillTime =
            MFBLocation.AutoFillOptions.values()[i] else if (spid == R.id.spnTOSpeed) takeOffSpeedIndex =
            i else if (spid == R.id.spnNightDef) MFBLocation.NightPref =
            MFBLocation.NightCriteria.values()[i] else if (spid == R.id.spnNightLandingDef) MFBLocation.NightLandingPref =
            MFBLocation.NightLandingCriteria.values()[i] else if (spid == R.id.spnFlightDetail) ActRecentsWS.flightDetail =
            FlightDetail.values()[i] else if (spid == R.id.spnNightMode) {
            if (MFBMain.NightModePref != i) {
                MFBMain.NightModePref = i
                AppCompatDelegate.setDefaultNightMode(MFBMain.NightModePref)
                if (activity != null) {
                    requireActivity().recreate()
                }
            }
        } else if (spid == R.id.spnAltUnits) altitudeUnits =
            AltitudeUnits.values()[i] else if (spid == R.id.spnSpeedUnits) speedUnits =
            SpeedUnits.values()[i]
        else if (spid == R.id.spnMapStyle) {
            ActFlightMap.MapType = pos + 1
        }
    }

    private fun contactUs() {
        ActWebView.viewURL(
            requireActivity(), String.format(
                MFBConstants.urlContact,
                MFBConstants.szIP,
                AuthToken.m_szEmail,
                "Comment from Android user",
                nightParam(
                    context
                )
            )
        )
    }

    private fun viewFacebook() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(MFBConstants.urlFacebook))
        startActivity(i)
    }

    private fun cleanUp() {
        var fOrphansFound = false

        // Clean up any:
        //  (a) orphaned new flights (shouldn't happen, but could)
        //  (b) any flight images that are not associated with pending or new flights
        //  (c) any aircraft images that have not yet been posted

        // first make sure we're only working on one new flight at a time.
        val rgLeNew = newFlights
        val leInProgress = MFBMain.newFlightListener?.getInProgressFlight(requireActivity())
        if (leInProgress != null && leInProgress.idLocalDB > 0) {
            for (le in rgLeNew) if (le.idLocalDB != leInProgress.idLocalDB) {
                Log.e(
                    MFBConstants.LOG_TAG,
                    String.format("FOUND ORPHANED FLIGHT: %d", le.idLocalDB)
                )
                le.idFlight =
                    LogbookEntry.ID_QUEUED_FLIGHT_UNSUBMITTED // put it into queued flights for review.
                le.toDB()
                clearCachedFlights()
                fOrphansFound = true
            }
        }

        // Now look for orphaned flight image files.  Start with the known flight images
        val rgLeAll = mergeFlightLists(rgLeNew, queuedAndUnsubmittedFlights)
        val alImages = ArrayList<String?>()
        for (le in rgLeAll!!) {
            le.imagesForFlight
            for (mfbii in le.rgFlightImages!!) alImages.add(mfbii.imageFile)
        }

        // Now delete the flight images that are not in our list
        deleteOrphansNotInList(PictureDestination.FlightImage, alImages, requireActivity())

        // Clean up any orphaned aircraft images
        // We can delete ALL aircraft images - if they weren't submitted, they aren't going to be picked up.

        // First delete all of the ones that haven't been saved to the server
        val rgMfbiiAircraft = allAircraftImages
        for (mfbii in rgMfbiiAircraft) if (!mfbii.isOnServer()) mfbii.deleteFromDB()

        // now delete any remaining aircraft images that might be in our files.
        deleteOrphansNotInList(PictureDestination.AircraftImage, ArrayList(), requireActivity())
        alert(
            this,
            getString(R.string.lblCleanup),
            getString(if (fOrphansFound) R.string.errCleanupOrphansFound else R.string.txtCleanupComplete)
        )
    }

    override fun onNothingSelected(arg0: AdapterView<*>?) {}
    private fun viewPreferences(szURL: String) {
        if (!isValid()) {
            alert(this, getString(R.string.txtError), getString(R.string.statusNotSignedIn))
            return
        }
        ActWebView.viewURL(requireActivity(), szURL)
    }

    override fun onClick(v: View) {
        when (val id = v.id) {
            R.id.btnSignIn -> {
                val d = DlgSignIn(requireActivity())
                d.setOnDismissListener { updateStatus() }
                d.show()
            }
            R.id.btnSignOut -> {
                AuthToken.m_szPass = ""
                AuthToken.m_szEmail = AuthToken.m_szPass
                AuthToken.m_szAuthToken = AuthToken.m_szEmail
                AuthToken().flushCache()
                PackAndGo(requireContext()).clearPackedData()
                MFBMain.invalidateAll()
                updateStatus()
            }
            R.id.btnCreateNewAccount -> {
                startActivity(Intent(v.context, ActNewUser::class.java))
            }
            R.id.ckAutodetect, R.id.ckRecord -> {
                fPendingAutodetect =
                    if (id == R.id.ckAutodetect) (v as CheckBox).isChecked else (findViewById(R.id.ckAutodetect) as CheckBox?)!!.isChecked
                fPendingRecord =
                    if (id == R.id.ckRecord) (v as CheckBox).isChecked else (findViewById(R.id.ckRecord) as CheckBox?)!!.isChecked
                mPermissionLauncher!!.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            R.id.ckRecordHighRes -> MFBLocation.fPrefRecordFlightHighRes =
                (v as CheckBox).isChecked
            R.id.ckHeliports -> Airport.fPrefIncludeHeliports =
                (v as CheckBox).isChecked
            R.id.ckUseHHMM -> DecimalEdit.DefaultHHMM =
                (v as CheckBox).isChecked
            R.id.ckUseLocalTime -> DlgDatePicker.fUseLocalTime =
                (v as CheckBox).isChecked
            R.id.ckRoundNearestTenth -> MFBLocation.fPrefRoundNearestTenth =
                (v as CheckBox).isChecked
            R.id.ckShowFlightImages -> ActRecentsWS.fShowFlightImages =
                (v as CheckBox).isChecked
            R.id.btnContact -> contactUs()
            R.id.btnFacebook -> viewFacebook()
            R.id.btnCleanUp -> cleanUp()
            R.id.btnSupport -> viewPreferences(
                authRedirWithParams("d=donate", context, false)
            )
            R.id.btnAdditionalOptions -> viewPreferences(
                authRedirWithParams(
                    "d=profile",
                    context
                )
            )
            R.id.btnManageAccount -> viewPreferences(authRedirWithParams("d=account", context))
            R.id.btnDeleteAccount -> viewPreferences(authRedirWithParams("d=bigredbuttons", context))
            R.id.btnFAQ -> ActWebView.viewURL(
                requireActivity(), MFBConstants.urlFAQ
            )
            R.id.btnPackAndGo -> packData()
            R.id.ckCockpitTach -> ActNewFlight.fShowTach = (v as CheckBox).isChecked
            R.id.ckCockpitHobbs -> ActNewFlight.fShowHobbs = (v as CheckBox).isChecked
            R.id.ckCockpitEngine -> ActNewFlight.fShowEngine = (v as CheckBox).isChecked
            R.id.ckCockpitBlock -> ActNewFlight.fShowBlock = (v as CheckBox).isChecked
            R.id.ckCockpitFlight -> ActNewFlight.fShowFlight = (v as CheckBox).isChecked
        }
    }

    companion object {
        @JvmField
        var altitudeUnits = AltitudeUnits.Feet
        @JvmField
        var speedUnits = SpeedUnits.Knots
    }
}