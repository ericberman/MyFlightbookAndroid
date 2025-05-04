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
package com.myflightbook.android

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.myflightbook.android.webservices.AircraftSvc
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.CustomPropertyTypesSvc
import com.myflightbook.android.webservices.MFBSoap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.AuthResult

internal class DlgSignIn(private val mCallingActivity: FragmentActivity) : Dialog(
    mCallingActivity, R.style.MFBDialog
), View.OnClickListener {

    private fun postSignIn() {
        val act = mCallingActivity // should never be null
        val c = context

        act.lifecycleScope.launch(Dispatchers.Main) {
            ActMFBForm.doAsync<MFBSoap, Any?>(
                act,
                MFBSoap(),
                c.getString(R.string.prgAircraft),
                {
                    s ->

                    AircraftSvc().getAircraftForUser(AuthToken.m_szAuthToken, c)
                    s.mProgress?.notifyProgress(0, c.getString(R.string.prgCPT))
                    CustomPropertyTypesSvc().getCustomPropertyTypes(AuthToken.m_szAuthToken, false, c)
                },
                { _, _ -> dismiss() }
            )
        }
    }

    // Sign in - if successful will then download Aircraft and then download Properties
    private fun signIn(szUser : String, szPass : String, sz2FA : String?) {
        val act = mCallingActivity // should never be null
        val c = context
        val d = this
        val at = AuthToken()
        // make sure we don't use any existing credentials!!
        at.flushCache()
        // Also flush any cached aircraft when we change users
        AircraftSvc().flushCache()

        act.lifecycleScope.launch(Dispatchers.Main) {
            ActMFBForm.doAsync<AuthToken, AuthResult?>(
                act,
                at,
                c.getString(R.string.prgSigningIn),
                { s -> s.authorizeUser(szUser, szPass, sz2FA, c) },
                { _, result ->
                    if (result!!.authStatus === AuthResult.AuthStatus.TwoFactorCodeRequired) {
                        findViewById<View>(R.id.layout2FA).visibility = View.VISIBLE
                        findViewById<View>(R.id.layoutCredentials).visibility = View.GONE
                        findViewById<View>(R.id.txtWarning).visibility = View.GONE
                        findViewById<View>(R.id.txt2FA).requestFocus()
                    } else {
                        if (result!!.authStatus === AuthResult.AuthStatus.Success) {
                            MFBMain.invalidateAll()
                            postSignIn()
                        } else if (d.findViewById<View>(R.id.layout2FA).visibility != View.VISIBLE)
                            d.findViewById<TextView>(R.id.editPass).text = ""   // clear out password for another try on failure
                    }
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        findViewById<View>(R.id.btnSubmit).setOnClickListener(this)
        findViewById<View>(R.id.btnCancel).setOnClickListener(this)
    }

    override fun onClick(v: View) {
        val id = v.id
        if (id == R.id.btnCancel) {
            dismiss()
            return
        }
        val txtUser = findViewById<EditText>(R.id.editEmail)
        val txtPass = findViewById<EditText>(R.id.editPass)
        val txt2FA = findViewById<EditText>(R.id.txt2FA)

        signIn(txtUser.text.toString(), txtPass.text.toString(), txt2FA.text.toString())
    }

    init {
        setContentView(R.layout.dlgsignin)
        setTitle("Please sign in to MyFlightbook")
        val txtUser = findViewById<EditText>(R.id.editEmail)
        val txtPass = findViewById<EditText>(R.id.editPass)
        txtUser.setText(AuthToken.m_szEmail)
        txtPass.setText(AuthToken.m_szPass)
    }
}