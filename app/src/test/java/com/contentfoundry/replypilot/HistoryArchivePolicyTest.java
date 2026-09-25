package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class HistoryArchivePolicyTest {
    @Test public void unchangedRefreshKeepsTheSnapshotForAnOlderPage(){
        long first=HistoryArchivePolicy.scanMarker(0,0,0,1_000);
        long snapshot=HistoryArchivePolicy.snapshotAfterScan(0,first,true);
        long refreshed=HistoryArchivePolicy.scanMarker(snapshot,first,first,61_000);
        assertEquals(1_000,HistoryArchivePolicy.snapshotAfterScan(snapshot,refreshed,false));
        assertEquals(61_000,HistoryArchivePolicy.snapshotAfterScan(snapshot,refreshed,true));
        assertEquals(61_000,HistoryArchivePolicy.snapshotAfterScan(0,refreshed,false));
    }
    @Test public void scanMarkersStayUniqueWhileSnapshotStaysStableAndClockMovesBack(){
        long marker=HistoryArchivePolicy.scanMarker(1_000,61_000,61_000,500);
        assertEquals(61_001,marker);
        assertEquals(1_000,HistoryArchivePolicy.snapshotAfterScan(1_000,marker,false));
        assertEquals(61_002,HistoryArchivePolicy.scanMarker(1_000,marker,marker,500));
        assertEquals(70_001,HistoryArchivePolicy.scanMarker(1_000,marker,70_000,500));
    }
    @Test public void transientProviderRetriesBackOffAndStop(){
        assertEquals(15000,HistoryArchivePolicy.retryDelay(1));assertEquals(30000,HistoryArchivePolicy.retryDelay(2));assertEquals(60000,HistoryArchivePolicy.retryDelay(3));
        assertEquals(0,HistoryArchivePolicy.retryDelay(0));assertEquals(0,HistoryArchivePolicy.retryDelay(4));assertEquals(0,HistoryArchivePolicy.retryDelay(100));
    }
    @Test public void fullInboxSelectionDoesNotRetainTheOld150ChatLimit(){
        List<PinnedChatPolicy.Row<Integer>> rows=new ArrayList<>();for(int i=1;i<=700;i++)rows.add(new PinnedChatPolicy.Row<>(i,i,i,i));
        var full=PinnedChatPolicy.selectAll(rows,Set.of(1L));assertEquals(700,full.size());assertEquals(1L,full.get(0).thread());assertEquals(700L,full.get(1).thread());
        assertEquals(151,PinnedChatPolicy.select(rows,Set.of(1L)).size());
    }
    @Test public void mixedHistoryCursorMatchesVisibleTieOrder(){
        var query=HistoryArchivePolicy.history(7,new MediaHistoryPolicy.Position(1234,"mms",18));
        assertEquals(List.of("7","1234","1234","1","1","18"),query.args());
        assertEquals("thread=? AND (date<? OR (date=? AND (rank<? OR (rank=? AND id<?))))",query.where());
        assertEquals("0",HistoryArchivePolicy.history(7,new MediaHistoryPolicy.Position(1234,"sms",18)).args().get(3));
    }
    @Test public void initialAndInboxQueriesAreScopedWithoutGlobalHistoryLimits(){
        assertEquals("thread=?",HistoryArchivePolicy.history(9,null).where());assertEquals(List.of("9"),HistoryArchivePolicy.history(9,null).args());
        assertEquals(List.of("99","99","7"),HistoryArchivePolicy.inbox(99,7,true).args());
        assertEquals("1",HistoryArchivePolicy.inbox(0,0,false).where());
    }
    @Test public void boundedPagesDoNotSilentlyTruncateIndividualMessageText(){
        var budget=new HistoryArchivePolicy.Budget(40);assertTrue(budget.add(HistoryArchivePolicy.PAGE_BYTES+1));assertFalse(budget.add(1));
        var normal=new HistoryArchivePolicy.Budget(40);for(int i=0;i<40;i++)assertTrue(normal.add(10));assertFalse(normal.add(10));
        assertThrows(IllegalArgumentException.class,()->new HistoryArchivePolicy.Budget(40).add(HistoryArchivePolicy.RECORD_BYTES+1));
    }
    @Test public void pageByteBudgetProducesAResumableBoundary(){
        var budget=new HistoryArchivePolicy.Budget(40);assertTrue(budget.add(HistoryArchivePolicy.PAGE_BYTES-5));assertTrue(budget.add(5));assertFalse(budget.add(1));assertEquals(2,budget.rows);
    }
    @Test public void addressMustMatchClickedSummaryExactly(){
        assertTrue(HistoryArchivePolicy.sameAddress("+12025550100","+12025550100",false));
        assertFalse(HistoryArchivePolicy.sameAddress("+12025550100","+12025550101",false));assertFalse(HistoryArchivePolicy.sameAddress(null,"+12025550100",false));
        assertFalse(HistoryArchivePolicy.sameAddress("","",false));assertTrue(HistoryArchivePolicy.sameAddress("","",true));
    }
    @Test public void permissionRoleAndAccessRevisionMustRemainCurrent(){
        assertTrue(HistoryArchivePolicy.canDeliver(7,7,4,4));assertTrue(HistoryArchivePolicy.canDeliver(3,3,4,4));
        assertFalse(HistoryArchivePolicy.canDeliver(6,7,4,4));assertFalse(HistoryArchivePolicy.canDeliver(5,7,4,4));assertFalse(HistoryArchivePolicy.canDeliver(3,7,4,4));assertFalse(HistoryArchivePolicy.canDeliver(7,7,5,4));
    }
    @Test public void contactPermissionLossAlwaysScrubsNames(){
        assertEquals("+12025550100",HistoryArchivePolicy.visibleName("+12025550100","Fictional friend",false));
        assertEquals("Fictional friend",HistoryArchivePolicy.visibleName("+12025550100","Fictional friend",true));
    }
    @Test public void pageLimitsRejectUnboundedOrNonIntegralRequests(){
        assertEquals(40,HistoryArchivePolicy.limit(null,40));assertEquals(1,HistoryArchivePolicy.limit(1,40));
        assertThrows(IllegalArgumentException.class,()->HistoryArchivePolicy.limit(41,40));assertThrows(IllegalArgumentException.class,()->HistoryArchivePolicy.limit(0,40));assertThrows(IllegalArgumentException.class,()->HistoryArchivePolicy.limit(1.5,40));assertThrows(IllegalArgumentException.class,()->HistoryArchivePolicy.limit("40",40));
    }
}
