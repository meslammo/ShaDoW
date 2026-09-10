package com.shadow.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.*;

/** MOD-18.2: Release UI wired directly to the native SHADOW runtime core. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int AUDIO=701;
    private ShadowCore core;
    private EditText input;
    private TextView status, output, memory, domain;
    private Button run, mic, speak;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); core=new ShadowCore(this); tts=new TextToSpeech(this,this); build(); setupVoice();
    }

    private TextView tv(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextColor(Color.rgb(232,236,245));v.setTextSize(size);v.setPadding(16,10,16,10);return v;}
    private Button btn(String text){Button b=new Button(this);b.setText(text);b.setTextColor(Color.rgb(102,232,255));return b;}
    private LinearLayout box(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(14,12,14,12);l.setBackgroundColor(Color.rgb(18,22,34));return l;}
    private void build(){
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(14,14,14,24);root.setBackgroundColor(Color.rgb(5,7,13));scroll.addView(root);setContentView(scroll);
        LinearLayout head=box(); TextView title=tv("SHADOW",29);title.setTextColor(Color.WHITE);title.setTypeface(null,1);head.addView(title);status=tv("● ONLINE · NATIVE CORE",11);status.setTextColor(Color.rgb(102,232,255));head.addView(status);head.addView(tv("Android runtime · local execution · fail-closed",10));root.addView(head);
        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(0,10,0,6);String[] names={"ASSISTANT","DEVICES","HOME","CAR","SETTINGS"};for(String n:names){Button b=btn(n);b.setTextSize(10);tabs.addView(b,new LinearLayout.LayoutParams(0,48,1));b.setOnClickListener(v->tab(n));}root.addView(tabs);
        root.addView(tv("COMMAND CENTER",11));root.addView(tv("اكتب أو اتكلم. النواة المحلية تنفّذ الأوامر الآمنة داخل الـAPK.",18));
        LinearLayout command=box();input=new EditText(this);input.setHint("مثال: افتح واتساب / احسب 25*4 / حالة البيت");input.setHintTextColor(Color.rgb(90,100,120));input.setTextColor(Color.WHITE);input.setTextSize(16);input.setMinHeight(90);command.addView(input);
        LinearLayout actions=new LinearLayout(this);mic=btn("🎙 VOICE");run=btn("RUN ▶");actions.addView(mic,new LinearLayout.LayoutParams(0,52,1));actions.addView(run,new LinearLayout.LayoutParams(0,52,1));command.addView(actions);root.addView(command);
        memory=tv("LOCAL MEMORY",10);memory.setTextColor(Color.rgb(100,110,130));root.addView(memory);
        LinearLayout response=box();LinearLayout rh=new LinearLayout(this);rh.addView(tv("SHADOW RESPONSE",11),new LinearLayout.LayoutParams(0,50,1));speak=btn("🔊");rh.addView(speak,new LinearLayout.LayoutParams(70,50));response.addView(rh);output=tv("أنا SHADOW.\n\nالنواة المحلية جاهزة.",15);response.addView(output);root.addView(response);
        domain=tv("HOME / CAR DOMAINS\n\nHome: READY · NOT CONNECTED\nCar: READY · NOT CONNECTED",13);root.addView(domain);
        Button clear=btn("CLEAR LOCAL MEMORY");root.addView(clear);clear.setOnClickListener(v->{core.clearMemory();refreshMemory();output.setText("تم مسح الذاكرة المحلية.");});
        run.setOnClickListener(v->execute());mic.setOnClickListener(v->listen());speak.setOnClickListener(v->speak(output.getText().toString()));refreshMemory();
    }

    private void tab(String n){
        if(n.equals("HOME")){output.setText(core.handle("home status"));}
        else if(n.equals("CAR")){output.setText(core.handle("car status"));}
        else if(n.equals("DEVICES")){output.setText(core.handle("device status"));}
        else if(n.equals("SETTINGS")){output.setText("SETTINGS\n\nGateway remains optional.\nNo AI secret is embedded in the APK.\nUse the repository gateway for cloud reasoning when configured.");}
        else output.setText("SHADOW ASSISTANT\n\nLocal runtime active.\nVoice + phone actions + memory + calculator + Home/Car domains are available.");
        refreshMemory();
    }
    private void execute(){String s=input.getText().toString().trim();if(s.isEmpty()){input.setError("اكتب أمراً");return;}run.setEnabled(false);status.setText("● EXECUTING · LOCAL CORE");new Thread(()->{String r;try{r=core.handle(s);}catch(Exception e){r="SHADOW SAFE FAILURE\n\n"+e.getClass().getSimpleName();}String out=r;runOnUiThread(()->{output.setText(out);status.setText("● READY · VERIFIED LOCAL EXECUTION");run.setEnabled(true);refreshMemory();});}).start();}
    private void refreshMemory(){if(memory!=null){String last=core.lastCommand();memory.setText("LOCAL MEMORY  ·  "+(last.isEmpty()?"empty":"last: "+last));}}

    private void setupVoice(){if(!SpeechRecognizer.isRecognitionAvailable(this)){mic.setEnabled(false);return;}recognizer=SpeechRecognizer.createSpeechRecognizer(this);recognizer.setRecognitionListener(new RecognitionListener(){public void onReadyForSpeech(Bundle b){status.setText("● LISTENING");}public void onBeginningOfSpeech(){status.setText("● HEARING");}public void onEndOfSpeech(){status.setText("● THINKING");}public void onError(int e){status.setText("● VOICE ERROR");mic.setEnabled(true);}public void onResults(Bundle b){ArrayList<String> a=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty()){input.setText(a.get(0));execute();}mic.setEnabled(true);}public void onPartialResults(Bundle b){}public void onEvent(int a,Bundle b){}public void onBufferReceived(byte[] b){}public void onRmsChanged(float v){}});}
    private void listen(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO);return;}if(recognizer==null)return;Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault());i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);mic.setEnabled(false);recognizer.startListening(i);}
    private void speak(String s){if(tts!=null)tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"shadow");}
    @Override public void onInit(int code){if(tts!=null)tts.setLanguage(new Locale("ar"));}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
