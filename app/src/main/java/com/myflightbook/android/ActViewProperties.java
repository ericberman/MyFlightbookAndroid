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
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.CustomPropertyTypesSvc;
import com.myflightbook.android.WebServices.FlightPropertiesSvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import Model.CustomPropertyType;
import Model.DBCache;
import Model.DecimalEdit;
import Model.FlightProperty;
import Model.MFBConstants;
import Model.MFBUtil;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ActViewProperties extends FixedExpandableListActivity implements PropertyEdit.PropertyListener, DecimalEdit.CrossFillDelegate {

    private FlightProperty[] m_rgfpIn = new FlightProperty[0];
    private FlightProperty[] m_rgfpAll = null;
    private CustomPropertyType[] m_rgcpt = null;
    private boolean[] m_rgExpandedGroups = null;
    private long m_idFlight = -1;
    private int m_idExistingId = 0;
    private double m_xfillValue = 0.0;
    private double m_xfillTachStart = 0.0;

    private static class RefreshCPTTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog m_pd = null;
        Boolean fAllowCache = true;
        CustomPropertyType[] m_rgcpt;
        final AsyncWeakContext<ActViewProperties> m_ctxt;

        RefreshCPTTask(Context c, ActViewProperties avp) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, avp);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            CustomPropertyTypesSvc cptSvc = new CustomPropertyTypesSvc();
            m_rgcpt = cptSvc.GetCustomPropertyTypes(AuthToken.m_szAuthToken, fAllowCache, m_ctxt.getContext());
            return m_rgcpt != null && m_rgcpt.length > 0;
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

            ActViewProperties avp = m_ctxt.getCallingActivity();
            if (avp == null)
                return;

            avp.m_rgcpt = this.m_rgcpt;
            if (b) {
                // Refresh the CPT's for each item in the full array
                if (avp.m_rgfpAll != null) {
                    FlightProperty.RefreshPropCache();
                    for (FlightProperty fp : avp.m_rgfpAll)
                        fp.RefreshPropType();
                }
                avp.populateList();
            }
        }
    }

    private static class DeletePropertyTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog m_pd = null;
        int propId;
        final int m_idExistingId;
        final AsyncWeakContext<ActViewProperties> m_ctxt;

        DeletePropertyTask(Context c, ActViewProperties avp, int idExisting) {
            super();
            m_ctxt = new AsyncWeakContext<>(c, avp);
            m_idExistingId = idExisting;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            FlightPropertiesSvc fpsvc = new FlightPropertiesSvc();
            fpsvc.DeletePropertyForFlight(AuthToken.m_szAuthToken, m_idExistingId, propId, m_ctxt.getContext());
            return true;
        }

        protected void onPreExecute() {
            Context c = m_ctxt.getContext();
            if (c != null)
                m_pd = MFBUtil.ShowProgress(c, c.getString(R.string.prgDeleteProp));
        }

        protected void onPostExecute(Boolean b) {
            try {
                if (m_pd != null)
                    m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
            ActViewProperties avp = m_ctxt.getCallingActivity();
            if (avp == null)
                return;

            // Now recreate m_rgPropIn without the specfied property.
            ArrayList<FlightProperty> alNew = new ArrayList<>();
            for (FlightProperty fp : avp.m_rgfpIn)
                if (fp.idProp != propId)
                    alNew.add(fp);

            avp.m_rgfpIn = alNew.toArray(new FlightProperty[0]);
        }
    }

    private class ExpandablePropertyListAdapter extends BaseExpandableListAdapter {

        final Context m_context;
        final ArrayList<String> m_groups;
        final ArrayList<ArrayList<FlightProperty>> m_children;
        private final SparseArray<View> m_cachedViews;

        ExpandablePropertyListAdapter(Context context, ArrayList<String> groups, ArrayList<ArrayList<FlightProperty>> children) {
            m_context = context;
            m_groups = groups;
            m_children = children;
            m_cachedViews = new SparseArray<>();
        }

        @Override
        public int getGroupCount() {
            assert  m_groups != null;
            return m_groups.size();
        }

        @Override
        public int getChildrenCount(int groupPos) {
            assert m_children != null;
            return m_children.get(groupPos).size();
        }

        @Override
        public Object getGroup(int i) {
            assert m_groups != null;
            return m_groups.get(i);
        }

        @Override
        public Object getChild(int groupPos, int childPos) {
            assert m_children != null;
            return m_children.get(groupPos).get(childPos);
        }

        @Override
        public long getGroupId(int groupPos) {
            return groupPos;
        }

        @Override
        public long getChildId(int groupPos, int childPos) {
            assert m_children != null;
//            return childPos;
            return m_children.get(groupPos).get(childPos).idPropType;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            assert m_groups != null;
            if (convertView == null) {
                assert m_context != null;
                LayoutInflater infalInflater = (LayoutInflater) m_context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                assert infalInflater != null;
                convertView = infalInflater.inflate(R.layout.grouprow, parent, false);
            }

            TextView tv = convertView.findViewById(R.id.propertyGroup);
            tv.setText(m_groups.get(groupPosition));

            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                                 View convertView, ViewGroup parent) {
            FlightProperty fp = (FlightProperty) getChild(groupPosition, childPosition);

            // ignore passed-in value of convert view; keep these all around all the time.
            convertView = m_cachedViews.get(fp.CustomPropertyType().idPropType);
            if (convertView == null) {
                assert m_context != null;
                LayoutInflater infalInflater = (LayoutInflater) m_context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                assert infalInflater != null;
                convertView = infalInflater.inflate(R.layout.cptitem, parent, false);
                m_cachedViews.put(fp.CustomPropertyType().idPropType, convertView);
            }

            PropertyEdit pe = convertView.findViewById(R.id.propEdit);

            // only init if it's not already set up - this avoids focus back-and-forth with edittext
            FlightProperty fpExisting = pe.getFlightProperty();
            if (fpExisting == null || fpExisting.idPropType != fp.idPropType)
                pe.InitForProperty(fp, fp.idPropType, ActViewProperties.this, fp.idPropType == CustomPropertyType.idPropTypeTachStart ?
                        sender -> {
                            if (ActViewProperties.this.m_xfillTachStart > 0)
                                sender.setDoubleValue(ActViewProperties.this.m_xfillTachStart);
                        }
                        : ActViewProperties.this);

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int i, int i1) {
            return false;
        }

        @Override
        public void onGroupExpanded(int groupPosition) {
            super.onGroupExpanded(groupPosition);
            if (m_rgExpandedGroups != null)
                m_rgExpandedGroups[groupPosition] = true;
        }

        @Override
        public void onGroupCollapsed(int groupPosition) {
            super.onGroupCollapsed(groupPosition);
            if (m_rgExpandedGroups != null)
                m_rgExpandedGroups[groupPosition] = false;
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.expandablelist);
        TextView tvSearch = findViewById(R.id.txtSearchProp);
        tvSearch.setHint(R.string.hintSearchProperties);
        tvSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                populateList();
            }

            @Override
            public void afterTextChanged(Editable editable) { }
        });

        Intent i = getIntent();
        m_idFlight = i.getLongExtra(ActNewFlight.PROPSFORFLIGHTID, -1);
        if (m_idFlight >= 0) {
            // initialize the flightprops from the db
            m_rgfpIn = FlightProperty.FromDB(m_idFlight);
        }

        m_idExistingId = i.getIntExtra(ActNewFlight.PROPSFORFLIGHTEXISTINGID, 0);

        m_xfillValue = i.getDoubleExtra(ActNewFlight.PROPSFORFLIGHTCROSSFILLVALUE, 0.0);
        m_xfillTachStart = i.getDoubleExtra(ActNewFlight.TACHFORCROSSFILLVALUE, 0.0);

        CustomPropertyTypesSvc cptSvc = new CustomPropertyTypesSvc();
        if (cptSvc.CacheStatus() == DBCache.DBCacheStatus.VALID) {
            m_rgcpt = CustomPropertyTypesSvc.getCachedPropertyTypes();
            populateList();
        } else {
            RefreshCPTTask rt = new RefreshCPTTask(this, this);
            rt.execute();
        }

        SwipeRefreshLayout srl = findViewById(R.id.swiperefresh);
        if (srl != null) {
            srl.setOnRefreshListener(() -> {
                srl.setRefreshing(false);
                refreshProps();
            });
        }
    }

    public void onPause() {
        super.onPause();

        if (getCurrentFocus() != null)
            getCurrentFocus().clearFocus();   // force any in-progress edit to commit, particularly for properties.

        updateProps();
    }

    private void updateProps() {
        FlightProperty[] rgfpUpdated = FlightProperty.DistillList(m_rgfpAll);
        FlightProperty.RewritePropertiesForFlight(m_idFlight, rgfpUpdated);
    }

    private Boolean containsWords(String szTarget, String[] rgTerms)
    {
        if (szTarget == null)
            return false;

        szTarget = szTarget.toUpperCase(Locale.getDefault());

        for (String s : rgTerms) {
            if (s.length() > 0 && !szTarget.contains(s))
                return false;
        }
        return true;
    }

    private void populateList() {
        // get the cross product of property types with existing properties
        if (m_rgcpt == null)
            m_rgcpt = CustomPropertyTypesSvc.getCachedPropertyTypes();  // try to avoid passing null
        if (m_rgfpAll == null)
            m_rgfpAll = FlightProperty.CrossProduct(m_rgfpIn, m_rgcpt);

        // This maps the headers to the individual sub-lists.
        HashMap<String, String> headers = new HashMap<>();
        HashMap<String, ArrayList<FlightProperty>> childrenMaps = new HashMap<>();

        // Keep a list of the keys in order
        ArrayList<String> alKeys = new ArrayList<>();
        String szKeyLast = "";

        String szRestrict = ((EditText) findViewById(R.id.txtSearchProp)).getText().toString().toUpperCase(Locale.getDefault());
        String[] rgTerms = szRestrict.split("\\s+");

        // slice and dice into headers/first names
        for (FlightProperty fp : m_rgfpAll) {
            if (!containsWords(fp.labelString(), rgTerms))
                continue;

            // get the section for this property
            String szKey = (fp.CustomPropertyType().IsFavorite) ? getString(R.string.lblPreviouslyUsed) : fp.labelString().substring(0, 1).toUpperCase(Locale.getDefault());
            if (szKey.compareTo(szKeyLast) != 0) {
                alKeys.add(szKey);
                szKeyLast = szKey;
            }

            if (!headers.containsKey(szKey))
                headers.put(szKey, szKey);

            // Get the array-list for that key, creating it if necessary
            ArrayList<FlightProperty> alProps;
            alProps = (childrenMaps.containsKey(szKey) ? childrenMaps.get(szKey) : new ArrayList<>());
            assert alProps != null;
            alProps.add(fp);

            childrenMaps.put(szKey, alProps);
        }

        // put the above into arrayLists, but in the order that the keys were encountered.  .values() is an undefined order.
        ArrayList<String> headerList = new ArrayList<>();
        ArrayList<ArrayList<FlightProperty>> childrenList = new ArrayList<>();
        for (String s : alKeys) {
            headerList.add(headers.get(s));
            childrenList.add(childrenMaps.get(s));
        }

        if (m_rgExpandedGroups == null)
            m_rgExpandedGroups = new boolean[alKeys.size()];
        else if (m_rgExpandedGroups.length != alKeys.size()) {
            m_rgExpandedGroups = new boolean[alKeys.size()];
            if (m_rgExpandedGroups.length <= 5) // autoexpand if fewer than 5 groups.
                Arrays.fill(m_rgExpandedGroups, true);
        }

        ExpandablePropertyListAdapter mAdapter = new ExpandablePropertyListAdapter(this, headerList, childrenList);
        setListAdapter(mAdapter);

        for (int i = 0; i < m_rgExpandedGroups.length; i++)
            if (m_rgExpandedGroups[i])
                this.getExpandableListView().expandGroup(i);
    }

    public void setProperties(FlightProperty[] rgfp) {
        m_rgfpIn = rgfp;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.propertylistmenu, menu);
        return true;
    }

    private void refreshProps() {
        updateProps();    // preserve current user edits
        RefreshCPTTask rt = new RefreshCPTTask(this, this);
        rt.fAllowCache = false;
        rt.execute();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menuBackToFlight) {
            updateProps();
            finish();
        } else if (id == R.id.menuRefreshProperties)
            refreshProps();
        else
            return super.onOptionsItemSelected(item);
        return true;
    }

    private void DeleteDefaultedProperty(FlightProperty fp) {
        for (FlightProperty f : m_rgfpIn)
            if (f.idPropType == fp.idPropType && f.idProp > 0) {
                DeletePropertyTask dpt = new DeletePropertyTask(this, this, m_idExistingId);
                dpt.propId = f.idProp;
                dpt.execute();
            }
    }

    //region Property update delegates
    public void updateProperty(int id, FlightProperty fp) {
        if (m_idExistingId > 0 && fp.IsDefaultValue())
            DeleteDefaultedProperty(fp);
    }

    public void dateOfFlightShouldReset(Date dt) {
    }

    //region DecimalEdit cross-fill
    public void CrossFillRequested(DecimalEdit sender) {
        sender.setDoubleValue(m_xfillValue);
    }
    //endregion
}
