package com.contentfoundry.replypilot;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.PhoneNumberUtils;
import org.json.*;
import java.util.ArrayList;

public final class Sender {
    public static boolean exact(Context c){return c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    public static void reconcile(Context c){synchronized(PilotApp.SEND_LOCK){
        SleepSession.refresh(c);
        Store db=Store.get(c);reconcileLocation(c);
        JSONArray attention=db.query("SELECT a.job FROM autopilot_attention a JOIN jobs j ON j._id=a.job WHERE a.notified=0 AND a.submitted=1 AND j.status IN ('sending','sent') AND j.uri<>''",null);for(int i=0;i<attention.length();i++)Notices.chatAttention(c,attention.optJSONObject(i).optLong("job"));
        // A notification acknowledgment has no timer and never retries after a crash.
        ContentValues deferral=new ContentValues();deferral.put("status","paused");deferral.put("approved",0);deferral.put("note","The acknowledgment was interrupted. Check the conversation before trying again.");
        db.getWritableDatabase().update("jobs",deferral,"attention_kind='delay' AND status='scheduled'",null);
        // An app restart before the draft alert was posted must never resume that timer.
        ContentValues interrupted=new ContentValues();interrupted.put("status","paused");interrupted.put("note","The automatic alert was interrupted. Review this reply and choose a timer.");
        db.getWritableDatabase().update("jobs",interrupted,"status='awaiting_alert'",null);
        if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.SEND_SMS))db.pauseAll("Messaging permissions changed. Restore access, then review and approve again.");
        // Exact-alarm access applies to timers, not new instant replies. Do not
        // invalidate an in-flight instant draft merely because timers are disabled.
        if(!exact(c)){
            JSONArray timed=db.query("SELECT * FROM jobs WHERE status='scheduled'",null);
            for(int i=0;i<timed.length();i++){
                JSONObject job=timed.optJSONObject(i);
                if(instantJob(db,job))continue;
                db.update(job.optLong("_id"),"paused","Precise timer access changed. Restore access, then review and choose a timer again.");cancelTimer(c,job.optLong("_id"));
            }
        }
        ContentValues v=new ContentValues();v.put("status","paused");v.put("approved",0);v.put("note","The timer was missed. Review and approve again.");
        db.getWritableDatabase().update("jobs",v,"status='scheduled' AND due<?",new String[]{""+(System.currentTimeMillis()-SendPolicy.MAX_LATENESS_MS)});
        JSONArray automatic=db.query("SELECT * FROM jobs WHERE auto_send=1 AND status='scheduled'",null);
        for(int i=0;i<automatic.length();i++){
            JSONObject job=automatic.optJSONObject(i);String block=automaticBlock(c,job);
            if(block!=null){db.update(job.optLong("_id"),"paused",block);cancelTimer(c,job.optLong("_id"));}
        }
    }}
    static void reconcileLocation(Context c){synchronized(PilotApp.SEND_LOCK){
        Store db=Store.get(c);JSONArray pending=db.query("SELECT * FROM jobs WHERE location_expires>0 AND status IN ('scheduled','awaiting_alert')",null);
        for(int i=0;i<pending.length();i++){JSONObject job=pending.optJSONObject(i);String note=LocationReplies.block(c,job,Math.max(System.currentTimeMillis(),job.optLong("due")));if(note!=null){db.update(job.optLong("_id"),"paused",note);cancelTimer(c,job.optLong("_id"));}}
    }}
    private static PendingIntent alarm(Context c,long id){return PendingIntent.getBroadcast(c,0,new Intent(c,SendReceiver.class).setData(Uri.parse("replypilot://send/"+id)).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    /** The send-arrow approves this body for the verified person, even during AI work. */
    public static JSONObject sendNow(Context c,long thread,String address,String body,long ignoredBase,int sub,String requestId) throws JSONException {
        SendPolicy.validate(address,body,0);SendPolicy.validateConversation(thread,ignoredBase);
        if(!ManualTakeoverPolicy.requestId(requestId))throw new IllegalArgumentException("Start this send again from the conversation.");
        synchronized(PilotApp.SEND_LOCK){
            if(!PilotApp.foreground||!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Open Reply Pilot with messaging access before sending.");
            String recipient=singleRecipient(c,thread);
            if(!PhoneNumberUtils.compare(recipient,address)||Messages.thread(c,address)!=thread)throw new IllegalStateException("The recipient does not match this conversation. Open it again.");
            Store db=Store.get(c);JSONObject previous=db.query("SELECT * FROM manual_sends WHERE request_id=?",new String[]{requestId}).optJSONObject(0);
            if(previous!=null){
                if(!ManualTakeoverPolicy.sameRequest(thread,recipient,body,sub,previous.optLong("thread"),previous.optString("address"),previous.optString("body"),previous.optInt("sub")))throw new IllegalStateException("This send request was already used for different text. Start a new send.");
                if(previous.optLong("job_id")>0)return manualResult(c,db,previous.optLong("job_id"),thread);
            }
            long base=Messages.latest(c,thread);
            // A stale UI base must not strip location expiry from the exact draft
            // body being sent. Read only this provenance, not its old send authority.
            JSONObject savedDraft=db.query("SELECT body,location_revision,location_expires FROM drafts WHERE thread=?",new String[]{Long.toString(thread)}).optJSONObject(0);
            long takeover=ManualTakeover.claim(c,thread,recipient);
            if(!Messages.allowed(c,Manifest.permission.SEND_SMS))throw new IllegalStateException("Allow SMS sending, then tap Send again. Pilot remains paused for this message.");
            if(!Messages.activeSim(c,sub))throw new IllegalStateException("Select an active SIM in Settings. Pilot remains paused for this message.");
            long due=System.currentTimeMillis();
            if(savedDraft!=null&&body.equals(savedDraft.optString("body"))){String location=LocationReplies.block(c,savedDraft,due);if(location!=null)throw new IllegalStateException(location);}
            // A request ID is linked to its job in one commit, before any carrier call.
            // Never retry a linked job, including failed/unknown carrier outcomes.
            android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();long id;sql.beginTransaction();
            try{
                id=db.queue(thread,recipient,body,base,sub,due);
                if(savedDraft!=null&&body.equals(savedDraft.optString("body"))){ContentValues source=new ContentValues();source.put("location_revision",savedDraft.optLong("location_revision"));source.put("location_expires",savedDraft.optLong("location_expires"));sql.update("jobs",source,"_id=?",new String[]{Long.toString(id)});}
                ContentValues request=new ContentValues();request.put("request_id",requestId);request.put("thread",thread);request.put("address",recipient);request.put("body",body);request.put("sub",sub);request.put("job_id",id);request.put("created",due);
                if(previous==null)sql.insertOrThrow("manual_sends",null,request);else if(sql.update("manual_sends",request,"request_id=? AND job_id=0",new String[]{requestId})!=1)throw new IllegalStateException("This send request was already claimed.");
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            ApprovedLearning.scheduleCapture(c,id,thread,base);
            try{dispatch(c,id);}catch(RuntimeException unavailable){JSONObject interrupted=db.job(id);if(interrupted!=null&&"scheduled".equals(interrupted.optString("status")))db.update(id,"paused","Sending could not start. Open the conversation before trying again.");}
            JSONObject job=db.job(id);
            if(job!=null&&"scheduled".equals(job.optString("status")))db.update(id,"paused","Sending did not start. Check the conversation before trying again.");
            if(job!=null&&("sending".equals(job.optString("status"))||"sent".equals(job.optString("status"))))sql.delete("drafts","thread=? AND base=?",new String[]{Long.toString(thread),Long.toString(base)});
            return manualResult(c,db,id,thread).put("manualRevision",takeover);
        }
    }
    private static JSONObject manualResult(Context c,Store db,long id,long thread)throws JSONException{
        JSONObject job=db.job(id);return new JSONObject().put("id",id).put("status",job==null?"unknown":job.optString("status","unknown"))
            .put("note",job==null?"Check Queue for the send result before trying again.":job.optString("note"))
            .put("job",job==null?JSONObject.NULL:job).put("base",job==null?0:job.optLong("base"))
            .put("latestBase",Messages.latest(c,thread)).put("manualRevision",ManualTakeover.revision(c,thread));
    }
    private static boolean immediateManual(Store db,long id){return db.query("SELECT request_id FROM manual_sends WHERE job_id=? LIMIT 1",new String[]{Long.toString(id)}).length()>0;}
    /** Lightweight authoritative status for the open composer; no inbox/history scan. */
    public static JSONObject sendState(Context c,long thread)throws JSONException{
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        synchronized(PilotApp.SEND_LOCK){
            if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Restore messaging access to check this send.");
            JSONArray jobs=Store.get(c).threadJobs(thread);long base=Messages.latest(c,thread);
            if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Messaging access changed. Open the conversation again.");
            return new JSONObject().put("thread",thread).put("jobs",jobs).put("latestBase",base);
        }
    }
    /** The notification tap approves only the displayed deferral, never a generated body. */
    static JSONObject sendAttentionDelay(Context c,JSONObject action) throws Exception {
        synchronized(PilotApp.SEND_LOCK){
            String token=action.optString("token");action=AttentionActions.row(c,token);
            if(action==null||!"delay".equals(action.optString("action"))||!"running".equals(action.optString("state"))||action.optLong("job_id")!=0)throw new IllegalStateException("This acknowledgment was already used.");
            String blocked=AttentionActions.block(c,action,0,false);if(blocked!=null)throw new IllegalStateException(blocked);
            if(!Messages.allowed(c,Manifest.permission.SEND_SMS))throw new IllegalStateException("Allow SMS sending before using Delay.");
            long thread=action.optLong("thread"),base=action.optLong("base");String address=singleRecipient(c,thread);
            if(!PhoneNumberUtils.compare(address,Messages.address(c,thread,base)))throw new IllegalStateException("The recipient could not be confirmed. Open the conversation.");
            JSONArray sims=Messages.sims(c);int sub=c.getSharedPreferences("settings",0).getInt("sub",-1);if(sub<0&&sims.length()==1)sub=sims.optJSONObject(0).optInt("id");
            if(!Messages.activeSim(c,sub))throw new IllegalStateException("Choose an active SIM in Settings before using Delay.");
            SendPolicy.validate(address,AttentionPolicy.DELAY_TEXT,0);SendPolicy.validateConversation(thread,base);
            Store db=Store.get(c);android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();long id;
            sql.beginTransaction();
            try{
                id=db.queue(thread,address,AttentionPolicy.DELAY_TEXT,base,sub,System.currentTimeMillis());
                ContentValues provenance=new ContentValues();provenance.put("attention_kind","delay");provenance.put("location_revision",0);provenance.put("location_expires",0);
                sql.update("jobs",provenance,"_id=?",new String[]{Long.toString(id)});
                ContentValues link=new ContentValues();link.put("job_id",id);
                if(sql.update("attention_actions",link,"token=? AND action='delay' AND state='running' AND job_id=0",new String[]{token})!=1)throw new IllegalStateException("This acknowledgment was already used.");
                // Manual Delay consumes the same hold too, so enabling the preference
                // later cannot produce a second automatic acknowledgement.
                sql.execSQL("UPDATE reply_holds SET delay_attempted=1 WHERE thread=?",new Object[]{thread});
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            // Linkage is committed before any possible carrier callback or process death.
            dispatch(c,id);JSONObject sent=db.job(id);
            if(sent!=null&&"scheduled".equals(sent.optString("status"))){db.update(id,"paused","Sending did not start. Open the conversation before trying again.");sent=db.job(id);}
            return new JSONObject().put("id",id).put("status",sent==null?"unknown":sent.optString("status","unknown")).put("note",sent==null?"Check Queue before trying again.":sent.optString("note"));
        }
    }
    public static long accept(Context c,long thread,String address,String body,long base,int sub,long delay){
        SendPolicy.validate(address,body,delay);SendPolicy.validateConversation(thread,base);
        synchronized(PilotApp.SEND_LOCK){
            if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.SEND_SMS)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Make Reply Pilot your default SMS app and allow messaging permissions first.");
            if(Messages.thread(c,address)!=thread)throw new IllegalStateException("The recipient does not match this conversation.");
            if(!Messages.activeSim(c,sub))throw new IllegalStateException("Select an active SIM in Settings first.");
            if(Messages.latest(c,thread)!=base)throw new IllegalStateException("This conversation changed. Review the latest message before accepting.");
            String timing=SendPolicy.manualTimingBlock(delay,delay==0||exact(c));if(timing!=null)throw new IllegalStateException(timing);
            if(Store.get(c).query("SELECT _id FROM jobs WHERE thread=? AND status IN ('scheduled','sending','awaiting_alert')",new String[]{""+thread}).length()>0)throw new IllegalStateException("This conversation already has a pending reply. Cancel it first.");
            long due=System.currentTimeMillis()+delay;
            JSONObject savedDraft=Store.get(c).draft(thread,base);
            if(savedDraft!=null&&body.equals(savedDraft.optString("body"))){String locationBlock=LocationReplies.block(c,savedDraft,due);if(locationBlock!=null)throw new IllegalStateException(locationBlock);}
            long id=Store.get(c).queue(thread,address,body,base,sub,due);
            ApprovedLearning.scheduleCapture(c,id,thread,base);
            if(delay==0)try{dispatch(c,id);}catch(RuntimeException e){
                JSONObject job=Store.get(c).job(id);
                if(job!=null&&"scheduled".equals(job.optString("status")))Store.get(c).update(id,"paused","Sending conditions changed. Review the conversation before trying again.");
                throw e;
            }else try{c.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,alarm(c,id));}catch(RuntimeException e){Store.get(c).update(id,"paused","Timer permission changed. Review and approve again.");throw e;}
            return id;
        }
    }
    /** Only called immediately after a real conversation's cloud draft has been saved. */
    public static long scheduleAutomatic(Context c,long thread,long base,long profileRevision,String configRevision,long sleepRevision) throws Exception {
        synchronized(PilotApp.SEND_LOCK){
            Store db=Store.get(c);JSONObject profile=db.relationship(thread),config=CloudConfig.read(c);
            if(!SleepSession.generationAllowed(c,base,sleepRevision))return 0;
            if(!IncomingBurst.ready(c,thread,base,Messages.address(c,thread,base)))return 0;
            DelayPolicy.Choice timing=SleepSession.delayChoice(c,profile);
            long minimumSeconds=timing.minimumSeconds();
            String block=SendPolicy.automaticBlock(profile.optBoolean("cloudEnabled"),profile.optBoolean("autoDraft"),profile.optBoolean("autoSend"),c.getSharedPreferences("settings",0).getBoolean("autoDraft",true),profileRevision,profile.optLong("revision"),configRevision,config==null?null:config.optString("revision"),Notices.canAlert(c),timing.instant()||exact(c),minimumSeconds);
            if(block!=null)throw new IllegalStateException(block);
            if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.SEND_SMS)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Restore the default SMS role and messaging permissions before automatic sending.");
            JSONArray history=Messages.history(c,thread);JSONObject incoming=history.optJSONObject(history.length()-1);
            if(incoming==null||incoming.optLong("_id")!=base||incoming.optInt("type")!=Telephony.Sms.MESSAGE_TYPE_INBOX)throw new IllegalStateException("Automatic sending needs a current incoming text. Review this reply instead.");
            String address=singleRecipient(c,thread);
            if(!IncomingBurst.ready(c,thread,base,address))return 0;
            if(!PhoneNumberUtils.compare(address,incoming.optString("address")))throw new IllegalStateException("The recipient could not be confirmed. Review this reply instead.");
            JSONObject draft=db.draft(thread,base);
            if(draft==null||!draft.optString("engine").startsWith("OpenAI"))throw new IllegalStateException("Generate a new AI reply to send automatically.");
            String silence=AutomaticReplies.reason(c,thread,base,draft.optString("body"),0);
            if(silence!=null){AutomaticReplies.record(c,thread,base,silence);return 0;}
            SendPolicy.validate(address,draft.optString("body"),minimumSeconds*1000);
            JSONArray sims=Messages.sims(c);int sub=c.getSharedPreferences("settings",0).getInt("sub",-1);if(sub<0&&sims.length()==1)sub=sims.optJSONObject(0).optInt("id");
            if(!Messages.activeSim(c,sub))throw new IllegalStateException("Choose an active SIM in Settings before automatic sending.");
            if(db.query("SELECT _id FROM jobs WHERE thread=? AND status IN ('scheduled','awaiting_alert')",new String[]{""+thread}).length()>0)throw new IllegalStateException("This conversation already has a pending reply. Review or cancel it first.");
            // Draw exactly once, after preflight. Never reroll to fit Sleep's cutoff.
            if(!SleepSession.generationAllowed(c,base,sleepRevision))return 0;
            long delaySeconds=DelayOptions.choose(timing),delay=delaySeconds*1000;
            // Persist the selected duration and due; alerts, refreshes, and dispatch
            // use these stored values rather than drawing a new delay.
            long due=System.currentTimeMillis()+delay;
            if(SleepSession.queueBlock(c,base,due,sleepRevision)!=null)return 0;
            String locationBlock=LocationReplies.block(c,draft,due);if(locationBlock!=null)throw new IllegalStateException(locationBlock);
            return db.queueAutomatic(thread,address,draft.optString("body"),base,sub,due,profileRevision,configRevision,incoming.optString("body"),sleepRevision,delaySeconds);
        }
    }
    static long scheduleAutomaticMms(Context c,AutopilotMms.Source source,AutopilotPolicy.Reply reply,long profileRevision,String configRevision)throws Exception{synchronized(PilotApp.SEND_LOCK){
        HistoryLearning.requireReady(c);Store db=Store.get(c);long thread=source.thread();JSONObject profile=db.relationship(thread),config=CloudConfig.read(c);
        long delay=PersonProfile.autoDelay(profile.optLong("autoDelay"));
        String denied=SendPolicy.automaticBlock(profile.optBoolean("cloudEnabled"),profile.optBoolean("autoDraft"),profile.optBoolean("autoSend"),c.getSharedPreferences("settings",0).getBoolean("autoDraft",true),profileRevision,profile.optLong("revision"),configRevision,config==null?null:config.optString("revision"),Notices.canAlert(c),delay==0||exact(c),delay);
        if(denied!=null)throw new IllegalStateException(denied);
        if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS)||!Messages.allowed(c,Manifest.permission.SEND_SMS))throw new IllegalStateException("Restore messaging access.");
        if(!AutopilotMms.same(source,AutopilotMms.capture(c,thread,source.id(),source.receipt(),0))||AutopilotMms.block(c,source,0)!=null)return 0;
        if(!ReplyReadiness.current(c,thread,profile.optString("samples")).eligible())return 0;
        if(PlanSafety.commitment(reply.body())||RequestSafety.unsuitableReply(reply.body()))throw new IllegalStateException("This reply needs your input.");
        SendPolicy.validate(source.address(),reply.body(),delay*1000);
        JSONArray sims=Messages.sims(c);int sub=c.getSharedPreferences("settings",0).getInt("sub",-1);if(sub<0&&sims.length()==1)sub=sims.optJSONObject(0).optInt("id");
        if(!Messages.activeSim(c,sub))throw new IllegalStateException("Choose an active SIM before Autopilot can send.");
        if(db.query("SELECT _id FROM jobs WHERE thread=? AND status IN ('scheduled','awaiting_alert')",new String[]{Long.toString(thread)}).length()>0)return 0;
        android.database.sqlite.SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
        try{
            long id=db.queueAutomatic(thread,source.address(),reply.body(),source.smsId(),sub,System.currentTimeMillis()+delay*1000,profileRevision,configRevision,source.text(),SleepSession.revision(c),delay);
            AutopilotMms.save(c,id,source);if(reply.attentionNeeded())db.attention(id,thread,reply.attentionReason());
            sql.setTransactionSuccessful();return id;
        }finally{sql.endTransaction();}
    }}
    static void activateAutomatic(Context c,long id){synchronized(PilotApp.SEND_LOCK){
        Store db=Store.get(c);JSONObject job=db.job(id);
        if(job==null||job.optInt("auto_send")!=1||!"awaiting_alert".equals(job.optString("status")))throw new IllegalStateException("This automatic reply is no longer available.");
        String sleep=automaticBoundaryBlock(c,job);if(sleep!=null){holdAutomatic(c,db,job,sleep);return;}
        String block=automaticBlock(c,job);
        if(block!=null){String changed=automaticBoundaryBlock(c,job);if(changed!=null){holdAutomatic(c,db,job,changed);return;}if(db.replyDecision(job.optLong("thread"),job.optLong("base"))!=null)return;throw new IllegalStateException(block);}
        if(instantJob(db,job)){
            // Refresh the zero-delay deadline in case the wall clock changed while
            // posting the alert. No alarm is created for instant replies.
            ContentValues ready=new ContentValues();ready.put("status","scheduled");ready.put("due",System.currentTimeMillis());ready.put("note","");
            db.getWritableDatabase().update("jobs",ready,"_id=?",new String[]{""+id});
            dispatch(c,id);
            JSONObject result=db.job(id);if(result!=null&&db.replyDecision(job.optLong("thread"),job.optLong("base"))==null)Notices.instantOutcome(c,id,result.optString("status"),result.optString("note"));
            return;
        }
        db.update(id,"scheduled","");
        try{c.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,job.optLong("due"),alarm(c,id));}
        catch(RuntimeException e){db.update(id,"paused","The automatic timer could not start. Review this reply instead.");Notices.cancelScheduled(c,id);throw new IllegalStateException("The automatic timer could not start. Review this reply instead.");}
    }}
    private static boolean instantJob(Store db,JSONObject job){
        if(job.optInt("auto_send")!=1)return false;
        if(job.optLong("auto_delay",-1)>=0)return job.optLong("auto_delay")==0;
        try{return DelayOptions.profile(db.relationship(job.optLong("thread"))).instant();}
        catch(JSONException unavailable){return false;}
    }
    static String singleRecipient(Context c,long thread){
        String ids="";
        Uri threads=Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple","true").build();
        try(Cursor cursor=c.getContentResolver().query(threads,new String[]{"recipient_ids"},"_id=?",new String[]{""+thread},null)){
            if(cursor!=null&&cursor.moveToFirst())ids=cursor.getString(0);
        }
        if(ids==null||!ids.trim().matches("[0-9]+"))throw new IllegalStateException("Automatic replies support conversations with one SMS recipient. Review this reply instead.");
        String address="";
        try(Cursor cursor=c.getContentResolver().query(Uri.parse("content://mms-sms/canonical-address/"+ids.trim()),new String[]{"address"},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())address=cursor.getString(0);
        }
        if(!SendPolicy.validAddress(address))throw new IllegalStateException("The SMS recipient could not be confirmed. Review this reply instead.");
        return address;
    }
    private static String automaticBoundaryBlock(Context c,JSONObject job){
        if(ManualTakeover.blocked(c,job.optLong("thread")))return ManualTakeoverPolicy.WAITING;
        if(!HistoryLearning.ready(c))return "Pilot is learning your conversations. This reply was not sent.";
        if(AutopilotMms.saved(c,job.optLong("_id"))!=null)return AutopilotMms.block(c,job);
        if(MmsAttachments.hasStaged(c,job.optLong("thread")))return "You are preparing an attachment. Automatic replies are paused for this conversation.";
        String location=LocationReplies.block(c,job,System.currentTimeMillis());if(location!=null)return location;
        String sleep=SleepSession.jobBlock(c,job);if(sleep!=null)return sleep;
        if(MmsDownloads.isPending()||IncomingBurst.hasUnbound(c)||!IncomingBurst.ready(c,job.optLong("thread"),job.optLong("base"),job.optString("address")))return "New messages are arriving. This old reply was stopped so the complete conversation can be considered.";
        return null;
    }
    private static String automaticBlock(Context c,JSONObject job){
        try{
            String sleep=automaticBoundaryBlock(c,job);if(sleep!=null)return sleep;
            boolean mms=AutopilotMms.saved(c,job.optLong("_id"))!=null;
            if(!mms&&Messages.allowed(c,Manifest.permission.READ_SMS)){
                String silence=AutomaticReplies.reason(c,job.optLong("thread"),job.optLong("base"),job.optString("body"),job.optLong("_id"));
                if(silence!=null){AutomaticReplies.record(c,job.optLong("thread"),job.optLong("base"),silence);return AutomaticReplyPolicy.message(silence);}
            }
            JSONObject profile=Store.get(c).relationship(job.optLong("thread")),config=CloudConfig.read(c);
            long delaySeconds=job.optLong("auto_delay",-1)>=0?job.optLong("auto_delay"):DelayOptions.profile(profile).minimumSeconds();
            String block=SendPolicy.automaticBlock(profile.optBoolean("cloudEnabled"),profile.optBoolean("autoDraft"),profile.optBoolean("autoSend"),c.getSharedPreferences("settings",0).getBoolean("autoDraft",true),job.optLong("profile_revision"),profile.optLong("revision"),job.optString("config_revision"),config==null?null:config.optString("revision"),Notices.canAlert(c),delaySeconds==0||exact(c),delaySeconds);
            if(block!=null)return block;
            if(!Messages.allowed(c,Manifest.permission.READ_SMS))return "Restore messaging permissions before automatic sending.";
            JSONObject draft=Store.get(c).draft(job.optLong("thread"),job.optLong("base"));
            if(!mms&&(draft==null||!job.optString("body").equals(draft.optString("body"))))return "The draft changed. Review and choose a timer again.";
            if(mms&&!ReplyReadiness.current(c,job.optLong("thread"),profile.optString("samples")).eligible())return "Autopilot needs more history for this person.";
            if(!job.optString("address").equals(singleRecipient(c,job.optLong("thread"))))return "The recipient changed. Review this reply instead.";
            return null;
        }catch(Exception e){return "Automatic reply settings are unavailable. Review this reply instead.";}
    }
    private static void cancelTimer(Context c,long id){try{c.getSystemService(AlarmManager.class).cancel(alarm(c,id));}finally{Notices.cancelScheduled(c,id);}}
    public static void cancelAutomaticForThread(Context c,long thread,String note){pauseAutomatic(c,thread,note);}
    public static void silenceAutomaticForThread(Context c,long thread,String note){
        synchronized(PilotApp.SEND_LOCK){
            JSONArray jobs=Store.get(c).query("SELECT _id FROM jobs WHERE thread=? AND auto_send=1 AND status IN ('scheduled','awaiting_alert')",new String[]{Long.toString(thread)});
            pauseAutomatic(c,thread,note);
            for(int i=0;i<jobs.length();i++)Notices.cancelSilentReply(c,jobs.optJSONObject(i).optLong("_id"));
        }
    }
    static void pauseAutomaticForSleep(Context c,String note){synchronized(PilotApp.SEND_LOCK){
        // Sleep changes invalidate background requests through their own persisted
        // revision. They must not invalidate an explicitly requested manual draft.
        JSONArray pending=Store.get(c).query("SELECT _id FROM jobs WHERE auto_send=1 AND status IN ('scheduled','awaiting_alert')",null);
        for(int i=0;i<pending.length();i++){
            long id=pending.optJSONObject(i).optLong("_id");Store.get(c).update(id,"paused",note);
            try{cancelTimer(c,id);}catch(RuntimeException unavailable){/* Revision checks still stop this alarm. */}
            try{Notices.cancelSilentReply(c,id);}catch(RuntimeException unavailable){/* Cleanup is best effort. */}
        }
    }}
    public static void pauseAutomatic(Context c,String note){synchronized(PilotApp.SEND_LOCK){Store.get(c).invalidateGeneration();pauseAutomatic(c,0,note);}}
    private static void pauseAutomatic(Context c,long thread,String note){synchronized(PilotApp.SEND_LOCK){
        Store db=Store.get(c);JSONArray jobs=db.query("SELECT _id FROM jobs WHERE auto_send=1 AND status IN ('scheduled','awaiting_alert')"+(thread>0?" AND thread=?":""),thread>0?new String[]{""+thread}:null);
        for(int i=0;i<jobs.length();i++){long id=jobs.optJSONObject(i).optLong("_id");db.update(id,"paused",note);cancelTimer(c,id);}
    }}
    static void cancelTakeoverTimers(Context c,JSONArray jobs){
        for(int i=0;i<jobs.length();i++)try{cancelTimer(c,jobs.optJSONObject(i).optLong("_id"));}catch(RuntimeException ignored){}
    }
    public static boolean cancel(Context c,long id){synchronized(PilotApp.SEND_LOCK){boolean cancelled=Store.get(c).cancel(id);if(cancelled)cancelTimer(c,id);return cancelled;}}
    public static void dispatch(Context c,long id){
        boolean changed=false;
        try{synchronized(PilotApp.SEND_LOCK){
        Store db=Store.get(c);JSONObject j=db.job(id);if(j==null)return;
        String state=j.optString("status");if(!"scheduled".equals(state))return;
        long thread=j.optLong("thread"),due=j.optLong("due"),now=System.currentTimeMillis();
        if(now<due)return;
        boolean automatic=j.optInt("auto_send")==1,manual=immediateManual(db,id),sourceMms=automatic&&AutopilotMms.saved(c,id)!=null;
        String sleep=automatic?automaticBoundaryBlock(c,j):null;
        if(sleep!=null){holdAutomatic(c,db,j,sleep);changed=true;return;}
        String block=AttentionActions.delayBlock(c,j);
        if(block==null)block=LocationReplies.block(c,j,now);
        if(block==null&&automatic)block=automaticBlock(c,j);
        if(block==null){long latest=Messages.latest(c,thread);block=SendPolicy.block(state,automatic||j.optInt("approved")==1,due,now,manual||sourceMms?latest:j.optLong("base"),latest,Messages.role(c),Messages.allowed(c,Manifest.permission.SEND_SMS));}
        if(block==null&&manual&&(!Messages.allowed(c,Manifest.permission.READ_SMS)||!PhoneNumberUtils.compare(j.optString("address"),singleRecipient(c,thread))))block="The recipient or message access changed. Reopen this conversation.";
        if(block==null&&!Messages.activeSim(c,j.optInt("sub")))block="The selected SIM is unavailable. Review and approve again.";
        if(block!=null&&automatic){String gate=automaticBoundaryBlock(c,j);if(gate!=null){holdAutomatic(c,db,j,gate);changed=true;return;}}
        if(block!=null){db.update(id,"paused",block);changed=true;cancelTimer(c,id);if(!(automatic&&db.replyDecision(thread,j.optLong("base"))!=null)&&!Notices.instantOutcome(c,id,"paused",block))Notices.show(c,(int)id+50000,"Reply paused",block,thread);return;}
        try{
            SmsManager manager=c.getSystemService(SmsManager.class).createForSubscriptionId(j.optInt("sub"));
            ArrayList<String> parts=manager.divideMessage(j.optString("body"));
            if(automatic){String gate=automaticBoundaryBlock(c,j);if(gate!=null){holdAutomatic(c,db,j,gate);changed=true;return;}}
            String attention=AttentionActions.delayBlock(c,j);if(attention!=null){holdAutomatic(c,db,j,attention);changed=true;return;}
            // Claim before the carrier call: a duplicate alarm can never submit twice.
            db.update(id,"sending","Waiting for the carrier. Do not resend while the outcome is unknown.");
            changed=true;
            Notices.cancelScheduled(c,id);
            ContentValues v=new ContentValues();v.put("address",j.optString("address"));v.put("body",j.optString("body"));v.put("date",now);v.put("read",1);v.put("seen",1);v.put("type",Telephony.Sms.MESSAGE_TYPE_OUTBOX);v.put("thread_id",thread);v.put("sub_id",j.optInt("sub"));v.put("status",Telephony.Sms.STATUS_PENDING);
            Uri uri=c.getContentResolver().insert(Telephony.Sms.CONTENT_URI,v);if(uri==null)throw new IllegalStateException("Could not save the outgoing message.");
            ContentValues job=new ContentValues();job.put("uri",uri.toString());job.put("parts",parts.size());job.put("delivery_status","pending");job.put("delivered_at",0);job.put("sms_date",now);db.getWritableDatabase().update("jobs",job,"_id=?",new String[]{""+id});
            ArrayList<PendingIntent> sent=new ArrayList<>(),delivered=new ArrayList<>();
            for(int i=0;i<parts.size();i++){
                sent.add(PendingIntent.getBroadcast(c,0,new Intent(c,SentReceiver.class).setData(Uri.parse("replypilot://sent/"+id+"/"+i)).putExtra("id",id).putExtra("part",i),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
                // Android must fill in the report PDU/format. Component, action and
                // per-part URI are fixed; no mutable extras identify the job.
                delivered.add(PendingIntent.getBroadcast(c,0,new Intent(c,DeliveryReceiver.class).setAction(DeliveryReceiver.ACTION).setData(Uri.parse("replypilot://delivery/"+id+"/"+i)),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE));
            }
            // Provider insertion and message splitting can be slow. Check fresh
            // wall/monotonic deadlines again immediately before carrier submission.
            if(automatic){String gate=automaticBoundaryBlock(c,j);if(gate!=null){discardUnsubmittedProviderRow(c,db,db.job(id));holdAutomatic(c,db,db.job(id),gate);changed=true;return;}}
            String locationGate=LocationReplies.block(c,j,System.currentTimeMillis());
            if(locationGate!=null){discardUnsubmittedProviderRow(c,db,db.job(id));holdAutomatic(c,db,db.job(id),locationGate);changed=true;return;}
            String attentionGate=AttentionActions.delayBlock(c,db.job(id));
            if(attentionGate!=null){discardUnsubmittedProviderRow(c,db,db.job(id));holdAutomatic(c,db,db.job(id),attentionGate);changed=true;return;}
            if(manual&&(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS)||!Messages.allowed(c,Manifest.permission.SEND_SMS)||!Messages.activeSim(c,j.optInt("sub"))||!PhoneNumberUtils.compare(j.optString("address"),singleRecipient(c,thread)))){
                discardUnsubmittedProviderRow(c,db,db.job(id));db.update(id,"paused","Messaging access or the recipient changed before sending. Reopen this conversation.");changed=true;return;
            }
            manager.sendMultipartTextMessage(j.optString("address"),null,parts,sent,delivered);
            try{db.attentionSubmitted(id);Notices.chatAttention(c,id);}catch(RuntimeException notificationUnavailable){/* Carrier submission is already in progress. */}
        }catch(Exception e){String note="The send did not complete. Check the conversation before trying again; no automatic retry.";db.update(id,"failed",note);db.deliveryUnknown(id);updateDeliveryProvider(c,db.job(id));changed=true;if(!Notices.instantOutcome(c,id,"failed",note))Notices.show(c,(int)id+50000,"Chat needs attention","The carrier send could not be completed.",thread);}
        }}finally{if(changed)MessageChanges.publish();}
    }
    private static void holdAutomatic(Context c,Store db,JSONObject job,String note){
        long id=job.optLong("_id");db.update(id,"paused",note);
        try{cancelTimer(c,id);}catch(RuntimeException unavailable){/* The job is already paused. */}
        try{Notices.cancelSilentReply(c,id);}catch(RuntimeException unavailable){/* Cleanup is best effort. */}
        MessageChanges.publish();
    }
    private static void discardUnsubmittedProviderRow(Context c,Store db,JSONObject job){
        if(job==null)return;
        // This path runs only before SmsManager was called. Remove only the exact
        // row just inserted, leaving the saved draft available for manual sending.
        try{
            int removed=c.getContentResolver().delete(Uri.parse(job.optString("uri")),"thread_id=? AND address=? AND body=? AND date=?",new String[]{Long.toString(job.optLong("thread")),job.optString("address"),job.optString("body"),Long.toString(job.optLong("sms_date"))});
            if(removed==1){ContentValues clear=new ContentValues();clear.put("uri","");clear.put("parts",0);clear.put("sms_date",0);db.getWritableDatabase().update("jobs",clear,"_id=?",new String[]{Long.toString(job.optLong("_id"))});}
            else{ContentValues unsent=new ContentValues();unsent.put("type",Telephony.Sms.MESSAGE_TYPE_FAILED);unsent.put("status",Telephony.Sms.STATUS_NONE);updateMessageProvider(c,job,unsent);}
        }catch(Exception ignored){ContentValues unsent=new ContentValues();unsent.put("type",Telephony.Sms.MESSAGE_TYPE_FAILED);unsent.put("status",Telephony.Sms.STATUS_NONE);updateMessageProvider(c,job,unsent);}
        ContentValues values=new ContentValues();values.put("delivery_status","none");db.getWritableDatabase().update("jobs",values,"_id=?",new String[]{Long.toString(job.optLong("_id"))});
    }
    public static void receipt(Context c,long id,int part,int result){
        boolean changed=false;
        try{synchronized(PilotApp.SEND_LOCK){
        Store db=Store.get(c);JSONObject j=db.job(id);if(j==null||part<0||part>=j.optInt("parts"))return;
        ContentValues r=new ContentValues();r.put("job",id);r.put("part",part);r.put("result",result);db.getWritableDatabase().insertWithOnConflict("receipts",null,r,android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE);
        JSONArray receipts=db.query("SELECT * FROM receipts WHERE job=?",new String[]{""+id});boolean failure=false;for(int i=0;i<receipts.length();i++)failure|=receipts.optJSONObject(i).optInt("result")!=Activity.RESULT_OK;
        if(!failure&&receipts.length()<j.optInt("parts"))return;
        boolean confirmed="delivered".equals(j.optString("delivery_status"));
        String state=DeliveryPolicy.receiptStatus(failure,j.optString("delivery_status")),note=DeliveryPolicy.receiptNote(failure,j.optString("delivery_status"));
        db.update(id,state,note);
        try{if("sent".equals(state))db.attentionSubmitted(id);Notices.chatAttention(c,id);}catch(RuntimeException notificationUnavailable){/* Do not discard a carrier receipt. */}
        if(IncomingBurst.manualCoversLatest(c,j.optLong("thread"),j.optLong("base"),j.optString("address")))db.clearReplyHoldForManual(db.job(id));
        if(failure&&!confirmed)db.deliveryUnknown(id);
        changed=true;
        ContentValues sentValues=new ContentValues();sentValues.put("type","failed".equals(state)?Telephony.Sms.MESSAGE_TYPE_FAILED:Telephony.Sms.MESSAGE_TYPE_SENT);updateMessageProvider(c,j,sentValues);
        if(failure)updateDeliveryProvider(c,db.job(id));
        if(!Notices.instantOutcome(c,id,confirmed?"delivered":state,note))Notices.show(c,(int)id+50000,confirmed?"Reply delivered":failure?"Reply failed":"Reply sent",note,j.optLong("thread"));
        }}finally{if(changed)MessageChanges.publish();}
    }
    public static void deliveryReceipt(Context c,long id,int part,DeliveryPolicy.Report report){
        boolean changed=false;
        try{synchronized(PilotApp.SEND_LOCK){
            JSONObject job=Store.get(c).delivery(id,part,report,System.currentTimeMillis());
            if(job==null)return;
            try{if("sent".equals(job.optString("status")))Store.get(c).attentionSubmitted(id);Notices.chatAttention(c,id);}catch(RuntimeException notificationUnavailable){/* Delivery remains authoritative. */}
            if(IncomingBurst.manualCoversLatest(c,job.optLong("thread"),job.optLong("base"),job.optString("address")))Store.get(c).clearReplyHoldForManual(job);
            changed=true;updateDeliveryProvider(c,job);
            // Complete delivery also settles a missing sent callback, without
            // retrying or generating another phone notification.
        }}finally{if(changed)MessageChanges.publish();}
    }
    private static void updateDeliveryProvider(Context c,JSONObject job){
        if(job==null||"none".equals(job.optString("delivery_status","none")))return;
        ContentValues values=new ContentValues();values.put("status",DeliveryPolicy.providerStatus(job.optString("delivery_status")));
        if("delivered".equals(job.optString("delivery_status"))&&"sent".equals(job.optString("status")))values.put("type",Telephony.Sms.MESSAGE_TYPE_SENT);
        updateMessageProvider(c,job,values);
    }
    private static void updateMessageProvider(Context c,JSONObject job,ContentValues values){
        try{
            // A delayed callback must never alter a newer SMS that reused this ID.
            // Legacy jobs without a captured provider timestamp still update Queue.
            if(job==null)return;
            if(!Messages.role(c)||job.optLong("sms_date")<=0||job.optLong("thread")<=0)return;
            Uri uri=Uri.parse(job.optString("uri"));
            if(!"content".equals(uri.getScheme())||!"sms".equals(uri.getAuthority())||uri.getPathSegments().size()!=1||!uri.getLastPathSegment().matches("[0-9]+"))return;
            c.getContentResolver().update(uri,values,"thread_id=? AND address=? AND body=? AND date=?",new String[]{job.optString("thread"),job.optString("address"),job.optString("body"),job.optString("sms_date")});
        }catch(RuntimeException unavailable){/* The private job metadata remains authoritative. */}
    }
}
