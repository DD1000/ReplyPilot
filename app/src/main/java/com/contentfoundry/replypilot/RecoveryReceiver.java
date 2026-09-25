package com.contentfoundry.replypilot;
import android.content.*;
public class RecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        String action=i.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)&&!Intent.ACTION_TIME_CHANGED.equals(action)&&!Intent.ACTION_TIMEZONE_CHANGED.equals(action)&&!"android.provider.action.DEFAULT_SMS_PACKAGE_CHANGED".equals(action))return;
        AttentionJob.recoverAndSchedule(c);
        PilotApp.recoverMms(c);
        LocationSharing.EXECUTOR.execute(()->LocationSharing.reconcile(c.getApplicationContext()));
        synchronized(PilotApp.SEND_LOCK){SleepSession.recovery(c,action);if(!Intent.ACTION_TIMEZONE_CHANGED.equals(action))Store.get(c).pauseAll("The phone restarted, the clock changed, or SMS settings changed. Review and approve again.");Notices.refreshScheduled(c);}
    }
}
