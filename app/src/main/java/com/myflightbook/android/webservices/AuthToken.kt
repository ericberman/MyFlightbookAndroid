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
package com.myflightbook.android.webservices

import android.content.Context
import model.DBCache
import org.ksoap2.serialization.SoapObject
import org.ksoap2.serialization.SoapPrimitive
import model.DBCache.DBCacheStatus
import model.AuthResult
import model.PackAndGo
import java.lang.Exception

/**
 * @author ericbe
 */
class AuthToken : MFBSoap() {
    private fun hasCredentials(): Boolean {
        return m_szEmail.isNotEmpty() && m_szPass.isNotEmpty()
    }

    // checks our auth status, refreshes it if needed
    fun refreshAuthorization(c: Context?): Boolean {
        // if no email/password, then we MUST return false because
        // we need to display UI to the user
        // try to authorize.  This will use cache if cache is valid, but will
        // attempt a refresh if possible.  Otherwise it is silent.
        if (!hasCredentials() || m_szAuthToken == null || m_szAuthToken!!.isEmpty()) return false
        if (hasValidCache()) return true
        if (!isOnline(c)) return false

        // if it is invalid, or if it is valid but we should retry, try
        // the SOAP call
        val request = setMethod("RefreshAuthToken")
        request.addProperty("szAppToken", APPTOKEN)
        request.addProperty("szUser", m_szEmail)
        request.addProperty("szPass", m_szPass)
        request.addProperty("szPreviousToken", m_szAuthToken)
        return try {
            val sp = invoke(c) as SoapPrimitive
            val szResult = sp.toString()
            if (szResult.isEmpty()) return false
            val dbc = DBCache()
            m_szAuthToken = szResult

            // success!
            dbc.updateCache(TABLE_AUTH)
            if (dbc.errorString.isNotEmpty()) {
                lastError = dbc.errorString
                return true
            }
            true
        } catch (e: Exception) {
            lastError =
                "Refresh auth failed.  Please check your email address and password.  $lastError"
            false
        }
    }

    fun hasValidCache(): Boolean {
        val dbc = DBCache()
        val dbcs = dbc.status(TABLE_AUTH)

        // if the cached token is valid and unexpired, use it.
        return dbcs == DBCacheStatus.VALID && isValid()
    }

    fun authorizeUser(szUser: String, szPass: String, sz2fa: String?, c: Context?): AuthResult {
        val dbc = DBCache()

        // flush the cache preemptively if the saved authtoken isn't actually valid
        // (i.e., even if it hasn't expired but for some reason has been deleted)
        if (!isValid()) flushCache()

        // if it is invalid, or if it is valid but we should retry, try
        // the SOAP call
        val request = setMethod("AuthTokenForUserNew")
        request.addProperty("szAppToken", APPTOKEN)
        request.addProperty("szUser", szUser)
        request.addProperty("szPass", szPass)
        request.addProperty("sz2FactorAuth", sz2fa)
        m_szEmail = szUser
        m_szPass = szPass
        var result: AuthResult
        try {
            result = AuthResult(invoke(c) as SoapObject)
            if (result.authStatus == AuthResult.AuthStatus.Success) {
                m_szAuthToken = result.authToken
                // success!
                dbc.updateCache(TABLE_AUTH)
                if (dbc.errorString.isNotEmpty()) {
                    lastError = dbc.errorString
                }
            }
        } catch (e: Exception) {
            result = AuthResult()
            m_szAuthToken = ""
        }
        return result
    }

    companion object {
        @JvmField
        var m_szAuthToken: String? = ""
        @JvmField
        var m_szEmail = ""
        @JvmField
        var m_szPass = ""
        private const val TABLE_AUTH = "Authentications"
        @JvmField
        var APPTOKEN = ""
        @JvmField
        var isDeniedAgeGate = false
        @JvmStatic
        fun isValid(): Boolean {
            return m_szAuthToken!!.isNotEmpty() // need to check for expiration too
        }
        @JvmStatic
        fun signOut() {
            m_szPass = ""
            m_szEmail = m_szPass
            m_szAuthToken = m_szEmail
            flushCache()
        }
        fun flushCache() {
            val dbc = DBCache()
            dbc.flushCache(TABLE_AUTH, false)
        }
    }
}