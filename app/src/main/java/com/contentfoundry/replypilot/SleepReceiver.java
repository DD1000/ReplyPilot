package com.contentfoundry.replypilot;

import android.content.*;

public class SleepReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(!SleepSession.ACTION.equals(intent.getAction()))return;
        PendingResult pending=goAsync();
        PilotApp.IO.execute(()->{try{SleepSession.alarm(context,intent.getLongExtra("revision",-1));}finally{pending.finish();}});
    }
}
