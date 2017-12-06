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

// This class taken and adapted from http://stackoverflow.com/questions/5015686/android-spinner-with-multiple-choice
// Thanks!!!

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.ArrayList;

public class MultiSpinner extends Spinner implements
        OnMultiChoiceClickListener, OnCancelListener {

    private Object[] items;
    private boolean[] selected;
    private String defaultText;
    private MultiSpinnerListener listener;

    public MultiSpinner(Context context) {
        super(context);
    }

    public MultiSpinner(Context arg0, AttributeSet arg1) {
        super(arg0, arg1);
    }

    public MultiSpinner(Context arg0, AttributeSet arg1, int arg2) {
        super(arg0, arg1, arg2);
    }

    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
        selected[which] = isChecked;
    }

    public void refresh() {
        // refresh text on spinner
        // if no items selected, display default text.
        // Otherwise, show the selected items.
        ArrayList<String> al = new ArrayList<>();
        for (int i = 0; i < items.length; i++)
            if (selected[i])
                al.add(items[i].toString());

        String spinnerText;

        if (al.size() == 0)
            spinnerText = defaultText;
        else
            spinnerText = TextUtils.join(", ", al.toArray(new String[0]));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item,
                new String[]{spinnerText});
        setAdapter(adapter);
    }

    public void onCancel(DialogInterface dialog) {
        refresh();

        if (listener != null)
            listener.onItemsSelected(this, selected);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        CharSequence[] rgItemStrings = new CharSequence[items.length];
        for (int i = 0; i < items.length; i++)
            rgItemStrings[i] = items[i].toString();
        builder.setMultiChoiceItems(rgItemStrings, selected, this);
        builder.setPositiveButton(R.string.lblOK, (dialog, which) -> dialog.cancel());
        builder.setOnCancelListener(this);
        builder.show();
        return true;
    }

    public boolean[] getSelected() {
        return this.selected;
    }

    public void setItems(Object[] items, String allText,
                         MultiSpinnerListener listener) {
        this.items = items;
        this.defaultText = allText;
        this.listener = listener;

        // none selected by default
        selected = new boolean[items.length];
        for (int i = 0; i < selected.length; i++)
            selected[i] = false;

        // all text on the spinner
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, new String[]{allText});
        setAdapter(adapter);
    }

    public interface MultiSpinnerListener {
        void onItemsSelected(MultiSpinner sender, boolean[] selected);
    }
}
