package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.telephony.PhoneNumberUtils;
import java.util.concurrent.*;
import org.json.*;

/** Settings are read independently of full history, never as permission to send. */
final class ReplyProfile {
    static final ExecutorService EXECUTOR=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(12),work->{Thread t=new Thread(work,"reply-pilot-contact-settings");t.setDaemon(true);return t;});
    private static final java.util.concurrent.atomic.AtomicLong EPOCH=new java.util.concurrent.atomic.AtomicLong();
    private ReplyProfile(){}
    static void changed(){EPOCH.incrementAndGet();}
    private static int access(Context c){return (Messages.allowed(c,Manifest.permission.READ_SMS)?1:0)|(Messages.role(c)?2:0)|(Messages.allowed(c,Manifest.permission.READ_CONTACTS)?4:0);}
    private static void requireAccess(Context c){if(!PilotApp.foreground||(access(c)&3)!=3)throw new IllegalStateException("Open Reply Pilot with SMS access to adjust reply settings.");}
    private static MediaNavigation.Destination destination(Context c,long thread)throws JSONException{
        requireAccess(c);SmsHistoryPolicy.validateThread(thread);
        // Resolve only the thread's canonical recipient. Do not load/read-mark messages.
        return MediaNavigation.destination(c,thread,new JSONObject());
    }
    static JSONObject read(Context c,long thread,String expectedAddress)throws JSONException{
        long epoch=EPOCH.get();int permission=access(c);MediaNavigation.Destination person=destination(c,thread);
        checkAddress(expectedAddress,person.address());
        JSONObject profile=Store.get(c).relationship(thread);
        JSONObject result=new JSONObject().put("thread",thread).put("address",person.address()).put("name",person.name()).put("photo",ContactPhotos.url(c,person.address())).put("contactPhotoRevision",ContactPhotos.revision()).put("readOnly",person.readOnly())
            .put("relationship",profile).put("profileRevision",profile.optLong("revision"))
            .put("replyEligibility",ReplyReadiness.forDisplay(c,thread,profile.optString("samples"),person.readOnly()))
            .put("approvedLearning",ApprovedLearning.state(c,thread)).put("persona",Personas.status(c,thread));
        result.put("pilotTraining",PilotTraining.counts(c,result));
        requireAccess(c);if(epoch!=EPOCH.get()||permission!=access(c))throw new IllegalStateException("Contact access changed. Open Reply setup again.");
        return result.put("epoch",epoch).put("access",new JSONObject().put("readSms",true).put("defaultSms",true).put("contacts",(permission&4)!=0));
    }
    static boolean canDeliver(Context c,JSONObject result){
        JSONObject stamp=result==null?null:result.optJSONObject("access");int permission=access(c);
        return PilotApp.foreground&&(permission&3)==3&&stamp!=null&&result.optLong("epoch",-1)==EPOCH.get()&&stamp.optBoolean("contacts")==((permission&4)!=0);
    }
    static void validateWrite(Context c,long thread,JSONObject request,JSONObject saved)throws JSONException{
        MediaNavigation.Destination person=destination(c,thread);
        if(person.readOnly()||person.address().isBlank())throw new IllegalStateException("Reply settings for automatic or AI replies need an individual contact.");
        if(request.has("expectedAddress"))checkAddress(request.getString("expectedAddress"),person.address());
        if(request.has("expectedRevision")&&MediaHistoryPolicy.integer(request.opt("expectedRevision"),false)!=saved.optLong("revision"))throw new IllegalStateException("These reply settings changed. Reopen Reply setup before saving.");
        requireAccess(c);
    }
    private static void checkAddress(String expected,String actual){
        if(expected!=null&&!expected.equals(actual)&&(!SendPolicy.validAddress(expected)||!SendPolicy.validAddress(actual)||!PhoneNumberUtils.compare(expected,actual)))throw new IllegalStateException("This contact changed. Refresh the conversation before editing reply settings.");
    }
}
