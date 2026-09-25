package com.contentfoundry.replypilot;

import org.json.*;
import java.util.*;

/** Shared message conversion for the connected AI service. */
public final class ReplyAgent {
    private ReplyAgent(){}
    public static List<ReplyPrompt.Message> promptHistory(JSONArray history){
        List<ReplyPrompt.Message> out=new ArrayList<>();
        for(int i=0;i<history.length();i++){
            JSONObject message=history.optJSONObject(i);
            if(message!=null)out.add(new ReplyPrompt.Message(message.optLong("thread_id"),message.optInt("type"),message.optString("body")));
        }
        return out;
    }
}
