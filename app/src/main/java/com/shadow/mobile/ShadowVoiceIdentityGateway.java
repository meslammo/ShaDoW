package com.shadow.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * MOD-56.1: local owner-identity gate for SHADOW.
 *
 * The Android SpeechRecognizer gives us recognized text, not a speaker embedding.
 * Therefore this stage implements the secure passphrase/session layer without
 * falsely claiming biometric voiceprint verification. Raw-audio speaker
 * verification is intentionally left for the dedicated voiceprint engine.
 */
public final class ShadowVoiceIdentityGateway {
    private static final String PREFS = "shadow_voice_identity_v1";
    private static final String KEY_HASH = "owner_passphrase_sha256";
    private static final String KEY_AUTH_UNTIL = "owner_authenticated_until";
    private static final String KEY_VOICE_VERIFIED_UNTIL = "owner_voice_verified_until";
    private static final long SESSION_MS = 10 * 60 * 1000L;
    private final SharedPreferences prefs;

    public ShadowVoiceIdentityGateway(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isEnrolled() {
        String h = prefs.getString(KEY_HASH, "");
        return h != null && !h.isEmpty();
    }

    public boolean isAuthenticated() {
        return System.currentTimeMillis() < prefs.getLong(KEY_AUTH_UNTIL, 0L);
    }

    public boolean isVoiceVerified() {
        return System.currentTimeMillis() < prefs.getLong(KEY_VOICE_VERIFIED_UNTIL, 0L);
    }

    public void markVoiceVerified(long durationMs) {
        long until = System.currentTimeMillis() + Math.max(60_000L, Math.min(durationMs, SESSION_MS));
        prefs.edit().putLong(KEY_VOICE_VERIFIED_UNTIL, until).apply();
    }

    public boolean enroll(String passphrase) {
        String normalized = normalize(passphrase);
        if (normalized.length() < 6) return false;
        prefs.edit().putString(KEY_HASH, sha256(normalized)).putLong(KEY_AUTH_UNTIL, 0L).apply();
        return true;
    }

    public boolean authenticate(String passphrase) {
        if (!isEnrolled()) return false;
        String normalized = normalize(passphrase);
        String expected = prefs.getString(KEY_HASH, "");
        if (expected.isEmpty() || !constantTimeEquals(expected, sha256(normalized))) return false;
        prefs.edit().putLong(KEY_AUTH_UNTIL, System.currentTimeMillis() + SESSION_MS).apply();
        return true;
    }

    public void lock() {
        prefs.edit().putLong(KEY_AUTH_UNTIL, 0L).putLong(KEY_VOICE_VERIFIED_UNTIL, 0L).apply();
    }

    public String status() {
        if (isVoiceVerified()) return "Master voiceprint verified for the current session.";
        if (isAuthenticated()) return "Master identity authenticated for the current session.";
        if (!isEnrolled()) return "Master passphrase not enrolled; voiceprint verification requires a configured online provider.";
        return "Master passphrase enrolled; authentication required for sensitive actions.";
    }

    public boolean isSensitiveCommand(String command) {
        String x = normalize(command);
        String[] keys = {
            "اتصل", "مكالمة", "اتصال", "ابعت رسالة", "رسالة", "احذف", "مسح", "ثبت", "تثبيت",
            "شراء", "دفع", "تحويل", "github", "جيت هاب", "تطوير", "عدل الكود", "عدّل الكود",
            "نفذ على الجهاز", "نفذ أمر حساس", "صلاحيات", "permission", "delete", "install",
            "purchase", "payment", "transfer", "call", "send message", "factory reset", "smart home",
            "السيارة", "العربية", "افتح الباب", "اقفل الباب"
        };
        for (String k : keys) if (x.contains(k)) return true;
        return false;
    }

    public String extractPassphrase(String command) {
        String x = command == null ? "" : command.trim();
        String[] prefixes = {
            "كلمة السر:", "كلمه السر:", "كلمة السر ", "كلمه السر ",
            "passphrase:", "passphrase ", "password:", "password "
        };
        String lower = x.toLowerCase(Locale.ROOT);
        for (String prefix : prefixes) {
            int i = lower.indexOf(prefix.toLowerCase(Locale.ROOT));
            if (i >= 0) return x.substring(i + prefix.length()).trim();
        }
        return "";
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(b.length * 2);
            for (byte v : b) out.append(String.format(Locale.ROOT, "%02x", v));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
