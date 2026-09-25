package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class SmsRoleRequestPolicyTest {
    @Test public void chooserWithoutCallbackHasABoundedWait(){
        SmsRoleRequestPolicy request=new SmsRoleRequestPolicy(1024);request.launch(100);
        assertNull(request.completion(false,30_099,false));
        assertEquals("timeout",request.completion(false,30_100,false));
        assertEquals(0,request.remaining(50_000));
    }
    @Test public void returningWithoutAResultCallbackStillUnlocksSetup(){
        SmsRoleRequestPolicy request=new SmsRoleRequestPolicy(1024);request.launch(100);request.leaveApp();
        assertNull(request.completion(false,101,false));
        assertEquals("notSelected",request.completion(false,102,true));
    }
    @Test public void onlyTheLiveRoleStateEstablishesSuccess(){
        SmsRoleRequestPolicy request=new SmsRoleRequestPolicy(1024);request.launch(100);
        assertEquals("selected",request.completion(true,101,false));
        assertTrue(request.returned(1024));
        assertEquals("notSelected",request.completion(false,102,false));
        assertEquals("selected",request.completion(true,50_000,false));
    }
    @Test public void lateCallbackCannotFinishAnotherAttempt(){
        SmsRoleRequestPolicy oldRequest=new SmsRoleRequestPolicy(1024);oldRequest.launch(1);
        assertEquals("timeout",oldRequest.completion(false,30_001,false));
        SmsRoleRequestPolicy retry=new SmsRoleRequestPolicy(1025);retry.launch(30_002);
        assertFalse(retry.returned(oldRequest.requestCode()));
        assertNull(retry.completion(false,30_003,false));
        assertTrue(retry.returned(1025));
        assertEquals("notSelected",retry.completion(false,30_004,false));
    }
    @Test public void localExplanationDoesNotExpireOrConsumeAStaleChooserCallback(){
        SmsRoleRequestPolicy request=new SmsRoleRequestPolicy(1024);
        assertEquals("confirmation",request.phase());request.leaveApp();
        assertFalse(request.returned(1024));assertNull(request.completion(false,100_000,true));
        request.launch(100_001);assertEquals("chooser",request.phase());
        assertEquals(30_000,request.remaining(100_001));
    }
}
