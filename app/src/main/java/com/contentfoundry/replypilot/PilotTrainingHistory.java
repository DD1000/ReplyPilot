package com.contentfoundry.replypilot;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.PhoneNumberUtils;
import java.util.*;
import org.json.*;

/** Read text from both carrier transports, only after explicit Start practice. */
final class PilotTrainingHistory {
    private static final String[] SMS={"_id","thread_id","date","type","body","address"};
    private static final String[] MMS={"_id","thread_id","date","msg_box","m_type"};
    private static final int MAX_PARTS=64,MAX_TEXT_FILE_BYTES=8192;
    private record Read(List<PilotTrainingHistoryPolicy.Entry> rows,boolean textUnavailable){}
    private PilotTrainingHistory(){}

    static JSONArray read(Context context,long thread,String address,Runnable validateAccess)throws JSONException{
        validateAccess.run();
        Read latest=readNewest(context,thread,address,false,PilotTrainingPolicy.HISTORY,validateAccess);boolean unavailable=latest.textUnavailable();
        PilotTrainingHistoryPolicy.Entry incoming=null;
        if(!PilotTrainingHistoryPolicy.hasIncoming(latest.rows())){
            Read received=readNewest(context,thread,address,true,1,validateAccess);unavailable|=received.textUnavailable();
            if(!received.rows().isEmpty())incoming=received.rows().get(0);
        }
        List<PilotTrainingHistoryPolicy.Entry> selected=PilotTrainingHistoryPolicy.select(thread,latest.rows(),incoming);
        validateAccess.run();
        if(!PilotTrainingHistoryPolicy.hasIncoming(selected))throw new IllegalStateException(unavailable?"Some text in this chat could not be read from your phone, and no usable received text was found. Reopen practice to try again.":"No received text was found in this contact's accessible SMS/MMS history. Messages kept only in another app, including RCS chats, may not be available here.");
        JSONArray history=new JSONArray();for(var row:selected)history.put(new JSONObject().put("speaker",row.type()==1?"them":"me").put("text",row.text()));
        return history;
    }

    /** Newest-first SMS/MMS text for Train Autopilot, bounded to PersonaPolicy.MAX_MESSAGES. */
    static List<PilotTrainingHistoryPolicy.Entry> recent(Context context,long thread,String address,int limit,Runnable validateAccess){
        try(Source sms=new Source(context,thread,"sms",false,address,validateAccess);Source mms=new Source(context,thread,"mms",false,address,validateAccess)){
            return PilotTrainingHistoryPolicy.newest(thread,sms,mms,limit,PersonaPolicy.MAX_MESSAGES,validateAccess);
        }
    }
    private static Read readNewest(Context context,long thread,String address,boolean incomingOnly,int limit,Runnable validateAccess){
        try(Source sms=new Source(context,thread,"sms",incomingOnly,address,validateAccess);Source mms=new Source(context,thread,"mms",incomingOnly,address,validateAccess)){
            List<PilotTrainingHistoryPolicy.Entry> rows=PilotTrainingHistoryPolicy.newest(thread,sms,mms,limit,validateAccess);
            return new Read(rows,mms.textUnavailable);
        }
    }

    private static final class Source implements AutoCloseable,PilotTrainingHistoryPolicy.TextSource {
        final Cursor cursor;final long thread;final String kind,address;final Context context;final Runnable validateAccess;
        MediaHistoryPolicy.Position position;boolean textUnavailable;
        Source(Context context,long thread,String kind,boolean incomingOnly,String address,Runnable validateAccess){
            this.thread=thread;this.kind=kind;this.context=context;this.address=address;this.validateAccess=validateAccess;
            var query=PilotTrainingHistoryPolicy.query(thread,kind,incomingOnly);
            cursor=context.getContentResolver().query("mms".equals(kind)?Telephony.Mms.CONTENT_URI:Telephony.Sms.CONTENT_URI,"mms".equals(kind)?MMS:SMS,query.selection(),query.arguments().toArray(new String[0]),query.order());
            if(cursor==null)throw new IllegalStateException("This chat's text could not be read. Try opening practice again.");
        }
        @Override public MediaHistoryPolicy.Position position(){return position;}
        @Override public void advance(){
            position=null;
            while(cursor.moveToNext()){
                validateAccess.run();
                if(cursor.getLong(1)!=thread)continue;
                try{position=new MediaHistoryPolicy.Position(MediaHistoryPolicy.date(kind,cursor.getLong(2)),kind,cursor.getLong(0));return;}
                catch(IllegalArgumentException malformed){/* Ignore invalid provider positions, never reuse another thread. */}
            }
        }
        @Override public PilotTrainingHistoryPolicy.Entry text(){
            int type=cursor.getInt(3),mmsType="mms".equals(kind)?cursor.getInt(4):0;
            String body;
            if("sms".equals(kind)){
                if(!sameNumber(address,cursor.getString(5)))return null;
                body=cursor.isNull(4)?"":cursor.getString(4);
            }else{
                if(!mmsRecipientMatches(context,position.id(),type,address))return null;
                body=mmsText(context,position.id());
                if(body==null){textUnavailable=true;return null;}
            }
            return new PilotTrainingHistoryPolicy.Entry(thread,position,type,mmsType,body);
        }
        @Override public void close(){cursor.close();}
    }
    private static boolean sameNumber(String expected,String actual){return SendPolicy.validAddress(actual)&&PhoneNumberUtils.compare(expected,actual);}
    private static boolean mmsRecipientMatches(Context context,long message,int type,String address){
        Uri uri=Telephony.Mms.CONTENT_URI.buildUpon().appendPath(Long.toString(message)).appendPath("addr").build();
        try(Cursor rows=context.getContentResolver().query(uri,new String[]{"address"},type==1?"type=137":"type IN (151,130,129)",null,null)){
            if(rows==null)throw new IllegalStateException("This chat's recipient could not be checked. Try again.");
            int count=0;while(rows.moveToNext()){if(++count>8||!sameNumber(address,rows.getString(0)))return false;}return count>0;
        }
    }
    private static String mmsText(Context context,long message){
        StringBuilder text=new StringBuilder();
        Uri uri=Telephony.Mms.Part.getPartUriForMessage(Long.toString(message));
        try(Cursor parts=context.getContentResolver().query(uri,new String[]{"_id","ct","text","_data","chset"},null,null,"seq ASC, _id ASC")){
            if(parts==null)throw new IllegalStateException("This chat's text could not be read. Try again.");
            int scanned=0;while(scanned++<MAX_PARTS&&text.length()<PilotTrainingPolicy.MAX_TEXT&&parts.moveToNext()){
                if(!InboxPreviewPolicy.textPart(parts.getString(1))||parts.getLong(0)<=0)continue;
                MmsTextReader.Read read=MmsTextReader.read(context,parts.getLong(0),parts.isNull(2)?"":parts.getString(2),!parts.isNull(3),parts.isNull(4)?106:parts.getInt(4),MAX_TEXT_FILE_BYTES);
                // Skip the whole unreadable message, not just its missing part.
                // Other readable turns can still teach; partial text cannot.
                if(read.unavailable())return null;
                String value=PilotTrainingPolicy.clipped(read.text());if(value.isEmpty())continue;
                if(text.length()>0)text.append('\n');text.append(value);
            }
        }
        return PilotTrainingPolicy.clipped(text.toString());
    }
}
