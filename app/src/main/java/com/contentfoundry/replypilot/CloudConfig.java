package com.contentfoundry.replypilot;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.net.URI;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Only a restricted relay credential is stored here. The OpenAI key never enters the app. */
public final class CloudConfig {
    private static final String ALIAS="reply-pilot-relay";
    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public static synchronized JSONObject read(Context c) throws Exception {
        String blob=c.getSharedPreferences("cloud",0).getString("credential","");if(blob.isEmpty())return null;
        JSONObject encoded=new JSONObject(blob);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(encoded.getString("iv"),Base64.NO_WRAP)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(encoded.getString("data"),Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8));
    }
    public static synchronized void save(Context c,String pairing) throws Exception {
        if(pairing.length()>4096)throw new IllegalArgumentException("The connection code is too long.");
        JSONObject input;try{input=new JSONObject(pairing);}catch(Exception e){throw new IllegalArgumentException("Paste the complete phone pairing code, not your OpenAI API key.");}String origin=input.getString("url"),token=input.getString("token");
        URI uri=new URI(origin);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getPath().isEmpty()||uri.getPath().equals("/"))||uri.getPort()==0||uri.getPort()>65535)throw new IllegalArgumentException("The private server address must be an HTTPS origin.");
        if(!token.matches("[a-zA-Z0-9_-]{32,128}")||token.startsWith("sk-"))throw new IllegalArgumentException("Use the phone connection code, not your OpenAI API key.");
        if(origin.endsWith("/"))origin=origin.substring(0,origin.length()-1);
        JSONObject config=new JSONObject().put("url",origin).put("token",token).put("revision",java.util.UUID.randomUUID().toString());
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String blob=new JSONObject().put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).put("data",Base64.encodeToString(cipher.doFinal(config.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP)).toString();
        if(!c.getSharedPreferences("cloud",0).edit().putString("credential",blob).commit())throw new IllegalStateException("The connection could not be saved.");
    }
    public static synchronized void clear(Context c){if(!c.getSharedPreferences("cloud",0).edit().clear().commit())throw new IllegalStateException("Could not save the disconnection. Revoke the phone token on your server.");}
    public static JSONObject publicState(Context c) {
        try{JSONObject config=read(c);return new JSONObject().put("configured",config!=null).put("url",config==null?"":config.getString("url"));}
        catch(Exception e){return new JSONObject();}
    }
}
