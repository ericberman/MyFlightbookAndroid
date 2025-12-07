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

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.ExpandableListView
import android.widget.SimpleExpandableListAdapter
import android.widget.TextView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.myflightbook.android.MFBMain.Invalidatable
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import com.myflightbook.android.webservices.VisitedAirportSvc
import kotlinx.coroutines.launch
import model.MFBUtil.alert
import model.PackAndGo
import model.VisitedAirport
import model.VisitedAirport.Companion.toRoute
import java.text.DateFormat
import java.util.*

class ActVisitedAirports : ExpandableListFragment(), Invalidatable {
    private fun refreshVisitedAirports() {
        lifecycleScope.launch {
            ActMFBForm.doAsync<VisitedAirportSvc, Array<VisitedAirport>?>(requireActivity(),
                VisitedAirportSvc(),
                getString(R.string.prgVisitedAirports),
                { s -> s.getVisitedAirportsForUser(AuthToken.m_szAuthToken, requireContext()) },
                { _, result ->
                    if (result != null) {
                        visitedAirports = result
                        PackAndGo(requireContext()).updateAirports(visitedAirports)
                        populateList()
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
        return inflater.inflate(R.layout.expandablelist, container, false)
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
                inflater.inflate(R.menu.currencymenu, menu)
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                if (item.itemId == R.id.menuRefresh) {
                    refreshAirports()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        MFBMain.registerNotifyDataChange(this)
        MFBMain.registerNotifyResetAll(this)
        val v = requireView()
        val tvSearch = requireView().findViewById<TextView>(R.id.txtSearchProp)
        tvSearch.setHint(R.string.hintSearchVisitedAirports)
        tvSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                populateList()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        val srl: SwipeRefreshLayout = v.findViewById(R.id.swiperefresh)
        srl.setOnRefreshListener {
            refreshAirports()
        }
    }

    override fun onResume() {
        super.onResume()
        if (visitedAirports == null) refreshAirports() else populateList()
    }

    private fun refreshAirports() {
        if (isOnline(context)) refreshVisitedAirports() else {
            val p = PackAndGo(requireContext())
            val dt = p.lastAirportsPackDate()
            if (dt != null) {
                visitedAirports = p.cachedAirports()
                populateList()
                alert(
                    context,
                    getString(R.string.packAndGoOffline),
                    String.format(
                        Locale.getDefault(),
                        getString(R.string.packAndGoUsingCached),
                        DateFormat.getDateInstance().format(dt)
                    )
                )
            } else alert(context, getString(R.string.txtError), getString(R.string.errNoInternet))
        }
    }

    private fun populateList() {
        if (visitedAirports == null || visitedAirports!!.isEmpty()) return
        val rgap = visitedAirports!!

        // This maps the headers to the individual sub-lists.
        val headers = HashMap<String, HashMap<String, String>>()
        val childrenMaps = HashMap<String, ArrayList<HashMap<String, String>>>()

        // Keep a list of the keys in order
        val alKeys = ArrayList<String>()
        rgap.sort()
        visitedAirports = rgap // update to be sorted.

        // First do "All airports"
        var szKeyLast = getString(R.string.AllAirports)
        alKeys.add(szKeyLast)
        var hmAllAirports = HashMap<String, String>()
        hmAllAirports["sectionName"] = szKeyLast
        headers[szKeyLast] = hmAllAirports
        hmAllAirports = HashMap()
        hmAllAirports["Code"] = ""
        hmAllAirports["Name"] = szKeyLast
        val cVisits = visitedAirports!!.size
        hmAllAirports["Visits"] =
            resources.getQuantityString(R.plurals.uniqueAirportsCount, cVisits, cVisits)
        hmAllAirports["Distance"] = ""
        hmAllAirports["Position"] = "-1"
        val alAllAirports = ArrayList<HashMap<String, String>>()
        alAllAirports.add(hmAllAirports)
        childrenMaps[szKeyLast] = alAllAirports
        val szRestrict =
            (requireView().findViewById<View>(R.id.txtSearchProp) as EditText).text.toString().uppercase(Locale.getDefault())

        // slice and dice into headers/first names
        for (i in visitedAirports!!.indices) {
            val va = visitedAirports!![i]
            val szSearch = String.format(Locale.getDefault(), "%s %s %s %s", va.airport!!.facilityName, va.airport!!.airportID, va.airport!!.country ?: "", va.airport!!.admin1 ?: "").uppercase(Locale.getDefault()).trim()
            if (szRestrict.isNotEmpty() && !szSearch.contains(szRestrict))
                continue

            // get the first letter for this property as the grouping key
            val szKey = va.airport!!.facilityName[0].uppercase(Locale.getDefault())
            if (szKey.compareTo(szKeyLast) != 0) {
                alKeys.add(szKey)
                szKeyLast = szKey
            }
            val hmGroups: HashMap<String, String> =
                (if (headers.containsKey(szKey)) headers[szKey] else HashMap())!!
            hmGroups["sectionName"] = szKey
            headers[szKey] = hmGroups

            // Get the array-list for that key, creating it if necessary
            val alAirports: ArrayList<HashMap<String, String>> = if (childrenMaps.containsKey(szKey)) childrenMaps[szKey]!! else ArrayList()
            val hmProperty = HashMap<String, String>()
            val szDistance = if (va.airport!!.distance > 0) String.format(
                Locale.getDefault(),
                "(%.1fnm) ",
                va.airport!!.distance
            ) else ""
            val szEarliestVisit =
                DateFormat.getDateInstance(DateFormat.SHORT).format(va.earliestDate)
            val szLatestVisit = DateFormat.getDateInstance(DateFormat.SHORT).format(va.latestDate)
            val szVisits = if (va.numberOfVisits > 1) String.format(
                getString(R.string.vaMultiVisit),
                va.numberOfVisits,
                szEarliestVisit,
                szLatestVisit
            ) else String.format(getString(R.string.vaSingleVisit), szEarliestVisit)
            hmProperty["Code"] = String.format("%s - ", va.code)
            hmProperty["Name"] = va.airport!!.facilityName
            hmProperty["Distance"] = szDistance
            hmProperty["Visits"] = szVisits
            hmProperty["Position"] = String.format(Locale.US, "%d", i)
            alAirports.add(hmProperty)
            childrenMaps[szKey] = alAirports
        }

        // put the above into arrayLists, but in the order that the keys were encountered.  .values() is an undefined order.
        val headerList = ArrayList<HashMap<String, String>>()
        val childrenList = ArrayList<ArrayList<HashMap<String, String>>>()
        for (s in alKeys) {
            headerList.add(headers[s]!!)
            childrenList.add(childrenMaps[s]!!)
        }
        val adapter = SimpleExpandableListAdapter(
            activity,
            headerList,
            R.layout.grouprow,
            arrayOf("sectionName"),
            intArrayOf(R.id.propertyGroup),
            childrenList,
            R.layout.visitedairportitem,
            arrayOf("Code", "Name", "Distance", "Visits"),
            intArrayOf(R.id.txtCode, R.id.txtName, R.id.txtDistance, R.id.txtVisits)
        )
        listAdapter = adapter

        // Auto-expand if 5 or fewer groups.
        val mRgexpandedgroups = BooleanArray(alKeys.size)
        if (mRgexpandedgroups.size <= 5) Arrays.fill(mRgexpandedgroups, true)
        for (i in mRgexpandedgroups.indices) if (mRgexpandedgroups[i]) expandableListView!!.expandGroup(
            i
        )
        expandableListView!!.setOnChildClickListener { _: ExpandableListView?, _: View?, groupPosition: Int, childPosition: Int, _: Long ->
            if (!MFBMain.hasMaps() || !isOnline(
                    context
                )
            ) return@setOnChildClickListener false
            val hmProp = adapter.getChild(groupPosition, childPosition) as HashMap<*, *>
            val position = (hmProp["Position"]!! as String).toInt()
            var szRoute = ""
            var szAlias: String? = ""
            if (position < 0 && visitedAirports != null) // all airports
                szRoute = toRoute(visitedAirports!!) else if (position < visitedAirports!!.size) {
                szRoute = visitedAirports!![position].code
                szAlias = visitedAirports!![position].aliases
            }
            val i = Intent(this@ActVisitedAirports.activity, ActFlightMap::class.java)
            i.putExtra(ActFlightMap.ROUTEFORFLIGHT, szRoute)
            i.putExtra(ActFlightMap.EXISTINGFLIGHTID, -1)
            i.putExtra(ActFlightMap.PENDINGFLIGHTID, -1L)
            i.putExtra(ActFlightMap.ALIASES, szAlias)
            startActivity(i)
            false
        }
    }

    override fun invalidate() {
        visitedAirports = null
    }

    companion object {
        private var visitedAirports: Array<VisitedAirport>? = null
    }
}