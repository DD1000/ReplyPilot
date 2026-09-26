package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import org.json.*;
import java.util.*;

/** A live downloaded MMS is a separate automatic source, never an invented SMS base. */
final class AutopilotMms {
    record Source(long thread,long id,long date,String fingerprint,String address,long smsId,String smsSignature,long manualRevision,long receipt,String text,boolean textOnly) {}
    private AutopilotMms(){}
    static void onDownloaded(Context c,long thread,long id,long receipt){
        if(receipt<=0||thread<=0||id<=0)return;
        try{synchronized(PilotApp.SEND_LOCK){
            JSONObject profile=Store.get(c).relationship(thread);
            if(!enabled(c,profile)||ManualTakeover.blocked(c,thread))return;
            capture(c,thread,id,receipt,0); // Never replace a newer SMS job with an older completed download.
            Sender.cancelAutomaticForThread(c,thread,"A new message arrived. Preparing a reply to the whole conversation.");
            DraftJob.scheduleMms(c,thread,id,receipt,BurstPolicy.QUIET_MS);
        }}catch(Exception unavailable){/* Only the live completed receipt can create work. */}
    }
    static boolean enabled(Context c,JSONObject p){return PersonProfile.automaticSendAllowed(p.optBoolean("cloudEnabled"),p.optBoolean("autoDraft"),p.optBoolean("autoSend"),c.getSharedPreferences("settings",0).getBoolean("autoDraft",true));}
    static Source capture(Context c,long thread,long id,long receipt,long ownJob)throws JSONException{
        if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Restore messaging access.");
        JSONObject message=MediaNavigation.exact(c,"mms",id,thread),latest=MediaNavigation.latestRow(c,thread,"mms");
        if(message==null||message.optInt("msg_box")!=1||message.optInt("m_type")!=132||latest==null||latest.optLong("_id")!=id)throw changed();
        // ID order also catches a newly received row with an earlier carrier timestamp.
        try(Cursor rows=c.getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id"},"thread_id=?",new String[]{Long.toString(thread)},"_id DESC")){
            if(rows==null||!rows.moveToFirst()||rows.getLong(0)!=id)throw changed();
        }
        MediaNavigation.Destination destination=MediaNavigation.destination(c,thread,message);
        if(destination.readOnly()||!SendPolicy.validAddress(destination.address())||!destination.address().equals(Sender.singleRecipient(c,thread)))throw changed();
        MediaNavigation.parts(c,message);String kind=message.optString("contentKind");
        if(!Set.of(MmsContentPolicy.TEXT,MmsContentPolicy.ATTACHMENTS).contains(kind)||message.optBoolean("truncated")||message.optBoolean("textUnavailable"))throw new IllegalStateException("This attachment is still incomplete. Open the chat.");
        JSONObject sms=latestSms(c,thread,ownJob);
        if(sms!=null&&MediaHistoryPolicy.compare(new MediaHistoryPolicy.Position(sms.optLong("latestDate",sms.optLong("date")),"sms",sms.optLong("latestDateId",sms.optLong("_id"))),new MediaHistoryPolicy.Position(message.optLong("date"),"mms",id))>=0)throw changed();
        String fingerprint=MediaContextPolicy.signature(message.optString("_id"),message.optString("thread_id"),message.optString("date"),message.optString("msg_box"),message.optString("m_type"),message.optString("body"),message.optJSONArray("parts").toString(),destination.address());
        return new Source(thread,id,message.optLong("date"),fingerprint,destination.address(),sms==null?0:sms.optLong("_id"),signature(sms),ManualTakeover.revision(c,thread),receipt,message.optString("body"),MmsContentPolicy.TEXT.equals(kind));
    }
    private static JSONObject latestSms(Context c,long thread,long ownJob)throws JSONException{
        String excluded="";JSONObject job=ownJob>0?Store.get(c).job(ownJob):null;
        if(job!=null&&!job.optString("uri").isEmpty()){
            Uri uri=Uri.parse(job.optString("uri"));
            if(!"content".equals(uri.getScheme())||!"sms".equals(uri.getAuthority())||uri.getPathSegments().size()!=1)throw changed();
            JSONObject own=MediaNavigation.exact(c,"sms",Long.parseLong(uri.getLastPathSegment()),thread);
            if(own==null||own.optInt("type")!=Telephony.Sms.MESSAGE_TYPE_OUTBOX||own.optLong("date")!=job.optLong("sms_date")||!own.optString("body").equals(job.optString("body"))||!own.optString("address").equals(job.optString("address")))throw changed();
            excluded=own.optString("_id");
        }
        String where="thread_id=?"+(excluded.isEmpty()?"":" AND _id<>?");String[] args=excluded.isEmpty()?new String[]{Long.toString(thread)}:new String[]{Long.toString(thread),excluded};
        JSONObject newest;
        try(Cursor rows=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},where,args,"_id DESC")){
            if(rows==null)throw changed();newest=rows.moveToFirst()?MediaNavigation.exact(c,"sms",rows.getLong(0),thread):null;
        }
        if(newest!=null)try(Cursor rows=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},where,args,"date DESC, _id DESC")){
            if(rows==null||!rows.moveToFirst())throw changed();JSONObject recent=MediaNavigation.exact(c,"sms",rows.getLong(0),thread);if(recent==null)throw changed();
            newest.put("latestDate",recent.optLong("date")).put("latestDateId",recent.optLong("_id")).put("latestDateSignature",signature(recent));
        }
        return newest;
    }
    private static String signature(JSONObject row){return row==null?"":MediaContextPolicy.signature(row.optString("_id"),row.optString("date"),row.optString("type"),row.optString("address"),row.optString("body"),row.optString("latestDateSignature"));}
    private static AutopilotSourcePolicy.Binding binding(Source s){return s==null?null:new AutopilotSourcePolicy.Binding(s.thread(),s.id(),s.date(),s.fingerprint(),s.address(),s.smsId(),s.smsSignature(),s.manualRevision());}
    static boolean same(Source a,Source b){return AutopilotSourcePolicy.matches(binding(a),binding(b));}
    static void generate(Context c,long thread,long id,long receipt)throws Exception{
        Personas.requireReady(c,thread);String learning=Personas.token(c,thread);Store db=Store.get(c);JSONObject profile,config;Source source;long generation;
        synchronized(PilotApp.SEND_LOCK){
            ManualTakeover.require(c,thread);profile=db.relationship(thread);if(!enabled(c,profile))return;
            config=CloudConfig.read(c);if(config==null)throw new IllegalStateException("Connect your AI service.");
            source=capture(c,thread,id,receipt,0);if(block(c,source,0)!=null)return;
            if(!ReplyReadiness.current(c,thread,profile.optString("samples")).eligible())return;
            if(db.draft(thread,source.smsId())!=null)return;
            generation=db.generationRevision();
        }
        TextMmsDrafts.History history=TextMmsDrafts.history(c,thread,id,source.address());
        String reason=AutopilotPolicy.incoming(history.context().unanswered(),false,history.context().incomplete()||!source.textOnly());
        AutopilotPolicy.Reply reply;
        // Same plan rule as text replies: two different AI deferrals, then quiet alerts.
        boolean planning="plans".equals(reason);int deferrals=0;List<String> sent=List.of();
        if(planning){
            deferrals=AutomaticReplies.planDeferrals(c,thread);
            if(!PlanDeferralPolicy.reply(deferrals)){
                synchronized(PilotApp.SEND_LOCK){
                    if(!same(source,capture(c,thread,id,receipt,0))||block(c,source,0)!=null)return;
                    Sender.silenceAutomaticForThread(c,thread,AutomaticReplyPolicy.message("plans_need_input"));MessageChanges.publish();
                }
                AutomaticReplies.quietPlans(c,thread,source.address());return;
            }
            reason=null;sent=AutomaticReplies.recentSent(c,thread);
        }
        if(reason!=null)reply=AutopilotPolicy.fallback(reason);
        else{
            JSONObject request=CloudDrafts.payload(thread,history.context().messages(),ContactGuidance.context(profile.optString("body"),profile.optString("importantDetails"),"always_reply"),profile.optString("samples"),"Use AI intuition",true)
                .put("automatic",true).put("autopilot",true).put("automationReady",true).put("engagement","always_reply").put("persona",Personas.forReply(c,thread)).put("approvedExamples",ApprovedLearning.examples(c,thread));
            String views=OwnerViews.read(c);if(!views.isEmpty())request.put("ownerViews",views);
            if(ReplyModels.premium(c,thread))request.put("premium",true);
            if(planning)request.put("planDeferral",new JSONObject().put("count",PlanDeferralPolicy.requestCount(deferrals)));
            if(!Personas.unchanged(c,thread,learning)||!same(source,capture(c,thread,id,receipt,0))||block(c,source,0)!=null)return;
            JSONObject response;
            try{response=CloudClient.request(config,"/draft",request);}
            catch(Exception unavailable){response=planning?new JSONObject().put("decision","reply").put("reason","reply_needed").put("body",PlanDeferralPolicy.fallback(deferrals,sent)):new JSONObject().put("decision","reply").put("reason","reply_needed").put("body",AutopilotPolicy.fallback("model_unavailable").body()).put("attentionNeeded",true).put("attentionReason","model_unavailable");}
            reply=planning?AutopilotPolicy.planDeferral(response.opt("decision"),response.opt("reason"),response.opt("body"),deferrals,sent):AutopilotPolicy.response(response.opt("decision"),response.opt("reason"),response.opt("body"),response.opt("attentionNeeded"),response.opt("attentionReason"),null);
        }
        if(!history.fingerprint().equals(TextMmsDrafts.history(c,thread,id,source.address()).fingerprint()))return;
        synchronized(PilotApp.SEND_LOCK){
            JSONObject current=db.relationship(thread),currentConfig=CloudConfig.read(c);
            if(!Personas.unchanged(c,thread,learning)||generation!=db.generationRevision()||current.optLong("revision")!=profile.optLong("revision")||!enabled(c,current)||currentConfig==null||!currentConfig.optString("revision").equals(config.optString("revision"))||!same(source,capture(c,thread,id,receipt,0))||block(c,source,0)!=null||db.draft(thread,source.smsId())!=null)return;
            long job=Sender.scheduleAutomaticMms(c,source,reply,profile.optLong("revision"),config.optString("revision"));
            if(job==0)return;JSONObject saved=db.job(job);boolean instant=saved.optLong("auto_delay")==0;
            if(!Notices.draft(c,(int)thread,Messages.name(c,source.address()),source.text().isBlank()?"Attachment":source.text(),reply.body(),thread,job,saved.optLong("due"),instant)){
                db.update(job,"paused","Turn on notifications before Autopilot can send.");return;
            }
            Sender.activateAutomatic(c,job);MessageChanges.publish();
        }
    }
    static void save(Context c,long job,Source s){
        ContentValues v=new ContentValues();v.put("job",job);v.put("thread",s.thread());v.put("mms_id",s.id());v.put("mms_date",s.date());v.put("fingerprint",s.fingerprint());v.put("address",s.address());v.put("sms_id",s.smsId());v.put("sms_signature",s.smsSignature());v.put("manual_revision",s.manualRevision());v.put("receipt",s.receipt());Store.get(c).getWritableDatabase().insertOrThrow("autopilot_sources",null,v);
    }
    static JSONObject saved(Context c,long job){return Store.get(c).query("SELECT * FROM autopilot_sources WHERE job=?",new String[]{Long.toString(job)}).optJSONObject(0);}
    static String block(Context c,JSONObject job){
        JSONObject row=saved(c,job.optLong("_id"));if(row==null)return "The incoming message source is unavailable.";
        try{
            Source source=capture(c,job.optLong("thread"),row.optLong("mms_id"),row.optLong("receipt"),job.optLong("_id"));
            AutopilotSourcePolicy.Binding prior=new AutopilotSourcePolicy.Binding(row.optLong("thread"),row.optLong("mms_id"),row.optLong("mms_date"),row.optString("fingerprint"),row.optString("address"),row.optLong("sms_id"),row.optString("sms_signature"),row.optLong("manual_revision"));
            if(!AutopilotSourcePolicy.matches(prior,binding(source)))return "New messages arrived. This old reply was stopped.";
            return block(c,source,job.optLong("_id"));
        }catch(Exception unavailable){return "The incoming message changed or could not be checked.";}
    }
    static String block(Context c,Source source,long ownJob){
        if(source.receipt()<=0)return "This was not a live incoming message.";
        if(ManualTakeover.blocked(c,source.thread())||!ManualTakeover.unchanged(c,source.thread(),source.manualRevision()))return ManualTakeoverPolicy.WAITING;
        if(MmsDownloads.isPending()||IncomingBurst.hasUnbound(c)||IncomingBurst.remaining(c,source.thread(),source.smsId(),source.address())>0)return "New messages are still arriving.";
        if(MmsAttachments.hasStaged(c,source.thread()))return "You are preparing an attachment.";
        JSONArray other=Store.get(c).query("SELECT j._id,j.status,j.uri FROM jobs j JOIN autopilot_sources s ON s.job=j._id WHERE s.thread=? AND s.mms_id=? AND j._id<>?",new String[]{Long.toString(source.thread()),Long.toString(source.id()),Long.toString(ownJob)});
        for(int i=0;i<other.length();i++){JSONObject j=other.optJSONObject(i);if(AutopilotSourcePolicy.competing(true,j.optString("status"),!j.optString("uri").isEmpty()))return "This message already has a reply.";}
        return null;
    }
    private static IllegalStateException changed(){return new IllegalStateException("The current incoming message changed.");}
}
