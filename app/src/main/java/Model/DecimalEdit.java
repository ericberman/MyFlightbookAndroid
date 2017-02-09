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
package Model;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.EditText;

import com.myflightbook.android.R;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class DecimalEdit extends EditText implements OnLongClickListener {

    public interface CrossFillDelegate {
        void CrossFillRequested(DecimalEdit sender);
    }

    public enum EditMode {INTEGER, DECIMAL, HHMM}

    // Mode: decimal, integer, or HH:MM
    private EditMode m_editMode = EditMode.INTEGER;

    public static boolean DefaultHHMM = false;
    static DecimalFormat m_df = new DecimalFormat();
    private CrossFillDelegate delegate = null;

    public DecimalEdit(Context context) {
        super(context);
    }

    public DecimalEdit(Context context, AttributeSet attrs) {
        super(context, attrs);
        InitFromAttributes(context, attrs);
    }

    public DecimalEdit(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        InitFromAttributes(context, attrs);
    }

    protected void InitFromAttributes(Context context, AttributeSet attrs) {
        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.DecimalEdit);

        CharSequence editmode = arr.getString(R.styleable.DecimalEdit_EditMode);
        if (editmode != null) {
            String szVal = editmode.toString();
            EditMode em = EditMode.valueOf(szVal);
            setMode(em);
        }

        arr.recycle();
    }

    public void setDelegate(CrossFillDelegate d) {
        delegate = d;
        setLongClickable(true);
        setOnLongClickListener(this);
    }

    // return the effective mode - if HHMM has been specified, then follow user preference.
    public EditMode EffectiveMode() {
        if (m_editMode == EditMode.HHMM)
            return DecimalEdit.DefaultHHMM ? EditMode.HHMM : EditMode.DECIMAL;
        return m_editMode;
    }

    public void setMode(EditMode value) {
        m_editMode = value;

        switch (EffectiveMode()) {
            case HHMM:
                this.setHint(R.string.emptyWaterMarkHHMM);
                this.setInputType(InputType.TYPE_CLASS_NUMBER);
                setKeyListener(DigitsKeyListener.getInstance(false, false));
                break;
            case INTEGER:
                this.setHint(R.string.emptyWaterMarkInt);
                this.setInputType(InputType.TYPE_CLASS_NUMBER);
                this.setKeyListener(DigitsKeyListener.getInstance(false, false));
                // setKeyListener(DigitsKeyListener.getInstance("01234567890"));
                break;
            case DECIMAL:
                // See Android bug #2626 (http://code.google.com/p/android/issues/detail?id=2626&colspec=ID%20Type%20Status%20Owner%20Summary%20Stars)
                // Using SetKeyListener
                this.setHint(String.format(Locale.getDefault(), "%.1f", 0.0));
                this.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                this.setKeyListener(DigitsKeyListener.getInstance(false, true)); // should work but bug above means it will ALWAYS use a period.
                break;
        }
    }

    public static double doubleFromString(String sz, EditMode em) {
        if (sz.length() == 0)
            return 0.0;

        try {
            if (em == EditMode.HHMM) {
                String[] rgsz = sz.split(":");
                if (rgsz.length == 1)
                    return (double) Integer.parseInt(sz);
                else if (rgsz.length == 2) {
                    if (rgsz[0].length() == 0)
                        rgsz[0] = "0";
                    switch (rgsz[1].length()) {
                        case 0:
                            rgsz[1] = "00";
                            break;
                        case 1:
                            rgsz[1] = String.format("%s0", rgsz[1]);
                            break;
                        case 2:
                            break;
                        default:
                            rgsz[1] = rgsz[1].substring(0, 2);
                    }
                    int h = Integer.parseInt(rgsz[0]);
                    int m = Integer.parseInt(rgsz[1]);
                    return h + (m / 60.0);
                } else
                    return 0.0;
            } else {
                // TODO: remove this when Android bug 2626 is fixed. See http://code.google.com/p/android/issues/detail?id=2626&colspec=ID%20Type%20Status%20Owner%20Summary%20Stars.
                DecimalFormatSymbols dfs = new DecimalFormatSymbols(Locale.getDefault());
                char c = dfs.getDecimalSeparator();

                return m_df.parse(sz.replace('.', c).replace(',', c)).doubleValue();
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getDoubleValue() {
        return doubleFromString(getText().toString(), EffectiveMode());
    }

    public static String DoubleToHHMM(double d) {
        int h = (int) d;
        int m = (int) Math.round((d - h) * 60);
        return String.format(Locale.US, "%d:%02d", h, m);
    }

    public static String StringForMode(double d, EditMode em) {
        if (d == 0.0)
            return "";

        if (em == EditMode.HHMM)
            return DoubleToHHMM(d);
        else
            return new DecimalFormat("#.##").format(d);
    }

    public void setDoubleValue(double d) {
        setText(StringForMode(d, EffectiveMode()));
    }

    public int getIntValue() {
        if (getText().length() == 0)
            return 0;

        try {
            return Integer.parseInt(getText().toString());
        } catch (Exception e) {
            return 0;
        }
    }

    public void setIntValue(int d) {
        setText(d == 0 ? "" : String.format(Locale.getDefault(), "%d", d));
    }

    public void onTextChanged(CharSequence s, int start, int before, int after) {
        super.onTextChanged(s, start, before, after);

        // If this is HHMM, see if we need to reformat the string
        if (EffectiveMode() == EditMode.HHMM) {
            String sz = s.toString();

            // do nothing if already in correct format.  This will also avoid an infinite loop
            if (sz.length() == 0 || sz.matches("\\d*:\\d\\d"))
                return;

            // Otherwise, extract all of the digits, pad to 3 characters, and insert the colon.
            int val = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9')
                    val = (val * 10) + (c - '0');
            }

            if (val == 0)
                sz = "";
            else
                sz = String.format(Locale.US, "%d:%02d", (val / 100), val % 100);
            this.setText(sz);
            this.setSelection(this.getText().length());
        }
    }

    public boolean onLongClick(View v) {
        if (delegate != null)
            delegate.CrossFillRequested(this);
        return true;
    }
}
