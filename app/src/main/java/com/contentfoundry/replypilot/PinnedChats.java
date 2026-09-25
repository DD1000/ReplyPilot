package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/** Private, durable thread IDs only. Pinning never modifies contacts or SMS. */
final class PinnedChats {
    static final java.util.concurrent.ExecutorService EXECUTOR=new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(64),work->{Thread t=new Thread(work,"reply-pilot-pin");t.setDaemon(true);return t;});
    private static final Object LOCK=new Object();
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences("pinned_chats",Context.MODE_PRIVATE);}
    static Set<Long> ids(Context context){synchronized(LOCK){
        return PinnedChatPolicy.ids(prefs(context).getStringSet("threads",Set.of()));
    }}
    // Called by the dedicated, ordered pin worker; acknowledge only after the preference is durable.
    @android.annotation.SuppressLint("ApplySharedPref")
    static void set(Context context,long thread,boolean pinned){
        PinnedChatPolicy.validateThread(thread);
        if(pinned){
                if(!Messages.allowed(context,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow SMS access before pinning a conversation.");
                if(Messages.latest(context,thread)<=0){
                    try{if(MediaNavigation.latestRow(context,thread,"mms")==null)throw new IllegalStateException("This conversation no longer has any messages to pin.");}
                    catch(org.json.JSONException unavailable){throw new IllegalStateException("The conversation could not be checked.");}
                }
                if(!Messages.allowed(context,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow SMS access before pinning a conversation.");
            }
        synchronized(LOCK){
            if(pinned&&!Messages.allowed(context,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow SMS access before pinning a conversation.");
            SharedPreferences preferences=prefs(context);
            Set<String> previous=new HashSet<>(preferences.getStringSet("threads",Set.of()));
            Set<Long> selected=new HashSet<>(PinnedChatPolicy.ids(previous));
            if(pinned)selected.add(thread);else selected.remove(thread);
            Set<String> stored=new HashSet<>();for(long id:selected)stored.add(Long.toString(id));
            if(!preferences.edit().putStringSet("threads",stored).commit()){
                // A failed disk commit may already have changed the in-memory
                // preferences. Restore the prior selection before reporting failure.
                try{preferences.edit().putStringSet("threads",previous).commit();}catch(RuntimeException ignored){/* Preserve the original save error. */}
                throw new IllegalStateException("The pinned conversation could not be saved. Please try again.");
            }
        }
    }
}
