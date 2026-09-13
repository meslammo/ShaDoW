package com.shadow.mobile;

import android.content.*;
import android.location.Location;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** MOD-42: independent camera data + detection engine using OSM/Overpass. */
public final class ShadowRadarManager {
    public static final class Camera { public final double lat,lon; public final Double limit; Camera(double a,double o,Double l){lat=a;lon=o;limit=l;} }
    private final Context ctx; private final ArrayList<Camera> cameras=new ArrayList<>(); private long lastSync=0; private double warnMeters=600;
    public ShadowRadarManager(Context c){ctx=c.getApplicationContext();loadCache();}
    private File cache(){return new File(ctx.getFilesDir(),"shadow_radar_cameras.json");}
    private void loadCache(){try{String s=read(cache());if(s==null)return;JSONArray a=new JSONArray(s);cameras.clear();for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);cameras.add(new Camera(o.getDouble("lat"),o.getDouble("lon"),o.has("limit")?o.optDouble("limit"):Double.NaN));}}catch(Exception ignored){}}
    private void saveCache(){try{JSONArray a=new JSONArray();for(Camera c:cameras){JSONObject o=new JSONObject();o.put("lat",c.lat);o.put("lon",c.lon);if(!Double.isNaN(c.limit))o.put("limit",c.limit);a.put(o);}write(cache(),a.toString());}catch(Exception ignored){}}
    private static String read(File f)throws Exception{if(!f.exists())return null;BufferedReader b=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"));StringBuilder s=new StringBuilder();String l;while((l=b.readLine())!=null)s.append(l);b.close();return s.toString();}
    private static void write(File f,String s)throws Exception{FileOutputStream o=new FileOutputStream(f);o.write(s.getBytes("UTF-8"));o.close();}
    public int count(){return cameras.size();}
    public String status(){return "Radar: "+cameras.size()+" كاميرا محفوظة • OSM • مستقل عن Radarbot";}
    public void syncNearby(final double lat,final double lon,final int radiusMeters){new Thread(()->{try{String q="[out:json][timeout:20];node(around:"+radiusMeters+","+lat+","+lon+")[\"highway\"=\"speed_camera\"];out;";URL u=new URL("https://overpass-api.de/api/interpreter?data="+URLEncoder.encode(q,"UTF-8"));HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(20000);BufferedReader b=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));StringBuilder s=new StringBuilder();String l;while((l=b.readLine())!=null)s.append(l);b.close();JSONObject root=new JSONObject(s.toString());JSONArray es=root.optJSONArray("elements");cameras.clear();if(es!=null)for(int i=0;i<es.length();i++){JSONObject e=es.getJSONObject(i);JSONObject t=e.optJSONObject("tags");Double lim=null;if(t!=null){String raw=t.optString("maxspeed","");try{lim=Double.parseDouble(raw.replaceAll("[^0-9.]",""));}catch(Exception ignored){}}cameras.add(new Camera(e.getDouble("lat"),e.getDouble("lon"),lim));}saveCache();lastSync=System.currentTimeMillis();}catch(Exception ignored){}}).start();}
    public String check(Location here){Camera best=null;float bestD=Float.MAX_VALUE;float[] res=new float[3];for(Camera c:cameras){Location.distanceBetween(here.getLatitude(),here.getLongitude(),c.lat,c.lon,res);if(res[0]<bestD){bestD=res[0];best=c;}}if(best==null||bestD>warnMeters)return null;if(here.hasBearing()){Location.distanceBetween(here.getLatitude(),here.getLongitude(),best.lat,best.lon,res);float delta=Math.abs((res[1]-here.getBearing()+540)%360-180);if(delta>85)return null;}String lim=(best.limit!=null&&!Double.isNaN(best.limit))?" • الحد "+(int)best.limit+" كم/س":"";return "كاميرا سرعة على بعد "+(int)bestD+" متر"+lim;}
}
