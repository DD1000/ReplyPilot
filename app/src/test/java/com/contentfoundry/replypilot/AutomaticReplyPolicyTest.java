package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class AutomaticReplyPolicyTest {
    private static final String PROMISE="I'll send you an update when everything is ready.";

    @Test public void newAcknowledgmentsRemainReplyable(){
        assertNull(AutomaticReplyPolicy.reason("Ok",true,List.of(PROMISE),1,null));
        assertNull(AutomaticReplyPolicy.reason("Okay!",true,List.of(PROMISE),1,PROMISE));
    }
    @Test public void standaloneAcknowledgementsAndClosingEmojiCanStaySilent(){
        for(String message:new String[]{" OK. ","thanks!","thank you","Ok thanks","got it","sounds good","👍","👍🏽","🙏","good night","bye"})
            assertTrue(message,AutomaticReplyPolicy.closing(message));
    }
    @Test public void aSubstantiveFollowUpOrQuestionMustNotBeSilencedAsAnAcknowledgement(){
        for(String message:new String[]{"Ok but where?","Thanks, what time?","Okay send the address","Ok?","OK？","Thanks for calling; can you call again","Yes","No","I need help"}){
            assertFalse(message,AutomaticReplyPolicy.closing(message));
            assertNull(AutomaticReplyPolicy.reason(message,true,List.of(PROMISE),1,null));
        }
    }
    @Test public void thanksAfterAnotherIncomingRequestIsLeftForContextualAi(){
        assertNull(AutomaticReplyPolicy.reason("thanks",false,List.of("An older outgoing message"),1,null));
        assertNull(AutomaticReplyPolicy.reason("Ok",false,List.of(),0,null));
    }
    @Test public void wordingComparisonIsNotASourceDuplicateGate(){
        assertNull(AutomaticReplyPolicy.reason("Any update?",true,List.of(PROMISE),1,"I'LL SEND YOU AN UPDATE WHEN EVERYTHING IS READY!"));
        assertTrue(AutomaticReplyPolicy.repeated("I’ll send you an update when everything is ready.",List.of(PROMISE)));
        assertFalse(AutomaticReplyPolicy.repeated("It is ready now; I just emailed it to you.",List.of(PROMISE)));
        assertFalse(AutomaticReplyPolicy.repeated("The amount is 1.5 dollars.",List.of("The amount is 15 dollars.")));
        assertFalse(AutomaticReplyPolicy.repeated("Your appointment is at 12:30 today.",List.of("Your appointment is at 1230 today.")));
    }
    @Test public void repeatedShortAnswersToNewQuestionsRemainPossible(){
        for(String response:new String[]{"Yes","No","Sounds good to me","Ok","Thanks"})
            assertFalse(response,AutomaticReplyPolicy.repeated(response,List.of(response)));
        assertFalse(AutomaticReplyPolicy.repeated("Extraordinarily long confirmation",List.of("Extraordinarily long confirmation")));
    }
    @Test public void newIncomingMessagesDoNotHaveAFiveReplyCap(){
        assertNull(AutomaticReplyPolicy.reason("What time are you coming?",true,List.of(PROMISE),4,null));
        assertNull(AutomaticReplyPolicy.reason("What time are you coming?",true,List.of(PROMISE),5,null));
        assertNull(AutomaticReplyPolicy.reason("Ok",true,List.of(PROMISE),100,null));
        // A store count reset by a confirmed manual send can start a new round.
        assertNull(AutomaticReplyPolicy.reason("What time are you coming?",true,List.of(PROMISE),0,null));
    }
    @Test public void deliberateSilenceIsAnExplicitEmptyBodyDecision(){
        for(String reason:List.of("conversation_complete","repeated_reply","needs_review","insufficient_history","plans_need_input")){
            AutomaticReplyPolicy.Response response=AutomaticReplyPolicy.response(true,"no_reply",reason,"");
            assertEquals("no_reply",response.decision());assertEquals(reason,response.reason());assertEquals("",response.body());
        }
    }
    @Test public void silenceCannotSmuggleASendableFallbackBody(){
        for(String body:new String[]{null," ","Okay, I'll keep replying"})
            assertThrows(IllegalStateException.class,()->AutomaticReplyPolicy.response(true,"no_reply","conversation_complete",body));
        assertThrows(IllegalStateException.class,()->AutomaticReplyPolicy.response(true,"no_reply","reply_needed",""));
        assertThrows(IllegalStateException.class,()->AutomaticReplyPolicy.response(true,"no_reply",null,""));
    }
    @Test public void repliesAndLegacyBodyOnlyResponsesRemainReviewable(){
        assertEquals("Hello",AutomaticReplyPolicy.response(true,"reply","reply_needed"," Hello ").body());
        assertEquals("reply",AutomaticReplyPolicy.response(false,"","","Legacy server reply").decision());
        assertThrows(IllegalStateException.class,()->AutomaticReplyPolicy.response(true,"unknown","reply_needed","Do not use this"));
        assertThrows(IllegalStateException.class,()->AutomaticReplyPolicy.response(true,"reply","repeated_reply","No fallback"));
        assertThrows(IllegalStateException.class,()->AutomaticReplyPolicy.response(false,"","",""));
        assertEquals("needs_review",AutomaticReplyPolicy.response(false,"","","a".repeat(1601)).reason());
    }
    @Test public void planningBodiesAreHeldEvenFromOlderServers(){
        for(boolean structured:new boolean[]{true,false}){
            AutomaticReplyPolicy.Response response=AutomaticReplyPolicy.response(structured,"reply","reply_needed","I can meet you tomorrow.");
            assertEquals("no_reply",response.decision());assertEquals("plans_need_input",response.reason());assertEquals("",response.body());
        }
        assertThrows(IllegalStateException.class,()->AutomaticReplyPolicy.response(true,"no_reply","plans_need_input","Sure, see you then"));
        assertEquals("plans_need_input",AutomaticReplyPolicy.validReason("plans_need_input"));
        assertTrue(AutomaticReplyPolicy.message("plans_need_input").contains("until you send a reply"));
    }
    @Test public void malformedBodyTypesMustNotBecomeSendableStrings(){
        for(Object body:new Object[]{42,true,List.of("Hi"),java.util.Map.of("reply","hello")}){
            assertThrows(IllegalStateException.class,()->AutomaticReplyPolicy.response(true,"reply","reply_needed",body));
            assertThrows(IllegalStateException.class,()->AutomaticReplyPolicy.response(false,null,null,body));
        }
    }
    @Test public void localDecisionMessagesExplainHowToContinueManually(){
        assertTrue(AutomaticReplyPolicy.message("automatic_limit").contains("Send a message yourself"));
        assertTrue(AutomaticReplyPolicy.message("repeated_reply").contains("already sent"));
        assertThrows(IllegalArgumentException.class,()->AutomaticReplyPolicy.validReason("arbitrary reason"));
    }
}
