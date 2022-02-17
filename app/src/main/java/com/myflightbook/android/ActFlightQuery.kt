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
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.myflightbook.android.DlgDatePicker.DateTimeUpdate
import com.myflightbook.android.webservices.AircraftSvc
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.CannedQuerySvc
import com.myflightbook.android.webservices.CustomPropertyTypesSvc
import com.myflightbook.android.webservices.CustomPropertyTypesSvc.Companion.searchableProperties
import com.myflightbook.android.webservices.RecentFlightsSvc.Companion.clearCachedFlights
import kotlinx.coroutines.launch
import model.*
import model.FlightQuery.*
import java.util.*

class ActFlightQuery : ActMFBForm(), View.OnClickListener, DateTimeUpdate {
    private var currentQuery: FlightQuery? = null
    private var mRgac: Array<Aircraft>? = null
    private var mRgacall: Array<Aircraft>? = null
    private var mRgmm: Array<MakeModel>? = null
    private var fShowAllAircraft = false
    private var fCannedQueryClicked = false

    private suspend fun getCannedQueries() {
        doAsync<CannedQuerySvc, Array<CannedQuery>?>(requireActivity(),
            CannedQuerySvc(),
            null,
            { s: CannedQuerySvc ->
                s.getNamedQueriesForUser(AuthToken.m_szAuthToken, requireContext())
            },
            { s: CannedQuerySvc, result: Array<CannedQuery>? ->
                if (s.lastError.isEmpty()) {
                    CannedQuery.cannedQueries = result
                    setUpNamedQueries()
                }
            })
    }

    private suspend fun addCannedQuery(name : String, fq : FlightQuery) {
        doAsync<CannedQuerySvc, Array<CannedQuery>?>(requireActivity(), CannedQuerySvc(), null,
            {
                s -> s.addNamedQueryForUser(AuthToken.m_szAuthToken!!, name, fq, requireContext())
            },
            {
                s, result ->
                if (s.lastError.isEmpty()) {
                    CannedQuery.cannedQueries = result
                    setUpNamedQueries()
                }
            })
    }

    private suspend fun deleteCannedQuery(fq : CannedQuery) {
        doAsync<CannedQuerySvc, Array<CannedQuery>?>(requireActivity(), CannedQuerySvc(), null,
            {
                s -> s.deleteNamedQueryForUser(AuthToken.m_szAuthToken!!, fq, requireContext())
            },
            {
                s, result ->
                if (s.lastError.isEmpty()) {
                    CannedQuery.cannedQueries = result
                    setUpNamedQueries()
                }
            })
    }

    private suspend fun refreshPropTypes()
    {
        doAsync<CustomPropertyTypesSvc, Boolean?>(
            requireActivity(),
            CustomPropertyTypesSvc(),
            getString(R.string.prgCPT),
            { s ->
                s.getCustomPropertyTypes(AuthToken.m_szAuthToken, false, requireContext())
                true
            },
            { _, _ ->
            }
        )
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
                for (ac in currentQuery!!.aircraftList) if (ac.hideFromSelection) {
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
        if (CannedQuery.cannedQueries == null)
            lifecycleScope.launch { getCannedQueries() }
        else
            setUpNamedQueries()
        val cptSvc = CustomPropertyTypesSvc()
        if (cptSvc.getCacheStatus() == DBCache.DBCacheStatus.INVALID) {
            lifecycleScope.launch { refreshPropTypes() }
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
            curQ.dateRange != DateRanges.AllTime
        )
        setExpandedState(
            findViewById(R.id.txtFQAirportsHeader) as TextView,
            findViewById(R.id.tblFQAirports)!!,
            curQ.airportList.isNotEmpty() || curQ.distance != FlightDistance.AllFlights
        )
        setExpandedState(
            findViewById(R.id.txtFQACFeatures) as TextView,
            findViewById(R.id.sectFQAircraftFeatures)!!,
            curQ.hasAircraftCriteria()
        )
        setExpandedState(
            findViewById(R.id.txtFQFlightFeatures) as TextView,
            findViewById(R.id.sectFQFlightFeatures)!!,
            curQ.hasFlightCriteria()
        )
        setExpandedState(
            findViewById(R.id.txtFQAircraftHeader) as TextView,
            findViewById(R.id.llfqAircraft)!!,
            curQ.aircraftList.isNotEmpty()
        )
        setExpandedState(
            findViewById(R.id.txtFQModelsHeader) as TextView,
            findViewById(R.id.sectFQModels)!!,
            curQ.makeList.isNotEmpty() || (curQ.modelName != null &&  curQ.modelName!!.isNotEmpty())
        )
        setExpandedState(
            findViewById(R.id.txtFQCatClassHeader) as TextView,
            findViewById(R.id.tblFQCatClass)!!,
            curQ.catClassList.isNotEmpty()
        )
        setExpandedState(
            findViewById(R.id.txtFQPropsHeader) as TextView,
            findViewById(R.id.fqPropsBody)!!,
            curQ.propertyTypes.isNotEmpty()
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
        if (curQ.hasCriteria() && szQueryName.isNotEmpty())
            lifecycleScope.launch {
                addCannedQuery(szQueryName, curQ)
            }

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
                        ArrayList(listOf(*currentQuery!!.aircraftList))
                    if (fAdded) lst.add(o as Aircraft) else lst.removeIf { ac: Aircraft? -> ac!!.aircraftID == (o as Aircraft?)!!.aircraftID }
                    currentQuery!!.aircraftList = lst.toTypedArray()
                }

                override fun itemIsChecked(o: Any?): Boolean {
                    for (ac in currentQuery!!.aircraftList) if (ac.aircraftID == (o as Aircraft?)!!.aircraftID) return true
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
                    ArrayList(listOf(*currentQuery!!.makeList))
                if (fAdded) lst.add(o as MakeModel) else lst.removeIf { m: MakeModel? -> m!!.makeModelId == (o as MakeModel?)!!.makeModelId }
                currentQuery!!.makeList = lst.toTypedArray()
            }

            override fun itemIsChecked(o: Any?): Boolean {
                for (m in currentQuery!!.makeList) if (m.makeModelId == (o as MakeModel?)!!.makeModelId) return true
                return false
            }
        })
        setUpDynamicCheckList(
            R.id.tblFQCatClass,
            CategoryClass.getAllCatClasses(),
            object : CheckedTableListener {
                override fun itemStateChanged(o: Any?, fAdded: Boolean) {
                    val lst: MutableList<CategoryClass> =
                        ArrayList(listOf(*currentQuery!!.catClassList))
                    if (fAdded) lst.add(o as CategoryClass) else lst.removeIf { cc: CategoryClass? -> cc!!.idCatClass == (o as CategoryClass?)!!.idCatClass }
                    currentQuery!!.catClassList = lst.toTypedArray()
                }

                override fun itemIsChecked(o: Any?): Boolean {
                    for (cc in currentQuery!!.catClassList) if (cc.idCatClass == (o as CategoryClass?)!!.idCatClass) return true
                    return false
                }
            })
        setUpDynamicCheckList(R.id.tblFQProps, searchableProperties, object : CheckedTableListener {
            override fun itemStateChanged(o: Any?, fAdded: Boolean) {
                val lst: MutableList<CustomPropertyType> =
                    ArrayList(listOf(*currentQuery!!.propertyTypes))
                if (fAdded) lst.add(o as CustomPropertyType) else lst.removeIf { cpt: CustomPropertyType? -> cpt!!.idPropType == (o as CustomPropertyType?)!!.idPropType }
                currentQuery!!.propertyTypes = lst.toTypedArray()
            }

            override fun itemIsChecked(o: Any?): Boolean {
                for (cpt in currentQuery!!.propertyTypes) if (cpt.idPropType == (o as CustomPropertyType?)!!.idPropType) return true
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
            tv.text = o.queryName
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
                        lifecycleScope.launch {
                            deleteCannedQuery(o)
                        }
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
        currentQuery!!.init()
        setUpChecklists()
        toForm()
        setExpandCollapseState()
    }

    private fun toForm() {
        setStringForField(R.id.fqGeneralText, currentQuery!!.generalText)
        setStringForField(R.id.fqModelName, currentQuery!!.modelName)
        setStringForField(R.id.fqAirports, TextUtils.join(" ", currentQuery!!.airportList))
        setLocalDateForField(
            R.id.btnfqDateStart,
            MFBUtil.localDateFromUTCDate(currentQuery!!.dateMin)
        )
        setLocalDateForField(
            R.id.btnfqDateEnd,
            MFBUtil.localDateFromUTCDate(currentQuery!!.dateMax)
        )
        when (currentQuery!!.dateRange!!) {
            DateRanges.None -> {}
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
        when (currentQuery!!.flightCharacteristicsConjunction) {
            GroupConjunction.All -> setRadioButton(R.id.rbConjunctionAllFeature)
            GroupConjunction.Any -> setRadioButton(R.id.rbConjunctionAnyFeature)
            GroupConjunction.None -> setRadioButton(R.id.rbConjunctionNoFeature)
        }
        when (currentQuery!!.propertiesConjunction) {
            GroupConjunction.All -> setRadioButton(R.id.rbConjunctionAllProps)
            GroupConjunction.Any -> setRadioButton(R.id.rbConjunctionAnyProps)
            GroupConjunction.None -> setRadioButton(R.id.rbConjunctionNoFeature)
        }
        setCheckState(R.id.ckIsPublic, currentQuery!!.isPublic)
        setCheckState(R.id.ckIsSigned, currentQuery!!.isSigned)
        setCheckState(R.id.ckHasApproaches, currentQuery!!.hasApproaches)
        setCheckState(R.id.ckHasCFI, currentQuery!!.hasCFI)
        setCheckState(R.id.ckHasDual, currentQuery!!.hasDual)
        setCheckState(R.id.ckHasFSLandings, currentQuery!!.hasFullStopLandings)
        setCheckState(R.id.ckHasFSNightLandings, currentQuery!!.hasNightLandings)
        setCheckState(R.id.ckHasHolds, currentQuery!!.hasHolds)
        setCheckState(R.id.ckHasIMC, currentQuery!!.hasIMC)
        setCheckState(R.id.ckHasNight, currentQuery!!.hasNight)
        setCheckState(R.id.ckHasPIC, currentQuery!!.hasPIC)
        setCheckState(R.id.ckHasTotal, currentQuery!!.hasTotalTime)
        setCheckState(R.id.ckHasSIC, currentQuery!!.hasSIC)
        setCheckState(R.id.ckHasSimIMC, currentQuery!!.hasSimIMCTime)
        setCheckState(R.id.ckHasTelemetry, currentQuery!!.hasTelemetry)
        setCheckState(R.id.ckHasImages, currentQuery!!.hasImages)
        setCheckState(R.id.ckHasXC, currentQuery!!.hasXC)
        setCheckState(R.id.ckHasGroundSim, currentQuery!!.hasGroundSim)
        setCheckState(R.id.ckHasAnyLandings, currentQuery!!.hasLandings)
        setCheckState(R.id.ckHasAnyInstrument, currentQuery!!.hasAnyInstrument)
        setCheckState(R.id.ckHasFlaps, currentQuery!!.hasFlaps)
        setCheckState(R.id.ckIsComplex, currentQuery!!.isComplex)
        setCheckState(R.id.ckIsConstantProp, currentQuery!!.isConstantSpeedProp)
        setCheckState(R.id.ckisGlass, currentQuery!!.isGlass)
        setCheckState(R.id.ckisTAA, currentQuery!!.isTAA)
        setCheckState(R.id.ckIsHighPerf, currentQuery!!.isHighPerformance)
        setCheckState(R.id.ckIsRetract, currentQuery!!.isRetract)
        setCheckState(R.id.ckIsTailwheel, currentQuery!!.isTailwheel)
        setCheckState(R.id.ckIsMotorGlider, currentQuery!!.isMotorglider)
        setCheckState(R.id.ckIsMultiEngineHeli, currentQuery!!.isMultiEngineHeli)
        when (currentQuery!!.engineType) {
            EngineTypeRestriction.AllEngines -> setRadioButton(R.id.rbAllEngines)
            EngineTypeRestriction.Piston -> setRadioButton(R.id.rbEnginePiston)
            EngineTypeRestriction.Jet -> setRadioButton(R.id.rbEngineJet)
            EngineTypeRestriction.Turboprop -> setRadioButton(R.id.rbEngineTurboprop)
            EngineTypeRestriction.AnyTurbine -> setRadioButton(R.id.rbEngineTurbine)
            EngineTypeRestriction.Electric -> setRadioButton(R.id.rbEngineElectric)
        }
        when (currentQuery!!.aircraftInstanceTypes) {
            AircraftInstanceRestriction.AllAircraft -> setRadioButton(R.id.rbInstanceAny)
            AircraftInstanceRestriction.RealOnly -> setRadioButton(R.id.rbInstanceReal)
            AircraftInstanceRestriction.TrainingOnly -> setRadioButton(R.id.rbInstanceTraining)
        }
        when (currentQuery!!.distance!!) {
            FlightDistance.AllFlights -> setRadioButton(R.id.rbDistanceAny)
            FlightDistance.LocalOnly -> setRadioButton(R.id.rbDistanceLocal)
            FlightDistance.NonLocalOnly -> setRadioButton(R.id.rbDistanceNonlocal)
        }
    }

    private fun readFlightCharacteristics() {
        currentQuery!!.isPublic = checkState(R.id.ckIsPublic)
        currentQuery!!.isSigned = checkState(R.id.ckIsSigned)
        currentQuery!!.hasApproaches = checkState(R.id.ckHasApproaches)
        currentQuery!!.hasCFI = checkState(R.id.ckHasCFI)
        currentQuery!!.hasDual = checkState(R.id.ckHasDual)
        currentQuery!!.hasFullStopLandings = checkState(R.id.ckHasFSLandings)
        currentQuery!!.hasNightLandings = checkState(R.id.ckHasFSNightLandings)
        currentQuery!!.hasHolds = checkState(R.id.ckHasHolds)
        currentQuery!!.hasIMC = checkState(R.id.ckHasIMC)
        currentQuery!!.hasNight = checkState(R.id.ckHasNight)
        currentQuery!!.hasPIC = checkState(R.id.ckHasPIC)
        currentQuery!!.hasTotalTime = checkState(R.id.ckHasTotal)
        currentQuery!!.hasSIC = checkState(R.id.ckHasSIC)
        currentQuery!!.hasSimIMCTime = checkState(R.id.ckHasSimIMC)
        currentQuery!!.hasTelemetry = checkState(R.id.ckHasTelemetry)
        currentQuery!!.hasImages = checkState(R.id.ckHasImages)
        currentQuery!!.hasXC = checkState(R.id.ckHasXC)
        currentQuery!!.hasAnyInstrument = checkState(R.id.ckHasAnyInstrument)
        currentQuery!!.hasLandings = checkState(R.id.ckHasAnyLandings)
        currentQuery!!.hasGroundSim = checkState(R.id.ckHasGroundSim)
    }

    private fun fromForm() {
        currentQuery!!.generalText = stringFromField(R.id.fqGeneralText)
        currentQuery!!.modelName = stringFromField(R.id.fqModelName)
        val szAirports =
            stringFromField(R.id.fqAirports).trim { it <= ' ' }.uppercase(Locale.getDefault())
        currentQuery!!.airportList =
            if (szAirports.isNotEmpty()) Airport.splitCodesSearch(szAirports) else arrayOf()
        readFlightCharacteristics()
        currentQuery!!.hasFlaps = checkState(R.id.ckHasFlaps)
        currentQuery!!.isComplex = checkState(R.id.ckIsComplex)
        currentQuery!!.isConstantSpeedProp = checkState(R.id.ckIsConstantProp)
        currentQuery!!.isGlass = checkState(R.id.ckisGlass)
        currentQuery!!.isTAA = checkState(R.id.ckisTAA)
        currentQuery!!.isHighPerformance = checkState(R.id.ckIsHighPerf)
        currentQuery!!.isRetract = checkState(R.id.ckIsRetract)
        currentQuery!!.isTailwheel = checkState(R.id.ckIsTailwheel)
        currentQuery!!.isMotorglider = checkState(R.id.ckIsMotorGlider)
        currentQuery!!.isMultiEngineHeli = checkState(R.id.ckIsMultiEngineHeli)
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
            currentQuery!!.dateMin = if (dt == null) null else MFBUtil.getUTCDateFromLocalDate(dt)
            currentQuery!!.dateRange = DateRanges.Custom
            (findViewById(R.id.rbCustom) as RadioButton).isChecked = true
        } else if (id == R.id.btnfqDateEnd) {
            currentQuery!!.dateMax = if (dt == null) null else MFBUtil.getUTCDateFromLocalDate(dt)
            currentQuery!!.dateRange = DateRanges.Custom
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
            val dt = if (id == R.id.btnfqDateStart) currentQuery!!.dateMin else currentQuery!!.dateMax
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
            R.id.rbAlltime -> currentQuery!!.setQueryDateRange(DateRanges.AllTime)
            R.id.rbCustom -> currentQuery!!.setQueryDateRange(
                DateRanges.Custom
            )
            R.id.rbPreviousMonth -> currentQuery!!.setQueryDateRange(DateRanges.PrevMonth)
            R.id.rbPreviousYear -> currentQuery!!.setQueryDateRange(
                DateRanges.PrevYear
            )
            R.id.rbThisMonth -> currentQuery!!.setQueryDateRange(DateRanges.ThisMonth)
            R.id.rbTrailing12 -> currentQuery!!.setQueryDateRange(
                DateRanges.Trailing12Months
            )
            R.id.rbTrailing6 -> currentQuery!!.setQueryDateRange(DateRanges.Tailing6Months)
            R.id.rbTrailing30 -> currentQuery!!.setQueryDateRange(
                DateRanges.Trailing30
            )
            R.id.rbTrailing90 -> currentQuery!!.setQueryDateRange(DateRanges.Trailing90)
            R.id.rbYTD -> currentQuery!!.setQueryDateRange(
                DateRanges.YTD
            )
            R.id.rbAllEngines -> currentQuery!!.engineType =
                EngineTypeRestriction.AllEngines
            R.id.rbEngineJet -> currentQuery!!.engineType =
                EngineTypeRestriction.Jet
            R.id.rbEnginePiston -> currentQuery!!.engineType =
                EngineTypeRestriction.Piston
            R.id.rbEngineTurbine -> currentQuery!!.engineType =
                EngineTypeRestriction.AnyTurbine
            R.id.rbEngineTurboprop -> currentQuery!!.engineType =
                EngineTypeRestriction.Turboprop
            R.id.rbEngineElectric -> currentQuery!!.engineType =
                EngineTypeRestriction.Electric
            R.id.rbInstanceAny -> currentQuery!!.aircraftInstanceTypes =
                AircraftInstanceRestriction.AllAircraft
            R.id.rbInstanceReal -> currentQuery!!.aircraftInstanceTypes =
                AircraftInstanceRestriction.RealOnly
            R.id.rbInstanceTraining -> currentQuery!!.aircraftInstanceTypes =
                AircraftInstanceRestriction.TrainingOnly
            R.id.rbConjunctionAllFeature -> {
                currentQuery!!.flightCharacteristicsConjunction = GroupConjunction.All
                readFlightCharacteristics()
            }
            R.id.rbConjunctionAnyFeature -> {
                currentQuery!!.flightCharacteristicsConjunction = GroupConjunction.Any
                readFlightCharacteristics()
            }
            R.id.rbConjunctionNoFeature -> {
                currentQuery!!.flightCharacteristicsConjunction = GroupConjunction.None
                readFlightCharacteristics()
            }
            R.id.rbConjunctionAllProps -> currentQuery!!.propertiesConjunction =
                GroupConjunction.All
            R.id.rbConjunctionAnyProps -> currentQuery!!.propertiesConjunction =
                GroupConjunction.Any
            R.id.rbConjunctionNoProps -> currentQuery!!.propertiesConjunction =
                GroupConjunction.None
            R.id.rbDistanceAny -> currentQuery!!.distance =
                FlightDistance.AllFlights
            R.id.rbDistanceLocal -> currentQuery!!.distance =
                FlightDistance.LocalOnly
            R.id.rbDistanceNonlocal -> currentQuery!!.distance =
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