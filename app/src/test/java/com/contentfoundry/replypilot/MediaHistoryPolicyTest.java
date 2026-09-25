package com.contentfoundry.replypilot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class MediaHistoryPolicyTest {
    private static MediaHistoryPolicy.Position p(long date,String kind,long id){return new MediaHistoryPolicy.Position(date,kind,id);}
    private static MediaHistoryPolicy.Row<String> row(long date,String kind,long id){var position=p(date,kind,id);return new MediaHistoryPolicy.Row<>(position,position.key());}
    private static List<String> keys(MediaHistoryPolicy.Page<String> page){return page.rows().stream().map(r->r.position().key()).toList();}

    @Test public void smsAndMmsIdsCannotCollideAtTheSameTimestamp(){
        var rows=List.of(row(1000,"mms",7),row(1000,"sms",7),row(1000,"sms",8),row(1000,"mms",8));
        var page=MediaHistoryPolicy.page(rows,p(2000,"mms",1),"older",40);
        assertEquals(List.of("sms:7","sms:8","mms:7","mms:8"),keys(page));
    }
    @Test public void messageUnitsNormalizeWithoutRoundingSmsMilliseconds(){
        assertEquals(1234000,MediaHistoryPolicy.date("mms",1234));
        assertEquals(1234567,MediaHistoryPolicy.date("sms",1234567));
        assertThrows(IllegalArgumentException.class,()->MediaHistoryPolicy.date("mms",Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->MediaHistoryPolicy.date("sms",-1));
    }
    @Test public void initialSidesExcludeButStraddleTheExactAnchor(){
        var anchor=p(1000,"mms",7);
        var rows=List.of(row(999,"sms",1),row(1000,"sms",999),row(1000,"mms",6),row(1000,"mms",7),row(1000,"mms",8),row(1001,"sms",1));
        assertEquals(List.of("sms:1","sms:999","mms:6"),keys(MediaHistoryPolicy.page(rows,anchor,"older",20)));
        assertEquals(List.of("mms:8","sms:1"),keys(MediaHistoryPolicy.page(rows,anchor,"newer",20)));
    }
    @Test public void olderPagesTakeTheNearestRowsAndKeepChronologicalDisplayOrder(){
        List<MediaHistoryPolicy.Row<String>> rows=new ArrayList<>();for(int i=1;i<=60;i++)rows.add(row(i,"sms",i));
        var page=MediaHistoryPolicy.page(rows,p(61,"mms",1),"older",40);
        assertEquals(40,page.rows().size());assertTrue(page.hasMore());
        assertEquals("sms:21",keys(page).get(0));assertEquals("sms:60",keys(page).get(39));
        var older=MediaHistoryPolicy.page(rows,page.rows().get(0).position(),"older",40);
        assertEquals(20,older.rows().size());assertFalse(older.hasMore());assertEquals("sms:1",keys(older).get(0));
    }
    @Test public void newerPagesRetainEverySameSecondMessageWithoutDuplicates(){
        List<MediaHistoryPolicy.Row<String>> rows=new ArrayList<>();for(int i=1;i<=60;i++){rows.add(row(1000,"sms",i));rows.add(row(1000,"mms",i));}
        var boundary=p(999,"sms",1);Set<String> observed=new HashSet<>();
        while(true){
            var page=MediaHistoryPolicy.page(rows,boundary,"newer",40);
            for(var entry:page.rows())assertTrue("Duplicate "+entry.position().key(),observed.add(entry.position().key()));
            if(!page.hasMore())break;
            boundary=page.rows().get(page.rows().size()-1).position();
        }
        assertEquals(120,observed.size());
    }
    @Test public void newArrivalsDoNotMoveAnOlderPagingBoundary(){
        var boundary=p(2000,"mms",5);List<MediaHistoryPolicy.Row<String>> rows=new ArrayList<>(List.of(row(1000,"sms",1),row(1500,"sms",2)));
        var before=MediaHistoryPolicy.page(rows,boundary,"older",40);
        rows.add(row(3000,"sms",3));rows.add(row(4000,"mms",8));
        assertEquals(keys(before),keys(MediaHistoryPolicy.page(rows,boundary,"older",40)));
    }
    @Test public void smsQueriesUseExactThreadAndTieBreakingIdArguments(){
        var q=MediaHistoryPolicy.query(9,"sms",p(1234,"sms",8),"older");
        assertEquals("thread_id=? AND (date<? OR (date=? AND _id<?))",q.selection());
        assertEquals(List.of("9","1234","1234","8"),q.arguments());assertEquals("date DESC, _id DESC",q.order());
        var next=MediaHistoryPolicy.query(9,"sms",p(1234,"sms",8),"newer");
        assertEquals("thread_id=? AND (date>? OR (date=? AND _id>?))",next.selection());assertEquals("date ASC, _id ASC",next.order());
    }
    @Test public void fractionalSmsTimestampIncludesEarlierMmsSecondOnlyOnOlderSide(){
        var before=MediaHistoryPolicy.query(7,"mms",p(5501,"sms",8),"older");
        assertEquals("thread_id=? AND date<=?",before.selection());assertEquals(List.of("7","5"),before.arguments());
        var after=MediaHistoryPolicy.query(7,"mms",p(5501,"sms",8),"newer");
        assertEquals("thread_id=? AND date>?",after.selection());assertEquals(List.of("7","5"),after.arguments());
    }
    @Test public void exactSecondCrossSourceQueriesAgreeWithSmsBeforeMmsOrdering(){
        assertEquals("thread_id=? AND date<=?",MediaHistoryPolicy.query(1,"sms",p(5000,"mms",1),"older").selection());
        assertEquals("thread_id=? AND date>?",MediaHistoryPolicy.query(1,"sms",p(5000,"mms",1),"newer").selection());
        assertEquals("thread_id=? AND date<?",MediaHistoryPolicy.query(1,"mms",p(5000,"sms",1),"older").selection());
        assertEquals("thread_id=? AND date>=?",MediaHistoryPolicy.query(1,"mms",p(5000,"sms",1),"newer").selection());
        var same=MediaHistoryPolicy.query(1,"mms",p(5000,"mms",2),"older");
        assertEquals(List.of("1","5","5","2"),same.arguments());
    }
    @Test public void latestMixedWindowNeverConfusesTheSameSmsAndMmsId(){
        List<MediaHistoryPolicy.Row<String>> rows=new ArrayList<>();for(int i=1;i<=30;i++){rows.add(row(i*1000,"sms",i));rows.add(row(i*1000,"mms",i));}
        var page=MediaHistoryPolicy.latest(rows,40);assertTrue(page.hasMore());assertEquals(40,page.rows().size());
        assertEquals("sms:11",keys(page).get(0));assertEquals("mms:30",keys(page).get(39));
        var older=MediaHistoryPolicy.page(rows,page.rows().get(0).position(),"older",40);assertFalse(older.hasMore());assertEquals(20,older.rows().size());
    }
    @Test public void latestWindowHandlesEmptyAndDuplicateRowsWithoutPhantomPages(){
        assertTrue(MediaHistoryPolicy.latest(List.of(),40).rows().isEmpty());assertFalse(MediaHistoryPolicy.latest(List.of(),40).hasMore());
        var page=MediaHistoryPolicy.latest(List.of(row(1,"sms",7),row(1,"sms",7),row(1,"mms",7)),40);
        assertEquals(List.of("sms:7","mms:7"),keys(page));assertFalse(page.hasMore());
    }
    @Test public void latestQueriesStayWithinTheRequestedThread(){
        var query=MediaHistoryPolicy.latestQuery(9,"mms");assertEquals("thread_id=?",query.selection());assertEquals(List.of("9"),query.arguments());assertEquals("date DESC, _id DESC",query.order());
        assertThrows(IllegalArgumentException.class,()->MediaHistoryPolicy.latestQuery(0,"mms"));assertThrows(IllegalArgumentException.class,()->MediaHistoryPolicy.latestQuery(1,"other"));
    }
    @Test public void inputValidationRejectsCoercionFractionsAndInvalidDestinations(){
        for(Object invalid:new Object[]{null,"2",1.5,Double.NaN,Double.POSITIVE_INFINITY,-1,0,9_007_199_254_740_992L})assertThrows(IllegalArgumentException.class,()->MediaHistoryPolicy.integer(invalid,true));
        assertEquals(0,MediaHistoryPolicy.integer(0,false));assertEquals(7,MediaHistoryPolicy.integer(7,true));
        assertThrows(IllegalArgumentException.class,()->p(1,"other",1));assertThrows(IllegalArgumentException.class,()->p(1,"sms",0));
        assertThrows(IllegalArgumentException.class,()->MediaHistoryPolicy.query(0,"sms",p(1,"sms",1),"older"));
        assertThrows(IllegalArgumentException.class,()->MediaHistoryPolicy.query(1,"sms",p(1,"sms",1),"later"));
    }
}
