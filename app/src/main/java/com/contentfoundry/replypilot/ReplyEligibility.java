package com.contentfoundry.replypilot;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Distinct labeled turns provide evidence of both sides of a personal conversation. */
final class ReplyEligibility {
    static final int MINIMUM_TOTAL=20,MINIMUM_OWNER=5,MINIMUM_INCOMING=5;
    record Result(boolean eligible,int total,int owner,int incoming,boolean countsAreMinimum,boolean scanComplete){
        Result(boolean eligible,int total,int owner,int incoming){this(eligible,total,owner,incoming,false,true);}
        String message(){return eligible?"Autopilot is trained for this chat.":"Train Autopilot for this chat before turning it on.";}
    }
    static Result evaluate(List<ChatLog.Turn> turns){
        Counter counter=new Counter(Integer.MAX_VALUE);for(ChatLog.Turn turn:turns)counter.add(turn);return counter.result(true);
    }
    /** Readiness needs a small proof, not an in-memory copy of the whole archive. */
    static final class Counter {
        private final Set<String> seen=new HashSet<>();private final int maximumPerSpeaker;
        private int owner,incoming;private boolean minimum;
        Counter(int maximumPerSpeaker){this.maximumPerSpeaker=maximumPerSpeaker;}
        void add(ChatLog.Turn turn){
            if(turn==null||(!"Me".equals(turn.speaker())&&!"Them".equals(turn.speaker())))return;
            String text=normalize(turn.text());if(text.isEmpty()||placeholder(text))return;
            boolean me="Me".equals(turn.speaker());if((me?owner:incoming)>=maximumPerSpeaker){minimum=true;return;}
            if(!seen.add(turn.speaker()+"\n"+text))return;if(me)owner++;else incoming++;
        }
        boolean eligible(){return owner+incoming>=MINIMUM_TOTAL&&owner>=MINIMUM_OWNER&&incoming>=MINIMUM_INCOMING;}
        Result result(boolean complete){return new Result(eligible(),owner+incoming,owner,incoming,minimum||!complete,complete);}
    }
    private static String normalize(String value){
        if(value==null)return "";
        String text=Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replaceAll("[\\u200b-\\u200d\\ufeff]","").replaceAll("['’‘]","");
        return TextWhitespace.RUN.matcher(text).replaceAll(" ").trim().replaceFirst("[.!?…]+$","").trim();
    }
    private static boolean placeholder(String text){
        return text.matches("[<\\[]?(?:media|image|video|audio|sticker|document|gif|contact card) omitted[>\\]]?")
            ||Set.of("this message was deleted","you deleted this message","message deleted","attachment","[attachment]").contains(text);
    }
}
