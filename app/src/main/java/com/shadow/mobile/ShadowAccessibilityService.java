package com.shadow.mobile;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
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
    public static boolean clickDescription(String text){
        if(instance==null||text==null||text.trim().isEmpty())return false;
        AccessibilityNodeInfo root=instance.getRootInActiveWindow();
        return root!=null&&clickDescriptionRecursive(root,text.trim());
    }
    private static boolean clickDescriptionRecursive(AccessibilityNodeInfo n,String text){
        if(n==null)return false;
        CharSequence d=n.getContentDescription();
        if(d!=null&&d.toString().equalsIgnoreCase(text)&&n.isClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
        for(int i=0;i<n.getChildCount();i++)if(clickDescriptionRecursive(n.getChild(i),text))return true;
        return false;
    }
    public static boolean longClickText(String text){
        if(instance==null||text==null||text.trim().isEmpty())return false;
        AccessibilityNodeInfo root=instance.getRootInActiveWindow();
        if(root==null)return false;
        java.util.List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(text.trim());
        for(AccessibilityNodeInfo n:nodes){
            if(n!=null&&n.isLongClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))return true;
        }
        return false;
    }
    public static boolean swipe(String direction){
        if(instance==null||android.os.Build.VERSION.SDK_INT<24)return false;
        AccessibilityNodeInfo root=instance.getRootInActiveWindow();
        if(root==null)return false;
        Rect r=new Rect();root.getBoundsInScreen(r);
        float cx=r.centerX(),cy=r.centerY();
        float x1=cx,y1=cy,x2=cx,y2=cy;
        String d=String.valueOf(direction==null?"up":direction).toLowerCase(java.util.Locale.ROOT);
        if(d.contains("down")||d.contains("تحت")||d.contains("اسفل")||d.contains("أسفل")){y1=cy*0.45f;y2=cy*1.55f;}
        else if(d.contains("left")||d.contains("شمال")||d.contains("يسار")){x1=cx*1.55f;x2=cx*0.45f;}
        else if(d.contains("right")||d.contains("يمين")){x1=cx*0.45f;x2=cx*1.55f;}
        else {y1=cy*1.55f;y2=cy*0.45f;}
        x1=Math.max(r.left+10,Math.min(r.right-10,x1));x2=Math.max(r.left+10,Math.min(r.right-10,x2));
        y1=Math.max(r.top+10,Math.min(r.bottom-10,y1));y2=Math.max(r.top+10,Math.min(r.bottom-10,y2));
        android.graphics.Path path=new android.graphics.Path();
        path.moveTo(x1,y1);path.lineTo(x2,y2);
        android.accessibilityservice.GestureDescription gesture=
            new android.accessibilityservice.GestureDescription.Builder()
                .addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path,0,450))
                .build();
        return instance.dispatchGesture(gesture,null,null);
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
