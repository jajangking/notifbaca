package com.home.notifbaca;

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

public class PauseTile extends TileService {
    private static final String PREF = "cfg";
    private static final String KEY_MUTED = "muted";

    @Override
    public void onTileAdded() {
        refresh();
    }

    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        boolean muted = "1".equals(sp.getString(KEY_MUTED, ""));
        sp.edit().putString(KEY_MUTED, muted ? "" : "1").apply();
        boolean nowMuted = !muted;
        Log.i("NotifBaca", "tile toggle -> " + (nowMuted ? "dibisukan" : "aktif"));
        try {
            Toast.makeText(this, nowMuted
                    ? "NotifBaca dibisukan - notif nggak dibacakan"
                    : "NotifBaca aktif - notif bakal dibacakan",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w("NotifBaca", "toast gagal", e);
        }
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean muted = "1".equals(getSharedPreferences(PREF, MODE_PRIVATE)
                .getString(KEY_MUTED, ""));
        if (muted) {
            t.setState(Tile.STATE_INACTIVE);
            t.setLabel("Dibisukan");
            t.setSubtitle("Tekan buat baca lagi");
            t.setIcon(Icon.createWithResource(this, android.R.drawable.ic_lock_silent_mode));
            t.setContentDescription("NotifBaca sedang dibisukan");
        } else {
            t.setState(Tile.STATE_ACTIVE);
            t.setLabel("Aktif Membaca");
            t.setSubtitle("Tekan buat jeda");
            t.setIcon(Icon.createWithResource(this, android.R.drawable.ic_media_play));
            t.setContentDescription("NotifBaca aktif membacakan");
        }
        t.updateTile();
    }
}