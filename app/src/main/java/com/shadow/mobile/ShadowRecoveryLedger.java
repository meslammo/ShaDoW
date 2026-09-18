package com.shadow.mobile;

import android.content.Context;
import android.content.SharedPreferences;

/** MOD-79.3: checkpoint/recovery ledger for governed Shadow actions. */
public final class ShadowRecoveryLedger {
    private static final String PREFS = "shadow_recovery_v1";
    private final SharedPreferences prefs;

    public ShadowRecoveryLedger(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized String checkpoint(String request, String route, String state) {
        String token = "cp-" + System.currentTimeMillis();
        prefs.edit()
                .putString("last_checkpoint", token)
                .putLong("checkpoint_time", System.currentTimeMillis())
                .putString("checkpoint_route", route == null ? "" : route)
                .putString("checkpoint_request_sha256", sha256(request == null ? "" : request))
                .putString("checkpoint_state", state == null ? "" : state)
                .apply();
        return token;
    }

    public synchronized void failure(String reason) {
        prefs.edit().putString("last_failure", trim(reason, 400)).putLong("failure_time", System.currentTimeMillis()).apply();
    }

    public String status() {
        return "checkpoint=" + prefs.getString("last_checkpoint", "none")
                + " route=" + prefs.getString("checkpoint_route", "none")
                + " state=" + prefs.getString("checkpoint_state", "none")
                + " last_failure=" + prefs.getString("last_failure", "none");
    }

    private static String trim(String value, int max) {
        String s = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(b.length * 2);
            for (byte v : b) out.append(String.format(java.util.Locale.ROOT, "%02x", v));
            return out.toString();
        } catch (Exception e) { return "unavailable"; }
    }
}
