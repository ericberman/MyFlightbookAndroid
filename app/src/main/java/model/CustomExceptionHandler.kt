/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2022 MyFlightbook, LLC

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

import android.util.Log
import com.myflightbook.android.MFBMain
import com.myflightbook.android.webservices.AuthToken
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*

class CustomExceptionHandler(private val localPath: String?, private val url: String) :
    Thread.UncaughtExceptionHandler {
    private val defaultUEH: Thread.UncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()!!
    override fun uncaughtException(t: Thread, e: Throwable) {
        val dt = Date()
        val result: Writer = StringWriter()
        val printWriter = PrintWriter(result)
        e.printStackTrace(printWriter)
        var szUsername = "(unknown user)"
        if (AuthToken.m_szEmail.isNotEmpty()) szUsername =
            AuthToken.m_szEmail
        val szVersion = String.format(
            Locale.getDefault(),
            "Version %s (%d)\r\n\r\n",
            MFBMain.versionName,
            MFBMain.versionCode
        )
        val stacktrace = """$szVersion$szUsername       

$result${e.message}${e.localizedMessage}"""
        printWriter.close()
        val c: Calendar = GregorianCalendar()
        c.time = dt
        val filename = String.format(
            Locale.getDefault(),
            "%d%d%d%d%d%d.stacktrace",
            c[Calendar.YEAR],
            c[Calendar.MONTH],
            c[Calendar.DATE],
            c[Calendar.HOUR_OF_DAY],
            c[Calendar.MINUTE],
            c[Calendar.SECOND]
        )
        if (localPath != null) {
            writeToFile(stacktrace, filename)
        }
        defaultUEH.uncaughtException(t, e)
    }

    private fun writeToFile(stacktrace: String, filename: String) {
        try {
            val bos = BufferedWriter(
                FileWriter(
                    "$localPath/$filename"
                )
            )
            bos.write(stacktrace)
            bos.flush()
            bos.close()
        } catch (e: Exception) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
        }
    }

    fun sendPendingReports() {
        if (localPath == null || localPath.isEmpty()) return
        Thread {
            val dir = File(localPath)
            val files = dir.listFiles()

            if (files != null) {
                val sb = StringBuilder()
                for (f in files) {
                    if (!f.name.endsWith(".stacktrace")) continue
                    try {
                        val br = BufferedReader(FileReader(f))
                        var line: String?
                        while (br.readLine().also { line = it } != null) {
                            sb.append(line)
                            sb.append('\n')
                        }
                        br.close()
                        sendToServer(sb.toString(), f.name)
                        if (!f.delete()) Log.e(
                            MFBConstants.LOG_TAG,
                            "Delete of error report failed"
                        )
                    } catch (ex: IOException) {
                        Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex))
                    }
                }
            }
        }.start()
    }

    private fun sendToServer(stacktrace: String, filename: String) {
        if (MFBConstants.fIsDebug) return
        val szBoundary = "EXCEPTONBOUNDARY"
        val szBoundaryDivider = String.format("--%s\r\n", szBoundary)
        var urlConnection: HttpURLConnection? = null
        var out: OutputStream? = null
        val `in`: InputStream
        try {
            urlConnection = URL(url).openConnection() as HttpURLConnection
            urlConnection.doOutput = true
            urlConnection.setChunkedStreamingMode(0)
            urlConnection.requestMethod = "POST"
            urlConnection.setRequestProperty(
                "Content-Type",
                String.format("multipart/form-data; boundary=%s", szBoundary)
            )
            out = BufferedOutputStream(urlConnection.outputStream)
            out.write(szBoundaryDivider.toByteArray(StandardCharsets.UTF_8))
            out.write(
                "Content-Disposition: form-data; name=\"filename\"\r\n\r\n".toByteArray(
                    StandardCharsets.UTF_8
                )
            )
            out.write(
                String.format("%s\r\n%s", filename, szBoundaryDivider).toByteArray(
                    StandardCharsets.UTF_8
                )
            )
            out.write(
                "Content-Disposition: form-data; name=\"stacktrace\"\r\n\r\n".toByteArray(
                    StandardCharsets.UTF_8
                )
            )
            out.write(
                String.format("%s\r\n%s", stacktrace, szBoundaryDivider).toByteArray(
                    StandardCharsets.UTF_8
                )
            )
            out.write(
                String.format("\r\n\r\n--%s--\r\n", szBoundary).toByteArray(StandardCharsets.UTF_8)
            )
            out.flush()
            `in` = BufferedInputStream(urlConnection.inputStream)
            val status = urlConnection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw Exception(
                String.format(
                    Locale.US,
                    "Bad response - status = %d",
                    status
                )
            )
            val rgResponse = ByteArray(1024)
            if (`in`.read(rgResponse) == 0) Log.e(
                MFBConstants.LOG_TAG,
                "No bytes read from the response to uploading error report!"
            )
            val sz = String(rgResponse, StandardCharsets.UTF_8)
            if (!sz.contains("OK")) throw Exception(sz)
        } catch (ex: Exception) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex))
        } finally {
            if (out != null) try {
                out.close()
            } catch (e: IOException) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            if (urlConnection != null) try {
                urlConnection.disconnect()
            } catch (ex: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex))
            }
        }
    }

}