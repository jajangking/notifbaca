package com.home.notifbaca;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.util.Log;

// Setelah reboot, sistem seharusnya bind ulang NLS sendiri, tapi di ROM
// agresif sering tidak. Receiver ini memicu rebind eksplisit.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String a = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a);
        boolean updated = Intent.ACTION_MY_PACKAGE_REPLACED.equals(a);
        if (!boot && !updated) return;
        Log.i("NotifBaca", (boot ? "boot" : "update app") + ", minta rebind NLS");
        RebindJobService.schedule(ctx);
        try {
            ComponentName cn = new ComponentName(ctx, NLS.class);
            if (Build.VERSION.SDK_INT >= 24) {
                NotificationListenerService.requestRebind(cn);
            }
        } catch (Exception e) {
            Log.e("NotifBaca", "rebind gagal", e);
        }
    }
}
