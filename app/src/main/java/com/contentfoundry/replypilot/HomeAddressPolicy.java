package com.contentfoundry.replypilot;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Only actual numbered street records become selectable; coordinates stay native. */
final class HomeAddressPolicy {
    static final int MAX_CANDIDATES=10, MAX_INPUT=210;
    static final double MAX_ACCURACY=50, RADIUS_METERS=250;
    static final long MAX_AGE=120_000L;
    record Candidate(String address,double latitude,double longitude){}
    record Choice(String address,double latitude,double longitude,double distance){}
    record Session(String requestId,long revision,long epoch,long captured,long elapsed,int boot,Map<String,Choice> choices){
        Session {choices=Map.copyOf(choices);}
        boolean valid(long activeRevision,long activeEpoch,long now,long elapsedNow,int activeBoot){return revision==activeRevision&&epoch==activeEpoch&&LocationPolicy.currentForHome(captured,elapsed,boot,now,elapsedNow,activeBoot);}
        Choice choose(String requested,String candidate,long activeRevision,long activeEpoch,long now,long elapsedNow,int activeBoot){
            if(!requestId.equals(requested)||!valid(activeRevision,activeEpoch,now,elapsedNow,activeBoot)||candidate==null||!choices.containsKey(candidate))throw new IllegalStateException("That address search expired. Search your current place again.");return choices.get(candidate);
        }
    }
    private HomeAddressPolicy(){}
    static boolean preciseFix(double latitude,double longitude,double accuracy,boolean mock){return LocationPolicy.usable(latitude,longitude,accuracy,mock)&&accuracy<=MAX_ACCURACY;}
    static String address(String number,String street,String city,String region,String postal,String country){
        String n=part(number,30),s=part(street,140);if(n.isEmpty()||s.isEmpty()||!n.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} /.,'’\\-]{0,29}")||!n.matches(".*\\p{Nd}.*")||!s.matches(".*\\p{L}.*"))return "";
        ArrayList<String> parts=new ArrayList<>();parts.add(n+" "+s);for(String raw:List.of(city==null?"":city,region==null?"":region,postal==null?"":postal,country==null?"":country)){String value=part(raw,80);if(!value.isEmpty()&&!parts.contains(value))parts.add(value);}
        String result=String.join(", ",parts);return result.length()<=300?result:"";
    }
    private static String part(String raw,int limit){if(raw==null)return "";if(raw.length()>limit*2)return "";for(int i=0;i<raw.length();i++)if(Character.isISOControl(raw.charAt(i))||Character.getType(raw.charAt(i))==Character.FORMAT)return "";String cleaned=Normalizer.normalize(raw,Normalizer.Form.NFKC).trim().replaceAll("\\s+"," ");return cleaned.length()<=limit?cleaned:"";}
    static List<Choice> nearest(List<Candidate> input,double latitude,double longitude){
        if(input==null||!LocationPolicy.coordinates(latitude,longitude))return List.of();LinkedHashMap<String,Choice> unique=new LinkedHashMap<>();
        for(int i=0;i<Math.min(MAX_INPUT,input.size());i++){Candidate c=input.get(i);if(c==null||c.address()==null||c.address().isBlank()||c.address().length()>300||!LocationPolicy.coordinates(c.latitude(),c.longitude()))continue;double distance=LocationPolicy.distance(latitude,longitude,c.latitude(),c.longitude());if(!Double.isFinite(distance)||distance>RADIUS_METERS)continue;String key=Normalizer.normalize(c.address(),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s,]+"," ").trim();Choice chosen=new Choice(c.address(),c.latitude(),c.longitude(),distance),previous=unique.get(key);if(previous==null||distance<previous.distance())unique.put(key,chosen);}
        return unique.values().stream().sorted(Comparator.comparingDouble(Choice::distance).thenComparing(Choice::address)).limit(MAX_CANDIDATES).toList();
    }
}
