package com.contentfoundry.replypilot;
import java.io.*;
import java.util.Locale;

/** Bounded, passive attachment copies for an explicit Android viewer action. */
final class AttachmentFilePolicy {
    static final long MAX_BYTES=20L*1024*1024;
    static String mime(String value){
        if(value==null)return null;
        String type=value.toLowerCase(Locale.ROOT);
        if(type.equals("image/svg+xml"))return null;
        return type.matches("(?:image|video|audio)/[a-z0-9.+-]{1,64}")||type.equals("application/pdf")||type.equals("text/plain")||type.equals("text/vcard")||type.equals("text/x-vcard")?type:null;
    }
    static long copy(InputStream input,OutputStream output,long limit)throws IOException{
        if(input==null||output==null||limit<=0||limit>MAX_BYTES)throw new IllegalArgumentException("Attachment unavailable.");
        byte[] buffer=new byte[8192];long size=0;int count;
        while((count=input.read(buffer))!=-1){if(count==0){int next=input.read();if(next<0)break;if(++size>limit)throw new IOException("This attachment is too large to open here.");output.write(next);continue;}size+=count;if(size>limit)throw new IOException("This attachment is too large to open here.");output.write(buffer,0,count);}
        if(size==0)throw new IOException("This attachment is empty or no longer available.");
        return size;
    }
}
