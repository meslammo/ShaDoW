package com.shadow.mobile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** MOD-15.2: Local Android action adapter. Destructive/communication actions stay user-confirmed. */
public final class ShadowMobileActions {
    private ShadowMobileActions() {}

    public static String execute(Activity activity, String command) {
        String original = command == null ? "" : command.trim();
        String x = original.toLowerCase(java.util.Locale.ROOT);
        try {
            if (contains(original, x, "افتح الإعدادات", "افتح الاعدادات", "open settings", "settings")) {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
                return "تم فتح إعدادات الجهاز.";
            }
            if (contains(original, x, "افتح الواي فاي", "افتح wifi", "wifi", "wi-fi")) {
                Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
                activity.startActivity(i);
                return "تم فتح إعدادات Wi-Fi.";
            }
            if (contains(original, x, "افتح البلوتوث", "بلوتوث", "bluetooth")) {
                Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                activity.startActivity(i);
                return "تم فتح إعدادات Bluetooth.";
            }
            if (contains(original, x, "إعدادات التطبيقات", "اعدادات التطبيقات", "app settings")) {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
                return "تم فتح إعدادات التطبيقات.";
            }
            if (contains(original, x, "إشعارات", "اشعارات", "notification settings")) {
                activity.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName()));
                return "تم فتح إعدادات إشعارات SHADOW.";
            }
            if (contains(original, x, "افتح الكاميرا", "الكاميرا", "open camera", "camera")) {
                activity.startActivity(new Intent("android.media.action.IMAGE_CAPTURE"));
                return "تم فتح الكاميرا.";
            }
            if (contains(original, x, "افتح الصور", "الصور", "المعرض", "gallery", "photos")) {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("content://media/external/images/media"));
                i.setType("image/*");
                activity.startActivity(i);
                return "تم فتح الصور.";
            }
            if (contains(original, x, "افتح الملفات", "الملفات", "مدير الملفات", "files", "file manager")) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                activity.startActivity(i);
                return "تم فتح مدير الملفات.";
            }
            if (contains(original, x, "افتح التقويم", "التقويم", "calendar")) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/")));
                return "تم فتح التقويم.";
            }
            if (contains(original, x, "افتح جهات الاتصال", "جهات الاتصال", "contacts")) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/")));
                return "تم فتح جهات الاتصال.";
            }
            if (contains(original, x, "افتح الساعة", "الساعة", "clock")) {
                activity.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setPackage("com.android.deskclock"));
                return "تم فتح الساعة.";
            }
            if (contains(original, x, "افتح الخرائط", "افتح الخريطة", "خرائط", "maps", "open maps")) {
                String query = extractAfter(original, "خرائط", "الخريطة", "maps", "map");
                String uri = query.isEmpty() ? "geo:0,0?q=" : "geo:0,0?q=" + Uri.encode(query);
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
                return query.isEmpty() ? "تم فتح الخرائط." : "تم فتح الخرائط على: " + query;
            }
            if (contains(original, x, "افتح المتصفح", "المتصفح", "browser", "open browser")) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
                return "تم فتح المتصفح.";
            }
            if (contains(original, x, "افتح الموسيقى", "الموسيقى", "music", "player")) {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC);
                activity.startActivity(i);
                return "تم فتح مشغل الموسيقى.";
            }
            if (contains(original, x, "اتصل", "اتصال", "call")) {
                String digits = original.replaceAll("[^0-9+]", "");
                if (digits.length() >= 5) {
                    activity.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits)));
                    return "فتحت شاشة الاتصال بالرقم " + digits + ". لم يتم الاتصال تلقائياً.";
                }
                return "قولّي رقم الهاتف عشان أفتح شاشة الاتصال به.";
            }
            if (contains(original, x, "رسالة", "sms", "text message")) {
                String digits = original.replaceAll("[^0-9+]", "");
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + (digits.length() >= 5 ? digits : "")));
                activity.startActivity(i);
                return "فتحت شاشة الرسائل. لن يتم الإرسال تلقائياً بدون تأكيد منك.";
            }
            if (contains(original, x, "شارك", "share")) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, "SHADOW");
                activity.startActivity(Intent.createChooser(i, "مشاركة عبر SHADOW"));
                return "تم فتح شاشة المشاركة.";
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            return "تعذر تنفيذ الأمر بأمان: " + (msg == null ? e.getClass().getSimpleName() : msg);
        }
        return null;
    }

    private static String extractAfter(String original, String... keys) {
        String lower = original.toLowerCase(java.util.Locale.ROOT);
        for (String key : keys) {
            int p = lower.indexOf(key.toLowerCase(java.util.Locale.ROOT));
            if (p >= 0) {
                String tail = original.substring(p + key.length()).trim();
                if (tail.startsWith(" ") || !tail.isEmpty()) return tail.replaceFirst("^[ :،-]+", "").trim();
            }
        }
        return "";
    }

    private static boolean contains(String original, String lower, String... values) {
        for (String v : values) {
            if (original.contains(v) || lower.contains(v.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }
}
