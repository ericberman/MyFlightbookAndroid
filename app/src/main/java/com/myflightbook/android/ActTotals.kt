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
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.ListFragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.myflightbook.android.MFBMain.Invalidatable
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.MFBSoap
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import com.myflightbook.android.webservices.TotalsSvc
import model.*
import model.DecimalEdit.Companion.stringForMode
import model.DecimalEdit.EditMode
import model.MFBUtil.alert
import model.MFBUtil.showProgress
import model.Totals.Companion.groupTotals
import model.Totals.NumType
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.*

class ActTotals : ListFragment(), Invalidatable, OnItemClickListener {
    private var mTotalsRows: Array<TotalsRowItem?>? = null
    private var mQueryLauncher: ActivityResultLauncher<Intent>? = null
    private var currentQuery: FlightQuery? = FlightQuery()

    enum class RowType {
        DATA_ITEM, HEADER_ITEM
    }

    class TotalsRowItem {
        var totalItem: Totals? = null
        var title: String? = null
        var rowType = RowType.DATA_ITEM

        constructor(obj: Totals?) {
            totalItem = obj
        }

        constructor(szTitle: String?) {
            rowType = RowType.HEADER_ITEM
            title = szTitle
        }
    }

    private class RefreshTotals(c: Context?, at: ActTotals) :
        AsyncTask<Void?, Void?, MFBSoap>() {
        private var mPd: ProgressDialog? = null
        var mResult: Array<Totals>? = null
        private val mCtxt: AsyncWeakContext<ActTotals> = AsyncWeakContext(c, at)
        override fun doInBackground(vararg params: Void?): MFBSoap {
            val ts = TotalsSvc()
            val c = mCtxt.context
            val at = mCtxt.callingActivity
            if (c != null && at != null) mResult =
                ts.getTotalsForUser(AuthToken.m_szAuthToken, at.currentQuery, c)
            return ts
        }

        override fun onPreExecute() {
            mPd = showProgress(mCtxt.context, mCtxt.context!!.getString(R.string.prgTotals))
        }

        override fun onPostExecute(svc: MFBSoap) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            val at = mCtxt.callingActivity
            if (at == null || !at.isAdded || at.isDetached || at.activity == null) return
            val rgti = groupTotals(mResult)
            if (svc.lastError.isNotEmpty()) {
                alert(at, at.getString(R.string.txtError), svc.lastError)
            } else {
                setNeedsRefresh(false)
                at.mTotalsRows = at.groupedTotals(rgti)
                if (!at.currentQuery!!.HasCriteria()) {
                    val p = PackAndGo(mCtxt.context!!)
                    p.updateTotals(mResult)
                }
                at.bindTable()
            }
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.totalslist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        MFBMain.registerNotifyDataChange(this)
        MFBMain.registerNotifyResetAll(this)
        mQueryLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                currentQuery = result.data!!.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT) as FlightQuery?
            }
        }
        val i = requireActivity().intent
        if (i != null) {
            val o: Any? = i.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT)
            if (o != null) currentQuery = o as FlightQuery?
        }
        val srl: SwipeRefreshLayout = requireView().findViewById(R.id.swiperefresh)
        srl.setOnRefreshListener {
            srl.isRefreshing = false
            refresh(true)
        }
    }

    override fun onDestroy() {
        MFBMain.unregisterNotify(this)
        super.onDestroy()
    }

    fun groupedTotals(rgti: ArrayList<ArrayList<Totals>>): Array<TotalsRowItem?> {
        val arr = ArrayList<TotalsRowItem>()
        // set up the rows
        for (arTotals in rgti) {
            // add a header row first
            arr.add(TotalsRowItem(arTotals[0].groupName))
            for (ti in arTotals) arr.add(TotalsRowItem(ti))
        }
        return arr.toTypedArray()
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        if (mTotalsRows == null || position < 0 || position >= mTotalsRows!!.size || mTotalsRows!![position]!!.rowType == RowType.HEADER_ITEM) return

        // disable click on total when offline.
        if (!isOnline(context)) return
        val fq = mTotalsRows!![position]!!.totalItem!!.query ?: return
        val i = Intent(activity, RecentFlightsActivity::class.java)
        val b = Bundle()
        b.putSerializable(ActFlightQuery.QUERY_TO_EDIT, fq)
        i.putExtras(b)
        startActivity(i)
    }

    private inner class TotalsAdapter(
        c: Context?,
        rgti: Array<TotalsRowItem?>?
    ) : ArrayAdapter<TotalsRowItem?>(
        c!!, R.layout.totalsitem, rgti!!
    ) {
        override fun getItemViewType(position: Int): Int {
            return if (mTotalsRows == null || mTotalsRows!!.isEmpty()) RowType.DATA_ITEM.ordinal else mTotalsRows!![position]!!.rowType.ordinal
        }

        private fun checkViewType(convertView: View?): RowType {
            return if (convertView!!.findViewById<View?>(R.id.lblTableRowSectionHeader) == null) RowType.DATA_ITEM else RowType.HEADER_ITEM
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rt = RowType.values()[getItemViewType(position)]
            var v = convertView
            if (v == null || checkViewType(convertView) != rt) {
                val vi =
                    (requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                val layoutID =
                    if (rt == RowType.HEADER_ITEM) R.layout.listviewsectionheader else R.layout.totalsitem
                v = vi.inflate(layoutID, parent, false)
            }
            if (rt == RowType.HEADER_ITEM) {
                val tvSectionHeader = v!!.findViewById<TextView>(R.id.lblTableRowSectionHeader)
                tvSectionHeader.text = mTotalsRows!![position]!!.title
                return v
            }
            val tri = getItem(position)
                ?: throw NullPointerException("Empty totalsrowitem in getView in ActTotals")
            val ti = tri.totalItem
                ?: throw NullPointerException("Empty totals item in getView in ActTotals")
            val em = if (DecimalEdit.DefaultHHMM) EditMode.HHMM else EditMode.DECIMAL
            val tvDescription = v!!.findViewById<TextView>(R.id.txtTotDescription)
            val tvSubDesc = v.findViewById<TextView>(R.id.txtTotSubDescription)
            val tvValue = v.findViewById<TextView>(R.id.txtTotValue)
            tvDescription.text = ti.description
            tvSubDesc.text = ti.subDescription
            when (ti.numericType) {
                NumType.Integer -> tvValue.text =
                    String.format(Locale.getDefault(), "%d", ti.value.toInt())
                NumType.Time -> tvValue.text = stringForMode(ti.value, em)
                NumType.Decimal -> tvValue.text = stringForMode(ti.value, EditMode.DECIMAL)
                NumType.Currency -> tvValue.text =
                    DecimalFormat.getCurrencyInstance(Locale.getDefault()).format(ti.value)
            }
            if (ti.subDescription.isEmpty()) tvSubDesc.visibility =
                View.GONE else tvSubDesc.visibility = View.VISIBLE
            return v
        }
    }

    private fun bindTable() {
        val v = view
            ?: throw NullPointerException("getView returned null in BindTable in ActTotals")
        val tv = v.findViewById<TextView>(R.id.txtFlightQueryStatus)
        tv.text =
            getString(if (currentQuery != null && currentQuery!!.HasCriteria()) R.string.fqStatusNotAllflights else R.string.fqStatusAllFlights)
        if (mTotalsRows == null) mTotalsRows = arrayOfNulls(0)
        val ta = TotalsAdapter(activity, mTotalsRows)
        listAdapter = ta
        listView.onItemClickListener = this
    }

    private fun refresh(fForce: Boolean) {
        if (isValid() && (fForce || fNeedsRefresh || mTotalsRows == null)) {
            if (isOnline(context)) {
                val st = RefreshTotals(activity, this)
                st.execute()
            } else {
                val p = PackAndGo(requireContext())
                val dt = p.lastTotalsPackDate()
                if (dt != null) {
                    mTotalsRows = groupedTotals(groupTotals(p.cachedTotals()))
                    setNeedsRefresh(false)
                    bindTable()
                    alert(
                        context,
                        getString(R.string.packAndGoOffline),
                        String.format(
                            Locale.getDefault(),
                            getString(R.string.packAndGoUsingCached),
                            DateFormat.getDateInstance().format(dt)
                        )
                    )
                } else alert(
                    context,
                    getString(R.string.txtError),
                    getString(R.string.errNoInternet)
                )
            }
        } else bindTable()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.totalsmenu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.menuRefresh) refresh(true) else if (id == R.id.findFlights) {
            if (isOnline(context)) {
                val i = Intent(activity, FlightQueryActivity::class.java)
                i.putExtra(ActFlightQuery.QUERY_TO_EDIT, currentQuery)
                mQueryLauncher!!.launch(i)
            } else alert(context, getString(R.string.txtError), getString(R.string.errNoInternet))
        } else return super.onOptionsItemSelected(item)
        return true
    }

    override fun onResume() {
        refresh(false)
        super.onResume()
    }

    override fun invalidate() {
        setNeedsRefresh(true)
    }

    companion object {
        private var fNeedsRefresh = true
        fun setNeedsRefresh(f: Boolean) {
            fNeedsRefresh = f
        }
    }
}