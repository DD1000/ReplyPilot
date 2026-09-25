package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import java.util.*;
import org.json.*;

/** Current provider evidence only: no launch/archive cache can enable Autopilot. */
final class ReplyReadinessHistory {
    private static final String[] SMS={"_id","thread_id","date","type","body","address"};
    private static final String[] MMS={"_id","thread_id","date","msg_box","m_type"};
    private ReplyReadinessHistory(){}
    private static void access(Context c){if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow message access before checking conversation history.");}
    static ReplyEligibility.Result read(Context c,long thread,String samples){
        access(c);String address=Sender.singleRecipient(c,thread);
        Runnable validate=()->access(c);
        ReplyEligibility.Result result;
        try(Source sms=new Source(c,thread,address,"sms");Source mms=new Source(c,thread,address,"mms")){
            result=ReplyReadinessPolicy.collect(thread,ChatLog.parseCanonical(samples),sms,mms,validate);
        }
        access(c);if(!PhoneNumberUtils.compare(address,Sender.singleRecipient(c,thread)))throw new IllegalStateException("The contact changed. Recheck this conversation's history.");
        return result;
    }
    private static boolean same(String expected,String actual){return SendPolicy.validAddress(actual)&&PhoneNumberUtils.compare(expected,actual);}
    private static final class Source implements AutoCloseable,ReplyReadinessPolicy.Source {
        final Context context;final Cursor cursor;final String address,kind;final long thread;
        final ArrayDeque<JSONObject> smsRows=new ArrayDeque<>();Set<Long> automatic=Set.of();JSONObject row;boolean complete=true;
        Source(Context c,long thread,String address,String kind){
            this.context=c;this.thread=thread;this.address=address;this.kind=kind;
            var query=PilotTrainingHistoryPolicy.query(thread,kind,false);
            cursor=c.getContentResolver().query("sms".equals(kind)?Telephony.Sms.CONTENT_URI:Telephony.Mms.CONTENT_URI,"sms".equals(kind)?SMS:MMS,query.selection(),query.arguments().toArray(new String[0]),query.order());
            if(cursor==null)throw new IllegalStateException("Conversation history could not be checked. Try again.");
        }
        @Override public boolean advance(){
            if("mms".equals(kind)){if(!cursor.moveToNext())return false;row=Store.json(cursor);return true;}
            if(smsRows.isEmpty()){
                JSONArray batch=new JSONArray();int read=0;
                while(read++<50&&cursor.moveToNext()){
                    JSONObject next=Store.json(cursor);
                    if(next.optString("body").length()>ReplyReadinessPolicy.MAX_TEXT){
                        complete=false;try{next.put("body","");}catch(JSONException invalid){throw new IllegalStateException(invalid);}
                    }
                    smsRows.add(next);
                    // Only SMS identities are sent to the automatic-output filter.
                    if(next.optInt("type")==2&&next.optLong("thread_id")==thread&&!next.optString("body").isEmpty())batch.put(next);
                }
                automatic=Store.get(context).automaticMessageIds(batch);
            }
            row=smsRows.pollFirst();return row!=null;
        }
        @Override public ReplyReadinessPolicy.Entry read(){
            long actualThread=row.optLong("thread_id"),id=row.optLong("_id"),date;
            try{date=MediaHistoryPolicy.date(kind,row.optLong("date",-1));}catch(IllegalArgumentException malformed){return null;}
            int type=row.optInt("sms".equals(kind)?"type":"msg_box"),messageType=row.optInt("m_type");
            if(actualThread!=thread||id<=0)return null;
            String text;boolean matched,readable=true;
            if("sms".equals(kind)){matched=same(address,row.optString("address"));text=row.optString("body");}
            else{
                matched=mmsRecipient(context,id,type,address);if(!matched)return null;
                text=mmsText(context,id);readable=text!=null;if(!readable)complete=false;
            }
            return new ReplyReadinessPolicy.Entry(actualThread,kind,id,date,type,messageType,text,"sms".equals(kind)&&automatic.contains(id),matched,readable);
        }
        @Override public boolean complete(){return complete;}
        @Override public void close(){cursor.close();}
    }
    private static boolean mmsRecipient(Context c,long id,int type,String address){
        Uri uri=Telephony.Mms.CONTENT_URI.buildUpon().appendPath(Long.toString(id)).appendPath("addr").build();
        try(Cursor rows=c.getContentResolver().query(uri,new String[]{"address"},type==1?"type=137":"type IN (151,130,129)",null,null)){
            if(rows==null)throw new IllegalStateException("The conversation recipient could not be checked. Try again.");
            int count=0;while(rows.moveToNext()){if(++count>8||!same(address,rows.getString(0)))return false;}return count>0;
        }
    }
    private static String mmsText(Context c,long message){
        Uri uri=Telephony.Mms.Part.getPartUriForMessage(Long.toString(message));StringBuilder text=new StringBuilder();
        try(Cursor parts=c.getContentResolver().query(uri,new String[]{"_id","ct","text","_data","chset"},null,null,"seq ASC, _id ASC")){
            if(parts==null)throw new IllegalStateException("MMS text could not be checked. Try again.");
            int scanned=0;while(parts.moveToNext()){
                if(++scanned>ReplyReadinessPolicy.MAX_PARTS)return null;
                if(!"text/plain".equals(MmsTextReader.mime(parts.getString(1)))||parts.getLong(0)<=0)continue;
                MmsTextReader.Read loaded=MmsTextReader.read(c,parts.getLong(0),parts.isNull(2)?"":parts.getString(2),!parts.isNull(3),parts.isNull(4)?106:parts.getInt(4),ReplyReadinessPolicy.MAX_FILE_BYTES);
                if(loaded.unavailable()||loaded.truncated())return null;
                if(!loaded.text().isEmpty()){if(text.length()>0)text.append('\n');text.append(loaded.text());if(text.length()>ReplyReadinessPolicy.MAX_TEXT)return null;}
            }
        }
        return text.toString();
    }
}
