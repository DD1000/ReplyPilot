package com.contentfoundry.replypilot;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiveInboxPolicyTest {
    @Test public void repeatedRowsFromOneBusyChatCannotScanTheWholeProvider(){
        var scan=new LiveInboxPolicy.Scan("sms");
        for(int i=0;i<LiveInboxPolicy.SCAN_LIMIT;i++)scan.add(7,10_000-i,1000-i,1,0,0);
        assertTrue(scan.full());assertEquals(List.of(7L),LiveInboxPolicy.threads(scan.rows()));
        assertThrows(IllegalStateException.class,()->scan.add(8,20_000,2000,1,0,0));
    }
    @Test public void invalidRowsAlsoConsumeTheScanBudget(){
        var scan=new LiveInboxPolicy.Scan("mms");
        for(int i=0;i<LiveInboxPolicy.SCAN_LIMIT;i++)scan.add(0,-1,-1,0,3,99);
        assertTrue(scan.full());assertTrue(scan.rows().isEmpty());
    }
    @Test public void mixedDatesAreNormalizedAndNewestTransportWinsPerThread(){
        var sms=new LiveInboxPolicy.Scan("sms");var mms=new LiveInboxPolicy.Scan("mms");
        sms.add(1,2_500,9000,1,0,0);sms.add(2,1_900,9001,2,0,0);
        mms.add(2,3,1,0,1,132);mms.add(3,2,2,0,2,128);
        var all=new ArrayList<>(sms.rows());all.addAll(mms.rows());
        assertEquals(3000,mms.rows().get(0).position().date());
        assertEquals(List.of(2L,1L,3L),LiveInboxPolicy.threads(all));
    }
    @Test public void sameMillisecondTieUsesMixedTransportOrderNotUnrelatedIds(){
        var sms=new LiveInboxPolicy.Scan("sms");var mms=new LiveInboxPolicy.Scan("mms");
        sms.add(1,2_000,9999,2,0,0);mms.add(2,2,1,0,1,132);
        var all=new ArrayList<>(sms.rows());all.addAll(mms.rows());
        assertEquals(List.of(2L,1L),LiveInboxPolicy.threads(all));
    }
    @Test public void onlyFortyMostRecentThreadsAreEnriched(){
        var scan=new LiveInboxPolicy.Scan("sms");
        for(int i=1;i<=100;i++)scan.add(i,i*1000L,i,1,0,0);
        List<Long> threads=LiveInboxPolicy.threads(scan.rows());
        assertEquals(40,threads.size());assertEquals(Long.valueOf(100),threads.get(0));assertEquals(Long.valueOf(61),threads.get(39));
    }
    @Test public void draftsAndControlPdusNeverCreateRecentConversations(){
        var sms=new LiveInboxPolicy.Scan("sms");var mms=new LiveInboxPolicy.Scan("mms");
        sms.add(1,2000,1,3,0,0);mms.add(2,3,1,0,3,128);mms.add(3,4,2,0,1,134);
        assertTrue(sms.rows().isEmpty());assertTrue(mms.rows().isEmpty());
        mms.add(4,5,3,0,1,130);assertEquals(List.of(4L),LiveInboxPolicy.threads(mms.rows()));
    }
    @Test public void badIdsAndDatesCannotMoveAChatToTheTop(){
        var scan=new LiveInboxPolicy.Scan("mms");
        scan.add(1,Long.MAX_VALUE,1,0,1,132);scan.add(2,2,-1,0,1,132);scan.add(Long.MAX_VALUE,2,3,0,1,132);
        assertTrue(scan.rows().isEmpty());
    }
}
