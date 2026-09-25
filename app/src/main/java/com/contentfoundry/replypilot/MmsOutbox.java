package com.contentfoundry.replypilot;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;

/** Separate durable manual-MMS state; no change to SMS send or AI-consent tables. */
final class MmsOutbox extends SQLiteOpenHelper {
    private static MmsOutbox instance;
    static synchronized MmsOutbox get(Context c){if(instance==null)instance=new MmsOutbox(c.getApplicationContext());return instance;}
    private MmsOutbox(Context c){super(c,"mms-outgoing.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE attachments(id TEXT PRIMARY KEY,thread INTEGER NOT NULL,name TEXT NOT NULL,mime TEXT NOT NULL,bytes INTEGER NOT NULL,hash TEXT NOT NULL,created INTEGER NOT NULL,job TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX mms_attachments_thread ON attachments(thread,created)");
        db.execSQL("CREATE TABLE sends(id TEXT PRIMARY KEY,request_id TEXT NOT NULL UNIQUE,thread INTEGER NOT NULL,address TEXT NOT NULL,caption TEXT NOT NULL,sub INTEGER NOT NULL,base INTEGER NOT NULL,status TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',fingerprint TEXT NOT NULL,attachments TEXT NOT NULL,created INTEGER NOT NULL,provider_id INTEGER NOT NULL DEFAULT 0,provider_date INTEGER NOT NULL DEFAULT 0,transaction_id TEXT NOT NULL,mms_before TEXT NOT NULL,pdu_bytes INTEGER NOT NULL DEFAULT 0,settled INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX mms_sends_thread ON sends(thread,created)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Unsupported outgoing MMS database upgrade.");}
    JSONArray query(String sql,String... args){JSONArray rows=new JSONArray();try(Cursor cursor=getReadableDatabase().rawQuery(sql,args.length==0?null:args)){while(cursor.moveToNext())rows.put(Store.json(cursor));}return rows;}
    JSONObject job(String id){return query("SELECT * FROM sends WHERE id=?",id).optJSONObject(0);}
    JSONObject request(String id){return query("SELECT * FROM sends WHERE request_id=?",id).optJSONObject(0);}
    JSONObject attachment(String id){return query("SELECT * FROM attachments WHERE id=?",id).optJSONObject(0);}
    void status(String id,String status,String note){ContentValues values=new ContentValues();values.put("status",status);values.put("note",note);getWritableDatabase().update("sends",values,"id=?",new String[]{id});}
}
