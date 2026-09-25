package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class ContactPhotoPolicyTest {
    @Test public void formattedPhonesGetOnlyLocalCanonicalPaths(){
        assertEquals("/contact-photo/%2B12025550101",ContactPhotoPolicy.path("+1 (202) 555-0101"));
        assertEquals("/contact-photo/2025550101",ContactPhotoPolicy.path("202-555-0101"));
        assertEquals("+12025550101",ContactPhotoPolicy.address("/contact-photo/%2B12025550101",null,null));
        assertEquals("2025550101",ContactPhotoPolicy.address("/contact-photo/2025550101",null,null));
        for(String number:new String[]{"person@example.com","https://remote.invalid/pic","content://com.android.contacts/1","12","+12025550101 ext 4","1/2/3",null})assertEquals("",ContactPhotoPolicy.path(number));
    }
    @Test public void alternateEncodingAndTraversalNeverBecomeProviderQueries(){
        for(String path:new String[]{"/contact-photo/+12025550101","/contact-photo/%2b12025550101","/contact-photo/%252B12025550101","/contact-photo/%31%32%33","/contact-photo/../123","/contact-photo/%2F123","/contact-photo/123/456","/contact-photo/123\\456","/contact-photo/123?x=1","/contact-photo/123#x","/contact-photo/12","/contact-photo/"+"1".repeat(26),"/contact-photo/１２３","/contact-photo/123\n",null})assertNull(path,ContactPhotoPolicy.address(path,null,null));
        assertNull(ContactPhotoPolicy.address("/contact-photo/123","",null));assertNull(ContactPhotoPolicy.address("/contact-photo/123",null,""));
    }
    @Test public void everyPermissionAndForegroundConditionIsNecessary(){
        for(int bits=0;bits<16;bits++)assertEquals(bits==15,ContactPhotoPolicy.allowed((bits&1)!=0,(bits&2)!=0,(bits&4)!=0,(bits&8)!=0));
    }
    @Test public void boundsReadRejectsMalformedAndEnormousDimensionsBeforeDecoding(){
        for(int[] size:new int[][]{{0,128},{128,0},{-1,128},{Integer.MAX_VALUE,2},{32769,1},{32768,32768}}){assertFalse(ContactPhotoPolicy.dimensions(size[0],size[1]));assertEquals(0,ContactPhotoPolicy.sample(size[0],size[1]));}
        assertThrows(IllegalArgumentException.class,()->ContactPhotoPolicy.fit(0,2));
    }
    @Test public void decodeSamplesKeepIntermediateSmallAndFinalThumbnailNeverUpscales(){
        assertEquals(1,ContactPhotoPolicy.sample(128,96));assertEquals(16,ContactPhotoPolicy.sample(4000,3000));
        assertEquals(new ContactPhotoPolicy.Size(128,96),ContactPhotoPolicy.fit(4000,3000));
        assertEquals(new ContactPhotoPolicy.Size(96,128),ContactPhotoPolicy.fit(3000,4000));
        assertEquals(new ContactPhotoPolicy.Size(32,48),ContactPhotoPolicy.fit(32,48));
        assertEquals(new ContactPhotoPolicy.Size(128,1),ContactPhotoPolicy.fit(32768,1));
        for(int width:new int[]{1,127,128,129,255,256,257,2048,4096,32768}){int sample=ContactPhotoPolicy.sample(width,1);assertTrue(sample>0&&(sample&(sample-1))==0);assertTrue((width+sample-1)/sample<=256);assertTrue(ContactPhotoPolicy.fit(width,1).width()<=128);}
    }
    @Test public void bothImageCountAndTotalByteBoundsEvictLeastRecentlyUsed(){
        var cache=new ContactPhotoPolicy.Cache();long version=cache.revision();
        for(int i=0;i<80;i++)assertTrue(cache.put("1202555"+String.format("%04d",i),version,new byte[1]));
        assertNotNull(cache.get("12025550000"));cache.put("12025559999",version,new byte[1]);
        assertEquals(80,cache.size());assertNotNull(cache.get("12025550000"));assertNull(cache.get("12025550001"));assertEquals(80,cache.bytes());
        cache.clear();version=cache.revision();for(int i=0;i<12;i++)cache.put("1202555"+String.format("%04d",i),version,new byte[ContactPhotoPolicy.MAX_IMAGE]);
        assertEquals(8,cache.size());assertEquals(ContactPhotoPolicy.MAX_BYTES,cache.bytes());assertNull(cache.get("12025550003"));assertNotNull(cache.get("12025550004"));
    }
    @Test public void revokedAccessOrContactChangeCannotRepopulateFromOldRead(){
        var cache=new ContactPhotoPolicy.Cache();long first=cache.revision();cache.put("12025550101",first,new byte[]{1});cache.clear();
        assertFalse(cache.current(first));assertFalse(cache.put("12025550101",first,new byte[]{2}));assertNull(cache.get("12025550101"));assertEquals(0,cache.bytes());
        assertTrue(cache.put("12025550101",cache.revision(),new byte[]{3}));assertArrayEquals(new byte[]{3},cache.get("12025550101"));
    }
    @Test public void missingImagesAreBoundedAndCachedValuesAreNotMutableByCallers(){
        var cache=new ContactPhotoPolicy.Cache();long version=cache.revision();byte[] bytes={1};cache.put("12025550101",version,bytes);bytes[0]=9;
        byte[] value=cache.get("12025550101");assertEquals(1,value[0]);value[0]=7;assertEquals(1,cache.get("12025550101")[0]);
        assertTrue(cache.put("12025550102",version,new byte[0]));assertNotNull(cache.get("12025550102"));assertEquals(0,cache.get("12025550102").length);
        assertFalse(cache.put("12025550103",version,new byte[ContactPhotoPolicy.MAX_IMAGE+1]));assertFalse(cache.put("../123",version,new byte[1]));assertFalse(cache.put("12025550101",version,null));
        cache.put("12025550101",version,new byte[3]);assertEquals(3,cache.bytes());
    }
}
