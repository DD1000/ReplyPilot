package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.os.Process;
import android.os.SystemClock;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONException;
import org.json.JSONObject;

/** Ephemeral public link previews. No cookies, JavaScript, provider writes or AI. */
final class LinkPreviews {
    static final ExecutorService EXECUTOR=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(6),work->worker(work,"reply-pilot-link-preview"));
    // Native DNS can ignore interruption; bound both its wait and number of workers.
    private static final ExecutorService DNS=new ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,new SynchronousQueue<>(),work->worker(work,"reply-pilot-link-dns"));
    private static final Object LOCK=new Object();
    private static final int MAX_ENTRIES=40, MAX_IMAGE_BYTES=3*1024*1024;
    private static final long TTL=10*60_000L, FAILED_TTL=60_000L, REQUEST_BUDGET=12_000L;
    private static final LinkedHashMap<String,Entry> CACHE=new LinkedHashMap<>(16,.75f,true);
    private static final LinkedHashMap<String,Picture> IMAGES=new LinkedHashMap<>();
    private static final Set<Call> CALLS=new HashSet<>();
    private static long revision=1;
    private static int imageBytes;
    private static boolean lastAllowed;
    private record Entry(LinkPreviewPolicy.Metadata metadata,String imageId,long at){}
    private record Picture(byte[] bytes,long at){}
    private record Download(byte[] bytes,String url,MediaType type){}
    private static final OkHttpClient CLIENT=new OkHttpClient.Builder().proxy(Proxy.NO_PROXY).cookieJar(CookieJar.NO_COOKIES)
        .dns(LinkPreviews::resolve).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .protocols(List.of(Protocol.HTTP_1_1)).connectionPool(new ConnectionPool(0,1,TimeUnit.MILLISECONDS))
        .connectTimeout(3,TimeUnit.SECONDS).readTimeout(3,TimeUnit.SECONDS).writeTimeout(3,TimeUnit.SECONDS).callTimeout(5,TimeUnit.SECONDS).build();
    private LinkPreviews(){}
    private static Thread worker(Runnable work,String name){Thread thread=new Thread(()->{Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);work.run();},name);thread.setDaemon(true);return thread;}
    private static boolean allowed(Context c){return Messages.allowed(c,Manifest.permission.READ_SMS)&&Messages.role(c)&&c.getSharedPreferences("settings",0).getBoolean("linkPreviews",true);}
    private static long access(Context c){boolean active=allowed(c);boolean lost;long value;synchronized(LOCK){lost=lastAllowed&&!active;lastAllowed=active;if(lost){revision++;CACHE.clear();IMAGES.clear();imageBytes=0;}value=revision;}if(lost)cancelCalls();return value;}
    private static boolean current(Context c,long expected){long active=access(c);return PilotApp.foreground&&allowed(c)&&active==expected;}
    static boolean canDeliver(Context c,JSONObject result){return result!=null&&current(c,result.optLong("revision",-1));}
    static void cancelPending(){synchronized(LOCK){revision++;}cancelCalls();}
    static void clear(){synchronized(LOCK){revision++;CACHE.clear();IMAGES.clear();imageBytes=0;}cancelCalls();}
    private static void cancelCalls(){Call[] active;synchronized(LOCK){active=CALLS.toArray(new Call[0]);}for(Call call:active)call.cancel();}

    static JSONObject preview(Context context,String raw)throws JSONException{
        Context c=context.getApplicationContext();String url=LinkPreviewPolicy.openUrl(raw);long expected=access(c),deadline=SystemClock.elapsedRealtime()+REQUEST_BUDGET;
        if(!current(c,expected))return json(url,null,expected);
        Entry cached;synchronized(LOCK){purge(SystemClock.elapsedRealtime());cached=CACHE.get(url);}if(cached!=null)return json(url,cached,expected);
        LinkPreviewPolicy.Metadata metadata=new LinkPreviewPolicy.Metadata("","","","",false);byte[] picture=null;
        try{
            Download page=download(c,LinkPreviewPolicy.previewUrl(url),false,expected,deadline);
            Charset charset=page.type()==null?StandardCharsets.UTF_8:page.type().charset(StandardCharsets.UTF_8);
            metadata=LinkPreviewPolicy.metadata(new String(page.bytes(),charset),page.url());
            if(metadata.available()&&!metadata.image().isEmpty()&&current(c,expected)&&SystemClock.elapsedRealtime()<deadline){
                try{Download input=download(c,metadata.image(),true,expected,deadline);picture=thumbnail(input.bytes());}catch(IOException|RuntimeException unavailable){/* Text card remains useful without a picture. */}
            }
        }catch(IOException|RuntimeException unavailable){/* Generic fallback; never expose URLs or network exception details. */}
        if(!current(c,expected))return json(url,null,expected);
        Entry entry; synchronized(LOCK){
            if(revision!=expected)return json(url,null,expected);long now=SystemClock.elapsedRealtime();purge(now);
            Entry completed=CACHE.get(url);
            // A concurrent worker may already have returned this image token.
            // Reuse its fresh successful entry rather than invalidating that card.
            if(completed!=null&&completed.metadata().available())entry=completed;
            else{
                String id=picture==null?"":UUID.randomUUID().toString();entry=new Entry(metadata,id,now);Entry previous=CACHE.put(url,entry);removeImage(previous);
                if(picture!=null){IMAGES.put(id,new Picture(picture,now));imageBytes+=picture.length;}purge(now);
            }
        }
        return json(url,entry,expected);
    }
    private static JSONObject json(String url,Entry entry,long expected)throws JSONException{
        LinkPreviewPolicy.Metadata data=entry==null?new LinkPreviewPolicy.Metadata("","","","",false):entry.metadata();JSONObject out=new JSONObject().put("url",url).put("available",data.available()).put("title",data.title()).put("description",data.description()).put("siteName",data.siteName()).put("host",HttpUrl.get(url).host()).put("revision",expected);
        if(entry!=null&&!entry.imageId().isEmpty())out.put("imageUrl","/link-preview/"+entry.imageId());return out;
    }
    private static void purge(long now){Iterator<Map.Entry<String,Entry>> it=CACHE.entrySet().iterator();while(it.hasNext()){Entry entry=it.next().getValue();long age=now-entry.at();if(age<0||age>=(entry.metadata().available()?TTL:FAILED_TTL)){removeImage(entry);it.remove();}}while(CACHE.size()>MAX_ENTRIES||imageBytes>MAX_IMAGE_BYTES){Iterator<Entry> oldest=CACHE.values().iterator();if(!oldest.hasNext())break;removeImage(oldest.next());oldest.remove();}}
    private static void removeImage(Entry entry){if(entry!=null&&!entry.imageId().isEmpty()){Picture removed=IMAGES.remove(entry.imageId());if(removed!=null)imageBytes-=removed.bytes().length;}}
    static WebResourceResponse image(Context c,String token){
        if(token==null||!token.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))return null;long expected=access(c);if(!current(c,expected))return null;Picture picture;synchronized(LOCK){purge(SystemClock.elapsedRealtime());picture=IMAGES.get(token);}
        if(picture==null||!current(c,expected))return null;return new WebResourceResponse("image/jpeg",null,200,"OK",Map.of("Cache-Control","no-store","X-Content-Type-Options","nosniff"),new ByteArrayInputStream(picture.bytes()));
    }
    private static List<InetAddress> resolve(String hostname)throws UnknownHostException{
        Future<List<InetAddress>> future=null;try{future=DNS.submit(()->LinkPreviewPolicy.publicAddresses(Arrays.asList(InetAddress.getAllByName(hostname))));return future.get(2,TimeUnit.SECONDS);}catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();throw new UnknownHostException("Public preview destination unavailable.");}finally{if(future!=null)future.cancel(true);}
    }
    private static Download download(Context c,String start,boolean image,long expected,long deadline)throws IOException{
        String next=LinkPreviewPolicy.previewUrl(start);Set<String> visited=new HashSet<>();
        for(int redirects=0;redirects<=LinkPreviewPolicy.MAX_REDIRECTS;redirects++){
            if(!current(c,expected))throw new IOException("Preview cancelled.");long remaining=deadline-SystemClock.elapsedRealtime();if(remaining<=0)throw new IOException("Preview unavailable.");
            HttpUrl target=HttpUrl.get(LinkPreviewPolicy.previewUrl(next)).newBuilder().fragment(null).build();if(!visited.add(target.toString()))throw new IOException("Preview unavailable.");
            Request request=new Request.Builder().url(target).header("User-Agent","ReplyPilot-LinkPreview/1.0").header("Accept",image?"image/jpeg,image/png,image/webp,image/gif":"text/html,application/xhtml+xml").build();
            Call call=CLIENT.newCall(request);call.timeout().timeout(Math.min(5000,remaining),TimeUnit.MILLISECONDS);
            synchronized(LOCK){if(revision!=expected)throw new IOException("Preview cancelled.");CALLS.add(call);}
            try(Response response=call.execute()){
                int status=response.code();if(status==301||status==302||status==303||status==307||status==308){String location=response.header("Location");if(location==null||redirects==LinkPreviewPolicy.MAX_REDIRECTS)throw new IOException("Preview unavailable.");next=LinkPreviewPolicy.resolvePreview(target.toString(),location);continue;}
                if(status!=200)throw new IOException("Preview unavailable.");ResponseBody body=response.body();if(body==null)throw new IOException("Preview unavailable.");MediaType type=body.contentType();String mime=type==null?"":type.type()+"/"+type.subtype();
                if(image?!List.of("image/jpeg","image/png","image/webp","image/gif").contains(mime):!List.of("text/html","application/xhtml+xml").contains(mime))throw new IOException("Preview unavailable.");
                int max=image?LinkPreviewPolicy.MAX_IMAGE_INPUT:LinkPreviewPolicy.MAX_HTML;if(image&&body.contentLength()>max)throw new IOException("Preview unavailable.");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];InputStream stream=body.byteStream();int count;
                // HTML metadata lives near the beginning: a large page can still
                // yield its bounded prefix. Images require complete bounded data.
                while(image||bytes.size()<max){int requested=image?buffer.length:Math.min(buffer.length,max-bytes.size());count=stream.read(buffer,0,requested);if(count==-1)break;if(!current(c,expected)||SystemClock.elapsedRealtime()>=deadline||(long)bytes.size()+count>max)throw new IOException("Preview unavailable.");bytes.write(buffer,0,count);}return new Download(bytes.toByteArray(),target.toString(),type);
            }finally{synchronized(LOCK){CALLS.remove(call);}}
        }throw new IOException("Preview unavailable.");
    }
    private static byte[] thumbnail(byte[] input)throws IOException{
        Bitmap decoded=null,flat=null;try{
            decoded=ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(input)),(decoder,info,source)->{int width=info.getSize().getWidth(),height=info.getSize().getHeight();if(width<1||height<1||(long)width*height>LinkPreviewPolicy.MAX_PIXELS)throw new IllegalArgumentException("Image too large.");double scale=Math.min(1d,(double)LinkPreviewPolicy.MAX_EDGE/Math.max(width,height));decoder.setTargetSize(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
            flat=Bitmap.createBitmap(decoded.getWidth(),decoded.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(flat);canvas.drawColor(Color.WHITE);canvas.drawBitmap(decoded,0,0,null);
            for(int quality:new int[]{82,68,50,35}){ByteArrayOutputStream out=new ByteArrayOutputStream();if(!flat.compress(Bitmap.CompressFormat.JPEG,quality,out))return null;if(out.size()<=LinkPreviewPolicy.MAX_IMAGE)return out.toByteArray();}return null;
        }finally{if(decoded!=null)decoded.recycle();if(flat!=null)flat.recycle();}
    }
}
