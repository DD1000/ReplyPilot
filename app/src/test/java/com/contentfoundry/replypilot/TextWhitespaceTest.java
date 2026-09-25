package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class TextWhitespaceTest {
    private static final int[] SPACES={9,10,11,12,13,32,0x85,0xa0,0x1680,0x2000,0x2001,0x2002,0x2003,0x2004,0x2005,0x2006,0x2007,0x2008,0x2009,0x200a,0x2028,0x2029,0x202f,0x205f,0x3000};
    private static String separator(int cp){return new String(Character.toChars(cp));}

    @Test public void unicodeSeparatorsStillDeduplicateConversationEvidence(){
        for(int cp:SPACES){
            String space=separator(cp);
            ReplyEligibility.Result result=ReplyEligibility.evaluate(List.of(
                new ChatLog.Turn("Me","I'll be there!"),new ChatLog.Turn("Me","I’ll"+space+space+"be"+space+"there."),
                new ChatLog.Turn("Them","I'll be there")));
            assertEquals("U+"+Integer.toHexString(cp),2,result.total());
            assertEquals(1,result.owner());assertEquals(1,result.incoming());
        }
    }
    @Test public void replyWordLimitIncludesEveryUnicodeWhitespaceSeparator(){
        for(int cp:SPACES){
            String space=separator(cp);
            assertFalse("60 words U+"+Integer.toHexString(cp),RequestSafety.unsuitableReply(("a"+space).repeat(59)+"a"));
            assertTrue("61 words U+"+Integer.toHexString(cp),RequestSafety.unsuitableReply(("a"+space).repeat(60)+"a"));
        }
        assertFalse(RequestSafety.unsuitableReply("a\r\n".repeat(59)+"a"));
        assertTrue(RequestSafety.unsuitableReply("a\r\n".repeat(60)+"a"));
    }
    @Test public void ordinaryUnicodeCharactersAndFormatMarksAreNotSeparators(){
        for(String text:List.of("café", "我哥哥", "👍🏽", "a\u180eb", "a\u200bb", "a\u200cb", "a\ufeffb", "a\u001cb"))
            assertArrayEquals(text,new String[]{text},TextWhitespace.RUN.split(text));
    }
}
