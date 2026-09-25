package com.contentfoundry.replypilot;
import org.junit.Test;
import static org.junit.Assert.*;
public class MediaRangePolicyTest {
    @Test public void fullOpenAndSuffixRangesAreBounded(){
        assertEquals(new MediaRangePolicy.Range(0,100,false),MediaRangePolicy.range(null,100));
        assertEquals(new MediaRangePolicy.Range(10,90,true),MediaRangePolicy.range("bytes=10-",100));
        assertEquals(new MediaRangePolicy.Range(10,11,true),MediaRangePolicy.range("bytes=10-20",100));
        assertEquals(new MediaRangePolicy.Range(90,10,true),MediaRangePolicy.range("bytes=-10",100));
        assertEquals(new MediaRangePolicy.Range(0,100,true),MediaRangePolicy.range("bytes=-1000",100));
        assertEquals(new MediaRangePolicy.Range(90,10,true),MediaRangePolicy.range("bytes=90-999",100));
    }
    @Test public void malformedOverflowAndMultipleRangesReject(){
        for(String header:new String[]{"bytes=100-","bytes=20-10","bytes=-0","bytes=-","bytes=1-2,5-6","bytes=999999999999999999999-","bytes=+1-2","items=1-2"})assertThrows(IllegalArgumentException.class,()->MediaRangePolicy.range(header,100));
        assertThrows(IllegalArgumentException.class,()->MediaRangePolicy.range("bytes=0-",0));
    }
    @Test public void unknownSizeStreamsWithoutInventedRange(){assertEquals(new MediaRangePolicy.Range(0,-1,false),MediaRangePolicy.range("bytes=1-",-1));}
    @Test public void onlyNonExecutableMediaMimeTypesAreServed(){
        for(String mime:new String[]{"image/jpeg","image/png","video/mp4","audio/3gpp","audio/mpeg"})assertTrue(MediaRangePolicy.supported(mime));
        for(String mime:new String[]{"text/html","image/svg+xml","video/mp4\ntext/html","application/javascript","image/"})assertFalse(MediaRangePolicy.supported(mime));
    }
}
