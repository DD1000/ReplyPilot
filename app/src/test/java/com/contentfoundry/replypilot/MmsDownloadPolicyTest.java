package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MmsDownloadPolicyTest {
    private static final String TOKEN="12345678-1234-4321-abcd-123456789abc";
    private static MmsDownloadPolicy.Notice notice(long id,long thread,long date,int sub,int box,int type,String url,String tx){return new MmsDownloadPolicy.Notice(id,thread,date,sub,box,type,url,tx);}
    private static final MmsDownloadPolicy.Notice ORIGINAL=notice(17,9,1_234,2,1,130,"http://mmsc.example/item","txn");
    @Test public void carrierHttpAndHttpsLocationsAreRetainedExactly(){
        for(String value:List.of("http://10.1.2.3:8080/mms?id=abc","https://mmsc.example/item%20name?t=1","http://[2001:db8::1]/mms"))assertEquals(value,MmsDownloadPolicy.location(value));
    }
    @Test public void configuredCarrierTransactionSuffixAppliesOnceOnly(){
        assertEquals("http://mmsc.example/?id=txn",MmsDownloadPolicy.contentLocation("http://mmsc.example/?id=","txn",true));
        assertEquals("http://mmsc.example/?id=",MmsDownloadPolicy.contentLocation("http://mmsc.example/?id=","txn",false));
        assertEquals("http://mmsc.example/?id=ready",MmsDownloadPolicy.contentLocation("http://mmsc.example/?id=ready","txn",true));
        assertThrows(IllegalArgumentException.class,()->MmsDownloadPolicy.contentLocation("http://mmsc.example/?id=","bad\r\ntxn",true));
    }
    @Test public void acknowledgmentsUseCarrierMmscUnlessContentLocationIsExplicitlyConfigured(){
        String url="http://mmsc.example/download?message=abc";
        assertNull(MmsDownloadPolicy.acknowledgmentLocation(url,false));
        assertEquals(url,MmsDownloadPolicy.acknowledgmentLocation(url,true));
        assertThrows(IllegalArgumentException.class,()->MmsDownloadPolicy.acknowledgmentLocation("file:///private/data",true));
    }
    @Test public void javascriptFilesCredentialsFragmentsAndControlLocationsReject(){
        for(String value:List.of("javascript:alert(1)","file:///tmp/mms","content://mms/1","https://user:password@mmsc.example/x","https://mmsc.example/x#fragment","https://mmsc.example/\r\nx","https://mmsc.example/a b","https:///missing-host","http://host:99999/x","https://mmsc.example/"+"a".repeat(4096)))assertThrows(value,IllegalArgumentException.class,()->MmsDownloadPolicy.location(value));
        assertThrows(IllegalArgumentException.class,()->MmsDownloadPolicy.location(null));
    }
    @Test public void transactionIsBoundedAndCannotCarryHeaderControls(){
        assertEquals("txn",MmsDownloadPolicy.transaction("txn".getBytes(StandardCharsets.ISO_8859_1)));
        for(byte[] bytes:new byte[][]{new byte[0],new byte[257],new byte[]{'a',10,'b'},new byte[]{127}})assertThrows(IllegalArgumentException.class,()->MmsDownloadPolicy.transaction(bytes));
        assertThrows(IllegalArgumentException.class,()->MmsDownloadPolicy.transaction(null));
    }
    @Test public void fingerprintSeparatesSimSenderUrlAndTransaction(){
        String first=MmsDownloadPolicy.fingerprint("http://mmsc/x","t","+15550001",2);
        assertEquals(first,MmsDownloadPolicy.fingerprint("http://mmsc/x","t","+15550001",2));
        assertNotEquals(first,MmsDownloadPolicy.fingerprint("http://mmsc/x","t","+15550001",3));
        assertNotEquals(first,MmsDownloadPolicy.fingerprint("http://mmsc/x","t","+15550002",2));
        assertNotEquals(first,MmsDownloadPolicy.fingerprint("http://mmsc/y","t","+15550001",2));
        assertNotEquals(first,MmsDownloadPolicy.fingerprint("http://mmsc/x","u","+15550001",2));
        assertEquals(64,first.length());
    }
    @Test public void ambiguousIncomingSimNeverUsesAnOutgoingDefault(){
        assertEquals(-1,MmsDownloadPolicy.subscription(-1,List.of(2,3)));
        assertEquals(2,MmsDownloadPolicy.subscription(-1,List.of(2)));
        assertEquals(-1,MmsDownloadPolicy.subscription(-1,List.of()));
        assertEquals(7,MmsDownloadPolicy.subscription(7,List.of(2,3)));
    }
    @Test public void callbackIsExactOneAttemptAndRejectsReplaysAfterClaim(){
        assertTrue(MmsDownloadPolicy.callback(TOKEN,TOKEN,"downloading"));
        assertTrue(MmsDownloadPolicy.callback(TOKEN,TOKEN,"interrupted"));
        for(String state:List.of("pending","processing","downloaded","failed","unavailable"))assertFalse(MmsDownloadPolicy.callback(TOKEN,TOKEN,state));
        assertFalse(MmsDownloadPolicy.callback(TOKEN,"00000000-0000-0000-0000-000000000000","downloading"));
        assertFalse(MmsDownloadPolicy.callback(TOKEN,null,"downloading"));
    }
    @Test public void tokensCannotSelectAFileOrContainExtraPath(){
        assertTrue(MmsDownloadPolicy.token(TOKEN));
        for(String value:List.of("../"+TOKEN,TOKEN+".pdu",TOKEN+"/x","",TOKEN.toUpperCase()))assertFalse(MmsDownloadPolicy.token(value));
    }
    @Test public void retryRequiresNoticeAccessAndItsOriginalActiveSim(){
        for(String state:List.of("pending","failed","interrupted"))assertTrue(MmsDownloadPolicy.retry(state,true,true,true));
        for(String state:List.of("downloading","processing","downloaded","unavailable"))assertFalse(MmsDownloadPolicy.retry(state,true,true,true));
        assertFalse(MmsDownloadPolicy.retry("failed",false,true,true));assertFalse(MmsDownloadPolicy.retry("failed",true,false,true));assertFalse(MmsDownloadPolicy.retry("failed",true,true,false));
    }
    @Test public void freshRequestsWaitForCallbackRatherThanRestartingOnOpen(){
        assertFalse(MmsDownloadPolicy.timedOut(1000,1001));
        assertFalse(MmsDownloadPolicy.timedOut(1000,1000+MmsDownloadPolicy.TIMEOUT-1));
        assertTrue(MmsDownloadPolicy.timedOut(1000,1000+MmsDownloadPolicy.TIMEOUT));
        assertTrue(MmsDownloadPolicy.timedOut(1000,999));assertTrue(MmsDownloadPolicy.timedOut(0,1000));
    }
    @Test public void downloadedAndAnnouncedPdusStayWithinTheBound(){
        assertTrue(MmsDownloadPolicy.pduSize(MmsDownloadPolicy.MAX_PDU));assertTrue(MmsDownloadPolicy.pduSize(1));
        assertFalse(MmsDownloadPolicy.pduSize(0));assertFalse(MmsDownloadPolicy.pduSize(-1));assertFalse(MmsDownloadPolicy.pduSize(MmsDownloadPolicy.MAX_PDU+1L));
        assertTrue(MmsDownloadPolicy.announcedSize(0));assertFalse(MmsDownloadPolicy.announcedSize(MmsDownloadPolicy.MAX_PDU+1L));
    }
    @Test public void retrieveFailureNeverBecomesDownloaded(){
        assertTrue(MmsDownloadPolicy.complete(128,1));assertTrue(MmsDownloadPolicy.complete(0,100));
        assertFalse(MmsDownloadPolicy.complete(192,1));assertFalse(MmsDownloadPolicy.complete(224,1));assertFalse(MmsDownloadPolicy.complete(128,0));assertFalse(MmsDownloadPolicy.complete(128,101));
    }
    @Test public void noticeBindingRejectsRecycledIdDifferentThreadDateSimOrUrl(){
        assertTrue(MmsDownloadPolicy.bound(ORIGINAL,ORIGINAL));assertFalse(MmsDownloadPolicy.bound(ORIGINAL,null));
        for(var row:List.of(notice(18,9,1234,2,1,130,ORIGINAL.location(),"txn"),notice(17,10,1234,2,1,130,ORIGINAL.location(),"txn"),notice(17,9,1235,2,1,130,ORIGINAL.location(),"txn"),notice(17,9,1234,3,1,130,ORIGINAL.location(),"txn"),notice(17,9,1234,2,2,130,ORIGINAL.location(),"txn"),notice(17,9,1234,2,1,132,ORIGINAL.location(),"txn"),notice(17,9,1234,2,1,130,"http://mmsc/other","txn"),notice(17,9,1234,2,1,130,ORIGINAL.location(),"other")))assertFalse(MmsDownloadPolicy.bound(ORIGINAL,row));
    }
    @Test public void partialRepairAllowsOnlyBoundRetrievedMessageEvenIfGroupThreadChanged(){
        var partial=notice(17,88,1234,2,1,132,ORIGINAL.location(),"retrieve-txn");
        assertTrue(MmsDownloadPolicy.partial(ORIGINAL,partial,"message-a","message-a"));
        assertFalse(MmsDownloadPolicy.partial(ORIGINAL,partial,"message-a","message-b"));assertFalse(MmsDownloadPolicy.partial(ORIGINAL,partial,"",""));
        assertFalse(MmsDownloadPolicy.partial(ORIGINAL,notice(17,88,1235,2,1,132,ORIGINAL.location(),"retrieve-txn"),"message-a","message-a"));
        assertFalse(MmsDownloadPolicy.partial(ORIGINAL,notice(17,88,1234,2,1,132,"http://mmsc/other","retrieve-txn"),"message-a","message-a"));
        assertFalse(MmsDownloadPolicy.partial(ORIGINAL,notice(18,88,1234,2,1,132,ORIGINAL.location(),"retrieve-txn"),"message-a","message-a"));
    }
    @Test public void localRepairDigestDetectsAnyChangedPdu(){
        byte[] first={1,2,3},changed={1,2,4};assertEquals(MmsDownloadPolicy.digest(first),MmsDownloadPolicy.digest(first.clone()));assertNotEquals(MmsDownloadPolicy.digest(first),MmsDownloadPolicy.digest(changed));
    }
}
