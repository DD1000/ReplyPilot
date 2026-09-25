package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ReplyEligibilityTest {
    private List<ChatLog.Turn> turns(int owner,int incoming){
        List<ChatLog.Turn> turns=new ArrayList<>();
        for(int i=0;i<owner;i++)turns.add(new ChatLog.Turn("Me","My personal message "+i));
        for(int i=0;i<incoming;i++)turns.add(new ChatLog.Turn("Them","Their personal message "+i));
        return turns;
    }
    @Test public void requiresTwentyDistinctTurnsAndFiveFromEachPerson(){
        assertTrue(ReplyEligibility.evaluate(turns(5,15)).eligible());
        assertTrue(ReplyEligibility.evaluate(turns(15,5)).eligible());
        assertFalse(ReplyEligibility.evaluate(turns(5,14)).eligible());
        assertFalse(ReplyEligibility.evaluate(turns(4,30)).eligible());
        assertFalse(ReplyEligibility.evaluate(turns(30,4)).eligible());
    }
    @Test public void overlappingSmsAndImportedLogsCannotDoubleReadiness(){
        List<ChatLog.Turn> turns=turns(5,5);turns.addAll(new ArrayList<>(turns));
        ReplyEligibility.Result result=ReplyEligibility.evaluate(turns);
        assertEquals(10,result.total());assertEquals(5,result.owner());assertEquals(5,result.incoming());assertFalse(result.eligible());
    }
    @Test public void caseWhitespaceAndApostropheChangesStillCountOncePerSpeaker(){
        ReplyEligibility.Result result=ReplyEligibility.evaluate(List.of(new ChatLog.Turn("Me","I’ll be there!"),new ChatLog.Turn("Me"," I'LL  BE\nTHERE. "),new ChatLog.Turn("Them","I'll be there")));
        assertEquals(2,result.total());assertEquals(1,result.owner());assertEquals(1,result.incoming());
    }
    @Test public void meaningfulDecimalDifferencesAreNotCollapsed(){
        assertEquals(2,ReplyEligibility.evaluate(List.of(new ChatLog.Turn("Me","It costs 1.5 dollars"),new ChatLog.Turn("Me","It costs 15 dollars"))).owner());
    }
    @Test public void mediaPlaceholdersAndUnknownSpeakersAreNotHistory(){
        List<ChatLog.Turn> turns=new ArrayList<>();
        for(String body:List.of(" ","<Media omitted>","[image omitted]","This message was deleted.","Attachment"))turns.add(new ChatLog.Turn("Me",body));
        turns.add(new ChatLog.Turn("Assistant","A generated response"));turns.add(null);
        assertEquals(0,ReplyEligibility.evaluate(turns).total());
    }
    @Test public void onlyLabeledSamplesAddEvidenceToRealSms(){
        List<ChatLog.Turn> combined=turns(4,15);
        combined.addAll(ChatLog.parseCanonical("I always text casually and use emojis"));
        assertFalse(ReplyEligibility.evaluate(combined).eligible());
        combined.addAll(ChatLog.parseCanonical("Me: That was a fun lunch yesterday."));
        assertTrue(ReplyEligibility.evaluate(combined).eligible());
    }
}
