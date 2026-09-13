package com.shadow.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** MOD-32: persistent project-scoped memory for development work. */
public final class ShadowProjectMemory {
    private final SharedPreferences prefs;
    public ShadowProjectMemory(Context context) {
        prefs = context.getSharedPreferences("shadow_project_memory", Context.MODE_PRIVATE);
    }
    public void setProject(String name, String summary) {
        prefs.edit().putString("project", name).putString("summary", summary).apply();
    }
    public void record(String type, String text) {
        try {
            JSONArray a = new JSONArray(prefs.getString("events", "[]"));
            JSONObject e = new JSONObject();
            e.put("type", type); e.put("text", text); e.put("time", System.currentTimeMillis());
            a.put(0, e);
            while (a.length() > 100) a.remove(a.length() - 1);
            prefs.edit().putString("events", a.toString()).apply();
        } catch (Exception ignored) { }
    }
    public String snapshot() {
        return "project=" + prefs.getString("project", "SHADOW")
                + "\nsummary=" + prefs.getString("summary", "")
                + "\nevents=" + prefs.getString("events", "[]");
    }
    public String lastEvent() {
        try {
            JSONArray a = new JSONArray(prefs.getString("events", "[]"));
            return a.length() == 0 ? "" : a.getJSONObject(0).toString();
        } catch (Exception e) { return ""; }
    }
}
