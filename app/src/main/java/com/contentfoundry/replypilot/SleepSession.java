package com.contentfoundry.replypilot;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Telephony;
import org.json.*;
import java.time.ZoneId;

/** Persisted global automation gate; manual drafts and manual SMS never consult it. */
final class SleepSession {
    static final String ACTION="com.contentfoundry.replypilot.SLEEP_CUTOFF";
    static final String PAUSED="Automatic replies are paused. Resume them in Sleep settings when you are ready.";
    static final String CHANGED="Sleep settings changed. This automatic timer was stopped; you can review the draft and send it yourself.";
    static final String PAST_CUTOFF="This reply would send at or after the Sleep cutoff. The draft is saved for your review.";
    record State(String mode,long until,long delay,long revision,String reason,long boundary,long deadlineElapsed,int boot,String delayMode,long delayMin,long delayMax){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("sleep",Context.MODE_PRIVATE);}
    private static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    private static State read(Context c){
        SharedPreferences p=prefs(c);
        return new State(p.getString("mode","off"),p.getLong("until",0),p.getLong("delay",300),p.getLong("revision",0),p.getString("reason",""),p.getLong("boundary",0),p.getLong("elapsed",0),p.getInt("boot",-1),p.getString("delayMode","fixed"),p.getLong("delayMin",DelayPolicy.DEFAULT_MIN),p.getLong("delayMax",DelayPolicy.DEFAULT_MAX));
    }
    private static void persist(Context c,State s){
        if(!prefs(c).edit().putString("mode",s.mode()).putLong("until",s.until()).putLong("delay",s.delay()).putLong("revision",s.revision()).putString("reason",s.reason())
            .putLong("boundary",s.boundary()).putLong("elapsed",s.deadlineElapsed()).putInt("boot",s.boot()).putString("delayMode",s.delayMode()).putLong("delayMin",s.delayMin()).putLong("delayMax",s.delayMax()).commit())throw new IllegalStateException("Sleep settings could not be saved. Try again.");
    }
    static State refresh(Context c){synchronized(PilotApp.SEND_LOCK){
        if(!prefs(c).getBoolean("autopilotSimplified",false)){
            long revision=Math.addExact(prefs(c).getLong("revision",0),1);
            if(!prefs(c).edit().clear().putString("mode","off").putLong("revision",revision).putBoolean("autopilotSimplified",true).commit())throw new IllegalStateException("Autopilot settings could not be updated.");
            cancelAlarm(c);
        }
        State s=read(c);
        // Read system settings before sampling fresh clocks at the send boundary.
        int currentBoot="active".equals(s.mode())?boot(c):s.boot();
        if("active".equals(s.mode())&&SleepPolicy.expired(s.until(),s.deadlineElapsed(),s.boot(),System.currentTimeMillis(),SystemClock.elapsedRealtime(),currentBoot)){
            String reason=s.boot()>=0&&currentBoot>=0&&s.boot()!=currentBoot?"The phone restarted. Automatic replies are paused until you resume.":"The Sleep cutoff was reached. Automatic replies are paused until you resume.";
            s=new State("paused",s.until(),s.delay(),Math.addExact(s.revision(),1),reason,s.boundary(),s.deadlineElapsed(),s.boot(),s.delayMode(),s.delayMin(),s.delayMax());
            persist(c,s);cancelPending(c,reason);cancelAlarm(c);
        }
        return s;
    }}
    static JSONObject publicState(Context c)throws JSONException{return json(refresh(c));}
    private static JSONObject json(State s)throws JSONException{return new JSONObject().put("mode",s.mode()).put("until",s.until()).put("delay",s.delay()).put("revision",s.revision()).put("reason",s.reason()).put("delayMode",s.delayMode()).put("delayMin",s.delayMin()).put("delayMax",s.delayMax());}
    static JSONObject change(Context c,String action,String clock,long seconds)throws JSONException{return change(c,action,clock,seconds,DelayPolicy.choice("fixed",seconds,DelayPolicy.DEFAULT_MIN,DelayPolicy.DEFAULT_MAX));}
    static JSONObject change(Context c,String action,String clock,long seconds,DelayPolicy.Choice timing)throws JSONException{synchronized(PilotApp.SEND_LOCK){
        State old=refresh(c);long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();State next;
        long revision=Math.addExact(old.revision(),1);
        switch(action){
            case "start" -> {if(!Sender.exact(c))throw new IllegalStateException("Enable precise timers in Settings before starting a Sleep schedule.");long delay=SleepPolicy.delay(seconds),until=SleepPolicy.nextCutoff(clock,now,ZoneId.systemDefault());next=new State("active",until,delay,revision,"",latestSms(c),elapsed+(until-now),boot(c),timing.mode(),timing.min(),timing.max());}
            case "pause" -> next=new State("paused",old.until(),old.delay(),revision,"Automatic replies were paused by you.",old.boundary(),old.deadlineElapsed(),old.boot(),old.delayMode(),old.delayMin(),old.delayMax());
            case "resume" -> next=new State("off",0,old.delay(),revision,"",latestSms(c),0,boot(c),old.delayMode(),old.delayMin(),old.delayMax());
            default -> throw new IllegalArgumentException("Choose start, pause or resume.");
        }
        // Persist the revision before cancellation. After a crash old jobs cannot
        // pass the revision gate, even if their Android alarms are still registered.
        persist(c,next);cancelPending(c,CHANGED);cancelAlarm(c);if("active".equals(next.mode()))arm(c,next);
        return json(next);
    }}
    private static long latestSms(Context c){
        if(!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow message access before starting or resuming automatic replies.");
        try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},null,null,"_id DESC")){
            if(cursor==null)throw new IllegalStateException("Messages could not be checked. Try starting or resuming again.");
            return cursor.moveToFirst()?cursor.getLong(0):0;
        }
    }
    static boolean generationAllowed(Context c,long base,long revision){
        State s=refresh(c);return SleepPolicy.allows(s.mode(),revision,s.revision(),base,s.boundary(),System.currentTimeMillis(),0,s.until());
    }
    static long revision(Context c){return refresh(c).revision();}
    static DelayPolicy.Choice delayChoice(Context c,JSONObject profile){State s=refresh(c);return "active".equals(s.mode())?DelayPolicy.choice(s.delayMode(),s.delay(),s.delayMin(),s.delayMax()):DelayOptions.profile(profile);}
    static String queueBlock(Context c,long base,long due,long revision){
        State s=refresh(c);
        if(revision!=s.revision())return CHANGED;
        if("paused".equals(s.mode()))return PAUSED;
        if(base<=s.boundary())return CHANGED;
        if(!SleepPolicy.allows(s.mode(),revision,s.revision(),base,s.boundary(),System.currentTimeMillis(),due,s.until()))return PAST_CUTOFF;
        return null;
    }
    static String jobBlock(Context c,JSONObject job){return queueBlock(c,job.optLong("base"),job.optLong("due"),job.optLong("sleep_revision"));}
    static void recovery(Context c,String action){synchronized(PilotApp.SEND_LOCK){
        State s=refresh(c);
        if("active".equals(s.mode())&&(Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action))){
            s=new State("paused",s.until(),s.delay(),Math.addExact(s.revision(),1),"The phone clock or timezone changed, or the phone restarted. Resume automatic replies when you are ready.",s.boundary(),s.deadlineElapsed(),s.boot(),s.delayMode(),s.delayMin(),s.delayMax());
            persist(c,s);cancelPending(c,s.reason());cancelAlarm(c);
        }else if("active".equals(s.mode()))arm(c,s);
    }}
    static void alarm(Context c,long revision){synchronized(PilotApp.SEND_LOCK){State s=refresh(c);if("active".equals(s.mode())&&s.revision()==revision)arm(c,s);}}
    private static void cancelPending(Context c,String reason){Sender.pauseAutomaticForSleep(c,reason);try{DraftJob.cancelPending(c);}catch(RuntimeException unavailable){/* Pending jobs also check the persisted boundary and revision. */}MessageChanges.publish();}
    private static PendingIntent pending(Context c,long revision){return PendingIntent.getBroadcast(c,0,new Intent(c,SleepReceiver.class).setAction(ACTION).setData(Uri.parse("replypilot://sleep/cutoff")).putExtra("revision",revision),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private static void cancelAlarm(Context c){try{c.getSystemService(AlarmManager.class).cancel(pending(c,0));}catch(RuntimeException unavailable){/* The persisted revision still invalidates an older alarm. */}}
    private static void arm(Context c,State s){
        AlarmManager alarms=c.getSystemService(AlarmManager.class);
        // This alarm updates status/cleans timers. Every send also enforces the
        // deadline directly, so delayed cleanup cannot cause an after-cutoff SMS.
        try{if(alarms.canScheduleExactAlarms())alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,s.until(),pending(c,s.revision()));else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,s.until(),pending(c,s.revision()));}
        catch(RuntimeException unavailable){/* Direct deadline checks remain authoritative. */}
    }
}
