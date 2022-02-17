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

import android.app.Dialog
import android.content.Context
import com.myflightbook.android.webservices.UTCDate.getNullDate
import com.myflightbook.android.webservices.UTCDate.getUTCTimeZone
import android.widget.DatePicker.OnDateChangedListener
import android.widget.TimePicker.OnTimeChangedListener
import android.widget.DatePicker
import android.widget.TimePicker
import android.os.Bundle
import android.view.View
import java.util.*

class DlgDatePicker(c: Context?, dpm: DatePickMode, dt: Date) : Dialog(
    c!!, R.style.MFBDialog
), View.OnClickListener, OnDateChangedListener, OnTimeChangedListener {
    interface DateTimeUpdate {
        fun updateDate(id: Int, dt: Date?)
    }

    enum class DatePickMode {
        LOCALDATEONLY, LOCALDATENULLABLE, UTCDATETIME
    }

    private var mDate = Date()
    private var mMode: DatePickMode? = null
    @JvmField
    var mId // the id of the date/time to update
            = 0
    @JvmField
    var mDelegate: DateTimeUpdate? = null
    private var dpDate: DatePicker? = null
    private var tpTime: TimePicker? = null
    private fun init(dpm: DatePickMode, dt: Date) {
        setContentView(R.layout.datepicker)
        setMode(dpm)
        date = dt
        setCanceledOnTouchOutside(false)
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        findViewById<View>(R.id.btnOK).setOnClickListener(this)
        findViewById<View>(R.id.btnDateNow).setOnClickListener(this)
        findViewById<View>(R.id.btnDateNone).setOnClickListener(this)
        val c = calendar
        c.time = mDate
        dpDate = findViewById(R.id.datePicker)
        tpTime = findViewById(R.id.timePicker)
        val y = c[Calendar.YEAR]
        val m = c[Calendar.MONTH]
        val d = c[Calendar.DAY_OF_MONTH]
        val h = c[Calendar.HOUR_OF_DAY]
        val min = c[Calendar.MINUTE]
        val vwDate = dpDate
        val vwTime = tpTime
        vwDate!!.init(y, m, d, this)
        vwTime!!.setOnTimeChangedListener(this)
        vwTime.setIs24HourView(true)
        vwTime.hour = h
        vwTime.minute = min
    }

    private fun updatePickers(dt: Date) {
        val c = calendar
        c.time = dt
        val y = c[Calendar.YEAR]
        val m = c[Calendar.MONTH]
        val d = c[Calendar.DAY_OF_MONTH]
        val h = c[Calendar.HOUR_OF_DAY]
        val min = c[Calendar.MINUTE]
        if (dpDate != null) dpDate!!.updateDate(y, m, d)
        if (tpTime != null) {
            tpTime!!.hour = h
            tpTime!!.minute = min
        }
    }

    var date: Date
        get() = mDate
        private set(dt) {
            updatePickers(dt.also { mDate = it })
        }

    private fun setMode(dp: DatePickMode) {
        mMode = dp
        findViewById<View>(R.id.timePicker).visibility =
            if (dp == DatePickMode.UTCDATETIME) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnDateNone).visibility =
            if (dp == DatePickMode.LOCALDATEONLY) View.GONE else View.VISIBLE
    }

    private fun notifyDelegate() {
        if (mDelegate != null) mDelegate!!.updateDate(mId, mDate)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnDateNone -> {
                mDate = getNullDate()
                notifyDelegate()
                dismiss()
            }
            R.id.btnDateNow -> {
                date = Date()
                notifyDelegate()
            }
            R.id.btnOK -> {
                // Issue #233 - typing and then hitting OK without committing what you typed loses typed changes.
                val tp = findViewById<TimePicker>(R.id.timePicker)
                val dp = findViewById<DatePicker>(R.id.datePicker)
                tp.clearFocus()
                dp.clearFocus()
                notifyDelegate()
                dismiss()
            }
        }
    }

    private val calendar: GregorianCalendar
        get() {
            return if (fUseLocalTime || mMode == DatePickMode.LOCALDATEONLY || mMode == DatePickMode.LOCALDATENULLABLE) GregorianCalendar() else GregorianCalendar(
                getUTCTimeZone
            )
        }

    override fun onDateChanged(vw: DatePicker, year: Int, monthOfYear: Int, dayOfMonth: Int) {
        val c = calendar
        c[year, monthOfYear, dayOfMonth, tpTime!!.hour, tpTime!!.minute] = 0
        mDate = c.time
    }

    override fun onTimeChanged(view: TimePicker, hourOfDay: Int, minute: Int) {
        val c = calendar
        c[dpDate!!.year, dpDate!!.month, dpDate!!.dayOfMonth, hourOfDay, minute] = 0
        mDate = c.time
    }

    companion object {
        @JvmField
        var fUseLocalTime = false
    }

    init {
        init(dpm, dt)
    }
}