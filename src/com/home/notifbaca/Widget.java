package com.home.notifbaca;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * Widget kecil di layar utama.
 *
 * Selain jadi tombol pintas ke aplikasi, widget ini punya fungsi penting di
 * ROM Infinix/Transsion (XOS): layanan "Usf_Hiber" membebaskan aplikasi dari
 * pembekuan proses hanya kalau aplikasi punya widget aktif di home screen
 * (importantReason=widget). Tanpa widget, seluruh UID aplikasi dibekukan
 * beberapa detik setelah layar utama ditekan, jadi NotificationListenerService
 * berhenti menerima notifikasi meskipun sudah memakai foreground service.
 */
public class Widget extends AppWidgetProvider {

    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, Widget.class));
        if (ids.length == 0) return;
        new Widget().onUpdate(context, manager, ids);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        boolean on = NLS.isAlive() && NLS.isConnected();
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_notifbaca);
        views.setTextViewText(R.id.widget_status, context.getString(
                on ? R.string.widget_on : R.string.widget_off));

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);

        for (int id : ids) manager.updateAppWidget(id, views);
    }
}
