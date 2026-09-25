package com.contentfoundry.replypilot;

import java.text.Normalizer;

/** Freshness and distance rules shared by capture, home classification, and sending. */
final class LocationPolicy {
    static final long INTERVAL_MS=20*60_000L,MAX_AGE_MS=25*60_000L,CLOCK_SKEW_MS=60_000L;
    static boolean coordinates(double latitude,double longitude){return Double.isFinite(latitude)&&Double.isFinite(longitude)&&latitude>=-90&&latitude<=90&&longitude>=-180&&longitude<=180;}
    static boolean usable(double latitude,double longitude,double accuracy,boolean mock){return coordinates(latitude,longitude)&&Double.isFinite(accuracy)&&accuracy>=0&&accuracy<=50_000&&!mock;}
    static boolean precise(double accuracy,boolean permission,boolean mock){return permission&&!mock&&Double.isFinite(accuracy)&&accuracy>=0&&accuracy<=150;}
    static boolean fresh(long captured,long elapsed,int fixBoot,long now,long elapsedNow,int boot){
        if(captured<=0||elapsed<=0||fixBoot<0||boot!=fixBoot||now<captured||elapsedNow<elapsed)return false;
        long wallAge=now-captured,monotonicAge=elapsedNow-elapsed;
        return wallAge<MAX_AGE_MS&&monotonicAge<MAX_AGE_MS&&Math.abs(wallAge-monotonicAge)<=CLOCK_SKEW_MS;
    }
    static boolean due(long lastWall,long lastElapsed,int lastBoot,long now,long elapsed,int boot){
        if(lastWall<=0)return true;
        if(lastBoot>=0&&lastBoot==boot&&elapsed>=lastElapsed)return elapsed-lastElapsed>=INTERVAL_MS;
        return now>=lastWall&&now-lastWall>=INTERVAL_MS;
    }
    static boolean currentForHome(long captured,long elapsed,int fixBoot,long now,long elapsedNow,int boot){
        return fresh(captured,elapsed,fixBoot,now,elapsedNow,boot)&&now-captured<=120_000&&elapsedNow-elapsed<=120_000;
    }
    static double distance(double lat1,double lon1,double lat2,double lon2){
        if(!coordinates(lat1,lon1)||!coordinates(lat2,lon2))return Double.POSITIVE_INFINITY;
        double a=Math.toRadians(lat2-lat1),b=Math.toRadians(lon2-lon1);
        double sin=Math.sin(a/2)*Math.sin(a/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(b/2)*Math.sin(b/2);
        return 6_371_000*2*Math.atan2(Math.sqrt(Math.min(1,sin)),Math.sqrt(Math.max(0,1-sin)));
    }
    static boolean home(double distance,double accuracy,boolean precise){return precise&&Double.isFinite(distance)&&distance>=0&&Double.isFinite(accuracy)&&accuracy>=0&&distance+accuracy<=150;}
    static String clean(String value,int limit){
        if(value==null)return "";
        String normalized=Normalizer.normalize(value,Normalizer.Form.NFKC);StringBuilder text=new StringBuilder();
        normalized.codePoints().forEach(cp->{if(Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT||Character.isWhitespace(cp)||Character.isSpaceChar(cp))text.append(' ');else text.appendCodePoint(cp);});
        String result=text.toString().trim().replaceAll(" +"," ");
        if(result.length()>limit){int end=limit;if(end>0&&Character.isHighSurrogate(result.charAt(end-1)))end--;result=result.substring(0,end).trim();}
        return result;
    }
    static String label(String place,String city,boolean home){
        if(home)return "at home";
        String p=component(place,100),c=component(city,60);
        return p.isEmpty()?(c.isEmpty()?"":"near "+c):"near "+p+(c.isEmpty()?"":" in "+c);
    }
    private static String component(String value,int limit){
        String text=clean(value,limit).replaceAll("[^\\p{L}\\p{N} .,'’()&/\\-]"," ").trim().replaceAll(" +"," ").replaceFirst("^[^\\p{L}\\p{N}]+","");
        if(text.matches(".*[+-]?\\p{Nd}{1,3}\\.\\p{Nd}+.*")||text.matches(".*[+-]?\\p{Nd}{1,3}\\s*,\\s*[+-]?\\p{Nd}{1,3}.*")
            ||java.util.regex.Pattern.compile("\\b\\p{Nd}+ [\\p{L}\\p{N} .'’-]{1,80} (?:street|st|road|rd|avenue|ave|drive|dr|lane|ln|boulevard|blvd|court|ct)\\b",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE).matcher(text).find())return "";
        return text;
    }
    static boolean valid(boolean enabled,boolean permission,boolean services,boolean preciseNow,boolean preciseAtCapture,long expectedRevision,long revision,long expires,long captured,long elapsed,int capturedBoot,long now,long elapsedNow,int boot){
        return enabled&&permission&&services&&(!preciseAtCapture||preciseNow)&&expectedRevision==revision&&expires==captured+MAX_AGE_MS&&now<expires&&fresh(captured,elapsed,capturedBoot,now,elapsedNow,boot);
    }
}
