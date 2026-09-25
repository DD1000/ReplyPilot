package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class ManualTakeoverPolicyTest {
    @Test public void sameIncomingStaysOwnedEvenAfterOutgoingRows(){assertFalse(ManualTakeoverPolicy.released(50,20,50,20,8,8,true));}
    @Test public void deletingIncomingCannotRevealAnOlderAutomaticTarget(){assertFalse(ManualTakeoverPolicy.released(50,20,49,20,8,8,true));assertFalse(ManualTakeoverPolicy.released(50,20,0,0,8,8,true));}
    @Test public void sameIdMutationOrDownloadedMmsDoesNotRelease(){assertFalse(ManualTakeoverPolicy.released(50,20,50,20,8,9,true));}
    @Test public void newerBoundSmsReleasesOnlyThisWatermark(){assertTrue(ManualTakeoverPolicy.released(50,20,51,20,8,9,true));assertFalse(ManualTakeoverPolicy.released(51,20,51,20,9,9,true));}
    @Test public void receiptBeforeManualTapCannotReleaseWhenItInsertsLater(){assertFalse(ManualTakeoverPolicy.released(50,20,51,20,9,9,true));}
    @Test public void unboundOrFailedSmsReceiptNeverReleases(){assertFalse(ManualTakeoverPolicy.released(50,20,51,20,8,9,false));assertFalse(ManualTakeoverPolicy.released(50,20,51,20,8,-1,false));}
    @Test public void newMmsReleasesEvenWhenSmsBaseHasNotChanged(){assertTrue(ManualTakeoverPolicy.released(50,20,50,21,8,8,true));}
    @Test public void emptyHistoryStillNeedsPositiveNewIncomingEvidence(){assertFalse(ManualTakeoverPolicy.released(0,0,0,0,0,0,true));assertTrue(ManualTakeoverPolicy.released(0,0,1,0,0,1,true));}
    @Test public void reusedLowerProviderIdDoesNotRelease(){assertFalse(ManualTakeoverPolicy.released(50,20,1,1,8,9,true));}
    @Test public void preTapMmsNoticeExtendsWatermarkWhenProviderInsertFinishes(){assertTrue(ManualTakeoverPolicy.bindOlderMms(7,7,20,21));assertTrue(ManualTakeoverPolicy.bindOlderMms(6,7,20,21));assertFalse(ManualTakeoverPolicy.bindOlderMms(8,7,20,21));assertFalse(ManualTakeoverPolicy.bindOlderMms(7,7,20,20));}
    @Test public void unavailableMmsReceiptOrderStaysConservative(){assertTrue(ManualTakeoverPolicy.bindOlderMms(-1,7,20,21));assertTrue(ManualTakeoverPolicy.bindOlderMms(8,Long.MAX_VALUE,20,21));assertFalse(ManualTakeoverPolicy.bindOlderMms(-1,7,20,19));}
    @Test public void lateAiResultCannotSurviveAnyLaterTakeover(){assertTrue(ManualTakeoverPolicy.unchanged(0,0));assertTrue(ManualTakeoverPolicy.unchanged(3,3));assertFalse(ManualTakeoverPolicy.unchanged(2,3));assertFalse(ManualTakeoverPolicy.unchanged(-1,-1));}
    @Test public void requestIdentifiersAreBoundedAndUnambiguous(){assertTrue(ManualTakeoverPolicy.requestId("119850d8-83fd-4dac-a34e-661950be60ef"));for(String bad:new String[]{"", "119850d8", "119850d8-83fd-4dac-a34e-661950be60ef-extra", "119850d8-83fd-4dac-a34e-661950be60eg"})assertFalse(ManualTakeoverPolicy.requestId(bad));assertFalse(ManualTakeoverPolicy.requestId(null));}
    @Test public void reusedRequestCannotChangePersonBodyOrSim(){assertTrue(ManualTakeoverPolicy.sameRequest(1,"+12025550101","Hey",2,1,"+12025550101","Hey",2));assertFalse(ManualTakeoverPolicy.sameRequest(1,"+12025550101","Hey",2,2,"+12025550101","Hey",2));assertFalse(ManualTakeoverPolicy.sameRequest(1,"+12025550101","Hey",2,1,"+12025550102","Hey",2));assertFalse(ManualTakeoverPolicy.sameRequest(1,"+12025550101","Hey",2,1,"+12025550101","New text",2));assertFalse(ManualTakeoverPolicy.sameRequest(1,"+12025550101","Hey",2,1,"+12025550101","Hey",3));}
}
