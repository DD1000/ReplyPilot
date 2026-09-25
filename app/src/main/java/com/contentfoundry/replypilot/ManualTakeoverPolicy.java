package com.contentfoundry.replypilot;

/** Outgoing rows, deletions, and edits are not proof of a new incoming message. */
final class ManualTakeoverPolicy {
    static final String WAITING="You took over this conversation. Pilot is waiting for a new incoming message.";
    static boolean released(long savedSms,long savedMms,long currentSms,long currentMms,long savedReceipt,long currentReceipt,boolean receiptBound){return savedSms>=0&&savedMms>=0&&currentSms>=0&&currentMms>=0&&(currentMms>savedMms||currentSms>savedSms&&savedReceipt>=0&&currentReceipt>savedReceipt&&receiptBound);}
    static boolean unchanged(long started,long current){return started>=0&&started==current;}
    static boolean bindOlderMms(long receipt,long claimedReceipt,long savedId,long incomingId){return savedId>=0&&incomingId>savedId&&receipt<=claimedReceipt;}
    static boolean requestId(String id){return id!=null&&id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");}
    static boolean sameRequest(long thread,String address,String body,int sub,long priorThread,String priorAddress,String priorBody,int priorSub){return thread==priorThread&&sub==priorSub&&java.util.Objects.equals(address,priorAddress)&&java.util.Objects.equals(body,priorBody);}
}
