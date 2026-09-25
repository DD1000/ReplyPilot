package com.contentfoundry.replypilot;

import org.junit.Test;
import java.io.*;
import java.nio.charset.*;
import static org.junit.Assert.*;

public class MmsTextPolicyTest {
    @Test public void ordinaryMmsTextAcceptsCaseAndCharsetParameters(){
        assertEquals("text/plain",MmsTextPolicy.mime(" Text/Plain; charset=utf-8 "));
        assertEquals("application/smil+xml",MmsTextPolicy.mime("APPLICATION/SMIL+XML"));
        assertEquals("",MmsTextPolicy.mime("text/*"));
    }
    @Test public void fileBackedTextPreservesUnicodeAndNewlines()throws Exception{
        String original="See you later\nNos vemos 😄\n明天见";
        var text=MmsTextPolicy.file(new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8,128000);
        assertEquals(original,text.text());assertFalse(text.truncated());assertFalse(text.unavailable());
    }
    @Test public void carrierCharsetIsRespected()throws Exception{
        String original="Café mañana";
        assertEquals(original,MmsTextPolicy.file(new ByteArrayInputStream(original.getBytes(StandardCharsets.ISO_8859_1)),StandardCharsets.ISO_8859_1,32000).text());
    }
    @Test public void missingIndividualFileIsExplicitRatherThanAnEmptySuccessfulText()throws Exception{
        var missing=MmsTextPolicy.file(null,StandardCharsets.UTF_8,32000);
        assertEquals("",missing.text());assertTrue(missing.unavailable());assertFalse(missing.truncated());
    }
    @Test public void boundedReadingDistinguishesExactLimitFromTruncation()throws Exception{
        assertFalse(MmsTextPolicy.file(new ByteArrayInputStream("12345".getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8,5).truncated());
        var longText=MmsTextPolicy.file(new ByteArrayInputStream("123456".getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8,5);
        assertEquals("12345",longText.text());assertTrue(longText.truncated());
    }
    @Test public void inlineTextDoesNotCutASurrogatePair(){
        var text=MmsTextPolicy.inline("abc😄def",4);assertEquals("abc",text.text());assertTrue(text.truncated());
    }
    @Test public void readerDoesNotSwallowPermissionFailures(){
        InputStream denied=new InputStream(){@Override public int read(){throw new SecurityException("revoked");}};
        assertThrows(SecurityException.class,()->MmsTextPolicy.file(denied,StandardCharsets.UTF_8,100));
    }
    @Test public void largeButValidTextIsNotReducedToInboxPreview()throws Exception{
        String original="old conversation text\n".repeat(5000);
        var text=MmsTextPolicy.file(new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8,HistoryArchivePolicy.RECORD_BYTES);
        assertEquals(original,text.text());assertFalse(text.truncated());
    }
}
