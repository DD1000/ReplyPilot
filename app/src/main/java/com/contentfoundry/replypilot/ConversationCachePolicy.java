package com.contentfoundry.replypilot;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, immutable serialized previews; callers serialize access to this map. */
final class ConversationCachePolicy {
    static final int MAX_THREADS=12,MAX_BYTES=2*1024*1024,MAX_PAGE_BYTES=512*1024;
    static final long TTL_MS=30_000;
    private record Entry(String page,int bytes,long revision,int access,long created){}
    private final LinkedHashMap<Long,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private int bytes;
    String get(long thread,long revision,int access,long now){
        Entry entry=entries.get(thread);if(entry==null)return null;
        if(entry.revision()!=revision||entry.access()!=access||now<entry.created()||now-entry.created()>=TTL_MS){remove(thread);return null;}
        return entry.page();
    }
    boolean put(long thread,String page,int size,long revision,int access,long now){
        if(thread<=0||page==null||size<=0||size>MAX_PAGE_BYTES||now<0)return false;
        remove(thread);entries.put(thread,new Entry(page,size,revision,access,now));bytes+=size;
        Iterator<Map.Entry<Long,Entry>> oldest=entries.entrySet().iterator();
        while(entries.size()>MAX_THREADS||bytes>MAX_BYTES){Map.Entry<Long,Entry> entry=oldest.next();bytes-=entry.getValue().bytes();oldest.remove();}
        return entries.containsKey(thread);
    }
    void clear(){entries.clear();bytes=0;}
    int size(){return entries.size();}
    int bytes(){return bytes;}
    private void remove(long thread){Entry old=entries.remove(thread);if(old!=null)bytes-=old.bytes();}
}
