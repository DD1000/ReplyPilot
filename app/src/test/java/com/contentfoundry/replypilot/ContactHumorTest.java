package com.contentfoundry.replypilot;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContactHumorTest {
    @Test public void acceptsEveryDiscreteLevel(){for(int i=0;i<=4;i++)assertEquals(i,ContactHumor.level(i));}
    @Test public void integralJsonNumericRepresentationsAreEquivalent(){assertEquals(3,ContactHumor.level(3L));assertEquals(4,ContactHumor.level(4.0));assertEquals(2,ContactHumor.level(new BigDecimal("2.000")));assertEquals(0,ContactHumor.level(BigInteger.ZERO));}
    @Test public void neverCoercesNullStringsOrBooleans(){for(Object value:new Object[]{null,"2",true,false,new Object()})assertThrows(IllegalArgumentException.class,()->ContactHumor.level(value));}
    @Test public void rejectsEveryOutOfRangeOrNonFiniteNumber(){for(Number value:new Number[]{-1,5,Integer.MAX_VALUE,Long.MAX_VALUE,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->ContactHumor.level(value));}
    @Test public void neverRoundsFractionalValuesIntoASetting(){for(Number value:new Number[]{1.2,-0.001,4.01,new BigDecimal("4.000000000000000000001"),new BigDecimal("0.000000000000000000001")})assertThrows(IllegalArgumentException.class,()->ContactHumor.level(value));}
    @Test public void optionalNotesCanBeEmptyAndPreserveFormatting(){assertEquals("",ContactHumor.notes(""));String note="  The rubber-duck incident 🦆\nOur phrase: 'nice landing'  ";assertEquals(note,ContactHumor.notes(note));}
    @Test public void notesRejectWrongTypesRatherThanSerializingThem(){for(Object value:new Object[]{null,1,true,new Object()})assertThrows(IllegalArgumentException.class,()->ContactHumor.notes(value));}
    @Test public void notesEnforceExactLimitWithoutTruncation(){String full="a".repeat(2000);assertEquals(full,ContactHumor.notes(full));assertThrows(IllegalArgumentException.class,()->ContactHumor.notes(full+"a"));}
    @Test public void emojiLimitMatchesAndroidAndHtmlUtf16Length(){assertEquals("🦆".repeat(1000),ContactHumor.notes("🦆".repeat(1000)));assertThrows(IllegalArgumentException.class,()->ContactHumor.notes("🦆".repeat(1001)));}
}
