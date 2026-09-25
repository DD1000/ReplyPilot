package com.contentfoundry.replypilot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Decoding and disclosure limits are shared by capture and focused JVM tests. */
final class MediaContextPolicy {
    static final int MAX_IMAGES=3,MAX_EDGE=1024,MAX_JPEG_BYTES=153600,MAX_TOTAL_BYTES=460800;
    static final int MAX_SOURCE_BYTES=20*1024*1024,MAX_ALL_SOURCE_BYTES=24*1024*1024,MAX_IMAGE_SOURCE_BYTES=12*1024*1024;
    static final long MAX_AGE_MS=5*60*1000,MAX_VIDEO_MS=120000;
    record Size(int width,int height){}
    static Size scaled(int width,int height){
        if(width<=0||height<=0||width>20000||height>20000||(long)width*height>100_000_000L)throw new IllegalArgumentException("This image has unsupported dimensions.");
        double ratio=Math.min(1d,(double)MAX_EDGE/Math.max(width,height));return new Size(Math.max(1,(int)Math.floor(width*ratio)),Math.max(1,(int)Math.floor(height*ratio)));
    }
    static int sample(int width,int height){scaled(width,height);int sample=1;while(width/sample>MAX_EDGE||height/sample>MAX_EDGE)sample*=2;return sample;}
    static boolean accepts(int currentCount,int currentBytes,int addedBytes){return currentCount>=0&&currentCount<MAX_IMAGES&&currentBytes>=0&&addedBytes>0&&addedBytes<=MAX_JPEG_BYTES&&(long)currentBytes+addedBytes<=MAX_TOTAL_BYTES;}
    static boolean fresh(long captured,long now){return captured>=0&&now>=captured&&now-captured<=MAX_AGE_MS;}
    static List<Long> frames(long duration,int available){
        if(duration<=0||duration>MAX_VIDEO_MS||available<=0)return List.of();
        int count=Math.min(MAX_IMAGES,available);if(count==1)return List.of(duration/2);
        if(count==2)return List.of(duration/4,3*duration/4);
        return List.of(duration/10,duration/2,9*duration/10);
    }
    static String clipped(String text,int limit){if(text==null)return "";if(text.length()<=limit)return text;int end=limit;if(end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;return text.substring(0,end);}
    static String signature(String... fields){
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");for(String field:fields){byte[] bytes=(field==null?"":field).getBytes(StandardCharsets.UTF_8);digest.update((bytes.length+":").getBytes(StandardCharsets.US_ASCII));digest.update(bytes);}StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(Character.forDigit((value>>>4)&15,16)).append(Character.forDigit(value&15,16));return hex.toString();}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
