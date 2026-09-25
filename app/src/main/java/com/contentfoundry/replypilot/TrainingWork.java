package com.contentfoundry.replypilot;

import java.util.concurrent.*;

/** Practice reads never queue behind inbox syncs or reply generation. */
final class TrainingWork {
    static final ExecutorService STATE=stateExecutor();
    static final ExecutorService GENERATION=generationExecutor();
    private TrainingWork(){}
    static boolean handles(String action){return "trainPilotState".equals(action)||"trainPilotStart".equals(action)||"trainPilotReply".equals(action)||"clearPilotTraining".equals(action);}
    static ExecutorService executor(String action){return "trainPilotStart".equals(action)||"trainPilotReply".equals(action)?GENERATION:STATE;}
    static ThreadPoolExecutor stateExecutor(){return executor("reply-pilot-practice-state",new ArrayBlockingQueue<>(4));}
    static ThreadPoolExecutor generationExecutor(){return executor("reply-pilot-practice-generation",new SynchronousQueue<>());}
    private static ThreadPoolExecutor executor(String name,BlockingQueue<Runnable> queue){return new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,queue,work->{Thread thread=new Thread(work,name);thread.setDaemon(true);return thread;});}
}
