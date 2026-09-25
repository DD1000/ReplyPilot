package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChannelMigrationPolicyTest {
    private static final String OLD="replies",NEW="messages_tinyblast_v1";
    @Test public void firstInstallUsesBundledSound(){assertEquals(NEW,ChannelMigrationPolicy.select(null,OLD,NEW,false,false,false));}
    @Test public void untouchedOldChannelGetsNewDefault(){assertEquals(NEW,ChannelMigrationPolicy.select(null,OLD,NEW,false,true,true));}
    @Test public void customizedOrMutedOldChannelIsRetained(){assertEquals(OLD,ChannelMigrationPolicy.select(null,OLD,NEW,false,true,false));}
    @Test public void preservedUserSettingsAreNotReconsideredLater(){assertEquals(OLD,ChannelMigrationPolicy.select(OLD,OLD,NEW,false,true,true));}
    @Test public void mutingNewChannelNeverFallsBackToAudibleOldChannel(){assertEquals(NEW,ChannelMigrationPolicy.select(NEW,OLD,NEW,true,true,false));}
    @Test public void missingSelectedChannelKeepsItsIdForSystemRestore(){assertEquals(OLD,ChannelMigrationPolicy.select(OLD,OLD,NEW,false,false,false));assertEquals(NEW,ChannelMigrationPolicy.select(NEW,OLD,NEW,false,true,false));}
    @Test public void interruptedPreferenceWriteKeepsAlreadyCreatedChannel(){assertEquals(NEW,ChannelMigrationPolicy.select(null,OLD,NEW,true,true,false));}
    @Test public void unknownSavedIdCannotRouteNotificationsElsewhere(){assertEquals(OLD,ChannelMigrationPolicy.select("unexpected",OLD,NEW,false,true,false));}
}
