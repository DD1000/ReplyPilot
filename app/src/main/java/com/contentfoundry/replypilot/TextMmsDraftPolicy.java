package com.contentfoundry.replypilot;

import java.util.*;

/** Text MMS uses its own verified context; it must never answer an older SMS. */
final class TextMmsDraftPolicy {
    record Source(long id,long date,String text,String address) {}
    static boolean sameSource(Source saved,Source current){return saved!=null&&current!=null&&saved.id()>0&&saved.date()>0&&saved.text()!=null&&!saved.text().isBlank()&&saved.address()!=null&&!saved.address().isBlank()&&saved.equals(current);}
    record Turn(long thread,int type,String body,boolean completeText) {}
    record Context(List<ReplyPrompt.Message> messages,List<String> unanswered,boolean incomplete) {}
    static Context context(long thread,List<Turn> rows,boolean hasOlder){
        if(thread<=0||rows.isEmpty()||rows.get(rows.size()-1).type()!=1)throw new IllegalArgumentException("Choose the latest incoming text to reply to.");
        int start=rows.size();while(start>0&&rows.get(start-1).type()!=2)start--;
        boolean incomplete=hasOlder&&start==0;List<ReplyPrompt.Message> messages=new ArrayList<>();List<String> unanswered=new ArrayList<>();
        for(int i=0;i<rows.size();i++){
            Turn row=rows.get(i);if(row.thread()!=thread)throw new IllegalArgumentException("This conversation changed. Open it again.");
            if(row.type()!=1&&row.type()!=2){if(i>=start)incomplete=true;continue;}
            String body=row.body()==null?"":row.body();
            if(i>=start){if(!row.completeText()||body.isBlank()||body.length()>1600)incomplete=true;unanswered.add(body);}
            if(row.completeText()&&!body.isBlank())messages.add(new ReplyPrompt.Message(thread,row.type(),body));
        }
        if(unanswered.size()>CloudPrompt.RECENT_LIMIT)incomplete=true;
        return new Context(List.copyOf(messages),List.copyOf(unanswered),incomplete);
    }
    static String reason(Context context,boolean planningHold,boolean girlfriendPaused,String engagement){
        if(girlfriendPaused)return "conversation_complete";
        if(planningHold||PlanSafety.incoming(context.unanswered()))return "plans_need_input";
        if(context.incomplete()||LocationRequestPolicy.isQuestion(context.unanswered()))return "needs_review";
        if(context.unanswered().stream().anyMatch(RequestSafety::needsReview)||RequestSafety.needsReview(String.join("\n",context.unanswered())))return "needs_review";
        if("girlfriend".equals(engagement)&&context.unanswered().stream().anyMatch(GirlfriendPolicy::bedtime))return "conversation_complete";
        if(ReplyEngagementPolicy.repeatedAcknowledgment(context.messages()))return "conversation_complete";
        return null;
    }
}
