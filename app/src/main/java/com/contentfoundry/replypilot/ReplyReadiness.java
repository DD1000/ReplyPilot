package com.contentfoundry.replypilot;

import android.content.Context;
import android.Manifest;
import org.json.*;
import java.util.*;

final class ReplyReadiness {
    static ReplyEligibility.Result current(Context context,long thread,String samples){
        SmsHistoryPolicy.validateThread(thread);
        return ReplyReadinessHistory.read(context,thread,samples);
    }
    /** Browsing a loaded chat does not depend on the optional readiness check. */
    static JSONObject forDisplay(Context context,long thread,String samples,boolean readOnly)throws JSONException{
        if(readOnly)return json(new ReplyEligibility.Result(false,0,0,0)).put("available",false).put("message","Automatic replies need a conversation with one person.");
        try{return json(current(context,thread,samples));}
        catch(SecurityException revoked){throw revoked;}
        catch(RuntimeException unavailable){
            if(!Messages.role(context)||!Messages.allowed(context,Manifest.permission.READ_SMS))throw unavailable;
            return json(new ReplyEligibility.Result(false,0,0,0,true,false)).put("available",false).put("message","Conversation history could not be checked. Recheck history to try again.");
        }
    }
    static JSONObject json(ReplyEligibility.Result value)throws JSONException{
        return new JSONObject().put("available",true).put("eligible",value.eligible()).put("total",value.total()).put("owner",value.owner()).put("incoming",value.incoming())
            .put("countsAreMinimum",value.countsAreMinimum()).put("scanComplete",value.scanComplete())
            .put("minimumTotal",ReplyEligibility.MINIMUM_TOTAL).put("minimumOwner",ReplyEligibility.MINIMUM_OWNER).put("minimumIncoming",ReplyEligibility.MINIMUM_INCOMING).put("message",value.message());
    }
    static JSONObject analyze(Context context,long thread,String body,String ownerLabel)throws JSONException{
        if(body==null||body.length()>ChatLog.MAX_RAW_CHARS)throw new IllegalArgumentException("Choose a text chat log no larger than 256 KB.");
        ChatLog.Result parsed=body!=null&&body.isBlank()?new ChatLog.Result("",0,0,0,false):ChatLog.parse(body,ownerLabel);
        return new JSONObject().put("samples",parsed.samples()).put("messageCount",parsed.messageCount()).put("ownerCount",parsed.ownerCount())
            .put("incomingCount",parsed.incomingCount()).put("truncated",parsed.truncated())
            .put("replyEligibility",json(current(context,thread,parsed.samples())));
    }
}
