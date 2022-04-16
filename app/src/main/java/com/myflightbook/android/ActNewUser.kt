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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.myflightbook.android.webservices.CreateUserSvc
import kotlinx.coroutines.launch
import model.MFBConstants
import model.MFBConstants.nightParam
import model.MFBUtil.alert
import java.util.*

class ActNewUser : AppCompatActivity(), View.OnClickListener {
    private var txtEmail: TextView? = null
    private var txtEmail2: TextView? = null
    private var txtPass: TextView? = null
    private var txtPass2: TextView? = null
    private var txtFirst: TextView? = null
    private var txtLast: TextView? = null
    private var txtQ: TextView? = null
    private var txtA: TextView? = null
    private var activityLauncher: ActivityResultLauncher<Intent>? = null

    private fun addUser(szEmail : String, szPass : String, szFirst : String, szLast : String, szQ : String, szA : String) {
        val act = this as Activity
        lifecycleScope.launch {
            ActMFBForm.doAsync<CreateUserSvc, Boolean?>(act,
                CreateUserSvc(),
                getString(R.string.prgCreatingAccount),
                {
                    s -> s.fCreateUser(szEmail, szPass, szFirst, szLast, szQ, szA, act)
                },
                {
                    _, result ->
                    if (result != null && result) {
                        val i = Intent()
                        setResult(RESULT_OK, i)
                        finish()
                    }
                }
            )
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.newuser)
        var b = findViewById<Button>(R.id.btnCreateUser)
        b.setOnClickListener(this)
        b = findViewById(R.id.btnCancel)
        b.setOnClickListener(this)
        b = findViewById(R.id.btnViewPrivacy)
        b.setOnClickListener(this)
        b = findViewById(R.id.btnViewTandC)
        b.setOnClickListener(this)
        txtEmail = findViewById(R.id.txtEmail)
        txtEmail2 = findViewById(R.id.txtEmail2)
        txtPass = findViewById(R.id.txtPass)
        txtPass2 = findViewById(R.id.txtPass2)
        txtFirst = findViewById(R.id.txtFirstName)
        txtLast = findViewById(R.id.txtLastName)
        txtQ = findViewById(R.id.txtQuestion)
        txtA = findViewById(R.id.txtAnswer)
        val rgSampleQuestions = resources.getStringArray(R.array.defaultSecurityQuestions)
        val spinner = findViewById<Spinner>(R.id.spnSampleQuestions)
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rgSampleQuestions)
        adapter.setDropDownViewResource(R.layout.samplequestion)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                val txtQ = findViewById<TextView>(R.id.txtQuestion)
                txtQ.text = rgSampleQuestions[i]
            }

            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                val txtQ = findViewById<TextView>(R.id.txtQuestion)
                txtQ.text = ""
            }
        }
        activityLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { }
    }

    private fun isValid(): Boolean {
        if (txtEmail!!.text.toString()
                .compareTo(txtEmail2!!.text.toString()) != 0
        ) {
            alert(this, getString(R.string.txtError), getString(R.string.errTypeEmailTwice))
            return false
        }
        if (txtPass!!.text.toString()
                .compareTo(txtPass2!!.text.toString()) != 0
        ) {
            alert(this, getString(R.string.txtError), getString(R.string.errTypePasswordTwice))
            return false
        }
        if (txtQ!!.text.isEmpty() || txtA!!.text.isEmpty()) {
            alert(this, getString(R.string.txtError), getString(R.string.errNeedQandA))
            return false
        }
        return true
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.btnCreateUser) {
            if (isValid()) {
                addUser(txtEmail!!.text.toString(), txtPass!!.text.toString(), txtFirst!!.text.toString(),
                    txtLast!!.text.toString(),
                    txtQ!!.text.toString(), txtA!!.text.toString()
                )
            }
        } else if (id == R.id.btnCancel) {
            val i = Intent()
            setResult(RESULT_CANCELED, i)
            finish()
        } else if (id == R.id.btnViewPrivacy) {
            val i = Intent(v.context, ActWebView::class.java)
            i.putExtra(
                MFBConstants.intentViewURL,
                String.format(
                    Locale.US,
                    MFBConstants.urlPrivacy,
                    MFBConstants.szIP,
                    nightParam(this)
                )
            )
            activityLauncher!!.launch(i)
        } else if (id == R.id.btnViewTandC) {
            val i = Intent(v.context, ActWebView::class.java)
            i.putExtra(
                MFBConstants.intentViewURL,
                String.format(Locale.US, MFBConstants.urlTandC, MFBConstants.szIP, nightParam(this))
            )
            activityLauncher!!.launch(i)
        }
    }
}