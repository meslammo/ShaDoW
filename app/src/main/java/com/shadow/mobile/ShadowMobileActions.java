package com.shadow.mobile;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

/** MOD-15.3: Local Android action adapter. Destructive/communication actions stay user-confirmed. */
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
                activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                return "تم فتح إعدادات Wi-Fi.";
            }
            if (contains(original, x, "افتح البلوتوث", "بلوتوث", "bluetooth")) {
                activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
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
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setPackage("com.android.deskclock");
                activity.startActivity(i);
                return "تم فتح الساعة.";
            }
            if (contains(original, x, "افتح الخرائط", "افتح الخريطة", "خرائط", "maps", "open maps")) {
                String query = extractAfter(original, "خرائط", "الخريطة", "maps", "map");
                String uri = query.isEmpty() ? "geo:0,0?q=" : "geo:0,0?q=" + Uri.encode(query);
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
                return query.isEmpty() ? "تم فتح الخرائط." : "تم فتح الخرائط على: " + query;
            }
            if (contains(original, x, "ابحث عن", "ابحث في جوجل عن", "search for", "google search")) {
                String q = extractSearch(original);
                if (q.isEmpty()) return "قولّي إيه اللي أبحث عنه.";
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))));
                return "تم فتح نتائج البحث عن: " + q;
            }
            if (contains(original, x, "افتح المتصفح", "المتصفح", "browser", "open browser")) {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
                return "تم فتح المتصفح.";
            }
            if (contains(original, x, "افتح الموسيقى", "الموسيقى", "music", "player")) {
                activity.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC));
                return "تم فتح مشغل الموسيقى.";
            }
            if (contains(original, x, "افتح واتساب", "واتساب", "whatsapp")) {
                if (launchPackage(activity, "com.whatsapp")) return "تم فتح WhatsApp.";
                return "WhatsApp غير مثبت على الجهاز.";
            }
            if (contains(original, x, "افتح تيليجرام", "تيليجرام", "telegram")) {
                if (launchPackage(activity, "org.telegram.messenger")) return "تم فتح Telegram.";
                return "Telegram غير مثبت على الجهاز.";
            }
            if (contains(original, x, "افتح فيسبوك", "فيسبوك", "facebook")) {
                if (launchPackage(activity, "com.facebook.katana")) return "تم فتح Facebook.";
                return "Facebook غير مثبت على الجهاز.";
            }
            if (contains(original, x, "افتح إنستجرام", "افتح انستجرام", "انستجرام", "instagram")) {
                if (launchPackage(activity, "com.instagram.android")) return "تم فتح Instagram.";
                return "Instagram غير مثبت على الجهاز.";
            }
            if (contains(original, x, "افتح يوتيوب", "youtube")) {
                if (launchPackage(activity, "com.google.android.youtube")) return "تم فتح YouTube.";
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")));
                return "تم فتح YouTube في المتصفح.";
            }
            if (contains(original, x, "افتح جوجل", "google")) {
                if (launchPackage(activity, "com.google.android.googlequicksearchbox")) return "تم فتح Google.";
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
                return "تم فتح Google في المتصفح.";
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

    private static boolean launchPackage(Activity activity, String packageName) {
        PackageManager pm = activity.getPackageManager();
        Intent i = pm.getLaunchIntentForPackage(packageName);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(i);
        return true;
    }

    private static String extractSearch(String original) {
        String lower = original.toLowerCase(java.util.Locale.ROOT);
        String[] keys = {"ابحث في جوجل عن", "ابحث عن", "search for", "google search"};
        for (String key : keys) {
            int p = lower.indexOf(key.toLowerCase(java.util.Locale.ROOT));
            if (p >= 0) return original.substring(p + key.length()).replaceFirst("^[ :،-]+", "").trim();
        }
        return "";
    }

    private static String extractAfter(String original, String... keys) {
        String lower = original.toLowerCase(java.util.Locale.ROOT);
        for (String key : keys) {
            int p = lower.indexOf(key.toLowerCase(java.util.Locale.ROOT));
            if (p >= 0) return original.substring(p + key.length()).replaceFirst("^[ :،-]+", "").trim();
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
