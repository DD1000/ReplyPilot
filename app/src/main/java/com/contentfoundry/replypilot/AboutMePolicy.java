package com.contentfoundry.replypilot;

import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit owner-supplied personality notes. Never mined from other contacts. */
final class AboutMePolicy {
    static final Map<String,Integer> LIMITS=Map.of("about",1200,"voice",800,"humor",800,"avoid",800,"examples",2400);
    static Map<String,String> validate(Map<String,?> input){
        if(input==null)input=Map.of();
        for(String key:input.keySet())if(!LIMITS.containsKey(key))throw new IllegalArgumentException("Unknown About me field.");
        Map<String,String> result=new LinkedHashMap<>();int total=0;
        for(String key:new String[]{"about","voice","humor","avoid","examples"}){
            Object raw=input.containsKey(key)?input.get(key):"";
            if(!(raw instanceof String text))throw new IllegalArgumentException("About me fields must be text.");
            if(text.length()>LIMITS.get(key))throw new IllegalArgumentException("The "+key+" field is too long.");
            total+=text.length();result.put(key,text.strip());
        }
        if(total>6000)throw new IllegalArgumentException("Keep About me within 6,000 characters.");
        return result;
    }
}
