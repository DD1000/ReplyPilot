package com.contentfoundry.replypilot;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** Bounded UTF-8 decoding for a user-selected document; no file is retained. */
final class ChatLogFile {
    static final int MAX_BYTES=262_144;
    static String read(InputStream stream)throws IOException{
        if(stream==null)throw new IllegalArgumentException("This text file could not be opened.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
        while((count=stream.read(buffer))!=-1){
            if(bytes.size()+count>MAX_BYTES)throw new IllegalArgumentException("Choose a text chat log no larger than 256 KB.");
            bytes.write(buffer,0,count);
        }
        String text;
        try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();}
        catch(CharacterCodingException invalid){throw new IllegalArgumentException("Choose a UTF-8 plain-text chat log.");}
        if(text.indexOf('\0')>=0)throw new IllegalArgumentException("Choose a plain-text chat log.");
        return text.startsWith("\uFEFF")?text.substring(1):text;
    }
}
