package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** One initial, resumable text-history pass. No reply, timer, or carrier action lives here. */
final class HistoryLearning {
    private static final Object LOCK=new Object();
    private static final ScheduledExecutorService WORK=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"reply-pilot-history-learning");t.setDaemon(true);return t;});
    private static volatile boolean queued,running;
    private static boolean requested;
    private static Database database;
    private static final String WAIT="Pilot is learning your saved conversations. You can still write and send messages.";
    private static final class Database extends SQLiteOpenHelper {
        Database(Context c){super(c,new File(c.getNoBackupFilesDir(),"history-learning-v1.db").getAbsolutePath(),null,1);setWriteAheadLoggingEnabled(true);}
        @Override public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE learning_meta(id INTEGER PRIMARY KEY CHECK(id=1),config TEXT NOT NULL,run TEXT NOT NULL,phase TEXT NOT NULL,error TEXT NOT NULL DEFAULT '',restart INTEGER NOT NULL DEFAULT 0,snapshot INTEGER NOT NULL DEFAULT 0,total_messages INTEGER NOT NULL DEFAULT 0,processed_messages INTEGER NOT NULL DEFAULT 0,total_contacts INTEGER NOT NULL DEFAULT 0,completed_contacts INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("INSERT INTO learning_meta(id,config,run,phase) VALUES(1,'','','waiting')");
            db.execSQL("CREATE TABLE learning_contacts(thread INTEGER PRIMARY KEY,scope TEXT NOT NULL,memory BLOB NOT NULL,done INTEGER NOT NULL DEFAULT 0)");
            db.execSQL("CREATE TABLE learning_queue(seq INTEGER PRIMARY KEY AUTOINCREMENT,thread INTEGER NOT NULL,kind TEXT NOT NULL,source_id INTEGER NOT NULL,source_signature TEXT NOT NULL,last_fragment INTEGER NOT NULL,payload BLOB NOT NULL)");
            db.execSQL("CREATE INDEX learning_queue_contact ON learning_queue(thread,seq)");
        }
        @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Update Reply Pilot to read its learned history.");}
    }
    private static synchronized Database helper(Context c){if(database==null)database=new Database(c.getApplicationContext());return database;}
    private static JSONObject one(Context c,String table,String where,String...args){
        try(Cursor rows=helper(c).getReadableDatabase().query(table,null,where,args,null,null,null,"1")){
            if(!rows.moveToFirst())return null;JSONObject value=new JSONObject();
            for(int i=0;i<rows.getColumnCount();i++){int type=rows.getType(i);if(type==Cursor.FIELD_TYPE_BLOB)continue;value.put(rows.getColumnName(i),type==Cursor.FIELD_TYPE_INTEGER?rows.getLong(i):rows.isNull(i)?"":rows.getString(i));}return value;
        }catch(JSONException invalid){throw new IllegalStateException("History checkpoint is unavailable.");}
    }
    private static JSONObject meta(Context c){return one(c,"learning_meta","id=1");}
    private static boolean access(Context c){return Messages.role(c)&&Messages.allowed(c,Manifest.permission.READ_SMS);}
    private static JSONObject configuration(Context c)throws Exception{if(!access(c))throw new IllegalStateException("Allow SMS access and choose Reply Pilot as your default texting app.");JSONObject config=CloudConfig.read(c);if(config==null)throw new IllegalStateException("Connect your AI service to start learning your message history.");return config;}
    private static boolean sameConfig(Context c,String expected){try{return expected.equals(configuration(c).optString("revision"));}catch(Exception unavailable){return false;}}
    static void refresh(Context c){if(!PilotApp.foreground)return;enqueue(c.getApplicationContext(),0);}
    private static void enqueue(Context c,long delay){synchronized(LOCK){if(running){requested=true;return;}if(queued)return;queued=true;WORK.schedule(()->work(c),delay,TimeUnit.MILLISECONDS);}}
    static JSONObject status(Context c)throws JSONException{
        JSONObject out=new JSONObject().put("phase","waiting").put("ready",false).put("processedMessages",0).put("totalMessages",0).put("completedContacts",0).put("totalContacts",0).put("error","");
        try{
            JSONObject config=configuration(c),m=meta(c);if(!config.optString("revision").equals(m.optString("config")))return out.put("phase",queued||running?"preparing":"waiting");
            String phase=m.optString("phase","waiting");if("waiting".equals(phase)&&(queued||running))phase="preparing";return out.put("phase",phase).put("ready","ready".equals(phase)).put("processedMessages",m.optLong("processed_messages")).put("totalMessages",m.optLong("total_messages")).put("completedContacts",m.optLong("completed_contacts")).put("totalContacts",m.optLong("total_contacts")).put("error",m.optString("error"));
        }catch(Exception unavailable){return out;}
    }
    static boolean ready(Context c){try{return status(c).optBoolean("ready");}catch(Exception unavailable){return false;}}
    static void requireReady(Context c){if(!ready(c))throw new IllegalStateException(WAIT);}
    static String token(Context c){requireReady(c);return meta(c).optString("run");}
    static boolean unchanged(Context c,String expected){return expected!=null&&!expected.isEmpty()&&ready(c)&&expected.equals(meta(c).optString("run"));}
    static JSONObject context(Context c,long thread)throws Exception{
        requireReady(c);JSONObject m=meta(c),row=one(c,"learning_contacts","thread=?",Long.toString(thread));if(row==null)return emptyMemory();
        if(!row.optString("scope").equals(HistoryArchive.learningScope(c,thread))){fail(c,"A conversation’s recipients changed. Retry history learning in Settings.",true);throw new IllegalStateException("Open Settings and retry history learning for the updated conversation.");}
        byte[] payload;try(Cursor rows=helper(c).getReadableDatabase().query("learning_contacts",new String[]{"memory"},"thread=?",new String[]{Long.toString(thread)},null,null,null)){if(!rows.moveToFirst())return emptyMemory();payload=rows.getBlob(0);}
        JSONObject memory;try{memory=savedMemory(m.optString("run")+":memory:"+thread,payload);}catch(CheckpointChanged invalid){fail(c,invalid.getMessage(),true);throw invalid;}
        if(!unchanged(c,m.optString("run"))||!row.optString("scope").equals(HistoryArchive.learningScope(c,thread)))throw new IllegalStateException("History learning changed. Try the draft again.");return memory;
    }
    static JSONObject retry(Context c)throws Exception{
        if(!PilotApp.foreground)throw new IllegalStateException("Open Reply Pilot to retry learning.");configuration(c);
        synchronized(LOCK){if(!running){ContentValues v=new ContentValues();v.put("error","");v.put("phase","preparing");helper(c).getWritableDatabase().update("learning_meta",v,"id=1",null);}}
        HistoryArchive.retry(c);
        refresh(c);return status(c);
    }
    private static final class CheckpointChanged extends IllegalStateException{CheckpointChanged(){super("The saved learning checkpoint could not be read. Tap Retry to rebuild it from your available messages.");}}
    private static JSONObject savedJson(String identity,byte[] payload){try{return new JSONObject(new String(HistoryArchive.openLearning(identity,payload),StandardCharsets.UTF_8));}catch(Exception invalid){throw new CheckpointChanged();}}
    private static JSONObject savedMemory(String identity,byte[] payload){try{return memory(savedJson(identity,payload));}catch(Exception invalid){throw new CheckpointChanged();}}
    private static JSONObject emptyMemory()throws JSONException{return new JSONObject().put("writingStyle","").put("historicalContext","");}
    private static JSONObject memory(JSONObject value)throws JSONException{
        if(value==null||value.length()!=2||!(value.opt("writingStyle") instanceof String)||!(value.opt("historicalContext") instanceof String))throw new IllegalArgumentException("Invalid history summary.");
        return new JSONObject().put("writingStyle",HistoryLearningPolicy.memory(value.getString("writingStyle"))).put("historicalContext",HistoryLearningPolicy.memory(value.getString("historicalContext")));
    }
    private static void phase(Context c,String phase){ContentValues v=new ContentValues();v.put("phase",phase);v.put("error","");helper(c).getWritableDatabase().update("learning_meta",v,"id=1",null);MessageChanges.publish();}
    private static void fail(Context c,String error,boolean restart){ContentValues v=new ContentValues();v.put("phase","error");v.put("error",error);if(restart)v.put("restart",1);helper(c).getWritableDatabase().update("learning_meta",v,"id=1",null);MessageChanges.publish();}
    private static void work(Context c){
        synchronized(LOCK){queued=false;running=true;}boolean more=false;
        try{
            JSONObject config=configuration(c),m=meta(c);String revision=config.getString("revision");
            if(revision.equals(m.optString("config"))&&Set.of("ready","error").contains(m.optString("phase")))return;
            if(!revision.equals(m.optString("config"))){
                // Publish only content-free new-connection progress before the long
                // snapshot transaction. No old connection's counts/memory are exposed.
                ContentValues reset=new ContentValues();reset.put("config",revision);reset.put("run","");reset.put("phase","preparing");reset.put("error","");reset.put("restart",1);reset.put("snapshot",0);reset.put("total_messages",0);reset.put("processed_messages",0);reset.put("total_contacts",0);reset.put("completed_contacts",0);helper(c).getWritableDatabase().update("learning_meta",reset,"id=1",null);m=meta(c);MessageChanges.publish();
            }
            if(m.optString("run").isEmpty()||m.optInt("restart")==1){phase(c,"preparing");prepare(c,config);m=meta(c);}
            if(!sameConfig(c,revision))return;
            if("ready".equals(m.optString("phase")))return;
            phase(c,"analyzing");
            JSONObject health=CloudClient.request(config,"/health",null);if(health.optInt("historyAnalysisVersion")<1)throw new IllegalStateException("Update your private AI service to support full-history learning, then tap Retry.");
            more=batch(c,config,meta(c));
        }catch(HistoryArchive.LearningPending waiting){
            if(!access(c))phase(c,"waiting");
            else try{if(HistoryArchive.learningFailed(c))fail(c,"Your phone history could not be fully saved. Tap Retry to save it and resume learning.",false);else{phase(c,"preparing");HistoryArchive.refresh(c);more=true;}}catch(Exception unavailable){fail(c,"Your phone history is unavailable. Tap Retry to save it and resume learning.",false);}
        }
        catch(Exception error){
            if(!access(c)){phase(c,"waiting");}
            else{String message=error instanceof IllegalStateException?error.getMessage():null;fail(c,message!=null&&message.length()<=280?message:"History learning could not finish. Check your connection, then tap Retry. Completed batches are saved.",error instanceof CheckpointChanged);}
        }finally{synchronized(LOCK){running=false;more|=requested;requested=false;}if(more)enqueue(c,3300);}
    }
    private static void prepare(Context c,JSONObject config)throws Exception{
        SQLiteDatabase db=helper(c).getWritableDatabase();String run=UUID.randomUUID().toString(),revision=config.getString("revision");long[] totals={0,0};Set<Long> contacts=new HashSet<>();
        db.beginTransactionNonExclusive();try{
            db.delete("learning_queue",null,null);db.delete("learning_contacts",null,null);
            long snapshot=HistoryArchive.exportLearning(c,(thread,scope,row)->{
                if(!HistoryLearningPolicy.eligible(row.optString("kind"),row.optInt("type"),row.optInt("msg_box"),row.optInt("m_type")))return;
                HistoryLearningPolicy.requireCompleteText(row.optString("kind"),row.optBoolean("textUnavailable"),row.optBoolean("truncated"));
                List<String> fragments=HistoryLearningPolicy.fragments(row.optString("body"));if(fragments.isEmpty())return;
                if(contacts.add(thread)){ContentValues contact=new ContentValues();contact.put("thread",thread);contact.put("scope",scope);contact.put("memory",HistoryArchive.sealLearning(run+":memory:"+thread,emptyMemory().toString().getBytes(StandardCharsets.UTF_8)));db.insertOrThrow("learning_contacts",null,contact);}
                totals[0]++;int at=0;
                for(String fragment:fragments){
                    // Exact fragments preserve long messages, including their whitespace.
                    JSONObject turn=new JSONObject().put("speaker",row.optInt("type")==2?"me":"them").put("text",fragment).put("continuation",at>0);
                    ContentValues unit=new ContentValues();unit.put("thread",thread);unit.put("kind",row.optString("kind"));unit.put("source_id",row.optLong("_id"));unit.put("source_signature",HistoryArchive.learningSignature(row));unit.put("last_fragment",++at==fragments.size()?1:0);unit.put("payload",new byte[0]);long seq=db.insertOrThrow("learning_queue",null,unit);
                    ContentValues encoded=new ContentValues();encoded.put("payload",HistoryArchive.sealLearning(run+":unit:"+seq,turn.toString().getBytes(StandardCharsets.UTF_8)));db.update("learning_queue",encoded,"seq=?",new String[]{Long.toString(seq)});totals[1]++;
                }
            });
            if(!sameConfig(c,revision))throw new IllegalStateException("AI connection changed. Open Settings and retry history learning.");
            ContentValues meta=new ContentValues();meta.put("config",revision);meta.put("run",run);meta.put("phase",totals[0]==0?"ready":"analyzing");meta.put("error","");meta.put("restart",0);meta.put("snapshot",snapshot);meta.put("total_messages",totals[0]);meta.put("processed_messages",0);meta.put("total_contacts",contacts.size());meta.put("completed_contacts",0);db.update("learning_meta",meta,"id=1",null);db.setTransactionSuccessful();
        }finally{db.endTransaction();}MessageChanges.publish();
    }
    private static boolean batch(Context c,JSONObject config,JSONObject meta)throws Exception{
        SQLiteDatabase db=helper(c).getWritableDatabase();String run=meta.getString("run"),revision=config.getString("revision");long thread;
        try(Cursor rows=db.rawQuery("SELECT thread FROM learning_queue ORDER BY seq LIMIT 1",null)){if(!rows.moveToFirst()){finish(c,db,run);return false;}thread=rows.getLong(0);}
        JSONObject contact=one(c,"learning_contacts","thread=?",Long.toString(thread));String scope=contact.getString("scope");JSONArray turns=new JSONArray(),sources=new JSONArray();long first=0,last=0,messages=0;int characters=0,encodedBytes=0;byte[] previous;
        try(Cursor rows=db.query("learning_contacts",new String[]{"memory"},"thread=?",new String[]{Long.toString(thread)},null,null,null)){if(!rows.moveToFirst())throw new IllegalStateException("History checkpoint changed. Retry learning.");previous=rows.getBlob(0);}
        try(Cursor rows=db.query("learning_queue",null,"thread=?",new String[]{Long.toString(thread)},null,null,"seq ASC",Integer.toString(HistoryLearningPolicy.BATCH_ITEMS))){
            while(rows.moveToNext()){
                long seq=rows.getLong(rows.getColumnIndexOrThrow("seq"));JSONObject turn=savedJson(run+":unit:"+seq,rows.getBlob(rows.getColumnIndexOrThrow("payload")));
                int count=turn.getString("text").length(),bytes=turn.toString().getBytes(StandardCharsets.UTF_8).length;if(!HistoryLearningPolicy.accepts(turns.length(),characters,count)||encodedBytes+bytes>200_000)break;encodedBytes+=bytes;
                if(first==0)first=seq;last=seq;characters+=count;turns.put(turn);messages+=rows.getInt(rows.getColumnIndexOrThrow("last_fragment"));sources.put(new JSONObject().put("kind",rows.getString(rows.getColumnIndexOrThrow("kind"))).put("id",rows.getLong(rows.getColumnIndexOrThrow("source_id"))).put("signature",rows.getString(rows.getColumnIndexOrThrow("source_signature"))));
            }
        }
        if(turns.length()==0)throw new IllegalStateException("The saved history batch could not be read. Retry learning.");
        if(!HistoryArchive.learningMatches(c,thread,scope,sources)){fail(c,"Saved messages or conversation recipients changed. Tap Retry to rebuild the initial history analysis.",true);return false;}
        boolean finalBatch=DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM learning_queue WHERE thread=? AND seq>?",new String[]{Long.toString(thread),Long.toString(last)})==0;
        String requestId=UUID.nameUUIDFromBytes((run+":"+thread+":"+first+":"+last).getBytes(StandardCharsets.UTF_8)).toString();
        JSONObject input=new JSONObject().put("requestId",requestId).put("previous",savedMemory(run+":memory:"+thread,previous)).put("history",turns).put("finalBatch",finalBatch);
        if(!sameConfig(c,revision))throw new IllegalStateException("AI connection changed. Open Settings and retry learning.");
        JSONObject result=CloudClient.request(config,"/history-analysis",input),learned=memory(result.optJSONObject("memory"));
        if(!sameConfig(c,revision)||!run.equals(meta(c).optString("run")))throw new IllegalStateException("History learning changed. Tap Retry to resume.");
        if(!HistoryArchive.learningMatches(c,thread,scope,sources)){fail(c,"Saved messages or conversation recipients changed. Tap Retry to rebuild the initial history analysis.",true);return false;}
        byte[] encoded=HistoryArchive.sealLearning(run+":memory:"+thread,learned.toString().getBytes(StandardCharsets.UTF_8));
        db.beginTransactionNonExclusive();try{
            ContentValues saved=new ContentValues();saved.put("memory",encoded);saved.put("done",finalBatch?1:0);if(db.update("learning_contacts",saved,"thread=? AND scope=?",new String[]{Long.toString(thread),scope})!=1)throw new IllegalStateException("Conversation binding changed.");
            db.delete("learning_queue","thread=? AND seq<=?",new String[]{Long.toString(thread),Long.toString(last)});
            db.execSQL("UPDATE learning_meta SET processed_messages=processed_messages+?,completed_contacts=completed_contacts+? WHERE id=1 AND run=?",new Object[]{messages,finalBatch?1:0,run});
            if(!sameConfig(c,revision))throw new IllegalStateException("AI connection changed. Open Settings and retry learning.");db.setTransactionSuccessful();
        }finally{db.endTransaction();}MessageChanges.publish();
        if(DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM learning_queue",null)==0){finish(c,db,run);return false;}return true;
    }
    private static void finish(Context c,SQLiteDatabase db,String run){
        JSONObject value=meta(c);long remaining=DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM learning_queue",null);
        if(!run.equals(value.optString("run"))||!HistoryLearningPolicy.complete(remaining,value.optLong("processed_messages"),value.optLong("total_messages"))||value.optLong("completed_contacts")!=value.optLong("total_contacts"))throw new IllegalStateException("History learning is incomplete. Tap Retry to resume.");phase(c,"ready");
    }
}
