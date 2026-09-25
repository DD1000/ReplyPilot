package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class GirlfriendPolicyTest {
    @Test public void directBedtimeAndGoodnightPhrasesPause(){
        for(String text:new String[]{"Goodnight","good night","night","night-night","gn","Goodnight babe ❤️","I'm going to bed","I am heading to bed","Headed to bed","Off to bed","Going to sleep","I need to sleep","Calling it a night","okay im going to bed","alright, I’m gonna go to sleep","I'm about to go to bed","I should get some sleep","ＧＯＯＤＮＩＧＨＴ","goodnight love you","goodnight I love you","goodnight see you tomorrow","I'm going to sleep goodnight"})assertTrue(text,GirlfriendPolicy.bedtime(text));
    }
    @Test public void directRequestsToStopAreBoundaries(){
        for(String text:new String[]{"Let me sleep","Stop texting me","Please stop messaging me","Leave me alone","Don't text me anymore","Can you please stop contacting me?","Let's stop talking","I don't want to talk anymore","I need some space","stop","please stop"})assertTrue(text,GirlfriendPolicy.bedtime(text));
    }
    @Test public void negationHistoricalExamplesAndNightAnecdotesDoNotPause(){
        for(String text:new String[]{"I'm not going to bed","Okay, I'm not going to bed","I was going to bed when the dog barked","She said goodnight yesterday","Goodnight is a strange word","The night sky looks beautiful","The movie was called Goodnight","I'm going to bed tomorrow at eight","I'm going to bed if the movie finishes","the stop sign","stop sign"})assertFalse(text,GirlfriendPolicy.bedtime(text));
    }
    @Test public void QuotedAndCodeExamplesDoNotBecomeCurrentInstructions(){
        for(String text:new String[]{"She wrote \"I'm going to bed\" yesterday","\"Goodnight\"","‘Goodnight’","'goodnight'","> Goodnight","```text\nGoodnight\n```","```text\n\"a quote\"\nGoodnight"})assertFalse(text,GirlfriendPolicy.bedtime(text));
        assertTrue(GirlfriendPolicy.bedtime("She wrote \"Goodnight\". I'm going to bed."));
    }
    @Test public void BedtimeInMixedMessageWinsOverItsPlanningOrQuestionClause(){
        for(String text:new String[]{"Goodnight, dinner tomorrow?","I'm going to bed. Dinner tomorrow?","Goodnight, when should we meet tomorrow?","Tomorrow sounds good. Goodnight!","Goodnight！Dinner tomorrow？"}){
            assertTrue(text,GirlfriendPolicy.bedtime(text));assertFalse(text,GirlfriendPolicy.shouldResume(text));
        }
    }
    @Test public void acknowledgmentsReactionsAndEmojisDoNotResume(){
        for(String text:new String[]{"Okay","Okay?","Okay ❤️","alright 😂","thanks ❤️","❤️","👍🏽","🙏","lol","love you","I love you ❤️","gn","Goodnight babe","...",""})assertFalse(text,GirlfriendPolicy.shouldResume(text));
    }
    @Test public void newSubstantiveMessagesCanResume(){
        for(String text:new String[]{"What did you think of the movie?","Good morning!","You awake?","Okay, but where is the address?","I'm not going to bed","How was your day?","Why?"})assertTrue(text,GirlfriendPolicy.shouldResume(text));
    }
    @Test public void onlyNewerIncomingBaseCanChangeThePause(){
        var stopped=GirlfriendPolicy.next(new GirlfriendPolicy.State(0,false),10,"Goodnight",true);
        assertEquals(new GirlfriendPolicy.State(10,true),stopped);
        assertEquals(stopped,GirlfriendPolicy.next(stopped,9,"Are you there?",true));
        assertEquals(stopped,GirlfriendPolicy.next(stopped,10,"Are you there?",true));
        assertEquals(stopped,GirlfriendPolicy.next(stopped,0,"Are you there?",true));
    }
    @Test public void pauseSurvivesReactionBurstThenResumesOnSubstantiveIncoming(){
        var state=GirlfriendPolicy.next(null,10,"Goodnight",true);
        state=GirlfriendPolicy.next(state,11,"❤️",true);assertTrue(state.paused());
        state=GirlfriendPolicy.next(state,12,"Okay ❤️",true);assertTrue(state.paused());
        state=GirlfriendPolicy.next(state,13,"love you",true);assertTrue(state.paused());
        // Reconstructing these persisted fields is enough to retain the pause.
        state=new GirlfriendPolicy.State(state.base(),state.paused());
        state=GirlfriendPolicy.next(state,14,"How was the movie?",true);assertFalse(state.paused());assertEquals(14,state.base());
    }
    @Test public void manualResumeRetainsWatermarkSoPollingCannotRepause(){
        var state=GirlfriendPolicy.resumed(new GirlfriendPolicy.State(10,true));
        assertFalse(state.paused());assertEquals(10,state.base());
        assertFalse(GirlfriendPolicy.next(state,10,"Goodnight",true).paused());
        assertTrue(GirlfriendPolicy.next(state,11,"Goodnight",true).paused());
    }
    @Test public void leavingModeClearsPauseWithoutConsumingAnUnprocessedBedtime(){
        var state=GirlfriendPolicy.next(new GirlfriendPolicy.State(10,true),12,"Goodnight",false);
        assertEquals(new GirlfriendPolicy.State(10,false),state);
        assertTrue(GirlfriendPolicy.next(state,12,"Goodnight",true).paused());
    }
    @Test public void ordinaryReactionsDoNotCreateANewPauseWithoutBedtime(){
        assertEquals(new GirlfriendPolicy.State(10,false),GirlfriendPolicy.next(null,10,"Okay ❤️",true));
        assertThrows(IllegalArgumentException.class,()->new GirlfriendPolicy.State(-1,true));
    }
}
