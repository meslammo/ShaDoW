package com.shadow.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** MOD-24.1: Encrypted-at-rest user AI configuration. */
public final class ShadowOnlineConfig {
    private static final String PREFS = "shadow_online";
    private static final String KEY_NAME = "shadow_online_aes";
    private static final String KEY_CIPHER = "key_cipher";
    private static final String KEY_MODEL = "model";
    private static final String DEFAULT_MODEL = "gpt-5.6";
    private final Context context;

    public ShadowOnlineConfig(Context context) { this.context = context.getApplicationContext(); }

    public void save(String apiKey, String model) throws Exception {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) throw new IllegalArgumentException("API key is empty");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
        byte[] encrypted = cipher.doFinal(key.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        String packed = Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
        prefs().edit().putString(KEY_CIPHER, packed).putString(KEY_MODEL, model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim()).apply();
    }

    public String getApiKey() {
        try {
            String packed = prefs().getString(KEY_CIPHER, "");
            if (packed.isEmpty()) return "";
            String[] parts = packed.split("\\.", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception ignored) { return ""; }
    }

    public String getModel() { return prefs().getString(KEY_MODEL, DEFAULT_MODEL); }
    public boolean isConfigured() { return !getApiKey().isEmpty(); }
    public void clear() { prefs().edit().clear().apply(); }

    private SharedPreferences prefs() { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private SecretKey getSecretKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_NAME)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_NAME, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_NAME, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build());
        return kg.generateKey();
    }
}
