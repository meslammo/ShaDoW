package com.shadow.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.speech.tts.TextToSpeech;

import java.io.ByteArrayInputStream;
import java.util.*;

/** MOD-29.6: ChatGPT-style SHADOW shell with in-app voice, device controls, remotes and image generation. */
public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_MIC=801, REQ_FILE=802, REQ_CAMERA=803, REQ_CONTACTS=805, REQ_CALL=806;
    private final int BG=Color.rgb(13,14,17), SURFACE=Color.rgb(29,31,36), SURFACE2=Color.rgb(42,44,51), TEXT=Color.rgb(241,243,246), MUTED=Color.rgb(155,160,170);
    private ShadowCore core; private ShadowCloudClient cloud; private ShadowVoiceRecognizer voice; private ShadowRemoteController remotes;
    private LinearLayout messages; private EditText input; private TextView status;
    private TextToSpeech tts; private boolean englishVoice; private boolean cloudOnline; private boolean conversationMode=true; private String pendingCallCommand;

    @Override public void onCreate(Bundle state){
        super.onCreate(state); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        core=new ShadowCore(this); cloud=new ShadowCloudClient(this); remotes=new ShadowRemoteController(this);
        voice=new ShadowVoiceRecognizer(this,new ShadowVoiceRecognizer.Listener(){
            @Override public void onListening(){runOnUiThread(()->status.setText("LISTENING • VOICE"));}
            @Override public void onText(String text){runOnUiThread(()->{input.setText(text); send();});}
            @Override public void onError(String message){runOnUiThread(()->{addSystem(message); if(conversationMode) queueNextVoiceTurn();});}
        });
        tts=new TextToSpeech(this,this); buildUi(); checkCloud();
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView tv(String text,float sp){TextView v=new TextView(this);v.setText(text);v.setTextColor(TEXT);v.setTextSize(sp);return v;}
    private GradientDrawable bg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private TextView iconButton(String text,String desc){TextView v=tv(text,24);v.setGravity(Gravity.CENTER);v.setContentDescription(desc);v.setClickable(true);v.setFocusable(true);v.setMinWidth(dp(44));v.setMinHeight(dp(44));return v;}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG); root.setPadding(dp(8),dp(4),dp(8),dp(5));
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(4),dp(4),dp(4),dp(4));
        TextView menu=iconButton("☰","Menu and files"); header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(52)));
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.shadow_logo); logo.setPadding(dp(8),dp(8),dp(8),dp(8)); header.addView(logo,new LinearLayout.LayoutParams(dp(50),dp(52)));
        LinearLayout titleBox=new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL); titleBox.setGravity(Gravity.CENTER);
        TextView title=tv("SHADOW",18); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); titleBox.addView(title);
        status=tv("CONNECTING • ONLINE",11); status.setTextColor(Color.rgb(112,210,160)); titleBox.addView(status);
        header.addView(titleBox,new LinearLayout.LayoutParams(0,dp(52),1));
        TextView more=iconButton("⋮","Features"); header.addView(more,new LinearLayout.LayoutParams(dp(48),dp(52))); root.addView(header);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(dp(12),dp(10),dp(12),dp(18)); scroll.addView(messages); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addAssistant("أهلاً محمد 👋\nأنا SHADOW — Z.\nصوت ومحادثة Hands-free من جوه التطبيق، من غير نافذة Google.\nأونلاين: بحث ويب + معلومات حديثة + صور.\nأوفلاين: تحكم محلي وصلاحيات الجهاز حسب إمكانياته.");

        LinearLayout composerWrap=new LinearLayout(this); composerWrap.setOrientation(LinearLayout.VERTICAL); composerWrap.setPadding(dp(4),dp(4),dp(4),dp(2));
        LinearLayout composer=new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(dp(5),dp(3),dp(5),dp(3)); composer.setBackground(bg(SURFACE,24));
        TextView plus=iconButton("＋","Add files, photos or camera"); composer.addView(plus,new LinearLayout.LayoutParams(dp(46),dp(52)));
        input=new EditText(this); input.setSingleLine(false); input.setMaxLines(5); input.setMinLines(1); input.setGravity(Gravity.CENTER_VERTICAL); input.setImeOptions(EditorInfo.IME_ACTION_SEND); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.setHint("Message SHADOW"); input.setHintTextColor(MUTED); input.setTextColor(TEXT); input.setTextSize(16); input.setPadding(dp(8),0,dp(6),0); input.setBackgroundColor(Color.TRANSPARENT); composer.addView(input,new LinearLayout.LayoutParams(0,dp(54),1));
        TextView mic=iconButton("🎙","Voice input"); composer.addView(mic,new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView send=iconButton("➤","Send message"); send.setTextSize(23); composer.addView(send,new LinearLayout.LayoutParams(dp(52),dp(52)));
        composerWrap.addView(composer,new LinearLayout.LayoutParams(-1,dp(60)));
        TextView hint=tv("＋ Files/Photos  •  🎙 Voice  •  Hands-free ON  •  Web Search ON",10); hint.setTextColor(MUTED); hint.setGravity(Gravity.CENTER); composerWrap.addView(hint,new LinearLayout.LayoutParams(-1,dp(24))); root.addView(composerWrap);
        setContentView(root);

        menu.setOnClickListener(v->showAttach()); more.setOnClickListener(v->showFeatures()); plus.setOnClickListener(v->showAttach()); mic.setOnClickListener(v->listen()); send.setOnClickListener(v->send());
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEND || (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN)){send();return true;}return false;});
    }

    private void addUser(String s){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.END);row.setPadding(dp(40),dp(5),0,dp(5));TextView v=tv(s,16);v.setPadding(dp(15),dp(10),dp(15),dp(10));v.setBackground(bg(SURFACE2,18));row.addView(v,new LinearLayout.LayoutParams(-2,-2));messages.addView(row);bottom();}
    private void addAssistant(String s){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.START);row.setPadding(0,dp(6),dp(18),dp(6));TextView v=tv(s,16);v.setLineSpacing(0,1.15f);row.addView(v,new LinearLayout.LayoutParams(-1,-2));messages.addView(row);bottom();}
    private void addSystem(String s){TextView v=tv(s,11);v.setTextColor(MUTED);v.setPadding(dp(4),dp(5),dp(4),dp(5));messages.addView(v);bottom();}
    private void addImage(byte[] bytes){ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(BitmapFactory.decodeStream(new ByteArrayInputStream(bytes)));image.setBackground(bg(SURFACE,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(320));p.setMargins(0,dp(8),dp(18),dp(8));messages.addView(image,p);bottom();}
    private void bottom(){messages.post(()->{ViewParent p=messages.getParent();if(p instanceof ScrollView)((ScrollView)p).fullScroll(View.FOCUS_DOWN);});}

    private void checkCloud(){new Thread(()->{boolean ok=cloud.health();runOnUiThread(()->{cloudOnline=ok;status.setText(ok?"ONLINE • CONNECTED":"OFFLINE • LOCAL MODE");status.setTextColor(ok?Color.rgb(112,210,160):Color.rgb(240,170,100));});}).start();}

    private void send(){final String s=input.getText().toString().trim();if(s.isEmpty())return;addUser(s);input.setText("");status.setText("THINKING • ONLINE");
        String localCall=ShadowPhoneController.execute(this,s);
        if(localCall!=null){
            if(localCall.startsWith("PERMISSION_")){pendingCallCommand=s;if(localCall.contains("READ_CONTACTS"))requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},REQ_CONTACTS);else requestPermissions(new String[]{Manifest.permission.CALL_PHONE},REQ_CALL);return;}
            addAssistant(localCall); speak(localCall); status.setText("PHONE • DONE"); queueNextVoiceTurn(); return;
        }
        String imagePrompt=imagePrompt(s);
        if(imagePrompt!=null){generateImage(imagePrompt);return;}
        new Thread(()->{
            try {
                ShadowCloudClient.CloudReply reply=cloud.chat(s); cloudOnline=true;
                runOnUiThread(()->{addAssistant(reply.answer);speak(reply.answer);status.setText("ONLINE • AI + WEB");status.setTextColor(Color.rgb(112,210,160));queueNextVoiceTurn();});
            } catch(Throwable cloudError) {
                cloudOnline=false; String local;
                try{local=core.handle(s);}catch(Throwable e){local=null;}
                if(local==null||local.trim().isEmpty())local=core.offlineChat(s);
                final String answer=local;
                runOnUiThread(()->{addAssistant(answer);speak(answer);addSystem("Cloud unavailable — local response used.");status.setText("OFFLINE • LOCAL FAILOVER");status.setTextColor(Color.rgb(240,170,100));queueNextVoiceTurn();});
            }
        }).start();
    }

    private String imagePrompt(String s){String x=s.toLowerCase(Locale.ROOT);if(x.contains("صمم صورة")||x.contains("اعمل صورة")||x.contains("اعمللى صورة")||x.contains("صورة لـ")||x.contains("generate image")||x.contains("create an image")||x.contains("design an image"))return s;return null;}
    private void generateImage(final String prompt){
        addSystem("SHADOW • بيصمم الصورة أونلاين…"); status.setText("GENERATING • IMAGE");
        new Thread(()->{try{String b64=cloud.generateImage(prompt);byte[] data=android.util.Base64.decode(b64,android.util.Base64.DEFAULT);runOnUiThread(()->{addAssistant("اتفضل — دي الصورة اللي طلبتها.");addImage(data);status.setText("ONLINE • IMAGE READY");queueNextVoiceTurn();});}catch(Throwable e){runOnUiThread(()->{addAssistant("مش قادر أولّد الصورة دلوقتي: "+e.getMessage());status.setText("ONLINE • IMAGE ERROR");queueNextVoiceTurn();});}}).start();
    }

    private void queueNextVoiceTurn(){if(!conversationMode)return;new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(conversationMode)listen();},2500);}

    private void showFeatures(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));
        p.getMenu().add("📺 TV Remote");p.getMenu().add("❄️ AC Remote");p.getMenu().add("🔎 Remote capabilities");p.getMenu().add("🌐 Web search");p.getMenu().add("🎨 Image design");p.getMenu().add("Voice language: "+(englishVoice?"English":"Arabic"));p.getMenu().add("Voice Conversation: "+(conversationMode?"ON":"OFF"));p.getMenu().add("Read last answer aloud");p.getMenu().add("Profile / Memory");p.getMenu().add("Home");p.getMenu().add("Car");p.getMenu().add("System status");p.getMenu().add("Settings");
        p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.equals("📺 TV Remote")||n.equals("❄️ AC Remote")||n.equals("🔎 Remote capabilities")){remotes.showCenter();}else if(n.equals("🌐 Web search")){input.setText("ابحث أونلاين عن ");input.requestFocus();}else if(n.equals("🎨 Image design")){input.setText("صمم صورة: ");input.requestFocus();}else if(n.equals("Voice language: "+(englishVoice?"English":"Arabic"))){englishVoice=!englishVoice;addSystem("Voice: "+(englishVoice?"English":"Arabic"));}else if(n.startsWith("Voice Conversation")){conversationMode=!conversationMode;addSystem("Hands-free voice conversation: "+(conversationMode?"ON":"OFF"));if(conversationMode)listen();else voice.stop();}else if(n.startsWith("Read last"))speak(lastAssistant());else if(n.equals("Profile / Memory"))showProfile();else if(n.equals("System status"))addAssistant(core.handle("status"));else if(n.equals("Settings"))startActivity(new Intent(Settings.ACTION_SETTINGS));else addSystem(n+" ready.");return true;});p.show();}

    private void showAttach(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("Files");p.getMenu().add("Photos");p.getMenu().add("Camera");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.equals("Files"))openFiles();else if(n.equals("Camera"))openCamera();else{Intent x=new Intent(Intent.ACTION_PICK);x.setType("image/*");startActivityForResult(x,REQ_FILE);}return true;});p.show();}
    private void openFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,REQ_FILE);}
    private void openCamera(){try{startActivityForResult(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),REQ_CAMERA);}catch(Exception e){addSystem("No camera application is available.");}}
    private void listen(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}if(!voice.isAvailable()){addSystem("خدمة الصوت مش متاحة على الجهاز.");return;}voice.start(englishVoice?"en-US":"ar-EG");}

    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null)return;Uri u=data.getData();if(u!=null){addSystem("Attached: "+u.getLastPathSegment());try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}else if(req==REQ_CAMERA)addSystem("Camera capture received.");}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(grantResults.length==0)return;
        if(requestCode==REQ_MIC&&grantResults[0]==PackageManager.PERMISSION_GRANTED){listen();}
        else if(requestCode==REQ_MIC){conversationMode=false;addSystem("Microphone permission denied — voice conversation turned OFF.");}
        else if((requestCode==REQ_CONTACTS||requestCode==REQ_CALL)&&grantResults[0]==PackageManager.PERMISSION_GRANTED&&pendingCallCommand!=null){String cmd=pendingCallCommand;pendingCallCommand=null;String r=ShadowPhoneController.execute(this,cmd);if(r!=null&&r.startsWith("PERMISSION_")){addSystem("لسه ناقصة صلاحية الاتصال.");}else{addAssistant(r==null?"تعذر تنفيذ الاتصال.":r);speak(r);queueNextVoiceTurn();}}
        else if(requestCode==REQ_CONTACTS||requestCode==REQ_CALL){pendingCallCommand=null;addSystem("الصلاحية مرفوضة — مش هقدر أنفذ المكالمة بدونها.");}
    }

    private String lastAssistant(){for(int i=messages.getChildCount()-1;i>=0;i--){View v=messages.getChildAt(i);if(v instanceof LinearLayout){LinearLayout l=(LinearLayout)v;if(l.getChildCount()>0&&l.getChildAt(0) instanceof TextView)return ((TextView)l.getChildAt(0)).getText().toString();}}return "أنا SHADOW — Z.";}
    private void showProfile(){new AlertDialog.Builder(this).setTitle("SHADOW • Z • Profile & Memory").setMessage("محمد\nEgyptian Arabic • direct practical replies\n\nSHADOW/Z knows this device profile for compatibility.\n\nCloud-first AI + local failover\n\nWeb Search + Image Design online\n\nTV / AC Remote Center\n\nPhone controls stay subject to Android permissions and the actual hardware installed.").setPositiveButton("Close",null).show();}
    private boolean arabic(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c>=0x0600&&c<=0x06FF)return true;}return false;}
    private void speak(String s){if(tts==null||s==null||s.trim().isEmpty())return;try{boolean ar=arabic(s);tts.setLanguage(ar?new Locale("ar","EG"):Locale.US);tts.setPitch(0.72f);tts.setSpeechRate(ar?0.96f:0.86f);tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"shadow");}catch(Exception ignored){}}
    @Override public void onInit(int code){if(tts!=null){tts.setLanguage(new Locale("ar","EG"));tts.setPitch(0.72f);tts.setSpeechRate(0.96f);}}
    @Override protected void onDestroy(){voice.stop();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
