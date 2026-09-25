package com.contentfoundry.replypilot;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Engagement changes ordinary closing behavior, never a safety or planning gate. */
final class ReplyEngagementPolicy {
    private static final Set<String> ACKS=Set.of("ok","okay","alright","all right","k","kk","thanks","thank you","thank you so much","thanks so much","thanks again","thank you again","thx","ty","got it","gotcha","sounds good","all good","no problem","no worries","you're welcome","you are welcome");
    private ReplyEngagementPolicy(){}
    static String normalize(String value){
        return "always_reply".equals(value)||"keep_going".equals(value)||"girlfriend".equals(value)?value:"natural";
    }
    static String validate(String value){
        if(value==null||"natural".equals(value))return "natural";
        if("always_reply".equals(value)||"keep_going".equals(value)||"girlfriend".equals(value))return value;
        throw new IllegalArgumentException("Choose a reply engagement option.");
    }
    static boolean repliesToClosings(String value){
        return "always_reply".equals(value)||"keep_going".equals(value)||"girlfriend".equals(value);
    }
    static boolean acknowledgment(String value){
        if(value==null)return false;
        String text=Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('’','\'').replace('‘','\'');
        if(text.contains("?")||text.contains("¿")||text.contains("؟"))return false;
        StringBuilder clean=new StringBuilder();boolean emoji=false;
        for(int cp:text.codePoints().toArray()){
            if(cp==0x1f44d||cp==0x1f44c||cp==0x1f64f||cp==0x1f64c){emoji=true;continue;}
            if(cp==0xfe0f||(cp>=0x1f3fb&&cp<=0x1f3ff)||cp=='.'||cp=='!'||cp==0x2026||cp==',')continue;
            clean.appendCodePoint(cp);
        }
        String plain=TextWhitespace.RUN.matcher(clean).replaceAll(" ").trim();
        return ACKS.contains(plain)||(plain.isEmpty()&&emoji);
    }
    /** Actual sent replies count; unsent/failed rows and other contacts do not. */
    static boolean repeatedAcknowledgment(List<ReplyPrompt.Message> history){
        if(history==null||history.isEmpty())return false;
        ReplyPrompt.Message last=history.get(history.size()-1);
        if(last==null||last.type()!=1||!acknowledgment(last.body()))return false;
        boolean sent=false;
        for(int i=history.size()-2;i>=0;i--){
            ReplyPrompt.Message turn=history.get(i);if(turn==null||turn.thread()!=last.thread())continue;
            if(turn.type()==2){sent=true;continue;}
            if(turn.type()!=1)continue;
            if(!acknowledgment(turn.body()))return false;
            if(sent)return true;
        }
        return false;
    }
}
