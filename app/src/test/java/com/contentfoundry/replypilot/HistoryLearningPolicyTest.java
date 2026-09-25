package com.contentfoundry.replypilot;

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryLearningPolicyTest {
    @Test public void onlyActuallySentAndReceivedTextBelongsToHistory(){
        assertTrue(HistoryLearningPolicy.eligible("sms",1,0,0));assertTrue(HistoryLearningPolicy.eligible("sms",2,0,0));
        for(int type:new int[]{0,3,4,5,6})assertFalse(HistoryLearningPolicy.eligible("sms",type,0,0));
        assertTrue(HistoryLearningPolicy.eligible("mms",1,1,132));assertTrue(HistoryLearningPolicy.eligible("mms",2,2,128));
        for(int box:new int[]{0,3,4,5})assertFalse(HistoryLearningPolicy.eligible("mms",2,box,128));
        assertFalse(HistoryLearningPolicy.eligible("mms",1,1,130));assertFalse(HistoryLearningPolicy.eligible("rcs",1,1,132));
    }
    @Test public void unreadableOrTruncatedMmsCannotBecomeACompleteHistory(){
        HistoryLearningPolicy.requireCompleteText("mms",false,false);
        HistoryLearningPolicy.requireCompleteText("sms",false,false);
        for(boolean[] flags:new boolean[][]{{true,false},{false,true},{true,true}}){
            IllegalStateException error=assertThrows(IllegalStateException.class,()->HistoryLearningPolicy.requireCompleteText("mms",flags[0],flags[1]));
            assertTrue(error.getMessage().contains("Retry"));assertTrue(error.getMessage().contains("remain paused"));
        }
        // A text-free image is legitimately absent from text analysis; an
        // unreadable text part must fail before the same empty-body skip.
        assertTrue(HistoryLearningPolicy.fragments("").isEmpty());
    }
    @Test public void longUnicodeAndWhitespaceSurviveAllFragmentsExactly(){
        String body="a".repeat(3999)+"😀"+" ".repeat(9000)+"Fictional final message\n"+"文".repeat(9000);
        List<String> pieces=HistoryLearningPolicy.fragments(body);assertTrue(pieces.size()>5);assertEquals(body,String.join("",pieces));
        assertTrue(pieces.stream().anyMatch(String::isBlank));for(String piece:pieces){assertTrue(piece.length()<=4000);assertFalse(Character.isHighSurrogate(piece.charAt(piece.length()-1)));assertFalse(Character.isLowSurrogate(piece.charAt(0)));}
        assertTrue(HistoryLearningPolicy.fragments(" \n").isEmpty());assertTrue(HistoryLearningPolicy.fragments(null).isEmpty());
    }
    @Test public void batchBoundsDoNotCapTotalHistory(){
        int batches=1,count=0,chars=0,total=0;for(int i=0;i<2000;i++)for(String part:HistoryLearningPolicy.fragments("x".repeat(13000))){if(!HistoryLearningPolicy.accepts(count,chars,part.length())){batches++;count=0;chars=0;}assertTrue(HistoryLearningPolicy.accepts(count,chars,part.length()));count++;chars+=part.length();total+=part.length();}
        assertTrue(batches>500);assertEquals(26000000,total);assertFalse(HistoryLearningPolicy.accepts(40,0,1));assertFalse(HistoryLearningPolicy.accepts(0,48000,1));assertFalse(HistoryLearningPolicy.accepts(0,0,4001));assertFalse(HistoryLearningPolicy.accepts(0,0,0));assertFalse(HistoryLearningPolicy.accepts(-1,0,1));
    }
    @Test public void partialCheckpointsNeverCountAsReady(){
        assertFalse(HistoryLearningPolicy.complete(1,10,10));assertFalse(HistoryLearningPolicy.complete(0,9,10));assertFalse(HistoryLearningPolicy.complete(0,11,10));assertFalse(HistoryLearningPolicy.complete(0,-1,-1));assertTrue(HistoryLearningPolicy.complete(0,10,10));assertTrue(HistoryLearningPolicy.complete(0,0,0));
    }
    @Test public void memoryIsBoundedAndValidatedInsteadOfTruncated(){
        assertEquals("x".repeat(1800),HistoryLearningPolicy.memory("x".repeat(1800)));assertThrows(IllegalArgumentException.class,()->HistoryLearningPolicy.memory("x".repeat(1801)));assertThrows(IllegalArgumentException.class,()->HistoryLearningPolicy.memory(null));assertThrows(IllegalArgumentException.class,()->HistoryLearningPolicy.memory("bad\0memory"));
    }
    @Test public void encryptedCheckpointCannotMoveBetweenContactsRunsOrRows()throws Exception{
        KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);SecretKey key=generator.generateKey();byte[] plain="Fictional historical owner style".getBytes(StandardCharsets.UTF_8);
        String identity="learning:run-a:memory:7";byte[] encrypted=HistoryArchiveCipher.seal(key,identity,plain);assertArrayEquals(plain,HistoryArchiveCipher.open(key,identity,encrypted));assertFalse(new String(encrypted,StandardCharsets.UTF_8).contains("Fictional"));
        for(String wrong:List.of("learning:run-a:memory:8","learning:run-b:memory:7","learning:run-a:unit:7","message:sms:7"))assertThrows(AEADBadTagException.class,()->HistoryArchiveCipher.open(key,wrong,encrypted));
    }
}
