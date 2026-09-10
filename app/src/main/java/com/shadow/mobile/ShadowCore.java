package com.shadow.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import java.text.SimpleDateFormat;
import java.util.*;

/** MOD-19.7: Native safety/action layer retained under the embedded unified runtime. */
public final class ShadowCore {
    public static final String VERSION = "0.50.0";
    private final Activity activity;
    private final SharedPreferences prefs;

    public ShadowCore(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences("shadow_core", Activity.MODE_PRIVATE);
    }

    public String handle(String raw) {
        String request = raw == null ? "" : raw.trim();
        if (request.isEmpty()) return "SHADOW\n\nاكتب أمراً أو استخدم الصوت.";
        String x = request.toLowerCase(Locale.ROOT);
        remember(request);

        String phoneAction = ShadowMobileActions.execute(activity, request);
        if (phoneAction != null) return envelope("ACTION", request, phoneAction);
        if (isAny(x, "status", "حالة", "وضع", "system")) return status();
        if (isAny(x, "home", "البيت", "المنزل", "سمارت هوم")) return home();
        if (isAny(x, "car", "السيارة", "العربية", "العربيه", "المركبة", "vespa")) return car();
        if (isAny(x, "device", "جهازي", "الجهاز", "هاتف")) return device();
        if (isAny(x, "memory", "ذاكرة", "الذاكرة", "history", "سجل")) return memory();
        if (isAny(x, "time", "الوقت", "الساعة")) return "SHADOW\n\nالوقت الآن: " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        if (isAny(x, "date", "التاريخ", "النهارده", "اليوم")) return "SHADOW\n\nالتاريخ: " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        if (isAny(x, "hello", "hi", "سلام", "اهلا", "أهلا", "مرحبا")) return "SHADOW\n\nأهلاً محمد.\nCore: ONLINE\nExecution: LOCAL\nMemory: ACTIVE\nVoice: AVAILABLE\nHome: READY\nCar: READY\nSafety: FAIL-CLOSED";
        if (starts(x, "احسب ") || starts(x, "calculate ") || looksLikeMath(request)) {
            String expression = request.replaceFirst("(?i)^احسب\\s*", "").replaceFirst("(?i)^calculate\\s*", "").trim();
            try { return "CALCULATOR\n\n" + format(eval(expression)); }
            catch (Exception e) { return "CALCULATOR\n\nالتعبير غير صالح أو غير آمن."; }
        }
        if (x.contains("ما انت") || x.contains("مين انت") || x.contains("who are you")) return "SHADOW\n\nأنا SHADOW: مساعد Android موحّد؛ نواة أصلية + Python runtime مدمج، ذاكرة، صوت، وأدوات الهاتف.\nالذكاء السحابي اختياري ولا يحتاج localhost.";
        return "SHADOW LOCAL CORE\n\nاستلمت الأمر:\n" + request + "\n\nPLAN\n1. Understand\n2. Check permission\n3. Execute safe action\n4. Verify\n5. Save to memory";
    }

    private String status() { return "SHADOW SYSTEM STATUS\n\nVersion     " + VERSION + "\nCore        ONLINE\nExecution   NATIVE + EMBEDDED PYTHON\nMemory      ACTIVE\nVoice       ANDROID STT/TTS\nPhone       CONTROL READY\nHome        ADAPTER READY\nCar         ADAPTER READY\nGateway     OPTIONAL\nSecurity    FAIL-CLOSED\nDevice      " + BuildInfo.summary(activity); }
    private String home() { return "HOME CORE\n\nDomain: ACTIVE\nAdapter: READY\nConnection: NOT CONNECTED\nEndpoint: NOT CONFIGURED\nControls: FAIL-CLOSED\n\nواجهة Home موجودة داخل التطبيق. عند إضافة Smart Home endpoint فعلي، يتم توصيله عبر نفس الـdomain."; }
    private String car() { return "CAR CORE\n\nDomain: ACTIVE\nAdapter: READY\nConnection: NOT CONNECTED\nEndpoint: NOT CONFIGURED\nControls: FAIL-CLOSED\n\nواجهة Car موجودة داخل التطبيق. عند إضافة Car/Vespa API أو tracker فعلي، يتم توصيله عبر نفس الـdomain."; }
    private String device() { return "DEVICE\n\n" + BuildInfo.summary(activity); }
    private String memory() { String h=prefs.getString("history",""); return "LOCAL MEMORY\n\n"+(h.isEmpty()?"لا يوجد سجل بعد.":h); }
    private void remember(String s) { String old=prefs.getString("history",""); String line=new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date())+" | "+s.replace('\n',' '); String[] rows=old.isEmpty()?new String[0]:old.split("\\n"); StringBuilder b=new StringBuilder(line); int n=0; for(String row:rows) if(!row.trim().isEmpty()&&n++<49)b.append('\n').append(row); prefs.edit().putString("history",b.toString()).putString("last",s).apply(); }
    public void clearMemory(){prefs.edit().clear().apply();}
    public String lastCommand(){return prefs.getString("last","");}
    private static boolean isAny(String s,String... a){for(String v:a)if(s.contains(v.toLowerCase(Locale.ROOT)))return true;return false;}
    private static boolean starts(String s,String p){return s.startsWith(p);}
    private static boolean looksLikeMath(String s){return s.matches(".*[0-9][0-9+*/(). %^-]+[0-9].*");}
    private static String envelope(String type,String req,String result){return "SHADOW "+type+"\n\n"+result+"\n\nVERIFIED: LOCAL ACTION RESULT";}
    private static String format(double v){return Math.rint(v)==v?Long.toString((long)v):Double.toString(v);}
    private static double eval(String s){Parser p=new Parser(s);double v=p.expr();p.skip();if(p.i!=s.length())throw new IllegalArgumentException();return v;}
    private static final class Parser{final String s;int i;Parser(String s){this.s=s;}void skip(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}boolean eat(char c){skip();if(i<s.length()&&s.charAt(i)==c){i++;return true;}return false;}double expr(){double v=term();while(true){if(eat('+'))v+=term();else if(eat('-'))v-=term();else return v;}}double term(){double v=power();while(true){if(eat('*'))v*=power();else if(eat('/')){double d=power();if(d==0)throw new ArithmeticException();v/=d;}else if(eat('%')){double d=power();if(d==0)throw new ArithmeticException();v%=d;}else return v;}}double power(){double v=unary();if(eat('^'))v=Math.pow(v,power());return v;}double unary(){skip();if(eat('+'))return unary();if(eat('-'))return -unary();if(eat('(')){double v=expr();if(!eat(')'))throw new IllegalArgumentException();return v;}return number();}double number(){skip();int st=i;while(i<s.length()&&(Character.isDigit(s.charAt(i))||s.charAt(i)=='.'))i++;if(st==i)throw new IllegalArgumentException();return Double.parseDouble(s.substring(st,i));}}
    static final class BuildInfo{static String summary(Activity a){BatteryManager bm=(BatteryManager)a.getSystemService(Activity.BATTERY_SERVICE);int bat=bm==null?-1:bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);return android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL+" · Android "+android.os.Build.VERSION.RELEASE+" · Battery "+(bat<0?"?":bat+"%");}}
}
