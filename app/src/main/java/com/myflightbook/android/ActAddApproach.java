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

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.Locale;

import Model.Airport;
import Model.ApproachDescription;
import Model.DecimalEdit;

public class ActAddApproach extends AppCompatActivity {

    // intent keys
    public static final String AIRPORTSFORAPPROACHES = "com.myflightbook.android.AirportsForApproaches";
    public static final String APPROACHDESCRIPTIONRESULT = "com.myflightbook.android.ApproachDescriptionResult";
    public static final String APPROACHADDTOTOTALSRESULT = "com.myflightbook.android.ApproachAddToTotalsResult";
    public static final int APPROACH_DESCRIPTION_REQUEST_CODE = 50382;

    private final ApproachDescription approachDescription = new ApproachDescription();

    private String approachBase = "";
    private String approachSuffix = "";
    private String runwayBase = "";
    private String runwaySuffix = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.addapproach);

        // Set up the approach type spinner...
        Spinner s = findViewById(R.id.spnApproachType);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ApproachDescription.ApproachNames);
        adapter.setDropDownViewResource(R.layout.samplequestion);
        s.setAdapter(adapter);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                approachBase = ApproachDescription.ApproachNames[i];
                approachDescription.approachName = approachBase + approachSuffix;
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                approachBase = "";
                approachDescription.approachName = approachBase + approachSuffix;
            }
        });

        // Then the approach suffix spinner
        s = findViewById(R.id.spnApproachSuffix);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ApproachDescription.ApproachSuffixes);
        adapter.setDropDownViewResource(R.layout.samplequestion);
        s.setAdapter(adapter);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                approachSuffix = ApproachDescription.ApproachSuffixes[i];
                approachDescription.approachName = approachBase + approachSuffix;
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                approachSuffix = "";
                approachDescription.approachName = approachBase + approachSuffix;
            }
        });

        // Runway spinner...
        s = findViewById(R.id.spnApproachRunway);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ApproachDescription.RunwayNames);
        adapter.setDropDownViewResource(R.layout.samplequestion);
        s.setAdapter(adapter);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                runwayBase = ApproachDescription.RunwayNames[i];
                approachDescription.runwayName = runwayBase + runwaySuffix;
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                runwayBase = "";
                approachDescription.runwayName = runwayBase + runwaySuffix;
            }
        });

        // And Runway suffix spinner.
        s = findViewById(R.id.spnApproachRunwaySuffix);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ApproachDescription.RunwayModifiers);
        adapter.setDropDownViewResource(R.layout.samplequestion);
        s.setAdapter(adapter);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                runwaySuffix = ApproachDescription.RunwayModifiers[i];
                approachDescription.runwayName = runwayBase + runwaySuffix;
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                runwaySuffix = "";
                approachDescription.runwayName = runwayBase + runwaySuffix;
            }
        });

        String szAirports = getIntent().getStringExtra(AIRPORTSFORAPPROACHES);
        if (szAirports == null)
            szAirports = "";
        String[] rgAirports = Airport.SplitCodes(szAirports);
        ArrayList<String> al = new ArrayList<>();
        for (int i = rgAirports.length - 1; i >= 0; i--)
            al.add(rgAirports[i]);
        rgAirports = al.toArray(new String[0]);

        AutoCompleteTextView et = findViewById(R.id.txtAirport);
        // if airports specified, default to the first one in the list (last one visited)
        if (rgAirports.length > 0) {
            approachDescription.airportName = rgAirports[0];
            et.setText(rgAirports[0]);
        }

        if (rgAirports.length > 1) {
            et.setAdapter(new ArrayAdapter<>(
                    this, android.R.layout.simple_list_item_1,
                    rgAirports));
        }

        CheckBox ckAddToTotals = findViewById(R.id.ckAddToApproachTotals);
        ckAddToTotals.setChecked(approachDescription.addToApproachCount);
        ckAddToTotals.setOnCheckedChangeListener((buttonView,  isChecked) -> approachDescription.addToApproachCount = isChecked);
    }

    @Override
    public void onBackPressed() {
        approachDescription.approachCount = ((DecimalEdit) findViewById(R.id.txtApproachCount)).getIntValue();
        String szTypedAirports = ((EditText) findViewById(R.id.txtAirport)).getText().toString();
        if (szTypedAirports.length() > 0)
            approachDescription.airportName = szTypedAirports.toUpperCase(Locale.getDefault());

        Bundle bundle = new Bundle();
        bundle.putString(APPROACHDESCRIPTIONRESULT, approachDescription.toString());
        bundle.putInt(APPROACHADDTOTOTALSRESULT, approachDescription.addToApproachCount ? approachDescription.approachCount : 0);

        Intent mIntent = new Intent();
        mIntent.putExtras(bundle);
        setResult(RESULT_OK, mIntent);
        super.onBackPressed();
    }
}
