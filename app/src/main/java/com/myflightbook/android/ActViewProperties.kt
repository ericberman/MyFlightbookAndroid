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
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.SparseArray
import android.view.*
import android.widget.BaseExpandableListAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.CustomPropertyTypesSvc
import com.myflightbook.android.webservices.CustomPropertyTypesSvc.Companion.cachedPropertyTypes
import com.myflightbook.android.webservices.FlightPropertiesSvc
import model.*
import model.DecimalEdit.CrossFillDelegate
import model.FlightProperty.Companion.crossProduct
import model.FlightProperty.Companion.distillList
import model.FlightProperty.Companion.fromDB
import model.FlightProperty.Companion.refreshPropCache
import model.FlightProperty.Companion.rewritePropertiesForFlight
import model.MFBUtil.showProgress
import java.util.*


class ActViewProperties : FixedExpandableListActivity(), PropertyEdit.PropertyListener,
    CrossFillDelegate {
    private var mrgfpIn = arrayOf<FlightProperty>()
    private var mRgfpall: Array<FlightProperty>? = null
    private var mRgcpt: Array<CustomPropertyType>? = null
    private var mRgexpandedgroups: BooleanArray? = null
    private var mIdflight: Long = -1
    private var mIdexistingid = 0
    private var mXfillvalue = 0.0
    private var mXfilltachstart = 0.0

    private class RefreshCPTTask(c: Context?, avp: ActViewProperties) :
        AsyncTask<Void?, Void?, Boolean>() {
        private var mPd: ProgressDialog? = null
        var fAllowCache = true
        var mRgcpt: Array<CustomPropertyType> = arrayOf()
        val mCtxt: AsyncWeakContext<ActViewProperties> = AsyncWeakContext(c, avp)
        override fun doInBackground(vararg params: Void?): Boolean {
            val cptSvc = CustomPropertyTypesSvc()
            mRgcpt =
                cptSvc.getCustomPropertyTypes(AuthToken.m_szAuthToken, fAllowCache, mCtxt.context!!)
            return mRgcpt.isNotEmpty()
        }

        override fun onPreExecute() {
            val c = mCtxt.context
            if (c != null) mPd = showProgress(c, c.getString(R.string.prgCPT))
        }

        override fun onPostExecute(b: Boolean) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            val avp = mCtxt.callingActivity ?: return
            avp.mRgcpt = mRgcpt
            if (b) {
                // Refresh the CPT's for each item in the full array
                if (avp.mRgfpall != null) {
                    refreshPropCache()
                    for (fp in avp.mRgfpall!!) fp.refreshPropType()
                }
                avp.populateList()
            }
        }

    }

    private class DeletePropertyTask(
        c: Context?,
        avp: ActViewProperties,
        idExisting: Int
    ) : AsyncTask<Void?, Void?, Boolean>() {
        private var mPd: ProgressDialog? = null
        var propId = 0
        val mIdexistingid: Int = idExisting
        val mCtxt: AsyncWeakContext<ActViewProperties> = AsyncWeakContext(c, avp)
        override fun doInBackground(vararg params: Void?): Boolean {
            val fpsvc = FlightPropertiesSvc()
            fpsvc.deletePropertyForFlight(
                AuthToken.m_szAuthToken,
                mIdexistingid,
                propId,
                mCtxt.context
            )
            return true
        }

        override fun onPreExecute() {
            val c = mCtxt.context
            if (c != null) mPd = showProgress(c, c.getString(R.string.prgDeleteProp))
        }

        override fun onPostExecute(b: Boolean) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            val avp = mCtxt.callingActivity ?: return

            // Now recreate m_rgPropIn without the specfied property.
            val alNew = ArrayList<FlightProperty>()
            for (fp in avp.mrgfpIn) if (fp.idProp != propId) alNew.add(fp)
            avp.mrgfpIn = alNew.toTypedArray()
        }

    }

    private inner class ExpandablePropertyListAdapter(
        val mContext: Context?,
        val mGroups: ArrayList<String>?,
        val mChildren: ArrayList<ArrayList<FlightProperty>?>?
    ) : BaseExpandableListAdapter() {
        private val mCachedviews: SparseArray<View> = SparseArray()
        override fun getGroupCount(): Int {
            assert(mGroups != null)
            return mGroups!!.size
        }

        override fun getChildrenCount(groupPos: Int): Int {
            assert(mChildren != null)
            return mChildren!![groupPos]!!.size
        }

        override fun getGroup(i: Int): Any {
            assert(mGroups != null)
            return mGroups!![i]
        }

        override fun getChild(groupPos: Int, childPos: Int): Any {
            assert(mChildren != null)
            return mChildren!![groupPos]!![childPos]
        }

        override fun getGroupId(groupPos: Int): Long {
            return groupPos.toLong()
        }

        override fun getChildId(groupPos: Int, childPos: Int): Long {
            assert(mChildren != null)
            //            return childPos;
            return mChildren!![groupPos]!![childPos].idPropType.toLong()
        }

        override fun hasStableIds(): Boolean {
            return false
        }

        override fun getGroupView(
            groupPosition: Int,
            isExpanded: Boolean,
            convertViewIn: View?,
            parent: ViewGroup
        ): View {
            assert(mGroups != null)

            var convertView = convertViewIn
            if (convertView == null) {
                assert(mContext != null)
                val infalInflater =
                    (mContext!!.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                convertView = infalInflater.inflate(R.layout.grouprow, parent, false)
            }

            val tv = convertView?.findViewById<TextView>(R.id.propertyGroup)
            tv?.text = mGroups!![groupPosition]
            return convertView!!
        }

        override fun getChildView(
            groupPosition: Int, childPosition: Int, isLastChild: Boolean,
            convertViewIn: View?, parent: ViewGroup
        ): View {
            val fp = getChild(groupPosition, childPosition) as FlightProperty

            // ignore passed-in value of convert view; keep these all around all the time.
            var convertView = mCachedviews[fp.getCustomPropertyType()!!.idPropType]
            if (convertView == null) {
                assert(mContext != null)
                val infalInflater =
                    (mContext!!.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                convertView = infalInflater.inflate(R.layout.cptitem, parent, false)
                mCachedviews.put(fp.getCustomPropertyType()!!.idPropType, convertView)
            }
            val pe: PropertyEdit = convertView.findViewById(R.id.propEdit)

            // only init if it's not already set up - this avoids focus back-and-forth with edittext
            val fpExisting = pe.flightProperty
            if (fpExisting == null || fpExisting.idPropType != fp.idPropType) pe.initForProperty(
                fp,
                fp.idPropType,
                this@ActViewProperties,
                if (fp.idPropType == CustomPropertyType.idPropTypeTachStart) (
                        object: CrossFillDelegate {
                            override fun crossFillRequested(sender: DecimalEdit?) {
                                if (mXfilltachstart > 0 && sender != null) sender.doubleValue = mXfilltachstart
                            }
                        }) else this@ActViewProperties)
            return convertView
        }

        override fun isChildSelectable(i: Int, i1: Int): Boolean {
            return false
        }

        override fun onGroupExpanded(groupPosition: Int) {
            super.onGroupExpanded(groupPosition)
            if (mRgexpandedgroups != null) mRgexpandedgroups!![groupPosition] = true
        }

        override fun onGroupCollapsed(groupPosition: Int) {
            super.onGroupCollapsed(groupPosition)
            if (mRgexpandedgroups != null) mRgexpandedgroups!![groupPosition] = false
        }

    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.expandablelist)
        val tvSearch = findViewById<TextView>(R.id.txtSearchProp)
        tvSearch.setHint(R.string.hintSearchProperties)
        tvSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                populateList()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        val i = intent
        mIdflight = i.getLongExtra(ActNewFlight.PROPSFORFLIGHTID, -1)
        if (mIdflight >= 0) {
            // initialize the flightprops from the db
            mrgfpIn = fromDB(mIdflight)
        }
        mIdexistingid = i.getIntExtra(ActNewFlight.PROPSFORFLIGHTEXISTINGID, 0)
        mXfillvalue = i.getDoubleExtra(ActNewFlight.PROPSFORFLIGHTCROSSFILLVALUE, 0.0)
        mXfilltachstart = i.getDoubleExtra(ActNewFlight.TACHFORCROSSFILLVALUE, 0.0)
        val cptSvc = CustomPropertyTypesSvc()
        if (cptSvc.getCacheStatus() === DBCache.DBCacheStatus.VALID) {
            mRgcpt = cachedPropertyTypes
            populateList()
        } else {
            val rt = RefreshCPTTask(this, this)
            rt.execute()
        }
        val srl = findViewById<SwipeRefreshLayout>(R.id.swiperefresh)
        srl?.setOnRefreshListener {
            srl.isRefreshing = false
            refreshProps()
        }
    }

    public override fun onPause() {
        super.onPause()
        if (currentFocus != null) currentFocus!!.clearFocus() // force any in-progress edit to commit, particularly for properties.
        updateProps()
    }

    private fun updateProps() {
        val rgfpUpdated = distillList(mRgfpall)
        rewritePropertiesForFlight(mIdflight, rgfpUpdated)
    }

    private fun containsWords(szTargetIn: String, rgTerms: Array<String>): Boolean {
        var szTarget = szTargetIn
        szTarget = szTarget.uppercase(Locale.getDefault())
        for (s in rgTerms) {
            if (s.isNotEmpty() && !szTarget.contains(s)) return false
        }
        return true
    }

    private fun populateList() {
        // get the cross product of property types with existing properties
        if (mRgcpt == null) mRgcpt = cachedPropertyTypes // try to avoid passing null
        if (mRgfpall == null) mRgfpall = crossProduct(mrgfpIn, mRgcpt)

        // This maps the headers to the individual sub-lists.
        val headers = HashMap<String, String>()
        val childrenMaps = HashMap<String, ArrayList<FlightProperty>?>()

        // Keep a list of the keys in order
        val alKeys = ArrayList<String>()
        var szKeyLast: String? = ""
        val szRestrict =
            (findViewById<View>(R.id.txtSearchProp) as EditText).text.toString().uppercase(
                Locale.getDefault()
            )
        val rgTerms = szRestrict.split("\\s+".toRegex()).toTypedArray()

        // slice and dice into headers/first names
        for (fp in mRgfpall!!) {
            if (!containsWords(fp.labelString(), rgTerms)) continue

            // get the section for this property
            val szKey =
                if (fp.getCustomPropertyType()!!.isFavorite) getString(R.string.lblPreviouslyUsed) else fp.labelString()
                    .substring(0, 1).uppercase(
                        Locale.getDefault()
                    )
            if (szKey.compareTo(szKeyLast!!) != 0) {
                alKeys.add(szKey)
                szKeyLast = szKey
            }
            if (!headers.containsKey(szKey)) headers[szKey] = szKey

            // Get the array-list for that key, creating it if necessary
            val alProps: ArrayList<FlightProperty>? = if (childrenMaps.containsKey(szKey)) childrenMaps[szKey] else ArrayList()
            assert(alProps != null)
            alProps!!.add(fp)
            childrenMaps[szKey] = alProps
        }

        // put the above into arrayLists, but in the order that the keys were encountered.  .values() is an undefined order.
        val headerList = ArrayList<String>()
        val childrenList = ArrayList<ArrayList<FlightProperty>?>()
        for (s in alKeys) {
            headerList.add(headers[s]!!)
            childrenList.add(childrenMaps[s])
        }
        if (mRgexpandedgroups == null)
            mRgexpandedgroups = BooleanArray(alKeys.size)
        else if (mRgexpandedgroups!!.size != alKeys.size) {
            mRgexpandedgroups = BooleanArray(alKeys.size)
            if (mRgexpandedgroups!!.size <= 5) // autoexpand if fewer than 5 groups.
                Arrays.fill(mRgexpandedgroups!!, true)
        }
        val mAdapter = ExpandablePropertyListAdapter(this, headerList, childrenList)
        setListAdapter(mAdapter)
        for (i in mRgexpandedgroups!!.indices) if (mRgexpandedgroups!![i]) this.expandableListView!!.expandGroup(i)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.propertylistmenu, menu)
        return true
    }

    private fun refreshProps() {
        updateProps() // preserve current user edits
        val rt = RefreshCPTTask(this, this)
        rt.fAllowCache = false
        rt.execute()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuBackToFlight -> {
                updateProps()
                finish()
            }
            R.id.menuRefreshProperties -> refreshProps()
            else -> return super.onOptionsItemSelected(
                item
            )
        }
        return true
    }

    private fun deleteDefaultedProperty(fp: FlightProperty) {
        for (f in mrgfpIn) if (f.idPropType == fp.idPropType && f.idProp > 0) {
            val dpt = DeletePropertyTask(this, this, mIdexistingid)
            dpt.propId = f.idProp
            dpt.execute()
        }
    }

    //region Property update delegates
    override fun updateProperty(id: Int, fp: FlightProperty) {
        if (mIdexistingid > 0 && fp.isDefaultValue()) deleteDefaultedProperty(fp)
    }

    override fun dateOfFlightShouldReset(dt: Date) {}

    //region DecimalEdit cross-fill
    override fun crossFillRequested(sender: DecimalEdit?) {
        sender!!.doubleValue = mXfillvalue
    } //endregion
}