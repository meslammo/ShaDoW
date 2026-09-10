package com.shadow.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** MOD-20.1: ChatGPT-style SHADOW shell around the same native + embedded Python runtime. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int AUDIO = 701;
    private static final int FILES = 702;
    private static final int CAMERA = 703;
    private ShadowCore core;
    private ShadowPythonRuntime python;
    private EditText input;
    private LinearLayout messages;
    private TextView status, title;
    private ImageButton menu, more, plus, mic, send;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;

    private int white = Color.rgb(236,238,244);
    private int muted = Color.rgb(150,155,168);
    private int panel = Color.rgb(28,30,34);
    private int bg = Color.rgb(20,20,20);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        core = new ShadowCore(this);
        python = new ShadowPythonRuntime(getFilesDir().getAbsolutePath());
        tts = new TextToSpeech(this, this);
        build();
        setupVoice();
    }

    private TextView text(String s, float size) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextColor(white); v.setTextSize(size);
        v.setPadding(0, 3, 0, 3); return v;
    }

    private ImageButton icon(String glyph, String desc) {
        ImageButton b = new ImageButton(this);
        b.setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        b.setContentDescription(desc);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setColorFilter(null);
        b.setPadding(8,8,8,8);
        b.setTag(glyph);
        b.setOnTouchListener((v,e) -> { if(e.getAction()==MotionEvent.ACTION_DOWN) ((ImageButton)v).setAlpha(.55f); if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) ((ImageButton)v).setAlpha(1f); return false; });
        b.setContentDescription(desc + " " + glyph);
        return b;
    }

    private TextView glyph(String g, float size) {
        TextView v = text(g, size); v.setGravity(Gravity.CENTER); v.setTextColor(white); return v;
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);

        // Top bar: hamburger | SHADOW | three dots.
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(8, 8, 8, 5);
        menu = icon("☰", "Open SHADOW menu"); top.addView(menu, new LinearLayout.LayoutParams(52,52));
        LinearLayout center = new LinearLayout(this); center.setGravity(Gravity.CENTER_VERTICAL); center.setOrientation(LinearLayout.VERTICAL);
        title = text("SHADOW", 18); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setGravity(Gravity.CENTER);
        status = text("Online", 10); status.setTextColor(Color.rgb(102,232,255)); status.setGravity(Gravity.CENTER);
        center.addView(title); center.addView(status);
        top.addView(center, new LinearLayout.LayoutParams(0,58,1));
        more = icon("⋮", "More options"); top.addView(more, new LinearLayout.LayoutParams(52,52));
        root.addView(top);

        View line = new View(this); line.setBackgroundColor(Color.rgb(48,48,52)); root.addView(line,new LinearLayout.LayoutParams(-1,1));

        // Chat transcript.
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(18,22,18,22);
        scroll.addView(messages); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addAssistant("أهلاً محمد 👋\nأنا SHADOW. اكتبلي أو دوس على المايك واتكلم.\n\nتقدر ترفع ملف من +، وباقي الأدوات موجودة في ☰.");

        // Composer: plus | text | mic/send.
        LinearLayout composerWrap = new LinearLayout(this); composerWrap.setPadding(10,7,10,10); composerWrap.setBackgroundColor(bg);
        LinearLayout composer = new LinearLayout(this); composer.setGravity(Gravity.CENTER_VERTICAL); composer.setPadding(5,3,5,3); composer.setBackground(round(panel, 24));
        plus = icon("+", "Attach file or camera"); composer.addView(plus,new LinearLayout.LayoutParams(48,52));
        input = new EditText(this);
        input.setSingleLine(true); input.setImeOptions(EditorInfo.IME_ACTION_SEND); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint("Message SHADOW"); input.setHintTextColor(muted); input.setTextColor(white); input.setTextSize(16); input.setPadding(8,0,5,0); input.setBackgroundColor(Color.TRANSPARENT);
        composer.addView(input,new LinearLayout.LayoutParams(0,52,1));
        mic = icon("🎙", "Voice input"); composer.addView(mic,new LinearLayout.LayoutParams(48,52));
        send = icon("➤", "Send message"); composer.addView(send,new LinearLayout.LayoutParams(48,52));
        composerWrap.addView(composer,new LinearLayout.LayoutParams(-1,58)); root.addView(composerWrap);

        setContentView(root);
        menu.setOnClickListener(v -> showMainMenu());
        more.setOnClickListener(v -> showMoreMenu());
        plus.setOnClickListener(v -> showAttachMenu());
        mic.setOnClickListener(v -> listen());
        send.setOnClickListener(v -> execute());
        input.setOnEditorActionListener((v, action, event) -> { if(action==EditorInfo.IME_ACTION_SEND || (event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER && event.getAction()==KeyEvent.ACTION_DOWN)){execute();return true;} return false; });
    }

    private android.graphics.drawable.Drawable round(int color, float radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); d.setStroke(1,Color.rgb(62,64,70)); return d;
    }

    private void addUser(String s) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.END); row.setPadding(36,7,0,7);
        TextView v = text(s,16); v.setTextColor(white); v.setPadding(15,11,15,11); v.setBackground(round(Color.rgb(47,49,55),20));
        row.addView(v,new LinearLayout.LayoutParams(-2,-2)); messages.addView(row);
        scrollBottom();
    }

    private void addAssistant(String s) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.START); row.setPadding(0,7,28,7);
        TextView v = text(s,16); v.setTextColor(white); v.setLineSpacing(0,1.12f); row.addView(v,new LinearLayout.LayoutParams(-1,-2)); messages.addView(row);
        scrollBottom();
    }

    private void addSystem(String s) { TextView v=text(s,11);v.setTextColor(muted);v.setPadding(0,8,0,8);messages.addView(v);scrollBottom(); }
    private void scrollBottom(){ messages.post(() -> { ViewParent p=messages.getParent(); if(p instanceof ScrollView)((ScrollView)p).fullScroll(View.FOCUS_DOWN); }); }

    private void execute() {
        String s=input.getText().toString().trim(); if(s.isEmpty()) return;
        addUser(s); input.setText(""); status.setText("Thinking…");
        new Thread(() -> {
            String r;
            try { r=handleUnified(s); } catch(Exception e){ r="حصل خطأ آمن أثناء التنفيذ.\n"+e.getClass().getSimpleName(); }
            String out=r;
            runOnUiThread(() -> { addAssistant(out); status.setText("Online"); });
        }).start();
    }

    private String handleUnified(String request) {
        String local=core.handle(request);
        if(isDefinitiveLocal(local)) return local;
        String answer=python.handle(request);
        return answer==null || answer.trim().isEmpty() ? local : answer;
    }

    private boolean isDefinitiveLocal(String s) {
        return s.startsWith("SHADOW ACTION") || s.startsWith("CALCULATOR") || s.startsWith("SHADOW SYSTEM STATUS") || s.startsWith("HOME CORE") || s.startsWith("CAR CORE") || s.startsWith("DEVICE") || s.startsWith("LOCAL MEMORY") || s.startsWith("SHADOW\n\nأهلاً") || s.startsWith("SHADOW\n\nالوقت") || s.startsWith("SHADOW\n\nالتاريخ");
    }

    private void showMainMenu() {
        PopupMenu p=new PopupMenu(this,menu);
        p.getMenu().add("New chat"); p.getMenu().add("Profile"); p.getMenu().add("Files"); p.getMenu().add("Camera"); p.getMenu().add("Devices"); p.getMenu().add("Home"); p.getMenu().add("Car"); p.getMenu().add("Settings");
        p.setOnMenuItemClickListener(i -> { String n=i.getTitle().toString(); if(n.equals("New chat")){messages.removeAllViews();addAssistant("محادثة جديدة. أنا SHADOW، اتكلم.");} else if(n.equals("Files"))openFiles(); else if(n.equals("Camera"))openCamera(); else if(n.equals("Profile"))showProfile(); else { addSystem(n+" selected"); } return true; }); p.show();
    }

    private void showMoreMenu() {
        PopupMenu p=new PopupMenu(this,more); p.getMenu().add("Read last answer aloud"); p.getMenu().add("Clear chat"); p.getMenu().add("System status"); p.getMenu().add("Voice settings");
        p.setOnMenuItemClickListener(i -> {String n=i.getTitle().toString(); if(n.startsWith("Read"))speak(lastAssistantText()); else if(n.equals("Clear chat")){messages.removeAllViews();addAssistant("تم مسح المحادثة. أنا SHADOW.");} else if(n.equals("System status")){addAssistant(core.handle("status"));} else startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)); return true;}); p.show();
    }

    private void showAttachMenu(){ PopupMenu p=new PopupMenu(this,plus);p.getMenu().add("Upload file");p.getMenu().add("Camera");p.getMenu().add("Photo / video");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.equals("Upload file"))openFiles();else if(n.equals("Camera"))openCamera();else {Intent x=new Intent(Intent.ACTION_PICK);x.setType("image/* video/*");startActivityForResult(x,FILES);}return true;});p.show(); }

    private void showProfile(){
        AlertDialog.Builder b=new AlertDialog.Builder(this); LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(28,20,28,10);
        TextView h=text("SHADOW Profile",23);h.setTypeface(null,Typeface.BOLD);l.addView(h);l.addView(text("\nRuntime\nNative Android + Embedded Python\n\nMemory\nLocal memory active\n\nVoice\nAndroid STT / TTS\n\nHome\nAdapter ready · endpoint later\n\nCar\nAdapter ready · endpoint later\n\nSecurity\nFail-closed",15));
        b.setView(l);b.setPositiveButton("Close",null);b.setNeutralButton("Settings",(d,w)->startActivity(new Intent(Settings.ACTION_SETTINGS)));b.show();
    }

    private String lastAssistantText(){ if(messages==null||messages.getChildCount()==0)return "أنا SHADOW."; for(int i=messages.getChildCount()-1;i>=0;i--){View v=messages.getChildAt(i);if(v instanceof LinearLayout){LinearLayout l=(LinearLayout)v;if(l.getChildCount()>0&&l.getChildAt(0) instanceof TextView)return ((TextView)l.getChildAt(0)).getText().toString();}} return "أنا SHADOW."; }

    private void openFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,FILES);}
    private void openCamera(){Intent i=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);try{startActivityForResult(i,CAMERA);}catch(Exception e){addSystem("Camera app is not available.");}}

    private void setupVoice(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){addSystem("Voice recognition is unavailable on this device.");return;}
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){status.setText("Listening…");}
            public void onBeginningOfSpeech(){status.setText("Hearing…");}
            public void onEndOfSpeech(){status.setText("Thinking…");}
            public void onError(int e){status.setText("Online");mic.setAlpha(1f);}
            public void onResults(Bundle b){ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()){input.setText(a.get(0));execute();}mic.setAlpha(1f);}
            public void onPartialResults(Bundle b){} public void onEvent(int a,Bundle b){} public void onBufferReceived(byte[] b){} public void onRmsChanged(float v){}
        });
    }
    private void listen(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);return;}if(recognizer==null)return;Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault());i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);mic.setAlpha(.5f);recognizer.startListening(i);}
    private void speak(String s){if(tts!=null&&!s.trim().isEmpty())tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"shadow");}
    @Override public void onInit(int code){if(tts!=null)tts.setLanguage(new Locale("ar"));}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
