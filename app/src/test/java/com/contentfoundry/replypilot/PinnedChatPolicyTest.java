package com.contentfoundry.replypilot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class PinnedChatPolicyTest {
    private static PinnedChatPolicy.Row<String> row(long thread,long date,long id){return new PinnedChatPolicy.Row<>(thread,date,id,"message "+id);}
    private static List<Long> threads(List<PinnedChatPolicy.Row<String>> rows){return rows.stream().map(PinnedChatPolicy.Row::thread).toList();}

    @Test public void oldPinnedConversationPrecedesNewerUnpinnedConversations(){
        var result=PinnedChatPolicy.select(List.of(row(1,900,90),row(2,100,10),row(3,800,80)),Set.of(2L));
        assertEquals(List.of(2L,1L,3L),threads(result));
    }
    @Test public void eachGroupUsesNewestDateThenNewestSmsId(){
        var result=PinnedChatPolicy.select(List.of(row(1,100,15),row(2,100,16),row(3,101,11),row(4,100,20),row(5,100,21)),Set.of(1L,2L,3L));
        assertEquals(List.of(3L,2L,1L,5L,4L),threads(result));
    }
    @Test public void oldPinsDoNotDisplaceAnyOfThe150RecentNormalConversations(){
        List<PinnedChatPolicy.Row<String>> rows=new ArrayList<>();
        for(int i=1;i<=160;i++)rows.add(row(i,i,i));
        rows.add(row(500,0,500));rows.add(row(501,0,501));
        var result=PinnedChatPolicy.select(rows,Set.of(500L,501L));
        assertEquals(152,result.size());
        assertEquals(List.of(501L,500L),threads(result.subList(0,2)));
        assertEquals(160,result.get(2).thread());assertEquals(11,result.get(result.size()-1).thread());
        assertFalse(threads(result).contains(10L));
    }
    @Test public void duplicateThreadRowsKeepOnlyTheirNewestMessage(){
        var result=PinnedChatPolicy.select(List.of(row(1,200,20),row(1,100,30),row(1,200,21),row(2,300,40)),Set.of(1L));
        assertEquals(List.of(1L,2L),threads(result));
        assertEquals(21,result.get(0).id());assertEquals("message 21",result.get(0).value());
    }
    @Test public void pinCountIsNotLimitedByTheRecentConversationWindow(){
        List<PinnedChatPolicy.Row<String>> rows=new ArrayList<>();Set<Long> pins=new HashSet<>();
        for(long i=1;i<=200;i++){rows.add(row(i,i,i));pins.add(i);}
        rows.add(row(300,300,300));
        var result=PinnedChatPolicy.select(rows,pins);
        assertEquals(201,result.size());assertEquals(200,result.get(0).thread());
        assertEquals(300,result.get(200).thread());
    }
    @Test public void pinAndUnpinReorderOnlyTheSameConversationRows(){
        var rows=List.of(row(1,300,30),row(2,100,10));
        assertEquals(List.of(2L,1L),threads(PinnedChatPolicy.select(rows,Set.of(2L))));
        assertEquals(List.of(1L,2L),threads(PinnedChatPolicy.select(rows,Set.of())));
        assertEquals(2,PinnedChatPolicy.select(rows,Set.of(99L)).size());
    }
    @Test public void malformedPreferencesNeverCreateInvalidOrDuplicatePinIds(){
        Set<Long> pins=PinnedChatPolicy.ids(Arrays.asList("1","01","2","0","-1","garbage","1.5","9223372036854775808",null));
        assertEquals(Set.of(1L,2L),pins);
        assertThrows(UnsupportedOperationException.class,()->pins.add(3L));
        assertEquals(Set.of(),PinnedChatPolicy.ids(null));
    }
    @Test public void invalidThreadsCannotBePinnedAndInvalidProviderRowsStayOutOfTheInbox(){
        assertThrows(IllegalArgumentException.class,()->PinnedChatPolicy.validateThread(0));
        assertThrows(IllegalArgumentException.class,()->PinnedChatPolicy.validateThread(-1));
        PinnedChatPolicy.validateThread(Long.MAX_VALUE);
        var result=PinnedChatPolicy.select(List.of(row(0,300,30),row(-1,200,20),row(1,100,0),row(2,50,5)),Set.of(0L,-1L));
        assertEquals(List.of(2L),threads(result));
    }
    @Test public void orderingDoesNotOverflowForExtremeDatesOrIds(){
        var result=PinnedChatPolicy.select(List.of(row(1,Long.MAX_VALUE,Long.MAX_VALUE),row(2,Long.MIN_VALUE,2),row(3,0,3)),Set.of());
        assertEquals(List.of(1L,3L,2L),threads(result));
    }
}
