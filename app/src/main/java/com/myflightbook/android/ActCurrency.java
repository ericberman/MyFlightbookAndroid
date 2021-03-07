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
package com.myflightbook.android;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TableRow.LayoutParams;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.CurrencySvc;
import com.myflightbook.android.WebServices.MFBSoap;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import Model.CurrencyStatusItem;
import Model.MFBConstants;
import Model.MFBUtil;
import Model.PackAndGo;
import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ActCurrency extends ActMFBForm implements MFBMain.Invalidatable {
    private static boolean fNeedsRefresh = true;

    public static void SetNeedsRefresh(boolean f) {
        fNeedsRefresh = f;
    }

    private static CurrencyStatusItem[] m_rgcsi = null;

    private static class RefreshCurrency extends AsyncTask<Void, Void, MFBSoap> {
        private ProgressDialog m_pd = null;
        Object m_Result = null;
        final AsyncWeakContext<ActCurrency> m_ctxt;

        RefreshCurrency(Context c, ActCurrency ac) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, ac);
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            CurrencySvc cs = new CurrencySvc();
            m_Result = cs.CurrencyForUser(AuthToken.m_szAuthToken, m_ctxt.getContext());
            return cs;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getContext(), m_ctxt.getContext().getString(R.string.prgCurrency));
        }

        protected void onPostExecute(MFBSoap svc) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            ActCurrency ac = m_ctxt.getCallingActivity();
            if (ac == null || !ac.isAdded() || ac.isDetached() || ac.getActivity() == null)
                return;

            m_rgcsi = (CurrencyStatusItem[]) m_Result;

            if (m_rgcsi == null || svc.getLastError().length() > 0) {
                MFBUtil.Alert(ac, ac.getString(R.string.txtError), svc.getLastError());
            } else {
                SetNeedsRefresh(false);
                PackAndGo p = new PackAndGo(m_ctxt.getContext());
                p.updateCurrency(m_rgcsi);
                ac.BindTable();
            }
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.currency, container, false);
    }

    @Override
    public void onViewCreated (@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView tvDisclaimer = (TextView) findViewById(R.id.lnkCurrencyDisclaimer);
        tvDisclaimer.setOnClickListener((v) -> ActWebView.ViewURL(getActivity(), String.format(Locale.US, "https://%s/logbook/public/CurrencyDisclaimer.aspx?naked=1&%s", MFBConstants.szIP, MFBConstants.NightParam(getContext()))));

        MFBMain.registerNotifyDataChange(this);
        MFBMain.registerNotifyResetAll(this);

        SwipeRefreshLayout srl = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        if (srl != null) {
            srl.setOnRefreshListener(() -> {
                srl.setRefreshing(false);
                Refresh(true);
            });
        }
    }

    private void RedirectTo(String szDest) {
        ActWebView.ViewURL(getActivity(), MFBConstants.AuthRedirWithParams("d=" + szDest, getContext()));
    }

    private void BindTable() {
        TableLayout tl = (TableLayout) findViewById(R.id.tblCurrency);
        if (tl == null)
            throw new NullPointerException("tl is null in BindTable (ActCurrency)!");
        tl.removeAllViews();
        LayoutInflater l = requireActivity().getLayoutInflater();

        if (m_rgcsi == null)
            return;

        for (CurrencyStatusItem csi : m_rgcsi) {
            try {
                // TableRow tr = new TableRow(this);

                TableRow tr = (TableRow) l.inflate(R.layout.currencyrow, tl, false);
                TextView tvAttribute = tr.findViewById(R.id.txtCsiAttribute);
                TextView tvValue = tr.findViewById(R.id.txtCsiValue);
                TextView tvDiscrepancy = tr.findViewById(R.id.txtCsiDiscrepancy);

                tvAttribute.setText(csi.Attribute);
                tvValue.setText(csi.Value);
                tvDiscrepancy.setText(csi.Discrepancy);
                if (csi.Discrepancy.length() == 0)
                    tvDiscrepancy.setVisibility(View.GONE);

                if (csi.Status.compareTo("NotCurrent") == 0) {
                    tvValue.setTextColor(Color.RED);
                    tvValue.setTypeface(tvValue.getTypeface(), Typeface.BOLD);
                } else if (csi.Status.compareTo("GettingClose") == 0) {
                    tvValue.setTextColor(Color.argb(255, 0, 128, 255));
                    tvValue.setTypeface(tvValue.getTypeface(), Typeface.BOLD);
                } else if (csi.Status.compareTo("NoDate") == 0) {
                    tvValue.setTypeface(tvValue.getTypeface(), Typeface.BOLD);
                }
                else
                    tvValue.setTextColor(Color.argb(255, 0, 128, 0));

                tl.addView(tr, new TableLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

                tr.setOnClickListener(v -> {
                    if (!MFBSoap.IsOnline(getContext()))
                        return;

                    switch (csi.CurrencyGroup) {
                        default:
                        case None:
                            break;
                        case Aircraft: {
                            Intent i = new Intent(getActivity(), EditAircraftActivity.class);
                            i.putExtra(ActEditAircraft.AIRCRAFTID, csi.AssociatedResourceID);
                            startActivity(i);
                        }
                            break;
                        case Medical:
                            RedirectTo("MEDICAL");
                            break;
                        case Deadline:
                            RedirectTo("DEADLINE");
                            break;
                        case AircraftDeadline:
                            RedirectTo(String.format(Locale.US, "AIRCRAFTEDIT&id=%d", csi.AssociatedResourceID));
                            break;
                        case Certificates:
                            RedirectTo("CERTIFICATES");
                            break;
                        case FlightReview:
                            RedirectTo("FLIGHTREVIEW");
                            break;
                        case FlightExperience:
                            if (csi.Query != null) {
                                Intent i = new Intent(getActivity(), RecentFlightsActivity.class);
                                Bundle b = new Bundle();
                                b.putSerializable(ActFlightQuery.QUERY_TO_EDIT, csi.Query);
                                i.putExtras(b);
                                startActivity(i);
                            }
                            break;
                        case CustomCurrency:
                            if (csi.Query == null)
                                RedirectTo("CUSTOMCURRENCY");
                            else {
                                Intent i = new Intent(getActivity(), RecentFlightsActivity.class);
                                Bundle b = new Bundle();
                                b.putSerializable(ActFlightQuery.QUERY_TO_EDIT, csi.Query);
                                i.putExtras(b);
                                startActivity(i);
                            }
                            break;
                    }
                });
            } catch (NullPointerException ex) { // should never happen.
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex));
            }
        }
    }

    private void Refresh(Boolean fForce) {
        if (AuthToken.FIsValid() && (fForce || fNeedsRefresh || m_rgcsi == null)) {
            if (MFBSoap.IsOnline(getContext())) {
                RefreshCurrency ts = new RefreshCurrency(getActivity(), this);
                ts.execute();
            } else {
                PackAndGo p = new PackAndGo(getContext());
                Date dt = p.lastCurrencyPackDate();
                if (dt != null) {
                    m_rgcsi = p.cachedCurrency();
                    SetNeedsRefresh(false);
                    BindTable();
                    MFBUtil.Alert(getContext(), getString(R.string.packAndGoOffline), String.format(Locale.getDefault(), getString(R.string.packAndGoUsingCached), DateFormat.getDateInstance().format(dt)));
                }
                else
                    MFBUtil.Alert(getContext(), getString(R.string.txtError), getString(R.string.errNoInternet));
            }
        } else
            BindTable();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.currencymenu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (item.getItemId() == R.id.menuRefresh) {
            Refresh(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onResume() {
        Refresh(fNeedsRefresh);
        super.onResume();
    }

    public void invalidate() {
        SetNeedsRefresh(true);
    }

}
