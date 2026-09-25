package com.contentfoundry.replypilot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Explicit carrier callback, mutable only for the bounded SendConf evidence; the receiver is not exported. */
public final class MmsSentReceiver extends BroadcastReceiver {
    public static final String ACTION="com.contentfoundry.replypilot.MMS_SENT";
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!ACTION.equals(intent.getAction()))return;Uri uri=intent.getData();
        if(uri==null||!"replypilot".equals(uri.getScheme())||!"mms-sent".equals(uri.getHost())||uri.getPathSegments().size()!=1||uri.getQuery()!=null||uri.getFragment()!=null)return;
        String id=uri.getLastPathSegment();if(!MmsSendPolicy.id(id))return;int result=getResultCode();byte[] data=null;try{byte[] received=intent.getByteArrayExtra(android.telephony.SmsManager.EXTRA_MMS_DATA);if(received!=null&&received.length<=16384)data=received.clone();}catch(RuntimeException malformed){}final byte[] response=data;PendingResult pending=goAsync();Context app=context.getApplicationContext();
        try{ReceiveWork.MMS.execute(()->{try{MmsAttachments.receipt(app,id,result,response);}finally{pending.finish();}});}catch(RuntimeException unavailable){pending.finish();}
    }
}
