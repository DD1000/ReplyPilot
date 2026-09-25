package com.contentfoundry.replypilot;

import java.io.*;
import java.nio.charset.Charset;

/** MMS text can live in either a provider column or a separate text part file. */
final class MmsTextPolicy {
    record Read(String text,boolean truncated,boolean unavailable){}
    static String mime(String supplied){return MediaGalleryPolicy.type(supplied);}
    static Read inline(String supplied,int maximumBytes){
        if(maximumBytes<1)throw new IllegalArgumentException("Invalid text limit");
        String text=supplied==null?"":supplied;
        // A character bound also bounds pathological inline values before encoding.
        if(text.length()<=maximumBytes)return new Read(text,false,false);
        int end=maximumBytes;if(end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;
        return new Read(text.substring(0,end),true,false);
    }
    static Read file(InputStream input,Charset charset,int maximumBytes)throws IOException{
        if(maximumBytes<1)throw new IllegalArgumentException("Invalid text limit");
        if(input==null)return new Read("",false,true);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[Math.min(8192,maximumBytes)];int count;
        while(bytes.size()<maximumBytes&&(count=input.read(buffer,0,Math.min(buffer.length,maximumBytes-bytes.size())))!=-1){
            if(count==0){int single=input.read();if(single<0)break;bytes.write(single);}else bytes.write(buffer,0,count);
        }
        boolean truncated=bytes.size()==maximumBytes&&input.read()!=-1;
        return new Read(new String(bytes.toByteArray(),charset),truncated,false);
    }
}
