package com.contentfoundry.replypilot;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Network-independent URL, public-address and bounded markup rules. */
final class LinkPreviewPolicy {
    static final int MAX_URL=2048, MAX_HTML=262144, MAX_IMAGE_INPUT=1048576, MAX_IMAGE=153600;
    static final int MAX_REDIRECTS=3, MAX_EDGE=768, MAX_PIXELS=16000000;
    private static final Pattern URL=Pattern.compile("(?i)https?://[^\\s<>\"'\\p{Cntrl}\\p{Cf}]+"), ENCODED_CONTROL=Pattern.compile("(?i)%(?:0[0-9a-f]|1[0-9a-f]|7f|5c)");
    private LinkPreviewPolicy(){}

    static String openUrl(String raw){
        if(raw==null||raw.isEmpty()||raw.length()>MAX_URL||!raw.matches("(?is)^https?://.+")||ENCODED_CONTROL.matcher(raw).find())throw invalid();
        for(int i=0;i<raw.length();i++){char ch=raw.charAt(i);if(Character.isISOControl(ch)||Character.isWhitespace(ch)||Character.getType(ch)==Character.FORMAT||ch=='\\')throw invalid();}
        HttpUrl url;try{url=HttpUrl.get(raw);}catch(IllegalArgumentException error){throw invalid();}
        int end=raw.length();for(char c:new char[]{'/','?','#'}){int pos=raw.indexOf(c,raw.indexOf("://")+3);if(pos>=0)end=Math.min(end,pos);}
        String authority=raw.substring(raw.indexOf("://")+3,end);
        if(authority.isEmpty()||authority.endsWith(":")||authority.indexOf('@')>=0||!url.username().isEmpty()||!url.password().isEmpty()||!(url.scheme().equals("http")||url.scheme().equals("https"))||url.host().contains("%"))throw invalid();
        return url.toString();
    }
    static String previewUrl(String raw){
        HttpUrl url=HttpUrl.get(openUrl(raw));String host=url.host().toLowerCase(Locale.ROOT);
        if(!url.isHttps()||url.port()!=443||host.endsWith("."))throw invalid();
        if(host.indexOf(':')>=0||host.matches("[0-9.]+")){
            // OkHttp bypasses custom DNS for literal IPs, so check these here too.
            try{if(!publicAddress(InetAddress.getByName(host).getAddress()))throw invalid();}catch(java.net.UnknownHostException error){throw invalid();}
        }else{
            if(host.indexOf('.')<1)throw invalid();
            for(String suffix:List.of("localhost","local","lan","internal","home","test","invalid","example","onion"))if(host.equals(suffix)||host.endsWith("."+suffix))throw invalid();
        }
        return url.toString();
    }
    static String resolvePreview(String base,String reference){
        if(reference==null||reference.isEmpty()||reference.length()>MAX_URL||ENCODED_CONTROL.matcher(reference).find())throw invalid();
        for(int i=0;i<reference.length();i++){char ch=reference.charAt(i);if(ch=='\\'||Character.isISOControl(ch)||Character.isWhitespace(ch)||Character.getType(ch)==Character.FORMAT)throw invalid();}
        HttpUrl result=HttpUrl.get(previewUrl(base)).resolve(reference);if(result==null)throw invalid();return previewUrl(result.toString());
    }
    static List<String> links(String body){
        if(body==null||body.isEmpty())return List.of();String input=body.substring(0,Math.min(body.length(),32768));Matcher match=URL.matcher(input);LinkedHashSet<String> result=new LinkedHashSet<>();int checked=0;
        while(match.find()&&checked++<64&&result.size()<8){String value=match.group();if(value.length()>MAX_URL+8)continue;
            while(!value.isEmpty()) {char last=value.charAt(value.length()-1);if(".,!?;:".indexOf(last)>=0||(last==')'&&count(value,')')>count(value,'('))||(last==']'&&count(value,']')>count(value,'['))||(last=='}'&&count(value,'}')>count(value,'{')))value=value.substring(0,value.length()-1);else break;}
            try{result.add(openUrl(value));}catch(IllegalArgumentException ignored){}
        }return List.copyOf(result);
    }
    static boolean contains(String body,String url){try{return links(body).contains(openUrl(url));}catch(IllegalArgumentException ignored){return false;}}
    private static int count(String value,char ch){int n=0;for(int i=0;i<value.length();i++)if(value.charAt(i)==ch)n++;return n;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("This link cannot be opened safely.");}

    static boolean publicAddress(byte[] ip){
        if(ip==null)return false;
        if(ip.length==4){int a=ip[0]&255,b=ip[1]&255,c=ip[2]&255;
            return !(a==0||a==10||a==127||a>=224||(a==100&&b>=64&&b<=127)||(a==169&&b==254)||(a==172&&b>=16&&b<=31)||(a==192&&(b==0&&c==0||b==0&&c==2||b==88&&c==99||b==168||b==31&&c==196||b==52&&c==193||b==175&&c==48))||(a==198&&(b==18||b==19||b==51&&c==100))||(a==203&&b==0&&c==113));
        }
        if(ip.length==16){int a=ip[0]&255,b=ip[1]&255,c=ip[2]&255,d=ip[3]&255;
            // Global unicast only; exclude protocol/translation/documentation ranges.
            return (a&0xe0)==0x20&&!(a==0x20&&b==1&&(c<=1||c==0x0d&&d==0xb8))&&!(a==0x20&&b==2)&&!(a==0x3f&&b==0xff&&(c&0xf0)==0);
        }return false;
    }
    static List<InetAddress> publicAddresses(List<InetAddress> addresses){
        if(addresses==null||addresses.isEmpty()||addresses.size()>32)throw invalid();ArrayList<InetAddress> checked=new ArrayList<>();
        for(InetAddress address:addresses){if(address==null||!publicAddress(address.getAddress()))throw invalid();checked.add(address);}return List.copyOf(checked);
    }
    record Metadata(String title,String description,String siteName,String image,boolean available){}
    static Metadata metadata(String html,String finalUrl){
        if(html==null||html.length()>MAX_HTML)return empty();Document doc=Jsoup.parse(html,finalUrl);
        String title=first(doc,"og:title","twitter:title"),description=first(doc,"og:description","twitter:description","description"),site=first(doc,"og:site_name"),image=first(doc,"og:image:secure_url","og:image","og:image:url","twitter:image","twitter:image:src");
        if(title.isEmpty())title=doc.title();title=clean(title,160);description=clean(description,280);site=clean(site,80);
        String lower=title.toLowerCase(Locale.ROOT);boolean login=lower.matches(".*\\b(log[ -]?in|sign[ -]?in|access denied|just a moment|security check|checking your browser)\\b.*");
        if(login||title.isEmpty()&&description.isEmpty())return empty();String resolved="";
        try{resolved=resolvePreview(finalUrl,image);}catch(IllegalArgumentException ignored){}
        // Resolve against the fetched URL, deliberately ignoring document <base>.
        return new Metadata(title,description,site,resolved,true);
    }
    private static String first(Document doc,String... names){
        for(String name:names){int n=0;for(Element meta:doc.head().select("meta")){if(n++>=128)break;if(name.equalsIgnoreCase(meta.attr("property"))||name.equalsIgnoreCase(meta.attr("name"))){String value=meta.attr("content");if(!value.isBlank()&&value.length()<=4096)return value;}}}return "";
    }
    private static Metadata empty(){return new Metadata("","","","",false);}
    static String clean(String raw,int limit){if(raw==null)return "";StringBuilder out=new StringBuilder();boolean space=false;for(int i=0;i<raw.length()&&out.length()<limit;i++){char ch=raw.charAt(i);if(Character.isWhitespace(ch)){space=out.length()>0;continue;}if(Character.isISOControl(ch)||Character.getType(ch)==Character.FORMAT)continue;if(space&&out.length()<limit)out.append(' ');space=false;if(out.length()<limit)out.append(ch);}if(out.length()>0&&Character.isHighSurrogate(out.charAt(out.length()-1)))out.setLength(out.length()-1);return out.toString().trim();}
}
