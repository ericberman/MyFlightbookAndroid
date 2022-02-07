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

import androidx.appcompat.app.AppCompatActivity
import model.ApproachDescription
import android.os.Bundle
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import model.Airport
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.CompoundButton
import model.DecimalEdit
import android.widget.EditText
import android.content.Intent
import android.view.*
import java.util.*

class ActAddApproach : AppCompatActivity() {
    private val approachDescription = ApproachDescription()
    private var approachBase = ""
    private var approachSuffix = ""
    private var runwayBase = ""
    private var runwaySuffix = ""
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.addapproach)

        // Set up the approach type spinner...
        var s = findViewById<Spinner>(R.id.spnApproachType)
        var adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ApproachDescription.ApproachNames
        )
        adapter.setDropDownViewResource(R.layout.samplequestion)
        s.adapter = adapter
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                approachBase = ApproachDescription.ApproachNames[i]
                approachDescription.approachName = approachBase + approachSuffix
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                approachBase = ""
                approachDescription.approachName = approachBase + approachSuffix
            }
        }

        // Then the approach suffix spinner
        s = findViewById(R.id.spnApproachSuffix)
        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ApproachDescription.ApproachSuffixes
        )
        adapter.setDropDownViewResource(R.layout.samplequestion)
        s.adapter = adapter
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                approachSuffix = ApproachDescription.ApproachSuffixes[i]
                approachDescription.approachName = approachBase + approachSuffix
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                approachSuffix = ""
                approachDescription.approachName = approachBase + approachSuffix
            }
        }

        // Runway spinner...
        s = findViewById(R.id.spnApproachRunway)
        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ApproachDescription.RunwayNames
        )
        adapter.setDropDownViewResource(R.layout.samplequestion)
        s.adapter = adapter
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                runwayBase = ApproachDescription.RunwayNames[i]
                approachDescription.runwayName = runwayBase + runwaySuffix
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                runwayBase = ""
                approachDescription.runwayName = runwayBase + runwaySuffix
            }
        }

        // And Runway suffix spinner.
        s = findViewById(R.id.spnApproachRunwaySuffix)
        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ApproachDescription.RunwayModifiers
        )
        adapter.setDropDownViewResource(R.layout.samplequestion)
        s.adapter = adapter
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                runwaySuffix = ApproachDescription.RunwayModifiers[i]
                approachDescription.runwayName = runwayBase + runwaySuffix
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                runwaySuffix = ""
                approachDescription.runwayName = runwayBase + runwaySuffix
            }
        }
        var szAirports = intent.getStringExtra(AIRPORTSFORAPPROACHES)
        if (szAirports == null) szAirports = ""
        var rgAirports = Airport.splitCodes(szAirports)
        val al = ArrayList<String>()
        for (i in rgAirports.indices.reversed()) al.add(rgAirports[i])
        rgAirports = al.toTypedArray()
        val et = findViewById<AutoCompleteTextView>(R.id.txtAirport)
        // if airports specified, default to the first one in the list (last one visited)
        if (rgAirports.isNotEmpty()) {
            approachDescription.airportName = rgAirports[0]
            et.setText(rgAirports[0])
        }
        if (rgAirports.size > 1) {
            et.setAdapter(
                ArrayAdapter(
                    this, android.R.layout.simple_list_item_1,
                    rgAirports
                )
            )
        }
        val ckAddToTotals = findViewById<CheckBox>(R.id.ckAddToApproachTotals)
        ckAddToTotals.isChecked = approachDescription.addToApproachCount
        ckAddToTotals.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            approachDescription.addToApproachCount = isChecked
        }
    }

    override fun onBackPressed() {
        approachDescription.approachCount =
            (findViewById<View>(R.id.txtApproachCount) as DecimalEdit).intValue
        val szTypedAirports = (findViewById<View>(R.id.txtAirport) as EditText).text.toString()
        if (szTypedAirports.isNotEmpty()) approachDescription.airportName =
            szTypedAirports.uppercase(
                Locale.getDefault()
            )
        val bundle = Bundle()
        bundle.putString(APPROACHDESCRIPTIONRESULT, approachDescription.toString())
        bundle.putInt(
            APPROACHADDTOTOTALSRESULT,
            if (approachDescription.addToApproachCount) approachDescription.approachCount else 0
        )
        val mIntent = Intent()
        mIntent.putExtras(bundle)
        setResult(RESULT_OK, mIntent)
        super.onBackPressed()
    }

    companion object {
        // intent keys
        const val AIRPORTSFORAPPROACHES = "com.myflightbook.android.AirportsForApproaches"
        const val APPROACHDESCRIPTIONRESULT = "com.myflightbook.android.ApproachDescriptionResult"
        const val APPROACHADDTOTOTALSRESULT = "com.myflightbook.android.ApproachAddToTotalsResult"
    }
}