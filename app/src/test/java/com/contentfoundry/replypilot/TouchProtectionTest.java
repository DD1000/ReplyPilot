package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class TouchProtectionTest {
    @Test public void unobscuredSelectionDoesNotDisableNormalTouches(){
        TouchProtection guard=new TouchProtection();
        assertEquals(new TouchProtection.Decision(false,false,false),guard.next(true,false,false,true));
        assertEquals(new TouchProtection.Decision(false,false,false),guard.next(false,true,false,true));
    }

    @Test public void obscuredOutsideTapDismissesToolbarButNeverClicksThrough(){
        TouchProtection guard=new TouchProtection();
        assertEquals(new TouchProtection.Decision(true,false,true),guard.next(true,false,true,true));
        // Dismissing the toolbar can clear the occlusion flag before this UP.
        assertEquals(new TouchProtection.Decision(true,false,false),guard.next(false,true,false,false));
        assertFalse(guard.next(true,false,false,false).block());
        assertFalse(guard.next(false,true,false,false).block());
    }

    @Test public void overlayDuringGestureCancelsWebViewTouchOnlyOnce(){
        TouchProtection guard=new TouchProtection();
        assertFalse(guard.next(true,false,false,false).block());
        assertEquals(new TouchProtection.Decision(true,true,false),guard.next(false,false,true,true));
        assertEquals(new TouchProtection.Decision(true,false,false),guard.next(false,false,true,true));
        assertEquals(new TouchProtection.Decision(true,false,false),guard.next(false,true,false,false));
    }

    @Test public void obscuredUpCancelsInsteadOfCompletingPendingSendTap(){
        TouchProtection guard=new TouchProtection();
        guard.next(true,false,false,false);
        assertEquals(new TouchProtection.Decision(true,true,false),guard.next(false,true,true,false));
        assertFalse(guard.next(true,false,false,false).block());
    }

    @Test public void noToolbarDoesNotAllowOverlayTouches(){
        TouchProtection guard=new TouchProtection();
        assertEquals(new TouchProtection.Decision(true,false,false),guard.next(true,false,true,false));
        assertTrue(guard.next(false,false,false,false).block());
        assertTrue(guard.next(false,true,false,false).block());
    }

    @Test public void releasingLongPressDoesNotDismissTheNewSelectionToolbar(){
        TouchProtection guard=new TouchProtection();
        guard.next(true,false,false,false);
        assertEquals(new TouchProtection.Decision(true,true,false),guard.next(false,true,true,true));
        assertEquals(new TouchProtection.Decision(true,false,true),guard.next(true,false,true,true));
    }

    @Test public void newGestureRecoversEvenWhenAndroidOmitsOldCancel(){
        TouchProtection guard=new TouchProtection();
        guard.next(true,false,true,true);
        assertEquals(new TouchProtection.Decision(false,false,false),guard.next(true,false,false,false));
    }

    @Test public void lifecycleResetDoesNotLeakRejectedGestureState(){
        TouchProtection guard=new TouchProtection();
        guard.next(true,false,true,true);
        guard.reset();
        assertEquals(new TouchProtection.Decision(false,false,false),guard.next(true,false,false,false));
    }
}
