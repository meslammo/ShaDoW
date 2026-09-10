package com.shadow.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    EditText url, token, goal; TextView status; Handler main = new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle b) { super.onCreate(b); setContentView(R.layout.activity_main);
        url=findViewById(R.id.url); token=findViewById(R.id.token); goal=findViewById(R.id.goal); status=findViewById(R.id.status);
        findViewById(R.id.health).setOnClickListener(v -> call("GET", "/health", null));
        findViewById(R.id.send).setOnClickListener(v -> { String g=goal.getText().toString().trim(); if(g.isEmpty()){status.setText("Enter a goal first");return;} call("POST","/v1/request", "{\"goal\":"+json(g)+"}"); }); }
    String json(String s){ return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\""; }
    void call(String method,String path,String body){ status.setText("Connecting…"); new Thread(() -> { try { String base=url.getText().toString().trim().replaceAll("/$",""); HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection(); c.setRequestMethod(method); c.setConnectTimeout(8000); c.setReadTimeout(30000); c.setRequestProperty("Accept","application/json"); String t=token.getText().toString().trim(); if(!t.isEmpty()) c.setRequestProperty("Authorization","Bearer "+t); if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode(); InputStream in=code>=400?c.getErrorStream():c.getInputStream(); StringBuilder sb=new StringBuilder(); if(in!=null){try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)sb.append(line);}} String out="HTTP "+code+"\n"+sb; main.post(()->status.setText(out)); c.disconnect();
        } catch(Exception e){ main.post(()->status.setText("CONNECTION FAILED\n"+e.getMessage())); } }).start(); }
}
