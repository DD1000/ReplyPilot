package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Read-only browsing of actual provider rows. This never supplies an AI/send base. */
final class MediaNavigation {
    private static final String[] SMS={"_id","thread_id","date","type","body","address","read","status","sub_id"};
    private static final String[] MMS={"_id","thread_id","date","msg_box","m_type","sub","read","sub_id"};
    private static final int MAX_PARTS=64,MAX_TEXT=32_000;
    record Destination(String address,String name,boolean readOnly){}

    static JSONObject latest(Context context,long thread)throws JSONException{return latest(context,thread,null);}
    /** Ordinary chat history is mixed; its caller must keep a separate SMS-only send base. */
    static JSONObject latest(Context context,long thread,JSONObject request)throws JSONException{
        requireAccess(context);if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        MediaHistoryPolicy.Position boundary=null;MediaHistoryPolicy.Page<JSONObject> page;
        if(request!=null&&request.has("cursor")){
            JSONObject cursor=request.optJSONObject("cursor");if(cursor==null)throw new IllegalArgumentException("This conversation position is missing.");
            boundary=new MediaHistoryPolicy.Position(MediaHistoryPolicy.integer(cursor.opt("date"),false),cursor.optString("kind"),MediaHistoryPolicy.integer(cursor.opt("id"),true));
            JSONObject actual=exact(context,boundary.kind(),boundary.id(),thread);
            if(actual==null||MediaHistoryPolicy.compare(position(actual),boundary)!=0)throw new IllegalStateException("This conversation changed. Open it again.");
            page=load(context,thread,boundary,"older",MediaHistoryPolicy.PAGE_SIZE);
        }else{
            List<MediaHistoryPolicy.Row<JSONObject>> candidates=new ArrayList<>();
            for(String kind:List.of("sms","mms")){
                MediaHistoryPolicy.Query query=MediaHistoryPolicy.latestQuery(thread,kind);
                try(Cursor rows=context.getContentResolver().query(uri(kind),columns(kind),query.selection(),query.arguments().toArray(new String[0]),query.order())){
                    if(rows==null)throw new IllegalStateException("The conversation could not be loaded. Please try again.");
                    int count=0;while(count++<MediaHistoryPolicy.PAGE_SIZE+1&&rows.moveToNext()){
                        JSONObject row=normalize(Store.json(rows),kind);if(row.optLong("thread_id")==thread)candidates.add(new MediaHistoryPolicy.Row<>(position(row),row));
                    }
                }
            }
            page=MediaHistoryPolicy.latest(candidates,MediaHistoryPolicy.PAGE_SIZE);
        }
        JSONObject latestMms=latestRow(context,thread,"mms"),latestSms=latestRow(context,thread,"sms");
        JSONObject newest=latestSms==null?latestMms:latestMms==null?latestSms:MediaHistoryPolicy.compare(position(latestSms),position(latestMms))>=0?latestSms:latestMms;
        Destination destination=destination(context,thread,newest==null?new JSONObject():newest);
        JSONArray history=new JSONArray(),sms=new JSONArray();
        for(var entry:page.rows()){
            JSONObject row=entry.value();if("mms".equals(entry.position().kind()))parts(context,row);else sms.put(row);history.put(row);
        }
        Store.get(context).annotateDelivery(sms);requireAccess(context);
        if(boundary!=null){JSONObject actual=exact(context,boundary.kind(),boundary.id(),thread);if(actual==null||MediaHistoryPolicy.compare(position(actual),boundary)!=0)throw new IllegalStateException("This conversation changed. Open it again.");}
        return new JSONObject().put("thread",thread).put("address",destination.address()).put("name",destination.name()).put("readOnly",destination.readOnly()).put("history",history)
            .put("before",page.rows().isEmpty()?JSONObject.NULL:json(page.rows().get(0).position())).put("hasOlder",page.hasMore()).put("hasMore",page.hasMore())
            .put("latest",newest==null?JSONObject.NULL:json(position(newest)).put("key",newest.optString("key")).put("type",newest.optInt("type")))
            .put("latestMms",latestMms==null?JSONObject.NULL:json(position(latestMms)).put("key",latestMms.optString("key")).put("type",latestMms.optInt("type")));
    }
    static JSONObject latestRow(Context context,long thread,String kind)throws JSONException{
        MediaHistoryPolicy.Query query=MediaHistoryPolicy.latestQuery(thread,kind);
        try(Cursor rows=context.getContentResolver().query(uri(kind),columns(kind),query.selection(),query.arguments().toArray(new String[0]),query.order())){
            if(rows==null)throw new IllegalStateException("The conversation could not be checked.");
            return rows.moveToFirst()?normalize(Store.json(rows),kind):null;
        }
    }
    static JSONObject around(Context context,long mediaId,JSONObject request)throws JSONException{
        requireAccess(context);
        if(mediaId<=0)throw new IllegalArgumentException("Choose a media message first.");
        JSONObject anchor=exact(context,"mms",mediaId,0);
        if(anchor==null)throw new IllegalStateException("This media message is no longer on your phone.");
        long thread=anchor.optLong("thread_id");
        if(thread<=0)throw new IllegalStateException("This media message has no conversation to open.");
        MediaHistoryPolicy.Position target=position(anchor);
        String direction=request==null?"":request.optString("direction","");
        List<MediaHistoryPolicy.Row<JSONObject>> rows=new ArrayList<>();boolean older,newer;
        if(direction.isEmpty()){
            var before=load(context,thread,target,"older",MediaHistoryPolicy.ANCHOR_SIDE);
            var after=load(context,thread,target,"newer",MediaHistoryPolicy.ANCHOR_SIDE);
            rows.addAll(before.rows());rows.add(new MediaHistoryPolicy.Row<>(target,anchor));rows.addAll(after.rows());
            older=before.hasMore();newer=after.hasMore();
        }else{
            if(!"older".equals(direction)&&!"newer".equals(direction))throw new IllegalArgumentException("Choose older or newer messages.");
            JSONObject cursor=request.optJSONObject("cursor");
            if(cursor==null)throw new IllegalArgumentException("This conversation position is missing. Open the media message again.");
            MediaHistoryPolicy.Position requested=new MediaHistoryPolicy.Position(MediaHistoryPolicy.integer(cursor.opt("date"),false),cursor.optString("kind"),MediaHistoryPolicy.integer(cursor.opt("id"),true));
            JSONObject boundary=exact(context,requested.kind(),requested.id(),thread);
            if(boundary==null||MediaHistoryPolicy.compare(position(boundary),requested)!=0)throw new IllegalStateException("This conversation changed. Open the media message again.");
            var page=load(context,thread,requested,direction,MediaHistoryPolicy.PAGE_SIZE);rows.addAll(page.rows());
            older="older".equals(direction)?page.hasMore():true;newer="newer".equals(direction)?page.hasMore():true;
        }
        Destination destination=destination(context,thread,anchor);
        JSONArray history=new JSONArray(),sms=new JSONArray();
        for(var entry:rows){JSONObject row=entry.value();if("mms".equals(entry.position().kind()))parts(context,row);else sms.put(row);history.put(row);}
        Store.get(context).annotateDelivery(sms);
        // Permission revocation or deletion while querying must not expose stale
        // content or silently substitute a different destination for the anchor.
        requireAccess(context);JSONObject confirmed=exact(context,"mms",mediaId,thread);
        if(confirmed==null||MediaHistoryPolicy.compare(position(confirmed),target)!=0)throw new IllegalStateException("This media message changed. Open Media and try again.");
        return new JSONObject().put("thread",thread).put("address",destination.address()).put("name",destination.name()).put("readOnly",destination.readOnly())
            .put("anchor",target.key()).put("history",history).put("before",rows.isEmpty()?JSONObject.NULL:json(rows.get(0).position()))
            .put("after",rows.isEmpty()?JSONObject.NULL:json(rows.get(rows.size()-1).position())).put("hasOlder",older).put("hasNewer",newer);
    }
    static void requireAccess(Context context){if(!Messages.allowed(context,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow SMS access to open this conversation.");}
    private static Uri uri(String kind){return "mms".equals(kind)?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI;}
    private static String[] columns(String kind){return "mms".equals(kind)?MMS:SMS;}
    static JSONObject exact(Context context,String kind,long id,long thread)throws JSONException{
        String selection=thread>0?"thread_id=?":null;String[] args=thread>0?new String[]{Long.toString(thread)}:null;
        try(Cursor cursor=context.getContentResolver().query(ContentUris.withAppendedId(uri(kind),id),columns(kind),selection,args,null)){
            if(cursor==null)throw new IllegalStateException("The conversation could not be loaded. Please try again.");
            if(!cursor.moveToFirst())return null;
            JSONObject row=normalize(Store.json(cursor),kind);
            return row.optLong("_id")==id&&(thread<=0||row.optLong("thread_id")==thread)?row:null;
        }
    }
    private static JSONObject normalize(JSONObject row,String kind)throws JSONException{
        long date=MediaHistoryPolicy.date(kind,row.optLong("date",-1));
        MediaHistoryPolicy.Position position=new MediaHistoryPolicy.Position(date,kind,row.optLong("_id"));
        row.put("key",position.key()).put("kind",kind).put("date",date);
        if("mms".equals(kind))row.put("type",row.optInt("msg_box")==1?1:row.optInt("msg_box")>=2&&row.optInt("msg_box")<=5?2:0).put("body","").put("address","");
        return row;
    }
    private static MediaHistoryPolicy.Position position(JSONObject row){return new MediaHistoryPolicy.Position(row.optLong("date",-1),row.optString("kind"),row.optLong("_id"));}
    private static JSONObject json(MediaHistoryPolicy.Position position)throws JSONException{return new JSONObject().put("date",position.date()).put("kind",position.kind()).put("id",position.id());}
    private static MediaHistoryPolicy.Page<JSONObject> load(Context context,long thread,MediaHistoryPolicy.Position boundary,String direction,int limit)throws JSONException{
        List<MediaHistoryPolicy.Row<JSONObject>> candidates=new ArrayList<>();
        for(String kind:List.of("sms","mms")){
            MediaHistoryPolicy.Query query=MediaHistoryPolicy.query(thread,kind,boundary,direction);
            try(Cursor cursor=context.getContentResolver().query(uri(kind),columns(kind),query.selection(),query.arguments().toArray(new String[0]),query.order())){
                if(cursor==null)throw new IllegalStateException("The conversation could not be loaded. Please try again.");
                int scanned=0;
                while(scanned++<limit+1&&cursor.moveToNext()){
                    JSONObject row=normalize(Store.json(cursor),kind);
                    if(row.optLong("thread_id")==thread)candidates.add(new MediaHistoryPolicy.Row<>(position(row),row));
                }
            }
        }
        return MediaHistoryPolicy.page(candidates,boundary,direction,limit);
    }
    static void parts(Context context,JSONObject message)throws JSONException{
        JSONArray parts=new JSONArray();List<MmsContentPolicy.Part> content=new ArrayList<>();StringBuilder body=new StringBuilder();boolean truncated=false,unavailable=false,complete=true;int scanned=0;
        Uri uri=Telephony.Mms.CONTENT_URI.buildUpon().appendPath(message.optString("_id")).appendPath("part").build();
        try(Cursor cursor=context.getContentResolver().query(uri,new String[]{"_id","ct","text","_data","chset","mid"},null,null,"seq ASC, _id ASC")){
            if(cursor==null)throw new IllegalStateException("The media message could not be loaded. Please try again.");
            while(cursor.moveToNext()){
                if(scanned++==MAX_PARTS){truncated=true;break;}
                long id=cursor.getLong(0);String type=MmsTextReader.mime(cursor.getString(1));
                if(cursor.getLong(5)!=message.optLong("_id")||id<=0||type.isEmpty()){
                    complete=false;content.add(new MmsContentPolicy.Part(id,type,true,false));continue;
                }
                if(MmsContentPolicy.presentation(type)){content.add(new MmsContentPolicy.Part(id,type,false,false));continue;}
                JSONObject part=new JSONObject().put("_id",id).put("ct",type);
                if("text/plain".equals(type)){
                    int remaining=Math.max(0,MAX_TEXT-body.length()-(body.length()>0?1:0));
                    MmsTextReader.Read loaded=remaining==0?new MmsTextReader.Read("",true,false):MmsTextReader.read(context,id,cursor.isNull(2)?"":cursor.getString(2),!cursor.isNull(3),cursor.isNull(4)?106:cursor.getInt(4),remaining*4);
                    String text=loaded.text();truncated|=loaded.truncated();unavailable|=loaded.unavailable();if(loaded.unavailable())part.put("textUnavailable",true);
                    content.add(new MmsContentPolicy.Part(id,type,loaded.unavailable(),loaded.truncated()));
                    if(text.length()>remaining){int end=remaining;if(end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;text=text.substring(0,end);truncated=true;}
                    part.put("text",text);if(!text.isEmpty()){if(body.length()>0)body.append('\n');body.append(text);}
                }else content.add(new MmsContentPolicy.Part(id,type,false,false));
                parts.put(part);
            }
        }
        String kind=MmsContentPolicy.classify(content,body.toString(),complete&&!truncated&&!unavailable);
        message.put("parts",parts).put("body",body.toString()).put("truncated",truncated).put("textUnavailable",unavailable)
            .put("contentKind",kind).put("textOnly",MmsContentPolicy.TEXT.equals(kind));
    }
    static Destination destination(Context context,long thread,JSONObject anchor){
        String ids="",address="";
        try(Cursor cursor=context.getContentResolver().query(Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple","true").build(),new String[]{"recipient_ids"},"_id=?",new String[]{Long.toString(thread)},null)){
            if(cursor!=null&&cursor.moveToFirst())ids=cursor.getString(0);
        }
        if(ids==null||!ids.trim().matches("[0-9]+"))return new Destination("","Media conversation",true);
        try(Cursor cursor=context.getContentResolver().query(Uri.parse("content://mms-sms/canonical-address/"+ids.trim()),new String[]{"address"},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())address=cursor.getString(0);
        }
        if(!SendPolicy.validAddress(address))return new Destination("","Media conversation",true);
        if(anchor.optInt("msg_box")==Telephony.Mms.MESSAGE_BOX_INBOX){
            Uri senders=Telephony.Mms.CONTENT_URI.buildUpon().appendPath(anchor.optString("_id")).appendPath("addr").build();boolean matched=false;
            try(Cursor cursor=context.getContentResolver().query(senders,new String[]{"address"},"type=?",new String[]{"137"},null)){
                int count=0;
                while(cursor!=null&&cursor.moveToNext()){
                    if(++count>8)return new Destination("","Media conversation",true);
                    String sender=cursor.getString(0);
                    if(!SendPolicy.validAddress(sender)||!PhoneNumberUtils.compare(address,sender))return new Destination("","Media conversation",true);
                    matched=true;
                }
            }
            if(!matched)return new Destination("","Media conversation",true);
        }
        String name=Messages.name(context,address);return new Destination(address,name==null||name.isBlank()?address:name,false);
    }
}
