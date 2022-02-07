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

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ExpandableListView
import android.widget.SimpleExpandableListAdapter
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.myflightbook.android.webservices.MFBSoap
import com.myflightbook.android.webservices.MakesandModelsSvc
import model.MFBConstants
import model.MFBUtil.alert
import model.MFBUtil.showProgress
import kotlin.collections.HashMap
import java.util.*

class ActSelectMake : FixedExpandableListActivity() {
    private var mModelID = 0

    private class GetMakesTask(c: Context?, asm: ActSelectMake?) :
        AsyncTask<Void?, Void?, MFBSoap>() {
        private var mPd: ProgressDialog? = null
        private val mCtxt: AsyncWeakContext<ActSelectMake> = AsyncWeakContext(c, asm)
        override fun doInBackground(vararg params: Void?): MFBSoap {
            val mms = MakesandModelsSvc()
            val rgmm = mms.getMakesAndModels(mCtxt.context)
            ActNewAircraft.AvailableMakesAndModels = rgmm
            return mms
        }

        override fun onPreExecute() {
            mPd = showProgress(mCtxt.context, mCtxt.context!!.getString(R.string.prgMakes))
        }

        override fun onPostExecute(svc: MFBSoap) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            val asm = mCtxt.callingActivity
            val c = mCtxt.context
            if (asm == null || c == null) return
            val rgmm = ActNewAircraft.AvailableMakesAndModels
            if (rgmm == null || rgmm.isEmpty()) {
                alert(
                    c,
                    c.getString(R.string.txtError),
                    c.getString(R.string.errCannotRetrieveMakes)
                )
            } else asm.populateList()
        }

    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.selectmake)
        val tvSearch = findViewById<TextView>(R.id.txtSearchProp)
        tvSearch.setHint(R.string.hintSearchModels)
        tvSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                populateList()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        val srl = findViewById<SwipeRefreshLayout>(R.id.swiperefresh)
        srl.setOnRefreshListener {
            srl.isRefreshing = false
            refresh()
        }

        // make the hint for creating make/model a hyperlink
        val txtHint = findViewById<TextView>(R.id.txtAddMakesHint)
        txtHint.text = getString(R.string.lblAddMakes)
        txtHint.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun refresh() {
        GetMakesTask(this, this).execute()
    }

    public override fun onResume() {
        super.onResume()
        val i = intent
        mModelID = i.getIntExtra(ActNewAircraft.MODELFORAIRCRAFT, -1)
        populateList()
    }

    private fun populateList() {
        // This maps the headers to the individual sub-lists.
        val headers = HashMap<String, HashMap<String, String>>()
        val childrenMaps = HashMap<String, ArrayList<HashMap<String, String>>>()

        // Keep a list of the keys in order
        val alKeys = ArrayList<String>()
        var szKeyLast: String? = ""
        var expandedGroupIndex = -1
        val szRestrict =
            (findViewById<View>(R.id.txtSearchProp) as EditText).text.toString().uppercase(Locale.getDefault())
        val rgRestrictStrings = szRestrict.split("\\W").toTypedArray()

        val rgmm = ActNewAircraft.AvailableMakesAndModels

        // slice and dice into headers/first names
        if (rgmm != null) // should never be non-null, but seems to happen occasionally
        {
            for (i in 0 until rgmm.count()) {
                val mm = rgmm[i]
                // reject anything that doesn't match the restriction
                var fIsMatch = true
                for (sz in rgRestrictStrings) {
                    if (sz.isNotEmpty() && !mm.description.uppercase(Locale.getDefault())
                            .contains(sz)
                    ) {
                        fIsMatch = false
                        break
                    }
                }
                if (!fIsMatch) continue

                // get the manufacturer for this property as the grouping key
                val szKey = mm.manufacturer
                if (szKey.compareTo(szKeyLast!!) != 0) {
                    alKeys.add(szKey)
                    szKeyLast = szKey
                }
                val hmGroups: HashMap<String, String> =
                    (if (headers.containsKey(szKey)) headers[szKey]!! else HashMap())
                hmGroups["sectionName"] = szKey
                headers[szKey] = hmGroups

                // Get the array-list for that key, creating it if necessary
                val alProps: ArrayList<HashMap<String, String>> =
                    if (childrenMaps.containsKey(szKey)) childrenMaps[szKey]!! else ArrayList<HashMap<String, String>>()
                val hmProperty = HashMap<String, String>()
                hmProperty["Description"] = mm.description
                hmProperty["Position"] = String.format(Locale.getDefault(), "%d", i)
                alProps.add(hmProperty)
                childrenMaps[szKey] = alProps

                // if this is the selected item, then expand this group index
                if (mm.modelId == mModelID) expandedGroupIndex = alKeys.size - 1
            }
        }
        val mrgExpandedGroups = BooleanArray(alKeys.size)
        if (expandedGroupIndex > 0 && expandedGroupIndex < alKeys.size) mrgExpandedGroups[expandedGroupIndex] =
            true
        if (mrgExpandedGroups.size <= 5) Arrays.fill(mrgExpandedGroups, true)

        // put the above into arrayLists, but in the order that the keys were encountered.  .values() is an undefined order.
        val headerList = ArrayList<HashMap<String, String>>()
        val childrenList = ArrayList<ArrayList<HashMap<String, String>>>()
        for (s in alKeys) {
            headerList.add(headers[s]!!)
            childrenList.add(childrenMaps[s]!!)
        }
        val adapter = SimpleExpandableListAdapter(
            this,
            headerList,
            R.layout.grouprow, arrayOf("sectionName"), intArrayOf(R.id.propertyGroup),
            childrenList,
            R.layout.makemodelitem, arrayOf("Description"), intArrayOf(R.id.txtDescription)
        )
        setListAdapter(adapter)
        for (i in mrgExpandedGroups.indices) if (mrgExpandedGroups[i]) expandableListView!!.expandGroup(
            i
        )
        expandableListView!!.setOnChildClickListener { _: ExpandableListView?, _: View?, groupPosition: Int, childPosition: Int, _: Long ->
            val hmProp = adapter.getChild(groupPosition, childPosition) as HashMap<String, String>
            val position = hmProp["Position"]!!.toInt()
            val i = Intent()
            i.putExtra(ActNewAircraft.MODELFORAIRCRAFT, position)
            this@ActSelectMake.setResult(RESULT_OK, i)
            finish()
            false
        }
    }
}