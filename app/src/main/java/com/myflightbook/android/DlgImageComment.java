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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.ImagesSvc;

import Model.MFBImageInfo;

class DlgImageComment extends Dialog implements android.view.View.OnClickListener {

    interface AnnotationUpdate {
        void updateAnnotation(MFBImageInfo mfbii);
    }

    private AnnotationUpdate m_delegate = null;
    private MFBImageInfo m_mfbii = null;
    private Context m_Context = null;

    DlgImageComment(Context context, MFBImageInfo mfbii, AnnotationUpdate d) {
        super(context, R.style.MFBDialog);
        m_mfbii = mfbii;
        m_delegate = d;
        m_Context = context;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dlgaddcomment);
        Button b = (Button) findViewById(R.id.btnOK);
        b.setOnClickListener(this);
        b = (Button) findViewById(R.id.btnCancel);
        b.setOnClickListener(this);

        if (m_mfbii != null) {
            ImageView imgview = (ImageView) findViewById(R.id.imgPreview);
            m_mfbii.LoadImageForImageView(true, imgview);

            EditText e = (EditText) findViewById(R.id.txtComment);
            e.setText(m_mfbii.Comment);
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnOK:
                m_mfbii.Comment = ((EditText) findViewById(R.id.txtComment)).getText().toString();
                // Note that an image can be BOTH on the server AND local (Aircraft images).
                if (m_mfbii.IsLocal())
                    m_mfbii.toDB();
                if (m_mfbii.IsOnServer()) {
                    ImagesSvc is = new ImagesSvc();
                    is.UpdateImageAnnotation(AuthToken.m_szAuthToken, m_mfbii, m_Context);
                }
                break;
            case R.id.btnCancel:
                break;
        }
        if (m_delegate != null)
            m_delegate.updateAnnotation(m_mfbii);
        dismiss();
    }

}
