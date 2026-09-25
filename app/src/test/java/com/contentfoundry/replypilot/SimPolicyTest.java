package com.contentfoundry.replypilot;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SimPolicyTest {
    @Test public void savedSimIsKeptWhileActive(){
        assertEquals(3,SimPolicy.effective(3,List.of(3)));
        assertEquals(4,SimPolicy.effective(4,List.of(3,4)));
    }
    @Test public void onlyActiveSimReplacesAStaleOrMissingChoice(){
        assertEquals(7,SimPolicy.effective(-1,List.of(7)));
        assertEquals(7,SimPolicy.effective(2,List.of(7)));
    }
    @Test public void severalSimsNeverGuess(){
        assertEquals(SimPolicy.NONE,SimPolicy.effective(-1,List.of(1,2)));
        assertEquals(SimPolicy.NONE,SimPolicy.effective(9,List.of(1,2)));
    }
    @Test public void noActiveSimMeansNone(){
        assertEquals(SimPolicy.NONE,SimPolicy.effective(3,List.of()));
        assertEquals(SimPolicy.NONE,SimPolicy.effective(3,null));
        assertEquals(SimPolicy.NONE,SimPolicy.effective(-1,Arrays.asList((Integer)null)));
        assertEquals(SimPolicy.NONE,SimPolicy.effective(-1,List.of(-1)));
    }
    @Test public void problemNamesTheActualFix(){
        assertTrue(SimPolicy.problem(false,0).contains("Phone access"));
        assertTrue(SimPolicy.problem(false,2).contains("Phone access"));
        assertTrue(SimPolicy.problem(true,0).contains("No active SIM"));
        assertTrue(SimPolicy.problem(true,2).contains("Choose your sending SIM"));
    }
}
