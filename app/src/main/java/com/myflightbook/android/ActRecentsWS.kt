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
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.ListFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.myflightbook.android.MFBMain.Invalidatable
import com.myflightbook.android.webservices.*
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import com.myflightbook.android.webservices.RecentFlightsSvc.Companion.clearCachedFlights
import com.myflightbook.android.webservices.RecentFlightsSvc.Companion.hasCachedFlights
import com.myflightbook.android.webservices.UTCDate.formatDate
import com.myflightbook.android.webservices.UTCDate.isNullDate
import kotlinx.coroutines.launch
import model.*
import model.Aircraft.Companion.getAircraftById
import model.DecimalEdit.Companion.doubleToHHMM
import model.DecimalEdit.Companion.stringForMode
import model.DecimalEdit.EditMode
import model.LogbookEntry.Companion.mergeFlightLists
import model.LogbookEntry.Companion.queuedAndUnsubmittedFlights
import model.LogbookEntry.Companion.unsubmittedFlights
import model.LogbookEntry.SigStatus
import model.MFBImageInfo.ImageCacheCompleted
import model.MFBUtil.alert
import model.MFBUtil.putCacheForKey
import java.util.*
import java.util.regex.Pattern

class ActRecentsWS : ListFragment(), AdapterView.OnItemSelectedListener, ImageCacheCompleted,
    Invalidatable {
    private var mRgle: Array<LogbookEntry>? = arrayOf()
    private val mRgexistingflights = ArrayList<LogbookEntry>()
    private var fCouldBeMore = true
    private var mQueryLauncher: ActivityResultLauncher<Intent>? = null

    enum class FlightDetail {
        Low, Medium, High
    }

    private var currentQuery: FlightQuery? = FlightQuery()

    private inner class FlightAdapter(
        c: Context?,
        rgpp: Array<LogbookEntry>?
    ) : ArrayAdapter<LogbookEntry>(
        c!!, R.layout.flightitem, rgpp!!
    ) {
        private fun formattedTimeForLabel(idLabel: Int, `val`: Double, em: EditMode): String {
            return if (`val` == 0.0) "" else String.format(
                Locale.getDefault(),
                "%s: <b>%s</b> ",
                getString(idLabel),
                stringForMode(`val`, em)
            )
        }

        private fun formattedTimeForLabel(idLabel: Int, `val`: Int): String {
            return if (`val` == 0) "" else String.format(
                Locale.getDefault(),
                "%s: <b>%d</b> ",
                getString(idLabel),
                `val`
            )
        }

        private fun formattedTimeForLabel(idLabel: Int, dtStart: Date?, dtEnd: Date?): String {
            if (dtStart == null || dtEnd == null || isNullDate(dtStart) || isNullDate(dtEnd)) return ""
            val fLocal = DlgDatePicker.fUseLocalTime
            val elapsedHours = (dtEnd.time - dtStart.time) / (1000.0 * 3600.0)
            val szElapsed = if (elapsedHours > 0) String.format(
                Locale.getDefault(),
                " (%s)",
                if (DecimalEdit.DefaultHHMM) doubleToHHMM(elapsedHours) else String.format(
                    Locale.getDefault(), "%.1f", elapsedHours
                )
            ) else ""
            return String.format(
                Locale.getDefault(),
                "%s: <b>%s - %s%s</b> ",
                getString(idLabel),
                formatDate(fLocal, dtStart, this.context),
                formatDate(fLocal, dtEnd, this.context),
                szElapsed
            )
        }

        private fun formattedTimeForLabel(idLabel : Int, start : Double, end : Double) : String {
            if (start <= 0 || end < start) return ""
            return String.format(Locale.getDefault(),
                "%s: <b>%s - %s (%s)</b> ", getString(idLabel), stringForMode(start, EditMode.DECIMAL), stringForMode(end, EditMode.DECIMAL), stringForMode(end - start, EditMode.DECIMAL))
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var v = convertView
            if (v == null) {
                val vi =
                    (requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                v = vi.inflate(R.layout.flightitem, parent, false)
            }

            // Trigger the next batch of flights
            if (fCouldBeMore && position + 1 >= mRgle!!.size) refreshRecentFlights(false)
            val le = getItem(position)!!

            val fIsAwaitingUpload = le.isAwaitingUpload()
            val fIsPendingFlight = le is PendingFlight
            val txtError = v!!.findViewById<TextView>(R.id.txtError)
            val ivCamera = v.findViewById<ImageView>(R.id.imgCamera)
            val ivSigState = v.findViewById<ImageView>(R.id.imgSigState)
            if (m_rgac == null) m_rgac = AircraftSvc().cachedAircraft
            val ac = getAircraftById(le.idAircraft, m_rgac)
            val em = if (DecimalEdit.DefaultHHMM) EditMode.HHMM else EditMode.DECIMAL
            if (fShowFlightImages) {
                ivCamera.visibility = View.VISIBLE
                if (le.hasImages() || ac != null && ac.hasImage()) {
                    val mfbii = if (le.hasImages()) le.rgFlightImages!![0] else ac!!.defaultImage!!
                    val b = mfbii.bitmapFromThumb()
                    if (b != null) {
                        ivCamera.setImageBitmap(b)
                    }
                } else ivCamera.setImageResource(R.drawable.noimage)
            } else ivCamera.visibility = View.GONE
            when (le.signatureStatus) {
                SigStatus.None -> ivSigState.visibility = View.GONE
                SigStatus.Valid -> {
                    ivSigState.visibility = View.VISIBLE
                    ivSigState.setImageResource(R.drawable.sigok)
                    ivSigState.contentDescription = getString(R.string.cdIsSignedValid)
                }
                SigStatus.Invalid -> {
                    ivSigState.visibility = View.VISIBLE
                    ivSigState.setImageResource(R.drawable.siginvalid)
                    ivSigState.contentDescription = getString(R.string.cdIsSignedInvalid)
                }
            }
            txtError.text = le.szError
            txtError.visibility =
                if (le.szError.isNotEmpty()) View.VISIBLE else View.GONE
            val szTailNumber = if (ac == null) le.szTailNumDisplay else {
                val modelDesc = String.format(Locale.getDefault(), "(%s)", ac.modelDescription)
                if (ac.isAnonymous() && modelDesc.compareTo(le.szTailNumDisplay) == 0) "" else le.szTailNumDisplay
            }

            val txtHeader = v.findViewById<TextView>(R.id.txtFlightHeader)
            val flightNum = le.propertyWithID(CustomPropertyType.idPropFlightNum)
            val szHeaderHTML = String.format(
                Locale.getDefault(),
                "<strong><big>%s %s %s %s</big></strong>%s <i><strong><font color='gray'>%s</font></strong></i>",
                TextUtils.htmlEncode(
                    DateFormat.getDateFormat(this.context).format(
                        MFBUtil.localDateToLocalDateTime(le.dtFlight)
                    )
                ),
                if (flightNum == null) "" else String.format(Locale.getDefault(), " <strong>%s</strong>", flightNum.stringValue),
                when {
                    fIsAwaitingUpload -> getString(R.string.txtAwaitingUpload)
                    fIsPendingFlight -> getString(
                        R.string.txtPendingAddition
                    )
                    else -> ""
                },
                TextUtils.htmlEncode(szTailNumber.trim { it <= ' ' }),
                TextUtils.htmlEncode(
                    if (ac == null) "" else String.format(
                        Locale.getDefault(),
                        " (%s)",
                        ac.modelDescription
                    )
                ),
                TextUtils.htmlEncode(le.szRoute.trim { it <= ' ' })
            )
            txtHeader.text = HtmlCompat.fromHtml(szHeaderHTML, HtmlCompat.FROM_HTML_MODE_LEGACY)
            val pBold = Pattern.compile("(\\*)([^*_\\r\\n]*)(\\*)", Pattern.CASE_INSENSITIVE)
            val pItalic = Pattern.compile("(_)([^*_\\r\\n]*)_", Pattern.CASE_INSENSITIVE)
            var szComments =
                pBold.matcher(TextUtils.htmlEncode(le.szComments)).replaceAll("<strong>$2</strong>")
            szComments = pItalic.matcher(szComments).replaceAll("<em>$2</em>")
            val txtComments = v.findViewById<TextView>(R.id.txtComments)
            txtComments.visibility = if (szComments.isEmpty()) View.GONE else View.VISIBLE
            txtComments.text = HtmlCompat.fromHtml(szComments, HtmlCompat.FROM_HTML_MODE_LEGACY)
            val txtFlightTimes = v.findViewById<TextView>(R.id.txtFlightTimes)
            val sb = StringBuilder()
            if (flightDetail != FlightDetail.Low) {
                sb.append(formattedTimeForLabel(R.string.lblTotal, le.decTotal, em))
                sb.append(formattedTimeForLabel(R.string.lblLandingsAlt, le.cLandings))
                sb.append(formattedTimeForLabel(R.string.lblApproaches, le.cApproaches))
                sb.append(formattedTimeForLabel(R.string.lblNight, le.decNight, em))
                sb.append(formattedTimeForLabel(R.string.lblSimIMC, le.decSimulatedIFR, em))
                sb.append(formattedTimeForLabel(R.string.lblIMC, le.decIMC, em))
                sb.append(formattedTimeForLabel(R.string.lblXC, le.decXC, em))
                sb.append(formattedTimeForLabel(R.string.lblDual, le.decDual, em))
                sb.append(formattedTimeForLabel(R.string.lblGround, le.decGrndSim, em))
                sb.append(formattedTimeForLabel(R.string.lblCFI, le.decCFI, em))
                sb.append(formattedTimeForLabel(R.string.lblSIC, le.decSIC, em))
                sb.append(formattedTimeForLabel(R.string.lblPIC, le.decPIC, em))
                if (flightDetail == FlightDetail.High) {
                    val szHobbs =
                        formattedTimeForLabel(R.string.lblElapsedHobbs, le.hobbsStart, le.hobbsEnd)
                    if (szHobbs.isNotEmpty())
                        sb.append(szHobbs)

                    val handledProps = hashSetOf(CustomPropertyType.idPropFlightNum)

                    val szTach = formattedTimeForLabel(
                        R.string.lblElapsedTach,
                        le.propertyWithID(CustomPropertyType.idPropTypeTachStart)?.decValue ?: 0.0,
                        le.propertyWithID(CustomPropertyType.idPropTypeTachEnd)?.decValue ?: 0.0
                    )
                    if (szTach.isNotEmpty()) {
                        sb.append(szTach)
                        handledProps.add(CustomPropertyType.idPropTypeTachStart)
                        handledProps.add(CustomPropertyType.idPropTypeTachEnd)
                    }

                    val szBlock = formattedTimeForLabel(
                        R.string.autoBlock,
                        le.propertyWithID(CustomPropertyType.idPropTypeBlockOut)?.dateValue,
                        le.propertyWithID(CustomPropertyType.idPropTypeBlockIn)?.dateValue
                    )
                    if (szBlock.isNotEmpty()) {
                        sb.append(szBlock)
                        handledProps.add(CustomPropertyType.idPropTypeBlockOut)
                        handledProps.add(CustomPropertyType.idPropTypeBlockIn)
                    }

                    sb.append(
                        formattedTimeForLabel(
                            R.string.autoEngine,
                            le.dtEngineStart,
                            le.dtEngineEnd
                        )
                    )
                    sb.append(
                        formattedTimeForLabel(
                            R.string.autoFlight,
                            le.dtFlightStart,
                            le.dtFlightEnd
                        )
                    )

                    for (fp in le.rgCustomProperties) {
                        if (handledProps.contains(fp.idPropType))
                            continue
                        sb.append(fp.format(DlgDatePicker.fUseLocalTime, true, this.context) + " ")
                    }
                }
            }
            txtFlightTimes.visibility = if (sb.isEmpty()) View.GONE else View.VISIBLE
            txtFlightTimes.text =
                HtmlCompat.fromHtml(sb.toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)

            // show queued flights different from others.
            val tf = Typeface.DEFAULT
            val tfNew =
                if (fIsAwaitingUpload || fIsPendingFlight) Typeface.ITALIC else Typeface.NORMAL
            txtComments.setTypeface(tf, tfNew)
            txtHeader.setTypeface(tf, tfNew)
            txtFlightTimes.setTypeface(tf, tfNew)

            val isColored = ((le.mFlightColorHex ?: "").isNotEmpty())

            val backColor = if (isColored)
                Color.parseColor("#" + le.mFlightColorHex)
            else ContextCompat.getColor(
                context,
                if (fIsAwaitingUpload || fIsPendingFlight) R.color.pendingBackground else R.color.colorBackground
            )

            // Issue #315 - flight coloring needs to use daytime colors for those flights
            val textForeColor = ContextCompat.getColor(context, if (isColored) R.color.textColorPrimaryColoredFlights else R.color.textColorPrimary)
            txtComments.setTextColor(textForeColor)
            txtFlightTimes.setTextColor(textForeColor)
            txtHeader.setTextColor((textForeColor))

            v.setBackgroundColor(backColor)
            ivCamera.setBackgroundColor(backColor)
            ivSigState.setBackgroundColor(backColor)
            txtError.setBackgroundColor(backColor)
            txtComments.setBackgroundColor(backColor)
            txtFlightTimes.setBackgroundColor(backColor)
            txtHeader.setBackgroundColor(backColor)
            return v
        }
    }

    private fun submitQueuedFlights(rgle: Array<LogbookEntry>?) {
        if (rgle.isNullOrEmpty())
            return

        val act = requireActivity()
        var mFflightsposted = false
        var mFerrorsfound = false

        lifecycleScope.launch {
            ActMFBForm.doAsync<MFBSoap, Boolean?>(
                act,
                MFBSoap(),
                "",  // actual status will be filled in below, but don't pass null because we want to show status
                {
                    s ->
                    val cFlights = rgle.size
                    var iFlight = 1
                    val szFmtUploadProgress = requireContext().getString(R.string.prgSavingFlights)
                    for (le in rgle) {
                        val szStatus = String.format(szFmtUploadProgress, iFlight, cFlights)
                        s.mProgress?.notifyProgress(iFlight * 100 / cFlights, szStatus)
                        iFlight++
                        le.syncProperties() // pull in the properties for the flight.
                        if (submitFlight(le)) {
                            mFflightsposted = true
                            le.deleteUnsubmittedFlightFromLocalDB()
                        } else {
                            le.idFlight =
                                LogbookEntry.ID_QUEUED_FLIGHT_UNSUBMITTED // don't auto-resubmit until the error is fixed.
                            le.toDB() // save the error so that we will display it on refresh.
                            mFerrorsfound = true
                        }
                    }
                    mFflightsposted || mFerrorsfound
                },
                { _, _ ->

                    if (mFflightsposted) {  // flight was added/updated, so invalidate stuff.
                        MFBMain.invalidateCachedTotals()
                        refreshRecentFlights(true)
                    } else if (mFerrorsfound) {
                        mRgle = mergeFlightLists(
                            mergeFlightLists(
                                queuedAndUnsubmittedFlights,
                                @Suppress("UNCHECKED_CAST")
                                if (currentQuery!!.hasCriteria()) null else cachedPendingFlights as Array<LogbookEntry>
                            ), mRgexistingflights.toTypedArray()
                        )
                        populateList()
                    }
                }
            )
        }
    }

    private fun submitFlight(le: LogbookEntry?): Boolean {
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
        val pf = if (le is PendingFlight) le else null
        return if (le!!.fForcePending) {
            check(!le.isExistingFlight()) { "Attempt to save an existing flight as a pending flight" }
            if (pf == null || pf.getPendingID().isEmpty()) {
                val pfs = PendingFlightSvc()
                pfs.createPendingFlight(AuthToken.m_szAuthToken, le, requireContext())
                    .also { cachedPendingFlights = it }
                true
            } else {
                // existing pending flight but still force pending - call updatependingflight
                val pfs = PendingFlightSvc()
                pfs.updatePendingFlight(AuthToken.m_szAuthToken, pf, requireContext())
                    .also { cachedPendingFlights = it }
                true
            }
        } else {
            // Not force pending.
            // If regular flight (new or existing), or pending without a pendingID
            if (pf == null || pf.getPendingID().isEmpty()) {
                val cf = CommitFlightSvc()
                cf.fCommitFlightForUser(AuthToken.m_szAuthToken, le, requireContext())
            } else {
                // By definition, here pf is non-null and it has a pending ID so it is a valid pending flight and we are not forcing - call commitpendingflight
                val pfs = PendingFlightSvc()
                val rgpf =
                    pfs.commitPendingFlight(AuthToken.m_szAuthToken, pf, requireContext())
                cachedPendingFlights = rgpf
                pf.szError = pfs.lastError
                pf.szError.isEmpty() // we want to show any error
            }
        }
    }

    private fun refreshFlights(fClearCache : Boolean = false) {
        val act = requireActivity()
        lifecycleScope.launch {
            ActMFBForm.doAsync<RecentFlightsSvc, Array<LogbookEntry>?>(
                act,
                RecentFlightsSvc(),
                null,
                { s->
                    val rgleQueuedAndUnsubmitted = queuedAndUnsubmittedFlights
                    if (fClearCache) clearCachedFlights()
                    val cFlightsPageSize = 15
                    val rgle = s.getRecentFlightsWithQueryAndOffset(
                        AuthToken.m_szAuthToken,
                        currentQuery,
                        mRgexistingflights.size,
                        cFlightsPageSize,
                        act
                    )

                    // Refresh pending flights, if it's null
                    if (cachedPendingFlights == null || cachedPendingFlights!!.isEmpty())
                        cachedPendingFlights = PendingFlightSvc().getPendingFlightsForUser(AuthToken.m_szAuthToken, act)
                    fCouldBeMore = rgle.size >= cFlightsPageSize
                    mRgexistingflights.addAll(rgle)
                    mergeFlightLists(
                        mergeFlightLists(
                            rgleQueuedAndUnsubmitted,
                            @Suppress("UNCHECKED_CAST")
                            if (currentQuery!!.hasCriteria()) null else cachedPendingFlights as Array<LogbookEntry>
                        ), mRgexistingflights.toTypedArray()
                    )
                },
                { _, result ->
                    mRgle = result
                    m_fIsRefreshing = false
                    populateList()

                    // Turn off the swipe refresh layout here because, unlike most other refreshable screens,
                    // this one doesn't show a progress indicator (because of continuous scroll)
                    // So if this was swipe-to-refresh, then we leave the indicator up until the operation is complete.
                    val v = view
                    if (v != null) {
                        val srl: SwipeRefreshLayout = v.findViewById(R.id.swiperefresh)
                        srl.isRefreshing = false
                        val tv = requireView().findViewById<TextView>(R.id.txtFlightQueryStatus)
                        tv.text =
                            getString(if (currentQuery != null && currentQuery!!.hasCriteria()) R.string.fqStatusNotAllflights else R.string.fqStatusAllFlights)
                    }
                }
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.recentsws, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()

        // Add menu items without using the Fragment Menu APIs
        // Note how we can tie the MenuProvider to the viewLifecycleOwner
        // and an optional Lifecycle.State (here, RESUMED) to indicate when
        // the menu should be visible
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.recentswsmenu, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                val id = item.itemId
                if (id == R.id.refreshRecents) refreshRecentFlights(true) else if (id == R.id.findFlights) {
                    if (isOnline(context)) {
                        val i = Intent(activity, FlightQueryActivity::class.java)
                        i.putExtra(ActFlightQuery.QUERY_TO_EDIT, currentQuery)
                        mQueryLauncher!!.launch(i)
                    } else alert(context, getString(R.string.txtError), getString(R.string.errNoInternet))
                } else return false
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        MFBMain.registerNotifyDataChange(this)
        MFBMain.registerNotifyResetAll(this)
        val i = requireActivity().intent
        if (i != null) {
            val o: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                i.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT, FlightQuery::class.java)
            else
                @Suppress("DEPRECATION")
                i.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT)
            if (o != null) currentQuery = o as FlightQuery?
        }
        fCouldBeMore = true
        mQueryLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                currentQuery =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        result.data!!.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT, FlightQuery::class.java)
                    else
                        @Suppress("DEPRECATION")
                        result.data!!.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT) as FlightQuery?
            }
        }
        val srl: SwipeRefreshLayout = requireView().findViewById(R.id.swiperefresh)
        srl.setOnRefreshListener { refreshRecentFlights(true) }
    }

    override fun onDestroy() {
        MFBMain.unregisterNotify(this)
        super.onDestroy()
    }

    fun refreshRecentFlights(fClearAll: Boolean) {
        if (!isValid()) {
            alert(
                this,
                getString(R.string.txtError),
                getString(R.string.errMustBeSignedInToViewRecentFlights)
            )
            fCouldBeMore = false
        }
        if (fClearAll) {
            mRgexistingflights.clear()
            cachedPendingFlights = null // force fetch of these on first batch
            fCouldBeMore = true
            this.listView.setSelectionFromTop(0, 0)
        }
        if (isOnline(context)) {
            if (!m_fIsRefreshing) {
                m_fIsRefreshing = true // don't refresh if already in progress.
                Log.d(MFBConstants.LOG_TAG, "ActRecentsWS - Refreshing From Server")
                refreshFlights(fClearAll)
            }
        } else {
            mRgle = queuedAndUnsubmittedFlights
            val p = PackAndGo(requireContext())
            val dtLastPack = p.lastFlightsPackDate()
            if (dtLastPack != null) {
                mRgle = mergeFlightLists(mRgle, p.cachedFlights())
                alert(
                    context,
                    getString(R.string.packAndGoOffline),
                    String.format(
                        Locale.getDefault(),
                        getString(R.string.packAndGoUsingCached),
                        java.text.DateFormat.getDateInstance().format(dtLastPack)
                    )
                )
            } else alert(context, getString(R.string.txtError), getString(R.string.errNoInternet))
            fCouldBeMore = false
            populateList()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasCachedFlights()) refreshRecentFlights(true) else populateList()
    }

    private fun populateList() {
        if (view == null) return
        var index = this.listView.firstVisiblePosition
        val v = this.listView.getChildAt(0)
        var top = v?.top ?: 0
        if (index >= mRgle!!.size) {
            top = 0
            index = 0
        }
        val fa = FlightAdapter(activity, mRgle)
        listAdapter = fa
        listView.setSelectionFromTop(index, top)
        listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                // Entry could be larger than can be passed directly in intent, so put it
                // into the MFBUtil cache and retrieve it on the other end
                val key = UUID.randomUUID().toString()
                putCacheForKey(key, mRgle!![position])
                val i = Intent(activity, EditFlightActivity::class.java)
                i.putExtra(VIEWEXISTINGFLIGHT, key)
                startActivity(i)
            }
        @Suppress("UNCHECKED_CAST")
        if (fShowFlightImages)
            LazyThumbnailLoader(
                mRgle as Array<LazyThumbnailLoader.ThumbnailedItem>,
                listAdapter as FlightAdapter,
                lifecycleScope
            ).start()
        val rgUnsubmitted = unsubmittedFlights
        if (rgUnsubmitted.isNotEmpty() && isOnline(context))
            submitQueuedFlights(rgUnsubmitted)
    }

    override fun imgCompleted(sender: MFBImageInfo?) {
        (Objects.requireNonNull(this.listAdapter) as FlightAdapter).notifyDataSetChanged()
    }

    override fun onItemSelected(
        arg0: AdapterView<*>?, arg1: View, arg2: Int,
        arg3: Long
    ) {
    }

    override fun onNothingSelected(arg0: AdapterView<*>?) {}
    override fun invalidate() {
        m_rgac = null
        mRgexistingflights.clear()
        fCouldBeMore = true
        val fa = this.listAdapter as FlightAdapter?
        fa?.notifyDataSetChanged()
    }

    companion object {
        var cachedPendingFlights: Array<PendingFlight>? = null
        const val VIEWEXISTINGFLIGHT = "com.myflightbook.android.ViewFlight"
        private var m_fIsRefreshing = false
        var m_rgac: Array<Aircraft>? = null
        @JvmField
        var fShowFlightImages = true
        @JvmField
        var flightDetail = FlightDetail.Low
    }
}