package com.contentfoundry.replypilot;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReplyReadinessPolicyTest {
    private static final long THREAD=12;
    private static ReplyReadinessPolicy.Entry sms(long id,int type,String body,boolean automatic){return new ReplyReadinessPolicy.Entry(THREAD,"sms",id,100_000-id,type,0,body,automatic,true,true);}
    private static ReplyReadinessPolicy.Entry mms(long id,int type,String body){return new ReplyReadinessPolicy.Entry(THREAD,"mms",id,100_000-id,type,type==1?132:128,body,false,true,true);}
    private static final class Rows implements ReplyReadinessPolicy.Source {
        final List<ReplyReadinessPolicy.Entry> rows;int position=-1,read;boolean complete=true;
        Rows(List<ReplyReadinessPolicy.Entry> rows){this.rows=rows;}
        public boolean advance(){return ++position<rows.size();}
        public ReplyReadinessPolicy.Entry read(){read++;return rows.get(position);}
        public boolean complete(){return complete;}
    }
    private static ReplyEligibility.Result check(List<ReplyReadinessPolicy.Entry> sms,List<ReplyReadinessPolicy.Entry> mms){return ReplyReadinessPolicy.collect(THREAD,List.of(),new Rows(sms),new Rows(mms),()->{});}
    @Test public void fourSmsRowsPlusExistingMmsHistoryMeetUnchangedThresholds(){
        List<ReplyReadinessPolicy.Entry> sms=List.of(sms(1,2,"I can bring some food",false),sms(2,1,"That sounds fun",false),sms(3,1,"I have the drinks",false),sms(4,1,"See you after work",false));
        List<ReplyReadinessPolicy.Entry> mms=new ArrayList<>();for(int i=0;i<16;i++)mms.add(mms(i+1,i<4?2:1,"Personal MMS text "+i));
        ReplyEligibility.Result result=check(sms,mms);assertTrue(result.eligible());assertEquals(20,result.total());assertEquals(5,result.owner());assertEquals(15,result.incoming());assertTrue(result.countsAreMinimum());
    }
    @Test public void olderOwnerRepliesPastTheNewestFiftyAreUsed(){
        List<ReplyReadinessPolicy.Entry> rows=new ArrayList<>();for(int i=0;i<80;i++)rows.add(sms(i+1,1,"Current incoming "+i,false));for(int i=0;i<5;i++)rows.add(sms(81+i,2,"Earlier owner reply "+i,false));
        ReplyEligibility.Result result=check(rows,List.of());assertTrue(result.eligible());assertEquals(5,result.owner());assertTrue(result.countsAreMinimum());
    }
    @Test public void newerSmsFloodDoesNotConsumeBudgetBeforeOlderMms(){
        List<ReplyReadinessPolicy.Entry> rows=new ArrayList<>();for(int i=0;i<5000;i++)rows.add(sms(i+1,1,"okay",false));List<ReplyReadinessPolicy.Entry> mms=new ArrayList<>();for(int i=0;i<20;i++)mms.add(mms(i+1,i<5?2:1,"Older meaningful MMS "+i));
        Rows first=new Rows(rows),second=new Rows(mms);ReplyEligibility.Result result=ReplyReadinessPolicy.collect(THREAD,List.of(),first,second,()->{});assertTrue(result.eligible());assertTrue(first.read<50);assertTrue(second.read<=20);
    }
    @Test public void automatedSmsAreExcludedWithoutDiscardingMmsWithTheSameId(){
        List<ReplyReadinessPolicy.Entry> sms=new ArrayList<>(),mms=new ArrayList<>();for(int i=1;i<=20;i++){sms.add(sms(i,2,"Automatic owner reply "+i,true));mms.add(mms(i,i<=5?2:1,"Actual manually exchanged text "+i));}
        ReplyEligibility.Result result=check(sms,mms);assertTrue(result.eligible());assertEquals(5,result.owner());assertEquals(20,result.total());assertFalse(check(sms,List.of()).eligible());
    }
    @Test public void repeatedTextsAndImportedDuplicatesCannotManufactureHistory(){
        List<ReplyReadinessPolicy.Entry> sms=new ArrayList<>(),mms=new ArrayList<>();List<ChatLog.Turn> imported=new ArrayList<>();for(int i=0;i<10;i++){String text="Real personal exchange "+i;sms.add(sms(i+1,i<5?2:1,text,false));mms.add(mms(i+1,i<5?2:1,text+"!"));imported.add(new ChatLog.Turn(i<5?"Me":"Them",text));}
        ReplyEligibility.Result result=ReplyReadinessPolicy.collect(THREAD,imported,new Rows(sms),new Rows(mms),()->{});assertFalse(result.eligible());assertEquals(10,result.total());assertTrue(result.scanComplete());
    }
    @Test public void noticesUnsentMediaAndForeignRecipientsDoNotCount(){
        var base=mms(1,1,"real");List<ReplyReadinessPolicy.Entry> rejected=List.of(
            new ReplyReadinessPolicy.Entry(THREAD,"mms",1,2,1,130,"notification",false,true,true),
            new ReplyReadinessPolicy.Entry(THREAD,"mms",2,2,4,128,"unsent",false,true,true),
            new ReplyReadinessPolicy.Entry(THREAD,"mms",3,2,1,132,"",false,true,true),
            new ReplyReadinessPolicy.Entry(THREAD,"mms",4,2,1,132,"missing",false,true,false),
            new ReplyReadinessPolicy.Entry(THREAD,"sms",5,2,1,0,"wrong recipient",false,false,true),
            new ReplyReadinessPolicy.Entry(THREAD+1,"sms",6,2,1,0,"other thread",false,true,true));
        assertEquals(0,check(List.of(),rejected).total());assertTrue(ReplyReadinessPolicy.eligible(THREAD,base));
    }
    @Test public void scanStopsWithinBudgetAndReportsIncompleteInsteadOfPretendingNoHistory(){
        List<ReplyReadinessPolicy.Entry> rows=new ArrayList<>();for(int i=0;i<5000;i++)rows.add(sms(i+1,1,"Only repeated incoming",false));Rows source=new Rows(rows);
        ReplyEligibility.Result result=ReplyReadinessPolicy.collect(THREAD,List.of(),source,new Rows(List.of()),()->{});assertFalse(result.eligible());assertFalse(result.scanComplete());assertTrue(result.countsAreMinimum());assertEquals(ReplyReadinessPolicy.MAX_ROWS,source.read);
    }
    @Test public void accessRevocationAbortsRatherThanReturningPartialEligibility(){
        List<ReplyReadinessPolicy.Entry> rows=new ArrayList<>();for(int i=0;i<30;i++)rows.add(sms(i+1,i<10?2:1,"valid message "+i,false));AtomicInteger calls=new AtomicInteger();
        assertThrows(SecurityException.class,()->ReplyReadinessPolicy.collect(THREAD,List.of(),new Rows(rows),new Rows(List.of()),()->{if(calls.incrementAndGet()==8)throw new SecurityException("revoked");}));
    }
    @Test public void sufficientLabeledSamplesDoNotTriggerUnnecessaryProviderReads(){
        List<ChatLog.Turn> samples=new ArrayList<>();for(int i=0;i<20;i++)samples.add(new ChatLog.Turn(i<5?"Me":"Them","Real imported message "+i));Rows first=new Rows(List.of()),second=new Rows(List.of());
        assertTrue(ReplyReadinessPolicy.collect(THREAD,samples,first,second,()->{}).eligible());assertEquals(0,first.read+second.read);
    }
    @Test public void missingTextPartsReportIncompleteWhenEvidenceIsInsufficient(){
        Rows mms=new Rows(List.of());mms.complete=false;ReplyEligibility.Result result=ReplyReadinessPolicy.collect(THREAD,List.of(),new Rows(List.of()),mms,()->{});assertFalse(result.scanComplete());assertTrue(result.countsAreMinimum());assertEquals(0,result.total());
    }
}
