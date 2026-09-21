package com.shadow.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.io.*;
import java.util.*;

/** MOD-73: unified Shadow agent surface with online-first tool execution and verification. */
public class JarvisMainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int MIC=801,FILE=802,CAMERA=803;
    private final int BG=Color.rgb(13,14,17),SURFACE=Color.rgb(29,31,36),SURFACE2=Color.rgb(42,44,51),TEXT=Color.rgb(241,243,246),MUTED=Color.rgb(155,160,170);
    private ShadowCore core; private ShadowCloudClient cloud; private ShadowGithubAuth githubAuth; private ShadowMasterCycle masterCycle; private ShadowCompanionRegistry companionRegistry; private ShadowVerificationLedger verificationLedger; private ShadowRecoveryLedger recoveryLedger; private ShadowMasterOrchestrator orchestrator; private ShadowMasterLifecycleBridge lifecycleBridge; private ShadowDevelopmentAgent developmentAgent; private ShadowPythonRuntimeBridge pythonBridge; private ShadowRadarController spatialRadar; private ShadowVoiceRecognizer voice; private ShadowVoiceIdentityGateway identity; private ShadowRemoteController remotes; private ShadowVoiceStateMachine voiceState; private LinearLayout messages; private EditText input; private ShadowStatusView status; private TextView liveVoice; private TextView micButton; private TextToSpeech tts; private MediaPlayer player; private boolean conversation=true, cloudOnline;
    private boolean voiceOutput=true;
    /** MOD-75: Android TTS is the default voice path; cloud TTS stays opt-in to avoid unnecessary provider cost. */
    private boolean cloudVoice=false;
    private String reasoningEffort="none";
    private boolean reasoningMode=false;
    private boolean handsFreeVoice=true;
    private final int REASON_RED=Color.rgb(235,45,55);
    private final int REASON_BLACK=Color.BLACK;
    private LinearLayout rootView,composerRef; private TextView plusButtonRef,sendButtonRef,micButtonRef,menuButtonRef,moreButtonRef;
    private boolean voiceTurnActive=false;
    private final Handler voiceHandler=new Handler(Looper.getMainLooper());
    private ShadowBargeInMonitor bargeInMonitor; private MediaRecorder voiceprintRecorder; private File voiceprintFile;
    private static final String WAKE_PREFS="shadow_voice_prefs";
    private static final String WAKE_ENABLED="wake_enabled";
    private int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private void setReasoningTheme(boolean on){reasoningMode=on;if(rootView==null)return;rootView.setBackgroundColor(on?REASON_BLACK:BG);if(composerRef!=null){GradientDrawable g=bg(on?REASON_BLACK:SURFACE,24);if(on)g.setStroke(dp(1),REASON_RED);composerRef.setBackground(g);}if(plusButtonRef!=null)plusButtonRef.setTextColor(on?REASON_RED:TEXT);if(sendButtonRef!=null)sendButtonRef.setTextColor(on?REASON_RED:TEXT);if(micButtonRef!=null)micButtonRef.setTextColor(on?REASON_RED:TEXT);if(menuButtonRef!=null)menuButtonRef.setTextColor(on?REASON_RED:TEXT);if(moreButtonRef!=null)moreButtonRef.setTextColor(on?REASON_RED:TEXT);if(input!=null){input.setTextColor(TEXT);input.setHintTextColor(on?REASON_RED:MUTED);}stage(on?"deep thinking":"online");}
    private void setReasoning(boolean on,String effort){reasoningEffort=on?(effort==null||effort.isEmpty()?"high":effort):"none";setReasoningTheme(on);}
    private void masterEvent(ShadowMasterEventBus.Type type,String request,String route,String detail,boolean ok){if(orchestrator!=null)orchestrator.events().publish(new ShadowMasterEventBus.Event(type,request,route,detail,ok));}
    private TextView tv(String s,float z){TextView v=new TextView(this);v.setText(s);v.setTextColor(TEXT);v.setTextSize(z);return v;}
    private void stage(String s){if(status!=null){status.setText(ShadowProcessIndicators.render(s));status.setTextColor(reasoningMode?REASON_RED:Color.rgb(112,210,160));}if(liveVoice!=null&&reasoningMode)liveVoice.setTextColor(REASON_RED);}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);core=new ShadowCore(this);cloud=new ShadowCloudClient(this);githubAuth=new ShadowGithubAuth(this);orchestrator=new ShadowMasterOrchestrator();identity=new ShadowVoiceIdentityGateway(this);remotes=new ShadowRemoteController(this);developmentAgent=new ShadowDevelopmentAgent(this);pythonBridge=new ShadowPythonRuntimeBridge(this);spatialRadar=new ShadowRadarController(this,orchestrator.events());masterCycle=new ShadowMasterCycle();companionRegistry=new ShadowCompanionRegistry(this);verificationLedger=new ShadowVerificationLedger(this);recoveryLedger=new ShadowRecoveryLedger(this);lifecycleBridge=new ShadowMasterLifecycleBridge(orchestrator.events(),core,developmentAgent,remotes,pythonBridge,companionRegistry,verificationLedger,recoveryLedger,masterCycle);
        voiceState=new ShadowVoiceStateMachine(handsFreeVoice,new ShadowVoiceStateMachine.Callback(){
            public void onListening(){runOnUiThread(()->{ShadowWakeWordService.pauseListening();stage("listening");if(liveVoice!=null){liveVoice.setVisibility(View.VISIBLE);if(liveVoice.getText().toString().isEmpty())liveVoice.setText("🎙 Listening…");}if(handsFreeVoice&&!voiceTurnActive){voiceTurnActive=true;}if(handsFreeVoice&&!voice.isActive())voiceHandler.postDelayed(()->{if(handsFreeVoice&&!isFinishing()&&!voice.isActive())listen();},250);});}
            public void onProcessing(){runOnUiThread(()->{stage("thinking");if(liveVoice!=null){liveVoice.setVisibility(View.VISIBLE);liveVoice.setText("⏳ Processing…");}});}
            public void onBotSpeaking(){runOnUiThread(()->{stage("speaking");if(liveVoice!=null){liveVoice.setVisibility(View.VISIBLE);liveVoice.setText("🔊 SHADOW speaking…");}});}
            public void onIdle(){runOnUiThread(()->{stage("done");voiceTurnActive=false;stopBargeInMonitor();ShadowWakeWordService.resumeListening();if(liveVoice!=null){liveVoice.setText("");liveVoice.setVisibility(View.GONE);}});}
            public void stopTtsPlayback(){stopCurrentTts();stopBargeInMonitor();}
        });
        voice=new ShadowVoiceRecognizer(this,new ShadowVoiceRecognizer.Listener(){
            public void onListening(){runOnUiThread(()->stage("listening"));}
            public void onReady(){runOnUiThread(()->{voiceTurnActive=true;voiceState.userStartedListening();});}
            public void onBeginningOfSpeech(){voiceState.speechStarted();}
            public void onPartial(String t){runOnUiThread(()->showLiveVoice(t));}
            public void onText(String t){runOnUiThread(()->handleVoiceText(t));}
            public void onFinal(String t){runOnUiThread(()->handleVoiceText(t));}
            public void onEndOfSpeech(){voiceState.speechEnded();}
            public void onAudioLevel(float level){runOnUiThread(()->{if(liveVoice!=null&&liveVoice.getVisibility()==View.VISIBLE&&voiceState.getState()==ShadowVoiceStateMachine.State.LISTENING){liveVoice.setAlpha(.65f+.35f*level);}});}
            public void onError(String m){runOnUiThread(()->{system(m);if(voiceState!=null)voiceState.userStoppedListening();});}
        });
        tts=new TextToSpeech(this,this);if(isWakeEnabled())try{ShadowWakeWordService.start(this);}catch(Exception ignored){}bargeInMonitor=new ShadowBargeInMonitor(this,new ShadowBargeInMonitor.Listener(){public void onSpeechDetected(){runOnUiThread(()->{if(voiceState!=null&&voiceState.getState()==ShadowVoiceStateMachine.State.BOT_SPEAKING){voiceState.speechStarted();}});}public void onLevel(float level){runOnUiThread(()->{if(liveVoice!=null&&voiceState!=null&&voiceState.getState()==ShadowVoiceStateMachine.State.BOT_SPEAKING){liveVoice.setAlpha(.55f+.45f*level);}});}});ui();check();}
    private void ui(){LinearLayout root=new LinearLayout(this);rootView=root;root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(8),dp(4),dp(8),dp(5));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(4),dp(4),dp(4),dp(4));TextView menu=tv("☰",24);menuButtonRef=menu;menu.setGravity(Gravity.CENTER);head.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(52)));ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.shadow_logo);logo.setPadding(dp(8),dp(8),dp(8),dp(8));head.addView(logo,new LinearLayout.LayoutParams(dp(50),dp(52)));LinearLayout tb=new LinearLayout(this);tb.setOrientation(LinearLayout.VERTICAL);tb.setGravity(Gravity.CENTER);TextView title=tv("SHADOW",18);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);tb.addView(title);status=new ShadowStatusView(this);status.setTextColor(Color.rgb(112,210,160));status.setTextSize(11);status.setGravity(Gravity.CENTER);stage("thinking");tb.addView(status);head.addView(tb,new LinearLayout.LayoutParams(0,dp(52),1));TextView more=tv("⋮",26);moreButtonRef=more;more.setGravity(Gravity.CENTER);head.addView(more,new LinearLayout.LayoutParams(dp(48),dp(52)));root.addView(head);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);messages.setPadding(dp(12),dp(10),dp(12),dp(18));sc.addView(messages);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));assistant("أهلاً محمد 👋\nأنا SHADOW — Z.\nأونلاين هو الوضع الأساسي: AI / Web / Images.\nالصوت والنص شغالين مع بعض — الكلام بيظهر على الشاشة وSHADOW يرد صوتيًا.");
        liveVoice=tv("",11);liveVoice.setTextColor(MUTED);liveVoice.setPadding(dp(8),0,dp(8),dp(2));liveVoice.setVisibility(View.GONE);root.addView(liveVoice,new LinearLayout.LayoutParams(-1,dp(24)));
        LinearLayout wrap=new LinearLayout(this);composerRef=wrap;wrap.setGravity(Gravity.CENTER_VERTICAL);wrap.setPadding(dp(5),dp(3),dp(5),dp(3));wrap.setBackground(bg(SURFACE,24));TextView plus=tv("＋",25);plusButtonRef=plus;plus.setGravity(Gravity.CENTER);wrap.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(56)));input=new EditText(this);input.setHint("Message SHADOW");input.setHintTextColor(MUTED);input.setTextColor(TEXT);input.setTextSize(16);input.setMaxLines(5);input.setSingleLine(false);input.setBackgroundColor(Color.TRANSPARENT);wrap.addView(input,new LinearLayout.LayoutParams(0,dp(56),1));micButton=tv(handsFreeVoice?"🎙":"🎙",24);micButtonRef=micButton;micButton.setGravity(Gravity.CENTER);wrap.addView(micButton,new LinearLayout.LayoutParams(dp(52),dp(56)));TextView send=tv("➤",23);sendButtonRef=send;send.setGravity(Gravity.CENTER);wrap.addView(send,new LinearLayout.LayoutParams(dp(52),dp(56)));root.addView(wrap,new LinearLayout.LayoutParams(-1,dp(64)));TextView hint=tv("＋ Files/Photos/Camera  •  🎙 Voice input  •  Hands‑Free via menu  •  Web Search  •  GitHub Authorization",10);hint.setTextColor(MUTED);hint.setGravity(Gravity.CENTER);root.addView(hint,new LinearLayout.LayoutParams(-1,dp(24)));setContentView(root);
        menu.setOnClickListener(v->attach());more.setOnClickListener(v->features());plus.setOnClickListener(v->attach());micButton.setOnClickListener(v->listen());send.setOnClickListener(v->send());input.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEND){send();return true;}return false;});}
    private void user(String s){TextView v=tv(s,16);v.setPadding(dp(14),dp(9),dp(14),dp(9));v.setBackground(bg(SURFACE2,18));messages.addView(v);bottom();}
    private TextView action(String label){TextView v=tv(label,17);v.setGravity(Gravity.CENTER);v.setTextColor(reasoningMode?REASON_RED:MUTED);v.setPadding(dp(8),0,dp(8),0);v.setMinWidth(dp(38));v.setMinHeight(dp(38));return v;}
    private void assistant(String s){LinearLayout block=new LinearLayout(this);block.setOrientation(LinearLayout.VERTICAL);block.setPadding(0,dp(3),0,dp(3));TextView v=tv(s,16);v.setPadding(0,dp(8),dp(18),dp(5));v.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);block.addView(v,new LinearLayout.LayoutParams(-1,-2));LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);bar.setPadding(0,0,0,dp(2));TextView copy=action("⧉"),like=action("👍"),dislike=action("👎"),read=action("🔊"),share=action("↗"),more=action("⋮");bar.addView(copy);bar.addView(like);bar.addView(dislike);bar.addView(read);bar.addView(share);bar.addView(more);copy.setOnClickListener(x->copyText(s));like.setOnClickListener(x->{like.setTextColor(reasoningMode?REASON_RED:Color.rgb(112,210,160));Toast.makeText(this,"تم تسجيل الإعجاب",Toast.LENGTH_SHORT).show();});dislike.setOnClickListener(x->{dislike.setTextColor(Color.rgb(230,120,120));Toast.makeText(this,"تم تسجيل عدم الإعجاب",Toast.LENGTH_SHORT).show();});read.setOnClickListener(x->speakNow(s));share.setOnClickListener(x->shareText(s));more.setOnClickListener(x->assistantMenu(s));block.addView(bar,new LinearLayout.LayoutParams(-1,dp(42)));messages.addView(block);bottom();}
    private void copyText(String s){try{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("SHADOW",s));Toast.makeText(this,"اتنسخ للحافظة",Toast.LENGTH_SHORT).show();}catch(Exception e){system("مش قادر أنسخ الرسالة.");}}
    private void shareText(String s){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,s);startActivity(Intent.createChooser(i,"مشاركة رد SHADOW"));}catch(Exception e){system("المشاركة غير متاحة دلوقتي.");}}
    private void speakNow(String s){localSpeak(s);}
    private void assistantMenu(String s){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("⧉ نسخ");p.getMenu().add("↗ مشاركة");p.getMenu().add("🔊 قراءة بصوت");p.getMenu().add("⏹ إيقاف الصوت");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.contains("نسخ"))copyText(s);else if(n.contains("مشاركة"))shareText(s);else if(n.contains("قراءة"))speakNow(s);else {stopCurrentTts();voiceState.userStoppedListening();}return true;});p.show();}
    private void system(String s){TextView v=tv(s,11);v.setTextColor(MUTED);messages.addView(v);bottom();}
    private void bottom(){messages.post(()->{ViewParent p=messages.getParent();if(p instanceof ScrollView)((ScrollView)p).fullScroll(View.FOCUS_DOWN);});}
    private void check(){new Thread(()->{cloudOnline=cloud.health();runOnUiThread(()->stage(cloudOnline?"online":"reconnecting"));}).start();}
    private boolean handleIdentityCommand(String s){String x=s.trim().toLowerCase(Locale.ROOT);if(x.contains("تحقق من صوتي")||x.contains("تحقق بصوتي")||x.equals("verify my voice")||x.equals("voiceprint verify")){startVoiceprintVerification();return true;}if(x.contains("حالة الهوية")||x.contains("حاله الهويه")||x.equals("identity status")||x.equals("voice identity status")){assistant(identity.status());return true;}if(x.contains("اقفل الهوية")||x.contains("اقفل الهويه")||x.equals("lock identity")||x.equals("logout shadow")){identity.lock();assistant("تم قفل هوية الـMaster. الأوامر الحساسة هتحتاج كلمة السر تاني.");return true;}if(x.startsWith("عيّن كلمة السر:")||x.startsWith("عين كلمة السر:")||x.startsWith("عيّن كلمه السر:")||x.startsWith("عين كلمه السر:")||x.startsWith("set passphrase:")||x.startsWith("set password:")){String p=identity.extractPassphrase(s);if(identity.enroll(p)){assistant("تم تسجيل كلمة سر الـMaster محليًا بشكل آمن. مش هخزن الكلمة نفسها، فقط SHA-256.\nقول: كلمة السر: <الكلمة> عند طلب أمر حساس.");}else assistant("كلمة السر لازم تكون 6 أحرف/رموز على الأقل.");return true;}return false;}
    private void startVoiceprintVerification(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;}
        if(voiceprintRecorder!=null)return;
        try{
            voiceprintFile=new File(getCacheDir(),"shadow_voiceprint.3gp");
            voiceprintRecorder=new MediaRecorder();
            voiceprintRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            voiceprintRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            voiceprintRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            voiceprintRecorder.setOutputFile(voiceprintFile.getAbsolutePath());
            voiceprintRecorder.prepare();
            voiceprintRecorder.start();
            stage("authenticating");
            assistant("SHADOW بيعمل تحقق صوتي أونلاين لمدة 4 ثواني. التسجيل مش هيتخزن كذاكرة.");
            voiceHandler.postDelayed(this::finishVoiceprintVerification,4000);
        }catch(Throwable e){cleanupVoiceprint();assistant("تعذر بدء التحقق الصوتي: "+e.getClass().getSimpleName());stage("online");}
    }
    private void finishVoiceprintVerification(){
        MediaRecorder recorder=voiceprintRecorder;
        voiceprintRecorder=null;
        try{if(recorder!=null){recorder.stop();recorder.reset();recorder.release();}}catch(Throwable ignored){}
        File f=voiceprintFile;voiceprintFile=null;
        if(f==null||!f.exists()){assistant("التسجيل الصوتي غير متاح للتحقق.");stage("online");return;}
        new Thread(()->{
            try{
                byte[] bytes=readFileBytes(f);
                ShadowCloudClient.VoiceprintReply r=cloud.verifyVoiceprint(bytes,"audio/3gpp");
                if(r.verified){
                    identity.markVoiceVerified(10*60*1000L);
                    runOnUiThread(()->{masterEvent(ShadowMasterEventBus.Type.IDENTITY_VERIFIED,"voiceprint verification","identity","online_voiceprint_verified",true);assistant("تم التحقق من هوية صوتك أونلاين ✓");stage("online");});
                }else{
                    runOnUiThread(()->{masterEvent(ShadowMasterEventBus.Type.FAILED,"voiceprint verification","identity","voiceprint_not_verified:"+r.reason,true);assistant("التحقق الصوتي لم ينجح: "+(r.reason==null||r.reason.isEmpty()?"غير متحقق":r.reason));stage("online");});
                }
            }catch(Throwable e){runOnUiThread(()->{assistant("خدمة التحقق الصوتي الأونلاين غير متاحة حالياً.");stage("reconnecting");});}
            finally{try{if(f.exists())f.delete();}catch(Exception ignored){}}
        }).start();
    }
    private void cleanupVoiceprint(){
        try{if(voiceprintRecorder!=null){voiceprintRecorder.stop();voiceprintRecorder.release();}}catch(Throwable ignored){}
        voiceprintRecorder=null;
        try{if(voiceprintFile!=null&&voiceprintFile.exists())voiceprintFile.delete();}catch(Exception ignored){}
        voiceprintFile=null;
    }
    private static byte[] readFileBytes(File file)throws Exception{
        try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toByteArray();
        }
    }
    private boolean authorizeSensitive(String s){if(!identity.isSensitiveCommand(s)||identity.isAuthenticated()||identity.isVoiceVerified())return true;String phrase=identity.extractPassphrase(s);if(!phrase.isEmpty()&&identity.authenticate(phrase)){system("SHADOW • Master identity authenticated for this session ✓");masterEvent(ShadowMasterEventBus.Type.IDENTITY_VERIFIED,s,"identity","master_authenticated_by_passphrase",true);return true;}masterEvent(ShadowMasterEventBus.Type.APPROVAL_REQUIRED,s,"master","sensitive_identity_required",false);masterEvent(ShadowMasterEventBus.Type.PAUSED,s,"master","awaiting_master_identity",false);assistant("الأمر ده حساس وعايز تأكيد هوية الـMaster.\nلو كلمة السر متسجلة، ابعتها بالشكل: كلمة السر: <الكلمة>\nأو استخدم: عيّن كلمة السر: <كلمة جديدة>");return false;}
    private boolean handleReasoningCommand(String s){String x=s.trim().toLowerCase(Locale.ROOT);if(x.equals("think hard")||x.equals("thinkhard")||x.equals("فكر بعمق")||x.equals("تفكير عميق")){setReasoning(true,"high");assistant("🧠 Think Hard اتفعل — الموديل هيستخدم reasoning أعلى، والواجهة بقت Black/Red.");return true;}if(x.equals("deep think")||x.equals("deep thinking")||x.equals("تفكير عميق جدا")||x.equals("تفكير عميق جدًا")){setReasoning(true,"xhigh");assistant("🔴 Deep Think اتفعل — أعلى reasoning متاح للمسار الحالي، والواجهة Black/Red.");return true;}if(x.equals("stop thinking")||x.equals("إيقاف التفكير")||x.equals("الغاء التفكير")||x.equals("إلغاء التفكير")){setReasoning(false,"none");assistant("تم إيقاف وضع التفكير العميق.");return true;}return false;}
    private boolean handleOutputCommand(String s){String x=s.trim().toLowerCase(Locale.ROOT);if(x.equals("رد كتابة")||x.equals("رد كتابه")||x.equals("من غير صوت")||x.equals("بدون صوت")||x.equals("text only")||x.equals("text response")){voiceOutput=false;assistant("تم — هرد كتابة فقط من دلوقتي.");stage("done");return true;}if(x.equals("رد صوتي")||x.equals("شغل الصوت")||x.equals("شغّل الصوت")||x.equals("voice on")||x.equals("voice response")){voiceOutput=true;assistant("تم — الصوت اتفعّل.");speak("تم — الصوت اتفعّل.");stage("done");return true;}return false;}
    private boolean isGithubCommand(String s){String x=s.trim().toLowerCase(Locale.ROOT);boolean github=x.contains("github")||x.contains("git hub")||x.contains("جيت هاب")||x.contains("جيتهاب")||x.contains("github.com")||x.contains("meslammo/shadow");if(!github)return false;return x.contains("authorization")||x.contains("authorize")||x.contains("auth")||x.contains("اربط")||x.contains("ابدأ")||x.contains("ابدء")||x.contains("connect")||x.contains("ربط")||x.contains("حالة")||x.contains("status")||x.contains("افصل")||x.contains("disconnect")||x.contains("تطوير")||x.contains("عدل")||x.contains("عدّل")||x.contains("نفذ")||x.contains("نفّذ")||x.contains("development")||x.contains("develop")||x.contains("repo")||x.contains("repository")||x.contains("فرع")||x.contains("branch");}
    private void handleGithubCommand(String s){String x=s.trim().toLowerCase(Locale.ROOT);if(x.contains("حالة")||x.equals("github status")){assistant(githubAuth.isConnected()?"GitHub متوصل بالفعل ومفتاح التفويض محفوظ بشكل مشفّر على الجهاز.":"GitHub مش متوصل. قول: اربط جيت هاب.");return;}if(x.contains("افصل")||x.contains("disconnect github")){githubAuth.disconnect();assistant("تم فصل GitHub من Shadow.");return;}connectGithub();}
    private void connectGithub(){stage("authenticating");system("SHADOW • GitHub intent detected — Chat route blocked. بيجهّز تفويض GitHub…");new Thread(()->{try{ShadowCloudClient.GithubDevice d=cloud.startGithubDevice();runOnUiThread(()->{try{String uri=d.verificationUriComplete.isEmpty()?d.verificationUri:d.verificationUriComplete;startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));}catch(Exception ignored){}new AlertDialog.Builder(this).setTitle("ربط GitHub بـ SHADOW").setMessage("افتح GitHub ووافق على التفويض.\n\nالكود: "+d.userCode+"\n\nبعد الموافقة اضغط تم.").setPositiveButton("تم",(dialog,which)->pollGithub(d)).setNegativeButton("إلغاء",(dialog,which)->stage("online")).show();});}catch(Exception e){runOnUiThread(()->{assistant("GitHub route وصل للـAuthorization لكن التفويض نفسه غير متاح: "+e.getMessage());stage("online");});}}).start();}
    private void pollGithub(ShadowCloudClient.GithubDevice d){stage("authenticating");new Thread(()->{try{long deadline=System.currentTimeMillis()+Math.max(60_000L,d.expiresIn*1000L);int wait=Math.max(2,d.interval);while(System.currentTimeMillis()<deadline){ShadowCloudClient.GithubPoll p=cloud.pollGithubDevice(d.deviceCode);if("authorized".equals(p.status)){githubAuth.saveToken(p.accessToken);runOnUiThread(()->{assistant("تم ربط GitHub بـ SHADOW ✓\nالتفويض محفوظ على الجهاز بشكل مشفّر.\nدلوقتي Shadow يقدر يستخدم صلاحية GitHub اللي وافقت عليها لتنفيذ مهام التطوير بعد التأكيد.");stage("online");});return;}if("denied".equals(p.status)||"expired".equals(p.status)||"error".equals(p.status)){String msg="expired".equals(p.status)?"انتهى كود التفويض.":"denied".equals(p.status)?"تم رفض تفويض GitHub.":"تفويض GitHub فشل.";runOnUiThread(()->{assistant(msg);stage("online");});return;}Thread.sleep(wait*1000L);if("slow_down".equals(p.status))wait+=5;}runOnUiThread(()->{assistant("انتهى وقت انتظار تفويض GitHub. جرّب: اربط جيت هاب.");stage("online");});}catch(Exception e){runOnUiThread(()->{assistant("حصل خطأ أثناء ربط GitHub: "+e.getMessage());stage("online");});}}).start();}
    private void handlePendingAction(String original, ShadowCloudClient.CloudReply reply){final ShadowCloudClient.PendingAction pa=reply.pendingAction;if(pa==null)return;final String command=(pa.action+" "+pa.argument).trim();Runnable execute=()->{stage("executing");String result;try{result=ShadowDeviceExecution.execute(this,command);}catch(Throwable e){result="تعذر التنفيذ: "+e.getClass().getSimpleName();}final String verified=(result==null||result.trim().isEmpty())?"لم يرجع الجهاز نتيجة مؤكدة لتنفيذ الإجراء.":result;masterEvent(ShadowMasterEventBus.Type.ACTION_EXECUTED,original,"local-device",verified,result!=null&&!result.trim().isEmpty());stage("verifying");new Thread(()->{try{ShadowCloudClient.CloudReply next=cloud.continueAgent(reply.provider,reply.responseId,pa.toolCallId,original,verified,reasoningEffort);runOnUiThread(()->{masterEvent(ShadowMasterEventBus.Type.VERIFICATION_RESULT,original,"local-device","tool_result_returned",!verified.isEmpty());masterEvent(ShadowMasterEventBus.Type.COMPLETED,original,"local-device","agent_continued",true);assistant(next.answer.isEmpty()?verified:next.answer);if(!next.answer.isEmpty())speak(next.answer);stage("online");if(next.pendingAction!=null)handlePendingAction(original,next);});}catch(Exception e){runOnUiThread(()->{masterEvent(ShadowMasterEventBus.Type.FAILED,original,"local-device","agent_continue_failed:"+e.getClass().getSimpleName(),false);assistant(verified);if(!verified.isEmpty())speak(verified);stage("reconnecting");});}}).start();};
        if(pa.requiresConfirmation)runOnUiThread(()->new AlertDialog.Builder(this).setTitle("تأكيد تنفيذ الإجراء").setMessage(pa.reason.isEmpty()?command:pa.reason+"\n\n"+command).setPositiveButton("تنفيذ",(d,w)->{masterEvent(ShadowMasterEventBus.Type.APPROVAL_REQUIRED,original,"local-device","execution_confirmed",true);execute.run();}).setNegativeButton("إلغاء",(d,w)->{masterEvent(ShadowMasterEventBus.Type.PAUSED,original,"local-device","user_cancelled_action",false);assistant("تم إلغاء الإجراء.");stage("online");}).show());else runOnUiThread(execute);
    }
    private void send(){String s=input.getText().toString().trim();if(s.isEmpty())return;user(s);input.setText("");
        if(handleIdentityCommand(s))return;
        if(!authorizeSensitive(s))return;
        if(handleOutputCommand(s))return;
        if(handleReasoningCommand(s))return;

        String request=s;
        String lower=s.toLowerCase(Locale.ROOT);
        if(lower.startsWith("think hard:")||lower.startsWith("thinkhard:")||lower.startsWith("deep think:")||lower.startsWith("deep thinking:")||lower.startsWith("فكر بعمق:")){int colon=s.indexOf(':');if(colon>0){setReasoning(true,lower.startsWith("deep")||lower.startsWith("deep thinking")?"xhigh":"high");request=s.substring(colon+1).trim();}}
        masterEvent(ShadowMasterEventBus.Type.INPUT_RECEIVED,request,"master","input_received",true); final ShadowMasterOrchestrator.Plan plan=orchestrator.plan(request,identity.isAuthenticated()); masterEvent(ShadowMasterEventBus.Type.IDENTITY_VERIFIED,request,plan.routeName(),identity.isAuthenticated()?"master_authenticated":"identity_unverified",identity.isAuthenticated());
        final String routedRequest=request;
        final ShadowMasterOrchestrator.Plan routedPlan=plan;
        new Thread(()->{
            try{core.governance().record("12-CORE MASTER PLAN | "+pythonBridge.masterPlan(routedRequest,identity.isAuthenticated()));}
            catch(Throwable ignored){}
        }).start();
        stage(plan.stageLabel());
        if(plan.confirmationRequired){ assistant("طلب التنفيذ محتاج توثيق هوية الـMaster قبل ما نكمل."); return; }

        if(plan.route==ShadowMasterOrchestrator.Route.GITHUB){handleGithubCommand(request);return;}
        if(plan.route==ShadowMasterOrchestrator.Route.DEVELOPMENT){
            stage("planning");
            new Thread(()->{
                final String devPlan;
                try{devPlan=developmentAgent.plan(routedRequest);}
                catch(Throwable e){runOnUiThread(()->assistant("Development Agent تعذر تشغيله الآن."));return;}
                runOnUiThread(()->{
                    masterEvent(ShadowMasterEventBus.Type.PLAN_READY,routedRequest,routedPlan.routeName(),"development_plan_ready",true);
                    masterEvent(ShadowMasterEventBus.Type.COMPLETED,routedRequest,routedPlan.routeName(),"plan_only",true);
                    assistant("🧠 Development Plan\n\n"+devPlan);stage("done");speak(devPlan);
                });
            }).start();
            return;
        }
        if(plan.route==ShadowMasterOrchestrator.Route.SPATIAL){
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},904);
            }else{
                spatialRadar.start();
                assistant("Spatial route اتفعل واتصل بالـMaster Bus. مش بخزن إحداثيات داخل event log.");
                stage("done");
            }
            return;
        }
        if(plan.route==ShadowMasterOrchestrator.Route.COMPANION){
            core.governance().companionState("companion", "ROUTED");masterEvent(ShadowMasterEventBus.Type.COMPLETED,request,plan.routeName(),"companion_route_ready",true);
            assistant("Companion route جاهز. الـMaster Bus موصل الـCompanion/Device boundary، والتنفيذ الفعلي لسا مربوط بقدرة companion موثقة.");
            stage("done");
            return;
        }
        if(plan.route==ShadowMasterOrchestrator.Route.IMAGE){stage("designing");generateImage(request);return;}
        if(plan.route==ShadowMasterOrchestrator.Route.SYSTEM){
            new Thread(()->{
                String answer;
                try{answer=core.handle(routedRequest,identity.isAuthenticated(),true,"android-master-orchestrator");}
                catch(Throwable e){answer="تعذر قراءة حالة النظام."; }
                final String master12=pythonBridge.masterStatus();
                final String baseAnswer=answer==null||answer.trim().isEmpty()?"حالة النظام غير متاحة الآن.":answer;
                final String out=baseAnswer+"\\n\\n"+master12;
                runOnUiThread(()->{masterEvent(ShadowMasterEventBus.Type.VERIFICATION_RESULT,routedRequest,routedPlan.routeName(),"system_status",true);masterEvent(ShadowMasterEventBus.Type.COMPLETED,routedRequest,routedPlan.routeName(),"completed",true);assistant(out);stage("done");speak(out);});
            }).start();
            return;
        }
        if(plan.route==ShadowMasterOrchestrator.Route.LOCAL_DEVICE){
            stage("executing");
            new Thread(()->{
                String local=null;
                try{local=ShadowPhoneController.execute(this,routedRequest);}catch(Throwable ignored){}
                if(local==null||local.trim().isEmpty())try{local=core.handle(routedRequest,identity.isAuthenticated(),true,"android-master-orchestrator");}catch(Throwable ignored){}
                final String out=(local==null||local.trim().isEmpty())?"ملقتش إجراء محلي مناسب للأمر.":local;
                runOnUiThread(()->{masterEvent(ShadowMasterEventBus.Type.ACTION_EXECUTED,routedRequest,routedPlan.routeName(),out,true);masterEvent(ShadowMasterEventBus.Type.VERIFICATION_RESULT,routedRequest,routedPlan.routeName(),"local_result",true);masterEvent(ShadowMasterEventBus.Type.COMPLETED,routedRequest,routedPlan.routeName(),"completed",true);stage("verifying");assistant(out);stage("done");speak(out);});
            }).start();
            return;
        }

        // CHAT route: online-only AI with streaming response transport.
        runStreamChat(request,plan);
        return;
    }
    private TextView beginStreamingAssistant(){
        LinearLayout block=new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0,dp(3),0,dp(3));
        TextView v=tv("",16);
        v.setPadding(0,dp(8),dp(18),dp(5));
        v.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        block.addView(v,new LinearLayout.LayoutParams(-1,-2));
        messages.addView(block);
        bottom();
        return v;
    }
    private void appendStreamingAssistant(TextView view,String delta){
        if(view==null||delta==null||delta.isEmpty())return;
        view.append(delta);
        bottom();
    }
    private void runStreamChat(String request,ShadowMasterOrchestrator.Plan plan){
        stage("online");
        new Thread(()->{
            final StringBuilder transcript=new StringBuilder();
            final TextView[] streamView=new TextView[1];
            final boolean[] streamed={false};
            try{
                cloud.streamChat(request,reasoningEffort,new ShadowCloudClient.StreamListener(){
                    public void onDelta(String delta){
                        if(delta==null||delta.isEmpty())return;
                        streamed[0]=true;
                        transcript.append(delta);
                        runOnUiThread(()->{
                            if(streamView[0]==null)streamView[0]=beginStreamingAssistant();
                            appendStreamingAssistant(streamView[0],delta);
                            stage("analyzing");
                        });
                    }
                    public void onDone(ShadowCloudClient.StreamDone done){
                        cloudOnline=true;
                        final String answer=transcript.toString().trim();
                        runOnUiThread(()->{
                            masterEvent(ShadowMasterEventBus.Type.VERIFICATION_RESULT,request,plan.routeName(),"online_stream_completed",!answer.isEmpty());
                            masterEvent(ShadowMasterEventBus.Type.MEMORY_WRITE,request,plan.routeName(),"conversation_response",true);
                            masterEvent(ShadowMasterEventBus.Type.COMPLETED,request,plan.routeName(),"online_stream_chat_completed",!answer.isEmpty());
                            if(!answer.isEmpty()){
                                if(streamView[0]==null)assistant(answer);
                                speak(answer);
                            }
                            stage("online");
                        });
                    }
                    public void onPending(ShadowCloudClient.PendingAction action,String provider,String responseId){
                        cloudOnline=true;
                        runOnUiThread(()->{
                            if(streamView[0]!=null&&!transcript.toString().trim().isEmpty())streamView[0].append("\n");
                            ShadowCloudClient.CloudReply pendingReply=new ShadowCloudClient.CloudReply(transcript.toString().trim(),responseId,provider,"",false,action);
                            masterEvent(ShadowMasterEventBus.Type.ACTION_REQUESTED,request,plan.routeName(),"online_stream_action_pending",true);
                            handlePendingAction(request,pendingReply);
                        });
                    }
                });
            }catch(Throwable cloudError){
                cloudOnline=false;
                final String message=String.valueOf(cloudError.getMessage()==null?"online_service_unavailable":cloudError.getMessage());
                if(!streamed[0]){
                    try{
                        final ShadowCloudClient.CloudReply fallback=cloud.chat(request,reasoningEffort);
                        cloudOnline=true;
                        runOnUiThread(()->{
                            if(fallback.pendingAction!=null){
                                masterEvent(ShadowMasterEventBus.Type.ACTION_REQUESTED,request,plan.routeName(),"online_stream_fallback_action_pending",true);
                                handlePendingAction(request,fallback);
                                return;
                            }
                            masterEvent(ShadowMasterEventBus.Type.VERIFICATION_RESULT,request,plan.routeName(),"online_fallback_response_received",!fallback.answer.isEmpty());
                            masterEvent(ShadowMasterEventBus.Type.MEMORY_WRITE,request,plan.routeName(),"conversation_response",true);
                            masterEvent(ShadowMasterEventBus.Type.COMPLETED,request,plan.routeName(),"online_chat_completed",!fallback.answer.isEmpty());
                            assistant(fallback.answer);
                            speak(fallback.answer);
                            stage("online");
                        });
                    }catch(Throwable fallbackError){
                        runOnUiThread(()->{
                            masterEvent(ShadowMasterEventBus.Type.FAILED,request,plan.routeName(),"online_agent_unavailable:"+fallbackError.getClass().getSimpleName(),false);
                            assistant("الأونلاين مش متاح دلوقتي، وSHADOW مش هيستخدم نسخة أوفلاين أو إجابة محلية بدل الذكاء السحابي.");
                            stage("reconnecting");
                        });
                    }
                }else{
                    runOnUiThread(()->{
                        masterEvent(ShadowMasterEventBus.Type.FAILED,request,plan.routeName(),"online_stream_interrupted:"+message,false);
                        assistant("البث النصي انقطع أثناء الرد. جرّب نفس الأمر مرة تانية.");
                        stage("reconnecting");
                    });
                }
            }
        }).start();
    }

    private boolean isImage(String s){String x=s.toLowerCase(Locale.ROOT);return x.contains("صمم صورة")||x.contains("اعمل صورة")||x.contains("صورة لـ")||x.contains("generate image")||x.contains("create an image")||x.contains("design an image");}
    private void generateImage(String prompt){system("SHADOW • بيصمم الصورة أونلاين…");new Thread(()->{try{String b64=cloud.generateImage(prompt);byte[] data=android.util.Base64.decode(b64,android.util.Base64.DEFAULT);runOnUiThread(()->{ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(BitmapFactory.decodeStream(new ByteArrayInputStream(data)));messages.addView(image,new LinearLayout.LayoutParams(-1,dp(320)));masterEvent(ShadowMasterEventBus.Type.VERIFICATION_RESULT,prompt,"image","image_rendered",true);masterEvent(ShadowMasterEventBus.Type.COMPLETED,prompt,"image","image_completed",true);assistant("اتفضل — الصورة جاهزة.");stage("online");});}catch(Throwable e){runOnUiThread(()->{assistant("مش قادر أولّد الصورة دلوقتي: "+e.getMessage());stage("reconnecting");});}}).start();}
    private void attach(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("Files");p.getMenu().add("Photos");p.getMenu().add("Camera");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.equals("Files")){Intent x=new Intent(Intent.ACTION_OPEN_DOCUMENT);x.addCategory(Intent.CATEGORY_OPENABLE);x.setType("*/*");startActivityForResult(x,FILE);}else if(n.equals("Photos")){Intent x=new Intent(Intent.ACTION_PICK);x.setType("image/*");startActivityForResult(x,FILE);}else{try{startActivityForResult(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),CAMERA);}catch(Exception e){system("No camera application is available.");}}return true;});p.show();}
    private void features(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));String[] a={"📺 TV Remote","❄️ AC Remote","🔎 Remote capabilities","🌐 Web search","🎨 Image design","⚡ Full 12-Step Master","📱 Deep phone control","🧠 Development Agent","🛰 Master Bus","📍 Spatial Radar","🧠 Think Hard"+(reasoningMode?" ✓":""),"🔴 Deep Think"+("xhigh".equals(reasoningEffort)?" ✓":""),"🛑 Stop Deep Think","🔐 GitHub Authorization","🎙 Hands-Free: "+(handsFreeVoice?"ON":"OFF"),"🌙 Wake Word: "+(isWakeEnabled()?"ON":"OFF"),"Profile / Memory","System status","🔒 Lock Master identity","Settings"};for(String s:a)p.getMenu().add(s);p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.startsWith("📺")||n.startsWith("❄️")||n.startsWith("🔎"))remotes.showCenter();else if(n.startsWith("⚡ Full 12-Step Master"))runFullMasterPipeline();else if(n.startsWith("📱"))startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));else if(n.startsWith("🛰"))assistant(lifecycleBridge==null?"Master Bus غير متاح.":lifecycleBridge.status());else if(n.startsWith("📍")){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},904);}else{spatialRadar.start();assistant("Spatial Radar اتفعل. تحديثات المكان هتدخل Master Bus بدون تخزين إحداثيات داخل الـevent.");}}else if(n.startsWith("🌐")){input.setText("ابحث أونلاين عن ");input.requestFocus();}else if(n.startsWith("🎨")){input.setText("صمم صورة: ");input.requestFocus();}else if(n.startsWith("🧠 Think Hard")){setReasoning(true,"high");assistant("🧠 Think Hard اتفعل — Black/Red mode.");}else if(n.startsWith("🔴 Deep Think")){setReasoning(true,"xhigh");assistant("🔴 Deep Think اتفعل — Black/Red mode.");}else if(n.startsWith("🛑 Stop Deep Think")){setReasoning(false,"none");assistant("تم إيقاف Think Hard / Deep Think.");}else if(n.startsWith("🧠 Development"))system("Development Agent: analyze → plan → approve → edit → test → report");else if(n.startsWith("🔐 GitHub"))connectGithub();else if(n.startsWith("🎙 Hands-Free")){handsFreeVoice=!handsFreeVoice;voiceState.setHandsFree(handsFreeVoice);assistant(handsFreeVoice?"Hands‑Free اتفعل — بعد كل رد صوتي Shadow هيبدأ يسمع تاني تلقائيًا.":"Hands‑Free اتقفل — الصوت هيفضل بنقرة واحدة فقط.");}
        else if(n.startsWith("🌙 Wake Word")){toggleWakeWord();}else if(n.equals("System status"))assistant(core.handle("status"));else if(n.startsWith("🔒")){identity.lock();assistant("تم قفل هوية الـMaster.");}else if(n.equals("Settings"))startActivity(new Intent(Settings.ACTION_SETTINGS));return true;});p.show();}
    /** MOD-86: full twelve-stage button now enters the same cloud master route used by normal AI execution. */
    private void runFullMasterPipeline(){
        final String request=input==null?"":input.getText().toString().trim();
        final String task=request.isEmpty()?"شغّل مسار SHADOW الكامل واختبر المراحل الـ12":"شغّل مسار SHADOW الكامل: "+request;
        system("SHADOW • تشغيل المراحل الـ12 أونلاين…");
        new Thread(()->{
            try{
                final String result=cloud.runMasterPipeline(task,identity!=null&&identity.isAuthenticated(),false);
                runOnUiThread(()->{assistant(result);stage("done");});
            }catch(Throwable e){
                runOnUiThread(()->{
                    assistant("المسار الأونلاين الكامل متاح لكن الطلب فشل: "+String.valueOf(e.getMessage())+"\n\nلن يتم تشغيل AI أوفلاين بدلًا منه.");
                    stage("reconnecting");
                });
            }
        }).start();
    }

    private boolean isWakeEnabled(){return getSharedPreferences(WAKE_PREFS,MODE_PRIVATE).getBoolean(WAKE_ENABLED,false);}
    private void toggleWakeWord(){
        boolean next=!isWakeEnabled();
        getSharedPreferences(WAKE_PREFS,MODE_PRIVATE).edit().putBoolean(WAKE_ENABLED,next).apply();
        if(next){try{ShadowWakeWordService.start(this);assistant("Wake Word اتفعل. SHADOW دلوقتي شغال في الخلفية على الجهاز، وبيستخدم ميكروفون مع إشعار نظام.");}catch(Throwable t){assistant("مش قادر أشغل Wake Word حالياً: "+t.getMessage());}}
        else {ShadowWakeWordService.stop(this);assistant("Wake Word اتقفل.");}
    }
    private void listen(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;}voiceTurnActive=true;stage("listening");if(liveVoice!=null){liveVoice.setVisibility(View.VISIBLE);liveVoice.setText("🎙 Listening…");}voice.start("ar-EG");}
    private void showLiveVoice(String text){if(liveVoice==null)return;liveVoice.setVisibility(View.VISIBLE);liveVoice.setAlpha(1f);liveVoice.setText("🎙 "+text);}
    private void handleVoiceText(String text){String s=text==null?"":text.trim();if(s.isEmpty())return;if(liveVoice!=null){liveVoice.setText("");liveVoice.setVisibility(View.GONE);}input.setText(s);send();}
    private void stopCurrentTts(){try{if(tts!=null)tts.stop();}catch(Exception ignored){}try{if(player!=null){player.stop();player.release();player=null;}}catch(Exception ignored){}}
    private void startBargeInMonitor(){if(bargeInMonitor!=null&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)bargeInMonitor.start();}
    private void stopBargeInMonitor(){if(bargeInMonitor!=null)bargeInMonitor.stop();}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==904&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED&&spatialRadar!=null){spatialRadar.start();assistant("Spatial Radar اتفعل.");}}
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(req==FILE){
            Uri uri=data.getData();
            if(uri==null){system("الملف المرفق مش متاح.");return;}
            system("SHADOW • بيحلل الصورة أونلاين…");
            new Thread(()->{
                try{
                    byte[] bytes=readUriBytes(uri);
                    String mime=getContentResolver().getType(uri);
                    String answer=cloud.analyzeImage(bytes,mime,"حلل الصورة، استخرج ما يمكن التحقق منه، واذكر أي عدم يقين. لو فيها واجهة تطبيق أو شاشة، صف العناصر المهمة.");
                    runOnUiThread(()->{assistant(answer);masterEvent(ShadowMasterEventBus.Type.VERIFICATION_RESULT,uri.toString(),"vision","vision_analyzed",!answer.isEmpty());stage("done");});
                }catch(Throwable e){runOnUiThread(()->{assistant("تعذر تحليل الصورة أونلاين: "+String.valueOf(e.getMessage()));stage("reconnecting");});}
            }).start();
        }else if(req==CAMERA){
            android.graphics.Bitmap bmp=data.getParcelableExtra("data");
            if(bmp==null){system("التقاط الكاميرا ما رجعش صورة قابلة للتحليل.");return;}
            system("SHADOW • بيحلل لقطة الكاميرا أونلاين…");
            new Thread(()->{
                try{
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG,88,out);
                    String answer=cloud.analyzeImage(out.toByteArray(),"image/jpeg","حلل لقطة الكاميرا بدقة، اذكر فقط ما يظهر بالفعل، ووضح عدم اليقين.");
                    runOnUiThread(()->{assistant(answer);masterEvent(ShadowMasterEventBus.Type.VERIFICATION_RESULT,"camera","vision","camera_analyzed",!answer.isEmpty());stage("done");});
                }catch(Throwable e){runOnUiThread(()->{assistant("تعذر تحليل لقطة الكاميرا: "+String.valueOf(e.getMessage()));stage("reconnecting");});}
            }).start();
        }else system("Attached: "+(data.getData()!=null?String.valueOf(data.getData().getLastPathSegment()):"camera capture"));
    }
    private byte[] readUriBytes(Uri uri)throws Exception{
        try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            if(in==null)throw new java.io.IOException("stream_unavailable");
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()>18*1024*1024)throw new java.io.IOException("image_too_large");out.write(buf,0,n);}
            return out.toByteArray();
        }
    }
    private boolean arabic(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c>=0x0600&&c<=0x06FF)return true;}return false;}
    private void speak(String s){if(s==null||s.trim().isEmpty())return;if(!voiceOutput){if(voiceTurnActive&&voiceState!=null)voiceState.responseFinishedWithoutTts();return;}if(voiceState!=null)voiceState.ttsStarted();if(cloudOnline&&cloudVoice)new Thread(()->{try{byte[] audio=cloud.synthesizeSpeech(s);File f=new File(getCacheDir(),"shadow_voice.mp3");try(FileOutputStream o=new FileOutputStream(f)){o.write(audio);}runOnUiThread(()->play(f));}catch(Exception e){runOnUiThread(()->localSpeak(s));}}).start();else localSpeak(s);}
    private void play(File f){try{if(player!=null)player.release();player=new MediaPlayer();player.setDataSource(f.getAbsolutePath());player.setOnCompletionListener(m->{m.release();player=null;stopBargeInMonitor();if(voiceState!=null)voiceState.ttsFinished();});player.setOnErrorListener((m,what,extra)->{try{m.release();}catch(Exception ignored){}player=null;stopBargeInMonitor();if(voiceState!=null)voiceState.ttsFinished();return true;});player.setOnPreparedListener(m->{m.start();startBargeInMonitor();});player.prepare();}catch(Exception e){localSpeak("حصلت مشكلة في الصوت.");}}
    private void localSpeak(String s){if(tts==null||!voiceOutput){if(voiceState!=null)voiceState.responseFinishedWithoutTts();return;}try{tts.setLanguage(arabic(s)?new Locale("ar","EG"):Locale.US);tts.setPitch(.72f);tts.setSpeechRate(.96f);tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"shadow");}catch(Exception ignored){if(voiceState!=null)voiceState.ttsFinished();}}
    @Override public void onInit(int c){if(tts!=null){tts.setLanguage(new Locale("ar","EG"));tts.setPitch(.72f);tts.setSpeechRate(.96f);tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){@Override public void onStart(String id){startBargeInMonitor();}@Override public void onDone(String id){stopBargeInMonitor();runOnUiThread(()->{if(voiceState!=null)voiceState.ttsFinished();});}@Override public void onError(String id){stopBargeInMonitor();runOnUiThread(()->{if(voiceState!=null)voiceState.ttsFinished();});}});}}
    @Override protected void onDestroy(){cleanupVoiceprint();handsFreeVoice=false;voiceHandler.removeCallbacksAndMessages(null);if(voiceState!=null)voiceState.setHandsFree(false);stopBargeInMonitor();if(bargeInMonitor!=null)bargeInMonitor.destroy();if(voice!=null)voice.destroy();stopCurrentTts();if(tts!=null)tts.shutdown();if(spatialRadar!=null)spatialRadar.stop();if(lifecycleBridge!=null)lifecycleBridge.destroy();super.onDestroy();}
}
