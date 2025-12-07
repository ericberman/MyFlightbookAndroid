/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2019-2025 MyFlightbook, LLC

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
// This code adapted from https://android.googlesource.com/platform/development/+/master/samples/StackWidget/src/com/example/android/stackwidget/StackWidgetService.java
package com.myflightbook.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.CurrencySvc
import model.CurrencyStatusItem
import model.MFBUtil.deserializeFromString
import model.MFBUtil.serializeToString
import androidx.core.content.edit

class CurrencyWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CurrencyRemoteViewsFactory(this.applicationContext, intent)
    }
}

internal class CurrencyRemoteViewsFactory(private val mContext: Context, intent: Intent?) :
    RemoteViewsFactory {
    private var mCurrencyItems: List<CurrencyStatusItem> = ArrayList()
    override fun onCreate() {
        // In onCreate() you setup any connections / cursors to your data source. Heavy lifting,
        // for example downloading or creating content etc, should be deferred to onDataSetChanged()
        // or getViewAt(). Taking more than 20 seconds in this call will result in an ANR.
        val prefs = mContext.getSharedPreferences(PREF_CURRENCY, Activity.MODE_PRIVATE)
        val szTotals = prefs.getString(PREF_CURRENCY_LAST, null)
        if (szTotals != null) {
            val rgcsi = deserializeFromString<Array<CurrencyStatusItem>>(szTotals) ?: arrayOf()
            mCurrencyItems = listOf(*rgcsi)
        }
    }

    override fun onDestroy() {
        // In onDestroy() you should tear down anything that was setup for your data source,
        // eg. cursors, connections, etc.
        mCurrencyItems = ArrayList()
    }

    override fun getCount(): Int {
        return mCurrencyItems.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        // position will always range from 0 to getCount() - 1.
        // We construct a remote views item based on our widget item xml file, and set the
        // text based on the position.
        val rv = RemoteViews(mContext.packageName, R.layout.widget_currency_item)

        // Next, we set a fill-intent which will be used to fill-in the pending intent template
        // which is set on the collection view in StackWidgetProvider.
        val extras = Bundle()
        val fillInIntent = Intent()
        fillInIntent.putExtras(extras)
        rv.setOnClickFillInIntent(R.id.layoutWidgetCurrencyItem, fillInIntent)
        val csi = mCurrencyItems[position]
        rv.setTextColor(R.id.txtCsiDiscrepancy, mContext.getColor(R.color.textColorPrimary))
        rv.setTextColor(R.id.txtCsiAttribute, mContext.getColor(R.color.textColorPrimary))
        rv.setTextViewText(R.id.txtCsiDiscrepancy, csi.discrepancy)
        rv.setTextViewText(R.id.txtCsiAttribute, csi.attribute)
        val value = SpannableString(csi.value)
        when {
            csi.status.compareTo("NotCurrent") == 0 -> {
                rv.setTextColor(R.id.txtCsiValue, Color.RED)
                value.setSpan(StyleSpan(Typeface.BOLD), 0, csi.value.length, 0)
            }
            csi.status.compareTo("GettingClose") == 0 -> {
                rv.setTextColor(R.id.txtCsiValue, Color.argb(255, 0, 128, 255))
            }
            csi.status.compareTo("NoDate") == 0 -> {
                value.setSpan(StyleSpan(Typeface.BOLD), 0, csi.value.length, 0)
                rv.setTextColor(R.id.txtCsiValue, mContext.getColor(R.color.textColorPrimary))
            }
            else -> rv.setTextColor(R.id.txtCsiValue, mContext.getColor(R.color.currencyGreen))
        }
        rv.setTextViewText(R.id.txtCsiValue, value)

        // Return the remote views object.
        return rv
    }

    override fun getLoadingView(): RemoteViews? {
        // You can create a custom loading view (for instance when getViewAt() is slow.) If you
        // return null here, you will get the default loading view.
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun onDataSetChanged() {
        // This is triggered when you call AppWidgetManager notifyAppWidgetViewDataChanged
        // on the collection view corresponding to this factory. You can do heaving lifting in
        // here, synchronously. For example, if you need to process an image, fetch something
        // from the network, etc., it is ok to do it here, synchronously. The widget will remain
        // in its current state while work is being done here, so you don't need to worry about
        // locking up the widget.
        if (AuthToken.m_szAuthToken == null || AuthToken.m_szAuthToken!!.isEmpty()) return
        val cs = CurrencySvc()
        val rgcsi = cs.getCurrencyForUser(AuthToken.m_szAuthToken, mContext)
        mCurrencyItems = listOf(*rgcsi)
        val prefs = mContext.getSharedPreferences(PREF_CURRENCY, Activity.MODE_PRIVATE)
        prefs.edit {
            putString(PREF_CURRENCY_LAST, serializeToString(rgcsi))
        }
    }

    companion object {
        private const val PREF_CURRENCY = "prefCurrencyWidget"
        private const val PREF_CURRENCY_LAST = "prefCurrencyWidgetLast"
    }
}