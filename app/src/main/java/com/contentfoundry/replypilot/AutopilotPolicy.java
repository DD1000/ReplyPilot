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
        if(attention){
            // Keep the model's own words when they are safe; a canned line reads like a bot.
            // Private-information questions always get the fixed deferral, so nothing private leaks.
            String why=attentionReason instanceof String value?value:"uncertain";
            if(Set.of("plans","sensitive","uncertain").contains(why)&&PlanDeferralPolicy.acceptable(text,List.of()))return new Reply(text.strip(),true,why);
            return fallback(why);
        }
        if(!(attentionReason instanceof String why)||!why.isEmpty())return fallback("uncertain");
        return new Reply(text.strip(),false,"");
    }
    /** A plan push Autopilot may still answer: the model's own deferral when it is safe and new, otherwise a varied fallback. Always flagged. */
    static Reply planDeferral(Object decision,Object reason,Object body,int prior,List<String> earlier){
        boolean usable="reply".equals(decision)&&"reply_needed".equals(reason)&&body instanceof String text&&PlanDeferralPolicy.acceptable(text,earlier);
        return new Reply(usable?((String)body).strip():PlanDeferralPolicy.fallback(prior,earlier),true,"plans");
    }
    static boolean acceptedState(String state,boolean hasCarrierRecord){return hasCarrierRecord&&Set.of("sending","sent").contains(state);}
}
