package com.contentfoundry.replypilot;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.PhoneNumberUtils;
import androidx.core.content.FileProvider;
import com.google.android.mms.pdu_alt.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Manual, local-only MMS staging. AI never receives these outgoing attachments. */
public final class MmsAttachments {
    public static final ExecutorService EXECUTOR=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"reply-pilot-mms-outgoing");t.setDaemon(true);return t;});
    private static final Set<String> LIVE=ConcurrentHashMap.newKeySet();
    private record Limits(boolean enabled,int bytes,int width,int height,boolean audio){}
    private record Prepared(SendReq pdu,byte[] bytes){}
    private MmsAttachments(){}
    public static boolean hasSentForBase(Context c,long thread,long base){return thread>0&&base>=0&&MmsOutbox.get(c).query("SELECT id FROM sends WHERE thread=? AND base=? AND status='sent' LIMIT 1",Long.toString(thread),Long.toString(base)).length()>0;}
    public static boolean hasStaged(Context c,long thread){return MmsOutbox.get(c).query("SELECT id FROM attachments WHERE thread=? LIMIT 1",Long.toString(thread)).length()>0;}
    public static JSONObject state(Context c,long thread)throws JSONException{
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        MmsOutbox db=MmsOutbox.get(c);JSONArray items=new JSONArray();boolean sending=false,blocked=false;
        JSONArray rows=db.query("SELECT * FROM attachments WHERE thread=? ORDER BY created,id",Long.toString(thread));
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i),job=row.optString("job").isEmpty()?null:db.job(row.optString("job"));String status=job==null?"":job.optString("status");
            boolean active="preparing".equals(status)||"sending".equals(status),unknown="unknown".equals(status);sending|=active;blocked|=unknown;
            JSONObject item=new JSONObject().put("id",row.optString("id")).put("name",row.optString("name")).put("mime",row.optString("mime")).put("bytes",row.optLong("bytes")).put("locked",active||unknown);
            if(MmsSendPolicy.preview(row.optString("mime")))item.put("previewUrl","/attachment/"+row.optString("id"));items.put(item);
        }
        boolean enabled=false;int max=0;String note="Choose an active SIM and allow messaging to send attachments.";
        try{int sub=selected(c);Limits limit=limits(c,sub);max=limit.bytes();enabled=limit.enabled()&&Messages.role(c)&&Messages.allowed(c,Manifest.permission.SEND_SMS)&&Messages.allowed(c,Manifest.permission.READ_SMS);note=enabled?"Attachments send now as carrier MMS. Mobile data may be required.":"Your carrier or messaging permissions currently prevent MMS.";}catch(Exception ignored){}
        JSONObject last=db.query("SELECT * FROM sends WHERE thread=? ORDER BY created DESC LIMIT 1",Long.toString(thread)).optJSONObject(0);
        JSONObject out=new JSONObject().put("items",items).put("sending",sending).put("blocked",blocked).put("maxBytes",max).put("maxItems",MmsSendPolicy.MAX_ITEMS).put("enabled",enabled).put("note",blocked?"The last send is unconfirmed. Check with the recipient before discarding or sending again.":note);
        if(last!=null)out.put("lastSend",result(last).put("caption",last.optString("caption")).put("base",last.optLong("base")));return out;
    }
    private record SharedAsset(String id,String name,String mime,int bytes,String hash){}
    static record Added(List<String> ids,JSONObject state){}
    public static JSONObject add(Context context,long thread,Uri uri)throws Exception{
        if(uri==null)throw new IllegalArgumentException("Choose an attachment from Android’s file picker.");return add(context,thread,List.of(uri),()->{}).state();
    }
    /** Validate/copy every shared file before publishing any staged attachment. */
    static Added add(Context context,long thread,List<Uri> uris,Runnable guard)throws Exception{
        Context c=context.getApplicationContext();require(c);if(thread<=0||uris==null||uris.isEmpty()||uris.size()>MmsSendPolicy.MAX_ITEMS)throw new IllegalArgumentException("Choose up to six attachments.");
        List<SharedAsset> prepared=new ArrayList<>();boolean inserted=false;
        try{
            synchronized(PilotApp.SEND_LOCK){guard.run();editable(c,thread);if(MmsOutbox.get(c).query("SELECT id FROM attachments WHERE thread=?",Long.toString(thread)).length()+uris.size()>MmsSendPolicy.MAX_ITEMS)throw new IllegalArgumentException("Attach up to six files at a time.");}
            long preparedBytes=0;
            for(Uri uri:uris){guard.run();SharedAsset part=prepareAttachment(c,uri);prepared.add(part);preparedBytes+=part.bytes();if(preparedBytes>MmsSendPolicy.MAX_STAGED)throw new IllegalArgumentException("Choose fewer or smaller attachments.");}
            synchronized(PilotApp.SEND_LOCK){
                guard.run();require(c);editable(c,thread);MmsOutbox db=MmsOutbox.get(c);JSONArray existing=db.query("SELECT bytes FROM attachments WHERE thread=?",Long.toString(thread));long total=preparedBytes;
                for(int i=0;i<existing.length();i++)total+=existing.optJSONObject(i).optLong("bytes");if(existing.length()+prepared.size()>MmsSendPolicy.MAX_ITEMS||total>MmsSendPolicy.MAX_STAGED)throw new IllegalArgumentException("Remove an attachment before adding more.");
                SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{
                    for(SharedAsset part:prepared){ContentValues row=new ContentValues();row.put("id",part.id());row.put("thread",thread);row.put("name",part.name());row.put("mime",part.mime());row.put("bytes",part.bytes());row.put("hash",part.hash());row.put("created",System.currentTimeMillis());sql.insertOrThrow("attachments",null,row);}
                    guard.run();sql.setTransactionSuccessful();
                }finally{sql.endTransaction();}
                inserted=true;invalidate(c,thread);MessageChanges.publish();return new Added(prepared.stream().map(SharedAsset::id).toList(),state(c,thread));
            }
        }catch(Exception failed){if(inserted)discardShared(c,thread,prepared.stream().map(SharedAsset::id).toList());throw failed;}finally{if(!inserted)for(SharedAsset part:prepared)asset(c,part.id()).delete();}
    }
    private static SharedAsset prepareAttachment(Context c,Uri uri)throws Exception{
        if(uri==null||!"content".equals(uri.getScheme()))throw new IllegalArgumentException("Choose an attachment from Android's file picker.");
        String name="Attachment";try(Cursor metadata=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(metadata!=null&&metadata.moveToFirst()){name=metadata.getString(0);if(!metadata.isNull(1)&&metadata.getLong(1)>MmsSendPolicy.MAX_SOURCE)throw new IllegalArgumentException("Choose a file smaller than 20 MB. Carrier MMS limits are usually much smaller.");}}
        byte[] bytes;try(InputStream input=c.getContentResolver().openInputStream(uri)){bytes=read(input,MmsSendPolicy.MAX_SOURCE);}
        String mime=MmsSendPolicy.mime(bytes,c.getContentResolver().getType(uri));
        if("image/gif".equals(mime)){BitmapFactory.Options info=new BitmapFactory.Options();info.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,info);if(info.outWidth<=0||info.outHeight<=0||info.outWidth>2048||info.outHeight>2048||(long)info.outWidth*info.outHeight>4_000_000)throw new IllegalArgumentException("Choose a GIF no larger than 2,048 pixels per edge.");}
        if("image/jpeg".equals(mime)||"image/png".equals(mime)){bytes=photo(bytes,1280,1280,1024*1024);mime="image/jpeg";name=stripExtension(name)+".jpg";}
        String id=UUID.randomUUID().toString();File file=asset(c,id);try{write(file,bytes);return new SharedAsset(id,MmsSendPolicy.name(name,mime),mime,bytes.length,hash(bytes));}catch(Exception failed){file.delete();throw failed;}
    }
    static void discardShared(Context c,long thread,List<String> ids){
        synchronized(PilotApp.SEND_LOCK){MmsOutbox db=MmsOutbox.get(c);for(String id:ids){JSONObject row=db.attachment(id);if(row!=null&&row.optLong("thread")==thread&&row.optString("job").isEmpty()){db.getWritableDatabase().delete("attachments","id=? AND thread=? AND job=''",new String[]{id,Long.toString(thread)});asset(c,id).delete();}}MessageChanges.publish();}
    }
    public static JSONObject remove(Context c,long thread,String id)throws JSONException{
        if(thread<=0||!MmsSendPolicy.id(id))throw new IllegalArgumentException("Choose an attachment to remove.");
        synchronized(PilotApp.SEND_LOCK){MmsOutbox db=MmsOutbox.get(c);JSONObject item=db.attachment(id);if(item!=null&&item.optLong("thread")==thread){
            JSONObject job=item.optString("job").isEmpty()?null:db.job(item.optString("job"));if(job!=null&&Set.of("preparing","sending").contains(job.optString("status")))throw new IllegalStateException("Wait for the carrier result before removing this attachment.");
            db.getWritableDatabase().delete("attachments","id=? AND thread=?",new String[]{id,Long.toString(thread)});asset(c,id).delete();invalidate(c,thread);MessageChanges.publish();
        }return state(c,thread);}
    }
    public static InputStream openPreview(Context c,String id)throws IOException{
        if(!canPreview(c)||!MmsSendPolicy.id(id))return null;JSONObject item=MmsOutbox.get(c).attachment(id);if(item==null||!MmsSendPolicy.preview(item.optString("mime")))return null;File file=asset(c,id);return file.isFile()&&file.length()<=MmsSendPolicy.MAX_SOURCE?new FileInputStream(file):null;
    }
    public static String previewMime(Context c,String id){JSONObject item=canPreview(c)&&MmsSendPolicy.id(id)?MmsOutbox.get(c).attachment(id):null;return item!=null&&MmsSendPolicy.preview(item.optString("mime"))?item.optString("mime"):"application/octet-stream";}
    public static JSONObject send(Context context,long thread,String address,String caption,int sub,long base,String requestId)throws Exception{
        Context c=context.getApplicationContext();if(!MmsSendPolicy.id(requestId))throw new IllegalArgumentException("Start a new attachment send from the conversation.");MmsSendPolicy.caption(caption);
        final JSONObject job,savedDraft;final JSONArray attachments;final Limits carrier;String id;
        synchronized(PilotApp.SEND_LOCK){
            MmsOutbox db=MmsOutbox.get(c);JSONObject prior=db.request(requestId);if(prior!=null){if(prior.optLong("thread")!=thread||prior.optLong("base")!=base||prior.optInt("sub")!=sub||!prior.optString("address").equals(address)||!prior.optString("caption").equals(caption))throw new IllegalStateException("This send request was already used with different message details. Start a new send.");return result(prior);}
            require(c);editable(c,thread);SendPolicy.validateConversation(thread,base);if(!SendPolicy.validAddress(address))throw new IllegalArgumentException("Choose a valid SMS recipient.");
            if(!PhoneNumberUtils.compare(Sender.singleRecipient(c,thread),address)||Messages.thread(c,address)!=thread)throw new IllegalStateException("The recipient does not match this conversation.");
            // Taking over survives carrier/preparation failure. Keep any generated
            // location binding before takeover removes the old AI draft.
            savedDraft=Store.get(c).draft(thread,base);
            ManualTakeover.claim(c,thread,address);
            if(!Messages.activeSim(c,sub))throw new IllegalStateException("Choose an active SIM in Settings first.");carrier=limits(c,sub);if(!carrier.enabled())throw new IllegalStateException("MMS is disabled by this carrier.");
            if(Messages.latest(c,thread)!=base||MmsDownloads.isPending()||IncomingBurst.hasUnbound(c))throw new IllegalStateException("New messages arrived. Review the conversation before sending.");
            attachments=db.query("SELECT * FROM attachments WHERE thread=? ORDER BY created,id",Long.toString(thread));if(attachments.length()==0||attachments.length()>MmsSendPolicy.MAX_ITEMS)throw new IllegalStateException("Add an attachment before sending.");
            StringBuilder signature=new StringBuilder();for(int i=0;i<attachments.length();i++){JSONObject item=attachments.optJSONObject(i);if(!item.optString("job").isEmpty())throw new IllegalStateException("The previous attachment send is not confirmed. Check it before trying again.");signature.append(item.optString("hash")).append(':').append(item.optString("mime")).append(';');}
            String fingerprint=MediaContextPolicy.signature(Long.toString(thread),address,caption,Long.toString(base),signature.toString());
            if(db.query("SELECT id FROM sends WHERE thread=? AND fingerprint=? AND status IN ('sending','sent','unknown')",Long.toString(thread),fingerprint).length()>0)throw new IllegalStateException("This attachment message was already submitted. Check the conversation before sending it again.");
            if(savedDraft!=null&&caption.equals(savedDraft.optString("body"))){String gate=LocationReplies.block(c,savedDraft,System.currentTimeMillis());if(gate!=null)throw new IllegalStateException(gate);}
            id=UUID.randomUUID().toString();long created=System.currentTimeMillis();ContentValues row=new ContentValues();row.put("id",id);row.put("request_id",requestId);row.put("thread",thread);row.put("address",address);row.put("caption",caption);row.put("sub",sub);row.put("base",base);row.put("status","preparing");row.put("fingerprint",fingerprint);row.put("attachments",attachments.toString());row.put("created",created);row.put("transaction_id","rp"+id.replace("-",""));row.put("mms_before",mmsHead(c,thread,0));
            SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();try{sql.insertOrThrow("sends",null,row);ContentValues lock=new ContentValues();lock.put("job",id);if(sql.update("attachments",lock,"thread=? AND job=''",new String[]{Long.toString(thread)})!=attachments.length())throw new IllegalStateException("The attachments changed. Try again.");sql.setTransactionSuccessful();}finally{sql.endTransaction();}
            LIVE.add(id);MessageChanges.publish();job=db.job(id);
        }
        boolean submitting=false;
        try{
            Prepared prepared=prepare(c,job,attachments,carrier);File file=pdu(c,id);write(file,prepared.bytes());
            synchronized(PilotApp.SEND_LOCK){
                MmsOutbox db=MmsOutbox.get(c);String gate=boundary(c,job,0,savedDraft);if(gate!=null)throw new IllegalStateException(gate);
                Limits current=limits(c,sub);if(!current.enabled()||prepared.bytes().length>current.bytes())throw new IllegalStateException("Carrier MMS settings changed. Check the attachments and try again.");
                // The durable job exists before provider persistence. This outbox
                // row and unique transaction ID bind every eventual callback.
                Uri stored=PduPersister.getPduPersister(c).persist(prepared.pdu(),Telephony.Mms.Outbox.CONTENT_URI,true,false,null,sub);if(stored==null)throw new IllegalStateException("The outgoing MMS could not be saved.");
                long providerId=ContentUris.parseId(stored);ContentValues saved=new ContentValues();saved.put("provider_id",providerId);saved.put("provider_date",prepared.pdu().getDate());saved.put("pdu_bytes",prepared.bytes().length);db.getWritableDatabase().update("sends",saved,"id=?",new String[]{id});
                JSONObject persisted=db.job(id);if(!providerMatches(c,persisted))throw new IllegalStateException("The outgoing MMS recipient could not be confirmed.");
                gate=boundary(c,persisted,providerId,savedDraft);if(gate!=null)throw new IllegalStateException(gate);
                Uri content=FileProvider.getUriForFile(c,c.getPackageName()+".files",file);
                PendingIntent callback=PendingIntent.getBroadcast(c,0,new Intent(c,MmsSentReceiver.class).setAction(MmsSentReceiver.ACTION).setData(Uri.parse("replypilot://mms-sent/"+id)),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
                gate=boundary(c,persisted,providerId,savedDraft);if(gate!=null)throw new IllegalStateException(gate);
                db.status(id,"sending","Waiting for the carrier. Do not send again while the outcome is unknown.");submitting=true;
                try{c.getSystemService(SmsManager.class).createForSubscriptionId(sub).sendMultimediaMessage(c,content,null,null,callback);}
                catch(SecurityException revoked){throw new IllegalStateException("Messaging permission changed during submission.",revoked);}
                MessageChanges.publish();return result(db.job(id));
            }
        }catch(Exception failed){synchronized(PilotApp.SEND_LOCK){
            MmsOutbox db=MmsOutbox.get(c);String status=submitting?"unknown":"failed";String note=submitting?"The send outcome is unknown. Check with the recipient; this message will not retry automatically.":(failed instanceof IllegalStateException||failed instanceof IllegalArgumentException?failed.getMessage():"The attachment message could not be prepared. Check the files and carrier settings.");
            db.status(id,status,note==null?"The attachment message could not be sent.":note);if(!submitting){settle(c,db,db.job(id));providerState(c,db.job(id),false);LIVE.remove(id);}MessageChanges.publish();return result(db.job(id));
        }}
    }
    private static void require(Context c){
        if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS)||!Messages.allowed(c,Manifest.permission.SEND_SMS))throw new IllegalStateException("Make Reply Pilot the default texting app and allow SMS before sending attachments.");
    }
    private static boolean canPreview(Context c){return Messages.role(c)&&Messages.allowed(c,Manifest.permission.READ_SMS);}
    private static int selected(Context c){int sub=c.getSharedPreferences("settings",0).getInt("sub",-1);JSONArray sims=Messages.sims(c);if(sub<0&&sims.length()==1)sub=sims.optJSONObject(0).optInt("id",-1);if(!Messages.activeSim(c,sub))throw new IllegalStateException("Choose an active SIM in Settings first.");return sub;}
    private static Limits limits(Context c,int sub){
        SmsManager service=c.getSystemService(SmsManager.class);if(service==null)throw new IllegalStateException("Carrier messaging is unavailable.");Bundle config=service.createForSubscriptionId(sub).getCarrierConfigValues();
        if(config==null)throw new IllegalStateException("Carrier MMS settings are unavailable.");
        int bytes=MmsSendPolicy.carrierBytes(config.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE,300*1024));
        int width=Math.max(1,Math.min(1280,config.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH,1280))),height=Math.max(1,Math.min(1280,config.getInt(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT,1280)));
        return new Limits(config.getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED,false),bytes,width,height,config.getBoolean(SmsManager.MMS_CONFIG_ALLOW_ATTACH_AUDIO,true));
    }
    private static void editable(Context c,long thread){
        if(MmsOutbox.get(c).query("SELECT id FROM sends WHERE thread=? AND status IN ('preparing','sending') LIMIT 1",Long.toString(thread)).length()>0)throw new IllegalStateException("Wait for the current attachment send to finish.");
        if(MmsOutbox.get(c).query("SELECT a.id FROM attachments a JOIN sends s ON a.job=s.id WHERE a.thread=? AND s.status='unknown' LIMIT 1",Long.toString(thread)).length()>0)throw new IllegalStateException("The previous send is unconfirmed. Check with the recipient before discarding its attachments.");
    }
    private static void invalidate(Context c,long thread){Store.get(c).invalidateGeneration();Sender.cancelAutomaticForThread(c,thread,"You are preparing an attachment. Review any automatic reply again afterward.");}
    private static File local(Context c,String directory,String id,String suffix){if(!MmsSendPolicy.id(id))throw new IllegalArgumentException("Invalid attachment identifier.");File dir=new File(c.getFilesDir(),"mms-outgoing/"+directory);if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Private attachment storage is unavailable.");return new File(dir,id+suffix);}
    private static File asset(Context c,String id){return local(c,"staged",id,".bin");}
    private static File pdu(Context c,String id){return local(c,"pdus",id,".pdu");}
    private static byte[] read(InputStream input,int max)throws IOException{
        if(input==null)throw new IOException("The selected file could not be opened.");ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(max,65536));byte[] buffer=new byte[8192];int n;
        while((n=input.read(buffer))!=-1){if(n==0)continue;if((long)out.size()+n>max)throw new IllegalArgumentException("This attachment exceeds the allowed size.");out.write(buffer,0,n);}if(out.size()==0)throw new IllegalArgumentException("The selected file is empty.");return out.toByteArray();
    }
    private static void write(File file,byte[] data)throws IOException{File temporary=new File(file.getParentFile(),file.getName()+".tmp");try{try(FileOutputStream output=new FileOutputStream(temporary)){output.write(data);output.getFD().sync();}if(!temporary.renameTo(file))throw new IOException("The attachment could not be saved privately.");}finally{temporary.delete();}}
    private static String hash(byte[] bytes){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder out=new StringBuilder();for(byte b:digest)out.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));return out.toString();}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static String stripExtension(String name){if(name==null)return "Photo";int dot=name.lastIndexOf('.');return dot>0?name.substring(0,dot):name;}
    /** ImageDecoder honors EXIF orientation before creating a metadata-free JPEG. */
    private static byte[] photo(byte[] source,int maxWidth,int maxHeight,int budget)throws IOException{
        if(budget<4096)throw new IllegalArgumentException("These attachments do not fit this carrier's MMS limit. Remove a file or choose a smaller one.");
        Bitmap decoded=ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(source)),(decoder,info,input)->{
            int width=info.getSize().getWidth(),height=info.getSize().getHeight();MediaContextPolicy.scaled(width,height);
            double ratio=Math.min(1d,Math.min((double)maxWidth/width,(double)maxHeight/height));decoder.setTargetSize(Math.max(1,(int)Math.floor(width*ratio)),Math.max(1,(int)Math.floor(height*ratio)));
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setMutableRequired(false);
        });
        Bitmap working=null;
        try{
            working=Bitmap.createBitmap(decoded.getWidth(),decoded.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(working);canvas.drawColor(Color.WHITE);canvas.drawBitmap(decoded,0,0,new Paint(Paint.FILTER_BITMAP_FLAG));decoded.recycle();decoded=null;
            for(int scale=0;scale<7;scale++){
                for(int quality:new int[]{88,75,60,45}){ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(!working.compress(Bitmap.CompressFormat.JPEG,quality,bytes))throw new IOException("This photo could not be converted.");if(bytes.size()<=budget)return bytes.toByteArray();}
                int width=Math.max(1,(int)(working.getWidth()*.75)),height=Math.max(1,(int)(working.getHeight()*.75));if(width==working.getWidth()&&height==working.getHeight())break;Bitmap smaller=Bitmap.createScaledBitmap(working,width,height,true);if(smaller!=working){working.recycle();working=smaller;}
            }
            throw new IllegalArgumentException("This photo cannot fit the carrier's MMS limit. Choose a smaller photo or remove another attachment.");
        }finally{if(decoded!=null)decoded.recycle();if(working!=null)working.recycle();}
    }
    private static Prepared prepare(Context c,JSONObject job,JSONArray attachments,Limits carrier)throws Exception{
        List<byte[]> contents=new ArrayList<>();int photos=0;long fixed=8192L+job.optString("caption").getBytes(StandardCharsets.UTF_8).length;
        for(int i=0;i<attachments.length();i++){
            JSONObject item=attachments.getJSONObject(i);byte[] bytes;try(InputStream input=new FileInputStream(asset(c,item.getString("id")))){bytes=read(input,MmsSendPolicy.MAX_SOURCE);}
            if(!hash(bytes).equals(item.getString("hash"))||bytes.length!=item.getLong("bytes"))throw new IllegalStateException("An attachment changed. Remove it and choose the file again.");
            String mime=item.getString("mime");if(!MmsSendPolicy.mime(bytes,mime).equals(mime))throw new IllegalStateException("An attachment's format changed.");
            if(mime.startsWith("audio/")&&!carrier.audio())throw new IllegalStateException("This carrier does not allow audio attachments.");
            if("image/jpeg".equals(mime))photos++;else fixed+=bytes.length;contents.add(bytes);fixed+=512;
        }
        int budget=photos==0?0:(int)Math.max(0,(carrier.bytes()-fixed)/photos);if(fixed>=carrier.bytes()||(photos>0&&budget<4096))throw new IllegalArgumentException("These files are larger than the carrier's MMS limit. Choose smaller files or remove an attachment.");
        StringBuilder smil=new StringBuilder("<smil><head><layout><root-layout width=\"320\" height=\"480\"/><region id=\"Image\" left=\"0\" top=\"0\" width=\"320\" height=\"360\" fit=\"meet\"/><region id=\"Text\" left=\"0\" top=\"360\" width=\"320\" height=\"120\"/></layout></head><body>");
        PduBody body=new PduBody();String caption=job.getString("caption");if(!caption.isEmpty()){body.addPart(part("text/plain","caption.txt",caption.getBytes(StandardCharsets.UTF_8)));smil.append("<par dur=\"5000ms\"><text src=\"caption.txt\" region=\"Text\"/></par>");}
        for(int i=0;i<attachments.length();i++){
            JSONObject item=attachments.getJSONObject(i);String mime=item.getString("mime"),name="part"+i+MmsSendPolicy.extension(mime);byte[] bytes=contents.get(i);
            if("image/jpeg".equals(mime))bytes=photo(bytes,carrier.width(),carrier.height(),budget);body.addPart(part(mime,name,bytes));
            String tag=mime.startsWith("image/")?"img":mime.startsWith("video/")?"video":mime.startsWith("audio/")?"audio":"ref";
            smil.append("<par dur=\"5000ms\"><").append(tag).append(" src=\"").append(name).append("\"");if(tag.equals("img")||tag.equals("video"))smil.append(" region=\"Image\"");smil.append("/></par>");
        }
        smil.append("</body></smil>");body.addPart(0,part("application/smil","smil.xml",smil.toString().getBytes(StandardCharsets.UTF_8)));
        SendReq request=new SendReq();request.setTo(new EncodedStringValue[]{new EncodedStringValue(job.getString("address"))});request.setFrom(new EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR));
        request.setDate(System.currentTimeMillis()/1000);request.setTransactionId(job.getString("transaction_id").getBytes(StandardCharsets.US_ASCII));request.setContentType("application/vnd.wap.multipart.related".getBytes(StandardCharsets.US_ASCII));
        request.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes(StandardCharsets.US_ASCII));request.setPriority(PduHeaders.PRIORITY_NORMAL);request.setDeliveryReport(PduHeaders.VALUE_NO);request.setReadReport(PduHeaders.VALUE_NO);request.setExpiry(7*24*60*60L);request.setBody(body);
        byte[] bytes=new PduComposer(c,request).make();if(bytes==null||bytes.length==0||bytes.length>carrier.bytes())throw new IllegalArgumentException("The prepared message exceeds this carrier's MMS limit. Remove an attachment or choose a smaller file.");return new Prepared(request,bytes);
    }
    private static PduPart part(String mime,String name,byte[] bytes){PduPart part=new PduPart();part.setContentType(mime.getBytes(StandardCharsets.US_ASCII));part.setContentId(name.getBytes(StandardCharsets.US_ASCII));part.setContentLocation(name.getBytes(StandardCharsets.US_ASCII));part.setName(name.getBytes(StandardCharsets.US_ASCII));part.setFilename(name.getBytes(StandardCharsets.US_ASCII));if(mime.startsWith("text/")||mime.equals("application/smil"))part.setCharset(106);part.setData(bytes);return part;}
    private static String mmsHead(Context c,long thread,long excluding){
        String selection="thread_id=?"+(excluding>0?" AND _id<>?":"");String[] args=excluding>0?new String[]{Long.toString(thread),Long.toString(excluding)}:new String[]{Long.toString(thread)};
        try(Cursor cursor=c.getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id","date","msg_box","m_type"},selection,args,"date DESC, _id DESC")){
            if(cursor==null)throw new IllegalStateException("The latest media message could not be checked.");if(!cursor.moveToFirst())return "";return cursor.getLong(0)+":"+cursor.getLong(1)+":"+cursor.getInt(2)+":"+cursor.getInt(3);
        }
    }
    private static String boundary(Context c,JSONObject job,long ownMms,JSONObject savedDraft){
        try{
            require(c);if(!Messages.activeSim(c,job.optInt("sub")))return "The selected SIM is no longer available.";
            if(!PhoneNumberUtils.compare(Sender.singleRecipient(c,job.optLong("thread")),job.optString("address"))||Messages.thread(c,job.optString("address"))!=job.optLong("thread"))return "The recipient changed. Review the conversation.";
            if(Messages.latest(c,job.optLong("thread"))!=job.optLong("base")||MmsDownloads.isPending()||IncomingBurst.hasUnbound(c)||!mmsHead(c,job.optLong("thread"),ownMms).equals(job.optString("mms_before")))return "New messages arrived. Review the conversation before sending these attachments.";
            if(savedDraft!=null&&job.optString("caption").equals(savedDraft.optString("body"))){String location=LocationReplies.block(c,savedDraft,System.currentTimeMillis());if(location!=null)return location;}
            return null;
        }catch(RuntimeException unavailable){return "Messaging access or the recipient could not be verified. Reopen the conversation and try again.";}
    }
    private static boolean providerMatches(Context c,JSONObject job){
        if(job==null||job.optLong("provider_id")<=0)return false;
        try(Cursor row=c.getContentResolver().query(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI,job.optLong("provider_id")),new String[]{"thread_id","date","tr_id","m_type"},null,null,null)){
            return row!=null&&row.moveToFirst()&&row.getLong(0)==job.optLong("thread")&&row.getLong(1)==job.optLong("provider_date")&&job.optString("transaction_id").equals(row.getString(2))&&row.getInt(3)==PduHeaders.MESSAGE_TYPE_SEND_REQ;
        }catch(RuntimeException unavailable){return false;}
    }
    private static void providerState(Context c,JSONObject job,boolean sent){
        if(job==null||job.optLong("provider_id")<=0||!Messages.role(c))return;
        try{ContentValues values=new ContentValues();values.put("msg_box",sent?Telephony.Mms.MESSAGE_BOX_SENT:Telephony.Mms.MESSAGE_BOX_FAILED);values.put("read",1);values.put("seen",1);
            c.getContentResolver().update(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI,job.optLong("provider_id")),values,"thread_id=? AND date=? AND tr_id=? AND m_type=?",new String[]{Long.toString(job.optLong("thread")),Long.toString(job.optLong("provider_date")),job.optString("transaction_id"),Integer.toString(PduHeaders.MESSAGE_TYPE_SEND_REQ)});
        }catch(RuntimeException ignored){}
    }
    private static void unlock(MmsOutbox db,String id){ContentValues values=new ContentValues();values.put("job","");db.getWritableDatabase().update("attachments",values,"job=?",new String[]{id});}
    private static JSONObject result(JSONObject row)throws JSONException{return new JSONObject().put("id",row.optString("id")).put("status",row.optString("status")).put("note",row.optString("note"));}
    static void receipt(Context c,String id,int resultCode,byte[] response){
        synchronized(PilotApp.SEND_LOCK){
            MmsOutbox db=MmsOutbox.get(c);JSONObject job=db.job(id);if(job==null||!MmsSendPolicy.callbackAllowed(job.optString("status")))return;
            String transaction=null;Integer responseStatus=null;
            if(response!=null&&response.length>0&&response.length<=16384)try{GenericPdu parsed=new PduParser(response).parse();if(parsed instanceof SendConf confirmation){byte[] actual=confirmation.getTransactionId();if(actual!=null&&actual.length<=80)transaction=new String(actual,StandardCharsets.US_ASCII);responseStatus=confirmation.getResponseStatus();}}catch(RuntimeException malformed){}
            String status=MmsSendPolicy.outcome(resultCode==Activity.RESULT_OK,job.optString("transaction_id"),transaction,responseStatus);boolean sent="sent".equals(status);String note=sent?"The carrier accepted this MMS. Recipient delivery is not confirmed.":"The carrier reported that this MMS could not send. Your attachments are kept; retry only after checking the conversation.";
            if("unknown".equals(status))note="The carrier response did not confirm this send. Check with the recipient; this message will not retry automatically.";
            db.status(id,status,note);if(!"unknown".equals(status))providerState(c,job,sent);
            if(!"unknown".equals(status))settle(c,db,db.job(id));
            if(!"unknown".equals(status))pdu(c,id).delete();LIVE.remove(id);MessageChanges.publish();
            if(currentConversation(c,job))Notices.show(c,(int)(job.optLong("thread")&0x7fffffff),sent?"Attachment sent":"failed".equals(status)?"Attachment not sent":"Attachment send unconfirmed",note,job.optLong("thread"));
        }
    }
    private static boolean currentConversation(Context c,JSONObject job){try{return Messages.allowed(c,Manifest.permission.READ_SMS)&&Messages.latest(c,job.optLong("thread"))==job.optLong("base")&&mmsHead(c,job.optLong("thread"),job.optLong("provider_id")).equals(job.optString("mms_before"));}catch(RuntimeException unavailable){return false;}}
    private static void settle(Context c,MmsOutbox db,JSONObject job){
        if(job==null||job.optInt("settled")==1||!MmsSendPolicy.terminal(job.optString("status")))return;
        String id=job.optString("id");if("sent".equals(job.optString("status"))){
            JSONArray staged=db.query("SELECT id FROM attachments WHERE job=?",id);
            // A repeated cleanup after process death is exact and cannot affect
            // another conversation, a newer SMS base, or edited caption text.
            Store.get(c).getWritableDatabase().delete("drafts","thread=? AND base=? AND body=?",new String[]{Long.toString(job.optLong("thread")),Long.toString(job.optLong("base")),job.optString("caption")});
            for(int i=0;i<staged.length();i++)asset(c,staged.optJSONObject(i).optString("id")).delete();
            db.getWritableDatabase().delete("attachments","job=?",new String[]{id});
        }else unlock(db,id);
        ContentValues done=new ContentValues();done.put("settled",1);db.getWritableDatabase().update("sends",done,"id=? AND status IN ('sent','failed')",new String[]{id});pdu(c,id).delete();
    }
    /** Repeated resume/recovery calls preserve work alive in this process. */
    public static void recover(Context c){synchronized(PilotApp.SEND_LOCK){
        MmsOutbox db=MmsOutbox.get(c);JSONArray jobs=db.query("SELECT * FROM sends WHERE status IN ('preparing','sending')");boolean changed=false;long now=System.currentTimeMillis();
        for(int i=0;i<jobs.length();i++){
            JSONObject job=jobs.optJSONObject(i);String id=job.optString("id"),status=job.optString("status");boolean live=LIVE.contains(id);
            if(live&&("preparing".equals(status)||(now>=job.optLong("created")&&now-job.optLong("created")<10*60*1000L)))continue;
            if("preparing".equals(status)){db.status(id,"failed","Preparation was interrupted before carrier submission. Your attachments are kept.");unlock(db,id);providerState(c,job,false);pdu(c,id).delete();}
            else db.status(id,"unknown","The carrier result was not received. Check with the recipient; this MMS will not retry automatically.");
            LIVE.remove(id);changed=true;
        }
        JSONArray terminal=db.query("SELECT * FROM sends WHERE status IN ('sent','failed') AND settled=0");for(int i=0;i<terminal.length();i++){JSONObject job=terminal.optJSONObject(i);settle(c,db,job);providerState(c,job,"sent".equals(job.optString("status")));changed=true;}
        if(changed)MessageChanges.publish();
    }}
    /** Bind status to the actual MMS transaction, never a recycled provider ID. */
    public static void annotate(Context c,JSONArray rows){
        if(!Messages.allowed(c,Manifest.permission.READ_SMS))return;MmsOutbox db=MmsOutbox.get(c);
        Map<Long,List<JSONObject>> candidates=new LinkedHashMap<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&("mms".equals(row.optString("kind"))||(!row.has("kind")&&row.has("m_type")))&&row.optLong("_id")>0)candidates.computeIfAbsent(row.optLong("_id"),key->new ArrayList<>()).add(row);}
        List<Long> ids=new ArrayList<>(candidates.keySet());for(int offset=0;offset<ids.size();offset+=150){
            int end=Math.min(ids.size(),offset+150);String[] args=new String[end-offset];for(int i=offset;i<end;i++)args[i-offset]=Long.toString(ids.get(i));
            JSONArray jobs=db.query("SELECT * FROM sends WHERE provider_id IN ("+String.join(",",Collections.nCopies(args.length,"?"))+")",args);
            for(int i=0;i<jobs.length();i++){
                JSONObject job=jobs.optJSONObject(i);if(!providerMatches(c,job))continue;for(JSONObject row:candidates.getOrDefault(job.optLong("provider_id"),List.of())){
                    if(row.optLong("thread_id")!=job.optLong("thread")||("mms".equals(row.optString("kind"))?row.optLong("date")/1000:row.optLong("date"))!=job.optLong("provider_date"))continue;
                    try{row.put("sendStatus",result(job));}catch(JSONException impossible){throw new IllegalStateException(impossible);}
                }
            }
        }
    }
}
