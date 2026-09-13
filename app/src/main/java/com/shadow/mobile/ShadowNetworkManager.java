package com.shadow.mobile;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;

/**
 * MOD-34.1 — Network state layer.
 *
 * This component detects the active connection and manages SHADOW's network policy.
 * It never attempts to guess, crack, or bypass Wi-Fi credentials.
 * Starlink is intentionally treated as a normal upstream connection: if the
 * Starlink router provides Wi-Fi/Internet, Android sees it as Wi-Fi.
 */
public final class ShadowNetworkManager {
    private ShadowNetworkManager() {}

    public static final class Status {
        public final boolean online;
        public final boolean wifi;
        public final boolean cellular;
        public final String transport;
        public final String ssid;

        public Status(boolean online, boolean wifi, boolean cellular, String transport, String ssid) {
            this.online = online;
            this.wifi = wifi;
            this.cellular = cellular;
            this.transport = transport;
            this.ssid = ssid;
        }

        @Override public String toString() {
            return "online=" + online + ", transport=" + transport + ", ssid=" + ssid;
        }
    }

    public static Status inspect(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean online = false, wifi = false, cellular = false;
        String transport = "offline";
        if (cm != null) {
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            if (caps != null) {
                online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                if (wifi) transport = "wifi";
                else if (cellular) transport = "cellular";
                else if (online) transport = "other";
            }
        }

        String ssid = "unknown";
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.getConnectionInfo() != null) {
                String value = wm.getConnectionInfo().getSSID();
                if (value != null && !value.isEmpty() && !"<unknown ssid>".equalsIgnoreCase(value)) ssid = value;
            }
        } catch (SecurityException ignored) {}

        return new Status(online, wifi, cellular, transport, ssid);
    }

    public static String describe(Context context) {
        Status s = inspect(context);
        if (!s.online) return "🌐 SHADOW: Offline — هشتغل بالمحرك المحلي.";
        if (s.wifi) return "🌐 SHADOW: Online عبر Wi‑Fi" + ("unknown".equals(s.ssid) ? "." : " — " + s.ssid + ".")
                + "\nStarlink لو هو مصدر الـWi‑Fi هيظهر هنا كاتصال Wi‑Fi عادي.";
        if (s.cellular) return "🌐 SHADOW: Online عبر بيانات الهاتف.";
        return "🌐 SHADOW: Online عبر اتصال آخر.";
    }
}
