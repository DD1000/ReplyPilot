package com.contentfoundry.replypilot;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import org.json.JSONArray;
public class RespondService extends Service {
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int flags,int startId){if(i!=null&&i.getData()!=null){String address=i.getData().getSchemeSpecificPart().split("\\?")[0];String body=i.getStringExtra(Intent.EXTRA_TEXT);if(SendPolicy.validAddress(address)&&body!=null){PilotApp.IO.execute(()->{long t=Messages.thread(this,address);Store.get(this).draft(t,Messages.latest(this,t),body,new JSONArray().put(body),"Your draft");Notices.show(this,(int)t,"Review your reply","Open Reply Pilot to accept and send.",t);stopSelf(startId);});return START_NOT_STICKY;}}stopSelf(startId);return START_NOT_STICKY;}
}
