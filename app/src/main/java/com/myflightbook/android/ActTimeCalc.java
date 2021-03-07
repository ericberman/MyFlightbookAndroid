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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import java.util.ArrayList;

import Model.DecimalEdit;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Created by ericberman on 2/12/17.
 *
 * Implements a time calculator, which allows adding of multiple time periods together
 *
 */

public class ActTimeCalc extends AppCompatActivity implements View.OnClickListener {

    public static final String INITIAL_TIME = "com.myflightbook.android.initialTime";
    public static final String COMPUTED_TIME = "com.myflightbook.android.computedTime";

    private final ArrayList<Double> m_values = new ArrayList<>();
    private double initialTime = 0.0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calctime);

        ((DecimalEdit) findViewById(R.id.decSegmentStart)).forceHHMM = true;
        ((DecimalEdit) findViewById(R.id.decSegmentEnd)).forceHHMM = true;
        ((DecimalEdit) findViewById(R.id.decSegmentStart)).setMode(DecimalEdit.EditMode.HHMM);
        ((DecimalEdit) findViewById(R.id.decSegmentEnd)).setMode(DecimalEdit.EditMode.HHMM);
        findViewById(R.id.btnCopySegement).setOnClickListener(this);
        findViewById(R.id.btnAddSegment).setOnClickListener(this);
        findViewById(R.id.btnAddAndUpdate).setOnClickListener(this);

        double dInit = getIntent().getDoubleExtra(INITIAL_TIME, 0.0);
        if (dInit > 0) {
            initialTime = dInit;
            m_values.add(dInit);
            updateEquationString();
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnCopySegement) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            String s = DecimalEdit.StringForMode(ComputedTotal(), DecimalEdit.DefaultHHMM ? DecimalEdit.EditMode.HHMM : DecimalEdit.EditMode.DECIMAL);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("total", s));
            }
        } else if (id == R.id.btnAddSegment) {
            addSpecifiedTime();
            updateEquationString();
        } else if (id == R.id.btnAddAndUpdate) {
            addSpecifiedTime();
            returnValue(ComputedTotal());
            super.onBackPressed();
        }
    }

    private void returnValue(double d) {
        Bundle bundle = new Bundle();
        bundle.putDouble(COMPUTED_TIME, d);
        hideKeyboard();

        Intent mIntent = new Intent();
        mIntent.putExtras(bundle);
        setResult(RESULT_OK, mIntent);
    }

    @Override
    public void onBackPressed() {
        returnValue(initialTime);
        super.onBackPressed();
    }

    //region Time calculation math
    private double ComputedTotal() {
        double result = 0.0;
        for (Double d : m_values)
            result += d;
        return result;
    }

    private double getSpecifiedTimeRange() {
        double dStart = ((DecimalEdit) findViewById(R.id.decSegmentStart)).getDoubleValue();
        double dEnd = ((DecimalEdit) findViewById(R.id.decSegmentEnd)).getDoubleValue();

        if (dEnd < 0 || dStart < 0 || dEnd > 24 || dStart > 24) {
            ((TextView) findViewById(R.id.errTimeCalc)).setText(R.string.tcErrBadTime);
            return 0;
        }
        else {
            ((TextView) findViewById(R.id.errTimeCalc)).setText(R.string.strEmpty);
            clearTime();
        }

        while (dEnd < dStart)
            dEnd += 24.0;

        return dEnd - dStart;
    }

    private void addSpecifiedTime() {
        double d = getSpecifiedTimeRange();
        if (d > 0.0)
            m_values.add(d);
    }

    private String getEquationString() {
        StringBuilder sb = new StringBuilder();
        DecimalEdit.EditMode mode = DecimalEdit.DefaultHHMM ? DecimalEdit.EditMode.HHMM : DecimalEdit.EditMode.DECIMAL;
        for (Double d : m_values) {
            if (sb.length() > 0)
                sb.append(" + ");
            sb.append(DecimalEdit.StringForMode(d, mode));
        }

        if (m_values.size() > 0)
            sb.append(" = ").append(DecimalEdit.StringForMode(ComputedTotal(), mode));

        return sb.toString();
    }

    private void updateEquationString() {
        ((TextView) findViewById(R.id.txtTimeCalcEquation)).setText(getEquationString());
    }

    private void clearTime() {
        DecimalEdit deStart = findViewById(R.id.decSegmentStart);
        deStart.setDoubleValue(0);
        ((DecimalEdit) findViewById(R.id.decSegmentEnd)).setDoubleValue(0);
        deStart.requestFocus();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        View v = getCurrentFocus();
        if (v != null && imm != null)
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
    //endregion
}
