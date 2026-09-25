package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class SendWorkTest {
    @Test public void routesOnlySmallSendAndEditActions(){
        for(String action:new String[]{"sendNow","saveDraft","sendState"})assertTrue(SendWork.handles(action));
        for(String action:new String[]{"snapshot","generate","suggestReply","prefetchHistory","saveProfile","inbox",null})assertFalse(SendWork.handles(action));
    }
    @Test public void lanePreservesEditSendOrderAndBoundsPendingWork()throws Exception{
        ThreadPoolExecutor lane=SendWork.executor("test-send",2);CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(2);List<String> order=new CopyOnWriteArrayList<>();
        try{
            lane.execute(()->{started.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            assertTrue(started.await(1,TimeUnit.SECONDS));
            lane.execute(()->{order.add("saveDraft");finished.countDown();});
            lane.execute(()->{order.add("sendNow");finished.countDown();});
            assertThrows(RejectedExecutionException.class,()->lane.execute(()->order.add("unexpected")));
            release.countDown();assertTrue(finished.await(2,TimeUnit.SECONDS));assertEquals(List.of("saveDraft","sendNow"),order);
        }finally{release.countDown();lane.shutdownNow();}
    }
    @Test public void statusLaneDoesNotWaitForUnrelatedInboxWork()throws Exception{
        ExecutorService inbox=Executors.newSingleThreadExecutor();ThreadPoolExecutor lane=SendWork.executor("test-send-status",2);CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1),status=new CountDownLatch(1);
        try{
            inbox.execute(()->{blocked.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(blocked.await(1,TimeUnit.SECONDS));
            lane.execute(status::countDown);assertTrue(status.await(1,TimeUnit.SECONDS));
        }finally{release.countDown();inbox.shutdownNow();lane.shutdownNow();}
    }
}
