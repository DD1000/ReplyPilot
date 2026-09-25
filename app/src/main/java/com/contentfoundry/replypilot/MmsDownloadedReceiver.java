package com.contentfoundry.replypilot;
import android.content.*;

/** Private explicit callback; binding data is loaded from durable native state. */
public class MmsDownloadedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(MmsDownloads.token(intent)==null||(!MmsDownloads.DOWNLOAD.equals(intent.getAction())&&!MmsDownloads.ACK.equals(intent.getAction())))return;
        int result=getResultCode();PendingResult done=goAsync();ReceiveWork.MMS.execute(()->{
            try{MmsDownloads.completed(c,intent,result);}catch(Exception unavailable){/* Recovery retains unknown state; never retry here. */}
            finally{done.finish();}
        });
    }
}
