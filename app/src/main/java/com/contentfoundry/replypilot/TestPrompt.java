package com.contentfoundry.replypilot;

import java.util.ArrayList;
import java.util.List;

/** A test request contains only what the user typed, with no real conversation lookup. */
public final class TestPrompt {
    public static ReplyPrompt.Prepared prepare(String message,String relationship,String examples,String tone) {
        if(message==null||message.isBlank())throw new IllegalArgumentException("Enter a test message first.");
        if(message.length()>360)throw new IllegalArgumentException("Keep the test message within 360 characters.");
        if(examples==null)examples="";
        if(examples.length()>1400)throw new IllegalArgumentException("Use up to six short examples of your texting style.");
        List<ReplyPrompt.Message> history=new ArrayList<>();
        for(String line:examples.split("\\R")){
            if(line.isBlank())continue;
            if(history.size()>=6||line.length()>220)throw new IllegalArgumentException("Use up to six examples, one per line, each within 220 characters.");
            history.add(new ReplyPrompt.Message(1,2,line.strip()));
        }
        history.add(new ReplyPrompt.Message(1,1,message.strip()));
        return ReplyPrompt.prepare(1,history,tone==null?"Natural":tone,true,ReplyPrompt.relationshipContext(relationship));
    }
}
