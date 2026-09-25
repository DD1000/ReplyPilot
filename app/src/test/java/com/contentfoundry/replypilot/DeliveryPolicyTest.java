package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class DeliveryPolicyTest {
    @Test public void onlyAnActualGsmReceivedReportConfirmsDelivery(){
        assertEquals("delivered",DeliveryPolicy.report("3gpp",true,0).state());
        assertEquals("unknown",DeliveryPolicy.report("3gpp",false,0).state());
        for(int code:new int[]{1,2,3,31,128,255,-1,2<<16})
            assertEquals("status "+code,"unknown",DeliveryPolicy.report("3gpp",true,code).state());
    }

    @Test public void gsmRetryingAndFinalFailuresRemainDistinct(){
        for(int code=32;code<64;code++)assertEquals("pending",DeliveryPolicy.report("3gpp",true,code).state());
        for(int code=64;code<128;code++)assertEquals("failed",DeliveryPolicy.report("3gpp",true,code).state());
    }

    @Test public void cdmaAcceptedZeroIsNotDelivered(){
        assertEquals("pending",DeliveryPolicy.report("3gpp2",true,0).state());
        assertEquals("delivered",DeliveryPolicy.report("3gpp2",true,2<<16).state());
        assertEquals("unknown",DeliveryPolicy.report("3gpp2",false,2<<16).state());
        assertEquals("unknown",DeliveryPolicy.report("3gpp2",true,1<<16).state());
        assertEquals("failed",DeliveryPolicy.report("3gpp2",true,3<<16).state());
    }

    @Test public void cdmaErrorClassCannotBeMistakenForTheReceivedCode(){
        assertEquals("pending",DeliveryPolicy.report("3gpp2",true,(2<<24)|(4<<16)).state());
        assertEquals("failed",DeliveryPolicy.report("3gpp2",true,(3<<24)|(7<<16)).state());
        assertEquals("failed",DeliveryPolicy.report("3gpp2",true,(3<<24)|(2<<16)).state());
        for(int code:new int[]{(1<<24)|(2<<16),(2<<16)|1,(1<<26)|(2<<16),-1})
            assertEquals("unknown",DeliveryPolicy.report("3gpp2",true,code).state());
    }

    @Test public void unknownFormatsOrMissingReportsNeverConfirmDelivery(){
        for(String format:new String[]{null,"","GSM","3GPP","invalid"})
            assertEquals("unknown",DeliveryPolicy.report(format,true,0).state());
        assertEquals("unknown",DeliveryPolicy.unknown().state());
        assertEquals(-1,DeliveryPolicy.unknown().rawStatus());
    }

    @Test public void oneReceivedPartIsInsufficientForAMultipartMessage(){
        assertEquals("pending",DeliveryPolicy.aggregate(3,Map.of(0,"delivered"),false));
        assertEquals("pending",DeliveryPolicy.aggregate(3,Map.of(0,"delivered",2,"delivered"),false));
        assertEquals("delivered",DeliveryPolicy.aggregate(3,Map.of(2,"delivered",0,"delivered",1,"delivered"),false));
    }

    @Test public void duplicatePartReportsCannotSubstituteForMissingParts(){
        Map<Integer,String> reports=new HashMap<>();
        for(int i=0;i<10;i++)reports.put(0,DeliveryPolicy.merge(reports.get(0),"delivered"));
        assertEquals("pending",DeliveryPolicy.aggregate(2,reports,false));
        reports.put(1,"delivered");assertEquals("delivered",DeliveryPolicy.aggregate(2,reports,false));
    }

    @Test public void outOfOrderTemporaryOrMalformedReportsDoNotUndoReceivedEvidence(){
        for(String older:new String[]{"pending","unknown","failed"}){
            assertEquals("delivered",DeliveryPolicy.merge("delivered",older));
            assertEquals("delivered",DeliveryPolicy.merge(older,"delivered"));
        }
        assertEquals("delivered",DeliveryPolicy.merge("delivered","delivered"));
    }

    @Test public void definitiveFailureSurvivesLessCertainLateReports(){
        assertEquals("failed",DeliveryPolicy.merge("failed","pending"));
        assertEquals("failed",DeliveryPolicy.merge("failed","unknown"));
        assertEquals("failed",DeliveryPolicy.merge("unknown","failed"));
        assertEquals("failed",DeliveryPolicy.aggregate(2,Map.of(0,"delivered",1,"failed"),false));
        assertEquals("delivered",DeliveryPolicy.merge("failed","delivered"));
    }

    @Test public void pendingEvidenceSurvivesAnUnparseableDuplicate(){
        assertEquals("pending",DeliveryPolicy.merge("pending","unknown"));
        assertEquals("pending",DeliveryPolicy.merge("unknown","pending"));
        assertEquals("unknown",DeliveryPolicy.merge(null,"unknown"));
    }

    @Test public void persistedPartStatesRebuildTheSameAggregateAfterRestart(){
        Map<Integer,String> before=new HashMap<>(Map.of(0,"delivered",1,"pending",2,"delivered"));
        Map<Integer,String> restored=new HashMap<>(before);
        assertEquals(DeliveryPolicy.aggregate(3,before,false),DeliveryPolicy.aggregate(3,restored,false));
        restored.put(1,DeliveryPolicy.merge(restored.get(1),"delivered"));
        assertEquals("delivered",DeliveryPolicy.aggregate(3,restored,false));
    }

    @Test public void explicitUnknownAndMissingSubmissionOutcomeAreNotSuccess(){
        assertEquals("pending",DeliveryPolicy.aggregate(2,Map.of(),false));
        assertEquals("unknown",DeliveryPolicy.aggregate(2,Map.of(0,"unknown"),false));
        assertEquals("unknown",DeliveryPolicy.aggregate(2,Map.of(0,"delivered"),true));
        // Complete receipt evidence can arrive even after the send outcome was uncertain.
        assertEquals("delivered",DeliveryPolicy.aggregate(2,Map.of(0,"delivered",1,"delivered"),true));
    }

    @Test public void invalidPartIndicesCannotCountTowardACompleteMessage(){
        assertFalse(DeliveryPolicy.validPart(0,0));
        assertFalse(DeliveryPolicy.validPart(2,-1));
        assertFalse(DeliveryPolicy.validPart(2,2));
        assertTrue(DeliveryPolicy.validPart(2,1));
        assertEquals("pending",DeliveryPolicy.aggregate(2,Map.of(-1,"delivered",0,"delivered",2,"delivered"),false));
        assertEquals("none",DeliveryPolicy.aggregate(0,Map.of(),false));
    }

    @Test public void providerStatusesDoNotReuseSmsTypeAsDeliveryState(){
        assertEquals(-1,DeliveryPolicy.providerStatus("none"));
        assertEquals(-1,DeliveryPolicy.providerStatus("unknown"));
        assertEquals(32,DeliveryPolicy.providerStatus("pending"));
        assertEquals(0,DeliveryPolicy.providerStatus("delivered"));
        assertEquals(64,DeliveryPolicy.providerStatus("failed"));
        assertEquals("none",DeliveryPolicy.fromProvider(1,0));
        assertEquals("none",DeliveryPolicy.fromProvider(3,0));
        assertEquals("none",DeliveryPolicy.fromProvider(2,-1));
        assertEquals("unknown",DeliveryPolicy.fromProvider(5,0));
        assertEquals("delivered",DeliveryPolicy.fromProvider(2,0));
        assertEquals("pending",DeliveryPolicy.fromProvider(2,32));
        assertEquals("failed",DeliveryPolicy.fromProvider(2,64));
        assertEquals("unknown",DeliveryPolicy.fromProvider(2,2<<16));
    }

    @Test public void aRecycledProviderIdWithIdenticalTextButDifferentDateIsNotOurMessage(){
        assertTrue(DeliveryPolicy.sameMessage(10,1000,"+14155550123","ok",10,1000,"+14155550123","ok"));
        assertFalse(DeliveryPolicy.sameMessage(10,1000,"+14155550123","ok",10,2000,"+14155550123","ok"));
        assertFalse(DeliveryPolicy.sameMessage(10,1000,"+14155550123","ok",20,1000,"+14155550123","ok"));
        assertFalse(DeliveryPolicy.sameMessage(10,1000,"+14155550123","ok",10,1000,"+14155554567","ok"));
        assertFalse(DeliveryPolicy.sameMessage(10,1000,"+14155550123","ok",10,1000,"+14155550123","different"));
        assertFalse(DeliveryPolicy.sameMessage(10,0,"+14155550123","ok",10,0,"+14155550123","ok"));
    }

    @Test public void aLaterSentCallbackCannotDescribeAnAlreadyConfirmedDeliveryAsUnconfirmed(){
        assertEquals("Delivery confirmed by your carrier.",DeliveryPolicy.receiptNote(false,"delivered"));
        assertEquals("Delivery confirmed by your carrier.",DeliveryPolicy.receiptNote(true,"delivered"));
        assertTrue(DeliveryPolicy.receiptNote(false,"pending").contains("not confirmed"));
        assertTrue(DeliveryPolicy.receiptNote(true,"unknown").contains("reported a failure"));
    }
    @Test public void completeDeliverySettlesMissingOrFailedSentCallbacks(){
        for(String state:new String[]{"sending","unknown","failed","sent"})assertEquals("sent",DeliveryPolicy.settledSendStatus(state,"delivered"));
        for(String delivery:new String[]{"none","pending","unknown","failed"})for(String state:new String[]{"sending","unknown","failed","sent"})assertEquals(state,DeliveryPolicy.settledSendStatus(state,delivery));
        for(String state:new String[]{"scheduled","paused","cancelled","awaiting_alert"})assertEquals(state,DeliveryPolicy.settledSendStatus(state,"delivered"));
    }
    @Test public void partialOrDuplicateMultipartProofDoesNotReleaseSending(){
        Map<Integer,String> reports=new HashMap<>();reports.put(0,"delivered");
        for(int i=0;i<5;i++)reports.put(0,DeliveryPolicy.merge(reports.get(0),"delivered"));
        assertEquals("sending",DeliveryPolicy.settledSendStatus("sending",DeliveryPolicy.aggregate(2,reports,false)));
        reports.put(1,"delivered");assertEquals("sent",DeliveryPolicy.settledSendStatus("sending",DeliveryPolicy.aggregate(2,reports,false)));
    }
    @Test public void lateFailureCannotUndoProvenDeliveryOrAuthorizeAnotherSend(){
        assertEquals("sent",DeliveryPolicy.receiptStatus(true,"delivered"));
        assertEquals("sent",DeliveryPolicy.receiptStatus(false,"delivered"));
        assertEquals("failed",DeliveryPolicy.receiptStatus(true,"pending"));
        assertEquals("sent",DeliveryPolicy.receiptStatus(false,"pending"));
        assertNotNull(SendPolicy.repeatedImmediateBlock(DeliveryPolicy.receiptStatus(true,"delivered"),true));
    }
}
