package com.shadow.mobile;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** MOD-33.2: semantic screen perception + safe phone-use primitives, integrated into SHADOW's existing service. */
public final class ShadowAccessibilityService extends AccessibilityService {
    private static ShadowAccessibilityService instance;
    @Override public void onServiceConnected(){
        instance=this; AccessibilityServiceInfo info=new AccessibilityServiceInfo();
        info.eventTypes=AccessibilityEvent.TYPES_ALL_MASK; info.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC; info.notificationTimeout=60;
        info.flags=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        setServiceInfo(info);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
    public static boolean enabled(){return instance!=null;}
    public static boolean back(){return global(GLOBAL_ACTION_BACK);}
    public static boolean home(){return global(GLOBAL_ACTION_HOME);}
    public static boolean recents(){return global(GLOBAL_ACTION_RECENTS);}
    public static boolean notifications(){return global(GLOBAL_ACTION_NOTIFICATIONS);}
    public static boolean quickSettings(){return global(GLOBAL_ACTION_QUICK_SETTINGS);}
    public static boolean global(int action){return instance!=null&&instance.performGlobalAction(action);}

    public static boolean clickText(String text){
        if(instance==null||text==null||text.trim().isEmpty())return false; AccessibilityNodeInfo root=instance.getRootInActiveWindow(); if(root==null)return false;
        java.util.List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(text.trim());
        for(AccessibilityNodeInfo n:nodes){if(n==null)continue;if(n.isClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;AccessibilityNodeInfo p=n.getParent();if(p!=null&&p.isClickable()&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;}return false;
    }
    public static boolean longClickText(String text){
        if(instance==null||text==null||text.trim().isEmpty())return false; AccessibilityNodeInfo root=instance.getRootInActiveWindow(); if(root==null)return false;
        java.util.List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(text.trim());
        for(AccessibilityNodeInfo n:nodes){
            if(n==null)continue;
            if(n.isLongClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))return true;
            AccessibilityNodeInfo p=n.getParent();
            if(p!=null&&p.isLongClickable()&&p.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))return true;
            Rect r=new Rect(); n.getBoundsInScreen(r);
            if(dispatchSwipe(r.centerX(),r.centerY(),r.centerX(),r.centerY(),700))return true;
        }
        return false;
    }
    public static boolean typeText(String text){
        if(instance==null||text==null)return false; AccessibilityNodeInfo root=instance.getRootInActiveWindow();if(root==null)return false; AccessibilityNodeInfo target=findEditable(root);
        if(target==null)return false; Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);
    }
    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n){
        if(n==null)return null;if(n.isEditable()||"android.widget.EditText".contentEquals(n.getClassName()))return n;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo r=findEditable(n.getChild(i));if(r!=null)return r;}return null;
    }
    public static boolean scrollForward(){return performScroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);}
    public static boolean scrollBackward(){return performScroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);}
    private static boolean performScroll(int action){
        if(instance==null)return false;AccessibilityNodeInfo root=instance.getRootInActiveWindow();if(root==null)return false;AccessibilityNodeInfo target=findScrollable(root);return target!=null&&target.performAction(action);
    }
    private static AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo n){
        if(n==null)return null;if(n.isScrollable())return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo r=findScrollable(n.getChild(i));if(r!=null)return r;}return null;
    }
    public static boolean swipe(String direction){
        if(instance==null||Build.VERSION.SDK_INT<Build.VERSION_CODES.N)return false;
        android.util.DisplayMetrics dm=instance.getResources().getDisplayMetrics();
        float w=dm.widthPixels, h=dm.heightPixels;
        float sx=w/2f, sy=h/2f, ex=sx, ey=sy;
        String d=String.valueOf(direction).toLowerCase(java.util.Locale.ROOT);
        if("left".equals(d)){sx=w*.8f;ex=w*.2f;} else if("right".equals(d)){sx=w*.2f;ex=w*.8f;}
        else if("down".equals(d)){sy=h*.25f;ey=h*.8f;} else {sy=h*.8f;ey=h*.25f;}
        return dispatchSwipe(sx,sy,ex,ey,500);
    }
    private static boolean dispatchSwipe(float sx,float sy,float ex,float ey,long durationMs){
        if(instance==null||Build.VERSION.SDK_INT<Build.VERSION_CODES.N)return false;
        Path path=new Path();path.moveTo(sx,sy);path.lineTo(ex,ey);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,Math.max(100,durationMs));
        return instance.dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),null,null);
    }
    public static String activePackage(){if(instance==null)return "ACCESSIBILITY_OFF";AccessibilityNodeInfo root=instance.getRootInActiveWindow();return root==null||root.getPackageName()==null?"UNKNOWN":String.valueOf(root.getPackageName());}
    public static String screenSummary(){
        if(instance==null)return "ACCESSIBILITY_OFF";AccessibilityNodeInfo root=instance.getRootInActiveWindow();if(root==null)return "ACCESSIBILITY_ON_NO_WINDOW";
        StringBuilder out=new StringBuilder("ACCESSIBILITY_ON package=").append(activePackage()).append('\n');dump(root,out,0,0);return out.toString();
    }
    private static int dump(AccessibilityNodeInfo n,StringBuilder out,int depth,int count){
        if(n==null||count>=120)return count;Rect r=new Rect();n.getBoundsInScreen(r);String cls=n.getClassName()==null?"":String.valueOf(n.getClassName());String text=n.getText()==null?"":String.valueOf(n.getText());String desc=n.getContentDescription()==null?"":String.valueOf(n.getContentDescription());
        if(text.length()>120)text=text.substring(0,120);if(desc.length()>120)desc=desc.substring(0,120);if(!text.isEmpty()||!desc.isEmpty()||n.isClickable()||n.isEditable()||n.isScrollable()){
            for(int i=0;i<depth;i++)out.append(' ');out.append(cls).append(" text=\"").append(text).append("\" desc=\"").append(desc).append("\" clickable=").append(n.isClickable()).append(" editable=").append(n.isEditable()).append(" scrollable=").append(n.isScrollable()).append(" bounds=").append(r).append('\n');count++;
        }
        for(int i=0;i<n.getChildCount()&&count<120;i++)count=dump(n.getChild(i),out,depth+1,count);return count;
    }
}
