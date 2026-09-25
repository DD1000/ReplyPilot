package com.contentfoundry.replypilot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A tiny display summary, never a send target or source of conversation history. */
final class LaunchInboxPolicy {
    static final int LIMIT=20,SNIPPET=240,NAME=120,ADDRESS=80,MAX_PLAIN=64*1024;
    static final long MAX_SAFE_INTEGER=9_007_199_254_740_991L;
    record Row(long thread,long id,long date,String kind,int type,int read,String address,String name,String body,boolean pinned,boolean readOnly,int mType,int box){}
    static final Comparator<Row> RECENT=Comparator.comparingLong(Row::date).reversed().thenComparing(Comparator.comparingLong(Row::id).reversed()).thenComparing(Row::kind).thenComparingLong(Row::thread);
    static boolean valid(Row row){return row!=null&&row.thread()>0&&row.thread()<=MAX_SAFE_INTEGER&&row.id()>0&&row.id()<=MAX_SAFE_INTEGER&&row.date()>=0&&row.date()<=MAX_SAFE_INTEGER&&("sms".equals(row.kind())||"mms".equals(row.kind()))&&row.type()>=0&&row.type()<=6&&(row.read()==0||row.read()==1);}
    static List<Row> select(Collection<Row> candidates){
        Map<Long,Row> newest=new HashMap<>();for(Row row:candidates){
            if(!valid(row))continue;Row old=newest.get(row.thread());if(old!=null&&RECENT.compare(old,row)<=0)continue;newest.put(row.thread(),row);
            if(newest.size()>LIMIT){Row oldest=null;for(Row value:newest.values())if(oldest==null||RECENT.compare(value,oldest)>0)oldest=value;newest.remove(oldest.thread());}
        }
        List<Row> result=new ArrayList<>(newest.values());result.sort(RECENT);return List.copyOf(result);
    }
    static String text(String raw,int limit){
        if(raw==null||limit<=0)return "";StringBuilder out=new StringBuilder(Math.min(raw.length(),limit));boolean space=false;
        for(int i=0;i<raw.length()&&out.length()<limit;){int point=raw.codePointAt(i);i+=Character.charCount(point);
            if(Character.isISOControl(point)||Character.isWhitespace(point)){if(out.length()>0)space=true;continue;}
            if(space){if(out.length()+1+Character.charCount(point)>limit)break;out.append(' ');space=false;}
            if(out.length()+Character.charCount(point)>limit)break;out.appendCodePoint(point);
        }
        return out.toString();
    }
    static Row scrub(Row row,boolean contacts){String address=text(row.address(),ADDRESS),name=contacts?text(row.name(),NAME):address;if(name.isEmpty())name=address;
        String snippet=text(row.body(),SNIPPET);if("mms".equals(row.kind())&&snippet.isEmpty())snippet=InboxPreviewPolicy.fallback(row.mType());
        return new Row(row.thread(),row.id(),row.date(),row.kind(),row.type(),row.read(),address,name,snippet,row.pinned(),row.readOnly(),row.mType(),row.box());}
}
