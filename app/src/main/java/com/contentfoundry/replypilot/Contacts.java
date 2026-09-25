package com.contentfoundry.replypilot;

import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Local phone records only, queried on demand and never stored by Reply Pilot. */
final class Contacts {
    private static final String[] COLUMNS={Phone._ID,Phone.CONTACT_ID,Phone.DISPLAY_NAME_PRIMARY,
        Phone.NUMBER,Phone.NORMALIZED_NUMBER,Phone.TYPE,Phone.LABEL};
    private static final String ORDER=Phone.SORT_KEY_PRIMARY+" COLLATE LOCALIZED ASC, "+Phone.CONTACT_ID
        +" ASC, "+Phone.IS_SUPER_PRIMARY+" DESC, "+Phone.IS_PRIMARY+" DESC, "+Phone._ID+" ASC";

    static JSONObject denied()throws JSONException{
        return new JSONObject().put("allowed",false).put("contacts",new JSONArray()).put("hasMore",false);
    }
    static JSONObject search(Context context,String input)throws JSONException{
        if(!Messages.allowed(context,Manifest.permission.READ_CONTACTS))return denied();
        String query=ContactPolicy.query(input);
        Uri uri=query.isEmpty()?Phone.CONTENT_URI:Phone.CONTENT_FILTER_URI.buildUpon().appendPath(query)
            .appendQueryParameter(Phone.SEARCH_DISPLAY_NAME_KEY,"1")
            .appendQueryParameter(Phone.SEARCH_PHONE_NUMBER_KEY,"1")
            .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,Long.toString(ContactsContract.Directory.DEFAULT)).build();
        ContactPolicy.Page page=new ContactPolicy.Page();
        try(Cursor cursor=context.getContentResolver().query(uri,COLUMNS,null,null,ORDER)){
            while(cursor!=null&&cursor.moveToNext()){
                CharSequence label=Phone.getTypeLabel(context.getResources(),cursor.getInt(5),cursor.getString(6));
                if(page.add(cursor.getLong(0),cursor.getLong(1),cursor.getString(2),cursor.getString(3),cursor.getString(4),label==null?null:label.toString()))break;
            }
        }catch(SecurityException revoked){return denied();}
        catch(RuntimeException unavailable){
            if(!Messages.allowed(context,Manifest.permission.READ_CONTACTS))return denied();
            throw new IllegalStateException("Contacts could not be loaded. Please try again.");
        }
        // Discard the entire result if access changed while the provider was reading.
        if(!Messages.allowed(context,Manifest.permission.READ_CONTACTS))return denied();
        JSONArray rows=new JSONArray();
        for(ContactPolicy.Contact contact:page.contacts())rows.put(new JSONObject().put("id",contact.id())
            .put("name",contact.name()).put("number",contact.number()).put("label",contact.label()));
        ContactPhotos.annotate(context,rows,"number");
        return new JSONObject().put("allowed",true).put("contacts",rows).put("hasMore",page.hasMore());
    }
}
