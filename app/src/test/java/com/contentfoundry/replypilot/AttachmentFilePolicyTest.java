package com.contentfoundry.replypilot;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class AttachmentFilePolicyTest {
    @Test public void passiveTypesOnly(){for(String type:new String[]{"image/jpeg","video/mp4","audio/ogg","text/vcard","text/x-vcard","text/plain","application/pdf"})assertEquals(type,AttachmentFilePolicy.mime(type));for(String type:new String[]{"image/svg+xml","text/html","application/javascript","application/vnd.android.package-archive","file:///private","image/jpeg\r\nx: y","image/jpeg;foo=bar"})assertNull(AttachmentFilePolicy.mime(type));assertNull(AttachmentFilePolicy.mime(null));}
    @Test public void exactByteLimitAndUnchangedBytes()throws Exception{byte[] bytes={0,1,2,(byte)255};ByteArrayOutputStream out=new ByteArrayOutputStream();assertEquals(4,AttachmentFilePolicy.copy(new ByteArrayInputStream(bytes),out,4));assertArrayEquals(bytes,out.toByteArray());}
    @Test public void overLimitNeverWritesOversizedChunk(){ByteArrayOutputStream out=new ByteArrayOutputStream();assertThrows(IOException.class,()->AttachmentFilePolicy.copy(new ByteArrayInputStream(new byte[9]),out,8));assertTrue(out.size()<=8);}
    @Test public void emptyOrMissingDataFails(){assertThrows(IOException.class,()->AttachmentFilePolicy.copy(new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream(),8));assertThrows(IllegalArgumentException.class,()->AttachmentFilePolicy.copy(null,new ByteArrayOutputStream(),8));}
}
