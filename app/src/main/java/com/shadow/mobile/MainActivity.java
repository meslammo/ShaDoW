package com.shadow.mobile;

import android.os.Bundle;
import android.widget.Toast;

/** MOD-46.8: launcher bridge — V4 UI boots the bundled Python Final Runtime through a capability gate. */
public final class MainActivity extends JarvisMainActivityV4 {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ShadowPythonRuntimeBridge bridge = new ShadowPythonRuntimeBridge(this);
        new Thread(() -> {
            String result = bridge.status();
            runOnUiThread(() -> Toast.makeText(this,
                    result.contains("Python Final Runtime:") ? "🧠 Final Runtime متوصل" : "🧠 Final Runtime capability-gated",
                    Toast.LENGTH_SHORT).show());
        }).start();
    }
}
