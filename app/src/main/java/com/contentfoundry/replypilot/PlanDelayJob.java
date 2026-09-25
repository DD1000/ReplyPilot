package com.contentfoundry.replypilot;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline-capable, single-attempt canned deferral; never generates or accepts plans. */
public final class PlanDelayJob extends JobService {
    private static final int ID=900003;
    private static final AtomicBoolean WORKING=new AtomicBoolean(),RECOVERY_QUEUED=new AtomicBoolean();
    private final AtomicBoolean active=new AtomicBoolean();
    private volatile String token;
    static void recoverAndSchedule(Context context){context.getSystemService(JobScheduler.class).cancel(ID);}
    static void schedule(Context c,long wait){
        if(WORKING.get())return;
        JobInfo job=new JobInfo.Builder(ID,new ComponentName(c,PlanDelayJob.class)).setPersisted(true).setMinimumLatency(Math.max(1000,wait)).setBackoffCriteria(10000,JobInfo.BACKOFF_POLICY_LINEAR).build();
        if(c.getSystemService(JobScheduler.class).schedule(job)!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("The planning deferral could not start. Open the conversation.");
    }
    @Override public boolean onStartJob(JobParameters params){
        if(!WORKING.compareAndSet(false,true))return false;
        active.set(true);Context c=getApplicationContext();
        PilotApp.IO.execute(()->{
            try{
                AttentionActions.recover(c);
                for(int i=0;i<3&&active.get();i++){
                    JSONObject action=AttentionActions.claimPlanDelay(c);if(action==null)break;token=action.optString("token");
                    try{
                        if(!active.get()){AttentionActions.interrupt(c,token);break;}
                        AttentionActions.sendPlanDelay(c,action);
                    }catch(Exception unavailable){/* The single attempt is consumed; the planning hold stays active. */}
                    finally{token=null;}
                }
            }finally{
                // Finish this run's state before permitting a replacement run.
                // Releasing WORKING first could clear the replacement's active flag.
                boolean finished=active.getAndSet(false);WORKING.set(false);if(finished)jobFinished(params,false);
                try{long wait=AttentionActions.nextPlanWait(c);if(wait>=0)schedule(c,wait);}catch(RuntimeException ignored){/* Resume can recover still-queued work. */}
                MessageChanges.publish();
            }
        });return true;
    }
    @Override public boolean onStopJob(JobParameters params){
        active.set(false);String current=token;if(current!=null)AttentionActions.interrupt(getApplicationContext(),current);
        return true;
    }
}
