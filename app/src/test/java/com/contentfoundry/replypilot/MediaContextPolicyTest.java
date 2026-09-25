package com.contentfoundry.replypilot;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MediaContextPolicyTest {
    @Test public void scalesPortraitAndLandscapeWithoutUpscaling(){
        assertEquals(new MediaContextPolicy.Size(1024,512),MediaContextPolicy.scaled(4000,2000));
        assertEquals(new MediaContextPolicy.Size(512,1024),MediaContextPolicy.scaled(2000,4000));
        assertEquals(new MediaContextPolicy.Size(100,200),MediaContextPolicy.scaled(100,200));
        assertEquals(4,MediaContextPolicy.sample(4000,2000));assertEquals(1,MediaContextPolicy.sample(640,480));
    }
    @Test public void rejectsMissingAndDecompressionBombDimensions(){
        assertThrows(IllegalArgumentException.class,()->MediaContextPolicy.scaled(0,100));assertThrows(IllegalArgumentException.class,()->MediaContextPolicy.scaled(-1,100));
        assertThrows(IllegalArgumentException.class,()->MediaContextPolicy.scaled(20001,1));assertThrows(IllegalArgumentException.class,()->MediaContextPolicy.scaled(20000,20000));
        assertEquals(new MediaContextPolicy.Size(1024,1024),MediaContextPolicy.scaled(10000,10000));
    }
    @Test public void appliesPerImageAndTotalByteLimitsWithoutOverflow(){
        assertTrue(MediaContextPolicy.accepts(0,0,153600));assertTrue(MediaContextPolicy.accepts(2,307200,153600));
        assertFalse(MediaContextPolicy.accepts(3,0,1));assertFalse(MediaContextPolicy.accepts(0,0,153601));assertFalse(MediaContextPolicy.accepts(2,307201,153600));
        assertFalse(MediaContextPolicy.accepts(0,Integer.MAX_VALUE,153600));assertFalse(MediaContextPolicy.accepts(0,0,0));assertFalse(MediaContextPolicy.accepts(-1,0,1));
    }
    @Test public void capturesExpireAndRebootedElapsedClockCannotReviveThem(){
        assertTrue(MediaContextPolicy.fresh(1000,301000));assertFalse(MediaContextPolicy.fresh(1000,301001));
        assertFalse(MediaContextPolicy.fresh(1000,999));assertFalse(MediaContextPolicy.fresh(-1,0));
    }
    @Test public void samplesOnlyWithinKnownShortVideoDurationAndAvailableSlots(){
        assertEquals(List.of(1000L,5000L,9000L),MediaContextPolicy.frames(10000,3));
        assertEquals(List.of(2500L,7500L),MediaContextPolicy.frames(10000,2));assertEquals(List.of(5000L),MediaContextPolicy.frames(10000,1));
        assertTrue(MediaContextPolicy.frames(10000,0).isEmpty());assertTrue(MediaContextPolicy.frames(0,3).isEmpty());assertTrue(MediaContextPolicy.frames(120001,3).isEmpty());
    }
    @Test public void captionClippingDoesNotSplitSurrogates(){
        String caption="a".repeat(1599)+"🦆";assertEquals(1599,MediaContextPolicy.clipped(caption,1600).length());
        assertEquals("Exact note",MediaContextPolicy.clipped("Exact note",1600));assertEquals("",MediaContextPolicy.clipped(null,1600));
    }
    @Test public void fingerprintBindsEveryIdentityComponentWithoutDelimiterAmbiguity(){
        assertEquals(MediaContextPolicy.signature("mms","1","23"),MediaContextPolicy.signature("mms","1","23"));
        assertNotEquals(MediaContextPolicy.signature("mms","1","23"),MediaContextPolicy.signature("sms","1","23"));
        assertNotEquals(MediaContextPolicy.signature("12","3:4"),MediaContextPolicy.signature("12:3","4"));
    }
}
