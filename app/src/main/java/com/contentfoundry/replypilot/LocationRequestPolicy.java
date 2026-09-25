package com.contentfoundry.replypilot;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Detects current-location questions only; never infers a location or consent. */
final class LocationRequestPolicy {
    private static final Pattern[] QUESTIONS={
        Pattern.compile("\\bwhere (?:are|r) (?:you|u)\\b|\\bwhere(?:'re|re) (?:you|u)\\b|\\bwhere (?:you|u) at\\b"),
        Pattern.compile("\\b(?:are|r) (?:you|u)(?: still| already| back)? (?:at )?(?:home|work|the office|school|the gym)\\b"),
        Pattern.compile("^(?:hey[,!]? )?(?:you|u)(?: still| already| back)? (?:at )?home(?: yet| now)?[?!.]*$"),
        Pattern.compile("\\b(?:your|ur) (?:current )?(?:location|whereabouts)\\b|\\bcurrent location\\b|^location[?!.]*$")
    };
    private static final Pattern FUTURE=Pattern.compile("\\b(?:when|will you|will u|tomorrow|next|later|tonight)\\b");
    private LocationRequestPolicy(){}
    static boolean isQuestion(String value){return isQuestion(value==null?List.of():List.of(value));}
    static boolean isQuestion(List<String> incoming){
        List<String> parts=new ArrayList<>();
        if(incoming!=null)for(String value:incoming){
            if(value==null)continue;
            String text=Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('’','\'').replace('‘','\'');
            text=TextWhitespace.RUN.matcher(text).replaceAll(" ").trim();if(!text.isEmpty())parts.add(text);
        }
        String joined=String.join(" ",parts);if(FUTURE.matcher(joined).find())return false;parts.add(joined);
        for(String text:parts){
            for(Pattern pattern:QUESTIONS)if(pattern.matcher(text).find())return true;
        }
        return false;
    }
}
