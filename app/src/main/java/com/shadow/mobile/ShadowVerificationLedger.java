package com.shadow.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** MOD-79.2: durable verification receipts without storing the raw request. */
public final class ShadowVerificationLedger {
    private static final String PREFS = "shadow_verification_v1";
    private static final String KEY = "receipts";
    private final SharedPreferences prefs;

    public ShadowVerificationLedger(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void record(String request, String route, boolean verified, String evidence) {
        try {
            org.json.JSONArray all = new org.json.JSONArray(prefs.getString(KEY, "[]"));
            org.json.JSONObject item = new org.json.JSONObject();
            item.put("request_sha256", sha256(request == null ? "" : request));
            item.put("route", route == null ? "" : route);
            item.put("verified", verified);
            item.put("evidence", trim(evidence, 600));
            item.put("timestamp", System.currentTimeMillis());
            all.put(item);
            while (all.length() > 100) all.remove(0);
            prefs.edit().putString(KEY, all.toString()).apply();
        } catch (Exception ignored) {}
    }

    public String snapshot() { return prefs.getString(KEY, "[]"); }

    private static String trim(String value, int max) {
        String s = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(b.length * 2);
            for (byte v : b) out.append(String.format(java.util.Locale.ROOT, "%02x", v));
            return out.toString();
        } catch (Exception e) { return "unavailable"; }
    }
}
