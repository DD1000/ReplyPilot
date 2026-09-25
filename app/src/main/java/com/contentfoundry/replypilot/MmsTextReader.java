package com.contentfoundry.replypilot;

import android.content.*;
import android.provider.Telephony;
import java.io.*;
import java.nio.charset.*;

/** Never opens attachment bytes; callers must select a normalized text/plain part. */
final class MmsTextReader {
    record Read(String text,boolean truncated,boolean unavailable){}
    static String mime(String supplied){return MmsTextPolicy.mime(supplied);}
    static Read read(Context context,long partId,String inline,boolean fileBacked,int charset,int maximumBytes){
        MmsTextPolicy.Read value;
        if((inline!=null&&!inline.isBlank())||!fileBacked)value=MmsTextPolicy.inline(inline,maximumBytes);
        else{
            Charset encoding=StandardCharsets.UTF_8;
            try{encoding=Charset.forName(com.google.android.mms.pdu_alt.CharacterSets.getMimeName(charset));}catch(Exception unsupported){}
            try(InputStream input=context.getContentResolver().openInputStream(ContentUris.withAppendedId(Telephony.Mms.Part.CONTENT_URI,partId))){value=MmsTextPolicy.file(input,encoding,maximumBytes);}
            catch(IOException missing){value=new MmsTextPolicy.Read("",false,true);}
        }
        return new Read(value.text(),value.truncated(),value.unavailable());
    }
}
