package com.contentfoundry.replypilot;

import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LiveHistoryWorkTest {
    @Test public void delayedInboxAndOneSlowChatDoNotBlockAnotherLiveChat()throws Exception{
        var inbox=Executors.newSingleThreadExecutor();var live=LiveHistoryWork.executor();
        CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1),opened=new CountDownLatch(1);
        Runnable blocked=()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}};
        try{
            inbox.execute(blocked);live.execute(blocked);assertTrue(entered.await(2,TimeUnit.SECONDS));
            live.execute(opened::countDown);assertTrue("A slow inbox and old chat must not block opening a new chat",opened.await(2,TimeUnit.SECONDS));
        }finally{release.countDown();inbox.shutdownNow();live.shutdownNow();}
    }
    @Test public void rapidChatRequestsCannotBuildAnUnboundedQueue()throws Exception{
        var live=LiveHistoryWork.executor();CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1);
        Runnable blocked=()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}};
        try{
            live.execute(blocked);live.execute(blocked);assertTrue(entered.await(2,TimeUnit.SECONDS));
            for(int i=0;i<4;i++)live.execute(()->{});
            assertThrows(RejectedExecutionException.class,()->live.execute(()->{}));
            assertEquals(4,live.getQueue().size());assertEquals(2,live.getLargestPoolSize());
        }finally{release.countDown();live.shutdownNow();}
    }
    @Test public void onlyForegroundReadsUseTheLiveWorkers(){
        for(String action:new String[]{"conversation","historyPage","mediaConversation"})assertTrue(LiveHistoryWork.handles(action));
        for(String action:new String[]{"snapshot","sendNow","sendMms","generate","approve","saveDraft","saveProfile","suggestReply","cacheHistory","prefetchHistory"})assertFalse(LiveHistoryWork.handles(action));
    }
    @Test public void pausingOrLosingMessageAccessInvalidatesPendingData(){
        assertTrue(LiveHistoryWork.canDeliver(5,5,true,true,true));
        assertFalse(LiveHistoryWork.canDeliver(5,6,true,true,true));
        assertFalse(LiveHistoryWork.canDeliver(5,5,false,true,true));
        assertFalse(LiveHistoryWork.canDeliver(5,5,true,false,true));
        assertFalse(LiveHistoryWork.canDeliver(5,5,true,true,false));
    }
}
