package com.contentfoundry.replypilot;

import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiveInboxWorkTest {
    @Test public void slowFullRefreshAndAIWorkDoNotDelayRecentMessages()throws Exception{
        var full=Executors.newSingleThreadExecutor();var ai=Executors.newSingleThreadExecutor();var live=LiveInboxWork.executor();
        CountDownLatch blocked=new CountDownLatch(2),release=new CountDownLatch(1),refreshed=new CountDownLatch(1);
        Runnable wait=()->{blocked.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}};
        try{full.execute(wait);ai.execute(wait);assertTrue(blocked.await(2,TimeUnit.SECONDS));live.execute(refreshed::countDown);assertTrue(refreshed.await(2,TimeUnit.SECONDS));}
        finally{release.countDown();full.shutdownNow();ai.shutdownNow();live.shutdownNow();}
    }
    @Test public void repeatedProviderSignalsCannotCreateAnUnboundedBacklog()throws Exception{
        var live=LiveInboxWork.executor();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try{live.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});assertTrue(entered.await(2,TimeUnit.SECONDS));
            live.execute(()->{});live.execute(()->{});assertThrows(RejectedExecutionException.class,()->live.execute(()->{}));assertEquals(2,live.getQueue().size());assertEquals(1,live.getLargestPoolSize());
        }finally{release.countDown();live.shutdownNow();}
    }
    @Test public void onlyTheReadOnlyDeltaUsesTheFastLane(){
        assertTrue(LiveInboxWork.handles("liveInbox"));
        for(String action:new String[]{"snapshot","conversation","generate","sendNow","sendMms","saveProfile","cacheInbox","suggestReply"})assertFalse(LiveInboxWork.handles(action));
    }
    @Test public void pendingContactNamesCannotSurviveRevocationOrRevisionChange(){
        assertTrue(LiveInboxWork.contactsCurrent(true,true,7,7));assertTrue(LiveInboxWork.contactsCurrent(false,false,7,7));
        assertFalse(LiveInboxWork.contactsCurrent(true,false,7,7));assertFalse(LiveInboxWork.contactsCurrent(false,true,7,7));assertFalse(LiveInboxWork.contactsCurrent(true,true,7,8));
    }
}
