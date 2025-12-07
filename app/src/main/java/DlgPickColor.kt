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

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.madrapps.pikolo.RGBColorPicker
import com.madrapps.pikolo.listeners.SimpleColorSelectionListener
import com.myflightbook.android.R


internal class DlgPickColor (mCallingActivity: FragmentActivity, private val inColor : Int, onColorChange: (outColor : Int) -> Unit) : Dialog(
    mCallingActivity, R.style.MFBDialog
), View.OnClickListener {
    val mOnColorChange : (outColor : Int) -> Unit

    public override fun onCreate(savedInstanceState: Bundle?) {
        val colorPicker = findViewById<RGBColorPicker>(R.id.pikoColorPicker)
        with (colorPicker!!) {
            setColor(inColor)

            setColorSelectionListener(object : SimpleColorSelectionListener() {
                override fun onColorSelected(color: Int) {
                    // Do whatever you want with the color
                    mOnColorChange(color)
                }
            })
        }

        val btnOK: Button? = findViewById(R.id.btnOK)
        btnOK!!.setOnClickListener { dismiss() }
    }

    override fun onClick(v: View?) {
    }

    init {
        setContentView(R.layout.dlgpickcolor)
        mOnColorChange = onColorChange
    }
}