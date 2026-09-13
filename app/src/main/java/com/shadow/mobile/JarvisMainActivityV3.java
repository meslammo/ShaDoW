package com.shadow.mobile;
import android.app.*;import android.content.*;import android.graphics.Color;import android.graphics.drawable.GradientDrawable;import android.net.Uri;import android.view.*;import android.widget.*;
/** MOD-44.7: compile-safe Quest/Build overlay. */
public class JarvisMainActivityV3 extends JarvisMainActivityV2 {
 private ShadowDevelopmentAgent dev; private ShadowWorkspace workspace; private ShadowRadarController radar;
 private int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);} private GradientDrawable bg(){GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(29,31,36));g.setCornerRadius(dp(18));return g;}
 @Override public void onCreate(android.os.Bundle b){super.onCreate(b);dev=new ShadowDevelopmentAgent(this);workspace=new ShadowWorkspace(this);radar=new ShadowRadarController(this);ViewGroup content=findViewById(android.R.id.content);if(content.getChildCount()==0)return;View v=content.getChildAt(0);if(!(v instanceof ViewGroup))return;ViewGroup root=(ViewGroup)v;LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER);TextView q=button("QUEST"),build=button("BUILD");bar.addView(q,new LinearLayout.LayoutParams(dp(92),dp(34)));bar.addView(new Space(this),new LinearLayout.LayoutParams(dp(12),1));bar.addView(build,new LinearLayout.LayoutParams(dp(92),dp(34)));root.addView(bar,Math.min(1,root.getChildCount()));q.setOnClickListener(x->quest());build.setOnClickListener(x->build());}
 private TextView button(String s){TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(12);v.setGravity(Gravity.CENTER);v.setBackground(bg());return v;}
 private void quest(){new AlertDialog.Builder(this).setTitle("QUEST").setMessage("Development Agent\n\n"+dev.status()+"\n\nقول: كمل شادو أو حلل المشروع لعمل Quest.").setPositiveButton("إغلاق",null).show();}
 private void build(){new AlertDialog.Builder(this).setTitle("BUILD").setMessage("Workspace: "+workspace.fileCount()+" files\nAnalyze → Edit → Test → Build → Verify").setPositiveButton("GitHub Actions",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/meslammo/ShaDoW/actions")))).setNegativeButton("إغلاق",null).show();}
 @Override protected void onDestroy(){try{radar.stop();}catch(Exception ignored){}super.onDestroy();}
}
