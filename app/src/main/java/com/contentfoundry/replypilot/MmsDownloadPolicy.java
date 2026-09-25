package com.contentfoundry.replypilot;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Pure validation and retry boundaries for incoming carrier MMS, never outbound messages. */
final class MmsDownloadPolicy {
    static final int MAX_NOTICE=256_000, MAX_PDU=10*1024*1024, MAX_PARTS=100;
    static final long TIMEOUT=15*60_000L;
    static String contentLocation(String value,String transaction,boolean append){return location(append&&value!=null&&value.endsWith("=")?value+transaction:value);}
    static String acknowledgmentLocation(String boundLocation,boolean notifyAtContentLocation){return notifyAtContentLocation?location(boundLocation):null;}
    static String location(String value){
        if(value==null||value.isEmpty()||value.length()>4096)throw new IllegalArgumentException("Invalid carrier download address.");
        for(int i=0;i<value.length();i++)if(value.charAt(i)<=32||value.charAt(i)>=127)throw new IllegalArgumentException("Invalid carrier download address.");
        try{
            URI uri=new URI(value);
            if(!List.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getPort()>65535)
                throw new IllegalArgumentException("Invalid carrier download address.");
            return value;
        }catch(java.net.URISyntaxException e){throw new IllegalArgumentException("Invalid carrier download address.");}
    }
    static String transaction(byte[] bytes){
        if(bytes==null||bytes.length==0||bytes.length>256)throw new IllegalArgumentException("Invalid carrier message reference.");
        for(byte b:bytes)if((b&255)<32||(b&255)==127)throw new IllegalArgumentException("Invalid carrier message reference.");
        return new String(bytes,StandardCharsets.ISO_8859_1);
    }
    static String fingerprint(String location,String transaction,String from,int sub){
        return digest((sub+"\n"+location.length()+":"+location+transaction.length()+":"+transaction+from).getBytes(StandardCharsets.UTF_8));
    }
    static String digest(byte[] value){
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value);StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));return hex.toString();}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    static boolean token(String value){return value!=null&&value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");}
    static boolean timedOut(long started,long now){return started<=0||now<started||now-started>=TIMEOUT;}
    static boolean retry(String state,boolean boundNotice,boolean access,boolean activeSim){return boundNotice&&access&&activeSim&&List.of("pending","failed","interrupted").contains(state);}
    static boolean callback(String expected,String supplied,String state){return token(supplied)&&supplied.equals(expected)&&List.of("downloading","interrupted").contains(state);}
    static boolean pduSize(long size){return size>0&&size<=MAX_PDU;}
    static boolean announcedSize(long size){return size>=0&&size<=MAX_PDU;}
    static boolean complete(int status,int parts){return (status==0||status==128)&&parts>0&&parts<=MAX_PARTS;}
    record Notice(long id,long thread,long date,int sub,int box,int type,String location,String transaction){}
    static boolean bound(Notice expected,Notice row){return row!=null&&expected.id()>0&&row.id()==expected.id()&&row.thread()==expected.thread()&&row.date()==expected.date()&&row.sub()==expected.sub()&&row.box()==1&&row.type()==130&&expected.location().equals(row.location())&&expected.transaction().equals(row.transaction());}
    static boolean partial(Notice expected,Notice row,String expectedMessageId,String messageId){return row!=null&&expected.id()>0&&row.id()==expected.id()&&row.date()==expected.date()&&row.sub()==expected.sub()&&row.box()==1&&row.type()==132&&expected.location().equals(row.location())&&expectedMessageId!=null&&!expectedMessageId.isEmpty()&&expectedMessageId.equals(messageId);}
    static int subscription(int supplied,List<Integer> active){
        if(supplied>=0)return supplied; // Never move a known incoming notice to another SIM.
        return active.size()==1?active.get(0):-1; // Outgoing default does not identify the receiving SIM.
    }
}
