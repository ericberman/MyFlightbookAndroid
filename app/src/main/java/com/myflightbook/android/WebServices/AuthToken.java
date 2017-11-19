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
package com.myflightbook.android.WebServices;

import android.content.Context;

import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapPrimitive;

import Model.DBCache;


/**
 * @author ericbe
 */
public class AuthToken extends MFBSoap {

    public static String m_szAuthToken = "";
    public static String m_szEmail = "";
    public static String m_szPass = "";
    private static String TABLE_AUTH = "Authentications";
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
        return FHasCredentials() && Authorize(m_szEmail, m_szPass, c).length() > 0;
    }

    public Boolean HasValidCache() {
        DBCache dbc = new DBCache();

        DBCache.DBCacheStatus dbcs = dbc.Status(TABLE_AUTH);

        // if the cached token is valid and unexpired, use it.
        return (dbcs == DBCache.DBCacheStatus.VALID && FIsValid());
    }

    public String Authorize(String szUser, String szPass, Context c) {
        DBCache dbc = new DBCache();

        DBCache.DBCacheStatus dbcs = dbc.Status(TABLE_AUTH);

        if (dbc.errorString.length() > 0) {
            setLastError(dbc.errorString);
            return "";
        }

        // if the cached token is valid and unexpired, use it.
        if (dbcs == DBCache.DBCacheStatus.VALID && FIsValid())
            return m_szAuthToken;

        // flush the cache preemptively if the saved authtoken isn't actually valid
        // (i.e., even if it hasn't expired but for some reason has been deleted)
        if (!FIsValid())
            FlushCache();

        // if it is invalid, or if it is valid but we should retry, try
        // the SOAP call
        SoapObject Request = setMethod("AuthTokenForUser");
        Request.addProperty("szAppToken", APPTOKEN);
        Request.addProperty("szUser", szUser);
        Request.addProperty("szPass", szPass);

        m_szEmail = szUser;
        m_szPass = szPass;

        try {
            SoapPrimitive sp = (SoapPrimitive) Invoke(c);
            m_szAuthToken = sp.toString();

            // success!
            dbc.updateCache(TABLE_AUTH);
            if (dbc.errorString.length() > 0) {
                setLastError(dbc.errorString);
                return "";
            }
        } catch (Exception e) {
            // if the cache is still valid, use it despite the failure.
            if (dbcs == DBCache.DBCacheStatus.VALID_BUT_RETRY && m_szAuthToken.length() > 0)
                return m_szAuthToken;

            FlushCache(); // we may have been VALID_BUT_RETRY before, but we're invalid now.
            m_szAuthToken = "";
            setLastError("Authentication failed.  Please check your email address and password.  " + getLastError());

        }

        return m_szAuthToken;
    }
}
