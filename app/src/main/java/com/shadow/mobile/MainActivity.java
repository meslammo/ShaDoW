package com.shadow.mobile;

import android.Manifest;
import android.app.*;
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

/** MOD-24.5: ChatGPT-style SHADOW shell with online/offline routing, voice and safe controls. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int AUDIO=701, FILES=702, CAMERA=703;
    private ShadowCore core; private ShadowPythonRuntime python; private ShadowOnlineConfig online;
    private EditText input; private LinearLayout messages; private TextView status;
    private SpeechRecognizer recognizer; private TextToSpeech tts;
    private final int white=Color.rgb(242,243,245), muted=Color.rgb(155,160,170), panel=Color.rgb(34,36,42), bg=Color.rgb(12,13,16);
    private boolean englishVoice=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        core=new ShadowCore(this); python=new ShadowPythonRuntime(getFilesDir().getAbsolutePath()); online=new ShadowOnlineConfig(this);
        restoreOnline(); tts=new TextToSpeech(this,this); build(); setupVoice();
    }

    private void restoreOnline(){
        String key=online.getApiKey();
        if(!key.isEmpty()) python.configureOnline(key,online.getModel());
    }
    private TextView label(String s,float size){TextView v=new TextView(this);v.setText(s);v.setTextColor(white);v.setTextSize(size);return v;}
    private TextView button(String s,String desc,float size){TextView v=label(s,size);v.setGravity(Gravity.CENTER);v.setContentDescription(desc);v.setClickable(true);v.setFocusable(true);v.setPadding(8,4,8,4);return v;}
    private android.graphics.drawable.Drawable round(int color,float radius){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(8,6,8,5);
        TextView menu=button("☰","Open SHADOW menu",27);top.addView(menu,new LinearLayout.LayoutParams(50,54));
        ImageView avatar=new ImageView(this);avatar.setImageResource(com.shadow.mobile.R.drawable.shadow_logo);avatar.setContentDescription("SHADOW");avatar.setPadding(7,7,7,7);top.addView(avatar,new LinearLayout.LayoutParams(52,52));
        LinearLayout center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setGravity(Gravity.CENTER);
        TextView title=label("SHADOW",18);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER);center.addView(title);
        status=label(online.isConfigured()?"ONLINE • AI CONNECTED":"OFFLINE • LOCAL READY",10);status.setTextColor(online.isConfigured()?Color.rgb(90,220,170):Color.rgb(240,190,90));status.setGravity(Gravity.CENTER);center.addView(status);
        top.addView(center,new LinearLayout.LayoutParams(0,58,1));
        TextView more=button("⋮","More options and SHADOW features",28);top.addView(more,new LinearLayout.LayoutParams(50,54));
        root.addView(top);View divider=new View(this);divider.setBackgroundColor(Color.rgb(48,50,58));root.addView(divider,new LinearLayout.LayoutParams(-1,1));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);messages.setPadding(16,14,16,18);scroll.addView(messages);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addAssistant("أهلاً محمد.\nأنا SHADOW.\nاكتب رسالتك أو اضغط MIC، وأنا هرد عليك نصاً وصوتاً.\nالذكاء السحابي يشتغل عند الاتصال، ولو النت فصل أكمل محلياً بدون انهيار.");

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(10,6,10,10);bottom.setBackgroundColor(bg);
        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(5,4,5,4);composer.setBackground(round(panel,28));
        TextView plus=button("＋","Attach file, photo or camera",27);composer.addView(plus,new LinearLayout.LayoutParams(48,52));
        input=new EditText(this);input.setSingleLine(true);input.setImeOptions(EditorInfo.IME_ACTION_SEND);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setHint("Message SHADOW…");input.setHintTextColor(muted);input.setTextColor(white);input.setTextSize(16);input.setPadding(8,0,5,0);input.setBackgroundColor(Color.TRANSPARENT);composer.addView(input,new LinearLayout.LayoutParams(0,52,1));
        TextView mic=button("MIC","Voice input",12);mic.setTypeface(null,Typeface.BOLD);composer.addView(mic,new LinearLayout.LayoutParams(52,52));
        TextView send=button("➤","Send message",25);composer.addView(send,new LinearLayout.LayoutParams(48,52));
        bottom.addView(composer,new LinearLayout.LayoutParams(-1,60));
        TextView hint=label("＋ attach  •  MIC talk  •  Enter send  •  voice reply",10);hint.setTextColor(muted);hint.setGravity(Gravity.CENTER);bottom.addView(hint,new LinearLayout.LayoutParams(-1,25));root.addView(bottom);setContentView(root);
        menu.setOnClickListener(v->showMainMenu());more.setOnClickListener(v->showMoreMenu());plus.setOnClickListener(v->showAttachMenu());mic.setOnClickListener(v->listen());send.setOnClickListener(v->execute());
        input.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEND||(e!=null&&e.getKeyCode()==KeyEvent.KEYCODE_ENTER&&e.getAction()==KeyEvent.ACTION_DOWN)){execute();return true;}return false;});
    }

    private void addUser(String s){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.END);row.setPadding(45,6,0,6);TextView v=label(s,16);v.setPadding(15,11,15,11);v.setBackground(round(Color.rgb(45,48,58),20));row.addView(v);messages.addView(row);scrollBottom();}
    private void addAssistant(String s){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.START);row.setPadding(0,7,22,7);TextView v=label(s,16);v.setLineSpacing(0,1.15f);row.addView(v,new LinearLayout.LayoutParams(-1,-2));messages.addView(row);scrollBottom();}
    private void addSystem(String s){TextView v=label(s,11);v.setTextColor(muted);v.setPadding(0,7,0,7);messages.addView(v);scrollBottom();}
    private void scrollBottom(){messages.post(()->{ViewParent p=messages.getParent();if(p instanceof ScrollView)((ScrollView)p).fullScroll(View.FOCUS_DOWN);});}

    private void execute(){String s=input.getText().toString().trim();if(s.isEmpty())return;addUser(s);input.setText("");status.setText(online.isConfigured()?"THINKING • ONLINE":"THINKING • LOCAL");new Thread(()->{String r;try{r=handleUnified(s);}catch(Throwable e){r=core.offlineChat(s);}String out=r;runOnUiThread(()->{addAssistant(out);status.setText(online.isConfigured()?"ONLINE • AI CONNECTED":"OFFLINE • LOCAL READY");speak(out);});}).start();}
    private String handleUnified(String request){
        String local=core.handle(request);
        if(isDefinitiveLocal(local))return local;
        String answer=python.handle(request);
        if(answer!=null&&!answer.trim().isEmpty()&&!isExternalOffline(answer))return answer;
        return core.offlineChat(request);
    }
    private boolean isExternalOffline(String s){String x=s.toLowerCase(Locale.ROOT);return x.contains("غير متصل")||x.contains("المحرك الخارجي")||x.contains("external ai")||x.contains("offline-safe")||x.contains("configured ai provider")||x.contains("الوضع المحلي الآمن")||x.contains("switched to local-safe");}
    private boolean isDefinitiveLocal(String s){return s!=null&&(s.startsWith("SHADOW ACTION")||s.startsWith("CALCULATOR")||s.startsWith("SHADOW SYSTEM STATUS")||s.startsWith("HOME CORE")||s.startsWith("CAR CORE")||s.startsWith("DEVICE")||s.startsWith("LOCAL MEMORY")||s.startsWith("SHADOW\n\nأهلاً")||s.startsWith("SHADOW\n\nالوقت")||s.startsWith("SHADOW\n\nالتاريخ"));}

    private void showMainMenu(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("New chat");p.getMenu().add("Profile / Memory");p.getMenu().add("Files & Photos");p.getMenu().add("Camera");p.getMenu().add("Devices");p.getMenu().add("Home");p.getMenu().add("Car");p.getMenu().add("Settings");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.equals("New chat")){messages.removeAllViews();addAssistant("محادثة جديدة. أنا SHADOW، جاهز.");}else if(n.equals("Files & Photos"))openFiles();else if(n.equals("Camera"))openCamera();else if(n.equals("Profile / Memory"))showProfile();else if(n.equals("Settings"))startActivity(new Intent(Settings.ACTION_SETTINGS));else addSystem(n+" ready.");return true;});p.show();}

    /** MOD-24.5: All secondary SHADOW features live under the top-right three-dot menu. */
    private void showMoreMenu(){
        PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));
        p.getMenu().add("Connect AI / Online");
        p.getMenu().add("Disconnect AI / Offline");
        p.getMenu().add("Read last answer aloud");
        p.getMenu().add("Voice language: "+(englishVoice?"English":"Arabic"));
        p.getMenu().add("Clear chat");
        p.getMenu().add("System status");
        p.getMenu().add("Profile / Memory");
        p.getMenu().add("Files & Photos");
        p.getMenu().add("Camera");
        p.getMenu().add("Home");
        p.getMenu().add("Car");
        p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();
            if(n.startsWith("Connect AI"))showOnlineDialog();
            else if(n.startsWith("Disconnect AI")){online.clear();python.clearOnline();status.setText("OFFLINE • LOCAL READY");addSystem("AI disconnected. Local SHADOW remains available.");}
            else if(n.startsWith("Read last"))speak(lastAssistantText());
            else if(n.startsWith("Voice language")){englishVoice=!englishVoice;addSystem("Voice language: "+(englishVoice?"English":"Arabic"));}
            else if(n.equals("Clear chat")){messages.removeAllViews();addAssistant("تم مسح المحادثة. أنا SHADOW.");}
            else if(n.equals("System status"))addAssistant(core.handle("status"));
            else if(n.equals("Profile / Memory"))showProfile();
            else if(n.equals("Files & Photos"))openFiles();
            else if(n.equals("Camera"))openCamera();
            else addSystem(n+" ready.");
            return true;});
        p.show();
    }

    private void showOnlineDialog(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(28,8,28,0);
        TextView info=label("Connect SHADOW to OpenAI Responses API.\nThe key is encrypted with Android Keystore and is not committed to the source.",14);l.addView(info);
        EditText key=new EditText(this);key.setHint("OpenAI API key");key.setSingleLine(true);key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setText(online.getApiKey());l.addView(key,new LinearLayout.LayoutParams(-1,55));
        EditText model=new EditText(this);model.setHint("Model");model.setSingleLine(true);model.setText(online.getModel());l.addView(model,new LinearLayout.LayoutParams(-1,55));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("SHADOW Online AI").setView(l).setNegativeButton("Cancel",null).setPositiveButton("Connect",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String k=key.getText().toString().trim();String m=model.getText().toString().trim();if(k.isEmpty()){key.setError("API key required");return;}try{online.save(k,m);if(python.configureOnline(k,m)){status.setText("ONLINE • AI CONNECTED");addSystem("Online AI connected. Offline failover remains enabled.");d.dismiss();}else key.setError("Runtime configuration failed");}catch(Exception e){key.setError("Could not save securely");}}));
        d.show();
    }

    private void showAttachMenu(){PopupMenu p=new PopupMenu(this,findViewById(android.R.id.content));p.getMenu().add("Upload file");p.getMenu().add("Photo");p.getMenu().add("Camera");p.setOnMenuItemClickListener(i->{String n=i.getTitle().toString();if(n.equals("Upload file"))openFiles();else if(n.equals("Camera"))openCamera();else{Intent x=new Intent(Intent.ACTION_PICK);x.setType("image/*");startActivityForResult(x,FILES);}return true;});p.show();}

    private void showProfile(){AlertDialog.Builder b=new AlertDialog.Builder(this);LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(28,20,28,10);TextView h=label("SHADOW • Profile & Memory",22);h.setTypeface(null,Typeface.BOLD);l.addView(h);l.addView(label("\nOwner\nمحمد\n\nStyle\nEgyptian Arabic • direct practical replies • masculine addressing\n\nProjects\nNEXO + SHADOW development; mobile-first workflow\n\nAssistant\nNative Android + Embedded Python • JARVIS-style shell\n\nVoice\nArabic natural male preset • English deep cinematic preset\n\nOnline / Offline\nCloud AI when configured • local-safe fallback when disconnected\n\nHome / Car\nDomains ready; physical endpoints remain fail-closed until configured\n\nSecurity\nAPI key encrypted with Android Keystore; no key in source\n",14));b.setView(l);b.setPositiveButton("Close",null);b.show();}
    private String lastAssistantText(){for(int i=messages.getChildCount()-1;i>=0;i--){View v=messages.getChildAt(i);if(v instanceof LinearLayout){LinearLayout l=(LinearLayout)v;if(l.getChildCount()>0&&l.getChildAt(0) instanceof TextView)return ((TextView)l.getChildAt(0)).getText().toString();}}return "أنا SHADOW.";}
    private void openFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,FILES);}
    private void openCamera(){Intent i=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);try{startActivityForResult(i,CAMERA);}catch(Exception e){addSystem("Camera app is not available.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;Uri u=data.getData();if(u!=null){String name=u.getLastPathSegment();addSystem("Attached: "+(name==null?u.toString():name));try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}else if(request==CAMERA)addSystem("Camera capture received.");}

    private void setupVoice(){if(!SpeechRecognizer.isRecognitionAvailable(this))return;recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(new RecognitionListener(){public void onReadyForSpeech(Bundle b){status.setText("LISTENING");}public void onBeginningOfSpeech(){status.setText("HEARING");}public void onEndOfSpeech(){status.setText("THINKING");}public void onError(int e){status.setText(online.isConfigured()?"ONLINE • AI CONNECTED":"OFFLINE • LOCAL READY");addSystem("Voice recognition error: "+e);}public void onResults(Bundle b){ArrayList<String>a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()){input.setText(a.get(0));execute();}}public void onPartialResults(Bundle b){}public void onEvent(int a,Bundle b){}public void onBufferReceived(byte[] b){}public void onRmsChanged(float v){}});}
    private void listen(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);return;}if(recognizer==null){addSystem("Voice recognition is unavailable on this device.");return;}Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,englishVoice?"en-US":"ar-EG");i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);recognizer.startListening(i);}
    private boolean containsArabic(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c>=0x0600&&c<=0x06FF)return true;}return false;}
    private void speak(String s){if(tts==null||s.trim().isEmpty())return;boolean ar=containsArabic(s);int r=tts.setLanguage(ar?new Locale("ar","EG"):Locale.US);if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED){tts.setLanguage(Locale.getDefault());}tts.setPitch(ar?0.95f:0.72f);tts.setSpeechRate(ar?0.98f:0.86f);tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"shadow");}
    @Override public void onInit(int code){if(tts!=null){tts.setLanguage(new Locale("ar","EG"));tts.setPitch(0.95f);tts.setSpeechRate(0.98f);}}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
