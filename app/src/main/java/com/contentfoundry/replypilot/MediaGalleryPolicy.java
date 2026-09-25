package com.contentfoundry.replypilot;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** A carrier MMS envelope can contain only ordinary text. Gallery entries need a file part. */
final class MediaGalleryPolicy {
    private static final Pattern MIME=Pattern.compile("[a-z0-9!#$%&'*+.^_`|~-]+/[a-z0-9!#$%&'*+.^_`|~-]+");
    private static final Set<String> PRESENTATION=Set.of("text/plain","text/html","application/smil","application/smil+xml");
    static String type(String supplied){
        if(supplied==null)return "";
        String type=supplied.split(";",2)[0].trim().toLowerCase(Locale.ROOT);
        return type.indexOf('*')<0&&MIME.matcher(type).matches()?type:"";
    }
    static boolean attachment(long partId,String supplied){
        String type=type(supplied);
        return partId>0&&partId<=9_007_199_254_740_991L&&!type.isEmpty()&&!PRESENTATION.contains(type)&&!type.startsWith("multipart/");
    }
    static boolean displayPart(long partId,String supplied){return "text/plain".equals(type(supplied))||attachment(partId,supplied);}
    static boolean message(int box,int type){return (box==1||box==2||box==4||box==5)&&(type==128||type==132);}
}
