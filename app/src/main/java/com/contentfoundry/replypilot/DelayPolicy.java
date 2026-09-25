package com.contentfoundry.replypilot;

import java.util.function.LongUnaryOperator;

/** Immutable choices; sampling is an explicit operation performed only when creating a timer. */
final class DelayPolicy {
    static final long DEFAULT_MIN=300,DEFAULT_MAX=1800,MAX_SECONDS=604800;
    record Choice(String mode,long fixed,long min,long max){
        boolean instant(){return "fixed".equals(mode)&&fixed==0;}
        long minimumSeconds(){return "range".equals(mode)?min:fixed;}
    }
    static String mode(Object value){
        if(!"fixed".equals(value)&&!"range".equals(value))throw new IllegalArgumentException("Choose a fixed delay or a random range.");
        return (String)value;
    }
    static long seconds(Object value){
        if(!(value instanceof Number number))throw new IllegalArgumentException("Choose a valid reply delay.");
        double seconds=number.doubleValue();
        if(!Double.isFinite(seconds)||seconds!=Math.rint(seconds)||seconds<0||seconds>MAX_SECONDS)
            throw new IllegalArgumentException("Choose a valid reply delay.");
        return number.longValue();
    }
    /** One second is an explicit testing preset; custom delays remain whole minutes. */
    static long fixedSeconds(long seconds,boolean allowInstant){
        if(seconds==1||allowInstant&&seconds==0||seconds>=60&&seconds<=MAX_SECONDS&&seconds%60==0)return seconds;
        throw new IllegalArgumentException("Choose 1 second (test), or a whole-minute delay from one minute to seven days."+(allowInstant?" Autopilot can send instantly.":""));
    }
    private static long endpoint(Object value){
        if(!(value instanceof Number number))throw new IllegalArgumentException("Enter both range limits as whole minutes from 1 to 10,080.");
        double seconds=number.doubleValue();
        if(!Double.isFinite(seconds)||seconds!=Math.rint(seconds)||seconds<60||seconds>MAX_SECONDS||seconds%60!=0)
            throw new IllegalArgumentException("Enter both range limits as whole minutes from 1 to 10,080.");
        return (long)seconds;
    }
    static Choice choice(Object mode,long fixed,Object min,Object max){
        String selected=mode(mode);
        fixedSeconds(fixed,true);
        long lower,upper;
        if("fixed".equals(selected)){
            // Incomplete hidden range inputs must not break a fixed/Autopilot choice.
            try{lower=endpoint(min);upper=endpoint(max);if(lower>upper)throw new IllegalArgumentException();}
            catch(IllegalArgumentException ignored){lower=DEFAULT_MIN;upper=DEFAULT_MAX;}
        }else{lower=endpoint(min);upper=endpoint(max);}
        if(lower>upper)throw new IllegalArgumentException("The shortest delay must be no longer than the longest delay.");
        return new Choice(selected,fixed,lower,upper);
    }
    static long choose(Choice choice,LongUnaryOperator draw){
        if("fixed".equals(choice.mode()))return choice.fixed();
        if(choice.min()==choice.max())return choice.min();
        long width=choice.max()-choice.min()+1,offset=draw.applyAsLong(width);
        if(offset<0||offset>=width)throw new IllegalStateException("A reply delay could not be chosen.");
        return choice.min()+offset;
    }
    static String duration(long seconds){
        long minutes=seconds/60,remainder=seconds%60;
        return minutes==0?remainder+" sec":minutes+" min"+(remainder==0?"":" "+remainder+" sec");
    }
}
