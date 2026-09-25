package com.contentfoundry.replypilot;

import java.util.*;

/** Bounded, chronological practice context; it never authorizes an actual reply. */
final class PilotTrainingHistoryPolicy {
    record Entry(long thread,MediaHistoryPolicy.Position position,int type,int mmsType,String text){}
    record Query(String selection,List<String> arguments,String order){}
    interface TextSource {
        void advance();
        MediaHistoryPolicy.Position position();
        Entry text();
    }
    private PilotTrainingHistoryPolicy(){}

    static Query query(long thread,String kind,boolean incomingOnly){
        SmsHistoryPolicy.validateThread(thread);
        if(!"sms".equals(kind)&&!"mms".equals(kind))throw new IllegalArgumentException("Choose a conversation.");
        String selection="sms".equals(kind)
            ?"thread_id=? AND "+(incomingOnly?"type=1":"type IN (1,2)")+" AND body IS NOT NULL AND length(body)>0"
            :"thread_id=? AND "+(incomingOnly?"msg_box=1 AND m_type=132":"((msg_box=1 AND m_type=132) OR (msg_box=2 AND m_type=128))");
        return new Query(selection,List.of(Long.toString(thread)),"date DESC, _id DESC");
    }
    static boolean eligible(long thread,Entry row){
        if(row==null||row.thread()!=thread||row.position()==null||row.text()==null||PilotTrainingPolicy.clipped(row.text()).isEmpty())return false;
        if("sms".equals(row.position().kind()))return row.type()==1||row.type()==2;
        return "mms".equals(row.position().kind())&&((row.type()==1&&row.mmsType()==132)||(row.type()==2&&row.mmsType()==128));
    }
    static boolean hasIncoming(Collection<Entry> rows){return rows.stream().anyMatch(row->row.type()==1);}

    /** Merge metadata first so an older MMS never requires decoding its text unnecessarily. */
    static List<Entry> newest(long thread,TextSource sms,TextSource mms,int limit,Runnable validateAccess){
        return newest(thread,sms,mms,limit,PilotTrainingPolicy.HISTORY,validateAccess);
    }
    /** Train Autopilot reads a larger, still bounded, window with the same merge rules. */
    static List<Entry> newest(long thread,TextSource sms,TextSource mms,int limit,int maximum,Runnable validateAccess){
        SmsHistoryPolicy.validateThread(thread);
        if(maximum<1||maximum>PersonaPolicy.MAX_MESSAGES||limit<1||limit>maximum)throw new IllegalArgumentException("Invalid practice history limit.");
        List<Entry> rows=new ArrayList<>();Set<String> keys=new HashSet<>();
        validateAccess.run();sms.advance();mms.advance();
        while(rows.size()<limit&&(sms.position()!=null||mms.position()!=null)){
            validateAccess.run();
            TextSource source=sms.position()==null?mms:mms.position()==null?sms:MediaHistoryPolicy.compare(sms.position(),mms.position())>=0?sms:mms;
            Entry row=source.text();
            if(eligible(thread,row)&&keys.add(row.position().key()))rows.add(clean(row));
            if(rows.size()<limit)source.advance();
        }
        validateAccess.run();return List.copyOf(rows);
    }

    /** Keep the newest turns, reserving one place for older contact text when needed. */
    static List<Entry> select(long thread,Collection<Entry> candidates,Entry incomingFallback){
        SmsHistoryPolicy.validateThread(thread);
        Map<String,Entry> unique=new HashMap<>();
        for(Entry row:candidates)if(eligible(thread,row))unique.putIfAbsent(row.position().key(),clean(row));
        List<Entry> selected=new ArrayList<>(unique.values());
        selected.sort(Comparator.comparing(Entry::position,MediaHistoryPolicy::compare));
        if(selected.size()>PilotTrainingPolicy.HISTORY)selected=new ArrayList<>(selected.subList(selected.size()-PilotTrainingPolicy.HISTORY,selected.size()));
        if(!hasIncoming(selected)&&eligible(thread,incomingFallback)&&incomingFallback.type()==1){
            if(selected.size()==PilotTrainingPolicy.HISTORY)selected.remove(0);
            selected.add(clean(incomingFallback));selected.sort(Comparator.comparing(Entry::position,MediaHistoryPolicy::compare));
        }
        return List.copyOf(selected);
    }
    private static Entry clean(Entry row){return new Entry(row.thread(),row.position(),row.type(),row.mmsType(),PilotTrainingPolicy.clipped(row.text()));}
}
