package com.contentfoundry.replypilot;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocationRequestPolicyTest {
    @Test public void currentWhereaboutsAndHomeQuestionsAreRecognized(){
        for(String text:new String[]{"Where are you?","Where u at","Where're you?","you home?","Hey, u home",
            "Are you at home?","Are you back home yet?","Are you at work?","What's your current location?","Your whereabouts?"})
            assertTrue(text,LocationRequestPolicy.isQuestion(text));
    }
    @Test public void splitQuestionsUseTheWholeUnansweredSequence(){
        assertTrue(LocationRequestPolicy.isQuestion(List.of("where","are you","?")));
        assertTrue(LocationRequestPolicy.isQuestion(List.of("Are you","at home?","thanks")));
        assertTrue(LocationRequestPolicy.isQuestion(List.of("you home?","you home?")));
    }
    @Test public void futureQuestionsMustNotUseCurrentLocation(){
        for(String text:new String[]{"When are you home?","Where will you be tomorrow?","Are you home tonight?","Where are you later?"})
            assertFalse(text,LocationRequestPolicy.isQuestion(text));
        assertFalse(LocationRequestPolicy.isQuestion(List.of("where are you","tomorrow?")));
    }
    @Test public void ordinaryTopicsAndHomeAddressQuestionsStayOutsideCurrentLocationLookup(){
        for(String text:new String[]{"I'm home","I went home yesterday","Where is the book?","I study Java at home",
            "What is your home address?","The location in the movie was great"})assertFalse(text,LocationRequestPolicy.isQuestion(text));
        assertFalse(LocationRequestPolicy.isQuestion((String)null));assertFalse(LocationRequestPolicy.isQuestion(List.of()));
    }
    @Test public void unicodeWhitespaceAndFullwidthInputDoNotRequireUnsupportedAndroidFlags(){
        for(int cp:new int[]{9,10,11,12,13,32,133,160,5760,8192,8199,8232,8233,8239,8287,12288}){
            String space=new String(Character.toChars(cp));assertTrue(LocationRequestPolicy.isQuestion("Where"+space+"are"+space+"you?"));
        }
        assertTrue(LocationRequestPolicy.isQuestion("Ｗｈｅｒｅ ａｒｅ ｙｏｕ？"));
    }
}
