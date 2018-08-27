/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2018 MyFlightbook, LLC

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

public class MFBConstants {

    public static final Boolean fIsDebug = false;   // Set to true to use one of the debug servers specified below
    public static final Boolean fFakeGPS = false;   // Set to true to simulate GPS when hitting "Engine Start"
    public static final Boolean fDebugLocal = false;    // If debug, this specifies a local (IP-based or LocalHost based) debug server, thus suppressing https
    public static final Boolean fFakePix = false;

    private static final String szIPDebug = "developer.myflightbook.com";
    private static final String szIPDebugRoam = "developer.myflightbook.com";
    private static final String szIPRelease = "myflightbook.com";

    // Configuration constants
    @SuppressWarnings("ConstantConditions")
    public static final String szIP = fIsDebug ? (fDebugLocal ? szIPDebug : szIPDebugRoam) : szIPRelease;

    // DB Versioning
    public static final int DBVersionMain = 24;
    public static final int DBVersionAirports = 25;

    // To set the DB version in Sqlite: PRAGMA user_version = x.  BE SURE TO DO THIS OR ELSE COPY DATABASE WILL NOT WORK
    // To read it: PRAGMA user_version

    // images
    static final String szURL_FlightPicture = "/logbook/public/uploadpicture.aspx";
    static final String szURL_AircraftPicture = "/logbook/public/uploadairplanepicture.aspx?id=1";
    static final String szIMG_KEY_Flight = "idFlight";
    static final String szIMG_KEY_Aircraft = "txtAircraft";

    public static final double MPS_TO_KNOTS = 1.94384449;
    public static final double METERS_TO_FEET = 3.2808399;
    public static final double METERS_TO_NM = (1.0 / 1852.0);
    public static final double MS_PER_HOUR = (1000.0 * 60 * 60); // ms per hour

    public static final double NM_FOR_CROSS_COUNTRY = 50.0;

    // speeds for distinguishing takeoff/landing
    // we want some hysteresis here, so set the take-off speed higher than the
    // landing speed
    private static final double MIN_SAMPLE_RATE_TAXI_DEBUG = 10000; // Sample rate in milliseconds
    private static final double MIN_SAMPLE_RATE_AIRBORNE_DEBUG = 3000;

    private static final double MIN_SAMPLE_RATE_TAXI_RELEASE = 10000;
    private static final double MIN_SAMPLE_RATE_AIRBORNE_RELEASE = 3000;

    static final int FULL_STOP_SPEED = 5; // 5kts or less is full stop

    @SuppressWarnings("ConstantConditions")
    static final double MIN_SAMPLE_RATE_TAXI = fIsDebug ? MIN_SAMPLE_RATE_TAXI_DEBUG
            : MIN_SAMPLE_RATE_TAXI_RELEASE;
    @SuppressWarnings("ConstantConditions")
    public static final double MIN_SAMPLE_RATE_AIRBORNE = fIsDebug ? MIN_SAMPLE_RATE_AIRBORNE_DEBUG
            : MIN_SAMPLE_RATE_AIRBORNE_RELEASE;

    // minimum horizontal accuracy for us not to throw things out.
    static final float MIN_ACCURACY = 50.0f;

    // Number of supposedly valid GPS samples to ignore after a wake-up
    static final int BOGUS_SAMPLE_COUNT = 1;

    // Soap constants
    public static final String szFaultSeparator = "--->";

    // Tabs
    public static final String tabCurrency = "tabCurrency";
    public static final String tabRecents = "tabRecents";
    public static final String tabTotals = "tabTotals";
    public static final String tabNewFlight = "tabNewFlight";
    public static final String tabOptions = "tabOptions";
    public static final String tabAircraft = "tabAircraft";
    public static final String tabVisitedAirports = "tabVisitedAirports";
    public static final String tabTraining = "tabTraining";

    // intents
    public static final String intentViewURL = "com.myflightbook.android.URL";
    public static final String intentViewTempFile = "com.myflightbook.android.TempFile";

    // URLs
    // TODO: These need to be branded and, in some cases, URLencoded.
    public static final String urlPrivacy = "http://MyFlightbook.com/logbook/public/privacy.aspx?naked=1";
    public static final String urlTandC = "http://myflightbook.com/logbook/Public/TandC.aspx?naked=1";
    public static final String urlContact = "http://myflightbook.com/logbook/public/ContactMe.aspx?email=%s&subj=%s&noCap=1&naked=1";
    public static final String urlSign = "%s://%s/logbook/public/SignEntry.aspx?idFlight=%d&auth=%s&naked=1";
    public static final String urlFacebook = "http://www.facebook.com/pages/MyFlightbook/145794653106";
    public static final String urlTwitter = "http://twitter.com/myflightbook";
    public static final String urlCrashReport = "http://%s/logbook/public/CrashReport.aspx";
    public static final String urlSupport = "https://%s/logbook/public/authredir.aspx?u=%s&p=%s&d=donate&naked=1";
    public static final String urlPreferences = "https://%s/logbook/public/authredir.aspx?u=%s&p=%s&d=profile&naked=1";
    public static final String urlTraining = "%s://%s/logbook/public/authredir.aspx?u=%s&p=%s&d=%s&naked=1";
    public static final String urlAircraftSchedule = "https://%s/logbook/public/authredir.aspx?u=%s&p=%s&d=aircraftschedule&naked=1&ac=%d";
    public static final String urlFAQ = "http://myflightbook.com/logbook/public/FAQ.aspx?naked=1";

    // Formatting strings
    static final String TIMESTAMP = "yyyy-MM-dd HH:mm:ss";

    public static final String LOG_TAG = "MFBAndroid";
}
