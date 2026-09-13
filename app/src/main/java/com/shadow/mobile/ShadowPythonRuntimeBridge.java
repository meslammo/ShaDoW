package com.shadow.mobile;

import android.content.Context;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/** MOD-46.5: real Android ↔ Python Final Runtime bridge via bundled Chaquopy runtime. */
public final class ShadowPythonRuntimeBridge {
    private final Context context;
    private final ShadowCapabilityGate gate;
    public ShadowPythonRuntimeBridge(Context context){this.context=context.getApplicationContext();this.gate=new ShadowCapabilityGate(context);}
    public boolean start(){
        try{if(!Python.isStarted()) Python.start(new AndroidPlatform(context)); return true;}catch(Throwable ignored){return false;}
    }
    public String status(){
        if(!start()) return "Python Final Runtime: unavailable";
        try{
            PyObject production=Python.getInstance().getModule("shadow.runtime.production");
            PyObject runtime=production.callAttr("ProductionRuntime",context.getFilesDir().getAbsolutePath()+"/shadow_workspace");
            return gate.releaseStatus()+"\n\nPython Final Runtime:\n"+runtime.callAttr("status").toString();
        }catch(Throwable e){return gate.releaseStatus()+"\n\nPython Final Runtime: error • "+(e.getMessage()==null?"unknown":e.getMessage());}
    }
    public String develop(String request){
        if(!start()) return "Python Final Runtime: unavailable";
        try{
            PyObject production=Python.getInstance().getModule("shadow.runtime.production");
            PyObject runtime=production.callAttr("ProductionRuntime",context.getFilesDir().getAbsolutePath()+"/shadow_workspace");
            return runtime.callAttr("develop",request).toString();
        }catch(Throwable e){return "Python Development Engine error • "+(e.getMessage()==null?"unknown":e.getMessage());}
    }
}
