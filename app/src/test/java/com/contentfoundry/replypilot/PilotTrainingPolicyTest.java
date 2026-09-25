package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PilotTrainingPolicyTest {
    @Test public void firstSimulatedMessageDoesNotTeachAnOwnerReply(){
        var session=new PilotTrainingPolicy.Session(10);session.generated("After work catch-up","how was your day?");
        assertEquals(0,session.answers);assertEquals(1,session.turns.size());assertEquals("them",session.turns.get(0).speaker());assertFalse(session.awaiting);
    }
    @Test public void onlyNativeCurrentTurnTokenCanTeach(){
        var session=new PilotTrainingPolicy.Session(10);session.generated("Catch-up","hey");
        assertThrows(IllegalStateException.class,()->session.accept("invented-token","yo"));assertEquals(0,session.answers);
        assertTrue(session.accept(session.token,"yo"));assertEquals(1,session.answers);assertEquals("me",session.turns.get(1).speaker());
    }
    @Test public void retriesAreIdempotentBeforeAndAfterGeneration(){
        var session=new PilotTrainingPolicy.Session(10);session.generated("Catch-up","hey");String token=session.token;
        assertTrue(session.accept(token,"yo"));assertFalse(session.accept(token,"yo"));String request=session.requestId;
        assertEquals(1,session.answers);session.generated("Catch-up","how's it going?");assertFalse(session.accept(token,"yo"));
        assertEquals(1,session.answers);assertEquals(request,session.requestId);assertNotEquals(token,session.token);
        assertThrows(IllegalStateException.class,()->session.accept(token,"different answer"));
    }
    @Test public void pendingGenerationCannotInventAnotherOwnerTurn(){
        var session=new PilotTrainingPolicy.Session(10);assertThrows(IllegalStateException.class,()->session.accept("","hi"));
        session.generated("Catch-up","hey");session.accept(session.token,"hi");assertThrows(IllegalStateException.class,session::incoming);
        assertThrows(IllegalStateException.class,()->session.accept("other","spam"));assertEquals(2,session.turns.size());
    }
    @Test public void sessionEndsAfterEightGenuineReplies(){
        var session=new PilotTrainingPolicy.Session(10);
        for(int i=0;i<8;i++){session.generated("Catch-up","message "+i);assertTrue(session.accept(session.token,"answer "+i));}
        assertTrue(session.complete());assertFalse(session.awaiting);assertEquals(16,session.turns.size());
        assertThrows(IllegalStateException.class,()->session.generated("Another","more"));
    }
    @Test public void malformedOutputsCannotBecomeSimulatedPractice(){
        var session=new PilotTrainingPolicy.Session(10);
        assertThrows(IllegalStateException.class,()->session.generated("x".repeat(301),"hello"));
        assertThrows(IllegalArgumentException.class,()->session.generated("A scenario","x".repeat(601)));
        assertThrows(IllegalArgumentException.class,()->session.generated("A scenario",null));assertTrue(session.turns.isEmpty());
    }
    @Test public void typedAnswerAndMeaningsAreStrictlyBounded(){
        assertThrows(IllegalArgumentException.class,()->PilotTrainingPolicy.text(3,false));
        assertThrows(IllegalArgumentException.class,()->PilotTrainingPolicy.text(" ",false));
        assertThrows(IllegalArgumentException.class,()->PilotTrainingPolicy.text("x".repeat(601),true));
        assertEquals("",PilotTrainingPolicy.text("  ",true));assertEquals("hi",PilotTrainingPolicy.text(" hi ",false));
    }
    @Test public void examplesNeverCrossThreadsOrReusedThreadAddresses(){
        var rows=List.of(new PilotTrainingPolicy.Example(1,7,"one","hey","hi"),new PilotTrainingPolicy.Example(2,7,"two","secret","private"),new PilotTrainingPolicy.Example(3,8,"one","other contact","other"));
        var result=PilotTrainingPolicy.examples(rows,7,"one");assertEquals(1,result.size());assertEquals("hi",result.get(0).reply());
    }
    @Test public void learnedExamplesAreDistinctNewestAndBounded(){
        List<PilotTrainingPolicy.Example> rows=new ArrayList<>();for(int i=1;i<=40;i++)rows.add(new PilotTrainingPolicy.Example(i,7,"a","question "+i,"reply "+i));
        rows.add(new PilotTrainingPolicy.Example(41,7,"a","question 40","reply 40"));var result=PilotTrainingPolicy.examples(rows,7,"a");
        assertEquals(24,result.size());assertEquals("reply 40",result.get(0).reply());assertEquals("reply 17",result.get(23).reply());
    }
    @Test public void clampedProviderTextDoesNotSplitEmoji(){assertEquals(599,PilotTrainingPolicy.clipped("x".repeat(599)+"😀").length());}
    @Test public void onlyActualReceivedSmsOrRetrievedMmsCanBeExplained(){
        assertTrue(PilotTrainingPolicy.received(7,7,2,2,"sms",1,0));assertTrue(PilotTrainingPolicy.received(7,7,2,2,"mms",1,132));
        assertFalse(PilotTrainingPolicy.received(7,8,2,2,"sms",1,0));assertFalse(PilotTrainingPolicy.received(7,7,2,3,"sms",1,0));
        assertFalse(PilotTrainingPolicy.received(7,7,2,2,"sms",2,0));assertFalse(PilotTrainingPolicy.received(7,7,2,2,"mms",1,130));assertFalse(PilotTrainingPolicy.received(7,7,2,2,"rcs",1,132));
    }
    @Test public void oldOrClockReversedSessionsExpire(){assertTrue(PilotTrainingPolicy.fresh(10,10));assertTrue(PilotTrainingPolicy.fresh(10,10+PilotTrainingPolicy.MAX_AGE));assertFalse(PilotTrainingPolicy.fresh(10,9));assertFalse(PilotTrainingPolicy.fresh(10,11+PilotTrainingPolicy.MAX_AGE));}
    @Test public void retiredAboutMeNotesCannotInfluenceNanoPrompts(){
        var prompt=ReplyPrompt.prepare(7,List.of(new ReplyPrompt.Message(7,1,"hi")),"Myself (beta)",true,"","RETIRED_PRIVATE_NOTE","natural");
        assertFalse(prompt.text().contains("RETIRED_PRIVATE_NOTE"));assertFalse(prompt.text().contains("owner_personality_json"));assertTrue(prompt.text().contains("Train Pilot"));
    }
    @Test public void invisibleOnlyTextCannotPoisonFutureRelayGuidance(){
        for(String invisible:List.of("\u200b","\u202e","\u0000\u007f\u009f\ufeff","\u00a0")){
            assertThrows(IllegalArgumentException.class,()->PilotTrainingPolicy.text(invisible,false));
            assertEquals("",PilotTrainingPolicy.clipped(invisible));
        }
        assertEquals("fine\nokay",PilotTrainingPolicy.text("\u200bfine\u202e\nokay\ufeff",false));
        assertEquals("",PilotTrainingPolicy.text("\u2066",true));
    }
    @Test public void oldEmptyAfterCleanupPairsAreExcluded(){
        var rows=List.of(new PilotTrainingPolicy.Example(1,7,"one","\u200b","okay"),new PilotTrainingPolicy.Example(2,7,"one","hey","\u202e"),new PilotTrainingPolicy.Example(3,7,"one","hi","fine"));
        var examples=PilotTrainingPolicy.examples(rows,7,"one");assertEquals(1,examples.size());assertEquals("fine",examples.get(0).reply());
    }
}
