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
package Model;

import java.util.Locale;

import androidx.annotation.NonNull;

public class ApproachDescription {

    public String approachName = "";
    public int approachCount = 0;
    public String runwayName = "";
    public String airportName = "";
    public Boolean addToApproachCount = true;

    public static final String[] ApproachNames = {
            "CONTACT",
            "COPTER",
            "GCA",
            "GLS",
            "ILS",
            "ILS (Cat I)",
            "ILS (Cat II)",
            "ILS (Cat III)",
            "ILS/PRM",
            "JPLAS",
            "LAAS",
            "LDA",
            "LOC",
            "LOC-BC",
            "MLS",
            "NDB",
            "OSAP",
            "PAR",
            "RNAV/GPS",
            "SDF",
            "SRA/ASR",
            "TACAN",
            "TYPE1",
            "TYPE2",
            "TYPE3",
            "TYPE4",
            "TYPEA",
            "TYPEB",
            "VISUAL",
            "VOR",
            "VOR/DME",
            "VOR/DME-ARC"};

    public static final String[] ApproachSuffixes = {"", "-A", "-B", "-C", "-D", "-X", "-Y", "-Z"};

    public static final String[] RunwayNames = {
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18",
            "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34", "35", "36"};

    public static final String[] RunwayModifiers = {"", "L", "R", "C"};

    @NonNull
    public String toString() {
        approachName = approachName.trim();
        runwayName = runwayName.trim();
        airportName = airportName.trim();
        return approachCount == 0 ? "" :
                String.format(Locale.getDefault(), "%d%s%s%s%s",
                        approachCount,
                        approachName.length() > 0 ? "-" + approachName : "",
                        runwayName.length() > 0 ? "-RWY" : "",
                        runwayName,
                        airportName.length() > 0 ? "@" + airportName : "");
    }
}
