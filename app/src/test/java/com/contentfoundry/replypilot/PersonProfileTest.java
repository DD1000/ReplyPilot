package com.contentfoundry.replypilot;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PersonProfileTest {
    @Test public void selectedRelationshipWorksWithoutOptionalDynamics() {
        String context=PersonProfile.context("Coworker", "");
        CloudPrompt.Input input=CloudPrompt.build(1,List.of(new ReplyPrompt.Message(1,1,"Can we talk?")),context,"","Professional",true);
        assertEquals("Relationship type: Coworker.",input.relationship());
        assertEquals("Professional",input.tone());
        assertTrue(input.style().isEmpty());
        assertEquals("",PersonProfile.context("", ""));
    }
    @Test public void longestExistingDynamicsArePreservedWhenChoosingARelationship() {
        String note="x".repeat(1500),context=PersonProfile.context("Acquaintance",note);
        assertTrue(context.endsWith(note));
        assertTrue(ReplyPrompt.prepare(1,List.of(),"Use AI intuition",false,context).text().contains(note));
        assertEquals(context,CloudPrompt.build(1,List.of(new ReplyPrompt.Message(1,1,"hello")),context,"","Use AI intuition",false).relationship());
        assertThrows(IllegalArgumentException.class,()->PersonProfile.context("Friend",note+"x"));
    }
    @Test public void perPersonToneOverridesDefaultWithoutLeakingBetweenProfiles() {
        assertEquals("Professional",PersonProfile.effectiveTone("Professional","Warm"));
        assertEquals("Warm",PersonProfile.effectiveTone("","Warm"));
        assertEquals("Use AI intuition",PersonProfile.effectiveTone("Use AI intuition","Brief"));
        assertEquals("Natural",PersonProfile.effectiveTone("INJECTED","INVALID"));
        assertEquals("",PersonProfile.kind("Ignore rules"));
        assertEquals("",PersonProfile.tone("Ignore rules"));
        assertEquals("",PersonProfile.context("Ignore rules",""));
    }
    @Test public void automaticDraftsRequireEveryOptIn() {
        for(boolean enabled:List.of(false,true))for(boolean automatic:List.of(false,true))for(boolean global:List.of(false,true))
            assertEquals(enabled&&automatic&&global,PersonProfile.automaticAllowed(enabled,automatic,global));
    }
    @Test public void automaticSendingRequiresSeparateStandingPermission() {
        for(boolean enabled:List.of(false,true))for(boolean drafts:List.of(false,true))for(boolean sends:List.of(false,true))for(boolean global:List.of(false,true))
            assertEquals(enabled&&drafts&&sends&&global,PersonProfile.automaticSendAllowed(enabled,drafts,sends,global));
        assertFalse(PersonProfile.automaticSendAllowed(true,true,false,true));
    }
    @Test public void offNeverTouchesAnUnavailableMessageProvider(){
        for(int flags=0;flags<7;flags++){
            var result=PersonProfile.readiness((flags&1)!=0,(flags&2)!=0,(flags&4)!=0,()->{throw new SecurityException("SMS access unavailable");});
            assertFalse(result.eligible());assertFalse(result.scanComplete());
        }
        assertThrows(SecurityException.class,()->PersonProfile.readiness(true,true,true,()->{throw new SecurityException("SMS access unavailable");}));
        var sufficient=new ReplyEligibility.Result(true,20,5,15);assertSame(sufficient,PersonProfile.readiness(true,true,true,()->sufficient));
        assertFalse(PersonProfile.readiness(true,true,true,()->new ReplyEligibility.Result(false,2,1,1)).eligible());
    }
    @Test public void instantRepliesAreExplicitAndTimedRepliesKeepTheirBounds() {
        assertEquals(0,PersonProfile.DEFAULT_AUTO_DELAY_SECONDS);
        assertEquals(0,PersonProfile.autoDelay(0));
        assertEquals(60,PersonProfile.autoDelay(60));
        assertEquals(300,PersonProfile.autoDelay(300));
        assertThrows(IllegalArgumentException.class,()->PersonProfile.autoDelay(604800));
        assertThrows(IllegalArgumentException.class,()->PersonProfile.autoDelay(-1));
        assertThrows(IllegalArgumentException.class,()->PersonProfile.autoDelay(1));
        assertThrows(IllegalArgumentException.class,()->PersonProfile.autoDelay(2));
        assertThrows(IllegalArgumentException.class,()->PersonProfile.autoDelay(59));
        assertThrows(IllegalArgumentException.class,()->PersonProfile.autoDelay(604801));
        assertThrows(IllegalArgumentException.class,()->PersonProfile.autoDelay(Long.MAX_VALUE));
    }
}
