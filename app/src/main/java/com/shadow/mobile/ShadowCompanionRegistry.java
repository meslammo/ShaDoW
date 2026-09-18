package com.shadow.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** MOD-79.1: persistent, governed companion registry for SHADOW. */
public final class ShadowCompanionRegistry {
    public enum State { DISCOVERED, AUTHENTICATED, TRUSTED, ACTIVE, REVOKED }

    private static final String PREFS = "shadow_companions_v1";
    private static final String KEY_ITEMS = "items";
    private final SharedPreferences prefs;

    public ShadowCompanionRegistry(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized void discover(String id, String type, Collection<String> capabilities) {
        if (id == null || id.trim().isEmpty()) return;
        JSONObject item = item(id.trim());
        try {
            item.put("id", id.trim());
            item.put("type", type == null ? "generic" : type.trim());
            item.put("state", item.optString("state", State.DISCOVERED.name()));
            JSONArray caps = new JSONArray();
            if (capabilities != null) for (String c : new TreeSet<>(capabilities)) if (c != null && !c.trim().isEmpty()) caps.put(c.trim());
            if (caps.length() > 0 || !item.has("capabilities")) item.put("capabilities", caps);
            item.put("updated_at", System.currentTimeMillis());
            saveItem(item);
        } catch (Exception ignored) {}
    }

    public synchronized boolean authenticate(String id) {
        return transition(id, State.AUTHENTICATED, State.DISCOVERED, State.AUTHENTICATED);
    }

    public synchronized boolean trust(String id, Collection<String> permissions) {
        JSONObject item = item(id);
        String state = item.optString("state", State.DISCOVERED.name());
        if (!State.AUTHENTICATED.name().equals(state) && !State.TRUSTED.name().equals(state)) return false;
        try {
            item.put("state", State.TRUSTED.name());
            JSONArray p = new JSONArray();
            if (permissions != null) for (String x : new TreeSet<>(permissions)) if (x != null && !x.trim().isEmpty()) p.put(x.trim());
            item.put("permissions", p);
            item.put("updated_at", System.currentTimeMillis());
            saveItem(item);
            return true;
        } catch (Exception ignored) { return false; }
    }

    public synchronized boolean activate(String id) {
        JSONObject item = item(id);
        if (!State.TRUSTED.name().equals(item.optString("state"))) return false;
        try { item.put("state", State.ACTIVE.name()); item.put("updated_at", System.currentTimeMillis()); saveItem(item); return true; }
        catch (Exception ignored) { return false; }
    }

    public synchronized boolean revoke(String id) {
        JSONObject item = item(id);
        if (!item.has("id")) return false;
        try { item.put("state", State.REVOKED.name()); item.put("permissions", new JSONArray()); item.put("updated_at", System.currentTimeMillis()); saveItem(item); return true; }
        catch (Exception ignored) { return false; }
    }

    public synchronized boolean can(String id, String capability) {
        JSONObject item = item(id);
        String state = item.optString("state");
        if (!State.TRUSTED.name().equals(state) && !State.ACTIVE.name().equals(state)) return false;
        JSONArray permissions = item.optJSONArray("permissions");
        if (permissions == null) return false;
        for (int i = 0; i < permissions.length(); i++) if (capability != null && capability.equals(permissions.optString(i))) return true;
        return false;
    }

    public synchronized String state(String id) {
        return item(id).optString("state", State.DISCOVERED.name());
    }

    public synchronized String snapshot() {
        return prefs.getString(KEY_ITEMS, "[]");
    }

    private JSONObject item(String id) {
        String key = id == null ? "" : id.trim();
        JSONArray all;
        try { all = new JSONArray(prefs.getString(KEY_ITEMS, "[]")); } catch (Exception e) { all = new JSONArray(); }
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && key.equals(o.optString("id"))) return o;
        }
        JSONObject fresh = new JSONObject();
        try { fresh.put("id", key); } catch (Exception ignored) {}
        return fresh;
    }

    private boolean transition(String id, State target, State allowedFrom, State allowedFrom2) {
        JSONObject item = item(id);
        String state = item.optString("state", State.DISCOVERED.name());
        if (!allowedFrom.name().equals(state) && !allowedFrom2.name().equals(state)) return false;
        try { item.put("state", target.name()); item.put("updated_at", System.currentTimeMillis()); saveItem(item); return true; }
        catch (Exception ignored) { return false; }
    }

    private void saveItem(JSONObject item) {
        try {
            JSONArray old = new JSONArray(prefs.getString(KEY_ITEMS, "[]"));
            JSONArray next = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && item.optString("id").equals(o.optString("id"))) { next.put(item); replaced = true; }
                else if (o != null) next.put(o);
            }
            if (!replaced) next.put(item);
            while (next.length() > 64) next.remove(0);
            prefs.edit().putString(KEY_ITEMS, next.toString()).apply();
        } catch (Exception ignored) {}
    }
}
