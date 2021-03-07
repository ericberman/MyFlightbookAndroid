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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
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

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import Model.DecimalEdit;
import Model.DecimalEdit.EditMode;
import Model.FlightQuery;
import Model.MFBConstants;
import Model.MFBUtil;
import Model.PackAndGo;
import Model.Totals;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ActTotals extends ListFragment implements MFBMain.Invalidatable, OnItemClickListener {
    private static boolean fNeedsRefresh = true;
    private TotalsRowItem[] mTotalsRows = null;
    private ActivityResultLauncher<Intent> mQueryLauncher = null;

    public static void SetNeedsRefresh(boolean f) {
        fNeedsRefresh = f;
    }

    private FlightQuery currentQuery = new FlightQuery();

    private enum RowType {DATA_ITEM, HEADER_ITEM}

    static class TotalsRowItem {

        Totals totalItem = null;
        String title = null;
        RowType rowType = RowType.DATA_ITEM;

        TotalsRowItem(Totals obj) {
            totalItem = obj;
        }

        TotalsRowItem(String szTitle) {
            rowType = RowType.HEADER_ITEM;
            title = szTitle;
        }
    }

    private static class RefreshTotals extends AsyncTask<Void, Void, MFBSoap> {
        private ProgressDialog m_pd = null;
        Object m_Result = null;
        private final AsyncWeakContext<ActTotals> m_ctxt;

        RefreshTotals(Context c, ActTotals at) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, at);
        }

        @Override
        protected MFBSoap doInBackground(Void... params) {
            TotalsSvc ts = new TotalsSvc();
            Context c = m_ctxt.getContext();
            ActTotals at = m_ctxt.getCallingActivity();
            if (c != null && at != null)
                m_Result = ts.TotalsForUser(AuthToken.m_szAuthToken, at.currentQuery, c);
            return ts;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(m_ctxt.getContext(), m_ctxt.getContext().getString(R.string.prgTotals));
        }

        protected void onPostExecute(MFBSoap svc) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            ActTotals at = m_ctxt.getCallingActivity();
            if (at == null || !at.isAdded() || at.isDetached() || at.getActivity() == null)
                return;

            ArrayList<ArrayList<Totals>> rgti = Totals.groupTotals((Totals[]) m_Result);

            if (svc.getLastError().length() > 0) {
                MFBUtil.Alert(at, at.getString(R.string.txtError), svc.getLastError());
            } else {
                SetNeedsRefresh(false);
                at.mTotalsRows = at.GroupedTotals(rgti);

                if (!at.currentQuery.HasCriteria()) {
                    PackAndGo p = new PackAndGo(m_ctxt.getContext());
                    p.updateTotals((Totals[]) m_Result);
                }
                at.BindTable();
            }
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.setHasOptionsMenu(true);
        return inflater.inflate(R.layout.totalslist, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated (@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MFBMain.registerNotifyDataChange(this);
        MFBMain.registerNotifyResetAll(this);

        mQueryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        currentQuery = (FlightQuery) Objects.requireNonNull(result.getData()).getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT);
                    }
                });

        Intent i = requireActivity().getIntent();
        if (i != null) {
            Object o = i.getSerializableExtra(ActFlightQuery.QUERY_TO_EDIT);
            if (o != null)
                currentQuery = (FlightQuery) o;
        }

        SwipeRefreshLayout srl = requireView().findViewById(R.id.swiperefresh);
        if (srl != null) {
            srl.setOnRefreshListener(() -> {
                srl.setRefreshing(false);
                Refresh(true);
            });
        }
    }

    public void onDestroy() {
        MFBMain.unregisterNotify(this);
        super.onDestroy();
    }

    TotalsRowItem[] GroupedTotals(ArrayList<ArrayList<Totals>> rgti) {
        ArrayList<TotalsRowItem> arr = new ArrayList<>();
        // set up the rows
        for (ArrayList<Totals> arTotals  : rgti) {
            // add a header row first
            arr.add(new TotalsRowItem(arTotals.get(0).GroupName));

            for (Totals ti : arTotals)
                arr.add(new TotalsRowItem(ti));
        }
        return arr.toArray(new TotalsRowItem[0]);
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mTotalsRows == null || position < 0 || position >= mTotalsRows.length || mTotalsRows[position].rowType == RowType.HEADER_ITEM)
            return;

        // disable click on total when offline.
        if (!MFBSoap.IsOnline(getContext()))
            return;

        FlightQuery fq = mTotalsRows[position].totalItem.Query;
        if (fq == null)
            return;

        Intent i = new Intent(getActivity(), RecentFlightsActivity.class);
        Bundle b = new Bundle();
        b.putSerializable(ActFlightQuery.QUERY_TO_EDIT, fq);
        i.putExtras(b);
        startActivity(i);
    }

    private class TotalsAdapter extends ArrayAdapter<TotalsRowItem> {
        TotalsAdapter(Context c,
                      TotalsRowItem[] rgti) {
            super(c, R.layout.totalsitem, rgti);
        }

        public int getItemViewType(int position) {
            if (mTotalsRows == null || mTotalsRows.length == 0)
                return RowType.DATA_ITEM.ordinal();

            return mTotalsRows[position].rowType.ordinal();
        }

        private RowType checkViewType(View convertView) {
            return convertView.findViewById(R.id.lblTableRowSectionHeader) == null ? RowType.DATA_ITEM : RowType.HEADER_ITEM;
        }

        @Override
        public
        @NonNull
        View getView(int position, View convertView, @NonNull ViewGroup parent) {
            RowType rt = RowType.values()[getItemViewType(position)];
            View v = convertView;
            if (v == null || checkViewType(convertView) != rt) {
                LayoutInflater vi = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                assert vi != null;
                int layoutID = (rt == RowType.HEADER_ITEM) ? R.layout.listviewsectionheader : R.layout.totalsitem;
                v = vi.inflate(layoutID, parent, false);
            }

            if (rt == RowType.HEADER_ITEM) {
                TextView tvSectionHeader = v.findViewById(R.id.lblTableRowSectionHeader);
                tvSectionHeader.setText(mTotalsRows[position].title);
                return v;
            }

            TotalsRowItem tri = this.getItem(position);
            if (tri == null)
                throw new NullPointerException("Empty totalsrowitem in getView in ActTotals");
            Totals ti = tri.totalItem;
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
                    tvValue.setText(DecimalEdit.StringForMode(ti.Value, EditMode.DECIMAL));
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
        tv.setText(getString(currentQuery != null && currentQuery.HasCriteria() ? R.string.fqStatusNotAllflights : R.string.fqStatusAllFlights));

        if (mTotalsRows == null)
            mTotalsRows = new TotalsRowItem[0];
        TotalsAdapter ta = new TotalsAdapter(getActivity(), mTotalsRows);
        setListAdapter(ta);
        getListView().setOnItemClickListener(this);
    }

    private void Refresh(Boolean fForce) {
        if (AuthToken.FIsValid() && (fForce || fNeedsRefresh || mTotalsRows == null)) {
            if (MFBSoap.IsOnline(getContext())) {
                RefreshTotals st = new RefreshTotals(getActivity(), this);
                st.execute();
            }
            else {
                PackAndGo p = new PackAndGo(getContext());
                Date dt = p.lastTotalsPackDate();
                if (dt != null) {
                    mTotalsRows = GroupedTotals(Totals.groupTotals(p.cachedTotals()));
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
        inflater.inflate(R.menu.totalsmenu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menuRefresh)
            Refresh(true);
        else if (id == R.id.findFlights) {
            if (MFBSoap.IsOnline(getContext())) {
                Intent i = new Intent(getActivity(), FlightQueryActivity.class);
                i.putExtra(ActFlightQuery.QUERY_TO_EDIT, currentQuery);
                mQueryLauncher.launch(i);
            } else
                MFBUtil.Alert(getContext(), getString(R.string.txtError), getString(R.string.errNoInternet));
        } else
            return super.onOptionsItemSelected(item);
        return true;
    }

    public void onResume() {
        Refresh(false);
        super.onResume();
    }

    public void invalidate() {
        SetNeedsRefresh(true);
    }
}
