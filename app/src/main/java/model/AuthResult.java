/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2020-2022 MyFlightbook, LLC

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
package model;

import org.ksoap2.serialization.KvmSerializable;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;

import java.util.Hashtable;

public class AuthResult extends SoapableObject implements KvmSerializable {
    public enum AuthStatus {Failed, TwoFactorCodeRequired, Success}

    public AuthStatus authStatus = AuthStatus.Failed;
    public String authToken = "";

    private enum AuthResultProp {
        pidResult, pidAuthToken
    }

    public AuthResult() {
    }

    public AuthResult(SoapObject so) {
        authStatus = AuthStatus.valueOf(so.getProperty("Result").toString());
        authToken = so.getPrimitivePropertySafelyAsString("AuthToken");
    }

    public void ToProperties(SoapObject so) {
        so.addProperty("Result", authStatus);
        so.addProperty("AuthToken", authToken);
    }

    public void FromProperties(SoapObject so) {
        authToken = so.getPrimitivePropertySafelyAsString("AuthToken");
        authStatus = AuthStatus.valueOf(so.getProperty("Result").toString());
    }

    public int getPropertyCount() {
        return AuthResultProp.values().length;
    }

    public Object getProperty(int i) {
        AuthResultProp prop = AuthResultProp.values()[i];
        switch (prop) {
            case pidResult:
                return authStatus;
            case pidAuthToken:
                return authToken;
            default:
                return null;
        }
    }

    public void setProperty(int i, Object value) {
        AuthResultProp prop = AuthResultProp.values()[i];
        String sz = value.toString();
        switch (prop) {
            case pidAuthToken:
                authToken = sz;
                break;
            case pidResult:
                authStatus = AuthStatus.valueOf(sz);
                break;
            default:
                break;
        }
    }

    public void getPropertyInfo(int i, Hashtable h, PropertyInfo pi) {
        AuthResultProp prop = AuthResultProp.values()[i];
        switch (prop) {
            case pidAuthToken:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "AuthToken";
                break;
            case pidResult:
                pi.type = PropertyInfo.STRING_CLASS;
                pi.name = "Result";
                break;
            default:
                break;
        }
    }
}
