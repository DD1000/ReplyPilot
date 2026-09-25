package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.telephony.PhoneNumberUtils;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Incoming Sharesheet content is an offer until the owner chooses Add to draft. */
final class ShareInbox {
    static final ExecutorService EXECUTOR=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),work->{Thread thread=new Thread(work,"reply-pilot-share");thread.setDaemon(true);return thread;});
    private static final Object LOCK=new Object();
    private static Session active;
    private static final String PREFS="consumed_shares";
    private static final class Session {
        final String id,text;final List<Uri> uris;String status="pending",error="";JSONObject result;boolean cancelled,retriable=true;
        Session(String id,String text,List<Uri> uris){this.id=id;this.text=text;this.uris=List.copyOf(uris);}
    }
    private ShareInbox(){}
    static boolean handles(String action){return "shareState".equals(action)||"cancelShare".equals(action)||"importShare".equals(action);}
    static boolean isShare(Intent intent){return intent!=null&&(Intent.ACTION_SEND.equals(intent.getAction())||Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()));}
    @SuppressWarnings("deprecation")
    static void receive(Context c,Intent intent){
        Session selected;
        try{
            if(!isShare(intent)||!SharePolicy.mime(intent.getType()))throw new IllegalArgumentException("Share text, photos, videos, audio, or a contact card.");
            CharSequence extra=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);String text=SharePolicy.text(extra);
            List<String> raw=new ArrayList<>();
            if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())){
                ArrayList<?> values=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if(values!=null){if(values.size()>SharePolicy.MAX_CLIP_ITEMS)throw new IllegalArgumentException("Share up to six attachments at a time.");for(Object value:values){if(!(value instanceof Uri))throw new IllegalArgumentException("This shared attachment is invalid.");raw.add(value.toString());}}
            }else{
                Object value=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(value!=null){if(!(value instanceof Uri))throw new IllegalArgumentException("This shared attachment is invalid.");raw.add(value.toString());}
            }
            ClipData clip=intent.getClipData();
            if(clip!=null){
                if(clip.getItemCount()>SharePolicy.MAX_CLIP_ITEMS)throw new IllegalArgumentException("Share up to six attachments at a time.");
                // EXTRA_STREAM is authoritative; ClipData can instead be a preview
                // thumbnail for a shared URL and must not silently become an MMS.
                if(raw.isEmpty()&&!"text/plain".equals(intent.getType()))for(int i=0;i<clip.getItemCount();i++){Uri uri=clip.getItemAt(i).getUri();if(uri!=null)raw.add(uri.toString());}
                if(extra==null&&clip.getItemCount()==1)text=SharePolicy.text(clip.getItemAt(0).getText());
            }
            if(!raw.isEmpty()&&(intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0)throw new IllegalArgumentException("The original app did not grant attachment access. Share it again.");
            List<Uri> uris=new ArrayList<>();for(String value:SharePolicy.uris(raw,c.getPackageName())){Uri uri=Uri.parse(value);requireGrant(c,uri);uris.add(uri);}
            if(text.isBlank()&&uris.isEmpty())throw new IllegalArgumentException("The share did not contain text or a supported attachment.");
            selected=new Session(UUID.randomUUID().toString(),text,uris);
        }catch(RuntimeException invalid){selected=new Session(UUID.randomUUID().toString(),"",List.of());selected.status="error";selected.retriable=false;selected.error=invalid instanceof IllegalArgumentException?invalid.getMessage():"This share could not be read. Share it again from the original app.";}
        synchronized(LOCK){if(active!=null)active.cancelled=true;active=selected;}
    }
    private static void requireGrant(Context c,Uri uri){
        SharePolicy.uri(uri.toString(),c.getPackageName());
        // This overload checks explicit URI grants only. Broad READ_SMS or
        // READ_CONTACTS permissions must never authorize an external share.
        if(c.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)!=PackageManager.PERMISSION_GRANTED)throw new IllegalArgumentException("The original app did not grant access to this attachment. Share it again.");
    }
    static JSONObject state(Context c)throws JSONException{
        if(!PilotApp.foreground)return new JSONObject();
        synchronized(LOCK){if(active==null||active.cancelled)return new JSONObject();Session value=active;JSONObject out=new JSONObject().put("id",value.id).put("text",value.text).put("attachmentCount",value.uris.size()).put("status",value.status).put("error",value.error).put("canImport",value.retriable&&(!value.text.isEmpty()||!value.uris.isEmpty()));if(value.result!=null&&PilotApp.foreground&&Messages.role(c)&&Messages.allowed(c,Manifest.permission.READ_SMS))out.put("thread",value.result.optLong("thread")).put("address",value.result.optString("address")).put("result",new JSONObject(value.result.toString()));return out;}
    }
    static boolean canDeliver(Context c,JSONObject result){
        synchronized(LOCK){return PilotApp.foreground&&Messages.role(c)&&Messages.allowed(c,Manifest.permission.READ_SMS)&&active!=null&&!active.cancelled&&"imported".equals(active.status)&&active.id.equals(result.optString("id"))&&active.result!=null&&active.result.optLong("thread")==result.optLong("thread");}
    }
    static JSONObject cancel(Context c,String id)throws JSONException{
        synchronized(LOCK){if(active!=null&&active.id.equals(id)){remember(c,id);active.cancelled=true;active=null;}return new JSONObject();}
    }
    static Bundle save(){
        synchronized(LOCK){Bundle out=new Bundle();if(active==null||active.cancelled)return out;Session value=active;out.putString("id",value.id);out.putString("text",value.text);ArrayList<String> uris=new ArrayList<>();for(Uri uri:value.uris)uris.add(uri.toString());out.putStringArrayList("uris",uris);out.putString("status",value.status);out.putString("error",value.error);return out;}
    }
    static void restore(Context c,Bundle saved){
        if(saved==null)return;String id=saved.getString("id","");if(!MmsSendPolicy.id(id))return;
        synchronized(LOCK){if(active!=null)return;try{
            boolean consumed=c.getSharedPreferences(PREFS,0).getStringSet("ids",Set.of()).contains(id);
            List<Uri> uris=new ArrayList<>();ArrayList<String> raw=saved.getStringArrayList("uris");for(String value:SharePolicy.uris(raw==null?List.of():raw,c.getPackageName())){Uri uri=Uri.parse(value);if(!consumed)requireGrant(c,uri);uris.add(uri);}
            active=new Session(id,SharePolicy.text(saved.getString("text","")),uris);
            if(consumed||"importing".equals(saved.getString("status"))||"imported".equals(saved.getString("status"))){active.status="error";active.retriable=false;active.error="This share was already added or interrupted. Check your draft before sharing it again.";}
            else if("error".equals(saved.getString("status"))){active.status="error";active.error=saved.getString("error","Share this item again.");}
        }catch(RuntimeException unavailable){active=new Session(id,"",List.of());active.status="error";active.retriable=false;active.error="Attachment access expired. Share it again from the original app.";}}
    }
    private static void remember(Context c,String id){
        var preferences=c.getSharedPreferences(PREFS,0);Set<String> ids=new LinkedHashSet<>(preferences.getStringSet("ids",Set.of()));ids.add(id);while(ids.size()>128)ids.remove(ids.iterator().next());
        if(!preferences.edit().putStringSet("ids",ids).commit())throw new IllegalStateException("The share could not be saved safely. Try again.");
    }
    private static void current(Session session){synchronized(LOCK){if(active!=session||session.cancelled)throw new IllegalStateException("This share was closed or replaced. Open the latest share.");}}
    private static String draftBody(Context c,long thread,long base){JSONObject draft=Store.get(c).draft(thread,base);return draft==null?"":draft.optString("body");}
    private static void access(Context c){
        if(!PilotApp.foreground||!Messages.role(c)||!Messages.allowed(c,Manifest.permission.READ_SMS)||!Messages.allowed(c,Manifest.permission.SEND_SMS))throw new IllegalStateException("Open Reply Pilot with messaging access before adding this share.");
    }
    private static void check(Context c,Session session,long thread,String address,long base,String existing){
        current(session);access(c);
        if(thread<=0||!SendPolicy.validAddress(address)||!PhoneNumberUtils.compare(Sender.singleRecipient(c,thread),address))throw new IllegalStateException("The recipient changed. Choose the conversation again.");
        if(Messages.latest(c,thread)!=base||!draftBody(c,thread,base).equals(existing))throw new IllegalStateException("The conversation or draft changed. Review it before adding this share.");
        if(Store.get(c).query("SELECT _id FROM jobs WHERE thread=? AND (status='sending' OR (status='scheduled' AND auto_send=0))",new String[]{Long.toString(thread)}).length()>0)throw new IllegalStateException("Cancel the active timer before adding to this draft.");
        for(Uri uri:session.uris)requireGrant(c,uri);
    }
    static JSONObject add(Context c,JSONObject request)throws Exception{
        access(c);
        String id=request.getString("id"),address=request.getString("expectedAddress"),existing=SharePolicy.text(request.getString("expectedDraftBody"));
        long thread=MediaHistoryPolicy.integer(request.opt("thread"),true),base=MediaHistoryPolicy.integer(request.opt("expectedBase"),false);Session session;
        synchronized(LOCK){session=active;if(session==null||session.cancelled||!session.id.equals(id))throw new IllegalStateException("This share has closed. Share it again.");if("imported".equals(session.status)){
            if(session.result.optLong("thread")!=thread||!PhoneNumberUtils.compare(session.result.optString("address"),address))throw new IllegalStateException("This share was already added to another conversation.");
            // A lost acknowledgement is not permission to reapply an old draft.
            // Only identify its destination; the UI must read today's draft again.
            return new JSONObject().put("id",session.id).put("thread",thread).put("address",address).put("alreadyImported",true);
        }if("importing".equals(session.status))throw new IllegalStateException("This share is already being added.");if(!session.retriable||(session.text.isEmpty()&&session.uris.isEmpty()))throw new IllegalStateException(session.error);session.status="importing";session.error="";}
        List<String> added=List.of();boolean committed=false;
        try{
            String body=SharePolicy.append(existing,session.text);Runnable guard=()->check(c,session,thread,address,base,existing);
            guard.run();synchronized(LOCK){current(session);remember(c,id);}
            if(!session.uris.isEmpty())added=MmsAttachments.add(c,thread,session.uris,guard).ids();
            synchronized(PilotApp.SEND_LOCK){synchronized(LOCK){
                guard.run();Store store=Store.get(c);JSONObject result;var sql=store.getWritableDatabase();sql.beginTransaction();
                try{
                    store.invalidateGeneration();Sender.cancelAutomaticForThread(c,thread,"You added a share. Review your draft before sending.");
                    store.draft(thread,base,body,new JSONArray(),"Edited by you");if(!draftBody(c,thread,base).equals(body))throw new IllegalStateException("The shared draft could not be saved.");store.clearReplyDecision(thread,base);
                    result=new JSONObject().put("id",id).put("thread",thread).put("address",address).put("base",base).put("text",body).put("body",body).put("attachments",MmsAttachments.state(c,thread));
                    sql.setTransactionSuccessful();
                }finally{sql.endTransaction();}
                session.status="imported";session.result=result;committed=true;MessageChanges.publish();return new JSONObject(result.toString());
            }}
        }catch(Exception failed){synchronized(LOCK){if(active==session&&!session.cancelled){session.status="error";session.error=failed instanceof IllegalArgumentException||failed instanceof IllegalStateException?failed.getMessage():"The share could not be added. Try again.";}}throw failed;
        }finally{if(!committed&&!added.isEmpty())MmsAttachments.discardShared(c,thread,added);}
    }
}
