package com.contentfoundry.replypilot;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;

/**
 * 0.11.0 analyzed every saved conversation before any AI reply. 0.12.0 trains one chat at a
 * time with Train Autopilot, so the old all-conversation summaries are deleted from the phone.
 */
final class HistoryLearning {
    private HistoryLearning(){}
    static void retire(Context context){
        Context c=context.getApplicationContext();
        PilotApp.IO.execute(()->{try{SQLiteDatabase.deleteDatabase(new File(c.getNoBackupFilesDir(),"history-learning-v1.db"));}catch(RuntimeException ignored){/* Retried on the next launch. */}});
    }
}
