package com.contentfoundry.replypilot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded, sendable phone choices; never converts extensions or vanity letters. */
final class ContactPolicy {
    static final int PAGE_SIZE=80;
    static final int MAX_QUERY_LENGTH=120;

    static String query(String value){
        if(value==null)return "";
        if(value.length()>MAX_QUERY_LENGTH)throw new IllegalArgumentException("Search with 120 characters or fewer.");
        return value.trim();
    }
    static String number(String value){
        if(value==null||value.length()>80)return null;
        StringBuilder number=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            if(ch>='0'&&ch<='9'||ch=='+')number.append(ch);
            else if(ch!='('&&ch!=')'&&ch!='-'&&ch!='.'&&!Character.isSpaceChar(ch))return null;
        }
        String result=number.toString();
        return SendPolicy.validAddress(result)?result:null;
    }
    private static String display(String value,int limit,String fallback){
        String result=value==null?"":value.trim();
        if(result.isEmpty())return fallback;
        if(result.length()<=limit)return result;
        int end=Character.isHighSurrogate(result.charAt(limit-1))?limit-1:limit;
        return result.substring(0,end);
    }
    record Contact(String id,String name,String number,String label){}

    static final class Page {
        private final List<Contact> contacts=new ArrayList<>();
        private final Set<String> seen=new HashSet<>();
        private boolean hasMore;

        /** True when a further unique sendable number proves another result exists. */
        boolean add(long rowId,long contactId,String name,String rawNumber,String normalizedNumber,String label){
            if(hasMore)return true;
            if(rowId<=0||contactId<=0)return false;
            String number=number(rawNumber);
            if(number==null)return false;
            String normalized=number(normalizedNumber);
            String key=contactId+":"+number;
            String normalizedKey=contactId+":"+(normalized!=null&&normalized.startsWith("+")?normalized:number);
            boolean duplicate=seen.contains(key)||seen.contains(normalizedKey);
            seen.add(key);seen.add(normalizedKey);
            if(duplicate)return false;
            if(contacts.size()==PAGE_SIZE){hasMore=true;return true;}
            contacts.add(new Contact(Long.toString(rowId),display(name,256,number),number,display(label,80,"Phone")));
            return false;
        }
        List<Contact> contacts(){return Collections.unmodifiableList(contacts);}
        boolean hasMore(){return hasMore;}
    }
}
