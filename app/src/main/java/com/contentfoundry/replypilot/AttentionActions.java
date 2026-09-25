package com.contentfoundry.replypilot;

import android.content.Context;
import android.content.ContentValues;
import org.json.JSONObject;

/** Old lock-screen reply actions remain uncallable after upgrade; notifications only open the chat. */
public final class AttentionActions {
    private static final String RETIRED="This reply action was retired. Open the conversation.";
    private AttentionActions(){}
    public static JSONObject offer(Context c,long thread,long base,String reason){return new JSONObject();}
    public static JSONObject perform(Context c,String token){throw new IllegalStateException(RETIRED);}
    static void schedulePlanDelay(Context c,long thread,long base){}
    public static void cancelAutomaticPlans(Context c){recover(c);}
    static JSONObject claimPlanDelay(Context c){return null;}
    static long nextPlanWait(Context c){return -1;}
    static void sendPlanDelay(Context c,JSONObject action){throw new IllegalStateException(RETIRED);}
    public static boolean check(Context c,long thread,long base,String token){return false;}
    public static void complete(Context c,String token,boolean success,String note){recover(c);}
    static JSONObject claim(Context c){return null;}
    static void interrupt(Context c,String token){recover(c);}
    static void recover(Context c){synchronized(PilotApp.SEND_LOCK){
        ContentValues values=new ContentValues();values.put("state","stale");values.put("note",RETIRED);
        Store.get(c).getWritableDatabase().update("attention_actions",values,"state IN ('offered','queued','running')",null);
    }}
    static long nextWait(Context c){return -1;}
    static JSONObject row(Context c,String token){return null;}
    static String delayBlock(Context c,JSONObject job){return job!=null&&"delay".equals(job.optString("attention_kind"))?RETIRED:null;}
    static String block(Context c,JSONObject action,long ownJob,boolean quiet){return RETIRED;}
    static void problem(Context c,String token){}
}
