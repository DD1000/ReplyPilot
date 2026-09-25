package com.contentfoundry.replypilot;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Legacy response parsing remains for the standalone test surface. */
final class AutomaticReplyPolicy {
    static final int MAX_CONSECUTIVE=5;
    static final String SUBMITTED_SQL="(status IN ('sending','sent','unknown') OR (status='failed' AND uri<>''))";
    static final String COUNT_SQL="SELECT COUNT(*) AS total FROM jobs WHERE thread=? AND auto_send=1 AND _id<>? AND "+SUBMITTED_SQL+" AND _id>COALESCE((SELECT MAX(_id) FROM jobs WHERE thread=? AND auto_send=0 AND attention_kind<>'delay' AND (status='sent' OR delivery_status='delivered')),0)";
    private static final Set<String> CLOSINGS=Set.of("ok","okay","k","kk","got it","sounds good","thanks","thank you","thank u","thx","ty","thanks again","ok thanks","okay thanks","ok thank you","okay thank you","no problem","you're welcome","you’re welcome","bye","good night","goodnight","see you","see you later","talk later","take care","👍","🙏","👌","ok 👍","okay 👍");
    record Response(String decision,String reason,String body){}

    private static String normalized(String value){
        if(value==null)return "";
        String text=Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder plain=new StringBuilder();
        text.codePoints().filter(cp->cp!=0xfe0f&&(cp<0x1f3fb||cp>0x1f3ff)).forEach(plain::appendCodePoint);
        return plain.toString().trim().replaceAll("\\s+"," ");
    }
    static boolean closing(String incoming){
        String text=normalized(incoming);
        if(text.contains("?"))return false;
        return CLOSINGS.contains(text.replaceFirst("[.!…]+$","").trim());
    }
    static String comparable(String value){return normalized(value).replaceAll("['’‘]","").replaceFirst("[.!?…]+$","").trim();}
    static boolean repeated(String body,List<String> outgoing){
        String candidate=comparable(body);if(candidate.length()<20||candidate.split(" ").length<=3)return false;
        for(String prior:outgoing)if(candidate.equals(comparable(prior)))return true;
        return false;
    }
    static String reason(String incoming,boolean hasOutgoing,List<String> outgoing,int submissions,String candidate){
        return null;
    }
    static String message(String reason){
        return switch(reason){
            case "plans_need_input" -> "Plans need your input. Automatic replies are paused until you send a reply.";
            case "needs_review" -> "Reply needs your input — this message needs your own judgment or personal information. Write a reply yourself.";
            case "insufficient_history" -> "Automatic replies are paused until this person has enough conversation history. Add a labeled chat log in Reply setup or keep chatting.";
            case "automatic_limit" -> "Automatic replies are paused after five replies. Send a message yourself to start a new round.";
            case "repeated_reply" -> "No reply needed — this would repeat a message you already sent.";
            default -> "No reply needed — this conversation appears complete.";
        };
    }
    static String validReason(String reason){
        if(reason==null||!Set.of("conversation_complete","repeated_reply","automatic_limit","needs_review","insufficient_history","plans_need_input").contains(reason))throw new IllegalArgumentException("Invalid reply decision.");
        return reason;
    }
    static Response response(boolean hasDecision,Object decision,Object reason,Object body){
        if(!(body instanceof String raw))throw new IllegalStateException("OpenAI returned an invalid reply body.");
        if(hasDecision&&"no_reply".equals(decision)){
            if(reason==null||!Set.of("conversation_complete","repeated_reply","needs_review","insufficient_history","plans_need_input").contains(reason)||!raw.isEmpty())throw new IllegalStateException("OpenAI returned an invalid reply decision.");
            return new Response("no_reply",(String)reason,"");
        }
        if(hasDecision&&(!"reply".equals(decision)||!"reply_needed".equals(reason)))throw new IllegalStateException("OpenAI returned an invalid reply decision.");
        String text=raw.trim();
        if(text.isEmpty())throw new IllegalStateException("OpenAI returned no usable reply.");
        if(RequestSafety.unsuitableReply(raw))return new Response("no_reply","needs_review","");
        if(PlanSafety.commitment(raw))return new Response("no_reply","plans_need_input","");
        return new Response("reply","reply_needed",text);
    }
}
