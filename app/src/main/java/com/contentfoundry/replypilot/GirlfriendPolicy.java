package com.contentfoundry.replypilot;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Direct present-tense boundaries only; quoted or historical examples do not pause. */
final class GirlfriendPolicy {
    private static final Pattern QUOTED=Pattern.compile("\"[^\"]*\"|“[^”]*”|‘[^’]*’|(?<![\\p{L}\\p{N}])'[^']*'(?![\\p{L}\\p{N}])",Pattern.DOTALL);
    private static final String END="(?:[^\\p{L}\\p{N}]*|,.*)$";
    private static final String PET="(?: ?(?:babe|baby|love|hun|honey|sweetheart|darling|dear|my love|gorgeous|beautiful|xx|xoxo))*";
    private static final String TAIL="(?: (?:i love you|love you|love u|ily|good ?night|night[- ]?night|see you tomorrow|talk (?:to you )?tomorrow))*";
    private static final Pattern NIGHT=Pattern.compile("^(?:good ?night|night[- ]?night|night|gn)"+PET+TAIL+END);
    private static final Pattern SLEEP=Pattern.compile("^(?:(?:i(?:'m| am|m) )?(?:(?:going|heading|headed|off) to (?:bed|sleep)|(?:gonna|about to) (?:go to )?(?:bed|sleep)|calling it a night|turning in)|i (?:need to|have to|got to|should) (?:get some )?sleep|(?:it's |its )?bedtime(?: for me)?|time for bed)(?: (?:now|soon|for the night))*"+PET+TAIL+END);
    private static final Pattern STOP=Pattern.compile("^(?:(?:please|pls) )?(?:(?:can|could|would) you (?:please )?)?(?:(?:i said )?stop(?: (?:texting|messaging|contacting|calling)(?: me)?)?|leave me alone|let me sleep|(?:don't|do not) (?:text|message|contact|call) me|(?:let's|lets|let us) (?:stop talking|end (?:this|the) conversation)|i (?:don't|do not) want to talk|(?:give me|i need) (?:some )?space)(?: (?:now|please|anymore|again|for now|tonight|today))*"+END);
    private static final Pattern NONCURRENT=Pattern.compile("\\b(?:not|never|yesterday|earlier|said|saying|say|told|remember|was|were|used to|last night|if|when)\\b|\\b(?:don't|can't|cannot|won't|wasn't|weren't)\\b");
    private static final Set<String> REACTIONS=Set.of("yes","yeah","yep","yup","no","nope","nah","lol","lmao","haha","hahaha","hehe","aww","aw","love you","i love you","love u","ily","x","xx","xoxo","goodnight","good night","night","gn");
    record State(long base,boolean paused){State{if(base<0)throw new IllegalArgumentException("Invalid conversation position.");}}
    private static String normalize(String text){
        if(text==null)return "";
        return TextWhitespace.RUN.matcher(Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('’','\'').replace('‘','\'').replaceAll("\\p{Cf}","")).replaceAll(" ").trim();
    }
    static boolean bedtime(String body){
        if(body==null||body.isBlank())return false;
        String unquoted=QUOTED.matcher(body.replaceAll("(?s)```.*?(?:```|$)"," ")).replaceAll(" ").replaceAll("(?m)^\\s*>.*$"," ");
        for(String raw:Normalizer.normalize(unquoted,Normalizer.Form.NFKC).split("[.!;\\r\\n。！？]+")){
            String clause=normalize(raw).replaceFirst("^(?:ok(?:ay)?|alright|all right)[,!]? +","");
            if(STOP.matcher(clause).matches())return true;
            if(NONCURRENT.matcher(clause.split(",",2)[0]).find())continue;
            if(NIGHT.matcher(clause).matches()||SLEEP.matcher(clause).matches())return true;
        }
        return false;
    }
    static boolean shouldResume(String body){
        if(body==null||bedtime(body))return false;
        String text=normalize(body).replaceAll("[?!¿؟！？]+","").trim();
        if(text.isEmpty()||ReplyEngagementPolicy.acknowledgment(text))return false;
        String words=text.replaceAll("[^\\p{L}\\p{N}' ]"," ").replaceAll(" +"," ").trim();
        if(words.isEmpty()||REACTIONS.contains(words)||ReplyEngagementPolicy.acknowledgment(words))return false;
        long letters=words.codePoints().filter(Character::isLetterOrDigit).count();
        return letters>=4||(letters>=2&&body.matches("(?s).*[?¿؟？].*"));
    }
    static State next(State previous,long base,String body,boolean girlfriendMode){
        State old=previous==null?new State(0,false):previous;
        if(!girlfriendMode)return new State(old.base(),false);
        if(base<=old.base()||base<=0)return old;
        return new State(base,bedtime(body)||(old.paused()&&!shouldResume(body)));
    }
    static State resumed(State previous){return new State(previous.base(),false);}
}
