package com.contentfoundry.replypilot;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Plan pushes: Autopilot puts plans off at most twice in the owner's voice, each time
 * in different words, then stays quiet and alerts the owner until they reply themselves.
 * It never agrees to, declines or suggests a time. Pure rules; no Android or storage here.
 */
final class PlanDeferralPolicy {
    static final int MAX_DEFERRALS=2;
    /** Older deferrals stop counting after this long, so a new ask on another day starts fresh. */
    static final long WINDOW_MS=12L*60*60*1000;
    // Kept in sync with relay/autopilot.mjs. None of these is a commitment under PlanSafety.
    private static final List<List<String>> FALLBACKS=List.of(
        List.of("Not sure yet, let me get back to you.","Let me see and get back to you.","Not sure yet, give me a bit."),
        List.of("Still figuring it out, give me a little bit.","Still working it out, hang tight.","Haven't figured it out yet, give me a bit."));
    private static final Pattern NOT_WORD=Pattern.compile("[^\\p{L}\\p{N}\\s]"),SPACES=Pattern.compile("\\s+");
    private static final Pattern WHEREABOUTS=Pattern.compile("\\b(?:i(?:'m| am)|we(?:'re| are))\\s+(?:at\\s|near\\s|in\\s|(?:back\\s+)?home\\b)");
    private PlanDeferralPolicy(){}

    /** Deferrals count only after the owner's own latest reply, and only within the window. */
    static long since(long now,long lastManualSend,long lastManualTakeover){
        return Math.max(Math.max(0,now-WINDOW_MS),Math.max(lastManualSend,lastManualTakeover));
    }
    /** Autopilot answers the first and second plan push; after that it waits for the owner. */
    static boolean reply(int prior){return prior<MAX_DEFERRALS;}
    /** What the relay is told: 0 for the first deferral, 1 for the follow-up. */
    static int requestCount(int prior){return prior>0?1:0;}

    static String comparable(String text){
        if(text==null)return "";
        String value=Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return SPACES.matcher(NOT_WORD.matcher(value).replaceAll("")).replaceAll(" ").trim();
    }
    static boolean repeats(String body,List<String> earlier){
        String value=comparable(body);
        for(String text:earlier)if(value.equals(comparable(text)))return true;
        return false;
    }
    /** A safe, varied deferral that was not already sent in this chat. */
    static String fallback(int prior,List<String> earlier){
        List<String> options=FALLBACKS.get(requestCount(prior));
        for(String option:options)if(!repeats(option,earlier))return option;
        return options.get(0);
    }
    /** The model's deferral is used only when it is safe, new and says nothing about where the owner is. */
    static boolean acceptable(String body,List<String> earlier){
        if(body==null||body.isBlank())return false;
        String text=body.strip(),plain=Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('’','\'').replace('‘','\'');
        return !PlanSafety.commitment(text)&&!RequestSafety.unsuitableReply(text)&&!repeats(text,earlier)&&!WHEREABOUTS.matcher(plain).find();
    }
    static String silentNotice(String name){
        String who=name==null||name.isBlank()?"They are":name.strip()+" is";
        return who+" still asking about plans. Autopilot already put it off twice, so it's waiting for you to reply.";
    }
}
