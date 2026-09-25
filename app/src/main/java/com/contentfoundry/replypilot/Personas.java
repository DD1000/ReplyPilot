package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.Telephony;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.json.*;

/**
 * One persona per chat, trained only when the owner taps Train Autopilot. The persona is
 * stored encrypted on this phone and sent with each reply request; the relay keeps nothing.
 */
final class Personas {
    static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"reply-pilot-personas");t.setDaemon(true);return t;});
    static final String UNTRAINED="Train Autopilot for this chat first. It learns from your recent texts with this person.";
    private static final String ALIAS="reply-pilot-personas-v1";
    private static final Object KEY_LOCK=new Object();
    private static volatile SecretKey secret;
    private static final Set<Long> TRAINING=ConcurrentHashMap.newKeySet();
    private static final Map<Long,String> ERRORS=new ConcurrentHashMap<>();
    private static Database database;
    private Personas(){}

    private static final class Database extends SQLiteOpenHelper {
        Database(Context c){super(c,new File(c.getNoBackupFilesDir(),"personas-v1.db").getAbsolutePath(),null,1);setWriteAheadLoggingEnabled(true);}
        @Override public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE personas(thread INTEGER PRIMARY KEY,scope TEXT NOT NULL,trained_at INTEGER NOT NULL,trained_messages INTEGER NOT NULL,latest_date INTEGER NOT NULL,model TEXT NOT NULL DEFAULT '',payload BLOB NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new IllegalStateException("Update Reply Pilot to read its trained chats.");}
    }
    private static synchronized Database helper(Context c){if(database==null)database=new Database(c.getApplicationContext());return database;}
    private record Row(long thread,String scope,long trainedAt,long trainedMessages,long latestDate,String model,byte[] payload){}
    private record Current(Row row,PersonaPolicy.Persona persona){}

    private static Row row(Context c,long thread){
        try(Cursor rows=helper(c).getReadableDatabase().query("personas",new String[]{"thread","scope","trained_at","trained_messages","latest_date","model","payload"},"thread=?",new String[]{Long.toString(thread)},null,null,null,"1")){
            return rows.moveToFirst()?new Row(rows.getLong(0),rows.getString(1),rows.getLong(2),rows.getLong(3),rows.getLong(4),rows.getString(5),rows.getBlob(6)):null;
        }
    }
    private static String identity(long thread,long trainedAt,String scope){return "persona:"+thread+":"+trainedAt+":"+scope;}
    /** A persona only applies while the chat still has the same single recipient it was trained on. */
    private static Current current(Context c,long thread)throws Exception{
        Row saved=row(c,thread);if(saved==null)return null;
        if(!saved.scope().equals(HistoryArchive.learningScope(c,thread)))return null;
        byte[] plain;
        try{plain=HistoryArchiveCipher.open(key(false),identity(thread,saved.trainedAt(),saved.scope()),saved.payload());}
        catch(Exception unreadable){
            helper(c).getWritableDatabase().delete("personas","thread=? AND trained_at=?",new String[]{Long.toString(thread),Long.toString(saved.trainedAt())});
            ERRORS.put(thread,"This chat's saved training could not be read. Train Autopilot again.");return null;
        }
        return new Current(saved,PersonaPolicy.parse(new JSONObject(new String(plain,StandardCharsets.UTF_8))));
    }
    static boolean ready(Context c,long thread){try{return thread>0&&current(c,thread)!=null;}catch(Exception unavailable){return false;}}
    static void requireReady(Context c,long thread){if(!ready(c,thread))throw new IllegalStateException(UNTRAINED);}
    /** Trained message count while the persona applies, otherwise -1. */
    static long trainedMessages(Context c,long thread){try{Current value=thread>0?current(c,thread):null;return value==null?-1:value.row().trainedMessages();}catch(Exception unavailable){return -1;}}
    /** Identifies one training. A retrain or removal during a reply request invalidates that request. */
    static String token(Context c,long thread){try{Row saved=thread>0?row(c,thread):null;return saved==null?"":Long.toString(saved.trainedAt());}catch(RuntimeException unavailable){return "";}}
    static boolean unchanged(Context c,long thread,String expected){return expected!=null&&expected.equals(token(c,thread))&&(expected.isEmpty()||ready(c,thread));}
    /** The persona sent with a reply request, or null when this chat is not trained. */
    static JSONObject forReply(Context c,long thread)throws Exception{Current value=current(c,thread);return value==null?null:PersonaPolicy.reply(value.persona(),value.row().trainedMessages());}

    static JSONObject status(Context c,long thread)throws JSONException{
        boolean busy=TRAINING.contains(thread);String error=ERRORS.getOrDefault(thread,"");
        JSONObject out=new JSONObject().put("thread",thread).put("trained",false).put("error",error);
        Current value=null;try{value=current(c,thread);}catch(Exception unavailable){if(error.isEmpty())out.put("error","This chat's training couldn't be checked. Reopen Reply setup.");}
        if(value!=null){
            long fresh=newMessages(c,thread,value.row().latestDate());
            out.put("trained",true).put("trainedAt",value.row().trainedAt()).put("trainedMessages",value.row().trainedMessages()).put("newMessages",fresh)
                .put("suggestRetrain",PersonaPolicy.suggestRetrain(fresh)).put("thin",PersonaPolicy.thin(value.row().trainedMessages())).put("model",value.row().model())
                .put("persona",PersonaPolicy.json(value.persona()));
        }
        return out.put("state",busy?"training":value!=null?"ready":!out.optString("error").isEmpty()?"error":"untrained");
    }
    private static long newMessages(Context c,long thread,long latestDate){
        if(latestDate<=0||!Messages.allowed(c,Manifest.permission.READ_SMS))return 0;long count=0;
        try(Cursor sms=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},"thread_id=? AND type IN (1,2) AND date>?",new String[]{Long.toString(thread),Long.toString(latestDate)},null)){if(sms!=null)count+=sms.getCount();}
        catch(RuntimeException unavailable){return 0;}
        try(Cursor mms=c.getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id"},"thread_id=? AND date>? AND ((msg_box=1 AND m_type=132) OR (msg_box=2 AND m_type=128))",new String[]{Long.toString(thread),Long.toString(latestDate/1000)},null)){if(mms!=null)count+=mms.getCount();}
        catch(RuntimeException unavailable){return count;}
        return count;
    }

    /** Explicit owner action. Reads this chat's newest texts, asks the relay once, stores the result. */
    static JSONObject train(Context context,long thread)throws Exception{
        Context c=context.getApplicationContext();SmsHistoryPolicy.validateThread(thread);
        if(!PilotApp.foreground)throw new IllegalStateException("Open Reply Pilot to train Autopilot.");
        if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow SMS access and choose Reply Pilot as your default texting app first.");
        JSONObject config=CloudConfig.read(c);if(config==null)throw new IllegalStateException("Connect your AI service in Settings first.");
        if(!TRAINING.add(thread))throw new IllegalStateException("Autopilot is already training for this chat.");
        ERRORS.remove(thread);
        try{
            String address=Sender.singleRecipient(c,thread),scope=HistoryArchive.learningScope(c,thread),revision=config.optString("revision");
            Runnable validate=()->{if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Messaging access changed. Training stopped.");};
            List<PilotTrainingHistoryPolicy.Entry> rows=new ArrayList<>(PilotTrainingHistory.recent(c,thread,address,PersonaPolicy.MAX_MESSAGES,validate));
            rows.sort(Comparator.comparing(PilotTrainingHistoryPolicy.Entry::position,MediaHistoryPolicy::compare));
            Set<Long> automatic=automaticIds(c,thread,address,rows);
            List<String> speakers=new ArrayList<>(),texts=new ArrayList<>();
            for(PilotTrainingHistoryPolicy.Entry row:rows){
                boolean mine=row.type()==2,auto=mine&&"sms".equals(row.position().kind())&&automatic.contains(row.position().id());
                speakers.add(auto?PersonaPolicy.AUTOPILOT:mine?PersonaPolicy.ME:PersonaPolicy.THEM);texts.add(row.text());
            }
            List<PersonaPolicy.Turn> turns=PersonaPolicy.turns(speakers,texts);
            String blocked=PersonaPolicy.trainingBlock(turns);if(blocked!=null)throw new IllegalStateException(blocked);
            long latest=rows.isEmpty()?0:rows.get(rows.size()-1).position().date();
            JSONObject health=CloudClient.request(config,"/health",null);
            if(health.optInt("personaVersion")<1)throw new IllegalStateException("Update your Reply Pilot server to use Train Autopilot, then try again.");
            JSONObject response=CloudClient.request(config,"/persona-train",PersonaPolicy.request(UUID.randomUUID().toString(),turns,rows.size()));
            PersonaPolicy.Persona persona=PersonaPolicy.result(response,turns);
            validate.run();
            if(!scope.equals(HistoryArchive.learningScope(c,thread)))throw new IllegalStateException("This chat's recipient changed during training. Try again.");
            JSONObject now=CloudConfig.read(c);if(now==null||!revision.equals(now.optString("revision")))throw new IllegalStateException("Your AI connection changed during training. Try again.");
            long trainedAt=System.currentTimeMillis();
            ContentValues values=new ContentValues();
            values.put("thread",thread);values.put("scope",scope);values.put("trained_at",trainedAt);values.put("trained_messages",turns.size());values.put("latest_date",latest);
            values.put("model",response.optString("model"));
            values.put("payload",HistoryArchiveCipher.seal(key(true),identity(thread,trainedAt,scope),PersonaPolicy.json(persona).toString().getBytes(StandardCharsets.UTF_8)));
            helper(c).getWritableDatabase().insertWithOnConflict("personas",null,values,SQLiteDatabase.CONFLICT_REPLACE);
            // Earlier "needs training" decisions no longer apply to the next incoming text.
            Store.get(c).getWritableDatabase().delete("reply_decisions","thread=? AND reason='insufficient_history'",new String[]{Long.toString(thread)});
            return status(c,thread);
        }catch(Exception error){
            String message=error instanceof IllegalStateException||error instanceof IllegalArgumentException?error.getMessage():null;
            if(message==null||message.isBlank()||message.length()>280)message="Training could not finish. Check your connection, then try again.";
            ERRORS.put(thread,message);throw new IllegalStateException(message);
        }finally{TRAINING.remove(thread);}
    }
    /** Automatic replies Reply Pilot sent are conversation context, never the owner's voice. */
    private static Set<Long> automaticIds(Context c,long thread,String address,List<PilotTrainingHistoryPolicy.Entry> rows)throws JSONException{
        Set<Long> result=new HashSet<>();JSONArray batch=new JSONArray();
        for(PilotTrainingHistoryPolicy.Entry row:rows){
            if(row.type()!=2||!"sms".equals(row.position().kind()))continue;
            batch.put(new JSONObject().put("_id",row.position().id()).put("type",2).put("thread_id",thread).put("date",row.position().date()).put("address",address).put("body",row.text()));
            if(batch.length()==400){result.addAll(Store.get(c).automaticMessageIds(batch));batch=new JSONArray();}
        }
        if(batch.length()>0)result.addAll(Store.get(c).automaticMessageIds(batch));
        return result;
    }
    /** Removing training immediately stops Autopilot for this chat until it is trained again. */
    static JSONObject forget(Context context,long thread)throws Exception{
        Context c=context.getApplicationContext();SmsHistoryPolicy.validateThread(thread);
        if(TRAINING.contains(thread))throw new IllegalStateException("Wait for training to finish, then remove it.");
        helper(c).getWritableDatabase().delete("personas","thread=?",new String[]{Long.toString(thread)});ERRORS.remove(thread);
        Sender.cancelAutomaticForThread(c,thread,"Autopilot training was removed for this chat. Review this reply before sending.");
        return status(c,thread);
    }
    private static SecretKey key(boolean create)throws Exception{
        SecretKey cached=secret;if(cached!=null)return cached;
        synchronized(KEY_LOCK){
            if(secret!=null)return secret;KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            if(store.containsAlias(ALIAS)){secret=(SecretKey)store.getKey(ALIAS,null);return secret;}
            if(!create)throw new IllegalStateException("Saved training is unavailable. Train Autopilot again.");
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            secret=generator.generateKey();return secret;
        }
    }
}
