package com.shadow.mobile;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.util.Map;

/** MOD-19.18: Calls the repository Python runtime in-process on Android. */
public final class ShadowPythonRuntime {
    private final String home;

    public ShadowPythonRuntime(String home) {
        this.home = home;
    }

    @SuppressWarnings("unchecked")
    public String handle(String request) {
        try {
            Python py = Python.getInstance();
            PyObject module = py.getModule("android_runtime");
            PyObject value = module.callAttr("handle", request, home);
            Map<String, Object> result = value.toJava(Map.class);
            Object answer = result.get("answer");
            if (answer == null) return null;
            return String.valueOf(answer);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean healthy() {
        try {
            Python py = Python.getInstance();
            PyObject module = py.getModule("android_runtime");
            PyObject value = module.callAttr("health", home);
            Map<?, ?> result = value.toJava(Map.class);
            return "ready".equals(String.valueOf(result.get("status")));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
