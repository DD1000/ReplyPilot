package com.contentfoundry.replypilot;

import android.content.Context;
import android.content.ContentValues;
import android.Manifest;
import android.database.Cursor;
import android.provider.Telephony;
import java.util.*;
import org.json.*;

/** Reuses existing manual-send evidence. No separate copy of the owner's chat archive. */
final class ApprovedLearning {
    private static final String PREFS="approved_learning";
    private ApprovedLearning(){}
    static void scheduleCapture(Context context,long jobId,long thread,long base){
        Context c=context.getApplicationContext();
        try{
            // This one indexed row is the only learning read on the approval path.
            // The longer context query waits until after the user can submit.
            ApprovedLearningPolicy.Anchor anchor=anchor(c,thread,base);if(anchor==null)return;
            PilotApp.IO.execute(()->capture(c,jobId,anchor));
        }catch(RuntimeException unavailable){/* Optional learning never delays or fails a send beyond this single lookup. */}
    }
    private static ApprovedLearningPolicy.Anchor anchor(Context c,long thread,long base){
        if(thread<=0||base<=0||!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))return null;
        try(Cursor row=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"date","type","address","body"},"thread_id=? AND _id=?",new String[]{Long.toString(thread),Long.toString(base)},null)){
            if(row==null||!row.moveToFirst()||row.getLong(0)<=0||row.getInt(1)!=1||row.isNull(2)||row.isNull(3))return null;
            String body=row.getString(3);if(body.length()>16_000)return null;
            return new ApprovedLearningPolicy.Anchor(thread,base,row.getLong(0),1,row.getString(2),body);
        }
    }
    private static void capture(Context c,long jobId,ApprovedLearningPolicy.Anchor expected){
        // Learning is optional metadata and must never turn approval into a send error.
        try{
            if(!ApprovedLearningPolicy.sameAnchor(expected,anchor(c,expected.thread(),expected.base())))return;
            String incoming=contextAtBase(c,expected);
            // Reject deleted, edited, or reused IDs, including changes during the read.
            if(!ApprovedLearningPolicy.sameAnchor(expected,anchor(c,expected.thread(),expected.base())))return;
            ContentValues values=new ContentValues();values.put("original",incoming);
            Store.get(c).getWritableDatabase().update("jobs",values,"_id=? AND thread=? AND base=? AND auto_send=0 AND attention_kind=''",new String[]{Long.toString(jobId),Long.toString(expected.thread()),Long.toString(expected.base())});
        }catch(RuntimeException ignored){}
    }
    /** Anchor to the approved incoming row, even after our outgoing SMS was inserted. */
    private static String contextAtBase(Context c,ApprovedLearningPolicy.Anchor expected){
        List<String> texts=new ArrayList<>();int used=0;
        // Exclude newer inbound and outgoing rows: delayed learning can never
        // associate this reply with a later message that the user had not seen.
        try(Cursor rows=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"type","body"},"thread_id=? AND type IN (1,2) AND (date<? OR (date=? AND _id<=?))",new String[]{Long.toString(expected.thread()),Long.toString(expected.date()),Long.toString(expected.date()),Long.toString(expected.base())},"date DESC, _id DESC")){
            while(rows!=null&&rows.moveToNext()&&texts.size()<8&&used<600){
                if(rows.getInt(0)==2)break;
                String body=ApprovedLearningPolicy.bounded(rows.isNull(1)?"":rows.getString(1),600-used);texts.add(body);used+=body.length()+1;
            }
        }
        Collections.reverse(texts);return ApprovedLearningPolicy.bounded(String.join("\n",texts),600);
    }
    static JSONArray examples(Context c,long thread)throws JSONException{
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        long after=c.getSharedPreferences(PREFS,0).getLong("after_"+thread,0);
        JSONArray rows=Store.get(c).query("SELECT _id,thread,approved,auto_send,status,delivery_status,attention_kind,original,body FROM jobs WHERE thread=? AND _id>? AND approved=1 AND auto_send=0 AND attention_kind='' AND (status='sent' OR delivery_status='delivered') ORDER BY _id DESC LIMIT 96",new String[]{Long.toString(thread),Long.toString(after)});
        List<ApprovedLearningPolicy.Choice> choices=new ArrayList<>();
        for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);choices.add(new ApprovedLearningPolicy.Choice(r.optLong("_id"),r.optLong("thread"),r.optInt("approved")==1,r.optInt("auto_send")==1,r.optString("status"),r.optString("delivery_status"),r.optString("attention_kind"),r.optString("original"),r.optString("body")));}
        JSONArray result=new JSONArray();for(var e:ApprovedLearningPolicy.examples(choices,thread,after))result.put(new JSONObject().put("incoming",e.incoming()).put("reply",e.reply()));return result;
    }
    static JSONObject state(Context c,long thread)throws JSONException{return new JSONObject().put("count",examples(c,thread).length()).put("limit",ApprovedLearningPolicy.LIMIT);}
    // Acknowledge forgetting only after the cutoff is durable across restart.
    @android.annotation.SuppressLint("ApplySharedPref")
    static JSONObject clear(Context c,long thread)throws JSONException{
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        synchronized(PilotApp.SEND_LOCK){
            JSONObject last=Store.get(c).query("SELECT MAX(_id) AS last FROM jobs WHERE thread=?",new String[]{Long.toString(thread)}).optJSONObject(0);
            var preferences=c.getSharedPreferences(PREFS,0);String key="after_"+thread;long previous=preferences.getLong(key,0);
            if(!preferences.edit().putLong(key,last==null?0:last.optLong("last")).commit()){
                preferences.edit().putLong(key,previous).commit();
                throw new IllegalStateException("Could not forget these examples. Try again.");
            }
            Store.get(c).invalidateGeneration();Sender.cancelAutomaticForThread(c,thread,"Approved examples cleared. Generate a fresh reply.");
            return state(c,thread);
        }
    }
}
