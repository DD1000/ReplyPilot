package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class DelayPolicyTest {
    @Test public void rangeRequiresActualFiniteIntegerWholeMinuteNumbers(){
        for(Object endpoint:new Object[]{null,"300",true,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,300.5,301,0,1,-60,604860,Long.MAX_VALUE}){
            assertThrows(String.valueOf(endpoint),IllegalArgumentException.class,()->DelayPolicy.choice("range",300,endpoint,1800));
            assertThrows(String.valueOf(endpoint),IllegalArgumentException.class,()->DelayPolicy.choice("range",300,300,endpoint));
        }
        assertEquals(300,DelayPolicy.choice("range",300,300.0,1800.0).min());
    }
    @Test public void rangeModeCannotBeGuessedOrCoerced(){
        for(Object mode:new Object[]{null,"",true,1,"random","RANGE"})assertThrows(IllegalArgumentException.class,()->DelayPolicy.choice(mode,300,300,1800));
    }
    @Test public void reversedRangesAreRejectedButEqualLimitsAreAllowed(){
        assertThrows(IllegalArgumentException.class,()->DelayPolicy.choice("range",300,1800,300));
        assertEquals(300,DelayPolicy.choose(DelayPolicy.choice("range",300,300,300),bound->{throw new AssertionError("No draw is needed for equal bounds");}));
    }
    @Test public void bothEndpointsAreIncludedAtWholeSecondResolution(){
        DelayPolicy.Choice choice=DelayPolicy.choice("range",300,300,1800);
        assertEquals(300,DelayPolicy.choose(choice,bound->{assertEquals(1501,bound);return 0;}));
        assertEquals(1800,DelayPolicy.choose(choice,bound->bound-1));
        assertEquals(721,DelayPolicy.choose(choice,bound->421));
    }
    @Test public void fullSevenDayRangeDoesNotOverflow(){
        DelayPolicy.Choice choice=DelayPolicy.choice("range",300,60,604800);
        assertEquals(60,DelayPolicy.choose(choice,bound->0));
        assertEquals(604800,DelayPolicy.choose(choice,bound->{assertEquals(604741,bound);return bound-1;}));
    }
    @Test public void fixedAndAutopilotDoNotDrawRandomNumbers(){
        for(long fixed:new long[]{0,1,60,600,900,604800}){
            DelayPolicy.Choice choice=DelayPolicy.choice("fixed",fixed,300,1800);
            assertEquals(fixed,DelayPolicy.choose(choice,bound->{throw new AssertionError("Fixed delay must never draw");}));
            assertEquals(fixed==0,choice.instant());
        }
    }
    @Test public void inactiveInvalidRangeFieldsDoNotChangeAFixedDelay(){
        DelayPolicy.Choice choice=DelayPolicy.choice("fixed",900,null,"unfinished");
        assertEquals(900,DelayPolicy.choose(choice,bound->0));assertEquals(300,choice.min());assertEquals(1800,choice.max());
    }
    @Test public void oneSecondIsTheOnlySubMinuteFixedTestPreset(){
        assertEquals(1,DelayPolicy.fixedSeconds(1,false));
        assertEquals(1,DelayPolicy.fixedSeconds(1,true));
        assertEquals(0,DelayPolicy.fixedSeconds(0,true));
        assertThrows(IllegalArgumentException.class,()->DelayPolicy.fixedSeconds(0,false));
        for(long delay:new long[]{-1,2,59,61,721,604801,Long.MAX_VALUE}){
            assertThrows(IllegalArgumentException.class,()->DelayPolicy.fixedSeconds(delay,false));
            assertThrows(IllegalArgumentException.class,()->DelayPolicy.choice("fixed",delay,300,1800));
        }
    }
    @Test public void bridgeSecondsRejectCoercedFractionalAndNonfiniteValues(){
        assertEquals(1,DelayPolicy.seconds(1));assertEquals(60,DelayPolicy.seconds(60.0));
        for(Object value:new Object[]{null,"1",true,1.1,Double.NaN,Double.POSITIVE_INFINITY,-1,604801,Long.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->DelayPolicy.seconds(value));
    }
    @Test public void aRememberedTestPresetDoesNotShortenRandomRanges(){
        DelayPolicy.Choice choice=DelayPolicy.choice("range",1,300,1800);
        assertFalse(choice.instant());assertEquals(300,choice.minimumSeconds());
        assertEquals(721,DelayPolicy.choose(choice,bound->421));
    }
    @Test public void aOneSecondTimerKeepsItsExactDeadlineAndRemainsCancelable(){
        long started=100_000,seconds=DelayPolicy.choose(DelayPolicy.choice("fixed",1,null,null),bound->{throw new AssertionError("No random draw");});
        long due=started+seconds*1000;
        assertEquals(101_000,due);
        assertEquals("The timer has not finished.",SendPolicy.block("scheduled",true,due,due-1,8,8,true,true));
        assertNull(SendPolicy.block("scheduled",true,due,due,8,8,true,true));
        assertNotNull(SendPolicy.block("cancelled",true,due,due,8,8,true,true));
        assertFalse(SleepPolicy.allows("active",2,2,11,10,started,due,due));
    }
    @Test public void rangeCannotAccidentallyBecomeAutopilotWhenRememberedFixedDelayIsZero(){
        DelayPolicy.Choice choice=DelayPolicy.choice("range",0,300,1800);
        assertFalse(choice.instant());assertEquals(300,choice.minimumSeconds());assertEquals(721,DelayPolicy.choose(choice,bound->421));
    }
    @Test public void aBadRandomProviderCannotCreateAnOutOfBoundsDelay(){
        DelayPolicy.Choice choice=DelayPolicy.choice("range",300,300,1800);
        assertThrows(IllegalStateException.class,()->DelayPolicy.choose(choice,bound->-1));
        assertThrows(IllegalStateException.class,()->DelayPolicy.choose(choice,bound->bound));
    }
    @Test public void aPickedDelayAndDeadlineStayFixedWhileTheCountdownChanges(){
        AtomicInteger draws=new AtomicInteger();long started=100_000;
        long picked=DelayPolicy.choose(DelayPolicy.choice("range",300,300,1800),bound->{draws.incrementAndGet();return 421;});
        long due=started+picked*1000;
        // Dispatch consumes the stored deadline; repeated checks do not sample.
        assertEquals("The timer has not finished.",SendPolicy.block("scheduled",true,due,started+300_000,8,8,true,true));
        assertNull(SendPolicy.block("scheduled",true,due,due,8,8,true,true));
        assertNull(SendPolicy.block("scheduled",true,due,due+1000,8,8,true,true));
        assertEquals(821_000,due);assertEquals(1,draws.get());
    }
    @Test public void sleepDoesNotReplaceAnUnsendableDrawWithAShorterOne(){
        AtomicInteger draws=new AtomicInteger();long now=100_000,cutoff=1_000_000;
        long picked=DelayPolicy.choose(DelayPolicy.choice("range",300,300,1800),bound->{draws.incrementAndGet();return bound-1;});
        assertFalse(SleepPolicy.allows("active",2,2,11,10,now,now+picked*1000,cutoff));
        assertEquals(1800,picked);assertEquals(1,draws.get());
    }
    @Test public void manualConfirmationCanShowTheExactChosenSeconds(){
        assertEquals("1 sec",DelayPolicy.duration(1));
        assertEquals("12 min 1 sec",DelayPolicy.duration(721));
        assertEquals("5 min",DelayPolicy.duration(300));
        assertEquals("10080 min",DelayPolicy.duration(604800));
    }
}
