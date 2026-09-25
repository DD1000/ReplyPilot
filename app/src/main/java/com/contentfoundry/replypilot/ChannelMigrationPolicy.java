package com.contentfoundry.replypilot;

/** Select once; subsequent OS changes belong to the user, including muting the new channel. */
final class ChannelMigrationPolicy {
    static String select(String saved,String legacy,String bundled,boolean bundledExists,boolean legacyExists,boolean legacyUnchanged) {
        if(legacy.equals(saved)||bundled.equals(saved))return saved;
        // Channel creation can survive process death before our preference was committed.
        if(bundledExists)return bundled;
        return !legacyExists||legacyUnchanged?bundled:legacy;
    }
}
