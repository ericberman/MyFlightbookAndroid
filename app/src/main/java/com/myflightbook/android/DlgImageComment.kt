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
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.ImagesSvc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.MFBImageInfo

internal class DlgImageComment(
    private val m_Context: Context,
    private val m_mfbii: MFBImageInfo?,
    private val m_delegate: AnnotationUpdate?,
    private val scope : LifecycleCoroutineScope
) : Dialog(
    m_Context, R.style.MFBDialog
), View.OnClickListener {
    internal interface AnnotationUpdate {
        fun updateAnnotation(mfbii: MFBImageInfo?)
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dlgaddcomment)
        var b = findViewById<Button>(R.id.btnOK)
        b.setOnClickListener(this)
        b = findViewById(R.id.btnCancel)
        b.setOnClickListener(this)
        if (m_mfbii != null) {
            val imgview = findViewById<ImageView>(R.id.imgPreview)
            scope.launch(Dispatchers.IO) {
                m_mfbii.loadImageForImageView(true, imgview)
            }
            val e = findViewById<EditText>(R.id.txtComment)
            e.setText(m_mfbii.comment)
        }
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.btnOK) {
            m_mfbii!!.comment = (findViewById<View>(R.id.txtComment) as EditText).text.toString()
            // Note that an image can be BOTH on the server AND local (Aircraft images).
            if (m_mfbii.isLocal()) m_mfbii.toDB()
            if (m_mfbii.isOnServer()) {
                val `is` = ImagesSvc()
                `is`.updateImageAnnotation(AuthToken.m_szAuthToken, m_mfbii, m_Context)
            }
        } // else if (id == R.id.btnCancel) { }
        m_delegate?.updateAnnotation(m_mfbii)
        dismiss()
    }
}