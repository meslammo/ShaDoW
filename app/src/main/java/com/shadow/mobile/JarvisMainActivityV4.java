package com.shadow.mobile;
import android.Manifest;import android.content.*;import android.content.pm.PackageManager;import android.view.*;import android.widget.*;import java.util.*;
/** MOD-46.3: final-runtime bridge on the existing V4 launcher; capability-gated and evidence-based. */
public class JarvisMainActivityV4 extends JarvisMainActivityV3 {
 private ShadowDevelopmentAgent development; private ShadowCloudClient cloud; private ShadowCapabilityGate gate;
 @Override public void onCreate(android.os.Bundle b){super.onCreate(b);development=new ShadowDevelopmentAgent(this);cloud=new ShadowCloudClient(this);gate=new ShadowCapabilityGate(this);ViewGroup root=findViewById(android.R.id.content);if(root==null)return;TextView send=findText(root,"➤");if(send!=null)send.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP){EditText in=findInput(root);if(in!=null){String s=in.getText().toString().trim();if(isDevelopment(s)){runDevelopment(s);return true;}if(isRadar(s)){startShadowRadar();return true;}}}return false;});}
 private TextView findText(ViewGroup g,String t){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v instanceof TextView&&t.contentEquals(((TextView)v).getText()))return(TextView)v;if(v instanceof ViewGroup){TextView r=findText((ViewGroup)v,t);if(r!=null)return r;}}return null;}
 private EditText findInput(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v instanceof EditText)return(EditText)v;if(v instanceof ViewGroup){EditText r=findInput((ViewGroup)v);if(r!=null)return r;}}return null;}
 private boolean isRadar(String x){String s=x.toLowerCase(Locale.ROOT);return s.contains("شغل الرادار")||s.contains("شغّل الرادار")||s.contains("رادار شادو")||s.contains("كاميرات الطريق")||s.contains("كاميرات السرعة");}
 private boolean isDevelopment(String x){String s=x.toLowerCase(Locale.ROOT);return s.contains("طور شادو")||s.contains("طوّر شادو")||s.contains("طور نفسك")||s.contains("طوّر نفسك")||s.contains("كمل شادو")||s.contains("كمّل شادو")||s.contains("حلل المشروع")||s.contains("حلّل المشروع")||s.contains("شوف الناقص")||s.contains("development engine")||s.contains("development agent")||s.contains("self development");}
 private void runDevelopment(String request){
  development.plan(request);
  Toast.makeText(this,"🧠 Development Engine: تحليل → بحث → تقييم → خطة → اختبار → Build → Verify",Toast.LENGTH_LONG).show();
  final String capability=gate.releaseStatus();
  new AlertDialog.Builder(this).setTitle("🧠 SHADOW Final Runtime").setMessage(capability+"\n\nالمحرك المحلي:\n"+development.status()+"\n\nجاري طلب خطة موثقة من الـ Runtime الأونلاين…").setPositiveButton("تمام",null).show();
  new Thread(()->{try{
      String plan=cloud.developmentPlan(request,"Android SHADOW project; launcher=MainActivity→JarvisMainActivityV4; capability gate active");
      runOnUiThread(()->new AlertDialog.Builder(this).setTitle("🧠 Development Plan • Runtime").setMessage(plan).setPositiveButton("تمام",null).show());
    }catch(Throwable e){runOnUiThread(()->Toast.makeText(this,"Runtime plan غير متاح حالياً: "+(e.getMessage()==null?"unknown":e.getMessage()),Toast.LENGTH_LONG).show());}}).start();
 }
 private void startShadowRadar(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},904);return;}Intent i=new Intent(this,ShadowRadarService.class);if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);Toast.makeText(this,"SHADOW Radar شغال في الخلفية — مستقل عن Radarbot.",Toast.LENGTH_LONG).show();}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==904&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startShadowRadar();}
}
