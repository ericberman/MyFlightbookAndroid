/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2026 MyFlightbook, LLC

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

import org.ksoap2.serialization.SoapObject

class PendingFlight : LogbookEntry {
    constructor() : super() {
        mPendingID = ""
    }

    constructor(so: SoapObject) : super() {
        fromProperties(so)
        if (mPendingID.isNotEmpty()) idFlight = 0
    }

    override fun fromProperties(so: SoapObject) {
        super.fromProperties(so)
        // Not every SOAP response this gets called against is PendingFlight-shaped - e.g.
        // InitFlightFromFlightDeckScan's WSDL declares its return type as plain LogbookEntry, so a
        // response from it never has a "PendingID" property at all, even when the SoapObject we're
        // handed value/fromProperties on is a PendingFlight instance. so.getProperty() throws
        // "Unknown Property" when a key is simply absent (as opposed to present-but-empty), so use
        // the safe accessor here and leave mPendingID as it was when the response doesn't carry one,
        // rather than throwing away everything super.fromProperties(so) just populated.
        val szPendingID = so.getPropertySafelyAsString("PendingID")
        if (szPendingID.isNotEmpty()) mPendingID = szPendingID
    }

    override fun toProperties(so: SoapObject) {
        super.toProperties(so)
        if (mPendingID.isNotEmpty()) so.addProperty("PendingID", mPendingID)
    }

    fun getPendingID() : String {
        return mPendingID
    }
}