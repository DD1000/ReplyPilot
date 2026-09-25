package com.contentfoundry.replypilot;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ChatLogFileTest {
    private String read(String value)throws IOException{return ChatLogFile.read(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));}
    @Test public void decodesUnicodeAndRemovesOnlyTheLeadingBom()throws Exception{
        assertEquals("Me: café 👍\nThem: Hi",read("\uFEFFMe: café 👍\nThem: Hi"));
    }
    @Test public void byteLimitAppliesBeforeUnicodeDecoding()throws Exception{
        assertEquals(ChatLogFile.MAX_BYTES,read("x".repeat(ChatLogFile.MAX_BYTES)).length());
        assertThrows(IllegalArgumentException.class,()->read("x".repeat(ChatLogFile.MAX_BYTES+1)));
        assertThrows(IllegalArgumentException.class,()->read("é".repeat(ChatLogFile.MAX_BYTES/2+1)));
    }
    @Test public void malformedUtf8AndBinaryDataAreRejected(){
        assertThrows(IllegalArgumentException.class,()->ChatLogFile.read(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28})));
        assertThrows(IllegalArgumentException.class,()->read("Me: hello\0Them: hi"));
        assertThrows(IllegalArgumentException.class,()->ChatLogFile.read(null));
    }
    @Test public void largeUnboundedProviderStreamStopsAtTheLimit()throws Exception{
        int[] consumed={0};
        InputStream stream=new InputStream(){public int read(){consumed[0]++;return 'x';}};
        assertThrows(IllegalArgumentException.class,()->ChatLogFile.read(stream));
        assertTrue(consumed[0]<=ChatLogFile.MAX_BYTES+4096);
    }
}
