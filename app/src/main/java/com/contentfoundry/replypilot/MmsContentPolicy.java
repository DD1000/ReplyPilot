package com.contentfoundry.replypilot;

import java.util.List;

/** MMS transport alone does not imply an attachment, but incomplete evidence does. */
final class MmsContentPolicy {
    static final String TEXT="text",ATTACHMENTS="attachments",UNKNOWN="unknown";
    record Part(long id,String mime,boolean unavailable,boolean truncated){}
    private MmsContentPolicy(){}
    static boolean presentation(String mime){return "application/smil".equals(mime)||"application/smil+xml".equals(mime);}
    static String classify(List<Part> parts,String body,boolean complete){
        if(!complete||parts==null||parts.isEmpty())return UNKNOWN;
        boolean text=false,attachment=false;
        for(Part part:parts){
            if(part==null||part.id()<=0||part.id()>9_007_199_254_740_991L||part.unavailable()||part.truncated())return UNKNOWN;
            String mime=MmsTextPolicy.mime(part.mime());
            if(mime.isEmpty())return UNKNOWN;
            if(presentation(mime))continue;
            if("text/plain".equals(mime))text=true;else attachment=true;
        }
        if(attachment)return ATTACHMENTS;
        return text&&body!=null&&!body.isBlank()?TEXT:UNKNOWN;
    }
    static boolean draftableText(String kind,String body){return TEXT.equals(kind)&&body!=null&&!body.isBlank()&&body.length()<=1600;}
}
