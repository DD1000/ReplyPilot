package com.contentfoundry.replypilot;

import java.util.*;
import org.json.*;

/** Pure rules for per-contact Train Autopilot. No Android, network or storage here. */
final class PersonaPolicy {
    static final int MAX_MESSAGES=1000,MIN_MESSAGES=5,THIN_BELOW=100,RETRAIN_AFTER=1000,MAX_EXAMPLES=30;
    static final int WRITING_STYLE=2400,RELATIONSHIP=2400,CONTEXT=2400,AVOID=1200,EXAMPLE_INCOMING=600,EXAMPLE_REPLY=360,MESSAGE_TEXT=2000;
    static final String ME="me",THEM="them",AUTOPILOT="autopilot";
    record Turn(String speaker,String text){}
    record Example(String incoming,String reply){}
    record Persona(String writingStyle,String relationship,String context,String avoid,List<Example> examples){}
    private PersonaPolicy(){}

    /** Chronological provider rows become speaker/text turns. Earlier automatic sends are context, never the owner's voice. */
    static List<Turn> turns(List<String> speakers,List<String> texts){
        if(speakers.size()!=texts.size())throw new IllegalArgumentException("History rows do not match.");
        List<Turn> result=new ArrayList<>();
        for(int i=0;i<speakers.size();i++){
            String speaker=speakers.get(i),text=texts.get(i)==null?"":texts.get(i).replace("\0","").trim();
            if(!Set.of(ME,THEM,AUTOPILOT).contains(speaker)||text.isEmpty())continue;
            if(text.length()>MESSAGE_TEXT)text=text.substring(0,MESSAGE_TEXT);
            result.add(new Turn(speaker,text));
        }
        if(result.size()>MAX_MESSAGES)result=new ArrayList<>(result.subList(result.size()-MAX_MESSAGES,result.size()));
        return List.copyOf(result);
    }
    /** Training needs a little real conversation, including at least one message the owner wrote. */
    static String trainingBlock(List<Turn> turns){
        long owner=turns.stream().filter(t->ME.equals(t.speaker())).count();
        if(turns.size()<MIN_MESSAGES)return "This chat needs at least "+MIN_MESSAGES+" text messages before Autopilot can learn from it.";
        if(owner==0)return "Autopilot learns from texts you wrote. This chat has none it can read yet.";
        if(turns.stream().noneMatch(t->THEM.equals(t.speaker())))return "Autopilot needs at least one text from this person to learn how you reply.";
        return null;
    }
    static JSONObject request(String requestId,List<Turn> turns,long totalMessages)throws JSONException{
        JSONArray history=new JSONArray();for(Turn turn:turns)history.put(new JSONObject().put("speaker",turn.speaker()).put("text",turn.text()));
        return new JSONObject().put("requestId",requestId).put("history",history).put("totalMessages",Math.max(totalMessages,turns.size()));
    }
    /** The relay returns 1-based indexes; the phone rebuilds examples from its own copy of the texts. */
    static Persona result(JSONObject response,List<Turn> turns){
        JSONObject persona=response==null?null:response.optJSONObject("persona");JSONArray indexes=response==null?null:response.optJSONArray("exampleIndexes");
        if(persona==null||indexes==null)throw new IllegalStateException("No usable persona was returned. Try training again.");
        List<Long> chosen=new ArrayList<>();for(int i=0;i<indexes.length();i++){Object raw=indexes.opt(i);if(raw instanceof Integer||raw instanceof Long)chosen.add(((Number)raw).longValue());}
        return build(text(persona,"writingStyle"),text(persona,"relationship"),text(persona,"context"),text(persona,"avoid"),chosen,turns);
    }
    private static String text(JSONObject value,String key){Object raw=value.opt(key);if(!(raw instanceof String))throw new IllegalStateException("No usable persona was returned. Try training again.");return (String)raw;}
    /** Bounded persona fields plus unique, real owner replies chosen by index. */
    static Persona build(String writingStyle,String relationship,String context,String avoid,List<Long> indexes,List<Turn> turns){
        String style=clean(writingStyle,WRITING_STYLE);if(style.isEmpty())throw new IllegalStateException("No usable persona was returned. Try training again.");
        List<Example> examples=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for(Long n:indexes){
            if(examples.size()==MAX_EXAMPLES)break;
            if(n==null||n<1||n>turns.size()||!seen.add(n))continue;
            Example example=example(turns,(int)(n-1));if(example!=null)examples.add(example);
        }
        return new Persona(style,clean(relationship,RELATIONSHIP),clean(context,CONTEXT),clean(avoid,AVOID),List.copyOf(examples));
    }
    private static String clean(String value,int limit){String text=value==null?"":value.replace("\0","").trim();return text.length()>limit?text.substring(0,limit).trim():text;}
    /** One real owner reply, with the contact's messages just before it as context. */
    static Example example(List<Turn> turns,int index){
        if(index<0||index>=turns.size())return null;Turn reply=turns.get(index);
        if(!ME.equals(reply.speaker())||reply.text().length()>EXAMPLE_REPLY)return null;
        Deque<String> incoming=new ArrayDeque<>();int length=0;
        for(int i=index-1;i>=0&&THEM.equals(turns.get(i).speaker());i--){
            String text=turns.get(i).text();if(incoming.isEmpty()&&text.length()>EXAMPLE_INCOMING){incoming.addFirst(text.substring(text.length()-EXAMPLE_INCOMING));break;}if(length+text.length()+(incoming.isEmpty()?0:1)>EXAMPLE_INCOMING)break;
            incoming.addFirst(text);length+=text.length()+(incoming.size()>1?1:0);
        }
        return new Example(String.join("\n",incoming),reply.text());
    }
    static JSONObject json(Persona persona)throws JSONException{
        JSONArray examples=new JSONArray();for(Example e:persona.examples())examples.put(new JSONObject().put("incoming",e.incoming()).put("reply",e.reply()));
        return new JSONObject().put("writingStyle",persona.writingStyle()).put("relationship",persona.relationship()).put("context",persona.context()).put("avoid",persona.avoid()).put("examples",examples);
    }
    static Persona parse(JSONObject saved){
        if(saved==null)throw new IllegalStateException("The saved persona could not be read. Train Autopilot again.");
        JSONArray rows=saved.optJSONArray("examples");List<Example> examples=new ArrayList<>();
        if(rows!=null)for(int i=0;i<rows.length()&&examples.size()<MAX_EXAMPLES;i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;String reply=row.optString("reply"),incoming=row.optString("incoming");if(!reply.isEmpty()&&reply.length()<=EXAMPLE_REPLY&&incoming.length()<=EXAMPLE_INCOMING)examples.add(new Example(incoming,reply));}
        return restore(saved.optString("writingStyle"),saved.optString("relationship"),saved.optString("context"),saved.optString("avoid"),examples);
    }
    static Persona restore(String writingStyle,String relationship,String context,String avoid,List<Example> examples){
        if(writingStyle==null||writingStyle.isEmpty()||writingStyle.length()>WRITING_STYLE)throw new IllegalStateException("The saved persona could not be read. Train Autopilot again.");
        return new Persona(writingStyle,clean(relationship,RELATIONSHIP),clean(context,CONTEXT),clean(avoid,AVOID),List.copyOf(examples.subList(0,Math.min(examples.size(),MAX_EXAMPLES))));
    }
    /** What each reply request carries. The phone keeps the persona; the relay forgets it. */
    static JSONObject reply(Persona persona,long trainedMessages)throws JSONException{return json(persona).put("trainedMessages",Math.max(0,trainedMessages));}
    static boolean suggestRetrain(long newMessages){return newMessages>=RETRAIN_AFTER;}
    static boolean thin(long trainedMessages){return trainedMessages<THIN_BELOW;}
}
