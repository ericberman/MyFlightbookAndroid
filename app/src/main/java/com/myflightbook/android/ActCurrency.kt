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

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.myflightbook.android.MFBMain.Invalidatable
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.AuthToken.Companion.isValid
import com.myflightbook.android.webservices.CurrencySvc
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import kotlinx.coroutines.launch
import model.CurrencyStatusItem
import model.CurrencyStatusItem.CurrencyGroups
import model.MFBConstants
import model.MFBUtil
import model.PackAndGo
import java.text.DateFormat
import java.util.*

class ActCurrency : ActMFBForm(), Invalidatable {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.currency, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tvDisclaimer = findViewById(R.id.lnkCurrencyDisclaimer) as TextView
        tvDisclaimer.setOnClickListener {
            ActWebView.viewURL(
                requireActivity(), String.format(
                    Locale.US,
                    "https://%s/logbook/public/CurrencyDisclaimer.aspx?naked=1&%s",
                    MFBConstants.szIP,
                    MFBConstants.nightParam(
                        context
                    )
                )
            )
        }
        MFBMain.registerNotifyDataChange(this)
        MFBMain.registerNotifyResetAll(this)
        val srl = findViewById(R.id.swiperefresh) as SwipeRefreshLayout
        srl.setOnRefreshListener {
            srl.isRefreshing = false
            refresh(true)
        }
    }

    private fun redirectTo(szDest: String) {
        ActWebView.viewURL(requireActivity(), MFBConstants.authRedirWithParams("d=$szDest", context))
    }

    private fun bindTable() {
        val tl = findViewById(R.id.tblCurrency) as TableLayout
        tl.removeAllViews()
        val l = requireActivity().layoutInflater
        for (csi in mRgcsi) {
            try {
                // TableRow tr = new TableRow(this);
                val tr = l.inflate(R.layout.currencyrow, tl, false) as TableRow
                val tvAttribute = tr.findViewById<TextView>(R.id.txtCsiAttribute)
                val tvValue = tr.findViewById<TextView>(R.id.txtCsiValue)
                val tvDiscrepancy = tr.findViewById<TextView>(R.id.txtCsiDiscrepancy)
                tvAttribute.text = csi.attribute
                tvValue.text = csi.value
                tvDiscrepancy.text = csi.discrepancy
                if (csi.discrepancy.isEmpty()) tvDiscrepancy.visibility = View.GONE
                when {
                    csi.status.compareTo("NotCurrent") == 0 -> {
                        tvValue.setTextColor(Color.RED)
                        tvValue.setTypeface(tvValue.typeface, Typeface.BOLD)
                    }
                    csi.status.compareTo("GettingClose") == 0 -> {
                        tvValue.setTextColor(Color.argb(255, 0, 128, 255))
                        tvValue.setTypeface(tvValue.typeface, Typeface.BOLD)
                    }
                    csi.status.compareTo("NoDate") == 0 -> {
                        tvValue.setTextColor(requireContext().getColor(R.color.textColorPrimary))
                        tvValue.setTypeface(tvValue.typeface, Typeface.BOLD)
                    }
                    else -> tvValue.setTextColor(requireContext().getColor(R.color.currencyGreen))
                }
                tl.addView(
                    tr,
                    TableLayout.LayoutParams(
                        TableRow.LayoutParams.MATCH_PARENT,
                        TableRow.LayoutParams.WRAP_CONTENT
                    )
                )
                tr.setOnClickListener {
                    if (!isOnline(context)) return@setOnClickListener
                    when (csi.currencyGroup) {
                        CurrencyGroups.None -> {}
                        CurrencyGroups.Aircraft -> {
                            val i = Intent(activity, EditAircraftActivity::class.java)
                            i.putExtra(
                                ActEditAircraft.AIRCRAFTID,
                                csi.associatedResourceID
                            )
                            startActivity(i)
                        }
                        CurrencyGroups.Medical -> redirectTo("MEDICAL")
                        CurrencyGroups.Deadline -> redirectTo("DEADLINE")
                        CurrencyGroups.AircraftDeadline -> redirectTo(
                            String.format(
                                Locale.US,
                                "AIRCRAFTEDIT&id=%d",
                                csi.associatedResourceID
                            )
                        )
                        CurrencyGroups.Certificates -> redirectTo("CERTIFICATES")
                        CurrencyGroups.FlightReview -> redirectTo("FLIGHTREVIEW")
                        CurrencyGroups.FlightExperience -> if (csi.query != null) {
                            val i = Intent(activity, RecentFlightsActivity::class.java)
                            val b = Bundle()
                            b.putSerializable(ActFlightQuery.QUERY_TO_EDIT, csi.query)
                            i.putExtras(b)
                            startActivity(i)
                        }
                        CurrencyGroups.CustomCurrency -> if (csi.query == null) redirectTo("CUSTOMCURRENCY") else {
                            val i = Intent(activity, RecentFlightsActivity::class.java)
                            val b = Bundle()
                            b.putSerializable(ActFlightQuery.QUERY_TO_EDIT, csi.query)
                            i.putExtras(b)
                            startActivity(i)
                        }
                    }
                }
            } catch (ex: NullPointerException) { // should never happen.
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex))
            }
        }
    }

    private fun refresh(fForce: Boolean) {
        if (isValid() && (fForce || fNeedsRefresh)) {
            if (isOnline(context)) {
                lifecycleScope.launch {
                doAsync<CurrencySvc, Array<CurrencyStatusItem>?>(
                    requireActivity(),
                    CurrencySvc(),
                    getString(R.string.prgCurrency),
                    {
                        s : CurrencySvc -> s.getCurrencyForUser(AuthToken.m_szAuthToken, requireContext())
                    },
                    {
                        svc: CurrencySvc, result : Array<CurrencyStatusItem>? ->
                            mRgcsi = result ?: arrayOf()
                            if (svc.lastError.isEmpty()) {
                                setNeedsRefresh(false)
                                val p = PackAndGo(requireContext())
                                p.updateCurrency(mRgcsi)
                                bindTable()
                            }
                    }
                )
                }
            } else {
                val p = PackAndGo(requireContext())
                val dt = p.lastCurrencyPackDate()
                if (dt != null) {
                    mRgcsi = p.cachedCurrency() ?: arrayOf()
                    setNeedsRefresh(false)
                    bindTable()
                    MFBUtil.alert(
                        context, getString(R.string.packAndGoOffline), String.format(
                            Locale.getDefault(),
                            getString(R.string.packAndGoUsingCached),
                            DateFormat.getDateInstance().format(dt)
                        )
                    )
                } else MFBUtil.alert(
                    context,
                    getString(R.string.txtError),
                    getString(R.string.errNoInternet)
                )
            }
        } else bindTable()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.currencymenu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        if (item.itemId == R.id.menuRefresh) {
            refresh(true)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        refresh(fNeedsRefresh)
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

        private var mRgcsi: Array<CurrencyStatusItem> = arrayOf()
    }
}