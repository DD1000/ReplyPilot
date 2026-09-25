package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Foreground-only display preloading. Never supplies a send base or AI context. */
final class ConversationHistoryCache {
    static final ExecutorService EXECUTOR=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),work->{
        Thread thread=new Thread(()->{Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);work.run();},"reply-pilot-history-preview");thread.setDaemon(true);return thread;
    });
    private static final Object LOCK=new Object();
    private static final ConversationCachePolicy CACHE=new ConversationCachePolicy();
    private static long revision=1;
    private static int lastAccess=-1;
    private ConversationHistoryCache(){}

    static void invalidate(){synchronized(LOCK){revision++;CACHE.clear();}}
    private static int access(Context c){
        if(!Messages.allowed(c,Manifest.permission.READ_SMS)||!Messages.role(c))return 0;
        return 5|(Messages.allowed(c,Manifest.permission.READ_CONTACTS)?2:0);
    }
    private static long syncAccess(int access){synchronized(LOCK){if(lastAccess!=access){lastAccess=access;revision++;CACHE.clear();}return revision;}}
    private static boolean current(Context c,long expected,int expectedAccess){
        int actual=access(c);long active=syncAccess(actual);
        return PilotApp.foreground&&actual!=0&&actual==expectedAccess&&active==expected;
    }
    /** Recheck this immediately before delivering the async preview to the WebView. */
    static boolean canDeliver(Context c,JSONObject result){return result!=null&&current(c,result.optLong("revision",-1),result.optInt("access",0));}
    private static JSONObject result(long expected,int access,JSONArray pages)throws JSONException{return new JSONObject().put("conversations",pages).put("revision",expected).put("access",access);}

    static JSONObject prefetch(Context context,JSONArray requested)throws JSONException{
        if(requested==null||requested.length()>6)throw new IllegalArgumentException("Preload up to six conversations at a time.");
        LinkedHashSet<Long> threads=new LinkedHashSet<>();for(int i=0;i<requested.length();i++)threads.add(MediaHistoryPolicy.integer(requested.opt(i),true));
        Context c=context.getApplicationContext();int permission=access(c);long expected=syncAccess(permission);JSONArray pages=new JSONArray();
        if(!PilotApp.foreground||permission==0)return result(expected,permission,pages);
        int payloadBytes=0;
        for(long thread:threads){
            if(!current(c,expected,permission))return result(expected,permission,new JSONArray());
            try{
                String encoded;synchronized(LOCK){encoded=revision==expected?CACHE.get(thread,expected,permission,SystemClock.elapsedRealtime()):null;}
                if(encoded==null){
                    // This path only reads bounded provider rows and local delivery
                    // state. No conversation() / Messages.read() / model invocation.
                    JSONObject page=MediaNavigation.latest(c,thread,null);encoded=page.toString();
                    if(encoded==null||encoded.length()>ConversationCachePolicy.MAX_PAGE_BYTES)continue;
                    int bytes=encoded.getBytes(StandardCharsets.UTF_8).length;if(bytes>ConversationCachePolicy.MAX_PAGE_BYTES)continue;
                    if(!current(c,expected,permission))return result(expected,permission,new JSONArray());
                    synchronized(LOCK){if(revision!=expected)return result(expected,permission,new JSONArray());CACHE.put(thread,encoded,bytes,expected,permission,SystemClock.elapsedRealtime());}
                }
                int bytes=encoded.getBytes(StandardCharsets.UTF_8).length;if((long)payloadBytes+bytes>ConversationCachePolicy.MAX_BYTES)continue;
                JSONObject page=new JSONObject(encoded);pages.put(new JSONObject().put("thread",thread).put("page",page));payloadBytes+=bytes;
            }catch(RuntimeException|JSONException unavailable){
                // A deleted chat, revoked permission or busy provider must not
                // discard unrelated previews or become a user-visible error.
                if(!current(c,expected,permission))return result(expected,permission,new JSONArray());
            }
        }
        if(!current(c,expected,permission))pages=new JSONArray();return result(expected,permission,pages);
    }
}
