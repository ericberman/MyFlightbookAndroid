/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2018 MyFlightbook, LLC

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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.text.format.DateFormat;
import android.text.method.TextKeyListener;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.myflightbook.android.WebServices.UTCDate;

import java.util.Date;

import Model.CustomPropertyType;
import Model.DecimalEdit;
import Model.FlightProperty;

public class PropertyEdit extends LinearLayout implements DlgDatePicker.DateTimeUpdate {

    interface PropertyListener {
        void updateProperty(int id, FlightProperty fp);
    }

    private FlightProperty m_fp;
    private int m_id;
    private PropertyListener m_pl;
    private DecimalEdit.CrossFillDelegate m_cfd;

    private DecimalEdit m_txtNumericField;
    private AutoCompleteTextView m_txtStringVal;
    private CheckBox m_ck;
    private TextView m_tvDate;
    private TextView m_tvLabel;
    private boolean m_fIsPinned = false;

    //region constructors
    public PropertyEdit(Context context) {
        super(context);
        initializeViews(context);
    }

    public PropertyEdit(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initializeViews(context);
    }

    public PropertyEdit(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initializeViews(context);
    }

    private void initializeViews(Context context) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        assert inflater != null;
        inflater.inflate(R.layout.propertyedit, this);
    }
    //endregion

    public FlightProperty getFlightProperty() {
        return m_fp;
    }

    @Override
    public void updateDate(int id, Date dt) {
        m_fp.dateValue = dt;
        UpdateForProperty();
        NotifyDelegate();
    }

    private void NotifyDelegate() {
        if (m_pl != null) {
            m_pl.updateProperty(m_id, m_fp);
        }
    }

    private void HandlePinClick() {
        SharedPreferences pref = getContext().getSharedPreferences(CustomPropertyType.prefSharedPinnedProps, Activity.MODE_PRIVATE);

        if (m_fIsPinned)
            CustomPropertyType.removePinnedProperty(pref, m_fp.idPropType);
        else
            CustomPropertyType.setPinnedProperty(pref, m_fp.idPropType);

        m_fIsPinned = !m_fIsPinned;
        UpdateForProperty();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void handleStupidFocusStuffInListViews(TextView tv) {
        // Wow.  See https://stackoverflow.com/questions/38890059/edittext-in-expandablelistview.  EditTexts inside ListView's are a pain in the ass
        // because of all of the re-using of the controls.
        tv.setFocusable(false);
        tv.setOnTouchListener((view, motionEvent) -> {
            view.setFocusableInTouchMode(true);
            view.performClick();
            return false;
        });
    }

    public void InitForProperty(FlightProperty fp, int id, PropertyListener pl, DecimalEdit.CrossFillDelegate cfd) {
        m_fp = fp;
        m_id = id;
        m_pl = pl;
        m_cfd = cfd;

        m_txtNumericField = findViewById(R.id.txtEditNumericProp);
        m_txtStringVal = findViewById(R.id.txtStringVal);
        m_ck = findViewById(R.id.ckBoolValue);
        m_tvDate = findViewById(R.id.txtDate);
        m_tvLabel = findViewById(R.id.txtLabel);

        // Initialize labels and values (whether or not visible)
        m_ck.setText(m_fp.labelString());
        m_ck.setChecked(m_fp.boolValue);
        m_tvLabel.setText(m_fp.labelString());
        UpdateLabelTypefaceForProperty();

        SharedPreferences pref = getContext().getSharedPreferences(CustomPropertyType.prefSharedPinnedProps, Activity.MODE_PRIVATE);
        m_fIsPinned = CustomPropertyType.isPinnedProperty(pref, m_fp.idPropType);


        findViewById(R.id.layoutPropEdit).setOnLongClickListener(v -> {
            HandlePinClick();
            return true;
        });

        findViewById(R.id.imgFavorite).setOnLongClickListener(v -> {
            HandlePinClick();
            return true;
        });

        findViewById(R.id.imgAboutProp).setOnClickListener(view -> {
                Toast t = Toast.makeText(getContext(), fp.descriptionString(), Toast.LENGTH_SHORT);
                int[] location = new int[2];
                findViewById(R.id.layoutPropEdit).getLocationOnScreen(location);
                t.setGravity(Gravity.TOP | Gravity.START, location[0], location[1]);
                t.show();
        });

        handleStupidFocusStuffInListViews(m_txtStringVal);
        handleStupidFocusStuffInListViews(m_txtNumericField);

        m_txtNumericField.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                switch (m_fp.getType()) {
                    case cfpCurrency:
                    case cfpDecimal:
                        m_fp.decValue = m_txtNumericField.getDoubleValue();
                        break;
                    case cfpInteger:
                        m_fp.intValue = m_txtNumericField.getIntValue();
                        break;
                    default:
                        break;
                }
                view.setFocusable(false);
                UpdateForProperty();
                NotifyDelegate();
            }
        });

        m_txtStringVal.setOnFocusChangeListener((view, hasFocus) -> {
            if (!hasFocus) {
                m_fp.stringValue = m_txtStringVal.getText().toString();
                view.setFocusable(false);
                UpdateForProperty();
                NotifyDelegate();
            }
        });

        m_ck.setOnCheckedChangeListener((ck, fChecked) -> {
            m_fp.boolValue = fChecked;
            UpdateForProperty();
            NotifyDelegate();
        });

        m_tvDate.setOnClickListener(view -> {
            if (m_fp.dateValue == null ||UTCDate.IsNullDate(m_fp.dateValue)) {
                updateDate(m_id, new Date());
            }
            else {
                DlgDatePicker ddp = new DlgDatePicker(getContext(),
                        fp.getType() == CustomPropertyType.CFPPropertyType.cfpDate ? DlgDatePicker.datePickMode.LOCALDATEONLY : DlgDatePicker.datePickMode.UTCDATETIME,
                        fp.dateValue == null ? new Date() : fp.dateValue);
                ddp.m_delegate = PropertyEdit.this;
                ddp.m_id = m_id;
                ddp.show();
            }
        });

        UpdateForProperty();

        invalidate();
        requestLayout();
    }

    private void UpdateLabelTypefaceForProperty() {
        TextView txtLabel = findViewById(R.id.txtLabel);
        txtLabel.setTypeface(null, m_fp.IsDefaultValue() ? Typeface.NORMAL : Typeface.BOLD);
    }

    private void UpdateForProperty() {
        UpdateLabelTypefaceForProperty();
        CustomPropertyType.CFPPropertyType cptType = m_fp.getType();
        Boolean fIsBasicDecimal = cptType == CustomPropertyType.CFPPropertyType.cfpDecimal && (m_fp.CustomPropertyType().cptFlag & 0x00200000) != 0;

        m_tvLabel.setVisibility(VISIBLE);
        m_txtStringVal.setVisibility(GONE);
        m_txtNumericField.setVisibility(GONE);
        m_ck.setVisibility(GONE);
        m_tvDate.setVisibility(GONE);

        switch (cptType) {
            default:
                break;
            case cfpInteger:
                m_txtNumericField.setVisibility(VISIBLE);
                m_txtNumericField.setMode(DecimalEdit.EditMode.INTEGER);
                m_txtNumericField.setIntValue(m_fp.intValue);
                break;
            case cfpDecimal:
                m_txtNumericField.setVisibility(VISIBLE);
                m_txtNumericField.setMode(DecimalEdit.DefaultHHMM && !fIsBasicDecimal ? DecimalEdit.EditMode.HHMM : DecimalEdit.EditMode.DECIMAL);
                m_txtNumericField.setDoubleValue(m_fp.decValue);
                if (m_cfd != null)
                    m_txtNumericField.setDelegate(m_cfd);
                break;
            case cfpCurrency:
                m_txtNumericField.setVisibility(VISIBLE);
                m_txtNumericField.setMode(DecimalEdit.EditMode.DECIMAL);
                m_txtNumericField.setDoubleValue(m_fp.decValue);
                break;
            case cfpString: {
                m_txtStringVal.setVisibility(VISIBLE);
                m_txtStringVal.setHint("");
                m_txtStringVal.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        | InputType.TYPE_CLASS_TEXT);
                m_txtStringVal.setKeyListener(TextKeyListener.getInstance());
                m_txtStringVal.setText(m_fp.toString());
                String[] rgPrevVals = m_fp.CustomPropertyType().PreviousValues;
                if (rgPrevVals != null && rgPrevVals.length > 0) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this.getContext(), android.R.layout.simple_list_item_1,
                            rgPrevVals);
                    m_txtStringVal.setAdapter(adapter);
                }
                m_txtStringVal.setThreshold(1);
            }
                break;
            case cfpBoolean:
                m_ck.setVisibility(VISIBLE);
                m_tvLabel.setVisibility(GONE);
                break;
            case cfpDate:
                m_tvDate.setVisibility(VISIBLE);
                SetPropDate(m_fp.dateValue, false);
                break;
            case cfpDateTime:
                m_tvDate.setVisibility(VISIBLE);
                SetPropDate(m_fp.dateValue, true);
                break;
        }

        findViewById(R.id.imgFavorite).setVisibility(m_fIsPinned ? View.VISIBLE : View.INVISIBLE);
    }

    private void SetPropDate(Date d, boolean fUTC) {
        if (d == null || UTCDate.IsNullDate(d))
            m_tvDate.setText(getContext().getString(fUTC ? R.string.lblTouchForNow : R.string.lblTouchForToday));
        else
            m_tvDate.setText(fUTC ? UTCDate.formatDate(DlgDatePicker.fUseLocalTime, d, getContext()) : DateFormat.getDateFormat(getContext()).format(d));
    }

    public FlightProperty getProperty() {
        switch (m_fp.getType()) {
            default:
                break;
            case cfpInteger:
                m_fp.intValue = m_txtNumericField.getIntValue();
                break;
            case cfpDecimal:
            case cfpCurrency:
                m_fp.decValue = m_txtNumericField.getDoubleValue();
                break;
            case cfpString:
                m_fp.stringValue = m_txtStringVal.getText().toString();
                break;
            case cfpBoolean:
                m_fp.boolValue = m_ck.isChecked();
                break;
        }
        if (m_pl != null)
            m_pl.updateProperty(m_id, m_fp);

        return m_fp;
    }
}
