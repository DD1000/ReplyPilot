package com.contentfoundry.replypilot;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import org.json.*;

/** Latest optional phone location only. No history, coordinates in UI, or send-lock calls. */
final class LocationSharing {
    static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"reply-pilot-location");t.setDaemon(true);return t;});
    static final ExecutorService HOME_EXECUTOR=HomeAddressSearch.EXECUTOR;
    private static final ExecutorService GEOCODER=new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"reply-pilot-geocoder");t.setDaemon(true);return t;});
    private static final Object LOCK=new Object();
    private static final int JOB_ID=900001,MAX_RESPONSE=512*1024;
    private static final OkHttpClient MAP_CLIENT=new OkHttpClient.Builder().callTimeout(10,TimeUnit.SECONDS).connectTimeout(10,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    private static boolean refreshing,cancelled,refreshAgain;
    private static long operation,activeRevision;
    private static CancellationSignal cancellation;
    private static Call mapCall;
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("location_sharing",Context.MODE_PRIVATE);}
    private static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    private static boolean permission(Context c){return Messages.allowed(c,Manifest.permission.ACCESS_COARSE_LOCATION)||precise(c);}
    private static boolean precise(Context c){return Messages.allowed(c,Manifest.permission.ACCESS_FINE_LOCATION);}
    private static boolean background(Context c){return Messages.allowed(c,Manifest.permission.ACCESS_BACKGROUND_LOCATION);}
    private static boolean services(Context c){try{LocationManager m=c.getSystemService(LocationManager.class);return m!=null&&m.isLocationEnabled();}catch(RuntimeException unavailable){return false;}}
    private static JSONObject read(Context c){try{return new JSONObject(prefs(c).getString("state","{}"));}catch(JSONException invalid){return new JSONObject();}}
    private static JSONObject put(JSONObject state,Object... values){try{for(int i=0;i<values.length;i+=2)state.put((String)values[i],values[i+1]);return state;}catch(JSONException invalid){throw new IllegalStateException("Location settings could not be saved.");}}
    private static void write(Context c,JSONObject state){
        SharedPreferences p=prefs(c);String old=p.getString("state","{}");
        if(!p.edit().putString("state",state.toString()).commit()){
            try{p.edit().putString("state",old).commit();}catch(RuntimeException ignored){}
            throw new IllegalStateException("Location settings could not be saved. Try again.");
        }
    }
    private static void bump(JSONObject state){put(state,"revision",Math.addExact(state.optLong("revision"),1));}
    private static void clearFix(JSONObject state){for(String key:List.of("label","captured","elapsed","boot","latitude","longitude","accuracy","precise"))state.remove(key);}
    private static boolean freshFix(Context c,JSONObject s){int b=boot(c);long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();return LocationPolicy.valid(s.optBoolean("enabled"),permission(c),services(c),precise(c),s.optBoolean("precise"),s.optLong("revision"),s.optLong("revision"),s.optLong("captured")+LocationPolicy.MAX_AGE_MS,s.optLong("captured"),s.optLong("elapsed"),s.optInt("boot",-1),now,elapsed,b);}
    private static boolean fresh(Context c,JSONObject s){return !s.optString("label").isBlank()&&freshFix(c,s);}
    static boolean enabled(Context c){synchronized(LOCK){return read(c).optBoolean("enabled");}}
    static long revision(Context c){synchronized(LOCK){return read(c).optLong("revision");}}
    static long homeSearchRevision(Context c){synchronized(LOCK){JSONObject s=read(c);return PilotApp.foreground&&s.optBoolean("enabled")&&precise(c)&&services(c)?s.optLong("revision"):-1;}}
    static JSONObject requestCurrentHomeCandidates(Context c)throws Exception{return HomeAddressSearch.request(c);}
    static JSONObject confirmHomeCandidate(Context c,JSONObject request)throws Exception{return HomeAddressSearch.confirm(c,request);}
    static void cancelHomeCandidates(){HomeAddressSearch.cancel();}
    static boolean homeCandidatesCanDeliver(Context c,JSONObject result){return HomeAddressSearch.canDeliver(c,result);}
    static void saveHomeCandidate(Context c,HomeAddressPolicy.Session selected,long candidateEpoch,HomeAddressPolicy.Choice choice){
        synchronized(PilotApp.SEND_LOCK){synchronized(LOCK){JSONObject s=read(c);int b=boot(c);if(!selected.valid(homeSearchRevision(c),candidateEpoch,System.currentTimeMillis(),SystemClock.elapsedRealtime(),b))throw new IllegalStateException("Location settings changed or the search expired. Search your current place again.");bump(s);clearFix(s);put(s,"homeSet",true,"homeAddress",choice.address(),"homeLatitude",choice.latitude(),"homeLongitude",choice.longitude(),"status","Home saved. Refreshing your location.");write(c,s);}}
    }
    static JSONObject state(Context c){synchronized(LOCK){
        JSONObject s=read(c);boolean fresh=freshFix(c,s);String status=s.optString("status","Location is off.");
        if(s.optBoolean("enabled")){if(!permission(c))status="Allow location access to update your whereabouts.";else if(!services(c))status="Turn on your phone’s Location setting.";else if(!fresh&&!refreshing&&s.optLong("captured")>0)status="Location is out of date. Refresh it before sharing.";}
        return put(new JSONObject(),"enabled",s.optBoolean("enabled"),"permission",permission(c),"precise",precise(c),"backgroundPermission",background(c),"refreshing",refreshing,"label",fresh?s.optString("label"):"","updatedAt",s.optLong("captured"),"fresh",fresh,"homeSet",s.optBoolean("homeSet"),"homeAddress",s.optString("homeAddress"),"status",status);
    }}
    static JSONObject context(Context c){synchronized(LOCK){JSONObject s=read(c);if(!fresh(c,s))return null;return put(new JSONObject(),"label",s.optString("label"),"capturedAt",s.optLong("captured"),"expiresAt",s.optLong("captured")+LocationPolicy.MAX_AGE_MS,"revision",s.optLong("revision"));}}
    static boolean valid(Context c,long expectedRevision,long expires){synchronized(LOCK){JSONObject s=read(c);int b=boot(c);return !s.optString("label").isBlank()&&LocationPolicy.valid(s.optBoolean("enabled"),permission(c),services(c),precise(c),s.optBoolean("precise"),expectedRevision,s.optLong("revision"),expires,s.optLong("captured"),s.optLong("elapsed"),s.optInt("boot",-1),System.currentTimeMillis(),SystemClock.elapsedRealtime(),b);}}
    static void save(Context c,boolean enabled){
        synchronized(PilotApp.SEND_LOCK){synchronized(LOCK){JSONObject s=read(c);if(s.optBoolean("enabled")!=enabled){bump(s);put(s,"enabled",enabled,"status",enabled?"Ready to update your location.":"Location is off.");clearFix(s);write(c,s);}if(!enabled){cancelled=true;refreshAgain=false;if(cancellation!=null)cancellation.cancel();if(mapCall!=null)mapCall.cancel();}}}
        cancelHomeCandidates();reconcile(c);MessageChanges.publish();if(enabled)refresh(c);
    }
    static void clearHome(Context c){synchronized(PilotApp.SEND_LOCK){synchronized(LOCK){JSONObject s=read(c);bump(s);for(String k:List.of("homeSet","homeAddress","homeLatitude","homeLongitude"))s.remove(k);clearFix(s);put(s,"status","Home removed. Refresh location to update your whereabouts.");write(c,s);}}cancelHomeCandidates();MessageChanges.publish();if(enabled(c))refresh(c);}
    static void setHome(Context c,JSONObject request)throws Exception{
        long expected;JSONObject before;synchronized(LOCK){before=read(c);expected=before.optLong("revision");}
        double lat,lon;String display;
        if(request.optBoolean("useCurrent"))throw new IllegalStateException("Find nearby addresses and confirm the correct one before saving home.");
        else{
            Object raw=request.opt("address");if(!(raw instanceof String address)||address.isBlank()||address.length()>300)throw new IllegalArgumentException("Enter a home address within 300 characters.");
            display=LocationPolicy.clean(address,300);List<Address> results=geocode(c,display,0,0);
            if(results.size()!=1||!results.get(0).hasLatitude()||!results.get(0).hasLongitude())throw new IllegalStateException("That address was not specific enough. Add the street, city, and postal code, then try again.");
            if(results.get(0).getThoroughfare()==null||results.get(0).getThoroughfare().isBlank()||results.get(0).getSubThoroughfare()==null||results.get(0).getSubThoroughfare().isBlank())throw new IllegalStateException("Enter a full street address with its number, or save your current location as home.");
            HomeAddressPolicy.Candidate chosen=HomeAddressSearch.fromAddress(results.get(0));if(chosen==null)throw new IllegalStateException("Enter a full street address with its number, city, and postal code.");lat=chosen.latitude();lon=chosen.longitude();
        }
        if(!LocationPolicy.coordinates(lat,lon))throw new IllegalStateException("Home could not be located. Try a more specific address.");
        synchronized(PilotApp.SEND_LOCK){synchronized(LOCK){JSONObject s=read(c);if(s.optLong("revision")!=expected)throw new IllegalStateException("Location settings changed. Save home again.");bump(s);clearFix(s);put(s,"homeSet",true,"homeAddress",display,"homeLatitude",lat,"homeLongitude",lon,"status","Home saved. Refreshing your location.");write(c,s);}}
        cancelHomeCandidates();MessageChanges.publish();if(enabled(c))refresh(c);
    }
    static void reconcile(Context c){
        JobScheduler jobs=c.getSystemService(JobScheduler.class);if(jobs==null)return;
        boolean schedule=enabled(c)&&permission(c)&&background(c);JobInfo existing=jobs.getPendingJob(JOB_ID);ComponentName service=new ComponentName(c,LocationJob.class);
        if(!schedule){if(existing!=null)jobs.cancel(JOB_ID);}
        else if(existing==null||!service.equals(existing.getService())||!existing.isPeriodic()||existing.getIntervalMillis()!=LocationPolicy.INTERVAL_MS)jobs.schedule(new JobInfo.Builder(JOB_ID,service).setPeriodic(LocationPolicy.INTERVAL_MS).setPersisted(true).build());
        boolean due;synchronized(LOCK){JSONObject s=read(c);int b=boot(c);due=LocationPolicy.due(s.optLong("attemptWall"),s.optLong("attemptElapsed"),s.optInt("attemptBoot",-1),System.currentTimeMillis(),SystemClock.elapsedRealtime(),b);}
        if(PilotApp.foreground&&enabled(c)&&due)refresh(c);
    }
    static void refresh(Context c){refresh(c,null);}
    static long refresh(Context supplied,Runnable complete){
        Context c=supplied.getApplicationContext();long id,expected;
        synchronized(LOCK){
            JSONObject s=read(c);
            if(!s.optBoolean("enabled")||!permission(c)||!services(c)||(!PilotApp.foreground&&!background(c))){if(complete!=null)complete.run();return 0;}
            if(refreshing){if(s.optLong("revision")!=activeRevision)refreshAgain=true;if(complete!=null)complete.run();return 0;}
            expected=s.optLong("revision");int b=boot(c);put(s,"attemptWall",System.currentTimeMillis(),"attemptElapsed",SystemClock.elapsedRealtime(),"attemptBoot",b,"status","Updating your location…");write(c,s);
            id=++operation;activeRevision=expected;refreshing=true;cancelled=false;
        }
        MessageChanges.publish();EXECUTOR.execute(()->{try{capture(c,id,expected);}finally{
            boolean again=false;synchronized(LOCK){if(id==operation){refreshing=false;cancellation=null;mapCall=null;again=refreshAgain;refreshAgain=false;}}
            MessageChanges.publish();if(complete!=null)complete.run();if(again)refresh(c);
        }});return id;
    }
    static void cancel(long id){synchronized(LOCK){if(id!=0&&id==operation){cancelled=true;if(cancellation!=null)cancellation.cancel();if(mapCall!=null)mapCall.cancel();}}}
    private static boolean mayCapture(Context c,long id,long expected){synchronized(LOCK){JSONObject s=read(c);return id==operation&&!cancelled&&s.optBoolean("enabled")&&s.optLong("revision")==expected&&permission(c)&&services(c)&&(PilotApp.foreground||background(c));}}
    private static void capture(Context c,long id,long expected){
        try{
            boolean fine=precise(c);Location location=fix(c,id,expected,fine);
            if(location==null||!location.hasAccuracy()||!LocationPolicy.usable(location.getLatitude(),location.getLongitude(),location.getAccuracy(),location.isMock()))throw new IllegalStateException("A reliable location was not available. Try again outdoors.");
            int capturedBoot=boot(c);long captured=location.getTime(),elapsed=location.getElapsedRealtimeNanos()/1_000_000;
            if(!LocationPolicy.fresh(captured,elapsed,capturedBoot,System.currentTimeMillis(),SystemClock.elapsedRealtime(),capturedBoot))throw new IllegalStateException("The phone returned an old location. Try again.");
            if(!mayCapture(c,id,expected)||(fine&&!precise(c)))return;
            double lat=location.getLatitude(),lon=location.getLongitude(),accuracy=location.getAccuracy();boolean specific=LocationPolicy.precise(accuracy,fine,false),atHome=false;JSONObject s;
            synchronized(LOCK){s=read(c);}
            if(specific&&s.optBoolean("homeSet"))atHome=LocationPolicy.home(LocationPolicy.distance(lat,lon,s.optDouble("homeLatitude",Double.NaN),s.optDouble("homeLongitude",Double.NaN)),accuracy,true);
            String city="",place="";
            if(!atHome){if(!mayCapture(c,id,expected)||(fine&&!precise(c)))return;try{List<Address> addresses=geocode(c,null,lat,lon);if(!addresses.isEmpty()){Address address=addresses.get(0);city=address.getLocality();if(city==null||city.isBlank())city=address.getSubAdminArea();if(city==null||city.isBlank())city=address.getAdminArea();}}catch(Exception ignored){/* A failed lookup supplies no old label. */}
                if(specific&&mayCapture(c,id,expected)&&reserveMap(c,id,expected))try{place=landmark(c,id,expected,lat,lon,accuracy);}catch(Exception ignored){/* A fresh city can still be useful. */}
            }
            String label=LocationPolicy.label(place,city,atHome);
            // Serialize only the final revision change with carrier submission.
            // Capture and all geocoder/map work above never hold SEND_LOCK.
            synchronized(PilotApp.SEND_LOCK){synchronized(LOCK){
                if(!mayCapture(c,id,expected)||(fine&&!precise(c)))return;s=read(c);bump(s);
                put(s,"latitude",lat,"longitude",lon,"accuracy",accuracy,"precise",fine,"captured",captured,"elapsed",elapsed,"boot",capturedBoot,"label",label,"status",label.isEmpty()?"Location found, but a nearby place could not be identified.":"Location updated.");write(c,s);
            }}
        }catch(Exception error){synchronized(PilotApp.SEND_LOCK){synchronized(LOCK){if(!mayCapture(c,id,expected))return;JSONObject s=read(c);bump(s);clearFix(s);put(s,"status",error instanceof IllegalStateException?error.getMessage():"Location could not be updated. Try again.");write(c,s);}}}
    }
    private static Location fix(Context c,long id,long expected,boolean fine)throws Exception{
        LocationManager manager=c.getSystemService(LocationManager.class);if(manager==null)return null;
        long deadline=SystemClock.elapsedRealtime()+30_000;
        for(String provider:List.of(LocationManager.FUSED_PROVIDER,LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER)){
            if(!mayCapture(c,id,expected)||(!fine&&LocationManager.GPS_PROVIDER.equals(provider)))return null;
            if(!manager.hasProvider(provider)||!manager.isProviderEnabled(provider))continue;
            CancellationSignal signal=new CancellationSignal();synchronized(LOCK){if(!mayCapture(c,id,expected))return null;cancellation=signal;}
            AtomicReference<Location> result=new AtomicReference<>();CountDownLatch returned=new CountDownLatch(1);
            try{manager.getCurrentLocation(provider,signal,c.getMainExecutor(),location->{result.set(location);returned.countDown();});}
            catch(SecurityException revoked){signal.cancel();return null;}
            long remaining=deadline-SystemClock.elapsedRealtime();if(remaining>0)returned.await(remaining,TimeUnit.MILLISECONDS);signal.cancel();
            if(result.get()!=null)return result.get();if(SystemClock.elapsedRealtime()>=deadline)return null;
        }
        return null;
    }
    private static List<Address> geocode(Context c,String address,double lat,double lon)throws Exception{
        if(!Geocoder.isPresent())return List.of();Geocoder geocoder=new Geocoder(c,Locale.getDefault());
        if(Build.VERSION.SDK_INT>=33){
            CompletableFuture<List<Address>> future=new CompletableFuture<>();Geocoder.GeocodeListener listener=new Geocoder.GeocodeListener(){@Override public void onGeocode(List<Address> values){future.complete(values==null?List.of():values);}@Override public void onError(String ignored){future.complete(List.of());}};
            if(address==null)geocoder.getFromLocation(lat,lon,1,listener);else geocoder.getFromLocationName(address,3,listener);
            return future.get(8,TimeUnit.SECONDS);
        }
        Future<List<Address>> future=GEOCODER.submit(()->address==null?geocoder.getFromLocation(lat,lon,1):geocoder.getFromLocationName(address,3));
        try{List<Address> result=future.get(8,TimeUnit.SECONDS);return result==null?List.of():result;}finally{future.cancel(true);}
    }
    private static boolean reserveMap(Context c,long id,long expected){synchronized(LOCK){
        if(!mayCapture(c,id,expected))return false;JSONObject s=read(c);int b=boot(c);long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();
        if(!LocationPolicy.due(s.optLong("mapWall"),s.optLong("mapElapsed"),s.optInt("mapBoot",-1),now,elapsed,b))return false;
        put(s,"mapWall",now,"mapElapsed",elapsed,"mapBoot",b);write(c,s);return true;
    }}
    private static String landmark(Context c,long id,long expected,double lat,double lon,double accuracy)throws Exception{
        String query=String.format(Locale.ROOT,"[out:json][timeout:8];nwr(around:800,%.5f,%.5f)[name][~\"^(shop|amenity)$\"~\".\"];out center tags 30;",lat,lon);
        Call call=MAP_CLIENT.newCall(new Request.Builder().url("https://overpass-api.de/api/interpreter").header("Accept","application/json").header("User-Agent","ReplyPilot/1.0 (optional location labels)").post(new FormBody.Builder().add("data",query).build()).build());
        synchronized(LOCK){if(!mayCapture(c,id,expected)||!precise(c))return "";mapCall=call;}
        try(Response response=call.execute()){
            if(response.code()!=200||response.body()==null||response.body().contentLength()>MAX_RESPONSE)return "";
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream input=response.body().byteStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(bytes.size()+count>MAX_RESPONSE)throw new IllegalStateException("Place response was too large.");bytes.write(buffer,0,count);}}
            JSONArray elements=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())).optJSONArray("elements");String best="";double nearest=Double.POSITIVE_INFINITY;
            if(elements!=null)for(int i=0;i<Math.min(100,elements.length());i++){
                JSONObject element=elements.optJSONObject(i);if(element==null)continue;JSONObject tags=element.optJSONObject("tags"),point=element.optJSONObject("center");if(point==null)point=element;
                if(tags==null||!(tags.has("shop")||tags.has("amenity")))continue;String name=LocationPolicy.clean(tags.optString("name"),100);
                double distance=LocationPolicy.distance(lat,lon,point.optDouble("lat",Double.NaN),point.optDouble("lon",Double.NaN));
                if(!name.isEmpty()&&distance+accuracy<=800&&distance<nearest){nearest=distance;best=name;}
            }
            return best;
        }finally{synchronized(LOCK){if(mapCall==call)mapCall=null;}}
    }
}
