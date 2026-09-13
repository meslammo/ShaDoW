package com.shadow.mobile;

import android.app.Application;

public final class ShadowDevRuntime extends Application {
    private static ShadowDevelopmentAgent agent;
    @Override public void onCreate() {
        super.onCreate();
        agent = new ShadowDevelopmentAgent(this);
    }
    public static ShadowDevelopmentAgent agent() { return agent; }
}
