package com.shadow.mobile;

import android.app.Activity;
import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.widget.Toast;

/** MOD-29.2: Safe IR remote capability layer. Device-specific codes are never guessed. */
public final class ShadowRemoteController {
    private final Activity activity;
    private final ConsumerIrManager ir;

    public ShadowRemoteController(Activity activity) {
        this.activity = activity;
        this.ir = (ConsumerIrManager) activity.getSystemService(Context.CONSUMER_IR_SERVICE);
    }

    public boolean hasIr() {
        return ir != null && ir.hasIrEmitter();
    }

    public String summary() {
        if (!hasIr()) return "الجهاز ده مفيهوش IR Blaster ظاهر لـ SHADOW. لو التلفزيون/التكييف Smart Wi‑Fi أو Bluetooth هنستخدم مسار الشبكة بدل الأشعة.";
        return "IR Blaster متاح. SHADOW جاهز يضيف ريموت TV أو AC بعد تحديد الشركة والموديل؛ مش هخمن كود ممكن يشغل جهاز غلط.";
    }

    public void showCenter() {
        String[] items = new String[]{
                "📺 ريموت تلفزيون",
                "❄️ ريموت تكييف",
                "🔎 اكتشاف قدرات الريموت",
                "🌐 البحث عن بروفايل الريموت أونلاين"
        };
        new android.app.AlertDialog.Builder(activity)
                .setTitle("SHADOW • Remote Center")
                .setItems(items, (d, which) -> {
                    if (which == 0) chooseDevice("TV");
                    else if (which == 1) chooseDevice("AC");
                    else if (which == 2) Toast.makeText(activity, summary(), Toast.LENGTH_LONG).show();
                    else Toast.makeText(activity, "اكتب في الشات: ابحث عن ريموت TV/AC + الشركة + الموديل، وSHADOW هيبحث عن البروفايل المناسب أونلاين.", Toast.LENGTH_LONG).show();
                }).show();
    }

    private void chooseDevice(String type) {
        if (!hasIr()) {
            Toast.makeText(activity, "مفيش IR Blaster في الموبايل. هنحتاج ريموت Smart/Wi‑Fi أو USB/Hub خارجي.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] brands = type.equals("TV")
                ? new String[]{"Samsung", "LG", "Sony", "TCL", "Hisense", "Philips", "Xiaomi", "أخرى"}
                : new String[]{"Carrier", "Sharp", "Tornado", "Midea", "Gree", "LG", "Samsung", "أخرى"};
        new android.app.AlertDialog.Builder(activity)
                .setTitle(type.equals("TV") ? "اختار شركة التلفزيون" : "اختار شركة التكييف")
                .setItems(brands, (d, which) -> {
                    String brand = brands[which];
                    Toast.makeText(activity, "تم اختيار " + brand + ". اكتب الموديل في الشات عشان SHADOW يبحث عن كود/بروفايل مطابق قبل الإرسال.", Toast.LENGTH_LONG).show();
                }).show();
    }

    /** Send a known raw IR pattern only after a trusted profile has supplied it. */
    public boolean transmit(int frequencyHz, int[] pattern) {
        if (!hasIr() || pattern == null || pattern.length == 0) return false;
        try {
            ir.transmit(frequencyHz, pattern);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
