package com.contentfoundry.replypilot;

import android.content.Context;

/**
 * What the owner thinks about things, typed by them in Settings ("Your views"). Autopilot
 * uses it only when someone asks their opinion, in their own texting style. Phone-only
 * storage; it is sent with Autopilot requests and never learned from other chats.
 */
final class OwnerViews {
    static final int LIMIT=1200;
    private static final String FILE="owner_views",KEY="text";
    private OwnerViews(){}
    static String clean(String text){
        String value=text==null?"":text.replace("\0","").strip();
        if(value.length()>LIMIT)throw new IllegalArgumentException("Keep your views within 1,200 characters.");
        return value;
    }
    static String read(Context c){
        try{return clean(c.getSharedPreferences(FILE,0).getString(KEY,""));}catch(RuntimeException unreadable){return "";}
    }
    // Persist before acknowledging, so a killed process cannot bring back the old text.
    @android.annotation.SuppressLint("ApplySharedPref")
    static String save(Context c,String text){
        String value=clean(text);
        if(!c.getSharedPreferences(FILE,0).edit().putString(KEY,value).commit())throw new IllegalStateException("Your views could not be saved. Try again.");
        return value;
    }
}
