package com.contentfoundry.replypilot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Thread-scoped SMS cursor pagination, shared by the provider adapter and tests. */
final class SmsHistoryPolicy {
    static final int PAGE_SIZE=50;
    static final int CONTEXT_SIZE=50;
    static final String ORDER="date DESC, _id DESC";

    record Before(long date,long id) {
        Before {
            if(date<0||id<=0)throw new IllegalArgumentException("Invalid conversation history cursor.");
        }
    }
    record Query(String selection,List<String> arguments) {}

    static void validateThread(long thread) {
        if(thread<=0)throw new IllegalArgumentException("Choose a conversation first.");
    }
    static Before cursor(long date,long id) {
        if(date==0&&id==0)return null;
        return new Before(date,id);
    }
    static Query query(long thread,Before before) {
        validateThread(thread);
        if(before==null)return new Query("thread_id=?",List.of(Long.toString(thread)));
        return new Query("thread_id=? AND (date<? OR (date=? AND _id<?))",
            List.of(Long.toString(thread),Long.toString(before.date),Long.toString(before.date),Long.toString(before.id)));
    }
    static boolean belongs(long thread,long rowThread,long date,long id,Before before) {
        return thread==rowThread&&thread>0&&date>=0&&id>0
            &&(before==null||date<before.date||(date==before.date&&id<before.id));
    }
    static boolean isContext(long thread,long rowThread,int type,String body) {
        // Android type 2 is sent. Draft, queued, outbox and failed messages teach nothing.
        return thread==rowThread&&thread>0&&(type==1||type==2)&&body!=null&&!body.isBlank();
    }

    /** Consumes provider rows in ORDER, retaining one page plus a has-more flag. */
    static final class Page<T> {
        private record Entry<T>(Before position,T value) {}
        private final long thread;
        private final Before boundary;
        private final List<Entry<T>> newestFirst=new ArrayList<>();
        private boolean hasMore;

        Page(long thread,Before boundary) {validateThread(thread);this.thread=thread;this.boundary=boundary;}

        /** Returns true once a valid lookahead row proves an older page exists. */
        boolean add(long rowThread,long date,long id,T value) {
            if(hasMore)return true;
            if(!belongs(thread,rowThread,date,id,boundary))return false;
            if(newestFirst.size()==PAGE_SIZE){hasMore=true;return true;}
            newestFirst.add(new Entry<>(new Before(date,id),value));
            return false;
        }
        List<T> history() {
            List<T> chronological=new ArrayList<>(newestFirst.size());
            for(Entry<T> entry:newestFirst)chronological.add(entry.value);
            Collections.reverse(chronological);
            return chronological;
        }
        boolean hasMore() {return hasMore;}
        Before before() {return newestFirst.isEmpty()?null:newestFirst.get(newestFirst.size()-1).position;}
    }
}
