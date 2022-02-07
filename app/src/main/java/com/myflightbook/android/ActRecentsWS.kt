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

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.AsyncTask
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
import androidx.fragment.app.ListFragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.myflightbook.android.MFBMain.Invalidatable
import com.myflightbook.android.webservices.*
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import com.myflightbook.android.webservices.MFBSoap.MFBSoapProgressUpdate
import com.myflightbook.android.webservices.RecentFlightsSvc.Companion.clearCachedFlights
import com.myflightbook.android.webservices.RecentFlightsSvc.Companion.hasCachedFlights
import com.myflightbook.android.webservices.UTCDate.formatDate
import com.myflightbook.android.webservices.UTCDate.isNullDate
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
import model.MFBUtil.showProgress
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
                "<b>%s:</b> %s ",
                getString(idLabel),
                stringForMode(`val`, em)
            )
        }

        private fun formattedTimeForLabel(idLabel: Int, `val`: Int): String {
            return if (`val` == 0) "" else String.format(
                Locale.getDefault(),
                "<b>%s:</b> %d ",
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
                "<b>%s: </b> %s - %s%s ",
                getString(idLabel),
                formatDate(fLocal, dtStart, this.context),
                formatDate(fLocal, dtEnd, this.context),
                szElapsed
            )
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
            val szTailNumber =
                (if (le.szTailNumDisplay.isEmpty() && ac != null) ac.displayTailNumber() else le.szTailNumDisplay)
            val txtHeader = v.findViewById<TextView>(R.id.txtFlightHeader)
            val szHeaderHTML = String.format(
                Locale.getDefault(),
                "<strong><big>%s %s %s</big></strong>%s <i><strong><font color='gray'>%s</font></strong></i>",
                TextUtils.htmlEncode(
                    DateFormat.getDateFormat(this.context).format(
                        le.dtFlight
                    )
                ),
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
                    val blockOut = le.propertyWithID(CustomPropertyType.idPropTypeBlockOut)
                    val blockIn = le.propertyWithID(CustomPropertyType.idPropTypeBlockIn)
                    if (blockOut != null && blockIn != null) sb.append(
                        formattedTimeForLabel(
                            R.string.autoBlock,
                            blockOut.dateValue,
                            blockIn.dateValue
                        )
                    )
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
            val backColor = ContextCompat.getColor(
                context,
                if (fIsAwaitingUpload || fIsPendingFlight) R.color.pendingBackground else R.color.colorBackground
            )
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

    private open class SubmitQueuedFlightsTask(
        c: Context?,
        arws: ActRecentsWS,
        rgle: Array<LogbookEntry>?
    ) : AsyncTask<Void?, String?, Boolean>(), MFBSoapProgressUpdate {
        private var mPd: ProgressDialog? = null
        private val mCtxt: AsyncWeakContext<ActRecentsWS> = AsyncWeakContext(c, arws)
        private val mRgle: Array<LogbookEntry>? = rgle
        var mFerrorsfound = false
        var mFflightsposted = false
        protected fun submitFlight(le: LogbookEntry?): Boolean {
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
                    pfs.createPendingFlight(AuthToken.m_szAuthToken, le, mCtxt.context)
                        .also { cachedPendingFlights = it }
                    true
                } else {
                    // existing pending flight but still force pending - call updatependingflight
                    val pfs = PendingFlightSvc()
                    pfs.updatePendingFlight(AuthToken.m_szAuthToken, pf, mCtxt.context)
                        .also { cachedPendingFlights = it }
                    true
                }
            } else {
                // Not force pending.
                // If regular flight (new or existing), or pending without a pendingID
                if (pf == null || pf.getPendingID().isEmpty()) {
                    val cf = CommitFlightSvc()
                    cf.fCommitFlightForUser(AuthToken.m_szAuthToken, le, mCtxt.context!!)
                } else {
                    // By definition, here pf is non-null and it has a pending ID so it is a valid pending flight and we are not forcing - call commitpendingflight
                    val pfs = PendingFlightSvc()
                    pfs.mProgress = this
                    val rgpf =
                        pfs.commitPendingFlight(AuthToken.m_szAuthToken, pf, mCtxt.context!!)
                    cachedPendingFlights = rgpf
                    pf.szError = pfs.lastError
                    pf.szError.isEmpty() // we want to show any error
                }
            }
        }

        override fun doInBackground(vararg params: Void?): Boolean {
            if (mRgle == null || mRgle.isEmpty()) return false
            mFflightsposted = false
            mFerrorsfound = mFflightsposted
            val cFlights = mRgle.size
            var iFlight = 1
            val c = mCtxt.context!!
            val szFmtUploadProgress = c.getString(R.string.prgSavingFlights)
            for (le in mRgle) {
                val szStatus = String.format(szFmtUploadProgress, iFlight, cFlights)
                if (mPd != null) notifyProgress(iFlight * 100 / cFlights, szStatus)
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
            return mFflightsposted || mFerrorsfound
        }

        override fun onPreExecute() {
            val c = mCtxt.context!!
            mPd = showProgress(c, c.getString(R.string.prgSavingFlight))
        }

        override fun onPostExecute(fResult: Boolean) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            if (!fResult) // nothing changed - nothing to do.
                return
            val arws = mCtxt.callingActivity
            if (arws == null || !arws.isAdded || arws.isDetached || arws.activity == null) return
            if (mFflightsposted) {  // flight was added/updated, so invalidate stuff.
                MFBMain.invalidateCachedTotals()
                arws.refreshRecentFlights(true)
            } else if (mFerrorsfound) {
                arws.mRgle = mergeFlightLists(
                    mergeFlightLists(
                        queuedAndUnsubmittedFlights,
                        if (arws.currentQuery!!.HasCriteria()) null else cachedPendingFlights as Array<LogbookEntry>
                    ), arws.mRgexistingflights.toTypedArray()
                )
                arws.populateList()
            }
        }

        override fun onProgressUpdate(vararg msg: String?) {
            mPd!!.setMessage(msg[0])
        }

        override fun notifyProgress(percentageComplete: Int, szMsg: String?) {
            publishProgress(szMsg)
        }

    }

    private class RefreshFlightsTask(c: Context?, arws: ActRecentsWS) :
        AsyncTask<Void?, Void?, Boolean>() {
        private var mRfsvc: RecentFlightsSvc? = null
        var fClearCache = false
        private val mCtxt: AsyncWeakContext<ActRecentsWS> = AsyncWeakContext(c, arws)
        override fun doInBackground(vararg params: Void?): Boolean {
            val c = mCtxt.context
            val arws = mCtxt.callingActivity
            if (c == null || arws == null) // can't do anything without a context
                return false
            mRfsvc = RecentFlightsSvc()
            val rgleQueuedAndUnsubmitted = queuedAndUnsubmittedFlights
            if (fClearCache) clearCachedFlights()
            val cFlightsPageSize = 15
            val rgle = mRfsvc!!.getRecentFlightsWithQueryAndOffset(
                AuthToken.m_szAuthToken,
                arws.currentQuery,
                arws.mRgexistingflights.size,
                cFlightsPageSize,
                c
            )

            // Refresh pending flights, if it's null
            if (cachedPendingFlights == null || cachedPendingFlights!!.isEmpty())
                cachedPendingFlights = PendingFlightSvc().getPendingFlightsForUser(AuthToken.m_szAuthToken, c)
            arws.fCouldBeMore = rgle.size >= cFlightsPageSize
            arws.mRgexistingflights.addAll(rgle)
            arws.mRgle = mergeFlightLists(
                mergeFlightLists(
                    rgleQueuedAndUnsubmitted,
                    if (arws.currentQuery!!.HasCriteria()) null else cachedPendingFlights as Array<LogbookEntry>
                ), arws.mRgexistingflights.toTypedArray()
            )
            return true
        }

        override fun onPreExecute() {}
        override fun onPostExecute(b: Boolean) {
            m_fIsRefreshing = false
            val c = mCtxt.context
            val arws = mCtxt.callingActivity
            if (c == null || arws == null || !arws.isAdded || arws.isDetached || arws.activity == null) // can't do anything without a context
                return
            arws.populateList()

            // Turn off the swipe refresh layout here because, unlike most other refreshable screens,
            // this one doesn't show a progress indicator (because of continuous scroll)
            // So if this was swipe-to-refresh, then we leave the indicator up until the operation is complete.
            val v = arws.view
            if (v != null) {
                val srl: SwipeRefreshLayout = v.findViewById(R.id.swiperefresh)
                srl.isRefreshing = false
            }
            if (arws.view != null) {
                val tv = arws.requireView().findViewById<TextView>(R.id.txtFlightQueryStatus)
                tv.text =
                    c.getString(if (arws.currentQuery != null && arws.currentQuery!!.HasCriteria()) R.string.fqStatusNotAllflights else R.string.fqStatusAllFlights)
            }
            if (!b && mRfsvc != null) {
                alert(arws, c.getString(R.string.txtError), mRfsvc!!.lastError)
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.recentsws, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MFBMain.registerNotifyDataChange(this)
        MFBMain.registerNotifyResetAll(this)
        val i = requireActivity().intent
        if (i != null) {
            val o: Any? = i.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT)
            if (o != null) currentQuery = o as FlightQuery?
        }
        fCouldBeMore = true
        mQueryLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                currentQuery = result.data!!.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT) as FlightQuery?
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
                val rft = RefreshFlightsTask(context, this)
                rft.fClearCache = fClearAll
                rft.execute()
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
            index = top
        }
        val fa = FlightAdapter(activity, mRgle)
        listAdapter = fa
        listView.setSelectionFromTop(index, top)
        listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                if (position >= 0 || position < mRgle!!.size) {
                    // Entry could be larger than can be passed directly in intent, so put it
                    // into the MFBUtil cache and retrieve it on the other end
                    val key = UUID.randomUUID().toString()
                    putCacheForKey(key, mRgle!![position])
                    val i = Intent(activity, EditFlightActivity::class.java)
                    i.putExtra(VIEWEXISTINGFLIGHT, key)
                    startActivity(i)
                }
            }
        if (fShowFlightImages) Thread(
            LazyThumbnailLoader(
                mRgle as Array<LazyThumbnailLoader.ThumbnailedItem>,
                (this.listAdapter as FlightAdapter?)!!
            )
        ).start()
        val rgUnsubmitted = unsubmittedFlights
        if (rgUnsubmitted.isNotEmpty() && isOnline(context)) {
            val spft = SubmitQueuedFlightsTask(context, this, rgUnsubmitted)
            spft.execute()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.recentswsmenu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        val id = item.itemId
        if (id == R.id.refreshRecents) refreshRecentFlights(true) else if (id == R.id.findFlights) {
            if (isOnline(context)) {
                val i = Intent(activity, FlightQueryActivity::class.java)
                i.putExtra(ActFlightQuery.QUERY_TO_EDIT, currentQuery)
                mQueryLauncher!!.launch(i)
            } else alert(context, getString(R.string.txtError), getString(R.string.errNoInternet))
        } else return super.onOptionsItemSelected(item)
        return true
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