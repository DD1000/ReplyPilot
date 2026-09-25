package com.contentfoundry.replypilot;

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryArchiveCipherTest {
    private static SecretKey key()throws Exception{KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);return generator.generateKey();}
    @Test public void fullLongUnicodeBodiesRoundTripWithoutTruncation()throws Exception{
        SecretKey key=key();byte[] body=("Fictional text 😀\n".repeat(15000)).getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(body,HistoryArchiveCipher.open(key,"message:sms:7",HistoryArchiveCipher.seal(key,"message:sms:7",body)));
    }
    @Test public void eachRewriteHasAnIndependentNonceAndCiphertext()throws Exception{
        SecretKey key=key();byte[] body="Fictional private message".getBytes(StandardCharsets.UTF_8);
        byte[] first=HistoryArchiveCipher.seal(key,"message:sms:7",body),second=HistoryArchiveCipher.seal(key,"message:sms:7",body);
        assertFalse(Arrays.equals(first,second));assertFalse(new String(first,StandardCharsets.UTF_8).contains("Fictional private message"));
    }
    @Test public void recordsCannotBeMovedBetweenMessagesThreadsOrTransports()throws Exception{
        SecretKey key=key();byte[] sealed=HistoryArchiveCipher.seal(key,"message:sms:7","private".getBytes(StandardCharsets.UTF_8));
        for(String wrong:List.of("message:sms:8","message:mms:7","thread:7"))assertThrows(AEADBadTagException.class,()->HistoryArchiveCipher.open(key,wrong,sealed));
    }
    @Test public void ModifiedCiphertextAndWrongKeysAreRejected()throws Exception{
        SecretKey key=key();byte[] sealed=HistoryArchiveCipher.seal(key,"thread:7","contact".getBytes(StandardCharsets.UTF_8));
        assertThrows(AEADBadTagException.class,()->HistoryArchiveCipher.open(key(),"thread:7",sealed));
        sealed[sealed.length-1]^=1;assertThrows(AEADBadTagException.class,()->HistoryArchiveCipher.open(key,"thread:7",sealed));
    }
    @Test public void CorruptOrOversizedRecordsFailRatherThanTruncate()throws Exception{
        SecretKey key=key();assertThrows(IllegalArgumentException.class,()->HistoryArchiveCipher.open(key,"thread:7",new byte[27]));
        assertThrows(IllegalArgumentException.class,()->HistoryArchiveCipher.seal(key,"thread:7",new byte[HistoryArchivePolicy.RECORD_BYTES+1]));
    }
}
