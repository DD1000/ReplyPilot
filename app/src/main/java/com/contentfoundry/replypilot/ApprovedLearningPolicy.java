package com.contentfoundry.replypilot;

import java.util.*;

/** Explicit, successfully sent owner choices only; never self-train on Autopilot. */
final class ApprovedLearningPolicy {
    static final int LIMIT=12;
    record Choice(long id,long thread,boolean approved,boolean automatic,String status,String delivery,String attention,String incoming,String reply){}
    record Example(String incoming,String reply){}
    record Anchor(long thread,long base,long date,int type,String address,String body){}
    static boolean sameAnchor(Anchor expected,Anchor actual){
        return expected!=null&&actual!=null&&expected.thread()>0&&expected.base()>0&&expected.date()>0&&expected.type()==1&&expected.address()!=null&&expected.body()!=null&&expected.equals(actual);
    }

    static boolean eligible(Choice c,long thread,long after){
        return c!=null&&c.id()>after&&c.thread()==thread&&c.approved()&&!c.automatic()&&"".equals(c.attention())
            &&("sent".equals(c.status())||"delivered".equals(c.delivery()))&&c.reply()!=null&&!c.reply().isBlank();
    }
    static String bounded(String value,int max){
        if(value==null)return "";if(value.length()<=max)return value;
        int end=max;if(Character.isHighSurrogate(value.charAt(end-1)))end--;return value.substring(0,end);
    }
    static List<Example> examples(List<Choice> choices,long thread,long after){
        List<Choice> ordered=new ArrayList<>(choices);ordered.sort(Comparator.comparingLong(Choice::id).reversed());
        LinkedHashMap<String,Example> selected=new LinkedHashMap<>();
        for(Choice c:ordered){
            if(!eligible(c,thread,after))continue;
            String incoming=bounded(c.incoming(),600).strip(),reply=bounded(c.reply(),360).strip();
            // Empty context remains honest rather than inventing the original message.
            if(incoming.isEmpty())incoming="[Owner-approved outgoing text; earlier context unavailable]";
            String key=TextWhitespace.RUN.matcher(incoming+"\n"+reply).replaceAll(" ").strip();
            selected.putIfAbsent(key,new Example(incoming,reply));if(selected.size()==LIMIT)break;
        }
        return List.copyOf(selected.values());
    }
}
