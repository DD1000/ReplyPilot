package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Encrypted launch-only inbox summaries; no history, media, draft or AI state. */
final class LaunchInboxCache {
    static final ExecutorService EXECUTOR=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),work->{Thread t=new Thread(work,"reply-pilot-launch-inbox");t.setDaemon(true);return t;});
    private static final ExecutorService WRITER=Executors.newSingleThreadExecutor(work->{Thread t=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);work.run();},"reply-pilot-save-inbox");t.setDaemon(true);return t;});
    private static final Object STATE=new Object(),FILE_LOCK=new Object();
    private static final String ALIAS="reply-pilot-launch-inbox-v1";
    private static final byte[] MAGIC={'R','P','L','1'},AAD="reply-pilot:launch-inbox:v1".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_FILE=LaunchInboxPolicy.MAX_PLAIN+64;
    private static long revision=1,sequence;
    private static int lastAccess=-1;
    private static Pending pending;
    private static boolean writing,blockedUntilSaved;
    private static String lastSavedSignature="";
    private record Pending(Context context,String action,String body,int access,long revision,long sequence){}
    private LaunchInboxCache(){}
    private static int access(Context c){return (Messages.allowed(c,Manifest.permission.READ_SMS)?1:0)|(Messages.role(c)?2:0)|(Messages.allowed(c,Manifest.permission.READ_CONTACTS)?4:0);}
    private static boolean allowed(int access){return (access&3)==3;}
    private static JSONObject publicAccess(int access)throws JSONException{return new JSONObject().put("readSms",(access&1)!=0).put("defaultSms",(access&2)!=0).put("contacts",(access&4)!=0);}
    static void accessChanged(Context context){syncAccess(context.getApplicationContext(),access(context));}
    private static long syncAccess(Context c,int value){
        synchronized(STATE){
            if(value!=lastAccess){int previous=lastAccess;lastAccess=value;revision++;if(!allowed(value)){blockedUntilSaved=true;queueLocked(c,"clear",null,value,revision);}else if(previous>=0&&(previous&4)!=0&&(value&4)==0)queueLocked(c,"scrub",null,value,revision);}
            return revision;
        }
    }
    static void clear(Context context){Context c=context.getApplicationContext();int permission=access(c);synchronized(STATE){lastAccess=permission;revision++;blockedUntilSaved=true;queueLocked(c,"clear",null,permission,revision);}}
    private static JSONObject response(JSONArray rows,long savedAt,long expected,int permission)throws JSONException{return new JSONObject().put("inbox",rows).put("savedAt",savedAt).put("revision",expected).put("access",publicAccess(permission));}
    static boolean canDeliver(Context c,JSONObject result){
        if(result==null||!PilotApp.foreground)return false;int permission=access(c);long active=syncAccess(c.getApplicationContext(),permission);JSONObject stamp=result.optJSONObject("access");
        boolean blocked;synchronized(STATE){blocked=blockedUntilSaved;}
        return !blocked&&allowed(permission)&&active==result.optLong("revision",-1)&&stamp!=null&&stamp.optBoolean("readSms")==((permission&1)!=0)&&stamp.optBoolean("defaultSms")==((permission&2)!=0)&&stamp.optBoolean("contacts")==((permission&4)!=0);
    }
    static JSONObject load(Context context)throws JSONException{
        Context c=context.getApplicationContext();int permission=access(c);long expected=syncAccess(c,permission);JSONArray rows=new JSONArray();long savedAt=0;boolean scrub=false;
        boolean blocked;synchronized(STATE){blocked=blockedUntilSaved;}
        if(allowed(permission)&&!blocked){
            try{synchronized(FILE_LOCK){JSONObject saved=read(c);if(saved!=null){JSONArray stored=saved.getJSONArray("inbox");rows=normalize(stored,(permission&4)!=0);savedAt=saved.getLong("savedAt");if((permission&4)==0)for(int i=0;i<stored.length();i++){JSONObject row=stored.optJSONObject(i);if(row!=null&&!text(row.opt("name")).equals(text(row.opt("address")))){scrub=true;break;}}}}}
            catch(Exception corrupt){/* A missing, locked, damaged or obsolete preview cannot block launch. */}
        }
        int after=access(c);long active=syncAccess(c,after);if(active!=expected||after!=permission||!allowed(after)){rows=new JSONArray();savedAt=0;}
        if(scrub&&active==expected&&after==permission)synchronized(STATE){if(revision==expected&&!writing)queueLocked(c,"scrub",null,permission,expected);}
        // Pin preferences are durable independently of the older inbox snapshot.
        java.util.Set<Long> currentPins=PinnedChats.ids(c);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);row.put("pinned",currentPins.contains(row.optLong("thread_id")));}
        // Resolve local thumbnail routes with current access; photos stay out of the saved preview.
        ContactPhotos.annotate(c,rows,"address");
        return response(rows,savedAt,active,after);
    }
    /** Only pass a successful authoritative inbox. A provider failure is not empty. */
    static void save(Context context,JSONArray authoritative){
        if(authoritative==null)return;Context c=context.getApplicationContext();int permission=access(c);long expected=syncAccess(c,permission);if(!allowed(permission))return;
        try{
            JSONArray rows=normalize(authoritative,(permission&4)!=0);if(authoritative.length()>0&&rows.length()==0)return;
            String signature=MediaContextPolicy.signature(rows.toString());
            String body=rows.length()==0?null:new JSONObject().put("version",2).put("savedAt",System.currentTimeMillis()).put("inbox",rows).toString();
            if(body!=null&&body.getBytes(StandardCharsets.UTF_8).length>LaunchInboxPolicy.MAX_PLAIN)return;
            synchronized(STATE){if(!writing&&!blockedUntilSaved&&signature.equals(lastSavedSignature))return;if(revision==expected&&lastAccess==permission)queueLocked(c,body==null?"clear":"save",body,permission,expected);}
        }catch(JSONException invalid){/* Preserve the last good preview. */}
    }
    private static void queueLocked(Context c,String action,String body,int access,long expected){
        if("clear".equals(action)){blockedUntilSaved=true;lastSavedSignature="";}
        if("scrub".equals(action)&&blockedUntilSaved)action="clear";
        pending=new Pending(c,action,body,access,expected,++sequence);if(writing)return;writing=true;
        WRITER.execute(()->{while(true){Pending work;synchronized(STATE){work=pending;pending=null;if(work==null){writing=false;return;}}
            try{apply(work);}catch(Exception unavailable){/* A preview write must never fail a message read or send. */}
        }});
    }
    private static boolean current(Pending work){int actual=access(work.context());synchronized(STATE){return actual==work.access()&&lastAccess==actual&&revision==work.revision()&&sequence==work.sequence();}}
    private static void apply(Pending work)throws Exception{
        synchronized(FILE_LOCK){
            AtomicFile file=file(work.context());
            if("clear".equals(work.action())){synchronized(STATE){if(sequence!=work.sequence())return;}file.delete();return;}
            if(!current(work))return;
            if(!allowed(work.access()))return;
            String plain=work.body();if("scrub".equals(work.action())){JSONObject saved=read(work.context());if(saved==null)return;saved.put("inbox",normalize(saved.getJSONArray("inbox"),false));plain=saved.toString();}
            byte[] data=plain.getBytes(StandardCharsets.UTF_8);if(data.length>LaunchInboxPolicy.MAX_PLAIN)return;String signature=MediaContextPolicy.signature(new JSONObject(plain).getJSONArray("inbox").toString());
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(AAD);byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(data);if(iv.length!=12||encrypted.length+16>MAX_FILE)return;
            if(!current(work))return;FileOutputStream output=null;try{output=file.startWrite();output.write(MAGIC);output.write(iv);output.write(encrypted);if(!current(work)){file.failWrite(output);output=null;return;}file.finishWrite(output);output=null;synchronized(STATE){if(revision==work.revision()&&sequence==work.sequence()){lastSavedSignature=signature;if("save".equals(work.action()))blockedUntilSaved=false;}}}finally{if(output!=null)file.failWrite(output);}
        }
    }
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"launch-inbox-v1.bin"));}
    private static SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);if(!create)return null;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    private static JSONObject read(Context c)throws Exception{
        byte[] blob;try(FileInputStream input=file(c).openRead()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1){if(bytes.size()+n>MAX_FILE)throw new IllegalArgumentException("Oversized launch preview");bytes.write(buffer,0,n);}blob=bytes.toByteArray();}
        if(blob.length<32||!Arrays.equals(MAGIC,Arrays.copyOf(blob,4)))return null;SecretKey key=key(false);if(key==null)return null;
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(blob,4,16)));cipher.updateAAD(AAD);
        byte[] plain=cipher.doFinal(Arrays.copyOfRange(blob,16,blob.length));if(plain.length>LaunchInboxPolicy.MAX_PLAIN)return null;
        JSONObject saved=new JSONObject(new String(plain,StandardCharsets.UTF_8));if(saved.optInt("version")!=2||!(saved.opt("inbox") instanceof JSONArray rows)||rows.length()>LaunchInboxPolicy.LIMIT||saved.optLong("savedAt",-1)<0)return null;return saved;
    }
    private static String text(Object value){return value instanceof String string?string:"";}
    private static long number(Object value){if(!(value instanceof Number number))return -1;double exact=number.doubleValue();long integral=number.longValue();return Double.isFinite(exact)&&exact==integral&&integral>=0&&integral<=LaunchInboxPolicy.MAX_SAFE_INTEGER?integral:-1;}
    private static JSONArray normalize(JSONArray input,boolean contacts)throws JSONException{
        List<LaunchInboxPolicy.Row> candidates=new ArrayList<>();for(int i=0;i<input.length();i++){
            JSONObject row=input.optJSONObject(i);if(row==null)continue;String kind=row.has("kind")?text(row.opt("kind")):"sms";long type=number(row.opt("type")),read=number(row.opt("read"));if(type>6||read>1)continue;
            LaunchInboxPolicy.Row value=new LaunchInboxPolicy.Row(number(row.opt("thread_id")),number(row.opt("_id")),number(row.opt("date")),kind,(int)type,(int)read,text(row.opt("address")),text(row.opt("name")),text(row.opt("body")),row.optBoolean("pinned"),row.optBoolean("readOnly"),(int)number(row.opt("m_type")),(int)number(row.opt("msg_box")));
            if(LaunchInboxPolicy.valid(value))candidates.add(LaunchInboxPolicy.scrub(value,contacts));
        }
        JSONArray result=new JSONArray();for(LaunchInboxPolicy.Row row:LaunchInboxPolicy.select(candidates)){
            JSONObject display=new JSONObject().put("thread_id",row.thread()).put("_id",row.id()).put("date",row.date()).put("kind",row.kind()).put("key",row.kind()+":"+row.id()).put("type",row.type()).put("read",row.read()).put("address",row.address()).put("name",row.name()).put("body",row.body()).put("pinned",row.pinned()).put("readOnly",row.readOnly());
            if("mms".equals(row.kind()))display.put("m_type",row.mType()).put("msg_box",row.box());result.put(display);
        }return result;
    }
}
