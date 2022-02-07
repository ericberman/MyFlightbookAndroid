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
package com.myflightbook.android

import com.myflightbook.android.webservices.RecentFlightsSvc.Companion.clearCachedFlights
import com.myflightbook.android.webservices.CustomPropertyTypesSvc.Companion.searchableProperties
import android.os.Bundle
import model.Airport
import android.content.Intent
import android.app.Activity
import android.app.AlertDialog
import model.Aircraft
import android.os.AsyncTask
import com.myflightbook.android.webservices.MFBSoap
import android.app.ProgressDialog
import android.content.Context
import com.myflightbook.android.webservices.AircraftSvc
import com.myflightbook.android.webservices.AuthToken
import model.MFBUtil
import model.MFBConstants
import com.myflightbook.android.DlgDatePicker.DateTimeUpdate
import model.FlightQuery
import android.content.DialogInterface
import model.MakeModel
import com.myflightbook.android.webservices.CannedQuerySvc
import model.CannedQuery
import com.myflightbook.android.webservices.CustomPropertyTypesSvc
import model.DBCache
import model.FlightQuery.DateRanges
import model.CategoryClass
import model.CustomPropertyType
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.widget.*
import model.FlightQuery.GroupConjunction
import model.FlightQuery.EngineTypeRestriction
import model.FlightQuery.AircraftInstanceRestriction
import model.FlightQuery.FlightDistance
import java.lang.Exception
import java.util.*

class ActFlightQuery : ActMFBForm(), View.OnClickListener, DateTimeUpdate {
    private var currentQuery: FlightQuery? = null
    private var mRgac: Array<Aircraft>? = null
    private var mRgacall: Array<Aircraft>? = null
    private var mRgmm: Array<MakeModel>? = null
    private var fShowAllAircraft = false
    private var fCannedQueryClicked = false

    private class GetCannedQueryTask(c: Context?, afq: ActFlightQuery) :
        AsyncTask<Void?, Void?, MFBSoap?>() {
        private val mAfq: AsyncWeakContext<ActFlightQuery> = AsyncWeakContext(c, afq)
        override fun doInBackground(vararg params: Void?): MFBSoap {
            val cqSVC = CannedQuerySvc()
            CannedQuery.cannedQueries = cqSVC.getNamedQueriesForUser(
                AuthToken.m_szAuthToken,
                mAfq.context
            )
            return cqSVC
        }

        override fun onPreExecute() {}
        override fun onPostExecute(svc: MFBSoap?) {
            val afq = mAfq.callingActivity
            if (svc != null && svc.lastError.isEmpty() && afq != null) afq.setUpNamedQueries()
        }

    }

    private class AddCannedQueryTask(
        c: Context?,
        fq: FlightQuery,
        szName: String
    ) : AsyncTask<Void?, Void?, MFBSoap>() {
        private val mCtxt: AsyncWeakContext<FlightQuery?> = AsyncWeakContext(c, null)
        private val mName = szName
        private var mFq: FlightQuery = fq
        override fun doInBackground(vararg params: Void?): MFBSoap {
            val cqSVC = CannedQuerySvc()
            CannedQuery.cannedQueries = cqSVC.addNamedQueryForUser(
                AuthToken.m_szAuthToken!!,
                mName,
                mFq,
                mCtxt.context
            )
            return cqSVC
        }

        override fun onPreExecute() {}
        override fun onPostExecute(svc: MFBSoap) {}
    }

    private class DeleteCannedQueryTask(
        c: Context?,
        fq: CannedQuery,
        afq: ActFlightQuery
    ) : AsyncTask<Void?, Void?, MFBSoap?>() {
        private val mCtxt: AsyncWeakContext<ActFlightQuery> = AsyncWeakContext(c, afq)
        private var mFq: CannedQuery = fq
        override fun doInBackground(vararg params: Void?): MFBSoap {
            val cqSVC = CannedQuerySvc()
            CannedQuery.cannedQueries = cqSVC.deleteNamedQueryForUser(
                AuthToken.m_szAuthToken!!,
                mFq,
                mCtxt.context
            )
            return cqSVC
        }

        override fun onPreExecute() {}
        override fun onPostExecute(svc: MFBSoap?) {
            if (svc != null) {
                if (svc.lastError.isEmpty()) {
                    val afq = mCtxt.callingActivity
                    afq?.setUpNamedQueries()
                } else {
                    val c = mCtxt.context
                    if (c != null) MFBUtil.alert(c, c.getString(R.string.txtError), svc.lastError)
                }
            }
        }
    }

    private class RefreshCPTTask(c: Context?, avt: ActFlightQuery) :
        AsyncTask<Void?, Void?, Boolean>() {
        private var mPd: ProgressDialog? = null
        val mCtxt: AsyncWeakContext<ActFlightQuery> = AsyncWeakContext(c, avt)
        override fun doInBackground(vararg params: Void?): Boolean {
            val cptSvc = CustomPropertyTypesSvc()
            cptSvc.getCustomPropertyTypes(AuthToken.m_szAuthToken, false, mCtxt.context!!)
            return true
        }

        override fun onPreExecute() {
            val c = mCtxt.context
            if (c != null) mPd = MFBUtil.showProgress(c, c.getString(R.string.prgCPT))
        }

        override fun onPostExecute(b: Boolean) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
        }

    }

    // We are going to return only active aircraft UNLESS:
    // a) fShowAllAircraft is true
    // b) all aircraft are active, or
    // c) the query references an inactive aircraft.
    // if b or c is true, we will set (a) to true.
    private fun getCurrentAircraft(): Array<Aircraft>? {
        if (mRgac == null) {
            mRgacall = AircraftSvc().cachedAircraft
            mRgac = mRgacall
        }
        if (fShowAllAircraft) mRgac = mRgacall else {
            val lst = ArrayList<Aircraft>()
            for (ac in mRgac!!) if (!ac.hideFromSelection) lst.add(ac)
            if (lst.size == mRgac!!.size) fShowAllAircraft =
                true else if (currentQuery != null) {
                for (ac in currentQuery!!.AircraftList) if (ac.hideFromSelection) {
                    fShowAllAircraft = true
                    break
                }
                if (!fShowAllAircraft) mRgac = lst.toTypedArray()
            }
        }
        return mRgac
    }

    private fun getActiveMakes(): Array<MakeModel> {
        if (mRgmm == null) {
            getCurrentAircraft()
            val htmm: MutableMap<String, MakeModel> = HashMap()
            for (ac in mRgacall!!) {
                if (!htmm.containsKey(ac.modelDescription)) {
                    val mm = MakeModel()
                    mm.makeModelId = ac.modelID
                    mm.description = ac.modelDescription
                    htmm[String.format(Locale.US, "%d", mm.makeModelId)] = mm
                }
            }
            val almm : MutableList<MakeModel> = mutableListOf()
            for (key in htmm.keys) almm.add(htmm[key]!!)
            almm.sort()
            mRgmm = almm.toTypedArray()
        }
        return mRgmm!!
    }

    fun getCurrentQuery(): FlightQuery? {
        fromForm()
        return currentQuery
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.flightquery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val i = requireActivity().intent
        currentQuery = i.getSerializableExtra(QUERY_TO_EDIT) as FlightQuery?
        if (currentQuery == null) currentQuery = FlightQuery()
        if (CannedQuery.cannedQueries == null) GetCannedQueryTask(
            requireActivity(),
            this
        ).execute() else setUpNamedQueries()
        val cptSvc = CustomPropertyTypesSvc()
        if (cptSvc.getCacheStatus() == DBCache.DBCacheStatus.INVALID) {
            val rt = RefreshCPTTask(this.context, this)
            rt.execute()
        }
        addListener(R.id.btnfqDateStart)
        addListener(R.id.btnfqDateEnd)
        addListener(R.id.rbAlltime)
        addListener(R.id.rbCustom)
        addListener(R.id.rbPreviousMonth)
        addListener(R.id.rbPreviousYear)
        addListener(R.id.rbThisMonth)
        addListener(R.id.rbTrailing12)
        addListener(R.id.rbTrailing6)
        addListener(R.id.rbTrailing30)
        addListener(R.id.rbTrailing90)
        addListener(R.id.rbYTD)
        addListener(R.id.rbAllEngines)
        addListener(R.id.rbEngineJet)
        addListener(R.id.rbEnginePiston)
        addListener(R.id.rbEngineTurbine)
        addListener(R.id.rbEngineTurboprop)
        addListener(R.id.rbEngineElectric)
        addListener(R.id.rbInstanceAny)
        addListener(R.id.rbInstanceReal)
        addListener(R.id.rbInstanceTraining)
        addListener(R.id.rbDistanceAny)
        addListener(R.id.rbDistanceLocal)
        addListener(R.id.rbDistanceNonlocal)
        addListener(R.id.rbConjunctionAllFeature)
        addListener(R.id.rbConjunctionAnyFeature)
        addListener(R.id.rbConjunctionNoFeature)
        addListener(R.id.rbConjunctionAllProps)
        addListener(R.id.rbConjunctionAnyProps)
        addListener(R.id.rbConjunctionNoProps)

        // Expand/collapse
        addListener(R.id.txtFQDatesHeader)
        addListener(R.id.txtFQAirportsHeader)
        addListener(R.id.txtFQACFeatures)
        addListener(R.id.txtFQFlightFeatures)
        addListener(R.id.txtFQAircraftHeader)
        addListener(R.id.txtFQModelsHeader)
        addListener(R.id.txtFQCatClassHeader)
        addListener(R.id.txtFQPropsHeader)
        addListener(R.id.txtFQNamedQueryHeader)
        setUpChecklists()
        setExpandCollapseState()
    }

    private fun setExpandCollapseState() {
        if (currentQuery == null)
            return
        
        val curQ = currentQuery!!
        val cannedQs = CannedQuery.cannedQueries
        
        setExpandedState(
            findViewById(R.id.txtFQDatesHeader) as TextView,
            findViewById(R.id.sectFQDates)!!,
            curQ.DateRange != DateRanges.AllTime
        )
        setExpandedState(
            findViewById(R.id.txtFQAirportsHeader) as TextView,
            findViewById(R.id.tblFQAirports)!!,
            curQ.AirportList.isNotEmpty() || curQ.Distance != FlightDistance.AllFlights
        )
        setExpandedState(
            findViewById(R.id.txtFQACFeatures) as TextView,
            findViewById(R.id.sectFQAircraftFeatures)!!,
            curQ.HasAircraftCriteria()
        )
        setExpandedState(
            findViewById(R.id.txtFQFlightFeatures) as TextView,
            findViewById(R.id.sectFQFlightFeatures)!!,
            curQ.HasFlightCriteria()
        )
        setExpandedState(
            findViewById(R.id.txtFQAircraftHeader) as TextView,
            findViewById(R.id.llfqAircraft)!!,
            curQ.AircraftList.isNotEmpty()
        )
        setExpandedState(
            findViewById(R.id.txtFQModelsHeader) as TextView,
            findViewById(R.id.sectFQModels)!!,
            curQ.MakeList.isNotEmpty() || (curQ.ModelName != null &&  curQ.ModelName!!.isNotEmpty())
        )
        setExpandedState(
            findViewById(R.id.txtFQCatClassHeader) as TextView,
            findViewById(R.id.tblFQCatClass)!!,
            curQ.CatClassList.isNotEmpty()
        )
        setExpandedState(
            findViewById(R.id.txtFQPropsHeader) as TextView,
            findViewById(R.id.fqPropsBody)!!,
            curQ.PropertyTypes.isNotEmpty()
        )
        setExpandedState(
            findViewById(R.id.txtFQNamedQueryHeader) as TextView,
            findViewById(R.id.sectFQNamedQueries)!!,
            cannedQs != null && cannedQs.isNotEmpty()
        )
        val btnShowAll = findViewById(R.id.btnShowAllAircraft) as Button
        btnShowAll.setOnClickListener {
            fromForm()
            fShowAllAircraft = true
            setUpAircraftChecklist()
            toForm()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!fCannedQueryClicked) fromForm()
        ActTotals.setNeedsRefresh(true)
        clearCachedFlights()
        val szQueryName = stringFromField(R.id.txtNameForQuery)
        val curQ = currentQuery!!
        if (curQ.HasCriteria() && szQueryName.isNotEmpty()) AddCannedQueryTask(
            requireActivity(),
            curQ,
            szQueryName
        ).execute()

        // in case things change before next time, clear out the cached arrays.
        mRgac = null
        mRgmm = null
    }

    // region Dynamic checklists - aircraft, models, category class, and properties
    internal interface CheckedTableListener {
        // called when a checkbox item is changed. fAdded is true if it's added, otherwise it is removed.
        fun itemStateChanged(o: Any?, fAdded: Boolean)

        // Tests if the object shoud initially be selected
        fun itemIsChecked(o: Any?): Boolean
    }

    private fun setUpDynamicCheckList(
        idTable: Int,
        rgItemsIn: Array<*>?,
        listener: CheckedTableListener?
    ) {
        val rgItems = rgItemsIn ?: arrayOf<String>()
        val tl = findViewById(idTable) as TableLayout
        tl.removeAllViews()
        val l = requireActivity().layoutInflater
        assert(listener != null)
        for (o in rgItems) {
            val tr = l.inflate(R.layout.checkboxtableitem, tl, false) as TableRow
            val ck = tr.findViewById<CheckBox>(R.id.checkbox)
            ck.text = o.toString()
            ck.isChecked = listener!!.itemIsChecked(o)
            ck.setOnCheckedChangeListener { _: CompoundButton?, fChecked: Boolean ->
                listener.itemStateChanged(
                    o,
                    fChecked
                )
            }
            tl.addView(
                tr,
                TableLayout.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun setUpAircraftChecklist() {
        setUpDynamicCheckList(
            R.id.tblFQAircraft,
            getCurrentAircraft() as Array<*>?,
            object : CheckedTableListener {
                override fun itemStateChanged(o: Any?, fAdded: Boolean) {
                    val lst: MutableList<Aircraft> =
                        ArrayList(listOf(*currentQuery!!.AircraftList))
                    if (fAdded) lst.add(o as Aircraft) else lst.removeIf { ac: Aircraft? -> ac!!.aircraftID == (o as Aircraft?)!!.aircraftID }
                    currentQuery!!.AircraftList = lst.toTypedArray()
                }

                override fun itemIsChecked(o: Any?): Boolean {
                    for (ac in currentQuery!!.AircraftList) if (ac.aircraftID == (o as Aircraft?)!!.aircraftID) return true
                    return false
                }
            })
        findViewById(R.id.btnShowAllAircraft)!!.visibility =
            if (fShowAllAircraft) View.GONE else View.VISIBLE
    }

    private fun setUpChecklists() {
        setUpAircraftChecklist()
        setUpDynamicCheckList(R.id.tblFQModels, getActiveMakes() as Array<*>?, object : CheckedTableListener {
            override fun itemStateChanged(o: Any?, fAdded: Boolean) {
                val lst: MutableList<MakeModel> =
                    ArrayList(listOf(*currentQuery!!.MakeList))
                if (fAdded) lst.add(o as MakeModel) else lst.removeIf { m: MakeModel? -> m!!.makeModelId == (o as MakeModel?)!!.makeModelId }
                currentQuery!!.MakeList = lst.toTypedArray()
            }

            override fun itemIsChecked(o: Any?): Boolean {
                for (m in currentQuery!!.MakeList) if (m.makeModelId == (o as MakeModel?)!!.makeModelId) return true
                return false
            }
        })
        setUpDynamicCheckList(
            R.id.tblFQCatClass,
            CategoryClass.getAllCatClasses(),
            object : CheckedTableListener {
                override fun itemStateChanged(o: Any?, fAdded: Boolean) {
                    val lst: MutableList<CategoryClass> =
                        ArrayList(listOf(*currentQuery!!.CatClassList))
                    if (fAdded) lst.add(o as CategoryClass) else lst.removeIf { cc: CategoryClass? -> cc!!.idCatClass == (o as CategoryClass?)!!.idCatClass }
                    currentQuery!!.CatClassList = lst.toTypedArray()
                }

                override fun itemIsChecked(o: Any?): Boolean {
                    for (cc in currentQuery!!.CatClassList) if (cc.idCatClass == (o as CategoryClass?)!!.idCatClass) return true
                    return false
                }
            })
        setUpDynamicCheckList(R.id.tblFQProps, searchableProperties, object : CheckedTableListener {
            override fun itemStateChanged(o: Any?, fAdded: Boolean) {
                val lst: MutableList<CustomPropertyType> =
                    ArrayList(listOf(*currentQuery!!.PropertyTypes))
                if (fAdded) lst.add(o as CustomPropertyType) else lst.removeIf { cpt: CustomPropertyType? -> cpt!!.idPropType == (o as CustomPropertyType?)!!.idPropType }
                currentQuery!!.PropertyTypes = lst.toTypedArray()
            }

            override fun itemIsChecked(o: Any?): Boolean {
                for (cpt in currentQuery!!.PropertyTypes) if (cpt.idPropType == (o as CustomPropertyType?)!!.idPropType) return true
                return false
            }
        })
    }

    //endregion
    private fun setUpNamedQueries() {
        var rgItems = CannedQuery.cannedQueries
        val tl = findViewById(R.id.tblFQNamedQueries) as TableLayout
        tl.removeAllViews()
        val l = requireActivity().layoutInflater
        if (rgItems == null) rgItems = arrayOf()
        for (o in rgItems) {
            val tr = l.inflate(R.layout.namedquerytableitem, tl, false) as TableRow
            val tv = tr.findViewById<TextView>(R.id.lblSavedQuery)
            tv.text = o.QueryName
            tv.setOnClickListener {
                currentQuery = o
                fCannedQueryClicked = true
                val i = Intent()
                i.putExtra(QUERY_TO_EDIT, o)
                requireActivity().setResult(Activity.RESULT_OK, i)
                requireActivity().finish()
            }
            val btnDelete = tr.findViewById<ImageButton>(R.id.btnDeleteNamedQuery)
            btnDelete.setOnClickListener {
                AlertDialog.Builder(requireActivity(), R.style.MFBDialog)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.lblConfirm)
                    .setMessage(R.string.fqQueryDeleteConfirm)
                    .setPositiveButton(R.string.lblOK) { _: DialogInterface?, _: Int ->
                        DeleteCannedQueryTask(
                            context, o, this
                        ).execute()
                    }
                    .setNegativeButton(R.string.lblCancel, null)
                    .show()
            }
            tl.addView(
                tr,
                TableLayout.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        toForm()
        fCannedQueryClicked = false
    }

    private fun reset() {
        currentQuery!!.Init()
        setUpChecklists()
        toForm()
        setExpandCollapseState()
    }

    private fun toForm() {
        setStringForField(R.id.fqGeneralText, currentQuery!!.GeneralText)
        setStringForField(R.id.fqModelName, currentQuery!!.ModelName)
        setStringForField(R.id.fqAirports, TextUtils.join(" ", currentQuery!!.AirportList))
        setLocalDateForField(
            R.id.btnfqDateStart,
            MFBUtil.localDateFromUTCDate(currentQuery!!.DateMin)
        )
        setLocalDateForField(
            R.id.btnfqDateEnd,
            MFBUtil.localDateFromUTCDate(currentQuery!!.DateMax)
        )
        when (currentQuery!!.DateRange) {
            DateRanges.none -> {}
            DateRanges.AllTime -> setRadioButton(R.id.rbAlltime)
            DateRanges.YTD -> setRadioButton(R.id.rbYTD)
            DateRanges.Trailing12Months -> setRadioButton(R.id.rbTrailing12)
            DateRanges.Tailing6Months -> setRadioButton(R.id.rbTrailing6)
            DateRanges.ThisMonth -> setRadioButton(R.id.rbThisMonth)
            DateRanges.PrevMonth -> setRadioButton(R.id.rbPreviousMonth)
            DateRanges.PrevYear -> setRadioButton(R.id.rbPreviousYear)
            DateRanges.Trailing30 -> setRadioButton(R.id.rbTrailing30)
            DateRanges.Trailing90 -> setRadioButton(R.id.rbTrailing90)
            DateRanges.Custom -> setRadioButton(R.id.rbCustom)
        }
        when (currentQuery!!.FlightCharacteristicsConjunction) {
            GroupConjunction.All -> setRadioButton(R.id.rbConjunctionAllFeature)
            GroupConjunction.Any -> setRadioButton(R.id.rbConjunctionAnyFeature)
            GroupConjunction.None -> setRadioButton(R.id.rbConjunctionNoFeature)
        }
        when (currentQuery!!.PropertiesConjunction) {
            GroupConjunction.All -> setRadioButton(R.id.rbConjunctionAllProps)
            GroupConjunction.Any -> setRadioButton(R.id.rbConjunctionAnyProps)
            GroupConjunction.None -> setRadioButton(R.id.rbConjunctionNoFeature)
        }
        setCheckState(R.id.ckIsPublic, currentQuery!!.IsPublic)
        setCheckState(R.id.ckIsSigned, currentQuery!!.IsSigned)
        setCheckState(R.id.ckHasApproaches, currentQuery!!.HasApproaches)
        setCheckState(R.id.ckHasCFI, currentQuery!!.HasCFI)
        setCheckState(R.id.ckHasDual, currentQuery!!.HasDual)
        setCheckState(R.id.ckHasFSLandings, currentQuery!!.HasFullStopLandings)
        setCheckState(R.id.ckHasFSNightLandings, currentQuery!!.HasNightLandings)
        setCheckState(R.id.ckHasHolds, currentQuery!!.HasHolds)
        setCheckState(R.id.ckHasIMC, currentQuery!!.HasIMC)
        setCheckState(R.id.ckHasNight, currentQuery!!.HasNight)
        setCheckState(R.id.ckHasPIC, currentQuery!!.HasPIC)
        setCheckState(R.id.ckHasTotal, currentQuery!!.HasTotalTime)
        setCheckState(R.id.ckHasSIC, currentQuery!!.HasSIC)
        setCheckState(R.id.ckHasSimIMC, currentQuery!!.HasSimIMCTime)
        setCheckState(R.id.ckHasTelemetry, currentQuery!!.HasTelemetry)
        setCheckState(R.id.ckHasImages, currentQuery!!.HasImages)
        setCheckState(R.id.ckHasXC, currentQuery!!.HasXC)
        setCheckState(R.id.ckHasGroundSim, currentQuery!!.HasGroundSim)
        setCheckState(R.id.ckHasAnyLandings, currentQuery!!.HasLandings)
        setCheckState(R.id.ckHasAnyInstrument, currentQuery!!.HasAnyInstrument)
        setCheckState(R.id.ckHasFlaps, currentQuery!!.HasFlaps)
        setCheckState(R.id.ckIsComplex, currentQuery!!.IsComplex)
        setCheckState(R.id.ckIsConstantProp, currentQuery!!.IsConstantSpeedProp)
        setCheckState(R.id.ckisGlass, currentQuery!!.IsGlass)
        setCheckState(R.id.ckisTAA, currentQuery!!.IsTAA)
        setCheckState(R.id.ckIsHighPerf, currentQuery!!.IsHighPerformance)
        setCheckState(R.id.ckIsRetract, currentQuery!!.IsRetract)
        setCheckState(R.id.ckIsTailwheel, currentQuery!!.IsTailwheel)
        setCheckState(R.id.ckIsMotorGlider, currentQuery!!.IsMotorglider)
        setCheckState(R.id.ckIsMultiEngineHeli, currentQuery!!.IsMultiEngineHeli)
        when (currentQuery!!.EngineType) {
            EngineTypeRestriction.AllEngines -> setRadioButton(R.id.rbAllEngines)
            EngineTypeRestriction.Piston -> setRadioButton(R.id.rbEnginePiston)
            EngineTypeRestriction.Jet -> setRadioButton(R.id.rbEngineJet)
            EngineTypeRestriction.Turboprop -> setRadioButton(R.id.rbEngineTurboprop)
            EngineTypeRestriction.AnyTurbine -> setRadioButton(R.id.rbEngineTurbine)
            EngineTypeRestriction.Electric -> setRadioButton(R.id.rbEngineElectric)
        }
        when (currentQuery!!.AircraftInstanceTypes) {
            AircraftInstanceRestriction.AllAircraft -> setRadioButton(R.id.rbInstanceAny)
            AircraftInstanceRestriction.RealOnly -> setRadioButton(R.id.rbInstanceReal)
            AircraftInstanceRestriction.TrainingOnly -> setRadioButton(R.id.rbInstanceTraining)
        }
        when (currentQuery!!.Distance) {
            FlightDistance.AllFlights -> setRadioButton(R.id.rbDistanceAny)
            FlightDistance.LocalOnly -> setRadioButton(R.id.rbDistanceLocal)
            FlightDistance.NonLocalOnly -> setRadioButton(R.id.rbDistanceNonlocal)
        }
    }

    private fun readFlightCharacteristics() {
        currentQuery!!.IsPublic = checkState(R.id.ckIsPublic)
        currentQuery!!.IsSigned = checkState(R.id.ckIsSigned)
        currentQuery!!.HasApproaches = checkState(R.id.ckHasApproaches)
        currentQuery!!.HasCFI = checkState(R.id.ckHasCFI)
        currentQuery!!.HasDual = checkState(R.id.ckHasDual)
        currentQuery!!.HasFullStopLandings = checkState(R.id.ckHasFSLandings)
        currentQuery!!.HasNightLandings = checkState(R.id.ckHasFSNightLandings)
        currentQuery!!.HasHolds = checkState(R.id.ckHasHolds)
        currentQuery!!.HasIMC = checkState(R.id.ckHasIMC)
        currentQuery!!.HasNight = checkState(R.id.ckHasNight)
        currentQuery!!.HasPIC = checkState(R.id.ckHasPIC)
        currentQuery!!.HasTotalTime = checkState(R.id.ckHasTotal)
        currentQuery!!.HasSIC = checkState(R.id.ckHasSIC)
        currentQuery!!.HasSimIMCTime = checkState(R.id.ckHasSimIMC)
        currentQuery!!.HasTelemetry = checkState(R.id.ckHasTelemetry)
        currentQuery!!.HasImages = checkState(R.id.ckHasImages)
        currentQuery!!.HasXC = checkState(R.id.ckHasXC)
        currentQuery!!.HasAnyInstrument = checkState(R.id.ckHasAnyInstrument)
        currentQuery!!.HasLandings = checkState(R.id.ckHasAnyLandings)
        currentQuery!!.HasGroundSim = checkState(R.id.ckHasGroundSim)
    }

    private fun fromForm() {
        currentQuery!!.GeneralText = stringFromField(R.id.fqGeneralText)
        currentQuery!!.ModelName = stringFromField(R.id.fqModelName)
        val szAirports =
            stringFromField(R.id.fqAirports).trim { it <= ' ' }.uppercase(Locale.getDefault())
        currentQuery!!.AirportList =
            if (szAirports.isNotEmpty()) Airport.splitCodesSearch(szAirports) else arrayOf()
        readFlightCharacteristics()
        currentQuery!!.HasFlaps = checkState(R.id.ckHasFlaps)
        currentQuery!!.IsComplex = checkState(R.id.ckIsComplex)
        currentQuery!!.IsConstantSpeedProp = checkState(R.id.ckIsConstantProp)
        currentQuery!!.IsGlass = checkState(R.id.ckisGlass)
        currentQuery!!.IsTAA = checkState(R.id.ckisTAA)
        currentQuery!!.IsHighPerformance = checkState(R.id.ckIsHighPerf)
        currentQuery!!.IsRetract = checkState(R.id.ckIsRetract)
        currentQuery!!.IsTailwheel = checkState(R.id.ckIsTailwheel)
        currentQuery!!.IsMotorglider = checkState(R.id.ckIsMotorGlider)
        currentQuery!!.IsMultiEngineHeli = checkState(R.id.ckIsMultiEngineHeli)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.flightquerymenu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        if (item.itemId == R.id.menuResetFlight) {
            reset()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun updateDate(id: Int, dt: Date?) {
        if (id == R.id.btnfqDateStart) {
            currentQuery!!.DateMin = if (dt == null) null else MFBUtil.getUTCDateFromLocalDate(dt)
            currentQuery!!.DateRange = DateRanges.Custom
            (findViewById(R.id.rbCustom) as RadioButton).isChecked = true
        } else if (id == R.id.btnfqDateEnd) {
            currentQuery!!.DateMax = if (dt == null) null else MFBUtil.getUTCDateFromLocalDate(dt)
            currentQuery!!.DateRange = DateRanges.Custom
            (findViewById(R.id.rbCustom) as RadioButton).isChecked = true
        }
        toForm()
    }

    private fun toggleHeader(v: View, idTarget: Int) {
        val vFocus = requireActivity().currentFocus
        vFocus?.clearFocus() // prevent scrolling to the top (where the first text box is)
        val target = findViewById(idTarget)!!
        setExpandedState(v as TextView, target, target.visibility != View.VISIBLE)
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.btnfqDateEnd || id == R.id.btnfqDateStart) {
            val dt = if (id == R.id.btnfqDateStart) currentQuery!!.DateMin else currentQuery!!.DateMax
            val dlg = DlgDatePicker(
                requireActivity(),
                DlgDatePicker.DatePickMode.LOCALDATEONLY,
                MFBUtil.localDateFromUTCDate(dt ?: Date())!!
            )
            dlg.mDelegate = this
            dlg.mId = id
            dlg.show()
            return
        }

        // All of the remaining items below are radio buttons
        when (id) {
            R.id.rbAlltime -> currentQuery!!.SetDateRange(DateRanges.AllTime)
            R.id.rbCustom -> currentQuery!!.SetDateRange(
                DateRanges.Custom
            )
            R.id.rbPreviousMonth -> currentQuery!!.SetDateRange(DateRanges.PrevMonth)
            R.id.rbPreviousYear -> currentQuery!!.SetDateRange(
                DateRanges.PrevYear
            )
            R.id.rbThisMonth -> currentQuery!!.SetDateRange(DateRanges.ThisMonth)
            R.id.rbTrailing12 -> currentQuery!!.SetDateRange(
                DateRanges.Trailing12Months
            )
            R.id.rbTrailing6 -> currentQuery!!.SetDateRange(DateRanges.Tailing6Months)
            R.id.rbTrailing30 -> currentQuery!!.SetDateRange(
                DateRanges.Trailing30
            )
            R.id.rbTrailing90 -> currentQuery!!.SetDateRange(DateRanges.Trailing90)
            R.id.rbYTD -> currentQuery!!.SetDateRange(
                DateRanges.YTD
            )
            R.id.rbAllEngines -> currentQuery!!.EngineType =
                EngineTypeRestriction.AllEngines
            R.id.rbEngineJet -> currentQuery!!.EngineType =
                EngineTypeRestriction.Jet
            R.id.rbEnginePiston -> currentQuery!!.EngineType =
                EngineTypeRestriction.Piston
            R.id.rbEngineTurbine -> currentQuery!!.EngineType =
                EngineTypeRestriction.AnyTurbine
            R.id.rbEngineTurboprop -> currentQuery!!.EngineType =
                EngineTypeRestriction.Turboprop
            R.id.rbEngineElectric -> currentQuery!!.EngineType =
                EngineTypeRestriction.Electric
            R.id.rbInstanceAny -> currentQuery!!.AircraftInstanceTypes =
                AircraftInstanceRestriction.AllAircraft
            R.id.rbInstanceReal -> currentQuery!!.AircraftInstanceTypes =
                AircraftInstanceRestriction.RealOnly
            R.id.rbInstanceTraining -> currentQuery!!.AircraftInstanceTypes =
                AircraftInstanceRestriction.TrainingOnly
            R.id.rbConjunctionAllFeature -> {
                currentQuery!!.FlightCharacteristicsConjunction = GroupConjunction.All
                readFlightCharacteristics()
            }
            R.id.rbConjunctionAnyFeature -> {
                currentQuery!!.FlightCharacteristicsConjunction = GroupConjunction.Any
                readFlightCharacteristics()
            }
            R.id.rbConjunctionNoFeature -> {
                currentQuery!!.FlightCharacteristicsConjunction = GroupConjunction.None
                readFlightCharacteristics()
            }
            R.id.rbConjunctionAllProps -> currentQuery!!.PropertiesConjunction =
                GroupConjunction.All
            R.id.rbConjunctionAnyProps -> currentQuery!!.PropertiesConjunction =
                GroupConjunction.Any
            R.id.rbConjunctionNoProps -> currentQuery!!.PropertiesConjunction =
                GroupConjunction.None
            R.id.rbDistanceAny -> currentQuery!!.Distance =
                FlightDistance.AllFlights
            R.id.rbDistanceLocal -> currentQuery!!.Distance =
                FlightDistance.LocalOnly
            R.id.rbDistanceNonlocal -> currentQuery!!.Distance =
                FlightDistance.NonLocalOnly
            R.id.txtFQACFeatures -> toggleHeader(
                v,
                R.id.sectFQAircraftFeatures
            )
            R.id.txtFQDatesHeader -> toggleHeader(
                v,
                R.id.sectFQDates
            )
            R.id.txtFQAirportsHeader -> toggleHeader(
                v,
                R.id.tblFQAirports
            )
            R.id.txtFQFlightFeatures -> toggleHeader(
                v,
                R.id.sectFQFlightFeatures
            )
            R.id.txtFQAircraftHeader -> toggleHeader(
                v,
                R.id.llfqAircraft
            )
            R.id.txtFQModelsHeader -> toggleHeader(
                v,
                R.id.sectFQModels
            )
            R.id.txtFQCatClassHeader -> toggleHeader(
                v,
                R.id.tblFQCatClass
            )
            R.id.txtFQPropsHeader -> toggleHeader(
                v,
                R.id.fqPropsBody
            )
            R.id.txtFQNamedQueryHeader -> toggleHeader(v, R.id.sectFQNamedQueries)
        }
        toForm()
    }

    companion object {
        const val QUERY_TO_EDIT = "com.myflightbook.android.querytoedit"
    }
}