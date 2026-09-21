package com.shadow.mobile;

import android.content.Context;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** MOD-33: ClosePaw-inspired phone-use orchestration on top of SHADOW's existing Accessibility bridge. */
public final class ShadowPhoneUseAgent {
    public static final class Step {
        public final String icon,name,detail;
        public Step(String icon,String name,String detail){this.icon=icon;this.name=name;this.detail=detail;}
    }
    public static final class Plan {
        public final String summary; public final Step[] steps; public final boolean approvalRequired;
        Plan(String summary,Step[] steps,boolean approvalRequired){this.summary=summary;this.steps=steps;this.approvalRequired=approvalRequired;}
    }
    private static final Pattern TAP=Pattern.compile("(?:اضغط|دوس|انقر)\\s+(?:على\\s+)?[\\\"']?(.+?)[\\\"']?$",Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPE=Pattern.compile("(?:اكتب|اكتبلي|اكتب لى)\\s+(.+)$",Pattern.CASE_INSENSITIVE);
    private Plan pending;
    public ShadowPhoneUseAgent(Context context){context.getApplicationContext();}
    public boolean looksLikePhoneTask(String input){
        if(input==null)return false; String s=input.toLowerCase(Locale.ROOT);
        return s.contains("اضغط")||s.contains("دوس")||s.contains("انقر")||s.contains("اضغط مطول")||s.contains("اكتب")||s.contains("اسحب")||s.contains("مرر")||s.contains("scroll")||s.contains("tap")||s.contains("swipe")||s.contains("ارجع")||s.contains("إرجع")||s.contains("الرئيسية")||s.contains("هوم")||s.contains("recent")||s.contains("التطبيقات الأخيرة")||s.contains("phone control")||s.contains("تحكم فى الموبايل")||s.contains("تحكم في الموبايل")||s.contains("افتح التطبيق")||s.contains("open app");
    }
    public Plan plan(String request){
        String s=request==null?"":request.trim(),lower=s.toLowerCase(Locale.ROOT); Step[] steps; boolean approval=isSensitive(lower);
        if(lower.contains("اسحب")||lower.contains("مرر")||lower.contains("swipe")||lower.contains("scroll")) steps=new Step[]{new Step("🧠","تحليل الشاشة","قراءة عناصر الشاشة عبر Accessibility"),new Step("👁️","تحديد الاتجاه","تحديد موضع التمرير المناسب"),new Step("👉","تنفيذ الحركة","إرسال gesture إلى التطبيق الحالي")};
        else if(TAP.matcher(s).find()||lower.contains("tap")) steps=new Step[]{new Step("🧠","تحليل الشاشة","قراءة شجرة الواجهة والعناصر القابلة للنقر"),new Step("👁️","تحديد العنصر","مطابقة الاسم مع عنصر الشاشة"),new Step("👆","تنفيذ الضغط","ضغط العنصر المطابق")};
        else if(TYPE.matcher(s).find()) steps=new Step[]{new Step("🧠","تحليل الشاشة","العثور على حقل إدخال نشط"),new Step("⌨️","تجهيز النص","تحضير النص المطلوب"),new Step("✍️","إدخال النص","كتابة النص داخل الحقل")};
        else steps=new Step[]{new Step("🧠","فهم الطلب","تحليل الهدف والسياق"),new Step("👁️","رؤية الشاشة","قراءة Accessibility tree"),new Step("🛠️","اختيار الأداة","اختيار الإجراء الأقل خطورة"),new Step("▶️","التنفيذ","تنفيذ الإجراء ثم التحقق")};
        pending=new Plan("🧠 SHADOW Phone Agent\n"+s,steps,approval); return pending;
    }
    public String executePending(){if(pending==null)return "لا توجد خطة معلقة.";if(pending.approvalRequired)return "APPROVAL_REQUIRED";String r=execute(pending.summary.replaceFirst("^🧠 SHADOW Phone Agent\\n",""));pending=null;return r;}
    public String execute(String request){
        if(request==null)return null;
        String s=request.trim(),lower=s.toLowerCase(Locale.ROOT);
        if(lower.equals("ارجع")||lower.equals("إرجع")||lower.equals("back")||lower.equals("رجوع"))return ShadowAccessibilityService.back()?"رجعت للشاشة السابقة.":"Accessibility مش مفعّل.";
        if(lower.equals("الرئيسية")||lower.equals("هوم")||lower.equals("home"))return ShadowAccessibilityService.home()?"رجعت للرئيسية.":"Accessibility مش مفعّل.";
        if(lower.contains("التطبيقات الأخيرة")||lower.contains("التطبيقات الاخيرة")||lower.equals("recent apps")||lower.equals("recents"))return ShadowAccessibilityService.recents()?"فتحت التطبيقات الأخيرة.":"Accessibility مش مفعّل.";
        if(lower.contains("الإشعارات")||lower.contains("الاشعارات")||lower.equals("notifications"))return ShadowAccessibilityService.notifications()?"فتحت لوحة الإشعارات.":"Accessibility مش مفعّل.";
        if(lower.contains("الإعدادات السريعة")||lower.contains("الاعدادات السريعة")||lower.equals("quick settings"))return ShadowAccessibilityService.quickSettings()?"فتحت الإعدادات السريعة.":"Accessibility مش مفعّل.";
        if(lower.contains("اسحب")||lower.contains("مرر")||lower.contains("swipe")||lower.contains("scroll")){
            String dir=lower.contains("شمال")||lower.contains("يسار")||lower.contains("left")?"left":lower.contains("يمين")||lower.contains("right")?"right":lower.contains("تحت")||lower.contains("اسفل")||lower.contains("down")?"down":"up";
            return ShadowAccessibilityService.swipe(dir)?"تم تنفيذ حركة السحب.":"مش قادر أنفذ السحب؛ فعّل Accessibility.";
        }
        if(lower.startsWith("اضغط مطول")||lower.startsWith("long press")){
            String target=s.replaceFirst("(?i)^(اضغط\\s*مطولًا?|long\\s+press)\\s+(?:على\\s+)?","").trim();
            return ShadowAccessibilityService.longClickText(target)?"تم الضغط المطول على: "+target:"ملقتش عنصر قابل للضغط المطول: "+target;
        }
                if(isSensitive(lower))return "APPROVAL_REQUIRED";
        Matcher tap=TAP.matcher(s);
        if(tap.find()){String target=tap.group(1).trim();return ShadowAccessibilityService.clickText(target)?"تم الضغط على: "+target:"ملقتش عنصر باسم: "+target+". حالة الشاشة: "+ShadowAccessibilityService.screenSummary();}
        Matcher type=TYPE.matcher(s);
        if(type.find())return ShadowAccessibilityService.typeText(type.group(1).trim())?"تم إدخال النص.":"مش لاقي حقل إدخال نشط.";
        return null;
    }    
    public Plan pendingPlan(){return pending;} public void clear(){pending=null;}
    private boolean isSensitive(String s){return s.contains("اتصل")||s.contains("كلم")||s.contains("ابعت")||s.contains("send")||s.contains("شراء")||s.contains("اشتر")||s.contains("purchase")||s.contains("delete")||s.contains("احذف")||s.contains("تحويل")||s.contains("transfer")||s.contains("password");}
}
