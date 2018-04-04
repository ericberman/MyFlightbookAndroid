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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import Model.MakesandModels;

public class ActSelectMake extends FixedExpandableListActivity {

    private int m_ModelID;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.expandablelist);

        TextView tvSearch = findViewById(R.id.txtSearchProp);
        tvSearch.setHint(R.string.hintSearchModels);
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
    }

    public void onResume() {
        super.onResume();

        Intent i = getIntent();
        m_ModelID = i.getIntExtra(ActNewAircraft.MODELFORAIRCRAFT, -1);
        populateList();
    }

    private void populateList() {
        // This maps the headers to the individual sub-lists.
        HashMap<String, HashMap<String, String>> headers = new HashMap<>();
        HashMap<String, ArrayList<HashMap<String, String>>> childrenMaps = new HashMap<>();

        // Keep a list of the keys in order
        ArrayList<String> alKeys = new ArrayList<>();
        String szKeyLast = "";
        int expandedGroupIndex = -1;

        String szRestrict = ((EditText) findViewById(R.id.txtSearchProp)).getText().toString().toUpperCase(Locale.getDefault());
        String[] rgRestrictStrings = szRestrict.split("\\W");

        // slice and dice into headers/first names
        if (ActNewAircraft.AvailableMakesAndModels != null) // should never be non-null, but seems to happen occasionally
        {
            for (int i = 0; i < ActNewAircraft.AvailableMakesAndModels.length; i++) {
                MakesandModels mm = ActNewAircraft.AvailableMakesAndModels[i];

                // reject anything that doesn't match the restriction
                boolean fIsMatch = true;
                for (String sz : rgRestrictStrings) {
                    if (sz.length() > 0 && !mm.Description.toUpperCase(Locale.getDefault()).contains(sz)) {
                        fIsMatch = false;
                        break;
                    }
                }
                if (!fIsMatch)
                    continue;

                // get the manufacturer for this property as the grouping key
                String szKey = mm.getManufacturer();
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
                hmProperty.put("Description", mm.Description);
                hmProperty.put("Position", String.format(Locale.getDefault(), "%d", i));
                alProps.add(hmProperty);

                childrenMaps.put(szKey, alProps);

                // if this is the selected item, then expand this group index
                if (mm.ModelId == m_ModelID)
                    expandedGroupIndex = alKeys.size() - 1;
            }
        }

        boolean[] m_rgExpandedGroups = new boolean[alKeys.size()];
        if (expandedGroupIndex > 0 && expandedGroupIndex < alKeys.size())
            m_rgExpandedGroups[expandedGroupIndex] = true;
        if (m_rgExpandedGroups.length <= 5)
            for (int i = 0; i < m_rgExpandedGroups.length; i++)
                m_rgExpandedGroups[i] = true;

        // put the above into arrayLists, but in the order that the keys were encountered.  .values() is an undefined order.
        ArrayList<HashMap<String, String>> headerList = new ArrayList<>();
        ArrayList<ArrayList<HashMap<String, String>>> childrenList = new ArrayList<>();
        for (String s : alKeys) {
            headerList.add(headers.get(s));
            childrenList.add(childrenMaps.get(s));
        }

        final SimpleExpandableListAdapter adapter = new SimpleExpandableListAdapter(
                this,
                headerList,
                R.layout.grouprow,
                new String[]{"sectionName"},
                new int[]{R.id.propertyGroup},
                childrenList,
                R.layout.makemodelitem,
                new String[]{"Description"},
                new int[]{R.id.txtDescription}
        );
        setListAdapter(adapter);

        for (int i = 0; i < m_rgExpandedGroups.length; i++)
            if (m_rgExpandedGroups[i])
            getExpandableListView().expandGroup(i);

        getExpandableListView().setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
                @SuppressWarnings("unchecked")
                HashMap<String, String> hmProp = (HashMap<String, String>) adapter.getChild(groupPosition, childPosition);
                int position = Integer.parseInt(hmProp.get("Position"));
                Intent i = new Intent();
                i.putExtra(ActNewAircraft.MODELFORAIRCRAFT, position);
                ActSelectMake.this.setResult(Activity.RESULT_OK, i);
                ActSelectMake.this.finish();
                return false;
            });
    }
}
