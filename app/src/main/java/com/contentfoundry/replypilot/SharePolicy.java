package com.contentfoundry.replypilot;

import java.net.URI;
import java.util.*;

/** Bounds and trust rules for external share intents. No provider is opened here. */
final class SharePolicy {
    static final int MAX_TEXT=1600,MAX_ITEMS=6,MAX_CLIP_ITEMS=32;
    private SharePolicy(){}
    static String text(CharSequence value){String text=value==null?"":value.toString();if(text.length()>MAX_TEXT||text.indexOf('\0')>=0)throw new IllegalArgumentException("Share up to 1,600 characters at a time.");return text;}
    static String append(String existing,String incoming){
        existing=text(existing);incoming=text(incoming);return text(existing.isEmpty()?incoming:incoming.isEmpty()?existing:existing+"\n\n"+incoming);
    }
    static boolean mime(String value){String type=value==null?"":value.toLowerCase(Locale.ROOT).split(";",2)[0].trim();return type.equals("text/plain")||type.equals("text/vcard")||type.equals("text/x-vcard")||type.startsWith("image/")||type.startsWith("video/")||type.startsWith("audio/");}
    static String uri(String value,String ownPackage){
        try{
            if(value==null||value.length()>8192)throw new IllegalArgumentException();
            URI uri=new URI(value);String authority=uri.getRawAuthority();
            if(!"content".equals(uri.getScheme())||authority==null||authority.isBlank()||authority.contains("@")||authority.contains("%")||uri.getFragment()!=null)throw new IllegalArgumentException();
            authority=authority.toLowerCase(Locale.ROOT);String own=ownPackage.toLowerCase(Locale.ROOT);
            boolean contacts=Set.of("contacts","com.android.contacts","com.google.android.contacts").contains(authority);
            boolean exportedCard=contacts&&uri.getRawPath()!=null&&uri.getRawPath().matches("/contacts/as_(?:multi_)?vcard/[^/]+")&&uri.getQuery()==null;
            if(authority.equals(own)||authority.startsWith(own+".")||(contacts&&!exportedCard)||Set.of("mms","sms","mms-sms","com.android.providers.telephony","telephony").contains(authority))throw new IllegalArgumentException();
            return uri.toString();
        }catch(Exception invalid){throw new IllegalArgumentException("Choose a file shared by another app. This attachment location is not allowed.");}
    }
    static List<String> uris(List<String> values,String ownPackage){
        if(values.size()>MAX_CLIP_ITEMS)throw new IllegalArgumentException("Share up to six attachments at a time.");
        Set<String> unique=new LinkedHashSet<>();for(String value:values){unique.add(uri(value,ownPackage));if(unique.size()>MAX_ITEMS)throw new IllegalArgumentException("Share up to six attachments at a time.");}return List.copyOf(unique);
    }
}
