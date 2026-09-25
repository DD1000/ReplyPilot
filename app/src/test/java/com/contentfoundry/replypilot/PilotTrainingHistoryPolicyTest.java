package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class PilotTrainingHistoryPolicyTest {
    private static PilotTrainingHistoryPolicy.Entry row(long thread,String kind,long id,long storedDate,int type,String text){
        return new PilotTrainingHistoryPolicy.Entry(thread,new MediaHistoryPolicy.Position(MediaHistoryPolicy.date(kind,storedDate),kind,id),type,"mms".equals(kind)?type==1?132:128:0,text);
    }
    private static class FakeSource implements PilotTrainingHistoryPolicy.TextSource {
        final List<PilotTrainingHistoryPolicy.Entry> rows;int index=-1,reads;
        FakeSource(List<PilotTrainingHistoryPolicy.Entry> rows){this.rows=new ArrayList<>(rows);this.rows.sort(Comparator.comparing(PilotTrainingHistoryPolicy.Entry::position,MediaHistoryPolicy::compare).reversed());}
        public void advance(){index++;}
        public MediaHistoryPolicy.Position position(){return index<rows.size()?rows.get(index).position():null;}
        public PilotTrainingHistoryPolicy.Entry text(){reads++;return rows.get(index);}
    }
    private static List<PilotTrainingHistoryPolicy.Entry> latest(FakeSource sms,FakeSource mms){return PilotTrainingHistoryPolicy.newest(7,sms,mms,50,()->{});}

    @Test public void mixedSmsAndTextOnlyMmsAreOrderedByTimeNotTransport(){
        var sms=new FakeSource(List.of(row(7,"sms",1,1100,1,"SMS reply"),row(7,"sms",3,2500,2,"sent SMS")));
        var mms=new FakeSource(List.of(row(7,"mms",1,1,1,"incoming plain text MMS"),row(7,"mms",3,2,2,"sent MMS caption")));
        var result=PilotTrainingHistoryPolicy.select(7,latest(sms,mms),null);
        assertEquals(List.of("incoming plain text MMS","SMS reply","sent MMS caption","sent SMS"),result.stream().map(PilotTrainingHistoryPolicy.Entry::text).toList());
        assertTrue(PilotTrainingHistoryPolicy.hasIncoming(result));
    }
    @Test public void latestFiftyCanUseOlderIncomingAfterLongOutgoingBurst(){
        List<PilotTrainingHistoryPolicy.Entry> sent=new ArrayList<>();for(int i=1;i<=80;i++)sent.add(row(7,"sms",i,100_000+i,2,"sent "+i));
        var newest=latest(new FakeSource(sent),new FakeSource(List.of()));
        assertEquals(50,newest.size());assertFalse(PilotTrainingHistoryPolicy.hasIncoming(newest));
        var oldIncoming=row(7,"mms",99,1,1,"older but useful contact text");
        var result=PilotTrainingHistoryPolicy.select(7,newest,oldIncoming);
        assertEquals(50,result.size());assertEquals(oldIncoming,result.get(0));assertEquals("sent 32",result.get(1).text());assertEquals("sent 80",result.get(49).text());
    }
    @Test public void incomingQueryReachesPastOutgoingRowsWithoutDependingOnRecentFifty(){
        var sms=PilotTrainingHistoryPolicy.query(7,"sms",true);var mms=PilotTrainingHistoryPolicy.query(7,"mms",true);
        assertEquals("thread_id=? AND type=1 AND body IS NOT NULL AND length(body)>0",sms.selection());
        assertEquals("thread_id=? AND msg_box=1 AND m_type=132",mms.selection());
        for(var query:List.of(sms,mms)){assertEquals(List.of("7"),query.arguments());assertEquals("date DESC, _id DESC",query.order());assertFalse(query.selection().contains("date>"));}
    }
    @Test public void ordinaryTrainingQueriesExcludeQueuedUnsentDraftsAndMmsDownloadNotices(){
        assertTrue(PilotTrainingHistoryPolicy.query(7,"sms",false).selection().contains("type IN (1,2)"));
        assertEquals("thread_id=? AND ((msg_box=1 AND m_type=132) OR (msg_box=2 AND m_type=128))",PilotTrainingHistoryPolicy.query(7,"mms",false).selection());
        var notice=new PilotTrainingHistoryPolicy.Entry(7,new MediaHistoryPolicy.Position(1000,"mms",1),1,130,"download later");
        for(int type:List.of(3,4,5,6))assertFalse(PilotTrainingHistoryPolicy.eligible(7,row(7,"sms",type,1000,type,"never sent")));
        assertFalse(PilotTrainingHistoryPolicy.eligible(7,notice));
    }
    @Test public void meaningfulTextIsCountedInsteadOfEmptyMediaOrAnotherContactsRows(){
        var sms=new FakeSource(List.of(row(8,"sms",1,7000,1,"private"),row(7,"sms",2,6000,1,"\u200b")));
        var mms=new FakeSource(List.of(row(7,"mms",1,5,1,""),row(7,"mms",2,4,1,"  useful text  ")));
        var result=latest(sms,mms);assertEquals(1,result.size());assertEquals("useful text",result.get(0).text());
    }
    @Test public void olderMmsTextIsNotDecodedWhenFiftyNewerTextsAreReady(){
        List<PilotTrainingHistoryPolicy.Entry> sent=new ArrayList<>();for(int i=1;i<=60;i++)sent.add(row(7,"sms",i,10_000+i,i%2+1,"text "+i));
        var sms=new FakeSource(sent);var mms=new FakeSource(List.of(row(7,"mms",1,1,1,"old MMS")));
        assertEquals(50,latest(sms,mms).size());assertEquals(50,sms.reads);assertEquals(0,mms.reads);
    }
    @Test public void sameIdsAndSameTimesRemainDistinctAcrossTransports(){
        var sms=row(7,"sms",1,1000,1,"sms");var mms=row(7,"mms",1,1,1,"mms");
        var result=PilotTrainingHistoryPolicy.select(7,List.of(sms,mms,sms),null);
        assertEquals(List.of(sms,mms),result);
    }
    @Test public void fallbackNeverCrossesContactsOrReplacesExistingIncoming(){
        var outgoing=row(7,"sms",1,1000,2,"sent");
        assertEquals(List.of(outgoing),PilotTrainingHistoryPolicy.select(7,List.of(outgoing),row(8,"sms",2,999,1,"private")));
        var incoming=row(7,"sms",3,2000,1,"current incoming");
        assertEquals(List.of(outgoing,incoming),PilotTrainingHistoryPolicy.select(7,List.of(outgoing,incoming),row(7,"sms",4,1,1,"old incoming")));
    }
    @Test public void textAndTotalContextStayBoundedWithoutBreakingEmoji(){
        var incoming=row(7,"sms",1,1000,1,"x".repeat(599)+"😀");
        var result=PilotTrainingHistoryPolicy.select(7,List.of(incoming),null);assertEquals(599,result.get(0).text().length());
        assertThrows(IllegalArgumentException.class,()->PilotTrainingHistoryPolicy.newest(7,new FakeSource(List.of()),new FakeSource(List.of()),51,()->{}));
        assertThrows(IllegalArgumentException.class,()->PilotTrainingHistoryPolicy.query(0,"sms",true));
        assertThrows(IllegalArgumentException.class,()->PilotTrainingHistoryPolicy.query(7,"rcs",true));
    }
    @Test public void losingAccessStopsCollectionBeforeMoreTextCanBeRead(){
        var sms=new FakeSource(List.of(row(7,"sms",1,1000,1,"private")));var mms=new FakeSource(List.of());AtomicInteger checks=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->PilotTrainingHistoryPolicy.newest(7,sms,mms,50,()->{if(checks.incrementAndGet()==2)throw new IllegalStateException("access changed");}));
        assertEquals(0,sms.reads);
    }
    @Test public void unreadableMmsDoesNotHideOtherReceivedTextsOrTeachPartialText(){
        var mms=new FakeSource(List.of(row(7,"mms",3,3,1,"partial text must not be taught"),row(7,"mms",2,2,1,"readable received text"))){
            @Override public PilotTrainingHistoryPolicy.Entry text(){var row=super.text();return row.position().id()==3?null:row;}
        };
        var result=latest(new FakeSource(List.of()),mms);
        assertEquals(1,result.size());assertEquals("readable received text",result.get(0).text());assertTrue(PilotTrainingHistoryPolicy.hasIncoming(result));
    }
    @Test public void revokedPermissionsAreNotTreatedAsAnIndividualMissingTextFile(){
        var mms=new FakeSource(List.of(row(7,"mms",1,2,1,"private"))){
            @Override public PilotTrainingHistoryPolicy.Entry text(){throw new SecurityException("access revoked");}
        };
        assertThrows(SecurityException.class,()->latest(new FakeSource(List.of()),mms));
    }
}
