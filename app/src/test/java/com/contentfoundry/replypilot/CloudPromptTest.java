package com.contentfoundry.replypilot;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class CloudPromptTest {
    private ReplyPrompt.Message m(long thread,int type,String body){return new ReplyPrompt.Message(thread,type,body);}
    @Test public void excludesOtherPeopleAndUnsentMessages(){
        CloudPrompt.Input p=CloudPrompt.build(1,List.of(m(2,1,"other person's secret"),m(1,2,"my sent text"),m(1,3,"draft secret"),m(1,4,"outbox"),m(1,5,"failed"),m(1,1,"latest incoming")),"coworker","Me: sure","Natural",true);
        assertEquals(2,p.history().size());assertEquals("me",p.history().get(0).speaker());assertEquals("them",p.history().get(1).speaker());assertEquals(List.of("my sent text"),p.style());assertFalse(p.toString().contains("secret"));
    }
    @Test public void latestFiftyCombinedTurnsTeachStyleWithoutOtherThreadsOrUnsentText(){
        List<ReplyPrompt.Message> history=new ArrayList<>();for(int i=0;i<80;i++){history.add(m(1,i%2==0?2:1,"text "+i));history.add(m(2,2,"other person's secret"));history.add(m(1,5,"failed text"));}
        CloudPrompt.Input p=CloudPrompt.build(1,history,"","","Natural",true);assertEquals(50,p.history().size());assertEquals(6,p.style().size());assertEquals("text 30",p.history().get(0).text());assertEquals("text 79",p.history().get(49).text());assertEquals("text 68",p.style().get(0));assertFalse(p.toString().contains("secret"));assertFalse(p.toString().contains("failed text"));
    }
    @Test public void matchingOffKeepsOnlyImmediateContextAndNoHistoricStyle(){
        List<ReplyPrompt.Message> history=new ArrayList<>();for(int i=0;i<80;i++)history.add(m(1,i%2==0?2:1,"text "+i));
        CloudPrompt.Input p=CloudPrompt.build(1,history,"","Me: explicitly supplied","Natural",false);assertEquals(8,p.history().size());assertEquals("text 72",p.history().get(0).text());assertTrue(p.style().isEmpty());assertEquals("Me: explicitly supplied",p.samples());
    }
    @Test public void styleNeverPullsOlderSentTextsOutsideTheLatestFifty(){
        List<ReplyPrompt.Message> history=new ArrayList<>();history.add(m(1,2,"very old style"));for(int i=0;i<50;i++)history.add(m(1,1,"incoming "+i));
        CloudPrompt.Input p=CloudPrompt.build(1,history,"","","Natural",true);assertEquals(50,p.history().size());assertTrue(p.style().isEmpty());assertFalse(p.toString().contains("very old style"));
    }
    @Test public void matchingOffStillPreservesTheEntireUnansweredBurst(){
        List<ReplyPrompt.Message> history=new ArrayList<>();history.add(m(1,2,"older reply"));
        for(int i=0;i<12;i++)history.add(m(1,1,"burst "+i));
        CloudPrompt.Input p=CloudPrompt.build(1,history,"","","Natural",false);
        assertEquals(12,p.history().size());assertEquals("burst 0",p.history().get(0).text());assertEquals("burst 11",p.history().get(11).text());
    }
    @Test public void earlierUnansweredMessagesAreNeverClipped(){
        String correction="x".repeat(1400)+" actually cancel that";
        CloudPrompt.Input p=CloudPrompt.build(1,List.of(m(1,2,"previous reply"),m(1,1,correction),m(1,1,"thanks")),"","","Natural",true);
        assertEquals(correction,p.history().get(1).text());
        assertThrows(IllegalArgumentException.class,()->CloudPrompt.build(1,List.of(m(1,1,"x".repeat(1601)),m(1,1,"thanks")),"","","Natural",true));
    }
    @Test public void overflowingBurstFailsInsteadOfLosingEarlierMessages(){
        List<ReplyPrompt.Message> history=new ArrayList<>();for(int i=0;i<51;i++)history.add(m(1,1,"burst "+i));
        assertThrows(IllegalArgumentException.class,()->CloudPrompt.build(1,history,"","","Natural",true));
    }
    @Test public void disablingRecentStylePreservesExplicitProfileSamples(){
        for(String tone:List.of("Warm","Use AI intuition")) {
            CloudPrompt.Input p=CloudPrompt.build(1,List.of(m(1,2,"old sent"),m(1,1,"hello")),"friend","Me: hey",tone,false);assertTrue(p.style().isEmpty());assertEquals("Me: hey",p.samples());assertEquals("friend",p.relationship());assertEquals(tone,p.tone());
        }
    }
    @Test public void rejectsOutgoingLastAndMissingIncoming(){
        assertThrows(IllegalArgumentException.class,()->CloudPrompt.build(1,List.of(m(1,2,"I already replied")),"","","Natural",true));
        assertThrows(IllegalArgumentException.class,()->CloudPrompt.build(1,List.of(m(2,1,"wrong person")),"","","Natural",true));
    }
    @Test public void neverTruncatesTheMessageBeingAnswered(){assertThrows(IllegalArgumentException.class,()->CloudPrompt.build(1,List.of(m(1,1,"x".repeat(1601))),"","","Natural",true));}
    @Test public void boundsSamplesAndPreservesSurrogatePairs(){
        assertThrows(IllegalArgumentException.class,()->CloudPrompt.samples("x".repeat(8001)));
        CloudPrompt.Input p=CloudPrompt.build(1,List.of(m(1,2,"x".repeat(219)+"😀"+"x".repeat(400)),m(1,1,"hello")),"","","nonsense",true);
        assertEquals(219,p.style().get(0).length());assertTrue(p.history().get(0).text().length()<=600);assertEquals("Natural",p.tone());
    }
    @Test public void profileDoesNotCarryAcrossFreshRequests(){
        CloudPrompt.build(1,List.of(m(1,1,"hey")),"friend","Me: hey","Warm",true);
        CloudPrompt.Input p=CloudPrompt.build(2,List.of(m(2,1,"meeting?")),"","","Professional",true);assertEquals("",p.relationship());assertEquals("",p.samples());assertTrue(p.style().isEmpty());assertEquals("meeting?",p.history().get(0).text());
    }
}
