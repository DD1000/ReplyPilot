package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class MessageChangesTest {
    private static final class DeliveryQueue implements Executor {
        final Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();
        @Override public void execute(Runnable command){tasks.add(command);}
        void drain(){Runnable task;while((task=tasks.poll())!=null)task.run();}
    }

    @Test public void carrierPublicationDoesNotRunUiWorkInline(){
        DeliveryQueue ui=new DeliveryQueue();AtomicInteger deliveries=new AtomicInteger();
        Runnable listener=deliveries::incrementAndGet;
        try(MessageChanges.Subscription ignored=MessageChanges.subscribe(ui,listener)){
            MessageChanges.publish();
            assertEquals(0,deliveries.get());
            ui.drain();
            assertEquals(1,deliveries.get());
        }
    }

    @Test public void pauseSuppressesQueuedAndFutureDeliveries(){
        DeliveryQueue ui=new DeliveryQueue();AtomicInteger deliveries=new AtomicInteger();
        Runnable listener=deliveries::incrementAndGet;
        MessageChanges.Subscription subscription=MessageChanges.subscribe(ui,listener);
        try{
            MessageChanges.publish();
            subscription.close();
            MessageChanges.publish();
            ui.drain();
            assertEquals(0,deliveries.get());
        }finally{subscription.close();}
    }

    @Test public void resumeDoesNotReviveThePreviousActivitiesQueuedEvent(){
        DeliveryQueue ui=new DeliveryQueue();AtomicInteger deliveries=new AtomicInteger();
        Runnable listener=deliveries::incrementAndGet;
        MessageChanges.Subscription previous=MessageChanges.subscribe(ui,listener);
        MessageChanges.publish();previous.close();
        try(MessageChanges.Subscription current=MessageChanges.subscribe(ui,listener)){
            ui.drain();
            assertEquals(0,deliveries.get());
            MessageChanges.publish();ui.drain();
            assertEquals(1,deliveries.get());
        }
    }

    @Test public void aBurstQueuesOneDeliveryAndLaterChangesStillArrive(){
        DeliveryQueue ui=new DeliveryQueue();AtomicInteger deliveries=new AtomicInteger();
        Runnable listener=deliveries::incrementAndGet;
        try(MessageChanges.Subscription ignored=MessageChanges.subscribe(ui,listener)){
            for(int i=0;i<100;i++)MessageChanges.publish();
            assertEquals(1,ui.tasks.size());
            ui.drain();assertEquals(1,deliveries.get());
            MessageChanges.publish();ui.drain();assertEquals(2,deliveries.get());
        }
    }

    @Test public void aChangeDuringDeliveryIsNotLost(){
        DeliveryQueue ui=new DeliveryQueue();AtomicInteger deliveries=new AtomicInteger();
        Runnable listener=()->{if(deliveries.incrementAndGet()==1)MessageChanges.publish();};
        try(MessageChanges.Subscription ignored=MessageChanges.subscribe(ui,listener)){
            MessageChanges.publish();ui.drain();
            assertEquals(2,deliveries.get());
        }
    }

    @Test public void concurrentSendAndReceiptSignalsCoalesce()throws Exception{
        DeliveryQueue ui=new DeliveryQueue();AtomicInteger deliveries=new AtomicInteger();
        Runnable listener=deliveries::incrementAndGet;
        try(MessageChanges.Subscription ignored=MessageChanges.subscribe(ui,listener)){
            CountDownLatch start=new CountDownLatch(1);Thread[] publishers=new Thread[8];
            for(int i=0;i<publishers.length;i++){
                publishers[i]=new Thread(()->{
                    try{start.await();}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
                    for(int n=0;n<100;n++)MessageChanges.publish();
                });publishers[i].start();
            }
            start.countDown();for(Thread publisher:publishers)publisher.join();
            assertEquals(1,ui.tasks.size());
            ui.drain();assertEquals(1,deliveries.get());
        }
    }

    @Test public void unavailableUiExecutorCannotFailSendingOrBlockLaterSignals(){
        DeliveryQueue ui=new DeliveryQueue();AtomicBoolean reject=new AtomicBoolean(true);
        AtomicInteger deliveries=new AtomicInteger();Runnable listener=deliveries::incrementAndGet;
        Executor executor=task->{if(reject.get())throw new RejectedExecutionException();ui.execute(task);};
        try(MessageChanges.Subscription ignored=MessageChanges.subscribe(executor,listener)){
            MessageChanges.publish();assertTrue(ui.tasks.isEmpty());
            reject.set(false);MessageChanges.publish();ui.drain();
            assertEquals(1,deliveries.get());
        }
    }
}
