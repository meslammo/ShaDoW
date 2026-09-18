package com.shadow.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** MOD-74.17: restore the opt-in wake-word service after device boot. */
public final class ShadowWakeBootReceiver extends BroadcastReceiver {
    private static final String PREFS = "shadow_voice_prefs";
    private static final String KEY_WAKE_ENABLED = "wake_enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        boolean enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_WAKE_ENABLED, false);
        if (enabled) {
            try {
                ShadowWakeWordService.start(context.getApplicationContext());
            } catch (Throwable ignored) {
                // OS/OEM restrictions may prevent auto-start; the service can still be
                // started from the Shadow UI.
            }
        }
    }
}
