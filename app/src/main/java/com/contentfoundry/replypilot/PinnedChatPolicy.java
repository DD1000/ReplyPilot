package com.contentfoundry.replypilot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pins reorder conversation summaries; they never change message or send state. */
final class PinnedChatPolicy {
    static final int RECENT_LIMIT=150;
    record Row<T>(long thread,long date,long id,T value){}

    static void validateThread(long thread){
        if(thread<=0)throw new IllegalArgumentException("Choose a conversation first.");
    }
    static Set<Long> ids(Collection<String> stored){
        Set<Long> result=new HashSet<>();
        if(stored!=null)for(String value:stored){
            try{long id=Long.parseLong(value);if(id>0)result.add(id);}
            catch(NumberFormatException ignored){/* Ignore invalid private preference entries. */}
        }
        return Set.copyOf(result);
    }
    private static <T> Comparator<Row<T>> newest(){
        return Comparator.<Row<T>>comparingLong(Row::date).reversed()
            .thenComparing(Comparator.comparingLong((Row<T> row)->row.id()).reversed())
            .thenComparingLong(Row::thread);
    }
    static <T> List<Row<T>> select(Collection<Row<T>> candidates,Set<Long> pins){return select(candidates,pins,false);}
    static <T> List<Row<T>> selectAll(Collection<Row<T>> candidates,Set<Long> pins){return select(candidates,pins,true);}
    private static <T> List<Row<T>> select(Collection<Row<T>> candidates,Set<Long> pins,boolean all){
        Comparator<Row<T>> recent=newest();Map<Long,Row<T>> unique=new HashMap<>();
        for(Row<T> row:candidates){
            if(row.thread()<=0||row.id()<=0)continue;
            Row<T> previous=unique.get(row.thread());
            if(previous==null||recent.compare(row,previous)<0)unique.put(row.thread(),row);
        }
        List<Row<T>> ordered=new ArrayList<>(unique.values());
        ordered.sort(Comparator.<Row<T>,Boolean>comparing(row->!pins.contains(row.thread())).thenComparing(recent));
        List<Row<T>> result=new ArrayList<>();int normal=0;
        for(Row<T> row:ordered){
            if(all||pins.contains(row.thread())||normal++<RECENT_LIMIT)result.add(row);
        }
        return List.copyOf(result);
    }
}
