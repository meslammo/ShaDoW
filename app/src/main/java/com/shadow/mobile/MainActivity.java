package com.shadow.mobile;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** MOD-21.1: Reliable ChatGPT-style shell with explicit visible controls. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int AUDIO=701, FILES=702, CAMERA=703;
    private ShadowCore core; private ShadowPythonRuntime python;
    private EditText input; private LinearLayout messages; private TextView status;
    private SpeechRecognizer recognizer; private TextToSpeech tts;
    private final int white=Color.rgb(242,243,245), muted=Color.rgb(155,160,170), panel=Color.rgb(36,38,43), bg=Color.rgb(18,18,18);

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        core=new ShadowCore(this); python=new ShadowPythonRuntime(getFilesDir().getAbsolutePath());
        tts=new TextToSpeech(this,this); build(); setupVoice();
    }

    private TextView label(String s,float size){TextView v=new TextView(this);v.setText(s);v.setTextColor(white);v.setTextSize(size);return v;}
    private TextView button(String s,String desc,float size){
        TextView v=label(s,size); v.setGravity(Gravity.CENTER); v.setContentDescription(desc); v.setClickable(true); v.setFocusable(true);
        v.setPadding(8,4,8,4); v.setBackgroundColor(Color.TRANSPARENT);
        return v;
    }
    private android.graphics.drawable.Drawable round(int color,float radius){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }

    private void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(6,6,6,4);
        TextView menu=button("☰","Open menu",28); top.addView(menu,new LinearLayout.LayoutParams(54,54));
        LinearLayout center=new LinearLayout(this); center.setOrientation(LinearLayout.VERTICAL); center.setGravity(Gravity.CENTER);
        TextView title=label("SHADOW",18); title.setTypeface(null,Typeface.BOLD); title.setGravity(Gravity.CENTER); center.addView(title);
        status=label("Ready",11); status.setTextColor(Color.rgb(90,220,170)); status.setGravity(Gravity.CENTER); center.addView(status);
        top.addView(center,new LinearLayout.LayoutParams(0,58,1));
        TextView more=button("⋮","More options",28); top.addView(more,new LinearLayout.LayoutParams(54,54));
        root.addView(top); View divider=new View(this); divider.setBackgroundColor(Color.rgb(48,48,52)); root.addView(divider,new LinearLayout.LayoutParams(-1,1));

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(16,18,16,18); scroll.addView(messages);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addAssistant("أهلاً محمد 👋\nأنا SHADOW. اكتب رسالتك هنا أو اضغط MIC للكلام.\nاستخدم + للملفات والكاميرا.");

        LinearLayout bottom=new LinearLayout(this); bottom.setOrientation(LinearLayout.VERTICAL); bottom.setPadding(10,6,10,10); bottom.setBackgroundColor(bg);
        LinearLayout composer=new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(5,4,5,4); composer.setBackground(round(panel,26));
        TextView plus=button("＋","Attach files, camera or photos",26); composer.addView(plus,new LinearLayout.LayoutParams(48,52));
        input=new EditText(this); input.setSingleLine(true); input.setImeOptions(EditorInfo.IME_ACTION_SEND); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Message SHADOW…"); input.setHintTextColor(muted); input.setTextColor(white); input.setTextSize(16); input.setPadding(8,0,5,0); input.setBackgroundColor(Color.TRANSPARENT);
        composer.addView(input,new LinearLayout.LayoutParams(0,52,1));
        TextView mic=button("MIC","Voice input",13); mic.setTypeface(null,Typeface.BOLD); composer.addView(mic,new LinearLayout.LayoutParams(52,52));
        TextView send=button("➤","Send message",25); composer.addView(send,new LinearLayout.LayoutParams(48,52));
        bottom.addView(composer,new LinearLayout.LayoutParams(-1,60));
        TextView hint=label("＋ files/camera   •   MIC voice   •   Enter send",11); hint.setTextColor(muted); hint.setGravity(Gravity.CENTER); bottom.addView(hint,new LinearLayout.LayoutParams(-1,25));
        root.addView(bottom);
        setContentView(root);

        menu.setOnClickListener(v->showMainMenu()); more.setOnClickListener(v->showMoreMenu()); plus.setOnClickListener(v->showAttachMenu()); mic.setOnClickListener(v->listen()); send.setOnClickListener(v->execute());
        input.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEND || (e!=null&&e.getKeyCode()==KeyEvent.KEYCODE_ENTER&&e.getAction()==KeyEvent.ACTION_DOWN)){execute();return true;}return false;});
    }

    private void addUser(String s){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.END);row.setPadding(45,6,0,6);TextView v=label(s,16);v.setPadding(15,11,15,11);v.setBackground(round(Color.rgb(48,50,56),20));row.addView(v);messages.addView(row);scrollBottom();}
    private void addAssistant(String s){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.START);row.setPadding(0,7,22,7);TextView v=label(s,16);v.setLineSpacing(0,1.15f);row.addView(v,new LinearLayout.LayoutParams(-1,-2));messages.addView(row);scrollBottom();}
    private void addSystem(String s){TextView v=label(s,11);v.setTextColor(muted);v.setPadding(0,7,0,7);messages.addView(v);scrollBottom();}
    private void scrollBottom(){messages.post(()->{ViewParent p=messages.getParent();if(p instanceof ScrollView)((ScrollView)p).fullScroll(View.FOCUS_DOWN);});}

    private void execute(){
        String s=input.getText().toString().trim(); if(s.isEmpty())return; addUser(s); input.setText(""); status.setText("Thinking…");
        new Thread(()->{String r;try{r=handleUnified(s);}catch(Throwable e){r="حصل خطأ أثناء معالجة الرسالة. جرّب تاني.";}String out=r;runOnUiThread(()->{addAssistant(out);status.setText("Ready");});}).start();
    }
    private String handleUnified(String request){
        String local=core.handle(request);
        if(isDefinitiveLocal(local)) return local;
        String answer=python.handle(request);
        return answer==null||answer.trim().isEmpty()?"أنا SHADOW. استلمت رسالتك، لكن محرك الذكاء الخارجي غير متصل حالياً.":answer;
    }
    private boolean isDefinitiveLocal(String s){
        return s.startsWith("SHADOW ACTION")||s.startsWith("CALCULATOR")||s.startsWith("SHADOW SYSTEM STATUS")||s.startsWith("HOME CORE")||s.startsWith("CAR CORE")||s.startsWith("DEVICE")||s.startsWith("LOCAL MEMORY")||s.startsWith("SHADOW\n\nأهلاً")||s.startsWith("SHADOW\n\nالوقت")||s.startsWith("SHADOW\n\nالتاريخ");
    }

    private void showMainMenu(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));
        p.getMenu().add("New chat");p.getMenu().add("Profile");p.getMenu().add("Files");p.getMenu().add("Camera");p.getMenu().add("Devices");p.getMenu().add("Home");p.getMenu().add("Car");p.getMenu().add("Settings");
        p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.equals("New chat")){messages.removeAllViews();addAssistant("محادثة جديدة. أنا SHADOW، اتكلم.");}else if(n.equals("Files"))openFiles();else if(n.equals("Camera"))openCamera();else if(n.equals("Profile"))showProfile();else if(n.equals("Settings"))startActivity(new Intent(Settings.ACTION_SETTINGS));else addSystem(n+" ready.");return true;});p.show();
    }
    private void showMoreMenu(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("Read last answer aloud");p.getMenu().add("Clear chat");p.getMenu().add("System status");p.getMenu().add("Voice settings");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.startsWith("Read"))speak(lastAssistantText());else if(n.equals("Clear chat")){messages.removeAllViews();addAssistant("تم مسح المحادثة. أنا SHADOW.");}else if(n.equals("System status"))addAssistant(core.handle("status"));else startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));return true;});p.show();}
    private void showAttachMenu(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("Upload file");p.getMenu().add("Camera");p.getMenu().add("Photo / video");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.equals("Upload file"))openFiles();else if(n.equals("Camera"))openCamera();else{Intent x=new Intent(Intent.ACTION_PICK);x.setType("image/*");startActivityForResult(x,FILES);}return true;});p.show();}
    private void showProfile(){AlertDialog.Builder b=new AlertDialog.Builder(this);LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(28,20,28,10);TextView h=label("SHADOW Profile",23);h.setTypeface(null,Typeface.BOLD);l.addView(h);l.addView(label("\nAssistant\nNative Android + Embedded Python\n\nVoice\nAndroid STT / TTS\n\nMemory\nLocal memory active\n\nHome\nDomain ready · endpoint later\n\nCar\nDomain ready · endpoint later\n\nSecurity\nFail-closed",15));b.setView(l);b.setPositiveButton("Close",null);b.show();}
    private String lastAssistantText(){for(int i=messages.getChildCount()-1;i>=0;i--){View v=messages.getChildAt(i);if(v instanceof LinearLayout){LinearLayout l=(LinearLayout)v;if(l.getChildCount()>0&&l.getChildAt(0) instanceof TextView)return ((TextView)l.getChildAt(0)).getText().toString();}}return "أنا SHADOW.";}
    private void openFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,FILES);}
    private void openCamera(){Intent i=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);try{startActivityForResult(i,CAMERA);}catch(Exception e){addSystem("Camera app is not available.");}}

    private void setupVoice(){
        if(!SpeechRecognizer.isRecognitionAvailable(this))return;
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){status.setText("Listening…");}
            public void onBeginningOfSpeech(){status.setText("Hearing…");}
            public void onEndOfSpeech(){status.setText("Thinking…");}
            public void onError(int e){status.setText("Ready");}
            public void onResults(Bundle b){ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()){input.setText(a.get(0));execute();}}
            public void onPartialResults(Bundle b){} public void onEvent(int a,Bundle b){} public void onBufferReceived(byte[] b){} public void onRmsChanged(float v){}
        });
    }
    private void listen(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);return;}if(recognizer==null){addSystem("Voice recognition is unavailable on this device.");return;}Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-EG");i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);recognizer.startListening(i);}
    private void speak(String s){if(tts!=null&&!s.trim().isEmpty())tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"shadow");}
    @Override public void onInit(int code){if(tts!=null)tts.setLanguage(new Locale("ar","EG"));}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
