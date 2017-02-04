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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.TextKeyListener;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import Model.CustomPropertyType;
import Model.DecimalEdit;
import Model.DecimalEdit.CrossFillDelegate;
import Model.DecimalEdit.EditMode;
import Model.FlightProperty;

public class DlgPropEdit extends Dialog implements android.view.View.OnClickListener, CrossFillDelegate{
	
	public interface PropertyListener {
		void updateProperty(int id, FlightProperty fp);
	}

	public FlightProperty m_fp = null;
	public int m_id = 0;
	public PropertyListener m_pl = null;
	public double m_xFillValue = 0.0;
	
	private DecimalEdit m_txtField;
	private AutoCompleteTextView m_txtStringVal;
	private CheckBox m_ck;
	
	public DlgPropEdit(Context c)
	{
		super(c, R.style.MFBDialog);
	}
	
	public DlgPropEdit(Context c, int id, PropertyListener pl, FlightProperty fp, double xFillValue)
	{
		super(c, R.style.MFBDialog);
		m_id = id;
		m_pl = pl;
		m_fp = fp;
		m_xFillValue = xFillValue;
		this.setTitle(R.string.lblEditPropertyTitle);
	}
	
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.dlgeditprop);
		Button b = (Button)findViewById(R.id.btnOK);
		b.setOnClickListener(this);
		b = (Button)findViewById(R.id.btnCancel);
		b.setOnClickListener(this);
		
		m_txtField = (DecimalEdit)findViewById(R.id.txtEditProp);
		m_txtStringVal = (AutoCompleteTextView)findViewById(R.id.txtStringVal);
		m_ck = (CheckBox) findViewById(R.id.ckBoolValue);
		
		TextView txtDesc = (TextView) findViewById(R.id.txtDescription);
		txtDesc.setText(m_fp.descriptionString());
		
		TextView txtLabel = (TextView) findViewById(R.id.txtLabel);
		
		CustomPropertyType.CFPPropertyType cptType = m_fp.getType();
		Boolean fIsBasicDecimal = cptType == CustomPropertyType.CFPPropertyType.cfpDecimal && (m_fp.CustomPropertyType().cptFlag & 0x00200000) != 0;
		
		// show/hide fields according to the type:
		m_txtField.setVisibility(cptType == CustomPropertyType.CFPPropertyType.cfpBoolean || cptType == CustomPropertyType.CFPPropertyType.cfpString ? View.GONE : View.VISIBLE);
		m_txtStringVal.setVisibility(cptType == CustomPropertyType.CFPPropertyType.cfpString ? View.VISIBLE : View.GONE);
		txtLabel.setVisibility(cptType == CustomPropertyType.CFPPropertyType.cfpBoolean ? View.GONE : View.VISIBLE);
		m_ck.setVisibility(cptType == CustomPropertyType.CFPPropertyType.cfpBoolean ? View.VISIBLE : View.GONE);

		// Initialize labels and values (whether or not visible)
		m_ck.setText(m_fp.labelString());
		m_ck.setChecked(m_fp.boolValue);
		txtLabel.setText(m_fp.labelString());

		// set the hint and the keyboard type
		switch (cptType) {
		default:
			break;
		case cfpInteger:
			m_txtField.setMode(EditMode.INTEGER);
			m_txtField.setIntValue(m_fp.intValue);
			break;
		case cfpDecimal:
			m_txtField.setMode(DecimalEdit.DefaultHHMM && !fIsBasicDecimal ? EditMode.HHMM : EditMode.DECIMAL);
			m_txtField.setDoubleValue(m_fp.decValue);
			m_txtField.setDelegate(this);
			break;
		case cfpCurrency:
			m_txtField.setMode(EditMode.DECIMAL);
			m_txtField.setDoubleValue(m_fp.decValue);
			break;
		case cfpString:
			m_txtStringVal.setHint("");
			m_txtStringVal.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
					| InputType.TYPE_CLASS_TEXT);
			m_txtStringVal.setKeyListener(TextKeyListener.getInstance());
			m_txtStringVal.setText(m_fp.toString());
			ArrayAdapter<String> adapter = new ArrayAdapter<String>(
					this.getContext(), android.R.layout.simple_list_item_1,
					m_fp.CustomPropertyType().PreviousValues);
			m_txtStringVal.setAdapter(adapter);
			m_txtStringVal.setThreshold(1);
			break;
		}
	}

	public void onClick(View v) {		
		switch (v.getId())
		{
		case R.id.btnOK:
			switch (m_fp.getType())
			{
			default:
				break;
			case cfpInteger:
				m_fp.intValue = m_txtField.getIntValue();
				break;
			case cfpDecimal:
			case cfpCurrency:
				m_fp.decValue = m_txtField.getDoubleValue();
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
			dismiss();
			break;
		case R.id.btnCancel:
			dismiss();
			break;
		}
	}

	public void CrossFillRequested(DecimalEdit sender) {
		if (m_xFillValue > 0)
			sender.setDoubleValue(m_xFillValue);
	}
}
