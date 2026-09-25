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

    private String workspacePath(){
        java.io.File root=new java.io.File(context.getFilesDir(),"shadow_workspace");
        if(!root.exists() && !root.mkdirs() && !root.isDirectory()) throw new IllegalStateException("shadow_workspace_unavailable");
        java.io.File shadow=new java.io.File(root,".shadow");
        if(!shadow.exists() && !shadow.mkdirs() && !shadow.isDirectory()) throw new IllegalStateException("shadow_state_unavailable");
        return root.getAbsolutePath();
    }

    public String status(){
        try{
            String workspace=workspacePath();
            PyObject result=androidRuntime().callAttr("core_status",workspace);
            PyObject supernice=androidRuntime().callAttr("supernice_status",workspace);
            return gate.releaseStatus()+"\n\n"+result.toString()+"\n\n"+supernice.toString();
        }catch(Throwable e){
            return gate.releaseStatus()+"\n\n12-Core/150-Core Runtime: error • "+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }

    /** MOD-155: Super Nice 150-Core status is exposed without hardware requirements. */
    public String superniceStatus(){
        try{
            PyObject result=androidRuntime().callAttr("supernice_status",workspacePath());
            return result.toString();
        }catch(Throwable e){
            return "{\"architecture\":\"SHADOW Super Nice 150-Core\",\"core_count\":150,\"status\":\"runtime_unavailable\",\"startup_blocking\":false}";
        }
    }

    /** MOD-168: run the complete 150-Core self-test from Android. */
    public String superniceSelfTest(){
        try{
            PyObject result=androidRuntime().callAttr("supernice_self_test",workspacePath());
            return result.toString();
        }catch(Throwable e){
            return "{\"core_count\":150,\"passed\":0,\"failed\":150,\"all_passed\":false,\"runtime\":\"unavailable\"}";
        }
    }

    /** MOD-100: unified master composition root status. */
    public String masterStatus(){
        try{
            PyObject result=androidRuntime().callAttr("master_status",workspacePath());
            return result.toString();
        }catch(Throwable e){
            return "150-Core Master: unavailable • "+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }

    /** MOD-100: ask the composition root for the governed plan. */
    public String masterPlan(String request, boolean authenticated){
        try{
            PyObject result=androidRuntime().callAttr("master_plan",request,workspacePath(),authenticated);
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
                workspacePath(),
                new org.json.JSONObject().toString(),
                confirmed
            );
            return result.toString();
        }catch(Throwable e){
            return "{\"core_id\":\""+String.valueOf(coreId).replace("\"","")+"\",\"ok\":false,\"status\":\"runtime_unavailable\",\"startup_blocking\":false}";
        }
    }

    /** MOD-78: execute the complete twelve-stage master pipeline. */
    public String superniceRun(String request, boolean authenticated, boolean confirmed){
        try{
            PyObject result=androidRuntime().callAttr(
                "supernice_run",
                request,
                workspacePath(),
                authenticated,
                confirmed,
                new org.json.JSONObject().toString()
            );
            return result.toString();
        }catch(Throwable e){
            return "{\"ok\":false,\"status\":\"runtime_unavailable\",\"startup_blocking\":false}";
        }
    }


    /** EVO-35: expose the unified 35-phase platform contract to Android. */
    public String platformStatus(){
        try{
            PyObject result=androidRuntime().callAttr("shadow_platform_status",workspacePath());
            return result.toString();
        }catch(Throwable e){
            return "{\"phase_count\":35,\"core_count\":150,\"status\":\"runtime_unavailable\"}";
        }
    }

    /** EVO-35: run unified diagnostics plus 150-Core reachability from Android. */
    public String platformDiagnostics(){
        try{
            PyObject result=androidRuntime().callAttr("shadow_diagnostics",workspacePath());
            return result.toString();
        }catch(Throwable e){
            return "{\"phase_count\":35,\"status\":\"runtime_unavailable\"}";
        }
    }

    /** MOD-58: identity-aware governance gate. */
    public String authorize(String request, boolean authenticated, boolean authorized, String source){
        try{
            return androidRuntime().callAttr("authorize", request, workspacePath(), authenticated, authorized, source == null ? "android" : source).toString();
        }catch(Throwable e){
            return "BLOCK|UNKNOWN|runtime_unavailable|"+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }

    public String authorize(String request){return authorize(request,false,false,"android");}

    public String handle(String request){
        try{
            return androidRuntime().callAttr("handle",request,workspacePath()).toString();
        }catch(Throwable e){
            return "Python Runtime error • "+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }

    public String develop(String request){
        try{
            PyObject production=Python.getInstance().getModule("shadow.runtime.production");
            PyObject runtime=production.callAttr("ProductionRuntime",workspacePath());
            return runtime.callAttr("develop",request).toString();
        }catch(Throwable e){
            return "Python Development Engine error • "+(e.getMessage()==null?"unknown":e.getMessage());
        }
    }
}
