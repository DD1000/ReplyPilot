package com.contentfoundry.replypilot;

import org.junit.Test;
import static org.junit.Assert.*;

public class ContactPolicyTest {
    @Test public void contactFormattingProducesAnAddressTheSenderAccepts(){
        assertEquals("+14155550123",ContactPolicy.number("+1 (415) 555-0123"));
        assertEquals("4155550123",ContactPolicy.number("(415) 555.0123"));
        assertEquals("+442079460123",ContactPolicy.number("+44\u00a020 7946 0123"));
        assertEquals("12345",ContactPolicy.number("12345"));
        for(String raw:new String[]{"+1 (415) 555-0123","(415) 555.0123","+44\u00a020 7946 0123","12345"})
            assertTrue(SendPolicy.validAddress(ContactPolicy.number(raw)));
    }

    @Test public void extensionsAndNonPhoneDestinationsAreNeverSilentlyConverted(){
        for(String raw:new String[]{"4155550123 ext 12","4155550123x12","4155550123,12","4155550123;12",
            "1-800-FLOWERS","person@example.com","sip:4155550123","*123#","4155550123\n4155554567","415/555/0123"})
            assertNull(raw,ContactPolicy.number(raw));
        assertNull(ContactPolicy.number(null));
        assertNull(ContactPolicy.number(""));
        assertNull(ContactPolicy.number("() - ."));
        assertNull(ContactPolicy.number("12"));
        assertNull(ContactPolicy.number("++14155550123"));
        assertNull(ContactPolicy.number("1415+5550123"));
        assertNull(ContactPolicy.number("1".repeat(26)));
    }

    @Test public void theSamePersonsDuplicateNumberIsOnlyListedOnce(){
        ContactPolicy.Page page=new ContactPolicy.Page();
        page.add(10,1,"Alex","(415) 555-0123","+14155550123","Mobile");
        page.add(11,1,"Alex","415.555.0123",null,"Home");
        page.add(12,1,"Alex","+1 415 555 0123","+14155550123","Mobile");
        assertEquals(1,page.contacts().size());
        assertEquals("10",page.contacts().get(0).id());
        assertEquals("4155550123",page.contacts().get(0).number());
        assertEquals("Mobile",page.contacts().get(0).label());
    }

    @Test public void differentNumbersAndDifferentContactsRemainSeparateChoices(){
        ContactPolicy.Page page=new ContactPolicy.Page();
        page.add(10,1,"Alex","4155550123",null,"Mobile");
        page.add(11,1,"Alex","4155554567",null,"Work");
        page.add(12,2,"Blair","4155550123",null,"Home");
        assertEquals(3,page.contacts().size());
        assertEquals("Work",page.contacts().get(1).label());
        assertEquals("Blair",page.contacts().get(2).name());
    }

    @Test public void invalidRowsAndDuplicateRowsDoNotConsumeTheResultLimit(){
        ContactPolicy.Page page=new ContactPolicy.Page();
        for(int i=1;i<=80;i++){
            assertFalse(page.add(i,i,"Contact "+i,"+1415555"+String.format("%04d",i),null,"Mobile"));
            assertFalse(page.add(1000+i,i,"Contact "+i,"+1415555"+String.format("%04d",i),null,"Other"));
            assertFalse(page.add(2000+i,i,"Contact "+i,"invalid",null,"Other"));
        }
        assertEquals(80,page.contacts().size());
        assertFalse(page.hasMore());
        assertTrue(page.add(5000,100,"Next contact","+14155559999",null,"Work"));
        assertEquals(80,page.contacts().size());
        assertTrue(page.hasMore());
    }

    @Test public void normalizedMetadataDoesNotMakeAnUnsendableNumberSelectable(){
        ContactPolicy.Page page=new ContactPolicy.Page();
        page.add(10,1,"Alex","4155550123 ext 99","+14155550123","Work");
        page.add(0,1,"Alex","4155550123",null,"Mobile");
        page.add(10,0,"Alex","4155550123",null,"Mobile");
        assertTrue(page.contacts().isEmpty());
    }

    @Test public void searchKeepsLiteralTextAndRejectsOversizeInputs(){
        assertEquals("O'Connor / Work",ContactPolicy.query("  O'Connor / Work  "));
        assertEquals("%_ +1 ?#",ContactPolicy.query("%_ +1 ?#"));
        assertEquals("",ContactPolicy.query(null));
        assertEquals("",ContactPolicy.query("   "));
        assertEquals(120,ContactPolicy.query("a".repeat(120)).length());
        assertThrows(IllegalArgumentException.class,()->ContactPolicy.query("a".repeat(121)));
        assertThrows(IllegalArgumentException.class,()->ContactPolicy.query(" ".repeat(121)));
    }

    @Test public void missingDisplayTextFallsBackWithoutDroppingThePhone(){
        ContactPolicy.Page page=new ContactPolicy.Page();
        page.add(10,1,null,"4155550123",null,null);
        assertEquals("4155550123",page.contacts().get(0).name());
        assertEquals("Phone",page.contacts().get(0).label());
    }
}
