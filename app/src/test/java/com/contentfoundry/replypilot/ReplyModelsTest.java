package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReplyModelsTest {
    @Test public void theChoiceIsKeptPerChat(){
        assertEquals("premium:42",ReplyModels.key(42));
        assertNotEquals(ReplyModels.key(42),ReplyModels.key(43));
        assertThrows(IllegalArgumentException.class,()->ReplyModels.key(0));
        assertThrows(IllegalArgumentException.class,()->ReplyModels.key(-5));
    }
}
