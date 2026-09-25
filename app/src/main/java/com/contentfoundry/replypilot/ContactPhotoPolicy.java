package com.contentfoundry.replypilot;

import java.util.LinkedHashMap;
import java.util.Map;

/** Local thumbnail URL, decoding and memory limits. No Android/provider access. */
final class ContactPhotoPolicy {
    static final int SIDE=128,MAX_INPUT=2*1024*1024,MAX_IMAGE=256*1024,MAX_ENTRIES=80,MAX_BYTES=2*1024*1024;
    private ContactPhotoPolicy(){}
    static boolean allowed(boolean contacts,boolean sms,boolean role,boolean foreground){return contacts&&sms&&role&&foreground;}
    static String path(String number){
        String address=ContactPolicy.number(number);
        return address==null?"":"/contact-photo/"+(address.startsWith("+")?"%2B"+address.substring(1):address);
    }
    static String address(String encodedPath,String query,String fragment){
        if(query!=null||fragment!=null||encodedPath==null||!encodedPath.matches("/contact-photo/(?:%2B)?[0-9]{3,25}"))return null;
        String value=encodedPath.substring("/contact-photo/".length()).replace("%2B","+");
        return SendPolicy.validAddress(value)?value:null;
    }
    static boolean dimensions(int width,int height){return width>0&&height>0&&width<=32768&&height<=32768&&(long)width*height<=32L*1024*1024;}
    static int sample(int width,int height){
        if(!dimensions(width,height))return 0;
        int size=1;while((Math.max(width,height)+size-1)/size>SIDE*2)size*=2;return size;
    }
    record Size(int width,int height){}
    static Size fit(int width,int height){
        if(!dimensions(width,height))throw new IllegalArgumentException("Invalid contact photo.");
        double factor=Math.min(1d,(double)SIDE/Math.max(width,height));
        return new Size(Math.max(1,(int)Math.round(width*factor)),Math.max(1,(int)Math.round(height*factor)));
    }
    /** Empty bytes memoize a missing photo. Every invalidation rejects in-flight reads. */
    static final class Cache {
        private final LinkedHashMap<String,byte[]> entries=new LinkedHashMap<>(16,.75f,true);
        private long revision;private int bytes;
        synchronized long revision(){return revision;}
        synchronized boolean current(long expected){return expected==revision;}
        synchronized byte[] get(String address){byte[] value=entries.get(address);return value==null?null:value.clone();}
        synchronized boolean put(String address,long expected,byte[] image){
            if(expected!=revision||!SendPolicy.validAddress(address)||image==null||image.length>MAX_IMAGE)return false;
            byte[] prior=entries.put(address,image.clone());if(prior!=null)bytes-=prior.length;bytes+=image.length;
            while(entries.size()>MAX_ENTRIES||bytes>MAX_BYTES){Map.Entry<String,byte[]> oldest=entries.entrySet().iterator().next();bytes-=oldest.getValue().length;entries.remove(oldest.getKey());}
            return true;
        }
        synchronized void clear(){revision++;entries.clear();bytes=0;}
        synchronized int size(){return entries.size();}
        synchronized int bytes(){return bytes;}
    }
}
