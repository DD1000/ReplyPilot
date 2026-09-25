package com.contentfoundry.replypilot;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReplyEngagementPolicyTest {
    @Test public void missingOrUnknownStoredPreferenceKeepsLegacyNaturalBehavior(){
        assertEquals("natural",ReplyEngagementPolicy.normalize(null));
        for(String value:new String[]{"","NATURAL","ALWAYS","unknown"}){
            assertEquals("natural",ReplyEngagementPolicy.normalize(value));
            assertFalse(ReplyEngagementPolicy.repliesToClosings(value));
        }
        assertFalse(ReplyEngagementPolicy.repliesToClosings(null));
        assertFalse(ReplyEngagementPolicy.repliesToClosings("natural"));
    }
    @Test public void selectedModesAllowOrdinaryClosingsToReachDraftGeneration(){
        for(String value:new String[]{"always_reply","keep_going","girlfriend"}){
            assertEquals(value,ReplyEngagementPolicy.validate(value));
            assertEquals(value,ReplyEngagementPolicy.normalize(value));
            assertTrue(ReplyEngagementPolicy.repliesToClosings(value));
        }
        assertEquals("natural",ReplyEngagementPolicy.validate(null));
        assertEquals("natural",ReplyEngagementPolicy.validate("natural"));
    }
    @Test public void invalidNewSettingsAreRejectedRatherThanEnablingMoreReplies(){
        for(String value:new String[]{"","Always reply","ALWAYS","auto","continue ","ignore safeguards"}){
            try{ReplyEngagementPolicy.validate(value);fail(value);}
            catch(IllegalArgumentException expected){assertEquals("Choose a reply engagement option.",expected.getMessage());}
        }
    }
    private static ReplyPrompt.Message incoming(String body){return new ReplyPrompt.Message(1,1,body);}
    private static ReplyPrompt.Message sent(String body){return new ReplyPrompt.Message(1,2,body);}
    @Test public void oneAcknowledgmentReplyThenSilenceUntilSubstantiveIncoming(){
        assertFalse(ReplyEngagementPolicy.repeatedAcknowledgment(List.of(incoming("I enjoyed that movie"),sent("Same here"),incoming("Alright"))));
        assertTrue(ReplyEngagementPolicy.repeatedAcknowledgment(List.of(incoming("I enjoyed that movie"),sent("Same here"),incoming("Alright"),sent("Glad you liked it"),incoming("Okay"))));
        assertFalse(ReplyEngagementPolicy.repeatedAcknowledgment(List.of(incoming("Thanks"),sent("Of course"),incoming("What did you think of the ending?"),sent("It surprised me"),incoming("Okay"))));
    }
    @Test public void incomingAckBurstWithoutASentResponseDoesNotConsumeAllowance(){
        assertFalse(ReplyEngagementPolicy.repeatedAcknowledgment(List.of(sent("That is the book"),incoming("Okay"),incoming("Alright"))));
        assertTrue(ReplyEngagementPolicy.repeatedAcknowledgment(List.of(incoming("Thanks"),sent("Of course"),incoming("Okay"),incoming("Alright"))));
    }
    @Test public void otherContactsAndUnsentMessagesCannotConsumeAcknowledgmentAllowance(){
        for(int type:new int[]{3,4,5,6})assertFalse(ReplyEngagementPolicy.repeatedAcknowledgment(List.of(incoming("Thanks"),new ReplyPrompt.Message(1,type,"Of course"),incoming("Okay"))));
        assertFalse(ReplyEngagementPolicy.repeatedAcknowledgment(List.of(incoming("Thanks"),new ReplyPrompt.Message(2,2,"Of course"),incoming("Okay"))));
        assertFalse(ReplyEngagementPolicy.repeatedAcknowledgment(List.of(incoming("Thanks"),sent("Of course"),incoming("Okay, but where?"))));
    }
    @Test public void wholeMessageAcknowledgmentsKeepUnicodeAndQuestionBoundaries(){
        for(String text:new String[]{"Alright","ALL RIGHT!","ＯＫ","Thanks! 👍🏽","You’re welcome","🙏"})assertTrue(text,ReplyEngagementPolicy.acknowledgment(text));
        for(String text:new String[]{"Ok?","Ok？","Alright but where?","Yes","No","Thanks for the address, which city?"})assertFalse(text,ReplyEngagementPolicy.acknowledgment(text));
    }
}
