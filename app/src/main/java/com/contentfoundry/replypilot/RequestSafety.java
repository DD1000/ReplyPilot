package com.contentfoundry.replypilot;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Obvious assistant tasks are held locally; nuanced personal intent is checked by the model. */
final class RequestSafety {
    static final int MAX_REPLY_CHARS=360,MAX_REPLY_WORDS=60;
    private static final String[] REQUESTS={
        "\\b(?:explain|teach|show|tell)\\s+(?:(?:to\\s+)?me\\s+)?(?:how\\s+to\\s+)(?:code|program|write\\s+(?:a\\s+)?(?:code|program|script))\\b",
        "\\b(?:write|generate|create|debug|fix|implement)\\s+(?:(?:me|us)\\s+)?(?:(?:a|an|some|the|my)\\s+)?(?:(?:java|javascript|python|sql|c\\+\\+|html)\\s+)?(?:code|script|program|function|algorithm)\\b",
        "\\b(?:can|could|would|will)\\s+you\\s+(?:please\\s+)?(?:code|program)\\b",
        "\\b(?:write|generate|create|draft|give|provide)\\s+(?:(?:me|us)\\s+)?(?:(?:a|an|the|my)\\s+)?(?:(?:short|long|\\d+[ -]word)\\s+)?(?:tutorial|essay|step[ -]by[ -]step\\s+(?:guide|instructions))\\b",
        "\\b(?:solve|do|complete|answer)\\s+(?:(?:my|this|the)\\s+)?(?:homework|assignment|math\\s+problem|equation)\\b",
        "\\b(?:ignore|disregard|override|forget)\\s+(?:(?:all|the|your|previous|prior|above|system|developer|earlier|safety)\\s+){0,5}(?:instructions?|rules?|prompts?)\\b",
        "\\b(?:act|behave|respond)\\s+(?:as|like)\\s+(?:(?:a|an|the)\\s+)?(?:chatgpt|(?:ai\\s+)?assistant|language\\s+model)\\b",
        "\\b(?:you\\s+are|become)\\s+(?:now\\s+)?(?:(?:a|an|the)\\s+)?(?:chatgpt|ai\\s+assistant|language\\s+model)\\b",
        "(?:^|\\n)\\s*(?:system|developer)(?:\\s+(?:message|prompt|instructions?))?\\s*:|</?(?:system|developer)>|<\\|(?:im_start|system|developer)\\b",
        "\\b(?:reveal|print|show|repeat)\\s+(?:(?:me|your|the|hidden|secret|system|developer)\\s+){0,5}(?:prompt|instructions)\\b",
        "\\b(?:teach|explain|tutor)\\s+(?:me\\s+)?(?:(?:the|some|basic|advanced)\\s+)?(?:java|javascript|python|programming|coding|calculus|algebra)\\b",
        "\\b(?:what(?:s| is)|send|share|give|tell)\\s+(?:(?:me|us)\\s+)?(?:(?:your|the|my)\\s+)?(?:password|passcode|bank account|credit card|social security|ssn|home address)\\b"
    };
    // Android rejects the desktop UNICODE_CHARACTER_CLASS flag. Spell out the
    // whitespace/digit classes so request guards retain their Unicode coverage.
    private static final Pattern[] PATTERNS=java.util.Arrays.stream(REQUESTS)
        .map(x->Pattern.compile(x.replace("\\s",TextWhitespace.CHARACTER_CLASS).replace("\\d","\\p{Nd}"))).toArray(Pattern[]::new);
    private static final Pattern LIST_ITEM=Pattern.compile("(?:^|\\n)[\\t ]*(?:[-*•]|\\d+[.)])[\\t ]+\\S");
    static boolean needsReview(String incoming){
        if(incoming==null)return false;
        String message=Normalizer.normalize(incoming,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\u200b-\\u200d\\ufeff]","").replaceAll("['’‘]","");
        for(Pattern pattern:PATTERNS)if(pattern.matcher(message).find())return true;
        return false;
    }
    static boolean unsuitableReply(String body){
        if(body==null)return true;
        if(body.length()>MAX_REPLY_CHARS||TextWhitespace.RUN.split(body.trim()).length>MAX_REPLY_WORDS||body.contains("```")||body.contains("~~~"))return true;
        var items=LIST_ITEM.matcher(body);int count=0;while(items.find())if(++count>=3)return true;
        return false;
    }
}
