package com.shadow.mobile;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** MOD-29.9: Optional user-enabled accessibility bridge for deeper cross-app control. */
public final class ShadowAccessibilityService extends AccessibilityService {
    private static ShadowAccessibilityService instance;

    @Override public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 80;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }

    public static boolean enabled() { return instance != null; }
    public static boolean global(int action) { return instance != null && instance.performGlobalAction(action); }

    public static boolean clickText(String text) {
        if (instance == null || text == null || text.trim().isEmpty()) return false;
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return false;
        java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text.trim());
        for (AccessibilityNodeInfo n : nodes) {
            if (n != null && n.isClickable()) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (n != null && n.getParent() != null && n.getParent().isClickable()) return n.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    public static String screenSummary() {
        if (instance == null) return "ACCESSIBILITY_OFF";
        AccessibilityNodeInfo root = instance.getRootInActiveWindow();
        if (root == null) return "ACCESSIBILITY_ON_NO_WINDOW";
        return "ACCESSIBILITY_ON";
    }
}
