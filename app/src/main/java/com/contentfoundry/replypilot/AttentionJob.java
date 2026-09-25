package com.contentfoundry.replypilot;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable queue for explicit Joke requests. Never sends an SMS. */
public final class AttentionJob extends JobService {
    private static final int ID=900002;
    private static final AtomicBoolean WORKING=new AtomicBoolean(),RECOVERY_QUEUED=new AtomicBoolean();
    private final AtomicBoolean active=new AtomicBoolean();
    private volatile String token;
    /** Repair the commit-before-schedule crash gap without replaying a claimed action. */
    public static void recoverAndSchedule(Context context){
        Context c=context.getApplicationContext();
        c.getSystemService(JobScheduler.class).cancel(ID);PlanDelayJob.recoverAndSchedule(c);
    }
    static void schedule(Context c,long wait){
        if(WORKING.get())return; // The active worker schedules the remaining durable queue.
        JobInfo job=new JobInfo.Builder(ID,new ComponentName(c,AttentionJob.class)).setPersisted(true).setMinimumLatency(Math.max(1,wait)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setBackoffCriteria(10000,JobInfo.BACKOFF_POLICY_LINEAR).build();
        if(c.getSystemService(JobScheduler.class).schedule(job)!=JobScheduler.RESULT_SUCCESS)throw new IllegalStateException("The background action could not start. Open the conversation.");
    }
    @Override public boolean onStartJob(JobParameters params){
        if(!WORKING.compareAndSet(false,true))return false;
        active.set(true);Context c=getApplicationContext();
        PilotApp.AI.execute(()->{
            try{
                AttentionActions.recover(c);
                // Keep each run bounded; leftover requests remain in SQLite.
                for(int i=0;i<3&&active.get();i++){
                    JSONObject action=AttentionActions.claim(c);if(action==null)break;
                    token=action.optString("token");
                    try{
                        if(!active.get()){AttentionActions.interrupt(c,token);break;}
                        CloudDrafts.joke(c,action.optLong("thread"),action.optLong("base"),token);
                        if(active.get())AttentionActions.complete(c,token,true,"The Joke action finished. Check the conversation.");
                    }catch(Exception unavailable){
                        boolean current=active.get()&&AttentionActions.check(c,action.optLong("thread"),action.optLong("base"),token);
                        AttentionActions.complete(c,token,false,"The revised draft was not saved. Open the conversation to try drafting again.");
                        if(current)AttentionActions.problem(c,token);
                    }
                    finally{token=null;}
                }
            }finally{
                WORKING.set(false);boolean finished=active.getAndSet(false);
                if(finished)jobFinished(params,false);
                try{long wait=AttentionActions.nextWait(c);if(wait>=0)schedule(c,Math.max(1000,wait));}catch(RuntimeException ignored){/* Durable pending work can be retried by the scheduler, without sending. */}
                MessageChanges.publish();
            }
        });return true;
    }
    @Override public boolean onStopJob(JobParameters params){
        active.set(false);String current=token;if(current!=null)AttentionActions.interrupt(getApplicationContext(),current);
        // Current work is consumed; rescheduling only discovers still-queued requests.
        return true;
    }
}
