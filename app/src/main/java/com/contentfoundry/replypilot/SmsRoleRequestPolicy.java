package com.contentfoundry.replypilot;

/** One foreground request for Android's SMS role. It never grants the role itself. */
final class SmsRoleRequestPolicy {
    static final long CHOOSER_TIMEOUT_MS=30_000;
    private final int requestCode;
    private long launchedAt;
    private boolean launched,leftApp,returned;

    SmsRoleRequestPolicy(int requestCode){this.requestCode=requestCode;}
    int requestCode(){return requestCode;}
    String phase(){return launched?"chooser":"confirmation";}
    void launch(long now){launched=true;launchedAt=now;}
    void leaveApp(){if(launched)leftApp=true;}
    boolean returned(int code){if(!launched||requestCode!=code)return false;returned=true;return true;}
    long remaining(long now){return launched?Math.max(0,CHOOSER_TIMEOUT_MS-Math.max(0,now-launchedAt)):CHOOSER_TIMEOUT_MS;}
    String completion(boolean roleHeld,long now,boolean resumed){
        // RESULT_OK is deliberately not an input: only Android's live role state
        // can establish success, including when Android omits its result callback.
        if(roleHeld)return "selected";
        if(returned||(resumed&&leftApp))return "notSelected";
        if(launched&&remaining(now)==0)return "timeout";
        return null;
    }
}
