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
package com.myflightbook.android.webservices

import android.content.Context
import org.ksoap2.serialization.SoapObject
import org.ksoap2.serialization.SoapSerializationEnvelope
import com.myflightbook.android.R
import org.ksoap2.SoapEnvelope
import model.MFBConstants
import org.ksoap2.transport.HttpTransportSE
import org.ksoap2.HeaderProperty
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import java.lang.Exception
import java.util.*

open class MFBSoap internal constructor() {
    var lastError = ""
    private var mMethodname = ""
    private var mRequest: SoapObject? = null

    interface MFBSoapProgressUpdate {
        fun notifyProgress(percentageComplete: Int, szMsg: String?)
    }

    @JvmField
    var mProgress: MFBSoapProgressUpdate? = null
    fun setMethod(szMethod: String): SoapObject {
        mMethodname = szMethod
        val obj = SoapObject(NAMESPACE, szMethod)
        mRequest = obj
        return obj
    }

    open fun addMappings(e: SoapSerializationEnvelope) {}
    enum class NetworkStatus {
        Unknown, Online, Offline
    }

    fun invoke(c: Context?): Any? {
        if (c == null) throw NullPointerException("null Context passed to Invoke")
        lastError = ""
        var o: Any?
        if (!isOnline(c)) {
            lastError = c.getString(R.string.errNoInternet)
            return null
        }
        if (mMethodname.isEmpty()) {
            lastError = "No method name specified"
            return null
        }
        if (mRequest == null) {
            lastError = "No request object set"
            return null
        }
        val envelope = SoapSerializationEnvelope(SoapEnvelope.VER11)
        envelope.dotNet = true
        // envelope.implicitTypes = true;
        addMappings(envelope)
        envelope.setOutputSoapObject(mRequest)
        val url = "https://" + MFBConstants.szIP + "/logbook/public/webservice.asmx"
        val androidHttpTransport = HttpTransportSE(url)
        androidHttpTransport.debug = MFBConstants.fIsDebug
        try {
            val headerList: MutableList<HeaderProperty?> = ArrayList()
            val l = Locale.getDefault()
            val szLocale = String.format("%s-%s", l.language, l.country)
            val hp = HeaderProperty("accept-language", szLocale)
            headerList.add(hp)
            androidHttpTransport.call(NAMESPACE + mMethodname, envelope, headerList)
            o = envelope.response
        } catch (e: Exception) {
            val rFault = Regex("^(.*-->\\s*)?(MyFlightbook\\..*Exception:)?(?<err>[^\\n]+).*")
            lastError = (rFault.find(e.message ?: c.getString(R.string.errorSoapError)) ?.groups?.get("err")?.value ?: e.message ?: c.getString(R.string.errorSoapError)).trim()
            o = null
        }

        // Un-comment one or the other lines above - if debug is true - to view raw XML:
//        String sRequestDump = androidHttpTransport.requestDump;
//        String sResponseDump = androidHttpTransport.responseDump;
        return o
    }

    companion object {
        const val NAMESPACE = "http://myflightbook.com/"
        private var mlastOnlineStatus = NetworkStatus.Unknown
        @JvmStatic
        fun startListeningNetwork(ctx: Context) {
            try {
                val connectivityManager =
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.registerDefaultNetworkCallback(object : NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        mlastOnlineStatus = NetworkStatus.Online
                    }

                    override fun onLost(network: Network) {
                        mlastOnlineStatus = NetworkStatus.Offline
                    }
                }
                )
            } catch (e: Exception) {
                mlastOnlineStatus = NetworkStatus.Unknown
            }
        }

        @JvmStatic
        fun isOnline(ctx: Context?): Boolean {
            // use the cached value, if it's known
            if (mlastOnlineStatus != NetworkStatus.Unknown)
                return mlastOnlineStatus == NetworkStatus.Online

            val result: Boolean
            val connectivityManager =
                ctx?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw =
                connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
            result = when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }

            mlastOnlineStatus = if (result) NetworkStatus.Online else NetworkStatus.Offline
            return result
        }
    }
}