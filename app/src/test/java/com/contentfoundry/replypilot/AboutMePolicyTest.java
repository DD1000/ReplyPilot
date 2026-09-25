package com.contentfoundry.replypilot;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;
public class AboutMePolicyTest {
    @Test public void missingNotesStayEmpty(){assertEquals(5,AboutMePolicy.validate(Map.of()).size());assertEquals("",AboutMePolicy.validate(Map.of()).get("humor"));}
    @Test public void keepsExplicitHumorAndBoundaries(){var p=AboutMePolicy.validate(Map.of("humor","  dry jokes  ","avoid","No jokes about grief"));assertEquals("dry jokes",p.get("humor"));assertEquals("No jokes about grief",p.get("avoid"));}
    @Test public void rejectsUnboundedOrUnrecognizedData(){for(Map<String,?> bad:java.util.List.of(Map.of("humor","a".repeat(801)),Map.of("key","private"),Map.of("voice",true))){assertThrows(IllegalArgumentException.class,()->AboutMePolicy.validate(bad));}}
}
