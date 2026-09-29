package com.shadow.mobile;

import android.content.Context;

/**
 * FIX-150.2: owner identity compatibility surface.
 *
 * Shadow no longer uses a master password/passphrase gate. The Android client
 * treats the local owner session as the identity context and leaves action
 * confirmation to the normal governed action policy.
 */
public final class ShadowVoiceIdentityGateway {
    public ShadowVoiceIdentityGateway(Context context) {
        // Kept as a compatibility constructor for existing callers.
    }

    public boolean isEnrolled() { return true; }

    public boolean isAuthenticated() { return true; }

    public boolean isVoiceVerified() { return false; }

    public void markVoiceVerified(long durationMs) { }

    public boolean enroll(String ignored) { return true; }

    public boolean authenticate(String ignored) { return true; }

    public void lock() { }

    public String status() {
        return "Owner identity: Mohamed • session active • passphrase disabled.";
    }

    public boolean isSensitiveCommand(String command) {
        String x = command == null ? "" : command.trim().toLowerCase(java.util.Locale.ROOT);
        String[] keys = {
            "اتصل","مكالمة","اتصال","ابعت رسالة","رسالة","احذف","مسح","ثبت","تثبيت",
            "شراء","دفع","تحويل","github","جيت هاب","تطوير","عدل الكود","عدّل الكود",
            "نفذ على الجهاز","نفذ أمر حساس","صلاحيات","permission","delete","install",
            "purchase","payment","transfer","call","send message","factory reset",
            "smart home","السيارة","العربية","افتح الباب","اقفل الباب"
        };
        for (String k : keys) if (x.contains(k)) return true;
        return false;
    }

    /** Password/passphrase input is intentionally ignored. */
    public String extractPassphrase(String command) { return ""; }
}
