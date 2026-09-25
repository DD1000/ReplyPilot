package com.contentfoundry.replypilot;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SharePolicyTest {
    private static final String OWN="com.contentfoundry.replypilot";
    @Test public void preservesLinksAndAppendsWithoutReplacingAnExistingDraft(){
        assertEquals("See this\n\nhttps://example.com/post?a=1&b=2",SharePolicy.append("See this","https://example.com/post?a=1&b=2"));
        assertEquals("photo caption",SharePolicy.append("","photo caption"));
        assertEquals("original",SharePolicy.append("original",""));
    }
    @Test public void oversizedSharesAndCombinedDraftsAreRejectedRatherThanTruncated(){
        assertEquals(1600,SharePolicy.text("x".repeat(1600)).length());
        assertThrows(IllegalArgumentException.class,()->SharePolicy.text("x".repeat(1601)));
        assertThrows(IllegalArgumentException.class,()->SharePolicy.append("x".repeat(1599),"y"));
        assertThrows(IllegalArgumentException.class,()->SharePolicy.text("bad\0text"));
    }
    @Test public void rejectsPrivateProvidersAndNonContentUrisEvenWhenCallerClaimsReadPermission(){
        for(String uri:List.of("file:///data/user/0/secrets","https://example.com/photo.jpg","android.resource://other/raw/image","content://mms/part/3","content://sms/4","content://mms-sms/conversations","content://com.android.contacts/contacts/1","content://com.google.android.contacts/1","content://com.android.providers.telephony/1","content://"+OWN+".files/private/file","content://0@"+OWN+".files/private/file","content://com.android.%63ontacts/1"))assertThrows(uri,IllegalArgumentException.class,()->SharePolicy.uri(uri,OWN));
    }
    @Test public void contactCardsUseOnlyTheExportEndpointNotRawContactRows(){
        assertEquals("content://com.android.contacts/contacts/as_vcard/person",SharePolicy.uri("content://com.android.contacts/contacts/as_vcard/person",OWN));
        assertThrows(IllegalArgumentException.class,()->SharePolicy.uri("content://com.android.contacts/contacts/as_vcard/person/photo",OWN));
        assertThrows(IllegalArgumentException.class,()->SharePolicy.uri("content://com.android.contacts/contacts/as_vcard/person?raw=1",OWN));
    }
    @Test public void deduplicatesSharedStreamsAndKeepsTheirOrder(){
        assertEquals(List.of("content://photos.example/1","content://photos.example/2"),SharePolicy.uris(List.of("content://photos.example/1","content://photos.example/1","content://photos.example/2"),OWN));
        List<String> tooMany=new ArrayList<>();for(int i=0;i<7;i++)tooMany.add("content://photos.example/"+i);
        assertThrows(IllegalArgumentException.class,()->SharePolicy.uris(tooMany,OWN));
        assertThrows(IllegalArgumentException.class,()->SharePolicy.uris(Collections.nCopies(33,"content://photos.example/1"),OWN));
    }
    @Test public void onlyTextAndMmsCategoriesAppearAsSupported(){
        for(String type:List.of("text/plain","image/jpeg","image/*","video/mp4","audio/mpeg","text/vcard","text/x-vcard"))assertTrue(SharePolicy.mime(type));
        for(String type:List.of("application/pdf","text/html","application/zip","*/*"))assertFalse(SharePolicy.mime(type));
    }
}
