package com.shadow.mobile;

/**
 * MOD-88.2: canonical user-facing SHADOW activity with progressive permissions.
 * The legacy JarvisMainActivity implementation remains internally shared.
 */
public class ShadowMainActivity extends JarvisMainActivity {
    private ShadowPermissionManager shadowPermissions;

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (shadowPermissions == null) {
            shadowPermissions = new ShadowPermissionManager(this);
            new android.os.Handler(getMainLooper()).postDelayed(
                    () -> shadowPermissions.showProgressiveSetupOnce(), 900L);
        }
    }
}
