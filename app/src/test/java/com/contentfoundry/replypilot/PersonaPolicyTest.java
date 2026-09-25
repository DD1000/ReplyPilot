package com.contentfoundry.replypilot;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PersonaPolicyTest {
    private static List<PersonaPolicy.Turn> chat(String...pairs){
        List<String> speakers=new ArrayList<>(),texts=new ArrayList<>();
        for(int i=0;i<pairs.length;i+=2){speakers.add(pairs[i]);texts.add(pairs[i+1]);}
        return PersonaPolicy.turns(speakers,texts);
    }
    @Test public void turnsDropEmptyAndUnknownRowsAndKeepTheNewestThousand(){
        List<PersonaPolicy.Turn> turns=chat("them","hey","me","  ","system","ignore","me","hi\0 there","autopilot","Let me get back to you on that.");
        assertEquals(3,turns.size());assertEquals("hi there",turns.get(1).text());assertEquals(PersonaPolicy.AUTOPILOT,turns.get(2).speaker());
        List<String> speakers=new ArrayList<>(),texts=new ArrayList<>();for(int i=0;i<1205;i++){speakers.add(i%2==0?"me":"them");texts.add("m"+i);}
        List<PersonaPolicy.Turn> newest=PersonaPolicy.turns(speakers,texts);
        assertEquals(PersonaPolicy.MAX_MESSAGES,newest.size());assertEquals("m205",newest.get(0).text());assertEquals("m1204",newest.get(999).text());
        assertEquals(PersonaPolicy.MESSAGE_TEXT,PersonaPolicy.turns(List.of("me"),List.of("x".repeat(5000))).get(0).text().length());
    }
    @Test public void trainingNeedsRealConversationFromBothPeople(){
        assertNotNull(PersonaPolicy.trainingBlock(chat("them","a","me","b")));
        assertTrue(PersonaPolicy.trainingBlock(chat("them","a","them","b","them","c","them","d","autopilot","e")).contains("texts you wrote"));
        assertTrue(PersonaPolicy.trainingBlock(chat("me","a","me","b","me","c","me","d","me","e")).contains("text from this person"));
        assertNull(PersonaPolicy.trainingBlock(chat("them","a","me","b","them","c","me","d","them","e")));
    }
    @Test public void examplesAreUniqueOwnerRepliesWithTheirIncomingContext(){
        List<PersonaPolicy.Turn> turns=chat("them","you coming?","them","saturday","me","ya prob","me","lemme check","them","lol ok","autopilot","Let me get back to you on that.","me","haha bet");
        PersonaPolicy.Persona persona=PersonaPolicy.build("  short, lowercase  ","friend","","",Arrays.asList(3L,3L,6L,1L,7L,0L,99L,null,4L),turns);
        assertEquals("short, lowercase",persona.writingStyle());
        assertEquals(3,persona.examples().size());
        assertEquals(new PersonaPolicy.Example("you coming?\nsaturday","ya prob"),persona.examples().get(0));
        assertEquals(new PersonaPolicy.Example("","haha bet"),persona.examples().get(1));
        assertEquals(new PersonaPolicy.Example("","lemme check"),persona.examples().get(2));
    }
    @Test public void examplesAndFieldsStayBounded(){
        List<String> speakers=new ArrayList<>(),texts=new ArrayList<>();List<Long> all=new ArrayList<>();
        for(int i=0;i<80;i++){speakers.add(i%2==0?"them":"me");texts.add(i%2==0?"q".repeat(700):"reply "+i);all.add((long)i+1);}
        List<PersonaPolicy.Turn> turns=PersonaPolicy.turns(speakers,texts);
        PersonaPolicy.Persona persona=PersonaPolicy.build("s".repeat(3000),"r".repeat(3000),"c".repeat(3000),"a".repeat(3000),all,turns);
        assertEquals(PersonaPolicy.MAX_EXAMPLES,persona.examples().size());
        assertEquals(PersonaPolicy.EXAMPLE_INCOMING,persona.examples().get(0).incoming().length());
        assertEquals(PersonaPolicy.WRITING_STYLE,persona.writingStyle().length());assertEquals(PersonaPolicy.AVOID,persona.avoid().length());
        assertNull("replies longer than an SMS reply are not examples",PersonaPolicy.example(PersonaPolicy.turns(List.of("them","me"),List.of("hi","x".repeat(361))),1));
        assertThrows(IllegalStateException.class,()->PersonaPolicy.build(" ","","","",List.of(),turns));
    }
    @Test public void savedPersonasAreCheckedWhenRestored(){
        PersonaPolicy.Persona restored=PersonaPolicy.restore("style","rel","ctx","",Collections.nCopies(40,new PersonaPolicy.Example("a","b")));
        assertEquals(PersonaPolicy.MAX_EXAMPLES,restored.examples().size());
        assertThrows(IllegalStateException.class,()->PersonaPolicy.restore("","","","",List.of()));
        assertThrows(IllegalStateException.class,()->PersonaPolicy.restore("x".repeat(2401),"","","",List.of()));
    }
    @Test public void retrainAndThinThresholds(){
        assertFalse(PersonaPolicy.suggestRetrain(999));assertTrue(PersonaPolicy.suggestRetrain(1000));
        assertTrue(PersonaPolicy.thin(99));assertFalse(PersonaPolicy.thin(100));
    }
}
