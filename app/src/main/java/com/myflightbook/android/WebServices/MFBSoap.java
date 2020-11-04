/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2019 MyFlightbook, LLC

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
package com.myflightbook.android.WebServices;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Looper;

import com.myflightbook.android.R;

import org.ksoap2.HeaderProperty;
import org.ksoap2.SoapEnvelope;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.HttpTransportSE;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import Model.MFBConstants;
import androidx.annotation.NonNull;

public class MFBSoap {
    private String m_szLastErr = "";
    private String m_methodName = "";
    private SoapObject m_request = null;

    public interface MFBSoapProgressUpdate {
        void NotifyProgress(int percentageComplete, String szMsg);
    }

    public MFBSoapProgressUpdate m_Progress = null;

    static final String NAMESPACE = "http://myflightbook.com/";

    MFBSoap() {
        super();
    }

    public String getLastError() {
        return m_szLastErr;
    }

    SoapObject setMethod(String szMethod) {
        m_methodName = szMethod;
        m_request = new SoapObject(NAMESPACE, szMethod);
        return m_request;
    }

    void AddMappings(SoapSerializationEnvelope e) {
    }

    public enum NetworkStatus { Unknown, Online, Offline }

    private static NetworkStatus mlastOnlineStatus = NetworkStatus.Unknown;

    public static void startListeningNetwork(Context ctx) {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkRequest.Builder builder = new NetworkRequest.Builder();

            connectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){
                                                                   @Override
                                                                   public void onAvailable(@NonNull Network network) {
                                                                       MFBSoap.mlastOnlineStatus = NetworkStatus.Online;
                                                                   }
                                                                   @Override
                                                                   public void onLost(@NonNull Network network) {
                                                                       MFBSoap.mlastOnlineStatus = NetworkStatus.Offline;
                                                                   }
                                                               }

            );
        }catch (Exception e){
            mlastOnlineStatus = NetworkStatus.Unknown;
        }
    }

    public static Boolean IsOnline(Context ctx) {
        if (mlastOnlineStatus != NetworkStatus.Unknown)
            return (mlastOnlineStatus == NetworkStatus.Online);

        // if we are here, then we have to guess at network status
        if (ctx == null)
            return false;

        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            mlastOnlineStatus = NetworkStatus.Offline;
            return false;
        }

        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            mlastOnlineStatus = NetworkStatus.Offline;
            return false;
        }

        // if on the main thread, and we're here, we need to be optimistic
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            mlastOnlineStatus = NetworkStatus.Online;
            return true;
        }

        Socket sock = null;
        try {
            SocketAddress sockaddr = new InetSocketAddress(MFBConstants.szIP, 80);
            // Create an unbound socket
            sock = new Socket();

            // This method will block no more than timeoutMs.
            // If the timeout occurs, SocketTimeoutException is thrown.
            int timeoutMs = 2000;   // 2 seconds
            sock.connect(sockaddr, timeoutMs);
            mlastOnlineStatus = NetworkStatus.Online;
            return true;
        } catch (Exception ignored) {
        } finally {
            if (sock != null)
                try {
                    sock.close();
                } catch (IOException ignored) {
                }
        }
        mlastOnlineStatus = NetworkStatus.Offline;
        return false;
    }

    Object Invoke(Context c) {
        if (c == null)
            throw new NullPointerException("null Context passed to Invoke");

        setLastError("");
        Object o;

        if (!IsOnline(c)) {
            setLastError(c.getString(R.string.errNoInternet));
            return null;
        }

        if (m_methodName.length() == 0) {
            setLastError("No method name specified");
            return null;
        }

        if (m_request == null) {
            setLastError("No request object set");
            return null;
        }

        SoapSerializationEnvelope envelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
        envelope.dotNet = true;
        // envelope.implicitTypes = true;
        AddMappings(envelope);
        envelope.setOutputSoapObject(m_request);

        String URL = "https://" + MFBConstants.szIP + "/logbook/public/webservice.asmx";
        HttpTransportSE androidHttpTransport = new HttpTransportSE(URL);

        androidHttpTransport.debug = MFBConstants.fIsDebug;

        try {
            List<HeaderProperty> headerList = new ArrayList<>();
            Locale l = Locale.getDefault();
            String szLocale = String.format("%s-%s", l.getLanguage(), l.getCountry());
            HeaderProperty hp = new HeaderProperty("accept-language", szLocale);
            headerList.add(hp);

            androidHttpTransport.call(NAMESPACE + m_methodName, envelope, headerList);
            o = envelope.getResponse();
        } catch (Exception e) {
            String szFault = e.getMessage();
            if (szFault == null)
                szFault = c.getString(R.string.errorSoapError);
            else if (szFault.contains(MFBConstants.szFaultSeparator)) {
                szFault = szFault.substring(szFault.lastIndexOf(MFBConstants.szFaultSeparator) + MFBConstants.szFaultSeparator.length()).replace("System.Exception: ", "");
                int iStartStackTrace = szFault.indexOf("\n ");
                if (iStartStackTrace > 0)
                    szFault = szFault.substring(0, iStartStackTrace);
            }

            setLastError(szFault);

            o = null;
        }

        // Un-comment one or the other lines above - if debug is true - to view raw XML:
//        String sRequestDump = androidHttpTransport.requestDump;
//        String sResponseDump = androidHttpTransport.responseDump;

        return o;
    }

    void setLastError(String sz) {
        this.m_szLastErr = sz;
    }
}
