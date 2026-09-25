package com.contentfoundry.replypilot;
import android.content.*;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.*;

public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(i.getAction())||!Messages.role(c))return;
        SmsMessage[] parts;try{parts=Telephony.Sms.Intents.getMessagesFromIntent(i);}catch(RuntimeException invalid){return;}if(parts==null||parts.length==0||parts[0]==null)return;
        String address=parts[0].getOriginatingAddress();if(address==null||address.isBlank())return;
        StringBuilder text=new StringBuilder();
        for(SmsMessage part:parts){
            if(part==null)return;
            String sender=part.getOriginatingAddress();
            if(sender==null||(!address.equals(sender)&&!PhoneNumberUtils.compare(address,sender)))return;
            if(part.getMessageBody()!=null)text.append(part.getMessageBody());
        }
        // Mark receipt before queued IO or SEND_LOCK can delay provider insertion.
        // Non-dialable senders still enter the inbox; they cannot receive auto SMS.
        IncomingBurst.Receipt receipt;
        try{receipt=IncomingBurst.mark(c,address);}catch(RuntimeException unavailable){IncomingBurst.failClosed();receipt=null;}
        final IncomingBurst.Receipt received=receipt;String body=text.toString();
        PendingResult done=goAsync();ReceiveWork.SMS.execute(()->{try{
            if(!Messages.role(c))return;
            long thread,id;
            synchronized(PilotApp.SEND_LOCK){
                thread=Messages.thread(c,address);Store.get(c).pauseThread(thread,"A new text arrived. Review the conversation and approve a new reply.");
                ContentValues v=new ContentValues();v.put("address",address);v.put("body",body);v.put("date",System.currentTimeMillis());v.put("date_sent",parts[0].getTimestampMillis());v.put("thread_id",thread);v.put("read",0);v.put("seen",0);v.put("sub_id",i.getIntExtra("subscription",SubscriptionManager.INVALID_SUBSCRIPTION_ID));
                Uri uri=c.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI,v);if(uri==null)return;id=ContentUris.parseId(uri);
                IncomingBurst.bind(c,received,thread,id);
                Store.get(c).extendReplyHold(thread,id);
                try{
                    boolean girlfriend="girlfriend".equals(Store.get(c).relationship(thread).optString("engagement"));
                    if(GirlfriendPause.onIncoming(c,thread,id,body,girlfriend))
                        AutomaticReplies.record(c,thread,id,"conversation_complete",false);
                }catch(org.json.JSONException unavailable){IncomingBurst.failClosed();}
            }
            // Publish the committed row before contact lookup or AI preflight.
            MessageChanges.publish();
            Notices.refreshScheduled(c);
            Notices.show(c,(int)thread,Messages.name(c,address),body,thread);
            AutomaticReplies.onIncoming(c,thread,id);
            if(c.getSharedPreferences("settings",0).getBoolean("autoDraft",true))DraftJob.schedule(c,thread,id);
        }finally{try{IncomingBurst.finish(c,received);}finally{done.finish();}}});
    }
}
