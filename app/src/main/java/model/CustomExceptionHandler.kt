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

import android.util.Log
import com.myflightbook.android.MFBMain
import com.myflightbook.android.webservices.AuthToken
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.*
import java.util.*

class CustomExceptionHandler(private val localPath: String?, private val url: String, private val appkey : String) :
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
                        sendToServer(sb.toString())
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

    private fun sendToServer(stacktrace: String) {
        if (MFBConstants.fIsDebug) return
        var response : Response? = null
        try {
            val client = OkHttpClient()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("szAppToken", appkey)
                .addFormDataPart("stacktrace", stacktrace)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Unexpected response: ${response.message()}")

            val sz = response?.body()?.string() ?: ""

            if (!sz.contains("OK")) throw Exception(sz)
        } catch (ex: Exception) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex))
        } finally {
            response?.body()?.close()
        }
    }

}