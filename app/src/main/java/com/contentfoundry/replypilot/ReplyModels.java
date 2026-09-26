package com.contentfoundry.replypilot;

import android.content.Context;

/**
 * Per-chat choice of the stronger reply model ("Use Astra for this chat"). Off by default.
 * The phone only asks for "premium"; the owner's server decides which model that is.
 */
final class ReplyModels {
    private static final String FILE="reply_models";
    private ReplyModels(){}
    static String key(long thread){if(thread<=0)throw new IllegalArgumentException("Choose a conversation first.");return "premium:"+thread;}
    static boolean premium(Context c,long thread){
        try{return c.getSharedPreferences(FILE,0).getBoolean(key(thread),false);}catch(RuntimeException unreadable){return false;}
    }
    // Persist before acknowledging, so the switch never shows a choice that wasn't saved.
    @android.annotation.SuppressLint("ApplySharedPref")
    static boolean setPremium(Context c,long thread,boolean on){
        if(!c.getSharedPreferences(FILE,0).edit().putBoolean(key(thread),on).commit())throw new IllegalStateException("This setting could not be saved. Try again.");
        return on;
    }
}
