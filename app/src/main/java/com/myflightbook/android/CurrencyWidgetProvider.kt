/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2019-2022 MyFlightbook, LLC

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
package com.myflightbook.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class CurrencyWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MFBMain.ACTION_VIEW_CURRENCY) context.startActivity(
            Intent(
                context,
                MFBMain::class.java
            ).setAction(intent.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            // Set up the intent that starts the ListViewService, which will
            // provide the views for this collection.
            val intent = Intent(context, CurrencyWidgetService::class.java)

            // Instantiate the RemoteViews object for the app widget layout.
            val rv = RemoteViews(context.packageName, R.layout.currency_widget)

            // Set up the RemoteViews object to use a RemoteViews adapter.
            // This adapter connects
            // to a RemoteViewsService  through the specified intent.
            rv.setRemoteAdapter(android.R.id.list, intent)

            // Trigger listview item click
            val startActivityIntent = Intent(
                context,
                CurrencyWidgetProvider::class.java
            ).setAction(MFBMain.ACTION_VIEW_CURRENCY)
            intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
            val startActivityPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                startActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setPendingIntentTemplate(android.R.id.list, startActivityPendingIntent)

            // The empty view is displayed when the collection has no items.
            // It should be in the same layout used to instantiate the RemoteViews  object above.
            rv.setEmptyView(android.R.id.list, R.id.empty_currency)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, android.R.id.list)
            appWidgetManager.updateAppWidget(appWidgetId, rv)
        }
    }
}