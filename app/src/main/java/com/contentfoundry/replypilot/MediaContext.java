package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaDataSource;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Telephony;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Explicit, on-demand attachment capture; no files, images or media history are retained. */
public final class MediaContext {
    // Native decoders may ignore interruption. A single worker with no backlog
    // bounds memory/concurrency even if one malformed attachment stalls decoding.
    private static final ThreadPoolExecutor READER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"reply-pilot-media");t.setDaemon(true);return t;});
    public record Image(String jpegBase64,String label){}
    public record Snapshot(long thread,long mediaId,long date,String address,String caption,String mediaType,List<Image> images,List<String> limitations,String fingerprint,String latestSms,String latestMms,long capturedElapsed){
        public Snapshot{images=List.copyOf(images);limitations=List.copyOf(limitations);}
        public boolean hasImages(){return !images.isEmpty();}
        public JSONObject payload()throws JSONException{
            JSONArray attachments=new JSONArray();for(Image image:images)attachments.put(new JSONObject().put("jpegBase64",image.jpegBase64()).put("label",image.label()));
            return new JSONObject().put("caption",caption).put("mediaType",mediaType).put("images",attachments).put("mediaLimitations",MediaContextPolicy.clipped(String.join(" ",limitations),360));
        }
    }
    private record Evidence(JSONObject message,String address,String fingerprint,String latestSms,String latestMms){}
    private MediaContext(){}
    /** Call from an existing worker, never the UI thread. Whole capture is bounded to25seconds. */
    public static Snapshot capture(Context context,long thread,long mediaId){
        return capture(context,thread,mediaId,false);
    }
    /** Text-only MMS keeps the same recipient, part and latest-message evidence as media. */
    public static Snapshot captureText(Context context,long thread,long mediaId){
        return capture(context,thread,mediaId,true);
    }
    private static Snapshot capture(Context context,long thread,long mediaId,boolean textOnly){
        Context c=context.getApplicationContext();Future<Snapshot> work;
        try{work=READER.submit(()->textOnly?captureTextNow(c,thread,mediaId):captureNow(c,thread,mediaId));}catch(RuntimeException busy){throw new IllegalStateException("A message is still being checked. Try again shortly.");}
        try{return work.get(25,TimeUnit.SECONDS);}
        catch(InterruptedException interrupted){Thread.currentThread().interrupt();work.cancel(true);throw new IllegalStateException("Media reading was interrupted.");}
        catch(java.util.concurrent.TimeoutException slow){work.cancel(true);throw new IllegalStateException("This message took too long to read. Its contents were not sent to AI.");}
        catch(java.util.concurrent.ExecutionException failed){Throwable cause=failed.getCause();if(cause instanceof IllegalArgumentException bad)throw bad;if(cause instanceof IllegalStateException unavailable)throw unavailable;throw new IllegalStateException("This media message could not be read. Its contents were not sent to AI.");}
    }
    public static boolean valid(Context context,Snapshot snapshot){
        if(snapshot==null||!MediaContextPolicy.fresh(snapshot.capturedElapsed(),SystemClock.elapsedRealtime()))return false;
        Future<Boolean> work;
        try{work=READER.submit(()->matches(snapshot,evidence(context.getApplicationContext(),snapshot.thread(),snapshot.mediaId())));}
        catch(RuntimeException busy){return false;}
        try{return work.get(5,TimeUnit.SECONDS)&&MediaContextPolicy.fresh(snapshot.capturedElapsed(),SystemClock.elapsedRealtime());}
        catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();work.cancel(true);return false;}
    }
    /** Metadata only: don't answer an old SMS while overlooking a newer incoming MMS. */
    public static boolean newerIncoming(Context c,long thread,long smsBase){
        try{
            if(thread<=0||smsBase<0||!Messages.allowed(c,Manifest.permission.READ_SMS))return true;
            long id,date;
            try(Cursor rows=c.getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id","date","thread_id"},"thread_id=? AND msg_box=1",new String[]{Long.toString(thread)},"date DESC, _id DESC")){
                if(rows==null)return true;if(!rows.moveToFirst())return false;if(rows.getLong(2)!=thread)return true;id=rows.getLong(0);date=MediaHistoryPolicy.date("mms",rows.getLong(1));
            }
            if(smsBase==0)return true;
            JSONObject sms=MediaNavigation.exact(c,"sms",smsBase,thread);if(sms==null)return true;
            return MediaHistoryPolicy.compare(new MediaHistoryPolicy.Position(date,"mms",id),new MediaHistoryPolicy.Position(sms.optLong("date"),"sms",smsBase))>0;
        }catch(Exception unavailable){return true;}
    }
    private static Snapshot captureNow(Context c,long thread,long id)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+24000;Evidence before=evidence(c,thread,id);JSONObject message=before.message();
        if(message.optBoolean("textOnly"))throw new IllegalStateException("This is a text message. Tap Draft reply.");
        String fullCaption=message.optString("body"),caption=MediaContextPolicy.clipped(fullCaption,1600);
        List<Image> images=new ArrayList<>();List<String> limitations=new ArrayList<>();int totalBytes=0,sourceBytes=0;boolean video=false;
        if(MmsContentPolicy.UNKNOWN.equals(message.optString("contentKind")))add(limitations,"Some message parts could not be read or classified.");
        if(fullCaption.length()>caption.length()||message.optBoolean("truncated"))add(limitations,"Only the first 1,600 caption characters are included.");
        JSONArray parts=message.optJSONArray("parts");
        for(int i=0;parts!=null&&i<parts.length();i++){
            checkTime(deadline);JSONObject part=parts.optJSONObject(i);String type=part.optString("ct").toLowerCase(java.util.Locale.ROOT);
            if("text/plain".equals(type)||"application/smil".equals(type))continue;
            boolean isVideo=type.startsWith("video/");video|=isVideo;
            if(!type.startsWith("image/")&&!isVideo){add(limitations,type.startsWith("audio/")?"Audio attachments are not transcribed or heard.":"Some attachment types cannot be analyzed.");continue;}
            if(images.size()==MediaContextPolicy.MAX_IMAGES){add(limitations,"Only three images or sampled video frames are included; other attachments were skipped.");continue;}
            try{
                int cap=Math.min(isVideo?MediaContextPolicy.MAX_SOURCE_BYTES:MediaContextPolicy.MAX_IMAGE_SOURCE_BYTES,MediaContextPolicy.MAX_ALL_SOURCE_BYTES-sourceBytes);
                if(cap<=0)throw new IOException("Source limit reached");
                byte[] source=read(c,part.optLong("_id"),cap,deadline);sourceBytes+=source.length;
                if(isVideo){
                    add(limitations,"Video is represented only by sampled still frames; no audio was heard and motion between frames is unknown.");
                    List<ImageBytes> frames=video(source,MediaContextPolicy.MAX_IMAGES-images.size(),deadline);
                    if(frames.isEmpty())add(limitations,"This video could not be sampled; its contents are unknown.");
                    for(ImageBytes frame:frames)if(MediaContextPolicy.accepts(images.size(),totalBytes,frame.bytes().length)){images.add(new Image(Base64.encodeToString(frame.bytes(),Base64.NO_WRAP),frame.label()));totalBytes+=frame.bytes().length;}
                }else{
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(source,0,source.length,bounds);
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=MediaContextPolicy.sample(bounds.outWidth,bounds.outHeight);options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                    Bitmap bitmap=BitmapFactory.decodeByteArray(source,0,source.length,options);if(bitmap==null)throw new IOException("Unreadable image");
                    byte[] jpeg;try{jpeg=jpeg(bitmap,deadline);}finally{bitmap.recycle();}
                    if(!MediaContextPolicy.accepts(images.size(),totalBytes,jpeg.length))throw new IOException("Image exceeds capture limit");
                    images.add(new Image(Base64.encodeToString(jpeg,Base64.NO_WRAP),"Image attachment "+(i+1)));totalBytes+=jpeg.length;
                    if("image/gif".equals(type)||"image/webp".equals(type))add(limitations,"Animated images are represented by one still image; animation was not observed.");
                }
            }catch(Exception unavailable){if(Thread.currentThread().isInterrupted())throw unavailable;add(limitations,isVideo?"A video could not be sampled; its contents are unknown.":"An image could not be read; its contents are unknown.");}
        }
        if(images.isEmpty())add(limitations,"No supported image or video frame could be read. AI cannot analyze the unseen attachments.");
        checkTime(deadline);Evidence after=evidence(c,thread,id);
        Snapshot snapshot=new Snapshot(thread,id,message.optLong("date"),before.address(),caption,video?"video":"image",images,limitations,before.fingerprint(),before.latestSms(),before.latestMms(),SystemClock.elapsedRealtime());
        if(!matches(snapshot,after))throw new IllegalStateException("The conversation changed while media was loading. Open the latest message and try again.");
        return snapshot;
    }
    private static Snapshot captureTextNow(Context c,long thread,long id)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+24000;Evidence before=evidence(c,thread,id);JSONObject message=before.message();
        String body=message.optString("body"),kind=message.optString("contentKind");
        if(!MmsContentPolicy.TEXT.equals(kind))throw new IllegalStateException("This message contains attachments or unreadable content. Open the message to review it.");
        if(!MmsContentPolicy.draftableText(kind,body))throw new IllegalStateException("This text is too long for a safe reply draft. Review it and write your reply.");
        requireLatestText(c,thread,id,message.optLong("date"));checkTime(deadline);
        Snapshot snapshot=new Snapshot(thread,id,message.optLong("date"),before.address(),body,"text",List.of(),List.of(),before.fingerprint(),before.latestSms(),before.latestMms(),SystemClock.elapsedRealtime());
        Evidence after=evidence(c,thread,id);checkTime(deadline);
        if(!matches(snapshot,after))throw new IllegalStateException("The conversation changed. Open the latest text and try Draft reply again.");
        requireLatestText(c,thread,id,message.optLong("date"));return snapshot;
    }
    private static void requireLatestText(Context c,long thread,long id,long date)throws JSONException{
        JSONObject mms=MediaNavigation.latestRow(c,thread,"mms"),sms=MediaNavigation.latestRow(c,thread,"sms");
        MediaHistoryPolicy.Position anchor=new MediaHistoryPolicy.Position(date,"mms",id);
        if(mms==null||mms.optLong("_id")!=id||mms.optLong("date")!=date
            ||sms!=null&&MediaHistoryPolicy.compare(new MediaHistoryPolicy.Position(sms.optLong("date"),"sms",sms.optLong("_id")),anchor)>=0)
            throw new IllegalStateException("Newer messages are available. Open the latest text before drafting.");
    }
    private static Evidence evidence(Context c,long thread,long id)throws Exception{
        if(thread<=0||id<=0)throw new IllegalArgumentException("Choose a media message in this conversation.");
        requireAccess(c);JSONObject message=MediaNavigation.exact(c,"mms",id,thread);
        if(message==null||message.optInt("msg_box")!=Telephony.Mms.MESSAGE_BOX_INBOX||message.optInt("m_type")!=132)throw new IllegalStateException("Only a downloaded incoming media message can be read for a reply.");
        MediaNavigation.Destination destination=MediaNavigation.destination(c,thread,message);
        if(destination.readOnly()||destination.address().isEmpty())throw new IllegalStateException("Media replies require one confirmed SMS recipient.");
        MediaNavigation.parts(c,message);
        String fingerprint=MediaContextPolicy.signature(rowSignature(message),message.optJSONArray("parts").toString(),partEvidence(c,id),destination.address());
        String latestSms=rowSignature(MediaNavigation.latestRow(c,thread,"sms")),latestMms=rowSignature(MediaNavigation.latestRow(c,thread,"mms"));
        requireAccess(c);return new Evidence(message,destination.address(),fingerprint,latestSms,latestMms);
    }
    private static String partEvidence(Context c,long id)throws JSONException{
        // The provider's private storage reference is hashed only; no path is
        // opened directly or exposed in the AI payload. This detects part rebinding.
        JSONArray metadata=new JSONArray();
        try(Cursor rows=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/part"),new String[]{"_id","mid","ct","_data","seq","text"},null,null,"seq ASC, _id ASC")){
            if(rows==null)throw new IllegalStateException("The media parts could not be checked.");int count=0;
            while(rows.moveToNext()){
                if(++count>65)break;
                if(rows.getLong(1)!=id)throw new IllegalStateException("The media attachment changed.");
                metadata.put(new JSONObject().put("id",rows.getLong(0)).put("type",rows.getString(2)).put("storage",rows.isNull(3)?"":rows.getString(3)).put("sequence",rows.getLong(4)).put("text",MediaContextPolicy.clipped(rows.isNull(5)?"":rows.getString(5),32000)));
            }
        }
        return metadata.toString();
    }
    private static boolean matches(Snapshot captured,Evidence current){return captured.address().equals(current.address())&&captured.fingerprint().equals(current.fingerprint())&&captured.latestSms().equals(current.latestSms())&&captured.latestMms().equals(current.latestMms());}
    private static String rowSignature(JSONObject row){return row==null?"":MediaContextPolicy.signature(row.optString("kind"),row.optString("_id"),row.optString("thread_id"),row.optString("date"),row.optString("type"),row.optString("m_type"),row.optString("sub"),row.optString("address"),row.optString("body"));}
    private static void requireAccess(Context c){if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Restore the default texting app and SMS access before reading media.");}
    private static void add(List<String> limitations,String note){if(!limitations.contains(note))limitations.add(note);}
    private static void checkTime(long deadline)throws IOException{if(Thread.currentThread().isInterrupted()||SystemClock.elapsedRealtime()>deadline)throw new IOException("Media read interrupted or timed out");}
    private static byte[] read(Context c,long id,int max,long deadline)throws IOException{
        if(id<=0)throw new IOException("Invalid part");
        try(InputStream input=c.getContentResolver().openInputStream(Uri.parse("content://mms/part/"+id));ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            if(input==null)throw new IOException("Part unavailable");byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1){checkTime(deadline);if(count>max-bytes.size())throw new IOException("Part too large");bytes.write(buffer,0,count);}return bytes.toByteArray();
        }
    }
    private record ImageBytes(byte[] bytes,String label){}
    private static List<ImageBytes> video(byte[] bytes,int available,long deadline)throws Exception{
        List<ImageBytes> result=new ArrayList<>();MediaMetadataRetriever retriever=new MediaMetadataRetriever();
        try{
            retriever.setDataSource(new MediaDataSource(){
                @Override public int readAt(long position,byte[] buffer,int offset,int size)throws IOException{checkTime(deadline);if(position<0)throw new IOException("Invalid media offset");if(position>=bytes.length)return -1;int count=(int)Math.min(size,bytes.length-position);System.arraycopy(bytes,(int)position,buffer,offset,count);return count;}
                @Override public long getSize(){return bytes.length;}
                @Override public void close(){}
            });
            long duration=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            int width=Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)),height=Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            if((long)width*height>3840L*2160)throw new IOException("Video resolution too large");MediaContextPolicy.scaled(width,height);
            for(long time:MediaContextPolicy.frames(duration,available)){
                checkTime(deadline);Bitmap frame=retriever.getScaledFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,MediaContextPolicy.MAX_EDGE,MediaContextPolicy.MAX_EDGE);
                if(frame==null)continue;
                try{result.add(new ImageBytes(jpeg(frame,deadline),"Sampled video frame near "+(time/1000)+"s; no audio"));}finally{frame.recycle();}
            }
        }finally{retriever.release();}
        return result;
    }
    private static byte[] jpeg(Bitmap source,long deadline)throws IOException{
        MediaContextPolicy.Size size=MediaContextPolicy.scaled(source.getWidth(),source.getHeight());
        Bitmap flattened=Bitmap.createBitmap(size.width(),size.height(),Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(flattened);canvas.drawColor(Color.WHITE);canvas.drawBitmap(source,null,new android.graphics.Rect(0,0,size.width(),size.height()),null);
            for(int step=0;step<4;step++){
                for(int quality:new int[]{85,70,55,40}){
                    checkTime(deadline);ByteArrayOutputStream output=new ByteArrayOutputStream();if(!flattened.compress(Bitmap.CompressFormat.JPEG,quality,output))throw new IOException("Could not encode image");
                    if(output.size()<=MediaContextPolicy.MAX_JPEG_BYTES&&output.size()>0)return output.toByteArray();
                }
                Bitmap smaller=Bitmap.createScaledBitmap(flattened,Math.max(1,flattened.getWidth()*3/4),Math.max(1,flattened.getHeight()*3/4),true);flattened.recycle();flattened=smaller;
            }
            throw new IOException("Image cannot fit the analysis limit");
        }finally{flattened.recycle();}
    }
}
