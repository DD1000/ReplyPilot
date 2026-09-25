package com.contentfoundry.replypilot;

import java.math.BigDecimal;

/** Per-contact preferences are validated independently of automatic-send consent. */
public final class ContactHumor {
    public static final int MAX_NOTES=2000;
    private ContactHumor(){}
    /** Missing fields are handled by the caller; an explicitly supplied null is invalid. */
    public static int level(Object value){
        if(!(value instanceof Number number))throw invalidLevel();
        try{
            int level=new BigDecimal(number.toString()).intValueExact();
            if(level<0||level>4)throw invalidLevel();
            return level;
        }catch(NumberFormatException|ArithmeticException invalid){throw invalidLevel();}
    }
    public static String notes(Object value){
        if(!(value instanceof String text))throw new IllegalArgumentException("Inside jokes must be text.");
        if(text.length()>MAX_NOTES)throw new IllegalArgumentException("Keep inside jokes to 2,000 characters or fewer.");
        return text;
    }
    private static IllegalArgumentException invalidLevel(){return new IllegalArgumentException("Choose a humor level from 0 to 4.");}
}
