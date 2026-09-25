package com.contentfoundry.replypilot;

import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Authentication binds each encrypted display record to its exact archive row. */
final class HistoryArchiveCipher {
    private static byte[] aad(String identity){return ("reply-pilot:full-history:v1:"+identity).getBytes(StandardCharsets.UTF_8);}
    static byte[] seal(SecretKey key,String identity,byte[] plain)throws Exception{
        if(plain==null||plain.length>HistoryArchivePolicy.RECORD_BYTES)throw new IllegalArgumentException("Archive record too large");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(aad(identity));
        byte[] iv=cipher.getIV(),sealed=cipher.doFinal(plain);if(iv.length!=12)throw new IllegalStateException("Invalid encryption nonce");
        byte[] result=new byte[12+sealed.length];System.arraycopy(iv,0,result,0,12);System.arraycopy(sealed,0,result,12,sealed.length);return result;
    }
    static byte[] open(SecretKey key,String identity,byte[] sealed)throws Exception{
        if(sealed==null||sealed.length<28||sealed.length>HistoryArchivePolicy.RECORD_BYTES+28)throw new IllegalArgumentException("Invalid archive record");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,sealed,0,12));cipher.updateAAD(aad(identity));return cipher.doFinal(sealed,12,sealed.length-12);
    }
}
