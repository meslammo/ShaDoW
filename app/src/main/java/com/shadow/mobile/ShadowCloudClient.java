package com.shadow.mobile;

import android.content.Context;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** MOD-73: unified online agent client with verified Android action continuation. */
public final class ShadowCloudClient {
    private static final String PREFS = "shadow_cloud";
    private static final String RESPONSE_ID = "previous_response_id";
    private final Context context; private final String baseUrl; private final ShadowPuterBridge puter;
    public ShadowCloudClient(Context context){this.context=context.getApplicationContext();this.baseUrl=BuildConfig.SHADOW_BACKEND_URL.replaceAll("/+$","");this.puter=new ShadowPuterBridge(context);}
    public boolean isConfigured(){return baseUrl.startsWith("https://")&&!baseUrl.contains("REPLACE_WITH");}
    public String getBaseUrl(){return baseUrl;} public boolean onlineBrainReady(){return puter.isReady();}
    public boolean health(){if(!isConfigured())return puter.isReady();HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(baseUrl+"/health").openConnection();c.setRequestMethod("GET");c.setConnectTimeout(5000);c.setReadTimeout(7000);return c.getResponseCode()==200;}catch(Exception ignored){return false;}finally{if(c!=null)c.disconnect();}}

    public CloudReply chat(String message)throws Exception{return chat(message,"none");}
    public CloudReply chat(String message,String reasoningEffort)throws Exception{
        try {
            if (isConfigured()) return runChat(message, null, null, reasoningEffort);
        } catch (Exception ignored) {}
        return puter.chatBlocking(message,"gpt-5.6-luna",reasoningEffort);
    }

    private JSONObject postAbsoluteJson(String url,JSONObject body,int timeout)throws Exception{
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(url).openConnection();
            c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(8000);c.setReadTimeout(timeout);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            c.setRequestProperty("Accept","application/json");
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int code=c.getResponseCode();
            String json=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());
            if(code<200||code>=300)throw new IllegalStateException("pollinations_http_"+code);
            return new JSONObject(json==null||json.trim().isEmpty()?"{}":json);
        }finally{if(c!=null)c.disconnect();}
    }
    public CloudReply continueAgent(String provider,String responseId,String toolCallId,String originalMessage,String output)throws Exception{return continueAgent(provider,responseId,toolCallId,originalMessage,output,"none");}
    public CloudReply continueAgent(String provider,String responseId,String toolCallId,String originalMessage,String output,String reasoningEffort)throws Exception{
        JSONObject body=new JSONObject(); body.put("provider",provider==null?"":provider); body.put("response_id",responseId==null?"":responseId); body.put("tool_call_id",toolCallId==null?"":toolCallId); body.put("original_message",originalMessage==null?"":originalMessage); body.put("output",output==null?"":output); body.put("reasoning_effort",reasoningEffort==null?"none":reasoningEffort);
        JSONObject result=postJson("/v1/agent/continue",body,70000); return parseReply(result);
    }
    private CloudReply runChat(String message,String provider,String responseId,String reasoningEffort)throws Exception{
        if(!isConfigured())throw new IllegalStateException("Cloud backend is not configured");
        JSONObject body=new JSONObject();body.put("message",message);String previous=responseId!=null?responseId:prefs().getString(RESPONSE_ID,"");if(!previous.isEmpty())body.put("previous_response_id",previous);if(provider!=null&&!provider.isEmpty())body.put("provider",provider);body.put("reasoning_effort",reasoningEffort==null?"none":reasoningEffort);
        JSONObject result=postJson("/v1/chat",body,65000); CloudReply reply=parseReply(result); if(!reply.responseId.isEmpty())prefs().edit().putString(RESPONSE_ID,reply.responseId).apply(); return reply;
    }
    private CloudReply parseReply(JSONObject result)throws Exception{
        if(!result.optBoolean("ok",false))throw new IllegalStateException(result.optString("error","cloud_request_failed"));
        String answer=result.optString("answer","").trim(); String responseId=result.optString("response_id",""); String provider=result.optString("provider",""); String model=result.optString("model",""); boolean usedWeb=result.optBoolean("used_web_search",false);
        PendingAction pending=null; JSONObject pa=result.optJSONObject("pending_action"); if(pa!=null){pending=new PendingAction(pa.optString("action",""),pa.optString("argument",""),pa.optString("reason",""),pa.optBoolean("requires_confirmation",false),pa.optString("toolCallId",pa.optString("tool_call_id","")));}
        if(answer.isEmpty()&&pending==null)throw new IllegalStateException("empty_online_response");
        return new CloudReply(answer,responseId,provider,model,usedWeb,pending);
    }
    public interface StreamListener {
        void onDelta(String text);
        void onDone(StreamDone done);
        void onPending(PendingAction action, String provider, String responseId);
    }
    public static final class StreamDone {
        public final String responseId, provider, model;
        public final boolean usedWeb;
        public StreamDone(String r,String p,String m,boolean w){responseId=r;provider=p;model=m;usedWeb=w;}
    }
    public void streamChat(String message,String reasoningEffort,StreamListener listener)throws Exception{
        final boolean[] seen = {false};
        StreamListener proxy = listener == null ? null : new StreamListener(){
            public void onDelta(String text){ if(text!=null&&!text.isEmpty())seen[0]=true; listener.onDelta(text); }
            public void onDone(StreamDone done){ listener.onDone(done); }
            public void onPending(PendingAction action,String provider,String responseId){ listener.onPending(action,provider,responseId); }
        };
        try{
            if(isConfigured()){
                streamChatBackend(message,reasoningEffort,proxy);
                return;
            }
        }catch(Exception backendError){
            // Preserve the conversation and switch to the direct online brain without
            // exposing reconnect/offline status to the user.
        }
        puter.streamChat(message,"gpt-5.6-luna",reasoningEffort,proxy == null ? new StreamListener(){
            public void onDelta(String t){}
            public void onDone(StreamDone d){}
            public void onPending(PendingAction a,String p,String r){}
        }:proxy);
    }

    private void streamChatBackend(String message,String reasoningEffort,StreamListener listener)throws Exception{
        JSONObject body=new JSONObject();
        body.put("message",message);
        String previous=prefs().getString(RESPONSE_ID,"");
        if(!previous.isEmpty())body.put("previous_response_id",previous);
        body.put("reasoning_effort",reasoningEffort==null?"none":reasoningEffort);
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(baseUrl+"/v1/chat/stream").openConnection();
            c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(8000);c.setReadTimeout(125000);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            c.setRequestProperty("Accept","text/event-stream");
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int code=c.getResponseCode();
            if(code<200||code>=300)throw new IllegalStateException("stream_http_"+code);
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){
                String line;
                while((line=reader.readLine())!=null){
                    if(!line.startsWith("data:"))continue;
                    String data=line.substring(5).trim();
                    if(data.isEmpty())continue;
                    JSONObject event=new JSONObject(data);
                    String type=event.optString("type","");
                    if("delta".equals(type)){
                        if(listener!=null)listener.onDelta(event.optString("text",""));
                    }else if("done".equals(type)){
                        String rid=event.optString("responseId",event.optString("response_id",""));
                        String provider=event.optString("provider","");
                        String model=event.optString("model","");
                        boolean usedWeb=event.optBoolean("usedWeb",event.optBoolean("used_web_search",false));
                        if(!rid.isEmpty())prefs().edit().putString(RESPONSE_ID,rid).apply();
                        if(listener!=null)listener.onDone(new StreamDone(rid,provider,model,usedWeb));
                    }else if("pending_action".equals(type)){
                        String rid=event.optString("responseId",event.optString("response_id",""));
                        String provider=event.optString("provider","");
                        String action=event.optString("action","");
                        String argument=event.optString("argument","");
                        String reason=event.optString("reason","");
                        boolean confirm=event.optBoolean("requires_confirmation",false);
                        String toolCallId=event.optString("toolCallId",event.optString("tool_call_id",""));
                        if(!rid.isEmpty())prefs().edit().putString(RESPONSE_ID,rid).apply();
                        if(listener!=null)listener.onPending(new PendingAction(action,argument,reason,confirm,toolCallId),provider,rid);
                    }else if("error".equals(type)){
                        throw new IllegalStateException(event.optString("error","stream_error"));
                    }else if("eof".equals(type)){
                        break;
                    }
                }
            }
        }finally{if(c!=null)c.disconnect();}
    }

    public VoiceprintReply verifyVoiceprint(byte[] audio,String contentType)throws Exception{
        if(audio==null||audio.length==0)throw new IllegalArgumentException("audio_required");
        JSONObject body=new JSONObject();
        body.put("audio_base64",android.util.Base64.encodeToString(audio,android.util.Base64.NO_WRAP));
        body.put("content_type",contentType==null?"audio/wav":contentType);
        JSONObject result=postJson("/v1/voiceprint/verify",body,30000);
        return new VoiceprintReply(result.optBoolean("verified",false),result.optBoolean("enrolled",false),result.optDouble("score",Double.NaN),result.optString("reason",""));
    }

    public GithubDevice startGithubDevice()throws Exception{JSONObject r=postJson("/v1/github/device/start",new JSONObject(),15000);if(!r.optBoolean("ok",false))throw new IllegalStateException(r.optString("error","github_authorization_failed"));return new GithubDevice(r.optString("device_code"),r.optString("user_code"),r.optString("verification_uri"),r.optString("verification_uri_complete",""),r.optInt("expires_in",900),r.optInt("interval",5));}
    public GithubPoll pollGithubDevice(String deviceCode)throws Exception{JSONObject body=new JSONObject();body.put("device_code",deviceCode);JSONObject r=postJson("/v1/github/device/poll",body,15000);if(!r.optBoolean("ok",false))throw new IllegalStateException(r.optString("error","github_authorization_failed"));return new GithubPoll(r.optString("status"),r.optString("access_token",""),r.optInt("interval",5));}
    public String createDevelopmentPullRequest(String githubToken,String branch,String title,String body,boolean draft)throws Exception{
        JSONObject payload=new JSONObject();
        payload.put("approved",true);
        payload.put("github_token",githubToken==null?"":githubToken);
        payload.put("branch",branch==null?"":branch);
        payload.put("title",title==null?"":title);
        payload.put("body",body==null?"":body);
        payload.put("draft",draft);
        JSONObject result=postJson("/v1/development/pull-request",payload,30000);
        if(!result.optBoolean("ok",false)) throw new IllegalStateException(result.optString("error","pull_request_failed"));
        JSONObject pr=result.optJSONObject("result");
        return pr==null?result.toString():pr.toString();
    }
    public void applyDevelopment(String githubToken,String branch,String commitMessage,JSONArray files)throws Exception{JSONObject body=new JSONObject();body.put("approved",true);body.put("github_token",githubToken);body.put("branch",branch);body.put("commit_message",commitMessage);body.put("files",files);JSONObject r=postJson("/v1/development/apply",body,60000);if(!r.optBoolean("ok",false))throw new IllegalStateException(r.optString("error","github_write_failed"));}
    private JSONObject postJson(String path,JSONObject body,int timeout)throws Exception{if(!isConfigured())throw new IllegalStateException("Cloud backend is not configured");HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(baseUrl+path).openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(8000);c.setReadTimeout(timeout);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setRequestProperty("Accept","application/json");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}int code=c.getResponseCode();String json=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());return new JSONObject(json==null?"{}":json);}finally{if(c!=null)c.disconnect();}}
    public String generateImage(String prompt)throws Exception{try{JSONObject result=postJson("/v1/images",new JSONObject().put("prompt",prompt).put("size","1024x1024"),120000);if(!result.optBoolean("ok",false))throw new IllegalStateException(result.optString("error","image_generation_failed"));String data=result.optString("image_base64","").trim();if(data.isEmpty())throw new IllegalStateException("empty_image");return data;}catch(Exception ignored){return puter.generateImage(prompt,"gpt-image-2");}}
    public byte[] synthesizeSpeech(String text)throws Exception{JSONObject body=new JSONObject().put("input",text);HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(baseUrl+"/v1/speech").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(8000);c.setReadTimeout(60000);c.setRequestProperty("Content-Type","application/json; charset=utf-8");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}int code=c.getResponseCode();if(code<200||code>=300)throw new IllegalStateException("speech_failed_"+code);return readBytes(c.getInputStream());}finally{if(c!=null)c.disconnect();}}
    /** MOD-78: cloud speech-to-text path. Provider credentials stay server-side. */
    public String transcribeSpeech(byte[] audio,String contentType)throws Exception{
        if(audio==null||audio.length==0)throw new IllegalArgumentException("audio_required");
        JSONObject body=new JSONObject();
        body.put("audio_base64",android.util.Base64.encodeToString(audio,android.util.Base64.NO_WRAP));
        body.put("content_type",contentType==null?"audio/wav":contentType);
        JSONObject r=postJson("/v1/transcribe",body,90000);
        if(!r.optBoolean("ok",false))throw new IllegalStateException(r.optString("error","transcription_failed"));
        return r.optString("text","").trim();
    }

    /** MOD-78: server-authoritative image understanding path. */
    public String analyzeImage(byte[] image,String mimeType,String prompt)throws Exception{
        if(image==null||image.length==0)throw new IllegalArgumentException("image_required");
        JSONObject body=new JSONObject();
        body.put("image_base64",android.util.Base64.encodeToString(image,android.util.Base64.NO_WRAP));
        body.put("content_type",mimeType==null?"image/jpeg":mimeType);
        body.put("prompt",prompt==null||prompt.trim().isEmpty()?"حلل الصورة بدقة واذكر ما يمكن التحقق منه فقط.":prompt);
        try{
            JSONObject r=postJson("/v1/vision",body,90000);
            if(!r.optBoolean("ok",false))throw new IllegalStateException(r.optString("error","vision_failed"));
            return r.optString("answer","").trim();
        }catch(Exception ignored){
            return puter.analyzeImage(image,mimeType,prompt);
        }
    }

    /** EVO-35: query the unified 35-phase platform contract. */
    public String platformStatus() throws Exception {
        if(!isConfigured()) throw new IllegalStateException("Cloud backend is not configured");
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(baseUrl+"/v1/platform/status").openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept","application/json");
            int code=c.getResponseCode();
            String json=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());
            if(code<200||code>=300) throw new IllegalStateException("platform_status_"+code);
            return json;
        }finally{if(c!=null)c.disconnect();}
    }

    /** EVO-35: cloud entry point for the governed unified master execution contract. */
    public String runMasterPipeline(String request,boolean authenticated,boolean confirmed)throws Exception{
        JSONObject body=new JSONObject();
        body.put("message",request==null?"":request);
        body.put("authenticated",authenticated);
        body.put("confirmed",confirmed);
        JSONObject r=postJson("/v1/master/run",body,90000);
        if(!r.optBoolean("ok",false)&&!"confirmation_required".equals(r.optString("error","")))throw new IllegalStateException(r.optString("error","master_pipeline_failed"));
        return r.toString();
    }

    public void resetConversation(){prefs().edit().remove(RESPONSE_ID).apply();puter.resetConversation();}

    private android.content.SharedPreferences prefs(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static String read(InputStream stream)throws Exception{if(stream==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line);}return b.toString();}
    private static byte[] readBytes(InputStream stream)throws Exception{if(stream==null)return new byte[0];java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=stream.read(buf))!=-1)b.write(buf,0,n);return b.toByteArray();}
    public static final class PendingAction{public final String action,argument,reason,toolCallId;public final boolean requiresConfirmation;PendingAction(String a,String g,String r,boolean c,String i){action=a;argument=g;reason=r;requiresConfirmation=c;toolCallId=i;}}
    public static final class CloudReply{public final String answer,responseId,provider,model;public final boolean usedWeb;public final PendingAction pendingAction;CloudReply(String a,String r,String p,String m,boolean w,PendingAction pa){answer=a;responseId=r;provider=p;model=m;usedWeb=w;pendingAction=pa;}}
    public static final class VoiceprintReply{public final boolean verified,enrolled;public final double score;public final String reason;VoiceprintReply(boolean v,boolean e,double s,String r){verified=v;enrolled=e;score=s;reason=r;}}
    public static final class GithubDevice{public final String deviceCode,userCode,verificationUri,verificationUriComplete;public final int expiresIn,interval;GithubDevice(String d,String u,String v,String vc,int e,int i){deviceCode=d;userCode=u;verificationUri=v;verificationUriComplete=vc;expiresIn=e;interval=i;}}
    public static final class GithubPoll{public final String status,accessToken;public final int interval;GithubPoll(String s,String t,int i){status=s;accessToken=t;interval=i;}}
}
