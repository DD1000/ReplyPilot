package com.contentfoundry.replypilot;

import java.util.*;

/** Selects only approved context for one conversation, before JSON encoding or networking. */
public final class CloudPrompt {
    public static final int RECENT_LIMIT=50;
    public static final int OLDER_TEXT_LIMIT=600;
    public record Turn(String speaker,String text) {}
    public record Input(String relationship,String samples,String tone,List<Turn> history,List<String> style) {}
    public static String samples(String value){if(value==null)return "";if(value.length()>8000)throw new IllegalArgumentException("Keep conversation samples within 8,000 characters.");return value.strip();}
    private static String clip(String text,int limit){if(text.length()<=limit)return text;int end=Character.isHighSurrogate(text.charAt(limit-1))?limit-1:limit;return text.substring(0,end);}
    public static Input build(long thread,List<ReplyPrompt.Message> history,String relationship,String samples,String tone,boolean matchStyle) {
        List<ReplyPrompt.Message> selected=new ArrayList<>();for(ReplyPrompt.Message m:history)if(m.thread()==thread&&(m.type()==1||m.type()==2)&&m.body()!=null&&!m.body().isBlank())selected.add(m);
        if(selected.isEmpty()||selected.get(selected.size()-1).type()!=1)throw new IllegalArgumentException("Choose an incoming message to reply to.");
        int unanswered=0;for(int i=selected.size()-1;i>=0&&selected.get(i).type()==1;i--)unanswered++;
        // Every text in a burst is part of the message being answered. Never
        // silently omit a correction or condition from an earlier incoming turn.
        if(unanswered>RECENT_LIMIT||selected.subList(selected.size()-unanswered,selected.size()).stream().anyMatch(m->m.body().length()>1600))throw new IllegalArgumentException("This incoming message burst is too long for an AI draft. Write a reply manually.");
        // Keep a combined window of incoming + actually sent texts for this person.
        // Disabling history matching retains only short immediate reply context.
        List<ReplyPrompt.Message> recent=selected.subList(Math.max(0,selected.size()-RECENT_LIMIT),selected.size());
        List<Turn> messages=new ArrayList<>();for(int i=Math.max(0,recent.size()-(matchStyle?RECENT_LIMIT:Math.max(8,unanswered)));i<recent.size();i++){ReplyPrompt.Message m=recent.get(i);messages.add(new Turn(m.type()==2?"me":"them",i>=recent.size()-unanswered?m.body():clip(m.body(),OLDER_TEXT_LIMIT)));}
        List<String> style=new ArrayList<>();if(matchStyle)for(ReplyPrompt.Message m:ReplyPrompt.sentExamples(thread,recent))style.add(clip(m.body(),220));
        return new Input(ReplyPrompt.promptContext(relationship),samples(samples),ReplyPrompt.normalizeTone(tone),List.copyOf(messages),List.copyOf(style));
    }
}
