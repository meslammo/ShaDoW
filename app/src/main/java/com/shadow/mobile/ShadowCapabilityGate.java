package com.shadow.mobile;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.ConsumerIrManager;
import android.os.Build;

/** MOD-46.1: Android capability gate. Never reports unavailable hardware as active. */
public final class ShadowCapabilityGate {
    public static final class Snapshot {
        public final boolean microphone, camera, contacts, phoneCalls, accessibility, ir, bluetooth, wifi, location;
        public Snapshot(boolean microphone, boolean camera, boolean contacts, boolean phoneCalls, boolean accessibility, boolean ir, boolean bluetooth, boolean wifi, boolean location) {
            this.microphone=microphone; this.camera=camera; this.contacts=contacts; this.phoneCalls=phoneCalls;
            this.accessibility=accessibility; this.ir=ir; this.bluetooth=bluetooth; this.wifi=wifi; this.location=location;
        }
        public String summary(){
            return "mic="+microphone+" camera="+camera+" contacts="+contacts+" calls="+phoneCalls+" accessibility="+accessibility+" ir="+ir+" bluetooth="+bluetooth+" wifi="+wifi+" location="+location;
        }
    }
    private final Context context;
    public ShadowCapabilityGate(Context context){this.context=context.getApplicationContext();}
    public Snapshot snapshot(){
        PackageManager pm=context.getPackageManager();
        boolean mic=pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        boolean camera=pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        boolean contacts=pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        boolean calls=pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        boolean ir=false;
        try{ConsumerIrManager irManager=(ConsumerIrManager)context.getSystemService(Context.CONSUMER_IR_SERVICE); ir=irManager!=null&&irManager.hasIrEmitter();}catch(Exception ignored){}
        boolean bluetooth=pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        boolean wifi=pm.hasSystemFeature(PackageManager.FEATURE_WIFI);
        boolean location=pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)||pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK);
        return new Snapshot(mic,camera,contacts,calls,isAccessibilityDeclared(),ir,bluetooth,wifi,location);
    }
    private boolean isAccessibilityDeclared(){
        try{return context.getPackageManager().getServiceInfo(new android.content.ComponentName(context, ShadowAccessibilityService.class), PackageManager.GET_META_DATA)!=null;}catch(Exception ignored){return false;}
    }
    public String releaseStatus(){
        Snapshot s=snapshot();
        return "SHADOW FINAL RUNTIME • Android bridge active\n"+s.summary()+"\nAndroid "+Build.VERSION.RELEASE+" (SDK "+Build.VERSION.SDK_INT+")";
    }
}
