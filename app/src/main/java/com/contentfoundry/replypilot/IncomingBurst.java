package com.contentfoundry.replypilot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import org.json.*;
import java.util.*;

/** Private receipt metadata. This lock never takes SEND_LOCK or waits for AI/IO work. */
final class IncomingBurst {
    private static final Object LOCK=new Object();
    private static final Set<Long> IN_FLIGHT=new HashSet<>();
    private static boolean persistenceFailed;
    record Receipt(String address,long token,long wall,long elapsed,int boot){}
    private record Saved(String address,long token,long wall,long elapsed,int boot,long thread,long base,boolean failed){}
    private record State(long token,long base,long remaining,boolean failed){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("incoming_bursts",Context.MODE_PRIVATE);}
    private static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    private static String number(String value){return value==null?"":PhoneNumberUtils.normalizeNumber(value);}
    private static String addressKey(String address){return "address."+address;}
    private static String threadKey(long thread){return "thread."+thread;}
    private static Set<String> pending(SharedPreferences p){return new HashSet<>(p.getStringSet("pending",Collections.emptySet()));}
    private static Saved saved(SharedPreferences p,String address){
        if(address==null||address.isEmpty())return null;
        String raw=p.getString(addressKey(address),null);if(raw==null)return null;
        try{JSONObject value=new JSONObject(raw);return new Saved(address,value.getLong("token"),value.getLong("wall"),value.getLong("elapsed"),value.getInt("boot"),value.optLong("thread"),value.optLong("base"),value.optBoolean("failed"));}
        catch(JSONException invalid){persistenceFailed=true;return null;}
    }
    private static String encode(Saved s){
        try{return new JSONObject().put("token",s.token()).put("wall",s.wall()).put("elapsed",s.elapsed()).put("boot",s.boot()).put("thread",s.thread()).put("base",s.base()).put("failed",s.failed()).toString();}
        catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    private static void commit(SharedPreferences.Editor edit){persistenceFailed=!edit.commit();}
    /** Called synchronously at the validated broadcast boundary, before SEND_LOCK/IO. */
    static Receipt mark(Context c,String origin){
        String address=number(origin);if(!SendPolicy.validAddress(origin)||address.isEmpty())return null;
        long wall=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();int currentBoot=boot(c);
        synchronized(LOCK){
            SharedPreferences p=prefs(c);long token=Math.addExact(p.getLong("sequence",0),1);Saved old=saved(p,address);
            Receipt receipt=new Receipt(address,token,wall,elapsed,currentBoot);
            Saved value=new Saved(address,token,wall,elapsed,currentBoot,old==null?0:old.thread(),0,false);
            Set<String> pending=pending(p);pending.add(address);IN_FLIGHT.add(token);
            SharedPreferences.Editor edit=p.edit().putLong("sequence",token).putString(addressKey(address),encode(value)).putStringSet("pending",pending);
            if(value.thread()>0)edit.putString(threadKey(value.thread()),address);
            commit(edit);return receipt;
        }
    }
    /** Bind only after this receipt's SMS row exists. An older receipt cannot finish a newer burst. */
    static void bind(Context c,Receipt receipt,long thread,long base){
        if(receipt==null||thread<=0||base<=0)return;
        synchronized(LOCK){
            SharedPreferences p=prefs(c);Saved latest=saved(p,receipt.address());if(latest==null)return;
            Set<String> pending=pending(p);Saved next=latest;
            if(BurstPolicy.mayBind(receipt.token(),latest.token())){
                next=new Saved(latest.address(),latest.token(),latest.wall(),latest.elapsed(),latest.boot(),thread,base,false);pending.remove(receipt.address());
            }else if(latest.thread()==0)next=new Saved(latest.address(),latest.token(),latest.wall(),latest.elapsed(),latest.boot(),thread,latest.base(),latest.failed());
            SharedPreferences.Editor edit=p.edit().putString(addressKey(next.address()),encode(next)).putStringSet("pending",pending);
            Saved bound=saved(p,p.getString(threadKey(thread),""));
            if(bound==null||BurstPolicy.mayReplace(bound.token(),next.token()))edit.putString(threadKey(thread),next.address());
            commit(edit);IN_FLIGHT.remove(receipt.token());
        }
    }
    /** Always finish the broadcast, including provider failures; don't block unrelated contacts forever. */
    static void finish(Context c,Receipt receipt){
        if(receipt==null)return;
        synchronized(LOCK){
            IN_FLIGHT.remove(receipt.token());SharedPreferences p=prefs(c);Saved latest=saved(p,receipt.address());
            if(latest!=null&&latest.token()==receipt.token()&&latest.base()==0){
                Saved failed=new Saved(latest.address(),latest.token(),latest.wall(),latest.elapsed(),latest.boot(),latest.thread(),0,true);
                Set<String> pending=pending(p);pending.remove(receipt.address());
                commit(p.edit().putString(addressKey(latest.address()),encode(failed)).putStringSet("pending",pending));
            }
        }
    }
    /** Final automatic submission can also guard an as-yet-unresolved address alias. */
    static void failClosed(){synchronized(LOCK){persistenceFailed=true;}}
    static boolean hasUnbound(Context c){synchronized(LOCK){return persistenceFailed||!IN_FLIGHT.isEmpty();}}
    private static State state(Context c,long thread,String origin){
        if(thread<=0)return new State(-1,0,0,true);
        SharedPreferences p=prefs(c);String direct=number(origin),bound=p.getString(threadKey(thread),"");
        Saved selected=null;
        Set<String> candidates=new HashSet<>();if(!direct.isEmpty())candidates.add(direct);if(!bound.isEmpty())candidates.add(bound);
        // Pending aliases have not reached the SMS provider yet. Compare them with
        // both spellings to cover national numbers versus +country-code senders.
        for(String address:pending(p))if((!direct.isEmpty()&&PhoneNumberUtils.compare(address,direct))||(!bound.isEmpty()&&PhoneNumberUtils.compare(address,bound)))candidates.add(address);
        for(String address:candidates){Saved value=saved(p,address);if(value!=null&&(value.thread()==0||value.thread()==thread)&&(selected==null||value.token()>selected.token()))selected=value;}
        if(persistenceFailed)return new State(-1,0,BurstPolicy.QUIET_MS,true);
        if(selected==null)return new State(0,0,0,false);
        int currentBoot=boot(c);
        long remaining=BurstPolicy.remaining(selected.wall(),selected.elapsed(),selected.boot(),System.currentTimeMillis(),SystemClock.elapsedRealtime(),currentBoot);
        if(selected.base()==0&&!selected.failed())remaining=Math.max(1000,remaining);
        return new State(selected.token(),selected.base(),remaining,selected.failed());
    }
    static long token(Context c,long thread,long base,String address){synchronized(LOCK){return state(c,thread,address).token();}}
    static long token(Context c,long thread,long base){return token(c,thread,base,null);}
    static long remaining(Context c,long thread,long base,String address){synchronized(LOCK){return state(c,thread,address).remaining();}}
    static long remaining(Context c,long thread,long base){return remaining(c,thread,base,null);}
    static boolean current(Context c,long thread,long base,String address){synchronized(LOCK){State s=state(c,thread,address);return BurstPolicy.current(s.token(),s.base(),base,s.failed());}}
    static boolean current(Context c,long thread,long base){return current(c,thread,base,null);}
    static boolean manualCoversLatest(Context c,long thread,long base,String address){synchronized(LOCK){State s=state(c,thread,address);return BurstPolicy.manualCoversLatest(s.token(),s.base(),base,s.failed());}}
    static boolean ready(Context c,long thread,long base,String address){synchronized(LOCK){State s=state(c,thread,address);return BurstPolicy.current(s.token(),s.base(),base,s.failed())&&s.remaining()==0;}}
    static boolean ready(Context c,long thread,long base){return ready(c,thread,base,null);}
    static boolean unchanged(Context c,long thread,long base,String address,long token){synchronized(LOCK){State s=state(c,thread,address);return BurstPolicy.unchanged(token,s.token(),BurstPolicy.current(s.token(),s.base(),base,s.failed()),s.remaining());}}
    static boolean unchanged(Context c,long thread,long base,long token){return unchanged(c,thread,base,null,token);}
    static int jobId(Context c,long thread){synchronized(LOCK){
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        SharedPreferences p=prefs(c);int existing=p.getInt("job."+thread,0);if(existing>0)return existing;
        int next=Math.max(1_000_000,p.getInt("nextJob",1_000_000));if(next==Integer.MAX_VALUE)throw new IllegalStateException("Too many reply jobs.");
        commit(p.edit().putInt("job."+thread,next).putInt("nextJob",next+1));return next;
    }}
    /** The callback only touches JobScheduler; it must never take SEND_LOCK. */
    static boolean scheduleIfCurrent(Context c,long thread,long base,long token,Runnable schedule){return scheduleIfCurrent(c,thread,base,null,token,schedule);}
    static boolean scheduleIfCurrent(Context c,long thread,long base,String address,long token,Runnable schedule){synchronized(LOCK){
        State s=state(c,thread,address);
        if(token!=s.token()||!BurstPolicy.current(s.token(),s.base(),base,s.failed()))return false;
        schedule.run();return true;
    }}
}
