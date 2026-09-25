package com.contentfoundry.replypilot;

import static org.junit.Assert.*;
import org.junit.Test;

public class MediaGalleryPolicyTest {
    @Test public void normalTextsAndPresentationPartsDoNotMakeMediaCards(){
        for(String type:new String[]{"text/plain","TEXT/PLAIN; charset=UTF-8","text/html","application/smil","Application/SMIL+XML; charset=utf-8","multipart/related"})assertFalse(type,MediaGalleryPolicy.attachment(12,type));
    }
    @Test public void actualFilesRemainEligibleIncludingContactCards(){
        for(String type:new String[]{"image/jpeg","image/gif","audio/amr","video/mp4","text/vcard","text/x-vcard","application/pdf","application/octet-stream"})assertTrue(type,MediaGalleryPolicy.attachment(12,type));
        assertEquals("image/jpeg",MediaGalleryPolicy.type(" IMAGE/JPEG ; name=photo.jpg"));
        assertTrue(MediaGalleryPolicy.attachment(12," IMAGE/JPEG ; name=photo.jpg"));
    }
    @Test public void captionsRemainVisibleBesideAttachments(){
        assertTrue(MediaGalleryPolicy.displayPart(7,"text/plain; charset=utf-8"));
        assertFalse(MediaGalleryPolicy.attachment(7,"text/plain; charset=utf-8"));
        assertTrue(MediaGalleryPolicy.displayPart(8,"image/png"));
        assertFalse(MediaGalleryPolicy.displayPart(9,"text/html"));
        assertFalse(MediaGalleryPolicy.displayPart(10,"application/smil+xml"));
    }
    @Test public void malformedMetadataAndMissingPartIdsNeverClaimAnAttachment(){
        for(String type:new String[]{""," ","image","image/","/jpeg","image/*","*/jpeg","application/<script>","image /jpeg","image/jpeg\n<script>","image/jpeg/extra"})assertFalse(type,MediaGalleryPolicy.attachment(12,type));
        assertFalse(MediaGalleryPolicy.attachment(12,null));
        for(long id:new long[]{-1,0,9_007_199_254_740_992L})assertFalse(MediaGalleryPolicy.attachment(id,"image/png"));
    }
    @Test public void draftAndUndownloadedOrProtocolEnvelopesAreNotGalleryMedia(){
        for(int box:new int[]{1,2,4,5})for(int type:new int[]{128,132})assertTrue(MediaGalleryPolicy.message(box,type));
        for(int type:new int[]{128,132})assertFalse(MediaGalleryPolicy.message(3,type));
        for(int type:new int[]{0,129,130,131,133,134,135,136})assertFalse(MediaGalleryPolicy.message(1,type));
    }
}
