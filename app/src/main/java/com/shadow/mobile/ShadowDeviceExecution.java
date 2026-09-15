package com.shadow.mobile;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import java.util.Locale;

/** MOD-59: concrete Android intents/system adapters; unsupported direct toggles open the proper system panel. */
public final class ShadowDeviceExecution {
    private ShadowDeviceExecution() {}
    public static String execute(Activity a, String raw) {
        if (raw == null) return null;
        String x = raw.trim().toLowerCase(Locale.ROOT);
        if (x.isEmpty()) return null;
        try {
            if (has(x,"افتح الإعدادات","افتح الاعدادات","open settings")) return launch(a,new Intent(Settings.ACTION_SETTINGS),"فتحت الإعدادات.");
            if (has(x,"wifi","واي فاي","الواي فاي")) return launch(a,new Intent(Settings.ACTION_WIFI_SETTINGS),"فتحت إعدادات Wi‑Fi.");
            if (has(x,"bluetooth","بلوتوث")) return launch(a,new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),"فتحت إعدادات Bluetooth.");
            if (has(x,"افتح الكاميرا","الكاميرا","open camera")) return launch(a,new Intent("android.media.action.IMAGE_CAPTURE"),"فتحت الكاميرا.");
            if (has(x,"افتح الصور","الصور","المعرض","gallery","photos")) return launch(a,new Intent(Intent.ACTION_VIEW, Uri.parse("content://media/external/images/media")),"فتحت الصور.");
            if (has(x,"افتح الملفات","الملفات","مدير الملفات","file manager")) { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); return launch(a,i,"فتحت مدير الملفات."); }
            if (has(x,"افتح التقويم","التقويم","calendar")) return launch(a,new Intent(Intent.ACTION_VIEW,Uri.parse("content://com.android.calendar/time/")),"فتحت التقويم.");
            if (has(x,"جهات الاتصال","contacts")) return launch(a,new Intent(Intent.ACTION_VIEW,Uri.parse("content://contacts/people/")),"فتحت جهات الاتصال.");
            if (has(x,"افتح الساعة","الساعة","clock")) return launch(a,new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CLOCK),"فتحت الساعة.");
            if (has(x,"افتح الخرائط","افتح الخريطة","خرائط","maps")) return launch(a,new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q=")),"فتحت الخرائط.");
            if (has(x,"افتح المتصفح","المتصفح","browser")) return launch(a,new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com")),"فتحت المتصفح.");
            if (has(x,"افتح الموسيقى","الموسيقى","music","player")) return launch(a,new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),"فتحت تطبيق الموسيقى.");
            if (has(x,"افتح واتساب","واتساب","whatsapp")) return webOrPackage(a,"com.whatsapp","https://wa.me/","واتساب");
            if (has(x,"افتح تيليجرام","تيليجرام","telegram")) return webOrPackage(a,"org.telegram.messenger","https://t.me/","تيليجرام");
            if (has(x,"افتح فيسبوك","فيسبوك","facebook")) return webOrPackage(a,"com.facebook.katana","https://facebook.com","فيسبوك");
            if (has(x,"افتح انستجرام","انستجرام","instagram")) return webOrPackage(a,"com.instagram.android","https://instagram.com","إنستجرام");
            if (has(x,"افتح يوتيوب","youtube")) return webOrPackage(a,"com.google.android.youtube","https://youtube.com","YouTube");
            if (has(x,"افتح جوجل","google")) return launch(a,new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com")),"فتحت Google.");
            if (has(x,"شارك","share")) { Intent i=new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT,"SHADOW"); return launchChooser(a,i,"فتحت قائمة المشاركة."); }
            if (has(x,"رسالة","sms","text message")) { Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")); return launch(a,i,"فتحت الرسائل."); }
            if (has(x,"رفع الصوت","علي الصوت","على الصوت","زود الصوت","volume up")) { adjust(a,AudioManager.ADJUST_RAISE); return "رفعت مستوى الصوت. ✓"; }
            if (has(x,"وطي الصوت","خفض الصوت","قلل الصوت","volume down")) { adjust(a,AudioManager.ADJUST_LOWER); return "وطّيت مستوى الصوت. ✓"; }
            if (has(x,"اكتم الصوت","mute")) { adjust(a,AudioManager.ADJUST_MUTE); return "كتمت الصوت. ✓"; }
            if (has(x,"شغل الفلاش","شغل الكشاف","افتح الفلاش","flashlight","torch")) return "FLASHLIGHT: يحتاج تنفيذ CameraManager + صلاحية/حالة جهاز مناسبة؛ لم أزعم نجاحاً بدون التحقق من الحالة الفعلية.";
            if (has(x,"اقفل الفلاش","اطفي الفلاش","اقفل الكشاف")) return "FLASHLIGHT: الإيقاف المباشر يعتمد على حالة الكاميرا الحالية؛ لم أزعم نجاحاً بدون حالة فعلية.";
        } catch (Throwable e) { return "تعذر تنفيذ الأمر على الجهاز: " + e.getClass().getSimpleName(); }
        return null;
    }
    private static void adjust(Activity a,int direction){ AudioManager am=(AudioManager)a.getSystemService(Activity.AUDIO_SERVICE); if(am!=null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC,direction,AudioManager.FLAG_SHOW_UI); }
    private static String webOrPackage(Activity a,String pkg,String web,String name){ try { Intent i=a.getPackageManager().getLaunchIntentForPackage(pkg); if(i!=null)return launch(a,i,"فتحت "+name+"."); } catch(Exception ignored){} return launch(a,new Intent(Intent.ACTION_VIEW,Uri.parse(web)),"فتحت رابط "+name+" في المتصفح."); }
    private static String launch(Activity a,Intent i,String ok){ a.startActivity(i); return ok; }
    private static String launchChooser(Activity a,Intent i,String ok){ a.startActivity(Intent.createChooser(i,"SHADOW")); return ok; }
    private static boolean has(String x,String... v){for(String s:v)if(x.contains(s))return true;return false;}
}
