package com.shadow.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/** MOD-44.8: background radar foreground service. */
public final class ShadowRadarService extends Service {
    private static final String CHANNEL = "shadow_radar";
    private ShadowRadarController controller;
    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "SHADOW Radar", NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(com.shadow.mobile.R.drawable.shadow_logo)
                .setContentTitle("SHADOW Radar")
                .setContentText("الرادار شغال في الخلفية")
                .setOngoing(true).build();
        startForeground(4402, n);
        controller = new ShadowRadarController(this);
        controller.start();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public void onDestroy() { if (controller != null) controller.stop(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
