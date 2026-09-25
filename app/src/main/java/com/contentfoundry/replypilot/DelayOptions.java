package com.contentfoundry.replypilot;

import org.json.JSONObject;
import java.util.concurrent.ThreadLocalRandom;

final class DelayOptions {
    static DelayPolicy.Choice request(JSONObject request,long fixed,String modeKey,String minKey,String maxKey){
        Object mode=request.has(modeKey)?request.opt(modeKey):"fixed";
        boolean range="range".equals(DelayPolicy.mode(mode));
        // Range endpoints are required raw numbers. In particular JSONObject's
        // optLong must not turn fractions, nulls, or numeric strings into consent.
        Object min=request.has(minKey)?request.opt(minKey):range?null:DelayPolicy.DEFAULT_MIN;
        Object max=request.has(maxKey)?request.opt(maxKey):range?null:DelayPolicy.DEFAULT_MAX;
        return DelayPolicy.choice(mode,fixed,min,max);
    }
    static DelayPolicy.Choice profile(JSONObject profile){return DelayPolicy.choice("fixed",PersonProfile.autoDelay(profile.optLong("autoDelay",0)),DelayPolicy.DEFAULT_MIN,DelayPolicy.DEFAULT_MAX);}
    static long choose(DelayPolicy.Choice choice){return DelayPolicy.choose(choice,bound->ThreadLocalRandom.current().nextLong(bound));}
}
