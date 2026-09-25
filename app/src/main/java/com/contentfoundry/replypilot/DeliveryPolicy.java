package com.contentfoundry.replypilot;

import java.util.Map;
import java.util.Objects;

/** Delivery is separate from carrier acceptance and can never authorize a retry. */
final class DeliveryPolicy {
    record Report(String state,int rawStatus,String format){}
    static Report unknown(){return new Report("unknown",-1,"");}
    static Report report(String format,boolean statusReport,int raw){
        if(!statusReport)return unknown();
        String state="unknown";
        if("3gpp".equals(format)){
            if(raw==0)state="delivered";
            else if(raw>=0x20&&raw<=0x3f)state="pending";
            else if(raw>=0x40&&raw<=0x7f)state="failed";
        }else if("3gpp2".equals(format)){
            // SmsMessage.getStatus() places CDMA's error class/status in bits 25..16.
            if((raw&0xfc00ffff)==0){
                int errorClass=(raw>>>24)&3,status=(raw>>>16)&0xff;
                if(errorClass==0&&status==2)state="delivered";
                else if(errorClass==2||errorClass==0&&status==0)state="pending";
                else if(errorClass==3||errorClass==0&&status==3)state="failed";
            }
        }else return unknown();
        return new Report(state,raw,format);
    }
    static boolean validPart(int parts,int part){return parts>0&&part>=0&&part<parts;}
    static String merge(String previous,String incoming){
        // A late temporary/invalid report cannot undo terminal evidence. A real
        // received report may resolve an earlier failure, even after an app restart.
        if("delivered".equals(previous)||"delivered".equals(incoming))return "delivered";
        if("failed".equals(previous)||"failed".equals(incoming))return "failed";
        if("pending".equals(previous)||"pending".equals(incoming))return "pending";
        return "unknown";
    }
    static String aggregate(int parts,Map<Integer,String> reports,boolean submissionFailed){
        if(parts<=0)return "none";
        int delivered=0;boolean unknown=false;
        for(int part=0;part<parts;part++){
            String report=reports.get(part);
            if("failed".equals(report))return "failed";
            if("delivered".equals(report))delivered++;
            else if(report!=null&&!"pending".equals(report))unknown=true;
        }
        if(delivered==parts)return "delivered";
        return unknown||submissionFailed?"unknown":"pending";
    }
    static int providerStatus(String state){
        return switch(state){case "delivered"->0;case "pending"->32;case "failed"->64;default->-1;};
    }
    static String fromProvider(int type,int status){
        if(!outgoing(type)||status==-1)return "none";
        if(status==0)return type==5?"unknown":"delivered";
        if(status>=32&&status<64)return "pending";
        if(status>=64&&status<=127)return "failed";
        return "unknown";
    }
    static boolean outgoing(int type){return type==2||type==4||type==5||type==6;}
    /** All parts delivered proves submission even if Android lost its sent callback. */
    static String settledSendStatus(String current,String delivery){
        return "delivered".equals(delivery)&&("sending".equals(current)||"unknown".equals(current)||"failed".equals(current)||"sent".equals(current))?"sent":current;
    }
    static String receiptStatus(boolean failure,String delivery){return "delivered".equals(delivery)?"sent":failure?"failed":"sent";}
    static String receiptNote(boolean sendingFailed,String delivery){
        if("delivered".equals(delivery))return "Delivery confirmed by your carrier.";
        return sendingFailed?"The carrier reported a failure. Some parts may have sent. Check before retrying."
            :"Accepted by your carrier. Delivery to the recipient is not confirmed.";
    }
    static boolean sameMessage(long thread,long date,String address,String body,long actualThread,long actualDate,String actualAddress,String actualBody){
        return thread>0&&date>0&&address!=null&&body!=null&&thread==actualThread&&date==actualDate
            &&Objects.equals(address,actualAddress)&&Objects.equals(body,actualBody);
    }
}
