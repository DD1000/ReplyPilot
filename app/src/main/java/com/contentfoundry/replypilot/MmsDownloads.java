package com.contentfoundry.replypilot;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.*;
import androidx.core.content.FileProvider;
import com.google.android.mms.pdu_alt.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable incoming-download state. URLs and callback capabilities never cross the WebView bridge. */
final class MmsDownloads extends SQLiteOpenHelper {
    static final String DOWNLOAD="com.contentfoundry.replypilot.MMS_DOWNLOAD", ACK="com.contentfoundry.replypilot.MMS_ACK";
    private static MmsDownloads instance;
    private static final Set<String> PROCESSING=new HashSet<>();
    private static final Object RECEIPT_LOCK=new Object();
    private static final java.util.concurrent.atomic.AtomicInteger PENDING_RECEIPTS=new java.util.concurrent.atomic.AtomicInteger();
    static void receiptStarted(){PENDING_RECEIPTS.incrementAndGet();}
    static void receiptFinished(){PENDING_RECEIPTS.decrementAndGet();MessageChanges.publish();}
    static boolean isPending(){return PENDING_RECEIPTS.get()>0;}
    /** This lock never takes SEND_LOCK: receipt order is recorded before queued work. */
    static long markReceipt(Context c){synchronized(RECEIPT_LOCK){
        try{
            SharedPreferences preferences=c.getSharedPreferences("mms_receipts",Context.MODE_PRIVATE);
            long current=preferences.getLong("sequence",0);if(current<0||current==Long.MAX_VALUE)return -1;
            long next=current+1;return preferences.edit().putLong("sequence",next).commit()?next:-1;
        }catch(RuntimeException unavailable){return -1;}
    }}
    static long receiptSequence(Context c){synchronized(RECEIPT_LOCK){
        try{long current=c.getSharedPreferences("mms_receipts",Context.MODE_PRIVATE).getLong("sequence",0);return current>=0?current:Long.MAX_VALUE;}
        catch(RuntimeException unavailable){return Long.MAX_VALUE;}
    }}
    private MmsDownloads(Context c){super(c,"mms-downloads.db",null,2);}
    private static synchronized MmsDownloads db(Context c){if(instance==null)instance=new MmsDownloads(c.getApplicationContext());return instance;}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE downloads (fingerprint TEXT PRIMARY KEY, notice_id INTEGER NOT NULL DEFAULT 0, thread INTEGER NOT NULL DEFAULT 0, notice_date INTEGER NOT NULL DEFAULT 0, location TEXT NOT NULL, transaction_id TEXT NOT NULL, sender TEXT NOT NULL, sub INTEGER NOT NULL, state TEXT NOT NULL, token TEXT NOT NULL DEFAULT '', started INTEGER NOT NULL DEFAULT 0, updated INTEGER NOT NULL, note TEXT NOT NULL DEFAULT '', manual INTEGER NOT NULL DEFAULT 0, expected_parts INTEGER NOT NULL DEFAULT 0, retrieve_id TEXT NOT NULL DEFAULT '', ready_hash TEXT NOT NULL DEFAULT '', ack_token TEXT NOT NULL DEFAULT '', ack_state TEXT NOT NULL DEFAULT '', receipt_token INTEGER NOT NULL DEFAULT -1)");
        db.execSQL("CREATE UNIQUE INDEX download_notice ON downloads(notice_id) WHERE notice_id>0");
        db.execSQL("CREATE UNIQUE INDEX download_token ON downloads(token) WHERE token<>''");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion==1&&newVersion==2)db.execSQL("ALTER TABLE downloads ADD COLUMN receipt_token INTEGER NOT NULL DEFAULT -1");else throw new IllegalStateException("Unsupported MMS download store upgrade.");}
    private JSONObject one(String selection,String...args){try(Cursor rows=getReadableDatabase().query("downloads",null,selection,args,null,null,null,"1")){return rows.moveToFirst()?Store.json(rows):null;}}
    private static boolean access(Context c){return Messages.role(c)&&Messages.allowed(c,Manifest.permission.READ_SMS)&&Messages.allowed(c,Manifest.permission.RECEIVE_MMS)&&Messages.allowed(c,Manifest.permission.RECEIVE_WAP_PUSH);}
    private static void requireAccess(Context c){if(!access(c))throw new IllegalStateException("Choose Reply Pilot as the default texting app and allow SMS/MMS access.");}
    private static List<Integer> active(Context c){JSONArray sims=Messages.sims(c);List<Integer> out=new ArrayList<>();for(int i=0;i<sims.length();i++)out.add(sims.optJSONObject(i).optInt("id",-1));return out;}
    private static Uri noticeUri(long id){if(id<=0)throw new IllegalArgumentException("Invalid media message.");return ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI,id);}
    private static JSONObject provider(Context c,long id){
        if(id<=0)return null;
        try(Cursor rows=c.getContentResolver().query(noticeUri(id),new String[]{"_id","thread_id","date","m_type","msg_box","ct_l","tr_id","sub_id","m_id","m_size"},null,null,null)){
            if(rows==null)throw new IllegalStateException("This media message cannot be read.");return rows.moveToFirst()?Store.json(rows):null;
        }
    }
    private static boolean identity(JSONObject item,JSONObject row){return row!=null&&row.optLong("_id")==item.optLong("notice_id")&&row.optInt("msg_box")==1&&row.optLong("thread_id")==item.optLong("thread")&&row.optLong("date")==item.optLong("notice_date")&&row.optInt("sub_id",-1)==item.optInt("sub",-1);}
    private static MmsDownloadPolicy.Notice expected(JSONObject item){return new MmsDownloadPolicy.Notice(item.optLong("notice_id"),item.optLong("thread"),item.optLong("notice_date"),item.optInt("sub",-1),1,130,item.optString("location"),item.optString("transaction_id"));}
    private static MmsDownloadPolicy.Notice actual(JSONObject row){return row==null?null:new MmsDownloadPolicy.Notice(row.optLong("_id"),row.optLong("thread_id"),row.optLong("date"),row.optInt("sub_id",-1),row.optInt("msg_box"),row.optInt("m_type"),row.optString("ct_l"),row.optString("tr_id"));}
    private static boolean bound(JSONObject item,JSONObject row){return MmsDownloadPolicy.bound(expected(item),actual(row));}
    private static boolean repairable(Context c,JSONObject item,JSONObject row){
        try{return item.optInt("expected_parts")>0&&!item.optString("ready_hash").isEmpty()&&MmsDownloadPolicy.pduSize(file(c,item.optString("token"),".ready").length())&&(bound(item,row)||MmsDownloadPolicy.partial(expected(item),actual(row),item.optString("retrieve_id"),row==null?"":row.optString("m_id")));}catch(IOException unavailable){return false;}
    }
    private static boolean received(JSONObject item,JSONObject row){return identity(item,row)&&row.optInt("m_type")==132&&row.optString("m_id").equals(item.optString("retrieve_id"));}
    private static ContentValues values(String state,String note){ContentValues v=new ContentValues();v.put("state",state);v.put("note",note);v.put("updated",System.currentTimeMillis());return v;}
    private static void set(Context c,JSONObject item,String state,String note){db(c).getWritableDatabase().update("downloads",values(state,note),"fingerprint=?",new String[]{item.optString("fingerprint")});}
    private static String latin(byte[] value){return value==null?"":new String(value,StandardCharsets.ISO_8859_1);}
    private static File file(Context c,String token,String suffix)throws IOException{
        if(!MmsDownloadPolicy.token(token))throw new IOException("Invalid download reference.");
        File dir=new File(c.getFilesDir(),"mms/incoming");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("MMS storage is unavailable.");return new File(dir,token+suffix);
    }
    private static Uri local(Context c,File file){return FileProvider.getUriForFile(c,c.getPackageName()+".files",file);}
    private static Intent callback(Context c,String action,String token){return new Intent(c,MmsDownloadedReceiver.class).setAction(action).setData(Uri.parse("replypilot://mms-download/"+token));}
    private static void cleanup(Context c,String token,String suffix){try{File f=file(c,token,suffix);c.revokeUriPermission(local(c,f),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);f.delete();}catch(Exception ignored){}}
    private static void announce(Context c,JSONObject item,String title,String text){long id=item.optLong("notice_id");Notices.show(c,0x40000000+(int)(id%0x0fffffff),title,text,item.optLong("thread"));MessageChanges.publish();}
    static void receive(Context c,Intent intent)throws Exception{receive(c,intent,-1);}
    static void receive(Context c,Intent intent,long receipt)throws Exception{
        requireAccess(c);recover(c);byte[] raw=intent.getByteArrayExtra("data");if(raw==null||raw.length==0||raw.length>MmsDownloadPolicy.MAX_NOTICE)return;
        GenericPdu parsed=new PduParser(raw,true).parse();if(!(parsed instanceof NotificationInd pdu))return;
        String transaction=MmsDownloadPolicy.transaction(pdu.getTransactionId());
        String sender=pdu.getFrom()==null?"":pdu.getFrom().getString();if(sender.length()>320||sender.isBlank())return;
        int supplied=intent.getIntExtra("subscription",intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,-1));
        int sub=MmsDownloadPolicy.subscription(supplied,active(c));
        boolean append=sub>=0&&c.getSystemService(SmsManager.class).createForSubscriptionId(sub).getCarrierConfigValues().getBoolean(SmsManager.MMS_CONFIG_APPEND_TRANSACTION_ID,false);
        String url=MmsDownloadPolicy.contentLocation(latin(pdu.getContentLocation()),transaction,append);
        pdu.setContentLocation(url.getBytes(StandardCharsets.ISO_8859_1));
        String key=MmsDownloadPolicy.fingerprint(url,transaction,sender,sub);JSONObject item;boolean priorNotice=false;
        synchronized(PilotApp.SEND_LOCK){
            requireAccess(c);item=db(c).one("fingerprint=?",key);if(item!=null)return;
            ContentValues v=values("pending","Waiting to download through your mobile carrier.");v.put("fingerprint",key);v.put("location",url);v.put("transaction_id",transaction);v.put("sender",sender);v.put("sub",sub);v.put("receipt_token",receipt);
            if(db(c).getWritableDatabase().insertWithOnConflict("downloads",null,v,SQLiteDatabase.CONFLICT_IGNORE)<0)return;
            // Reserve before touching the provider: repeated pushes cannot initiate another request.
            long existing=0;try(Cursor rows=c.getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id"},"m_type=130 AND msg_box=1 AND ct_l=? AND tr_id=? AND sub_id=?",new String[]{url,transaction,Integer.toString(sub)},"_id DESC")){if(rows==null)throw new IOException("The carrier notice could not be read.");if(rows.moveToFirst())existing=rows.getLong(0);}
            if(existing>0){item=adopt(c,existing);priorNotice=true;}
            else{
            Uri stored=PduPersister.getPduPersister(c).persist(pdu,Telephony.Mms.Inbox.CONTENT_URI,true,true,null,sub);
            long id=ContentUris.parseId(stored),date=System.currentTimeMillis()/1000;
            ContentValues stamp=new ContentValues();stamp.put("date",date);stamp.put("sub_id",sub);stamp.put("read",0);stamp.put("seen",0);
            if(c.getContentResolver().update(noticeUri(id),stamp,"m_type=?",new String[]{"130"})!=1)throw new IOException("The carrier notice could not be saved.");
            JSONObject row=provider(c,id);if(row==null)throw new IOException("The carrier notice could not be saved.");
            ContentValues binding=new ContentValues();binding.put("notice_id",id);binding.put("thread",row.optLong("thread_id"));binding.put("notice_date",row.optLong("date"));
            db(c).getWritableDatabase().update("downloads",binding,"fingerprint=?",new String[]{key});item=db(c).one("fingerprint=?",key);
            }
            bindReceipt(c,item);
            Store.get(c).pauseAll("A multimedia message arrived. Review queued replies before sending.");
        }
        Notices.refreshScheduled(c);MessageChanges.publish();
        if(priorNotice){announce(c,item,"Media download needs attention","Open the conversation to retry the earlier carrier download.");return;}
        if(!MmsDownloadPolicy.announcedSize(pdu.getMessageSize())){set(c,item,"unavailable","This carrier message exceeds the 10 MB download limit.");announce(c,item,"Media too large","This carrier message exceeds the 10 MB download limit.");return;}
        start(c,item,false);
    }
    private static void bindReceipt(Context c,JSONObject item){
        if(item==null||item.optLong("notice_id")<=0||item.optLong("thread")<=0)return;
        JSONObject notice=provider(c,item.optLong("notice_id"));
        if(bound(item,notice)||received(item,notice))ManualTakeover.bindMmsReceipt(c,item.optLong("thread"),item.optLong("notice_id"),item.optLong("receipt_token",-1));
    }
    private static JSONObject adopt(Context c,long id)throws Exception{
        JSONObject row=provider(c,id);if(row==null||row.optInt("m_type")!=130||row.optInt("msg_box")!=1)throw new IllegalStateException("This message has no downloadable carrier notice.");
        GenericPdu parsed=PduPersister.getPduPersister(c).load(noticeUri(id));
        if(!(parsed instanceof NotificationInd pdu))throw new IllegalStateException("This carrier notice is unavailable.");
        String url=MmsDownloadPolicy.location(row.optString("ct_l")),tx=MmsDownloadPolicy.transaction(pdu.getTransactionId());
        if(!url.equals(latin(pdu.getContentLocation()))||!tx.equals(row.optString("tr_id")))throw new IllegalStateException("The carrier notice changed.");
        String sender=pdu.getFrom()==null?"":pdu.getFrom().getString();int sub=row.optInt("sub_id",-1);String key=MmsDownloadPolicy.fingerprint(url,tx,sender,sub);
        ContentValues v=values("interrupted","This download needs your retry. Check mobile data and the receiving SIM.");v.put("fingerprint",key);v.put("location",url);v.put("transaction_id",tx);v.put("sender",sender);v.put("sub",sub);v.put("notice_id",id);v.put("thread",row.optLong("thread_id"));v.put("notice_date",row.optLong("date"));
        // Complete a notice binding interrupted between the provider and our private store.
        if(db(c).getWritableDatabase().update("downloads",v,"fingerprint=? AND notice_id=0",new String[]{key})==0)db(c).getWritableDatabase().insertWithOnConflict("downloads",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        JSONObject item=db(c).one("notice_id=?",Long.toString(id));if(item==null)throw new IllegalStateException("This carrier notice was already handled.");bindReceipt(c,item);return item;
    }
    static JSONObject retry(Context c,long id)throws Exception{
        requireAccess(c);recover(c);JSONObject item;
        synchronized(PilotApp.SEND_LOCK){item=db(c).one("notice_id=?",Long.toString(id));if(item==null)item=adopt(c,id);}
        if(repairable(c,item,provider(c,id)))repair(c,item);else start(c,item,true);return state(c,id);
    }
    private static void start(Context c,JSONObject item,boolean manual)throws Exception{
        String token=UUID.randomUUID().toString();
        try{
            synchronized(PilotApp.SEND_LOCK){
                requireAccess(c);item=db(c).one("fingerprint=?",item.optString("fingerprint"));
                JSONObject notice=provider(c,item.optLong("notice_id"));
                if(!MmsDownloadPolicy.retry(item.optString("state"),bound(item,notice),true,active(c).contains(item.optInt("sub",-1))))throw new IllegalStateException("This download is active, unavailable, or its receiving SIM needs attention.");
                if(!MmsDownloadPolicy.announcedSize(notice.optLong("m_size"))){set(c,item,"unavailable","This carrier message exceeds the 10 MB download limit.");throw new IllegalStateException("This carrier message exceeds the 10 MB download limit.");}
                cleanup(c,item.optString("token"),".pdu");cleanup(c,item.optString("token"),".ready");
                ContentValues v=values("downloading","Downloading through your mobile carrier…");v.put("token",token);v.put("started",System.currentTimeMillis());v.put("manual",manual?1:0);
                if(db(c).getWritableDatabase().update("downloads",v,"fingerprint=? AND state IN ('pending','failed','interrupted')",new String[]{item.optString("fingerprint")})!=1)return;
                item=db(c).one("token=?",token);
            }
            File target=file(c,token,".pdu");if(!target.createNewFile())throw new IOException("MMS storage is unavailable.");
            PendingIntent pi=PendingIntent.getBroadcast(c,0,callback(c,DOWNLOAD,token),PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE);
            requireAccess(c);
            c.getSystemService(SmsManager.class).createForSubscriptionId(item.optInt("sub")).downloadMultimediaMessage(c,item.getString("location"),local(c,target),null,pi);
            announce(c,item,"Downloading media",item.optString("note"));
        }catch(Exception error){
            if(item!=null){JSONObject current=db(c).one("fingerprint=?",item.optString("fingerprint"));if(current!=null&&(token.equals(current.optString("token"))||"pending".equals(current.optString("state")))){set(c,current,"failed",error instanceof SecurityException?"Allow SMS/MMS access, then retry.":"Download could not start. Check mobile data and the receiving SIM, then retry.");announce(c,current,"Media download needs attention","Open this conversation to retry the download.");}}
            cleanup(c,token,".pdu");if(manual)throw error;
        }
    }
    static void completed(Context c,Intent intent,int result)throws Exception{
        String token=token(intent);if(token==null)return;
        if(ACK.equals(intent.getAction())){ackComplete(c,token,result);return;}
        if(!DOWNLOAD.equals(intent.getAction()))return;
        JSONObject item;
        synchronized(PilotApp.SEND_LOCK){
            item=db(c).one("token=?",token);if(item==null||!MmsDownloadPolicy.callback(item.optString("token"),token,item.optString("state")))return;
            if(!access(c)){set(c,item,"failed","Restore SMS/MMS access, then retry.");cleanup(c,token,".pdu");return;}
            if(!bound(item,provider(c,item.optLong("notice_id")))){set(c,item,"unavailable","The original carrier notice changed or was removed.");cleanup(c,token,".pdu");return;}
            if(db(c).getWritableDatabase().update("downloads",values("processing","Saving downloaded media…"),"token=? AND state IN ('downloading','interrupted')",new String[]{token})!=1)return;
            PROCESSING.add(token);
        }
        try{
            if(result!=Activity.RESULT_OK)throw new IOException(failure(result));
            File downloaded=file(c,token,".pdu");if(!MmsDownloadPolicy.pduSize(downloaded.length()))throw new IOException("The carrier returned an empty or oversized media message.");
            byte[] bytes=read(downloaded);RetrieveConf pdu=parse(item,bytes);
            // Move a verified copy outside the carrier-granted URI before any provider mutation.
            File ready=file(c,token,".ready");try(FileOutputStream out=new FileOutputStream(ready)){out.write(bytes);out.getFD().sync();}
            synchronized(PilotApp.SEND_LOCK){
                ContentValues expected=new ContentValues();expected.put("expected_parts",pdu.getBody().getPartsNum());expected.put("retrieve_id",latin(pdu.getMessageId()));expected.put("ready_hash",MmsDownloadPolicy.digest(bytes));
                if(db(c).getWritableDatabase().update("downloads",expected,"token=? AND state='processing'",new String[]{token})!=1)throw new IOException("The original carrier notice changed.");item=db(c).one("token=?",token);
            }
            item=persistDownloaded(c,item,pdu);
            // Only a newly received, fully persisted carrier message may start Autopilot.
            // Recovery, imported history and owner-requested retries never create replies.
            if(item.optLong("receipt_token")>0&&item.optInt("manual")==0){
                try{AutopilotMms.onDownloaded(c,item.optLong("thread"),item.optLong("notice_id"),item.optLong("receipt_token"));}
                catch(Exception unavailable){/* AI scheduling must not fail a completed carrier download. */}
            }
            announce(c,item,"Media message received","Open the conversation to view the message and attachments.");acknowledge(c,item,pdu);
        }catch(Exception error){synchronized(PilotApp.SEND_LOCK){set(c,item,"failed",safeFailure(error));}announce(c,item,"Media download failed",safeFailure(error)+" Open the conversation to retry when available.");}
        finally{synchronized(PilotApp.SEND_LOCK){PROCESSING.remove(token);}cleanup(c,token,".pdu");JSONObject latest=db(c).one("token=?",token);if(latest!=null&&"downloaded".equals(latest.optString("state")))cleanup(c,token,".ready");MessageChanges.publish();}
    }
    private static byte[] read(File file)throws IOException{
        if(!MmsDownloadPolicy.pduSize(file.length()))throw new IOException("The carrier returned an empty or oversized media message.");
        try(InputStream stream=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1){if(out.size()+count>MmsDownloadPolicy.MAX_PDU)throw new IOException("The media message is too large.");out.write(buffer,0,count);}return out.toByteArray();
        }
    }
    private static RetrieveConf parse(JSONObject item,byte[] bytes)throws IOException{
        GenericPdu parsed=new PduParser(bytes,true).parse();if(!(parsed instanceof RetrieveConf pdu))throw new IOException("The carrier returned an invalid media message.");
        int parts=pdu.getBody()==null?0:pdu.getBody().getPartsNum();if(!MmsDownloadPolicy.complete(pdu.getRetrieveStatus(),parts))throw new IOException("The carrier could not retrieve this media. It may have expired.");
        String sender=pdu.getFrom()==null?"":pdu.getFrom().getString();if(!sameSender(item.optString("sender"),sender))throw new IOException("The downloaded message did not match its carrier notice.");
        byte[] id=pdu.getMessageId();if(id==null||id.length==0||id.length>1024)throw new IOException("The carrier returned a message without a valid reference.");return pdu;
    }
    private static JSONObject persistDownloaded(Context c,JSONObject item,RetrieveConf pdu)throws Exception{
        synchronized(PilotApp.SEND_LOCK){
            requireAccess(c);if(!repairable(c,item,provider(c,item.optLong("notice_id"))))throw new IOException("The original carrier notice changed.");
            Uri uri=noticeUri(item.optLong("notice_id"));
            // This updates only the exact notice/partial message, with the verified local PDU.
            c.getContentResolver().delete(Uri.withAppendedPath(uri,"part"),null,null);
            c.getContentResolver().delete(Uri.withAppendedPath(uri,"addr"),null,null);
            pdu.setDate(item.optLong("notice_date"));
            PduPersister.getPduPersister(c).persist(pdu,uri,true,true,null,item.optInt("sub"));
            JSONObject stored=provider(c,item.optLong("notice_id"));
            if(stored==null||stored.optInt("m_type")!=132||partCount(c,item.optLong("notice_id"))!=pdu.getBody().getPartsNum()||!latin(pdu.getMessageId()).equals(stored.optString("m_id")))throw new IOException("Media saving was interrupted. Tap retry to finish saving the local download.");
            ContentValues v=values("downloaded","Media downloaded.");v.put("thread",stored.optLong("thread_id"));
            if(db(c).getWritableDatabase().update("downloads",v,"token=? AND state='processing'",new String[]{item.optString("token")})!=1)throw new IOException("Media saving was interrupted. Tap retry to finish saving the local download.");return db(c).one("token=?",item.optString("token"));
        }
    }
    private static void repair(Context c,JSONObject item)throws Exception{
        String token=item.optString("token");
        synchronized(PilotApp.SEND_LOCK){
            requireAccess(c);if(!repairable(c,item,provider(c,item.optLong("notice_id")))||db(c).getWritableDatabase().update("downloads",values("processing","Finishing the saved download…"),"token=? AND state IN ('failed','interrupted')",new String[]{token})!=1)throw new IllegalStateException("This download cannot be retried yet.");PROCESSING.add(token);
        }
        try{
            byte[] bytes=read(file(c,token,".ready"));if(!MmsDownloadPolicy.digest(bytes).equals(item.optString("ready_hash")))throw new IOException("The downloaded message changed. It cannot be restored.");
            RetrieveConf pdu=parse(item,bytes);if(!latin(pdu.getMessageId()).equals(item.optString("retrieve_id")))throw new IOException("The downloaded message did not match its saved reference.");
            item=persistDownloaded(c,item,pdu);announce(c,item,"Media message received","Open the conversation to view the message and attachments.");acknowledge(c,item,pdu);cleanup(c,token,".ready");
        }catch(Exception error){set(c,item,"failed",safeFailure(error));throw error;}
        finally{synchronized(PilotApp.SEND_LOCK){PROCESSING.remove(token);}MessageChanges.publish();}
    }
    private static String safeFailure(Exception error){String message=error.getMessage();return error instanceof IOException&&message!=null&&(message.startsWith("The carrier ")||message.startsWith("Mobile data ")||message.startsWith("The receiving SIM ")||message.startsWith("Carrier MMS ")||message.startsWith("Your carrier ")||message.startsWith("The media message ")||message.startsWith("The downloaded message ")||message.startsWith("The original carrier ")||message.startsWith("Media saving "))?message:"Media could not be saved. Check SMS/MMS access and available storage.";}
    private static boolean sameSender(String a,String b){return !a.isBlank()&&!b.isBlank()&&(a.equalsIgnoreCase(b)||PhoneNumberUtils.compare(a,b));}
    private static int partCount(Context c,long id){try(Cursor rows=c.getContentResolver().query(Uri.withAppendedPath(noticeUri(id),"part"),new String[]{"_id"},null,null,null)){return rows==null?-1:rows.getCount();}}
    static String token(Intent intent){if(intent==null||intent.getData()==null)return null;Uri data=intent.getData();List<String> path=data.getPathSegments();return "replypilot".equals(data.getScheme())&&"mms-download".equals(data.getAuthority())&&data.getQuery()==null&&data.getFragment()==null&&path.size()==1&&MmsDownloadPolicy.token(path.get(0))?path.get(0):null;}
    private static String failure(int result){return switch(result){
        case SmsManager.MMS_ERROR_NO_DATA_NETWORK,SmsManager.MMS_ERROR_DATA_DISABLED -> "Mobile data is unavailable. Turn it on and retry.";
        case SmsManager.MMS_ERROR_INVALID_SUBSCRIPTION_ID,SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION -> "The receiving SIM is unavailable. Restore that SIM and retry.";
        case SmsManager.MMS_ERROR_INVALID_APN,SmsManager.MMS_ERROR_CONFIGURATION_ERROR -> "Carrier MMS settings are unavailable. Check your SIM/APN settings and retry.";
        case SmsManager.MMS_ERROR_MMS_DISABLED_BY_CARRIER -> "Your carrier has disabled MMS. Check your plan with the carrier.";
        default -> "The carrier download failed. Check mobile data and retry.";
    };}
    static JSONObject state(Context c,long id){
        if(id<=0)throw new IllegalArgumentException("Invalid media message.");
        try{
            if(!access(c))return publicState(id,0,"unavailable","Allow SMS/MMS access to download this message.",false);
            JSONObject row=provider(c,id),item=db(c).one("notice_id=?",Long.toString(id));
            if(row==null)return publicState(id,0,"unavailable","This message was removed.",false);
            if(row.optInt("m_type")!=130&&item==null)return publicState(id,row.optLong("thread_id"),"downloaded","",false);
            if(item==null)return publicState(id,row.optLong("thread_id"),"interrupted","Tap retry to download this carrier message.",row.optInt("m_type")==130&&row.optInt("msg_box")==1&&MmsDownloadPolicy.announcedSize(row.optLong("m_size"))&&active(c).contains(row.optInt("sub_id",-1)));
            String status=item.optString("state"),note=item.optString("note");
            if(!bound(item,row)&&!received(item,row)&&!repairable(c,item,row))return publicState(id,row.optLong("thread_id"),"unavailable","The original carrier message changed or was removed.",false);
            if("processing".equals(status))status="downloading";
            if("downloading".equals(status)&&MmsDownloadPolicy.timedOut(item.optLong("started"),System.currentTimeMillis())){status="interrupted";note="The download did not finish. Check mobile data and tap retry.";}
            boolean retry=MmsDownloadPolicy.retry(status,bound(item,row),true,active(c).contains(item.optInt("sub",-1)))||(List.of("failed","interrupted").contains(status)&&repairable(c,item,row));
            return publicState(id,row.optLong("thread_id"),status,note,retry);
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    private static JSONObject publicState(long id,long thread,String status,String note,boolean retry)throws JSONException{return new JSONObject().put("mediaId",id).put("thread",thread).put("status",status).put("note",note).put("retryAllowed",retry);}
    static void annotate(Context c,JSONArray rows){if(rows==null)return;for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&(!row.has("kind")||"mms".equals(row.optString("kind")))&&(row.optInt("m_type")==130||row.optInt("m_type")==132))try{row.put("download",state(c,row.optLong("_id")));}catch(Exception ignored){}}}
    static void recover(Context c){
        if(!access(c))return;boolean changed=false;
        synchronized(PilotApp.SEND_LOCK){
            List<JSONObject> rows=new ArrayList<>();try(Cursor cursor=db(c).getReadableDatabase().query("downloads",null,"notice_id=0 OR state IN ('pending','downloading','processing') OR ack_state='sending' OR ready_hash<>''",null,null,null,"updated ASC")){while(cursor.moveToNext())rows.add(Store.json(cursor));}
            for(JSONObject row:rows){
                bindReceipt(c,row);
                String token=row.optString("token"),status=row.optString("state");if(PROCESSING.contains(token))continue;
                if(row.optLong("notice_id")==0){
                    // No carrier operation is launched before a provider ID is bound. A confirmed
                    // absent notice permits a future push to try saving again; unreadable is unknown.
                    try(Cursor matches=c.getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id","m_type"},"msg_box=1 AND ct_l=? AND COALESCE(sub_id,-1)=?",new String[]{row.optString("location"),Integer.toString(row.optInt("sub",-1))},"_id DESC")){
                        if(matches!=null&&!matches.moveToFirst()){db(c).getWritableDatabase().delete("downloads","fingerprint=? AND notice_id=0",new String[]{row.optString("fingerprint")});changed=true;continue;}
                        if(matches!=null&&matches.getInt(1)==130){try{adopt(c,matches.getLong(0));changed=true;continue;}catch(Exception unbound){/* Retain the reservation if exact adoption is uncertain. */}}
                    }catch(RuntimeException unavailable){/* Do not release a reservation on provider failure. */}
                }
                if("processing".equals(status)||("pending".equals(status)&&row.optLong("notice_id")==0)||("downloading".equals(status)&&MmsDownloadPolicy.timedOut(row.optLong("started"),System.currentTimeMillis()))){set(c,row,"interrupted","The download was interrupted. Review this message and retry when available.");cleanup(c,token,".pdu");changed=true;}
                if(!row.optString("ready_hash").isEmpty()&&!"processing".equals(status)){
                    JSONObject saved=provider(c,row.optLong("notice_id"));
                    if("downloaded".equals(status)||saved==null||(!bound(row,saved)&&!received(row,saved)&&!repairable(c,row,saved))){
                        cleanup(c,token,".ready");ContentValues clear=new ContentValues();clear.put("ready_hash","");db(c).getWritableDatabase().update("downloads",clear,"fingerprint=?",new String[]{row.optString("fingerprint")});
                    }
                }
                if("sending".equals(row.optString("ack_state"))&&MmsDownloadPolicy.timedOut(row.optLong("updated"),System.currentTimeMillis())){ContentValues v=new ContentValues();v.put("ack_state","unknown");db(c).getWritableDatabase().update("downloads",v,"fingerprint=?",new String[]{row.optString("fingerprint")});cleanup(c,row.optString("ack_token"),".ack");}
            }
        }
        if(changed)MessageChanges.publish();
    }
    /** Carrier protocol acknowledgment only, without an outgoing chat row or automatic retry. */
    private static void acknowledge(Context c,JSONObject item,RetrieveConf retrieved){
        String token=UUID.randomUUID().toString();try{
            if(!access(c)||!Messages.allowed(c,Manifest.permission.SEND_SMS)||!active(c).contains(item.optInt("sub",-1)))return;
            GenericPdu response;
            if(item.optInt("manual")==1){byte[] transaction=retrieved.getTransactionId();MmsDownloadPolicy.transaction(transaction);AcknowledgeInd ack=new AcknowledgeInd(PduHeaders.CURRENT_MMS_VERSION,transaction);ack.setReportAllowed(PduHeaders.VALUE_NO);response=ack;}
            else{NotifyRespInd ack=new NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION,item.getString("transaction_id").getBytes(StandardCharsets.ISO_8859_1),PduHeaders.STATUS_RETRIEVED);ack.setReportAllowed(PduHeaders.VALUE_NO);response=ack;}
            byte[] bytes=new PduComposer(c,response).make();if(bytes==null||bytes.length==0||bytes.length>4096)return;
            File file=file(c,token,".ack");try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}
            synchronized(PilotApp.SEND_LOCK){ContentValues v=new ContentValues();v.put("ack_token",token);v.put("ack_state","sending");v.put("updated",System.currentTimeMillis());if(db(c).getWritableDatabase().update("downloads",v,"fingerprint=? AND state='downloaded' AND ack_state=''",new String[]{item.optString("fingerprint")})!=1){cleanup(c,token,".ack");return;}}
            if(!access(c)||!Messages.allowed(c,Manifest.permission.SEND_SMS)||!active(c).contains(item.optInt("sub",-1))){ackComplete(c,token,0);return;}
            PendingIntent pi=PendingIntent.getBroadcast(c,0,callback(c,ACK,token),PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE);
            SmsManager manager=c.getSystemService(SmsManager.class).createForSubscriptionId(item.optInt("sub"));
            boolean notifyAtLocation=manager.getCarrierConfigValues().getBoolean(SmsManager.MMS_CONFIG_NOTIFY_WAP_MMSC_ENABLED,false);
            String destination=MmsDownloadPolicy.acknowledgmentLocation(item.getString("location"),notifyAtLocation);
            manager.sendMultimediaMessage(c,local(c,file),destination,null,pi);
        }catch(Exception ignored){ackComplete(c,token,0);}
    }
    private static void ackComplete(Context c,String token,int result){ContentValues v=new ContentValues();v.put("ack_state",result==Activity.RESULT_OK?"sent":"failed");db(c).getWritableDatabase().update("downloads",v,"ack_token=? AND ack_state='sending'",new String[]{token});cleanup(c,token,".ack");}
}
