package com.shadow.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** MOD-28.3: Cloud-first ChatGPT-style shell with local failover and hands-free voice turns. */
public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_MIC=801, REQ_FILE=802, REQ_CAMERA=803, REQ_SPEECH=804;
    private final int BG=Color.rgb(13,14,17), SURFACE=Color.rgb(29,31,36), SURFACE2=Color.rgb(42,44,51), TEXT=Color.rgb(241,243,246), MUTED=Color.rgb(155,160,170);
    private ShadowCore core; private ShadowCloudClient cloud;
    private LinearLayout messages; private EditText input; private TextView status;
    private TextToSpeech tts; private boolean englishVoice; private boolean cloudOnline; private boolean conversationMode=true;

    @Override public void onCreate(Bundle state){
        super.onCreate(state); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        core=new ShadowCore(this); cloud=new ShadowCloudClient(this); tts=new TextToSpeech(this,this); buildUi(); checkCloud();
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView tv(String text,float sp){TextView v=new TextView(this);v.setText(text);v.setTextColor(TEXT);v.setTextSize(sp);return v;}
    private GradientDrawable bg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private TextView iconButton(String text,String desc){TextView v=tv(text,24);v.setGravity(Gravity.CENTER);v.setContentDescription(desc);v.setClickable(true);v.setFocusable(true);v.setMinWidth(dp(44));v.setMinHeight(dp(44));return v;}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG); root.setPadding(dp(8),dp(4),dp(8),dp(5));
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(4),dp(4),dp(4),dp(4));
        TextView menu=iconButton("☰","Menu"); header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(52)));
        ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.shadow_logo); logo.setPadding(dp(8),dp(8),dp(8),dp(8)); header.addView(logo,new LinearLayout.LayoutParams(dp(50),dp(52)));
        LinearLayout titleBox=new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL); titleBox.setGravity(Gravity.CENTER);
        TextView title=tv("SHADOW",18); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); titleBox.addView(title);
        status=tv("CONNECTING • ONLINE",11); status.setTextColor(Color.rgb(112,210,160)); titleBox.addView(status);
        header.addView(titleBox,new LinearLayout.LayoutParams(0,dp(52),1));
        TextView more=iconButton("⋮","More"); header.addView(more,new LinearLayout.LayoutParams(dp(48),dp(52))); root.addView(header);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(dp(12),dp(10),dp(12),dp(18)); scroll.addView(messages); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addAssistant("أهلاً محمد 👋\nأنا SHADOW.\nأنا متوصل بالسحابة أولاً، وهرد عليك كتابة وصوت.\nVoice Conversation: ON\nلو السحابة وقعت، عندي Local failover من غير ما المحادثة تقف.");

        LinearLayout composerWrap=new LinearLayout(this); composerWrap.setOrientation(LinearLayout.VERTICAL); composerWrap.setPadding(dp(4),dp(4),dp(4),dp(2));
        LinearLayout composer=new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(dp(5),dp(3),dp(5),dp(3)); composer.setBackground(bg(SURFACE,24));
        TextView plus=iconButton("＋","Add files, photos or camera"); composer.addView(plus,new LinearLayout.LayoutParams(dp(46),dp(52)));
        input=new EditText(this); input.setSingleLine(false); input.setMaxLines(5); input.setMinLines(1); input.setGravity(Gravity.CENTER_VERTICAL); input.setImeOptions(EditorInfo.IME_ACTION_SEND); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.setHint("Message SHADOW"); input.setHintTextColor(MUTED); input.setTextColor(TEXT); input.setTextSize(16); input.setPadding(dp(8),0,dp(6),0); input.setBackgroundColor(Color.TRANSPARENT); composer.addView(input,new LinearLayout.LayoutParams(0,dp(54),1));
        TextView mic=iconButton("🎙","Voice input"); composer.addView(mic,new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView send=iconButton("➤","Send message"); send.setTextSize(23); composer.addView(send,new LinearLayout.LayoutParams(dp(52),dp(52)));
        composerWrap.addView(composer,new LinearLayout.LayoutParams(-1,dp(60)));
        TextView hint=tv("＋ Files/Photos  •  🎙 Voice  •  Hands-free ON  •  Enter Send",10); hint.setTextColor(MUTED); hint.setGravity(Gravity.CENTER); composerWrap.addView(hint,new LinearLayout.LayoutParams(-1,dp(24))); root.addView(composerWrap);
        setContentView(root);

        menu.setOnClickListener(v->showMenu()); more.setOnClickListener(v->showMenu()); plus.setOnClickListener(v->showAttach()); mic.setOnClickListener(v->listen()); send.setOnClickListener(v->send());
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEND || (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN)){send();return true;}return false;});
    }

    private void addUser(String s){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.END);row.setPadding(dp(40),dp(5),0,dp(5));TextView v=tv(s,16);v.setPadding(dp(15),dp(10),dp(15),dp(10));v.setBackground(bg(SURFACE2,18));row.addView(v,new LinearLayout.LayoutParams(-2,-2));messages.addView(row);bottom();}
    private void addAssistant(String s){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.START);row.setPadding(0,dp(6),dp(18),dp(6));TextView v=tv(s,16);v.setLineSpacing(0,1.15f);row.addView(v,new LinearLayout.LayoutParams(-1,-2));messages.addView(row);bottom();}
    private void addSystem(String s){TextView v=tv(s,11);v.setTextColor(MUTED);v.setPadding(dp(4),dp(5),dp(4),dp(5));messages.addView(v);bottom();}
    private void bottom(){messages.post(()->{ViewParent p=messages.getParent();if(p instanceof ScrollView)((ScrollView)p).fullScroll(View.FOCUS_DOWN);});}

    private void checkCloud(){new Thread(()->{boolean ok=cloud.health();runOnUiThread(()->{cloudOnline=ok;status.setText(ok?"ONLINE • CONNECTED":"ONLINE • BACKEND UNAVAILABLE");status.setTextColor(ok?Color.rgb(112,210,160):Color.rgb(240,170,100));});}).start();}

    private void send(){final String s=input.getText().toString().trim();if(s.isEmpty())return;addUser(s);input.setText("");status.setText("THINKING • ONLINE");
        new Thread(()->{
            try {
                ShadowCloudClient.CloudReply reply=cloud.chat(s);
                cloudOnline=true;
                runOnUiThread(()->{addAssistant(reply.answer);speak(reply.answer);status.setText("ONLINE • AI CONNECTED");status.setTextColor(Color.rgb(112,210,160));queueNextVoiceTurn();});
            } catch(Throwable cloudError) {
                cloudOnline=false;
                String local;
                try{local=core.handle(s);}catch(Throwable e){local=null;}
                if(local==null||local.trim().isEmpty())local=core.offlineChat(s);
                final String answer=local;
                runOnUiThread(()->{addAssistant(answer);speak(answer);addSystem("Cloud unavailable — local response used.");status.setText("ONLINE • LOCAL FAILOVER");status.setTextColor(Color.rgb(240,170,100));queueNextVoiceTurn();});
            }
        }).start();
    }

    private void queueNextVoiceTurn(){
        if(!conversationMode)return;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(conversationMode)listen();},2200);
    }

    private void showMenu(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("New chat");p.getMenu().add("Cloud status");p.getMenu().add("Voice language: "+(englishVoice?"English":"Arabic"));p.getMenu().add("Voice Conversation: "+(conversationMode?"ON":"OFF"));p.getMenu().add("Read last answer aloud");p.getMenu().add("Profile / Memory");p.getMenu().add("Files / Photos");p.getMenu().add("Camera");p.getMenu().add("Home");p.getMenu().add("Car");p.getMenu().add("System status");p.getMenu().add("Settings");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.equals("New chat")){cloud.resetConversation();messages.removeAllViews();addAssistant("محادثة جديدة. أنا SHADOW، جاهز أونلاين.");}else if(n.equals("Cloud status")){checkCloud();addSystem("Backend: "+cloud.getBaseUrl());}else if(n.startsWith("Voice language")){englishVoice=!englishVoice;addSystem("Voice: "+(englishVoice?"English":"Arabic"));}else if(n.startsWith("Voice Conversation")){conversationMode=!conversationMode;addSystem("Hands-free voice conversation: "+(conversationMode?"ON":"OFF"));if(conversationMode)listen();}else if(n.startsWith("Read last"))speak(lastAssistant());else if(n.equals("Profile / Memory"))showProfile();else if(n.equals("Files / Photos"))showAttach();else if(n.equals("Camera"))openCamera();else if(n.equals("System status"))addAssistant(core.handle("status"));else if(n.equals("Settings"))startActivity(new Intent(Settings.ACTION_SETTINGS));else addSystem(n+" ready.");return true;});p.show();}

    private void showAttach(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("Files");p.getMenu().add("Photos");p.getMenu().add("Camera");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.equals("Files"))openFiles();else if(n.equals("Camera"))openCamera();else{Intent x=new Intent(Intent.ACTION_PICK);x.setType("image/*");startActivityForResult(x,REQ_FILE);}return true;});p.show();}
    private void openFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,REQ_FILE);}
    private void openCamera(){try{startActivityForResult(new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),REQ_CAMERA);}catch(Exception e){addSystem("No camera application is available.");}}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==REQ_SPEECH&&result==RESULT_OK&&data!=null){ArrayList<String>a=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(a!=null&&!a.isEmpty()){input.setText(a.get(0));send();}return;}if(result!=RESULT_OK||data==null)return;Uri u=data.getData();if(u!=null){addSystem("Attached: "+u.getLastPathSegment());try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}else if(req==REQ_CAMERA)addSystem("Camera capture received.");}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQ_MIC&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED){listen();}else if(requestCode==REQ_MIC){conversationMode=false;addSystem("Microphone permission denied — voice conversation turned OFF.");}}
    private void listen(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,englishVoice?"en-US":"ar-EG");i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);try{status.setText("LISTENING • VOICE");startActivityForResult(i,REQ_SPEECH);}catch(Exception e){conversationMode=false;addSystem("Voice recognition is unavailable on this device.");}}
    private String lastAssistant(){for(int i=messages.getChildCount()-1;i>=0;i--){View v=messages.getChildAt(i);if(v instanceof LinearLayout){LinearLayout l=(LinearLayout)v;if(l.getChildCount()>0&&l.getChildAt(0) instanceof TextView)return ((TextView)l.getChildAt(0)).getText().toString();}}return "أنا SHADOW.";}
    private void showProfile(){new AlertDialog.Builder(this).setTitle("SHADOW • Profile & Memory").setMessage("محمد\nEgyptian Arabic • direct practical replies\n\nNEXO + SHADOW project context\n\nNative Android + embedded Python\n\nArabic male voice + English cinematic preset\n\nCloud-first AI + local failover\n\nHome / Car domains are fail-closed until a real endpoint is configured").setPositiveButton("Close",null).show();}
    private boolean arabic(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c>=0x0600&&c<=0x06FF)return true;}return false;}
    private void speak(String s){if(tts==null||s==null||s.trim().isEmpty())return;try{boolean ar=arabic(s);int r=tts.setLanguage(ar?new Locale("ar","EG"):Locale.US);if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED)tts.setLanguage(Locale.getDefault());tts.setPitch(ar?0.95f:0.72f);tts.setSpeechRate(ar?0.98f:0.86f);tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"shadow");}catch(Exception ignored){}}
    @Override public void onInit(int code){if(tts!=null){tts.setLanguage(new Locale("ar","EG"));tts.setPitch(0.95f);tts.setSpeechRate(0.98f);}}
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
