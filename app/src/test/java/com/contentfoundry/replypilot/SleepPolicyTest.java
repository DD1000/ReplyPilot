package com.contentfoundry.replypilot;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class SleepPolicyTest {
    private long at(String value){return OffsetDateTime.parse(value).toInstant().toEpochMilli();}
    private static final ZoneId NEW_YORK=ZoneId.of("America/New_York");

    @Test public void midnightMeansTheNextLocalMidnight(){
        assertEquals(at("2026-09-24T00:00:00-04:00"),SleepPolicy.nextCutoff("00:00",at("2026-09-23T20:00:00-04:00"),NEW_YORK));
        assertEquals(at("2026-09-25T00:00:00-04:00"),SleepPolicy.nextCutoff("00:00",at("2026-09-24T00:00:00-04:00"),NEW_YORK));
    }
    @Test public void pastClockTimeUsesTomorrowWhileLaterTimeUsesToday(){
        long now=at("2026-09-23T20:00:00-04:00");
        assertEquals(at("2026-09-24T19:00:00-04:00"),SleepPolicy.nextCutoff("19:00",now,NEW_YORK));
        assertEquals(at("2026-09-23T23:00:00-04:00"),SleepPolicy.nextCutoff("23:00",now,NEW_YORK));
    }
    @Test public void skippedSpringClockTimeUsesTheFirstValidInstantAfterward(){
        assertEquals(at("2026-03-08T03:00:00-04:00"),SleepPolicy.nextCutoff("02:30",at("2026-03-08T01:00:00-05:00"),NEW_YORK));
    }
    @Test public void fallOverlapUsesTheNextActualOccurrence(){
        assertEquals(at("2026-11-01T01:30:00-04:00"),SleepPolicy.nextCutoff("01:30",at("2026-11-01T00:45:00-04:00"),NEW_YORK));
        assertEquals(at("2026-11-01T01:30:00-05:00"),SleepPolicy.nextCutoff("01:30",at("2026-11-01T01:45:00-04:00"),NEW_YORK));
    }
    @Test public void malformedTimesCannotSilentlyTurnIntoMidnight(){
        for(String value:new String[]{null,"","24:00","12:60","1:30","00:00 tomorrow","-1:00"})assertThrows(IllegalArgumentException.class,()->SleepPolicy.nextCutoff(value,0,NEW_YORK));
    }
    @Test public void sessionDelayAcceptsOneSecondTestOrWholeMinutesWithinSevenDays(){
        for(long delay:new long[]{1,60,300,1800,3600,604800})assertEquals(delay,SleepPolicy.delay(delay));
        for(long delay:new long[]{-1,0,2,59,61,604801,Long.MAX_VALUE})assertThrows(IllegalArgumentException.class,()->SleepPolicy.delay(delay));
    }
    @Test public void activeSleepOverridesInstantAndTimedProfilesButOffRestoresEach(){
        assertEquals(1,SleepPolicy.effectiveDelay("active",300,1));
        assertEquals(60,SleepPolicy.effectiveDelay("off",60,300));
        assertEquals(300,SleepPolicy.effectiveDelay("active",0,300));
        assertEquals(300,SleepPolicy.effectiveDelay("active",60,300));
        assertEquals(0,SleepPolicy.effectiveDelay("off",0,300));
        assertEquals(60,SleepPolicy.effectiveDelay("off",60,300));
    }
    @Test public void aTimerDueExactlyAtTheCutoffIsWithheld(){
        assertTrue(SleepPolicy.allows("active",2,2,11,10,100,199,200));
        assertFalse(SleepPolicy.allows("active",2,2,11,10,100,200,200));
        assertFalse(SleepPolicy.allows("active",2,2,11,10,100,201,200));
        assertFalse(SleepPolicy.allows("active",2,2,11,10,200,199,200));
    }
    @Test public void expiredWallOrElapsedDeadlineStopsTheSession(){
        assertFalse(SleepPolicy.expired(2000,1000,4,1999,999,4));
        assertTrue(SleepPolicy.expired(2000,1000,4,2000,999,4));
        assertTrue(SleepPolicy.expired(2000,1000,4,100,1000,4)); // Wall clock moved back.
    }
    @Test public void rebootCannotReuseAnEarlierElapsedClock(){
        assertTrue(SleepPolicy.expired(2000,1000,4,100,5,5));
    }
    @Test public void aLatchedPauseDoesNotResumeWhenTheClockMovesBack(){
        assertFalse(SleepPolicy.allows("paused",3,3,11,10,0,50,2000));
    }
    @Test public void sessionRevisionStopsOldJobsAndBackgroundCompletions(){
        assertFalse(SleepPolicy.allows("active",1,2,11,10,100,150,200));
        assertFalse(SleepPolicy.allows("off",1,2,11,10,100,0,0));
        assertTrue(SleepPolicy.allows("off",2,2,11,10,100,0,0));
    }
    @Test public void startAndResumeDoNotProcessTheExistingInbox(){
        for(String mode:new String[]{"active","off"}){
            assertFalse(SleepPolicy.allows(mode,2,2,9,10,100,0,200));
            assertFalse(SleepPolicy.allows(mode,2,2,10,10,100,0,200));
            assertTrue(SleepPolicy.allows(mode,2,2,11,10,100,0,200));
        }
    }
    @Test public void initialOffModeKeepsExistingAutomationBehavior(){
        assertTrue(SleepPolicy.allows("off",0,0,1,0,100,0,0));
        assertFalse(SleepPolicy.allows("unknown",0,0,1,0,100,0,0));
    }
}
