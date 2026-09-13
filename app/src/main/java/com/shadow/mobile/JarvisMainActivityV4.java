package com.shadow.mobile;
import android.Manifest;import android.content.*;import android.content.pm.PackageManager;import android.view.*;import android.widget.*;import java.util.*;
/** MOD-44.9: background radar command bridge over the existing Jarvis UI. */
public class JarvisMainActivityV4 extends JarvisMainActivityV3 {
 @Override public void onCreate(android.os.Bundle b){super.onCreate(b);ViewGroup root=findViewById(android.R.id.content);if(root==null)return;TextView send=findText(root,"➤");if(send!=null)send.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP){EditText in=findInput(root);if(in!=null){String s=in.getText().toString().trim().toLowerCase(Locale.ROOT);if(isRadar(s)){startShadowRadar();return true;}}}return false;});}
 private TextView findText(ViewGroup g,String t){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v instanceof TextView&&t.contentEquals(((TextView)v).getText()))return(TextView)v;if(v instanceof ViewGroup){TextView r=findText((ViewGroup)v,t);if(r!=null)return r;}}return null;}
 private EditText findInput(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v instanceof EditText)return(EditText)v;if(v instanceof ViewGroup){EditText r=findInput((ViewGroup)v);if(r!=null)return r;}}return null;}
 private boolean isRadar(String x){return x.contains("شغل الرادار")||x.contains("شغّل الرادار")||x.contains("رادار شادو")||x.contains("كاميرات الطريق")||x.contains("كاميرات السرعة");}
 private void startShadowRadar(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},904);return;}Intent i=new Intent(this,ShadowRadarService.class);if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);Toast.makeText(this,"SHADOW Radar شغال في الخلفية — مستقل عن Radarbot.",Toast.LENGTH_LONG).show();}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==904&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startShadowRadar();}
}
