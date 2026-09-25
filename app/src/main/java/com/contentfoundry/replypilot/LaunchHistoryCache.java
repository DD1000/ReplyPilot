package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Encrypted recent display histories, loaded independently of the SMS provider. */
final class LaunchHistoryCache {
    static final ExecutorService EXECUTOR=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{java.lang.Thread t=new java.lang.Thread(r,"reply-pilot-launch-history");t.setDaemon(true);return t;});
    private static final ExecutorService WRITER=Executors.newSingleThreadExecutor(r->{java.lang.Thread t=new java.lang.Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"reply-pilot-save-history");t.setDaemon(true);return t;});
    private static final Object STATE=new Object(),FILE_LOCK=new Object();
    private static final String ALIAS="reply-pilot-launch-history-v1";
    private static final byte[] MAGIC={'R','P','H','1'},AAD="reply-pilot:launch-history:v1".getBytes(StandardCharsets.US_ASCII);
    private static long revision=1,sequence,contentEpoch;
    private static int lastAccess=-1;
    private static boolean writing,blockedUntilSaved;
    private static String lastRequested="",lastSaved="";
    // Every Work holds only getApplicationContext(), never an Activity.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static Work pending;
    private record Work(Context context,List<LaunchHistoryPolicy.Thread> threads,int access,long revision,long sequence,long contentEpoch,String signature,boolean clear,boolean scrub){}
    private LaunchHistoryCache(){}
    private static int access(Context c){return (Messages.allowed(c,Manifest.permission.READ_SMS)?1:0)|(Messages.role(c)?2:0)|(Messages.allowed(c,Manifest.permission.READ_CONTACTS)?4:0);}
    private static boolean allowed(int value){return (value&3)==3;}
    static void changed(){synchronized(STATE){contentEpoch++;lastRequested="";}}
    static void accessChanged(Context supplied){Context c=supplied.getApplicationContext();syncAccess(c,access(c));}
    private static long syncAccess(Context c,int permission){synchronized(STATE){
        if(lastAccess!=permission){int previous=lastAccess;lastAccess=permission;revision++;lastRequested="";
            if(!allowed(permission)||previous>=0){blockedUntilSaved=true;lastSaved="";queueLocked(new Work(c,List.of(),permission,revision,++sequence,contentEpoch,"",true,false));}
        }return revision;
    }}
    private static JSONObject publicAccess(int permission)throws JSONException{return new JSONObject().put("readSms",(permission&1)!=0).put("defaultSms",(permission&2)!=0).put("contacts",(permission&4)!=0);}
    static boolean canDeliver(Context c,JSONObject result){
        if(result==null||!PilotApp.foreground)return false;int permission=access(c);long active=syncAccess(c.getApplicationContext(),permission);JSONObject stamp=result.optJSONObject("access");
        synchronized(STATE){return !blockedUntilSaved&&allowed(permission)&&active==result.optLong("revision",-1)&&stamp!=null&&stamp.optBoolean("readSms")==((permission&1)!=0)&&stamp.optBoolean("defaultSms")==((permission&2)!=0)&&stamp.optBoolean("contacts")==((permission&4)!=0);}
    }
    static JSONObject load(Context supplied)throws JSONException{
        Context c=supplied.getApplicationContext();int permission=access(c);long expected=syncAccess(c,permission),savedAt=0;boolean needsScrub=false;JSONArray pages=new JSONArray();boolean blocked;synchronized(STATE){blocked=blockedUntilSaved;}
        if(allowed(permission)&&!blocked)try{synchronized(FILE_LOCK){JSONObject saved=read(c);if(saved!=null){JSONArray original=saved.getJSONArray("conversations");pages=normalize(original,(permission&4)!=0);savedAt=saved.getLong("savedAt");needsScrub=(permission&4)==0&&!pages.toString().equals(original.toString());}}}catch(Exception unavailable){/* A missing or corrupt preview must not block launch. */}
        int after=access(c);long active=syncAccess(c,after);if(after!=permission||active!=expected||!allowed(after)){pages=new JSONArray();savedAt=0;}
        if(needsScrub&&active==expected&&after==permission)synchronized(STATE){if(revision==expected&&!writing){lastRequested="";queueLocked(new Work(c,List.of(),permission,expected,++sequence,contentEpoch,"",false,true));}}
        return new JSONObject().put("conversations",pages).put("savedAt",savedAt).put("revision",active).put("access",publicAccess(after));
    }
    /** Schedule only after an authoritative inbox succeeded; no provider work on caller. */
    static void refresh(Context supplied,JSONArray inbox){
        if(inbox==null)return;Context c=supplied.getApplicationContext();int permission=access(c);long expected=syncAccess(c,permission);if(!allowed(permission))return;
        List<LaunchHistoryPolicy.Thread> rows=new ArrayList<>();for(int i=0;i<inbox.length();i++){JSONObject row=inbox.optJSONObject(i);if(row==null)continue;rows.add(new LaunchHistoryPolicy.Thread(number(row.opt("thread_id")),number(row.opt("date")),number(row.opt("_id")),row.optString("kind","sms"),LaunchInboxPolicy.text(row.optString("address"),80),LaunchInboxPolicy.text(row.optString("name"),120)));}
        List<LaunchHistoryPolicy.Thread> selected=LaunchHistoryPolicy.threads(rows);if(inbox.length()>0&&selected.isEmpty())return;
        String signature=MediaContextPolicy.signature(selected.toString());synchronized(STATE){
            if(revision!=expected||lastAccess!=permission)return;String request=signature+":"+contentEpoch;if(request.equals(lastRequested))return;lastRequested=request;
            if(selected.isEmpty())blockedUntilSaved=true;
            queueLocked(new Work(c,selected,permission,expected,++sequence,contentEpoch,request,selected.isEmpty(),false));
        }
    }
    private static void queueLocked(Work work){pending=work;if(writing)return;writing=true;WRITER.execute(()->{while(true){Work next;synchronized(STATE){next=pending;pending=null;if(next==null){writing=false;return;}}
        try{apply(next);}catch(Exception unavailable){synchronized(STATE){if(sequence==next.sequence())lastRequested="";}}
    }});}
    private static boolean current(Work work){int permission=access(work.context());long active=syncAccess(work.context(),permission);synchronized(STATE){return permission==work.access()&&active==work.revision()&&sequence==work.sequence()&&(work.clear()||work.scrub()||contentEpoch==work.contentEpoch());}}
    private static void apply(Work work)throws Exception{
        if(!current(work))return;
        if(work.clear()){synchronized(FILE_LOCK){if(!current(work))return;file(work.context()).delete();synchronized(STATE){lastSaved="";}}return;}
        JSONArray pages=new JSONArray();long savedAt=System.currentTimeMillis();
        if(work.scrub()){synchronized(FILE_LOCK){if(!current(work))return;JSONObject old=read(work.context());if(old==null)return;pages=old.getJSONArray("conversations");savedAt=old.getLong("savedAt");}}
        else for(LaunchHistoryPolicy.Thread thread:work.threads()){
            if(!current(work))return;
            JSONObject live=readPage(work.context(),thread);
            pages.put(new JSONObject().put("thread",thread.id()).put("page",live));
        }
        if(!current(work))return;JSONArray safe=normalize(pages,(work.access()&4)!=0);String signature=MediaContextPolicy.signature(safe.toString());
        synchronized(STATE){if(!blockedUntilSaved&&signature.equals(lastSaved))return;}
        byte[] plain=new JSONObject().put("version",1).put("savedAt",savedAt).put("conversations",safe).toString().getBytes(StandardCharsets.UTF_8);if(plain.length>LaunchHistoryPolicy.MAX_PLAIN)throw new IOException("Oversized history preview");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(AAD);byte[] iv=cipher.getIV(),encrypted=cipher.doFinal(plain);if(iv.length!=12)throw new IOException("Invalid history encryption");
        synchronized(FILE_LOCK){if(!current(work))return;AtomicFile file=file(work.context());FileOutputStream output=null;
            try{output=file.startWrite();output.write(MAGIC);output.write(iv);output.write(encrypted);if(!current(work)){file.failWrite(output);output=null;return;}file.finishWrite(output);output=null;synchronized(STATE){if(sequence==work.sequence()&&revision==work.revision()){lastSaved=signature;blockedUntilSaved=false;}}}finally{if(output!=null)file.failWrite(output);}
        }
    }
    private static JSONObject readPage(Context c,LaunchHistoryPolicy.Thread thread)throws JSONException{
        List<JSONObject> rows=new ArrayList<>();for(String kind:List.of("sms","mms")){
            InboxPreviewPolicy.Query query=InboxPreviewPolicy.latest(thread.id(),kind);
            String[] columns="sms".equals(kind)?new String[]{"_id","thread_id","date","type","read","body"}:new String[]{"_id","thread_id","date","msg_box","m_type","read"};
            android.net.Uri uri="sms".equals(kind)?android.provider.Telephony.Sms.CONTENT_URI:android.provider.Telephony.Mms.CONTENT_URI;
            try(android.database.Cursor cursor=c.getContentResolver().query(uri,columns,query.selection(),query.arguments().toArray(new String[0]),"date DESC, _id DESC")){
                if(cursor==null)throw new IllegalStateException("Recent messages unavailable");int scanned=0;
                while(scanned++<LaunchHistoryPolicy.MESSAGES&&cursor.moveToNext()){
                    JSONObject row=Store.json(cursor);if(row.optLong("thread_id")!=thread.id())continue;row.put("kind",kind).put("date",MediaHistoryPolicy.date(kind,row.optLong("date",-1)));
                    if("mms".equals(kind))row.put("type",row.optInt("msg_box")==1?1:2).put("body","");rows.add(row);
                }
            }
        }
        rows.sort((a,b)->MediaHistoryPolicy.compare(new MediaHistoryPolicy.Position(a.optLong("date"),a.optString("kind"),a.optLong("_id")),new MediaHistoryPolicy.Position(b.optLong("date"),b.optString("kind"),b.optLong("_id"))));
        JSONArray history=new JSONArray();for(int i=Math.max(0,rows.size()-LaunchHistoryPolicy.MESSAGES);i<rows.size();i++){JSONObject row=rows.get(i);if("mms".equals(row.optString("kind"))){MediaNavigation.parts(c,row);if(row.optString("body").isBlank()&&!row.optBoolean("textUnavailable")){String preview=Messages.mmsPreview(c,row.optLong("_id"),row.optInt("m_type"));row.put("body",preview);if(preview.length()>=InboxPreviewPolicy.MAX_TEXT)row.put("truncated",true);}}history.put(row);}
        return new JSONObject().put("thread",thread.id()).put("address",thread.address()).put("name",thread.name()).put("history",history);
    }
    private static long number(Object value){if(!(value instanceof Number n))return -1;double exact=n.doubleValue();long integral=n.longValue();return Double.isFinite(exact)&&exact==integral&&integral>=0&&integral<=LaunchInboxPolicy.MAX_SAFE_INTEGER?integral:-1;}
    private static JSONArray normalize(JSONArray input,boolean contacts)throws JSONException{
        JSONArray result=new JSONArray();Set<Long> seen=new HashSet<>();for(int i=0;i<input.length()&&result.length()<LaunchHistoryPolicy.THREADS;i++){
            JSONObject item=input.optJSONObject(i),page=item==null?null:item.optJSONObject("page");long thread=item==null?-1:number(item.opt("thread"));if(thread<=0||page==null||number(page.opt("thread"))!=thread||!seen.add(thread))continue;JSONArray rows=page.optJSONArray("history");if(rows==null)continue;
            List<LaunchHistoryPolicy.Text> messages=new ArrayList<>();for(int j=0;j<Math.min(rows.length(),120);j++){JSONObject row=rows.optJSONObject(j);if(row==null)continue;messages.add(new LaunchHistoryPolicy.Text(number(row.opt("thread_id")),number(row.opt("_id")),number(row.opt("date")),row.optString("kind","sms"),row.optInt("type",-1),row.optInt("read",-1),row.optString("body",""),row.optInt("m_type",-1),row.optInt("msg_box",-1),row.optBoolean("truncated"),row.optBoolean("textUnavailable")));}
            JSONArray history=new JSONArray();for(LaunchHistoryPolicy.Text row:LaunchHistoryPolicy.messages(messages,thread)){
                JSONObject text=new JSONObject().put("thread_id",thread).put("_id",row.id()).put("date",row.date()).put("kind",row.kind()).put("key",row.kind()+":"+row.id()).put("type",row.type()).put("read",row.read()).put("body",row.body()).put("truncated",row.truncated());
                if("mms".equals(row.kind()))text.put("m_type",row.mType()).put("msg_box",row.box()).put("textUnavailable",row.textUnavailable());history.put(text);
            }
            String address=LaunchInboxPolicy.text(page.optString("address"),80),name=contacts?LaunchInboxPolicy.text(page.optString("name"),120):address;if(name.isBlank())name=address;
            result.put(new JSONObject().put("thread",thread).put("page",new JSONObject().put("thread",thread).put("address",address).put("name",name).put("readOnly",true).put("history",history).put("hasOlder",true).put("hasMore",true)));
        }return result;
    }
    private static AtomicFile file(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"launch-history-v1.bin"));}
    private static SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);if(!create)return null;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
    }
    private static JSONObject read(Context c)throws Exception{
        byte[] blob;try(FileInputStream input=file(c).openRead()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(bytes.size()+count>LaunchHistoryPolicy.MAX_PLAIN+64)throw new IOException("Oversized history cache");bytes.write(buffer,0,count);}blob=bytes.toByteArray();}
        if(blob.length<32||!Arrays.equals(MAGIC,Arrays.copyOf(blob,4)))return null;SecretKey key=key(false);if(key==null)return null;Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(blob,4,16)));cipher.updateAAD(AAD);byte[] plain=cipher.doFinal(Arrays.copyOfRange(blob,16,blob.length));if(plain.length>LaunchHistoryPolicy.MAX_PLAIN)return null;
        JSONObject saved=new JSONObject(new String(plain,StandardCharsets.UTF_8));if(saved.optInt("version")!=1||saved.optLong("savedAt",-1)<0||!(saved.opt("conversations") instanceof JSONArray rows)||rows.length()>LaunchHistoryPolicy.THREADS)return null;return saved;
    }
}
