package com.shadow.mobile;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** MOD-29.13: Cloud gateway for chat, web, image generation and male voice. */
public final class ShadowCloudClient {
    private static final String PREFS = "shadow_cloud";
    private static final String RESPONSE_ID = "previous_response_id";
    private final Context context; private final String baseUrl;
    public ShadowCloudClient(Context context){this.context=context.getApplicationContext();this.baseUrl=BuildConfig.SHADOW_BACKEND_URL.replaceAll("/+$","");}
    public boolean isConfigured(){return baseUrl.startsWith("https://")&&!baseUrl.contains("REPLACE_WITH");}
    public String getBaseUrl(){return baseUrl;}
    public boolean health(){if(!isConfigured())return false;HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(baseUrl+"/health").openConnection();c.setRequestMethod("GET");c.setConnectTimeout(5000);c.setReadTimeout(7000);return c.getResponseCode()==200;}catch(Exception ignored){return false;}finally{if(c!=null)c.disconnect();}}

    public CloudReply chat(String message)throws Exception{
        if(!isConfigured())throw new IllegalStateException("Cloud backend is not configured");JSONObject body=new JSONObject();body.put("message",message);String previous=prefs().getString(RESPONSE_ID,"");if(!previous.isEmpty())body.put("previous_response_id",previous);body.put("device",deviceProfile());
        HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(baseUrl+"/v1/chat").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(8000);c.setReadTimeout(60000);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Accept","application/json");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}int code=c.getResponseCode();String json=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());JSONObject result=new JSONObject(json==null?"{}":json);if(code<200||code>=300||!result.optBoolean("ok",false))throw new IllegalStateException(result.optString("error","cloud_request_failed"));String answer=result.optString("answer","").trim();String responseId=result.optString("response_id","");if(!responseId.isEmpty())prefs().edit().putString(RESPONSE_ID,responseId).apply();if(answer.isEmpty())throw new IllegalStateException("empty_cloud_response");return new CloudReply(answer,responseId);}finally{if(c!=null)c.disconnect();}}

    public String generateImage(String prompt)throws Exception{if(!isConfigured())throw new IllegalStateException("Cloud backend is not configured");JSONObject body=new JSONObject();body.put("prompt",prompt);body.put("size","1024x1024");HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(baseUrl+"/v1/images").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(10000);c.setReadTimeout(120000);c.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}int code=c.getResponseCode();String json=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());JSONObject result=new JSONObject(json==null?"{}":json);if(code<200||code>=300||!result.optBoolean("ok",false))throw new IllegalStateException(result.optString("error","image_generation_failed"));String data=result.optString("image_base64","").trim();if(data.isEmpty())throw new IllegalStateException("empty_image");return data;}finally{if(c!=null)c.disconnect();}}

    public byte[] synthesizeSpeech(String text)throws Exception{if(!isConfigured())throw new IllegalStateException("Cloud backend is not configured");JSONObject body=new JSONObject();body.put("input",text);HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(baseUrl+"/v1/speech").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(8000);c.setReadTimeout(60000);c.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}int code=c.getResponseCode();if(code<200||code>=300)throw new IllegalStateException("speech_failed_"+code);return readBytes(c.getInputStream());}finally{if(c!=null)c.disconnect();}}

    public void resetConversation(){prefs().edit().remove(RESPONSE_ID).apply();}
    private String deviceProfile(){JSONObject d=new JSONObject();try{d.put("manufacturer",android.os.Build.MANUFACTURER);d.put("model",android.os.Build.MODEL);d.put("android",android.os.Build.VERSION.RELEASE);d.put("sdk",android.os.Build.VERSION.SDK_INT);PackageManager pm=context.getPackageManager();List<ApplicationInfo> apps=pm.getInstalledApplications(PackageManager.GET_META_DATA);ArrayList<String> names=new ArrayList<>();for(ApplicationInfo app:apps){CharSequence label=pm.getApplicationLabel(app);if(label!=null)names.add(label.toString());if(names.size()>=120)break;}Collections.sort(names,String.CASE_INSENSITIVE_ORDER);org.json.JSONArray a=new org.json.JSONArray();for(String n:names)a.put(n);d.put("installed_apps",a);}catch(Exception ignored){}return d.toString();}
    private android.content.SharedPreferences prefs(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static String read(InputStream stream)throws Exception{if(stream==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line);}return b.toString();}
    private static byte[] readBytes(InputStream stream)throws Exception{if(stream==null)return new byte[0];java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=stream.read(buf))!=-1)b.write(buf,0,n);return b.toByteArray();}
    public static final class CloudReply{public final String answer;public final String responseId;CloudReply(String answer,String responseId){this.answer=answer;this.responseId=responseId;}}
}
