/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2020 MyFlightbook, LLC

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
package com.myflightbook.android.WebServices;

import android.content.Context;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapPrimitive;

import Model.AuthResult;
import Model.DBCache;


/**
 * @author ericbe
 */
public class AuthToken extends MFBSoap {

    public static String m_szAuthToken = "";
    public static String m_szEmail = "";
    public static String m_szPass = "";
    private static final String TABLE_AUTH = "Authentications";
    public static String APPTOKEN = "";

    public static Boolean FIsValid() {
        return (m_szAuthToken.length() > 0); // need to check for expiration too
    }

    public void FlushCache() {
        DBCache dbc = new DBCache();
        dbc.flushCache(TABLE_AUTH, false);
    }

    public Boolean FHasCredentials() {
        return (m_szEmail.length() > 0) && (m_szPass.length() > 0);
    }

    // checks our auth status, refreshes it if needed
    public Boolean RefreshAuthorization(Context c) {
        // if no email/password, then we MUST return false because
        // we need to display UI to the user
        // try to authorize.  This will use cache if cache is valid, but will
        // attempt a refresh if possible.  Otherwise it is silent.
        if (!FHasCredentials() || m_szAuthToken == null || m_szAuthToken.length() == 0)
            return false;

        if (HasValidCache())
            return true;

        if (!MFBSoap.IsOnline(c))
            return false;

        // if it is invalid, or if it is valid but we should retry, try
        // the SOAP call
        SoapObject Request = setMethod("RefreshAuthToken");
        Request.addProperty("szAppToken", APPTOKEN);
        Request.addProperty("szUser", m_szEmail);
        Request.addProperty("szPass", m_szPass);
        Request.addProperty("szPreviousToken", m_szAuthToken);

        try {
            SoapPrimitive sp = (SoapPrimitive) Invoke(c);

            String szResult = sp.toString();
            if (szResult.length() == 0)
                return false;

            DBCache dbc = new DBCache();
            m_szAuthToken = szResult;

            // success!
            dbc.updateCache(TABLE_AUTH);
            if (dbc.errorString.length() > 0) {
                setLastError(dbc.errorString);
                return true;
            }
            return true;

        } catch (Exception e) {
            setLastError("Refresh auth failed.  Please check your email address and password.  " + getLastError());
            return false;
        }
    }

    public Boolean HasValidCache() {
        DBCache dbc = new DBCache();

        DBCache.DBCacheStatus dbcs = dbc.Status(TABLE_AUTH);

        // if the cached token is valid and unexpired, use it.
        return (dbcs == DBCache.DBCacheStatus.VALID && FIsValid());
    }

    public AuthResult Authorize(String szUser, String szPass, String sz2fa, Context c) {
        DBCache dbc = new DBCache();

        // flush the cache preemptively if the saved authtoken isn't actually valid
        // (i.e., even if it hasn't expired but for some reason has been deleted)
        if (!FIsValid())
            FlushCache();

        // if it is invalid, or if it is valid but we should retry, try
        // the SOAP call
        SoapObject Request = setMethod("AuthTokenForUserNew");
        Request.addProperty("szAppToken", APPTOKEN);
        Request.addProperty("szUser", szUser);
        Request.addProperty("szPass", szPass);
        Request.addProperty("sz2FactorAuth", sz2fa);

        m_szEmail = szUser;
        m_szPass = szPass;

        AuthResult result;
        try {
            result = new AuthResult((SoapObject) Invoke(c));

            if (result.authStatus == AuthResult.AuthStatus.Success) {
                m_szAuthToken = result.authToken;
                // success!
                dbc.updateCache(TABLE_AUTH);
                if (dbc.errorString.length() > 0) {
                    setLastError(dbc.errorString);
                }
            }
        } catch (Exception e) {
            result = new AuthResult();
            m_szAuthToken = "";
        }

        return result;
    }
}
