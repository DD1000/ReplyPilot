package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationCachePolicyTest {
    @Test public void previewsExpireAtThirtySeconds(){ConversationCachePolicy cache=new ConversationCachePolicy();assertTrue(cache.put(1,"page",4,7,5,100));assertEquals("page",cache.get(1,7,5,30_099));assertNull(cache.get(1,7,5,30_100));assertEquals(0,cache.size());assertEquals(0,cache.bytes());}
    @Test public void clockResetCannotResurrectPages(){ConversationCachePolicy cache=new ConversationCachePolicy();cache.put(1,"page",4,7,5,100);assertNull(cache.get(1,7,5,99));assertEquals(0,cache.size());}
    @Test public void providerInvalidationNeverReturnsOldEpoch(){ConversationCachePolicy cache=new ConversationCachePolicy();cache.put(1,"private history",15,7,5,100);assertNull(cache.get(1,8,5,101));assertEquals(0,cache.bytes());}
    @Test public void PermissionOrRoleChangesInvalidateEvenWithoutProviderEvent(){for(int changed:new int[]{0,1,3,7}){ConversationCachePolicy cache=new ConversationCachePolicy();cache.put(1,"private history",15,7,5,100);assertNull(cache.get(1,7,changed,101));}}
    @Test public void lateLoadCannotBeReadInNewRevision(){ConversationCachePolicy cache=new ConversationCachePolicy();long capturedBeforeInvalidation=7;cache.clear();cache.put(1,"old query",9,capturedBeforeInvalidation,5,100);assertNull(cache.get(1,8,5,101));}
    @Test public void leastRecentlyUsedThreadIsEvicted(){ConversationCachePolicy cache=new ConversationCachePolicy();for(int i=1;i<=12;i++)cache.put(i,"page"+i,8,7,5,100);assertEquals("page1",cache.get(1,7,5,101));cache.put(13,"new page",8,7,5,102);assertEquals(12,cache.size());assertNull(cache.get(2,7,5,103));assertEquals("page1",cache.get(1,7,5,103));assertEquals("new page",cache.get(13,7,5,103));}
    @Test public void byteBudgetBoundsMemoryIndependentlyOfThreadCount(){ConversationCachePolicy cache=new ConversationCachePolicy();for(int i=1;i<=5;i++)cache.put(i,"serialized",512*1024,7,5,100);assertEquals(4,cache.size());assertEquals(2*1024*1024,cache.bytes());assertNull(cache.get(1,7,5,101));assertEquals("serialized",cache.get(5,7,5,101));}
    @Test public void replacingOneThreadAccountsOnlyItsLatestPage(){ConversationCachePolicy cache=new ConversationCachePolicy();cache.put(1,"first",5,7,5,100);cache.put(1,"second",6,7,5,101);assertEquals(1,cache.size());assertEquals(6,cache.bytes());assertEquals("second",cache.get(1,7,5,102));}
    @Test public void OversizedAndInvalidEntriesAreNotRetained(){ConversationCachePolicy cache=new ConversationCachePolicy();assertFalse(cache.put(1,"oversized",ConversationCachePolicy.MAX_PAGE_BYTES+1,7,5,100));assertFalse(cache.put(0,"invalid thread",14,7,5,100));assertFalse(cache.put(1,null,0,7,5,100));assertFalse(cache.put(1,"page",4,7,5,-1));assertEquals(0,cache.size());assertEquals(0,cache.bytes());}
    @Test public void explicitClearRemovesAllPrivatePages(){ConversationCachePolicy cache=new ConversationCachePolicy();cache.put(1,"first",5,7,5,100);cache.put(2,"second",6,7,5,100);cache.clear();assertEquals(0,cache.size());assertEquals(0,cache.bytes());assertNull(cache.get(1,7,5,101));assertNull(cache.get(2,7,5,101));}
}
