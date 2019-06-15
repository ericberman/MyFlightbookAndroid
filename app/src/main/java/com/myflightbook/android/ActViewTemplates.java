/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2019 MyFlightbook, LLC

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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.CustomPropertyTypesSvc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

import Model.MFBConstants;
import Model.MFBUtil;
import Model.PropertyTemplate;
import Model.TemplateGroup;

public class ActViewTemplates extends ListFragment implements OnItemClickListener {
    private enum RowType {DATA_ITEM, HEADER_ITEM}
    private TemplateRowItem[] m_templateRows;
    HashSet<PropertyTemplate> m_activeTemplates = new HashSet<>();
    public static final String ACTIVE_PROPERTYTEMPLATES = "com.myflightbook.android.viewactivetemplates";

    private static class RefreshCPTTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog m_pd = null;
        final AsyncWeakContext<ActViewTemplates> m_ctxt;

        RefreshCPTTask(Context c, ActViewTemplates avt) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, avt);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            CustomPropertyTypesSvc cptSvc = new CustomPropertyTypesSvc();
            cptSvc.GetCustomPropertyTypes(AuthToken.m_szAuthToken, false, m_ctxt.getContext());
            return true;
        }

        protected void onPreExecute() {
            Context c = m_ctxt.getContext();
            if (c != null)
                m_pd = MFBUtil.ShowProgress(c, c.getString(R.string.prgCPT));
        }

        protected void onPostExecute(Boolean b) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            ActViewTemplates avt = this.m_ctxt.getCallingActivity();
            if (avt != null) {
                avt.m_templateRows = null;
                avt.populateList();
            }
        }
    }

    static class TemplateRowItem {

        PropertyTemplate pt = null;
        String title = null;
        RowType rowType = RowType.DATA_ITEM;

        TemplateRowItem(PropertyTemplate obj) {
            pt = obj;
        }

        TemplateRowItem(String szTitle) {
            rowType = RowType.HEADER_ITEM;
            title = szTitle;
        }
    }

    private class TemplateAdapter extends ArrayAdapter<TemplateRowItem> {
        TemplateAdapter(Context c, TemplateRowItem[] rgTemplates) {
            super(c, R.layout.propertytemplateitem, rgTemplates == null ? new TemplateRowItem[0] : rgTemplates);
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            if (m_templateRows == null || m_templateRows.length == 0)
                return RowType.DATA_ITEM.ordinal();

            return m_templateRows[position].rowType.ordinal();
        }

        @Override
        public
        @NonNull
        View getView(int position, @Nullable View v, @NonNull ViewGroup parent) {
            RowType rt = RowType.values()[getItemViewType(position)];

            if (v == null) {
                LayoutInflater vi = (LayoutInflater) Objects.requireNonNull(getActivity()).getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                int layoutID = (rt == RowType.HEADER_ITEM) ? R.layout.listviewsectionheader : R.layout.propertytemplateitem;
                assert vi != null;
                v = vi.inflate(layoutID, parent, false);
            }

            if (m_templateRows == null || m_templateRows.length == 0)
                return v;

            if (rt == RowType.HEADER_ITEM) {
                TextView tvSectionHeader = v.findViewById(R.id.lblTableRowSectionHeader);
                tvSectionHeader.setText(m_templateRows[position].title);
                return v;
            }

            PropertyTemplate pt = m_templateRows[position].pt;

            TextView tvName = v.findViewById(R.id.txtTemplateName);
            tvName.setText(pt.Name);
            TextView tvDescription = v.findViewById(R.id.txtDescription);
            tvDescription.setText(pt.Description);

            CheckBox ckIsActive = v.findViewById(R.id.ckActiveTemplate);
            ckIsActive.setChecked(ActViewTemplates.this.m_activeTemplates.contains(m_templateRows[position].pt));

            return v;
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.selecttemplate, container, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Intent i = Objects.requireNonNull(getActivity()).getIntent();
        try {
            Bundle b = i.getExtras();
            assert b != null;
            m_activeTemplates = (HashSet<PropertyTemplate>) b.getSerializable(ACTIVE_PROPERTYTEMPLATES);
        }
        catch (ClassCastException ex) {
            Log.e(MFBConstants.LOG_TAG, ex.getMessage());
        }

        SwipeRefreshLayout srl = Objects.requireNonNull(getView()).findViewById(R.id.swiperefresh);
        srl.setOnRefreshListener(() -> {
            srl.setRefreshing(false);
            refresh();
        });

        PropertyTemplate[] rgpt =PropertyTemplate.getSharedTemplates(getActivity().getSharedPreferences(PropertyTemplate.PREF_KEY_TEMPLATES, Activity.MODE_PRIVATE));
        if (rgpt == null || rgpt.length == 0)
            refresh();
    }

    private void refresh() {
        new RefreshCPTTask(getContext(), this).execute();
    }

    public void onResume() {
        super.onResume();
        populateList();
    }

    private TemplateRowItem[] refreshRows(Context a) {
        if (m_templateRows == null) {
            ArrayList<TemplateRowItem> al = new ArrayList<>();
            TemplateGroup[] rgtg = TemplateGroup.groupTemplates(PropertyTemplate.getSharedTemplates(a.getSharedPreferences(PropertyTemplate.PREF_KEY_TEMPLATES, Activity.MODE_PRIVATE)));

            for (TemplateGroup tg : rgtg) {
                al.add(new TemplateRowItem(tg.groupDisplayName));
                for (PropertyTemplate pt : tg.templates)
                    al.add(new TemplateRowItem(pt));
            }
            m_templateRows = al.toArray(new TemplateRowItem[0]);
        }

        return m_templateRows;
    }

    private void populateList() {
        Activity a = getActivity();
        TemplateAdapter ta = new TemplateAdapter(a, refreshRows(a));
        setListAdapter(ta);
        getListView().setOnItemClickListener(this);
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (m_templateRows == null || position < 0 || position >= m_templateRows.length || m_templateRows[position].rowType == RowType.HEADER_ITEM)
            return;

        PropertyTemplate pt = m_templateRows[position].pt;
        if (m_activeTemplates.contains(pt))
            m_activeTemplates.remove(pt);
        else
            m_activeTemplates.add(pt);

        CheckBox ck = view.findViewById(R.id.ckActiveTemplate);
        ck.setChecked(m_activeTemplates.contains(pt));
    }
}
