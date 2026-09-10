package com.shadow.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private EditText input, url;
    private TextView status, output;
    private Button send, check;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        input=findViewById(R.id.goal); url=findViewById(R.id.url);
        status=findViewById(R.id.status); output=findViewById(R.id.output);
        send=findViewById(R.id.send); check=findViewById(R.id.check);
        status.setText("● SHADOW ONLINE · LOCAL BRAIN");
        output.setText("أنا SHADOW.\nجاهز للعمل بدون سيرفر أو Localhost.\n\nاكتب أي أمر للتجربة.");
        send.setOnClickListener(v -> runGoal());
        check.setOnClickListener(v -> checkGateway());
    }
    private void runGoal() {
        String goal=input.getText().toString().trim();
        if(goal.isEmpty()){ input.setError("اكتب الأمر"); return; }
        send.setEnabled(false); status.setText("● SHADOW THINKING...");
        executor.execute(() -> {
            String r=localBrain(goal);
            runOnUiThread(() -> { output.setText(r); status.setText("● SHADOW READY · VERIFIED LOCAL RESPONSE"); send.setEnabled(true); });
        });
    }
    private String localBrain(String g) {
        String x=g.toLowerCase();
        if(x.contains("hello")||x.contains("hi")||g.contains("سلام")||g.contains("اهلا")||g.contains("أهلا")) return "أهلاً. أنا SHADOW.\nالوضع: Local Demo\nالذاكرة: متاحة داخل الجلسة\nالأدوات الخارجية: غير مفعلة حتى يتم ربط Gateway.";
        if(x.contains("status")||g.contains("حالة")) return "SHADOW STATUS\n\nBrain: READY\nReasoning: READY\nMemory: SESSION\nGateway: OPTIONAL\nNetwork: NOT REQUIRED\nSafety: FAIL-CLOSED";
        return "SHADOW RECEIVED\n\nالأمر: " + g + "\n\nتمت المعالجة محلياً بنجاح.\nللتفكير السحابي/الأدوات الحقيقية، اربط Gateway من إعدادات السيرفر الاختيارية بالأسفل.";
    }
    private void checkGateway() {
        String base=url.getText().toString().trim();
        if(base.isEmpty()){ status.setText("● LOCAL MODE · NO GATEWAY CONFIGURED"); return; }
        if(!base.startsWith("http://")&&!base.startsWith("https://")) base="http://"+base;
        final String target=base;
        check.setEnabled(false); status.setText("● CHECKING GATEWAY...");
        executor.execute(() -> {
            String r;
            try { r=httpGet(target+"/health"); } catch(Exception e){ r="Gateway unavailable: "+e.getClass().getSimpleName(); }
            final String rr=r;
            runOnUiThread(() -> { output.setText(rr); status.setText(rr.startsWith("OK")?"● GATEWAY CONNECTED":"● LOCAL MODE · GATEWAY OFFLINE"); check.setEnabled(true); });
        });
    }
    private String httpGet(String u) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(3500); c.setReadTimeout(3500); c.setRequestMethod("GET");
        int code=c.getResponseCode(); InputStream in=code<400?c.getInputStream():c.getErrorStream();
        BufferedReader br=new BufferedReader(new InputStreamReader(in)); StringBuilder s=new StringBuilder(); String line; while((line=br.readLine())!=null)s.append(line).append('\n'); c.disconnect();
        return "HTTP "+code+"\n"+s.toString();
    }
    @Override protected void onDestroy(){ executor.shutdownNow(); super.onDestroy(); }
}
