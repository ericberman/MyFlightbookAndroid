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
package com.myflightbook.android.WebServices;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;

import com.myflightbook.android.MFBMain;
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
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import Model.MFBConstants;

public class MFBSoap extends Object {
	private String m_szLastErr = "";
	private String m_methodName = "";
	private SoapObject m_request = null;
	public Object m_Result;

	public interface MFBSoapProgressUpdate {
		void NotifyProgress(int percentageComplete, String szMsg);
	}
	
	public MFBSoapProgressUpdate m_Progress = null;

	public static String NAMESPACE = "http://myflightbook.com/";
	private String PROTOCOL = "http://";
	private String PROTOCOLS = "https://";
	private String URL;
		
	public MFBSoap()
	{
		super();
	}
	
	public String getLastError()
	{
		return m_szLastErr;
	}
	
	public SoapObject setMethod(String szMethod)
	{
		m_methodName = szMethod;
		m_request = new SoapObject(NAMESPACE, szMethod);
		return m_request;
	}
	
	public String getMethod()
	{
		return m_methodName;
	}
	
	public void AddMappings(SoapSerializationEnvelope e)
	{
		
	}
	
	public static Boolean IsOnline()
	{
		Context ctx = MFBMain.GetMainContext();
		if (ctx == null)
			return false;
		
		ConnectivityManager cm = (ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if (info==null || !info.isConnected())
            return false;
        
        // if on the main thread, and we're here, we need to be optimistic
        if (Looper.getMainLooper().getThread() == Thread.currentThread())
        	return true;
        
        Socket sock = null;
        try 
        {
            SocketAddress sockaddr = new InetSocketAddress(MFBConstants.szIP, 80);
            // Create an unbound socket
            sock = new Socket();

            // This method will block no more than timeoutMs.
            // If the timeout occurs, SocketTimeoutException is thrown.
            int timeoutMs = 2000;   // 2 seconds
            sock.connect(sockaddr, timeoutMs);
            return true;
        }
        catch (SocketTimeoutException e) {}
        catch(Exception e){ }
        finally 
        {
        	if (sock != null)
				try {
					sock.close();
				} catch (IOException e) {
				}
        }
        return false;
	}
	
	public Object Invoke()
	{
		setLastError("");
		Object o = null;
		
		if (!IsOnline())
		{
			setLastError(MFBMain.GetMainContext().getString(R.string.errNoInternet));
			return o;
		}
		
		if (m_methodName.length() == 0)
		{
			setLastError("No method name specified");
			return o;
		}
		
		if (m_request == null)
		{
			setLastError("No request object set");
			return o;
		}
		
    	SoapSerializationEnvelope envelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
    	envelope.dotNet = true;
    	// envelope.implicitTypes = true;
    	AddMappings(envelope);
    	envelope.setOutputSoapObject(m_request);

    	URL = ((MFBConstants.fIsDebug && MFBConstants.fDebugLocal) ? PROTOCOL : PROTOCOLS) + MFBConstants.szIP + "/logbook/public/webservice.asmx";
    	HttpTransportSE androidHttpTransport = new HttpTransportSE(URL);

		androidHttpTransport.debug = MFBConstants.fIsDebug;
		
    	try
    	{
    		List<HeaderProperty> headerList = new ArrayList<HeaderProperty>();
    		Locale l = Locale.getDefault();
    		String szLocale = String.format("%s-%s", l.getLanguage(), l.getCountry());
    		HeaderProperty hp = new HeaderProperty("accept-language", szLocale);
    		headerList.add(hp);
    		
    		androidHttpTransport.call(NAMESPACE + m_methodName, envelope, headerList);
    		o = envelope.getResponse();
    	}
    	catch (Exception e)
    	{
    		String szFault = e.getMessage();
    		if (szFault == null)
    			szFault = MFBMain.GetMainContext().getString(R.string.errorSoapError);
    		else if (szFault.contains(MFBConstants.szFaultSeparator))
    		{
    			szFault = szFault.substring(szFault.lastIndexOf(MFBConstants.szFaultSeparator) + MFBConstants.szFaultSeparator.length()).replace("System.Exception: ", "");
    			int iStartStackTrace = szFault.indexOf("\n ");
    			if (iStartStackTrace > 0)
    				szFault = szFault.substring(0, iStartStackTrace);
    		}
    			
    		setLastError(szFault);
    		
    		o = null;
    	}
    	
    	return m_Result = o;
	}

	public void setLastError(String sz) {
		this.m_szLastErr = sz;
	}
}
