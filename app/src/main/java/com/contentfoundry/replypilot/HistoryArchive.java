package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.net.Uri;
import android.provider.Telephony;
import android.security.keystore.*;
import java.io.*;
import java.nio.charset.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Full, paged display archive. Every user-content record is authenticated/encrypted;
 * private SQLite indexes contain only provider IDs, dates and hashes. No media bytes,
 * drafts, training, send authorization or provider read markers are written here. */
final class HistoryArchive {
    static final ExecutorService EXECUTOR=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"reply-pilot-archive-read");t.setDaemon(true);return t;});
    private static final ScheduledExecutorService WRITER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"reply-pilot-archive-sync");t.setDaemon(true);return t;});
    private static final Object STATE=new Object(),KEY_LOCK=new Object();
    private static final String ALIAS="reply-pilot-full-history-v1";
    private static long accessRevision=1,contentEpoch=1,savedEpoch,lastSuccess;
    private static int lastAccess=-1;
    private static int syncFailures;
    private static String syncError="";
    private static boolean queued,working,blocked,clearPending,scrubPending;
    private static ScheduledFuture<?> scheduled;
    private static volatile SecretKey secret;
    @android.annotation.SuppressLint("StaticFieldLeak") private static Context application;
    private static Database database;
    private static final class Database extends SQLiteOpenHelper {
        Database(Context c){super(c,new File(c.getNoBackupFilesDir(),"history-archive-v1.db").getAbsolutePath(),null,1);setWriteAheadLoggingEnabled(true);}
        @Override public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE archive_meta(id INTEGER PRIMARY KEY CHECK(id=1),generation INTEGER NOT NULL,saved_at INTEGER NOT NULL,contacts INTEGER NOT NULL)");
            db.execSQL("INSERT INTO archive_meta VALUES(1,0,0,0)");
            db.execSQL("CREATE TABLE archive_messages(kind TEXT NOT NULL,id INTEGER NOT NULL,thread INTEGER NOT NULL,date INTEGER NOT NULL,rank INTEGER NOT NULL,seen INTEGER NOT NULL,signature TEXT NOT NULL,payload BLOB NOT NULL,PRIMARY KEY(kind,id))");
            db.execSQL("CREATE INDEX archive_history_page ON archive_messages(thread,date DESC,rank DESC,id DESC)");
            db.execSQL("CREATE TABLE archive_threads(thread INTEGER PRIMARY KEY,date INTEGER NOT NULL,id INTEGER NOT NULL,kind TEXT NOT NULL,seen INTEGER NOT NULL,payload BLOB NOT NULL)");
            db.execSQL("CREATE INDEX archive_inbox_page ON archive_threads(date DESC,thread DESC)");
            db.execSQL("CREATE TABLE archive_identities(thread INTEGER PRIMARY KEY,fingerprint TEXT NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("This archive needs a newer app.");}
    }
    private HistoryArchive(){}
    private static Database helper(Context c){synchronized(STATE){if(database==null)database=new Database(c.getApplicationContext());return database;}}
    private static int access(Context c){return (Messages.allowed(c,Manifest.permission.READ_SMS)?1:0)|(Messages.role(c)?2:0)|(Messages.allowed(c,Manifest.permission.READ_CONTACTS)?4:0);}
    static void changed(){synchronized(STATE){contentEpoch++;syncFailures=0;if(application!=null)scheduleLocked(application,750);}}
    static void accessChanged(Context supplied){Context c=supplied.getApplicationContext();synchronizeAccess(c,access(c));}
    private static long synchronizeAccess(Context c,int permission){synchronized(STATE){
        application=c;
        if(permission!=lastAccess){lastAccess=permission;accessRevision++;contentEpoch++;syncFailures=0;syncError="";
            if(!HistoryArchivePolicy.canRead(permission)){blocked=true;clearPending=true;}
            if((permission&4)==0)scrubPending=true;
            scheduleLocked(c,0);
        }
        return accessRevision;
    }}
    static void refresh(Context supplied){Context c=supplied.getApplicationContext();int permission=access(c);synchronizeAccess(c,permission);synchronized(STATE){
        application=c;if(!HistoryArchivePolicy.canRead(permission)){scheduleLocked(c,0);return;}
        if(syncFailures<=3&&(contentEpoch!=savedEpoch||lastSuccess==0||System.currentTimeMillis()-lastSuccess>60_000))scheduleLocked(c,400);
    }}
    static JSONObject status(Context supplied)throws JSONException{
        Context c=supplied.getApplicationContext();int permission=access(c);synchronizeAccess(c,permission);
        JSONObject empty=new JSONObject().put("status","waiting").put("savedAt",0).put("conversations",0).put("messages",0).put("error","");
        if(!PilotApp.foreground||!HistoryArchivePolicy.canRead(permission))return empty;
        long savedAt=0,chats=0,messages=0;String error;boolean running,hidden;
        synchronized(STATE){error=syncError;running=queued||working;hidden=blocked;}
        try{if(!hidden)try(Cursor rows=helper(c).getReadableDatabase().rawQuery("SELECT saved_at,(SELECT COUNT(*) FROM archive_threads),(SELECT COUNT(*) FROM archive_messages) FROM archive_meta WHERE id=1",null)){
            if(rows.moveToFirst()){savedAt=rows.getLong(0);chats=rows.getLong(1);messages=rows.getLong(2);}
        }}catch(RuntimeException unavailable){error="Saved history could not be checked. Retry saving your chats.";}
        if(!PilotApp.foreground||permission!=access(c))return empty;
        return new JSONObject().put("status",running?"syncing":!error.isEmpty()?"error":savedAt>0?"ready":"waiting").put("savedAt",savedAt).put("conversations",chats).put("messages",messages).put("error",error);
    }
    static JSONObject retry(Context supplied)throws JSONException{
        Context c=supplied.getApplicationContext();int permission=access(c);synchronizeAccess(c,permission);
        if(!PilotApp.foreground||!HistoryArchivePolicy.canRead(permission))throw new IllegalStateException("Open Reply Pilot with SMS access and set it as your default texting app to save history.");
        synchronized(STATE){contentEpoch++;syncFailures=0;syncError="";scheduleLocked(c,0);}return status(c);
    }
    private static void scheduleLocked(Context c,long delay){
        application=c;if(working)return;
        // An explicit retry or access revocation should not wait behind backoff.
        if(queued){if(delay!=0||scheduled==null||!scheduled.cancel(false))return;queued=false;}
        queued=true;scheduled=WRITER.schedule(HistoryArchive::work,delay,TimeUnit.MILLISECONDS);
    }
    private static void work(){
        Context c;long epoch,revision;int permission;boolean clear,scrub;
        synchronized(STATE){queued=false;scheduled=null;working=true;c=application;epoch=contentEpoch;revision=accessRevision;permission=lastAccess;clear=clearPending;scrub=scrubPending;clearPending=false;scrubPending=false;}
        boolean success=false;
        try{
            SQLiteDatabase db=helper(c).getWritableDatabase();
            if(clear){db.beginTransactionNonExclusive();try{db.delete("archive_messages",null,null);db.delete("archive_threads",null,null);db.delete("archive_identities",null,null);db.execSQL("UPDATE archive_meta SET generation=generation+1,saved_at=0,contacts=0 WHERE id=1");db.setTransactionSuccessful();}finally{db.endTransaction();}}
            if(scrub)scrubNames(c,db);
            if(HistoryArchivePolicy.canRead(permission)){sync(c,db,permission,revision);success=true;}
        }catch(Exception unavailable){/* Old complete archive survives provider/disk/key failures. Live messaging stays available. */}
        boolean again;synchronized(STATE){working=false;if(success&&revision==accessRevision){savedEpoch=epoch;lastSuccess=System.currentTimeMillis();blocked=false;syncFailures=0;syncError="";}
            else if(revision==accessRevision&&HistoryArchivePolicy.canRead(lastAccess)){syncFailures++;syncError="Phone history could not be fully saved. Retry saving your chats.";}
            again=clearPending||scrubPending||(contentEpoch!=epoch&&HistoryArchivePolicy.canRead(lastAccess));
            if(again)scheduleLocked(c,750);else if(!success&&HistoryArchivePolicy.canRead(lastAccess)){long delay=HistoryArchivePolicy.retryDelay(syncFailures);if(delay>0)scheduleLocked(c,delay);}}
        if(success||HistoryArchivePolicy.canRead(permission))MessageChanges.publish();
        if(success)HistoryLearning.refresh(c);
    }
    private static void check(Context c,int permission,long revision){int actual=access(c);long current=synchronizeAccess(c,actual);if(actual!=permission||current!=revision||!HistoryArchivePolicy.canRead(actual))throw new IllegalStateException("Message access changed during archive refresh.");}
    static boolean canDeliver(Context c,JSONObject result){
        if(result==null||!PilotApp.foreground)return false;int now=access(c);long revision=synchronizeAccess(c.getApplicationContext(),now);JSONObject stamp=result.optJSONObject("access");
        boolean pending=result.optBoolean("archivePending"),emptyPending=pending&&emptyPending(result);
        synchronized(STATE){return (pending?emptyPending:!blocked)&&stamp!=null&&HistoryArchivePolicy.canDeliver(now,(stamp.optBoolean("readSms")?1:0)|(stamp.optBoolean("defaultSms")?2:0)|(stamp.optBoolean("contacts")?4:0),revision,result.optLong("revision",-1));}
    }
    /** During a rebuild only this content-free progress envelope may cross the
     * blocked archive boundary. Old ciphertext stays unavailable until commit. */
    private static boolean emptyPending(JSONObject value){
        Set<String> allowed=Set.of("cacheOnly","readOnly","revision","snapshot","savedAt","access","archivePending","thread","address","name","history","inbox","hasMore","hasOlder","before","nextCursor");
        for(Iterator<String> keys=value.keys();keys.hasNext();)if(!allowed.contains(keys.next()))return false;
        JSONArray history=value.optJSONArray("history"),inbox=value.optJSONArray("inbox");
        if((history==null)==(inbox==null)||history!=null&&history.length()!=0||inbox!=null&&inbox.length()!=0)return false;
        if(value.has("history")&&history==null||value.has("inbox")&&inbox==null)return false;
        if(!(value.opt("revision") instanceof Number)||!(value.opt("savedAt") instanceof Number))return false;
        if(history!=null){try{MediaHistoryPolicy.integer(value.opt("thread"),true);}catch(IllegalArgumentException invalid){return false;}}
        else if(value.has("thread")||value.has("address")||value.has("name")||value.has("before")||value.has("hasOlder"))return false;
        JSONObject stamp=value.optJSONObject("access");if(stamp==null||stamp.length()!=3)return false;
        for(String key:List.of("readSms","defaultSms","contacts"))if(!(stamp.opt(key) instanceof Boolean))return false;
        return value.optBoolean("archivePending")&&value.optBoolean("cacheOnly")&&value.optBoolean("readOnly")&&"0".equals(value.optString("snapshot"))&&value.optLong("savedAt",-1)==0&&value.optString("address").isEmpty()&&value.optString("name").isEmpty()&&!value.optBoolean("hasMore")&&!value.optBoolean("hasOlder")&&value.isNull("before")&&value.isNull("nextCursor");
    }
    private static JSONObject envelope(int permission,long revision,long generation,long savedAt)throws JSONException{return new JSONObject().put("cacheOnly",true).put("readOnly",true).put("revision",revision).put("snapshot",Long.toString(generation)).put("savedAt",savedAt).put("access",new JSONObject().put("readSms",(permission&1)!=0).put("defaultSms",(permission&2)!=0).put("contacts",(permission&4)!=0));}
    private static void expectedSnapshot(JSONObject request,long generation){String expected=request.optString("snapshot");if(!expected.isBlank()&&!expected.equals(Long.toString(generation)))throw new IllegalStateException("Saved conversations updated. Open the latest cached page.");}
    private static Cursor window(Cursor cursor){if(cursor instanceof AbstractWindowedCursor rows)rows.setWindow(new CursorWindow("reply-pilot-archive",HistoryArchivePolicy.RECORD_BYTES*2L));return cursor;}
    static JSONObject inbox(Context supplied,JSONObject request)throws Exception{
        Context c=supplied.getApplicationContext();int permission=access(c);long revision=synchronizeAccess(c,permission);JSONArray rows=new JSONArray();
        JSONObject empty=envelope(permission,revision,0,0).put("inbox",rows).put("hasMore",false).put("nextCursor",JSONObject.NULL);
        synchronized(STATE){if(!HistoryArchivePolicy.canRead(permission))return empty;if(blocked)return empty.put("archivePending",true);}
        int limit=HistoryArchivePolicy.limit(request.opt("limit"),HistoryArchivePolicy.INBOX_PAGE);JSONObject cursor=request.optJSONObject("cursor");
        HistoryArchivePolicy.Query query=HistoryArchivePolicy.inbox(cursor==null?0:MediaHistoryPolicy.integer(cursor.opt("date"),false),cursor==null?0:MediaHistoryPolicy.integer(cursor.opt("thread"),true),cursor!=null);
        String search=request.optString("query").strip().toLowerCase(Locale.ROOT);if(search.length()>120)throw new IllegalArgumentException("Keep the search within 120 characters.");
        String sql="SELECT t.thread,t.date,t.payload,m.generation,m.saved_at FROM archive_meta m LEFT JOIN (SELECT thread,date,payload FROM archive_threads WHERE "+query.where()+" ORDER BY "+HistoryArchivePolicy.INBOX_ORDER+" LIMIT "+(search.isEmpty()?limit+1:HistoryArchivePolicy.SCAN_PAGE+1)+") t ON 1 ORDER BY t.date DESC,t.thread DESC";
        long generation=0,savedAt=0;boolean more=false;JSONObject next=null;int scanned=0;Set<Long> pins=PinnedChats.ids(c);HistoryArchivePolicy.Budget budget=new HistoryArchivePolicy.Budget(limit);
        try(Cursor data=window(helper(c).getReadableDatabase().rawQuery(sql,query.args().toArray(new String[0])))){
            while(data.moveToNext()){
                if(generation==0){generation=data.getLong(3);savedAt=data.getLong(4);expectedSnapshot(request,generation);}
                if(data.isNull(0))break;
                if(scanned++>=HistoryArchivePolicy.SCAN_PAGE||rows.length()>=limit){more=true;break;}
                long thread=data.getLong(0),date=data.getLong(1);byte[] plain=open("thread:"+thread,data.getBlob(2));JSONObject row=new JSONObject(new String(plain,StandardCharsets.UTF_8));
                if(row.optLong("thread_id")!=thread||row.optLong("date")!=date)throw new IOException("Invalid archive index");
                row.put("name",HistoryArchivePolicy.visibleName(row.optString("address"),row.optString("name"),(permission&4)!=0)).put("pinned",pins.contains(thread));
                boolean match=search.isEmpty()||(row.optString("name")+"\n"+row.optString("address")+"\n"+row.optString("body")).toLowerCase(Locale.ROOT).contains(search);
                if(match&&!budget.add(plain.length)){more=true;break;}next=new JSONObject().put("date",date).put("thread",thread);if(match)rows.put(row);
            }
        }
        if(savedAt==0)return empty.put("inbox",new JSONArray()).put("archivePending",true);
        ContactPhotos.annotate(c,rows,"address");
        return envelope(permission,revision,generation,savedAt).put("inbox",rows).put("hasMore",more).put("nextCursor",more&&next!=null?next:JSONObject.NULL);
    }
    static JSONObject history(Context supplied,JSONObject request)throws Exception{
        Context c=supplied.getApplicationContext();int permission=access(c);long revision=synchronizeAccess(c,permission),thread=MediaHistoryPolicy.integer(request.opt("thread"),true);JSONArray history=new JSONArray();
        JSONObject empty=envelope(permission,revision,0,0).put("thread",thread).put("address","").put("name","").put("history",history).put("hasOlder",false).put("hasMore",false).put("before",JSONObject.NULL);
        synchronized(STATE){if(!HistoryArchivePolicy.canRead(permission))return empty;if(blocked)return empty.put("archivePending",true);}
        if(!(request.opt("expectedAddress") instanceof String expected))throw new IllegalArgumentException("Open a cached conversation first.");
        int limit=HistoryArchivePolicy.limit(request.opt("limit"),HistoryArchivePolicy.HISTORY_PAGE);JSONObject before=request.optJSONObject("cursor");
        MediaHistoryPolicy.Position position=before==null?null:new MediaHistoryPolicy.Position(MediaHistoryPolicy.integer(before.opt("date"),false),before.optString("kind"),MediaHistoryPolicy.integer(before.opt("id"),true));
        HistoryArchivePolicy.Query query=HistoryArchivePolicy.history(thread,position);
        String sql="SELECT t.payload,h.kind,h.id,h.date,h.payload,m.generation,m.saved_at FROM archive_meta m LEFT JOIN archive_threads t ON t.thread=? LEFT JOIN (SELECT thread,kind,id,date,rank,payload FROM archive_messages WHERE "+query.where()+" ORDER BY "+HistoryArchivePolicy.HISTORY_ORDER+" LIMIT "+(limit+1)+") h ON h.thread=t.thread ORDER BY h.date DESC,h.rank DESC,h.id DESC";
        List<String> arguments=new ArrayList<>();arguments.add(Long.toString(thread));arguments.addAll(query.args());
        long generation=0,savedAt=0;JSONObject header=null,boundary=null;boolean more=false;List<JSONObject> reverse=new ArrayList<>();HistoryArchivePolicy.Budget budget=new HistoryArchivePolicy.Budget(limit);
        try(Cursor data=window(helper(c).getReadableDatabase().rawQuery(sql,arguments.toArray(new String[0])))){
            while(data.moveToNext()){
                if(generation==0){generation=data.getLong(5);savedAt=data.getLong(6);expectedSnapshot(request,generation);}
                if(data.isNull(0))break;
                if(header==null){header=new JSONObject(new String(open("thread:"+thread,data.getBlob(0)),StandardCharsets.UTF_8));if(header.optLong("thread_id")!=thread||!HistoryArchivePolicy.sameAddress(expected,header.optString("address"),header.optBoolean("readOnly")))throw new IllegalStateException("This saved chat belongs to a different contact. Open the latest conversation.");}
                if(data.isNull(1))break;
                byte[] plain=open("message:"+data.getString(1)+":"+data.getLong(2),data.getBlob(4));if(!budget.add(plain.length)){more=true;break;}
                JSONObject row=new JSONObject(new String(plain,StandardCharsets.UTF_8));if(row.optLong("thread_id")!=thread||!row.optString("kind").equals(data.getString(1))||row.optLong("_id")!=data.getLong(2)||row.optLong("date")!=data.getLong(3))throw new IOException("Invalid archive message index");
                reverse.add(row);boundary=new JSONObject().put("date",row.optLong("date")).put("kind",row.optString("kind")).put("id",row.optLong("_id"));
            }
        }
        if(savedAt==0)return empty.put("archivePending",true);
        Collections.reverse(reverse);for(JSONObject row:reverse)history.put(row);
        if(header==null)return envelope(permission,revision,generation,savedAt).put("thread",thread).put("address","").put("name","").put("history",history).put("hasOlder",false).put("hasMore",false).put("before",JSONObject.NULL);
        return envelope(permission,revision,generation,savedAt).put("thread",thread).put("address",header.optString("address")).put("name",HistoryArchivePolicy.visibleName(header.optString("address"),header.optString("name"),(permission&4)!=0)).put("contactFingerprint",header.optString("contactFingerprint")).put("recipientReadOnly",header.optBoolean("readOnly")).put("photo",ContactPhotos.url(c,header.optString("address"))).put("history",history).put("hasOlder",more).put("hasMore",more).put("before",boundary==null?JSONObject.NULL:boundary);
    }
    interface LearningSink {void row(long thread,String scope,JSONObject message)throws Exception;}
    static final class LearningPending extends IOException {LearningPending(){super("Wait for the complete saved history.");}}
    static boolean learningFailed(Context c){synchronizeAccess(c.getApplicationContext(),access(c));synchronized(STATE){return !working&&!queued&&!syncError.isEmpty();}}
    private static void learningAvailable(Context c)throws LearningPending{
        int permission=access(c);synchronizeAccess(c.getApplicationContext(),permission);
        synchronized(STATE){if(!HistoryArchivePolicy.canRead(permission)||blocked||lastSuccess==0||savedEpoch!=contentEpoch)throw new LearningPending();}
    }
    /** No names or phone numbers leave this accessor: only a local binding hash. */
    static String learningScope(Context c,long thread)throws Exception{
        if(!HistoryArchivePolicy.canRead(access(c)))throw new IllegalStateException("Restore messaging access.");
        String ids;
        try(Cursor rows=c.getContentResolver().query(Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple","true").build(),new String[]{"recipient_ids"},"_id=?",new String[]{Long.toString(thread)},null)){
            if(rows==null||!rows.moveToFirst()||rows.isNull(0))throw new IOException("Conversation recipients are unavailable");ids=rows.getString(0).strip();
        }
        if(ids.isEmpty()||ids.length()>16000)throw new IOException("Conversation recipients are unavailable");
        StringBuilder addresses=new StringBuilder();for(String id:ids.split(" +")){
            if(!id.matches("[0-9]+"))throw new IOException("Conversation recipient changed");
            try(Cursor rows=c.getContentResolver().query(Uri.parse("content://mms-sms/canonical-address/"+id),new String[]{"address"},null,null,null)){
                if(rows==null||!rows.moveToFirst()||rows.isNull(0))throw new IOException("Conversation recipient changed");addresses.append(id).append(':').append(rows.getString(0)).append('\n');
            }
        }
        return MediaContextPolicy.signature(ids,addresses.toString());
    }
    /** Copy one atomic archive snapshot into the encrypted learning checkpoint. */
    static long exportLearning(Context c,LearningSink sink)throws Exception{
        learningAvailable(c);int permission=access(c);long revision=synchronizeAccess(c,permission);SQLiteDatabase db=helper(c).getReadableDatabase();long generation;
        db.beginTransactionNonExclusive();try{
            generation=DatabaseUtils.longForQuery(db,"SELECT generation FROM archive_meta WHERE id=1",null);long previous=0;String scope="";
            try(Cursor rows=window(db.rawQuery("SELECT h.thread,h.kind,h.id,h.payload,i.fingerprint FROM archive_messages h JOIN archive_identities i ON i.thread=h.thread ORDER BY h.thread,h.date,h.rank,h.id",null))){
                int checked=0;while(rows.moveToNext()){
                    if(checked++%32==0)check(c,permission,revision);long thread=rows.getLong(0);
                    if(thread!=previous){
                        String ids="";try(Cursor live=c.getContentResolver().query(Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple","true").build(),new String[]{"recipient_ids"},"_id=?",new String[]{Long.toString(thread)},null)){if(live==null||!live.moveToFirst())throw new IOException("Conversation changed");ids=live.getString(0);}
                        if(!MediaContextPolicy.signature(ids==null?"":ids).equals(rows.getString(4)))throw new IOException("Conversation changed");scope=learningScope(c,thread);previous=thread;
                    }
                    JSONObject message=new JSONObject(new String(open("message:"+rows.getString(1)+":"+rows.getLong(2),rows.getBlob(3)),StandardCharsets.UTF_8));
                    if(message.optLong("thread_id")!=thread||!message.optString("kind").equals(rows.getString(1))||message.optLong("_id")!=rows.getLong(2))throw new IOException("Invalid archive binding");
                    sink.row(thread,scope,message);
                }
            }
            check(c,permission,revision);learningAvailable(c);db.setTransactionSuccessful();
        }finally{db.endTransaction();}return generation;
    }
    static String learningSignature(JSONObject message){return MediaContextPolicy.signature(message.optString("kind"),message.optString("_id"),message.optString("thread_id"),message.optString("date"),message.optString("type"),message.optString("msg_box"),message.optString("m_type"),message.optString("body"),message.optString("textUnavailable"),message.optString("truncated"));}
    static boolean learningMatches(Context c,long thread,String scope,JSONArray sources)throws Exception{
        learningAvailable(c);if(!scope.equals(learningScope(c,thread)))return false;SQLiteDatabase db=helper(c).getReadableDatabase();Set<String> checked=new HashSet<>();
        for(int i=0;i<sources.length();i++){
            JSONObject source=sources.getJSONObject(i);String kind=source.getString("kind");long id=source.getLong("id");if(!checked.add(kind+":"+id))continue;
            try(Cursor rows=window(db.query("archive_messages",new String[]{"thread","payload"},"kind=? AND id=?",new String[]{kind,Long.toString(id)},null,null,null))){
                if(!rows.moveToFirst()||rows.getLong(0)!=thread)return false;JSONObject message=new JSONObject(new String(open("message:"+kind+":"+id,rows.getBlob(1)),StandardCharsets.UTF_8));if(!source.getString("signature").equals(learningSignature(message)))return false;
            }
        }
        learningAvailable(c);return scope.equals(learningScope(c,thread));
    }
    static byte[] sealLearning(String identity,byte[] plain)throws Exception{return seal("learning:"+identity,plain);}
    static byte[] openLearning(String identity,byte[] payload)throws Exception{return open("learning:"+identity,payload);}
    private static void sync(Context c,SQLiteDatabase db,int permission,long revision)throws Exception{
        check(c,permission,revision);long generation,previousGeneration;
        try(Cursor row=db.rawQuery("SELECT generation,COALESCE((SELECT MAX(seen) FROM archive_messages),0),COALESCE((SELECT MAX(seen) FROM archive_threads),0) FROM archive_meta WHERE id=1",null)){
            if(!row.moveToFirst())throw new IOException("Archive metadata unavailable");
            previousGeneration=row.getLong(0);generation=HistoryArchivePolicy.scanMarker(previousGeneration,row.getLong(1),row.getLong(2),System.currentTimeMillis());
        }
        db.beginTransactionNonExclusive();try{
            captureIdentities(c,db,permission,revision);
            boolean contentChanged=false;
            for(String kind:List.of("sms","mms"))contentChanged|=scan(c,db,kind,generation,permission,revision);
            contentChanged|=db.delete("archive_messages","seen<>?",new String[]{Long.toString(generation)})>0;
            Uri threads=Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple","true").build();
            try(Cursor source=c.getContentResolver().query(threads,new String[]{"_id","recipient_ids"},null,null,"date DESC")){
                if(source==null)throw new IOException("Thread provider unavailable");int checked=0;
                while(source.moveToNext()){
                    if(checked++%32==0)check(c,permission,revision);long thread=source.getLong(0);if(thread<=0)continue;
                    String identity=MediaContextPolicy.signature(source.isNull(1)?"":source.getString(1));
                    try(Cursor captured=db.query("archive_identities",new String[]{"fingerprint"},"thread=?",new String[]{Long.toString(thread)},null,null,null)){
                        if(!captured.moveToFirst()||!identity.equals(captured.getString(0)))throw new IOException("Conversation recipients changed during archive refresh");
                    }
                    JSONObject newest;
                    try(Cursor latest=window(db.query("archive_messages",new String[]{"kind","id","payload"},"thread=?",new String[]{Long.toString(thread)},null,null,HistoryArchivePolicy.HISTORY_ORDER,"1"))){if(!latest.moveToFirst())continue;newest=new JSONObject(new String(open("message:"+latest.getString(0)+":"+latest.getLong(1),latest.getBlob(2)),StandardCharsets.UTF_8));}
                    MediaNavigation.Destination destination=MediaNavigation.destination(c,thread,newest);String address=destination.address(),name=HistoryArchivePolicy.visibleName(address,destination.name(),(permission&4)!=0),body=newest.optString("body");
                    if(body.isBlank()&&"mms".equals(newest.optString("kind")))body=InboxPreviewPolicy.fallback(newest.optInt("m_type"));
                    JSONObject summary=new JSONObject().put("thread_id",thread).put("_id",newest.optLong("_id")).put("date",newest.optLong("date")).put("kind",newest.optString("kind")).put("key",newest.optString("key")).put("type",newest.optInt("type")).put("read",newest.optInt("read")).put("body",ApprovedLearningPolicy.bounded(body,240)).put("address",address).put("name",name).put("readOnly",destination.readOnly()).put("contactFingerprint",MediaContextPolicy.signature(source.isNull(1)?"":source.getString(1),address));
                    if("mms".equals(newest.optString("kind")))summary.put("m_type",newest.optInt("m_type")).put("msg_box",newest.optInt("msg_box"));
                    byte[] plain=summary.toString().getBytes(StandardCharsets.UTF_8);boolean same=false;
                    try(Cursor old=window(db.query("archive_threads",new String[]{"date","id","kind","payload"},"thread=?",new String[]{Long.toString(thread)},null,null,null))){
                        same=old.moveToFirst()&&old.getLong(0)==newest.optLong("date")&&old.getLong(1)==newest.optLong("_id")&&old.getString(2).equals(newest.optString("kind"))&&Arrays.equals(plain,open("thread:"+thread,old.getBlob(3)));
                    }
                    ContentValues value=new ContentValues();value.put("seen",generation);
                    if(same)db.update("archive_threads",value,"thread=?",new String[]{Long.toString(thread)});
                    else{contentChanged=true;value.put("thread",thread);value.put("date",newest.optLong("date"));value.put("id",newest.optLong("_id"));value.put("kind",newest.optString("kind"));value.put("payload",seal("thread:"+thread,plain));if(db.insertWithOnConflict("archive_threads",null,value,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IOException("Archive full");}
                }
            }
            contentChanged|=db.delete("archive_threads","seen<>?",new String[]{Long.toString(generation)})>0;
            db.execSQL("DELETE FROM archive_messages WHERE thread NOT IN (SELECT thread FROM archive_threads)");
            contentChanged|=DatabaseUtils.longForQuery(db,"SELECT changes()",null)>0;
            check(c,permission,revision);ContentValues meta=new ContentValues();meta.put("generation",HistoryArchivePolicy.snapshotAfterScan(previousGeneration,generation,contentChanged));meta.put("saved_at",System.currentTimeMillis());meta.put("contacts",(permission&4)!=0?1:0);db.update("archive_meta",meta,"id=1",null);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private static void captureIdentities(Context c,SQLiteDatabase db,int permission,long revision)throws Exception{
        db.delete("archive_identities",null,null);Uri uri=Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple","true").build();
        try(Cursor rows=c.getContentResolver().query(uri,new String[]{"_id","recipient_ids"},null,null,"_id ASC")){
            if(rows==null)throw new IOException("Conversation identities unavailable");int checked=0;
            while(rows.moveToNext()){if(checked++%64==0)check(c,permission,revision);long thread=rows.getLong(0);if(thread<=0)continue;ContentValues values=new ContentValues();values.put("thread",thread);values.put("fingerprint",MediaContextPolicy.signature(rows.isNull(1)?"":rows.getString(1)));db.insertOrThrow("archive_identities",null,values);}
        }
    }
    private static boolean scan(Context c,SQLiteDatabase db,String kind,long generation,int permission,long revision)throws Exception{
        boolean contentChanged=false;
        boolean mms="mms".equals(kind);Uri uri=mms?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI;
        String[] columns=mms?new String[]{"_id","thread_id","date","msg_box","m_type","read"}:new String[]{"_id","thread_id","date","type","read","body"};
        String selection=mms?"msg_box IN (1,2,4,5) AND m_type IN (128,130,132)":"type IN (1,2,4,5,6)";
        try(Cursor source=c.getContentResolver().query(uri,columns,selection,null,"_id ASC")){
            if(source==null)throw new IOException("Message provider unavailable");int checked=0;
            while(source.moveToNext()){
                if(checked++%64==0)check(c,permission,revision);JSONObject original=Store.json(source);long id=original.optLong("_id"),thread=original.optLong("thread_id");if(id<=0||thread<=0)continue;
                if(!InboxPreviewPolicy.eligible(kind,original.optInt("type"),original.optInt("msg_box"),original.optInt("m_type")))continue;
                long date=MediaHistoryPolicy.date(kind,original.optLong("date",-1));int type=mms?(original.optInt("msg_box")==1?1:2):original.optInt("type");
                JSONObject row=new JSONObject().put("thread_id",thread).put("_id",id).put("date",date).put("kind",kind).put("key",kind+":"+id).put("type",type).put("read",original.optInt("read")).put("body",mms?"":original.optString("body")).put("cacheOnly",true);
                if(mms){row.put("m_type",original.optInt("m_type")).put("msg_box",original.optInt("msg_box"));parts(c,row);}
                byte[] plain=row.toString().getBytes(StandardCharsets.UTF_8);if(plain.length>HistoryArchivePolicy.RECORD_BYTES)throw new IOException("Message too large to archive");String signature=MediaContextPolicy.signature(row.toString());boolean same=false;
                try(Cursor existing=db.query("archive_messages",new String[]{"signature"},"kind=? AND id=?",new String[]{kind,Long.toString(id)},null,null,null)){same=existing.moveToFirst()&&signature.equals(existing.getString(0));}
                if(same){ContentValues seen=new ContentValues();seen.put("seen",generation);db.update("archive_messages",seen,"kind=? AND id=?",new String[]{kind,Long.toString(id)});}
                else{contentChanged=true;ContentValues values=new ContentValues();values.put("kind",kind);values.put("id",id);values.put("thread",thread);values.put("date",date);values.put("rank",mms?1:0);values.put("seen",generation);values.put("signature",signature);values.put("payload",seal("message:"+kind+":"+id,plain));if(db.insertWithOnConflict("archive_messages",null,values,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IOException("Archive full");}
            }
        }
        return contentChanged;
    }
    private static void parts(Context c,JSONObject row)throws Exception{
        JSONArray parts=new JSONArray();StringBuilder body=new StringBuilder();boolean unavailable=false,truncated=false;Uri uri=Telephony.Mms.Part.getPartUriForMessage(Long.toString(row.optLong("_id")));
        try(Cursor source=c.getContentResolver().query(uri,new String[]{"_id","ct","text","_data","chset"},null,null,"seq ASC, _id ASC")){
            if(source==null)throw new IOException("MMS text unavailable");
            while(source.moveToNext()){
                long id=source.getLong(0);String type=MmsTextReader.mime(source.getString(1));if(id<=0||type.isEmpty()||"application/smil".equals(type)||"application/smil+xml".equals(type))continue;JSONObject part=new JSONObject().put("_id",id).put("ct",type);
                if("text/plain".equals(type)){
                    MmsTextReader.Read loaded=MmsTextReader.read(c,id,source.isNull(2)?"":source.getString(2),!source.isNull(3),source.isNull(4)?106:source.getInt(4),HistoryArchivePolicy.RECORD_BYTES);
                    String text=loaded.text();unavailable|=loaded.unavailable();truncated|=loaded.truncated();if(loaded.unavailable())part.put("textUnavailable",true);
                    part.put("text",text);if(!text.isEmpty()){if(body.length()>0)body.append('\n');body.append(text);}
                }parts.put(part);
                if(body.length()>HistoryArchivePolicy.RECORD_BYTES||parts.length()>HistoryArchivePolicy.RECORD_BYTES/32)throw new IOException("MMS too large to archive");
            }
        }row.put("parts",parts).put("body",body.toString()).put("textUnavailable",unavailable).put("truncated",truncated);
    }
    private static void scrubNames(Context c,SQLiteDatabase db)throws Exception{
        if((access(c)&4)!=0)return;db.beginTransactionNonExclusive();try{
            try(Cursor rows=window(db.query("archive_threads",new String[]{"thread","payload"},null,null,null,null,"thread"))){while(rows.moveToNext()){long thread=rows.getLong(0);JSONObject summary=new JSONObject(new String(open("thread:"+thread,rows.getBlob(1)),StandardCharsets.UTF_8));summary.put("name",summary.optString("address"));ContentValues values=new ContentValues();values.put("payload",seal("thread:"+thread,summary.toString().getBytes(StandardCharsets.UTF_8)));db.update("archive_threads",values,"thread=?",new String[]{Long.toString(thread)});}}
            db.execSQL("UPDATE archive_meta SET contacts=0 WHERE id=1");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private static SecretKey key(boolean create)throws Exception{
        SecretKey cached=secret;if(cached!=null)return cached;synchronized(KEY_LOCK){if(secret!=null)return secret;KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS)){secret=(SecretKey)store.getKey(ALIAS,null);return secret;}
            if(!create){corrupt();throw new IOException("Archive encryption key is unavailable");}
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());secret=generator.generateKey();return secret;}
    }
    private static byte[] seal(String identity,byte[] plain)throws Exception{return HistoryArchiveCipher.seal(key(true),identity,plain);}
    private static byte[] open(String identity,byte[] sealed)throws Exception{
        try{return HistoryArchiveCipher.open(key(false),identity,sealed);}
        catch(AEADBadTagException|IllegalArgumentException invalid){corrupt();throw invalid;}
    }
    private static void corrupt(){synchronized(STATE){blocked=true;clearPending=true;contentEpoch++;if(application!=null)scheduleLocked(application,0);}}

}
