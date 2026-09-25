package com.contentfoundry.replypilot;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlanDeferralPolicyTest {
    private static final List<String> JOSH=List.of("I'll let you know in a bit.","I'll let you know in a bit.","I'll let you know in a bit.");

    @Test public void autopilotDefersTwiceThenWaitsForTheOwner(){
        assertTrue(PlanDeferralPolicy.reply(0));
        assertTrue(PlanDeferralPolicy.reply(1));
        assertFalse(PlanDeferralPolicy.reply(2));
        assertFalse(PlanDeferralPolicy.reply(3));
        assertEquals(0,PlanDeferralPolicy.requestCount(0));
        assertEquals(1,PlanDeferralPolicy.requestCount(1));
        assertEquals(1,PlanDeferralPolicy.requestCount(5));
    }
    @Test public void onlyDeferralsAfterTheOwnersLatestReplyAndWithinTheWindowCount(){
        long now=100L*PlanDeferralPolicy.WINDOW_MS;
        assertEquals(now-PlanDeferralPolicy.WINDOW_MS,PlanDeferralPolicy.since(now,0,0));
        assertEquals(now-5,PlanDeferralPolicy.since(now,now-5,0));
        assertEquals(now-3,PlanDeferralPolicy.since(now,now-9,now-3));
        assertEquals(0,PlanDeferralPolicy.since(5,0,0));
    }
    @Test public void fallbacksDifferBetweenFirstAndSecondAndNeverRepeatWhatWasSent(){
        String first=PlanDeferralPolicy.fallback(0,JOSH),second=PlanDeferralPolicy.fallback(1,JOSH);
        assertNotEquals(first,second);
        assertNotEquals("I'll let you know in a bit.",first);
        assertNotEquals(first,PlanDeferralPolicy.fallback(0,List.of(first)));
        assertNotEquals(second,PlanDeferralPolicy.fallback(1,List.of(second.toUpperCase()+"!!")));
        for(int prior=0;prior<2;prior++){
            List<String> earlier=new ArrayList<>();
            for(int i=0;i<3;i++){String body=PlanDeferralPolicy.fallback(prior,earlier);assertFalse(body,PlanSafety.commitment(body));assertFalse(body,RequestSafety.unsuitableReply(body));assertFalse(body,earlier.contains(body));earlier.add(body);}
        }
    }
    @Test public void theModelsOwnDeferralIsUsedOnlyWhenSafeAndNew(){
        AutopilotPolicy.Reply ok=AutopilotPolicy.planDeferral("reply","reply_needed","  lol idk yet gimme a sec ",0,JOSH);
        assertEquals("lol idk yet gimme a sec",ok.body());assertTrue(ok.attentionNeeded());assertEquals("plans",ok.attentionReason());
        for(String unsafe:new String[]{"I'm free saturday","sounds good, see you at 7","I'll be there","i'm at work rn","i'll let you know in a bit","", "x".repeat(361)}){
            AutopilotPolicy.Reply r=AutopilotPolicy.planDeferral("reply","reply_needed",unsafe,1,JOSH);
            assertEquals(unsafe,PlanDeferralPolicy.fallback(1,JOSH),r.body());assertEquals("plans",r.attentionReason());
        }
        assertEquals(PlanDeferralPolicy.fallback(0,JOSH),AutopilotPolicy.planDeferral("no_reply","plans_need_input","",0,JOSH).body());
        assertEquals(PlanDeferralPolicy.fallback(0,JOSH),AutopilotPolicy.planDeferral("reply","reply_needed",null,0,JOSH).body());
    }
    @Test public void quietNoticeNamesThePersonAndSaysWhatHappened(){
        assertEquals("Josh is still asking about plans. Autopilot already put it off twice, so it's waiting for you to reply.",PlanDeferralPolicy.silentNotice(" Josh "));
        assertTrue(PlanDeferralPolicy.silentNotice(null).startsWith("They are still asking"));
    }
}
