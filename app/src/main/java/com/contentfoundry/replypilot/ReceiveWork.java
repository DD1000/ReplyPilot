package com.contentfoundry.replypilot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Carrier receipts must not wait for full inbox reads or history imports. */
final class ReceiveWork {
    static final ExecutorService SMS=serial("reply-pilot-incoming-sms");
    // Download, retry, recovery and callbacks share one lane so a completed
    // download cannot race a retry or cleanup of the same private carrier file.
    static final ExecutorService MMS=serial("reply-pilot-carrier-mms");
    private ReceiveWork(){}
    static ExecutorService serial(String name){
        return Executors.newSingleThreadExecutor(work->{Thread thread=new Thread(work,name);thread.setDaemon(true);return thread;});
    }
}
