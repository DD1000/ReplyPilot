package com.contentfoundry.replypilot;
import android.content.*;
import java.util.concurrent.RejectedExecutionException;
public class SendReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        PendingResult result=goAsync();Runnable send=()->{try{Sender.dispatch(c,i.getLongExtra("id",-1));}finally{result.finish();}};
        // A busy edit lane must not drop a due alarm or leak its PendingResult.
        try{SendWork.EXECUTOR.execute(send);}catch(RejectedExecutionException busy){SendWork.RECEIPTS.execute(send);}
    }
}
