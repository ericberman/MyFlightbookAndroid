/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2019 MyFlightbook, LLC

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

package com.myflightbook.android;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.myflightbook.android.WebServices.AuthToken;
import com.myflightbook.android.WebServices.TotalsSvc;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import Model.DecimalEdit;
import Model.DecimalEdit.EditMode;
import Model.FlightQuery;
import Model.Totals;


public class TotalsWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TotalsRemoteViewsFactory(this.getApplicationContext(), intent);
    }
}

class TotalsRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private List<Totals> mTotalsItmes = new ArrayList<>();
    final private Context mContext;

    TotalsRemoteViewsFactory(Context context, Intent intent) {
        mContext = context;
    }
    public void onCreate() {
        // In onCreate() you setup any connections / cursors to your data source. Heavy lifting,
        // for example downloading or creating content etc, should be deferred to onDataSetChanged()
        // or getViewAt(). Taking more than 20 seconds in this call will result in an ANR.
    }

    public void onDestroy() {
        // In onDestroy() you should tear down anything that was setup for your data source,
        // eg. cursors, connections, etc.
        mTotalsItmes.clear();
    }
    public int getCount() {
        return mTotalsItmes.size();
    }
    public RemoteViews getViewAt(int position) {
        // position will always range from 0 to getCount() - 1.
        // We construct a remote views item based on our widget item xml file, and set the
        // text based on the position.
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.widget_totals_item);

        // Next, we set a fill-intent which will be used to fill-in the pending intent template
        // which is set on the collection view in StackWidgetProvider.
        Bundle extras = new Bundle();
        Intent fillInIntent = new Intent();
        fillInIntent.putExtras(extras);
        rv.setOnClickFillInIntent(R.id.layoutWidgetTotalsItem, fillInIntent);

        Totals ti = mTotalsItmes.get(position);

        EditMode em = DecimalEdit.DefaultHHMM ? EditMode.HHMM : EditMode.DECIMAL;

        rv.setTextViewText(R.id.txtTotDescription, ti.Description);
        rv.setTextViewText(R.id.txtTotSubDescription, ti.SubDescription);
        switch (ti.NumericType) {
            case Integer:
                rv.setTextViewText(R.id.txtTotValue, String.format(Locale.getDefault(), "%d", (int) ti.Value));
                break;
            case Time:
                rv.setTextViewText(R.id.txtTotValue, DecimalEdit.StringForMode(ti.Value, em));
                break;
            case Decimal:
                rv.setTextViewText(R.id.txtTotValue, DecimalEdit.StringForMode(ti.Value, EditMode.DECIMAL));
                break;
            case Currency:
                rv.setTextViewText(R.id.txtTotValue, DecimalFormat.getCurrencyInstance(Locale.getDefault()).format(ti.Value));
                break;
        }

        // Return the remote views object.
        return rv;
    }
    public RemoteViews getLoadingView() {
        // You can create a custom loading view (for instance when getViewAt() is slow.) If you
        // return null here, you will get the default loading view.
        return null;
    }
    public int getViewTypeCount() {
        return 1;
    }
    public long getItemId(int position) {
        return position;
    }
    public boolean hasStableIds() {
        return true;
    }
    public void onDataSetChanged() {
        // This is triggered when you call AppWidgetManager notifyAppWidgetViewDataChanged
        // on the collection view corresponding to this factory. You can do heaving lifting in
        // here, synchronously. For example, if you need to process an image, fetch something
        // from the network, etc., it is ok to do it here, synchronously. The widget will remain
        // in its current state while work is being done here, so you don't need to worry about
        // locking up the widget.
        if (AuthToken.m_szAuthToken == null || AuthToken.m_szAuthToken.length() == 0)
            return;

        TotalsSvc ts = new TotalsSvc();
        mTotalsItmes = Arrays.asList(ts.TotalsForUser(AuthToken.m_szAuthToken, new FlightQuery(), mContext));
    }
}
