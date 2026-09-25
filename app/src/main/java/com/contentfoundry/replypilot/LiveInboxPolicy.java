package com.contentfoundry.replypilot;

import java.util.*;

/** A recent inbox delta is bounded by rows scanned, not by unique conversations. */
final class LiveInboxPolicy {
    static final int SCAN_LIMIT=128,THREAD_LIMIT=40;
    record Candidate(long thread,MediaHistoryPolicy.Position position){}
    static final class Scan {
        private final String kind;
        private final List<Candidate> rows=new ArrayList<>();
        private int scanned;
        Scan(String kind){if(!Set.of("sms","mms").contains(kind))throw new IllegalArgumentException("Invalid message source.");this.kind=kind;}
        boolean full(){return scanned>=SCAN_LIMIT;}
        void add(long thread,long storedDate,long id,int type,int box,int messageType){
            if(full())throw new IllegalStateException("Recent inbox scan is complete.");
            scanned++;
            if(thread<=0||thread>9_007_199_254_740_991L||!InboxPreviewPolicy.eligible(kind,type,box,messageType))return;
            try{rows.add(new Candidate(thread,new MediaHistoryPolicy.Position(MediaHistoryPolicy.date(kind,storedDate),kind,id)));}
            catch(IllegalArgumentException invalid){/* Invalid provider metadata cannot become a displayed row. */}
        }
        List<Candidate> rows(){return List.copyOf(rows);}
    }
    static List<Long> threads(Collection<Candidate> candidates){
        Map<Long,Candidate> newest=new HashMap<>();
        for(Candidate row:candidates){Candidate prior=newest.get(row.thread());if(prior==null||MediaHistoryPolicy.compare(row.position(),prior.position())>0)newest.put(row.thread(),row);}
        List<Candidate> sorted=new ArrayList<>(newest.values());
        sorted.sort((a,b)->{int compared=MediaHistoryPolicy.compare(b.position(),a.position());return compared==0?Long.compare(a.thread(),b.thread()):compared;});
        List<Long> threads=new ArrayList<>();for(int i=0;i<Math.min(THREAD_LIMIT,sorted.size());i++)threads.add(sorted.get(i).thread());return List.copyOf(threads);
    }
}
