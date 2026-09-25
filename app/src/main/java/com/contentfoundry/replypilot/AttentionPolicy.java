package com.contentfoundry.replypilot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Notification authorization is for one exact message and one explicit action. */
final class AttentionPolicy {
    static final String DELAY_TEXT="I'll get back to you on that.";
    record Binding(long thread,long base,long profileRevision,String configRevision,String fingerprint,long burst){}
    static boolean token(String token){return token!=null&&token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    static boolean offered(String action,String reason){return "joke".equals(action)&&("needs_review".equals(reason)||"plans_need_input".equals(reason))||"delay".equals(action)&&"plans_need_input".equals(reason);}
    static boolean matches(Binding saved,Binding current){return saved!=null&&current!=null&&saved.thread()>0&&saved.base()>0&&saved.burst()>=0&&saved.fingerprint()!=null&&!saved.fingerprint().isEmpty()&&saved.equals(current);}
    static boolean claimable(String state,long job){return "queued".equals(state)&&job==0;}
    static boolean reoffer(String action,String state,long job,boolean bindingChanged,boolean live){
        return ("joke".equals(action)||"delay".equals(action))&&job==0&&!live&&("failed".equals(state)||"stale".equals(state)||"offered".equals(state)&&bindingChanged);
    }
    static boolean current(long base,long latest){return base>0&&base==latest;}
    static String fingerprint(long thread,long base,long date,int type,String address,String body){
        try{
            // Length-prefixed fields cannot collide by moving a delimiter inside text.
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(String part:new String[]{Long.toString(thread),Long.toString(base),Long.toString(date),Integer.toString(type),address==null?"":address,body==null?"":body}){
                byte[] bytes=part.getBytes(StandardCharsets.UTF_8);
                digest.update((bytes.length+":").getBytes(StandardCharsets.US_ASCII));digest.update(bytes);
            }
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest()){hex.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));}return hex.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
