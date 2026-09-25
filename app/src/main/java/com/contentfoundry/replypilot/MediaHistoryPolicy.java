package com.contentfoundry.replypilot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stable mixed-message ordering despite SMS milliseconds and MMS seconds. */
final class MediaHistoryPolicy {
    static final int PAGE_SIZE=40;
    static final int ANCHOR_SIDE=20;
    private static final long MAX_SAFE_INTEGER=9_007_199_254_740_991L;
    record Position(long date,String kind,long id){
        Position{if(date<0||date>MAX_SAFE_INTEGER||id<=0||id>MAX_SAFE_INTEGER||!("sms".equals(kind)||"mms".equals(kind)))throw new IllegalArgumentException("Invalid media history position.");}
        String key(){return kind+":"+id;}
    }
    record Query(String selection,List<String> arguments,String order){}
    record Row<T>(Position position,T value){}
    record Page<T>(List<Row<T>> rows,boolean hasMore){}
    static long integer(Object value,boolean positive){
        if(!(value instanceof Number number))throw new IllegalArgumentException("Invalid media history position.");
        double d=number.doubleValue();
        if(!Double.isFinite(d)||d!=Math.rint(d)||d<(positive?1:0)||d>MAX_SAFE_INTEGER)throw new IllegalArgumentException("Invalid media history position.");
        return number.longValue();
    }
    static long date(String kind,long stored){
        if(stored<0)throw new IllegalArgumentException("This message has an invalid date.");
        long date;
        try{date="mms".equals(kind)?Math.multiplyExact(stored,1000):stored;}
        catch(ArithmeticException invalid){throw new IllegalArgumentException("This message has an invalid date.");}
        if(date>MAX_SAFE_INTEGER)throw new IllegalArgumentException("This message has an invalid date.");
        return date;
    }
    static int compare(Position a,Position b){
        int result=Long.compare(a.date(),b.date());
        if(result==0)result=Integer.compare("sms".equals(a.kind())?0:1,"sms".equals(b.kind())?0:1);
        return result!=0?result:Long.compare(a.id(),b.id());
    }
    static Query query(long thread,String source,Position boundary,String direction){
        if(thread<=0||!("sms".equals(source)||"mms".equals(source))||!("older".equals(direction)||"newer".equals(direction)))throw new IllegalArgumentException("Invalid media history request.");
        boolean older="older".equals(direction),mms="mms".equals(source);
        String sign=older?"<":">",order=older?"date DESC, _id DESC":"date ASC, _id ASC";
        long stored=mms?boundary.date()/1000:boundary.date();
        List<String> args=new ArrayList<>();args.add(Long.toString(thread));args.add(Long.toString(stored));
        String comparison;
        if(mms&&boundary.date()%1000!=0){
            comparison="date"+(older?"<=":">")+"?";
        }else if(source.equals(boundary.kind())){
            comparison="(date"+sign+"? OR (date=? AND _id"+sign+"?))";
            args.add(Long.toString(stored));args.add(Long.toString(boundary.id()));
        }else{
            boolean sourceBefore="sms".equals(source);
            comparison="date"+sign+((older==sourceBefore)?"=":"")+"?";
        }
        return new Query("thread_id=? AND "+comparison,List.copyOf(args),order);
    }
    static Query latestQuery(long thread,String source){
        if(thread<=0||!("sms".equals(source)||"mms".equals(source)))throw new IllegalArgumentException("Invalid media history request.");
        return new Query("thread_id=?",List.of(Long.toString(thread)),"date DESC, _id DESC");
    }
    static <T> Page<T> latest(Collection<Row<T>> candidates,int limit){
        if(limit<1||limit>PAGE_SIZE)throw new IllegalArgumentException("Invalid media history request.");
        Map<String,Row<T>> unique=new HashMap<>();for(Row<T> row:candidates)unique.putIfAbsent(row.position().key(),row);
        List<Row<T>> rows=new ArrayList<>(unique.values());rows.sort(Comparator.comparing(Row<T>::position,MediaHistoryPolicy::compare));
        boolean more=rows.size()>limit;if(more)rows=new ArrayList<>(rows.subList(rows.size()-limit,rows.size()));
        return new Page<>(List.copyOf(rows),more);
    }
    static <T> Page<T> page(Collection<Row<T>> candidates,Position boundary,String direction,int limit){
        if(!("older".equals(direction)||"newer".equals(direction))||limit<1||limit>PAGE_SIZE)throw new IllegalArgumentException("Invalid media history request.");
        boolean older="older".equals(direction);Map<String,Row<T>> unique=new HashMap<>();
        for(Row<T> row:candidates){int compared=compare(row.position(),boundary);if(older?compared<0:compared>0)unique.putIfAbsent(row.position().key(),row);}
        List<Row<T>> rows=new ArrayList<>(unique.values());rows.sort(Comparator.comparing(Row<T>::position,MediaHistoryPolicy::compare));
        boolean more=rows.size()>limit;
        if(more)rows=new ArrayList<>(older?rows.subList(rows.size()-limit,rows.size()):rows.subList(0,limit));
        return new Page<>(List.copyOf(rows),more);
    }
}
