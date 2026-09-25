package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class BurstPolicyTest {
    @Test public void quietWindowBeginsAtLocalReceiptAndEndsAtTenSeconds(){
        assertEquals(10_000,BurstPolicy.remaining(100_000,50_000,4,100_000,50_000,4));
        assertEquals(1,BurstPolicy.remaining(100_000,50_000,4,109_999,59_999,4));
        assertEquals(0,BurstPolicy.remaining(100_000,50_000,4,110_000,60_000,4));
    }
    @Test public void everyFollowUpRestartsTheWindowForTheLatestBase(){
        // Messages arrive at 0, 8 and 15 seconds. The third text is ready at 25.
        assertEquals(2000,BurstPolicy.remaining(100_000,50_000,4,108_000,58_000,4));
        assertEquals(3000,BurstPolicy.remaining(108_000,58_000,4,115_000,65_000,4));
        assertEquals(1,BurstPolicy.remaining(115_000,65_000,4,124_999,74_999,4));
        assertEquals(0,BurstPolicy.remaining(115_000,65_000,4,125_000,75_000,4));
        assertFalse(BurstPolicy.current(3,103,101,false));assertTrue(BurstPolicy.current(3,103,103,false));
    }
    @Test public void aBroadcastWaitingForProviderInsertionCannotBeAnswered(){
        assertEquals(0,BurstPolicy.remaining(100_000,50_000,4,130_000,80_000,4));
        assertFalse(BurstPolicy.current(3,0,102,false));
        assertFalse(BurstPolicy.unchanged(3,3,BurstPolicy.current(3,0,102,false),0));
        assertTrue(BurstPolicy.unchanged(3,3,BurstPolicy.current(3,103,103,false),0));
    }
    @Test public void aLaterPhysicalReceiptInvalidatesAnInFlightModelBeforeItsSmsIsStored(){
        long modelToken=1;
        assertFalse(BurstPolicy.unchanged(modelToken,2,false,10_000));
        assertFalse(BurstPolicy.unchanged(modelToken,2,true,0));
        assertTrue(BurstPolicy.unchanged(2,2,true,0));
    }
    @Test public void olderReceiptsCannotBindOrReplaceTheLatestBurst(){
        assertFalse(BurstPolicy.mayBind(1,2));assertTrue(BurstPolicy.mayBind(2,2));
        assertFalse(BurstPolicy.mayBind(0,0));
        assertFalse(BurstPolicy.mayReplace(2,1));assertTrue(BurstPolicy.mayReplace(1,2));
    }
    @Test public void futureSenderDatesAndWallClockChangesDoNotChangeSameBootQuietTime(){
        assertEquals(5000,BurstPolicy.remaining(100_000,50_000,4,9_999_999,55_000,4));
        assertEquals(5000,BurstPolicy.remaining(100_000,50_000,4,1,55_000,4));
    }
    @Test public void processRestartUsesPersistedClocksAndRebootUsesReceiptWallTime(){
        assertEquals(3000,BurstPolicy.remaining(100_000,50_000,4,107_000,57_000,4));
        assertEquals(3000,BurstPolicy.remaining(100_000,50_000,4,107_000,2000,5));
        assertEquals(0,BurstPolicy.remaining(100_000,50_000,4,112_000,2000,5));
        assertEquals(10_000,BurstPolicy.remaining(100_000,50_000,4,99_000,2000,5));
    }
    @Test public void failedStorageDoesNotMakeTheOlderBaseCurrent(){
        assertFalse(BurstPolicy.current(7,0,99,true));
        assertFalse(BurstPolicy.current(7,99,99,true));
    }
    @Test public void conversationsWithoutRecordedReceiptsRetainExistingBehavior(){
        assertTrue(BurstPolicy.current(0,0,99,false));
        assertTrue(BurstPolicy.unchanged(0,0,true,0));
        assertFalse(BurstPolicy.current(0,0,0,false));
    }
    @Test public void successfulManualRetryCoversTheOlderIncomingBase(){
        assertTrue(BurstPolicy.manualCoversLatest(7,101,101,false));
        // Incoming 101, failed outgoing 102, then the owner retries based on 102.
        assertTrue(BurstPolicy.manualCoversLatest(7,101,102,false));
        assertFalse(BurstPolicy.current(7,101,102,false));
    }
    @Test public void newPendingOrFailedIncomingReceiptsKeepTheManualHold(){
        assertFalse(BurstPolicy.manualCoversLatest(8,103,102,false));
        assertFalse(BurstPolicy.manualCoversLatest(8,0,102,false));
        assertFalse(BurstPolicy.manualCoversLatest(8,0,102,true));
        assertFalse(BurstPolicy.manualCoversLatest(8,101,102,true));
        assertFalse(BurstPolicy.manualCoversLatest(-1,0,102,true));
    }
    @Test public void manualHoldClearingKeepsLegacyReceiptsAndRejectsInvalidBases(){
        assertTrue(BurstPolicy.manualCoversLatest(0,0,102,false));
        assertFalse(BurstPolicy.manualCoversLatest(0,0,0,false));
        assertFalse(BurstPolicy.manualCoversLatest(0,0,-1,false));
        assertFalse(BurstPolicy.manualCoversLatest(0,0,102,true));
        assertFalse(BurstPolicy.manualCoversLatest(-1,101,102,false));
        assertFalse(BurstPolicy.manualCoversLatest(7,-1,102,false));
        assertFalse(BurstPolicy.manualCoversLatest(0,103,102,false));
    }
    @Test public void manualSuccessDoesNotNeedToWaitOutAnAutomaticQuietPeriod(){
        long remaining=BurstPolicy.remaining(100_000,50_000,4,100_000,50_000,4);
        assertEquals(10_000,remaining);
        assertTrue(BurstPolicy.manualCoversLatest(7,101,102,false));
        assertFalse(BurstPolicy.unchanged(7,7,BurstPolicy.current(7,101,102,false),remaining));
    }
}
