package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestPromptTest {
    @Test public void testUsesSuppliedMessageContextAndStyle(){
        ReplyPrompt.Prepared p=TestPrompt.prepare("Can you cover my shift?","Coworker; keep it professional.","yeah sounds good\nlet me check","Natural");
        assertEquals(2,p.styleSamples());
        assertTrue(p.text().contains("Coworker; keep it professional."));
        assertTrue(p.text().endsWith("\"speaker\":\"them\",\"text\":\"Can you cover my shift?\"}]}"));
    }
    @Test public void emptyOrLongMessagesAreRejected(){
        assertThrows(IllegalArgumentException.class,()->TestPrompt.prepare(" \n ","","","Natural"));
        assertThrows(IllegalArgumentException.class,()->TestPrompt.prepare("x".repeat(361),"","","Natural"));
        assertTrue(TestPrompt.prepare("x".repeat(360),"","","Natural").text().contains("x".repeat(360)));
    }
    @Test public void excessiveStyleExamplesAreRejectedInsteadOfTruncated(){
        assertThrows(IllegalArgumentException.class,()->TestPrompt.prepare("hello","","one\ntwo\nthree\nfour\nfive\nsix\nseven","Natural"));
        assertThrows(IllegalArgumentException.class,()->TestPrompt.prepare("hello","","x".repeat(221),"Natural"));
        assertThrows(IllegalArgumentException.class,()->TestPrompt.prepare("hello","","x".repeat(1401),"Natural"));
    }
    @Test public void subsequentTestDoesNotRetainPreviousInputs(){
        TestPrompt.prepare("earlier test","private previous context","unique old example","Natural");
        ReplyPrompt.Prepared p=TestPrompt.prepare("new message",null,null,null);
        assertEquals(0,p.styleSamples());
        assertFalse(p.text().contains("private previous context"));
        assertFalse(p.text().contains("unique old example"));
    }
}
