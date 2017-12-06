/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017 MyFlightbook, LLC

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

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.MFBSoap;
import com.myflightbook.android.WebServices.TotalsSvc;

import java.text.DecimalFormat;
import java.util.Locale;

import Model.DecimalEdit;
import Model.DecimalEdit.EditMode;
import Model.FlightQuery;
import Model.MFBConstants;
import Model.MFBUtil;
import Model.Totals;

public class ActTotals extends ListFragment implements MFBMain.Invalidatable, OnItemClickListener {
    private static boolean fNeedsRefresh = true;
    private static Totals[] mRgti = null;

    public static void SetNeedsRefresh(boolean f) {
        fNeedsRefresh = f;
    }

    @SuppressLint("StaticFieldLeak")
    private class SoapTask extends AsyncTask<Void, Void, MFBSoap> {
        private Context m_Context = null;
        private ProgressDialog m_pd = null;
        Object m_Result = null;

        SoapTask(Context c) {
            super();
            m_Context = c;
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            TotalsSvc ts = new TotalsSvc();
            m_Result = ts.TotalsForUser(AuthToken.m_szAuthToken, m_Context);
            return ts;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_Context, m_Context.getString(R.string.prgTotals));
        }

        protected void onPostExecute(MFBSoap svc) {
            if (!isAdded() || getActivity().isFinishing())
                return;

            Totals[] rgti = (Totals[]) m_Result;

            if (rgti == null || svc.getLastError().length() > 0) {
                MFBUtil.Alert(m_Context, getString(R.string.txtError), svc.getLastError());
            } else {
                SetNeedsRefresh(false);
                mRgti = rgti;
                ActTotals.this.BindTable();
            }
            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.totalslist, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        MFBMain.registerNotifyDataChange(this);
        MFBMain.registerNotifyResetAll(this);
    }

    public void onDestroy() {
        MFBMain.unregisterNotify(this);
        super.onDestroy();
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mRgti == null || position < 0 || position >= mRgti.length)
            return;

        FlightQuery fq = mRgti[position].Query;
        if (fq == null)
            return;

        // get any airport aliases
        Intent i = new Intent(getActivity(), RecentFlightsActivity.class);
        Bundle b = new Bundle();
        b.putSerializable(RecentFlightsActivity.REQUEST_FLIGHT_QUERY, fq);
        i.putExtras(b);
        startActivity(i);
    }

    private class TotalsAdapter extends ArrayAdapter<Totals> {
        TotalsAdapter(Context c, int rid,
                      Totals[] rgti) {
            super(c, rid, rgti);
        }

        @Override
        public
        @NonNull
        View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                assert vi != null;
                v = vi.inflate(R.layout.totalsitem, parent, false);
            }

            Totals ti = this.getItem(position);
            if (ti == null)
                throw new NullPointerException("Empty totals item in getView in ActTotals");

            EditMode em = DecimalEdit.DefaultHHMM ? EditMode.HHMM : EditMode.DECIMAL;

            TextView tvDescription = v.findViewById(R.id.txtTotDescription);
            TextView tvSubDesc = v.findViewById(R.id.txtTotSubDescription);
            TextView tvValue = v.findViewById(R.id.txtTotValue);

            tvDescription.setText(ti.Description);
            tvSubDesc.setText(ti.SubDescription);
            switch (ti.NumericType) {
                case Integer:
                    tvValue.setText(String.format(Locale.getDefault(), "%d", (int) ti.Value));
                    break;
                case Time:
                    tvValue.setText(DecimalEdit.StringForMode(ti.Value, em));
                    break;
                case Decimal:
                    tvValue.setText(String.format(Locale.getDefault(), "%.2f", ti.Value));
                    break;
                case Currency:
                    tvValue.setText(DecimalFormat.getCurrencyInstance(Locale.getDefault()).format(ti.Value));
                    break;
            }

            if (ti.SubDescription.length() == 0)
                tvSubDesc.setVisibility(View.GONE);
            else
                tvSubDesc.setVisibility(View.VISIBLE);

            return v;
        }
    }

    private void BindTable() {
        View v = getView();
        if (v == null)
            throw new NullPointerException("getView returned null in BindTable in ActTotals");
        TextView tv = v.findViewById(R.id.txtFlightQueryStatus);
        tv.setText(getString(ActFlightQuery.GetCurrentQuery().HasCriteria() ? R.string.fqStatusNotAllflights : R.string.fqStatusAllFlights));

        if (mRgti == null)
            mRgti = new Totals[0];
        TotalsAdapter ta = new TotalsAdapter(getActivity(), R.layout.totalsitem, mRgti);
        setListAdapter(ta);
        getListView().setOnItemClickListener(this);
    }

    private void Refresh(Boolean fForce) {
        if (AuthToken.FIsValid() && (fForce || fNeedsRefresh || mRgti == null)) {
            SoapTask st = new SoapTask(getActivity());
            st.execute();
        } else
            BindTable();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.totalsmenu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menuRefresh:
                Refresh(true);
                return true;
            case R.id.findFlights:
                Intent i = new Intent(getActivity(), FlightQueryActivity.class);
                startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onResume() {
        Refresh(false);
        super.onResume();
    }

    public void invalidate() {
        SetNeedsRefresh(true);
    }
}
