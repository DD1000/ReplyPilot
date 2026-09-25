package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class SmsHistoryPolicyTest {
    private record Sms(long thread,long date,long id) {}

    private static SmsHistoryPolicy.Page<Long> page(long thread,SmsHistoryPolicy.Before before,List<Sms> input) {
        List<Sms> ordered=new ArrayList<>(input);
        ordered.sort(Comparator.comparingLong(Sms::date).thenComparingLong(Sms::id).reversed());
        SmsHistoryPolicy.Page<Long> page=new SmsHistoryPolicy.Page<>(thread,before);
        for(Sms row:ordered)if(page.add(row.thread,row.date,row.id,row.id))break;
        return page;
    }
    private static List<Sms> messages(long thread,int count) {
        List<Sms> rows=new ArrayList<>();
        for(int i=1;i<=count;i++)rows.add(new Sms(thread,1000+i/10,i));
        return rows;
    }

    @Test public void traversesEveryMessageAcrossManyPagesAndTimestampTies() {
        List<Sms> rows=messages(7,267);
        rows.addAll(messages(9,340));
        List<Long> history=new ArrayList<>();
        SmsHistoryPolicy.Before before=null;
        int pages=0;
        do{
            SmsHistoryPolicy.Page<Long> next=page(7,before,rows);
            assertTrue(next.history().size()<=50);
            history.addAll(0,next.history());
            pages++;
            if(!next.hasMore())break;
            assertNotNull(next.before());
            if(before!=null)assertTrue(next.before().date()<before.date()
                ||(next.before().date()==before.date()&&next.before().id()<before.id()));
            before=next.before();
            assertTrue("Cursor must make progress",pages<10);
        }while(true);
        assertEquals(6,pages);
        assertEquals(267,history.size());
        assertEquals(267,new HashSet<>(history).size());
        for(int i=0;i<history.size();i++)assertEquals(Long.valueOf(i+1),history.get(i));
    }

    @Test public void lookaheadDoesNotConsumeOldestDisplayedOrInventAnotherPage() {
        SmsHistoryPolicy.Page<Long> exact=page(7,null,messages(7,50));
        assertFalse(exact.hasMore());
        assertEquals(Long.valueOf(1),exact.history().get(0));
        assertEquals(1,exact.before().id());
        SmsHistoryPolicy.Page<Long> extra=page(7,null,messages(7,51));
        assertTrue(extra.hasMore());
        assertEquals(50,extra.history().size());
        assertEquals(2,extra.before().id());
        SmsHistoryPolicy.Page<Long> tail=page(7,extra.before(),messages(7,51));
        assertEquals(List.of(1L),tail.history());
        assertFalse(tail.hasMore());
    }

    @Test public void exclusiveCursorUsesDateThenIdAndKeepsOtherThreadsOut() {
        SmsHistoryPolicy.Before before=new SmsHistoryPolicy.Before(2000,50);
        List<Sms> rows=List.of(new Sms(7,2000,49),new Sms(7,2000,50),new Sms(7,2000,51),
            new Sms(7,1999,900),new Sms(7,2001,1),new Sms(8,1999,2));
        SmsHistoryPolicy.Page<Long> result=page(7,before,rows);
        assertEquals(List.of(900L,49L),result.history());
        assertEquals(new SmsHistoryPolicy.Before(1999,900),result.before());
    }

    @Test public void newlyArrivedMessagesDoNotShiftOlderPages() {
        List<Sms> rows=messages(7,135);
        SmsHistoryPolicy.Page<Long> first=page(7,null,rows);
        rows.add(new Sms(7,99999,1000));
        rows.add(new Sms(7,first.before().date(),1001));
        SmsHistoryPolicy.Page<Long> older=page(7,first.before(),rows);
        assertEquals(Long.valueOf(36),older.history().get(0));
        assertEquals(Long.valueOf(85),older.history().get(49));
        assertFalse(older.history().contains(1000L));
        assertFalse(older.history().contains(1001L));
        assertTrue(older.hasMore());
    }

    @Test public void rejectedRowsDoNotFillPageOrCreateFalseLookahead() {
        SmsHistoryPolicy.Page<Long> result=new SmsHistoryPolicy.Page<>(7,null);
        for(int i=1;i<=80;i++)assertFalse(result.add(8,1000,i,(long)i));
        assertFalse(result.add(7,-1,81,81L));
        assertFalse(result.add(7,1000,0,0L));
        assertFalse(result.hasMore());
        assertTrue(result.history().isEmpty());
        assertNull(result.before());
        assertFalse(result.add(7,0,1,1L));
        assertEquals(List.of(1L),result.history());
    }

    @Test public void validatesCursorsAndKeepsAllNumbersInBoundArguments() {
        assertNull(SmsHistoryPolicy.cursor(0,0));
        assertEquals(new SmsHistoryPolicy.Before(0,1),SmsHistoryPolicy.cursor(0,1));
        assertThrows(IllegalArgumentException.class,()->SmsHistoryPolicy.cursor(1,0));
        assertThrows(IllegalArgumentException.class,()->SmsHistoryPolicy.cursor(-1,2));
        assertThrows(IllegalArgumentException.class,()->SmsHistoryPolicy.cursor(1,-2));
        assertThrows(IllegalArgumentException.class,()->SmsHistoryPolicy.query(0,null));
        assertThrows(IllegalArgumentException.class,()->new SmsHistoryPolicy.Page<>(-3,null));
        SmsHistoryPolicy.Query first=SmsHistoryPolicy.query(123,null);
        assertEquals("thread_id=?",first.selection());
        assertEquals(List.of("123"),first.arguments());
        SmsHistoryPolicy.Query older=SmsHistoryPolicy.query(123,new SmsHistoryPolicy.Before(10000,42));
        assertEquals("thread_id=? AND (date<? OR (date=? AND _id<?))",older.selection());
        assertEquals(List.of("123","10000","10000","42"),older.arguments());
        assertFalse(older.selection().contains("123"));
        assertFalse(older.selection().contains("LIMIT"));
    }

    @Test public void aiContextUsesOnlyNonblankReceivedAndSentForThisThread() {
        assertTrue(SmsHistoryPolicy.isContext(7,7,1,"Incoming question?"));
        assertTrue(SmsHistoryPolicy.isContext(7,7,2,"My actual reply 😊"));
        for(int type:new int[]{0,3,4,5,6})assertFalse(SmsHistoryPolicy.isContext(7,7,type,"Unsent text"));
        assertFalse(SmsHistoryPolicy.isContext(7,8,2,"Other person's style"));
        assertFalse(SmsHistoryPolicy.isContext(7,7,2,null));
        assertFalse(SmsHistoryPolicy.isContext(7,7,2,""));
        assertFalse(SmsHistoryPolicy.isContext(7,7,2," \n\t\u2003"));
    }
}
