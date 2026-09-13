package com.shadow.mobile;

import android.os.Bundle;
import android.widget.Toast;

/** MOD-46.10: launcher bridge — V4 UI boots the bundled Python runtime through the Android connector. */
public final class MainActivity extends JarvisMainActivityV4 {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ShadowRuntimeConnector connector = new ShadowRuntimeConnector(this);
        new Thread(() -> {
            String result = connector.status();
            runOnUiThread(() -> Toast.makeText(this,
                    result.startsWith("runtime_error:") ? "🧠 Runtime capability-gated" : "🧠 Runtime connected",
                    Toast.LENGTH_SHORT).show());
        }).start();
    }
}
