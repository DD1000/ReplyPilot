package com.contentfoundry.replypilot;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ConcurrentHashMap;

/** Best-effort periodic refresh; Android may defer this job. Never sends messages. */
public final class LocationJob extends JobService {
    private final ConcurrentHashMap<JobParameters,Long> running=new ConcurrentHashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public boolean onStartJob(JobParameters parameters){
        running.put(parameters,0L);
        LocationSharing.EXECUTOR.execute(()->{
            if(!running.containsKey(parameters))return;
            try{
                long operation=LocationSharing.refresh(this,()->main.post(()->{if(running.remove(parameters)!=null)jobFinished(parameters,false);}));
                if(!running.replace(parameters,0L,operation))LocationSharing.cancel(operation);
            }catch(RuntimeException unavailable){main.post(()->{if(running.remove(parameters)!=null)jobFinished(parameters,false);});}
        });return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){Long operation=running.remove(parameters);if(operation!=null)LocationSharing.cancel(operation);return false;}
}
