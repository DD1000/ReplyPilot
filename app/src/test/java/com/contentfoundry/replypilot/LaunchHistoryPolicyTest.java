package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class LaunchHistoryPolicyTest {
    @Test public void unavailableMmsTextRemainsExplicitInTheLaunchCache(){
        var missing=new LaunchHistoryPolicy.Text(7,1,1000,"mms",1,1,"",132,1,false,true);
        var loaded=LaunchHistoryPolicy.messages(List.of(missing),7).get(0);
        assertEquals("",loaded.body());assertTrue(loaded.textUnavailable());
        var partial=new LaunchHistoryPolicy.Text(7,2,2000,"mms",1,1,"Known caption",132,1,false,true);
        assertEquals("Known caption",LaunchHistoryPolicy.messages(List.of(partial),7).get(0).body());
    }
    private static LaunchHistoryPolicy.Text sms(long thread,long id,long date,String body){return new LaunchHistoryPolicy.Text(thread,id,date,"sms",1,0,body,-1,-1,false);}
    private static LaunchHistoryPolicy.Thread thread(long id,long date){return new LaunchHistoryPolicy.Thread(id,date,id,"sms","+12025550100","Fictional person");}
    @Test public void latestTenThreadsIgnoreInputOrPinOrdering(){List<LaunchHistoryPolicy.Thread> input=new ArrayList<>();for(int i=1;i<=20;i++)input.add(thread(i,i));Collections.reverse(input);input.add(0,thread(1,1));assertEquals(List.of(20L,19L,18L,17L,16L,15L,14L,13L,12L,11L),LaunchHistoryPolicy.threads(input).stream().map(LaunchHistoryPolicy.Thread::id).toList());}
    @Test public void duplicateThreadKeepsNewestPosition(){assertEquals(100,LaunchHistoryPolicy.threads(List.of(thread(1,1),thread(1,100))).get(0).date());}
    @Test public void invalidThreadRecordsAreExcluded(){assertTrue(LaunchHistoryPolicy.threads(List.of(thread(0,0),thread(1,-1),thread(Long.MAX_VALUE,1),new LaunchHistoryPolicy.Thread(2,2,2,"unknown","",""))).isEmpty());}
    @Test public void lastThirtyMessagesRemainChronological(){List<LaunchHistoryPolicy.Text> rows=new ArrayList<>();for(int i=45;i>=1;i--)rows.add(sms(1,i,i,"message"));List<LaunchHistoryPolicy.Text> saved=LaunchHistoryPolicy.messages(rows,1);assertEquals(30,saved.size());assertEquals(16,saved.get(0).id());assertEquals(45,saved.get(29).id());}
    @Test public void duplicateMessageDoesNotConsumeCapacity(){assertEquals(1,LaunchHistoryPolicy.messages(List.of(sms(1,1,10,"a"),sms(1,1,10,"a")),1).size());}
    @Test public void idsInDifferentTransportsStayDistinct(){var mms=new LaunchHistoryPolicy.Text(1,1,11,"mms",1,0,"mms text",132,1,false);assertEquals(2,LaunchHistoryPolicy.messages(List.of(sms(1,1,10,"sms text"),mms),1).size());}
    @Test public void otherThreadCannotEnterSavedPage(){assertTrue(LaunchHistoryPolicy.messages(List.of(sms(2,1,1,"wrong conversation")),1).isEmpty());}
    @Test public void invalidPositionsAndTypesCannotPersist(){assertTrue(LaunchHistoryPolicy.messages(List.of(sms(1,0,1,"bad"),sms(1,1,-1,"bad"),new LaunchHistoryPolicy.Text(1,1,1,"sms",99,0,"bad",0,0,false)),1).isEmpty());}
    @Test public void draftsAndProtocolReportsAreExcluded(){assertTrue(LaunchHistoryPolicy.messages(List.of(new LaunchHistoryPolicy.Text(1,1,1,"sms",3,0,"draft",0,0,false),new LaunchHistoryPolicy.Text(1,2,2,"mms",2,0,"report",134,2,false),new LaunchHistoryPolicy.Text(1,3,3,"mms",2,0,"draft",128,3,false)),1).isEmpty());}
    @Test public void mmsTextSurvivesAndEmptyMediaHasHonestPlaceholder(){var text=new LaunchHistoryPolicy.Text(1,1,1,"mms",1,0,"Meet you there",132,1,false);var photo=new LaunchHistoryPolicy.Text(1,2,2,"mms",1,0,"",132,1,false);var pending=new LaunchHistoryPolicy.Text(1,3,3,"mms",1,0,"",130,1,false);var saved=LaunchHistoryPolicy.messages(List.of(text,photo,pending),1);assertEquals("Meet you there",saved.get(0).body());assertEquals("Media message",saved.get(1).body());assertEquals("Media message — download pending",saved.get(2).body());}
    @Test public void longTextsAreBoundedWithoutSplittingEmoji(){String text="x".repeat(2047)+"😀rest";var saved=LaunchHistoryPolicy.messages(List.of(sms(1,1,1,text)),1).get(0);assertEquals(2047,saved.body().length());assertTrue(saved.truncated());assertFalse(Character.isHighSurrogate(saved.body().charAt(2046)));}
    @Test public void textPreservesLinesButRemovesControlBytes(){assertEquals("first\nsecond\tend",LaunchHistoryPolicy.text("first\u0000\nsecond\tend\u0007"));}
    @Test public void emptyProviderHistoryStaysEmpty(){assertTrue(LaunchHistoryPolicy.messages(List.of(),1).isEmpty());}
    @Test public void returnedSelectionsAreImmutable(){var saved=LaunchHistoryPolicy.messages(List.of(sms(1,1,1,"x")),1);assertThrows(UnsupportedOperationException.class,()->saved.clear());}
}
