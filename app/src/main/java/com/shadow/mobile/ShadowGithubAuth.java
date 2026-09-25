package com.shadow.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** MOD-50: stores the GitHub user token encrypted with Android Keystore. */
public final class ShadowGithubAuth {
    private static final String PREFS="shadow_github";
    private static final String TOKEN="token";
    private static final String KEY_ALIAS="shadow_github_key";
    private final Context context;
    public ShadowGithubAuth(Context c){context=c.getApplicationContext();}
    public boolean isConnected(){return !loadToken().isEmpty();}
    public void saveToken(String token)throws Exception{
        if(token==null||token.trim().isEmpty())throw new IllegalArgumentException("token_required");
        byte[] iv=new byte[12];new java.security.SecureRandom().nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        byte[] encrypted=cipher.doFinal(token.trim().getBytes(StandardCharsets.UTF_8));
        prefs().edit().putString(TOKEN,Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply();
    }
    public String loadToken(){
        try{String raw=prefs().getString(TOKEN,"");if(raw.isEmpty()||!raw.contains(":"))return "";String[] p=raw.split(":",2);byte[] iv=Base64.decode(p[0],Base64.NO_WRAP);byte[] data=Base64.decode(p[1],Base64.NO_WRAP);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));return new String(cipher.doFinal(data),StandardCharsets.UTF_8);}catch(Exception ignored){return "";}
    }
    public void disconnect(){prefs().edit().remove(TOKEN).apply();}
    private SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(KEY_ALIAS))return((KeyStore.SecretKeyEntry)ks.getEntry(KEY_ALIAS,null)).getSecretKey();
        KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS,android.security.keystore.KeyProperties.PURPOSE_ENCRYPT|android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build());return kg.generateKey();
    }
    private SharedPreferences prefs(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
}
