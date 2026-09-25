package com.contentfoundry.replypilot;

import java.util.*;

/** Contact-scoped, explicitly written practice answers; never a send instruction. */
final class PilotTrainingPolicy {
    static final int EXAMPLES=24,MEANINGS=40,MAX_TEXT=600,MAX_TURNS=8,HISTORY=50;
    static final long MAX_AGE=30*60*1000L;
    record Example(long id,long thread,String scope,String incoming,String reply){}
    record Turn(String speaker,String text){}
    /** Match the relay sanitizer before any saved pair or history turn can poison later requests. */
    private static String clean(String raw){
        StringBuilder result=new StringBuilder();
        for(int i=0;i<raw.length();i++){
            char ch=raw.charAt(i);
            if(ch<=8||ch==11||ch==12||(ch>=14&&ch<=31)||(ch>=127&&ch<=159)||(ch>=0x200b&&ch<=0x200f)||(ch>=0x202a&&ch<=0x202e)||(ch>=0x2060&&ch<=0x206f)||ch==0xfeff)continue;
            result.append(ch);
        }
        int start=0,end=result.length();
        while(start<end&&(Character.isWhitespace(result.charAt(start))||Character.isSpaceChar(result.charAt(start))))start++;
        while(end>start&&(Character.isWhitespace(result.charAt(end-1))||Character.isSpaceChar(result.charAt(end-1))))end--;
        return result.substring(start,end);
    }
    static String text(Object value,boolean empty){
        if(!(value instanceof String raw))throw new IllegalArgumentException("Enter your own text.");
        String result=clean(raw);if((!empty&&result.isEmpty())||raw.length()>MAX_TEXT)throw new IllegalArgumentException("Use "+(empty?"up to":"1 to")+" 600 characters.");return result;
    }
    static String clipped(String value){return clean(ApprovedLearningPolicy.bounded(value,MAX_TEXT));}
    static List<Example> examples(List<Example> rows,long thread,String scope){
        List<Example> ordered=new ArrayList<>(rows);ordered.sort(Comparator.comparingLong(Example::id).reversed());
        LinkedHashMap<String,Example> kept=new LinkedHashMap<>();
        for(Example row:ordered){if(row.thread()!=thread||!Objects.equals(scope,row.scope())||row.incoming()==null||row.reply()==null||row.incoming().isBlank()||row.reply().isBlank())continue;
            String incoming=clipped(row.incoming()),reply=clipped(row.reply());if(incoming.isEmpty()||reply.isEmpty())continue;kept.putIfAbsent(incoming+"\u0000"+reply,new Example(row.id(),thread,scope,incoming,reply));if(kept.size()==EXAMPLES)break;
        }return List.copyOf(kept.values());
    }
    static boolean received(long requestedThread,long actualThread,long requestedId,long actualId,String kind,int type,int mmsType){
        return requestedThread>0&&requestedThread==actualThread&&requestedId>0&&requestedId==actualId&&type==1&&("sms".equals(kind)||("mms".equals(kind)&&mmsType==132));
    }
    static boolean fresh(long created,long now){return created>=0&&now>=created&&now-created<=MAX_AGE;}
    /** One native-owned token per simulated contact line. A repeated submit is not a new lesson. */
    static final class Session {
        final String id=UUID.randomUUID().toString();final long created;
        final List<Turn> turns=new ArrayList<>();
        String token="",acceptedToken="",acceptedReply="",requestId=UUID.randomUUID().toString(),scenario="";
        int answers;boolean awaiting=true;
        Session(long now){created=now;}
        boolean accept(String suppliedToken,Object suppliedReply){
            String reply=text(suppliedReply,false);
            if(!acceptedToken.isEmpty()&&acceptedToken.equals(suppliedToken)){
                if(!acceptedReply.equals(reply))throw new IllegalStateException("That practice answer was already saved. Start another scenario to try a different response.");
                return false;
            }
            if(awaiting||answers>=MAX_TURNS||token.isEmpty()||!token.equals(suppliedToken))throw new IllegalStateException("This practice turn changed. Reopen Train Pilot.");
            turns.add(new Turn("me",reply));answers++;acceptedToken=token;acceptedReply=reply;awaiting=answers<MAX_TURNS;requestId=UUID.randomUUID().toString();return true;
        }
        void generated(Object nextScenario,Object nextMessage){
            if(!awaiting||answers>=MAX_TURNS)throw new IllegalStateException("This practice is complete.");
            if(!(nextScenario instanceof String s)||s.isBlank()||s.length()>300)throw new IllegalStateException("The service returned an invalid practice scenario. Try again.");
            String message=text(nextMessage,false);scenario=s.strip();turns.add(new Turn("them",message));token=UUID.randomUUID().toString();awaiting=false;
        }
        String incoming(){if(turns.isEmpty()||!"them".equals(turns.get(turns.size()-1).speaker()))throw new IllegalStateException("Wait for the practice message first.");return turns.get(turns.size()-1).text();}
        boolean complete(){return answers>=MAX_TURNS;}
    }
}
