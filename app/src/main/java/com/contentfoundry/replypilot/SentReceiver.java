package com.contentfoundry.replypilot;
import android.content.*;
public class SentReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){int code=getResultCode();PendingResult result=goAsync();SendWork.RECEIPTS.execute(()->{try{Sender.receipt(c,i.getLongExtra("id",-1),i.getIntExtra("part",-1),code);}finally{result.finish();}});}
}
