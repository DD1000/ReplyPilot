package com.contentfoundry.replypilot;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MmsContentPolicyTest {
    private static MmsContentPolicy.Part part(long id,String mime){return new MmsContentPolicy.Part(id,mime,false,false);}
    private static String kind(String body,MmsContentPolicy.Part... parts){return MmsContentPolicy.classify(List.of(parts),body,true);}

    @Test public void plainTextMmsIsTextEvenWithPresentationParts(){
        assertEquals("text",kind("Hello there",part(1,"application/smil"),part(2,"text/plain"),part(3,"application/smil+xml")));
    }
    @Test public void mimeParametersAndCaseDoNotTurnTextIntoMedia(){
        assertEquals("text",kind("Hello there",part(1," Text/Plain; charset=UTF-8 ")));
    }
    @Test public void severalDecodedTextPartsRemainText(){
        assertEquals("text",kind("first\nsecond",part(1,"text/plain"),part(2,"text/plain")));
    }
    @Test public void captionWithPhotoVideoOrAudioIsNotTextOnly(){
        for(String mime:List.of("image/jpeg","video/mp4","audio/mpeg"))assertEquals("attachments",kind("caption",part(1,"text/plain"),part(2,mime)));
    }
    @Test public void UnsupportedAttachmentsRemainProtected(){
        for(String mime:List.of("application/pdf","text/vcard","text/html","application/octet-stream","multipart/mixed"))assertEquals("attachments",kind("caption",part(1,"text/plain"),part(2,mime)));
    }
    @Test public void emptyOrSmilOnlyMessageIsUnknown(){
        assertEquals("unknown",kind(""));
        assertEquals("unknown",kind("",part(1,"application/smil")));
        assertEquals("unknown",kind(" \n",part(1,"text/plain")));
    }
    @Test public void missingInvalidOrUnsafePartIdentityCannotEnableTextDrafts(){
        for(long id:List.of(0L,-1L,9_007_199_254_740_992L))assertEquals("unknown",kind("caption",part(1,"text/plain"),part(id,"application/smil")));
    }
    @Test public void missingOrMalformedMimeCannotEnableTextDrafts(){
        for(String mime:List.of("","unknown","image/*","/jpeg"))assertEquals("unknown",kind("caption",part(1,"text/plain"),part(2,mime)));
        assertEquals("unknown",kind("caption",part(1,null)));
    }
    @Test public void unavailableAndTruncatedTextAreNotCompleteEvidence(){
        assertEquals("unknown",kind("partial",new MmsContentPolicy.Part(1,"text/plain",true,false)));
        assertEquals("unknown",kind("partial",new MmsContentPolicy.Part(1,"text/plain",false,true)));
        assertEquals("unknown",MmsContentPolicy.classify(List.of(part(1,"text/plain")),"partial",false));
    }
    @Test public void incompleteScanCannotHideAFileAfterThePartLimit(){
        assertEquals("unknown",MmsContentPolicy.classify(List.of(part(1,"text/plain")),"caption",false));
    }
    @Test public void longTextStaysTextButCannotBeSilentlyTruncatedForAi(){
        String body="a".repeat(1601);
        assertEquals("text",kind(body,part(1,"text/plain")));
        assertFalse(MmsContentPolicy.draftableText("text",body));
        assertTrue(MmsContentPolicy.draftableText("text",body.substring(0,1600)));
        assertFalse(MmsContentPolicy.draftableText("unknown","hello"));
        assertFalse(MmsContentPolicy.draftableText("attachments","hello"));
    }
}
