package com.contentfoundry.replypilot;
import org.junit.Test;
import static org.junit.Assert.*;
public class AutopilotSourcePolicyTest {
    private AutopilotSourcePolicy.Binding source(long id,long sms,long revision,String hash){return new AutopilotSourcePolicy.Binding(10,id,1000,hash,"+12025550147",sms,"sms-proof",revision);}
    @Test public void olderCarrierSubmissionDoesNotSilenceANewSource(){for(String state:new String[]{"sending","sent","unknown","failed"}){assertFalse(AutopilotSourcePolicy.competing(false,state,true));assertTrue(AutopilotSourcePolicy.competing(true,state,true));}assertTrue(AutopilotSourcePolicy.competing(false,"scheduled",false));assertTrue(AutopilotSourcePolicy.competing(false,"awaiting_alert",false));assertFalse(AutopilotSourcePolicy.competing(true,"cancelled",false));}
    @Test public void exactIncomingAndContextRemainValid(){assertTrue(AutopilotSourcePolicy.matches(source(5,8,0,"proof"),source(5,8,0,"proof")));}
    @Test public void newerMmsCannotReuseUnchangedSmsBase(){assertFalse(AutopilotSourcePolicy.matches(source(5,8,0,"proof"),source(6,8,0,"proof")));}
    @Test public void deletionCannotExposeAnOlderReplyableSource(){assertFalse(AutopilotSourcePolicy.matches(source(5,8,0,"proof"),source(4,8,0,"proof")));}
    @Test public void sourcePartsOrTextMutationRejectsSameIds(){assertFalse(AutopilotSourcePolicy.matches(source(5,8,0,"proof"),source(5,8,0,"changed")));}
    @Test public void anyNewSmsOrManualTakeoverInvalidatesMmsWork(){assertFalse(AutopilotSourcePolicy.matches(source(5,8,0,"proof"),source(5,9,0,"proof")));assertFalse(AutopilotSourcePolicy.matches(source(5,8,0,"proof"),source(5,8,1,"proof")));}
    @Test public void missingOrInvalidSourceCannotAuthorizeAnything(){assertFalse(AutopilotSourcePolicy.matches(null,null));assertFalse(AutopilotSourcePolicy.matches(source(0,8,0,"proof"),source(0,8,0,"proof")));assertFalse(AutopilotSourcePolicy.matches(source(5,8,0,""),source(5,8,0,"")));}
    @Test public void recipientAndTimestampArePartOfIdentity(){var original=source(5,8,0,"proof");assertFalse(AutopilotSourcePolicy.matches(original,new AutopilotSourcePolicy.Binding(10,5,1000,"proof","+12025550148",8,"sms-proof",0)));assertFalse(AutopilotSourcePolicy.matches(original,new AutopilotSourcePolicy.Binding(10,5,1001,"proof","+12025550147",8,"sms-proof",0)));}
}
