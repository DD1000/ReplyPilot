package com.contentfoundry.replypilot;

import java.util.Set;

/** Media interpretation is tentative context; its reply is never send approval. */
final class MediaAnalysisPolicy {
    record Result(String summary,String intent,String confidence,String limitation,String suggestion,String reason){}
    static Result validate(Object summary,Object intent,Object confidence,Object limitation,Object suggestion,Object reason){
        String s=text(summary,600),i=text(intent,360),l=text(limitation,360),reply=text(suggestion,360);
        if(!(confidence instanceof String certainty)||!Set.of("low","medium","high").contains(certainty))throw invalid();
        if(!(reason instanceof String why)||!Set.of("reply_needed","conversation_complete","repeated_reply","needs_review","insufficient_history","plans_need_input").contains(why))throw invalid();
        if(!"reply_needed".equals(why)&&!reply.isEmpty())throw invalid();
        if("reply_needed".equals(why)&&reply.isBlank())throw invalid();
        if(RequestSafety.unsuitableReply(reply))return new Result(s,i,certainty,l,"","needs_review");
        if(!reply.isEmpty()&&PlanSafety.commitment(reply))return new Result(s,i,certainty,l,"","plans_need_input");
        return new Result(s,i,certainty,l,reply,why);
    }
    private static String text(Object value,int max){if(!(value instanceof String s)||s.length()>max)throw invalid();return s.strip();}
    private static IllegalStateException invalid(){return new IllegalStateException("The media reply was incomplete. Try again or write your own reply.");}
}
