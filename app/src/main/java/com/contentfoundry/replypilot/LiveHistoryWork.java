package com.contentfoundry.replypilot;

import java.util.concurrent.*;

/** Opening a live chat must not wait behind the full inbox or carrier recovery. */
final class LiveHistoryWork {
    static final ExecutorService EXECUTOR=executor();
    private LiveHistoryWork(){}
    static boolean handles(String action){return "conversation".equals(action)||"historyPage".equals(action)||"mediaConversation".equals(action);}
    static boolean canDeliver(long requestedEpoch,long currentEpoch,boolean foreground,boolean defaultSms,boolean readSms){
        return requestedEpoch==currentEpoch&&foreground&&defaultSms&&readSms;
    }
    // Two bounded readers let a newly opened chat proceed even while one older
    // provider request is slow. No send, generation, or profile mutation uses it.
    static ThreadPoolExecutor executor(){
        return new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),work->{
            Thread thread=new Thread(work,"reply-pilot-live-history");thread.setDaemon(true);return thread;
        });
    }
}
