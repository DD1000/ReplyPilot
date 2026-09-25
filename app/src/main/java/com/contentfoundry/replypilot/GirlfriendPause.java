package com.contentfoundry.replypilot;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Private per-thread pause metadata only; SMS content is never stored here. */
final class GirlfriendPause {
    private static final Set<Long> FAILED=new HashSet<>();
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("girlfriend_pauses",Context.MODE_PRIVATE);}
    private static String key(long thread){return "thread."+thread;}
    private static GirlfriendPolicy.State read(Context c,long thread){
        String raw=prefs(c).getString(key(thread),"");
        if(raw.isEmpty())return new GirlfriendPolicy.State(0,FAILED.contains(thread));
        try{JSONObject stored=new JSONObject(raw);return new GirlfriendPolicy.State(stored.getLong("base"),stored.getBoolean("paused")||FAILED.contains(thread));}
        catch(JSONException invalid){throw new IllegalStateException("The conversation pause could not be read.");}
    }
    private static String json(GirlfriendPolicy.State state){try{return new JSONObject().put("base",state.base()).put("paused",state.paused()).toString();}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    private static void write(Context c,long thread,GirlfriendPolicy.State state){
        if(!prefs(c).edit().putString(key(thread),json(state)).commit()){
            FAILED.add(thread);
            // The first failed commit may have changed memory. Restore a safe
            // paused state and attempt to persist it; never report a successful resume.
            try{prefs(c).edit().putString(key(thread),json(new GirlfriendPolicy.State(state.base(),true))).commit();}catch(RuntimeException ignored){}
            throw new IllegalStateException("The conversation pause could not be saved. Try again.");
        }
        FAILED.remove(thread);
    }
    static boolean onIncoming(Context c,long thread,long base,String body,boolean girlfriendMode){synchronized(PilotApp.SEND_LOCK){
        if(thread<=0||base<=0)return girlfriendMode;
        try{
            GirlfriendPolicy.State old=read(c,thread),next=GirlfriendPolicy.next(old,base,body,girlfriendMode);
            if(!next.equals(old))write(c,thread,next);
            return next.paused();
        }catch(RuntimeException unavailable){FAILED.add(thread);return true;}
    }}
    static boolean isPaused(Context c,long thread){synchronized(PilotApp.SEND_LOCK){
        if(thread<=0)return false;
        try{return read(c,thread).paused();}catch(RuntimeException unavailable){FAILED.add(thread);return true;}
    }}
    static void clear(Context c,long thread){
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        synchronized(PilotApp.SEND_LOCK){write(c,thread,GirlfriendPolicy.resumed(read(c,thread)));}
    }
    static JSONObject snapshot(Context c,long thread){boolean paused=isPaused(c,thread);try{return new JSONObject().put("paused",paused).put("reason",paused?"bedtime":"");}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
}
