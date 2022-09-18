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
package com.myflightbook.android

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.View.OnFocusChangeListener
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.app.ShareCompat.IntentBuilder
import androidx.lifecycle.lifecycleScope
import com.myflightbook.android.ActMFBForm.GallerySource
import com.myflightbook.android.ActOptions.AltitudeUnits
import com.myflightbook.android.ActOptions.SpeedUnits
import com.myflightbook.android.DlgDatePicker.DateTimeUpdate
import com.myflightbook.android.MFBMain.Invalidatable
import com.myflightbook.android.webservices.*
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.CustomPropertyTypesSvc.Companion.cachedPropertyTypes
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import com.myflightbook.android.webservices.RecentFlightsSvc.Companion.clearCachedFlights
import com.myflightbook.android.webservices.UTCDate.isNullDate
import kotlinx.coroutines.launch
import model.*
import model.Aircraft.Companion.getAircraftById
import model.Aircraft.Companion.getHighWaterHobbsForAircraft
import model.Aircraft.Companion.getHighWaterTachForAircraft
import model.Airport.Companion.appendCodeToRoute
import model.Airport.Companion.appendNearestToRoute
import model.CustomPropertyType.Companion.getPinnedProperties
import model.CustomPropertyType.Companion.isPinnedProperty
import model.DecimalEdit.CrossFillDelegate
import model.FlightProperty.Companion.crossProduct
import model.FlightProperty.Companion.distillList
import model.FlightProperty.Companion.rewritePropertiesForFlight
import model.GPSSim.Companion.autoFill
import model.LogbookEntry.SigStatus
import model.MFBConstants.nightParam
import model.MFBFlightListener.ListenerFragmentDelegate
import model.MFBImageInfo.PictureDestination
import model.MFBLocation.AutoFillOptions
import model.MFBLocation.Companion.getMainLocation
import model.MFBLocation.Companion.hasGPS
import model.MFBLocation.GPSQuality
import model.MFBUtil.alert
import model.MFBUtil.getForKey
import model.MFBUtil.nowWith0Seconds
import model.MFBUtil.removeForKey
import model.MFBUtil.removeSeconds
import model.PropertyTemplate.Companion.anonTemplate
import model.PropertyTemplate.Companion.defaultTemplates
import model.PropertyTemplate.Companion.getSharedTemplates
import model.PropertyTemplate.Companion.mergeTemplates
import model.PropertyTemplate.Companion.simTemplate
import model.PropertyTemplate.Companion.templatesWithIDs
import java.io.UnsupportedEncodingException
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

class ActNewFlight : ActMFBForm(), View.OnClickListener, ListenerFragmentDelegate, DateTimeUpdate,
    PropertyEdit.PropertyListener, GallerySource, CrossFillDelegate, Invalidatable {
    private var mRgac: Array<Aircraft>? = null
    private var mle: LogbookEntry? = null
    private var mActivetemplates: HashSet<PropertyTemplate?>? = HashSet()
    private var needsDefaultTemplates = true
    private var txtQuality: TextView? = null
    private var txtStatus: TextView? = null
    private var txtSpeed: TextView? = null
    private var txtAltitude: TextView? = null
    private var txtSunrise: TextView? = null
    private var txtSunset: TextView? = null
    private var txtLatitude: TextView? = null
    private var txtLongitude: TextView? = null
    private var imgRecording: ImageView? = null
    private var mHandlerupdatetimer: Handler? = null
    private var mUpdateelapsedtimetask: Runnable? = null
    private var mTimeCalcLauncher: ActivityResultLauncher<Intent>? = null
    private var mApproachHelperLauncher: ActivityResultLauncher<Intent>? = null
    private var mMapRouteLauncher: ActivityResultLauncher<Intent>? = null
    private var mTemplateLauncher: ActivityResultLauncher<Intent>? = null
    private var mPropertiesLauncher: ActivityResultLauncher<Intent>? = null
    private var mAppendAdhocLauncher: ActivityResultLauncher<String>? = null
    private var mAppendNearestLauncher: ActivityResultLauncher<String>? = null
    private var mAddAircraftLauncher: ActivityResultLauncher<Intent>? = null

    private fun deleteFlight(le : LogbookEntry?) {
        if (le == null)
            return
        val soapCall = when { (le is PendingFlight) -> PendingFlightSvc() else -> DeleteFlightSvc() }
        val pf = le as? PendingFlight

        lifecycleScope.launch {
            doAsync<MFBSoap, Boolean?>(
                requireActivity(),
                soapCall,
                getString(R.string.prgDeletingFlight),
                {
                    s ->
                    if (pf != null && pf.getPendingID().isNotEmpty())
                        ActRecentsWS.cachedPendingFlights = (s as PendingFlightSvc).deletePendingFlight(AuthToken.m_szAuthToken, pf.getPendingID(), requireContext())
                    else
                        (s as DeleteFlightSvc).deleteFlightForUser(AuthToken.m_szAuthToken, le.idFlight, requireContext())
                    s.lastError.isEmpty()
                },
                {
                    _, result ->
                    if (result!!) {
                        clearCachedFlights()
                        MFBMain.invalidateCachedTotals()
                        finish()
                    }
                }
            )
        }
    }

    private fun submitFlight(le : LogbookEntry?) {
        if (le == null)
            return
        val fIsNew = le.isNewFlight()

        val pf = le as? PendingFlight

        lifecycleScope.launch {
            doAsync<MFBSoap, Boolean?>(
                requireActivity(),
                if (le.fForcePending || (pf != null && pf.getPendingID().isNotEmpty())) PendingFlightSvc() else CommitFlightSvc(),
                getString(R.string.prgSavingFlight),
                { s -> submitFlightWorker(le, s) },
                { _, result ->
                    if (result!!) {
                        MFBMain.invalidateCachedTotals()

                        // success, so we our cached recents are invalid
                        clearCachedFlights()
                        val ocl: DialogInterface.OnClickListener

                        // the flight was successfully saved, so delete any local copy regardless
                        le.deleteUnsubmittedFlightFromLocalDB()
                        if (fIsNew) {
                            // Reset the flight and we stay on this page
                            resetFlight(true)
                            ocl =
                                DialogInterface.OnClickListener { d: DialogInterface, _: Int -> d.cancel() }
                        } else {
                            // no need to reset the current flight because we will finish.
                            ocl = DialogInterface.OnClickListener { d: DialogInterface, _: Int ->
                                d.cancel()
                                finish()
                            }
                            mle =
                                null  // so that onPause won't cause it to be saved on finish() call.
                        }
                        AlertDialog.Builder(requireActivity(), R.style.MFBDialog)
                            .setMessage(getString(R.string.txtSavedFlight))
                            .setTitle(getString(R.string.txtSuccess))
                            .setNegativeButton("OK", ocl)
                            .create().show()
                    } else
                        alert(requireContext(), getString(R.string.txtError), le.szError)
                })
        }
    }

    private fun submitFlightWorker(le : LogbookEntry, s : MFBSoap) : Boolean {
        /*
    Scenarios:
     - fForcePending is false, Regular flight, new or existing: call CommitFlightWithOptions
     - fForcePending is false, Pending flight without a pending ID call CommitFlightWithOptions.  Shouldn't happen, but no big deal if it does
     - fForcePending is false, Pending flight with a Pending ID: call CommitPendingFlight to commit it
     - fForcePending is false, Pending flight without a pending ID: THROW EXCEPTION, how did this happen?

     - fForcePending is true, Regular flight that is not new/pending (sorry about ambiguous "pending"): THROW EXCEPTION; this is an error
     - fForcePending is true, Regular flight that is NEW: call CreatePendingFlight
     - fForcePending is true, PendingFlight without a PendingID: call CreatePendingFlight.  Shouldn't happen, but no big deal if it does
     - fForcePending is true, PendingFlight with a PendingID: call UpdatePendingFlight
 */
        val pf = le as? PendingFlight
        if (le.fForcePending) {
            val pfs = s as PendingFlightSvc
            check(!le.isExistingFlight()) { "Attempt to save an existing flight as a pending flight" }
            if (pf == null || pf.getPendingID().isEmpty()) {
                pfs.createPendingFlight(AuthToken.m_szAuthToken, le, requireContext())
                    .also { ActRecentsWS.cachedPendingFlights = it }
                le.szError = pfs.lastError
            } else {
                // existing pending flight but still force pending - call updatependingflight
                pfs.updatePendingFlight(AuthToken.m_szAuthToken, pf, requireContext())
                    .also { ActRecentsWS.cachedPendingFlights = it }
                le.szError = pfs.lastError
            }
        } else {
            // Not force pending.
            // If regular flight (new or existing), or pending without a pendingID
            if (pf == null || pf.getPendingID().isEmpty()) {
                val cf = s as CommitFlightSvc
                cf.fCommitFlightForUser(AuthToken.m_szAuthToken, le, requireContext())
                le.szError = cf.lastError
            } else {
                // By definition, here pf is non-null and it has a pending ID so it is a valid pending flight and we are not forcing - call commitpendingflight
                val pfs = s as PendingFlightSvc
                val rgpf =
                    pfs.commitPendingFlight(AuthToken.m_szAuthToken, pf, requireContext())
                ActRecentsWS.cachedPendingFlights = rgpf
                pf.szError = pfs.lastError
            }
        }
        return le.szError.isEmpty()
    }

    private fun fetchDigitizedSig(url : String?, iv : ImageView) {
        if (url == null || url.isEmpty())
            return
        lifecycleScope.launch {
            doAsync<String, Bitmap?>(
                requireActivity(),
                url,
                null,
                {
                    url ->
                    try {
                        val str = URL(url).openStream()
                        BitmapFactory.decodeStream(str)
                    } catch (e: Exception) {
                        Log.e(MFBConstants.LOG_TAG, e.message!!)
                        null
                    }
                },
                { _, result ->
                    if (result != null)
                        iv.setImageBitmap(result)
                }
            )
        }
    }

    private fun refreshAircraft() {
        lifecycleScope.launch {
            doAsync<AircraftSvc, Array<Aircraft>?>(
                requireActivity(),
                AircraftSvc(),
                getString(R.string.prgAircraft),
                {
                    s -> s.getAircraftForUser(AuthToken.m_szAuthToken, requireContext())
                },
                {
                    s, result ->
                    if (result == null)
                        alert(requireActivity(),getString(R.string.txtError), s.lastError)
                    else
                        refreshAircraft(result, false)
                }
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.newflight, container, false)
    }

    override fun onStop() {
        super.onStop()
        if (mle == null || mle!!.isNewFlight()) MFBMain.setInProgressFlightActivity(context, null)
    }

    private fun setUpActivityLaunchers() {
        mApproachHelperLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val approachDesc = result.data!!.getStringExtra(ActAddApproach.APPROACHDESCRIPTIONRESULT)
                if (approachDesc!!.isNotEmpty()) {
                    mle!!.addApproachDescription(approachDesc)
                    val cApproachesToAdd = result.data!!.getIntExtra(ActAddApproach.APPROACHADDTOTOTALSRESULT, 0)
                    if (cApproachesToAdd > 0) {
                        mle!!.cApproaches += cApproachesToAdd
                        setIntForField(R.id.txtApproaches, mle!!.cApproaches)
                    }
                    toView()
                }
            }
        }
        mTimeCalcLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                setDoubleForField(
                    R.id.txtTotal,
                    result.data!!.getDoubleExtra(ActTimeCalc.COMPUTED_TIME, mle!!.decTotal)
                        .also { mle!!.decTotal = it })
            }
        }
        mMapRouteLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                mle!!.szRoute = result.data!!.getStringExtra(ActFlightMap.ROUTEFORFLIGHT)!!
                setStringForField(R.id.txtRoute, mle!!.szRoute)
                findViewById(R.id.txtRoute)!!.requestFocus()
            }
        }
        mTemplateLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val b = result.data!!.extras
                try {
                    val o = b!!.getSerializable(ActViewTemplates.ACTIVE_PROPERTYTEMPLATES)
                    mActivetemplates = o as HashSet<PropertyTemplate?>?
                    updateTemplatesForAircraft(true)
                    toView()
                } catch (ex: ClassCastException) {
                    Log.e(MFBConstants.LOG_TAG, ex.message!!)
                }
            }
        }
        mPropertiesLauncher = registerForActivityResult(
            StartActivityForResult()
        ) {
            setUpPropertiesForFlight()
            if (MFBLocation.fPrefAutoFillTime === AutoFillOptions.BlockTime) doAutoTotals()
        }
        mAppendAdhocLauncher = registerForActivityResult(
            RequestPermission()
        ) { result: Boolean ->
            if (result) {
                val loc = getMainLocation()!!
                if (loc.currentLoc() == null) return@registerForActivityResult
                val szAdHoc = LatLong(loc.currentLoc()!!).toAdHocLocString()
                val txtRoute = findViewById(R.id.txtRoute) as TextView?
                mle!!.szRoute = appendCodeToRoute(txtRoute!!.text.toString(), szAdHoc)
                txtRoute.text = mle!!.szRoute
            }
        }
        mAppendNearestLauncher = registerForActivityResult(
            RequestPermission()
        ) { result: Boolean ->
            if (result) {
                val txtRoute = findViewById(R.id.txtRoute) as TextView?
                mle!!.szRoute = appendNearestToRoute(
                    txtRoute!!.text.toString(), getMainLocation()!!.currentLoc()
                )
                txtRoute.text = mle!!.szRoute
            }
        }
        mAddAircraftLauncher = registerForActivityResult(
            StartActivityForResult()
        ) {
            val rgac = AircraftSvc().cachedAircraft
            mRgac = rgac
            refreshAircraft(mRgac, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpActivityLaunchers()
        addListener(R.id.btnFlightSet)
        addListener(R.id.btnFlightStartSet)
        addListener(R.id.btnEngineStartSet)
        addListener(R.id.btnFlightEndSet)
        addListener(R.id.btnEngineEndSet)
        addListener(R.id.btnBlockOut)
        addListener(R.id.btnBlockIn)
        addListener(R.id.btnProps)
        addListener(R.id.btnAppendNearest)
        addListener(R.id.btnAddAircraft)
        findViewById(R.id.btnAppendNearest)!!.setOnLongClickListener {
            appendAdHoc()
            true
        }

        // Expand/collapse
        addListener(R.id.txtViewInTheCockpit)
        addListener(R.id.txtImageHeader)
        addListener(R.id.txtPinnedPropertiesHeader)
        addListener(R.id.txtSignatureHeader)
        enableCrossFill(R.id.txtNight)
        enableCrossFill(R.id.txtSimIMC)
        enableCrossFill(R.id.txtIMC)
        enableCrossFill(R.id.txtXC)
        enableCrossFill(R.id.txtDual)
        enableCrossFill(R.id.txtGround)
        enableCrossFill(R.id.txtCFI)
        enableCrossFill(R.id.txtSIC)
        enableCrossFill(R.id.txtPIC)
        enableCrossFill(R.id.txtHobbsStart)
        enableCrossFill(R.id.txtTachStart)
        findViewById(R.id.txtTotal)!!.setOnLongClickListener {
            val i = Intent(requireActivity(), ActTimeCalc::class.java)
            i.putExtra(ActTimeCalc.INITIAL_TIME, doubleFromField(R.id.txtTotal))
            mTimeCalcLauncher!!.launch(i)
            true
        }
        findViewById(R.id.btnFlightSet)!!.setOnLongClickListener {
            resetDateOfFlight()
            true
        }
        var b = findViewById(R.id.btnPausePlay) as ImageButton?
        b!!.setOnClickListener(this)
        b = findViewById(R.id.btnViewOnMap) as ImageButton?
        b!!.setOnClickListener(this)
        b = findViewById(R.id.btnAddApproach) as ImageButton?
        b!!.setOnClickListener(this)

        // cache these views for speed.
        txtQuality = findViewById(R.id.txtFlightGPSQuality) as TextView?
        txtStatus = findViewById(R.id.txtFlightStatus) as TextView?
        txtSpeed = findViewById(R.id.txtFlightSpeed) as TextView?
        txtAltitude = findViewById(R.id.txtFlightAltitude) as TextView?
        txtSunrise = findViewById(R.id.txtSunrise) as TextView?
        txtSunset = findViewById(R.id.txtSunset) as TextView?
        txtLatitude = findViewById(R.id.txtLatitude) as TextView?
        txtLongitude = findViewById(R.id.txtLongitude) as TextView?
        imgRecording = findViewById(R.id.imgRecording) as ImageView?

        // get notification of hobbs changes, or at least focus changes
        val s =
            OnFocusChangeListener { v: View, hasFocus: Boolean -> if (!hasFocus) onHobbsChanged(v) }
        findViewById(R.id.txtHobbsStart)!!.onFocusChangeListener = s
        findViewById(R.id.txtHobbsEnd)!!.onFocusChangeListener = s
        val i = requireActivity().intent
        val keyForLeToView = i.getStringExtra(ActRecentsWS.VIEWEXISTINGFLIGHT)
        val leToView = if (keyForLeToView == null) null else getForKey(keyForLeToView) as LogbookEntry?
        removeForKey(keyForLeToView)
        val fIsNewFlight = leToView == null
        if (!fIsNewFlight && mRgac == null) mRgac = AircraftSvc().cachedAircraft
        val sp = findViewById(R.id.spnAircraft) as Spinner?
        sp!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val ac = parent.selectedItem as Aircraft
                if (mle != null && mle!!.idAircraft != ac.aircraftID) {
                    if (ac.aircraftID == -1) {   // show all!
                        refreshAircraft(mRgac, true)
                        sp.performClick()
                    } else {
                        fromView()
                        mle!!.idAircraft = ac.aircraftID
                        updateTemplatesForAircraft(false)
                        toView()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // set for no focus.
        findViewById(R.id.btnFlightSet)!!.requestFocus()
        if (fIsNewFlight) {
            // re-use the existing in-progress flight
            mle = MFBMain.newFlightListener?.getInProgressFlight(requireActivity())
            MFBMain.registerNotifyResetAll(this)
            val pref = requireActivity().getPreferences(Context.MODE_PRIVATE)
            val fExpandCockpit = pref.getBoolean(m_KeyShowInCockpit, true)
            setExpandedState(
                (findViewById(R.id.txtViewInTheCockpit) as TextView?)!!,
                findViewById(R.id.sectInTheCockpit)!!,
                fExpandCockpit,
                false
            )
        } else {
            // view an existing flight
            mle = leToView
            if (mle!!.isExistingFlight() || mle is PendingFlight) {
                mle!!.toDB() // ensure that this is in the database - above call could have pulled from cache
                rewritePropertiesForFlight(mle!!.idLocalDB, mle!!.rgCustomProperties)
            }
            setUpPropertiesForFlight()
        }
        if (mle != null && mle!!.rgFlightImages == null) mle!!.imagesForFlight

        // Refresh aircraft on create
        if (isValid() && (mRgac == null || mRgac!!.isEmpty()))
            refreshAircraft()
        Log.w(
            MFBConstants.LOG_TAG,
            String.format(
                "ActNewFlight - created, m_le is %s",
                if (mle == null) "null" else "non-null"
            )
        )
    }

    private fun enableCrossFill(id: Int) {
        val de = findViewById(id) as DecimalEdit?
        de!!.setDelegate(this)
    }

    override fun crossFillRequested(sender: DecimalEdit?) {
        fromView()
        if (sender!!.id == R.id.txtHobbsStart) {
            val d = getHighWaterHobbsForAircraft(mle!!.idAircraft)
            if (d > 0) sender.doubleValue = d
        } else if (sender.id == R.id.txtTachStart) {
            val d = getHighWaterTachForAircraft(mle!!.idAircraft)
            if (d > 0) sender.doubleValue = d
        } else if (mle!!.decTotal > 0) sender.doubleValue = mle!!.decTotal
        fromView()
    }

    private fun selectibleAircraft(): Array<Aircraft>? {
        if (mRgac == null) return null
        val lst: MutableList<Aircraft> = ArrayList()
        for (ac in mRgac!!) {
            if (!ac.hideFromSelection || mle != null && ac.aircraftID == mle!!.idAircraft) lst.add(
                ac
            )
        }
        if (lst.size == 0) return mRgac
        if (lst.size != mRgac!!.size) {    // some aircraft are filtered
            // Issue #202 - add a "Show all Aircraft" option
            val ac = Aircraft()
            ac.aircraftID = -1
            ac.modelDescription = getString(R.string.fqShowAllAircraft)
            ac.tailNumber = "#"
            lst.add(ac)
        }
        return lst.toTypedArray()
    }

    private fun refreshAircraft(rgac: Array<Aircraft>?, fShowAll: Boolean) {
        mRgac = rgac
        val spnAircraft = findViewById(R.id.spnAircraft) as Spinner?
        val rgFilteredAircraft = if (fShowAll) rgac else selectibleAircraft()
        if (rgFilteredAircraft != null && rgFilteredAircraft.isNotEmpty()) {
            var pos = 0
            for (i in rgFilteredAircraft.indices) {
                if (mle == null || mle!!.idAircraft == rgFilteredAircraft[i].aircraftID) {
                    pos = i
                    break
                }
            }

            // Create a list of the aircraft to show, which are the ones that are not hidden OR the active one for the flight
            val adapter = ArrayAdapter(
                requireActivity(), R.layout.mfbsimpletextitem, rgFilteredAircraft
            )
            spnAircraft!!.adapter = adapter
            spnAircraft.setSelection(pos)
            // need to notifydatasetchanged or else setselection doesn't
            // update correctly.
            adapter.notifyDataSetChanged()
        } else {
            spnAircraft!!.prompt = getString(R.string.errNoAircraftFoundShort)
            alert(this, getString(R.string.txtError), getString(R.string.errMustCreateAircraft))
        }
    }

    override fun onResume() {
        // refresh the aircraft list (will be cached if we already have it)
        // in case a new aircraft has been added.
        super.onResume()
        if (!isValid()) {
            val d = DlgSignIn(requireActivity())
            d.show()
        }
        val rgac = AircraftSvc().cachedAircraft
        mRgac = rgac
        if (mRgac != null) refreshAircraft(mRgac, false)

        // Not sure why le can sometimes be empty here...
        if (mle == null) mle =
            MFBMain.newFlightListener?.getInProgressFlight(requireActivity())

        // show/hide GPS controls based on whether this is a new flight or existing.
        val fIsNewFlight = mle!!.isNewFlight()
        val l = findViewById(R.id.sectGPS) as LinearLayout?
        l!!.visibility = if (fIsNewFlight) View.VISIBLE else View.GONE
        findViewById(R.id.btnAppendNearest)!!.visibility =
            if (fIsNewFlight && hasGPS(requireContext())) View.VISIBLE else View.GONE
        val btnViewFlight = findViewById(R.id.btnViewOnMap) as ImageButton?
        btnViewFlight!!.visibility = if (MFBMain.hasMaps()) View.VISIBLE else View.GONE
        if (fIsNewFlight) {
            // ensure that we keep the elapsed time up to date
            updatePausePlayButtonState()
            mHandlerupdatetimer = Handler(Looper.getMainLooper())
            val elapsedTimeTask = object : Runnable {
                override fun run() {
                    updateElapsedTime()
                    mHandlerupdatetimer!!.postDelayed(this, 1000)
                }
            }
            mUpdateelapsedtimetask = elapsedTimeTask
            mHandlerupdatetimer!!.postDelayed(elapsedTimeTask, 1000)
        }
        setUpGalleryForFlight()
        restoreState()

        // set the ensure that we are following the right numerical format
        setDecimalEditMode(R.id.txtCFI)
        setDecimalEditMode(R.id.txtDual)
        setDecimalEditMode(R.id.txtGround)
        setDecimalEditMode(R.id.txtIMC)
        setDecimalEditMode(R.id.txtNight)
        setDecimalEditMode(R.id.txtPIC)
        setDecimalEditMode(R.id.txtSIC)
        setDecimalEditMode(R.id.txtSimIMC)
        setDecimalEditMode(R.id.txtTotal)
        setDecimalEditMode(R.id.txtXC)

        // Make sure the date of flight is up-to-date
        if (mle!!.isKnownEngineStart || mle!!.isKnownFlightStart) resetDateOfFlight()

        // First resume after create should pull in default templates;
        // subsequent resumes should NOT.
        updateTemplatesForAircraft(!needsDefaultTemplates)
        needsDefaultTemplates = false // reset this.
        toView()

        // do this last to start GPS service
        if (fIsNewFlight) MFBMain.setInProgressFlightActivity(context, this)
    }

    private fun setUpGalleryForFlight() {
        if (mle!!.rgFlightImages == null) mle!!.imagesForFlight
        setUpImageGallery(getGalleryID(), mle!!.rgFlightImages, getGalleryHeader())
    }

    override fun onPause() {
        super.onPause()

        // should only happen when we are returning from viewing an existing/queued/pending flight, may have been submitted
        // either way, no need to save this
        if (mle == null) return
        fromView()
        saveCurrentFlight()
        Log.w(MFBConstants.LOG_TAG, String.format("Paused, landings are %d", mle!!.cLandings))
        saveState()

        // free up some scheduling resources.
        if (mHandlerupdatetimer != null) mHandlerupdatetimer!!.removeCallbacks(
            mUpdateelapsedtimetask!!
        )
    }

    private fun restoreState() {
        try {
            val mPrefs = requireActivity().getPreferences(Activity.MODE_PRIVATE)
            fPaused = mPrefs.getBoolean(m_KeysIsPaused, false)
            dtPauseTime = mPrefs.getLong(m_KeysPausedTime, 0)
            dtTimeOfLastPause = mPrefs.getLong(m_KeysTimeOfLastPause, 0)
            accumulatedNight = mPrefs.getFloat(m_KeysAccumulatedNight, 0.0.toFloat()).toDouble()
        } catch (e: Exception) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
        }
    }

    private fun saveState() {
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
        val ed = requireActivity().getPreferences(Activity.MODE_PRIVATE).edit()
        ed.putBoolean(m_KeysIsPaused, fPaused)
        ed.putLong(m_KeysPausedTime, dtPauseTime)
        ed.putLong(m_KeysTimeOfLastPause, dtTimeOfLastPause)
        ed.putFloat(m_KeysAccumulatedNight, accumulatedNight.toFloat())
        ed.apply()
    }

    override fun saveCurrentFlight() {
        if (!mle!!.isExistingFlight()) mle!!.toDB()
        if (activity == null) // sometimes not yet set up
            return

        // and we only want to save the current flightID if it is a new (not queued!) flight
        if (mle!!.isNewFlight()) MFBMain.newFlightListener?.saveCurrentFlightId(activity)
    }

    private fun setLogbookEntry(le: LogbookEntry) {
        mle = le
        saveCurrentFlight()
        if (view == null) return
        setUpGalleryForFlight()
        toView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val idMenu: Int
        if (mle != null) {
            idMenu =
                if (mle!!.isExistingFlight()) R.menu.mfbexistingflightmenu else if (mle!!.isNewFlight()) R.menu.mfbnewflightmenu else if (mle is PendingFlight) R.menu.mfbpendingflightmenu else if (mle!!.isQueuedFlight()) R.menu.mfbqueuedflightmenu else R.menu.mfbqueuedflightmenu
            inflater.inflate(idMenu, menu)
        }
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater = requireActivity().menuInflater
        inflater.inflate(R.menu.imagemenu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Should never happen.
        if (mle == null) return false

        // Handle item selection
        val menuId = item.itemId
        when (menuId) {
            R.id.menuUploadLater -> submitFlight(true)
            R.id.menuResetFlight -> {
                if (mle!!.idLocalDB > 0) mle!!.deleteUnsubmittedFlightFromLocalDB()
                resetFlight(false)
            }
            R.id.menuSignFlight -> {
                try {
                    ActWebView.viewURL(
                        requireActivity(), String.format(
                            Locale.US, MFBConstants.urlSign,
                            MFBConstants.szIP,
                            mle!!.idFlight,
                            URLEncoder.encode(AuthToken.m_szAuthToken, "UTF-8"),
                            nightParam(context)
                        )
                    )
                } catch (ignored: UnsupportedEncodingException) {
                }
            }
            R.id.btnDeleteFlight -> AlertDialog.Builder(
                requireActivity(),
                R.style.MFBDialog
            )
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.lblConfirm)
                .setMessage(R.string.lblConfirmFlightDelete)
                .setPositiveButton(R.string.lblOK) { _: DialogInterface?, _: Int ->
                    if (mle!!.isAwaitingUpload()) {
                        mle!!.deleteUnsubmittedFlightFromLocalDB()
                        mle = null // clear this out since we're going to finish().
                        clearCachedFlights()
                        finish()
                    } else if (mle!!.isExistingFlight() || mle is PendingFlight)
                        deleteFlight(mle)
                }
                .setNegativeButton(R.string.lblCancel, null)
                .show()
            R.id.btnSubmitFlight, R.id.btnUpdateFlight -> submitFlight(
                false
            )
            R.id.btnSavePending -> {
                mle!!.fForcePending = true
                submitFlight(false)
            }
            R.id.menuTakePicture -> takePictureClicked()
            R.id.menuTakeVideo -> takeVideoClicked()
            R.id.menuChoosePicture -> choosePictureClicked()
            R.id.menuChooseTemplate -> {
                val i = Intent(requireActivity(), ViewTemplatesActivity::class.java)
                val b = Bundle()
                b.putSerializable(ActViewTemplates.ACTIVE_PROPERTYTEMPLATES, mActivetemplates)
                i.putExtras(b)
                mTemplateLauncher!!.launch(i)
            }
            R.id.menuRepeatFlight, R.id.menuReverseFlight -> {
                assert(mle != null)
                val leNew =
                    if (menuId == R.id.menuRepeatFlight) mle!!.clone() else mle!!.cloneAndReverse()
                leNew!!.idFlight = LogbookEntry.ID_QUEUED_FLIGHT_UNSUBMITTED
                leNew.toDB()
                rewritePropertiesForFlight(leNew.idLocalDB, leNew.rgCustomProperties)
                leNew.syncProperties()
                clearCachedFlights()
                AlertDialog.Builder(requireActivity(), R.style.MFBDialog)
                    .setMessage(getString(R.string.txtRepeatFlightComplete))
                    .setTitle(getString(R.string.txtSuccess))
                    .setNegativeButton("OK") { d: DialogInterface, _: Int ->
                        d.cancel()
                        finish()
                    }
                    .create().show()
                return true
            }
            R.id.menuSendFlight -> sendFlight()
            R.id.menuShareFlight -> shareFlight()
            R.id.btnAutoFill -> {
                assert(mle != null)
                fromView()
                autoFill(requireContext(), mle)
                toView()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun sendFlight() {
        if (mle == null || mle!!.sendLink.isEmpty()) {
            alert(this, getString(R.string.txtError), getString(R.string.errCantSend))
            return
        }
        IntentBuilder(requireActivity())
            .setType("message/rfc822")
            .setSubject(getString(R.string.sendFlightSubject))
            .setText(
                String.format(
                    Locale.getDefault(),
                    getString(R.string.sendFlightBody),
                    mle!!.sendLink
                )
            )
            .setChooserTitle(getString(R.string.menuSendFlight))
            .startChooser()
    }

    private fun shareFlight() {
        if (mle == null || mle!!.shareLink.isEmpty()) {
            alert(this, getString(R.string.txtError), getString(R.string.errCantShare))
            return
        }
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(
            Intent.EXTRA_SUBJECT,
            String.format(Locale.getDefault(), "%s %s", mle!!.szComments, mle!!.szRoute)
                .trim { it <= ' ' })
        intent.putExtra(Intent.EXTRA_TEXT, mle!!.shareLink)
        startActivity(Intent.createChooser(intent, getString(R.string.menuShareFlight)))
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val menuID = item.itemId
        return if (menuID == R.id.menuAddComment || menuID == R.id.menuDeleteImage || menuID == R.id.menuViewImage) onImageContextItemSelected(
            item,
            this
        ) else true
    }

    //region append to route
    // NOTE: I had been doing this with an AsyncTask, but it
    // wasn't thread safe with the database.  DB is pretty fast,
    // so we can just make sure we do all DB stuff on the main thread.
    private fun appendNearest() {
        assert(getMainLocation() != null)
        mAppendNearestLauncher!!.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun appendAdHoc() {
        assert(getMainLocation() != null)
        mAppendAdhocLauncher!!.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    //endregion
    private fun takePictureClicked() {
        saveCurrentFlight()
        takePicture()
    }

    private fun takeVideoClicked() {
        saveCurrentFlight()
        takeVideo()
    }

    private fun choosePictureClicked() {
        saveCurrentFlight()
        choosePicture()
    }

    override fun onClick(v: View) {
        fromView()
        when (val id = v.id) {
            R.id.btnEngineStartSet -> {
                if (!mle!!.isKnownEngineStart) {
                    mle!!.dtEngineStart = nowWith0Seconds()
                    engineStart()
                } else setDateTime(
                    id,
                    mle!!.dtEngineStart,
                    this,
                    DlgDatePicker.DatePickMode.UTCDATETIME
                )
            }
            R.id.btnEngineEndSet -> {
                if (!mle!!.isKnownEngineEnd) {
                    mle!!.dtEngineEnd = nowWith0Seconds()
                    engineStop()
                } else setDateTime(id, mle!!.dtEngineEnd, this, DlgDatePicker.DatePickMode.UTCDATETIME)
            }
            R.id.btnBlockOut -> {
                val dtBlockOut = mle!!.propDateForID(CustomPropertyType.idPropTypeBlockOut)
                if (dtBlockOut == null) {
                    mle!!.addOrSetPropertyDate(CustomPropertyType.idPropTypeBlockOut, nowWith0Seconds())
                    resetDateOfFlight()
                }
                else
                    setDateTime(id, dtBlockOut, this, DlgDatePicker.DatePickMode.UTCDATETIME)
            }
            R.id.btnBlockIn -> {
                val dtBlockIn = mle!!.propDateForID(CustomPropertyType.idPropTypeBlockIn)
                if (dtBlockIn == null)
                    mle!!.addOrSetPropertyDate(CustomPropertyType.idPropTypeBlockIn, nowWith0Seconds())
                else
                    setDateTime(id, dtBlockIn, this, DlgDatePicker.DatePickMode.UTCDATETIME)
            }
            R.id.btnFlightStartSet -> {
                if (!mle!!.isKnownFlightStart) {
                    mle!!.dtFlightStart = nowWith0Seconds()
                    flightStart()
                } else setDateTime(
                    id,
                    mle!!.dtFlightStart,
                    this,
                    DlgDatePicker.DatePickMode.UTCDATETIME
                )
            }
            R.id.btnFlightEndSet -> {
                if (!mle!!.isKnownFlightEnd) {
                    mle!!.dtFlightEnd = nowWith0Seconds()
                    flightStop()
                } else setDateTime(id, mle!!.dtFlightEnd, this, DlgDatePicker.DatePickMode.UTCDATETIME)
            }
            R.id.btnFlightSet -> {
                val dlg = DlgDatePicker(
                    requireActivity(),
                    DlgDatePicker.DatePickMode.LOCALDATEONLY,
                    mle!!.dtFlight
                )
                dlg.mDelegate = this
                dlg.mId = id
                dlg.show()
            }
            R.id.btnProps -> viewPropsForFlight()
            R.id.btnAppendNearest -> appendNearest()
            R.id.btnAddAircraft -> mAddAircraftLauncher!!.launch(
                Intent(
                    activity, NewAircraftActivity::class.java
                )
            )
            R.id.btnAddApproach -> {
                val i = Intent(requireActivity(), ActAddApproach::class.java)
                i.putExtra(ActAddApproach.AIRPORTSFORAPPROACHES, mle!!.szRoute)
                mApproachHelperLauncher!!.launch(i)
            }
            R.id.btnViewOnMap -> {
                val i = Intent(requireActivity(), ActFlightMap::class.java)
                i.putExtra(ActFlightMap.ROUTEFORFLIGHT, mle!!.szRoute)
                i.putExtra(
                    ActFlightMap.EXISTINGFLIGHTID,
                    if (mle!!.isExistingFlight()) mle!!.idFlight else 0
                )
                i.putExtra(
                    ActFlightMap.PENDINGFLIGHTID,
                    if (mle!!.isAwaitingUpload()) mle!!.idLocalDB else 0
                )
                i.putExtra(
                    ActFlightMap.NEWFLIGHTID,
                    if (mle!!.isNewFlight()) LogbookEntry.ID_NEW_FLIGHT else 0
                )
                i.putExtra(ActFlightMap.ALIASES, "")
                mMapRouteLauncher!!.launch(i)
            }
            R.id.btnPausePlay -> toggleFlightPause()
            R.id.txtViewInTheCockpit -> {
                val target = findViewById(R.id.sectInTheCockpit)
                val fExpandCockpit = target!!.visibility != View.VISIBLE
                if (mle != null && mle!!.isNewFlight()) {
                    val e = requireActivity().getPreferences(Context.MODE_PRIVATE).edit()
                    e.putBoolean(m_KeyShowInCockpit, fExpandCockpit)
                    e.apply()
                }
                setExpandedState((v as TextView), target, fExpandCockpit)
            }
            R.id.txtImageHeader -> {
                val target = findViewById(R.id.tblImageTable)
                setExpandedState((v as TextView), target!!, target.visibility != View.VISIBLE)
            }
            R.id.txtSignatureHeader -> {
                val target = findViewById(R.id.sectSignature)
                setExpandedState((v as TextView), target!!, target.visibility != View.VISIBLE)
            }
            R.id.txtPinnedPropertiesHeader -> {
                val target = findViewById(R.id.sectPinnedProperties)
                setExpandedState((v as TextView), target!!, target.visibility != View.VISIBLE)
            }
        }
        toView()
    }

    //region Image support
    override fun chooseImageCompleted(result: ActivityResult?) {
        addGalleryImage(result!!.data!!)
    }

    override fun takePictureCompleted(result: ActivityResult?) {
        addCameraImage(mTempfilepath, false)
    }

    override fun takeVideoCompleted(result: ActivityResult?) {
        addCameraImage(mTempfilepath, true)
    }

    //endregion
    private fun viewPropsForFlight() {
        val i = Intent(requireActivity(), ActViewProperties::class.java)
        i.putExtra(PROPSFORFLIGHTID, mle!!.idLocalDB)
        i.putExtra(PROPSFORFLIGHTEXISTINGID, mle!!.idFlight)
        i.putExtra(PROPSFORFLIGHTCROSSFILLVALUE, mle!!.decTotal)
        i.putExtra(
            TACHFORCROSSFILLVALUE, getHighWaterTachForAircraft(
                mle!!.idAircraft
            )
        )
        mPropertiesLauncher!!.launch(i)
    }

    private fun onHobbsChanged(v: View) {
        if (mle != null && MFBLocation.fPrefAutoFillTime === AutoFillOptions.HobbsTime) {
            val txtHobbsStart = findViewById(R.id.txtHobbsStart) as EditText?
            val txtHobbsEnd = findViewById(R.id.txtHobbsEnd) as EditText?
            val newHobbsStart = doubleFromField(R.id.txtHobbsStart)
            val newHobbsEnd = doubleFromField(R.id.txtHobbsEnd)
            if (v === txtHobbsStart && newHobbsStart != mle!!.hobbsStart ||
                v === txtHobbsEnd && newHobbsEnd != mle!!.hobbsEnd
            ) doAutoTotals()
        }
    }

    override fun updateDate(id: Int, dt: Date?) {
        var dt2 = dt
        fromView()
        var fEngineChanged = false
        var fFlightChanged = false
        var fBlockChanged = false
        dt2 = removeSeconds(dt2!!)
        when (id) {
            R.id.btnEngineStartSet -> {
                mle!!.dtEngineStart = dt2
                fEngineChanged = true
                resetDateOfFlight()
            }
            R.id.btnEngineEndSet -> {
                mle!!.dtEngineEnd = dt2
                fEngineChanged = true
                showRecordingIndicator()
            }
            R.id.btnBlockOut -> {
                handlePotentiallyDefaultedProperty(mle!!.addOrSetPropertyDate(CustomPropertyType.idPropTypeBlockOut, dt2))
                resetDateOfFlight()
                fBlockChanged = true
            }
            R.id.btnBlockIn -> {
                handlePotentiallyDefaultedProperty(mle!!.addOrSetPropertyDate(CustomPropertyType.idPropTypeBlockIn, dt2))
                fBlockChanged = true
            }
            R.id.btnFlightStartSet -> {
                mle!!.dtFlightStart = dt2
                resetDateOfFlight()
                fFlightChanged = true
            }
            R.id.btnFlightEndSet -> {
                mle!!.dtFlightEnd = dt2
                fFlightChanged = true
            }
            R.id.btnFlightSet -> {
                mle!!.dtFlight = dt2
            }
        }
        toView()
        when (MFBLocation.fPrefAutoFillHobbs) {
            AutoFillOptions.EngineTime -> if (fEngineChanged) doAutoHobbs()
            AutoFillOptions.FlightTime -> if (fFlightChanged) doAutoHobbs()
            else -> {}
        }
        when (MFBLocation.fPrefAutoFillTime) {
            AutoFillOptions.EngineTime -> if (fEngineChanged) doAutoTotals()
            AutoFillOptions.FlightTime -> if (fFlightChanged) doAutoTotals()
            AutoFillOptions.BlockTime -> if (fBlockChanged) doAutoTotals()
            AutoFillOptions.HobbsTime -> {}
            AutoFillOptions.FlightStartToEngineEnd -> doAutoTotals()
            else -> {}
        }
    }

    private fun validateAircraftID(id: Int): Int {
        var idAircraftToUse = -1
        if (mRgac != null) {
            for (ac in mRgac!!) if (ac.aircraftID == id) {
                idAircraftToUse = id
                break
            }
        }
        return idAircraftToUse
    }

    private fun resetFlight(fCarryHobbs: Boolean) {
        // start up a new flight with the same aircraft ID and public setting.
        // first, validate that the aircraft is still OK for the user
        val hobbsEnd = mle!!.hobbsEnd
        val leNew = LogbookEntry(validateAircraftID(mle!!.idAircraft), mle!!.fPublic)
        if (fCarryHobbs) leNew.hobbsStart = hobbsEnd
        mActivetemplates!!.clear()
        mle!!.idAircraft = leNew.idAircraft // so that updateTemplatesForAircraft works
        updateTemplatesForAircraft(false)
        setLogbookEntry(leNew)
        MFBMain.newFlightListener?.setInProgressFlight(leNew)
        saveCurrentFlight()

        // flush any pending flight data
        getMainLocation()!!.resetFlightData()

        // and flush any pause/play data
        fPaused = false
        dtPauseTime = 0
        dtTimeOfLastPause = 0
        accumulatedNight = 0.0
    }

    private fun submitFlight(forceQueued: Boolean) {
        fromView()
        val a: Activity = requireActivity()
        if (a.currentFocus != null) a.currentFocus!!.clearFocus() // force any in-progress edit to commit, particularly for properties.
        val fIsNew = mle!!.isNewFlight() // hold onto this because we can change the status.
        if (fIsNew) {
            getMainLocation()?.isRecording = false
            showRecordingIndicator()
        }

        // load any pending properties from the DB into the logbookentry object itself.
        mle!!.syncProperties()

        // load the telemetry string, if it's a first submission.
        if (mle!!.isNewFlight()) mle!!.szFlightData = getMainLocation()!!.flightDataString

        // Save for later if offline or if forceQueued
        val fIsOnline = isOnline(context)
        if (forceQueued || !fIsOnline) {

            // save the flight with id of -2 if it's a new flight
            if (fIsNew) mle!!.idFlight =
                if (forceQueued) LogbookEntry.ID_QUEUED_FLIGHT_UNSUBMITTED else LogbookEntry.ID_UNSUBMITTED_FLIGHT

            // Existing flights can't be saved for later.  No good reason for that except work.
            if (mle!!.isExistingFlight()) {
                AlertDialog.Builder(requireActivity(), R.style.MFBDialog)
                    .setMessage(getString(R.string.errNoInternetNoSave))
                    .setTitle(getString(R.string.txtError))
                    .setNegativeButton(getString(R.string.lblOK)) { d: DialogInterface, _: Int ->
                        d.cancel()
                        finish()
                    }
                    .create().show()
                return
            }

            // now save it - but check for success
            if (!mle!!.toDB()) {
                // Failure!
                // Try saving it without the flight data string, in case that was the issue
                mle!!.szFlightData = ""
                if (!mle!!.toDB()) {
                    // still didn't work. give an error message and, if necessary,
                    // restore to being a new flight.
                    alert(this, getString(R.string.txtError), mle!!.szError)
                    // restore the previous idFlight if we were saving a new flight.
                    if (fIsNew) {
                        mle!!.idFlight = LogbookEntry.ID_NEW_FLIGHT
                        saveCurrentFlight()
                    }
                    return
                }
                // if we're here, then phew - saved without the string
            }

            // if we're here, save was successful (even if flight data was dropped)
            clearCachedFlights()
            MFBMain.invalidateCachedTotals()
            if (fIsNew) {
                resetFlight(true)
                alert(this, getString(R.string.txtSuccess), getString(R.string.txtFlightQueued))
            } else {
                AlertDialog.Builder(requireActivity(), R.style.MFBDialog)
                    .setMessage(getString(R.string.txtFlightQueued))
                    .setTitle(getString(R.string.txtSuccess))
                    .setNegativeButton("OK") { d: DialogInterface, _: Int ->
                        d.cancel()
                        finish()
                    }
                    .create().show()
            }
        } else {
            val le = mle
            submitFlight(le)
        }
    }

    private fun setVisibilityForRow(rowID: Int, visible : Boolean) {
        val v = findViewById(rowID)
        v!!.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun toView() {
        if (view == null) return
        setStringForField(R.id.txtComments, mle!!.szComments)
        setStringForField(R.id.txtRoute, mle!!.szRoute)
        setIntForField(R.id.txtApproaches, mle!!.cApproaches)
        setIntForField(R.id.txtLandings, mle!!.cLandings)
        setIntForField(R.id.txtFSNightLandings, mle!!.cNightLandings)
        setIntForField(R.id.txtDayLandings, mle!!.cFullStopLandings)
        setCheckState(R.id.ckMyFlightbook, mle!!.fPublic)
        setCheckState(R.id.ckHold, mle!!.fHold)
        setLocalDateForField(R.id.btnFlightSet, mle!!.dtFlight)

        // Engine/Flight dates
        val tachStart = mle!!.propDoubleForID(CustomPropertyType.idPropTypeTachStart)
        val tachEnd = mle!!.propDoubleForID(CustomPropertyType.idPropTypeTachEnd)
        val blockOut = mle!!.propDateForID(CustomPropertyType.idPropTypeBlockOut)
        val blockIn = mle!!.propDateForID(CustomPropertyType.idPropTypeBlockIn)

        setUTCDateForField(R.id.btnEngineStartSet, mle!!.dtEngineStart)
        setUTCDateForField(R.id.btnEngineEndSet, mle!!.dtEngineEnd)
        setUTCDateForField(R.id.btnFlightStartSet, mle!!.dtFlightStart)
        setUTCDateForField(R.id.btnFlightEndSet, mle!!.dtFlightEnd)
        setDoubleForField(R.id.txtHobbsStart, mle!!.hobbsStart)
        setDoubleForField(R.id.txtHobbsEnd, mle!!.hobbsEnd)
        setDoubleForField(R.id.txtTachStart, tachStart)
        setDoubleForField(R.id.txtTachEnd, tachEnd)
        setUTCDateForField(R.id.btnBlockOut, blockOut)
        setUTCDateForField(R.id.btnBlockIn, blockIn)

        // show tach/block in "In the cockpit" only if the options are selected (will otherwise show in properties)...
        val showTach = fShowTach
        val showBlock = fShowBlock
        //...but hobbs/engine/flight must be shown if they have values, since they won't show anywhere else.
        val showHobbs = fShowHobbs || mle!!.hobbsStart > 0 || mle!!.hobbsEnd > 0
        val showEngine = fShowEngine || mle!!.isKnownEngineStart || mle!!.isKnownEngineEnd
        val showFlight = fShowFlight || mle!!.isKnownFlightStart || mle!!.isKnownFlightEnd
        setVisibilityForRow(R.id.rowTachStart, showTach)
        setVisibilityForRow(R.id.rowTachEnd, showTach)
        setVisibilityForRow(R.id.rowHobbsStart, showHobbs)
        setVisibilityForRow(R.id.rowHobbsEnd, showHobbs)
        setVisibilityForRow(R.id.rowEngineStart, showEngine)
        setVisibilityForRow(R.id.rowEngineEnd, showEngine)
        setVisibilityForRow(R.id.rowBlockOut, showBlock)
        setVisibilityForRow(R.id.rowBlockIn, showBlock)
        setVisibilityForRow(R.id.rowFlightStart, showFlight)
        setVisibilityForRow(R.id.rowFlightEnd, showFlight)

        setDoubleForField(R.id.txtCFI, mle!!.decCFI)
        setDoubleForField(R.id.txtDual, mle!!.decDual)
        setDoubleForField(R.id.txtGround, mle!!.decGrndSim)
        setDoubleForField(R.id.txtIMC, mle!!.decIMC)
        setDoubleForField(R.id.txtNight, mle!!.decNight)
        setDoubleForField(R.id.txtPIC, mle!!.decPIC)
        setDoubleForField(R.id.txtSIC, mle!!.decSIC)
        setDoubleForField(R.id.txtSimIMC, mle!!.decSimulatedIFR)
        setDoubleForField(R.id.txtTotal, mle!!.decTotal)
        setDoubleForField(R.id.txtXC, mle!!.decXC)
        val fIsSigned = mle!!.signatureStatus !== SigStatus.None
        findViewById(R.id.sectSignature)!!.visibility = if (fIsSigned) View.VISIBLE else View.GONE
        findViewById(R.id.txtSignatureHeader)!!.visibility =
            if (fIsSigned) View.VISIBLE else View.GONE
        if (fIsSigned) {
            val imgSigStatus = findViewById(R.id.imgSigState) as ImageView?
            when (mle!!.signatureStatus) {
                SigStatus.None -> {}
                SigStatus.Valid -> {
                    imgSigStatus!!.setImageResource(R.drawable.sigok)
                    imgSigStatus.contentDescription = getString(R.string.cdIsSignedValid)
                    (findViewById(R.id.txtSigState) as TextView?)!!.text =
                        getString(R.string.cdIsSignedValid)
                }
                SigStatus.Invalid -> {
                    imgSigStatus!!.setImageResource(R.drawable.siginvalid)
                    imgSigStatus.contentDescription = getString(R.string.cdIsSignedInvalid)
                    (findViewById(R.id.txtSigState) as TextView?)!!.text =
                        getString(R.string.cdIsSignedInvalid)
                }
            }
            val szSigInfo1 = if (isNullDate(mle!!.signatureDate)) "" else String.format(
                Locale.getDefault(),
                getString(R.string.lblSignatureTemplate1),
                DateFormat.getDateFormat(requireActivity()).format(mle!!.signatureDate!!),
                mle!!.signatureCFIName
            )
            val szSigInfo2 = if (isNullDate(mle!!.signatureCFIExpiration)) String.format(
                Locale.getDefault(),
                getString(R.string.lblSignatureTemplate2NoExp),
                mle!!.signatureCFICert
            ) else String.format(
                Locale.getDefault(),
                getString(R.string.lblSignatureTemplate2),
                mle!!.signatureCFICert,
                DateFormat.getDateFormat(requireActivity()).format(
                    mle!!.signatureCFIExpiration!!
                )
            )
            (findViewById(R.id.txtSigInfo1) as TextView?)!!.text = szSigInfo1
            (findViewById(R.id.txtSigInfo2) as TextView?)!!.text = szSigInfo2
            (findViewById(R.id.txtSigComment) as TextView?)!!.text = mle!!.signatureComments
            val ivDigitizedSig = findViewById(R.id.imgDigitizedSig) as ImageView?
            if (mle!!.signatureHasDigitizedSig) {
                if (ivDigitizedSig!!.drawable == null) {
                    val szURL = String.format(
                        Locale.US,
                        "https://%s/Logbook/Public/ViewSig.aspx?id=%d",
                        MFBConstants.szIP,
                        mle!!.idFlight
                    )
                    fetchDigitizedSig(szURL, ivDigitizedSig)
                }
            } else ivDigitizedSig!!.visibility = View.GONE
        }

        // Aircraft spinner
        val rgSelectibleAircraft = selectibleAircraft()
        if (rgSelectibleAircraft != null) {
            val sp = findViewById(R.id.spnAircraft) as Spinner?

            // Pick the first selectible aircraft, if no aircraft is selected
            if (mle!!.idAircraft == -1 && rgSelectibleAircraft.isNotEmpty()) mle!!.idAircraft =
                rgSelectibleAircraft[0].aircraftID

            // Issue #188 set the spinner, but ONLY if it's not currently set to the correct tail.
            val ac = sp!!.selectedItem as Aircraft?
            if (ac == null || ac.aircraftID != mle!!.idAircraft) {
                for (i in rgSelectibleAircraft.indices) {
                    if (mle!!.idAircraft == rgSelectibleAircraft[i].aircraftID) {
                        sp.setSelection(i)
                        sp.prompt = "Current Aircraft: " + rgSelectibleAircraft[i].tailNumber
                        break
                    }
                }
            }
        }

        // Current properties
        if (!mle!!.isExistingFlight() && mle!!.rgCustomProperties.isEmpty()) mle!!.syncProperties()
        setUpPropertiesForFlight()
        updateElapsedTime()
        updatePausePlayButtonState()
    }

    override fun fromView() {
        if (view == null || mle == null) return

        // Integer fields
        mle!!.cApproaches = intFromField(R.id.txtApproaches)
        mle!!.cFullStopLandings = intFromField(R.id.txtDayLandings)
        mle!!.cLandings = intFromField(R.id.txtLandings)
        mle!!.cNightLandings = intFromField(R.id.txtFSNightLandings)

        // Double fields
        mle!!.decCFI = doubleFromField(R.id.txtCFI)
        mle!!.decDual = doubleFromField(R.id.txtDual)
        mle!!.decGrndSim = doubleFromField(R.id.txtGround)
        mle!!.decIMC = doubleFromField(R.id.txtIMC)
        mle!!.decNight = doubleFromField(R.id.txtNight)
        mle!!.decPIC = doubleFromField(R.id.txtPIC)
        mle!!.decSIC = doubleFromField(R.id.txtSIC)
        mle!!.decSimulatedIFR = doubleFromField(R.id.txtSimIMC)
        mle!!.decTotal = doubleFromField(R.id.txtTotal)
        mle!!.decXC = doubleFromField(R.id.txtXC)
        mle!!.hobbsStart = doubleFromField(R.id.txtHobbsStart)
        mle!!.hobbsEnd = doubleFromField(R.id.txtHobbsEnd)

        // Date - no-op because it should be in sync
        // Flight/Engine times - ditto

        // But tach, if shown, isn't coming from props, so load it here.
        if (fShowTach) {
            handlePotentiallyDefaultedProperty(mle!!.addOrSetPropertyDouble(CustomPropertyType.idPropTypeTachStart, doubleFromField(R.id.txtTachStart)))
            handlePotentiallyDefaultedProperty(mle!!.addOrSetPropertyDouble(CustomPropertyType.idPropTypeTachEnd, doubleFromField(R.id.txtTachEnd)))
        }

        // checkboxes
        mle!!.fHold = checkState(R.id.ckHold)
        mle!!.fPublic = checkState(R.id.ckMyFlightbook)

        // And strings
        mle!!.szComments = stringFromField(R.id.txtComments)
        mle!!.szRoute = stringFromField(R.id.txtRoute)

        // Aircraft spinner
        mle!!.idAircraft = selectedAircraftID()
    }

    private fun selectedAircraftID(): Int {
        val rgSelectibleAircraft = selectibleAircraft()
        if (rgSelectibleAircraft != null && rgSelectibleAircraft.isNotEmpty()) {
            val sp = findViewById(R.id.spnAircraft) as Spinner?
            val ac = sp!!.selectedItem as Aircraft
            return ac.aircraftID
        }
        return -1
    }

    private fun doAutoTotals() {
        fromView()
        val sp = findViewById(R.id.spnAircraft) as Spinner?
        if (mle!!.autoFillTotal(
                if (mle!!.idAircraft > 0 && sp!!.selectedItem != null) sp.selectedItem as Aircraft else null,
                totalTimePaused()
            ) > 0
        ) toView()
    }

    private fun doAutoHobbs() {
        fromView()
        if (mle!!.autoFillHobbs(totalTimePaused()) > 0) {
            toView() // sync the view to the change we just made - especially since autototals can read it.

            // if total is linked to hobbs, need to do autotime too
            if (MFBLocation.fPrefAutoFillTime === AutoFillOptions.HobbsTime) doAutoTotals()
        }
    }

    private fun resetDateOfFlight() {
        if (mle != null && mle!!.isNewFlight()) {
            // set the date of the flight to now in local time.
            var dt = Date()
            if (mle!!.isKnownEngineStart && mle!!.dtEngineStart < dt) dt =
                mle!!.dtEngineStart
            if (mle!!.isKnownFlightStart && mle!!.dtFlightStart < dt) dt =
                mle!!.dtFlightStart

            val dtBlockOut = mle!!.propDateForID(CustomPropertyType.idPropTypeBlockOut)
            if (dtBlockOut != null && !isNullDate(dtBlockOut) && dtBlockOut < dt)
                dt = dtBlockOut
            mle!!.dtFlight = dt
            setLocalDateForField(R.id.btnFlightSet, mle!!.dtFlight)
        }
    }

    private fun engineStart() {
        // don't do any GPS stuff unless this is a new flight
        if (!mle!!.isNewFlight()) return
        resetDateOfFlight()
        if (MFBLocation.fPrefAutoDetect) appendNearest()
        getMainLocation()?.isRecording = true // will respect preference
        showRecordingIndicator()
        if (MFBConstants.fFakeGPS) {
            getMainLocation()!!.stopListening(requireContext())
            getMainLocation()?.isRecording = true // will respect preference
            val gpss = GPSSim(getMainLocation()!!)
            gpss.feedEvents()
        }
    }

    private fun engineStop() {
        // don't do any GPS stuff unless this is a new flight
        if (!mle!!.isNewFlight()) return
        if (MFBLocation.fPrefAutoDetect) appendNearest()
        getMainLocation()?.isRecording = false
        doAutoHobbs()
        doAutoTotals()
        showRecordingIndicator()
        unPauseFlight()
    }

    private fun flightStart() {
        // don't do any GPS stuff unless this is a new flight
        if (!mle!!.isNewFlight()) return
        if (!mle!!.isKnownEngineStart) resetDateOfFlight()
        getMainLocation()?.isRecording = true
        if (MFBLocation.fPrefAutoDetect) appendNearest()
        unPauseFlight() // don't pause in flight
    }

    private fun flightStop() {
        // don't do any GPS stuff unless this is a new flight
        if (!mle!!.isNewFlight()) return
        if (MFBLocation.fPrefAutoDetect) appendNearest()
    }

    private fun showRecordingIndicator() {
        imgRecording!!.visibility =
            if (getMainLocation() != null && getMainLocation()?.isRecording!!) View.VISIBLE else View.INVISIBLE
    }

    private var dfSunriseSunset: SimpleDateFormat? = null
    private fun displaySpeed(s: Double): String {
        val res = resources
        return if (s < 1) res.getString(R.string.strEmpty) else when (ActOptions.speedUnits) {
            SpeedUnits.Knots -> String.format(
                Locale.getDefault(),
                res.getString(R.string.lblSpeedFormatKts),
                s * MFBConstants.MPS_TO_KNOTS
            )
            SpeedUnits.KmPerHour -> String.format(
                Locale.getDefault(),
                res.getString(R.string.lblSpeedFormatKph),
                s * MFBConstants.MPS_TO_KPH
            )
            SpeedUnits.MilesPerHour -> String.format(
                Locale.getDefault(),
                res.getString(R.string.lblSpeedFormatMph),
                s * MFBConstants.MPS_TO_MPH
            )
        }
    }

    private fun displayAlt(a: Double): String {
        val res = resources
        return when (ActOptions.altitudeUnits) {
            AltitudeUnits.Feet -> String.format(
                Locale.getDefault(),
                res.getString(R.string.lblAltFormatFt),
                (a * MFBConstants.METERS_TO_FEET).roundToInt()
            )
            AltitudeUnits.Meters -> String.format(
                Locale.getDefault(), res.getString(R.string.lblAltFormatMeters), a.roundToInt()
            )
        }
    }

    override fun updateStatus(
        quality: GPSQuality, fAirborne: Boolean?, loc: Location?,
        fRecording: Boolean?
    ) {
        if (!isAdded || isDetached) {
            return
        } else {
            requireActivity()
        }
        showRecordingIndicator()
        val res = resources
        val idSzQuality: Int = when (quality) {
            GPSQuality.Excellent -> R.string.lblGPSExcellent
            GPSQuality.Good -> R.string.lblGPSGood
            GPSQuality.Poor -> R.string.lblGPSPoor
            else -> R.string.lblGPSUnknown
        }
        if (loc != null) {
            txtQuality!!.text = res.getString(idSzQuality)
            txtSpeed!!.text = displaySpeed(loc.speed.toDouble())
            txtAltitude!!.text = displayAlt(loc.altitude)
            val lat = loc.latitude
            val lon = loc.longitude
            txtLatitude!!.text = String.format(
                Locale.getDefault(),
                "%.5f°%s",
                abs(lat),
                if (lat > 0) "N" else "S"
            )
            txtLongitude!!.text = String.format(
                Locale.getDefault(),
                "%.5f°%s",
                abs(lon),
                if (lon > 0) "E" else "W"
            )
            val sst = SunriseSunsetTimes(Date(loc.time), lat, lon)
            if (dfSunriseSunset == null) dfSunriseSunset =
                SimpleDateFormat("hh:mm a z", Locale.getDefault())
            txtSunrise!!.text = dfSunriseSunset!!.format(sst.sunrise)
            txtSunset!!.text = dfSunriseSunset!!.format(sst.sunset)
        }

        // don't show in-air/on-ground if we aren't actually detecting these
        if (MFBLocation.fPrefAutoDetect) txtStatus!!.text =
            res.getString(if (fAirborne!!) R.string.lblFlightInAir else R.string.lblFlightOnGround) else txtStatus!!.text =
            res.getString(R.string.lblGPSUnknown)
        if (fAirborne!!) unPauseFlight()
    }

    // Pause/play functionality
    private fun timeSinceLastPaused(): Long {
        return if (fPaused) Date().time - dtTimeOfLastPause else 0
    }

    private fun totalTimePaused(): Long {
        return dtPauseTime + timeSinceLastPaused()
    }

    private fun pauseFlight() {
        dtTimeOfLastPause = Date().time
        fPaused = true
    }

    override fun unPauseFlight() {
        if (fPaused) {
            dtPauseTime += timeSinceLastPaused()
            fPaused = false // do this AFTER calling [self timeSinceLastPaused]
        }
    }

    override fun isPaused(): Boolean {
        return fPaused
    }

    override fun togglePausePlay() {
        if (fPaused) unPauseFlight() else pauseFlight()
        updatePausePlayButtonState()
    }

    override fun startEngine() {
        if (mle != null && !mle!!.isKnownEngineStart) {
            mle!!.dtEngineStart = nowWith0Seconds()
            engineStart()
            toView()
        }
    }

    override fun stopEngine() {
        if (mle != null && !mle!!.isKnownEngineEnd) {
            mle!!.dtEngineEnd = nowWith0Seconds()
            engineStop()
            toView()
        }
    }

    private fun updateElapsedTime() {            // update the button state
        val ib = findViewById(R.id.btnPausePlay) as ImageButton?
        // pause/play should only be visible on ground with engine running (or flight start known but engine end unknown)
        if (mle == null)    // should never happen!
            return
        val fShowPausePlay =
            !MFBLocation.IsFlying && (mle!!.isKnownEngineStart || mle!!.isKnownFlightStart) && !mle!!.isKnownEngineEnd
        ib!!.visibility = if (fShowPausePlay) View.VISIBLE else View.INVISIBLE
        val txtElapsed = findViewById(R.id.txtElapsedTime) as TextView?
        if (txtElapsed != null) {
            var dtTotal: Long
            var dtFlight: Long = 0
            var dtEngine: Long = 0
            val fIsKnownFlightStart = mle!!.isKnownFlightStart
            val fIsKnownEngineStart = mle!!.isKnownEngineStart
            if (fIsKnownFlightStart) {
                dtFlight = if (!mle!!.isKnownFlightEnd) // in flight
                    Date().time - mle!!.dtFlightStart.time else mle!!.dtFlightEnd.time - mle!!.dtFlightStart.time
            }
            if (fIsKnownEngineStart) {
                dtEngine =
                    if (!mle!!.isKnownEngineEnd) Date().time - mle!!.dtEngineStart.time else mle!!.dtEngineEnd.time - mle!!.dtEngineStart.time
            }

            // if totals mode is FLIGHT TIME, then elapsed time is based on flight time if/when it is known.
            // OTHERWISE, we use engine time (if known) or else flight time.
            dtTotal =
                if (MFBLocation.fPrefAutoFillTime === AutoFillOptions.FlightTime) if (fIsKnownFlightStart) dtFlight else 0 else if (fIsKnownEngineStart) dtEngine else dtFlight
            dtTotal -= totalTimePaused()
            if (dtTotal <= 0) dtTotal = 0 // should never happen

            // dtTotal is in milliseconds - convert it to seconds for ease of reading
            dtTotal = (dtTotal / 1000.0).toLong()
            val sTime = String.format(
                Locale.US,
                "%02d:%02d:%02d",
                (dtTotal / 3600).toInt(),
                dtTotal.toInt() % 3600 / 60,
                dtTotal.toInt() % 60
            )
            txtElapsed.text = sTime
        }
        showRecordingIndicator()
    }

    private fun updatePausePlayButtonState() {
        // update the button state
        val ib = findViewById(R.id.btnPausePlay) as ImageButton?
        ib!!.setImageResource(if (fPaused) R.drawable.play else R.drawable.pause)
    }

    private fun toggleFlightPause() {
        // don't pause or play if we're not flying/engine started
        if (mle!!.isKnownFlightStart || mle!!.isKnownEngineStart) {
            if (fPaused) unPauseFlight() else pauseFlight()
        } else {
            unPauseFlight()
            dtPauseTime = 0
        }
        updateElapsedTime()
        updatePausePlayButtonState()
    }

    /*
     * (non-Javadoc)
     * @see com.myflightbook.android.ActMFBForm.GallerySource#getGalleryID()
     * GallerySource protocol
     */
    override fun getGalleryID(): Int {
        return R.id.tblImageTable
    }

    override fun getGalleryHeader(): View {
        return findViewById(R.id.txtImageHeader)!!
    }

    override fun getImages(): Array<MFBImageInfo> {
        return if (mle == null || mle!!.rgFlightImages == null) arrayOf() else mle!!.rgFlightImages!!
    }

    override fun setImages(rgmfbii: Array<MFBImageInfo>?) {
        if (mle == null) {
            throw NullPointerException("m_le is null in setImages")
        }
        mle!!.rgFlightImages = rgmfbii
    }

    override fun newImage(mfbii: MFBImageInfo?) {
        Log.w(
            MFBConstants.LOG_TAG,
            String.format("newImage called. m_le is %s", if (mle == null) "null" else "not null")
        )
        mle!!.addImageForflight(mfbii!!)
    }

    override fun refreshGallery() {
        setUpGalleryForFlight()
    }

    override fun getPictureDestination(): PictureDestination {
        return PictureDestination.FlightImage
    }

    override fun invalidate() {
        resetFlight(false)
    }

    private fun updateIfChanged(id: Int, value: Int) {
        if (intFromField(id) != value) setIntForField(id, value)
    }

    // Update the fields which could possibly have changed via auto-detect
    override fun refreshDetectedFields() {
        setUTCDateForField(R.id.btnFlightStartSet, mle!!.dtFlightStart)
        setUTCDateForField(R.id.btnFlightEndSet, mle!!.dtFlightEnd)
        updateIfChanged(R.id.txtLandings, mle!!.cLandings)
        updateIfChanged(R.id.txtFSNightLandings, mle!!.cNightLandings)
        updateIfChanged(R.id.txtDayLandings, mle!!.cFullStopLandings)
        setDoubleForField(R.id.txtNight, mle!!.decNight)
        setStringForField(R.id.txtRoute, mle!!.szRoute)
    }

    private fun setUpPropertiesForFlight() {
        val a: Activity = requireActivity()
        val l = a.layoutInflater
        val tl = findViewById(R.id.tblPinnedProperties) as TableLayout? ?: return
        tl.removeAllViews()
        if (mle == null) return

        // Handle block Out having been specified by viewprops
        var fHadBlockOut = false
        for (fp in mle!!.rgCustomProperties) if (fp.idPropType == CustomPropertyType.idPropTypeBlockOut) {
            fHadBlockOut = true
            break
        }
        mle!!.syncProperties()
        val pinnedProps = getPinnedProperties(
            requireActivity().getSharedPreferences(
                CustomPropertyType.prefSharedPinnedProps,
                Activity.MODE_PRIVATE
            )
        )
        val templateProps = mergeTemplates(
            mActivetemplates!!.toTypedArray()
        )
        val rgcptAll = cachedPropertyTypes
        Arrays.sort(rgcptAll)
        val rgProps = crossProduct(mle!!.rgCustomProperties, rgcptAll)
        var fHasBlockOutAdded = false
        for (fp in rgProps) {
            // should never happen, but does - not sure why
            if (fp.getCustomPropertyType() == null) fp.refreshPropType()
            if (fp.idPropType == CustomPropertyType.idPropTypeBlockOut && !fp.isDefaultValue()) fHasBlockOutAdded =
                true

            // Don't show any properties that are:
            // a) unpinned and default value, or
            // b) "hoisted" into the "in the cockpit" section
            val fIsPinned = isPinnedProperty(pinnedProps, fp.idPropType)
            if (!fIsPinned && !templateProps.contains(fp.idPropType) && fp.isDefaultValue()) continue

            if (fShowTach && (fp.idPropType == CustomPropertyType.idPropTypeTachStart || fp.idPropType == CustomPropertyType.idPropTypeTachEnd))
                continue
            if (fShowBlock && (fp.idPropType == CustomPropertyType.idPropTypeBlockOut || fp.idPropType == CustomPropertyType.idPropTypeBlockIn))
                continue

            val tr = l.inflate(R.layout.cpttableitem, tl, false) as TableRow
            tr.id = View.generateViewId()
            val pe: PropertyEdit = tr.findViewById(R.id.propEdit)
            val onCrossFill = object : CrossFillDelegate {
                override fun crossFillRequested(sender: DecimalEdit?) {
                    val d = getHighWaterTachForAircraft(selectedAircraftID())
                    if (d > 0 && sender != null) sender.doubleValue = d
                }
            }
            pe.initForProperty(
                fp,
                tr.id,
                this,
                if (fp.getCustomPropertyType()!!.idPropType == CustomPropertyType.idPropTypeTachStart) onCrossFill else this)
            tr.findViewById<View>(R.id.imgFavorite).visibility =
                if (fIsPinned) View.VISIBLE else View.INVISIBLE
            tl.addView(
                tr,
                TableLayout.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                )
            )
        }
        if (!fHadBlockOut && fHasBlockOutAdded) resetDateOfFlight()
    }

    //region in-line property editing
    private fun deleteProperty(fp : FlightProperty) {
        lifecycleScope.launch {
            doAsync<FlightPropertiesSvc, Any?>(
                requireActivity(),
                FlightPropertiesSvc(),
                getString(R.string.prgDeleteProp),
                { s -> s.deletePropertyForFlight(AuthToken.m_szAuthToken, fp.idFlight, fp.idProp,requireContext()) },
                { _, _ ->
                    setUpPropertiesForFlight()
                }
            )
        }
    }

    private fun deleteDefaultedProperty(fp: FlightProperty) {
        if (fp.idProp > 0 && fp.isDefaultValue()) {
            deleteProperty(fp)
            return
        }

        // Otherwise, save it
        val rgProps = crossProduct(mle!!.rgCustomProperties, cachedPropertyTypes)
        val rgfpUpdated = distillList(rgProps)
        rewritePropertiesForFlight(mle!!.idLocalDB, rgfpUpdated)
    }

    private fun handlePotentiallyDefaultedProperty(fp : FlightProperty?) {
        if (mle!!.idFlight > 0 && fp != null && fp.idProp > 0 && fp.isDefaultValue()) {
            fp.idFlight = mle!!.idFlight   // this is pointing to the LOCAL db ID of the flight, so we need to point it instead to the server-based flight id2
            deleteProperty(fp)
        }
    }

    override fun updateProperty(id: Int, fp: FlightProperty) {
        if (mle == null) {
            // this can get called by PropertyEdit as it loses focus.
            return
        }
        val rgProps = crossProduct(mle!!.rgCustomProperties, cachedPropertyTypes)
        for (i in rgProps.indices) {
            if (rgProps[i].idPropType == fp.idPropType) {
                rgProps[i] = fp
                deleteDefaultedProperty(fp)
                rewritePropertiesForFlight(mle!!.idLocalDB, distillList(rgProps))
                mle!!.syncProperties()
                break
            }
        }
        if (MFBLocation.fPrefAutoFillTime === AutoFillOptions.BlockTime &&
            (fp.idPropType == CustomPropertyType.idPropTypeBlockOut || fp.idPropType == CustomPropertyType.idPropTypeBlockIn)
        ) doAutoTotals()
    }

    override fun dateOfFlightShouldReset(dt: Date) {
        resetDateOfFlight()
    }

    //endregion
    // region Templates
    private fun updateTemplatesForAircraft(noDefault: Boolean) {
        val a = activity
            ?: return // this can be called via invalidate, which can be called before everything is set up, so check for null
        if (PropertyTemplate.sharedTemplates == null && getSharedTemplates(
                a.getSharedPreferences(
                    PropertyTemplate.PREF_KEY_TEMPLATES,
                    Activity.MODE_PRIVATE
                )
            ) == null
        ) return
        val rgDefault = defaultTemplates
        val ptAnon = anonTemplate
        val ptSim = simTemplate
        val ac = getAircraftById(mle!!.idAircraft, mRgac)
        if (ac != null && ac.defaultTemplates.size > 0) mActivetemplates!!.addAll(
            listOf(
                *templatesWithIDs(
                    ac.defaultTemplates
                )
            )
        ) else if (rgDefault.isNotEmpty() && !noDefault) mActivetemplates!!.addAll(
            listOf(*rgDefault)
        )
        mActivetemplates!!.remove(ptAnon)
        mActivetemplates!!.remove(ptSim)

        // Always add in sims or anon as appropriate
        if (ac != null) {
            if (ac.isAnonymous() && ptAnon != null) mActivetemplates!!.add(ptAnon)
            if (!ac.isReal() && ptSim != null) mActivetemplates!!.add(ptSim)
        }
    } // endregion

    companion object {
        const val PROPSFORFLIGHTID = "com.myflightbook.android.FlightPropsID"
        const val PROPSFORFLIGHTEXISTINGID = "com.myflightbook.android.FlightPropsIDExisting"
        const val PROPSFORFLIGHTCROSSFILLVALUE = "com.myflightbook.android.FlightPropsXFill"
        const val TACHFORCROSSFILLVALUE = "com.myflightbook.android.TachStartXFill"

        // current state of pause/play and accumulated night
        var fPaused = false
        private var dtPauseTime: Long = 0
        private var dtTimeOfLastPause: Long = 0
        var accumulatedNight = 0.0

        // pause/play state
        private const val m_KeysIsPaused = "flightIsPaused"
        private const val m_KeysPausedTime = "totalPauseTime"
        private const val m_KeysTimeOfLastPause = "timeOfLastPause"
        private const val m_KeysAccumulatedNight = "accumulatedNight"

        // Expand state of "in the cockpit"
        private const val m_KeyShowInCockpit = "inTheCockpit"

        // Display options
        var fShowTach = false
        var fShowHobbs = true
        var fShowEngine = true
        var fShowBlock = false
        var fShowFlight = true

        const val prefKeyShowTach = "cockpitShowTach"
        const val prefKeyShowHobbs = "cockpitShowHobbs"
        const val prefKeyShowEngine = "cockpitShowEngine"
        const val prefKeyShowBlock = "cockpitShowBlock"
        const val prefKeyShowFlight = "cockpitShowFlight"
    }
}