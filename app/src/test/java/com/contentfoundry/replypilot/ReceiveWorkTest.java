package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class ReceiveWorkTest {
    @Test public void receiptsStayOrderedWhileInboxIsBlocked()throws Exception{
        ExecutorService inbox=ReceiveWork.serial("test-blocked-inbox"),incoming=ReceiveWork.serial("test-incoming");
        CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1),received=new CountDownLatch(3);
        List<Integer> order=new CopyOnWriteArrayList<>();
        try{
            inbox.execute(()->{blocked.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            assertTrue(blocked.await(1,TimeUnit.SECONDS));
            for(int i=1;i<=3;i++){int id=i;incoming.execute(()->{order.add(id);received.countDown();});}
            assertTrue(received.await(1,TimeUnit.SECONDS));assertEquals(List.of(1,2,3),order);
            assertEquals(1,release.getCount());
        }finally{release.countDown();inbox.shutdownNow();incoming.shutdownNow();}
    }
}
