package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.os.SystemClock;
import org.json.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local preparation only. No model call, network request, draft or send on warm-up. */
public final class RecentContext {
    public record Snapshot(long latestId,long preparedAt,List<ReplyPrompt.Message> messages) {
        public JSONObject metadata() throws JSONException {
            int sent=0;for(ReplyPrompt.Message message:messages)if(message.type()==2)sent++;
            return new JSONObject().put("messageCount",messages.size()).put("sentCount",sent).put("limit",CloudPrompt.RECENT_LIMIT);
        }
    }
    private static final Map<Long,Snapshot> cache=new LinkedHashMap<>(64,.75f,true);
    private static final ExecutorService preparation=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean warming=new AtomicBoolean();

    public static Snapshot refresh(Context context,long thread) {
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        if(!Messages.allowed(context,Manifest.permission.READ_SMS)){clear();return new Snapshot(0,SystemClock.elapsedRealtime(),List.of());}
        long latest=Messages.latest(context,thread);
        Snapshot result=new Snapshot(latest,SystemClock.elapsedRealtime(),List.copyOf(ReplyAgent.promptHistory(Messages.recentContext(context,thread))));
        synchronized(cache){cache.put(thread,result);while(cache.size()>64)cache.remove(cache.keySet().iterator().next());}
        return result;
    }
    public static Snapshot forConversation(Context context,long thread) throws JSONException {
        if(!Messages.allowed(context,Manifest.permission.READ_SMS)){clear();return new Snapshot(0,SystemClock.elapsedRealtime(),List.of());}
        if(!context.getSharedPreferences("settings",0).getBoolean("matchMyStyle",true)||!Store.get(context).relationship(thread).optBoolean("cloudEnabled")){
            forget(thread);return new Snapshot(0,SystemClock.elapsedRealtime(),List.of());
        }
        long latest=Messages.latest(context,thread);Snapshot result;
        synchronized(cache){result=cache.get(thread);}
        if(result!=null&&result.latestId()==latest&&SystemClock.elapsedRealtime()-result.preparedAt()<15000)return result;
        return refresh(context,thread);
    }
    public static void clear(){synchronized(cache){cache.clear();}}
    public static void forget(long thread){synchronized(cache){cache.remove(thread);}}
    public static void prepareEnabled(Context context) {
        Context app=context.getApplicationContext();
        if(!Messages.allowed(app,Manifest.permission.READ_SMS)||!app.getSharedPreferences("settings",0).getBoolean("matchMyStyle",true)){clear();return;}
        if(!warming.compareAndSet(false,true))return;
        preparation.execute(()->{
            try{
                JSONArray people=Store.get(app).query("SELECT thread FROM relationships WHERE cloud_enabled=1 ORDER BY thread",null);
                for(int i=0;i<people.length();i++){
                    if(!PilotApp.foreground||!Messages.allowed(app,Manifest.permission.READ_SMS)||!app.getSharedPreferences("settings",0).getBoolean("matchMyStyle",true))break;
                    long thread=people.optJSONObject(i).optLong("thread");
                    if(Store.get(app).relationship(thread).optBoolean("cloudEnabled"))refresh(app,thread);
                }
            }catch(Exception ignored){
                // Revoked permission or a busy SMS provider can be retried when a chat opens.
            }finally{warming.set(false);}
        });
    }
}
