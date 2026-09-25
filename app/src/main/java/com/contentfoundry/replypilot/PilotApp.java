package com.contentfoundry.replypilot;

import android.app.Application;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PilotApp extends Application {
    public static final Object SEND_LOCK = new Object();
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    public static final ExecutorService AI = Executors.newSingleThreadExecutor();
    public static volatile boolean foreground = false;
    @Override public void onCreate() { super.onCreate(); Notices.channels(this); AttentionJob.recoverAndSchedule(this); recoverMms(); HistoryLearning.retire(this); }
    static void recoverMms(Context context){Context app=context.getApplicationContext();ReceiveWork.MMS.execute(()->{try{MmsDownloads.recover(app);}catch(RuntimeException ignored){}try{MmsAttachments.recover(app);}catch(RuntimeException ignored){}});}
    private void recoverMms(){recoverMms(this);}
}
