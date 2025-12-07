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
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.TotalsSvc
import model.DecimalEdit
import model.DecimalEdit.Companion.stringForMode
import model.DecimalEdit.EditMode
import model.FlightQuery
import model.MFBUtil.deserializeFromString
import model.MFBUtil.serializeToString
import model.Totals
import model.Totals.NumType
import java.text.DecimalFormat
import java.util.*
import androidx.core.content.edit

class TotalsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TotalsRemoteViewsFactory(this.applicationContext, intent)
    }
}

internal class TotalsRemoteViewsFactory(private val mContext: Context, intent: Intent?) :
    RemoteViewsFactory {
    private var mTotalsItmes: List<Totals> = ArrayList()
    override fun onCreate() {
        // In onCreate() you setup any connections / cursors to your data source. Heavy lifting,
        // for example downloading or creating content etc, should be deferred to onDataSetChanged()
        // or getViewAt(). Taking more than 20 seconds in this call will result in an ANR.
        val prefs = mContext.getSharedPreferences(PREF_TOTALS, Activity.MODE_PRIVATE)
        val szTotals = prefs.getString(PREF_TOTALS_LAST, null)
        if (szTotals != null) {
            val rgti = deserializeFromString<Array<Totals>>(szTotals) ?: arrayOf()
            mTotalsItmes = listOf(*rgti)
        }
    }

    override fun onDestroy() {
        // In onDestroy() you should tear down anything that was setup for your data source,
        // eg. cursors, connections, etc.
        mTotalsItmes = ArrayList()
    }

    override fun getCount(): Int {
        return mTotalsItmes.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        // position will always range from 0 to getCount() - 1.
        // We construct a remote views item based on our widget item xml file, and set the
        // text based on the position.
        val rv = RemoteViews(mContext.packageName, R.layout.widget_totals_item)

        // Next, we set a fill-intent which will be used to fill-in the pending intent template
        // which is set on the collection view in StackWidgetProvider.
        val extras = Bundle()
        val fillInIntent = Intent()
        fillInIntent.putExtras(extras)
        rv.setOnClickFillInIntent(R.id.layoutWidgetTotalsItem, fillInIntent)
        val ti = mTotalsItmes[position]
        val em = if (DecimalEdit.DefaultHHMM) EditMode.HHMM else EditMode.DECIMAL
        rv.setTextColor(R.id.txtTotDescription, mContext.getColor(R.color.textColorPrimary))
        rv.setTextColor(R.id.txtTotSubDescription, mContext.getColor(R.color.textColorPrimary))
        rv.setTextColor(R.id.txtTotValue, mContext.getColor(R.color.textColorPrimary))
        rv.setTextViewText(R.id.txtTotDescription, ti.description)
        rv.setTextViewText(R.id.txtTotSubDescription, ti.subDescription)
        when (ti.numericType) {
            NumType.Integer -> rv.setTextViewText(
                R.id.txtTotValue, String.format(
                    Locale.getDefault(), "%d", ti.value.toInt()
                )
            )
            NumType.Time -> rv.setTextViewText(R.id.txtTotValue, stringForMode(ti.value, em))
            NumType.Decimal -> rv.setTextViewText(
                R.id.txtTotValue,
                stringForMode(ti.value, EditMode.DECIMAL)
            )
            NumType.Currency -> rv.setTextViewText(
                R.id.txtTotValue, DecimalFormat.getCurrencyInstance(
                    Locale.getDefault()
                ).format(ti.value)
            )
        }

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
        val ts = TotalsSvc()
        val rgti = ts.getTotalsForUser(AuthToken.m_szAuthToken, FlightQuery(), mContext)
        mTotalsItmes = listOf(*rgti)
        val prefs = mContext.getSharedPreferences(PREF_TOTALS, Activity.MODE_PRIVATE)
        prefs.edit {
            putString(PREF_TOTALS_LAST, serializeToString(rgti))
        }
    }

    companion object {
        private const val PREF_TOTALS = "prefTotalsWidget"
        private const val PREF_TOTALS_LAST = "prefTotalsWidgetLast"
    }
}