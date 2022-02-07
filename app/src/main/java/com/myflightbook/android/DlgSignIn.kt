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
import android.app.ProgressDialog
import android.content.Context
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import com.myflightbook.android.webservices.AircraftSvc
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.CustomPropertyTypesSvc
import com.myflightbook.android.webservices.MFBSoap
import model.Aircraft
import model.AuthResult
import model.CustomPropertyType
import model.MFBConstants
import model.MFBUtil.alert
import model.MFBUtil.showProgress

internal class DlgSignIn(private val m_CallingContext: Context?) : Dialog(
    m_CallingContext!!, R.style.MFBDialog
), View.OnClickListener {
    private class RefreshCPTTask(c: Context?, d: DlgSignIn?) :
        AsyncTask<Void?, Void?, Boolean>() {
        private var mPd: ProgressDialog? = null
        val fAllowCache = true
        var mRgcpt: Array<CustomPropertyType> = arrayOf()
        val mCtxt: AsyncWeakContext<DlgSignIn> = AsyncWeakContext(c, d)
        override fun doInBackground(vararg params: Void?): Boolean {
            val cptSvc = CustomPropertyTypesSvc()
            mRgcpt = cptSvc.getCustomPropertyTypes(
                AuthToken.m_szAuthToken,
                fAllowCache,
                mCtxt.context!!
            )
            return mRgcpt.isNotEmpty()
        }

        override fun onPreExecute() {
            val c = mCtxt.context
            if (c != null) mPd = showProgress(c, c.getString(R.string.prgCPT))
        }

        override fun onPostExecute(b: Boolean) {
            val d = mCtxt.callingActivity
            d?.dismiss()
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
        }

    }

    private class AircraftTask(c: Context?, d: DlgSignIn?) :
        AsyncTask<String?, Void?, MFBSoap>() {
        private var mResult: Array<Aircraft> = arrayOf()
        private var mPd: ProgressDialog? = null
        val mCtxt: AsyncWeakContext<DlgSignIn> = AsyncWeakContext(c, d)
        override fun doInBackground(vararg params: String?): MFBSoap {
            val aircraftSvc = AircraftSvc()
            mResult = aircraftSvc.getAircraftForUser(AuthToken.m_szAuthToken, mCtxt.context)
            return aircraftSvc
        }

        override fun onPreExecute() {
            mPd = showProgress(mCtxt.context, mCtxt.context!!.getString(R.string.prgAircraft))
        }

        override fun onPostExecute(svc: MFBSoap) {
            val rgac = mResult as Array<Aircraft>?
            val c = mCtxt.context
            val d = mCtxt.callingActivity
            if (c != null && (rgac == null || svc.lastError.isNotEmpty())) {
                alert(c, c.getString(R.string.txtError), svc.lastError)
                d?.dismiss()
            }
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            val cptTask = RefreshCPTTask(c, d)
            cptTask.execute()
        }

    }

    private class SignInTask(c: Context?, d: DlgSignIn?, at: AuthToken) :
        AsyncTask<String?, Void?, MFBSoap>() {
        private var mPd: ProgressDialog? = null
        var mResult: AuthResult? = null
        val mSvc: AuthToken = at
        val mCtxt: AsyncWeakContext<DlgSignIn> = AsyncWeakContext(c, d)
        override fun doInBackground(vararg params: String?): MFBSoap {
            mResult = mSvc.authorizeUser(params[0]!!, params[1]!!, params[2]!!, mCtxt.context)
            return mSvc
        }

        override fun onPreExecute() {
            mPd = showProgress(mCtxt.context, mCtxt.context!!.getString(R.string.prgSigningIn))
        }

        override fun onPostExecute(svc: MFBSoap) {
            val c = mCtxt.context
            val d = mCtxt.callingActivity
            if (c == null) return
            if (mResult!!.authStatus === AuthResult.AuthStatus.TwoFactorCodeRequired) {
                d!!.findViewById<View>(R.id.layout2FA).visibility = View.VISIBLE
                d.findViewById<View>(R.id.layoutCredentials).visibility = View.GONE
                d.findViewById<View>(R.id.txtWarning).visibility = View.GONE
            } else {
                if (mResult!!.authStatus === AuthResult.AuthStatus.Success) {
                    MFBMain.invalidateAll()
                    // now download aircraft
                    val act = AircraftTask(c, d)
                    act.execute()
                } else {
                    alert(c, c.getString(R.string.txtError), svc.lastError)
                    if (d!!.findViewById<View>(R.id.layout2FA).visibility != View.VISIBLE) d.dismiss()
                }
            }
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
        }

    }

    public override fun onCreate(savedInstanceState: Bundle?) {
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
        val at = AuthToken()
        // make sure we don't use any existing credentials!!
        at.flushCache()

        // Also flush any cached aircraft when we change users
        val ac = AircraftSvc()
        ac.flushCache()
        val st = SignInTask(m_CallingContext ?: this.context, this, at)
        st.execute(txtUser.text.toString(), txtPass.text.toString(), txt2FA.text.toString())
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