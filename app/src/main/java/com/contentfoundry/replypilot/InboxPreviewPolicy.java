package com.contentfoundry.replypilot;

import java.util.List;
import java.util.Locale;

/** Display snippets and latest-message filters; never determines an SMS send base. */
final class InboxPreviewPolicy {
    static final int MAX_PARTS=64,MAX_TEXT=240,MAX_FILE_BYTES=4096,MAX_FILE_PARTS=4;
    record Query(String selection,List<String> arguments){}
    private InboxPreviewPolicy(){}
    static boolean eligible(String kind,int smsType,int box,int messageType){
        if("sms".equals(kind))return smsType>=1&&smsType<=6&&smsType!=3;
        return "mms".equals(kind)&&box>=1&&box<=5&&box!=3&&(messageType==128||messageType==130||messageType==132);
    }
    static String transport(int smsType,int box){
        if(smsType>=1&&smsType<=6&&box==0)return "sms";
        if(smsType==0&&box>=1&&box<=5)return "mms";
        return "";
    }
    static Query latest(long thread,String kind){
        if(thread<=0||!("sms".equals(kind)||"mms".equals(kind)))throw new IllegalArgumentException("Choose a conversation.");
        return new Query("sms".equals(kind)?"thread_id=? AND type!=3":"thread_id=? AND msg_box!=3 AND m_type IN (128,130,132)",List.of(Long.toString(thread)));
    }
    static boolean textPart(String type){return type!=null&&"text/plain".equals(type.split(";",2)[0].trim().toLowerCase(Locale.ROOT));}
    static String fallback(int messageType){return messageType==130?"Media message — download pending":"Media message";}
    static final class Snippet {
        private final StringBuilder body=new StringBuilder();
        private int scanned;
        boolean full(){return body.length()>=MAX_TEXT||scanned>=MAX_PARTS;}
        void add(String type,String value){if(full())return;scanned++;if(!textPart(type)||value==null)return;int remaining=MAX_TEXT-body.length()-(body.length()==0?0:1);if(remaining<=0)return;String text=LaunchInboxPolicy.text(value,remaining);if(!text.isEmpty()){if(body.length()>0)body.append(' ');body.append(text);}}
        String result(int messageType){return body.length()==0?fallback(messageType):body.toString();}
    }
}
