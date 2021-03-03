/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2021 MyFlightbook, LLC

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

package Model;

import org.ksoap2.serialization.SoapObject;

public class PendingFlight extends LogbookEntry {

    public String getPendingID() { return pendingID; }

    public PendingFlight() {
        super();
    }

    public PendingFlight(SoapObject o) {
        super(o);
    }

    @Override
    public void FromProperties(SoapObject so) {
        super.FromProperties(so);
        pendingID = so.getProperty("PendingID").toString();
    }

    @Override
    public void ToProperties(SoapObject so) {
        super.ToProperties(so);
        if (pendingID != null && pendingID.length()>0)
            so.addProperty("PendingID", pendingID);
    }
}
