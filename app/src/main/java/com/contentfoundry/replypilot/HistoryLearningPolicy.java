package com.contentfoundry.replypilot;

import java.util.*;

/** Batches bound work, never the amount of available historical text inspected. */
final class HistoryLearningPolicy {
    static final int FRAGMENT=4000,BATCH_ITEMS=40,BATCH_CHARS=48000,MEMORY=1800;
    static boolean eligible(String kind,int type,int box,int mmsType){return "sms".equals(kind)?type==1||type==2:"mms".equals(kind)&&(box==1&&mmsType==132||box==2&&mmsType==128);}
    static void requireCompleteText(String kind,boolean unavailable,boolean truncated){
        if("mms".equals(kind)&&(unavailable||truncated))throw new IllegalStateException("Some MMS text could not be fully read from your phone. Tap Retry to refresh saved history. AI replies remain paused until preparation finishes.");
    }
    static List<String> fragments(String value){
        List<String> parts=new ArrayList<>();if(value==null||value.isBlank())return parts;
        for(int start=0;start<value.length();){int end=Math.min(value.length(),start+FRAGMENT);if(end<value.length()&&Character.isHighSurrogate(value.charAt(end-1))&&Character.isLowSurrogate(value.charAt(end)))end--;parts.add(value.substring(start,end));start=end;}return parts;
    }
    static String memory(String text){if(text==null||text.length()>MEMORY||text.indexOf('\0')>=0)throw new IllegalArgumentException("The learned history response was invalid.");return text;}
    static boolean accepts(int count,int characters,int next){return count>=0&&count<BATCH_ITEMS&&characters>=0&&next>0&&next<=FRAGMENT&&characters+next<=BATCH_CHARS;}
    static boolean complete(long remaining,long completed,long total){return remaining==0&&completed==total&&total>=0;}
}
