package com.home.notifbaca;

import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

public class PauseTile extends TileService {
    private static final String PREF = "cfg";
    private static final String KEY_MUTED = "muted";

    @Override
    public void onTileAdded() {
        updateTileState();
    }

    @Override
    public void onStartListening() {
        updateTileState();
    }

    @Override
    public void onClick() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        boolean muted = "1".equals(sp.getString(KEY_MUTED, ""));
        sp.edit().putString(KEY_MUTED, muted ? "" : "1").apply();
        Log.i("NotifBaca", "tile toggle -> " + (muted ? "aktif" : "jeda"));
        updateTileState();
    }

    private void updateTileState() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean muted = "1".equals(getSharedPreferences(PREF, MODE_PRIVATE)
                .getString(KEY_MUTED, ""));
        if (muted) {
            t.setState(Tile.STATE_INACTIVE);
            t.setLabel("Jeda");
            t.setSubtitle("Notif dibisukan");
            t.setIcon(Icon.createWithResource(this, android.R.drawable.ic_lock_silent_mode));
            t.setContentDescription("NotifBaca dijeda");
        } else {
            t.setState(Tile.STATE_ACTIVE);
            t.setLabel("Baca");
            t.setSubtitle("Notif aktif");
            t.setIcon(Icon.createWithResource(this, android.R.drawable.ic_media_play));
            t.setContentDescription("NotifBaca aktif");
        }
        t.updateTile();
    }
}