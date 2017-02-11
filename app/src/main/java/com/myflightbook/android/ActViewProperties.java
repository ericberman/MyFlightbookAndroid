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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;

import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.CustomPropertyTypesSvc;
import com.myflightbook.android.WebServices.FlightPropertiesSvc;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import Model.CustomPropertyType;
import Model.DBCache;
import Model.FlightProperty;
import Model.MFBConstants;
import Model.MFBUtil;

public class ActViewProperties extends FixedExpandableListActivity implements DlgDatePicker.DateTimeUpdate, DlgPropEdit.PropertyListener {

    private final String IS_PINNED_FLAG = "1";
    private FlightProperty[] m_rgfpIn = new FlightProperty[0];
    private FlightProperty[] m_rgfpAll = null;
    private CustomPropertyType[] m_rgcpt = null;
    private boolean[] m_rgExpandedGroups = null;
    private long m_idFlight = -1;
    private int m_idExistingId = 0;
    private double m_xfillValue = 0.0;
    private HashMap<String, String> mActiveProperty = null;
    private MFBExpandableListAdapter mAdapter = null;

    private class RefreshCPTTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog m_pd = null;
        Boolean fAllowCache = true;

        @Override
        protected Boolean doInBackground(Void... params) {
            CustomPropertyTypesSvc cptSvc = new CustomPropertyTypesSvc();
            m_rgcpt = cptSvc.GetCustomPropertyTypes(AuthToken.m_szAuthToken, fAllowCache);
            return m_rgcpt != null && m_rgcpt.length > 0;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(ActViewProperties.this, ActViewProperties.this.getString(R.string.prgCPT));
        }

        protected void onPostExecute(Boolean b) {
            if (b) {
                // Refresh the CPT's for each item in the full array
                if (m_rgfpAll != null) {
                    FlightProperty.RefreshPropCache();
                    for (FlightProperty fp : m_rgfpAll)
                        fp.RefreshPropType();
                }
                populateList();
            }
            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }
        }
    }

    private class DeletePropertyTask extends AsyncTask<Void, Void, Boolean> {
        private ProgressDialog m_pd = null;
        int propId;

        @Override
        protected Boolean doInBackground(Void... params) {
            FlightPropertiesSvc fpsvc = new FlightPropertiesSvc();
            fpsvc.DeletePropertyForFlight(AuthToken.m_szAuthToken, m_idExistingId, propId);
            return true;
        }

        protected void onPreExecute() {
            m_pd = MFBUtil.ShowProgress(ActViewProperties.this, ActViewProperties.this.getString(R.string.prgDeleteProp));
        }

        protected void onPostExecute(Boolean b) {
            try {
                m_pd.dismiss();
            } catch (Exception e) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
            }

            // Now recreate m_rgPropIn without the specfied property.
            ArrayList<FlightProperty> alNew = new ArrayList<>();
            for (FlightProperty fp : m_rgfpIn)
                if (fp.idProp != propId)
                    alNew.add(fp);

            m_rgfpIn = alNew.toArray(new FlightProperty[0]);
        }
    }

    public class MFBExpandableListAdapter extends
            SimpleExpandableListAdapter {
        MFBExpandableListAdapter(Context context,
                                        ArrayList<HashMap<String, String>> headerList, int grouprow,
                                        String[] strings, int[] is,
                                        ArrayList<ArrayList<HashMap<String, String>>> childrenList,
                                        int itemrow, String[] strings2, int[] is2) {
            super(context, headerList, grouprow, strings, is, childrenList, itemrow, strings2, is2);
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

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            final View v = super.getChildView(groupPosition, childPosition, isLastChild, convertView, parent);

            //noinspection unchecked
            v.findViewById(R.id.imgFavorite).setVisibility(((HashMap<String, String>) getChild(groupPosition, childPosition)).get("IsPinned").compareTo(IS_PINNED_FLAG) == 0 ? View.VISIBLE : View.INVISIBLE);

            return v;
        }

        @Override
        public View newChildView(boolean isLastChild, ViewGroup parent) {
            LayoutInflater layoutInflater = (LayoutInflater) ActViewProperties.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            return layoutInflater.inflate(R.layout.cptitem, parent, false);
        }

    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.expandablelist);
    }

    public void onResume() {
        super.onResume();

        Intent i = getIntent();
        m_idFlight = i.getLongExtra(ActNewFlight.PROPSFORFLIGHTID, -1);
        if (m_idFlight >= 0) {
            // initialize the flightprops from the db
            m_rgfpIn = FlightProperty.FromDB(m_idFlight);
        }

        m_idExistingId = i.getIntExtra(ActNewFlight.PROPSFORFLIGHTEXISTINGID, 0);

        m_xfillValue = i.getDoubleExtra(ActNewFlight.PROPSFORFLIGHTCROSSFILLVALUE, 0.0);

        CustomPropertyTypesSvc cptSvc = new CustomPropertyTypesSvc();
        if (cptSvc.CacheStatus() == DBCache.DBCacheStatus.VALID) {
            m_rgcpt = CustomPropertyTypesSvc.getCachedPropertyTypes();
            populateList();
        } else {
            RefreshCPTTask rt = new RefreshCPTTask();
            rt.execute();
        }
    }

    public void onPause() {
        super.onPause();
        updateProps();
    }

    public void updateProps() {
        FlightProperty[] rgfpUpdated = FlightProperty.DistillList(m_rgfpAll);
        FlightProperty.RewritePropertiesForFlight(m_idFlight, rgfpUpdated);
    }

    public void populateList() {
        // get the cross product of property types with existing properties
        if (m_rgfpAll == null)
            m_rgfpAll = FlightProperty.CrossProduct(m_rgfpIn, m_rgcpt);

        mActiveProperty = null;

        // This maps the headers to the individual sub-lists.
        HashMap<String, HashMap<String, String>> headers = new HashMap<>();
        HashMap<String, ArrayList<HashMap<String, String>>> childrenMaps = new HashMap<>();

        // Keep a list of the keys in order
        ArrayList<String> alKeys = new ArrayList<>();
        String szKeyLast = "";

        HashSet<Integer> pinnedProperties = CustomPropertyType.getPinnedProperties(ActViewProperties.this.getSharedPreferences(CustomPropertyType.prefSharedPinnedProps, MODE_PRIVATE));

        // slice and dice into headers/first names
        for (int i = 0; i < m_rgfpAll.length; i++) {
            FlightProperty fp = m_rgfpAll[i];

            // get the section for this property
            String szKey = (fp.CustomPropertyType().IsFavorite) ? getString(R.string.lblPreviouslyUsed) : fp.labelString().substring(0, 1).toUpperCase(Locale.getDefault());
            if (szKey.compareTo(szKeyLast) != 0) {
                alKeys.add(szKey);
                szKeyLast = szKey;
            }

            HashMap<String, String> hmGroups = (headers.containsKey(szKey) ? headers.get(szKey) : new HashMap<>());
            hmGroups.put("sectionName", szKey);
            headers.put(szKey, hmGroups);

            // Get the array-list for that key, creating it if necessary
            ArrayList<HashMap<String, String>> alProps;
            alProps = (childrenMaps.containsKey(szKey) ? childrenMaps.get(szKey) : new ArrayList<>());

            HashMap<String, String> hmProperty = new HashMap<>();
            hmProperty.put("Name", fp.labelString());
            hmProperty.put("Value", fp.toString(DlgDatePicker.fUseLocalTime, this));
            hmProperty.put("Description", fp.descriptionString());
            hmProperty.put("Position", String.format(Locale.US, "%d", i));
            hmProperty.put("IsPinned", CustomPropertyType.isPinnedProperty(pinnedProperties, fp.idPropType) ? IS_PINNED_FLAG : "");
            alProps.add(hmProperty);

            childrenMaps.put(szKey, alProps);
        }

        // put the above into arrayLists, but in the order that the keys were encountered.  .values() is an undefined order.
        ArrayList<HashMap<String, String>> headerList = new ArrayList<>();
        ArrayList<ArrayList<HashMap<String, String>>> childrenList = new ArrayList<>();
        for (String s : alKeys) {
            headerList.add(headers.get(s));
            childrenList.add(childrenMaps.get(s));
        }

        if (m_rgExpandedGroups == null)
            m_rgExpandedGroups = new boolean[alKeys.size()];

        mAdapter = new MFBExpandableListAdapter(
                this,
                headerList,
                R.layout.grouprow,
                new String[]{"sectionName"},
                new int[]{R.id.propertyGroup},
                childrenList,
                R.layout.cptitem,
                new String[]{"Name", "Value", "Description"},
                new int[]{R.id.txtName, R.id.txtValue, R.id.txtCPTDescription}
        );
        setListAdapter(mAdapter);

        getExpandableListView().setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
            //noinspection unchecked
            ActViewProperties.this.mActiveProperty = (HashMap<String, String>) ActViewProperties.this.mAdapter.getChild(groupPosition, childPosition);
                int position = Integer.parseInt(ActViewProperties.this.mActiveProperty.get("Position"));
                ActViewProperties.this.onItemClick(v, position, id);
                return false;
            });

        final ExpandableListView elv = getExpandableListView();
        elv.setOnItemLongClickListener((parent, view, position, id) -> {

            long packedPosition = elv.getExpandableListPosition(position);

            int itemType = ExpandableListView.getPackedPositionType(packedPosition);
            int groupPosition = ExpandableListView.getPackedPositionGroup(packedPosition);
            int childPosition = ExpandableListView.getPackedPositionChild(packedPosition);

            if (itemType == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                //noinspection unchecked
                HashMap<String, String> hm = (HashMap<String, String>) ActViewProperties.this.mAdapter.getChild(groupPosition, childPosition);
                int pos = Integer.parseInt(hm.get("Position"));
                FlightProperty fp = m_rgfpAll[pos];

                SharedPreferences pref = ActViewProperties.this.getSharedPreferences(CustomPropertyType.prefSharedPinnedProps, MODE_PRIVATE);
                if (CustomPropertyType.isPinnedProperty(pref, fp.idPropType)) {
                    CustomPropertyType.removePinnedProperty(pref, fp.idPropType);
                    hm.put("IsPinned", "");
                }
                else {
                    CustomPropertyType.setPinnedProperty(pref, fp.idPropType);
                    hm.put("IsPinned", IS_PINNED_FLAG);
                }

                mAdapter.notifyDataSetChanged();
            }

            return true;
        });

        for (int i = 0; i < m_rgExpandedGroups.length; i++)
            if (m_rgExpandedGroups[i])
                this.getExpandableListView().expandGroup(i);
    }

    public void setProperties(FlightProperty[] rgfp) {
        m_rgfpIn = rgfp;
    }

    @SuppressWarnings("UnusedParameters")
    public void onItemClick(View view, int position, long id) {
        FlightProperty fp = m_rgfpAll[position];
        switch (fp.getType()) {
            case cfpDate:
            case cfpDateTime:
                DlgDatePicker ddp = new DlgDatePicker(this,
                        fp.getType() == CustomPropertyType.CFPPropertyType.cfpDate ? DlgDatePicker.datePickMode.LOCALDATEONLY : DlgDatePicker.datePickMode.UTCDATETIME,
                        fp.dateValue == null ? new Date() : fp.dateValue);
                ddp.m_delegate = this;
                ddp.m_id = position;
                ddp.show();
                break;
            default:
                DlgPropEdit dpe = new DlgPropEdit(this, position, this, fp, m_xfillValue);
                dpe.show();
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.propertylistmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menuBackToFlight:
                updateProps();
                finish();
                return true;
            case R.id.menuRefreshProperties:
                updateProps();    // preserve current user edits
                RefreshCPTTask rt = new RefreshCPTTask();
                rt.fAllowCache = false;
                rt.execute();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void RefreshList(FlightProperty fp) {
        if (mActiveProperty == null || fp == null)
            populateList();
        else {
            mActiveProperty.put("Value", fp.toString(DlgDatePicker.fUseLocalTime, this));
            mAdapter.notifyDataSetChanged();
        }
    }

    private void DeleteDefaultedProperty(FlightProperty fp) {
        for (FlightProperty f : m_rgfpIn)
            if (f.idPropType == fp.idPropType && f.idProp > 0) {
                DeletePropertyTask dpt = new DeletePropertyTask();
                dpt.propId = f.idProp;
                dpt.execute();
            }
    }


    /*
     * Delegates for updating properties
     */
    public void updateProperty(int id, FlightProperty fp) {
        if (m_idExistingId > 0 && fp.IsDefaultValue())
            DeleteDefaultedProperty(fp);

        RefreshList(fp);
    }

    public void updateDate(int id, Date dt) {
        m_rgfpAll[id].dateValue = dt;
        if (m_idExistingId > 0 && m_rgfpAll[id].IsDefaultValue())
            DeleteDefaultedProperty(m_rgfpAll[id]);

        RefreshList(m_rgfpAll[id]);
    }
}
