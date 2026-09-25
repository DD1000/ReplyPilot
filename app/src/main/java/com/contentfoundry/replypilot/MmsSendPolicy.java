package com.contentfoundry.replypilot;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class MmsSendPolicy {
    static final int MAX_ITEMS=6,MAX_SOURCE=20*1024*1024,MAX_STAGED=24*1024*1024,MAX_CARRIER=10*1024*1024;
    static boolean id(String value){return value!=null&&value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    static int carrierBytes(int value){if(value<16384)throw new IllegalArgumentException("The carrier's MMS size limit is unavailable.");return Math.min(value,MAX_CARRIER);}
    static String caption(String value){if(value==null||value.length()>1600)throw new IllegalArgumentException("Keep the attachment caption to 1,600 characters or fewer.");return value;}
    static boolean terminal(String status){return "sent".equals(status)||"failed".equals(status);}
    static boolean callbackAllowed(String status){return "sending".equals(status)||"unknown".equals(status);}
    /** HTTP success alone is not an accepted MMS: require its SendConf. */
    static String outcome(boolean androidSuccess,String expectedTransaction,String actualTransaction,Integer responseStatus){
        if(expectedTransaction==null||expectedTransaction.isEmpty()||!expectedTransaction.equals(actualTransaction)||responseStatus==null)return "unknown";
        if(responseStatus==128)return androidSuccess?"sent":"unknown";
        // Partial success is deliberately uncertain even for a single recipient.
        if((responseStatus>=129&&responseStatus<=136)||(responseStatus>=192&&responseStatus<=195)||(responseStatus>=224&&responseStatus<=235))return "failed";
        return "unknown";
    }
    static boolean preview(String mime){return "image/jpeg".equals(mime)||"image/png".equals(mime)||"image/gif".equals(mime);}
    static String name(String value,String mime){
        String safe=value==null?"":value.replaceAll("[\\p{Cntrl}/\\\\]"," ").trim();if(safe.length()>100)safe=safe.substring(0,100);
        return safe.isEmpty()?"Attachment"+extension(mime):safe;
    }
    static String extension(String mime){return switch(mime){case "image/jpeg"->".jpg";case "image/png"->".png";case "image/gif"->".gif";case "video/mp4"->".mp4";case "video/3gpp"->".3gp";case "video/webm"->".webm";case "audio/mpeg"->".mp3";case "audio/amr"->".amr";case "audio/ogg"->".ogg";case "audio/wav"->".wav";case "audio/mp4"->".m4a";case "text/x-vcard"->".vcf";default->".bin";};}
    static String mime(byte[] bytes,String declared){
        String type=declared==null?"":declared.toLowerCase(Locale.ROOT).split(";",2)[0].trim();
        if(bytes==null||bytes.length<4)throw new IllegalArgumentException("This attachment is empty or unsupported.");
        if((bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)return "image/jpeg";
        if(bytes.length>=8&&(bytes[0]&255)==137&&ascii(bytes,1,3).equals("PNG")&&bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10)return "image/png";
        if(bytes.length>=6&&(ascii(bytes,0,6).equals("GIF87a")||ascii(bytes,0,6).equals("GIF89a")))return "image/gif";
        if(bytes.length>=12&&ascii(bytes,4,4).equals("ftyp")){
            String brand=ascii(bytes,8,4);if(brand.startsWith("3g"))return "video/3gpp";
            if(type.equals("audio/mp4")||type.equals("audio/x-m4a"))return "audio/mp4";
            if(type.equals("video/mp4")||type.equals("video/quicktime"))return "video/mp4";
        }
        if(bytes.length>=4&&(bytes[0]&255)==0x1a&&(bytes[1]&255)==0x45&&(bytes[2]&255)==0xdf&&(bytes[3]&255)==0xa3&&type.equals("video/webm"))return "video/webm";
        if(bytes.length>=6&&ascii(bytes,0,6).equals("#!AMR\n"))return "audio/amr";
        if(ascii(bytes,0,4).equals("OggS")&&type.startsWith("audio/"))return "audio/ogg";
        if(bytes.length>=12&&ascii(bytes,0,4).equals("RIFF")&&ascii(bytes,8,4).equals("WAVE"))return "audio/wav";
        if(type.equals("audio/mpeg")&&(ascii(bytes,0,3).equals("ID3")||((bytes[0]&255)==255&&(bytes[1]&224)==224)))return "audio/mpeg";
        if(bytes.length<=65536&&(type.equals("text/vcard")||type.equals("text/x-vcard"))){String text=new String(bytes,StandardCharsets.UTF_8).trim();if(text.startsWith("BEGIN:VCARD")&&text.endsWith("END:VCARD")&&!text.contains("\u0000"))return "text/x-vcard";}
        throw new IllegalArgumentException("MMS supports photos, GIFs, small videos or audio, and contact cards. This file type is not supported.");
    }
    private static String ascii(byte[] bytes,int start,int count){return new String(bytes,start,Math.min(count,bytes.length-start),StandardCharsets.US_ASCII);}
}
