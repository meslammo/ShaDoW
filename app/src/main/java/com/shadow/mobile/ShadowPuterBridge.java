package com.shadow.mobile;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Online brain fallback for SHADOW.
 * Uses Puter.js in an embedded browser context so no AI API key is shipped inside the APK.
 * Backend remains preferred for governed tools; Puter is the direct online conversation path
 * when the configured cloud backend is unavailable.
 */
public final class ShadowPuterBridge {
    private final Activity activity;
    private final WebView webView;
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();
    private final Object readyLock = new Object();
    private volatile boolean ready = false;
    private volatile boolean signedIn = false;

    public ShadowPuterBridge(Context context) {
        if (!(context instanceof Activity)) throw new IllegalArgumentException("Activity context required");
        this.activity = (Activity) context;
        webView = new WebView(activity);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setVisibility(View.GONE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new JsApi(), "AndroidShadow");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
            @Override public void onPageFinished(WebView view, String url) {
                if (!ready) {
                    view.evaluateJavascript(
                        "(typeof puter!=='undefined') ? '1' : '0'",
                        value -> {
                            if ("1".equals(value) || "\"1\"".equals(value)) {
                                ready = true;
                                synchronized (readyLock) { readyLock.notifyAll(); }
                            }
                        }
                    );
                }
            }
        });
        activity.addContentView(webView, new ViewGroup.LayoutParams(1, 1));
        String html = loadBridgeHtml();
        webView.loadDataWithBaseURL("https://shadow.local/", html, "text/html", "UTF-8", null);
    }

    private String loadBridgeHtml() {
        try {
            java.io.InputStream in = activity.getAssets().open("puter_bridge.html");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            in.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            throw new IllegalStateException("puter_bridge_asset_missing", e);
        }
    }

    public boolean isReady() { return ready; }
    public boolean isSignedIn() { return signedIn; }
    public void resetConversation() { eval("shadowReset();"); }

    public ShadowCloudClient.CloudReply chatBlocking(String message, String model, String reasoningEffort) throws Exception {
        String id = "c" + seq.incrementAndGet();
        Pending p = new Pending();
        pending.put(id, p);
        evaluateChat(id, message, model, reasoningEffort, false);
        if (!p.latch.await(125, TimeUnit.SECONDS)) { pending.remove(id); throw new java.util.concurrent.TimeoutException("puter_chat_timeout"); }
        if (p.error != null) throw new IllegalStateException(p.error);
        return new ShadowCloudClient.CloudReply(p.text, "", "puter", model, false, null);
    }

    public void streamChat(String message, String model, String reasoningEffort, ShadowCloudClient.StreamListener listener) throws Exception {
        waitReady(15000);
        String id = "s" + seq.incrementAndGet();
        Pending p = new Pending();
        p.listener = listener;
        pending.put(id, p);
        evaluateChat(id, message, model, reasoningEffort, true);
        if (!p.latch.await(125, TimeUnit.SECONDS)) { pending.remove(id); throw new java.util.concurrent.TimeoutException("puter_stream_timeout"); }
        if (p.error != null) throw new IllegalStateException(p.error);
    }

    public String generateImage(String prompt, String model) throws Exception {
        waitReady(15000);
        String id = "i" + seq.incrementAndGet();
        Pending p = new Pending();
        pending.put(id, p);
        String payload = "{\"prompt\":" + q(prompt) + ",\"model\":" + q(model) + "}";
        eval("shadowImage(" + q(id) + "," + q(payload) + ");");
        if (!p.latch.await(125, TimeUnit.SECONDS)) { pending.remove(id); throw new java.util.concurrent.TimeoutException("puter_image_timeout"); }
        if (p.error != null) throw new IllegalStateException(p.error);
        String data = p.text;
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma > 0) return data.substring(comma + 1);
        return data;
    }

    public String analyzeImage(byte[] image, String mimeType, String prompt) throws Exception {
        waitReady(15000);
        String id = "v" + seq.incrementAndGet();
        Pending p = new Pending();
        pending.put(id, p);
        String base64 = android.util.Base64.encodeToString(image, android.util.Base64.NO_WRAP);
        String payload = "{\"base64\":" + q(base64) + ",\"mime\":" + q(mimeType == null ? "image/jpeg" : mimeType) + ",\"prompt\":" + q(prompt) + "}";
        eval("shadowVision(" + q(id) + "," + q(payload) + ");");
        if (!p.latch.await(125, TimeUnit.SECONDS)) { pending.remove(id); throw new java.util.concurrent.TimeoutException("puter_vision_timeout"); }
        if (p.error != null) throw new IllegalStateException(p.error);
        return p.text == null ? "" : p.text.trim();
    }

    private void evaluateChat(String id, String message, String model, String reasoningEffort, boolean stream) throws Exception {
        waitReady(15000);
        String payload = "{\"message\":" + q(message) + ",\"model\":" + q(model == null || model.isEmpty() ? "openai/gpt-5.6-luna" : model)
                + ",\"reasoning_effort\":" + q(reasoningEffort == null ? "none" : reasoningEffort)
                + ",\"stream\":" + stream + "}";
        eval("shadowChat(" + q(id) + "," + q(payload) + ");");
    }

    private void waitReady(long timeoutMs) throws Exception {
        if (ready) return;
        long end = SystemClock.uptimeMillis() + timeoutMs;
        synchronized (readyLock) {
            while (!ready && SystemClock.uptimeMillis() < end) {
                readyLock.wait(Math.max(50, end - SystemClock.uptimeMillis()));
            }
        }
        if (!ready) throw new java.util.concurrent.TimeoutException("puter_bridge_not_ready");
    }

    private void eval(String js) {
        activity.runOnUiThread(() -> webView.evaluateJavascript("javascript:" + js, null));
    }

    private static String q(String s) {
        if (s == null) s = "";
        return JSONObjectStringQuote.quote(s);
    }

    public void show() {
        activity.runOnUiThread(() -> {
            ViewGroup.LayoutParams lp = webView.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            webView.setLayoutParams(lp);
            webView.setVisibility(View.VISIBLE);
            webView.bringToFront();
        });
    }
    public void hide() {
        activity.runOnUiThread(() -> {
            ViewGroup.LayoutParams lp = webView.getLayoutParams();
            lp.width = 1;
            lp.height = 1;
            webView.setLayoutParams(lp);
            webView.setVisibility(View.GONE);
        });
    }
    public void destroy() {
        for (Pending p : pending.values()) { p.error = "bridge_destroyed"; p.latch.countDown(); }
        pending.clear();
        activity.runOnUiThread(() -> { webView.stopLoading(); webView.destroy(); });
    }

    private static final class Pending {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile String text = "";
        volatile String error;
        volatile ShadowCloudClient.StreamListener listener;
    }

    private final class JsApi {
        @JavascriptInterface public void onNeedAuth() {
            show();
        }
        @JavascriptInterface public void onReady(boolean signed) {
            signedIn = signed;
            ready = true;
            synchronized (readyLock) { readyLock.notifyAll(); }
            if (signed) hide();
        }
        @JavascriptInterface public void onChunk(String id, String text) {
            Pending p = pending.get(id); if (p == null) return;
            if (p.listener != null) p.listener.onDelta(text == null ? "" : text);
        }
        @JavascriptInterface public void onDone(String id, String text) {
            Pending p = pending.get(id); if (p == null) return;
            p.text = text == null ? "" : text;
            if (p.listener != null) {
                p.listener.onDone(new ShadowCloudClient.StreamDone("", "puter", pModel(id), false));
            }
            p.latch.countDown();
            pending.remove(id);
        }
        @JavascriptInterface public void onImage(String id, String dataUri) {
            Pending p = pending.get(id); if (p == null) return;
            p.text = dataUri == null ? "" : dataUri;
            p.latch.countDown();
            pending.remove(id);
        }
        @JavascriptInterface public void onError(String id, String message, boolean auth) {
            Pending p = pending.get(id); if (p == null) return;
            p.error = auth ? "puter_auth_required:" + (message == null ? "" : message) : (message == null ? "puter_error" : message);
            p.latch.countDown();
            pending.remove(id);
        }
    }

    private String pModel(String id) { return "openai/gpt-5.6-luna"; }

    /** Tiny self-contained JSON string escaper; avoids another runtime dependency. */
    private static final class JSONObjectStringQuote {
        static String quote(String s) {
            StringBuilder b = new StringBuilder(s.length() + 2);
            b.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch(c) {
                    case '\\': b.append("\\\\"); break;
                    case '"': b.append("\\\""); break;
                    case '\n': b.append("\\n"); break;
                    case '\r': b.append("\\r"); break;
                    case '\t': b.append("\\t"); break;
                    default:
                        if (c < 0x20) b.append(String.format("\\u%04x",(int)c));
                        else b.append(c);
                }
            }
            b.append('"');
            return b.toString();
        }
    }
}
