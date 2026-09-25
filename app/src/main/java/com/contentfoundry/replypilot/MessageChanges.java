package com.contentfoundry.replypilot;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Process-local invalidation only: no message text, address, or Activity is retained. */
final class MessageChanges {
    private static final CopyOnWriteArrayList<Subscription> listeners=new CopyOnWriteArrayList<>();

    static Subscription subscribe(Executor executor,Runnable listener){
        Subscription subscription=new Subscription(executor,listener);
        listeners.add(subscription);
        return subscription;
    }

    static void publish(){ConversationHistoryCache.invalidate();for(Subscription subscription:listeners)subscription.enqueue();}

    static final class Subscription implements AutoCloseable {
        private final Executor executor;
        private final WeakReference<Runnable> listener;
        private final AtomicBoolean pending=new AtomicBoolean();
        private volatile boolean active=true;

        private Subscription(Executor executor,Runnable listener){
            this.executor=Objects.requireNonNull(executor);
            this.listener=new WeakReference<>(Objects.requireNonNull(listener));
        }
        private void enqueue(){
            if(!active)return;
            if(listener.get()==null){close();return;}
            if(!pending.compareAndSet(false,true))return;
            // The Activity supplies a main-thread executor; carrier work never waits
            // for the WebView or its refresh. A stopped executor cannot fail a send.
            try{executor.execute(this::deliver);}catch(RuntimeException stopped){pending.set(false);}
        }
        private void deliver(){
            pending.set(false);
            if(!active)return;
            Runnable callback=listener.get();
            if(callback==null){close();return;}
            callback.run();
        }
        @Override public void close(){active=false;listeners.remove(this);}
    }
}
