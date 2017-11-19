/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017 MyFlightbook, LLC

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

import android.util.Log;

import com.myflightbook.android.MFBMain;
import com.myflightbook.android.WebServices.AuthToken;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

public class CustomExceptionHandler implements UncaughtExceptionHandler {

    private UncaughtExceptionHandler defaultUEH;

    private String localPath;

    private String url;

    /* 
     * if any of the parameters is null, the respective functionality 
     * will not be used 
     */
    public CustomExceptionHandler(String localPath, String url) {
        this.localPath = localPath;
        this.url = url;
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
    }

    public void uncaughtException(Thread t, Throwable e) {
        Date dt = new Date();
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        e.printStackTrace(printWriter);
        String szUsername = "(unknown user)";
        if (AuthToken.m_szEmail != null && AuthToken.m_szEmail.length() > 0)
            szUsername = AuthToken.m_szEmail;

        String szVersion = String.format(Locale.getDefault(), "Version %s (%d)\r\n\r\n", MFBMain.versionName, MFBMain.versionCode);

        final String stacktrace = szVersion + szUsername + "       \r\n\r\n" + result.toString() + e.getMessage() + e.getLocalizedMessage();
        printWriter.close();

        Calendar c = new GregorianCalendar();
        c.setTime(dt);

        final String filename = String.format(Locale.getDefault(), "%d%d%d%d%d%d.stacktrace", c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DATE), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));

        if (localPath != null) {
            writeToFile(stacktrace, filename);
        }

        defaultUEH.uncaughtException(t, e);
    }

    private void writeToFile(String stacktrace, String filename) {
        try {
            BufferedWriter bos = new BufferedWriter(new FileWriter(
                    localPath + "/" + filename));
            bos.write(stacktrace);
            bos.flush();
            bos.close();
        } catch (Exception e) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
        }
    }

    public void sendPendingReports() {
        if (localPath == null || localPath.length() == 0)
            return;

        new Thread(() -> {
                File dir = new File(localPath);
                File[] files = dir.listFiles();
                StringBuilder sb = new StringBuilder();
                for (File f : files) {
                    if (!f.getName().endsWith(".stacktrace"))
                        continue;

                    try {
                        BufferedReader br = new BufferedReader(new FileReader(f));
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                            sb.append('\n');
                        }
                        br.close();

                        sendToServer(sb.toString(), f.getName());
                        if (!f.delete())
                            Log.e(MFBConstants.LOG_TAG, "Delete of error report failed");
                    } catch (IOException ex) {
                        Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex));
                    }
                }
            }).start();
    }

    private void sendToServer(String stacktrace, String filename) {
        if (MFBConstants.fIsDebug)
            return;

        String szBoundary = "EXCEPTONBOUNDARY";
        String szBoundaryDivider = String.format("--%s\r\n", szBoundary);

        HttpURLConnection urlConnection = null;

        OutputStream out = null;
        InputStream in;
        try {
            urlConnection = (HttpURLConnection) (new URL(url)).openConnection();

            urlConnection.setDoOutput(true);
            urlConnection.setChunkedStreamingMode(0);
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", String.format("multipart/form-data; boundary=%s", szBoundary));

            out = new BufferedOutputStream(urlConnection.getOutputStream());

            out.write(szBoundaryDivider.getBytes("UTF8"));
            out.write("Content-Disposition: form-data; name=\"filename\"\r\n\r\n".getBytes("UTF8"));
            out.write(String.format("%s\r\n%s", filename, szBoundaryDivider).getBytes("UTF8"));

            out.write("Content-Disposition: form-data; name=\"stacktrace\"\r\n\r\n".getBytes("UTF8"));
            out.write(String.format("%s\r\n%s", stacktrace, szBoundaryDivider).getBytes("UTF8"));

            out.write(String.format("\r\n\r\n--%s--\r\n", szBoundary).getBytes("UTF8"));

            out.flush();

            in = new BufferedInputStream(urlConnection.getInputStream());
            int status = urlConnection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK)
                throw new Exception(String.format(Locale.US, "Bad response - status = %d", status));

            byte[] rgResponse = new byte[1024];
            if (in.read(rgResponse) == 0)
                Log.e(MFBConstants.LOG_TAG, "No bytes read from the response to uploading error report!");

            String sz = new String(rgResponse, "UTF8");
            if (!sz.contains("OK"))
                throw new Exception(sz);
        } catch (Exception ex) {
            Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex));
        } finally {
            if (out != null)
                try {
                    out.close();
                } catch (IOException e) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e));
                }
            if (urlConnection != null)
                try {
                    urlConnection.disconnect();
                } catch (Exception ex) {
                    Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(ex));
                }
        }
    }
}
