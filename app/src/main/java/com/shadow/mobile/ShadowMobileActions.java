package com.shadow.mobile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** MOD-08.16: Safe Android action adapter. Only launches explicit user-requested intents. */
public final class ShadowMobileActions {
    private ShadowMobileActions() {}

    public static String execute(Activity activity, String command) {
        String x = command.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            if (contains(command, x, "افتح الإعدادات", "افتح الاعدادات", "open settings", "settings")) {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
                return "تم فتح إعدادات الجهاز.";
            }
            if (contains(command, x, "افتح الكاميرا", "الكاميرا", "open camera", "camera")) {
                Intent i = new Intent("android.media.action.IMAGE_CAPTURE");
                activity.startActivity(i);
                return "تم فتح الكاميرا.";
            }
            if (contains(command, x, "افتح الخرائط", "افتح الخريطة", "خرائط", "maps", "open maps")) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")));
                return "تم فتح الخرائط.";
            }
            if (contains(command, x, "اتصل", "اتصال", "call")) {
                String digits = command.replaceAll("[^0-9+]", "");
                if (digits.length() >= 5) {
                    activity.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits)));
                    return "فتحت شاشة الاتصال بالرقم " + digits + ".";
                }
                return "قولّي رقم الهاتف عشان أفتح شاشة الاتصال به.";
            }
            if (contains(command, x, "رسالة", "sms", "text message")) {
                String digits = command.replaceAll("[^0-9+]", "");
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + (digits.length() >= 5 ? digits : "")));
                activity.startActivity(i);
                return "فتحت شاشة الرسائل. لن يتم الإرسال تلقائياً.";
            }
        } catch (Exception e) {
            return "تعذر تنفيذ الأمر بأمان: " + e.getMessage();
        }
        return null;
    }

    private static boolean contains(String original, String lower, String... values) {
        for (String v : values) {
            if (original.contains(v) || lower.contains(v.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }
}
