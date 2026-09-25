package com.contentfoundry.replypilot;
import android.Manifest;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Export only a user-selected provider part to a one-time read-only Android viewer. */
final class AttachmentViewer {
    record Prepared(Uri uri,String mime){}
    static Prepared prepare(Context c,long id)throws IOException{
        if(id<=0||!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Allow message access to open this attachment.");
        Uri part=Uri.parse("content://mms/part/"+id);String mime=null,text=null;
        try(Cursor row=c.getContentResolver().query(part,new String[]{"ct","text"},null,null,null)){
            if(row==null||!row.moveToFirst())throw new IllegalStateException("This attachment is no longer available.");
            mime=AttachmentFilePolicy.mime(row.getString(0));if(!row.isNull(1))text=row.getString(1);
        }
        if(mime==null)throw new IllegalStateException("This attachment format cannot be opened here.");
        File dir=new File(c.getFilesDir(),"mms-viewer");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Attachment unavailable.");
        File[] files=dir.listFiles();if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(int i=0;i<files.length;i++)if(files[i].isFile()&&(i<files.length-7||files[i].lastModified()<System.currentTimeMillis()-86_400_000L))files[i].delete();}
        File target=new File(dir,UUID.randomUUID()+".attachment");
        try{
            try(InputStream input="text/plain".equals(mime)&&text!=null?new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)):c.getContentResolver().openInputStream(part);OutputStream output=new FileOutputStream(target)){
                AttachmentFilePolicy.copy(input,output,AttachmentFilePolicy.MAX_BYTES);
            }
            if(!Messages.allowed(c,Manifest.permission.READ_SMS))throw new IllegalStateException("Message access changed. Open the attachment again.");
            return new Prepared(FileProvider.getUriForFile(c,c.getPackageName()+".files",target),mime);
        }catch(Exception error){target.delete();if(error instanceof IOException io)throw io;throw error;}
    }
}
