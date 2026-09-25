package com.contentfoundry.replypilot;

import java.util.*;

/** Both transports get a fair opportunity to supply actual owner/contact text. */
final class ReplyReadinessPolicy {
    static final int MAX_ROWS=2000,MAX_TEXT=32_000,MAX_PARTS=64,MAX_FILE_BYTES=128_000;
    record Entry(long thread,String kind,long id,long date,int type,int mmsType,String text,boolean automatic,boolean matched,boolean readable){}
    interface Source {boolean advance();Entry read();default boolean complete(){return true;}}
    private ReplyReadinessPolicy(){}
    static boolean eligible(long thread,Entry entry){
        if(entry==null||entry.thread()!=thread||entry.id()<=0||entry.date()<0||!entry.matched()||!entry.readable()||entry.text()==null||entry.text().length()>MAX_TEXT)return false;
        if("sms".equals(entry.kind()))return entry.type()==1||entry.type()==2&&!entry.automatic();
        // MMS is only currently sent manually. A colliding SMS id must never
        // exclude a real sent MMS; unsent/failed/notification rows do not count.
        return "mms".equals(entry.kind())&&((entry.type()==1&&entry.mmsType()==132)||(entry.type()==2&&entry.mmsType()==128));
    }
    static ReplyEligibility.Result collect(long thread,List<ChatLog.Turn> imported,Source sms,Source mms,Runnable validate){
        SmsHistoryPolicy.validateThread(thread);ReplyEligibility.Counter count=new ReplyEligibility.Counter(ReplyEligibility.MINIMUM_TOTAL);
        for(ChatLog.Turn turn:imported)count.add(turn);validate.run();if(count.eligible())return count.result(false);
        Source[] sources={sms,mms};boolean[] ended={false,false};int scanned=0;
        // Readiness is not a timeline. Alternating transports prevents a long SMS
        // burst from consuming the budget before any older MMS evidence is read.
        while(scanned<MAX_ROWS&&!(ended[0]&&ended[1])){
            for(int i=0;i<2&&scanned<MAX_ROWS;i++){
                if(ended[i])continue;validate.run();Source source=sources[i];
                if(!source.advance()){ended[i]=true;continue;}scanned++;
                Entry row=source.read();if(eligible(thread,row))count.add(new ChatLog.Turn(row.type()==2?"Me":"Them",row.text()));
                if(count.eligible()){validate.run();return count.result(false);}
            }
        }
        validate.run();return count.result(ended[0]&&ended[1]&&sms.complete()&&mms.complete());
    }
}
