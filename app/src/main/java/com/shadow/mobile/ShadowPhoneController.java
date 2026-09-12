package com.shadow.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.List;

/** MOD-29.3: Device-local phone controls. Permissions remain Android-enforced. */
public final class ShadowPhoneController {
    private ShadowPhoneController() { }

    public static String execute(Activity activity, String command) {
        if (command == null) return null;
        String original = command.trim();
        String x = original.toLowerCase(java.util.Locale.ROOT);

        if (x.startsWith("اتصل ب") || x.startsWith("اتصل بـ") || x.startsWith("كلم ") || x.startsWith("call ")) {
            String name = original.replaceFirst("(?i)^(اتصل\\s*ب[ـ ]?|كلم\\s+|call\\s+)", "").trim();
            if (name.isEmpty()) return "قولّي اسم الشخص اللي عايز تتصل بيه.";
            if (android.os.Build.VERSION.SDK_INT >= 23 && activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return "PERMISSION_READ_CONTACTS";
            }
            if (android.os.Build.VERSION.SDK_INT >= 23 && activity.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                return "PERMISSION_CALL_PHONE";
            }
            String number = findNumber(activity, name);
            if (number == null) return "ملقتش جهة اتصال باسم: " + name;
            try {
                activity.startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))));
                return "بتصل بـ " + name + ".";
            } catch (Exception e) {
                return "تعذر بدء المكالمة مع " + name + ".";
            }
        }

        if (x.startsWith("افتح ") || x.startsWith("open ")) {
            String requested = original.replaceFirst("(?i)^(افتح|open)\\s+", "").trim();
            String result = launchByInstalledLabel(activity, requested);
            if (result != null) return result;
        }
        return null;
    }

    public static String permissionMessage(String code) {
        if ("PERMISSION_READ_CONTACTS".equals(code)) return "PERMISSION_REQUIRED:READ_CONTACTS";
        if ("PERMISSION_CALL_PHONE".equals(code)) return "PERMISSION_REQUIRED:CALL_PHONE";
        return code;
    }

    private static String findNumber(Activity activity, String requested) {
        android.database.Cursor c = null;
        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
            c = activity.getContentResolver().query(uri, projection, null, null, null);
            if (c == null) return null;
            String q = requested.toLowerCase(java.util.Locale.ROOT);
            while (c.moveToNext()) {
                String name = c.getString(0);
                String number = c.getString(1);
                if (name != null && number != null) {
                    String n = name.toLowerCase(java.util.Locale.ROOT);
                    if (n.equals(q) || n.contains(q) || q.contains(n)) return number;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private static String launchByInstalledLabel(Activity activity, String requested) {
        if (requested.isEmpty()) return null;
        PackageManager pm = activity.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String q = requested.toLowerCase(java.util.Locale.ROOT);
        for (ApplicationInfo app : apps) {
            CharSequence label = pm.getApplicationLabel(app);
            if (label == null) continue;
            String name = label.toString();
            String n = name.toLowerCase(java.util.Locale.ROOT);
            if (n.equals(q) || n.contains(q)) {
                Intent launch = pm.getLaunchIntentForPackage(app.packageName);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(launch);
                    return "تم فتح " + name + ".";
                }
            }
        }
        return "ملقتش تطبيق مثبت باسم: " + requested;
    }
}
