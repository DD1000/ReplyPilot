package com.contentfoundry.replypilot;

import java.util.List;
import java.util.Set;

/** Autopilot never guesses commitments or private facts. A safe deferral is still a reply. */
final class AutopilotPolicy {
    static final Set<String> REASONS=Set.of("plans","personal_info","sensitive","uncertain","model_unavailable");
    record Reply(String body,boolean attentionNeeded,String attentionReason) {}
    static String reason(String value){return REASONS.contains(value==null?"":value)?value:"uncertain";}
    static Reply fallback(String reason){
        String why=reason(reason);
        return new Reply(switch(why){
            case "plans" -> "I'll let you know in a bit.";
            case "personal_info" -> "Let me get back to you on that.";
            case "sensitive" -> "I hear you. Let me get back to you.";
            default -> "Got your message. Let me get back to you.";
        },true,why);
    }
    static String incoming(List<String> messages,boolean locationAllowed,boolean incomplete){
        if(PlanSafety.incoming(messages))return "plans";
        if(LocationRequestPolicy.isQuestion(messages)&&!locationAllowed)return "personal_info";
        if(incomplete)return "uncertain";
        if(messages.stream().anyMatch(RequestSafety::needsReview)||RequestSafety.needsReview(String.join("\n",messages)))return "sensitive";
        return null;
    }
    static Reply response(Object decision,Object reason,Object body,Object attentionNeeded,Object attentionReason,String incomingReason){
        if(incomingReason!=null)return fallback(incomingReason);
        if("no_reply".equals(decision)){
            if("insufficient_history".equals(reason))throw new IllegalStateException("Autopilot needs enough conversation history before it can reply.");
            return fallback("plans_need_input".equals(reason)?"plans":"needs_review".equals(reason)?"sensitive":"uncertain");
        }
        if(!"reply".equals(decision)||!"reply_needed".equals(reason)||!(body instanceof String text)||text.isBlank())return fallback("uncertain");
        if(PlanSafety.commitment(text))return fallback("plans");
        if(RequestSafety.unsuitableReply(text))return fallback("uncertain");
        if(!(attentionNeeded instanceof Boolean attention))return fallback("uncertain");
        if(attention)return fallback(attentionReason instanceof String why?why:"uncertain");
        if(!(attentionReason instanceof String why)||!why.isEmpty())return fallback("uncertain");
        return new Reply(text.strip(),false,"");
    }
    static boolean acceptedState(String state,boolean hasCarrierRecord){return hasCarrierRecord&&Set.of("sending","sent").contains(state);}
}
