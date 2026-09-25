package com.contentfoundry.replypilot;

import android.content.SharedPreferences;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Apply the durable theme before the WebView's first paint, independently of SMS work. */
final class ThemePreferences {
    static final ExecutorService EXECUTOR=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),work->{Thread t=new Thread(work,"reply-pilot-theme");t.setDaemon(true);return t;});
    private static final Object LOCK=new Object();
    private static final Map<String,String> COLORS=Map.of("midnight","#191F2C","forest","#192923","ocean","#192731","lavender","#282433","rose","#30232A","sunset","#2E261F","slate","#20252B","mocha","#2B2421","mint","#1B2A29","plum","#2E2430");
    private ThemePreferences(){}
    static String theme(String value){return value!=null&&COLORS.containsKey(value)?value:"midnight";}
    static String background(String value){return COLORS.get(theme(value));}
    static String launchHtml(String html,String value){
        String selected=theme(value);
        return html.replace("data-theme=\"midnight\"","data-theme=\""+selected+"\"")
            .replace("<meta name=\"theme-color\" content=\"#191F2C\">","<meta name=\"theme-color\" content=\""+background(selected)+"\">");
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    static String save(SharedPreferences preferences,String requested){
        if(requested==null||!COLORS.containsKey(requested))throw new IllegalArgumentException("Choose one of the available themes.");
        synchronized(LOCK){
            String previous=theme(preferences.getString("theme","midnight"));
            if(!preferences.edit().putString("theme",requested).commit()){
                try{preferences.edit().putString("theme",previous).commit();}catch(RuntimeException ignored){/* Keep the original save error. */}
                throw new IllegalStateException("The theme could not be saved. Please try again.");
            }
        }
        return requested;
    }
}
