package com.contentfoundry.replypilot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsMessage;
import java.util.List;

/** Explicit PendingIntent target; ordinary applications cannot send this receiver broadcasts. */
public final class DeliveryReceiver extends BroadcastReceiver {
    static final String ACTION="com.contentfoundry.replypilot.DELIVERY_REPORT";

    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        Uri uri=intent.getData();
        if(uri==null||!"replypilot".equals(uri.getScheme())||!"delivery".equals(uri.getHost()))return;
        List<String> path=uri.getPathSegments();
        if(path.size()!=2)return;
        final long job;final int part;
        try{job=Long.parseLong(path.get(0));part=Integer.parseInt(path.get(1));}
        catch(NumberFormatException invalid){return;}
        if(job<=0||part<0)return;
        PendingResult pending=goAsync();
        // Identity comes from the fixed PendingIntent URI, not mutable fill-in extras.
        SendWork.RECEIPTS.execute(()->{try{Sender.deliveryReceipt(context,job,part,parse(intent));}catch(RuntimeException unavailable){/* Keep the previous, unconfirmed state if storage is unavailable. */}finally{pending.finish();}});
    }
    private static DeliveryPolicy.Report parse(Intent intent){
        try{
            byte[] pdu=intent.getByteArrayExtra("pdu");String format=intent.getStringExtra("format");
            if(pdu==null||pdu.length==0||pdu.length>4096||!"3gpp".equals(format)&&!"3gpp2".equals(format))return DeliveryPolicy.unknown();
            SmsMessage message=SmsMessage.createFromPdu(pdu,format);
            return message==null?DeliveryPolicy.unknown():DeliveryPolicy.report(format,message.isStatusReportMessage(),message.getStatus());
        }catch(RuntimeException malformed){return DeliveryPolicy.unknown();}
    }
}
