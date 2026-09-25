package com.contentfoundry.replypilot;

import java.util.List;

/** Saved choices for one conversation, including an explicit standing send permission. */
public final class PersonProfile {
    public static final long DEFAULT_AUTO_DELAY_SECONDS=0;
    public static final long MIN_AUTO_DELAY_SECONDS=60;
    public static String kind(String value) {
        return value != null && List.of("Friend","Family","Partner","Dating","Coworker","Client","Acquaintance").contains(value) ? value : "";
    }
    public static String tone(String value) {
        return value != null && !value.isEmpty() && ReplyPrompt.normalizeTone(value).equals(value) ? value : "";
    }
    public static String effectiveTone(String saved, String fallback) {
        String chosen=tone(saved);
        return chosen.isEmpty() ? ReplyPrompt.normalizeTone(fallback) : chosen;
    }
    public static boolean automaticAllowed(boolean enabled, boolean automatic, boolean global) {
        return enabled && automatic && global;
    }
    public static boolean automaticSendAllowed(boolean enabled,boolean automatic,boolean autoSend,boolean global) {
        return autoSend && automaticAllowed(enabled,automatic,global);
    }
    /** Turning Off is a local permission revocation, never a provider-read operation. */
    static ReplyEligibility.Result readiness(boolean enabled,boolean automatic,boolean autoSend,java.util.function.Supplier<ReplyEligibility.Result> scan){
        return enabled&&automatic&&autoSend?scan.get():new ReplyEligibility.Result(false,0,0,0,true,false);
    }
    public static long autoDelay(long seconds) {
        if(seconds!=0&&seconds!=60&&seconds!=300)throw new IllegalArgumentException("Choose instant, one minute or five minutes for Autopilot.");
        return seconds;
    }
    public static String context(String relationshipKind, String dynamics) {
        String chosen=kind(relationshipKind),note=ReplyPrompt.relationshipContext(dynamics);
        return chosen.isEmpty() ? note : "Relationship type: " + chosen + "." + (note.isEmpty() ? "" : "\n" + note);
    }
}
