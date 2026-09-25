package com.contentfoundry.replypilot;

import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrainingWorkTest {
    @Test public void slowPracticeGenerationDoesNotBlockOpeningPractice()throws Exception{
        var generation=TrainingWork.generationExecutor();var state=TrainingWork.stateExecutor();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),shown=new CountDownLatch(1);
        try{
            generation.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            assertTrue(entered.await(2,TimeUnit.SECONDS));state.execute(shown::countDown);
            assertTrue("Practice controls must open while a scenario is generating",shown.await(2,TimeUnit.SECONDS));
        }finally{release.countDown();generation.shutdownNow();state.shutdownNow();}
    }
    @Test public void repeatedStartTapsCannotQueueMoreNetworkRequests()throws Exception{
        var generation=TrainingWork.generationExecutor();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try{
            generation.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            for(int i=0;i<20;i++)assertThrows(RejectedExecutionException.class,()->generation.execute(()->fail("Must not queue a second generation")));
            assertEquals(0,generation.getQueue().size());assertEquals(1,generation.getLargestPoolSize());
        }finally{release.countDown();generation.shutdownNow();}
    }
    @Test public void repeatedStateRequestsHaveABoundedQueue()throws Exception{
        var state=TrainingWork.stateExecutor();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try{
            state.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            assertTrue(entered.await(2,TimeUnit.SECONDS));for(int i=0;i<4;i++)state.execute(()->{});
            assertThrows(RejectedExecutionException.class,()->state.execute(()->{}));assertEquals(4,state.getQueue().size());
        }finally{release.countDown();state.shutdownNow();}
    }
    @Test public void OnlyPracticeUsesTheseWorkers(){
        for(String action:new String[]{"trainPilotState","trainPilotStart","trainPilotReply","clearPilotTraining"})assertTrue(TrainingWork.handles(action));
        for(String action:new String[]{"generate","snapshot","suggestReply","sendNow","conversation","explainMessage"})assertFalse(TrainingWork.handles(action));
        assertSame(TrainingWork.STATE,TrainingWork.executor("trainPilotState"));assertSame(TrainingWork.GENERATION,TrainingWork.executor("trainPilotReply"));
    }
}
