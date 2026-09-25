package com.contentfoundry.replypilot;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.provider.Settings;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import org.json.*;

/** Explicit foreground lookup; never enables sharing or saves home until confirmed. */
final class HomeAddressSearch {
    static final ExecutorService EXECUTOR=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"reply-pilot-home-address");t.setDaemon(true);return t;});
    private static final ExecutorService GEOCODER=new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"reply-pilot-home-geocoder");t.setDaemon(true);return t;});
    private static final OkHttpClient CLIENT=new OkHttpClient.Builder().callTimeout(10,TimeUnit.SECONDS).connectTimeout(5,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    private static final Object LOCK=new Object();
    private static long epoch=1;
    private static HomeAddressPolicy.Session session;
    private static CancellationSignal signal;
    private static Call mapCall;
    private static CountDownLatch fixReturned;
    private static Future<?> lookup;
    private HomeAddressSearch(){}
    private static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    static void cancel(){synchronized(LOCK){epoch++;session=null;if(signal!=null)signal.cancel();if(fixReturned!=null)fixReturned.countDown();if(mapCall!=null)mapCall.cancel();if(lookup!=null)lookup.cancel(true);}}
    private static boolean current(Context c,long expected,long revision){long active;synchronized(LOCK){active=epoch;}return active==expected&&LocationSharing.homeSearchRevision(c)==revision;}
    static boolean canDeliver(Context c,JSONObject response){HomeAddressPolicy.Session value;long active;synchronized(LOCK){value=session;active=epoch;}if(value==null||response==null||!value.requestId().equals(response.optString("requestId")))return false;int b=boot(c);return value.valid(LocationSharing.homeSearchRevision(c),active,System.currentTimeMillis(),SystemClock.elapsedRealtime(),b);}
    static JSONObject request(Context supplied)throws Exception{
        Context c=supplied.getApplicationContext();long revision=LocationSharing.homeSearchRevision(c);if(revision<0)throw new IllegalStateException("Turn on location replies and allow precise location, then try your current place again. You can also type your home address.");
        cancel();long expected;synchronized(LOCK){expected=epoch;}
        Location location=fix(c,expected,revision);int capturedBoot=boot(c);
        if(location==null||!location.hasAccuracy()||!HomeAddressPolicy.preciseFix(location.getLatitude(),location.getLongitude(),location.getAccuracy(),location.isMock())||!LocationPolicy.currentForHome(location.getTime(),location.getElapsedRealtimeNanos()/1_000_000,capturedBoot,System.currentTimeMillis(),SystemClock.elapsedRealtime(),capturedBoot))throw new IllegalStateException("A precise current location was not available. Try near a window or type your home address.");
        if(!current(c,expected,revision))throw expired();ArrayList<HomeAddressPolicy.Candidate> candidates=new ArrayList<>();
        try{List<Address> addresses=geocode(c,expected,location.getLatitude(),location.getLongitude());for(int i=0;i<Math.min(10,addresses.size());i++){HomeAddressPolicy.Candidate candidate=fromAddress(addresses.get(i));if(candidate!=null)candidates.add(candidate);}}catch(Exception unavailable){/* Manual entry and the independent map lookup remain available. */}
        if(!current(c,expected,revision))throw expired();
        try{candidates.addAll(map(c,expected,revision,location.getLatitude(),location.getLongitude()));}catch(Exception unavailable){/* Geocoder results remain useful if public maps are unavailable. */}
        if(!current(c,expected,revision))throw expired();List<HomeAddressPolicy.Choice> choices=HomeAddressPolicy.nearest(candidates,location.getLatitude(),location.getLongitude());LinkedHashMap<String,HomeAddressPolicy.Choice> identified=new LinkedHashMap<>();JSONArray rows=new JSONArray();
        for(HomeAddressPolicy.Choice choice:choices){String id=UUID.randomUUID().toString();identified.put(id,choice);rows.put(new JSONObject().put("id",id).put("address",choice.address()).put("distanceMeters",Math.round(choice.distance())));}
        String id=UUID.randomUUID().toString();HomeAddressPolicy.Session created=new HomeAddressPolicy.Session(id,revision,expected,location.getTime(),location.getElapsedRealtimeNanos()/1_000_000,capturedBoot,identified);
        int activeBoot=boot(c);if(!created.valid(LocationSharing.homeSearchRevision(c),expected,System.currentTimeMillis(),SystemClock.elapsedRealtime(),activeBoot))throw expired();
        synchronized(LOCK){if(epoch!=expected)throw expired();session=created;signal=null;mapCall=null;}
        return new JSONObject().put("requestId",id).put("candidates",rows).put("accuracyMeters",Math.ceil(location.getAccuracy())).put("expiresAt",location.getTime()+HomeAddressPolicy.MAX_AGE).put("note",choices.isEmpty()?"No nearby street addresses were found. Type your full home address instead.":"Nearby addresses are estimates. Confirm the correct address, or type it yourself.");
    }
    static JSONObject confirm(Context c,JSONObject request)throws Exception{
        Object rawRequest=request.opt("requestId"),rawCandidate=request.opt("candidateId");if(!(rawRequest instanceof String requestId)||!(rawCandidate instanceof String candidateId))throw new IllegalArgumentException("Choose an address from the current search.");
        synchronized(PilotApp.SEND_LOCK){
            HomeAddressPolicy.Session value;long active;synchronized(LOCK){value=session;active=epoch;}if(value==null)throw expired();long revision=LocationSharing.homeSearchRevision(c);int activeBoot=boot(c);
            HomeAddressPolicy.Choice choice=value.choose(requestId,candidateId,revision,active,System.currentTimeMillis(),SystemClock.elapsedRealtime(),activeBoot);
            // Keep token cancellation serialized with the short home commit.
            synchronized(LOCK){if(epoch!=active||session!=value)throw expired();LocationSharing.saveHomeCandidate(c,value,active,choice);epoch++;session=null;}
        }
        MessageChanges.publish();if(LocationSharing.enabled(c))LocationSharing.refresh(c);return LocationSharing.state(c);
    }
    private static IllegalStateException expired(){return new IllegalStateException("That address search expired. Search your current place again.");}
    static HomeAddressPolicy.Candidate fromAddress(Address a){
        if(a==null||!a.hasLatitude()||!a.hasLongitude())return null;String display=HomeAddressPolicy.address(a.getSubThoroughfare(),a.getThoroughfare(),a.getLocality(),a.getAdminArea(),a.getPostalCode(),a.getCountryName());return display.isEmpty()?null:new HomeAddressPolicy.Candidate(display,a.getLatitude(),a.getLongitude());
    }
    private static Location fix(Context c,long expected,long revision)throws Exception{
        LocationManager manager=c.getSystemService(LocationManager.class);if(manager==null)return null;long deadline=SystemClock.elapsedRealtime()+30_000;
        for(String provider:List.of(LocationManager.FUSED_PROVIDER,LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER)){
            if(!current(c,expected,revision))return null;if(!manager.hasProvider(provider)||!manager.isProviderEnabled(provider))continue;CancellationSignal next=new CancellationSignal();AtomicReference<Location> result=new AtomicReference<>();CountDownLatch returned=new CountDownLatch(1);synchronized(LOCK){if(epoch!=expected)return null;signal=next;fixReturned=returned;}
            try{manager.getCurrentLocation(provider,next,c.getMainExecutor(),location->{result.set(location);returned.countDown();});}catch(SecurityException revoked){next.cancel();return null;}
            long remaining=Math.min(10_000,deadline-SystemClock.elapsedRealtime());if(remaining>0)returned.await(remaining,TimeUnit.MILLISECONDS);next.cancel();Location found=result.get();int b=boot(c);
            if(found!=null&&found.hasAccuracy()&&HomeAddressPolicy.preciseFix(found.getLatitude(),found.getLongitude(),found.getAccuracy(),found.isMock())&&LocationPolicy.currentForHome(found.getTime(),found.getElapsedRealtimeNanos()/1_000_000,b,System.currentTimeMillis(),SystemClock.elapsedRealtime(),b))return found;if(SystemClock.elapsedRealtime()>=deadline)return null;
        }return null;
    }
    private static List<Address> geocode(Context c,long expected,double lat,double lon)throws Exception{
        if(!Geocoder.isPresent())return List.of();Geocoder geocoder=new Geocoder(c,Locale.getDefault());
        if(Build.VERSION.SDK_INT>=33){CompletableFuture<List<Address>> returned=new CompletableFuture<>();synchronized(LOCK){if(epoch!=expected)throw expired();lookup=returned;}try{geocoder.getFromLocation(lat,lon,10,new Geocoder.GeocodeListener(){@Override public void onGeocode(List<Address> values){returned.complete(values==null?List.of():values);}@Override public void onError(String ignored){returned.complete(List.of());}});return returned.get(8,TimeUnit.SECONDS);}finally{synchronized(LOCK){if(lookup==returned)lookup=null;}returned.cancel(true);}}
        Future<List<Address>> returned; synchronized(LOCK){if(epoch!=expected)throw expired();returned=GEOCODER.submit(()->geocoder.getFromLocation(lat,lon,10));lookup=returned;}
        try{List<Address> values=returned.get(8,TimeUnit.SECONDS);return values==null?List.of():values;}finally{synchronized(LOCK){if(lookup==returned)lookup=null;}returned.cancel(true);}
    }
    private static List<HomeAddressPolicy.Candidate> map(Context c,long expected,long revision,double lat,double lon)throws Exception{
        String query=String.format(Locale.ROOT,"[out:json][timeout:8];nwr(around:250,%.6f,%.6f)[\"addr:housenumber\"][\"addr:street\"];out center tags 200;",lat,lon);Call call=CLIENT.newCall(new Request.Builder().url("https://overpass-api.de/api/interpreter").header("Accept","application/json").header("User-Agent","ReplyPilot/1.0 (user-requested home address lookup)").post(new FormBody.Builder().add("data",query).build()).build());
        if(!current(c,expected,revision))throw expired();synchronized(LOCK){if(epoch!=expected)throw expired();mapCall=call;}
        try(Response response=call.execute()){
            if(response.code()!=200||response.body()==null||response.body().contentLength()>512*1024)return List.of();ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream input=response.body().byteStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(!current(c,expected,revision))throw expired();if(bytes.size()+count>512*1024)return List.of();bytes.write(buffer,0,count);}}
            JSONArray elements=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())).optJSONArray("elements");ArrayList<HomeAddressPolicy.Candidate> out=new ArrayList<>();if(elements==null)return out;
            for(int i=0;i<Math.min(200,elements.length());i++){JSONObject element=elements.optJSONObject(i);if(element==null)continue;JSONObject tags=element.optJSONObject("tags"),point=element.optJSONObject("center");if(tags==null)continue;if(point==null)point=element;String address=HomeAddressPolicy.address(tags.optString("addr:housenumber"),tags.optString("addr:street"),tags.optString("addr:city"),tags.optString("addr:state"),tags.optString("addr:postcode"),tags.optString("addr:country"));if(!address.isEmpty())out.add(new HomeAddressPolicy.Candidate(address,point.optDouble("lat",Double.NaN),point.optDouble("lon",Double.NaN)));}return out;
        }finally{synchronized(LOCK){if(mapCall==call)mapCall=null;}}
    }
}
