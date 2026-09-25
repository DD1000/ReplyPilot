package com.contentfoundry.replypilot;

import java.util.concurrent.*;

/** New inbox activity must not wait for history imports, media recovery, or AI. */
final class LiveInboxWork {
    static final ExecutorService EXECUTOR=executor();
    private LiveInboxWork(){}
    static boolean handles(String action){return "liveInbox".equals(action);}
    static boolean contactsCurrent(boolean before,boolean now,long beforeRevision,long nowRevision){return before==now&&beforeRevision==nowRevision;}
    static ThreadPoolExecutor executor(){
        return new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),work->{Thread thread=new Thread(work,"reply-pilot-live-inbox");thread.setDaemon(true);return thread;});
    }
}
