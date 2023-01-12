/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2023 MyFlightbook, LLC

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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.text.format.DateFormat
import android.text.method.TextKeyListener
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.google.android.material.snackbar.Snackbar
import com.myflightbook.android.DlgDatePicker.DateTimeUpdate
import com.myflightbook.android.webservices.UTCDate.formatDate
import com.myflightbook.android.webservices.UTCDate.isNullDate
import model.CustomPropertyType
import model.CustomPropertyType.CFPPropertyType
import model.CustomPropertyType.Companion.isPinnedProperty
import model.CustomPropertyType.Companion.removePinnedProperty
import model.CustomPropertyType.Companion.setPinnedProperty
import model.DecimalEdit
import model.DecimalEdit.CrossFillDelegate
import model.FlightProperty
import model.LogbookEntry
import java.util.*

class PropertyEdit : LinearLayout, DateTimeUpdate {
    interface PropertyListener {
        fun updateProperty(id: Int, fp: FlightProperty)
        fun dateOfFlightShouldReset(dt: Date)
    }

    //endregion
    var flightProperty: FlightProperty? = null
        private set
    private var mId = 0
    private var mPl: PropertyListener? = null
    private var mCfd: CrossFillDelegate? = null
    private var mTxtnumericfield: DecimalEdit? = null
    private var mTxtstringval: AutoCompleteTextView? = null
    private var mCk: CheckBox? = null
    private var mTvdate: TextView? = null
    private var mTvlabel: TextView? = null
    private var mIspinned = false

    //region constructors
    constructor(context: Context) : super(context) {
        initializeViews(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initializeViews(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initializeViews(context)
    }

    private fun initializeViews(context: Context) {
        val inflater = (context
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
        inflater.inflate(R.layout.propertyedit, this)
    }

    override fun updateDate(id: Int, dt: Date?) {
        flightProperty!!.dateValue = dt
        updateForProperty()
        notifyDelegate()
    }

    private fun notifyDelegate() {
        if (mPl != null && flightProperty != null) {
            mPl!!.updateProperty(mId, flightProperty!!)
        }
    }

    private fun handlePinClick() {
        val pref = context.getSharedPreferences(
            CustomPropertyType.prefSharedPinnedProps,
            Activity.MODE_PRIVATE
        )
        if (mIspinned) removePinnedProperty(
            pref,
            flightProperty!!.idPropType
        ) else setPinnedProperty(pref, flightProperty!!.idPropType)
        mIspinned = !mIspinned
        updateForProperty()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleStupidFocusStuffInListViews(tv: TextView?) {
        // Wow.  See https://stackoverflow.com/questions/38890059/edittext-in-expandablelistview.  EditTexts inside ListView's are a pain in the ass
        // because of all of the re-using of the controls.
        tv!!.isFocusable = false
        tv.setOnTouchListener { view: View, _: MotionEvent? ->
            view.isFocusableInTouchMode = true
            view.performClick()
            false
        }
    }

    fun initForProperty(
        fp: FlightProperty,
        id: Int,
        pl: PropertyListener?,
        cfd: CrossFillDelegate?
    ) {
        flightProperty = fp
        mId = id
        mPl = pl
        mCfd = cfd
        mTxtnumericfield = findViewById(R.id.txtEditNumericProp)
        mTxtstringval = findViewById(R.id.txtStringVal)
        mCk = findViewById(R.id.ckBoolValue)
        mTvdate = findViewById(R.id.txtDate)
        mTvlabel = findViewById(R.id.txtLabel)

        // Initialize labels and values (whether or not visible)
        val ck = mCk
        ck?.text = flightProperty!!.labelString()
        ck?.isChecked = flightProperty!!.boolValue
        val tvLabel = mTvlabel
        tvLabel?.text = flightProperty!!.labelString()
        updateLabelTypefaceForProperty()
        val pref = context.getSharedPreferences(
            CustomPropertyType.prefSharedPinnedProps,
            Activity.MODE_PRIVATE
        )
        mIspinned = isPinnedProperty(pref, flightProperty!!.idPropType)
        findViewById<View>(R.id.layoutPropEdit).setOnLongClickListener {
            handlePinClick()
            true
        }
        findViewById<View>(R.id.imgFavorite).setOnLongClickListener {
            handlePinClick()
            true
        }
        findViewById<View>(R.id.imgAboutProp).setOnClickListener {
            Snackbar.make(context, this, fp.descriptionString(), Snackbar.LENGTH_SHORT).setTextMaxLines(4).setTextColor(context.getColor(R.color.textColorPrimary)).setBackgroundTint(context.getColor(R.color.colorBackground)).show()
        }
        val txtStringVal = mTxtstringval
        val txtNumericField = mTxtnumericfield
        handleStupidFocusStuffInListViews(txtStringVal)
        handleStupidFocusStuffInListViews(txtNumericField)
        txtNumericField?.setOnFocusChangeListener { view: View, hasFocus: Boolean ->
            if (!hasFocus) {
                when (fp.getType()) {
                    CFPPropertyType.cfpCurrency, CFPPropertyType.cfpDecimal -> flightProperty!!.decValue =
                        txtNumericField.doubleValue
                    CFPPropertyType.cfpInteger -> flightProperty!!.intValue =
                        txtNumericField.intValue
                    else -> {}
                }
                view.isFocusable = false
                updateForProperty()
                notifyDelegate()
            }
        }
        txtStringVal?.setOnFocusChangeListener { view: View, hasFocus: Boolean ->
            if (!hasFocus) {
                flightProperty!!.stringValue = txtStringVal.text.toString()
                view.isFocusable = false
                updateForProperty()
                notifyDelegate()
            }
        }
        ck?.setOnCheckedChangeListener { _: CompoundButton?, fChecked: Boolean ->
            flightProperty!!.boolValue = fChecked
            updateForProperty()
            notifyDelegate()
        }
        val tvDate = mTvdate
        tvDate?.setOnClickListener {
            if (flightProperty!!.dateValue == null || isNullDate(
                    flightProperty!!.dateValue
                )
            ) {
                val dt = Date()
                updateDate(mId, dt)
                if (mPl != null) mPl!!.dateOfFlightShouldReset(dt)
            } else {
                val ddp = DlgDatePicker(
                    context,
                    if (fp.getType() === CFPPropertyType.cfpDate) DlgDatePicker.DatePickMode.LOCALDATEONLY else DlgDatePicker.DatePickMode.UTCDATETIME,
                    (if (fp.dateValue == null) Date() else fp.dateValue)!!
                )
                ddp.mDelegate = this@PropertyEdit
                ddp.mId = mId
                ddp.show()
            }
        }
        updateForProperty()
        invalidate()
        requestLayout()
    }

    private fun updateLabelTypefaceForProperty() {
        val txtLabel = findViewById<TextView>(R.id.txtLabel)
        txtLabel.setTypeface(
            null,
            if (flightProperty!!.isDefaultValue()) Typeface.NORMAL else Typeface.BOLD
        )
        val ckLabel = findViewById<CheckBox>(R.id.ckBoolValue)
        ckLabel.setTypeface(
            null,
            if (flightProperty!!.isDefaultValue()) Typeface.NORMAL else Typeface.BOLD
        )
    }

    private fun updateForProperty() {
        updateLabelTypefaceForProperty()
        val cpt = flightProperty!!.getCustomPropertyType()!!
        val cptType = cpt.cptType
        val fIsTime = cpt.isTime()
        mTvlabel!!.visibility = VISIBLE
        mTxtstringval!!.visibility = GONE
        mTxtnumericfield!!.visibility = GONE
        mCk!!.visibility = GONE
        mTvdate!!.visibility = GONE
        when (cptType) {
            CFPPropertyType.cfpInteger -> {
                mTxtnumericfield!!.visibility = VISIBLE
                mTxtnumericfield!!.setMode(DecimalEdit.EditMode.INTEGER)
                mTxtnumericfield!!.intValue = flightProperty!!.intValue
                if (mCfd != null) mTxtnumericfield!!.setDelegate(mCfd)
            }
            CFPPropertyType.cfpDecimal -> {
                mTxtnumericfield!!.visibility = VISIBLE
                mTxtnumericfield!!.setMode(if (fIsTime && DecimalEdit.DefaultHHMM) DecimalEdit.EditMode.HHMM else DecimalEdit.EditMode.DECIMAL)
                mTxtnumericfield!!.doubleValue = flightProperty!!.decValue
                if (mCfd != null) mTxtnumericfield!!.setDelegate(mCfd)
            }
            CFPPropertyType.cfpCurrency -> {
                mTxtnumericfield!!.visibility = VISIBLE
                mTxtnumericfield!!.setMode(DecimalEdit.EditMode.DECIMAL)
                mTxtnumericfield!!.doubleValue = flightProperty!!.decValue
            }
            CFPPropertyType.cfpString -> {
                mTxtstringval!!.visibility = VISIBLE
                mTxtstringval!!.hint = ""
                val capFlag =
                    if (flightProperty!!.getCustomPropertyType()!!.cptFlag and 0x04000000 != 0) InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else if (flightProperty!!.getCustomPropertyType()!!.cptFlag and 0x10000000 != 0) InputType.TYPE_TEXT_FLAG_CAP_WORDS else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                mTxtstringval!!.inputType =
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_CLASS_TEXT or capFlag
                mTxtstringval!!.keyListener = TextKeyListener.getInstance()
                mTxtstringval!!.setText(flightProperty.toString())
                val rgPrevVals = flightProperty!!.getCustomPropertyType()!!.previousValues
                if (rgPrevVals.isNotEmpty()) {
                    val adapter = ArrayAdapter(
                        this.context, android.R.layout.simple_list_item_1,
                        rgPrevVals
                    )
                    mTxtstringval!!.setAdapter(adapter)
                }
                mTxtstringval!!.threshold = 1
            }
            CFPPropertyType.cfpBoolean -> {
                mCk!!.visibility = VISIBLE
                mTvlabel!!.visibility = GONE
            }
            CFPPropertyType.cfpDate -> {
                mTvdate!!.visibility = VISIBLE
                setPropDate(flightProperty!!.dateValue, false)
            }
            CFPPropertyType.cfpDateTime -> {
                mTvdate!!.visibility = VISIBLE
                setPropDate(flightProperty!!.dateValue, true)
            }
        }
        findViewById<View>(R.id.imgFavorite).visibility = if (mIspinned) VISIBLE else INVISIBLE
    }

    private fun setPropDate(d: Date?, fUTC: Boolean) {
        if (d == null || isNullDate(d)) mTvdate!!.text =
            context.getString(if (fUTC) R.string.lblTouchForNow else R.string.lblTouchForToday) else mTvdate!!.text =
            if (fUTC) formatDate(
                DlgDatePicker.fUseLocalTime,
                d,
                context
            ) else DateFormat.getDateFormat(
                context
            ).format(d)
    }

    val property: FlightProperty?
        get() {
            when (flightProperty?.getType()) {
                CFPPropertyType.cfpInteger -> flightProperty!!.intValue = mTxtnumericfield!!.intValue
                CFPPropertyType.cfpDecimal, CFPPropertyType.cfpCurrency -> flightProperty!!.decValue =
                    mTxtnumericfield!!.doubleValue
                CFPPropertyType.cfpString -> flightProperty!!.stringValue = mTxtstringval!!.text.toString()
                CFPPropertyType.cfpBoolean -> flightProperty!!.boolValue = mCk!!.isChecked
                else -> {}
            }
            if (mPl != null)
                mPl!!.updateProperty(mId, flightProperty!!)
            return flightProperty
        }

    companion object {
        fun crossFillProperty(cpt: CustomPropertyType?, le : LogbookEntry?, decEdit : DecimalEdit?) {
            if (cpt == null || le == null || decEdit == null)
                return
            val xfillVal = le.xfillValueForProperty(cpt)
            if (xfillVal > 0) {
                if (cpt.cptType == CFPPropertyType.cfpInteger)
                    decEdit.intValue = xfillVal.toInt()
                else
                    decEdit.doubleValue = xfillVal
            }
        }
    }
}