package com.contentfoundry.replypilot;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Only an explicit immutable, authenticated notification PendingIntent reaches this receiver. */
public final class AttentionReceiver extends BroadcastReceiver {
    public static final String ACTION="com.contentfoundry.replypilot.ATTENTION";
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        Uri uri=intent.getData();
        if(uri==null||!"replypilot".equals(uri.getScheme())||!"attention".equals(uri.getHost())||uri.getPathSegments().size()!=1||uri.getQuery()!=null||uri.getFragment()!=null)return;
        String token=uri.getLastPathSegment();if(!AttentionPolicy.token(token))return;
        try{KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);if(keyguard==null||keyguard.isDeviceLocked())return;}catch(RuntimeException unavailable){return;}
        Context c=context.getApplicationContext();PendingResult pending=goAsync();
        PilotApp.IO.execute(()->{
            try{AttentionActions.perform(c,token);}
            catch(Exception unavailable){AttentionActions.problem(c,token);
            }finally{pending.finish();}
        });
    }
}
