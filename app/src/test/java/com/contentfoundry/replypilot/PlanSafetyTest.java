package com.contentfoundry.replypilot;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlanSafetyTest {
    @Test public void invitationsAvailabilityAndWorkRequestsNeedTheOwner(){
        for(String text:new String[]{"Are you free tomorrow?","Will you be available on Friday?",
            "What's your availability?","When are you free?","What are you doing tonight?",
            "Are you working tomorrow?","Want to grab dinner?","Wanna hang out?",
            "Would you like to meet?","Down for coffee?","Can you come over?",
            "Join us for lunch","Can you cover my shift?","Could you pick me up?",
            "Let's meet at 7","Can we go for a walk?","Dinner tomorrow?","Coffee at 3pm?","Coffee at Java House?","Dinner?","Want coffee?","You free?","Come over"})
            assertEquals(text,"plans_need_input",PlanSafety.reason(List.of(text),null));
    }
    @Test public void completeUnansweredSequenceKeepsSplitInvitesAndCorrectionsHeld(){
        for(List<String> burst:List.of(List.of("dinner","tomorrow?"),
            List.of("dinner","tomorrow?","thanks"),List.of("Are you free Friday?","actually Saturday","ok"),
            List.of("Can","you","come over?"),List.of("Want to grab dinner?","Sorry, wrong time","7 instead"),
            List.of("dinner","tomorrow?","dinner","tomorrow?")))
            assertTrue(burst.toString(),PlanSafety.incoming(burst));
    }
    @Test public void confirmationsChangesAndDelaysNeverGetAnAutomaticAcknowledgment(){
        for(String text:new String[]{"Are we still on?","We're still on for dinner",
            "Confirm our meeting","Can you confirm the arrangement?","See you at 7",
            "Let's reschedule","Move our dinner to Friday","Can we change it?",
            "Can't make it","I'm running late","I'll be 10 minutes late",
            "Does 7 work?","Actually tomorrow instead","What time works for you?"})
            assertTrue(text,PlanSafety.incoming(List.of(text)));
    }
    @Test public void historicalStoriesAndOrdinaryTopicsStayContextual(){
        for(String text:new String[]{"I'm studying Java","I started learning Java today",
            "We had dinner last Friday","Dinner yesterday was great","I went to a concert last weekend",
            "That meeting was funny","The coffee is delicious","I am reading about scheduling algorithms",
            "Want to hear a joke?","How was your day?","Thanks","Sounds good","See you later"})
            assertNull(text,PlanSafety.reason(List.of(text),null));
    }
    @Test public void candidateAvailabilityPromisesAndInvitationsAreHeld(){
        for(String text:new String[]{"I'm free tomorrow","I am available","I'm busy tonight",
            "I can make it","I can't make it","I'll be there","Count me in",
            "7 works for me","I could do Friday","Let's meet tomorrow","See you at 7",
            "I'll call you","I will send it","We booked a table","I'm on my way",
            "I don't have plans","What time works for you?","I'm not free","I'll be available","Tomorrow works","Friday sounds good","6 it is","See you then"})
            assertEquals(text,"plans_need_input",PlanSafety.reason(List.of("How are things?"),text));
    }
    @Test public void safeCandidatesDoNotInventPlans(){
        for(String text:new String[]{"That sounds frustrating","Which movie was it?","That made me laugh",
            "I liked that book","I could not understand the ending","Thanks for sharing"})
            assertFalse(text,PlanSafety.commitment(text));
        assertFalse(PlanSafety.commitment(null));assertFalse(PlanSafety.incoming(List.of()));
    }
    @Test public void unicodeWhitespaceApostrophesAndSplitWordsRemainCoveredWithoutAndroidFlags(){
        for(int cp:new int[]{9,10,11,12,13,32,0x85,0xa0,0x1680,0x2000,0x2001,0x2002,0x2003,
            0x2004,0x2005,0x2006,0x2007,0x2008,0x2009,0x200a,0x2028,0x2029,0x202f,0x205f,0x3000}){
            String space=new String(Character.toChars(cp));
            assertTrue("U+"+Integer.toHexString(cp),PlanSafety.incoming(List.of("Are"+space+"you"+space+"free?")));
        }
        assertTrue(PlanSafety.incoming(List.of("Ａｒｅ ｙｏｕ ｆｒｅｅ？")));
        assertTrue(PlanSafety.commitment("I’ll be there"));
        assertTrue(PlanSafety.incoming(List.of("Coffee at ٣pm?")));
    }
}
