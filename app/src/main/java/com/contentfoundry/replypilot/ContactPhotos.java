package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Read-only local contact thumbnails. Never opens a supplied provider URI or network URL. */
final class ContactPhotos {
    private static final ContactPhotoPolicy.Cache CACHE=new ContactPhotoPolicy.Cache();
    private static final Semaphore DECODERS=new Semaphore(2,true);
    private static final Map<String,String> HEADERS=Map.of("Cache-Control","no-store","X-Content-Type-Options","nosniff");
    private ContactPhotos(){}
    static void invalidate(){CACHE.clear();}
    static long revision(){return CACHE.revision();}
    private static boolean allowed(Context c){
        return ContactPhotoPolicy.allowed(Messages.allowed(c,Manifest.permission.READ_CONTACTS),Messages.allowed(c,Manifest.permission.READ_SMS),Messages.role(c),PilotApp.foreground);
    }
    static String url(Context c,String address){return allowed(c)?ContactPhotoPolicy.path(address):"";}
    static JSONArray annotate(Context c,JSONArray rows,String addressKey)throws JSONException{
        boolean permitted=allowed(c);
        if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)row.put("photo",permitted?ContactPhotoPolicy.path(row.optString(addressKey)):"");}
        // One check per batch keeps a large inbox independent of binder latency.
        if(permitted&&!allowed(c)&&rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null)row.put("photo","");}
        return rows;
    }
    static WebResourceResponse open(Context c,Uri uri){
        // Check the original encoded path: encoded separators, double encoding,
        // alternative ports, queries and arbitrary content/file URIs never resolve.
        if(uri==null||!"https".equals(uri.getScheme())||!"app.replypilot.local".equals(uri.getEncodedAuthority()))return missing();
        String address=ContactPhotoPolicy.address(uri.getEncodedPath(),uri.getEncodedQuery(),uri.getEncodedFragment());
        if(address==null)return missing();
        if(!allowed(c)){invalidate();return missing();}
        long expected=CACHE.revision();byte[] image=CACHE.get(address);
        if(image==null){
            boolean acquired=false;
            try{
                DECODERS.acquire();acquired=true;
                if(!allowed(c)||!CACHE.current(expected))return missing();
                image=CACHE.get(address);
                if(image==null){image=load(c,address);if(image==null)image=new byte[0];if(!allowed(c)||!CACHE.put(address,expected,image))return missing();}
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();return missing();}
            catch(IOException|RuntimeException unavailable){return missing();}
            finally{if(acquired)DECODERS.release();}
        }
        if(!allowed(c)||!CACHE.current(expected)||image.length==0)return missing();
        return new WebResourceResponse("image/png",null,200,"OK",HEADERS,new GuardedStream(c,image,expected));
    }
    private static byte[] load(Context c,String address)throws IOException{
        Uri lookup=ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath(address)
            .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,Long.toString(ContactsContract.Directory.DEFAULT)).build();
        long contact=0;
        try(Cursor cursor=c.getContentResolver().query(lookup,new String[]{ContactsContract.PhoneLookup._ID,ContactsContract.PhoneLookup.NUMBER},null,null,null)){
            int checked=0;while(cursor!=null&&checked++<16&&cursor.moveToNext()){
                String candidate=ContactPolicy.number(cursor.getString(1));long id=cursor.getLong(0);
                if(id>0&&candidate!=null&&PhoneNumberUtils.compare(address,candidate)){contact=id;break;}
            }
        }
        if(contact<=0||!allowed(c))return null;
        Uri local=ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,contact);
        byte[] input;
        // Thumbnail mode reads the Android Contacts Provider's stored photo only.
        try(InputStream stream=ContactsContract.Contacts.openContactPhotoInputStream(c.getContentResolver(),local,false)){
            if(stream==null)return null;ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
            while((count=stream.read(buffer))!=-1){if(bytes.size()+count>ContactPhotoPolicy.MAX_INPUT||!allowed(c))return null;bytes.write(buffer,0,count);}input=bytes.toByteArray();
        }
        if(input.length==0)return null;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(input,0,input.length,bounds);
        int sample=ContactPhotoPolicy.sample(bounds.outWidth,bounds.outHeight);if(sample==0)return null;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap decoded=null,scaled=null;
        try{
            decoded=BitmapFactory.decodeByteArray(input,0,input.length,options);if(decoded==null||!ContactPhotoPolicy.dimensions(decoded.getWidth(),decoded.getHeight()))return null;
            ContactPhotoPolicy.Size size=ContactPhotoPolicy.fit(decoded.getWidth(),decoded.getHeight());
            scaled=Bitmap.createScaledBitmap(decoded,size.width(),size.height(),true);ByteArrayOutputStream output=new ByteArrayOutputStream();
            if(!scaled.compress(Bitmap.CompressFormat.PNG,100,output)||output.size()>ContactPhotoPolicy.MAX_IMAGE)return null;
            return output.toByteArray();
        }finally{if(scaled!=null&&scaled!=decoded)scaled.recycle();if(decoded!=null)decoded.recycle();}
    }
    /** Access can change after the response is built but before WebView consumes it. */
    private static final class GuardedStream extends ByteArrayInputStream {
        private final Context context;private final long revision;
        GuardedStream(Context c,byte[] bytes,long revision){super(bytes);context=c.getApplicationContext();this.revision=revision;}
        private boolean current(){return allowed(context)&&CACHE.current(revision);}
        @Override public synchronized int read(){return current()?super.read():-1;}
        @Override public synchronized int read(byte[] bytes,int offset,int length){return current()?super.read(bytes,offset,length):-1;}
        @Override public synchronized long skip(long count){return current()?super.skip(count):0;}
        @Override public synchronized int available(){return current()?super.available():0;}
    }
    private static WebResourceResponse missing(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",HEADERS,new ByteArrayInputStream(new byte[0]));}
}
