package com.contentfoundry.replypilot;

import android.content.Context;
import org.json.*;
import java.util.*;

public final class CloudDrafts {
    public static JSONObject payload(long thread,List<ReplyPrompt.Message> history,String relationship,String samples,String tone,boolean matchStyle) throws JSONException {
        CloudPrompt.Input input=CloudPrompt.build(thread,history,relationship,samples,tone,matchStyle);
        JSONArray messages=new JSONArray();for(CloudPrompt.Turn turn:input.history())messages.put(new JSONObject().put("speaker",turn.speaker()).put("text",turn.text()));
        return new JSONObject().put("requestId",UUID.randomUUID().toString()).put("relationship",input.relationship()).put("samples",input.samples()).put("tone",input.tone()).put("history",messages).put("style",new JSONArray(input.style())).put("matchStyle",matchStyle).put("automatic",false);
    }
    public static void generate(Context c,long thread,long base,String tone,boolean background) throws Exception {
        generate(c,thread,base,tone,background,null,false);
    }
    static void suggest(Context c,long thread,long base,String tone)throws Exception{generate(c,thread,base,tone,false,null,true);}
    /** A message-bound owner clarification creates a reviewable draft, never send approval. */
    public static void joke(Context c,long thread,long base,String token) throws Exception {
        JSONObject profile=Store.get(c).relationship(thread);
        generate(c,thread,base,ContactGuidance.effectiveTone(profile.optString("engagement")),false,token,false);
    }
    private static void generate(Context c,long thread,long base,String tone,boolean background,String jokeToken,boolean inApp) throws Exception {
        boolean joke=jokeToken!=null;
        if(joke)throw new IllegalStateException("This reply action was retired. Open the conversation.");
        HistoryLearning.requireReady(c);String learningToken=HistoryLearning.token(c);
        Store store=Store.get(c);JSONObject profile;JSONObject before;JSONObject config;long generationRevision,manualRevision,sleepRevision,burstToken=0;String burstAddress;LocationReplies.Snapshot location;
        synchronized(PilotApp.SEND_LOCK){
            if(inApp&&!ForegroundSuggestions.eligible(c,thread,base))return;
            if(ManualTakeover.blocked(c,thread)){if(background||inApp)return;throw new IllegalStateException(ManualTakeoverPolicy.WAITING);}
            manualRevision=ManualTakeover.revision(c,thread);
            if(joke&&!AttentionActions.check(c,thread,base,jokeToken))throw new IllegalStateException("This message or action changed. Open the latest conversation.");
            sleepRevision=background?SleepSession.revision(c):0;
            if(background&&!SleepSession.generationAllowed(c,base,sleepRevision))return;
            profile=store.relationship(thread);if(!profile.optBoolean("cloudEnabled"))throw new IllegalStateException("Enable OpenAI for this person and save their profile first.");
            if(background&&!PersonProfile.automaticAllowed(profile.optBoolean("cloudEnabled"),profile.optBoolean("autoDraft"),c.getSharedPreferences("settings",0).getBoolean("autoDraft",true)))return;
            if(Messages.latest(c,thread)!=base){
                if(background||inApp)return;
                throw new IllegalStateException("The conversation changed. Open its latest message, then tap Draft reply again.");
            }
            burstAddress=Messages.address(c,thread,base);
            String requestReason=AutomaticReplies.generationRequestReason(c,thread,base);if(requestReason!=null){recordDecision(c,thread,base,requestReason,background,joke);return;}
            if(background&&(!IncomingBurst.ready(c,thread,base,burstAddress))){DraftJob.schedule(c,thread,base);return;}
            burstToken=IncomingBurst.token(c,thread,base,burstAddress);
            if(background&&store.replyDecision(thread,base)!=null)return;
            before=store.draft(thread,base);if(background&&before!=null)return;
            if(joke&&before!=null&&!before.optString("body").isBlank()&&!before.optString("engine").startsWith("OpenAI"))throw new IllegalStateException("Send or clear your current draft before using Joke.");
            if(background){String reason=AutomaticReplies.generationReason(c,thread,base,null);if(reason!=null){AutomaticReplies.record(c,thread,base,reason);return;}}
            config=CloudConfig.read(c);if(config==null)throw new IllegalStateException("Connect your OpenAI server in Settings.");
            if(!background)Sender.cancelAutomaticForThread(c,thread,"You requested a new draft. The previous automatic timer was stopped.");
            location=LocationReplies.capture(c,thread,base);
            generationRevision=store.generationRevision();
        }
        boolean matchStyle=c.getSharedPreferences("settings",0).getBoolean("matchMyStyle",true);
        List<String> incomingRun=Messages.unanswered(c,thread,base);
        String attentionReason=AutopilotPolicy.incoming(incomingRun,location.payload()!=null,incomingRun.size()>CloudPrompt.RECENT_LIMIT||incomingRun.stream().anyMatch(x->x.length()>1600));
        AutopilotPolicy.Reply reply;boolean insufficientHistory=false;
        if(attentionReason!=null)reply=AutopilotPolicy.fallback(attentionReason);
        else{
            List<ReplyPrompt.Message> context=matchStyle?RecentContext.refresh(c,thread).messages():ReplyAgent.promptHistory(Messages.history(c,thread));
            JSONObject request=payload(thread,context,ContactGuidance.context(profile.optString("body"),profile.optString("importantDetails"),"always_reply"),profile.optString("samples"),"Use AI intuition",matchStyle)
                .put("automatic",background).put("autopilot",true).put("automationReady",ReplyReadiness.current(c,thread,profile.optString("samples")).eligible())
                .put("engagement","always_reply").put("styleMode","learned").put("historyMemory",HistoryLearning.context(c,thread));
            if(matchStyle)request.put("approvedExamples",ApprovedLearning.examples(c,thread));
            if(location.payload()!=null)request.put("locationContext",location.payload());
            if(background&&(!SleepSession.generationAllowed(c,base,sleepRevision)||!IncomingBurst.unchanged(c,thread,base,burstAddress,burstToken)))return;
            if(!ManualTakeover.unchanged(c,thread,manualRevision)||ManualTakeover.blocked(c,thread)||!HistoryLearning.unchanged(c,learningToken))return;
            AutomaticReplies.requireSettledMms();
            JSONObject response;
            try{response=CloudClient.request(config,"/draft",request);}
            catch(Exception error){
                if(background&&(!SleepSession.generationAllowed(c,base,sleepRevision)||!IncomingBurst.unchanged(c,thread,base,burstAddress,burstToken)))return;
                response=new JSONObject().put("decision","reply").put("reason","reply_needed").put("body",AutopilotPolicy.fallback("model_unavailable").body()).put("attentionNeeded",true).put("attentionReason","model_unavailable");
            }
            insufficientHistory="no_reply".equals(response.optString("decision"))&&"insufficient_history".equals(response.optString("reason"));
            // A no-reply decision mutates durable state too. Adopt it only through
            // the same access, recipient, manual, learning and configuration checks.
            reply=insufficientHistory?AutopilotPolicy.fallback("uncertain"):AutopilotPolicy.response(response.opt("decision"),response.opt("reason"),response.opt("body"),response.opt("attentionNeeded"),response.opt("attentionReason"),null);
        }
        if(background&&(!SleepSession.generationAllowed(c,base,sleepRevision)||!IncomingBurst.unchanged(c,thread,base,burstAddress,burstToken)))return;
        String text=reply.body();
        if(reply.attentionNeeded())location=new LocationReplies.Snapshot(null,0,0);
        synchronized(PilotApp.SEND_LOCK){
            if(!ManualTakeover.unchanged(c,thread,manualRevision)||ManualTakeover.blocked(c,thread)||!HistoryLearning.unchanged(c,learningToken))return;
            if(inApp&&!ForegroundSuggestions.enabled(c))return;
            if(MediaContext.newerIncoming(c,thread,base)){
                if(background){AutomaticReplies.record(c,thread,base,"needs_review");return;}
                if(inApp)return;
                throw new IllegalStateException("A media message arrived. Use Understand & reply on that message.");
            }
            AutomaticReplies.requireSettledMms();
            if(joke&&!AttentionActions.check(c,thread,base,jokeToken))throw new IllegalStateException("New messages or settings changed this action. Your current draft was kept.");
            if(joke){String held=AutomaticReplies.requestReason(c,thread,base,true);if(held!=null){recordDecision(c,thread,base,held,background,joke);return;}}
            if(background&&(!SleepSession.generationAllowed(c,base,sleepRevision)||!IncomingBurst.unchanged(c,thread,base,burstAddress,burstToken)))return;
            if(location.payload()!=null&&!LocationReplies.valid(c,thread,location.revision(),location.expires())){AutomaticReplies.record(c,thread,base,"needs_review",background||joke);return;}
            String interrupted=SendPolicy.generationBlock(generationRevision,store.generationRevision());if(interrupted!=null)throw new IllegalStateException(interrupted);
            JSONObject latest=store.relationship(thread),latestConfig=CloudConfig.read(c),current=store.draft(thread,base);
            if(latest.optLong("revision")!=profile.optLong("revision")||!latest.optBoolean("cloudEnabled")||latestConfig==null||!latestConfig.optString("revision").equals(config.optString("revision")))throw new IllegalStateException("Your AI settings changed. Generate a new draft.");
            if(background&&!PersonProfile.automaticAllowed(latest.optBoolean("cloudEnabled"),latest.optBoolean("autoDraft"),c.getSharedPreferences("settings",0).getBoolean("autoDraft",true)))return;
            if(Messages.latest(c,thread)!=base||!Objects.equals(before==null?null:before.toString(),current==null?null:current.toString())){if(background)return;throw new IllegalStateException("The conversation or draft changed. Review it before redrafting.");}
            // Quick send can clear the saved draft while this request is running.
            // Check submission evidence under the same send lock, regardless of text.
            JSONArray submissions=store.query("SELECT thread,base,status,uri FROM jobs WHERE thread=? AND base=?",new String[]{Long.toString(thread),Long.toString(base)});
            for(int i=0;i<submissions.length();i++){
                JSONObject job=submissions.optJSONObject(i);
                String submitted=SendPolicy.generationAfterSubmissionBlock(thread,base,job.optLong("thread"),job.optLong("base"),job.optString("status"),!job.optString("uri").isEmpty());
                if(submitted!=null){if(background){AutomaticReplies.record(c,thread,base,"repeated_reply");return;}throw new IllegalStateException(submitted);}
            }
            if(background){String reason=AutomaticReplies.generationReason(c,thread,base,text);if(reason!=null){AutomaticReplies.record(c,thread,base,reason);return;}}
            AutomaticReplies.requireSettledMms();
            if(insufficientHistory){recordDecision(c,thread,base,"insufficient_history",background,false);return;}
            if(!background)store.clearReplyDecision(thread,base);
            store.draft(thread,base,text,new JSONArray().put(text),"OpenAI · awaiting your review",location.revision(),location.expires());
            store.draftAttention(thread,base,text,reply.attentionNeeded()?reply.attentionReason():"");
            JSONArray history=Messages.history(c,thread);JSONObject incoming=history.optJSONObject(history.length()-1);
            String original=String.join("\n",Messages.unanswered(c,thread,base)),sender=incoming==null?"Conversation":Messages.name(c,incoming.optString("address"));
            // Explicit Draft is always reviewable, even for a contact with auto-send enabled.
            boolean automatic=background&&PersonProfile.automaticSendAllowed(latest.optBoolean("cloudEnabled"),latest.optBoolean("autoDraft"),latest.optBoolean("autoSend"),c.getSharedPreferences("settings",0).getBoolean("autoDraft",true));
            if(automatic){
                long jobId=0;boolean instant=SleepSession.delayChoice(c,latest).instant();
                try{
                    jobId=Sender.scheduleAutomatic(c,thread,base,profile.optLong("revision"),config.optString("revision"),sleepRevision);
                    if(jobId==0)return; // A deliberate silence decision is not an error.
                    JSONObject job=store.job(jobId);instant=job.optLong("auto_delay",-1)==0;
                    if(!Notices.draft(c,(int)thread,sender,original,text,thread,jobId,job.optLong("due"),instant))throw new IllegalStateException("The reply alert could not be shown. Turn on notifications, then review this draft.");
                    Sender.activateAutomatic(c,jobId);
                    if(store.replyDecision(thread,base)!=null)return;
                    // Instant activation may already have submitted to the carrier,
                    // paused, or failed. Never describe that outcome as a timer.
                    store.draft(thread,base,text,new JSONArray().put(text),automaticEngine(store.job(jobId),instant),location.revision(),location.expires());
                }catch(Exception error){
                    if(store.replyDecision(thread,base)!=null)return;
                    String reason=error instanceof IllegalStateException?error.getMessage():"Automatic sending is unavailable. Check your permissions and reply settings.";
                    if(jobId>0)Sender.cancelAutomaticForThread(c,thread,reason);
                    JSONObject outcome=jobId>0?store.job(jobId):null;
                    store.draft(thread,base,text,new JSONArray().put(text),automaticEngine(outcome,instant),location.revision(),location.expires());
                    boolean submitted=outcome!=null&&Set.of("sending","sent").contains(outcome.optString("status"));
                    boolean alerted=instant&&outcome!=null&&Notices.instantOutcome(c,jobId,outcome.optString("status"),outcome.optString("note"));
                    if(!submitted){
                        if(!alerted)Notices.show(c,(int)thread,"Chat needs attention",reason,thread);
                        if(!background)throw new IllegalStateException("Your draft is saved. "+reason);
                    }
                }
            }else if(background||joke)Notices.draft(c,(int)thread,sender,original,text,thread,0,0);
            if(joke)MessageChanges.publish();
        }
    }
    private static void recordDecision(Context c,long thread,long base,String reason,boolean background,boolean joke){
        AutomaticReplies.record(c,thread,base,reason,background||joke);
        if(joke&&!Set.of("plans_need_input","needs_review").contains(reason))
            Notices.show(c,(int)thread,"Reply check complete",AutomaticReplyPolicy.message(reason),thread);
    }
    private static String automaticEngine(JSONObject job,boolean instant){
        String status=job==null?"":job.optString("status");
        return switch(status){
            case "sending" -> "OpenAI · sending automatically";
            case "sent" -> "OpenAI · automatically sent";
            case "failed" -> "OpenAI · automatic send failed";
            case "paused" -> instant?"OpenAI · automatic send paused":"OpenAI · timer not started";
            case "unknown" -> "OpenAI · send outcome unknown";
            case "scheduled" -> instant?"OpenAI · automatic send not started":"OpenAI · automatic timer started";
            default -> instant?"OpenAI · automatic send not started":"OpenAI · timer not started";
        };
    }
    public static JSONObject test(Context c,String message,String relationship,String examples,String tone) throws Exception {
        long started=System.nanoTime();String engine="Reply Pilot · safe deferral";
        if(message==null||message.isBlank()||message.length()>1600)throw new IllegalArgumentException("Enter a test message within 1,600 characters.");
        String localReason=AutopilotPolicy.incoming(List.of(message),false,false);
        AutopilotPolicy.Reply reply;
        if(localReason!=null)reply=AutopilotPolicy.fallback(localReason);
        else{
            // Fictional practice input has no phone source and cannot authorize sending.
            JSONObject request=payload(1,List.of(new ReplyPrompt.Message(1,1,message)),ReplyPrompt.relationshipContext(relationship),examples,tone,false)
                .put("autopilot",true).put("automatic",false).put("automationReady",false).put("engagement","always_reply");
            JSONObject response=CloudClient.request(c,"/draft",request);
            engine=response.optString("engine","OpenAI");
            reply=AutopilotPolicy.response(response.opt("decision"),response.opt("reason"),response.opt("body"),response.opt("attentionNeeded"),response.opt("attentionReason"),null);
        }
        return new JSONObject().put("decision","reply").put("reason","reply_needed").put("body",reply.body())
            .put("attentionNeeded",reply.attentionNeeded()).put("attentionReason",reply.attentionReason())
            .put("engine",engine).put("elapsedMs",Math.max(0,(System.nanoTime()-started)/1_000_000));
    }
}
