package com.shadow.mobile;

import android.content.Context;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/** MOD-46.9: Android connector for the bundled SHADOW Python runtime. */
public final class ShadowRuntimeConnector {
    private final Context context;
    public ShadowRuntimeConnector(Context context){this.context=context.getApplicationContext();}
    public String status(){
        try{
            if(!Python.isStarted()) Python.start(new AndroidPlatform(context));
            PyObject production=Python.getInstance().getModule("shadow.runtime.production");
            PyObject runtime=production.callAttr("ProductionRuntime",context.getFilesDir().getAbsolutePath()+"/shadow_workspace");
            return runtime.callAttr("status").toString();
        }catch(Throwable e){return "runtime_error:"+(e.getMessage()==null?"unknown":e.getMessage());}
    }
}
