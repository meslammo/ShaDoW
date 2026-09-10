package com.shadow.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
    private TextView status, output;
    private Button send, check, mic, speak;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private String lastAnswer = "";
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        input=findViewById(R.id.goal); url=findViewById(R.id.url); token=findViewById(R.id.token);
        status=findViewById(R.id.status); output=findViewById(R.id.output);
        send=findViewById(R.id.send); check=findViewById(R.id.check); mic=findViewById(R.id.mic); speak=findViewById(R.id.speak);
        tts = new TextToSpeech(this, this);
        setupVoice();
        status.setText("● SHADOW ONLINE · LOCAL CORE");
        output.setText("أنا SHADOW.\n\nالصوت، الذاكرة المحلية، الأوامر السريعة، وGateway جاهزين.\nاكتب أمرك أو اضغط VOICE.");
        send.setOnClickListener(v -> runGoal());
        mic.setOnClickListener(v -> listen());
        speak.setOnClickListener(v -> speak(lastAnswer.isEmpty() ? output.getText().toString() : lastAnswer));
        check.setOnClickListener(v -> checkGateway());
    }

    private void setupVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { mic.setEnabled(false); return; }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p){ status.setText("● SHADOW LISTENING..."); }
            public void onBeginningOfSpeech(){ status.setText("● SHADOW HEARING..."); }
            public void onEndOfSpeech(){ status.setText("● SHADOW THINKING..."); }
            public void onError(int e){ status.setText("● VOICE ERROR · " + e); mic.setEnabled(true); }
            public void onResults(Bundle r){
                ArrayList<String> values=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(values!=null && !values.isEmpty()){ input.setText(values.get(0)); runGoal(); }
                mic.setEnabled(true);
            }
            public void onPartialResults(Bundle p){}
            public void onEvent(int t, Bundle p){}
            public void onBufferReceived(byte[] b){}
            public void onRmsChanged(float v){}
        });
    }

    private void listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION); return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        mic.setEnabled(false); recognizer.startListening(i);
    }

    private void runGoal() {
        String goal=input.getText().toString().trim();
        if(goal.isEmpty()){ input.setError("اكتب أو قل الأمر"); return; }
        send.setEnabled(false); status.setText("● SHADOW EXECUTING...");
        executor.execute(() -> {
            String gateway=url.getText().toString().trim();
            String result;
            try {
                result = gateway.isEmpty() ? localBrain(goal) : gatewayRequest(gateway, goal, token.getText().toString().trim());
            } catch(Exception e) { result="SHADOW SAFE FAILURE\n\nGateway request failed: "+e.getMessage(); }
            final String r=result;
            runOnUiThread(() -> { lastAnswer=r; output.setText(r); status.setText("● SHADOW READY · EXECUTION COMPLETE"); send.setEnabled(true); });
        });
    }

    private String localBrain(String g) {
        String x=g.toLowerCase(Locale.ROOT);
        saveMemory(g);
        String mobileAction = ShadowMobileActions.execute(this, g);
        if (mobileAction != null) return mobileAction;
        if(x.contains("hello")||x.contains("hi")||g.contains("سلام")||g.contains("اهلا")||g.contains("أهلا")) return "أهلاً. أنا SHADOW.\nCore: ONLINE\nVoice: READY\nMemory: LOCAL\nSafety: FAIL-CLOSED";
        if(x.contains("status")||g.contains("حالة")||g.contains("وضع")) return "SHADOW SYSTEM STATUS\n\nCore        ONLINE\nVoice       READY\nMemory      LOCAL\nGateway     "+(url.getText().toString().trim().isEmpty()?"OPTIONAL":"CONFIGURED")+"\nSecurity    FAIL-CLOSED";
        if(g.contains("الوقت")||x.contains("time")) return "الوقت الآن: " + clock.format(new Date());
        if(g.contains("التاريخ")||x.contains("date")) return "التاريخ: " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        if(g.contains("ذاكرة")||x.contains("memory")) return readMemory();
        if(g.contains("افتح جوجل")||g.contains("افتح Google")||x.contains("open google")) return openUrl("https://www.google.com", "تم فتح Google.");
        if(g.contains("افتح يوتيوب")||g.contains("افتح YouTube")||x.contains("open youtube")) return openUrl("https://www.youtube.com", "تم فتح YouTube.");
        if(g.startsWith("احسب ")||x.startsWith("calculate ")||g.matches(".*[0-9][0-9+*/(). -]+[0-9].*")) {
            String expr=g.replaceFirst("(?i)^احسب\\s*", "").replaceFirst("(?i)^calculate\\s*", "").trim();
            try { return "RESULT\n\n" + formatNumber(eval(expr)); } catch(Exception e) { return "لم أقدر أحسب التعبير بأمان: " + e.getMessage(); }
        }
        return "SHADOW LOCAL CORE\n\nاستلمت: " + g + "\n\nالأمر محفوظ في الذاكرة المحلية.\nللتحليل العميق والأدوات السحابية، اربط Gateway آمن عبر HTTPS.";
    }

    private String openUrl(String target, String message) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target))); return message; }
        catch(Exception e) { return "تعذر فتح الرابط بأمان: " + e.getMessage(); }
    }

    private void saveMemory(String command) {
        getPreferences(MODE_PRIVATE).edit().putString("last_command", command).apply();
    }
    private String readMemory() {
        String last=getPreferences(MODE_PRIVATE).getString("last_command", "لا توجد أوامر محفوظة بعد.");
        return "LOCAL MEMORY\n\nآخر أمر:\n"+last;
    }

    private double eval(String s) {
        final int[] p={0};
        double v=parseExpr(s,p);
        skip(s,p); if(p[0]!=s.length()) throw new IllegalArgumentException("تعبير غير صالح");
        return v;
    }
    private double parseExpr(String s,int[] p){ double v=parseTerm(s,p); while(true){ skip(s,p); if(p[0]>=s.length()) return v; char c=s.charAt(p[0]); if(c!='+'&&c!='-') return v; p[0]++; double r=parseTerm(s,p); v=c=='+'?v+r:v-r; }}
    private double parseTerm(String s,int[] p){ double v=parseFactor(s,p); while(true){ skip(s,p); if(p[0]>=s.length()) return v; char c=s.charAt(p[0]); if(c!='*'&&c!='/') return v; p[0]++; double r=parseFactor(s,p); if(c=='/'&&r==0) throw new ArithmeticException("لا يمكن القسمة على صفر"); v=c=='*'?v*r:v/r; }}
    private double parseFactor(String s,int[] p){ skip(s,p); if(p[0]<s.length()&&s.charAt(p[0])=='('){p[0]++;double v=parseExpr(s,p);skip(s,p);if(p[0]>=s.length()||s.charAt(p[0])!=')')throw new IllegalArgumentException("قوس غير مغلق");p[0]++;return v;} int start=p[0]; if(p[0]<s.length()&&(s.charAt(p[0])=='+'||s.charAt(p[0])=='-'))p[0]++; while(p[0]<s.length()&&(Character.isDigit(s.charAt(p[0]))||s.charAt(p[0])=='.'))p[0]++; if(start==p[0])throw new IllegalArgumentException("رقم متوقع"); return Double.parseDouble(s.substring(start,p[0])); }
    private void skip(String s,int[] p){while(p[0]<s.length()&&Character.isWhitespace(s.charAt(p[0])))p[0]++;}
    private String formatNumber(double n){ if(n==Math.rint(n)) return String.valueOf((long)n); return String.format(Locale.US,"%.8f",n).replaceAll("0+$","").replaceAll("\\.$",""); }

    private String gatewayRequest(String base, String goal, String authToken) throws Exception {
        if(!base.startsWith("http://")&&!base.startsWith("https://")) base="https://"+base;
        String body="{\"action\":\"run_goal\",\"payload\":{\"goal\":\""+jsonEscape(goal)+"\"},\"context\":{\"platform\":\"android\",\"runtime\":\"shadow-0.40\"}}";
        HttpURLConnection c=(HttpURLConnection)new URL(base.replaceAll("/$","")+"/v1/request").openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(30000); c.setRequestMethod("POST"); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("Accept","application/json");
        if(!authToken.isEmpty()) c.setRequestProperty("Authorization","Bearer "+authToken);
        try(OutputStream out=c.getOutputStream()){ out.write(body.getBytes(StandardCharsets.UTF_8)); }
        int code=c.getResponseCode(); InputStream in=code<400?c.getInputStream():c.getErrorStream(); String response=read(in); c.disconnect();
        if(code>=400) throw new IOException("HTTP "+code+": "+response);
        return "SHADOW GATEWAY\n\n"+response;
    }

    private void checkGateway() {
        String base=url.getText().toString().trim();
        if(base.isEmpty()){ status.setText("● LOCAL CORE · NO GATEWAY"); return; }
        if(!base.startsWith("http://")&&!base.startsWith("https://")) base="https://"+base;
        final String target=base; check.setEnabled(false); status.setText("● CHECKING GATEWAY...");
        executor.execute(() -> { String r; try { r=httpGet(target+"/health", token.getText().toString().trim()); } catch(Exception e){ r="Gateway unavailable: "+e.getMessage(); } final String rr=r; runOnUiThread(() -> { output.setText(rr); status.setText(rr.startsWith("HTTP 200")?"● GATEWAY CONNECTED":"● GATEWAY CHECK FAILED"); check.setEnabled(true); }); });
    }
    private String httpGet(String u,String authToken)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setRequestMethod("GET");if(!authToken.isEmpty())c.setRequestProperty("Authorization","Bearer "+authToken);int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();String s=read(in);c.disconnect();return "HTTP "+code+"\n"+s;}
    private static String read(InputStream in)throws IOException{if(in==null)return "";StringBuilder s=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)s.append(line).append('\n');}return s.toString().trim();}
    private static String jsonEscape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");}
    public void onInit(int result){if(result==TextToSpeech.SUCCESS)tts.setLanguage(Locale.getDefault());}
    private void speak(String text){if(tts!=null&&!text.trim().isEmpty())tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"shadow-answer");}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null){tts.stop();tts.shutdown();}executor.shutdownNow();super.onDestroy();}
}
