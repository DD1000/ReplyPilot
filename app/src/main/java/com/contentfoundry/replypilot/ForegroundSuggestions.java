package com.contentfoundry.replypilot;

import android.content.Context;
import java.util.LinkedHashSet;
import org.json.*;

/** Visible-chat suggestions are drafts, never automatic-send authorization. */
final class ForegroundSuggestions {
    private static final LinkedHashSet<String> attempted=new LinkedHashSet<>();
    static boolean enabled(Context c){return PilotApp.foreground&&c.getSharedPreferences("settings",0).getBoolean("inAppSuggestions",true);}
    static boolean eligible(Context c,long thread,long base)throws Exception{
        if(!enabled(c)||!HistoryLearning.ready(c)||base<=0||MmsDownloads.isPending()||MmsAttachments.hasStaged(c,thread)||MmsAttachments.hasSentForBase(c,thread,base)||Messages.latest(c,thread)!=base)return false;
        if(ManualTakeover.blocked(c,thread))return false;
        Store db=Store.get(c);JSONObject profile=db.relationship(thread);
        if(!profile.optBoolean("cloudEnabled")||CloudConfig.read(c)==null||db.draft(thread,base)!=null||db.replyDecision(thread,base)!=null||profile.optBoolean("autoSend"))return false;
        JSONArray history=Messages.history(c,thread);JSONObject latest=history.optJSONObject(history.length()-1);
        if(latest==null||latest.optLong("_id")!=base||latest.optInt("type")!=1)return false;
        if(!IncomingBurst.ready(c,thread,base,Messages.address(c,thread,base)))return false;
        return db.query("SELECT _id FROM jobs WHERE thread=? AND status IN ('scheduled','sending','awaiting_alert')",new String[]{Long.toString(thread)}).length()==0;
    }
    static void generate(Context c,long thread,long base)throws Exception{
        synchronized(PilotApp.SEND_LOCK){
            if(!eligible(c,thread,base))return;
            String key=thread+":"+base;
            if(!attempted.add(key))return;while(attempted.size()>128)attempted.remove(attempted.iterator().next());
        }
        JSONObject profile=Store.get(c).relationship(thread);
        CloudDrafts.suggest(c,thread,base,PersonProfile.effectiveTone(profile.optString("tone"),c.getSharedPreferences("settings",0).getString("tone","Natural")));
    }
}
