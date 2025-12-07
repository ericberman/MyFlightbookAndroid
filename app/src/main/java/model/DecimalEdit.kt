/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2025

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
package model

import android.content.Context
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.util.AttributeSet
import android.view.View
import android.view.View.OnLongClickListener
import androidx.appcompat.widget.AppCompatEditText
import com.myflightbook.android.R
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*
import kotlin.math.roundToLong
import androidx.core.content.withStyledAttributes

class DecimalEdit : AppCompatEditText, OnLongClickListener {
    interface CrossFillDelegate {
        fun crossFillRequested(sender: DecimalEdit?)
    }

    enum class EditMode {
        INTEGER, DECIMAL, HHMM
    }

    // Mode: decimal, integer, or HH:MM
    private var mEditMode = EditMode.INTEGER
    private var delegate: CrossFillDelegate? = null
    @JvmField
    var forceHHMM = false

    constructor(context: Context?) : super(context!!)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initFromAttributes(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        initFromAttributes(context, attrs)
    }

    private fun initFromAttributes(context: Context, attrs: AttributeSet?) {
        context.withStyledAttributes(attrs, R.styleable.DecimalEdit) {
            val editmode: CharSequence? = getString(R.styleable.DecimalEdit_EditMode)
            if (editmode != null) {
                val szVal = editmode.toString()
                val em = EditMode.valueOf(szVal)
                setMode(em)
            }
        }
    }

    fun setDelegate(d: CrossFillDelegate?) {
        delegate = d
        isLongClickable = true
        setOnLongClickListener(this)
    }

    // return the effective mode - if HHMM has been specified, then follow user preference.
    private fun effectiveMode(): EditMode {
        return if (mEditMode == EditMode.HHMM) if (DefaultHHMM || forceHHMM) EditMode.HHMM else EditMode.DECIMAL else mEditMode
    }

    fun setMode(value: EditMode) {
        mEditMode = value
        when (effectiveMode()) {
            EditMode.HHMM -> {
                this.setHint(R.string.emptyWaterMarkHHMM)
                this.inputType = InputType.TYPE_CLASS_NUMBER
                keyListener = DigitsKeyListener.getInstance(Locale.getDefault(), false, false)
            }
            EditMode.INTEGER -> {
                this.setHint(R.string.emptyWaterMarkInt)
                this.inputType = InputType.TYPE_CLASS_NUMBER
                this.keyListener = DigitsKeyListener.getInstance(Locale.getDefault(), false, false)
            }
            EditMode.DECIMAL -> {
                this.hint = String.format(Locale.getDefault(), "%.1f", 0.0)
                this.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                // this is preferred and works internationally, but only works on version 26 and higher, which we don't require
                this.keyListener =
                    DigitsKeyListener.getInstance(Locale.getDefault(), false, true)
            }
        }
    }

    var doubleValue: Double
        get() = doubleFromString(Objects.requireNonNull(text).toString(), effectiveMode())
        set(d) {
            setText(stringForMode(d, effectiveMode()))
        }
    var intValue: Int
        get() = if (text!!.isEmpty()) 0 else try {
            text.toString().toInt()
        } catch (_: Exception) {
            0
        }
        set(d) {
            setText(if (d == 0) "" else String.format(Locale.getDefault(), "%d", d))
        }

    public override fun onTextChanged(s: CharSequence, start: Int, before: Int, after: Int) {
        super.onTextChanged(s, start, before, after)

        // If this is HHMM, see if we need to reformat the string
        if (effectiveMode() == EditMode.HHMM) {
            var sz = s.toString()

            // do nothing if already in correct format.  This will also avoid an infinite loop
            if (sz.isEmpty() || sz.matches(Regex("\\d*:\\d\\d"))) return

            // Otherwise, extract all of the digits, pad to 3 characters, and insert the colon.
            var `val` = 0
            for (element in s) {
                if (element in '0'..'9') `val` = `val` * 10 + (element - '0')
            }
            sz = if (`val` == 0) "" else String.format(
                Locale.US,
                "%d:%02d",
                `val` / 100,
                `val` % 100
            )
            this.setText(sz)
            this.setSelection(text!!.length)
        }
    }

    override fun onLongClick(v: View): Boolean {
        if (delegate != null) delegate!!.crossFillRequested(this)
        return true
    }

    companion object {
        @JvmField
        var DefaultHHMM = false
        private fun doubleFromString(sz: String, em: EditMode): Double {
            return if (sz.isEmpty()) 0.0 else try {
                if (em == EditMode.HHMM) {
                    val rgsz = sz.split(":").toTypedArray()
                    when (rgsz.size) {
                        1 -> sz.toInt().toDouble()
                        2 -> {
                            if (rgsz[0].isEmpty()) rgsz[0] = "0"
                            when (rgsz[1].length) {
                                0 -> rgsz[1] = "00"
                                1 -> rgsz[1] = String.format("%s0", rgsz[1])
                                2 -> {}
                                else -> rgsz[1] = rgsz[1].substring(0, 2)
                            }
                            val h = rgsz[0].toInt()
                            val m = rgsz[1].toInt()
                            h + m / 60.0
                        }
                        else -> 0.0
                    }
                } else {
                    val format = NumberFormat.getInstance(Locale.getDefault())
                    Objects.requireNonNull(format.parse(sz)).toDouble()
                }
            } catch (_: Exception) {
                0.0
            }
        }

        @JvmStatic
        fun doubleToHHMM(d: Double): String {
            val totalMinutes = (d * 60.0).roundToLong().toInt()
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return String.format(Locale.US, "%d:%02d", h, m)
        }

        @JvmStatic
        fun stringForMode(d: Double, em: EditMode): String {
            if (d == 0.0) return ""
            return if (em == EditMode.HHMM) doubleToHHMM(d) else DecimalFormat(
                "#,##0.0#"
            ).format(d)
        }
    }
}