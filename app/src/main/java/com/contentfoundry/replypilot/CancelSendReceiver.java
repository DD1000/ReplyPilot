package com.contentfoundry.replypilot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Notification cancellation never creates, approves, or sends a message. */
public final class CancelSendReceiver extends BroadcastReceiver {
    public static final String ACTION_CANCEL="com.contentfoundry.replypilot.CANCEL_TIMER";
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null||!ACTION_CANCEL.equals(intent.getAction()))return;
        long id=intent.getLongExtra("id",-1);if(id<=0)return;
        Context c=context.getApplicationContext();PendingResult result=goAsync();
        PilotApp.IO.execute(()->{
            try{
                synchronized(PilotApp.SEND_LOCK){
                    if(Sender.cancel(c,id))Notices.cancelScheduled(c,id);
                    else Notices.timerNoLongerActive(c,id);
                }
            }finally{result.finish();}
        });
    }
}
