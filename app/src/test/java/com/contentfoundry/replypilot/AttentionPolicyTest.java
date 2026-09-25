package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class AttentionPolicyTest {
    private static AttentionPolicy.Binding saved(){return new AttentionPolicy.Binding(4,90,3,"config-a",AttentionPolicy.fingerprint(4,90,1234,1,"+15550001111","Dinner tonight?"),7);}
    @Test public void supportsOnlyExplicitAttentionActions(){
        assertTrue(AttentionPolicy.offered("joke","needs_review"));assertTrue(AttentionPolicy.offered("joke","plans_need_input"));assertTrue(AttentionPolicy.offered("delay","plans_need_input"));
        assertFalse(AttentionPolicy.offered("delay","needs_review"));assertFalse(AttentionPolicy.offered("send","plans_need_input"));assertFalse(AttentionPolicy.offered("joke","conversation_complete"));assertFalse(AttentionPolicy.offered(null,null));
    }
    @Test public void acceptsOpaqueTokensWithoutExtraPayload(){
        assertTrue(AttentionPolicy.token("093d8279-1fa3-42e6-9a3f-a503f002ab1c"));
        for(String invalid:new String[]{"","1","../093d8279-1fa3-42e6-9a3f-a503f002ab1c","093d8279-1fa3-42e6-9a3f-a503f002ab1c?body=Hi","093d8279-1fa3-42e6-9a3f-a503f002ab1c\n"})assertFalse(invalid,AttentionPolicy.token(invalid));
        assertFalse(AttentionPolicy.token(null));
    }
    @Test public void sameExactBoundMessageCanBeCheckedAgain(){assertTrue(AttentionPolicy.matches(saved(),saved()));}
    @Test public void contactAndMessageCannotBeSubstituted(){
        var a=saved();assertFalse(AttentionPolicy.matches(a,new AttentionPolicy.Binding(5,a.base(),a.profileRevision(),a.configRevision(),a.fingerprint(),a.burst())));
        assertFalse(AttentionPolicy.matches(a,new AttentionPolicy.Binding(a.thread(),91,a.profileRevision(),a.configRevision(),a.fingerprint(),a.burst())));
    }
    @Test public void settingsAndNewReceiptInvalidatePendingWork(){
        var a=saved();assertFalse(AttentionPolicy.matches(a,new AttentionPolicy.Binding(a.thread(),a.base(),4,a.configRevision(),a.fingerprint(),a.burst())));
        assertFalse(AttentionPolicy.matches(a,new AttentionPolicy.Binding(a.thread(),a.base(),a.profileRevision(),"config-b",a.fingerprint(),a.burst())));
        assertFalse(AttentionPolicy.matches(a,new AttentionPolicy.Binding(a.thread(),a.base(),a.profileRevision(),a.configRevision(),a.fingerprint(),8)));
    }
    @Test public void reusedProviderIdsCannotReauthorizeAnOldAction(){
        var a=saved();var changed=new AttentionPolicy.Binding(4,90,3,"config-a",AttentionPolicy.fingerprint(4,90,5678,1,"+15550001111","Dinner tonight?"),7);
        assertFalse(AttentionPolicy.matches(a,changed));
    }
    @Test public void fingerprintBindsSenderTypeAndText(){
        String a=AttentionPolicy.fingerprint(4,90,1234,1,"+15550001111","Dinner tonight?");
        assertNotEquals(a,AttentionPolicy.fingerprint(4,90,1234,2,"+15550001111","Dinner tonight?"));
        assertNotEquals(a,AttentionPolicy.fingerprint(4,90,1234,1,"+15550002222","Dinner tonight?"));
        assertNotEquals(a,AttentionPolicy.fingerprint(4,90,1234,1,"+15550001111","Dinner tomorrow?"));
        assertNotEquals(AttentionPolicy.fingerprint(4,90,1234,1,"12:3","4"),AttentionPolicy.fingerprint(4,90,1234,1,"12","3:4"));
    }
    @Test public void absentOrFailedBindingsNeverMatch(){
        assertFalse(AttentionPolicy.matches(null,saved()));
        var invalid=new AttentionPolicy.Binding(4,90,3,"config-a","",7);assertFalse(AttentionPolicy.matches(invalid,invalid));
        invalid=new AttentionPolicy.Binding(4,90,3,"config-a","hash",-1);assertFalse(AttentionPolicy.matches(invalid,invalid));
        invalid=new AttentionPolicy.Binding(0,90,3,"config-a","hash",7);assertFalse(AttentionPolicy.matches(invalid,invalid));
    }
    @Test public void claimedCompletedAndLinkedWorkCannotRunTwice(){
        assertTrue(AttentionPolicy.claimable("queued",0));
        for(String state:new String[]{"offered","running","complete","failed","stale","unknown"})assertFalse(state,AttentionPolicy.claimable(state,0));
        assertFalse(AttentionPolicy.claimable("queued",123));assertFalse(AttentionPolicy.claimable("queued",-1));
    }
    @Test public void latestMessageMustRemainTheAuthorizedBase(){
        assertTrue(AttentionPolicy.current(90,90));assertFalse(AttentionPolicy.current(90,91));assertFalse(AttentionPolicy.current(0,0));assertFalse(AttentionPolicy.current(90,0));
    }
    @Test public void explicitNewTokenCanRetryOnlyWorkWithoutASmsJob(){
        assertTrue(AttentionPolicy.reoffer("joke","failed",0,false,false));
        assertTrue(AttentionPolicy.reoffer("delay","failed",0,false,false));
        assertTrue(AttentionPolicy.reoffer("delay","offered",0,true,false));
        assertFalse(AttentionPolicy.reoffer("joke","failed",0,false,true));
        assertFalse(AttentionPolicy.reoffer("joke","running",0,false,false));
        assertFalse(AttentionPolicy.reoffer("joke","complete",0,false,false));
        assertFalse(AttentionPolicy.reoffer("delay","offered",0,false,false));
        for(String state:new String[]{"offered","failed","stale","running","complete"})assertFalse(state,AttentionPolicy.reoffer("delay",state,4,true,false));
    }
    @Test public void delayUsesTheExactDisplayedText(){assertEquals("I'll get back to you on that.",AttentionPolicy.DELAY_TEXT);assertTrue(PlanSafety.commitment(AttentionPolicy.DELAY_TEXT));}
}
