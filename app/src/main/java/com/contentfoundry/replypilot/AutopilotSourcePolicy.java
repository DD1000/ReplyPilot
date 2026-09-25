package com.contentfoundry.replypilot;

/** Exact MMS reply authority: neither an equal SMS base nor newer timestamps are sufficient. */
final class AutopilotSourcePolicy {
    record Binding(long thread,long id,long date,String fingerprint,String address,long smsId,String smsSignature,long manualRevision) {}
    static boolean competing(boolean sameSource,String status,boolean carrierRecord){return "scheduled".equals(status)||"awaiting_alert".equals(status)||sameSource&&SendPolicy.repeatedImmediateBlock(status,carrierRecord)!=null;}
    static boolean matches(Binding saved,Binding current){return saved!=null&&current!=null&&saved.thread()>0&&saved.id()>0&&saved.date()>0&&saved.smsId()>=0&&saved.manualRevision()>=0&&saved.fingerprint()!=null&&!saved.fingerprint().isEmpty()&&saved.address()!=null&&!saved.address().isEmpty()&&saved.smsSignature()!=null&&saved.equals(current);}
}
