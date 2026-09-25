package com.contentfoundry.replypilot;
import android.app.job.*;
import android.content.*;
import android.os.PersistableBundle;
import org.json.JSONObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class DraftJob extends JobService {
    private final ConcurrentHashMap<JobParameters,AtomicBoolean> running=new ConcurrentHashMap<>();
    public static void schedule(Context c,long thread,long base){
        try {
            if(ManualTakeover.blocked(c,thread))return;
            String address=Messages.address(c,thread,base);
            if(!IncomingBurst.current(c,thread,base,address))return;
            long burst=IncomingBurst.token(c,thread,base,address);
            if(!SleepSession.generationAllowed(c,base,SleepSession.revision(c)))return;
            JSONObject profile=Store.get(c).relationship(thread);
            if(Store.get(c).replyDecision(thread,base)!=null)return;
            if(!PersonProfile.automaticAllowed(profile.optBoolean("cloudEnabled"),profile.optBoolean("autoDraft"),c.getSharedPreferences("settings",0).getBoolean("autoDraft",true)))return;
            if(Messages.latest(c,thread)!=base)return;
            int id=IncomingBurst.jobId(c,thread);long wait=IncomingBurst.remaining(c,thread,base,address);
            PersistableBundle extras=new PersistableBundle();extras.putLong("thread",thread);extras.putLong("base",base);extras.putLong("burst",burst);
            JobInfo job=new JobInfo.Builder(id,new ComponentName(c,DraftJob.class)).setExtras(extras).setMinimumLatency(wait).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build();
            JobScheduler scheduler=c.getSystemService(JobScheduler.class);
            IncomingBurst.scheduleIfCurrent(c,thread,base,address,burst,()->{
                // Remove a legacy modulo-based job only for this exact thread.
                for(JobInfo old:scheduler.getAllPendingJobs())if(old.getId()!=id&&new ComponentName(c,DraftJob.class).equals(old.getService())&&old.getExtras().getLong("thread")==thread)scheduler.cancel(old.getId());
                scheduler.schedule(job);
            });
        }catch(Exception ignored){/* A newer text or changed setting will schedule its own current job. */}
    }
    private static void scheduleDeferred(Context c,long thread,long base,long wait){
        try{synchronized(PilotApp.SEND_LOCK){String address=Messages.address(c,thread,base);if(!IncomingBurst.current(c,thread,base,address)||MediaContext.newerIncoming(c,thread,base)||ManualTakeover.blocked(c,thread))return;
            long burst=IncomingBurst.token(c,thread,base,address);PersistableBundle extras=new PersistableBundle();extras.putLong("thread",thread);extras.putLong("base",base);extras.putLong("burst",burst);
            JobInfo job=new JobInfo.Builder(IncomingBurst.jobId(c,thread),new ComponentName(c,DraftJob.class)).setExtras(extras).setMinimumLatency(wait).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build();
            IncomingBurst.scheduleIfCurrent(c,thread,base,address,burst,()->c.getSystemService(JobScheduler.class).schedule(job));
        }}catch(Exception unavailable){/* Keep the current-message binding; never discover old messages. */}
    }
    static void scheduleMms(Context c,long thread,long source,long receipt,long wait){
        if(thread<=0||source<=0||receipt<=0)return;
        try{synchronized(PilotApp.SEND_LOCK){
            if(ManualTakeover.blocked(c,thread)||!AutopilotMms.enabled(c,Store.get(c).relationship(thread)))return;
            AutopilotMms.capture(c,thread,source,receipt,0);
            PersistableBundle extras=new PersistableBundle();extras.putLong("thread",thread);extras.putLong("sourceMms",source);extras.putLong("receipt",receipt);
            JobInfo job=new JobInfo.Builder(IncomingBurst.jobId(c,thread),new ComponentName(c,DraftJob.class)).setExtras(extras).setMinimumLatency(Math.max(1000,wait)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build();
            c.getSystemService(JobScheduler.class).schedule(job);
        }}catch(Exception changed){/* A newer SMS/MMS keeps its own queued job. */}
    }
    static void cancelPending(Context c){
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        for(JobInfo job:scheduler.getAllPendingJobs())if(new ComponentName(c,DraftJob.class).equals(job.getService()))scheduler.cancel(job.getId());
    }
    static void cancelThread(Context c,long thread){
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        for(JobInfo job:scheduler.getAllPendingJobs())if(new ComponentName(c,DraftJob.class).equals(job.getService())&&job.getExtras().getLong("thread")==thread)scheduler.cancel(job.getId());
    }
    @Override public boolean onStartJob(JobParameters p){
        AtomicBoolean active=new AtomicBoolean(true);running.put(p,active);
        PilotApp.AI.execute(()->{
            long thread=p.getExtras().getLong("thread"),base=p.getExtras().getLong("base"),burst=p.getExtras().getLong("burst",-1);
            long sleepRevision=-1;boolean reschedule=false;String address="";
            try{
                if(ManualTakeover.blocked(this,thread))return;
                long sourceMms=p.getExtras().getLong("sourceMms"),receipt=p.getExtras().getLong("receipt");
                if(sourceMms>0){
                    if(!active.get())return;
                    if(MmsDownloads.isPending()||IncomingBurst.hasUnbound(this)){reschedule=true;return;}
                    if(!HistoryLearning.ready(this)){reschedule=true;return;}
                    AutopilotMms.generate(this,thread,sourceMms,receipt);return;
                }
                address=Messages.address(this,thread,base);
                if(!active.get()||!IncomingBurst.current(this,thread,base,address)||IncomingBurst.token(this,thread,base,address)!=burst)return;
                if(IncomingBurst.remaining(this,thread,base,address)>0){reschedule=true;return;}
                sleepRevision=SleepSession.revision(this);
                if(!SleepSession.generationAllowed(this,base,sleepRevision))return;
                JSONObject profile=Store.get(this).relationship(thread);
                if(!PersonProfile.automaticAllowed(profile.optBoolean("cloudEnabled"),profile.optBoolean("autoDraft"),getSharedPreferences("settings",0).getBoolean("autoDraft",true))||!Messages.role(this)||Messages.latest(this,thread)!=base)return;
                if(!active.get()||!IncomingBurst.unchanged(this,thread,base,address,burst))return;
                if(MmsDownloads.isPending()){reschedule=true;return;}
                if(!HistoryLearning.ready(this)){reschedule=true;return;}
                CloudDrafts.generate(this,thread,base,getSharedPreferences("settings",0).getString("tone","Natural"),true);
            }catch(AutomaticReplies.WaitingForMms waiting){
                // Retry only this already-received source, never scan old history.
                reschedule=true;
            }catch(Exception e){
                if(active.get()&&!ManualTakeover.blocked(this,thread)&&IncomingBurst.unchanged(this,thread,base,address,burst)&&SleepSession.generationAllowed(this,base,sleepRevision)&&Store.get(this).replyDecision(thread,base)==null)
                    Notices.show(this,(int)thread,"Chat needs attention","Open the conversation to check your AI connection and draft a reply.",thread);
            }finally{
                running.remove(p,active);jobFinished(p,false);
                if(reschedule&&active.get()){
                    long sourceMms=p.getExtras().getLong("sourceMms");
                    if(sourceMms>0)scheduleMms(this,thread,sourceMms,p.getExtras().getLong("receipt"),15000);
                    else scheduleDeferred(this,thread,base,15000);
                }
            }
        });return true;
    }
    @Override public boolean onStopJob(JobParameters p){AtomicBoolean active=running.remove(p);if(active!=null)active.set(false);return false;}
}
