package com.shadow.mobile;

import android.content.Context;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/** MOD-58: Android identity bridge into the governed SHADOW Python runtime. */
public final class ShadowPythonRuntimeBridge {
    private final Context context;
    private final ShadowCapabilityGate gate;

    public ShadowPythonRuntimeBridge(Context context){
        this.context=context.getApplicationContext();
        this.gate=new ShadowCapabilityGate(context);
    }

    public boolean start(){
        try{
            if(!Python.isStarted()) Python.start(new AndroidPlatform(context));
            return true;
        }catch(Throwable ignored){return false;}
    }

    private PyObject androidRuntime(){
        if(!start()) throw new IllegalStateException("Python runtime unavailable");
        return Python.getInstance().getModule("android_runtime");
    }

    public String status(){
        try{
            PyObject result=androidRuntime().callAttr("core_status",context.getFilesDir().getAbsolutePath()+"/shadow_workspace");
            return gate.releaseStatus()+"\n\n"+result.toString();
        }catch(Throwable e){
            return gate.releaseStatus()+"\n\n12-Core Runtime: error • "+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }

    /** MOD-155: Super Nice 150-Core status is exposed without hardware requirements. */
    public String superniceStatus(){
        try{
            PyObject result=androidRuntime().callAttr("supernice_status",context.getFilesDir().getAbsolutePath()+"/shadow_workspace");
            return result.toString();
        }catch(Throwable e){
            return "{\"architecture\":\"SHADOW Super Nice 150-Core\",\"core_count\":150,\"status\":\"runtime_unavailable\",\"startup_blocking\":false}";
        }
    }

    /** MOD-168: run the complete 150-Core self-test from Android. */
    public String superniceSelfTest(){
        try{
            PyObject result=androidRuntime().callAttr("supernice_self_test",context.getFilesDir().getAbsolutePath()+"/shadow_workspace");
            return result.toString();
        }catch(Throwable e){
            return "{\"core_count\":150,\"passed\":0,\"failed\":150,\"all_passed\":false,\"runtime\":\"unavailable\"}";
        }
    }

    /** MOD-100: unified master composition root status. */
    public String masterStatus(){
        try{
            PyObject result=androidRuntime().callAttr("master_status",context.getFilesDir().getAbsolutePath()+"/shadow_workspace");
            return result.toString();
        }catch(Throwable e){
            return "150-Core Master: unavailable • "+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }

    /** MOD-100: ask the composition root for the governed plan. */
    public String masterPlan(String request, boolean authenticated){
        try{
            PyObject result=androidRuntime().callAttr("master_plan",request,context.getFilesDir().getAbsolutePath()+"/shadow_workspace",authenticated);
            return result.toString();
        }catch(Throwable e){
            return "Master Plan: unavailable • "+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }

    /** MOD-155: direct contract execution for one Super Nice core. */
    public String executeCore(String coreId, String request, boolean confirmed){
        try{
            PyObject result=androidRuntime().callAttr(
                "supernice_execute",
                coreId,
                request,
                context.getFilesDir().getAbsolutePath()+"/shadow_workspace",
                new org.json.JSONObject().toString(),
                confirmed
            );
            return result.toString();
        }catch(Throwable e){
            return "{\"core_id\":\""+String.valueOf(coreId).replace("\"","")+"\",\"ok\":false,\"status\":\"runtime_unavailable\",\"startup_blocking\":false}";
        }
    }

    /** MOD-58: identity-aware governance gate. */
    public String authorize(String request, boolean authenticated, boolean authorized, String source){
        try{
            return androidRuntime().callAttr("authorize", request, context.getFilesDir().getAbsolutePath()+"/shadow_workspace", authenticated, authorized, source == null ? "android" : source).toString();
        }catch(Throwable e){
            return "BLOCK|UNKNOWN|runtime_unavailable|"+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }

    public String authorize(String request){return authorize(request,false,false,"android");}

    public String handle(String request){
        try{
            return androidRuntime().callAttr("handle",request,context.getFilesDir().getAbsolutePath()+"/shadow_workspace").toString();
        }catch(Throwable e){
            return "Python Runtime error • "+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }

    public String develop(String request){
        try{
            PyObject production=Python.getInstance().getModule("shadow.runtime.production");
            PyObject runtime=production.callAttr("ProductionRuntime",context.getFilesDir().getAbsolutePath()+"/shadow_workspace");
            return runtime.callAttr("develop",request).toString();
        }catch(Throwable e){
            return "Python Development Engine error • "+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }
}
