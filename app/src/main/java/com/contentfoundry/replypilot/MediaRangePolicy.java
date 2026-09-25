package com.contentfoundry.replypilot;

/** One HTTP byte range for local attachments; no caller-selected file paths. */
final class MediaRangePolicy {
    record Range(long start,long count,boolean partial){}
    static Range range(String header,long length){
        if(header==null||header.isBlank()||length<0)return new Range(0,length,false);
        if(length==0||!header.matches("bytes=[0-9]*-[0-9]*"))throw new IllegalArgumentException("Invalid media range");
        String[] parts=header.substring(6).split("-",-1);
        try{
            long start,end;
            if(parts[0].isEmpty()){
                long suffix=Long.parseLong(parts[1]);if(suffix<=0)throw new IllegalArgumentException();
                start=Math.max(0,length-suffix);end=length-1;
            }else{
                start=Long.parseLong(parts[0]);end=parts[1].isEmpty()?length-1:Math.min(length-1,Long.parseLong(parts[1]));
            }
            if(start<0||start>=length||end<start)throw new IllegalArgumentException();
            return new Range(start,end-start+1,true);
        }catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid media range");}
    }
    static boolean supported(String mime){return mime!=null&&mime.matches("(?:image|audio|video)/[A-Za-z0-9.+-]{1,64}")&&!mime.equalsIgnoreCase("image/svg+xml");}
}
