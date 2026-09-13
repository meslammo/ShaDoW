package com.shadow.mobile;
import android.Manifest;import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.net.Uri;import android.view.*;import android.widget.*;import java.util.*;
/** MOD-44.9: background radar + Quest/Build overlay. */
public class JarvisMainActivityV4 extends JarvisMainActivityV3 {
 private int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(android.os.Bundle b){super.onCreate(b);ViewGroup root=findViewById(android.R.id.content);if(root==null)return;}
 public void startShadowRadar(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},904);return;}Intent i=new Intent(this,ShadowRadarService.class);if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);Toast.makeText(this,"SHADOW Radar شغال في الخلفية — مستقل عن Radarbot.",Toast.LENGTH_LONG).show();}
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==904&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)startShadowRadar();}
}
