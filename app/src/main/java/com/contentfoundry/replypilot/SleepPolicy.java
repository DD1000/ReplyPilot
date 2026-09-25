package com.contentfoundry.replypilot;

import java.time.*;
import java.util.List;

/** A one-off local cutoff, never a recurring/proactive message schedule. */
final class SleepPolicy {
    static long nextCutoff(String clock,long now,ZoneId zone){
        if(clock==null||!clock.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))throw new IllegalArgumentException("Choose a cutoff time.");
        LocalTime time=LocalTime.parse(clock);LocalDate date=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for(int day=0;day<3;day++){
            LocalDateTime local=date.plusDays(day).atTime(time);
            List<ZoneOffset> offsets=zone.getRules().getValidOffsets(local);
            if(offsets.isEmpty()){
                // A skipped DST clock time means the first valid instant afterward.
                long value=zone.getRules().getTransition(local).getInstant().toEpochMilli();
                if(value>now)return value;
            }else{
                long next=Long.MAX_VALUE;
                for(ZoneOffset offset:offsets){long value=local.toInstant(offset).toEpochMilli();if(value>now)next=Math.min(next,value);}
                if(next!=Long.MAX_VALUE)return next;
            }
        }
        throw new IllegalArgumentException("Choose a future cutoff time.");
    }
    static long delay(long seconds){
        return DelayPolicy.fixedSeconds(seconds,false);
    }
    static boolean expired(long until,long deadlineElapsed,int expectedBoot,long now,long elapsed,int boot){
        return until<=now||(expectedBoot>=0&&boot>=0&&expectedBoot!=boot)||(deadlineElapsed>0&&elapsed>=deadlineElapsed);
    }
    static boolean allows(String mode,long expectedRevision,long currentRevision,long base,long boundary,long now,long due,long until){
        if(expectedRevision!=currentRevision||base<=boundary||"paused".equals(mode))return false;
        if("off".equals(mode))return true;
        return "active".equals(mode)&&now<until&&(due<=0||due<until);
    }
    static long effectiveDelay(String mode,long profileDelay,long sessionDelay){return "active".equals(mode)?delay(sessionDelay):PersonProfile.autoDelay(profileDelay);}
}
