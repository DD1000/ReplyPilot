package com.contentfoundry.replypilot;

/** Pure decision rules shared by acceptance and alarm dispatch. */
public final class SendPolicy {
    public static final long MAX_DELAY_MS = 7L * 24 * 60 * 60 * 1000;
    public static final long MAX_LATENESS_MS = 2 * 60 * 1000;
    public static String generationBlock(long startedRevision,long currentRevision){
        return startedRevision==currentRevision?null:"Messaging conditions changed while the reply was being created. Review the conversation and generate a fresh draft.";
    }
    public static String automaticBlock(boolean enabled,boolean autoDraft,boolean autoSend,boolean global,
                                        long expectedProfile,long currentProfile,String expectedConfig,String currentConfig,
                                        boolean canAlert,boolean exact) {
        return automaticBlock(enabled,autoDraft,autoSend,global,expectedProfile,currentProfile,expectedConfig,currentConfig,canAlert,exact,PersonProfile.DEFAULT_AUTO_DELAY_SECONDS);
    }
    public static String automaticBlock(boolean enabled,boolean autoDraft,boolean autoSend,boolean global,
                                        long expectedProfile,long currentProfile,String expectedConfig,String currentConfig,
                                        boolean canAlert,boolean exact,long delaySeconds) {
        if(!PersonProfile.automaticSendAllowed(enabled,autoDraft,autoSend,global))return "Automatic sending was turned off. Review and choose a timer to send this reply.";
        try{PersonProfile.autoDelay(delaySeconds);}catch(IllegalArgumentException e){return e.getMessage();}
        if(expectedProfile<=0||expectedProfile!=currentProfile)return "Reply settings changed. Review and choose a timer again.";
        if(expectedConfig==null||expectedConfig.isEmpty()||!expectedConfig.equals(currentConfig))return "The AI connection changed. Generate a new reply.";
        if(!canAlert)return "Turn on reply notifications before using automatic sending.";
        if(delaySeconds>0&&!exact)return "Restore precise timer access before using automatic sending.";
        return null;
    }
    public static String block(String status, boolean approved, long due, long now,
                               long expectedMessage, long latestMessage, boolean role, boolean permission) {
        if (!"scheduled".equals(status)) return "This reply is no longer scheduled.";
        if (!approved) return "Approval is required.";
        if (!role || !permission) return "Restore the default SMS role and permissions, then approve again.";
        if (expectedMessage != latestMessage) return "The conversation changed. Review and approve again.";
        if (now < due) return "The timer has not finished.";
        if (now - due > MAX_LATENESS_MS) return "The timer was missed. Review and approve again.";
        return null;
    }
    public static boolean validAddress(String value) { return value != null && value.matches("\\+?[0-9][0-9 ()-]{2,24}"); }
    public static void validateConversation(long thread,long base) {
        if(thread<=0||base<0)throw new IllegalArgumentException("Open a valid conversation before sending.");
    }
    public static String manualTimingBlock(long delay,boolean exact) {
        if(delay<0||delay>MAX_DELAY_MS)return "Choose a delay from zero to seven days.";
        if(delay>0&&!exact)return "Enable precise timers in Settings before scheduling.";
        return null;
    }
    /** Applied only to a prior job with the identical conversation, SMS base and text. */
    public static String repeatedImmediateBlock(String status,boolean hasCarrierRecord) {
        if("sending".equals(status)||"sent".equals(status))return "This reply has already been submitted. Check the conversation before sending it again.";
        if("unknown".equals(status)||("failed".equals(status)&&hasCarrierRecord))return "This reply may already have sent. Check its result in Queue before trying again.";
        return null;
    }
    public static String generationAfterSubmissionBlock(long thread,long base,long jobThread,long jobBase,String status,boolean hasCarrierRecord) {
        if(thread!=jobThread||base!=jobBase)return null;
        // A completed send supersedes any in-flight wording for the same incoming
        // message, even when its timestamp prevents the provider's latest ID moving.
        return repeatedImmediateBlock(status,hasCarrierRecord)==null?null
            :"A reply was already submitted for this message. The later AI draft was discarded; check the conversation or Queue for the send result.";
    }
    public static void validate(String address, String text, long delay) {
        if (!validAddress(address)) throw new IllegalArgumentException("Enter one valid phone number.");
        if (text == null || text.trim().isEmpty() || text.length() > 1600) throw new IllegalArgumentException("Write a reply between 1 and 1,600 characters.");
        String timing=manualTimingBlock(delay,true);if(timing!=null)throw new IllegalArgumentException(timing);
    }
}
