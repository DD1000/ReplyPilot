package com.contentfoundry.replypilot;

/** Local receipt clocks, never the sender's timestamp, define an incoming burst. */
final class BurstPolicy {
    static final long QUIET_MS=10_000;
    static long remaining(long receivedWall,long receivedElapsed,int receivedBoot,long nowWall,long nowElapsed,int boot){
        long elapsed=receivedBoot==boot&&receivedBoot>=0&&nowElapsed>=receivedElapsed
            ?nowElapsed-receivedElapsed:Math.max(0,nowWall-receivedWall);
        return elapsed>=QUIET_MS?0:QUIET_MS-elapsed;
    }
    static boolean current(long recordedToken,long recordedBase,long requestedBase,boolean failed){
        return requestedBase>0&&!failed&&(recordedToken==0||recordedBase==requestedBase);
    }
    /** A successful manual retry may be based on an intervening failed outgoing row. */
    static boolean manualCoversLatest(long recordedToken,long recordedBase,long jobBase,boolean failed){
        return jobBase>0&&!failed&&((recordedToken==0&&recordedBase==0)
            ||(recordedToken>0&&recordedBase>0&&recordedBase<=jobBase));
    }
    static boolean unchanged(long expected,long actual,boolean current,long remaining){return expected==actual&&current&&remaining==0;}
    static boolean mayBind(long receiptToken,long latestToken){return receiptToken>0&&receiptToken==latestToken;}
    static boolean mayReplace(long existingToken,long candidateToken){return candidateToken>=existingToken;}
}
