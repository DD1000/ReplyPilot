package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ApprovedLearningPolicyTest {
    private ApprovedLearningPolicy.Choice choice(long id,long thread,boolean auto,String status,String attention,String reply){return new ApprovedLearningPolicy.Choice(id,thread,true,auto,status,"none",attention,"How was your morning?",reply);}
    @Test public void onlyManualSuccessfulChoicesTeachStyle(){
        var rows=List.of(choice(1,7,false,"sent","","owner's final wording"),choice(2,7,true,"sent","","automatic"),choice(3,7,false,"failed","","failed"),choice(4,7,false,"scheduled","","not sent"),choice(5,7,false,"sent","delay","template"),choice(6,8,false,"sent","","other person"));
        var result=ApprovedLearningPolicy.examples(rows,7,0);assertEquals(1,result.size());assertEquals("owner's final wording",result.get(0).reply());
    }
    @Test public void explicitApprovalIsRequiredEvenIfDeliveryExists(){
        var unapproved=new ApprovedLearningPolicy.Choice(1,7,false,false,"sent","delivered","","hi","no approval");
        assertFalse(ApprovedLearningPolicy.eligible(unapproved,7,0));
        var delivered=new ApprovedLearningPolicy.Choice(2,7,true,false,"unknown","delivered","","hi","confirmed");
        assertTrue(ApprovedLearningPolicy.eligible(delivered,7,0));
    }
    @Test public void forgetWatermarkAlsoExcludesLateOldCallbacks(){
        assertTrue(ApprovedLearningPolicy.examples(List.of(choice(7,7,false,"sent","","old")),7,7).isEmpty());
        assertEquals(1,ApprovedLearningPolicy.examples(List.of(choice(8,7,false,"sent","","new")),7,7).size());
    }
    @Test public void recentDistinctExamplesAreBoundedAndOrdered(){
        List<ApprovedLearningPolicy.Choice> rows=new ArrayList<>();for(int i=1;i<=50;i++)rows.add(choice(i,7,false,"sent","","reply "+i));
        rows.add(choice(51,7,false,"sent","","reply 50"));
        var result=ApprovedLearningPolicy.examples(rows,7,0);assertEquals(12,result.size());assertEquals("reply 50",result.get(0).reply());assertEquals("reply 39",result.get(11).reply());
    }
    @Test public void longExamplesAreBoundedWithoutSplittingEmoji(){
        var row=new ApprovedLearningPolicy.Choice(1,7,true,false,"sent","none","","x".repeat(599)+"😀","y".repeat(359)+"😀");
        var example=ApprovedLearningPolicy.examples(List.of(row),7,0).get(0);
        assertEquals(599,example.incoming().length());assertEquals(359,example.reply().length());
    }
    @Test public void missingContextIsNotInvented(){
        var row=new ApprovedLearningPolicy.Choice(1,7,true,false,"sent","none","","","That's fine");
        assertTrue(ApprovedLearningPolicy.examples(List.of(row),7,0).get(0).incoming().contains("context unavailable"));
    }
    @Test public void deferredLearningRequiresTheExactApprovalTimeIncomingIdentity(){
        var original=new ApprovedLearningPolicy.Anchor(3,90,1234,1,"+12025550100","Original incoming text");
        assertTrue(ApprovedLearningPolicy.sameAnchor(original,new ApprovedLearningPolicy.Anchor(3,90,1234,1,"+12025550100","Original incoming text")));
        assertFalse(ApprovedLearningPolicy.sameAnchor(original,null));
        assertFalse(ApprovedLearningPolicy.sameAnchor(null,original));
        for(var changed:java.util.List.of(
            new ApprovedLearningPolicy.Anchor(4,90,1234,1,"+12025550100","Original incoming text"),
            new ApprovedLearningPolicy.Anchor(3,91,1234,1,"+12025550100","Original incoming text"),
            new ApprovedLearningPolicy.Anchor(3,90,9999,1,"+12025550100","Original incoming text"),
            new ApprovedLearningPolicy.Anchor(3,90,1234,2,"+12025550100","Original incoming text"),
            new ApprovedLearningPolicy.Anchor(3,90,1234,1,"+12025550200","Original incoming text"),
            new ApprovedLearningPolicy.Anchor(3,90,1234,1,"+12025550100","New text reusing the same id")))assertFalse(ApprovedLearningPolicy.sameAnchor(original,changed));
    }
}
