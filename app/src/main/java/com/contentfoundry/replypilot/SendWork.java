package com.contentfoundry.replypilot;

import java.util.concurrent.*;

/** Small send/edit operations never queue behind inbox scans, imports, or AI. */
final class SendWork {
    static final ExecutorService EXECUTOR=executor("reply-pilot-send",64);
    // Carrier callbacks must be accepted even when the user lane is busy. Their
    // durable transitions still share SEND_LOCK with submission and cancellation.
    static final ExecutorService RECEIPTS=Executors.newSingleThreadExecutor(work->thread(work,"reply-pilot-receipts"));
    private SendWork(){}
    static boolean handles(String action){return "sendNow".equals(action)||"saveDraft".equals(action)||"sendState".equals(action);}
    static ThreadPoolExecutor executor(String name,int capacity){return new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(capacity),work->thread(work,name));}
    private static Thread thread(Runnable work,String name){Thread worker=new Thread(work,name);worker.setDaemon(true);return worker;}
}
