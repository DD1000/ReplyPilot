package com.contentfoundry.replypilot;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class HomeAddressPolicyTest {
    private static final long WALL=1_000_000,ELAPSED=500_000;
    private static HomeAddressPolicy.Session session(){return new HomeAddressPolicy.Session("request-a",7,9,WALL,ELAPSED,3,Map.of("choice-a",new HomeAddressPolicy.Choice("12 Main Street",40,-74,15)));}
    private static HomeAddressPolicy.Candidate candidate(String address,double offset){return new HomeAddressPolicy.Candidate(address,40+offset,-74);}
    @Test public void requiresNumberAndStreetRatherThanCityOrLandmark(){assertEquals("",HomeAddressPolicy.address(null,"Main Street","Example City","NY","10000","US"));assertEquals("",HomeAddressPolicy.address("12",null,"Example City","NY","10000","US"));assertEquals("",HomeAddressPolicy.address("Unknown","Main Street",null,null,null,null));assertEquals("",HomeAddressPolicy.address("12","12345",null,null,null,null));}
    @Test public void formatsActualAddressComponentsWithoutGuessing(){assertEquals("12B Main Street, Example City, NY, 10000, US",HomeAddressPolicy.address("12B","Main Street","Example City","NY","10000","US"));assertEquals("12 Main Street",HomeAddressPolicy.address("12","Main Street",null,null,null,null));}
    @Test public void preservesUnicodeAndReportedHouseRanges(){assertEquals("12-14 Rue de l’École, Montréal",HomeAddressPolicy.address("12-14","Rue de l’École","Montréal",null,null,null));}
    @Test public void rejectsControlSpoofingAndOversizeStreet(){assertEquals("",HomeAddressPolicy.address("12\n13","Main Street",null,null,null,null));assertEquals("",HomeAddressPolicy.address("12","Main\u202e Street",null,null,null,null));assertEquals("",HomeAddressPolicy.address("12","X".repeat(141),null,null,null,null));}
    @Test public void currentFixMustBePreciseRealAndFinite(){assertTrue(HomeAddressPolicy.preciseFix(40,-74,50,false));assertFalse(HomeAddressPolicy.preciseFix(40,-74,50.01,false));assertFalse(HomeAddressPolicy.preciseFix(40,-74,5,true));assertFalse(HomeAddressPolicy.preciseFix(40,-74,Double.NaN,false));assertFalse(HomeAddressPolicy.preciseFix(100,-74,5,false));}
    @Test public void selectsNearestTenRatherThanProviderOrder(){ArrayList<HomeAddressPolicy.Candidate> input=new ArrayList<>();for(int i=15;i>=1;i--)input.add(candidate(i+" Main Street",i*.0001));var result=HomeAddressPolicy.nearest(input,40,-74);assertEquals(10,result.size());assertEquals("1 Main Street",result.get(0).address());assertEquals("10 Main Street",result.get(9).address());for(int i=1;i<result.size();i++)assertTrue(result.get(i-1).distance()<=result.get(i).distance());}
    @Test public void duplicateProviderRecordsUseClosestActualCoordinate(){var result=HomeAddressPolicy.nearest(List.of(candidate("12 Main Street, Example City",.001),candidate("12 MAIN STREET  EXAMPLE CITY",.0002),candidate("14 Main Street",.0003)),40,-74);assertEquals(2,result.size());assertEquals(40.0002,result.get(0).latitude(),.0000001);}
    @Test public void excludesDistantAndInvalidCoordinates(){var result=HomeAddressPolicy.nearest(List.of(candidate("12 Main Street",.001),candidate("99 Far Street",.01),new HomeAddressPolicy.Candidate("5 Broken Street",Double.NaN,-74)),40,-74);assertEquals(1,result.size());assertEquals("12 Main Street",result.get(0).address());}
    @Test public void noResultsNeverFabricatesNearbyHouseNumbers(){assertEquals(List.of(),HomeAddressPolicy.nearest(List.of(),40,-74));assertEquals(List.of(),HomeAddressPolicy.nearest(null,40,-74));assertEquals(List.of(),HomeAddressPolicy.nearest(List.of(candidate("12 Main Street",0)),Double.NaN,-74));}
    @Test public void handlesAntimeridianDistances(){var result=HomeAddressPolicy.nearest(List.of(new HomeAddressPolicy.Candidate("12 Shore Road",0,-179.9995)),0,179.9995);assertEquals(1,result.size());assertTrue(result.get(0).distance()<120);}
    @Test public void tokenSelectsOnlyExactOfferedAddress(){var s=session();assertEquals("12 Main Street",s.choose("request-a","choice-a",7,9,WALL+1000,ELAPSED+1000,3).address());assertThrows(IllegalStateException.class,()->s.choose("request-a","invented",7,9,WALL+1000,ELAPSED+1000,3));assertThrows(IllegalStateException.class,()->s.choose("other-request","choice-a",7,9,WALL+1000,ELAPSED+1000,3));}
    @Test public void tokenFailsAfterSettingsChangeOrCancellation(){assertFalse(session().valid(8,9,WALL+1000,ELAPSED+1000,3));assertFalse(session().valid(7,10,WALL+1000,ELAPSED+1000,3));assertFalse(session().valid(-1,9,WALL+1000,ELAPSED+1000,3));}
    @Test public void tokenExpiresUsingBothClocks(){assertTrue(session().valid(7,9,WALL+119000,ELAPSED+119000,3));assertFalse(session().valid(7,9,WALL+120001,ELAPSED+120001,3));assertFalse(session().valid(7,9,WALL+1000,ELAPSED+120001,3));}
    @Test public void clockRollbackAndRebootCannotReviveToken(){assertFalse(session().valid(7,9,WALL-1,ELAPSED+1000,3));assertFalse(session().valid(7,9,WALL+1000,ELAPSED-1,3));assertFalse(session().valid(7,9,WALL+1000,ELAPSED+1000,4));assertFalse(session().valid(7,9,WALL+1000,ELAPSED+62000,3));}
    @Test public void capturedFixAgeIncludesTimeSpentSearching(){assertFalse(session().valid(7,9,WALL+125000,ELAPSED+125000,3));}
    @Test public void sessionDoesNotShareMutableCallerMap(){Map<String,HomeAddressPolicy.Choice> choices=new LinkedHashMap<>();choices.put("choice-a",new HomeAddressPolicy.Choice("12 Main Street",40,-74,0));var s=new HomeAddressPolicy.Session("request-a",7,9,WALL,ELAPSED,3,choices);choices.clear();assertEquals(1,s.choices().size());assertThrows(UnsupportedOperationException.class,()->s.choices().clear());}
}
