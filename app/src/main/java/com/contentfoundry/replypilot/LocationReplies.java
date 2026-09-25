package com.contentfoundry.replypilot;

import android.content.Context;
import org.json.JSONObject;
import org.json.JSONException;

/** Location is released only for this contact's current location question. */
final class LocationReplies {
    record Snapshot(JSONObject payload,long revision,long expires) {}
    static boolean question(Context c,long thread,long base){return LocationRequestPolicy.isQuestion(Messages.unanswered(c,thread,base));}
    static Snapshot capture(Context c,long thread,long base)throws JSONException{
        if(!question(c,thread,base))return new Snapshot(null,0,0);
        JSONObject profile=Store.get(c).relationship(thread);
        if(!profile.optBoolean("shareLocation")||!profile.optBoolean("cloudEnabled"))return new Snapshot(null,0,0);
        JSONObject location=LocationSharing.context(c);if(location==null)return new Snapshot(null,0,0);
        JSONObject payload=new JSONObject().put("label",location.getString("label")).put("capturedAt",location.getLong("capturedAt")).put("expiresAt",location.getLong("expiresAt"));
        return new Snapshot(payload,location.getLong("revision"),location.getLong("expiresAt"));
    }
    static boolean allowed(Context c,long thread,long base){
        try{return capture(c,thread,base).payload()!=null;}
        catch(Exception unavailable){return false;}
    }
    static boolean valid(Context c,long thread,long revision,long expires){
        try{return Store.get(c).relationship(thread).optBoolean("shareLocation")&&LocationSharing.valid(c,revision,expires);}
        catch(Exception unavailable){return false;}
    }
    static String block(Context c,JSONObject record,long due){
        if(record==null||record.optLong("location_expires")==0)return null;
        long expires=record.optLong("location_expires");
        return due>=expires||!valid(c,record.optLong("thread"),record.optLong("location_revision"),expires)
            ?"Your location changed, expired, or is no longer shared with this person. Refresh it and review a new reply.":null;
    }
}
