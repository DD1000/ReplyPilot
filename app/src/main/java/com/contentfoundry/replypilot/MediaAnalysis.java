package com.contentfoundry.replypilot;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Foreground, opt-in image interpretation. Does not create a draft, timer or send. */
final class MediaAnalysis {
    static JSONObject analyze(Context c,long thread,long mediaId,boolean inApp)throws Exception{
        HistoryLearning.requireReady(c);String learningToken=HistoryLearning.token(c);
        Store store=Store.get(c);JSONObject profile,config;long generation,manualRevision;
        synchronized(PilotApp.SEND_LOCK){
            ManualTakeover.require(c,thread);manualRevision=ManualTakeover.revision(c,thread);
            if(!PilotApp.foreground)throw new IllegalStateException("Open the conversation to understand this media.");
            if(inApp&&(MmsDownloads.isPending()||MmsAttachments.hasStaged(c,thread)||!ForegroundSuggestions.enabled(c)||!isCurrentMedia(c,thread,mediaId)))throw new IllegalStateException("A newer conversation is available. Open its latest message.");
            profile=store.relationship(thread);if(!profile.optBoolean("cloudEnabled"))throw new IllegalStateException("Enable OpenAI for this person in Reply setup first.");
            if(inApp&&profile.optBoolean("autoSend"))throw new IllegalStateException("Autopilot is already handling this conversation.");
            config=CloudConfig.read(c);if(config==null)throw new IllegalStateException("Connect your AI service in Settings first.");
            generation=store.generationRevision();
        }
        MediaContext.Snapshot media=MediaContext.capture(c,thread,mediaId);
        JSONObject input=media.payload();JSONArray limitations=input.optJSONArray("limitations");input.remove("limitations");
        StringBuilder limitation=new StringBuilder();if(limitations!=null)for(int i=0;i<limitations.length();i++){if(limitation.length()>0)limitation.append(' ');limitation.append(limitations.optString(i));}
        String notes=ApprovedLearningPolicy.bounded(input.optString("mediaLimitations",limitation.toString()),360);
        if(!media.hasImages())return new JSONObject().put("mediaId",mediaId).put("summary","").put("intent","I couldn't inspect this attachment, so its meaning is unknown.").put("confidence","low").put("limitation",notes.isBlank()?"This attachment cannot be analyzed yet. You can still open it from the conversation.":notes).put("suggestion","").put("reason","needs_review").put("reviewOnly",true);
        // Keep the whole visible unanswered run; never treat omitted conditions as absent.
        JSONObject page=MediaNavigation.around(c,mediaId,null);JSONArray around=page.getJSONArray("history");
        List<JSONObject> prior=new ArrayList<>();boolean priorOtherMedia=false;
        for(int i=0;i<around.length();i++){
            JSONObject row=around.optJSONObject(i);if(("mms:"+mediaId).equals(row.optString("key")))break;
            if(row.optLong("thread_id")==thread&&(row.optInt("type")==1||row.optInt("type")==2))prior.add(row);
        }
        int unanswered=prior.size();while(unanswered>0&&prior.get(unanswered-1).optInt("type")==1)unanswered--;
        boolean incomplete=page.optBoolean("hasOlder")&&unanswered==0&&!prior.isEmpty();
        for(String note:media.limitations())if(note.contains("first 1,600")||note.contains("skipped")||note.contains("could not")||note.contains("not transcribed")||note.contains("cannot be analyzed"))incomplete=true;
        boolean match=c.getSharedPreferences("settings",0).getBoolean("matchMyStyle",true);
        JSONArray history=new JSONArray();int start=match?0:Math.max(0,Math.min(unanswered,prior.size()-8));
        for(int i=start;i<prior.size();i++){
            JSONObject row=prior.get(i);String body=row.optString("body");int limit=i>=unanswered?1600:600;
            if(i>=unanswered&&body.length()>limit)incomplete=true;
            if(i>=unanswered&&"mms".equals(row.optString("kind"))&&!row.optBoolean("textOnly"))priorOtherMedia=true;
            if(!body.isBlank())history.put(new JSONObject().put("speaker",row.optInt("type")==2?"me":"them").put("text",ApprovedLearningPolicy.bounded(body,limit)));
        }
        incomplete|=priorOtherMedia;
        if(incomplete)notes=ApprovedLearningPolicy.bounded("Some unanswered context or attachments were omitted. Explain the visible media only; no reply suggestion. "+notes,360);
        input.put("requestId",UUID.randomUUID().toString()).put("history",history).put("matchStyle",match).put("style",new JSONArray()).put("automatic",false)
            .put("relationship",ContactGuidance.context(profile.optString("body"),profile.optString("importantDetails"),profile.optString("engagement"))).put("samples",profile.optString("samples"))
            .put("tone",ContactGuidance.effectiveTone(profile.optString("engagement")))
            .put("engagement",profile.optString("engagement","natural")).put("humorLevel",0)
            .put("styleMode","learned").put("insideJokes","").put("mediaLimitations",notes);
        if(match)input.put("approvedExamples",ApprovedLearning.examples(c,thread));
        input.put("historyMemory",HistoryLearning.context(c,thread)).put("autopilot",true).put("engagement","always_reply");
        if(inApp&&(MmsDownloads.isPending()||MmsAttachments.hasStaged(c,thread)||!ForegroundSuggestions.enabled(c)||!isCurrentMedia(c,thread,mediaId)))throw new IllegalStateException("A newer conversation is available. Open its latest message.");
        if(!ManualTakeover.unchanged(c,thread,manualRevision))throw new IllegalStateException(ManualTakeoverPolicy.WAITING);ManualTakeover.require(c,thread);
        JSONObject response=CloudClient.request(config,"/media-analysis",input);
        MediaAnalysisPolicy.Result result=MediaAnalysisPolicy.validate(response.opt("summary"),response.opt("intent"),response.opt("confidence"),response.opt("limitation"),response.opt("suggestion"),response.opt("reason"));
        // Decode/provider checks stay outside SEND_LOCK; final settings checks are atomic.
        if(!MediaContext.valid(c,media))throw new IllegalStateException("This conversation changed while analyzing. Open the latest media and try again.");
        synchronized(PilotApp.SEND_LOCK){
            if(!ManualTakeover.unchanged(c,thread,manualRevision))throw new IllegalStateException(ManualTakeoverPolicy.WAITING);ManualTakeover.require(c,thread);
            JSONObject latest=store.relationship(thread),latestConfig=CloudConfig.read(c);
            if(!PilotApp.foreground||(inApp&&!ForegroundSuggestions.enabled(c))||generation!=store.generationRevision()||latest.optLong("revision")!=profile.optLong("revision")||!latest.optBoolean("cloudEnabled")||latestConfig==null||!latestConfig.optString("revision").equals(config.optString("revision")))throw new IllegalStateException("Your AI settings changed. Analyze the media again.");
            String suggestion=result.suggestion(),reason=result.reason();
            if(incomplete&&"reply_needed".equals(reason)){suggestion="";reason="needs_review";}
            if(!HistoryLearning.unchanged(c,learningToken))throw new IllegalStateException("Conversation learning changed. Analyze again.");
            String attentionReason="";
            if(!"reply_needed".equals(reason)&&!"insufficient_history".equals(reason)){
                attentionReason="plans_need_input".equals(reason)?"plans":"uncertain";
                suggestion=AutopilotPolicy.fallback(attentionReason).body();reason="reply_needed";
            }
            if(!attentionReason.isEmpty()&&isCurrentMedia(c,thread,mediaId))store.draftAttention(thread,Messages.latest(c,thread),suggestion,attentionReason);
            return new JSONObject().put("mediaId",mediaId).put("summary",result.summary()).put("intent",result.intent()).put("confidence",result.confidence()).put("limitation",incomplete?ApprovedLearningPolicy.bounded("Some unanswered context or attachments were omitted. Review the conversation before replying. "+result.limitation(),360):result.limitation()).put("suggestion",suggestion).put("reason",reason).put("attentionNeeded",!attentionReason.isEmpty()).put("attentionReason",attentionReason).put("reviewOnly",true);
        }
    }
    private static boolean isCurrentMedia(Context c,long thread,long mediaId)throws JSONException{
        JSONObject mms=MediaNavigation.latestRow(c,thread,"mms"),sms=MediaNavigation.latestRow(c,thread,"sms");
        if(mms==null||mms.optLong("_id")!=mediaId)return false;
        return sms==null||MediaHistoryPolicy.compare(new MediaHistoryPolicy.Position(mms.optLong("date"),"mms",mediaId),new MediaHistoryPolicy.Position(sms.optLong("date"),"sms",sms.optLong("_id")))>0;
    }
}
