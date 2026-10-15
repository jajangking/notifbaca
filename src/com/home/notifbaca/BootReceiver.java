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
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(a)) return;
        Log.i("NotifBaca", "boot, minta rebind NLS");
        try {
            ComponentName cn = new ComponentName(ctx, NLS.class);
            if (Build.VERSION.SDK_INT >= 24) {
                NotificationListenerService.requestRebind(cn);
            }
        } catch (Exception e) {
            Log.e("NotifBaca", "rebind boot gagal", e);
        }
    }
}
