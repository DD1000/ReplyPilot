package com.contentfoundry.replypilot;

import java.util.List;

/** Chooses the sending SIM and explains, in plain words, why none can be used. */
final class SimPolicy {
    static final int NONE=-1;
    private SimPolicy(){}

    /**
     * The saved SIM while Android still reports it as active. Otherwise, when exactly one
     * SIM is active, that SIM: there is no other line the owner could have meant. A phone
     * with several active SIMs and a stale or missing choice still requires the owner to pick.
     */
    static int effective(int saved,List<Integer> active){
        if(active==null||active.isEmpty())return NONE;
        if(saved>=0&&active.contains(saved))return saved;
        return active.size()==1&&active.get(0)!=null&&active.get(0)>=0?active.get(0):NONE;
    }

    /** Owner-facing reason that no sending SIM is available. */
    static String problem(boolean phoneAccess,int activeCount){
        if(!phoneAccess)return "Reply Pilot needs Phone access to find your SIM. Open Settings → Sending SIM → Allow Phone access.";
        if(activeCount<=0)return "No active SIM found. Check Android Settings → Network & internet → SIMs and turn off airplane mode.";
        return "Choose your sending SIM in Settings.";
    }
}
