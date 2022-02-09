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
package model

import org.ksoap2.serialization.KvmSerializable
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import java.util.*

class AuthResult : SoapableObject, KvmSerializable {
    enum class AuthStatus {
        Failed, TwoFactorCodeRequired, Success
    }

    @JvmField
    var authStatus = AuthStatus.Failed
    var authToken = ""

    private enum class AuthResultProp {
        PIDResult, PIDAuthToken
    }

    constructor()
    constructor(so: SoapObject?) {
        if (so != null) {
            authStatus = AuthStatus.valueOf(so.getProperty("Result").toString())
            authToken = so.getPrimitivePropertySafelyAsString("AuthToken")
        }
    }

    override fun toProperties(so: SoapObject) {
        so.addProperty("Result", authStatus)
        so.addProperty("AuthToken", authToken)
    }

    public override fun fromProperties(so: SoapObject) {
        authToken = so.getPrimitivePropertySafelyAsString("AuthToken")
        authStatus = AuthStatus.valueOf(so.getProperty("Result").toString())
    }

    override fun getPropertyCount(): Int {
        return AuthResultProp.values().size
    }

    override fun getProperty(i: Int): Any {
        return when (AuthResultProp.values()[i]) {
            AuthResultProp.PIDResult -> authStatus
            AuthResultProp.PIDAuthToken -> authToken
        }
    }

    override fun setProperty(i: Int, value: Any) {
        val prop = AuthResultProp.values()[i]
        val sz = value.toString()
        when (prop) {
            AuthResultProp.PIDAuthToken -> authToken = sz
            AuthResultProp.PIDResult -> authStatus = AuthStatus.valueOf(sz)
        }
    }

    override fun getPropertyInfo(i: Int, h: Hashtable<*, *>?, pi: PropertyInfo) {
        when (AuthResultProp.values()[i]) {
            AuthResultProp.PIDAuthToken -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "AuthToken"
            }
            AuthResultProp.PIDResult -> {
                pi.type = PropertyInfo.STRING_CLASS
                pi.name = "Result"
            }
        }
    }
}