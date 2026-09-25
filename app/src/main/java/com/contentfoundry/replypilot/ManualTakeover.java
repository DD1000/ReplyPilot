package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import org.json.*;

/** Durable per-person manual ownership; no other contact's generation is invalidated. */
final class ManualTakeover {
    private record Source(long id,long date,String signature){}
    private record Incoming(Source sms,Source mms){}
    private ManualTakeover(){}
    static long revision(Context c,long thread){JSONObject row=row(c,thread);return row==null?0:row.optLong("revision");}
    static boolean unchanged(Context c,long thread,long expected){return ManualTakeoverPolicy.unchanged(expected,revision(c,thread));}
    static boolean blocked(Context c,long thread){
        JSONObject saved=row(c,thread);if(saved==null)return false;
        try{
            access(c);String address=Sender.singleRecipient(c,thread);Incoming current=incoming(c,thread,address);
            access(c);return !ManualTakeoverPolicy.released(saved.optLong("sms_id"),saved.optLong("mms_id"),current.sms().id(),current.mms().id(),saved.optLong("sms_receipt"),IncomingBurst.token(c,thread,current.sms().id(),address),IncomingBurst.current(c,thread,current.sms().id(),address));
        }catch(Exception unavailable){return true;}
    }
    static void require(Context c,long thread){if(blocked(c,thread))throw new IllegalStateException(ManualTakeoverPolicy.WAITING);}
    /** Call after confirming a single recipient, before remaining send preflight. */
    static long claim(Context c,long thread,String address){synchronized(PilotApp.SEND_LOCK){
        access(c);if(!SendPolicy.validAddress(address)||!PhoneNumberUtils.compare(address,Sender.singleRecipient(c,thread)))throw new IllegalStateException("The recipient could not be confirmed. Reopen this conversation.");
        Incoming source=incoming(c,thread,address);Store store=Store.get(c);SQLiteDatabase sql=store.getWritableDatabase();long revision=Math.addExact(revision(c,thread),1);
        JSONArray pending=store.query("SELECT _id FROM jobs WHERE thread=? AND (auto_send=1 OR attention_kind='delay') AND status IN ('scheduled','awaiting_alert')",new String[]{Long.toString(thread)});
        // Commit ownership separately from the send transaction. A failed SIM or
        // carrier operation must never silently hand this incoming text back to AI.
        sql.beginTransaction();
        try{
            ContentValues values=new ContentValues();values.put("thread",thread);values.put("revision",revision);values.put("sms_id",source.sms().id());values.put("sms_date",source.sms().date());values.put("sms_signature",source.sms().signature());values.put("sms_receipt",IncomingBurst.token(c,thread,source.sms().id(),address));values.put("mms_id",source.mms().id());values.put("mms_date",source.mms().date());values.put("mms_signature",source.mms().signature());values.put("mms_receipt",MmsDownloads.receiptSequence(c));values.put("claimed_at",System.currentTimeMillis());
            if(sql.insertWithOnConflict("manual_takeovers",null,values,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("Your manual reply could not take control. Try again.");
            ContentValues stopped=new ContentValues();stopped.put("status","paused");stopped.put("approved",0);stopped.put("note",ManualTakeoverPolicy.WAITING);
            sql.update("jobs",stopped,"thread=? AND (auto_send=1 OR attention_kind='delay') AND status IN ('scheduled','awaiting_alert')",new String[]{Long.toString(thread)});
            ContentValues stale=new ContentValues();stale.put("state","stale");stale.put("note",ManualTakeoverPolicy.WAITING);
            sql.update("attention_actions",stale,"thread=? AND state IN ('offered','queued','running') AND job_id=0",new String[]{Long.toString(thread)});
            sql.delete("drafts","thread=? AND engine LIKE 'OpenAI%'",new String[]{Long.toString(thread)});
            sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
        // Durable state is already stopped; cleanup cannot resurrect it on failure.
        try{Sender.cancelTakeoverTimers(c,pending);}catch(RuntimeException ignored){}
        try{DraftJob.cancelThread(c,thread);}catch(RuntimeException ignored){}
        MessageChanges.publish();return revision;
    }}
    /** A notice already received before the manual tap must not later release it. */
    static void bindMmsReceipt(Context c,long thread,long id,long token){synchronized(PilotApp.SEND_LOCK){
        if(thread<=0||id<=0)return;JSONObject saved=row(c,thread);if(saved==null||!ManualTakeoverPolicy.bindOlderMms(token,saved.optLong("mms_receipt"),saved.optLong("mms_id"),id))return;
        // Caller has just bound this exact notice to this thread. Keeping its ID is
        // sufficient even if permissions disappear before optional metadata reads.
        ContentValues values=new ContentValues();values.put("mms_id",id);values.put("mms_date",0);values.put("mms_signature","");
        try{JSONObject notice=MediaNavigation.exact(c,"mms",id,thread);if(notice!=null){values.put("mms_date",notice.optLong("date"));values.put("mms_signature",MediaContextPolicy.signature(Long.toString(thread),Long.toString(id),notice.optString("date")));}}catch(Exception unavailable){}
        Store.get(c).getWritableDatabase().update("manual_takeovers",values,"thread=? AND revision=?",new String[]{Long.toString(thread),saved.optString("revision")});
    }}
    private static JSONObject row(Context c,long thread){return Store.get(c).query("SELECT * FROM manual_takeovers WHERE thread=?",new String[]{Long.toString(thread)}).optJSONObject(0);}
    private static void access(Context c){if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Restore messaging access before taking over this conversation.");}
    private static Incoming incoming(Context c,long thread,String address){return new Incoming(source(c,thread,address,false),source(c,thread,address,true));}
    private static Source source(Context c,long thread,String address,boolean mms){
        String[] columns=mms?new String[]{"_id","thread_id","date"}:new String[]{"_id","thread_id","date","address","body"};
        String selection="thread_id=? AND "+(mms?"msg_box=1 AND m_type IN (130,132)":"type=1");
        try(Cursor rows=c.getContentResolver().query(mms?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI,columns,selection,new String[]{Long.toString(thread)},"_id DESC")){
            if(rows==null)throw new IllegalStateException("The incoming message could not be checked.");
            if(!rows.moveToFirst())return new Source(0,0,"");
            long id=rows.getLong(0),date=MediaHistoryPolicy.date(mms?"mms":"sms",rows.getLong(2));
            if(rows.getLong(1)!=thread||id<=0||!mms&&!PhoneNumberUtils.compare(address,rows.getString(3)))throw new IllegalStateException("The incoming recipient changed. Open the conversation again.");
            // An MMS notification becoming downloaded is the same incoming source.
            // Do not include its changing PDU type or newly materialized parts.
            String signature=MediaContextPolicy.signature(Long.toString(thread),Long.toString(id),Long.toString(date),address,mms?"":rows.isNull(4)?"":rows.getString(4));
            return new Source(id,date,signature);
        }
    }
}
