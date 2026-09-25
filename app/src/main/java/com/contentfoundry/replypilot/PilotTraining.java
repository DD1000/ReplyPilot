package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.telephony.PhoneNumberUtils;
import org.json.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Private, explicit practice and interpretation notes. No practice path writes SMS or creates a send. */
final class PilotTraining {
    private static final ConcurrentHashMap<Long,Practice> SESSIONS=new ConcurrentHashMap<>();
    private static final AtomicLong ACCESS_EPOCH=new AtomicLong();
    record AccessStamp(long epoch,int permissions){}
    private record Contact(long thread,String scope,String address,String name,String fingerprint){}
    private static final class Practice {
        final Contact contact;final PilotTrainingPolicy.Session state;final JSONObject profile,config;final JSONArray history;
        long generation;boolean busy;volatile String published;
        Practice(Contact contact,JSONObject profile,JSONObject config,JSONArray history,long revision){this.contact=contact;this.profile=profile;this.config=config;this.history=history;generation=revision;state=new PilotTrainingPolicy.Session(System.currentTimeMillis());publish(this);}
    }
    private PilotTraining(){}
    static void onPause(){accessChanged();}
    static void accessChanged(){ACCESS_EPOCH.incrementAndGet();}
    private static int permissions(Context c){return (Messages.allowed(c,Manifest.permission.READ_SMS)?1:0)|(Messages.role(c)?2:0)|(Messages.allowed(c,Manifest.permission.READ_CONTACTS)?4:0);}
    static AccessStamp captureAccess(Context c){requireAccess(c,true);return new AccessStamp(ACCESS_EPOCH.get(),permissions(c));}
    static boolean canDeliver(Context c,AccessStamp stamp){return stamp!=null&&PilotApp.foreground&&(stamp.permissions&3)==3&&stamp.epoch==ACCESS_EPOCH.get()&&stamp.permissions==permissions(c);}
    static void validateAccess(Context c,AccessStamp stamp){if(!canDeliver(c,stamp))throw new IllegalStateException("Contact access changed. Reopen Train Pilot.");}
    private static void expectedContact(Contact contact,String expectedAddress){
        if(expectedAddress!=null&&!expectedAddress.equals(contact.address)&&(!SendPolicy.validAddress(expectedAddress)||!PhoneNumberUtils.compare(expectedAddress,contact.address)))throw new IllegalStateException("This contact changed. Open the conversation again before training Pilot.");
    }
    private static void requireAccess(Context c,boolean foreground){
        if((foreground&&!PilotApp.foreground)||!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Open this chat with SMS access to use Train Pilot.");
    }
    private static String scope(String address){return MediaContextPolicy.signature(PhoneNumberUtils.normalizeNumber(address));}
    private static Contact contact(Context c,long thread,boolean foreground)throws JSONException{
        requireAccess(c,foreground);SmsHistoryPolicy.validateThread(thread);
        JSONObject sms=MediaNavigation.latestRow(c,thread,"sms"),mms=MediaNavigation.latestRow(c,thread,"mms");
        JSONObject newest=sms==null?mms:mms==null?sms:MediaHistoryPolicy.compare(new MediaHistoryPolicy.Position(sms.optLong("date"),"sms",sms.optLong("_id")),new MediaHistoryPolicy.Position(mms.optLong("date"),"mms",mms.optLong("_id")))>=0?sms:mms;
        if(newest==null)throw new IllegalStateException("Receive a message from this person before training Pilot.");
        MediaNavigation.Destination destination=MediaNavigation.destination(c,thread,newest);
        if(destination.readOnly()||destination.address().isBlank())throw new IllegalStateException("Train Pilot is available in individual chats.");
        requireAccess(c,foreground);
        return new Contact(thread,scope(destination.address()),destination.address(),destination.name(),MediaContextPolicy.signature(sms==null?"":fingerprint(sms),mms==null?"":fingerprint(mms)));
    }
    static JSONObject state(Context c,long thread,String expectedAddress)throws JSONException{
        Contact current=contact(c,thread,true);expectedContact(current,expectedAddress);
        // Reading an immutable published view never waits behind a send, a network
        // request or a settings write. Session mutations remain under SEND_LOCK.
        Practice practice=SESSIONS.get(thread);
        if(practice!=null&&(!same(current,practice.contact)||!PilotTrainingPolicy.fresh(practice.state.created,System.currentTimeMillis())))practice=null;
        return result(c,current,practice);
    }
    static JSONObject counts(Context c,long thread)throws JSONException{
        // Count alone carries no contact content and never starts a network request.
        if(!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))return emptyCounts();
        try{Contact current=contact(c,thread,false);return counts(c,current);}
        catch(IllegalStateException unavailable){return emptyCounts();}
    }
    static JSONObject counts(Context c,JSONObject page)throws JSONException{
        String address=page.optString("address");long thread=page.optLong("thread");
        if(thread<=0||address.isBlank()||page.optBoolean("readOnly")||!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS))return emptyCounts();
        return counts(c,new Contact(thread,scope(address),address,page.optString("name"),""));
    }
    private static JSONObject emptyCounts()throws JSONException{return new JSONObject().put("count",0).put("limit",PilotTrainingPolicy.EXAMPLES).put("meaningCount",0).put("meaningLimit",PilotTrainingPolicy.MEANINGS);}
    private static JSONObject counts(Context c,Contact contact)throws JSONException{
        JSONObject result=emptyCounts();result.put("count",examples(c,contact).length());
        JSONObject count=Store.get(c).query("SELECT COUNT(*) AS total FROM message_meanings WHERE thread=? AND scope=?",args(contact)).optJSONObject(0);
        return result.put("meaningCount",count==null?0:count.optInt("total"));
    }
    private static String[] args(Contact contact){return new String[]{Long.toString(contact.thread),contact.scope};}
    private static boolean same(Contact a,Contact b){return a.thread==b.thread&&a.scope.equals(b.scope)&&a.fingerprint.equals(b.fingerprint);}
    static JSONObject start(Context c,long thread,String expectedAddress,AccessStamp access)throws Exception{
        validateAccess(c,access);Contact captured=contact(c,thread,true);expectedContact(captured,expectedAddress);
        JSONArray history=PilotTrainingHistory.read(c,thread,captured.address,()->validateAccess(c,access));Practice practice;
        synchronized(PilotApp.SEND_LOCK){
            validateAccess(c,access);
            if(!same(captured,contact(c,thread,true)))throw new IllegalStateException("This conversation changed. Start practice again.");
            JSONObject config=CloudConfig.read(c);if(config==null)throw new IllegalStateException("Connect your AI service in Settings before training Pilot.");
            Store store=Store.get(c);practice=new Practice(captured,store.relationship(thread),config,history,store.generationRevision());
            SESSIONS.put(thread,practice);while(SESSIONS.size()>8){Practice oldest=Collections.min(SESSIONS.values(),Comparator.comparingLong(value->value.state.created));SESSIONS.remove(oldest.contact.thread,oldest);}
        }
        return generate(c,practice,access);
    }
    static JSONObject reply(Context c,long thread,String sessionId,String turnId,Object reply,String expectedAddress,AccessStamp access)throws Exception{
        validateAccess(c,access);Contact current=contact(c,thread,true);expectedContact(current,expectedAddress);Practice practice;boolean changed;
        synchronized(PilotApp.SEND_LOCK){
            validateAccess(c,access);
            practice=SESSIONS.get(thread);check(c,practice,current,sessionId);
            if(practice.busy)throw new IllegalStateException("Pilot is preparing the next practice message.");
            String incoming=practice.state.awaiting||practice.state.complete()?"":practice.state.incoming();
            // Validate without changing the session until the lesson transaction is durable.
            String answer=PilotTrainingPolicy.text(reply,false);
            boolean repeated=practice.state.acceptedToken.equals(turnId)&&!turnId.isEmpty();
            if(repeated){if(!practice.state.acceptedReply.equals(answer))throw new IllegalStateException("That answer was already saved. Start another scenario to try a different response.");changed=false;}
            else{
                if(practice.state.awaiting||practice.state.complete()||!practice.state.token.equals(turnId))throw new IllegalStateException("This practice turn changed. Reopen Train Pilot.");
                saveExample(c,practice.contact,turnId,incoming,answer);
                changed=practice.state.accept(turnId,answer);practice.generation=Store.get(c).generationRevision();publish(practice);
            }
            if(practice.state.complete()||(!changed&&!practice.state.awaiting))return result(c,current,practice);
        }
        return generate(c,practice,access);
    }
    private static void check(Context c,Practice practice,Contact current,String sessionId)throws Exception{
        requireAccess(c,true);
        if(practice==null||!practice.state.id.equals(sessionId)||!same(current,practice.contact)||!PilotTrainingPolicy.fresh(practice.state.created,System.currentTimeMillis()))throw new IllegalStateException("This conversation or practice changed. Start a new scenario.");
        JSONObject config=CloudConfig.read(c),profile=Store.get(c).relationship(current.thread);
        if(config==null||!config.optString("revision").equals(practice.config.optString("revision"))||profile.optLong("revision")!=practice.profile.optLong("revision")||Store.get(c).generationRevision()!=practice.generation)throw new IllegalStateException("Your reply settings changed. Start a new practice scenario.");
    }
    private static JSONObject generate(Context c,Practice practice,AccessStamp access)throws Exception{
        long epoch;JSONObject request;boolean ownsBusy=false;
        try{
            synchronized(PilotApp.SEND_LOCK){
                validateAccess(c,access);
                if(SESSIONS.get(practice.contact.thread)!=practice||practice.busy)throw new IllegalStateException("This practice changed. Reopen Train Pilot.");
                // Publish before checking provider rows or gathering meaning notes.
                // A panel reopened during those reads must show ongoing preparation.
                practice.busy=true;ownsBusy=true;publish(practice);
                check(c,practice,contact(c,practice.contact.thread,true),practice.state.id);
                if(!practice.state.awaiting||practice.state.complete()){practice.busy=false;publish(practice);return result(c,practice.contact,practice);}
                JSONArray turns=new JSONArray();for(var turn:practice.state.turns)turns.put(new JSONObject().put("speaker",turn.speaker()).put("text",turn.text()));
                request=new JSONObject().put("requestId",practice.state.requestId).put("relationship",ContactGuidance.context(practice.profile.optString("body"),practice.profile.optString("importantDetails"),practice.profile.optString("engagement")))
                    .put("samples",practice.profile.optString("samples")).put("tone",ContactGuidance.effectiveTone(practice.profile.optString("engagement")))
                    .put("styleMode","learned").put("engagement",practice.profile.optString("engagement","natural")).put("humorLevel",0).put("insideJokes","")
                    .put("history",practice.history).put("practice",turns);
                addContext(c,practice.contact,request);epoch=ACCESS_EPOCH.get();
            }
            validateAccess(c,access);
            JSONObject response=CloudClient.request(practice.config,"/train",request);
            synchronized(PilotApp.SEND_LOCK){
                validateAccess(c,access);
                check(c,practice,contact(c,practice.contact.thread,true),practice.state.id);
                if(SESSIONS.get(practice.contact.thread)!=practice||epoch!=ACCESS_EPOCH.get())throw new IllegalStateException("Practice paused when the app closed. Reopen Train Pilot to continue.");
                practice.state.generated(response.opt("scenario"),response.opt("message"));practice.busy=false;publish(practice);return result(c,practice.contact,practice);
            }
        }finally{
            // Preparation failures need the same retryable snapshot as a network
            // failure. A rejected concurrent caller must never clear another job.
            if(ownsBusy)synchronized(PilotApp.SEND_LOCK){practice.busy=false;publish(practice);}
        }
    }
    private static void publish(Practice practice){
        try{
            JSONArray messages=new JSONArray();for(var turn:practice.state.turns)messages.put(new JSONObject().put("role","me".equals(turn.speaker())?"you":"contact").put("text",turn.text()));
            practice.published=new JSONObject().put("sessionId",practice.state.id).put("turnId",practice.state.token).put("scenario",practice.state.scenario).put("messages",messages)
                .put("turns",practice.state.answers).put("complete",practice.state.complete()).put("awaitingNext",practice.state.awaiting).put("busy",practice.busy)
                .put("retryReply",practice.state.awaiting?practice.state.acceptedReply:"").toString();
        }catch(JSONException impossible){throw new IllegalStateException("Practice could not be saved.",impossible);}
    }
    private static JSONObject result(Context c,Contact contact,Practice practice)throws JSONException{
        JSONObject result=counts(c,contact).put("thread",contact.thread).put("address",contact.address).put("contactName",contact.name).put("maxTurns",PilotTrainingPolicy.MAX_TURNS).put("messages",new JSONArray()).put("sessionId","").put("turnId","").put("scenario","").put("turns",0).put("complete",false).put("awaitingNext",false).put("busy",false);
        if(practice!=null){JSONObject published=new JSONObject(practice.published);for(Iterator<String> keys=published.keys();keys.hasNext();){String key=keys.next();result.put(key,published.get(key));}}
        return result;
    }
    private static void saveExample(Context c,Contact contact,String turn,String incoming,String reply){
        SQLiteDatabase db=Store.get(c).getWritableDatabase();db.beginTransaction();try{
            ContentValues values=new ContentValues();values.put("thread",contact.thread);values.put("scope",contact.scope);values.put("turn",turn);values.put("incoming",incoming);values.put("reply",reply);values.put("created",System.currentTimeMillis());
            db.insertOrThrow("pilot_training",null,values);
            db.execSQL("DELETE FROM pilot_training WHERE thread=? AND scope=? AND _id NOT IN (SELECT _id FROM pilot_training WHERE thread=? AND scope=? ORDER BY _id DESC LIMIT "+PilotTrainingPolicy.EXAMPLES+")",new Object[]{contact.thread,contact.scope,contact.thread,contact.scope});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        learningChanged(c,contact.thread);
    }
    private static void learningChanged(Context c,long thread){
        Store store=Store.get(c);store.invalidateGeneration();
        String note="Pilot learned new context. Generate a fresh reply before automatic sending.";
        try{Sender.cancelAutomaticForThread(c,thread,note);}catch(RuntimeException unavailable){
            // Durable job states still fail closed if one alarm/notification cleanup fails.
            ContentValues paused=new ContentValues();paused.put("status","paused");paused.put("note",note);
            store.getWritableDatabase().update("jobs",paused,"thread=? AND auto_send=1 AND status IN ('scheduled','awaiting_alert')",new String[]{Long.toString(thread)});
        }
        // Preserve manually edited text while removing generated responses based on superseded context.
        store.getWritableDatabase().delete("drafts","thread=? AND engine<>'Edited by you'",new String[]{Long.toString(thread)});
        MessageChanges.publish();
    }
    static JSONObject clear(Context c,long thread,String expectedAddress,AccessStamp access)throws JSONException{
        validateAccess(c,access);Contact current=contact(c,thread,true);expectedContact(current,expectedAddress);synchronized(PilotApp.SEND_LOCK){
            validateAccess(c,access);
            if(!same(current,contact(c,thread,true)))throw new IllegalStateException("This conversation changed. Reopen Train Pilot.");
            SQLiteDatabase db=Store.get(c).getWritableDatabase();db.beginTransaction();try{db.delete("pilot_training","thread=? AND scope=?",args(current));db.setTransactionSuccessful();}finally{db.endTransaction();}
            SESSIONS.remove(thread);learningChanged(c,thread);return result(c,current,null);
        }
    }
    private static JSONArray examples(Context c,Contact contact)throws JSONException{
        JSONArray rows=Store.get(c).query("SELECT _id,thread,scope,incoming,reply FROM pilot_training WHERE thread=? AND scope=? ORDER BY _id DESC LIMIT "+PilotTrainingPolicy.EXAMPLES,args(contact));List<PilotTrainingPolicy.Example> choices=new ArrayList<>();
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);choices.add(new PilotTrainingPolicy.Example(row.optLong("_id"),row.optLong("thread"),row.optString("scope"),row.optString("incoming"),row.optString("reply")));}
        JSONArray result=new JSONArray();for(var row:PilotTrainingPolicy.examples(choices,contact.thread,contact.scope))result.put(new JSONObject().put("incoming",row.incoming()).put("reply",row.reply()));return result;
    }
    private static JSONObject received(Context c,long thread,long id,String transport)throws JSONException{
        if(!Set.of("sms","mms").contains(transport))throw new IllegalArgumentException("Choose a received text or media message.");
        JSONObject row=MediaNavigation.exact(c,transport,id,thread);
        if(row==null||!PilotTrainingPolicy.received(thread,row.optLong("thread_id"),id,row.optLong("_id"),transport,row.optInt("type"),row.optInt("m_type")))throw new IllegalStateException("This received message is no longer available.");
        if("mms".equals(transport))MediaNavigation.parts(c,row);return row;
    }
    private static String fingerprint(JSONObject row){
        return MediaContextPolicy.signature(row.optString("kind","sms"),row.optString("_id"),row.optString("thread_id"),row.optString("date"),row.optString("type"),row.optString("body"),row.optString("address"),row.has("parts")?row.optJSONArray("parts").toString():"");
    }
    static JSONObject explain(Context c,long thread,long id,String transport,String expectedToken,Object raw)throws JSONException{
        String meaning=PilotTrainingPolicy.text(raw,true);Contact captured=contact(c,thread,true);JSONObject original=received(c,thread,id,transport);String stamp=fingerprint(original);
        if(expectedToken==null||!expectedToken.equals(stamp))throw new IllegalStateException("This message changed since you opened it. Reopen the message before explaining its meaning.");
        synchronized(PilotApp.SEND_LOCK){
            if(!same(captured,contact(c,thread,true))||!stamp.equals(fingerprint(received(c,thread,id,transport))))throw new IllegalStateException("The message changed. Open it again before saving its meaning.");
            SQLiteDatabase db=Store.get(c).getWritableDatabase();db.beginTransaction();try{
                if(meaning.isEmpty())db.delete("message_meanings","thread=? AND scope=? AND kind=? AND message_id=?",new String[]{Long.toString(thread),captured.scope,transport,Long.toString(id)});
                else{
                    ContentValues v=new ContentValues();v.put("thread",thread);v.put("scope",captured.scope);v.put("kind",transport);v.put("message_id",id);v.put("fingerprint",stamp);v.put("message",PilotTrainingPolicy.clipped(original.optString("body").isBlank()?"[Media message]":original.optString("body")));v.put("meaning",meaning);v.put("created",System.currentTimeMillis());
                    if(db.insertWithOnConflict("message_meanings",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new IllegalStateException("This meaning could not be saved. Please try again.");
                    db.execSQL("DELETE FROM message_meanings WHERE thread=? AND scope=? AND rowid NOT IN (SELECT rowid FROM message_meanings WHERE thread=? AND scope=? ORDER BY created DESC,rowid DESC LIMIT "+PilotTrainingPolicy.MEANINGS+")",new Object[]{thread,captured.scope,thread,captured.scope});
                }db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            SESSIONS.remove(thread);learningChanged(c,thread);
            return new JSONObject().put("messageId",id).put("transport",transport).put("meaning",meaning).put("pilotTraining",counts(c,captured));
        }
    }
    static JSONObject addContext(Context c,long thread,JSONObject request)throws JSONException{return addContext(c,contact(c,thread,false),request);}
    private static JSONObject addContext(Context c,Contact contact,JSONObject request)throws JSONException{
        JSONArray meanings=new JSONArray(),saved=Store.get(c).query("SELECT * FROM message_meanings WHERE thread=? AND scope=? ORDER BY created DESC LIMIT "+PilotTrainingPolicy.MEANINGS,args(contact));
        for(int i=0;i<saved.length();i++){
            JSONObject note=saved.getJSONObject(i);
            try{JSONObject actual=received(c,contact.thread,note.optLong("message_id"),note.optString("kind"));if(note.optString("fingerprint").equals(fingerprint(actual))){String message=PilotTrainingPolicy.clipped(note.optString("message")),meaning=PilotTrainingPolicy.clipped(note.optString("meaning"));if(!message.isBlank()&&!meaning.isBlank())meanings.put(new JSONObject().put("message",message).put("meaning",meaning));}}
            catch(IllegalStateException unavailable){/* Deleted or changed notes cannot become invented evidence. */}
        }
        requireAccess(c,false);return request.put("pilotTraining",examples(c,contact)).put("messageMeanings",meanings);
    }
    static JSONObject annotate(Context c,JSONObject page)throws JSONException{
        JSONArray history=page.optJSONArray("history");String address=page.optString("address");long thread=page.optLong("thread");
        if(history==null||thread<=0||address.isBlank())return page;
        JSONArray saved=Store.get(c).query("SELECT kind,message_id,fingerprint,meaning FROM message_meanings WHERE thread=? AND scope=?",new String[]{Long.toString(thread),scope(address)});Map<String,JSONObject> notes=new HashMap<>();
        for(int i=0;i<saved.length();i++){JSONObject note=saved.getJSONObject(i);notes.put(note.optString("kind")+":"+note.optLong("message_id"),note);}
        for(int i=0;i<history.length();i++){JSONObject row=history.optJSONObject(i);if(row==null)continue;String transport=row.optString("kind","sms");if(!PilotTrainingPolicy.received(thread,row.optLong("thread_id"),row.optLong("_id"),row.optLong("_id"),transport,row.optInt("type"),row.optInt("m_type")))continue;String token=fingerprint(row);row.put("meaningToken",token);JSONObject note=notes.get(transport+":"+row.optLong("_id"));if(note!=null&&note.optString("fingerprint").equals(token))row.put("meaning",note.optString("meaning"));}
        return page;
    }
}
