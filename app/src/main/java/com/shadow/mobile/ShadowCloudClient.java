package com.shadow.mobile;

import android.content.Context;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** MOD-26.2: Client-side gateway for the SHADOW cloud backend. No provider secret is stored in the APK. */
public final class ShadowCloudClient {
    private static final String PREFS = "shadow_cloud";
    private static final String RESPONSE_ID = "previous_response_id";
    private final Context context;
    private final String baseUrl;

    public ShadowCloudClient(Context context) {
        this.context = context.getApplicationContext();
        this.baseUrl = BuildConfig.SHADOW_BACKEND_URL.replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return baseUrl.startsWith("https://") && !baseUrl.contains("REPLACE_WITH");
    }

    public String getBaseUrl() { return baseUrl; }

    public boolean health() {
        if (!isConfigured()) return false;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(baseUrl + "/health").openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(5000);
            c.setReadTimeout(7000);
            return c.getResponseCode() == 200;
        } catch (Exception ignored) { return false; }
        finally { if (c != null) c.disconnect(); }
    }

    public CloudReply chat(String message) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("Cloud backend is not configured");
        JSONObject body = new JSONObject();
        body.put("message", message);
        String previous = prefs().getString(RESPONSE_ID, "");
        if (!previous.isEmpty()) body.put("previous_response_id", previous);

        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(baseUrl + "/v1/chat").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(50000);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }

            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String json = read(stream);
            JSONObject result = new JSONObject(json == null ? "{}" : json);
            if (code < 200 || code >= 300 || !result.optBoolean("ok", false)) {
                throw new IllegalStateException(result.optString("error", "cloud_request_failed"));
            }
            String answer = result.optString("answer", "").trim();
            String responseId = result.optString("response_id", "");
            if (!responseId.isEmpty()) prefs().edit().putString(RESPONSE_ID, responseId).apply();
            if (answer.isEmpty()) throw new IllegalStateException("empty_cloud_response");
            return new CloudReply(answer, responseId);
        } finally { if (c != null) c.disconnect(); }
    }

    public void resetConversation() { prefs().edit().remove(RESPONSE_ID).apply(); }

    private android.content.SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }

    public static final class CloudReply {
        public final String answer;
        public final String responseId;
        CloudReply(String answer, String responseId) { this.answer = answer; this.responseId = responseId; }
    }
}
