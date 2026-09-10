package com.shadow.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int AUDIO_PERMISSION = 701;
    private EditText input, url, token;
    private TextView status, output, historyInfo, deviceInfo, deviceInfoCard, homeStatus, carStatus;
    private Button send, check, mic, speak, copy, share, clearHistory;
    private Button tabAssistant, tabDevices, tabHome, tabCar, tabSettings;
    private LinearLayout panelAssistant, panelDevices, panelHome, panelCar, panelSettings;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private String lastAnswer = "";
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); setContentView(R.layout.activity_main);
        bindViews(); tts = new TextToSpeech(this, this); setupVoice(); loadSettings(); updateDeviceInfo(); updateHistoryInfo();
        showTab("assistant"); updateDomainCards();
        status.setText("● SHADOW ONLINE · LOCAL CORE");
        output.setText("أنا SHADOW.\n\nالمساعد، الذاكرة، الصوت، تنفيذ أوامر الهاتف، وتبويبا HOME وCAR جاهزين.\nالتكامل الخارجي للبيت والسيارة مصمم كطبقة مستقلة لحين توصيل الجهاز الفعلي.");
        wireActions();
    }

    private void bindViews() {
        input=findViewById(R.id.goal); url=findViewById(R.id.url); token=findViewById(R.id.token);
        status=findViewById(R.id.status); output=findViewById(R.id.output); historyInfo=findViewById(R.id.history_info);
        deviceInfo=findViewById(R.id.device_info); deviceInfoCard=findViewById(R.id.device_info_card);
        homeStatus=findViewById(R.id.home_status); carStatus=findViewById(R.id.car_status);
        send=findViewById(R.id.send); check=findViewById(R.id.check); mic=findViewById(R.id.mic); speak=findViewById(R.id.speak);
        copy=findViewById(R.id.copy); share=findViewById(R.id.share); clearHistory=findViewById(R.id.clear_history);
        tabAssistant=findViewById(R.id.tab_assistant); tabDevices=findViewById(R.id.tab_devices); tabHome=findViewById(R.id.tab_home);
        tabCar=findViewById(R.id.tab_car); tabSettings=findViewById(R.id.tab_settings);
        panelAssistant=findViewById(R.id.panel_assistant); panelDevices=findViewById(R.id.panel_devices); panelHome=findViewById(R.id.panel_home);
        panelCar=findViewById(R.id.panel_car); panelSettings=findViewById(R.id.panel_settings);
    }

    private void wireActions() {
        send.setOnClickListener(v->runGoal()); mic.setOnClickListener(v->listen());
        speak.setOnClickListener(v->speak(lastAnswer.isEmpty()?output.getText().toString():lastAnswer));
        copy.setOnClickListener(v->copyAnswer()); share.setOnClickListener(v->shareAnswer());
        clearHistory.setOnClickListener(v->{getPreferences(MODE_PRIVATE).edit().remove("history").remove("last_command").apply();updateHistoryInfo();output.setText("تم مسح الذاكرة المحلية.");});
        check.setOnClickListener(v->checkGateway());
        tabAssistant.setOnClickListener(v->showTab("assistant")); tabDevices.setOnClickListener(v->showTab("devices"));
        tabHome.setOnClickListener(v->showTab("home")); tabCar.setOnClickListener(v->showTab("car")); tabSettings.setOnClickListener(v->showTab("settings"));
        findViewById(R.id.home_refresh).setOnClickListener(v->updateDomainCards()); findViewById(R.id.car_refresh).setOnClickListener(v->updateDomainCards());
        findViewById(R.id.home_command).setOnClickListener(v->{input.setText("home status");showTab("assistant");runGoal();});
        findViewById(R.id.car_command).setOnClickListener(v->{input.setText("car status");showTab("assistant");runGoal();});
        findViewById(R.id.device_command).setOnClickListener(v->{input.setText("device status");showTab("assistant");runGoal();});
    }

    private void showTab(String tab) {
        panelAssistant.setVisibility("assistant".equals(tab)?View.VISIBLE:View.GONE); panelDevices.setVisibility("devices".equals(tab)?View.VISIBLE:View.GONE);
        panelHome.setVisibility("home".equals(tab)?View.VISIBLE:View.GONE); panelCar.setVisibility("car".equals(tab)?View.VISIBLE:View.GONE);
        panelSettings.setVisibility("settings".equals(tab)?View.VISIBLE:View.GONE);
        tabAssistant.setAlpha("assistant".equals(tab)?1f:.55f); tabDevices.setAlpha("devices".equals(tab)?1f:.55f);
        tabHome.setAlpha("home".equals(tab)?1f:.55f); tabCar.setAlpha("car".equals(tab)?1f:.55f); tabSettings.setAlpha("settings".equals(tab)?1f:.55f);
        if("devices".equals(tab)||"home".equals(tab)||"car".equals(tab))updateDomainCards();
    }

    private void updateDomainCards() {
        String net=networkState();
        homeStatus.setText("HOME CORE\n\nAdapter: READY\nConnection: NOT CONNECTED\nEndpoint: NOT CONFIGURED\nNetwork: "+net+"\n\nجاهز لاستقبال Smart Home Gateway لاحقاً.");
        carStatus.setText("CAR CORE\n\nAdapter: READY\nConnection: NOT CONNECTED\nEndpoint: NOT CONFIGURED\nNetwork: "+net+"\n\nجاهز لاستقبال Car/Vespa API أو tracker لاحقاً.");
    }

    private void setupVoice() {
        if(!SpeechRecognizer.isRecognitionAvailable(this)){mic.setEnabled(false);return;}
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle p){status.setText("● SHADOW LISTENING...");} public void onBeginningOfSpeech(){status.setText("● SHADOW HEARING...");}
            public void onEndOfSpeech(){status.setText("● SHADOW THINKING...");} public void onError(int e){status.setText("● VOICE ERROR · "+e);mic.setEnabled(true);}
            public void onResults(Bundle r){ArrayList<String> v=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(v!=null&&!v.isEmpty()){input.setText(v.get(0));runGoal();}mic.setEnabled(true);}
            public void onPartialResults(Bundle p){} public void onEvent(int t,Bundle p){} public void onBufferReceived(byte[] b){} public void onRmsChanged(float v){}
        });
    }
    private void listen(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_PERMISSION);return;}
        if(recognizer==null){status.setText("● VOICE NOT AVAILABLE");return;}
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault());i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);mic.setEnabled(false);recognizer.startListening(i);
    }

    private void runGoal(){
        String goal=input.getText().toString().trim();if(goal.isEmpty()){input.setError("اكتب أو قل الأمر");return;}saveMemory(goal);send.setEnabled(false);status.setText("● SHADOW EXECUTING...");
        final String gateway=url.getText().toString().trim(),auth=token.getText().toString().trim();saveSettings(gateway,auth);
        executor.execute(()->{String result;try{result=gateway.isEmpty()?localBrain(goal):gatewayRequest(gateway,goal,auth);}catch(Exception e){result="SHADOW SAFE FAILURE\n\nGateway request failed: "+safeMessage(e);}final String r=result;
            runOnUiThread(()->{lastAnswer=r;output.setText(r);status.setText("● SHADOW READY · EXECUTION COMPLETE");send.setEnabled(true);updateHistoryInfo();updateDomainCards();});});
    }

    private String localBrain(String g){
        String x=g.toLowerCase(Locale.ROOT);String action=ShadowMobileActions.execute(this,g);if(action!=null)return action;
        if(x.contains("home")||g.contains("البيت")||g.contains("المنزل")||g.contains("سمارت هوم"))return homeLocalStatus();
        if(x.contains("car")||g.contains("العربية")||g.contains("السيارة")||g.contains("العربيه")||g.contains("المركبة"))return carLocalStatus();
        if(x.contains("hello")||x.contains("hi")||g.contains("سلام")||g.contains("اهلا")||g.contains("أهلا"))return "أهلاً. أنا SHADOW.\nCore: ONLINE\nVoice: READY\nMemory: LOCAL\nHome: ADAPTER READY\nCar: ADAPTER READY\nSafety: FAIL-CLOSED";
        if(x.contains("status")||g.contains("حالة")||g.contains("وضع"))return "SHADOW SYSTEM STATUS\n\nCore        ONLINE\nVoice       READY\nMemory      LOCAL\nHome        ADAPTER READY\nCar         ADAPTER READY\nGateway     "+(url.getText().toString().trim().isEmpty()?"OPTIONAL":"CONFIGURED")+"\nSecurity    FAIL-CLOSED\nNetwork     "+networkState();
        if(g.contains("الوقت")||x.contains("time"))return "الوقت الآن: "+clock.format(new Date());
        if(g.contains("التاريخ")||x.contains("date"))return "التاريخ: "+new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date());
        if(g.contains("ذاكرة")||x.contains("memory"))return readMemory(); if(g.contains("جهازي")||g.contains("الجهاز")||x.contains("device"))return deviceStatus();
        if(g.contains("افتح جوجل")||g.contains("افتح Google")||x.contains("open google"))return openUrl("https://www.google.com","تم فتح Google.");
        if(g.contains("افتح يوتيوب")||g.contains("افتح YouTube")||x.contains("open youtube"))return openUrl("https://www.youtube.com","تم فتح YouTube.");
        if(g.contains("شارك")||x.contains("share"))return "استخدم زر SHARE لمشاركة آخر رد بأمان.";
        if(g.startsWith("احسب ")||x.startsWith("calculate ")||g.matches(".*[0-9][0-9+*/(). -]+[0-9].*")){String e=g.replaceFirst("(?i)^احسب\\s*","").replaceFirst("(?i)^calculate\\s*","").trim();try{return "RESULT\n\n"+formatNumber(eval(e));}catch(Exception ex){return "لم أقدر أحسب التعبير بأمان: "+safeMessage(ex);}}
        return "SHADOW LOCAL CORE\n\nاستلمت: "+g+"\n\nالأمر محفوظ في الذاكرة المحلية.\nللتحليل العميق والأدوات السحابية، اربط Gateway آمن عبر HTTPS.";
    }
    private String homeLocalStatus(){showTab("home");return "HOME CORE\n\nالحالة: ADAPTER READY\nالاتصال: غير موصل\nالتحكم: محمي حتى يتم ربط Smart Home Gateway فعلي.\nالتصميم والـAPI boundary موجودان من الآن.";}
    private String carLocalStatus(){showTab("car");return "CAR CORE\n\nالحالة: ADAPTER READY\nالاتصال: غير موصل\nالتحكم: محمي حتى يتم ربط Car/Vespa API أو tracker فعلي.\nالتصميم والـAPI boundary موجودان من الآن.";}
    private String openUrl(String target,String message){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(target)));return message;}catch(Exception e){return "تعذر فتح الرابط بأمان: "+safeMessage(e);}}

    private void saveMemory(String command){SharedPreferences p=getPreferences(MODE_PRIVATE);String old=p.getString("history","");String entry=clock.format(new Date())+" | "+command.replace("\n"," ");String[] lines=old.isEmpty()?new String[0]:old.split("\\n");StringBuilder h=new StringBuilder(entry);int count=0;for(String s:lines)if(!s.trim().isEmpty()&&count++<19)h.append("\n").append(s);p.edit().putString("last_command",command).putString("history",h.toString()).apply();}
    private String readMemory(){SharedPreferences p=getPreferences(MODE_PRIVATE);String last=p.getString("last_command","");String history=p.getString("history","");return "LOCAL MEMORY\n\nآخر أمر:\n"+(last.isEmpty()?"لا توجد أوامر محفوظة بعد.":last)+"\n\nHISTORY\n"+(history.isEmpty()?"لا يوجد سجل.":history);}
    private void updateHistoryInfo(){SharedPreferences p=getPreferences(MODE_PRIVATE);String h=p.getString("history","");int n=h.isEmpty()?0:h.split("\\n").length;historyInfo.setText("LOCAL MEMORY  ·  "+n+" سجل محفوظ");}
    private void saveSettings(String u,String t){getPreferences(MODE_PRIVATE).edit().putString("gateway",u).putString("token",t).apply();}
    private void loadSettings(){SharedPreferences p=getPreferences(MODE_PRIVATE);url.setText(p.getString("gateway",""));token.setText(p.getString("token",""));}
    private void copyAnswer(){String s=lastAnswer.isEmpty()?output.getText().toString():lastAnswer;((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("SHADOW",s));status.setText("● RESPONSE COPIED");}
    private void shareAnswer(){String s=lastAnswer.isEmpty()?output.getText().toString():lastAnswer;try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,s);startActivity(Intent.createChooser(i,"مشاركة رد SHADOW"));}catch(Exception e){status.setText("● SHARE FAILED");}}
    private String deviceStatus(){BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);int battery=bm==null?-1:bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);return "DEVICE STATUS\n\nModel: "+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\nBattery: "+(battery<0?"unknown":battery+"%")+"\nNetwork: "+networkState();}
    private void updateDeviceInfo(){String info=Build.MANUFACTURER+" "+Build.MODEL+"  ·  Android "+Build.VERSION.RELEASE+"  ·  "+networkState();deviceInfo.setText(info);if(deviceInfoCard!=null){int b=batteryPercent();deviceInfoCard.setText(info+"\nBattery: "+(b<0?"unknown":b+"%"));}}
    private int batteryPercent(){BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE);return bm==null?-1:bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);}
    private String networkState(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);if(cm==null)return "UNKNOWN";Network n=cm.getActiveNetwork();if(n==null)return "OFFLINE";NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c==null)return "ONLINE";if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return "Wi-Fi";if(c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return "Mobile";return "ONLINE";}

    private double eval(String s){final int[] p={0};double v=parseExpr(s,p);skip(s,p);if(p[0]!=s.length())throw new IllegalArgumentException("تعبير غير صالح");return v;}
    private double parseExpr(String s,int[] p){double v=parseTerm(s,p);while(true){skip(s,p);if(p[0]>=s.length())return v;char c=s.charAt(p[0]);if(c!='+'&&c!='-')return v;p[0]++;double r=parseTerm(s,p);v=c=='+'?v+r:v-r;}}
    private double parseTerm(String s,int[] p){double v=parseFactor(s,p);while(true){skip(s,p);if(p[0]>=s.length())return v;char c=s.charAt(p[0]);if(c!='*'&&c!='/')return v;p[0]++;double r=parseFactor(s,p);if(c=='/'&&r==0)throw new ArithmeticException("لا يمكن القسمة على صفر");v=c=='*'?v*r:v/r;}}
    private double parseFactor(String s,int[] p){skip(s,p);if(p[0]<s.length()&&s.charAt(p[0])=='('){p[0]++;double v=parseExpr(s,p);skip(s,p);if(p[0]>=s.length()||s.charAt(p[0])!=')')throw new IllegalArgumentException("قوس غير مغلق");p[0]++;return v;}int start=p[0];if(p[0]<s.length()&&(s.charAt(p[0])=='+'||s.charAt(p[0])=='-'))p[0]++;while(p[0]<s.length()&&(Character.isDigit(s.charAt(p[0]))||s.charAt(p[0])=='.'))p[0]++;if(start==p[0])throw new IllegalArgumentException("رقم متوقع");return Double.parseDouble(s.substring(start,p[0]));}
    private void skip(String s,int[] p){while(p[0]<s.length()&&Character.isWhitespace(s.charAt(p[0])))p[0]++;}
    private String formatNumber(double n){if(n==Math.rint(n))return String.valueOf((long)n);return String.format(Locale.US,"%.8f",n).replaceAll("0+$","").replaceAll("\\.$","");}

    private String gatewayRequest(String base,String goal,String auth)throws Exception{if(!base.startsWith("http://")&&!base.startsWith("https://"))base="https://"+base;String body="{\"action\":\"run_goal\",\"payload\":{\"goal\":\""+jsonEscape(goal)+"\"},\"context\":{\"platform\":\"android\",\"runtime\":\"shadow-0.42\",\"device\":\""+jsonEscape(Build.MANUFACTURER+" "+Build.MODEL)+"\"}}";HttpURLConnection c=(HttpURLConnection)new URL(base.replaceAll("/$","")+"/v1/request").openConnection();c.setConnectTimeout(8000);c.setReadTimeout(30000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");if(!auth.isEmpty())c.setRequestProperty("Authorization","Bearer "+auth);try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();String response=read(in);c.disconnect();if(code>=400)throw new IOException("HTTP "+code+": "+response);return "SHADOW GATEWAY\n\n"+response;}
    private void checkGateway(){String base=url.getText().toString().trim();if(base.isEmpty()){status.setText("● LOCAL CORE · NO GATEWAY");return;}if(!base.startsWith("http://")&&!base.startsWith("https://"))base="https://"+base;final String target=base;check.setEnabled(false);status.setText("● CHECKING GATEWAY...");executor.execute(()->{String r;try{r=httpGet(target+"/health",token.getText().toString().trim());}catch(Exception e){r="Gateway unavailable: "+safeMessage(e);}final String rr=r;runOnUiThread(()->{output.setText(rr);status.setText(rr.startsWith("HTTP 200")?"● GATEWAY CONNECTED":"● GATEWAY CHECK FAILED");check.setEnabled(true);});});}
    private String httpGet(String u,String auth)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setRequestMethod("GET");if(!auth.isEmpty())c.setRequestProperty("Authorization","Bearer "+auth);int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();String s=read(in);c.disconnect();return "HTTP "+code+"\n"+s;}
    private static String read(InputStream in)throws IOException{if(in==null)return "";StringBuilder s=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)s.append(line).append('\n');}return s.toString().trim();}
    private static String jsonEscape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
    private static String safeMessage(Exception e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m;}
    public void onInit(int result){if(result==TextToSpeech.SUCCESS){int r=tts.setLanguage(Locale.getDefault());if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED)tts.setLanguage(Locale.ENGLISH);}}
    private void speak(String text){if(tts!=null&&!text.trim().isEmpty())tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"shadow-answer");}
    @Override protected void onResume(){super.onResume();if(deviceInfo!=null){updateDeviceInfo();updateDomainCards();}}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}executor.shutdownNow();super.onDestroy();}
}
