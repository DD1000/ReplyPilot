package com.contentfoundry.replypilot;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public class Store extends SQLiteOpenHelper {
    private static Store instance;
    private final Context context;
    // In-flight generation cannot outlive its process. A global pause invalidates its snapshot.
    private long generationRevision;
    public static synchronized Store get(Context c) { if(instance==null) instance=new Store(c.getApplicationContext()); return instance; }
    private Store(Context c) { super(c,"pilot.db",null,18);context=c; }
    @Override public void onOpen(SQLiteDatabase db){
        super.onOpen(db);
        // Repair outcomes saved by older versions: terminal delivery is stronger
        // evidence than a missing, delayed, or failed submission callback.
        if(!db.isReadOnly())db.execSQL("UPDATE jobs SET status='sent',note='Delivery confirmed by your carrier.' WHERE delivery_status='delivered' AND status IN ('sending','unknown','failed') AND parts>0 AND uri<>''");
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE jobs (_id INTEGER PRIMARY KEY AUTOINCREMENT, thread INTEGER NOT NULL, address TEXT NOT NULL, body TEXT NOT NULL, base INTEGER NOT NULL, sub INTEGER NOT NULL, due INTEGER NOT NULL, approved INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', uri TEXT NOT NULL DEFAULT '', parts INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, auto_send INTEGER NOT NULL DEFAULT 0, profile_revision INTEGER NOT NULL DEFAULT 0, config_revision TEXT NOT NULL DEFAULT '', original TEXT NOT NULL DEFAULT '', delivery_status TEXT NOT NULL DEFAULT 'none', delivered_at INTEGER NOT NULL DEFAULT 0, sms_date INTEGER NOT NULL DEFAULT 0, sleep_revision INTEGER NOT NULL DEFAULT 0, auto_delay INTEGER NOT NULL DEFAULT -1, location_revision INTEGER NOT NULL DEFAULT 0, location_expires INTEGER NOT NULL DEFAULT 0, attention_kind TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE receipts(job INTEGER, part INTEGER, result INTEGER, PRIMARY KEY(job,part))");
        db.execSQL("CREATE TABLE drafts(thread INTEGER PRIMARY KEY, base INTEGER NOT NULL, body TEXT NOT NULL, alternatives TEXT NOT NULL, engine TEXT NOT NULL, location_revision INTEGER NOT NULL DEFAULT 0, location_expires INTEGER NOT NULL DEFAULT 0, source_mms INTEGER NOT NULL DEFAULT 0, source_mms_date INTEGER NOT NULL DEFAULT 0, source_text TEXT NOT NULL DEFAULT '', source_address TEXT NOT NULL DEFAULT '')");
        createManualTakeover(db);
        createAutopilot(db);
        createRelationships(db);
        createDeliveryReports(db);
        createReplyDecisions(db);
        createReplyHolds(db);
        createAttentionActions(db);
        createPilotTraining(db);
    }
    private static void createAutopilot(SQLiteDatabase db){
        db.execSQL("CREATE TABLE autopilot_attention(job INTEGER PRIMARY KEY, thread INTEGER NOT NULL, reason TEXT NOT NULL, notified INTEGER NOT NULL DEFAULT 0, submitted INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE draft_attention(thread INTEGER PRIMARY KEY, base INTEGER NOT NULL, body TEXT NOT NULL, reason TEXT NOT NULL)");
        db.execSQL("CREATE TABLE autopilot_sources(job INTEGER PRIMARY KEY, thread INTEGER NOT NULL, mms_id INTEGER NOT NULL, mms_date INTEGER NOT NULL, fingerprint TEXT NOT NULL, address TEXT NOT NULL, sms_id INTEGER NOT NULL, sms_signature TEXT NOT NULL, manual_revision INTEGER NOT NULL, receipt INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX autopilot_sources_message ON autopilot_sources(thread,mms_id)");
    }
    private static void createPilotTraining(SQLiteDatabase db){
        db.execSQL("CREATE TABLE pilot_training(_id INTEGER PRIMARY KEY AUTOINCREMENT, thread INTEGER NOT NULL, scope TEXT NOT NULL, turn TEXT NOT NULL UNIQUE, incoming TEXT NOT NULL, reply TEXT NOT NULL, created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX pilot_training_contact ON pilot_training(thread,scope,_id)");
        db.execSQL("CREATE TABLE message_meanings(thread INTEGER NOT NULL, scope TEXT NOT NULL, kind TEXT NOT NULL, message_id INTEGER NOT NULL, fingerprint TEXT NOT NULL, message TEXT NOT NULL, meaning TEXT NOT NULL, created INTEGER NOT NULL, PRIMARY KEY(thread,scope,kind,message_id))");
    }
    private static void createAttentionActions(SQLiteDatabase db){
        db.execSQL("CREATE TABLE attention_actions(token TEXT PRIMARY KEY, thread INTEGER NOT NULL, base INTEGER NOT NULL, action TEXT NOT NULL, reason TEXT NOT NULL, profile_revision INTEGER NOT NULL, config_revision TEXT NOT NULL, fingerprint TEXT NOT NULL, burst INTEGER NOT NULL, state TEXT NOT NULL, created INTEGER NOT NULL, job_id INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', auto_plan INTEGER NOT NULL DEFAULT 0, auto_sleep_revision INTEGER NOT NULL DEFAULT 0, UNIQUE(thread,base,action))");
        db.execSQL("CREATE INDEX attention_pending ON attention_actions(state,created)");
    }
    private static void createReplyHolds(SQLiteDatabase db){
        db.execSQL("CREATE TABLE reply_holds(thread INTEGER PRIMARY KEY, base INTEGER NOT NULL, latest_base INTEGER NOT NULL, created INTEGER NOT NULL, delay_attempted INTEGER NOT NULL DEFAULT 0)");
    }
    private static void createReplyDecisions(SQLiteDatabase db){
        db.execSQL("CREATE TABLE reply_decisions(thread INTEGER NOT NULL, base INTEGER NOT NULL, reason TEXT NOT NULL, created INTEGER NOT NULL, PRIMARY KEY(thread,base))");
        db.execSQL("CREATE INDEX jobs_thread_automatic ON jobs(thread,auto_send,_id)");
    }
    private static void createDeliveryReports(SQLiteDatabase db){
        db.execSQL("CREATE TABLE delivery_reports(job INTEGER NOT NULL, part INTEGER NOT NULL, report_state TEXT NOT NULL, raw_status INTEGER NOT NULL, format TEXT NOT NULL, received INTEGER NOT NULL, PRIMARY KEY(job,part))");
        db.execSQL("CREATE INDEX jobs_uri_delivery ON jobs(uri)");
    }
    private static void createRelationships(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE relationships(thread INTEGER PRIMARY KEY, body TEXT NOT NULL, revision INTEGER NOT NULL, samples TEXT NOT NULL DEFAULT '', cloud_enabled INTEGER NOT NULL DEFAULT 0, auto_draft INTEGER NOT NULL DEFAULT 0, relationship_kind TEXT NOT NULL DEFAULT '', tone TEXT NOT NULL DEFAULT '', auto_send INTEGER NOT NULL DEFAULT 0, auto_delay INTEGER NOT NULL DEFAULT 0, auto_delay_mode TEXT NOT NULL DEFAULT 'fixed', auto_delay_min INTEGER NOT NULL DEFAULT 300, auto_delay_max INTEGER NOT NULL DEFAULT 1800, engagement TEXT NOT NULL DEFAULT 'natural', share_location INTEGER NOT NULL DEFAULT 0, humor_level INTEGER NOT NULL DEFAULT 0, inside_jokes TEXT NOT NULL DEFAULT '', important_details TEXT NOT NULL DEFAULT '', plan_handling TEXT NOT NULL DEFAULT 'ask_me')");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2)createRelationships(db);
        else {
            if(oldVersion<3){db.execSQL("ALTER TABLE relationships ADD COLUMN samples TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE relationships ADD COLUMN cloud_enabled INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE relationships ADD COLUMN auto_draft INTEGER NOT NULL DEFAULT 0");}
            if(oldVersion<4){db.execSQL("ALTER TABLE relationships ADD COLUMN relationship_kind TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE relationships ADD COLUMN tone TEXT NOT NULL DEFAULT ''");}
            if(oldVersion<5){db.execSQL("ALTER TABLE relationships ADD COLUMN auto_send INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE relationships ADD COLUMN auto_delay INTEGER NOT NULL DEFAULT 300");}
        }
        if(oldVersion<5){db.execSQL("ALTER TABLE jobs ADD COLUMN auto_send INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE jobs ADD COLUMN profile_revision INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE jobs ADD COLUMN config_revision TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE jobs ADD COLUMN original TEXT NOT NULL DEFAULT ''");}
        if(oldVersion<6){db.execSQL("ALTER TABLE jobs ADD COLUMN delivery_status TEXT NOT NULL DEFAULT 'none'");db.execSQL("ALTER TABLE jobs ADD COLUMN delivered_at INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE jobs ADD COLUMN sms_date INTEGER NOT NULL DEFAULT 0");createDeliveryReports(db);}
        if(oldVersion<7)createReplyDecisions(db);
        if(oldVersion<8){db.execSQL("ALTER TABLE jobs ADD COLUMN sleep_revision INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE jobs ADD COLUMN auto_delay INTEGER NOT NULL DEFAULT -1");}
        if(oldVersion>=2&&oldVersion<9){db.execSQL("ALTER TABLE relationships ADD COLUMN auto_delay_mode TEXT NOT NULL DEFAULT 'fixed'");db.execSQL("ALTER TABLE relationships ADD COLUMN auto_delay_min INTEGER NOT NULL DEFAULT 300");db.execSQL("ALTER TABLE relationships ADD COLUMN auto_delay_max INTEGER NOT NULL DEFAULT 1800");}
        if(oldVersion<10)createReplyHolds(db);
        if(oldVersion<11){
            if(oldVersion>=2){db.execSQL("ALTER TABLE relationships ADD COLUMN engagement TEXT NOT NULL DEFAULT 'natural'");db.execSQL("ALTER TABLE relationships ADD COLUMN share_location INTEGER NOT NULL DEFAULT 0");}
            for(String table:new String[]{"drafts","jobs"}){db.execSQL("ALTER TABLE "+table+" ADD COLUMN location_revision INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE "+table+" ADD COLUMN location_expires INTEGER NOT NULL DEFAULT 0");}
        }
        if(oldVersion<12){db.execSQL("ALTER TABLE jobs ADD COLUMN attention_kind TEXT NOT NULL DEFAULT ''");createAttentionActions(db);}
        if(oldVersion<14)createPilotTraining(db);
        if(oldVersion<15){
            if(oldVersion>=2){db.execSQL("ALTER TABLE relationships ADD COLUMN important_details TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE relationships ADD COLUMN plan_handling TEXT NOT NULL DEFAULT 'ask_me'");}
            if(oldVersion>=10)db.execSQL("ALTER TABLE reply_holds ADD COLUMN delay_attempted INTEGER NOT NULL DEFAULT 0");
            if(oldVersion>=12){db.execSQL("ALTER TABLE attention_actions ADD COLUMN auto_plan INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE attention_actions ADD COLUMN auto_sleep_revision INTEGER NOT NULL DEFAULT 0");}
        }
        if(oldVersion<16){db.execSQL("ALTER TABLE drafts ADD COLUMN source_mms INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE drafts ADD COLUMN source_mms_date INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE drafts ADD COLUMN source_text TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE drafts ADD COLUMN source_address TEXT NOT NULL DEFAULT ''");}
        if(oldVersion<17)createManualTakeover(db);
        if(oldVersion<18){
            createAutopilot(db);
            // Only contacts with standing send permission retain Autopilot.
            db.execSQL("UPDATE relationships SET cloud_enabled=0,auto_draft=0,auto_send=0 WHERE cloud_enabled<>1 OR auto_draft<>1 OR auto_send<>1");
            db.execSQL("UPDATE relationships SET auto_delay=CASE WHEN auto_delay IN (0,60,300) THEN auto_delay ELSE 0 END,auto_delay_mode='fixed',engagement='always_reply',plan_handling='delay_answer',revision=revision+1");
            db.execSQL("UPDATE jobs SET status='paused',approved=0,note='Autopilot was updated. Waiting for a new incoming message.' WHERE (auto_send=1 OR attention_kind='delay') AND status IN ('scheduled','awaiting_alert')");
            db.execSQL("UPDATE attention_actions SET state='stale',note='This old reply action was retired.' WHERE state IN ('offered','queued','running')");
            db.execSQL("DELETE FROM reply_holds");db.execSQL("DELETE FROM reply_decisions");
            db.execSQL("DELETE FROM drafts WHERE engine LIKE 'OpenAI%'");
        }
        if(oldVersion>=2&&oldVersion<13){db.execSQL("ALTER TABLE relationships ADD COLUMN humor_level INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE relationships ADD COLUMN inside_jokes TEXT NOT NULL DEFAULT ''");}
    }
    public JSONObject relationship(long thread) throws JSONException {
        JSONObject saved=query("SELECT body,revision,samples,cloud_enabled,auto_draft,relationship_kind,tone,auto_send,auto_delay,auto_delay_mode,auto_delay_min,auto_delay_max,engagement,share_location,humor_level,inside_jokes,important_details,plan_handling FROM relationships WHERE thread=?",new String[]{""+thread}).optJSONObject(0);
        if(saved==null)saved=new JSONObject().put("body","").put("revision",0).put("samples","");
        return saved.put("importantDetails",ContactGuidance.details(saved.optString("important_details"))).put("planHandling",ContactGuidance.planHandling(saved.optString("plan_handling","ask_me"))).put("humorLevel",ContactHumor.level(saved.has("humor_level")?saved.opt("humor_level"):0)).put("insideJokes",ContactHumor.notes(saved.has("inside_jokes")?saved.opt("inside_jokes"):"")).put("engagement",ReplyEngagementPolicy.normalize(saved.optString("engagement"))).put("shareLocation",saved.optInt("share_location")==1).put("relationshipKind",PersonProfile.kind(saved.optString("relationship_kind"))).put("tone",PersonProfile.tone(saved.optString("tone"))).put("cloudEnabled",saved.optInt("cloud_enabled")==1).put("autoDraft",saved.optInt("auto_draft")==1).put("autoSend",saved.optInt("auto_send")==1).put("autoDelay",saved.optLong("auto_delay",PersonProfile.DEFAULT_AUTO_DELAY_SECONDS)).put("autoDelayMode",saved.optString("auto_delay_mode","fixed")).put("autoDelayMin",saved.optLong("auto_delay_min",DelayPolicy.DEFAULT_MIN)).put("autoDelayMax",saved.optLong("auto_delay_max",DelayPolicy.DEFAULT_MAX));
    }
    private static void createManualTakeover(SQLiteDatabase db){
        db.execSQL("CREATE TABLE manual_takeovers(thread INTEGER PRIMARY KEY,revision INTEGER NOT NULL,sms_id INTEGER NOT NULL,sms_date INTEGER NOT NULL,sms_signature TEXT NOT NULL,sms_receipt INTEGER NOT NULL DEFAULT 0,mms_id INTEGER NOT NULL,mms_date INTEGER NOT NULL,mms_signature TEXT NOT NULL,mms_receipt INTEGER NOT NULL DEFAULT 0,claimed_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE manual_sends(request_id TEXT PRIMARY KEY,thread INTEGER NOT NULL,address TEXT NOT NULL,body TEXT NOT NULL,sub INTEGER NOT NULL,job_id INTEGER NOT NULL DEFAULT 0,created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX manual_sends_job ON manual_sends(job_id)");
    }
    public long generationRevision(){synchronized(PilotApp.SEND_LOCK){return generationRevision;}}
    public void invalidateGeneration(){synchronized(PilotApp.SEND_LOCK){generationRevision++;}}
    public JSONObject relationship(long thread,String body) throws JSONException {
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        String value=ReplyPrompt.relationshipContext(body);
        synchronized(PilotApp.SEND_LOCK){
            getWritableDatabase().execSQL("INSERT INTO relationships(thread,body,revision) VALUES(?,?,1) ON CONFLICT(thread) DO UPDATE SET body=excluded.body,revision=relationships.revision+1",new Object[]{thread,value});
            Sender.cancelAutomaticForThread(context,thread,"Relationship details changed. Generate a new reply to start an automatic timer.");
            return relationship(thread);
        }
    }
    public JSONObject profile(long thread,String body,String examples,boolean enabled,boolean automatic,String kind,String tone) throws JSONException {
        return profile(thread,body,examples,enabled,automatic,kind,tone,false,PersonProfile.DEFAULT_AUTO_DELAY_SECONDS);
    }
    public JSONObject profile(long thread,String body,String examples,boolean enabled,boolean automatic,String kind,String tone,boolean autoSend,long autoDelay) throws JSONException {
        return profile(thread,body,examples,enabled,automatic,kind,tone,autoSend,autoDelay,DelayPolicy.choice("fixed",autoDelay,DelayPolicy.DEFAULT_MIN,DelayPolicy.DEFAULT_MAX));
    }
    public JSONObject profile(long thread,String body,String examples,boolean enabled,boolean automatic,String kind,String tone,boolean autoSend,long autoDelay,DelayPolicy.Choice choice) throws JSONException {
        synchronized(PilotApp.SEND_LOCK){
            JSONObject prior=relationship(thread);
            return profile(thread,body,examples,enabled,automatic,kind,tone,autoSend,autoDelay,choice,prior.optString("engagement","natural"),prior.optBoolean("shareLocation"));
        }
    }
    public JSONObject profile(long thread,String body,String examples,boolean enabled,boolean automatic,String kind,String tone,boolean autoSend,long autoDelay,DelayPolicy.Choice choice,String engagement,boolean shareLocation) throws JSONException {
        synchronized(PilotApp.SEND_LOCK){
            JSONObject prior=relationship(thread);
            return profile(thread,body,examples,enabled,automatic,kind,tone,autoSend,autoDelay,choice,engagement,shareLocation,ContactHumor.level(prior.opt("humorLevel")),ContactHumor.notes(prior.opt("insideJokes")));
        }
    }
    public JSONObject profile(long thread,String body,String examples,boolean enabled,boolean automatic,String kind,String tone,boolean autoSend,long autoDelay,DelayPolicy.Choice choice,String engagement,boolean shareLocation,int humorLevel,String insideJokes) throws JSONException {
        JSONObject prior=relationship(thread);return profile(thread,body,examples,enabled,automatic,kind,tone,autoSend,autoDelay,choice,engagement,shareLocation,humorLevel,insideJokes,prior.optString("importantDetails"),prior.optString("planHandling","ask_me"));
    }
    public JSONObject profile(long thread,String body,String examples,boolean enabled,boolean automatic,String kind,String tone,boolean autoSend,long autoDelay,DelayPolicy.Choice choice,String engagement,boolean shareLocation,int humorLevel,String insideJokes,String importantDetails,String planHandling) throws JSONException {
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        String important=ContactGuidance.details(importantDetails),plans="delay_answer";
        String engage="always_reply";
        String context=ReplyPrompt.relationshipContext(body),samples=CloudPrompt.samples(examples);
        long delay=PersonProfile.autoDelay(autoDelay);
        if(!"fixed".equals(choice.mode()))throw new IllegalArgumentException("Choose a fixed Autopilot delay.");
        DelayPolicy.Choice timing=DelayPolicy.choice("fixed",delay,DelayPolicy.DEFAULT_MIN,DelayPolicy.DEFAULT_MAX);
        synchronized(PilotApp.SEND_LOCK){
            ReplyEligibility.Result readiness=PersonProfile.readiness(enabled,automatic,autoSend,()->ReplyReadiness.current(this.context,thread,samples));
            boolean canAutomate=readiness.eligible();
            boolean autopilot=enabled&&automatic&&autoSend&&canAutomate;
            // Retired style columns remain untouched for existing installations.
            // They are not copied into the new guidance fields or used in prompts.
            getWritableDatabase().execSQL("INSERT INTO relationships(thread,body,revision,samples,cloud_enabled,auto_draft,auto_send,auto_delay,auto_delay_mode,auto_delay_min,auto_delay_max,engagement,share_location,important_details,plan_handling) VALUES(?,?,1,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(thread) DO UPDATE SET body=excluded.body,samples=excluded.samples,cloud_enabled=excluded.cloud_enabled,auto_draft=excluded.auto_draft,auto_send=excluded.auto_send,auto_delay=excluded.auto_delay,auto_delay_mode=excluded.auto_delay_mode,auto_delay_min=excluded.auto_delay_min,auto_delay_max=excluded.auto_delay_max,engagement=excluded.engagement,share_location=excluded.share_location,important_details=excluded.important_details,plan_handling=excluded.plan_handling,revision=relationships.revision+1",new Object[]{thread,context,samples,autopilot?1:0,autopilot?1:0,autopilot?1:0,delay,timing.mode(),timing.min(),timing.max(),engage,shareLocation?1:0,important,plans});
            Sender.cancelAutomaticForThread(this.context,thread,"Reply settings changed. Generate a new reply to start an automatic timer.");
            getWritableDatabase().delete("reply_decisions","thread=? AND reason='conversation_complete'",new String[]{Long.toString(thread)});
            if(canAutomate)getWritableDatabase().delete("reply_decisions","thread=? AND reason='insufficient_history'",new String[]{Long.toString(thread)});
            JSONObject eligibility=ReplyReadiness.json(readiness);
            if(!enabled||!automatic||!autoSend)eligibility.put("available",false).put("message","Autopilot is off. History is checked when you turn it on.");
            return relationship(thread).put("replyEligibility",eligibility);
        }
    }
    public long queue(long thread,String address,String body,long base,int sub,long due) {
        ContentValues v=new ContentValues(); v.put("thread",thread);v.put("address",address);v.put("body",body);v.put("base",base);v.put("sub",sub);v.put("due",due);v.put("approved",1);v.put("status","scheduled");v.put("created",System.currentTimeMillis());
        JSONObject source=draft(thread,base);
        if(source!=null&&body.equals(source.optString("body"))){v.put("location_revision",source.optLong("location_revision"));v.put("location_expires",source.optLong("location_expires"));}
        long id=getWritableDatabase().insertOrThrow("jobs",null,v);linkAttention(id,thread,base,body);return id;
    }
    public long queueAutomatic(long thread,String address,String body,long base,int sub,long due,long revision,String configRevision,String original,long sleepRevision,long autoDelay) {
        ContentValues v=new ContentValues();v.put("thread",thread);v.put("address",address);v.put("body",body);v.put("base",base);v.put("sub",sub);v.put("due",due);v.put("approved",0);v.put("auto_send",1);v.put("profile_revision",revision);v.put("config_revision",configRevision);v.put("original",original);v.put("sleep_revision",sleepRevision);v.put("auto_delay",autoDelay);v.put("status","awaiting_alert");v.put("created",System.currentTimeMillis());
        JSONObject source=draft(thread,base);
        if(source!=null&&body.equals(source.optString("body"))){v.put("location_revision",source.optLong("location_revision"));v.put("location_expires",source.optLong("location_expires"));}
        long id=getWritableDatabase().insertOrThrow("jobs",null,v);linkAttention(id,thread,base,body);return id;
    }
    void draftAttention(long thread,long base,String body,String reason){
        getWritableDatabase().delete("draft_attention","thread=?",new String[]{Long.toString(thread)});
        if(reason==null||reason.isEmpty())return;
        ContentValues v=new ContentValues();v.put("thread",thread);v.put("base",base);v.put("body",body);v.put("reason",AutopilotPolicy.reason(reason));getWritableDatabase().insertOrThrow("draft_attention",null,v);
    }
    private void linkAttention(long job,long thread,long base,String body){
        JSONObject source=query("SELECT reason FROM draft_attention WHERE thread=? AND base=? AND body=?",new String[]{Long.toString(thread),Long.toString(base),body}).optJSONObject(0);
        if(source!=null)attention(job,thread,source.optString("reason"));
    }
    void attention(long job,long thread,String reason){
        ContentValues v=new ContentValues();v.put("job",job);v.put("thread",thread);v.put("reason",AutopilotPolicy.reason(reason));v.put("created",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("autopilot_attention",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    void attentionSubmitted(long job){ContentValues v=new ContentValues();v.put("submitted",1);getWritableDatabase().update("autopilot_attention",v,"job=?",new String[]{Long.toString(job)});}
    public void update(long id,String status,String note) { ContentValues v=new ContentValues();v.put("status",status);v.put("note",note);getWritableDatabase().update("jobs",v,"_id=?",new String[]{""+id}); }
    public boolean cancel(long id) { ContentValues v=new ContentValues();v.put("status","cancelled");v.put("approved",0);return getWritableDatabase().update("jobs",v,"_id=? AND status IN ('scheduled','awaiting_alert')",new String[]{""+id})==1; }
    public void pauseThread(long thread,String note) { ContentValues v=new ContentValues();v.put("status","paused");v.put("approved",0);v.put("note",note);getWritableDatabase().update("jobs",v,"thread=? AND status IN ('scheduled','awaiting_alert')",new String[]{""+thread}); getWritableDatabase().delete("drafts","thread=?",new String[]{""+thread}); }
    public void pauseAll(String note) { synchronized(PilotApp.SEND_LOCK){invalidateGeneration();ContentValues v=new ContentValues();v.put("status","paused");v.put("approved",0);v.put("note",note);getWritableDatabase().update("jobs",v,"status IN ('scheduled','awaiting_alert')",null);} }
    private void ageSendingJobs(){
        ContentValues v=new ContentValues();v.put("status","unknown");v.put("note","No carrier result after ten minutes. The text may have sent. Verify with the recipient before trying again.");
        getWritableDatabase().update("jobs",v,"status='sending' AND delivery_status<>'delivered' AND due<?",new String[]{""+(System.currentTimeMillis()-600000)});
    }
    public JSONArray jobs() {
        ageSendingJobs();
        return query("SELECT * FROM jobs ORDER BY CASE WHEN status IN ('scheduled','sending','paused','unknown') THEN 0 ELSE 1 END, created DESC LIMIT 100",null);
    }
    public JSONArray threadJobs(long thread){
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");ageSendingJobs();
        return query("SELECT * FROM jobs WHERE thread=? ORDER BY _id DESC LIMIT 40",new String[]{Long.toString(thread)});
    }
    public JSONObject job(long id) { JSONArray a=query("SELECT * FROM jobs WHERE _id=?",new String[]{""+id});return a.optJSONObject(0); }
    public int consecutiveAutomaticSubmissions(long thread,long excludedJob){
        JSONObject count=query(AutomaticReplyPolicy.COUNT_SQL,new String[]{Long.toString(thread),Long.toString(excludedJob),Long.toString(thread)}).optJSONObject(0);
        return count==null?0:count.optInt("total");
    }
    public JSONObject lastSubmission(long thread,long excludedJob){
        return query("SELECT body FROM jobs WHERE thread=? AND _id<>? AND "+AutomaticReplyPolicy.SUBMITTED_SQL+" ORDER BY _id DESC LIMIT 1",new String[]{Long.toString(thread),Long.toString(excludedJob)}).optJSONObject(0);
    }
    public JSONObject replyDecision(long thread,long base){
        JSONObject saved=query("SELECT reason FROM reply_decisions WHERE thread=? AND base=?",new String[]{Long.toString(thread),Long.toString(base)}).optJSONObject(0);
        if(saved==null)return null;
        try{return saved.put("decision","no_reply").put("message",AutomaticReplyPolicy.message(saved.optString("reason")));}
        catch(JSONException e){throw new IllegalStateException(e);}
    }
    public JSONObject replyHold(long thread){
        JSONObject hold=query("SELECT base,latest_base,delay_attempted FROM reply_holds WHERE thread=?",new String[]{Long.toString(thread)}).optJSONObject(0);
        if(hold==null)return null;
        try{return hold.put("reason","plans_need_input").put("message",AutomaticReplyPolicy.message("plans_need_input"));}
        catch(JSONException invalid){throw new IllegalStateException(invalid);}
    }
    public boolean holdReply(long thread,long base){
        SendPolicy.validateConversation(thread,base);if(base<=0)throw new IllegalArgumentException("No incoming message to review.");
        boolean first=replyHold(thread)==null;
        getWritableDatabase().execSQL("INSERT INTO reply_holds(thread,base,latest_base,created) VALUES(?,?,?,?) ON CONFLICT(thread) DO UPDATE SET latest_base=MAX(reply_holds.latest_base,excluded.latest_base)",new Object[]{thread,base,base,System.currentTimeMillis()});
        return first;
    }
    /** Media holds use no fake SMS id; later SMS extends the real reply watermark. */
    public void holdMediaReply(long thread,long smsBase){
        SendPolicy.validateConversation(thread,smsBase);
        getWritableDatabase().execSQL("INSERT INTO reply_holds(thread,base,latest_base,created) VALUES(?,0,?,?) ON CONFLICT(thread) DO UPDATE SET latest_base=MAX(reply_holds.latest_base,excluded.latest_base)",new Object[]{thread,smsBase,System.currentTimeMillis()});
    }
    public void extendReplyHold(long thread,long base){
        getWritableDatabase().execSQL("UPDATE reply_holds SET latest_base=MAX(latest_base,?) WHERE thread=?",new Object[]{base,thread});
    }
    public void clearReplyHoldForManual(JSONObject job){
        if(job==null||"delay".equals(job.optString("attention_kind"))||job.optInt("auto_send")!=0||job.optInt("approved")!=1||!("sent".equals(job.optString("status"))||"delivered".equals(job.optString("delivery_status"))))return;
        long thread=job.optLong("thread"),base=job.optLong("base");
        getWritableDatabase().delete("reply_holds","thread=? AND latest_base<=?",new String[]{Long.toString(thread),Long.toString(base)});
        getWritableDatabase().delete("reply_decisions","thread=? AND base<=? AND reason='plans_need_input'",new String[]{Long.toString(thread),Long.toString(base)});
    }
    public void noReply(long thread,long base,String reason){
        SendPolicy.validateConversation(thread,base);if(base<=0)throw new IllegalArgumentException("No incoming message to answer.");
        ContentValues values=new ContentValues();values.put("thread",thread);values.put("base",base);values.put("reason",AutomaticReplyPolicy.validReason(reason));values.put("created",System.currentTimeMillis());
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            if(db.insertWithOnConflict("reply_decisions",null,values,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("The reply decision could not be saved.");
            // Silence must not leave a stale automatic suggestion visible. Typed
            // text and drafts belonging to a different SMS base stay untouched.
            db.delete("drafts","thread=? AND base=? AND engine LIKE 'OpenAI%'",new String[]{Long.toString(thread),Long.toString(base)});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void clearReplyDecision(long thread,long base){getWritableDatabase().delete("reply_decisions","thread=? AND base=?",new String[]{Long.toString(thread),Long.toString(base)});}
    public void deliveryUnknown(long id){
        ContentValues values=new ContentValues();values.put("delivery_status","unknown");
        getWritableDatabase().update("jobs",values,"_id=? AND delivery_status='pending'",new String[]{Long.toString(id)});
    }
    /** The part report and aggregate survive process death as one transaction. No PDU is stored. */
    public JSONObject delivery(long id,int part,DeliveryPolicy.Report report,long received){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            JSONObject job=job(id);
            if(job==null||"none".equals(job.optString("delivery_status","none"))||job.optString("uri").isEmpty()||!DeliveryPolicy.validPart(job.optInt("parts"),part))return null;
            JSONObject prior=query("SELECT report_state FROM delivery_reports WHERE job=? AND part=?",new String[]{Long.toString(id),Integer.toString(part)}).optJSONObject(0);
            String previous=prior==null?null:prior.optString("report_state"),merged=DeliveryPolicy.merge(previous,report.state());
            if(!merged.equals(previous)){
                ContentValues row=new ContentValues();row.put("job",id);row.put("part",part);row.put("report_state",merged);row.put("raw_status",report.rawStatus());row.put("format",report.format());row.put("received",received);
                if(db.insertWithOnConflict("delivery_reports",null,row,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Delivery report could not be saved.");
            }
            Map<Integer,String> reports=new HashMap<>();
            JSONArray saved=query("SELECT part,report_state FROM delivery_reports WHERE job=?",new String[]{Long.toString(id)});
            for(int i=0;i<saved.length();i++){JSONObject row=saved.optJSONObject(i);reports.put(row.optInt("part"),row.optString("report_state"));}
            String state=DeliveryPolicy.aggregate(job.optInt("parts"),reports,"failed".equals(job.optString("status")));
            long deliveredAt="delivered".equals(state)?(job.optLong("delivered_at")>0?job.optLong("delivered_at"):received):0;
            String sendState=DeliveryPolicy.settledSendStatus(job.optString("status"),state);
            ContentValues values=new ContentValues();values.put("delivery_status",state);values.put("delivered_at",deliveredAt);
            if("delivered".equals(state)&&"sent".equals(sendState)){values.put("status",sendState);values.put("note",DeliveryPolicy.receiptNote(false,state));job.put("status",sendState).put("note",DeliveryPolicy.receiptNote(false,state));}
            db.update("jobs",values,"_id=?",new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
            return job.put("delivery_status",state).put("delivered_at",deliveredAt);
        }catch(JSONException e){throw new IllegalStateException(e);}
        finally{db.endTransaction();}
    }
    /** Exclude app-generated automatic sends from evidence of the owner's personal style. */
    public Set<Long> automaticMessageIds(JSONArray rows){
        Map<String,JSONObject> sent=new HashMap<>();
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);
            if(row!=null&&row.optInt("type")==2&&row.optLong("_id")>0)sent.put("content://sms/"+row.optLong("_id"),row);
        }
        Set<Long> result=new HashSet<>();if(sent.isEmpty())return result;
        String[] uris=sent.keySet().toArray(new String[0]);
        JSONArray jobs=query("SELECT uri,thread,address,body,sms_date FROM jobs WHERE (auto_send=1 OR attention_kind='delay') AND uri IN ("+String.join(",",Collections.nCopies(uris.length,"?"))+")",uris);
        for(int i=0;i<jobs.length();i++){
            JSONObject job=jobs.optJSONObject(i),row=sent.get(job.optString("uri"));
            if(row!=null&&DeliveryPolicy.sameMessage(job.optLong("thread"),job.optLong("sms_date"),job.optString("address"),job.optString("body"),row.optLong("thread_id"),row.optLong("date"),row.optString("address"),row.optString("body")))result.add(row.optLong("_id"));
        }
        return result;
    }
    /** One bounded lookup per visible list/page also works when a provider status write failed. */
    public void annotateDelivery(JSONArray rows){
        Map<String,JSONObject> outgoing=new HashMap<>();
        try{
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                int type=row.optInt("type"),status=row.optInt("status",-1);
                row.put("status",status).put("delivery",DeliveryPolicy.fromProvider(type,status)).put("delivered_at",0);
                if(DeliveryPolicy.outgoing(type)&&row.optLong("_id")>0)outgoing.put("content://sms/"+row.optLong("_id"),row);
            }
            if(outgoing.isEmpty())return;
            String[] uris=outgoing.keySet().toArray(new String[0]);
            JSONArray jobs=query("SELECT uri,thread,address,body,sms_date,delivery_status,delivered_at FROM jobs WHERE uri IN ("+String.join(",",Collections.nCopies(uris.length,"?"))+") AND delivery_status<>'none' ORDER BY _id DESC",uris);
            for(int i=0;i<jobs.length();i++){
                JSONObject job=jobs.optJSONObject(i),row=outgoing.get(job.optString("uri"));
                // IDs can be reused after provider history is deleted. Even an
                // identical reply must have the same original send timestamp.
                if(row!=null&&DeliveryPolicy.sameMessage(job.optLong("thread"),job.optLong("sms_date"),job.optString("address"),job.optString("body"),row.optLong("thread_id"),row.optLong("date"),row.optString("address"),row.optString("body"))){
                    row.put("delivery",job.optString("delivery_status")).put("delivered_at",job.optLong("delivered_at"));
                    outgoing.remove(job.optString("uri"));
                }
            }
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    public JSONObject draft(long thread,long base) { JSONArray a=query("SELECT * FROM drafts WHERE thread=? AND base=?",new String[]{""+thread,""+base});JSONObject draft=a.optJSONObject(0);return draft!=null&&draft.optLong("source_mms")>0&&!TextMmsDrafts.matchesStored(context,thread,draft)?null:draft; }
    public void draft(long thread,long base,String body,JSONArray alternatives,String engine) {draft(thread,base,body,alternatives,engine,0,0);}
    public void draft(long thread,long base,String body,JSONArray alternatives,String engine,long locationRevision,long locationExpires) {
        ContentValues v=new ContentValues();v.put("location_revision",locationRevision);v.put("location_expires",locationExpires);v.put("thread",thread);v.put("base",base);v.put("body",body);v.put("alternatives",alternatives.toString());v.put("engine",engine);getWritableDatabase().insertWithOnConflict("drafts",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public void draftTextMms(long thread,long base,String body,MediaContext.Snapshot source){
        ContentValues v=new ContentValues();v.put("thread",thread);v.put("base",base);v.put("body",body);v.put("alternatives",new JSONArray().put(body).toString());v.put("engine","OpenAI · awaiting your review");
        v.put("source_mms",source.mediaId());v.put("source_mms_date",source.date());v.put("source_text",source.caption());v.put("source_address",source.address());
        if(getWritableDatabase().insertWithOnConflict("drafts",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException("Your draft could not be saved.");
    }
    public JSONArray query(String sql,String[] args) {
        JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery(sql,args)){while(c.moveToNext())out.put(json(c));}return out;
    }
    static JSONObject json(Cursor c) { JSONObject o=new JSONObject(); try { for(int i=0;i<c.getColumnCount();i++){String n=c.getColumnName(i);if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER)o.put(n,c.getLong(i));else o.put(n,c.isNull(i)?"":c.getString(i));} }catch(JSONException e){throw new IllegalStateException(e);}return o; }
}
