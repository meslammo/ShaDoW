package com.shadow.mobile;

import android.content.Context;
import java.io.*;

/** MOD-43: project workspace state visible to the Development Agent. */
public final class ShadowWorkspace {
    private final File root;
    public ShadowWorkspace(Context c){root=new File(c.getFilesDir(),"shadow_workspace");root.mkdirs();}
    public File root(){return root;}
    public int fileCount(){int[] n={0};walk(root,n);return n[0];}
    private void walk(File f,int[] n){File[] xs=f.listFiles();if(xs==null)return;for(File x:xs){if(x.getName().equals(".shadow"))continue;if(x.isFile())n[0]++;else walk(x,n);}}
    public String status(){return "Workspace: "+root.getAbsolutePath()+" • files="+fileCount();}
}
