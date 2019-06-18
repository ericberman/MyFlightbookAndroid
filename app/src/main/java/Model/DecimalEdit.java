/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2019

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
import android.os.Build;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.myflightbook.android.R;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

public class DecimalEdit extends android.support.v7.widget.AppCompatEditText implements OnLongClickListener {

    public interface CrossFillDelegate {
        void CrossFillRequested(DecimalEdit sender);
    }

    public enum EditMode {INTEGER, DECIMAL, HHMM}

    // Mode: decimal, integer, or HH:MM
    private EditMode m_editMode = EditMode.INTEGER;

    public static boolean DefaultHHMM = false;
    private CrossFillDelegate delegate = null;

    public Boolean forceHHMM = false;

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

    private void InitFromAttributes(Context context, AttributeSet attrs) {
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
    private EditMode EffectiveMode() {
        if (m_editMode == EditMode.HHMM)
            return DecimalEdit.DefaultHHMM || forceHHMM ? EditMode.HHMM : EditMode.DECIMAL;
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
                // However:
                //  * setKeyListener with a string of characters reverts from decimal input on some versions of android (i.e., becomes integer only - lame!)
                //  * setKeyListener(DigitsKeyListener.getInstance(sign, decimal)) doesn't work internationally (only accepts a period)
                //  * setKeyListener(DigitsKeyListener.getInstance(Locale.getDefault()...) only works on 26 and higher.
                // Uggh.  So here's the hack we will do:
                //  * Use DigitsKeyListener.getInstance(Locale.getDefault()...) if on 26 and higher.  This does the right thing.
                //  * Otherwise, if in a locale that uses a period, use DigitsKeyListener.getInstance(false, true)
                //  * Otherwise, essentially give up and use a default keyboard.
                this.setHint(String.format(Locale.getDefault(), "%.1f", 0.0));
                this.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                if (Build.VERSION.SDK_INT >= 26) {
                    // this is preferred and works internationally, but only works on version 26 and higher, which we don't require
                    this.setKeyListener(DigitsKeyListener.getInstance(Locale.getDefault(), false, true));
                }
                else {
                    DecimalFormatSymbols dfs = new DecimalFormatSymbols(Locale.getDefault());
                    if (dfs.getDecimalSeparator() == '.') // US style - we can use regular digitsKeyListener
                        this.setKeyListener(DigitsKeyListener.getInstance(false, true));
                    else {
                        this.setKeyListener(DigitsKeyListener.getInstance("0123456789" + dfs.getDecimalSeparator())); // should work but bug above means it will ALWAYS use a period.
                        this.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    }
                }
                break;
        }
    }

    private static double doubleFromString(String sz, EditMode em) {
        if (sz.length() == 0)
            return 0.0;

        try {
            if (em == EditMode.HHMM) {
                String[] rgsz = sz.split(":");
                switch (rgsz.length) {
                    case 1:
                        return (double) Integer.parseInt(sz);
                    case 2:
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
                    default:
                        return 0.0;
                }
            } else {
                NumberFormat format = NumberFormat.getInstance(Locale.getDefault());
                return format.parse(sz).doubleValue();
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getDoubleValue() {
        return doubleFromString(Objects.requireNonNull(getText()).toString(), EffectiveMode());
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
            return new DecimalFormat("#,###.##").format(d);
    }

    public void setDoubleValue(double d) {
        setText(StringForMode(d, EffectiveMode()));
    }

    public int getIntValue() {
        if (Objects.requireNonNull(getText()).length() == 0)
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
            this.setSelection(Objects.requireNonNull(this.getText()).length());
        }
    }

    public boolean onLongClick(View v) {
        if (delegate != null)
            delegate.CrossFillRequested(this);
        return true;
    }
}
