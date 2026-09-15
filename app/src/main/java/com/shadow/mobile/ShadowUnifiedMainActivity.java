package com.shadow.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/** MOD-70.4: one unified Shadow command surface. Online is primary; offline is internal fallback only. */
public class ShadowUnifiedMainActivity extends Activity implements TextToSpeech.OnInitListener {
    private final int BG=Color.rgb(13,14,17), SURFACE=Color.rgb(29,31,36), SURFACE2=Color.rgb(42,44,51), TEXT=Color.rgb(241,243,246), MUTED=Color.rgb(155,160,170);
    private ShadowCore core;
    private ShadowCloudClient cloud;
    private ShadowVoiceIdentityGateway identity;
    private ShadowVoiceRecognizer voice;
    private LinearLayout messages;
    private EditText input;
    private TextView status;
    private TextToSpeech tts;
    private boolean online;

    private int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int c,float r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
    private TextView tv(String s,float z){TextView v=new TextView(this);v.setText(s);v.setTextColor(TEXT);v.setTextSize(z);return v;}
    private void stage(String s){if(status!=null)status.setText(ShadowProcessIndicators.render(s));}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        core=new ShadowCore(this); cloud=new ShadowCloudClient(this); identity=new ShadowVoiceIdentityGateway(this); tts=new TextToSpeech(this,this);
        voice=new ShadowVoiceRecognizer(this,new ShadowVoiceRecognizer.Listener(){
            public void onListening(){runOnUiThread(()->stage("listening"));}
            public void onText(String t){runOnUiThread(()->{input.setText(t);send();});}
            public void onError(String m){runOnUiThread(()->system(m));}
        });
        buildUi();
        new Thread(()->{online=cloud.health();runOnUiThread(()->stage(online?"online":"ready"));}).start();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG); root.setPadding(dp(8),dp(4),dp(8),dp(5));
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(4),dp(4),dp(4),dp(4));
        TextView menu=tv("☰",24); menu.setGravity(Gravity.CENTER); head.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(52)));
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.shadow_logo); logo.setPadding(dp(8),dp(8),dp(8),dp(8)); head.addView(logo,new LinearLayout.LayoutParams(dp(50),dp(52)));
        LinearLayout titleBox=new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL); titleBox.setGravity(Gravity.CENTER);
        TextView title=tv("SHADOW",18); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); titleBox.addView(title);
        status=tv("جاهز",11); status.setTextColor(Color.rgb(112,210,160)); status.setGravity(Gravity.CENTER); titleBox.addView(status); head.addView(titleBox,new LinearLayout.LayoutParams(0,dp(52),1));
        TextView more=tv("⋮",26); more.setGravity(Gravity.CENTER); head.addView(more,new LinearLayout.LayoutParams(dp(48),dp(52))); root.addView(head);

        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(dp(12),dp(10),dp(12),dp(18)); sc.addView(messages); root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        assistant("أهلاً محمد 👋\nأنا SHADOW.\nقول اللي إنت عايزه بطريقتك العادية.\nالأوامر العادية بتنفيذ مباشر، والإنترنت هو المسار الأساسي، وبدون إنترنت الـfallback بيشتغل تلقائيًا من غير ما يظهر كـLocal Chat.");

        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(5),dp(3),dp(5),dp(3)); bar.setBackground(bg(SURFACE,24));
        TextView plus=tv("＋",25); plus.setGravity(Gravity.CENTER); bar.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(56)));
        input=new EditText(this); input.setHint("اكتب لـ SHADOW..."); input.setHintTextColor(MUTED); input.setTextColor(TEXT); input.setTextSize(16); input.setMaxLines(5); input.setBackgroundColor(Color.TRANSPARENT); bar.addView(input,new LinearLayout.LayoutParams(0,dp(56),1));
        TextView mic=tv("🎙",24); mic.setGravity(Gravity.CENTER); bar.addView(mic,new LinearLayout.LayoutParams(dp(52),dp(56)));
        TextView send=tv("➤",23); send.setGravity(Gravity.CENTER); bar.addView(send,new LinearLayout.LayoutParams(dp(52),dp(56))); root.addView(bar,new LinearLayout.LayoutParams(-1,dp(64)));
        TextView hint=tv("🎙 صوت  •  📎 ملفات/صور  •  🌐 تنفيذ أونلاين  •  GitHub",10); hint.setTextColor(MUTED); hint.setGravity(Gravity.CENTER); root.addView(hint,new LinearLayout.LayoutParams(-1,dp(24)));
        setContentView(root);

        menu.setOnClickListener(v->attach()); plus.setOnClickListener(v->attach()); mic.setOnClickListener(v->listen()); send.setOnClickListener(v->send()); more.setOnClickListener(v->features());
        input.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEND){send();return true;}return false;});
    }

    private TextView action(String label){TextView v=tv(label,17);v.setGravity(Gravity.CENTER);v.setTextColor(MUTED);v.setPadding(dp(8),0,dp(8),0);v.setMinWidth(dp(38));v.setMinHeight(dp(38));return v;}
    private void assistant(String s){
        LinearLayout block=new LinearLayout(this); block.setOrientation(LinearLayout.VERTICAL); block.setPadding(0,dp(3),0,dp(3));
        TextView v=tv(s,16); v.setPadding(0,dp(8),dp(18),dp(5)); v.setAutoLinkMask(android.text.util.Linkify.WEB_URLS); block.addView(v,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        TextView copy=action("⧉"),like=action("👍"),dislike=action("👎"),read=action("🔊"),share=action("↗"),more=action("⋮");
        bar.addView(copy);bar.addView(like);bar.addView(dislike);bar.addView(read);bar.addView(share);bar.addView(more);
        copy.setOnClickListener(x->copyText(s)); like.setOnClickListener(x->{like.setTextColor(Color.rgb(112,210,160));Toast("تم تسجيل الإعجاب");}); dislike.setOnClickListener(x->{dislike.setTextColor(Color.rgb(230,120,120));Toast("تم تسجيل عدم الإعجاب");}); read.setOnClickListener(x->speakNow(s)); share.setOnClickListener(x->shareText(s)); more.setOnClickListener(x->assistantMenu(s));
        block.addView(bar,new LinearLayout.LayoutParams(-1,dp(42))); messages.addView(block); bottom();
    }
    private void user(String s){TextView v=tv(s,16);v.setPadding(dp(14),dp(9),dp(14),dp(9));v.setBackground(bg(SURFACE2,18));messages.addView(v);bottom();}
    private void system(String s){TextView v=tv(s,11);v.setTextColor(MUTED);messages.addView(v);bottom();}
    private void Toast(String s){android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show();}
    private void bottom(){messages.post(()->{ViewParent p=messages.getParent();if(p instanceof ScrollView)((ScrollView)p).fullScroll(View.FOCUS_DOWN);});}

    private void send(){
        String s=input.getText().toString().trim(); if(s.isEmpty())return; user(s); input.setText(""); stage("thinking");
        if(handleIdentity(s))return;
        String x=s.toLowerCase(Locale.ROOT);
        if(isImage(s)){stage("designing");system("SHADOW • طلب صورة أونلاين…");return;}
        if(ShadowOnlineExecutionRouter.requiresLocalExecution(s)){
            stage("executing"); new Thread(()->runLocal(s)).start(); return;
        }
        stage("understanding"); new Thread(()->runCloud(s)).start();
    }

    private boolean handleIdentity(String s){
        String x=s.trim().toLowerCase(Locale.ROOT);
        if(x.startsWith("عيّن كلمة السر:")||x.startsWith("عين كلمة السر:")||x.startsWith("عيّن كلمه السر:")||x.startsWith("عين كلمه السر:")||x.startsWith("set passphrase:")||x.startsWith("set password:")){
            String p=identity.extractPassphrase(s); assistant(identity.enroll(p)?"تم تسجيل كلمة سر الـMaster محليًا بشكل آمن.":"كلمة السر لازم تكون 6 أحرف/رموز على الأقل."); return true;
        }
        if(x.contains("حالة الهوية")||x.contains("حاله الهويه")||x.equals("identity status")){assistant(identity.status());return true;}
        if(x.contains("اقفل الهوية")||x.contains("اقفل الهويه")||x.equals("lock identity")){identity.lock();assistant("تم قفل هوية الـMaster.");return true;}
        return false;
    }

    private void runLocal(String s){
        try{
            boolean auth=identity.isAuthenticated();
            String r=ShadowPhoneController.execute(this,s);
            if(r==null||r.trim().isEmpty()) r=core.handle(s,auth,true,"android-unified");
            final String answer=r==null?"مش قادر أحدد إجراء آمن للأمر ده على الجهاز.":r;
            runOnUiThread(()->{assistant(answer);speakNow(answer);stage("done");});
        }catch(Throwable e){runOnUiThread(()->{assistant("تعذر تنفيذ الأمر على الجهاز: "+safe(e));stage("done");});}
    }

    private void runCloud(String s){
        try{
            ShadowCloudClient.CloudReply r=cloud.chat(s); online=true; runOnUiThread(()->{assistant(r.answer);speakNow(r.answer);stage("done");});
        }catch(Throwable e){
            online=false;
            String fallback=null;
            try{fallback=core.handle(s,identity.isAuthenticated(),true,"android-unified");}catch(Throwable ignored){}
            final String answer=(fallback==null||fallback.trim().isEmpty())?core.offlineChat(s):fallback;
            runOnUiThread(()->{assistant(answer);speakNow(answer);stage("done");});
        }
    }

    private boolean isImage(String s){String x=s.toLowerCase(Locale.ROOT);boolean n=x.contains("صورة")||x.contains("صوره")||x.contains("image")||x.contains("picture");boolean v=x.contains("اعمل")||x.contains("اعملي")||x.contains("ارسم")||x.contains("صمم")||x.contains("generate")||x.contains("create");return(n&&v)||x.startsWith("ارسم ")||x.startsWith("generate ")||x.startsWith("create ");}
    private String safe(Throwable e){String m=e.getMessage();return m==null?"unknown":m;}
    private void copyText(String s){try{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("SHADOW",s));Toast("اتنسخ للحافظة");}catch(Exception e){system("مش قادر أنسخ الرد دلوقتي.");}}
    private void shareText(String s){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,s);startActivity(Intent.createChooser(i,"مشاركة رد SHADOW"));}catch(Exception e){system("المشاركة غير متاحة دلوقتي.");}}
    private void speakNow(String s){if(s==null||s.trim().isEmpty())return; if(!online){localSpeak(s);return;}new Thread(()->{try{byte[] audio=cloud.synthesizeSpeech(s);File f=new File(getCacheDir(),"shadow_voice.mp3");try(FileOutputStream o=new FileOutputStream(f)){o.write(audio);}runOnUiThread(()->playAudio(f));}catch(Exception e){runOnUiThread(()->localSpeak(s));}}).start();}
    private void playAudio(File f){try{android.media.MediaPlayer p=new android.media.MediaPlayer();p.setDataSource(f.getAbsolutePath());p.setOnCompletionListener(android.media.MediaPlayer::release);p.prepare();p.start();}catch(Exception e){localSpeak(f.getName());}}
    private void localSpeak(String s){if(tts==null)return;try{tts.setLanguage(new Locale("ar","EG"));tts.setPitch(.72f);tts.setSpeechRate(.96f);tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"shadow");}catch(Exception ignored){}}
    private void listen(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},801);return;}stage("listening");voice.start("ar-EG");}
    private void attach(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("ملفات");p.getMenu().add("صور");p.getMenu().add("كاميرا");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();Intent x;if(n.equals("ملفات")){x=new Intent(Intent.ACTION_OPEN_DOCUMENT);x.addCategory(Intent.CATEGORY_OPENABLE);x.setType("*/*");startActivityForResult(x,802);}else if(n.equals("صور")){x=new Intent(Intent.ACTION_PICK);x.setType("image/*");startActivityForResult(x,802);}else{try{x=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);startActivityForResult(x,803);}catch(Exception e){system("الكاميرا مش متاحة.");}}return true;});p.show();}
    private void features(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("📊 حالة SHADOW");p.getMenu().add("🔐 حالة الهوية");p.getMenu().add("⚙ إعدادات الهاتف");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.startsWith("📊"))assistant(core.handle("status",identity.isAuthenticated(),true,"android-unified"));else if(n.startsWith("🔐"))assistant(identity.status());else startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));return true;});p.show();}
    private void assistantMenu(String s){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("⧉ نسخ");p.getMenu().add("↗ مشاركة");p.getMenu().add("🔊 قراءة بصوت");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.contains("نسخ"))copyText(s);else if(n.contains("مشاركة"))shareText(s);else speakNow(s);return true;});p.show();}
    @Override public void onInit(int c){if(tts!=null){tts.setLanguage(new Locale("ar","EG"));tts.setPitch(.72f);tts.setSpeechRate(.96f);}}
    @Override protected void onDestroy(){try{voice.stop();}catch(Exception ignored){}if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
