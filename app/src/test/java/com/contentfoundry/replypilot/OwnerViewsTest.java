package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class OwnerViewsTest {
    @Test public void viewsAreTrimmedBoundedText(){
        assertEquals("Very pro AI.",OwnerViews.clean("  Very pro AI.\n"));
        assertEquals("",OwnerViews.clean(null));
        assertEquals("ab",OwnerViews.clean("a\0b"));
        assertEquals(1200,OwnerViews.clean("x".repeat(1200)).length());
        assertThrows(IllegalArgumentException.class,()->OwnerViews.clean("x".repeat(1201)));
    }
}
