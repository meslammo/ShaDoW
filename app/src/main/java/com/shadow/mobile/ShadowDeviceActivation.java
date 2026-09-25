package com.shadow.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

/**
 * EVO-35: device-bound Shadow activation state.
 *
 * The server activation token is encrypted with an Android Keystore AES key.
 * It is never sent to logs, memory, or cross-device sync as plaintext.
 */
public final class ShadowDeviceActivation {
    private static final String PREFS = "shadow_device_activation_v1";
    private static final String KEY_INSTANCE = "instance_id";
    private static final String KEY_DEVICE = "device_id";
    private static final String KEY_TOKEN = "activation_token_enc";
    private static final String KEY_ALIAS = "SHADOW_DEVICE_ACTIVATION_V1";
    private final Context context;
    private final SharedPreferences prefs;

    public ShadowDeviceActivation(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String deviceId() {
        String existing = prefs.getString(KEY_DEVICE, "");
        if (existing != null && !existing.isEmpty()) return existing;
        String raw = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (raw == null || raw.isEmpty()) raw = UUID.randomUUID().toString();
        String id = "android-" + sha256(raw + "|" + context.getPackageName()).substring(0, 32);
        prefs.edit().putString(KEY_DEVICE, id).apply();
        return id;
    }

    public String instanceId() { return prefs.getString(KEY_INSTANCE, ""); }

    public boolean isActivated() {
        return !instanceId().isEmpty() && !readToken().isEmpty();
    }

    public void saveActivation(String instanceId, String token) {
        if (instanceId == null || instanceId.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("activation_fields_required");
        }
        prefs.edit().putString(KEY_INSTANCE, instanceId.trim()).apply();
        prefs.edit().putString(KEY_TOKEN, encrypt(token.trim())).apply();
    }

    public String activationToken() { return readToken(); }

    public void clear() {
        prefs.edit().remove(KEY_INSTANCE).remove(KEY_TOKEN).apply();
    }

    private String readToken() {
        String stored = prefs.getString(KEY_TOKEN, "");
        if (stored == null || stored.isEmpty()) return "";
        try { return decrypt(stored); } catch (Exception e) { return ""; }
    }

    private String encrypt(String plaintext) {
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
                    Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("device_activation_encrypt_failed", e);
        }
    }

    private String decrypt(String encoded) throws Exception {
        String[] parts = encoded.split("\\.", 2);
        if (parts.length != 2) throw new IllegalArgumentException("activation_ciphertext_invalid");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            generator.generateKey();
        }
        return ((SecretKey) store.getKey(KEY_ALIAS, null));
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(b.length * 2);
            for (byte v : b) out.append(String.format(Locale.ROOT, "%02x", v));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256_unavailable", e);
        }
    }
}
