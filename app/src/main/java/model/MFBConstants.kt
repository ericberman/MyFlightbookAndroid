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
package model

import android.content.Context
import android.content.res.Configuration
import com.myflightbook.android.webservices.AuthToken
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.*

object MFBConstants {
    const val IS_DEBUG = false // Set to true to use one of the debug servers specified below
    const val FAKE_GPS = false // Set to true to simulate GPS when hitting "Engine Start"
    private const val DEBUG_LOCAL =
        false // If debug, this specifies a local (IP-based or LocalHost based) debug server, thus suppressing https
    private const val IP_DEBUG = "developer.myflightbook.com"
    private const val IP_DEBUG_ROAM = "staging.myflightbook.com"    // Note that you can use 10.0.2.2 to reference the host's localhost.
    private const val IP_RELEASE = "myflightbook.com"

    // Configuration constants
    @JvmField
    val szIP = if (IS_DEBUG) if (DEBUG_LOCAL) IP_DEBUG else IP_DEBUG_ROAM else IP_RELEASE

    // DB Versioning
    const val DB_VERSION_MAIN = 36
    const val DB_VERSION_AIRPORTS = 59

    // To set the DB version in Sqlite: PRAGMA user_version = x.  BE SURE TO DO THIS OR ELSE COPY DATABASE WILL NOT WORK
    // To read it: PRAGMA user_version
    // images
    const val URL_FLIGHT_PICTURE = "/logbook/public/uploadpicture.aspx"
    const val URL_AIRPLANE_PICTURE = "/logbook/public/uploadairplanepicture.aspx?id=1"
    const val IMG_KEY_FLIGHT = "idFlight"
    const val IMG_KEY_AIRCRAFT = "txtAircraft"
    const val MPS_TO_KNOTS = 1.94384449
    const val MPS_TO_MPH = 2.23694
    const val MPS_TO_KPH = 3.6000059687997
    const val METERS_TO_FEET = 3.2808399
    const val METERS_TO_NM = 1.0 / 1852.0
    const val MS_PER_HOUR = 1000.0 * 60 * 60 // ms per hour
    const val NM_FOR_CROSS_COUNTRY = 50.0

    // speeds for distinguishing takeoff/landing
    // we want some hysteresis here, so set the take-off speed higher than the
    // landing speed
    private const val MIN_SAMPLE_RATE_TAXI_DEBUG = 10000.0 // Sample rate in milliseconds
    private const val MIN_SAMPLE_RATE_AIRBORNE_DEBUG = 3000.0
    private const val MIN_SAMPLE_RATE_TAXI_RELEASE = 10000.0
    private const val MIN_SAMPLE_RATE_AIRBORNE_RELEASE = 3000.0
    const val FULL_STOP_SPEED = 5 // 5kts or less is full stop
    @JvmField
    val MIN_SAMPLE_RATE_TAXI =
        if (IS_DEBUG) MIN_SAMPLE_RATE_TAXI_DEBUG else MIN_SAMPLE_RATE_TAXI_RELEASE
    @JvmField
    val MIN_SAMPLE_RATE_AIRBORNE =
        if (IS_DEBUG) MIN_SAMPLE_RATE_AIRBORNE_DEBUG else MIN_SAMPLE_RATE_AIRBORNE_RELEASE

    // minimum horizontal accuracy for us not to throw things out.
    const val MIN_ACCURACY = 50.0f

    // Number of supposedly valid GPS samples to ignore after a wake-up
    const val BOGUS_SAMPLE_COUNT = 1

    // intents
    const val INTENT_VIEW_URL = "com.myflightbook.android.URL"
    const val INTENT_VIEW_TEMPFILE = "com.myflightbook.android.TempFile"

    // URLs
    // TODO: These need to be branded and, in some cases, URLencoded.
    const val URI_PRIVACY = "https://%s/logbook/mvc/pub/Privacy?naked=1&%s"
    const val URI_TERMS_AND_CONDITIONS = "https://%s/logbook/mvc/pub/TandC?naked=1&%s"
    const val URI_FACEBOOK = "https://www.facebook.com/pages/MyFlightbook/145794653106"
    const val URI_CRASH_REPORT = "https://%s/logbook/mvc/pub/CrashReport"
    private const val URL_PARAM_NO_NIGHT = "night=no"
    private const val URL_PARAM_NIGHT = "night=yes"
    private const val URL_AUTH_REDIR_BASE =
        "https://%s/logbook/mvc/auth/authredir?u=%s&p=%s&naked=%s&%s"

    // Formatting strings
    const val TIMESTAMP = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    const val LOG_TAG = "MFBAndroid"

    // Utilities
    @JvmStatic
    fun nightParam(c: Context?): String {
        if (c == null) return URL_PARAM_NO_NIGHT
        val currentNightMode = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) URL_PARAM_NIGHT else URL_PARAM_NO_NIGHT
    }

    @JvmStatic
    @JvmOverloads
    fun authRedirWithParams(szParams: String?, c: Context?, fUseNight: Boolean = true, fNaked : Boolean = true): String {
        return if (szParams == null) "" else try {
            String.format(
                Locale.US,
                URL_AUTH_REDIR_BASE,
                szIP,
                URLEncoder.encode(AuthToken.m_szEmail, "UTF-8"),
                URLEncoder.encode(AuthToken.m_szPass, "UTF-8"),
                if (fNaked) "1" else "0",
                if (fUseNight) nightParam(c) else URL_PARAM_NO_NIGHT
            ) + if (szParams.isEmpty()) "" else "&$szParams"
        } catch (_: UnsupportedEncodingException) {
            ""
        }
    }
}