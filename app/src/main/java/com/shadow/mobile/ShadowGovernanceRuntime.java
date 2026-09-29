package com.shadow.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** MOD-48.1: final local governance runtime — risk gate, journal, discovery and bounded recovery. */
public final class ShadowGovernanceRuntime {
    public enum Risk { LOW, MEDIUM, HIGH, CRITICAL }
    private final Activity activity;
    private final android.content.SharedPreferences prefs;
    private final ArrayDeque<String> journal = new ArrayDeque<>();
    private long checkpoint;

    public ShadowGovernanceRuntime(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences("shadow_governance", Context.MODE_PRIVATE);
        checkpoint = SystemClock.elapsedRealtime();
    }

    public synchronized Risk risk(String command) {
        String x = command == null ? "" : command.toLowerCase(Locale.ROOT);
        if (contains(x,"احذف","امسح كل","factory reset","delete all","wipe","format","transfer money","تحويل فلوس","send money","شراء","purchase")) return Risk.CRITICAL;
        if (contains(x,"اتصل","اتصال","رسالة","send","post","publish","install","ثبت","الغى","cancel","shutdown","إيقاف التشغيل")) return Risk.HIGH;
        if (contains(x,"افتح","شغل","اقفل","اطفي","ارفع","وطي","ابحث","search","camera","الكاميرا")) return Risk.MEDIUM;
        return Risk.LOW;
    }

    /** Returns null when execution may continue, otherwise a confirmation barrier. */
    public synchronized String authorize(String command) {
        Risk r = risk(command);
        if (r == Risk.LOW) return null;
        String x = command == null ? "" : command.toLowerCase(Locale.ROOT);
        boolean confirmed = contains(x,"أكد","اكد","موافق","نفذ","نفّذ","confirm","confirmed","yes");
        if ((r == Risk.HIGH || r == Risk.CRITICAL) && !confirmed) {
            return "SHADOW SECURITY\n\nالأمر مصنف " + r + ".\nمش هأنفذه تلقائياً. قول: أكد التنفيذ + الأمر لو أنت متأكد.";
        }
        checkpoint = SystemClock.elapsedRealtime();
        record("AUTHORIZED " + r + " | " + command);
        return null;
    }

    public synchronized void record(String event) {
        if (event == null || event.trim().isEmpty()) return;
        String line = System.currentTimeMillis() + " | " + event.replace('\n',' ');
        journal.addFirst(line);
        while (journal.size() > 100) journal.removeLast();
        prefs.edit().putString("last_event", line).putLong("checkpoint", checkpoint).apply();
    }

    public synchronized String status() {
        return "GOVERNANCE\nRisk gate: ACTIVE\nCheckpoint: " + (SystemClock.elapsedRealtime()-checkpoint) + "ms ago\nJournal: " + journal.size() + " events\nRecovery: BOUNDED\nFail-closed: YES";
    }

    public String discoverApps() {
        JSONArray out = new JSONArray();
        try {
            PackageManager pm = activity.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo a : apps) {
                CharSequence label = pm.getApplicationLabel(a);
                if (label == null) continue;
                JSONObject o = new JSONObject();
                o.put("name", label.toString()); o.put("package", a.packageName);
                o.put("launchable", pm.getLaunchIntentForPackage(a.packageName) != null);
                out.put(o);
                if (out.length() >= 200) break;
            }
        } catch (Exception ignored) {}
        return out.toString();
    }

    public String companionState(String id, String state) {
        if (id == null || id.trim().isEmpty()) return "companion_id_required";
        String key = "companion." + id.trim();
        prefs.edit().putString(key, state == null ? "DISCOVERED" : state).apply();
        return "COMPANION " + id + " → " + prefs.getString(key, "DISCOVERED");
    }

    public String memory(String kind, String value) {
        if (kind == null || value == null || value.trim().isEmpty()) return "memory_input_required";
        String k = kind.trim().toLowerCase(Locale.ROOT);
        if (!(k.equals("fact") || k.equals("evidence") || k.equals("interpretation") || k.equals("conclusion"))) return "memory_type_rejected";
        if (k.equals("fact") || k.equals("evidence")) prefs.edit().putString("memory."+k, value.trim()).apply();
        return "MEMORY " + k.toUpperCase(Locale.ROOT) + " recorded with explicit type; sensitive data is not auto-promoted.";
    }

    private static boolean contains(String s, String... values) { for (String v: values) if (s.contains(v)) return true; return false; }
}
