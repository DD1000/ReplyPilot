package com.contentfoundry.replypilot;

import android.content.*;
import android.provider.Telephony;

public class MmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null||!Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(intent.getAction())||!Messages.role(c))return;
        MmsDownloads.receiptStarted();long receipt=MmsDownloads.markReceipt(c);PendingResult done=goAsync();try{ReceiveWork.MMS.execute(()->{try{MmsDownloads.receive(c,intent,receipt);}
            catch(Exception invalid){Notices.show(c,80000,"Media needs attention","The carrier notice could not be opened. Check SMS access and available storage.",0);}
            finally{MmsDownloads.receiptFinished();done.finish();}});}catch(RuntimeException unavailable){MmsDownloads.receiptFinished();done.finish();}
    }
}
