package com.contentfoundry.replypilot;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.*;
import android.telephony.*;
import org.json.*;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class Messages {
    private static final String[] SMS_COLUMNS={"_id","thread_id","address","body","date","type","read","sub_id","status"};
    private static final String[] MMS_SUMMARY_COLUMNS={"_id","thread_id","date","msg_box","m_type","sub","read","sub_id"};
    public static boolean role(Context c) {
        // Check our own role directly; querying another SMS package requires package visibility.
        RoleManager manager=c.getSystemService(RoleManager.class);
        return manager!=null&&manager.isRoleAvailable(RoleManager.ROLE_SMS)&&manager.isRoleHeld(RoleManager.ROLE_SMS);
    }
    public static boolean allowed(Context c,String p) {return c.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    public static JSONArray inbox(Context c) {
        JSONArray result=new JSONArray();if(!allowed(c,Manifest.permission.READ_SMS))return result;
        Set<Long> pins=PinnedChats.ids(c),seen=new HashSet<>();List<PinnedChatPolicy.Row<JSONObject>> summaries=new ArrayList<>();
        // AOSP's combined conversation view returns one latest row per thread.
        // msg_box is populated only for MMS; transport_type is not projected by
        // every provider's grouped view. No full-history scan is needed here.
        String[] columns={"_id","thread_id","date","type","msg_box","m_type","address","body","read","sub_id","status","sub"};
        try{
            try(Cursor rows=c.getContentResolver().query(Telephony.Threads.CONTENT_URI,columns,null,null,"normalized_date DESC, _id DESC")){
                if(rows==null)throw new IllegalStateException("Conversation summaries unavailable");
                while(rows.moveToNext()){
                    JSONObject row=Store.json(rows);long thread=row.optLong("thread_id");if(thread<=0||!seen.add(thread))continue;
                    String kind=InboxPreviewPolicy.transport(row.optInt("type"),row.optInt("msg_box"));
                    // Resolve ambiguous OEM fields and the grouped-provider tie
                    // against actual, displayable per-transport rows.
                    if(kind.isEmpty()||"mms".equals(kind)||row.optLong("date")%1000==0||!InboxPreviewPolicy.eligible(kind,row.optInt("type"),row.optInt("msg_box"),row.optInt("m_type")))row=latestSummary(c,thread);
                    else row=inboxRow(row,kind);
                    if(row==null)continue;
                    summaries.add(new PinnedChatPolicy.Row<>(thread,row.optLong("date"),row.optLong("_id"),row));
                }
            }
        }catch(RuntimeException|JSONException unavailable){
            // OEM fallback: read all thread IDs, then two bounded latest
            // rows per thread. Each thread still needs only its latest row, never its entire history.
            summaries.clear();seen.clear();
            try(Cursor threads=c.getContentResolver().query(Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple","true").build(),new String[]{"_id"},null,null,"date DESC")){
                if(threads==null)throw new IllegalStateException("Conversation summaries are temporarily unavailable. Try again.");
                while(threads.moveToNext()){
                    long thread=threads.getLong(0);if(thread<=0||!seen.add(thread))continue;
                    JSONObject row=latestSummary(c,thread);if(row==null)continue;
                    summaries.add(new PinnedChatPolicy.Row<>(thread,row.optLong("date"),row.optLong("_id"),row));
                }
            }
        }
        for(long thread:pins)if(!seen.contains(thread)){
            JSONObject row=latestSummary(c,thread);if(row!=null)summaries.add(new PinnedChatPolicy.Row<>(thread,row.optLong("date"),row.optLong("_id"),row));
        }
        return inboxSummaries(c,summaries,pins);
    }
    /** Fresh partial delta. No history scan and no query for every saved contact. */
    static JSONArray recentInbox(Context c){
        if(!role(c)||!allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Open Messages with texting access to refresh conversations.");
        List<LiveInboxPolicy.Candidate> candidates=new ArrayList<>();
        for(String kind:List.of("sms","mms")){
            boolean mms="mms".equals(kind);LiveInboxPolicy.Scan scan=new LiveInboxPolicy.Scan(kind);
            String[] columns=mms?new String[]{"thread_id","date","_id","msg_box","m_type"}:new String[]{"thread_id","date","_id","type"};
            String selection=mms?"msg_box IN (1,2,4,5) AND m_type IN (128,130,132)":"type IN (1,2,4,5,6)";
            try(Cursor rows=c.getContentResolver().query(mms?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI,columns,selection,null,"date DESC, _id DESC")){
                if(rows==null)throw new IllegalStateException("Recent conversations are temporarily unavailable.");
                while(!scan.full()&&rows.moveToNext())scan.add(rows.getLong(0),rows.getLong(1),rows.getLong(2),mms?0:rows.getInt(3),mms?rows.getInt(3):0,mms?rows.getInt(4):0);
            }
            candidates.addAll(scan.rows());
        }
        List<PinnedChatPolicy.Row<JSONObject>> summaries=new ArrayList<>();
        for(long thread:LiveInboxPolicy.threads(candidates)){
            if(!role(c)||!allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Message access changed. Reopen Messages.");
            // Reread both transports for each selected thread. An SMS-only change
            // must not overwrite a newer MMS (or vice versa) in the visible list.
            JSONObject row=latestSummary(c,thread);if(row!=null)summaries.add(new PinnedChatPolicy.Row<>(thread,row.optLong("date"),row.optLong("_id"),row));
        }
        return inboxSummaries(c,summaries,PinnedChats.ids(c));
    }
    private static JSONArray inboxSummaries(Context c,List<PinnedChatPolicy.Row<JSONObject>> summaries,Set<Long> pins){
        JSONArray result=new JSONArray();
        JSONArray sms=new JSONArray();
        try{
            for(PinnedChatPolicy.Row<JSONObject> selected:PinnedChatPolicy.selectAll(summaries,pins)){
                JSONObject row=selected.value();row.put("pinned",pins.contains(selected.thread()));
                if("mms".equals(row.optString("kind"))){
                    row.put("body",mmsPreview(c,row.optLong("_id"),row.optInt("m_type")));
                    MediaNavigation.Destination destination=MediaNavigation.destination(c,selected.thread(),row);
                    row.put("address",destination.address()).put("name",destination.name()).put("readOnly",destination.readOnly());
                }else{row.put("name",name(c,row.optString("address"))).put("readOnly",false);sms.put(row);}
                result.put(row);
            }
        }catch(JSONException invalid){throw new IllegalStateException(invalid);}
        for(int start=0;start<sms.length();start+=PinnedChatPolicy.RECENT_LIMIT){
            JSONArray batch=new JSONArray();for(int i=start;i<Math.min(sms.length(),start+PinnedChatPolicy.RECENT_LIMIT);i++)batch.put(sms.optJSONObject(i));Store.get(c).annotateDelivery(batch);
        }
        return allowed(c,Manifest.permission.READ_SMS)?result:new JSONArray();
    }
    private static JSONObject inboxRow(JSONObject row,String kind)throws JSONException{
        long date=row.has("kind")?row.optLong("date"):MediaHistoryPolicy.date(kind,row.optLong("date",-1));
        if(row.optLong("_id")<=0)throw new IllegalArgumentException("Invalid conversation summary.");
        row.put("kind",kind).put("key",kind+":"+row.optLong("_id")).put("date",date);
        if("mms".equals(kind))row.put("type",row.optInt("msg_box")==1?1:row.optInt("msg_box")>=2&&row.optInt("msg_box")<=5?2:0).put("status",-1).put("delivery","none").put("delivered_at",0);
        return row;
    }
    private static JSONObject latestSummary(Context c,long thread){
        try{
            JSONObject sms=latestSummaryRow(c,thread,"sms"),mms=latestSummaryRow(c,thread,"mms");
            JSONObject newest=sms==null?mms:mms==null?sms:MediaHistoryPolicy.compare(new MediaHistoryPolicy.Position(sms.optLong("date"),"sms",sms.optLong("_id")),new MediaHistoryPolicy.Position(mms.optLong("date"),"mms",mms.optLong("_id")))>=0?sms:mms;
            return newest==null?null:inboxRow(newest,newest.optString("kind"));
        }catch(JSONException invalid){throw new IllegalStateException(invalid);}
    }
    private static JSONObject latestSummaryRow(Context c,long thread,String kind)throws JSONException{
        InboxPreviewPolicy.Query query=InboxPreviewPolicy.latest(thread,kind);boolean mms="mms".equals(kind);
        try(Cursor rows=c.getContentResolver().query(mms?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI,mms?MMS_SUMMARY_COLUMNS:SMS_COLUMNS,query.selection(),query.arguments().toArray(new String[0]),"date DESC, _id DESC")){
            if(rows==null)throw new IllegalStateException("Conversation summaries are temporarily unavailable. Try again.");int checked=0;
            while(checked++<64&&rows.moveToNext()){JSONObject row=Store.json(rows);if(row.optLong("thread_id")==thread&&InboxPreviewPolicy.eligible(kind,row.optInt("type"),row.optInt("msg_box"),row.optInt("m_type")))return inboxRow(row,kind);}return null;
        }
    }
    /** Small actual MMS caption/text only; never opens image, audio or video bytes. */
    static String mmsPreview(Context c,long mediaId,int messageType){
        if(mediaId<=0||!allowed(c,Manifest.permission.READ_SMS))return InboxPreviewPolicy.fallback(messageType);
        if(messageType==130)return InboxPreviewPolicy.fallback(messageType);
        InboxPreviewPolicy.Snippet snippet=new InboxPreviewPolicy.Snippet();int files=0;
        Uri uri=Telephony.Mms.Part.getPartUriForMessage(Long.toString(mediaId));
        try(Cursor rows=c.getContentResolver().query(uri,new String[]{"_id","ct","text","_data","chset"},null,null,"seq ASC, _id ASC")){
            if(rows==null)throw new IllegalStateException("Message previews are temporarily unavailable. Try again.");
            while(!snippet.full()&&rows.moveToNext()){
                String type=rows.getString(1),text=rows.isNull(2)?"":rows.getString(2);
                if(InboxPreviewPolicy.textPart(type)&&text.isBlank()&&!rows.isNull(3)&&files++<InboxPreviewPolicy.MAX_FILE_PARTS){
                    long part=rows.getLong(0);if(part>0)text=previewTextFile(c,part,rows.isNull(4)?106:rows.getInt(4));
                }
                snippet.add(type,text);
            }
        }
        return allowed(c,Manifest.permission.READ_SMS)?snippet.result(messageType):InboxPreviewPolicy.fallback(messageType);
    }
    private static String previewTextFile(Context c,long part,int charset){
        Charset encoding=StandardCharsets.UTF_8;try{encoding=Charset.forName(com.google.android.mms.pdu_alt.CharacterSets.getMimeName(charset));}catch(Exception unsupported){}
        try(InputStream input=c.getContentResolver().openInputStream(ContentUris.withAppendedId(Telephony.Mms.Part.CONTENT_URI,part))){
            if(input==null)return "";ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];while(bytes.size()<InboxPreviewPolicy.MAX_FILE_BYTES){int count=input.read(buffer,0,Math.min(buffer.length,InboxPreviewPolicy.MAX_FILE_BYTES-bytes.size()));if(count<0)break;bytes.write(buffer,0,count);}return new String(bytes.toByteArray(),encoding);
        }catch(Exception unavailable){return "";}
    }
    public static String name(Context c,String address) {
        if(!allowed(c,Manifest.permission.READ_CONTACTS))return address;
        try(Cursor cur=c.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(address)),new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null)){if(cur!=null&&cur.moveToFirst())return cur.getString(0);}catch(RuntimeException ignored){}return address;
    }
    public static JSONArray history(Context c,long thread) {
        JSONArray a=new JSONArray();if(!allowed(c,Manifest.permission.READ_SMS))return a;
        List<JSONObject> rows=new ArrayList<>();
        try(Cursor cur=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,SMS_COLUMNS,"thread_id=?",new String[]{""+thread},"date DESC, _id DESC")){while(cur!=null&&cur.moveToNext()&&rows.size()<60)rows.add(Store.json(cur));}
        Collections.reverse(rows);for(JSONObject r:rows)a.put(r);Store.get(c).annotateDelivery(a);return a;
    }
    public static JSONObject historyPage(Context c,long thread) {return historyPage(c,thread,0,0);}
    public static JSONObject historyPage(Context c,long thread,long beforeDate,long beforeId) {
        SmsHistoryPolicy.Before boundary=SmsHistoryPolicy.cursor(beforeDate,beforeId);
        SmsHistoryPolicy.Query request=SmsHistoryPolicy.query(thread,boundary);
        SmsHistoryPolicy.Page<JSONObject> page=new SmsHistoryPolicy.Page<>(thread,boundary);
        if(allowed(c,Manifest.permission.READ_SMS)){
            // Values are bound arguments. No provider-specific LIMIT or caller SQL is used.
            try(Cursor cur=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,SMS_COLUMNS,request.selection(),request.arguments().toArray(new String[0]),SmsHistoryPolicy.ORDER)){
                while(cur!=null&&cur.moveToNext()){
                    JSONObject row=Store.json(cur);
                    if(page.add(row.optLong("thread_id"),row.optLong("date",-1),row.optLong("_id"),row))break;
                }
            }
        }
        try{
            JSONArray rows=new JSONArray();for(JSONObject row:page.history())rows.put(row);Store.get(c).annotateDelivery(rows);
            SmsHistoryPolicy.Before before=page.before();
            Object position=before==null?JSONObject.NULL:new JSONObject().put("date",before.date()).put("id",before.id());
            return new JSONObject().put("history",rows).put("hasMore",page.hasMore()).put("before",position);
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    /** Refresh only receipt fields for older rows currently visible in the open chat. */
    public static JSONArray receiptUpdates(Context c,long thread,JSONArray requested){
        SmsHistoryPolicy.validateThread(thread);
        JSONArray result=new JSONArray();if(requested==null||requested.length()==0||!allowed(c,Manifest.permission.READ_SMS))return result;
        if(requested.length()>100)throw new IllegalArgumentException("Refresh up to 100 visible messages at once.");
        Set<Long> ids=new LinkedHashSet<>();
        for(int i=0;i<requested.length();i++){long id=requested.optLong(i,0);if(id>0)ids.add(id);}
        if(ids.isEmpty())return result;
        List<String> args=new ArrayList<>();args.add(Long.toString(thread));for(long id:ids)args.add(Long.toString(id));
        JSONArray rows=new JSONArray();
        String selection="thread_id=? AND _id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";
        try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,SMS_COLUMNS,selection,args.toArray(new String[0]),"_id ASC")){
            while(cursor!=null&&cursor.moveToNext())rows.put(Store.json(cursor));
        }
        Store.get(c).annotateDelivery(rows);
        try{
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                result.put(new JSONObject().put("_id",row.optLong("_id")).put("status",row.optInt("status",-1))
                    .put("delivery",row.optString("delivery","none")).put("delivered_at",row.optLong("delivered_at")));
            }
        }catch(JSONException e){throw new IllegalStateException(e);}
        return result;
    }
    /** Latest actual incoming/sent text for this contact; the full UI history stays paged. */
    public static JSONArray recentContext(Context c,long thread) {
        SmsHistoryPolicy.validateThread(thread);
        JSONArray result=new JSONArray();if(!allowed(c,Manifest.permission.READ_SMS))return result;
        List<JSONObject> rows=new ArrayList<>();
        try(Cursor cur=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,SMS_COLUMNS,"thread_id=? AND type IN (1,2) AND body IS NOT NULL AND length(body)>0",new String[]{Long.toString(thread)},SmsHistoryPolicy.ORDER)){
            while(cur!=null&&rows.size()<SmsHistoryPolicy.CONTEXT_SIZE&&cur.moveToNext()){
                JSONObject row=Store.json(cur);
                if(SmsHistoryPolicy.belongs(thread,row.optLong("thread_id"),row.optLong("date",-1),row.optLong("_id"),null)
                    &&SmsHistoryPolicy.isContext(thread,row.optLong("thread_id"),row.optInt("type"),row.optString("body",null)))rows.add(row);
            }
        }
        Collections.reverse(rows);for(JSONObject row:rows)result.put(row);return result;
    }
    public static long latest(Context c,long thread){
        if(!allowed(c,Manifest.permission.READ_SMS))return 0;
        // Draft edits need a fresh ID, not the text and JSON for the entire conversation.
        try(Cursor cur=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id"},"thread_id=?",new String[]{""+thread},"date DESC, _id DESC")){
            return cur!=null&&cur.moveToFirst()?cur.getLong(0):0;
        }
    }
    /** Resolve the exact inbound row so pending receipt aliases survive process restarts. */
    public static String address(Context c,long thread,long base){
        if(!allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow SMS access to review this conversation.");
        try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"address"},"thread_id=? AND _id=?",new String[]{Long.toString(thread),Long.toString(base)},null)){
            if(cursor==null)throw new IllegalStateException("The conversation could not be read.");
            return cursor.moveToFirst()&&!cursor.isNull(0)?cursor.getString(0):"";
        }
    }
    /** Preserve the complete unanswered run, with one lookahead to reject overflow. */
    public static List<String> unanswered(Context c,long thread,long base){
        SmsHistoryPolicy.validateThread(thread);List<String> texts=new ArrayList<>();
        if(!allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow SMS access to review this conversation.");
        try(Cursor cursor=c.getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id","type","body"},"thread_id=? AND type IN (1,2)",new String[]{Long.toString(thread)},SmsHistoryPolicy.ORDER)){
            if(cursor==null)throw new IllegalStateException("The conversation could not be read.");
            if(!cursor.moveToFirst()||cursor.getLong(0)!=base||cursor.getInt(1)!=1)return texts;
            do{if(cursor.getInt(1)==2)break;texts.add(cursor.isNull(2)?"":cursor.getString(2));}while(texts.size()<=CloudPrompt.RECENT_LIMIT&&cursor.moveToNext());
        }
        Collections.reverse(texts);return texts;
    }
    public static long thread(Context c,String address){return Telephony.Threads.getOrCreateThreadId(c,address);}
    public static JSONArray sims(Context c) {
        JSONArray out=new JSONArray();if(!allowed(c,Manifest.permission.READ_PHONE_STATE))return out;
        List<SubscriptionInfo> list;
        try{list=c.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();}catch(SecurityException revoked){return out;}
        if(list!=null)for(SubscriptionInfo s:list)try{out.put(new JSONObject().put("id",s.getSubscriptionId()).put("name",s.getDisplayName().toString()));}catch(JSONException ignored){}return out;
    }
    public static boolean activeSim(Context c,int sub){JSONArray a=sims(c);for(int i=0;i<a.length();i++)if(a.optJSONObject(i).optInt("id")==sub)return true;return false;}
    /** Android shares the SIM list only with Phone (READ_PHONE_STATE) access. */
    public static boolean phoneAccess(Context c){return allowed(c,Manifest.permission.READ_PHONE_STATE);}
    static List<Integer> simIds(JSONArray sims){List<Integer> ids=new ArrayList<>();for(int i=0;i<sims.length();i++){JSONObject s=sims.optJSONObject(i);if(s!=null)ids.add(s.optInt("id",SimPolicy.NONE));}return ids;}
    /** The requested SIM if still active, otherwise the phone's only active SIM, otherwise -1. */
    public static int effectiveSim(Context c,int requested){return SimPolicy.effective(requested,simIds(sims(c)));}
    /** The owner's saved sending SIM, recovered to the only active SIM when the saved one is gone. */
    public static int savedSim(Context c){return effectiveSim(c,c.getSharedPreferences("settings",0).getInt("sub",SimPolicy.NONE));}
    public static String simProblem(Context c){return SimPolicy.problem(phoneAccess(c),sims(c).length());}
    public static void read(Context c,long thread){
        if(thread<=0||!PilotApp.foreground||!role(c)||!allowed(c,Manifest.permission.READ_SMS))return;
        ContentValues values=new ContentValues();values.put("read",1);values.put("seen",1);
        String selection="thread_id=? AND (read=0 OR seen=0)";String[] args={Long.toString(thread)};
        for(Uri uri:List.of(Telephony.Sms.CONTENT_URI,Telephony.Mms.CONTENT_URI)){
            // Some providers notify observers even when UPDATE changes zero rows.
            // Reopening an already-read chat must not trigger another full archive
            // scan, whose completion refreshes the chat and starts the cycle again.
            boolean unread;
            try(Cursor rows=c.getContentResolver().query(uri,new String[]{"_id"},selection,args,null)){
                unread=rows!=null&&rows.moveToFirst();
            }
            if(unread&&PilotApp.foreground&&role(c)&&allowed(c,Manifest.permission.READ_SMS))c.getContentResolver().update(uri,values,selection,args);
        }
    }
    public static JSONArray multimedia(Context c){
        JSONArray out=new JSONArray();if(!allowed(c,Manifest.permission.READ_SMS))return out;
        // A media load often contains several messages from the same person. Keep
        // names only for this response so repeated MMS rows need one contact lookup.
        Map<String,String> contactNames=new HashMap<>();
        try(Cursor cur=c.getContentResolver().query(Telephony.Mms.CONTENT_URI,new String[]{"_id","thread_id","date","sub","msg_box","m_type"},"msg_box IN (1,2,4,5) AND m_type IN (128,132)",null,"date DESC, _id DESC")){
            if(cur==null)throw new IllegalStateException("Attachments are temporarily unavailable. Try again.");
            while(cur.moveToNext()&&out.length()<60){JSONObject m=Store.json(cur);String id=m.optString("_id");JSONArray parts=new JSONArray();boolean hasAttachment=false;
                if(m.optLong("_id")<=0||!MediaGalleryPolicy.message(m.optInt("msg_box"),m.optInt("m_type")))continue;
                try(Cursor p=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/part"),new String[]{"_id","ct","text"},null,null,"seq ASC, _id ASC")){
                    if(p==null)throw new IllegalStateException("Attachments are temporarily unavailable. Try again.");
                    while(p.moveToNext()){
                        JSONObject part=Store.json(p);String type=MediaGalleryPolicy.type(part.optString("ct"));long partId=part.optLong("_id");
                        if(MediaGalleryPolicy.displayPart(partId,type))parts.put(part.put("ct",type));
                        hasAttachment|=MediaGalleryPolicy.attachment(partId,type);
                    }
                }
                // Count actual attachment messages, so text-only MMS cannot fill the
                // gallery's 60-card window or displace older photos. Keep their texts in chat.
                if(!hasAttachment)continue;
                String from="Multimedia message";
                try(Cursor p=c.getContentResolver().query(Uri.parse("content://mms/"+id+"/addr"),new String[]{"address","type"},m.optInt("msg_box")==1?"type=137":"type=151",null,null)){
                    if(p!=null&&p.moveToFirst()){
                        String address=p.getString(0);
                        if(!allowed(c,Manifest.permission.READ_CONTACTS)){contactNames.clear();from=address;}
                        else{
                            if(!contactNames.containsKey(address))contactNames.put(address,name(c,address));
                            from=contactNames.get(address);
                        }
                    }
                }
                m.put("name",from);m.put("parts",parts);out.put(m);
            }
        }catch(JSONException e){throw new IllegalStateException(e);}return out;
    }
}
