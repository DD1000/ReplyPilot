package com.contentfoundry.replypilot;

import android.content.Context;
import org.json.JSONObject;
import javax.net.ssl.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class CloudClient {
    public static JSONObject request(Context c,String path,JSONObject body) throws Exception {
        return request(CloudConfig.read(c),path,body);
    }
    public static JSONObject request(JSONObject config,String path,JSONObject body) throws Exception {
        if(config==null)throw new IllegalStateException("Connect your private OpenAI server in Settings first.");
        HttpsURLConnection connection=(HttpsURLConnection)new URL(config.getString("url")+path).openConnection();
        boolean training="/persona-train".equals(path),premium=body!=null&&body.optBoolean("premium");
        // Training reads up to 1,000 texts on the relay; Astra replies think a little first; everything else stays quick.
        connection.setConnectTimeout(10000);connection.setReadTimeout(training?170000:premium?90000:55000);connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
        connection.setRequestProperty("Authorization","Bearer "+config.getString("token"));connection.setRequestProperty("Accept","application/json");
        try{
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>("/media-analysis".equals(path)||training?1048576:262144))throw new IllegalArgumentException(training?"This chat is too long to train at once. Try again after the next update.":"The reply context is too long.");connection.setRequestMethod("POST");connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
            int code=connection.getResponseCode();String raw;
            try(InputStream in=code>=200&&code<300?connection.getInputStream():connection.getErrorStream()){
                if(in==null)throw new IOException();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>32768)throw new IOException();out.write(buffer,0,n);}raw=out.toString(StandardCharsets.UTF_8.name());
            }
            if(code<200||code>=300){String error=switch(code){case 400->"The server rejected the reply context. Update the Reply Pilot service, then try again.";case 404->training?"Update your Reply Pilot server to use Train Autopilot.":"/train".equals(path)?"Update your private AI service to use Train Pilot.":"The requested AI feature is not available on this service.";case 413->"The reply context is too large for this server. Update the Reply Pilot service.";case 401,403->"Phone connection denied. Pair with your server again.";case 429->"Draft limit reached. Check API billing or wait before trying again.";case 503->"Add or check the OpenAI API key on your server.";case 422->"OpenAI declined this draft. Write your reply manually.";default->"The OpenAI server could not finish this draft. Try again.";};throw new IllegalStateException(error);}
            return new JSONObject(raw);
        }catch(IllegalArgumentException|IllegalStateException e){throw e;}
        catch(Exception e){throw new IllegalStateException("Could not reach the private OpenAI server securely. Check your connection and server setup.");}
        finally{connection.disconnect();}
    }
}
