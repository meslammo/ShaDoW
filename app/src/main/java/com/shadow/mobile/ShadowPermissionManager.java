package com.shadow.mobile;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;

/** MOD-77.2: progressive permission onboarding. Android remains the authority; SHADOW never self-grants permissions. */
public final class ShadowPermissionManager {
    private final JarvisMainActivity activity;
    private boolean shown;

    public ShadowPermissionManager(JarvisMainActivity activity) {
        this.activity = activity;
    }

    public void showProgressiveSetupOnce() {
        if (shown) return;
        shown = true;
        new AlertDialog.Builder(activity)
                .setTitle("تهيئة صلاحيات SHADOW")
                .setMessage(
                        "هنفعّل قدرات SHADOW تدريجيًا بعد التثبيت، كل قدرة وقت احتياجها وبموافقتك.\n\n" +
                        "🎙 الصوت\n📷 الكاميرا\n🔔 الإشعارات\n📍 الموقع أثناء استخدام الميزة\n\n" +
                        "📱 التحكم العميق بالموبايل يظل اختياريًا من إعدادات Accessibility."
                )
                .setPositiveButton("تفعيل عند الحاجة", (d, w) -> requestNext())
                .setNegativeButton("لاحقًا", null)
                .show();
    }

    /** Continue the progressive permission flow only after Android reports a grant. */
    public boolean onPermissionResult(int requestCode, int[] grantResults) {
        if (requestCode != 905 && requestCode != 801 && requestCode != 803 && requestCode != 904) return false;
        if (grantResults == null || grantResults.length == 0) return true;
        boolean grantedAny = false;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) { grantedAny = true; break; }
        }
        if (grantedAny) requestNext();
        return true;
    }

    private void requestNext() {
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 905);
            return;
        }
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 801);
            return;
        }
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, 803);
            return;
        }
        if (!isLocationGranted()) {
            activity.requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, 904);
        }
    }

    public String status() {
        return "MIC=" + granted(Manifest.permission.RECORD_AUDIO)
                + " CAMERA=" + granted(Manifest.permission.CAMERA)
                + " LOCATION=" + (isLocationGranted() ? "ON" : "OFF")
                + " NOTIFICATIONS=" + ((Build.VERSION.SDK_INT < 33 || isGranted(Manifest.permission.POST_NOTIFICATIONS)) ? "ON" : "OFF")
                + " ACCESSIBILITY=" + (ShadowAccessibilityService.enabled() ? "ON" : "OFF");
    }

    private boolean isLocationGranted() {
        return isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
                || isGranted(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private boolean isGranted(String p) {
        return activity.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private String granted(String p) {
        return isGranted(p) ? "ON" : "OFF";
    }
}
