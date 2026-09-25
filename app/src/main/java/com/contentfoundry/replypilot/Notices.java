package com.contentfoundry.replypilot;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import java.util.Arrays;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Notices {
    private static final String LEGACY_MESSAGES="replies";
    private static final String LEGACY_DRAFTS="draft_timers";
    private static final String BUNDLED_MESSAGES="messages_tinyblast_v1";
    private static final String BUNDLED_DRAFTS="draft_timers_tinyblast_v1";
    private static final String TIMER_TAG="replypilot.timer.";
    private static final String DRAFT_TAG="replypilot.draft.";
    private static final String INSTANT_TAG="replypilot.instant.";
    private static final String INSTANT_STATE="replypilot.instant.state";
    private static final int TIMER_ID=1;

    public static void channels(Context c) {
        try{messageChannel(c);draftChannel(c);}catch(RuntimeException unavailable){/* Posting checks fail closed if channel creation is unavailable. */}
    }

    public static String draftChannel(Context c) {
        return channel(c,LEGACY_DRAFTS,BUNDLED_DRAFTS,"Reply drafts & timers","Suggested replies and scheduled-send timers. Default sound: Tiny Blast. You control sound and lock-screen appearance.");
    }

    public static String messageChannel(Context c) {
        return channel(c,LEGACY_MESSAGES,BUNDLED_MESSAGES,"Messages & replies","Incoming messages and reply status. Default sound: Tiny Blast. You control notification behavior.");
    }

    private static AudioAttributes audioAttributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    }

    private static Uri defaultSound(Context c) {
        // Keep an explicit resource reference, but store names in the persistent URI:
        // numeric resource IDs may change when the app is updated.
        int sound=R.raw.tiny_blast;
        return Uri.parse("android.resource://"+c.getPackageName()+"/"+c.getResources().getResourceTypeName(sound)+"/"+c.getResources().getResourceEntryName(sound));
    }

    private static NotificationChannel originalChannel(String id,String name) {
        NotificationChannel channel=new NotificationChannel(id,name,NotificationManager.IMPORTANCE_HIGH);
        if(LEGACY_DRAFTS.equals(id))channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,audioAttributes());
        return channel;
    }

    private static boolean unchanged(NotificationChannel old,NotificationChannel original) {
        if(old.hasUserSetSound()||old.hasUserSetImportance())return false;
        if(!Objects.equals(old.getSound(),original.getSound())||old.getImportance()!=original.getImportance())return false;
        if(!Objects.equals(old.getAudioAttributes(),original.getAudioAttributes())||!Objects.equals(old.getGroup(),original.getGroup()))return false;
        if(old.getLockscreenVisibility()!=original.getLockscreenVisibility()||old.canBypassDnd()!=original.canBypassDnd()||old.canBubble()!=original.canBubble())return false;
        if(old.canShowBadge()!=original.canShowBadge()||old.shouldShowLights()!=original.shouldShowLights()||old.getLightColor()!=original.getLightColor())return false;
        if(old.shouldVibrate()!=original.shouldVibrate()||!Arrays.equals(old.getVibrationPattern(),original.getVibrationPattern()))return false;
        if(old.isConversation()||old.isDemoted()||old.isImportantConversation())return false;
        if(Build.VERSION.SDK_INT>=33&&old.isBlockable()!=original.isBlockable())return false;
        return Build.VERSION.SDK_INT<35||Objects.equals(old.getVibrationEffect(),original.getVibrationEffect());
    }

    private static synchronized String channel(Context c,String legacy,String bundled,String name,String description) {
        NotificationManager n=c.getSystemService(NotificationManager.class);
        if(n==null)throw new IllegalStateException("Notification settings are unavailable.");
        SharedPreferences preferences=c.getSharedPreferences("notification_channels",0);
        String saved=preferences.getString(legacy,null);
        NotificationChannel old=n.getNotificationChannel(legacy),replacement=n.getNotificationChannel(bundled);
        String selected=ChannelMigrationPolicy.select(saved,legacy,bundled,replacement!=null,old!=null,old!=null&&unchanged(old,originalChannel(legacy,name)));
        if(n.getNotificationChannel(selected)==null){
            NotificationChannel created=originalChannel(selected,name);
            created.setDescription(description);
            if(bundled.equals(selected))created.setSound(defaultSound(c),audioAttributes());
            // Recreating a previously deleted ID restores Android's original settings.
            n.createNotificationChannel(created);
            if(n.getNotificationChannel(selected)==null)throw new IllegalStateException("Notification settings could not be created.");
        }
        if(!selected.equals(saved)&&!preferences.edit().putString(legacy,selected).commit())throw new IllegalStateException("Notification settings could not be saved.");
        return selected;
    }

    private static boolean allowed(Context c,NotificationManager n,String channelId) {
        if(n==null||(Build.VERSION.SDK_INT>=33&&!Messages.allowed(c,Manifest.permission.POST_NOTIFICATIONS))||!n.areNotificationsEnabled())return false;
        NotificationChannel channel=n.getNotificationChannel(channelId);
        if(channel==null||channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
        if(channel.getGroup()!=null){NotificationChannelGroup group=n.getNotificationChannelGroup(channel.getGroup());if(group!=null&&group.isBlocked())return false;}
        return true;
    }

    /** True means Android permits posting, not that sound, DND, or lock-screen settings are overridden. */
    public static boolean canAlert(Context c) {
        try{return allowed(c,c.getSystemService(NotificationManager.class),draftChannel(c));}catch(RuntimeException unavailable){return false;}
    }

    private static PendingIntent open(Context c,long thread) {
        Intent intent=new Intent(c,MainActivity.class).setData(Uri.parse("replypilot://conversation/"+thread)).putExtra("thread",thread).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static Notification redacted(Context c,String channel,String text) {
        return new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_pilot).setContentTitle("Reply Pilot").setContentText(text).setVisibility(Notification.VISIBILITY_PUBLIC).build();
    }

    private static Notification.Builder privacy(Context c,Notification.Builder builder,String channel) {
        boolean previews=c.getSharedPreferences("settings",0).getBoolean("lockScreenPreviews",true);
        return builder.setVisibility(previews?Notification.VISIBILITY_PUBLIC:Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(previews?null:redacted(c,channel,"Open to view your message and reply"));
    }

    private static void section(SpannableStringBuilder text,String label,String value) {
        if(text.length()>0)text.append("\n\n");
        int start=text.length();text.append(label);
        text.setSpan(new StyleSpan(Typeface.BOLD),start,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append("\n").append(value==null||value.isBlank()?"Open the conversation to view this message.":value);
    }

    public static boolean attention(Context c,long thread,long base,String reason){return false;}
    public static boolean attention(Context c,long thread,long base,String reason,String title,String explanation){return false;}
    /** Durable job-bound attention is posted only after a carrier submission actually began. */
    static void chatAttention(Context c,long id){synchronized(PilotApp.SEND_LOCK){
        try{
            Store db=Store.get(c);JSONObject job=db.job(id),attention=db.query("SELECT * FROM autopilot_attention WHERE job=?",new String[]{Long.toString(id)}).optJSONObject(0);
            if(job==null||attention==null||attention.optInt("notified")!=0||attention.optInt("submitted")!=1||!AutopilotPolicy.acceptedState(job.optString("status"),!job.optString("uri").isEmpty())||!canAlert(c))return;
            String reason=attention.optString("reason"),channel=draftChannel(c);long thread=job.optLong("thread");
            String explanation=switch(reason){case "plans" -> "They are making plans. Pilot sent a deferral so you can decide.";case "personal_info" -> "They asked something only you can answer. Pilot sent a deferral.";case "sensitive" -> "This conversation needs your judgment. Pilot sent a brief acknowledgment.";case "model_unavailable" -> "AI was unavailable. Pilot sent a brief acknowledgment.";default -> "Pilot sent a brief acknowledgment. Open the chat to follow up.";};
            SpannableStringBuilder text=new SpannableStringBuilder();section(text,"Chat needs attention",explanation);section(text,"Reply",job.optString("body"));
            Notification.Builder builder=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_pilot).setContentTitle("Chat needs attention").setContentText(explanation).setContentIntent(open(c,thread)).setCategory(Notification.CATEGORY_MESSAGE).setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true).setOnlyAlertOnce(true).setAllowSystemGeneratedContextualActions(false);
            c.getSystemService(NotificationManager.class).notify("replypilot.attention."+id,1,privacy(c,builder,channel).build());
            ContentValues done=new ContentValues();done.put("notified",1);db.getWritableDatabase().update("autopilot_attention",done,"job=? AND notified=0",new String[]{Long.toString(id)});
        }catch(Exception unavailable){/* Receipt/recovery can retry posting the same notification id. */}
    }}

    /** Posts once when the draft is created. The system renders the countdown without repeat alerts. */
    public static boolean draft(Context c,int id,String sender,String original,String reply,long thread,long jobId,long due) {
        return draft(c,id,sender,original,reply,thread,jobId,due,false);
    }

    public static boolean draft(Context c,int id,String sender,String original,String reply,long thread,long jobId,long due,boolean instant) {
        if(instant&&jobId<=0)return false;
        if(!canAlert(c))return false;
        try{
            String channel=draftChannel(c);
            boolean scheduled=!instant&&jobId>0&&due>0;
            String title=sender==null||sender.isBlank()?"Conversation":sender;
            String status=instant?"Sending automatically":scheduled?"Sends "+DateUtils.formatDateTime(c,due,DateUtils.FORMAT_SHOW_TIME|(DateUtils.isToday(due)?0:DateUtils.FORMAT_SHOW_DATE|DateUtils.FORMAT_ABBREV_MONTH)):"Reply ready";
            SpannableStringBuilder text=new SpannableStringBuilder();
            section(text,"Incoming",original);section(text,"Reply",reply);
            Notification.Builder builder=new Notification.Builder(c,channel)
                .setSmallIcon(R.drawable.ic_pilot).setContentTitle(title).setContentText("Reply: "+(reply==null?"":reply))
                .setSubText(status).setContentIntent(open(c,thread)).setCategory(Notification.CATEGORY_MESSAGE)
                .setStyle(new Notification.BigTextStyle().setBigContentTitle(title).bigText(text).setSummaryText(status))
                .setAutoCancel(!scheduled).setOnlyAlertOnce(true).setAllowSystemGeneratedContextualActions(false);
            if(instant){Bundle extras=new Bundle();extras.putString(INSTANT_STATE,"awaiting_alert");builder.addExtras(extras);}
            if(scheduled){
                Intent cancel=new Intent(c,CancelSendReceiver.class).setAction(CancelSendReceiver.ACTION_CANCEL)
                    .setData(Uri.parse("replypilot://cancel-timer/"+jobId)).putExtra("id",jobId);
                PendingIntent action=PendingIntent.getBroadcast(c,0,cancel,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                builder.setWhen(due).setUsesChronometer(true).setChronometerCountDown(true)
                    .addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,android.R.drawable.ic_menu_close_clear_cancel),"Cancel timer",action).build());
            }
            NotificationManager n=c.getSystemService(NotificationManager.class);
            n.notify(instant?INSTANT_TAG+jobId:scheduled?TIMER_TAG+jobId:DRAFT_TAG+thread,instant||scheduled?TIMER_ID:id,privacy(c,builder,channel).build());
            // Keep the incoming alert until the combined original/reply notice is posted.
            try{if(scheduled||instant)n.cancel(DRAFT_TAG+thread,id);n.cancel((int)thread);}catch(RuntimeException ignored){}
            return true;
        }catch(RuntimeException unavailable){return false;}
    }

    public static void cancelScheduled(Context c,long jobId) {
        NotificationManager n=c.getSystemService(NotificationManager.class);
        if(n!=null)try{n.cancel(TIMER_TAG+jobId,TIMER_ID);}catch(RuntimeException ignored){}
    }
    public static void cancelSilentReply(Context c,long jobId){
        NotificationManager manager=c.getSystemService(NotificationManager.class);
        if(manager!=null)try{manager.cancel(TIMER_TAG+jobId,TIMER_ID);manager.cancel(INSTANT_TAG+jobId,TIMER_ID);}catch(RuntimeException ignored){}
    }

    /** Keep the original and reply visible after an instant send, without sounding again. */
    public static boolean instantOutcome(Context c,long jobId,String status,String note) {
        try{
            NotificationManager n=c.getSystemService(NotificationManager.class);if(n==null)return false;
            for(StatusBarNotification active:n.getActiveNotifications()){
                if(active.getId()==TIMER_ID&&(INSTANT_TAG+jobId).equals(active.getTag())){
                    updateInstant(c,n,active,status);return true;
                }
            }
        }catch(RuntimeException ignored){}
        return false;
    }

    private static void updateInstant(Context c,NotificationManager n,StatusBarNotification active,String status) {
        Notification previous=active.getNotification();
        if(status.equals(previous.extras.getString(INSTANT_STATE)))return;
        String label=switch(status){
            case "awaiting_alert","scheduled","sending" -> "Sending automatically";
            case "sent" -> "Accepted by your carrier";
            case "delivered" -> "Delivered";
            case "failed" -> "Reply failed · open to review";
            case "paused" -> "Reply paused · open to review";
            case "cancelled" -> "Reply cancelled";
            default -> "Open to check reply status";
        };
        Bundle extras=new Bundle();extras.putString(INSTANT_STATE,status);
        Notification.Builder builder=Notification.Builder.recoverBuilder(c,previous).addExtras(extras)
            .setSubText(label).setOnlyAlertOnce(true).setAutoCancel(true)
            .setStyle(new Notification.BigTextStyle().setBigContentTitle(previous.extras.getCharSequence(Notification.EXTRA_TITLE))
                .bigText(previous.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)).setSummaryText(label));
        n.notify(active.getTag(),active.getId(),privacy(c,builder,previous.getChannelId()).build());
    }

    /** Remove ended timers and apply privacy only when changed; never recreate dismissed notices. */
    public static void refreshScheduled(Context c) {
        try{
            NotificationManager n=c.getSystemService(NotificationManager.class);if(n==null)return;
            synchronized(PilotApp.SEND_LOCK){
                int visibility=c.getSharedPreferences("settings",0).getBoolean("lockScreenPreviews",true)?Notification.VISIBILITY_PUBLIC:Notification.VISIBILITY_PRIVATE;
                for(StatusBarNotification active:n.getActiveNotifications()){
                    String channel=active.getNotification().getChannelId();
                    if(!LEGACY_DRAFTS.equals(channel)&&!BUNDLED_DRAFTS.equals(channel))continue;
                    String tag=active.getTag();
                    if(tag!=null&&tag.startsWith(INSTANT_TAG)){
                        long id;
                        try{id=Long.parseLong(tag.substring(INSTANT_TAG.length()));}catch(NumberFormatException invalid){n.cancel(tag,active.getId());continue;}
                        org.json.JSONObject job=Store.get(c).job(id);
                        if(job!=null&&!job.optString("status").equals(active.getNotification().extras.getString(INSTANT_STATE))){
                            updateInstant(c,n,active,job.optString("status"));continue;
                        }
                    }
                    if(tag!=null&&tag.startsWith(TIMER_TAG)){
                        long id;
                        try{id=Long.parseLong(tag.substring(TIMER_TAG.length()));}catch(NumberFormatException invalid){n.cancel(tag,active.getId());continue;}
                        org.json.JSONObject job=Store.get(c).job(id);
                        if(job==null||!"scheduled".equals(job.optString("status"))){n.cancel(tag,active.getId());continue;}
                    }
                    if(active.getNotification().visibility==visibility)continue;
                    Notification.Builder builder=Notification.Builder.recoverBuilder(c,active.getNotification()).setOnlyAlertOnce(true);
                    n.notify(active.getTag(),active.getId(),privacy(c,builder,channel).build());
                }
            }
        }catch(RuntimeException ignored){}
    }

    static void timerNoLongerActive(Context c,long jobId) {
        try{
            NotificationManager n=c.getSystemService(NotificationManager.class);if(n==null)return;
            for(StatusBarNotification active:n.getActiveNotifications()){
                if(active.getId()!=TIMER_ID||!(TIMER_TAG+jobId).equals(active.getTag()))continue;
                String text="This timer is no longer active. Open the conversation to check the reply's status.";
                Notification.Builder builder=Notification.Builder.recoverBuilder(c,active.getNotification())
                    .setContentTitle("Timer ended").setContentText(text).setSubText(null)
                    .setStyle(new Notification.BigTextStyle().bigText(text)).setActions(new Notification.Action[0])
                    .setUsesChronometer(false).setWhen(System.currentTimeMillis()).setAutoCancel(true).setOnlyAlertOnce(true);
                n.notify(active.getTag(),active.getId(),privacy(c,builder,active.getNotification().getChannelId()).build());
            }
        }catch(RuntimeException ignored){}
    }

    public static void show(Context c,int id,String title,String text,long thread) {
        try{
            String channel=messageChannel(c);NotificationManager n=c.getSystemService(NotificationManager.class);if(!allowed(c,n,channel))return;
            Notification.Builder builder=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_pilot).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open(c,thread)).setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_MESSAGE)
                .setPublicVersion(redacted(c,channel,"Open to review a message"));
            n.notify(id,builder.build());
        }catch(RuntimeException ignored){}
    }
}
