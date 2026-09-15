package com.shadow.mobile;

import java.util.Locale;

/** MOD-54: keep Online as the primary channel while routing deterministic device actions to the governed local Core. */
public final class ShadowOnlineExecutionRouter {
    private ShadowOnlineExecutionRouter() {}

    public static boolean requiresLocalExecution(String command) {
        if (command == null) return false;
        String x = command.trim().toLowerCase(Locale.ROOT);
        if (x.isEmpty()) return false;
        return containsAny(x,
                "شغل الفلاش", "شغل الكشاف", "افتح الفلاش", "اقفل الفلاش", "اقفل الكشاف", "اطفي الفلاش",
                "flashlight", "torch", "ارفع الصوت", "علي الصوت", "على الصوت", "زود الصوت", "وطي الصوت", "خفض الصوت", "قلل الصوت", "volume up", "volume down", "mute", "اكتم الصوت",
                "افتح الإعدادات", "افتح الاعدادات", "open settings", "افتح الواي فاي", "افتح wifi", "wifi", "افتح البلوتوث", "بلوتوث", "bluetooth",
                "افتح الكاميرا", "الكاميرا", "open camera", "افتح الصور", "الصور", "المعرض", "gallery", "photos", "افتح الملفات", "الملفات", "مدير الملفات", "file manager",
                "افتح التقويم", "التقويم", "calendar", "افتح جهات الاتصال", "جهات الاتصال", "contacts", "افتح الساعة", "الساعة", "clock",
                "افتح الخرائط", "افتح الخريطة", "خرائط", "maps", "افتح المتصفح", "المتصفح", "browser", "افتح الموسيقى", "الموسيقى", "music", "player",
                "افتح واتساب", "واتساب", "whatsapp", "افتح تيليجرام", "تيليجرام", "telegram", "افتح فيسبوك", "فيسبوك", "facebook", "افتح انستجرام", "انستجرام", "instagram", "افتح يوتيوب", "youtube", "افتح جوجل", "google",
                "افتح ", "open ", "اتصل ", "كلم ", "call ", "رسالة", "sms", "text message", "شارك", "share",
                "راداربوت", "رادار بوت", "radarbot", "كاميرات الطريق", "كاميرات السرعة", "شغل الرادار",
                "الآلة الحاسبة", "الاله الحاسبه", "الحاسبة", "الحاسبه", "calculator", "calc", "تطبيق الحاسبة",
                "حالة النت", "حالة الإنترنت", "حالة الانترنت", "network status", "internet status",
                "احسب ", "calculate ", "الوقت", "الساعة", "التاريخ", "النهارده", "اليوم",
                "حالة الجهاز", "جهازي", "system status", "حالة النظام");
    }

    private static boolean containsAny(String x, String... values) {
        for (String value : values) if (x.contains(value)) return true;
        return false;
    }
}
