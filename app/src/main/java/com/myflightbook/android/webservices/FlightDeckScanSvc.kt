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
package com.myflightbook.android.webservices

import android.content.Context
import android.util.Log
import com.myflightbook.android.marshal.MarshalDate
import com.myflightbook.android.marshal.MarshalDouble
import model.FlightProperty
import model.FlightProperty.Companion.rewritePropertiesForFlight
import model.LatLong
import model.LogbookEntry
import model.MFBConstants
import model.MFBImageInfo
import model.PendingFlight
import org.json.JSONException
import org.json.JSONObject
import org.ksoap2.serialization.PropertyInfo
import org.ksoap2.serialization.SoapObject
import org.ksoap2.serialization.SoapSerializationEnvelope
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/**
 * Opaque result of a POST to ScanFlightDeckImage.
 *
 * The HTTP 200 response body is the server's full ScanResult JSON - a much
 * bigger object (screenType, notes, raw model output, etc.) than the client
 * needs. Only three top-level fields matter here: success, error, and
 * parsedResults (ScanResult.ParsedResults server-side - the simplified
 * ScannedFlight shape: flightNumber/origin/destination/date/out/off/on/in/
 * block). parsedResults is re-serialized back to its own JSON text and kept
 * opaque from there on - the server may change its shape at any time - and
 * is meant to be forwarded as-is into FlightDeckScanSvc.initFromScannedResult(),
 * whose server-side counterpart (InitFromScannedFlightJSON) deserializes it
 * straight into ScannedFlight. Everything else in the response is ignored.
 */
data class ScanFlightDeckResult(
    val success: Boolean,
    val error: String?,
    val parsedResults: String?
)

/**
 * NOTE: instantiate a fresh FlightDeckScanSvc() per scan (same convention as
 * every other *Svc class in this package, e.g. AircraftSvc(), CommitFlightSvc()).
 * MFBSoap's lastError/mRequest/mMethodname are mutable instance state, so a
 * shared/singleton instance would leak state between unrelated calls (e.g. a
 * stale lastError from a previous scan silently reappearing on the next one).
 */
class FlightDeckScanSvc : MFBSoap() {
    companion object {
        private const val FIELD_AUTH_TOKEN = "txtAuthToken"
        private const val FIELD_IMAGE = "imgPicture"
    }

    /**
     * Posts the given image bytes to ScanFlightDeckImage and returns the
     * opaque envelope {success, error, parsedResults}.  This is a raw
     * multipart upload, NOT a SOAP call - it doesn't go through invoke(),
     * so it doesn't touch/depend on lastError.  Performs blocking network
     * I/O - call from a background thread/coroutine (e.g. via
     * ActMFBForm.doAsync, whose inBackground callback already runs on
     * Dispatchers.IO).
     */
    fun scanFlightDeckImage(imageBytes: ByteArray, szFilename: String = "scan.jpg"): ScanFlightDeckResult {
        var urlConnection: HttpURLConnection? = null
        return try {
            val szBase = "https://" + MFBConstants.szIP
            val szBoundary = UUID.randomUUID().toString()
            val szBoundaryDivider = String.format("--%s\r\n", szBoundary)
            val url = URL(szBase + MFBConstants.URL_SCAN_FLIGHT_DECK)
            val conn = url.openConnection() as HttpURLConnection
            urlConnection = conn
            conn.doOutput = true
            conn.setChunkedStreamingMode(0)
            conn.requestMethod = "POST"
            conn.setRequestProperty(
                "Content-Type",
                String.format("multipart/form-data; boundary=%s", szBoundary)
            )
            BufferedOutputStream(conn.outputStream).use { out ->
                out.write(szBoundaryDivider.toByteArray(StandardCharsets.UTF_8))
                out.write(
                    String.format(
                        "Content-Disposition: form-data; name=\"%s\"\r\n\r\n",
                        FIELD_AUTH_TOKEN
                    ).toByteArray(StandardCharsets.UTF_8)
                )
                out.write(
                    String.format("%s\r\n%s", AuthToken.m_szAuthToken, szBoundaryDivider)
                        .toByteArray(StandardCharsets.UTF_8)
                )
                out.write(
                    String.format(
                        Locale.getDefault(),
                        "Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n",
                        FIELD_IMAGE, szFilename
                    ).toByteArray(StandardCharsets.UTF_8)
                )
                out.write(
                    "Content-Type: image/jpeg\r\nContent-Transfer-Encoding: binary\r\n\r\n"
                        .toByteArray(StandardCharsets.UTF_8)
                )
                out.write(imageBytes)
                out.write(
                    String.format("\r\n\r\n--%s--\r\n", szBoundary).toByteArray(StandardCharsets.UTF_8)
                )
                out.flush()
            }

            val status = conn.responseCode
            val stream = if (status == HttpURLConnection.HTTP_OK) conn.inputStream else conn.errorStream
            val szResponse = stream?.let { s -> BufferedInputStream(s).use { it.readBytes() } }
                ?.toString(StandardCharsets.UTF_8) ?: ""

            if (status != HttpURLConnection.HTTP_OK) {
                // Non-200: SafeOp on the server returns the exception message as a plain-text
                // body (not JSON) - use it as-is.
                Log.e(
                    MFBConstants.LOG_TAG,
                    String.format(Locale.US, "ScanFlightDeckImage failed - status = %d: %s", status, szResponse)
                )
                return ScanFlightDeckResult(
                    success = false,
                    error = szResponse.ifEmpty { "Server returned status $status" },
                    parsedResults = null
                )
            }

            // HTTP 200: body is the full ScanResult JSON. A scan can still be a recognized
            // failure here (e.g. "not a flight-deck display") - success lives in the body,
            // not the HTTP status - so pull it, error, and parsedResults out explicitly
            // rather than assuming 200 == success.
            val json = JSONObject(szResponse)
            if (json.optBoolean("success", false)) {
                val parsed = json.opt("parsedResults")
                ScanFlightDeckResult(
                    success = true,
                    error = null,
                    parsedResults = if (parsed == null || parsed == JSONObject.NULL) null else parsed.toString()
                )
            } else {
                ScanFlightDeckResult(
                    success = false,
                    error = if (json.isNull("error")) null else json.optString("error", ""),
                    parsedResults = null
                )
            }
        } catch (ex: JSONException) {
            Log.e(MFBConstants.LOG_TAG, "Error parsing ScanFlightDeckImage response: " + ex.message)
            ScanFlightDeckResult(success = false, error = "Unrecognized response from server", parsedResults = null)
        } catch (ex: Exception) {
            Log.e(MFBConstants.LOG_TAG, "Error scanning flight deck image: " + ex.message)
            ScanFlightDeckResult(success = false, error = ex.message ?: "Unknown error", parsedResults = null)
        } finally {
            urlConnection?.disconnect()
        }
    }

    override fun addMappings(e: SoapSerializationEnvelope) {
        e.addMapping(NAMESPACE, "InitFlightFromFlightDeckScanResult", LogbookEntry::class.java)
        e.addMapping(NAMESPACE, "LogbookEntry", LogbookEntry::class.java)
        e.addMapping(NAMESPACE, "PendingFlight", PendingFlight::class.java)
        e.addMapping(NAMESPACE, "CustomFlightProperty", FlightProperty::class.java)
        e.addMapping(NAMESPACE, "MFBImageInfo", MFBImageInfo::class.java)
        e.addMapping(NAMESPACE, "LatLong", LatLong::class.java)
        val mdt = MarshalDate()
        val md = MarshalDouble()
        mdt.register(e)
        md.register(e)
    }

    /**
     * Calls InitFlightFromFlightDeckScan, which merges the scanned
     * flight-deck data (scanResultJSON - the opaque parsedResults from
     * scanFlightDeckImage) onto le server-side and sends back the updated
     * flight.
     *
     * ksoap2 always deserializes a SOAP response into a generic SoapObject
     * property bag - registering a mapping in addMappings() only tells it
     * how to *serialize* an outgoing LogbookEntry (e.g. the "le" parameter
     * below), it does not make invoke() hand back an actual LogbookEntry
     * instance. So "invoke(c) as LogbookEntry" can never succeed: SoapObject
     * and LogbookEntry are unrelated concrete classes (LogbookEntry doesn't
     * extend SoapObject), so nothing can ever be an instance of both -
     * that's why the compiler flags it as a cast that always fails, not
     * just an unchecked-cast warning. Every other *Svc class in this
     * package instead reconstructs the typed model from the raw SoapObject
     * by hand (e.g. LogbookEntry(so), or so.fromProperties(so)) - see
     * CommitFlightSvc.fCommitFlightForUser for the closest analogue to this
     * method.
     *
     * We mutate le in place via fromProperties() and return that same
     * reference, rather than constructing a new LogbookEntry, because a
     * fresh LogbookEntry(so) would lose everything that never round-trips
     * over SOAP: le's local SQLite row id (idLocalDB - fromProperties()
     * never touches it, so it survives), and - importantly for the pending-
     * flight entry point - le's concrete subtype. If le is actually a
     * PendingFlight, swapping in a plain new LogbookEntry would silently
     * drop its PendingFlight identity (and the pending-specific state that
     * comes with it) mid-edit.
     *
     * On failure, le is returned unchanged and lastError is set (checked
     * automatically by ActMFBForm.doAsync when this instance is passed in
     * as its service).
     */
    fun initFromScannedResult(szAuthToken: String?, le: LogbookEntry, scanResultJSON: String, c: Context): LogbookEntry {
        val request = setMethod("InitFlightFromFlightDeckScan")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("szScannedFlight", scanResultJSON)
        val piLe = PropertyInfo()
        piLe.name = "le"
        piLe.type = "LogbookEntry"
        piLe.value = le
        piLe.namespace = NAMESPACE
        request.addProperty(piLe)

        val r = invoke(c) as? SoapObject ?: return le
        try {
            // Mutate le in place (fromProperties(), not LogbookEntry(r)) rather than swapping in a
            // freshly-constructed object. A fresh LogbookEntry(r) would be a plain LogbookEntry even
            // if le was actually a PendingFlight - silently dropping its PendingFlight identity
            // mid-edit - and le.idLocalDB is already valid by the time we get here (ActNewFlight's
            // scanFlightDeckClicked() calls saveCurrentFlight() before the scan even starts), so
            // there's no need to delete the old local row and create a new one; doing so would also
            // cascade-delete any images already attached to this flight before the scan
            // (LogbookEntry.deleteUnsubmittedFlightFromLocalDB() deletes pending images for the old
            // local id as part of clearing it out).
            le.fromProperties(r)

            // le.rgCustomProperties (flight number / block out / block in from the scan) only exists
            // in memory at this point - unlike an existing/pending flight loaded from the server, or
            // a repeated/reversed flight, this LogbookEntry has never had its properties written to
            // the local FlightProperties table. Persist them now so the next unconditional
            // syncProperties() call anywhere in the app (submitFlightConfirmed(), a property edit,
            // etc.) doesn't reload for this idLocalDB, find nothing, and silently wipe them back to
            // empty. Same fix as menuRepeatFlight/menuReverseFlight and the "view an existing/pending
            // flight" path in ActNewFlight.onCreate.
            rewritePropertiesForFlight(le.idLocalDB, le.rgCustomProperties)
        } catch (ex: Exception) {
            lastError += ex.message
        }
        return le
    }
}
