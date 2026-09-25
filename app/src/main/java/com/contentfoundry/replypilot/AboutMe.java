package com.contentfoundry.replypilot;

import android.content.Context;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.LinkedHashMap;
import java.util.Map;

final class AboutMe {
    static JSONObject read(Context c){
        try{return checked(new JSONObject(c.getSharedPreferences("personality",0).getString("profile","{}")));}
        catch(Exception invalid){return new JSONObject(AboutMePolicy.validate(Map.of()));}
    }
    private static JSONObject checked(JSONObject input)throws JSONException{
        Map<String,Object> fields=new LinkedHashMap<>();
        for(var keys=input.keys();keys.hasNext();){String key=keys.next();fields.put(key,input.get(key));}
        return new JSONObject(AboutMePolicy.validate(fields));
    }
    // Persist before acknowledging the save, so a killed process cannot revive old settings.
    @android.annotation.SuppressLint("ApplySharedPref")
    static JSONObject save(Context c,JSONObject input)throws JSONException{
        JSONObject next=checked(input);
        synchronized(PilotApp.SEND_LOCK){
            var preferences=c.getSharedPreferences("personality",0);String prior=preferences.getString("profile","{}");
            if(!preferences.edit().putString("profile",next.toString()).commit()){
                preferences.edit().putString("profile",prior).commit();
                throw new IllegalStateException("Your profile could not be saved. Try again.");
            }
            Sender.pauseAutomatic(c,"Your About me profile changed. Generate a fresh reply before automatic sending.");
        }
        MessageChanges.publish();return next;
    }
}
