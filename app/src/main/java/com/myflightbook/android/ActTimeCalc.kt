/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025 MyFlightbook, LLC

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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import model.DecimalEdit
import model.DecimalEdit.Companion.stringForMode
import model.DecimalEdit.EditMode

/**
 * Created by ericberman on 2/12/17.
 *
 * Implements a time calculator, which allows adding of multiple time periods together
 *
 */
class ActTimeCalc : AppCompatActivity(), View.OnClickListener {
    private val mValues = ArrayList<Double>()
    private var initialTime = 0.0
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.calctime)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_root)) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }
        (findViewById<View>(R.id.decSegmentStart) as DecimalEdit).forceHHMM = true
        (findViewById<View>(R.id.decSegmentEnd) as DecimalEdit).forceHHMM = true
        (findViewById<View>(R.id.decSegmentStart) as DecimalEdit).setMode(EditMode.HHMM)
        (findViewById<View>(R.id.decSegmentEnd) as DecimalEdit).setMode(EditMode.HHMM)
        findViewById<View>(R.id.btnCopySegement).setOnClickListener(this)
        findViewById<View>(R.id.btnAddSegment).setOnClickListener(this)
        findViewById<View>(R.id.btnAddAndUpdate).setOnClickListener(this)
        val dInit = intent.getDoubleExtra(INITIAL_TIME, 0.0)
        if (dInit > 0) {
            initialTime = dInit
            mValues.add(dInit)
            updateEquationString()
        }

        onBackPressedDispatcher.addCallback(this /* lifecycle owner */) {
            returnValue(initialTime)
            finish()
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnCopySegement -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val s = stringForMode(
                    computedTotal(),
                    if (DecimalEdit.DefaultHHMM) EditMode.HHMM else EditMode.DECIMAL
                )
                clipboard.setPrimaryClip(ClipData.newPlainText("total", s))
            }
            R.id.btnAddSegment -> {
                addSpecifiedTime()
                updateEquationString()
            }
            R.id.btnAddAndUpdate -> {
                addSpecifiedTime()
                returnValue(computedTotal())
                finish()
            }
        }
    }

    private fun returnValue(d: Double) {
        val bundle = Bundle()
        bundle.putDouble(COMPUTED_TIME, d)
        hideKeyboard()
        val mIntent = Intent()
        mIntent.putExtras(bundle)
        setResult(RESULT_OK, mIntent)
    }

    //region Time calculation math
    private fun computedTotal(): Double {
        var result = 0.0
        for (d in mValues) result += d
        return result
    }

    private val specifiedTimeRange: Double
        get() {
            val dStart = (findViewById<View>(R.id.decSegmentStart) as DecimalEdit).doubleValue
            var dEnd = (findViewById<View>(R.id.decSegmentEnd) as DecimalEdit).doubleValue
            if (dEnd < 0 || dStart < 0 || dEnd > 24 || dStart > 24) {
                (findViewById<View>(R.id.errTimeCalc) as TextView).setText(R.string.tcErrBadTime)
                return 0.0
            } else {
                (findViewById<View>(R.id.errTimeCalc) as TextView).setText(R.string.strEmpty)
                clearTime()
            }
            while (dEnd < dStart) dEnd += 24.0
            return dEnd - dStart
        }

    private fun addSpecifiedTime() {
        val d = specifiedTimeRange
        if (d > 0.0) mValues.add(d)
    }

    private val equationString: String
        get() {
            val sb = StringBuilder()
            val mode = if (DecimalEdit.DefaultHHMM) EditMode.HHMM else EditMode.DECIMAL
            for (d in mValues) {
                if (sb.isNotEmpty()) sb.append(" + ")
                sb.append(stringForMode(d, mode))
            }
            if (mValues.size > 0) sb.append(" = ").append(stringForMode(computedTotal(), mode))
            return sb.toString()
        }

    private fun updateEquationString() {
        (findViewById<View>(R.id.txtTimeCalcEquation) as TextView).text = equationString
    }

    private fun clearTime() {
        val deStart = findViewById<DecimalEdit>(R.id.decSegmentStart)
        deStart.doubleValue = 0.0
        (findViewById<View>(R.id.decSegmentEnd) as DecimalEdit).doubleValue = 0.0
        deStart.requestFocus()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val v = currentFocus
        if (v != null) imm.hideSoftInputFromWindow(v.windowToken, 0)
    } //endregion

    companion object {
        const val INITIAL_TIME = "com.myflightbook.android.initialTime"
        const val COMPUTED_TIME = "com.myflightbook.android.computedTime"
    }
}