package com.contentfoundry.replypilot;

import java.util.*;

/** Paging limits bound one operation, never the number of stored chats/messages. */
final class HistoryArchivePolicy {
    static final int HISTORY_PAGE=40,INBOX_PAGE=60,SCAN_PAGE=256,PAGE_BYTES=2*1024*1024,RECORD_BYTES=8*1024*1024;
    static final String HISTORY_ORDER="date DESC,rank DESC,id DESC";
    static final String INBOX_ORDER="date DESC,thread DESC";
    record Query(String where,List<String> args){}
    static int limit(Object requested,int maximum){if(requested==null)return maximum;long value=MediaHistoryPolicy.integer(requested,true);if(value>maximum)throw new IllegalArgumentException("This cache page is too large.");return (int)value;}
    static Query history(long thread,MediaHistoryPolicy.Position before){
        if(thread<=0)throw new IllegalArgumentException("Choose a conversation.");
        if(before==null)return new Query("thread=?",List.of(Long.toString(thread)));
        return new Query("thread=? AND (date<? OR (date=? AND (rank<? OR (rank=? AND id<?))))",List.of(Long.toString(thread),Long.toString(before.date()),Long.toString(before.date()),"mms".equals(before.kind())?"1":"0","mms".equals(before.kind())?"1":"0",Long.toString(before.id())));
    }
    static Query inbox(long date,long thread,boolean cursor){
        if(!cursor)return new Query("1",List.of());
        if(date<0||thread<=0)throw new IllegalArgumentException("Invalid cached inbox position.");
        return new Query("(date<? OR (date=? AND thread<?))",List.of(Long.toString(date),Long.toString(date),Long.toString(thread)));
    }
    static boolean sameAddress(String expected,String actual,boolean group){return expected!=null&&actual!=null&&expected.equals(actual)&&(!actual.isBlank()||group);}
    static boolean canRead(int access){return (access&3)==3;}
    static boolean canDeliver(int now,int captured,long revision,long expected){return canRead(now)&&now==captured&&revision==expected;}
    /** A scan marker is independent from the public content snapshot. Even after a
     * clock change it must differ from every prior seen marker so deletions work. */
    static long scanMarker(long generation,long messageSeen,long threadSeen,long now){return Math.max(now,Math.addExact(Math.max(generation,Math.max(messageSeen,threadSeen)),1));}
    /** Refreshing unchanged records must not invalidate pages already being read. */
    static long snapshotAfterScan(long previous,long marker,boolean contentChanged){return previous==0||contentChanged?marker:previous;}
    /** Three automatic retries per invalidation; an ongoing failure cannot spin forever. */
    static long retryDelay(int failures){return failures>=1&&failures<=3?15_000L<<(failures-1):0;}
    static String visibleName(String address,String name,boolean contacts){return contacts&&name!=null&&!name.isBlank()?name:address;}
    static final class Budget {
        private final int limit;int rows,bytes;
        Budget(int limit){this.limit=limit;}
        boolean add(int size){if(size<0||size>RECORD_BYTES)throw new IllegalArgumentException("This message is too large to cache safely.");if(rows>=limit||(rows>0&&(long)bytes+size>PAGE_BYTES))return false;rows++;bytes+=size;return true;}
    }
}
