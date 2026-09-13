package com.shadow.mobile;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** MOD-46.14: safe real project workspace for the Development Agent. */
public final class ShadowWorkspace {
    private final File root;
    public ShadowWorkspace(Context c){root=new File(c.getFilesDir(),"shadow_workspace");root.mkdirs();}
    public File root(){return root;}
    public int fileCount(){int[] n={0};walk(root,n);return n[0];}
    private void walk(File f,int[] n){File[] xs=f.listFiles();if(xs==null)return;for(File x:xs){if(x.getName().equals(".shadow"))continue;if(x.isFile())n[0]++;else walk(x,n);}}
    private File safe(String relative)throws IOException{String p=relative==null?"":relative.trim();if(p.isEmpty()||p.startsWith("/")||p.contains("..")||p.contains("\\"))throw new IOException("unsafe workspace path");File f=new File(root,p).getCanonicalFile();if(!f.equals(root)&&!root.toPath().toAbsolutePath().normalize().startsWith(root.toPath().toAbsolutePath().normalize()))throw new IOException("workspace path escape");if(!f.equals(root)&&!f.getPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("workspace path escape");return f;}
    public synchronized String writeText(String relative,String content)throws IOException{File f=safe(relative);File parent=f.getParentFile();if(parent!=null)parent.mkdirs();try(FileOutputStream out=new FileOutputStream(f)){out.write((content==null?"":content).getBytes(StandardCharsets.UTF_8));}return relative;}
    public synchronized String readText(String relative)throws IOException{File f=safe(relative);return new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}
    public synchronized List<String> listFiles(){ArrayList<String> out=new ArrayList<>();collect(root,root,out);return out;}
    private void collect(File base,File f,List<String> out){File[] xs=f.listFiles();if(xs==null)return;for(File x:xs){if(x.getName().equals(".shadow"))continue;if(x.isFile())out.add(base.toPath().relativize(x.toPath()).toString());else collect(base,x,out);}}
    public synchronized String backup(String label)throws IOException{File backupRoot=new File(root,".shadow/backups/"+System.currentTimeMillis()+"-"+(label==null?"change":label.replaceAll("[^A-Za-z0-9_-]","_")));backupRoot.mkdirs();copyTree(root,backupRoot,backupRoot);return backupRoot.getAbsolutePath();}
    private void copyTree(File src,File dst,File backup)throws IOException{File[] xs=src.listFiles();if(xs==null)return;for(File x:xs){if(x.equals(backup)||x.getName().equals(".shadow"))continue;File y=new File(dst,x.getName());if(x.isDirectory()){y.mkdirs();copyTree(x,y,backup);}else Files.copy(x.toPath(),y.toPath());}}
    public String status(){return "Workspace: "+root.getAbsolutePath()+" • files="+fileCount();}
}
