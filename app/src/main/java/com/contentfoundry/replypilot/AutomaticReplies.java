package com.contentfoundry.replypilot;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

final class AutomaticReplies {
    static final class WaitingForMms extends IllegalStateException {
        WaitingForMms(){super("Messages are still arriving. Try again in a moment.");}
    }
    static void requireSettledMms(){if(MmsDownloads.isPending())throw new WaitingForMms();}
    /** Transport/source/eligibility gates only. Natural closings are valid new messages. */
    static String reason(Context c,long thread,long base,String candidate,long excludedJob){
        return reason(c,thread,base,candidate,excludedJob,false);
    }
    static String generationReason(Context c,long thread,long base,String candidate){return reason(c,thread,base,candidate,0,true);}
    private static String reason(Context c,long thread,long base,String candidate,long excludedJob,boolean generation){
        String blocked=sourceReason(c,thread,base,generation);if(blocked!=null)return blocked;
        JSONArray history=Messages.history(c,thread);JSONObject incoming=history.optJSONObject(history.length()-1);
        if(incoming==null||incoming.optLong("_id")!=base||incoming.optInt("type")!=1)return "conversation_complete";
        if(candidate!=null&&PlanSafety.commitment(candidate))return "plans_need_input";
        if(candidate!=null&&RequestSafety.unsuitableReply(candidate))return "needs_review";
        try{if(!ReplyReadiness.current(c,thread,Store.get(c).relationship(thread).optString("samples")).eligible())return "insufficient_history";}
        catch(org.json.JSONException invalid){throw new IllegalStateException("Reply settings could not be checked.");}
        JSONArray submissions=Store.get(c).query("SELECT _id,status,uri FROM jobs WHERE thread=? AND base=? AND _id<>?",new String[]{Long.toString(thread),Long.toString(base),Long.toString(excludedJob)});
        for(int i=0;i<submissions.length();i++){JSONObject job=submissions.optJSONObject(i);if(AutopilotSourcePolicy.competing(true,job.optString("status"),!job.optString("uri").isEmpty()))return "repeated_reply";}
        return null;
    }
    static boolean requestNeedsReview(Context c,long thread,long base){return requestReason(c,thread,base)!=null;}
    static String requestReason(Context c,long thread,long base){return requestReason(c,thread,base,false);}
    static String requestReason(Context c,long thread,long base,boolean unused){
        return sourceReason(c,thread,base,false);
    }
    static String generationRequestReason(Context c,long thread,long base){return sourceReason(c,thread,base,true);}
    private static String sourceReason(Context c,long thread,long base,boolean generation){
        if(ManualTakeover.blocked(c,thread)||MmsAttachments.hasSentForBase(c,thread,base))return "conversation_complete";
        if(!generation&&MmsDownloads.isPending())return "needs_review";
        if(MmsAttachments.hasStaged(c,thread)||MediaContext.newerIncoming(c,thread,base))return "needs_review";
        // Receipt binding is global and brief; another chat's MMS must not become
        // durable silence for this live SMS. Dispatch retains its existing gate.
        if(generation)requireSettledMms();
        return null;
    }
    static JSONObject attentionActions(Context c,long thread,long base){return new JSONObject();}
    static boolean girlfriendPaused(Context c,long thread,long base){return false;}
    static JSONObject publicHold(Context c,long thread){return null;}
    static void onIncoming(Context c,long thread,long base){/* Live receipt scheduling is handled by DraftJob. */}
    static JSONObject heldResult(String reason)throws org.json.JSONException{return new JSONObject().put("decision","no_reply").put("reason",reason).put("body","").put("engine","Reply Pilot · conversation check").put("elapsedMs",0);}
    static void record(Context c,long thread,long base,String reason){record(c,thread,base,reason,true);}
    static void record(Context c,long thread,long base,String reason,boolean background){
        Store.get(c).noReply(thread,base,reason);Sender.silenceAutomaticForThread(c,thread,AutomaticReplyPolicy.message(reason));MessageChanges.publish();
        if(background&&"insufficient_history".equals(reason))Notices.show(c,(int)thread,"Chat needs attention","Autopilot needs more conversation history for this person.",thread);
    }
}
