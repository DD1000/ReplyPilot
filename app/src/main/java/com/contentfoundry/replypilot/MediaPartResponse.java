package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.*;
import java.util.*;

/** Streams one provider attachment into the packaged WebView, including seek requests. */
final class MediaPartResponse {
    static WebResourceResponse open(Context c,Uri part,WebResourceRequest request)throws IOException{
        if(!Messages.allowed(c,Manifest.permission.READ_SMS))return empty(403,"Forbidden",Map.of());
        String mime=c.getContentResolver().getType(part);
        if(!MediaRangePolicy.supported(mime))return empty(415,"Unsupported Media Type",Map.of());
        AssetFileDescriptor descriptor=c.getContentResolver().openAssetFileDescriptor(part,"r");
        if(descriptor==null)return empty(404,"Not Found",Map.of());
        long length=descriptor.getLength();String header=null;
        for(var entry:request.getRequestHeaders().entrySet())if("range".equalsIgnoreCase(entry.getKey()))header=entry.getValue();
        MediaRangePolicy.Range range;
        try{range=MediaRangePolicy.range(header,length);}catch(IllegalArgumentException invalid){descriptor.close();return empty(416,"Range Not Satisfiable",length>=0?Map.of("Content-Range","bytes */"+length):Map.of());}
        InputStream stream;
        try{stream=descriptor.createInputStream();}catch(IOException failed){descriptor.close();throw failed;}
        try{
            long remaining=range.start();while(remaining>0){long skipped=stream.skip(remaining);if(skipped<=0){if(stream.read()<0)throw new EOFException();skipped=1;}remaining-=skipped;}
            if(!Messages.allowed(c,Manifest.permission.READ_SMS)){stream.close();return empty(403,"Forbidden",Map.of());}
            Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");
            if(length>=0){headers.put("Accept-Ranges","bytes");headers.put("Content-Length",Long.toString(range.count()));}
            if(range.partial())headers.put("Content-Range","bytes "+range.start()+"-"+(range.start()+range.count()-1)+"/"+length);
            return new WebResourceResponse(mime,null,range.partial()?206:200,range.partial()?"Partial Content":"OK",headers,range.count()<0?stream:new Limited(stream,range.count()));
        }catch(Exception failed){stream.close();if(failed instanceof IOException io)throw io;throw new IOException("Attachment unavailable",failed);}
    }
    private static WebResourceResponse empty(int status,String reason,Map<String,String> headers){return new WebResourceResponse("text/plain","UTF-8",status,reason,headers,new ByteArrayInputStream(new byte[0]));}
    private static final class Limited extends FilterInputStream {
        private long remaining;Limited(InputStream source,long count){super(source);remaining=count;}
        @Override public int read()throws IOException{if(remaining<=0)return -1;int result=super.read();if(result>=0)remaining--;return result;}
        @Override public int read(byte[] bytes,int offset,int count)throws IOException{if(count==0)return 0;if(remaining<=0)return -1;int result=in.read(bytes,offset,(int)Math.min(count,remaining));if(result>0)remaining-=result;return result;}
        @Override public long skip(long n)throws IOException{long skipped=in.skip(Math.min(Math.max(0,n),remaining));remaining-=skipped;return skipped;}
        @Override public int available()throws IOException{return (int)Math.min(in.available(),remaining);}
    }
}
