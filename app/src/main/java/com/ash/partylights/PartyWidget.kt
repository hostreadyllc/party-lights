package com.ash.partylights

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: one tap starts the light show. */
class PartyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_START)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val views = RemoteViews(context.packageName, R.layout.widget_party).apply {
            setOnClickPendingIntent(R.id.widget_root, pi)
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
