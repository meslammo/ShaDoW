package com.shadow.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.ContactsContract;
import java.util.List;

/** MOD-59: concrete device adapters first; MOD-58 governed Core remains the authorization gate. */
public final class ShadowPhoneController {
    private static final String RADARBOT_PACKAGE = "com.vialsoft.radarbot_free";
    private static ShadowPhoneUseAgent agent;
    private static String pendingSensitiveCommand;
    private ShadowPhoneController(){}

    public static String execute(Activity activity,String command){
        if(command==null)return null;
        String original=command.trim(); if(original.isEmpty())return null;
        String x=original.toLowerCase(java.util.Locale.ROOT);
        ShadowVoiceIdentityGateway identity=new ShadowVoiceIdentityGateway(activity);
        if(isApproval(x)&&pendingSensitiveCommand!=null){String approved=pendingSensitiveCommand;pendingSensitiveCommand=null;return executeInternal(activity,approved,true);}
        if(!ShadowOnlineExecutionRouter.requiresLocalExecution(original)) return null;
        try {
            if(x.startsWith("اتصل ب")||x.startsWith("اتصل بـ")||x.startsWith("كلم ")||x.startsWith("call ")) pendingSensitiveCommand=original;
            String governed = new ShadowCore(activity).handle(original, identity.isAuthenticated(), true, "android-local");
            if(governed != null && !governed.trim().isEmpty()) return governed;
        } catch (Throwable ignored) {}
        String concrete = ShadowDeviceExecution.execute(activity, original);
        if(concrete != null && !concrete.trim().isEmpty()) return concrete;
        if(isRadarbotCommand(x)) return launchRadarbot(activity);
        if(isCalculatorCommand(x)) return launchCalculator(activity);
        if(isNetworkCommand(x)) return ShadowNetworkManager.describe(activity);
        if(x.startsWith("اتصل ب")||x.startsWith("اتصل بـ")||x.startsWith("كلم ")||x.startsWith("call ")){
            pendingSensitiveCommand=original;
            return "🧠 SHADOW • محتاج موافقتك قبل إجراء مكالمة\n\n👤 الشخص: "+original.replaceFirst("(?i)^(اتصل\\s*ب[ـ ]?|كلم\\s+|call\\s+)","").trim()+"\n🔐 الإجراء حساس وغير قابل للتراجع بعد البدء.\n\nاكتب «وافق» أو «نفّذ» للتنفيذ.";
        }
        if(agent==null)agent=new ShadowPhoneUseAgent(activity);
        if(agent.looksLikePhoneTask(original)){
            ShadowPhoneUseAgent.Plan plan=agent.plan(original);
            String direct=agent.execute(original);
            if(direct!=null&&!"APPROVAL_REQUIRED".equals(direct))return direct;
            if(plan.approvalRequired){pendingSensitiveCommand=original;return renderPlan(plan)+"\n\n🔐 موافقة مطلوبة: اكتب «وافق» أو «نفّذ».";}
            return renderPlan(plan)+"\n\n❌ مش قادر أنفذ الأمر على الشاشة الحالية.";
        }
        return executeInternal(activity,original,false);
    }
    private static String executeInternal(Activity activity,String original,boolean approved){
        String x=original.toLowerCase(java.util.Locale.ROOT);
        if(x.startsWith("اتصل ب")||x.startsWith("اتصل بـ")||x.startsWith("كلم ")||x.startsWith("call ")){
            if(!approved)return "APPROVAL_REQUIRED";
            String name=original.replaceFirst("(?i)^(اتصل\\s*ب[ـ ]?|كلم\\s+|call\\s+)","").trim(); if(name.isEmpty())return "قولّي اسم الشخص اللي عايز تتصل بيه.";
            if(android.os.Build.VERSION.SDK_INT>=23&&activity.checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)return "PERMISSION_READ_CONTACTS";
            if(android.os.Build.VERSION.SDK_INT>=23&&activity.checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED)return "PERMISSION_CALL_PHONE";
            String number=findNumber(activity,name);if(number==null)return "ملقتش جهة اتصال باسم: "+name;
            try{activity.startActivity(new Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(number))));return "بتصل بـ "+name+".";}catch(Exception e){return "تعذر بدء المكالمة مع "+name+".";}
        }
        if(x.startsWith("افتح ")||x.startsWith("open ")){
            String requested=original.replaceFirst("(?i)^(افتح|open)\\s+","").trim();
            if(isCalculatorCommand(requested))return launchCalculator(activity);
            if(isRadarbotCommand(requested.toLowerCase(java.util.Locale.ROOT)))return launchRadarbot(activity);
            String result=launchByInstalledLabel(activity,requested);if(result!=null)return result;
        }
        return null;
    }
    private static boolean isCalculatorCommand(String x){if(x==null)return false;String s=x.toLowerCase(java.util.Locale.ROOT);return s.contains("الآلة الحاسبة")||s.contains("الاله الحاسبه")||s.contains("الآلة حاسبة")||s.contains("الاله حاسبه")||s.equals("الحاسبة")||s.equals("الحاسبه")||s.contains("calculator")||s.equals("calc")||s.contains("calculator app")||s.contains("تطبيق الحاسبة");}
    private static String launchCalculator(Activity activity){Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(Intent.CATEGORY_APP_CALCULATOR);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{activity.startActivity(i);return "تم فتح الآلة الحاسبة. ✓";}catch(Exception ignored){}return "مش لاقي تطبيق آلة حاسبة على الموبايل.";}
    private static boolean isRadarbotCommand(String x){return x.contains("راداربوت")||x.contains("رادار بوت")||x.contains("radarbot")||x.contains("كاميرات الطريق")||x.contains("كاميرات السرعة")||x.contains("تنبيه الكاميرات")||x.contains("شغل الرادار")||x.contains("شغل رادار الطريق");}
    private static String launchRadarbot(Activity activity){try{PackageManager pm=activity.getPackageManager();Intent launch=pm.getLaunchIntentForPackage(RADARBOT_PACKAGE);if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity.startActivity(launch);return "شغلت Radarbot. سيبه شغال في الخلفية أثناء السواقة عشان يديك تنبيهات الكاميرات حسب إعداداته.";}}catch(Exception ignored){}return "Radarbot مش متثبت على الموبايل. نزّله من Google Play الأول، وبعدها قولّي: «شغّل راداربوت».";}
    private static boolean isNetworkCommand(String x){return x.equals("النت")||x.equals("الانترنت")||x.equals("الإنترنت")||x.contains("حالة النت")||x.contains("حالة الإنترنت")||x.contains("حالة الانترنت")||x.contains("network status")||x.contains("internet status");}
    private static String renderPlan(ShadowPhoneUseAgent.Plan p){StringBuilder b=new StringBuilder(p.summary);for(ShadowPhoneUseAgent.Step s:p.steps)b.append("\n").append(s.icon).append(" ").append(s.name).append(" — ").append(s.detail);return b.toString();}
    private static boolean isApproval(String s){return s.equals("وافق")||s.equals("موافق")||s.equals("نفذ")||s.equals("نفّذ")||s.equals("approve")||s.equals("approved")||s.equals("yes")||s.equals("نعم");}
    public static String permissionMessage(String code){if("PERMISSION_READ_CONTACTS".equals(code))return "PERMISSION_REQUIRED:READ_CONTACTS";if("PERMISSION_CALL_PHONE".equals(code))return "PERMISSION_REQUIRED:CALL_PHONE";return code;}
    private static String findNumber(Activity activity,String requested){android.database.Cursor c=null;try{Uri uri=ContactsContract.CommonDataKinds.Phone.CONTENT_URI;String[] projection={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER};c=activity.getContentResolver().query(uri,projection,null,null,null);if(c==null)return null;String q=requested.toLowerCase(java.util.Locale.ROOT);while(c.moveToNext()){String name=c.getString(0),number=c.getString(1);if(name!=null&&number!=null){String n=name.toLowerCase(java.util.Locale.ROOT);if(n.equals(q)||n.contains(q)||q.contains(n))return number;}}}catch(Exception ignored){}finally{if(c!=null)c.close();}return null;}
    private static String launchByInstalledLabel(Activity activity,String requested){if(requested.isEmpty())return null;PackageManager pm=activity.getPackageManager();List<ApplicationInfo> apps=pm.getInstalledApplications(PackageManager.GET_META_DATA);String q=requested.toLowerCase(java.util.Locale.ROOT);for(ApplicationInfo app:apps){CharSequence label=pm.getApplicationLabel(app);if(label==null)continue;String name=label.toString(),n=name.toLowerCase(java.util.Locale.ROOT);if(n.equals(q)||n.contains(q)){Intent launch=pm.getLaunchIntentForPackage(app.packageName);if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);activity.startActivity(launch);return "تم فتح "+name+".";}}}return "ملقتش تطبيق مثبت باسم: "+requested;}
}
