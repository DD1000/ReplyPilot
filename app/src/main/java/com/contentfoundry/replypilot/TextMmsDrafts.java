package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import org.json.*;
import java.util.*;

/** Reviewable foreground text drafts for MMS transport. This never schedules or sends. */
final class TextMmsDrafts {
    record History(TextMmsDraftPolicy.Context context,String fingerprint) {}
    static JSONObject generate(Context c,long thread,long base,long textMmsId,boolean inApp)throws Exception{
        if(inApp)Personas.requireReady(c,thread);String learningToken=Personas.token(c,thread);
        Store store=Store.get(c);JSONObject profile,config,before;long revision,manualRevision;
        synchronized(PilotApp.SEND_LOCK){
            require(c,thread,base,inApp);ManualTakeover.require(c,thread);manualRevision=ManualTakeover.revision(c,thread);profile=store.relationship(thread);
            if(!profile.optBoolean("cloudEnabled"))throw new IllegalStateException("Enable AI for this person in Reply setup first.");
            if(inApp&&profile.optBoolean("autoSend"))throw new IllegalStateException("Autopilot is already handling this conversation.");
            config=CloudConfig.read(c);if(config==null)throw new IllegalStateException("Connect your AI service in Settings first.");
            before=store.draft(thread,base);
            if(inApp&&before!=null&&!before.optString("body").isBlank())throw new IllegalStateException("Your existing draft was kept.");
            revision=store.generationRevision();
        }
        MediaContext.Snapshot anchor=MediaContext.captureText(c,thread,textMmsId);
        History history=history(c,thread,textMmsId,anchor.address());
        String reason=AutopilotPolicy.incoming(history.context().unanswered(),false,history.context().incomplete());
        AutopilotPolicy.Reply reply;
        if(reason!=null)reply=AutopilotPolicy.fallback(reason);
        else{
            boolean match=c.getSharedPreferences("settings",0).getBoolean("matchMyStyle",true);
            JSONObject request=CloudDrafts.payload(thread,history.context().messages(),ContactGuidance.context(profile.optString("body"),profile.optString("importantDetails"),"always_reply"),profile.optString("samples"),"Use AI intuition",match)
                .put("automatic",false).put("autopilot",true).put("automationReady",false).put("engagement","always_reply").put("styleMode","learned").put("persona",Personas.forReply(c,thread));
            if(match)request.put("approvedExamples",ApprovedLearning.examples(c,thread));
            String views=OwnerViews.read(c);if(!views.isEmpty())request.put("ownerViews",views);
            if(ReplyModels.premium(c,thread))request.put("premium",true);
            if(!ManualTakeover.unchanged(c,thread,manualRevision)||!Personas.unchanged(c,thread,learningToken))throw new IllegalStateException(ManualTakeoverPolicy.WAITING);ManualTakeover.require(c,thread);
            JSONObject response;
            try{response=CloudClient.request(config,"/draft",request);}
            catch(Exception unavailable){response=new JSONObject().put("decision","reply").put("reason","reply_needed").put("body",AutopilotPolicy.fallback("model_unavailable").body()).put("attentionNeeded",true).put("attentionReason","model_unavailable");}
            reply=AutopilotPolicy.response(response.opt("decision"),response.opt("reason"),response.opt("body"),response.opt("attentionNeeded"),response.opt("attentionReason"),null);
        }
        // Provider reads stay off the send lock; compare the whole bounded text run,
        // including rapid follow-ups and any incomplete attachment-containing turn.
        if(!MediaContext.valid(c,anchor)||!history.fingerprint().equals(history(c,thread,textMmsId,anchor.address()).fingerprint()))throw changed();
        synchronized(PilotApp.SEND_LOCK){
            require(c,thread,base,inApp);
            if(!ManualTakeover.unchanged(c,thread,manualRevision))throw new IllegalStateException(ManualTakeoverPolicy.WAITING);ManualTakeover.require(c,thread);
            JSONObject latest=store.relationship(thread),latestConfig=CloudConfig.read(c),draft=store.draft(thread,base);
            if(revision!=store.generationRevision()||latest.optLong("revision")!=profile.optLong("revision")||!latest.optBoolean("cloudEnabled")||latestConfig==null||!latestConfig.optString("revision").equals(config.optString("revision")))throw new IllegalStateException("Your AI settings changed. Tap Draft reply again.");
            if(!Objects.equals(before==null?null:before.toString(),draft==null?null:draft.toString()))throw new IllegalStateException("Your draft changed while AI was working. Your text was kept.");
            if(!current(c,thread,textMmsId))throw changed();
            if(!Personas.unchanged(c,thread,learningToken))throw new IllegalStateException("Conversation learning changed. Draft again.");
            store.clearReplyDecision(thread,base);store.draftTextMms(thread,base,reply.body(),anchor);
            store.draftAttention(thread,base,reply.body(),reply.attentionNeeded()?reply.attentionReason():"");
            JSONObject saved=store.draft(thread,base),heldReply=null,outcome=null;
            MessageChanges.publish();
            return new JSONObject().put("draftState",true).put("thread",thread).put("base",base).put("textMmsId",textMmsId)
                .put("draft",saved==null?JSONObject.NULL:saved)
                .put("replyDecision",outcome==null?JSONObject.NULL:outcome)
                .put("replyHold",heldReply==null?JSONObject.NULL:heldReply)
                .put("replyWaiting",false).put("waiting",false);
        }
    }
    static boolean matchesStored(Context c,long thread,JSONObject draft){
        try{
            long id=draft.optLong("source_mms");
            if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS)||!current(c,thread,id))return false;
            JSONObject row=MediaNavigation.exact(c,"mms",id,thread);if(row==null||row.optInt("m_type")!=132)return false;
            MediaNavigation.parts(c,row);MediaNavigation.Destination destination=MediaNavigation.destination(c,thread,row);
            return !destination.readOnly()&&row.optBoolean("textOnly")&&TextMmsDraftPolicy.sameSource(new TextMmsDraftPolicy.Source(id,draft.optLong("source_mms_date"),draft.optString("source_text"),draft.optString("source_address")),new TextMmsDraftPolicy.Source(row.optLong("_id"),row.optLong("date"),row.optString("body"),destination.address()));
        }catch(Exception unavailable){return false;}
    }
    private static void require(Context c,long thread,long base,boolean inApp)throws Exception{
        if(!PilotApp.foreground||!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Open this chat with messaging access to draft a reply.");
        if(thread<=0||base<0||Messages.latest(c,thread)!=base)throw changed();
        if(inApp&&!ForegroundSuggestions.enabled(c))throw new IllegalStateException("Reply suggestions are turned off.");
        if(MmsDownloads.isPending()||MmsAttachments.hasStaged(c,thread))throw new IllegalStateException("Finish loading or sending attachments before drafting a text reply.");
        if(Store.get(c).query("SELECT _id FROM jobs WHERE thread=? AND status IN ('scheduled','sending','awaiting_alert')",new String[]{Long.toString(thread)}).length()>0)throw new IllegalStateException("Finish or cancel the current reply before drafting another.");
    }
    private static boolean current(Context c,long thread,long id)throws JSONException{
        JSONObject mms=MediaNavigation.latestRow(c,thread,"mms"),sms=MediaNavigation.latestRow(c,thread,"sms");
        return mms!=null&&mms.optLong("_id")==id&&mms.optInt("type")==1&&(sms==null||MediaHistoryPolicy.compare(new MediaHistoryPolicy.Position(mms.optLong("date"),"mms",id),new MediaHistoryPolicy.Position(sms.optLong("date"),"sms",sms.optLong("_id")))>0);
    }
    static History history(Context c,long thread,long id,String address)throws JSONException{
        JSONObject page=MediaNavigation.latest(c,thread);JSONArray rows=page.getJSONArray("history");JSONObject latest=page.optJSONObject("latest");
        if(page.optBoolean("readOnly")||!address.equals(page.optString("address"))||latest==null||!("mms:"+id).equals(latest.optString("key")))throw changed();
        List<TextMmsDraftPolicy.Turn> turns=new ArrayList<>();JSONArray identity=new JSONArray();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String kind=row.optString("kind"),body=row.optString("body");
            boolean complete=!row.optBoolean("truncated")&&!row.optBoolean("textUnavailable")&&(!"mms".equals(kind)||row.optBoolean("textOnly"));
            int type=row.optInt("type");
            if("mms".equals(kind))type=row.optInt("msg_box")==1&&row.optInt("m_type")==132?1:row.optInt("msg_box")==2&&row.optInt("m_type")==128?2:0;
            else if(type!=1&&type!=2)type=0;
            turns.add(new TextMmsDraftPolicy.Turn(row.optLong("thread_id"),type,body,complete));
            identity.put(new JSONArray().put(kind).put(row.optLong("_id")).put(row.optLong("date")).put(type).put(row.optInt("msg_box")).put(row.optInt("m_type")).put(body).put(complete).put(row.optJSONArray("parts")));
        }
        return new History(TextMmsDraftPolicy.context(thread,turns,page.optBoolean("hasOlder")),MediaContextPolicy.signature(address,identity.toString()));
    }
    private static IllegalStateException changed(){return new IllegalStateException("The conversation changed. Open its latest message, then tap Draft reply again.");}
}
