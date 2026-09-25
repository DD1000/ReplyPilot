package com.contentfoundry.replypilot;
import org.junit.Test;
import static org.junit.Assert.*;
public class SendPolicyTest {
    private String autoGate(boolean cloud,boolean draft,boolean send,boolean global,long expected,long current,String savedConfig,String config,boolean notify,boolean exact){return SendPolicy.automaticBlock(cloud,draft,send,global,expected,current,savedConfig,config,notify,exact);}
    private String gate(String state,boolean approved,long due,long now,long base,long latest,boolean role,boolean permission){return SendPolicy.block(state,approved,due,now,base,latest,role,permission);}
    @Test public void approvedReplyCanSendAtItsDeadline(){assertNull(gate("scheduled",true,10000,10000,5,5,true,true));}
    @Test public void unapprovedDraftCannotSend(){assertNotNull(gate("scheduled",false,10000,10000,5,5,true,true));}
    @Test public void cancellationAndDuplicateAlarmsCannotSend(){for(String s:new String[]{"draft","awaiting_alert","cancelled","sending","sent","paused","failed"})assertNotNull(gate(s,true,10000,10000,5,5,true,true));}
    @Test public void neverSendBeforeDeadline(){assertNotNull(gate("scheduled",true,10001,10000,5,5,true,true));}
    @Test public void changedConversationRequiresNewApproval(){assertNotNull(gate("scheduled",true,10000,10000,5,6,true,true));}
    @Test public void removedPermissionsOrRoleBlockSend(){assertNotNull(gate("scheduled",true,10000,10000,5,5,false,true));assertNotNull(gate("scheduled",true,10000,10000,5,5,true,false));}
    @Test public void delayedDeliveryDoesNotSendStaleReply(){assertNotNull(gate("scheduled",true,10000,130001,5,5,true,true));assertNull(gate("scheduled",true,10000,130000,5,5,true,true));}
    @Test public void acceptsInternationalAndFormattedNumbers(){SendPolicy.validate("+1 (202) 555-0147","Hello",60000);SendPolicy.validate("020 7946 0123","Hello",0);}
    @Test public void rejectsMultipleRecipientsAndControlCodes(){for(String s:new String[]{"+12025550147;+12025550148","*123#","Alice","","123\n456"})assertFalse(SendPolicy.validAddress(s));}
    @Test(expected=IllegalArgumentException.class) public void emptyTextCannotBeAccepted(){SendPolicy.validate("2025550147","  ",1000);}
    @Test(expected=IllegalArgumentException.class) public void excessiveDelayCannotBeAccepted(){SendPolicy.validate("2025550147","Hi",SendPolicy.MAX_DELAY_MS+1);}
    @Test(expected=IllegalArgumentException.class) public void negativeDelayCannotBeAccepted(){SendPolicy.validate("2025550147","Hi",-1);}
    @Test(expected=IllegalArgumentException.class) public void oversizeDraftCannotBeAccepted(){SendPolicy.validate("2025550147","x".repeat(1601),0);}
    @Test public void explicitAutomaticSendPermissionCanAuthorizeTheTimer() {
        assertNull(autoGate(true,true,true,true,4,4,"connection-a","connection-a",true,true));
    }
    @Test public void turningOffAnyAutomaticPermissionRevokesTheTimer() {
        assertNotNull(autoGate(false,true,true,true,4,4,"a","a",true,true));
        assertNotNull(autoGate(true,false,true,true,4,4,"a","a",true,true));
        assertNotNull(autoGate(true,true,false,true,4,4,"a","a",true,true));
        assertNotNull(autoGate(true,true,true,false,4,4,"a","a",true,true));
    }
    @Test public void changedOrMissingPersonProfileCannotAuthorizeOldReply() {
        assertNotNull(autoGate(true,true,true,true,4,5,"a","a",true,true));
        assertNotNull(autoGate(true,true,true,true,0,0,"a","a",true,true));
        assertNotNull(autoGate(true,true,true,true,4,0,"a","a",true,true));
    }
    @Test public void ReconnectingOrRemovingCloudConnectionRevokesAutomaticReply() {
        assertNotNull(autoGate(true,true,true,true,4,4,"a","b",true,true));
        assertNotNull(autoGate(true,true,true,true,4,4,"a",null,true,true));
        assertNotNull(autoGate(true,true,true,true,4,4,"","",true,true));
        assertNotNull(autoGate(true,true,true,true,4,4,null,"a",true,true));
    }
    @Test public void AutomaticRepliesNeedWorkingAlertsAndPreciseTimers() {
        assertNotNull(autoGate(true,true,true,true,4,4,"a","a",false,true));
        assertNull(autoGate(true,true,true,true,4,4,"a","a",true,false));
    }
    @Test public void instantRepliesNeedNoAlarmPermissionButDelayedRepliesStillDo() {
        assertNull(SendPolicy.automaticBlock(true,true,true,true,4,4,"a","a",true,false,0));
        assertNull(SendPolicy.automaticBlock(true,true,true,true,4,4,"a","a",true,true,0));
        for(long delay:new long[]{60,300}){
            assertNotNull(SendPolicy.automaticBlock(true,true,true,true,4,4,"a","a",true,false,delay));
            assertNull(SendPolicy.automaticBlock(true,true,true,true,4,4,"a","a",true,true,delay));
        }
    }
    @Test public void instantRepliesRetainEveryStandingAuthorizationAndAlertGuard() {
        for(int disabled=0;disabled<4;disabled++){
            boolean[] enabled={true,true,true,true};enabled[disabled]=false;
            assertNotNull(SendPolicy.automaticBlock(enabled[0],enabled[1],enabled[2],enabled[3],4,4,"a","a",true,false,0));
        }
        assertNotNull(SendPolicy.automaticBlock(true,true,true,true,4,5,"a","a",true,false,0));
        assertNotNull(SendPolicy.automaticBlock(true,true,true,true,0,0,"a","a",true,false,0));
        assertNotNull(SendPolicy.automaticBlock(true,true,true,true,4,4,"a","b",true,false,0));
        assertNotNull(SendPolicy.automaticBlock(true,true,true,true,4,4,"a",null,true,false,0));
        assertNotNull(SendPolicy.automaticBlock(true,true,true,true,4,4,"a","a",false,false,0));
    }
    @Test public void instantPermissionCannotBypassCurrentConversationOrCarrierGuards() {
        boolean standing=SendPolicy.automaticBlock(true,true,true,true,4,4,"a","a",true,false,0)==null;
        assertNull(gate("scheduled",standing,10000,10000,5,5,true,true));
        assertNotNull(gate("scheduled",standing,10000,10000,5,6,true,true));
        assertNotNull(gate("scheduled",standing,10000,10000,5,5,false,true));
        assertNotNull(gate("scheduled",standing,10000,10000,5,5,true,false));
        for(String status:new String[]{"awaiting_alert","cancelled","sending","sent","failed","paused"})
            assertNotNull(gate(status,standing,10000,10000,5,5,true,true));
    }
    @Test public void invalidAutomaticDelaysCannotFallBackToInstantSending() {
        for(long delay:new long[]{-1,1,2,59,721,3600,604800,604801,Long.MAX_VALUE})
            assertNotNull(SendPolicy.automaticBlock(true,true,true,true,4,4,"a","a",true,true,delay));
    }
    @Test public void AutomaticPermissionDoesNotOverrideConversationAndCarrierRules() {
        boolean standingPermission=autoGate(true,true,true,true,4,4,"a","a",true,true)==null;
        assertNotNull(gate("scheduled",standingPermission,10000,10000,5,6,true,true));
        assertNotNull(gate("cancelled",standingPermission,10000,10000,5,5,true,true));
        assertNotNull(gate("scheduled",standingPermission,10000,10000,5,5,false,true));
        assertNotNull(gate("scheduled",standingPermission,10000,10000,5,5,true,false));
    }
    @Test public void GlobalPauseInvalidatesAnInflightReplyEvenWithAnUnchangedSmsBase() {
        assertNull(SendPolicy.generationBlock(9,9));
        // A multimedia arrival, recovery event, SIM change, or global stop moves the revision.
        assertNotNull(SendPolicy.generationBlock(9,10));
        assertNotNull(SendPolicy.generationBlock(9,11));
        // Only generation begun after that interruption can be used again.
        assertNull(SendPolicy.generationBlock(11,11));
    }
    @Test public void directSendAllowsNewConversationsButRejectsInvalidMessageCoordinates() {
        SendPolicy.validateConversation(1,0);
        SendPolicy.validateConversation(23,814);
        for(long thread:new long[]{0,-1,Long.MIN_VALUE})
            assertThrows(IllegalArgumentException.class,()->SendPolicy.validateConversation(thread,0));
        assertThrows(IllegalArgumentException.class,()->SendPolicy.validateConversation(1,-1));
    }
    @Test public void immediateManualSendWorksWithoutAlarmAccessAndTimersStillRequireIt() {
        assertNull(SendPolicy.manualTimingBlock(0,false));
        assertNull(SendPolicy.manualTimingBlock(0,true));
        for(long delay:new long[]{1,1000,300000,3600000,SendPolicy.MAX_DELAY_MS}){
            assertNotNull(SendPolicy.manualTimingBlock(delay,false));
            assertNull(SendPolicy.manualTimingBlock(delay,true));
        }
        for(long delay:new long[]{-1,SendPolicy.MAX_DELAY_MS+1,Long.MAX_VALUE})
            assertNotNull(SendPolicy.manualTimingBlock(delay,true));
    }
    @Test public void repeatedQuickTapCannotResubmitCompletedOrUncertainCarrierAttempts() {
        for(String status:new String[]{"sending","sent","unknown"}){
            assertNotNull(SendPolicy.repeatedImmediateBlock(status,false));
            assertNotNull(SendPolicy.repeatedImmediateBlock(status,true));
        }
        assertNotNull(SendPolicy.repeatedImmediateBlock("failed",true));
    }
    @Test public void failedPreCarrierAttemptCanBeRetriedWithoutBlockingUnsubmittedDrafts() {
        assertNull(SendPolicy.repeatedImmediateBlock("failed",false));
        for(String status:new String[]{"draft","cancelled","paused"})
            assertNull(SendPolicy.repeatedImmediateBlock(status,false));
    }
    @Test public void submittedReplyStopsLateGenerationEvenWhenSmsBaseAndGlobalRevisionStayUnchanged() {
        assertNull(SendPolicy.generationBlock(10,10));
        for(String status:new String[]{"sending","sent","unknown"}){
            assertNotNull(SendPolicy.generationAfterSubmissionBlock(7,90,7,90,status,false));
            assertNotNull(SendPolicy.generationAfterSubmissionBlock(7,90,7,90,status,true));
        }
        assertNotNull(SendPolicy.generationAfterSubmissionBlock(7,90,7,90,"failed",true));
    }
    @Test public void submissionGuardDoesNotInvalidateAnotherContactOrIncomingMessage() {
        for(String status:new String[]{"sending","sent","unknown","failed"}){
            assertNull(SendPolicy.generationAfterSubmissionBlock(7,90,8,90,status,true));
            assertNull(SendPolicy.generationAfterSubmissionBlock(7,90,7,91,status,true));
        }
    }
    @Test public void retryablePreCarrierFailuresAndUnsubmittedTimersDoNotDiscardGeneration() {
        assertNull(SendPolicy.generationAfterSubmissionBlock(7,90,7,90,"failed",false));
        for(String status:new String[]{"draft","scheduled","awaiting_alert","paused","cancelled"})
            assertNull(SendPolicy.generationAfterSubmissionBlock(7,90,7,90,status,false));
    }
}
