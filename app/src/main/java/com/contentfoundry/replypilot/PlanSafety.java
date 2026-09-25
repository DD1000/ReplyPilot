package com.contentfoundry.replypilot;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Common planning language requires the owner's input; this never authorizes a commitment. */
final class PlanSafety {
    private static final Pattern[] REQUESTS=compile(new String[]{
        "\\b(?:are|r|will|would) (?:you|u|ya)(?: be| still| happen to be)? (?:free|available|busy|off)\\b",
        "\\b(?:you|u) (?:free|available)\\b|\\b(?:come over|come join (?:me|us)|swing by|stop by)\\b",
        "\\b(?:i(?:'m| am)|im|we(?:'re| are))(?: not)? (?:free|available)\\b|\\b(?:i|we)'ll be (?:free|available)\\b",
        "\\b(?:when|what time|which days?|what days?) (?:are|will|would) (?:you|u)(?: be)? (?:free|available|off)\\b|\\b(?:your|ur) (?:availability|schedule)\\b",
        "\\b(?:what (?:are|r) (?:you|u) (?:doing|up to)|are you working)\\b.*\\b(?:today|tonight|tomorrow|(?:this|next) (?:morning|afternoon|evening|week|weekend|month)|(?:on )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|at \\p{Nd}{1,2}(?::\\p{Nd}{2})?(?: ?[ap]m)?|\\p{Nd}{1,2}(?::\\p{Nd}{2})? ?[ap]m|noon|midnight|in \\p{Nd}+ (?:minutes?|hours?|days?))\\b",
        "\\b(?:(?:do|would) you (?:want|like|care) to|want to|wanna|up for|down for|let's|lets|shall we|can we|could we|we should) (?:grab|get|go|meet|hang|catch up|do|have|come|join|visit|plan|book|schedule|watch|see|play|eat|take a walk)\\b",
        "\\b(?:up for|down for|join (?:me|us) for|how about|what about) (?:a |some |the )?(?:dinner|lunch|breakfast|brunch|coffee|drinks|a drink|movie|movies|concert|party|meeting|appointment|date|trip|hike|walk|hangout|game|gym|call|catch up)\\b",
        "\\b(?:can|could|would|will) (?:you|u)(?: please)? (?:come|make it|join|meet|call|hop on|cover (?:my|a|the) shift|pick (?:me|us) up|give (?:me|us) a ride|babysit|watch (?:the|my|our) kids)\\b",
        "\\b(?:are (?:you|we)|r u)(?: still)? (?:coming|joining|meeting)\\b|\\bare we(?: still)? on\\b|\\bare you still (?:on|going)\\b|\\b(?:we(?:'re| are)|it(?:'s| is)) still on\\b",
        "\\b(?:reschedule|re-schedule|rearrange)\\b|\\b(?:cancel|move|change|postpone) (?:our |the |that )?(?:plans?|meeting|appointment|dinner|lunch|date|reservation|it)\\b",
        "\\b(?:can't|cannot|can not|won't|will not) make it\\b|\\b(?:running|going to be|will be|i'll be|im|i'm|i am) (?:\\p{Nd}+ (?:minutes?|hours?) )?late\\b",
        "\\b(?:what time|when|where) (?:should|shall|can|could|do|are) we (?:meet|go|start|leave|get together)\\b|\\b(?:what time|when) (?:works|is good|is best) (?:for you|for u)\\b",
        "\\b(?:see|meet) you (?:at|on|tomorrow|tonight|this|next|then)\\b|\\b(?:confirm|confirming|confirmed)(?: our| the| your)? (?:plans?|meeting|appointment|reservation|dinner|time|arrangement)\\b",
        "\\b(?:how about|what about|does|would|can we do|make it|actually) (?:today|tonight|tomorrow|(?:this|next) (?:morning|afternoon|evening|week|weekend|month)|(?:on )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|at \\p{Nd}{1,2}(?::\\p{Nd}{2})?(?: ?[ap]m)?|\\p{Nd}{1,2}(?::\\p{Nd}{2})? ?[ap]m|noon|midnight|in \\p{Nd}+ (?:minutes?|hours?|days?))\\b",
        "\\b(?:does|would) (?:\\p{Nd}{1,2}(?::\\p{Nd}{2})?(?: ?[ap]m)?|(?:today|tonight|tomorrow|(?:this|next) (?:morning|afternoon|evening|week|weekend|month)|(?:on )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|at \\p{Nd}{1,2}(?::\\p{Nd}{2})?(?: ?[ap]m)?|\\p{Nd}{1,2}(?::\\p{Nd}{2})? ?[ap]m|noon|midnight|in \\p{Nd}+ (?:minutes?|hours?|days?)))(?: still)? (?:work|suit)\\b|\\b\\p{Nd}{1,2}(?::\\p{Nd}{2})?(?: ?[ap]m)? instead\\b",
        "\\b(?:today|tonight|tomorrow|(?:this|next) (?:morning|afternoon|evening|week|weekend|month)|(?:on )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|at \\p{Nd}{1,2}(?::\\p{Nd}{2})?(?: ?[ap]m)?|\\p{Nd}{1,2}(?::\\p{Nd}{2})? ?[ap]m|noon|midnight|in \\p{Nd}+ (?:minutes?|hours?|days?)) (?:works|sounds good|is (?:good|perfect|fine))\\b|\\b\\p{Nd}{1,2}(?::\\p{Nd}{2})?(?: ?[ap]m)? it is\\b"
    });
    private static final Pattern[] COMMITMENTS=compile(new String[]{
        "\\b(?:i(?:'m| am)|im|we(?:'re| are))(?: not)? (?:free|available|busy|off work|working|on my way|on our way|coming)\\b",
        "\\b(?:i|we) (?:can|could|will|would|can't|cannot|won't)(?: definitely| probably| happily)? (?:make it|come|join|meet|attend|cover|pick|drop|bring|call|text|send|book|reserve|check|get back|be there|do (?:it|that)|handle|take care)\\b",
        "\\b(?:i|we)'(?:ll|d) (?:come|join|meet|attend|cover|pick|drop|bring|call|text|send|book|reserve|check|get back|be (?:there|free|available|busy)|do (?:it|that)|handle|take care)\\b",
        "\\b(?:count me in|i'm in|im in|i am in|i can do|i could do|works for me|works for us|my schedule is|i have no plans|i don't have plans|i do not have plans)\\b",
        "\\b(?:i|we)(?:'ve| have)? (?:booked|reserved|scheduled|confirmed|cancelled|canceled)\\b",
        "\\b(?:today|tonight|tomorrow|(?:this|next) (?:morning|afternoon|evening|week|weekend|month)|(?:on )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|at \\p{Nd}{1,2}(?::\\p{Nd}{2})?(?: ?[ap]m)?|\\p{Nd}{1,2}(?::\\p{Nd}{2})? ?[ap]m|noon|midnight|in \\p{Nd}+ (?:minutes?|hours?|days?)) (?:works|sounds good|is (?:good|perfect|fine))\\b|\\b\\p{Nd}{1,2}(?::\\p{Nd}{2})?(?: ?[ap]m)? it is\\b"
    });
    private static final Pattern ACTIVITY=Pattern.compile("\\b(?:dinner|lunch|breakfast|brunch|coffee|drinks|a drink|movie|movies|concert|party|meeting|appointment|date|trip|hike|walk|hangout|game|gym|call|catch up)\\b");
    private static final Pattern TIME=Pattern.compile("\\b(?:today|tonight|tomorrow|(?:this|next) (?:morning|afternoon|evening|week|weekend|month)|(?:on )?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|at \\p{Nd}{1,2}(?::\\p{Nd}{2})?(?: ?[ap]m)?|\\p{Nd}{1,2}(?::\\p{Nd}{2})? ?[ap]m|noon|midnight|in \\p{Nd}+ (?:minutes?|hours?|days?))\\b");
    private static final Pattern SHORT_INVITE=Pattern.compile("^(?:(?:want|fancy) (?:some |a )?)?(?:dinner|lunch|breakfast|brunch|coffee|drinks|a drink|movie|movies|concert|party|meeting|appointment|date|trip|hike|walk|hangout|game|gym|call|catch up)(?: (?:at|with|near|after|before) .{1,100})?\\?+$");
    private static final Pattern PAST=Pattern.compile("\\b(?:yesterday|last (?:night|week|weekend|month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)|used to|had|went|was|were|did)\\b");
    private PlanSafety(){}
    private static Pattern[] compile(String[] patterns){
        Pattern[] result=new Pattern[patterns.length];
        for(int i=0;i<patterns.length;i++)result[i]=Pattern.compile(patterns[i]);
        return result;
    }
    private static String normalize(String text){
        if(text==null)return "";
        String value=Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replaceAll("[\\u200b-\\u200d\\ufeff]","").replace('’','\'').replace('‘','\'');
        return TextWhitespace.RUN.matcher(value).replaceAll(" ").trim();
    }
    /** Pass all consecutive incoming messages since the latest outgoing, oldest first. */
    static boolean incoming(List<String> unanswered){
        List<String> parts=new ArrayList<>();
        if(unanswered!=null)for(String message:unanswered){String text=normalize(message);if(!text.isEmpty())parts.add(text);}
        String joined=String.join(" ",parts);
        if(joined.isEmpty())return false;
        for(Pattern pattern:REQUESTS){
            if(pattern.matcher(joined).find())return true;
            for(String text:parts)if(pattern.matcher(text).find())return true;
        }
        for(String text:parts)if(SHORT_INVITE.matcher(text).matches())return true;
        if(ACTIVITY.matcher(joined).find()&&TIME.matcher(joined).find()
            &&(!PAST.matcher(joined).find()||joined.contains("?")))return true;
        for(String text:parts)if(text.contains("?")&&TIME.matcher(text).find()
            &&text.split(" ").length<=8&&!PAST.matcher(text).find())return true;
        return false;
    }
    /** AI candidates only. Manually written SMS must not use this guard. */
    static boolean commitment(String candidate){
        String value=normalize(candidate);if(value.isEmpty())return false;
        if(incoming(List.of(value)))return true;
        for(Pattern pattern:COMMITMENTS)if(pattern.matcher(value).find())return true;
        return false;
    }
    static String reason(List<String> unanswered,String candidate){
        return incoming(unanswered)||commitment(candidate)?"plans_need_input":null;
    }
}
