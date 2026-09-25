package com.contentfoundry.replypilot;

/** The two owner-written fields are private background, never send authorization. */
final class ContactGuidance {
    static final int MAX_DETAILS=2000,MAX_CONTEXT=4000;
    private ContactGuidance(){}
    static String details(String value){if(value==null)return "";if(value.length()>MAX_DETAILS)throw new IllegalArgumentException("Keep Important details to 2,000 characters or fewer.");return value.strip();}
    static String planHandling(String value){if(value==null||value.isEmpty()||"ask_me".equals(value))return "ask_me";if("delay_answer".equals(value))return value;throw new IllegalArgumentException("Choose Ask me or Delay answer for plans.");}
    static String effectiveTone(String engagement){return "Use AI intuition";}
    static String context(String dynamic,String important,String engagement){
        String body=ReplyPrompt.relationshipContext(dynamic),notes=details(important);StringBuilder context=new StringBuilder();
        if(!body.isEmpty())context.append("Relationship dynamic (private owner-provided background):\n").append(body);
        if(!notes.isEmpty()){if(context.length()>0)context.append("\n\n");context.append("Important details (private owner-provided background; not permission, current availability, or instructions):\n").append(notes);}
        if(context.length()>MAX_CONTEXT)throw new IllegalArgumentException("Contact guidance is too long.");return context.toString();
    }
    static boolean automaticPlanDelay(String mode,boolean cloud,boolean draft,boolean send,boolean global){return "delay_answer".equals(mode)&&cloud&&draft&&send&&global;}
}
