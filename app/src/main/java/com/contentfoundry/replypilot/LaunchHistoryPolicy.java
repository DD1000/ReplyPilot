package com.contentfoundry.replypilot;

import java.util.*;

/** Bounded display text only; no drafts, send base, credentials or attachment data. */
final class LaunchHistoryPolicy {
    static final int THREADS=10,MESSAGES=30,BODY=2048,MAX_PLAIN=2*1024*1024;
    record Thread(long id,long date,long message,String kind,String address,String name){}
    record Text(long thread,long id,long date,String kind,int type,int read,String body,int mType,int box,boolean truncated,boolean textUnavailable){
        Text(long thread,long id,long date,String kind,int type,int read,String body,int mType,int box,boolean truncated){this(thread,id,date,kind,type,read,body,mType,box,truncated,false);}
    }
    private static boolean id(long value){return value>0&&value<=LaunchInboxPolicy.MAX_SAFE_INTEGER;}
    private static boolean date(long value){return value>=0&&value<=LaunchInboxPolicy.MAX_SAFE_INTEGER;}
    private static boolean kind(String value){return "sms".equals(value)||"mms".equals(value);}
    static List<Thread> threads(Collection<Thread> input){
        Comparator<Thread> recent=Comparator.comparingLong(Thread::date).reversed().thenComparing(Comparator.comparingLong(Thread::message).reversed()).thenComparingLong(Thread::id);
        Map<Long,Thread> unique=new HashMap<>();for(Thread row:input){if(row==null||!id(row.id())||!id(row.message())||!date(row.date())||!kind(row.kind()))continue;Thread old=unique.get(row.id());if(old==null||recent.compare(row,old)<0)unique.put(row.id(),row);}
        return unique.values().stream().sorted(recent).limit(THREADS).toList();
    }
    static boolean valid(Text row,long thread){
        if(row==null||!id(thread)||row.thread()!=thread||!id(row.id())||!date(row.date())||!kind(row.kind())||row.type()<0||row.type()>6||(row.read()!=0&&row.read()!=1))return false;
        return InboxPreviewPolicy.eligible(row.kind(),row.type(),row.box(),row.mType());
    }
    static List<Text> messages(Collection<Text> input,long thread){
        Comparator<Text> order=(a,b)->MediaHistoryPolicy.compare(new MediaHistoryPolicy.Position(a.date(),a.kind(),a.id()),new MediaHistoryPolicy.Position(b.date(),b.kind(),b.id()));
        Map<String,Text> unique=new HashMap<>();for(Text row:input){if(!valid(row,thread))continue;String body=text(row.body());boolean clipped=row.truncated()||row.body()!=null&&row.body().length()>BODY,unavailable="mms".equals(row.kind())&&row.textUnavailable();if(body.isBlank()&&"mms".equals(row.kind())&&!unavailable)body=row.mType()==130?"Media message — download pending":"Media message";
            Text safe=new Text(thread,row.id(),row.date(),row.kind(),row.type(),row.read(),body,row.mType(),row.box(),clipped,unavailable);String key=row.kind()+":"+row.id();Text old=unique.get(key);if(old==null||order.compare(safe,old)>0)unique.put(key,safe);
        }
        List<Text> sorted=unique.values().stream().sorted(order).toList();return List.copyOf(sorted.subList(Math.max(0,sorted.size()-MESSAGES),sorted.size()));
    }
    static String text(String raw){
        if(raw==null)return "";StringBuilder out=new StringBuilder(Math.min(raw.length(),BODY));
        for(int i=0;i<raw.length()&&out.length()<BODY;){int point=raw.codePointAt(i);i+=Character.charCount(point);if(Character.isISOControl(point)&&point!='\n'&&point!='\t')continue;if(out.length()+Character.charCount(point)>BODY)break;out.appendCodePoint(point);}return out.toString();
    }
}
