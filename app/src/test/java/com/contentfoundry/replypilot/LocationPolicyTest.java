package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocationPolicyTest {
    private static final long WALL=10_000_000,ELAPSED=2_000_000;
    private static boolean valid(boolean enabled,boolean permission,boolean services,boolean fine,long expected,long actual,long expires,long age){
        return LocationPolicy.valid(enabled,permission,services,fine,true,expected,actual,expires,WALL,ELAPSED,3,WALL+age,ELAPSED+age,3);
    }
    @Test public void freshFixExpiresAfter25MinutesByBothClocks(){
        assertTrue(LocationPolicy.fresh(WALL,ELAPSED,3,WALL+LocationPolicy.MAX_AGE_MS-1,ELAPSED+LocationPolicy.MAX_AGE_MS-1,3));
        assertFalse(LocationPolicy.fresh(WALL,ELAPSED,3,WALL+LocationPolicy.MAX_AGE_MS,ELAPSED+LocationPolicy.MAX_AGE_MS,3));
    }
    @Test public void rebootUnknownBootAndClockRollbackDoNotReviveOldCoordinates(){
        assertFalse(LocationPolicy.fresh(WALL,ELAPSED,3,WALL+1000,ELAPSED+1000,4));
        assertFalse(LocationPolicy.fresh(WALL,ELAPSED,-1,WALL+1000,ELAPSED+1000,-1));
        assertFalse(LocationPolicy.fresh(WALL,ELAPSED,3,WALL-1,ELAPSED+1000,3));
        assertFalse(LocationPolicy.fresh(WALL,ELAPSED,3,WALL+1000,ELAPSED-1,3));
    }
    @Test public void clockJumpDisagreementInvalidatesOtherwiseRecentFix(){
        assertFalse(LocationPolicy.fresh(WALL,ELAPSED,3,WALL+120_000,ELAPSED+1000,3));
        assertTrue(LocationPolicy.fresh(WALL,ELAPSED,3,WALL+2000,ELAPSED+1000,3));
    }
    @Test public void disabledPermissionRevokedOrLocationServicesOffStopsSending(){
        long expiry=WALL+LocationPolicy.MAX_AGE_MS;
        assertTrue(valid(true,true,true,true,8,8,expiry,1000));
        assertFalse(valid(false,true,true,true,8,8,expiry,1000));
        assertFalse(valid(true,false,true,true,8,8,expiry,1000));
        assertFalse(valid(true,true,false,true,8,8,expiry,1000));
    }
    @Test public void oldPreciseLabelsCannotSurvivePrecisionRevocation(){
        assertFalse(valid(true,true,true,false,8,8,WALL+LocationPolicy.MAX_AGE_MS,1000));
        assertTrue(LocationPolicy.valid(true,true,true,false,false,8,8,WALL+LocationPolicy.MAX_AGE_MS,WALL,ELAPSED,3,WALL+1000,ELAPSED+1000,3));
    }
    @Test public void changedHomeOrNewFixRevisionInvalidatesQueuedLocationReply(){
        assertFalse(valid(true,true,true,true,8,9,WALL+LocationPolicy.MAX_AGE_MS,1000));
        assertFalse(valid(true,true,true,true,8,8,WALL+LocationPolicy.MAX_AGE_MS+1,1000));
        assertFalse(valid(true,true,true,true,8,8,WALL+LocationPolicy.MAX_AGE_MS,LocationPolicy.MAX_AGE_MS));
    }
    @Test public void coarseOrMockFixesNeverEstablishHomeOrLandmarkPrecision(){
        assertTrue(LocationPolicy.precise(150,true,false));assertFalse(LocationPolicy.precise(150.1,true,false));
        assertFalse(LocationPolicy.precise(5,false,false));assertFalse(LocationPolicy.precise(5,true,true));
        assertFalse(LocationPolicy.precise(Double.NaN,true,false));assertFalse(LocationPolicy.precise(-1,true,false));
        assertFalse(LocationPolicy.usable(10,20,10,true));assertTrue(LocationPolicy.usable(10,20,5000,false));
        assertFalse(LocationPolicy.usable(10,20,50_001,false));
    }
    @Test public void homeRequiresWholeUncertaintyCircleInside150Meters(){
        assertTrue(LocationPolicy.home(100,50,true));assertFalse(LocationPolicy.home(100.1,50,true));
        assertFalse(LocationPolicy.home(0,151,true));assertFalse(LocationPolicy.home(0,1,false));
        assertFalse(LocationPolicy.home(Double.NaN,10,true));
    }
    @Test public void savingCurrentPlaceAsHomeRequiresAFixWithinTwoMinutes(){
        assertTrue(LocationPolicy.currentForHome(WALL,ELAPSED,3,WALL+120_000,ELAPSED+120_000,3));
        assertFalse(LocationPolicy.currentForHome(WALL,ELAPSED,3,WALL+120_001,ELAPSED+120_001,3));
        assertFalse(LocationPolicy.currentForHome(WALL,ELAPSED,3,WALL+1000,ELAPSED+121_000,3));
        assertFalse(LocationPolicy.currentForHome(WALL,ELAPSED,3,WALL+1000,ELAPSED+1000,4));
    }
    @Test public void distanceAndCoordinateValidationHandleGlobalBoundaries(){
        assertEquals(0,LocationPolicy.distance(0,0,0,0),0.01);
        assertEquals(111_195,LocationPolicy.distance(0,0,0,1),2);
        assertTrue(LocationPolicy.distance(0,179.999,0,-179.999)<225);
        assertFalse(LocationPolicy.coordinates(91,0));assertFalse(LocationPolicy.coordinates(0,181));assertFalse(LocationPolicy.coordinates(Double.NaN,0));
        assertEquals(Double.POSITIVE_INFINITY,LocationPolicy.distance(91,0,0,0),0);
    }
    @Test public void periodicRefreshAndMapRequestsStayThrottledAcrossRestarts(){
        assertTrue(LocationPolicy.due(0,0,-1,WALL,ELAPSED,3));
        assertFalse(LocationPolicy.due(WALL,ELAPSED,3,WALL+LocationPolicy.INTERVAL_MS-1,ELAPSED+LocationPolicy.INTERVAL_MS-1,3));
        assertTrue(LocationPolicy.due(WALL,ELAPSED,3,WALL+LocationPolicy.INTERVAL_MS,ELAPSED+LocationPolicy.INTERVAL_MS,3));
        assertFalse(LocationPolicy.due(WALL,ELAPSED,3,WALL+1000,1000,4));
        assertTrue(LocationPolicy.due(WALL,ELAPSED,3,WALL+LocationPolicy.INTERVAL_MS,1000,4));
        assertFalse(LocationPolicy.due(WALL,ELAPSED,3,WALL-1000,1000,4));
    }
    @Test public void coarseFallbackCannotReuseAnOldLandmark(){
        assertEquals("near Corner Cafe in Sample City",LocationPolicy.label("Corner Cafe","Sample City",false));
        assertEquals("near Sample City",LocationPolicy.label("","Sample City",false));
        assertEquals("",LocationPolicy.label(null,null,false));assertEquals("at home",LocationPolicy.label("","",true));
    }
    @Test public void labelsAreBoundedAndDoNotContainCoordinatesOrStreetAddresses(){
        assertTrue(LocationPolicy.label("A".repeat(200),"B".repeat(200),false).length()<=180);
        assertEquals("near Sample City",LocationPolicy.label("40.7128, -74.0060","Sample City",false));
        assertEquals("near Sample City",LocationPolicy.label("123 Main Street","Sample City",false));
        assertEquals("near 7-Eleven in Sample City",LocationPolicy.label("7-Eleven","Sample City",false));
        assertEquals("near Cafe in Sample City",LocationPolicy.label("☕ Cafe:\n","Sample\u200bCity",false));
    }
}
