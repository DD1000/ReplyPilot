package com.contentfoundry.replypilot;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private WebView web;
    private FrameLayout contentFrame;
    private boolean ready;
    private boolean keyboardVisible;
    private ActionMode textSelectionMode;
    private final TouchProtection touchProtection=new TouchProtection();
    private long editorFocusRequest;
    private static final Set<String> EDITORS=Set.of("draft","pilot-reply","relationship-context","relationship-important","relationship-samples","chat-log-owner","person-custom-delay","default-custom-delay","sleep-custom-delay","custom-delay","person-range-min","person-range-max","default-range-min","default-range-max","sleep-range-min","sleep-range-max","manual-range-min","manual-range-max","search","recipient","home-address","pairing-code","test-message","test-relationship","test-examples");
    private final java.util.concurrent.atomic.AtomicLong homeRequestEpoch=new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong linkRequestEpoch=new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong liveHistoryEpoch=new java.util.concurrent.atomic.AtomicLong();
    private final Handler messageUi=new Handler(Looper.getMainLooper());
    private boolean observingMessages;
    private boolean smsObserverRegistered;
    private boolean contactsObserverRegistered;
    private boolean messageRefreshPending;
    private MessageChanges.Subscription messageSubscription;
    private final Runnable messageChangeListener=this::queueMessageRefresh;
    private final Runnable messageRefresh=()->{
        messageRefreshPending=false;
        if(observingMessages&&ready&&!isDestroyed())web.evaluateJavascript("window.onMessagesChanged?.()",null);
    };
    private final ContentObserver smsObserver=new ContentObserver(messageUi){
        @Override public void onChange(boolean selfChange){LaunchHistoryCache.changed();HistoryArchive.changed();queueMessageRefresh();}
        @Override public void onChange(boolean selfChange,Uri uri){LaunchHistoryCache.changed();HistoryArchive.changed();queueMessageRefresh();}
    };
    private final ContentObserver contactsObserver=new ContentObserver(messageUi){
        @Override public void onChange(boolean selfChange){contactsChanged();}
        @Override public void onChange(boolean selfChange,Uri uri){contactsChanged();}
    };
    private void contactsChanged(){
        ContactPhotos.invalidate();ReplyProfile.changed();PilotTraining.accessChanged();HistoryArchive.changed();LaunchHistoryCache.changed();
        if(web!=null&&ready&&!isDestroyed())web.evaluateJavascript("window.onContactPhotosChanged?.()",null);
        queueMessageRefresh();
    }
    private SharedPreferences settings;
    // Never reuse a chooser code while this process is alive. A late Android
    // callback from an expired request must not finish the user's next attempt.
    private static final java.util.concurrent.atomic.AtomicInteger SMS_ROLE_CODES=new java.util.concurrent.atomic.AtomicInteger(1024);
    private static final int CONTACTS_PERMISSION_REQUEST=4;
    private static final int CHAT_LOG_REQUEST=5;
    private static final int ATTACHMENT_REQUEST=7;
    private String pendingAttachmentRequest;
    private long pendingAttachmentThread;
    private boolean attachmentPickerActive;
    private boolean attachmentReturned;
    private JSONObject attachmentResult;
    private String attachmentError;
    private static final int LOCATION_PERMISSION_REQUEST=6;
    private String pendingLocationRequest;
    private boolean locationRequestReturned;
    private String pendingChatLogRequest;
    private boolean chatLogReturned;
    private JSONObject chatLogResult;
    private String chatLogError;
    private String pendingRoleRequest;
    private SmsRoleRequestPolicy pendingRoleAttempt;
    private AlertDialog roleExplanation;
    private String lastRoleOutcome="idle";
    private final Runnable roleDeadline=()->reconcileRoleRequest(false);
    private String pendingContactsRequest;
    private boolean contactsRequestReturned;
    private boolean contactsRequestCancelled;
    @Override public void onCreate(Bundle state){super.onCreate(state);settings=getSharedPreferences("settings",0);if(state!=null){ShareInbox.restore(this,state.getBundle("incomingShare"));if(ShareInbox.isShare(getIntent()))setIntent(new Intent(Intent.ACTION_MAIN).setClass(this,MainActivity.class));}else captureShareIntent(getIntent());if(state!=null){pendingAttachmentThread=state.getLong("attachmentThread",0);attachmentPickerActive=pendingAttachmentThread>0;SMS_ROLE_CODES.accumulateAndGet(state.getInt("nextSmsRoleCode",1024),Math::max);}
        if(!settings.getBoolean("timerUiV7",false)){settings.edit().putInt("delay",Math.max(300,settings.getInt("delay",300))).putBoolean("timerUiV7",true).apply();}
        WebView.setWebContentsDebuggingEnabled(false);
        // Android 12+ can keep third-party overlay windows away without disabling
        // Android's own copy/paste toolbar, selection handles, or keyboard.
        getWindow().setHideOverlayWindows(true);
        if(!settings.getBoolean("midnightThemeV75",false)){settings.edit().putString("theme","midnight").putBoolean("midnightThemeV75",true).apply();}
        // Use one consistent inset path on every supported Android version. Android
        // 15+ enforces edge-to-edge for this target; older versions need it explicitly.
        getWindow().setDecorFitsSystemWindows(false);
        WindowManager.LayoutParams windowAttributes=getWindow().getAttributes();
        windowAttributes.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setAttributes(windowAttributes);
        if(Build.VERSION.SDK_INT<35){getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);}
        web=new WebView(this){
            @Override public ActionMode startActionMode(ActionMode.Callback callback){return startActionMode(callback,ActionMode.TYPE_PRIMARY);}
            @Override public ActionMode startActionMode(ActionMode.Callback callback,int type){return super.startActionMode(selectionCallback(callback),type);}
        };web.setBackgroundColor(themeBackground(settings.getString("theme","midnight")));
        // The bridge is reachable only by packaged assets. No remote pages or scripts can load.
        web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setBlockNetworkLoads(true);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();if(!"https".equals(u.getScheme())||!"app.replypilot.local".equals(u.getHost()))return denied();
                String p=u.getPath();try{
                    if(p!=null&&p.startsWith("/contact-photo"))return ContactPhotos.open(MainActivity.this,u);
                    if(p!=null&&p.matches("/link-preview/[a-f0-9-]{36}")){
                        WebResourceResponse preview=LinkPreviews.image(MainActivity.this,u.getLastPathSegment());
                        return preview==null?denied():preview;
                    }
                    if(p!=null&&p.matches("/attachment/[a-f0-9-]{36}")){
                        String token=u.getLastPathSegment(),mime=MmsAttachments.previewMime(MainActivity.this,token);
                        if(mime==null)return denied();InputStream stream=MmsAttachments.openPreview(MainActivity.this,token);
                        if(stream==null)return denied();
                        return new WebResourceResponse(mime,null,200,"OK",Map.of("Cache-Control","no-store","X-Content-Type-Options","nosniff"),stream);
                    }
                    if(p!=null&&p.matches("/part/[0-9]+")){
                        Uri part=Uri.parse("content://mms/part/"+u.getLastPathSegment());
                        return MediaPartResponse.open(MainActivity.this,part,r);
                    }
                    if(!Arrays.asList("/index.html","/app.css","/app.js","/demo.js","/theme.js").contains(p))return denied();
                    String mime=p.endsWith("css")?"text/css":p.endsWith("js")?"application/javascript":"text/html";
                    InputStream asset=getAssets().open(p.substring(1));
                    if("/index.html".equals(p))try(InputStream original=asset){
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=original.read(buffer))!=-1){if(bytes.size()+count>128*1024)throw new IOException("Oversized launch document");bytes.write(buffer,0,count);}
                        String html=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
                        html=ThemePreferences.launchHtml(html,settings.getString("theme","midnight"));
                        asset=new ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    return new WebResourceResponse(mime,"UTF-8",asset);
                }catch(Exception e){return denied();}
            }
            @Override public void onPageFinished(WebView v,String u){ready=true;handleIntent(getIntent());notifyShare();queueMessageRefresh();}
        });
        // WebView padding does not establish a safe viewport for fixed HTML. Measure
        // the entire WebView inside a padded native parent instead. The combined mask
        // takes the largest inset on each edge, so IME and navigation space do not add.
        contentFrame=new FrameLayout(this);
        contentFrame.setClipToPadding(true);
        contentFrame.addView(web,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        contentFrame.setOnApplyWindowInsetsListener((v,insets)->{
            int handledTypes=WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime();
            android.graphics.Insets safe=insets.getInsets(handledTypes);
            if(v.getPaddingLeft()!=safe.left||v.getPaddingTop()!=safe.top||v.getPaddingRight()!=safe.right||v.getPaddingBottom()!=safe.bottom)v.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            boolean visible=insets.isVisible(WindowInsets.Type.ime());
            boolean hidden=keyboardVisible&&!visible;
            keyboardVisible=visible;
            if(hidden&&ready)web.post(()->{
                // Ignore a queued hide if the keyboard has already opened again.
                if(!keyboardVisible&&ready&&!isDestroyed())web.evaluateJavascript("window.onKeyboardHidden?.()",null);
            });
            // Forward every keyboard transition so WebView updates its viewport;
            // zero the space already handled by this parent to avoid double padding.
            return new WindowInsets.Builder(insets).setInsets(handledTypes,android.graphics.Insets.NONE).build();
        });
        setContentView(contentFrame);applyNativeTheme();contentFrame.post(contentFrame::requestApplyInsets);web.loadUrl("https://app.replypilot.local/index.html");
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::handleBack);
    }
    private void finishReplyProfile(String id,JSONObject result){
        String response="window.nativeResult("+JSONObject.quote(id)+","+result+",null)";
        String invalidated="window.nativeResult("+JSONObject.quote(id)+",null,\"Contact access changed. Open Reply setup again.\")";
        runOnUiThread(()->{if(!isDestroyed()&&web!=null)web.evaluateJavascript(ReplyProfile.canDeliver(this,result)?response:invalidated,null);});
    }
    private void finishTraining(String id,JSONObject result,PilotTraining.AccessStamp stamp){
        String response="window.nativeResult("+JSONObject.quote(id)+","+result+",null)";
        String invalidated="window.nativeResult("+JSONObject.quote(id)+",null,\"Contact access changed. Reopen Train Pilot.\")";
        runOnUiThread(()->{if(!isDestroyed()&&web!=null)web.evaluateJavascript(PilotTraining.canDeliver(this,stamp)?response:invalidated,null);});
    }
    private WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        // MotionEvent cannot identify the window causing occlusion. Never trust
        // obscured send taps merely because a text-selection toolbar is open.
        int obscured=MotionEvent.FLAG_WINDOW_IS_OBSCURED|MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED;
        int action=event.getActionMasked();
        TouchProtection.Decision decision=touchProtection.next(action==MotionEvent.ACTION_DOWN,
            action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL,
            (event.getFlags()&obscured)!=0,textSelectionMode!=null);
        if(decision.cancelDeliveredTouch()){
            MotionEvent cancel=MotionEvent.obtain(event);
            try{cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);}
            finally{cancel.recycle();}
        }
        // A floating copy toolbar must not leave the screen in a state where every
        // outside tap is rejected forever. Dismiss it, but consume this complete
        // gesture; the user must make a fresh unobscured tap to activate a control.
        if(decision.dismissSelection())dismissTextSelection(true);
        if(decision.block())return true;
        return super.dispatchTouchEvent(event);
    }
    @Override public void onActionModeStarted(ActionMode mode){super.onActionModeStarted(mode);textSelectionMode=mode;}
    @Override public void onActionModeFinished(ActionMode mode){if(textSelectionMode==mode)textSelectionMode=null;super.onActionModeFinished(mode);}
    private ActionMode.Callback selectionCallback(ActionMode.Callback callback){
        return new ActionMode.Callback2(){
            @Override public boolean onCreateActionMode(ActionMode mode,Menu menu){return callback.onCreateActionMode(mode,menu);}
            @Override public boolean onPrepareActionMode(ActionMode mode,Menu menu){return callback.onPrepareActionMode(mode,menu);}
            @Override public boolean onActionItemClicked(ActionMode mode,MenuItem item){
                boolean handled=callback.onActionItemClicked(mode,item);
                // Select all keeps Android's toolbar/handles. Copy and Cut finish
                // the owning mode after the WebView has handled the operation.
                if(handled&&closesSelection(item)&&textSelectionMode==mode)mode.finish();
                return handled;
            }
            @Override public void onDestroyActionMode(ActionMode mode){
                // Let the WebView clear its own native selection, rather than
                // later clearing whichever editor the user has newly focused.
                try{callback.onDestroyActionMode(mode);}finally{if(textSelectionMode==mode)textSelectionMode=null;}
            }
            @Override public void onGetContentRect(ActionMode mode,View view,android.graphics.Rect rect){
                if(callback instanceof ActionMode.Callback2 placement)placement.onGetContentRect(mode,view,rect);
                else super.onGetContentRect(mode,view,rect);
            }
        };
    }
    private boolean closesSelection(MenuItem item){
        if(item.getItemId()==android.R.id.copy||item.getItemId()==android.R.id.cut)return true;
        // Chromium WebView uses provider-private IDs for these two items, but
        // their labels come from Android's localized copy/cut string resources.
        // This only closes an already-handled action; it never changes its work.
        return android.text.TextUtils.equals(item.getTitle(),getText(android.R.string.copy))
            ||android.text.TextUtils.equals(item.getTitle(),getText(android.R.string.cut));
    }
    private boolean dismissTextSelection(boolean clearWhenInactive){
        ActionMode mode=textSelectionMode;boolean active=mode!=null;
        if(active){textSelectionMode=null;mode.finish();}
        // Never collapse document.activeElement here: this asynchronous script
        // may run after the user has already tapped a different editor/caret.
        if((active||clearWhenInactive)&&ready&&web!=null&&!isDestroyed())web.evaluateJavascript("(()=>{if(!document.activeElement?.matches('input,textarea,[contenteditable=true]'))window.getSelection()?.removeAllRanges();})()",null);
        return active;
    }
    private void handleBack(){
        editorFocusRequest++;
        if(dismissTextSelection(false))return;
        if(ready&&web!=null&&!isDestroyed())web.evaluateJavascript("window.goBack()",null);
    }
    private boolean editorWindowReady(){return PilotApp.foreground&&ready&&web!=null&&!isDestroyed()&&!isFinishing()&&hasWindowFocus();}
    private void requestEditorKeyboard(String requestId,String editor)throws JSONException{
        if(!EDITORS.contains(editor))throw new IllegalArgumentException("Choose an editable field.");
        long request=++editorFocusRequest;
        if(!editorWindowReady()){finish(requestId,new JSONObject().put("requested",false),null);return;}
        // JavaScript has already focused this field in response to the user's tap.
        // Never move focus or reopen the keyboard for a stale/background editor.
        String script="(()=>{const e=document.getElementById("+JSONObject.quote(editor)+");if(!e||e!==document.activeElement||!e.isConnected||e.disabled||e.readOnly||e.closest('[hidden],[inert]'))return false;if(!(e instanceof HTMLTextAreaElement)&&!(e instanceof HTMLInputElement&&['text','search','tel','number','url','email','password'].includes(e.type)))return false;const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&r.bottom>0&&r.top<innerHeight&&s.visibility==='visible'&&s.display!=='none';})()";
        web.evaluateJavascript(script,value->{
            boolean requested=false;
            if(request==editorFocusRequest&&editorWindowReady()&&"true".equals(value)){
                web.requestFocus();InputMethodManager keyboard=getSystemService(InputMethodManager.class);
                if(keyboard!=null)requested=keyboard.showSoftInput(web,InputMethodManager.SHOW_IMPLICIT);
            }
            try{finish(requestId,new JSONObject().put("requested",requested),null);}
            catch(JSONException invalid){finish(requestId,null,"The keyboard could not be opened. Tap the field again.");}
        });
    }
    private void queueMessageRefresh(){
        ConversationHistoryCache.invalidate();
        // Keep a fixed short window: a stream of provider changes must not postpone
        // the refresh indefinitely. Both provider and send-status signals arrive here.
        if(!observingMessages||!ready||messageRefreshPending)return;
        messageRefreshPending=true;
        messageUi.postDelayed(messageRefresh,50);
    }
    private void registerSmsObserver(){
        if(!observingMessages||smsObserverRegistered||!Messages.allowed(this,Manifest.permission.READ_SMS))return;
        try{getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI,true,smsObserver);smsObserverRegistered=true;getContentResolver().registerContentObserver(Telephony.Mms.CONTENT_URI,true,smsObserver);}
        catch(SecurityException revoked){/* Permission can change between the check and registration. */}
    }
    private void registerContactsObserver(){
        if(!observingMessages||contactsObserverRegistered||!Messages.allowed(this,Manifest.permission.READ_CONTACTS))return;
        try{getContentResolver().registerContentObserver(android.provider.ContactsContract.Contacts.CONTENT_URI,true,contactsObserver);contactsObserverRegistered=true;}
        catch(SecurityException revoked){/* Permission may change while registering. */}
    }
    private void notifyMessageAccess(){
        liveHistoryEpoch.incrementAndGet();
        LaunchInboxCache.accessChanged(getApplicationContext());LaunchHistoryCache.accessChanged(getApplicationContext());HistoryArchive.accessChanged(getApplicationContext());ReplyProfile.changed();PilotTraining.accessChanged();ContactPhotos.invalidate();
        if(!Messages.allowed(this,Manifest.permission.READ_SMS)||!Messages.role(this)){linkRequestEpoch.incrementAndGet();LinkPreviews.clear();}
        if(web!=null&&ready)web.evaluateJavascript("window.onMessageAccessChanged?.({readSms:"+Messages.allowed(this,Manifest.permission.READ_SMS)+",contacts:"+Messages.allowed(this,Manifest.permission.READ_CONTACTS)+",defaultSms:"+Messages.role(this)+"})",null);
    }
    private void observeMessages(){
        observingMessages=true;
        if(messageSubscription==null){
            // Capture the Handler, not this Activity, in the process-local executor.
            Handler handler=messageUi;
            messageSubscription=MessageChanges.subscribe(command->handler.post(command),messageChangeListener);
        }
        registerSmsObserver();registerContactsObserver();
    }
    private void stopObservingMessages(){
        observingMessages=false;
        if(messageSubscription!=null){messageSubscription.close();messageSubscription=null;}
        if(smsObserverRegistered){getContentResolver().unregisterContentObserver(smsObserver);smsObserverRegistered=false;}
        if(contactsObserverRegistered){getContentResolver().unregisterContentObserver(contactsObserver);contactsObserverRegistered=false;}
        messageUi.removeCallbacks(messageRefresh);messageRefreshPending=false;
    }
    /** True when Android lets Reply Pilot's reply jobs and network run while the phone is dozing. */
    private boolean batteryUnrestricted(){PowerManager power=getSystemService(PowerManager.class);return power!=null&&power.isIgnoringBatteryOptimizations(getPackageName());}
    /** Android's own one-time prompt; some phones hide it, so fall back to the battery list, then app info. */
    private void openBatterySettings(){
        if(batteryUnrestricted())return;
        for(Intent intent:new Intent[]{new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())),new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))}){
            try{startActivity(intent);return;}catch(ActivityNotFoundException unavailable){/* Try the next screen. */}
        }
        throw new IllegalStateException("Open Android Settings → Apps → Reply Pilot → Battery and choose Unrestricted.");
    }
    @Override protected void onResume(){super.onResume();ConversationHistoryCache.invalidate();LaunchHistoryCache.changed();HistoryArchive.changed();PilotApp.foreground=true;HistoryArchive.refresh(getApplicationContext());PilotApp.recoverMms(this);observeMessages();LocationSharing.EXECUTOR.execute(()->LocationSharing.reconcile(getApplicationContext()));RecentContext.prepareEnabled(this);if(web!=null){web.onResume();notifyMessageAccess();if(ready)web.evaluateJavascript("window.onNativeResume?.()",null);}reconcileRoleRequest(true);if(contactsRequestReturned)finishContactsRequest();if(locationRequestReturned)finishLocationRequest();if(chatLogReturned)finishChatLogRequest();if(attachmentReturned)finishAttachmentRequest();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        ConversationHistoryCache.invalidate();notifyMessageAccess();
        if(requestCode==CONTACTS_PERMISSION_REQUEST&&pendingContactsRequest!=null){
            contactsRequestReturned=true;contactsRequestCancelled=results.length==0;
            // The JS continuation may query contacts, so wait for the bridge to be active.
            if(PilotApp.foreground)finishContactsRequest();
        }
        if(requestCode==LOCATION_PERMISSION_REQUEST&&pendingLocationRequest!=null){
            locationRequestReturned=true;
            if(PilotApp.foreground)finishLocationRequest();
        }
        registerSmsObserver();registerContactsObserver();RecentContext.prepareEnabled(this);if(web!=null&&ready)web.evaluateJavascript("window.onNativeResume?.()",null);
    }
    @Override protected void onPause(){
        editorFocusRequest++;
        touchProtection.reset();dismissTextSelection(false);
        if(pendingRoleAttempt!=null)pendingRoleAttempt.leaveApp();
        // Flush any coalesced edit before pausing the WebView; the bridge may receive it
        // after Android has marked this Activity backgrounded.
        if(web!=null&&ready)web.evaluateJavascript("window.onNativePause?.()",null);
        stopObservingMessages();ConversationHistoryCache.invalidate();liveHistoryEpoch.incrementAndGet();linkRequestEpoch.incrementAndGet();LinkPreviews.cancelPending();homeRequestEpoch.incrementAndGet();LocationSharing.cancelHomeCandidates();PilotTraining.onPause();ReplyProfile.changed();ContactPhotos.invalidate();PilotApp.foreground=false;if(web!=null)web.onPause();super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putLong("attachmentThread",attachmentPickerActive?pendingAttachmentThread:0);out.putInt("nextSmsRoleCode",SMS_ROLE_CODES.get());out.putBundle("incomingShare",ShareInbox.save());}
    @Override protected void onDestroy(){editorFocusRequest++;stopObservingMessages();clearRoleRequest();if(web!=null){web.removeJavascriptInterface("Native");web.destroy();}super.onDestroy();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if(captureShareIntent(i)){notifyShare();return;}if(ready)handleIntent(i);}
    private boolean captureShareIntent(Intent intent){
        if(!ShareInbox.isShare(intent))return false;ShareInbox.receive(getApplicationContext(),intent);
        // The received intent is consumed once, even when the page reloads or the
        // user reopens this Activity from Recents after cancelling the share.
        setIntent(new Intent(Intent.ACTION_MAIN).setClass(this,MainActivity.class));return true;
    }
    private void notifyShare(){if(ready&&web!=null&&!isDestroyed())web.evaluateJavascript("window.onShareReceived?.()",null);}
    private void handleIntent(Intent i){if(i==null)return;if(captureShareIntent(i)){notifyShare();return;}try{JSONObject o=new JSONObject();long thread=i.getLongExtra("thread",0);if(thread>0)o.put("thread",thread);if(Intent.ACTION_SENDTO.equals(i.getAction())&&i.getData()!=null){String address=i.getData().getSchemeSpecificPart().split("\\?")[0];if(SendPolicy.validAddress(address)){o.put("address",address);o.put("body",i.getStringExtra("sms_body"));}}web.evaluateJavascript("window.openIntent("+o+")",null);}catch(JSONException ignored){}}
    // Only used on Android 12. Android 13+ registers OnBackInvokedCallback in onCreate.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){handleBack();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==ATTACHMENT_REQUEST&&pendingAttachmentThread>0){
            attachmentPickerActive=false;final long thread=pendingAttachmentThread;
            if(resultCode!=RESULT_OK){
                PilotApp.IO.execute(()->{try{completeAttachment(MmsAttachments.state(getApplicationContext(),thread),null);}catch(Exception e){completeAttachment(null,safeError(e));}});return;
            }
            Uri chosen=data==null?null:data.getData();
            if(chosen==null&&data!=null&&data.getClipData()!=null&&data.getClipData().getItemCount()==1)chosen=data.getClipData().getItemAt(0).getUri();
            if(chosen==null||!"content".equals(chosen.getScheme())||(data!=null&&data.getClipData()!=null&&data.getClipData().getItemCount()>1)){completeAttachment(null,"Choose one photo, short video, audio file or contact card.");return;}
            final Uri uri=chosen;final Context application=getApplicationContext();
            try{MmsAttachments.EXECUTOR.execute(()->{try{JSONObject selected=MmsAttachments.add(application,thread,uri);completeAttachment(selected,null);}catch(Exception e){completeAttachment(null,safeError(e));}});}
            catch(RuntimeException busy){completeAttachment(null,"An attachment is still being prepared. Try again in a moment.");}
            return;
        }
        if(requestCode==CHAT_LOG_REQUEST&&pendingChatLogRequest!=null){
            if(resultCode!=RESULT_OK){try{completeChatLog(new JSONObject().put("cancelled",true),null);}catch(JSONException ignored){}return;}
            Uri uri=data==null?null:data.getData();
            if(uri==null||!"content".equals(uri.getScheme())){completeChatLog(null,"Choose a plain-text chat log from Files.");return;}
            PilotApp.IO.execute(()->{
                try(InputStream stream=getContentResolver().openInputStream(uri)){
                    String body=ChatLogFile.read(stream),name="Chat log.txt";
                    try(Cursor file=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
                        if(file!=null&&file.moveToFirst()&&!file.isNull(0)){String value=file.getString(0);if(value!=null&&!value.isBlank())name=value.replaceAll("[\\p{Cntrl}]","").substring(0,Math.min(160,value.replaceAll("[\\p{Cntrl}]","").length()));}
                    }
                    completeChatLog(new JSONObject().put("body",body).put("fileName",name),null);
                }catch(Exception e){completeChatLog(null,safeError(e));}
            });return;
        }
        if(pendingRoleAttempt!=null&&pendingRoleAttempt.returned(requestCode)){
            // Android delivers this callback before onResume; wait until the bridge is active.
            if(PilotApp.foreground)reconcileRoleRequest(false);
        }
    }
    private void openAttachment(String id,long partId){
        MmsAttachments.EXECUTOR.execute(()->{
            try{
                AttachmentViewer.Prepared prepared=AttachmentViewer.prepare(getApplicationContext(),partId);
                runOnUiThread(()->{try{
                    if(isDestroyed()||!PilotApp.foreground)throw new IllegalStateException("Open Reply Pilot to view the attachment.");
                    Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(prepared.uri(),prepared.mime())
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    view.setClipData(ClipData.newRawUri("Attachment",prepared.uri()));
                    Intent chooser=Intent.createChooser(view,"Open attachment").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(chooser);finish(id,new JSONObject(),null);
                }catch(ActivityNotFoundException unavailable){finish(id,null,"No app on this phone can open this attachment format.");}catch(Exception error){finish(id,null,safeError(error));}});
            }catch(Exception error){finish(id,null,safeError(error));}
        });
    }
    private void pickAttachments(String id,long thread){
        if(!PilotApp.foreground||!Messages.role(this)||!Messages.allowed(this,Manifest.permission.READ_SMS))throw new IllegalStateException("Choose Reply Pilot as your default texting app and allow messages first.");
        if(thread<=0)throw new IllegalArgumentException("Open a conversation first.");
        if(pendingAttachmentThread>0)throw new IllegalStateException("Finish choosing your attachment first.");
        pendingAttachmentRequest=id;pendingAttachmentThread=thread;attachmentPickerActive=true;attachmentReturned=false;attachmentResult=null;attachmentError=null;
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*","audio/*","text/vcard","text/x-vcard"})
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(picker,ATTACHMENT_REQUEST);}catch(RuntimeException unavailable){pendingAttachmentRequest=null;pendingAttachmentThread=0;attachmentPickerActive=false;throw new IllegalStateException("Android could not open Files. Try again in a moment.");}
    }
    private void completeAttachment(JSONObject result,String error){
        MessageChanges.publish();
        runOnUiThread(()->{if(isDestroyed())return;attachmentResult=result;attachmentError=error;attachmentReturned=true;if(PilotApp.foreground)finishAttachmentRequest();});
    }
    private void finishAttachmentRequest(){
        String id=pendingAttachmentRequest;JSONObject result=attachmentResult;String error=attachmentError;
        pendingAttachmentRequest=null;pendingAttachmentThread=0;attachmentPickerActive=false;attachmentReturned=false;attachmentResult=null;attachmentError=null;
        if(id!=null)finish(id,result,error);else queueMessageRefresh();
    }
    private JSONArray annotateMms(JSONArray rows)throws JSONException{
        if(rows!=null){MmsDownloads.annotate(this,rows);MmsAttachments.annotate(this,rows);}return rows;
    }
    private JSONObject annotateMmsPage(JSONObject page)throws JSONException{annotateMms(page.optJSONArray("history"));return page;}
    private void importChatLog(String id){
        if(pendingChatLogRequest!=null)throw new IllegalStateException("Finish the open chat log import first.");
        pendingChatLogRequest=id;chatLogReturned=false;chatLogResult=null;chatLogError=null;
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(picker,CHAT_LOG_REQUEST);}
        catch(RuntimeException unavailable){pendingChatLogRequest=null;throw new IllegalStateException("Android could not open Files. Paste your labeled chat log instead.");}
    }
    private void completeChatLog(JSONObject result,String error){
        runOnUiThread(()->{if(isDestroyed()||pendingChatLogRequest==null)return;chatLogResult=result;chatLogError=error;chatLogReturned=true;if(PilotApp.foreground)finishChatLogRequest();});
    }
    private void finishChatLogRequest(){
        String id=pendingChatLogRequest;JSONObject result=chatLogResult;String error=chatLogError;
        pendingChatLogRequest=null;chatLogReturned=false;chatLogResult=null;chatLogError=null;
        if(id!=null)finish(id,result,error);
    }
    private JSONObject roleResult()throws JSONException{
        boolean selected=Messages.role(this);
        boolean pending=pendingRoleAttempt!=null;
        String outcome=selected?"selected":pending?"waiting":"selected".equals(lastRoleOutcome)?"notSelected":lastRoleOutcome;
        String message=switch(outcome){
            case "selected" -> "Reply Pilot is your default texting app.";
            case "timeout" -> "Android has not confirmed a selection. Try again, or open Android default apps and choose SMS app → Reply Pilot.";
            case "settings" -> "In Android default apps, choose SMS app → Reply Pilot, then return here.";
            case "notSelected" -> "Reply Pilot is not selected yet. Try again, or open Android default apps and choose SMS app → Reply Pilot.";
            case "waiting" -> "Waiting for Android to confirm your default texting app…";
            default -> "";
        };
        return new JSONObject().put("defaultSms",selected).put("permissions",Messages.allowed(this,Manifest.permission.READ_SMS)&&Messages.allowed(this,Manifest.permission.SEND_SMS)).put("phoneAccess",Messages.phoneAccess(this))
            .put("pending",pending&&!selected).put("phase",pending&&!selected?pendingRoleAttempt.phase():"idle").put("outcome",outcome).put("message",message);
    }
    private String clearRoleRequest(){
        String id=pendingRoleRequest;pendingRoleRequest=null;pendingRoleAttempt=null;
        messageUi.removeCallbacks(roleDeadline);
        AlertDialog explanation=roleExplanation;roleExplanation=null;if(explanation!=null)explanation.dismiss();
        return id;
    }
    private void finishRoleRequest(String outcome){
        String id=clearRoleRequest();lastRoleOutcome=outcome;
        if(id==null)return;
        try{finish(id,roleResult(),null);}catch(Exception e){finish(id,null,safeError(e));}
    }
    private void reconcileRoleRequest(boolean resumed){
        if(pendingRoleAttempt==null||isDestroyed())return;
        try{
            String outcome=pendingRoleAttempt.completion(Messages.role(this),SystemClock.elapsedRealtime(),resumed);
            if(outcome!=null)finishRoleRequest(outcome);
        }catch(Exception unavailable){String id=clearRoleRequest();if(id!=null)finish(id,null,"Android's default-app status could not be checked. Try Open Android default apps.");}
    }
    private JSONObject contactsPermissionResult(boolean cancelled)throws JSONException{
        boolean allowed=Messages.allowed(this,Manifest.permission.READ_CONTACTS);
        return new JSONObject().put("allowed",allowed).put("blocked",!allowed&&!cancelled&&!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS));
    }
    private void finishLocationRequest(){
        String id=pendingLocationRequest;pendingLocationRequest=null;locationRequestReturned=false;
        if(id==null)return;
        try{finish(id,LocationSharing.state(this),null);}catch(Exception e){finish(id,null,safeError(e));}
        LocationSharing.EXECUTOR.execute(()->{LocationSharing.reconcile(getApplicationContext());LocationSharing.refresh(getApplicationContext());});
    }
    private void requestLocation(String id)throws JSONException{
        if(!LocationSharing.enabled(this))throw new IllegalStateException("Turn on Location replies first.");
        if(pendingLocationRequest!=null||pendingContactsRequest!=null)throw new IllegalStateException("Finish the open permission request first.");
        if(Messages.allowed(this,Manifest.permission.ACCESS_FINE_LOCATION)){finish(id,LocationSharing.state(this),null);return;}
        pendingLocationRequest=id;locationRequestReturned=false;
        try{requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION_REQUEST);}
        catch(RuntimeException unavailable){pendingLocationRequest=null;throw new IllegalStateException("Android could not open location permissions. Use App settings to allow Location.");}
    }
    private void requestContacts(String id)throws JSONException{
        if(pendingContactsRequest!=null)throw new IllegalStateException("Finish the open contacts permission request first.");
        if(Messages.allowed(this,Manifest.permission.READ_CONTACTS)){finish(id,contactsPermissionResult(false),null);return;}
        pendingContactsRequest=id;contactsRequestReturned=false;contactsRequestCancelled=false;
        try{requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},CONTACTS_PERMISSION_REQUEST);}
        catch(RuntimeException unavailable){pendingContactsRequest=null;throw new IllegalStateException("Android could not open contacts permissions. Use App settings to allow Contacts.");}
    }
    private void finishContactsRequest(){
        String id=pendingContactsRequest;pendingContactsRequest=null;contactsRequestReturned=false;
        if(id==null)return;
        try{finish(id,contactsPermissionResult(contactsRequestCancelled),null);}catch(Exception e){finish(id,null,safeError(e));}
    }
    private void finishContacts(String id,JSONObject result){
        runOnUiThread(()->{
            // Check again at delivery: a queued worker response must not reveal a
            // contacts list after permission has been revoked in Android Settings.
            try{finish(id,Messages.allowed(this,Manifest.permission.READ_CONTACTS)?result:Contacts.denied(),null);}
            catch(JSONException e){finish(id,null,"Contacts could not be loaded. Please try again.");}
        });
    }
    private void requestSmsRole(String id)throws JSONException{
        if(!PilotApp.foreground)throw new IllegalStateException("Open Reply Pilot to choose your default texting app.");
        RoleManager manager=getSystemService(RoleManager.class);
        if(manager==null||!manager.isRoleAvailable(RoleManager.ROLE_SMS))throw new IllegalStateException("This phone does not offer a default SMS app setting.");
        reconcileRoleRequest(false);
        if(Messages.role(this)){finish(id,roleResult(),null);return;}
        if(pendingRoleRequest!=null)throw new IllegalStateException("Finish the open default-app prompt first.");
        int requestCode=SMS_ROLE_CODES.getAndIncrement();
        if(requestCode>65535)throw new IllegalStateException("Open Android default apps to choose Reply Pilot.");
        SmsRoleRequestPolicy attempt=new SmsRoleRequestPolicy(requestCode);
        pendingRoleRequest=id;pendingRoleAttempt=attempt;lastRoleOutcome="waiting";
        roleExplanation=new AlertDialog.Builder(this).setTitle("Make Reply Pilot your SMS app?")
            .setMessage("This early version sends individual SMS and can receive carrier MMS. Photos and other supported attachments send by MMS using your selected SIM. MMS may require mobile data and carrier support. RCS and group replies are not supported; turn off RCS in Google Messages before switching.")
            .setOnCancelListener(d->{if(pendingRoleAttempt==attempt)finishRoleRequest("cancelled");}).setNegativeButton("Not now",(d,w)->{if(pendingRoleAttempt==attempt)finishRoleRequest("cancelled");})
            .setPositiveButton("Choose default app",(d,w)->{
                if(pendingRoleAttempt!=attempt)return;
                try{
                    attempt.launch(SystemClock.elapsedRealtime());
                    startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_SMS),attempt.requestCode());
                    messageUi.postDelayed(roleDeadline,SmsRoleRequestPolicy.CHOOSER_TIMEOUT_MS);
                }catch(Exception e){clearRoleRequest();lastRoleOutcome="idle";finish(id,null,"Android could not open the chooser. Use Open Android default apps below.");}
            }).create();
        try{roleExplanation.show();}catch(RuntimeException unavailable){clearRoleRequest();throw unavailable;}
    }
    private String contactFingerprint(long thread,String address){
        Uri uri=Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple","true").build();
        try(Cursor row=getContentResolver().query(uri,new String[]{"recipient_ids"},"_id=?",new String[]{Long.toString(thread)},null)){
            if(row==null)throw new IllegalStateException("Conversation details are temporarily unavailable.");
            return row.moveToFirst()?MediaContextPolicy.signature(row.isNull(0)?"":row.getString(0),address):"";
        }
    }
    /** A draft completion never needs to reload the entire conversation or readiness. */
    private JSONObject draftResult(long thread,long base,boolean waiting,long expectedEpoch)throws JSONException{
        synchronized(PilotApp.SEND_LOCK){
            requireLiveHistory(expectedEpoch);
            if(thread<=0||base<0)throw new IllegalArgumentException("Open a conversation first.");
            if(Messages.latest(this,thread)!=base)throw new IllegalStateException("New messages arrived. Open the latest conversation to see your saved draft.");
            if(MediaContext.newerIncoming(this,thread,base))throw new IllegalStateException("A newer media message needs your attention. Open it to review the reply.");
            Store store=Store.get(this);
            JSONObject draft=store.draft(thread,base),decision=store.replyDecision(thread,base),hold=AutomaticReplies.publicHold(this,thread);
            boolean replyWaiting=waiting;
            if(base>0)try(Cursor row=getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"type","address"},"thread_id=? AND _id=?",new String[]{Long.toString(thread),Long.toString(base)},null)){
                if(row==null||!row.moveToFirst())throw new IllegalStateException("The latest message could not be checked. Reopen this chat.");
                replyWaiting=row.getInt(0)==1&&!IncomingBurst.ready(this,thread,base,row.getString(1));
            }
            requireLiveHistory(expectedEpoch);
            if(Messages.latest(this,thread)!=base)throw new IllegalStateException("New messages arrived. Open the latest conversation to see your saved draft.");
            return new JSONObject().put("draftState",true).put("thread",thread).put("base",base)
                .put("draft",draft==null?JSONObject.NULL:draft).put("replyDecision",decision==null?JSONObject.NULL:decision)
                .put("replyHold",hold==null?JSONObject.NULL:hold).put("replyWaiting",replyWaiting).put("waiting",waiting).put("manualReplySuppressed",ManualTakeover.blocked(this,thread));
        }
    }
    private void finishDraftResult(String id,JSONObject value,long expectedEpoch){
        String response="window.nativeResult("+JSONObject.quote(id)+","+value+",null)";
        String unavailable="window.nativeResult("+JSONObject.quote(id)+",null,\"Reopen this chat to see its saved draft.\")";
        runOnUiThread(()->{if(!isDestroyed()&&web!=null)web.evaluateJavascript(liveHistoryAllowed(expectedEpoch)?response:unavailable,null);});
    }
    private JSONObject conversation(long thread,JSONArray receiptIds)throws JSONException{
        JSONObject page=annotateMmsPage(MediaNavigation.latest(this,thread,null));
        page.put("photo",ContactPhotos.url(this,page.optString("address"))).put("contactPhotoRevision",ContactPhotos.revision()).put("contactFingerprint",contactFingerprint(thread,page.optString("address")));
        // The live SMS base needs only the newest row; mixed history is already loaded above.
        JSONObject latest=MediaNavigation.latestRow(this,thread,"sms");
        long base=latest==null?0:latest.optLong("_id");
        RecentContext.Snapshot recent=RecentContext.forConversation(this,thread);
        JSONObject profile=Store.get(this).relationship(thread);
        JSONObject decision=Store.get(this).replyDecision(thread,base),hold=AutomaticReplies.publicHold(this,thread);
        JSONObject mixedLatest=page.optJSONObject("latest"),latestMms=page.optJSONObject("latestMms");
        long incomingMms=mixedLatest!=null&&latestMms!=null&&!page.optBoolean("readOnly")&&"mms".equals(mixedLatest.optString("kind"))&&mixedLatest.optLong("id")==latestMms.optLong("id")&&latestMms.optInt("type")==1?latestMms.optLong("id"):0;
        if(incomingMms>0&&latest!=null&&MediaHistoryPolicy.compare(new MediaHistoryPolicy.Position(latest.optLong("date"),"sms",base),new MediaHistoryPolicy.Position(latestMms.optLong("date"),"mms",incomingMms))>=0)incomingMms=0;
        long incomingTextMms=0;
        if(incomingMms>0){
            JSONArray rows=page.optJSONArray("history");
            for(int i=0;rows!=null&&i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                if(row!=null&&"mms".equals(row.optString("kind"))&&row.optLong("_id")==incomingMms&&row.optLong("date")==latestMms.optLong("date")&&row.optInt("type")==1&&row.optInt("m_type")==132&&row.optBoolean("textOnly")){incomingTextMms=incomingMms;break;}
            }
        }
        long incomingMedia=incomingTextMms>0?0:incomingMms;
        if(incomingMms>0)decision=null;
        page.put("attachments",MmsAttachments.state(this,thread)).put("smsLatest",latest==null?JSONObject.NULL:latest).put("latestIncomingMediaId",incomingMedia).put("latestIncomingTextMmsId",incomingTextMms).put("approvedLearning",ApprovedLearning.state(this,thread))
            .put("attentionActions",incomingMms>0?new JSONObject():AutomaticReplies.attentionActions(this,thread,base))
            .put("replyHold",hold==null?JSONObject.NULL:hold)
            .put("replyWaiting",incomingMms==0&&base>0&&latest.optInt("type")==1&&!IncomingBurst.ready(this,thread,base,Messages.address(this,thread,base)))
            .put("replyEligibility",ReplyReadiness.forDisplay(this,thread,profile.optString("samples"),page.optBoolean("readOnly")))
            .put("receiptUpdates",Messages.receiptUpdates(this,thread,receiptIds))
            .put("learning",recent.metadata()).put("styleSamples",ReplyPrompt.sentExamples(thread,recent.messages()).size())
            .put("base",base).put("manualReplySuppressed",ManualTakeover.blocked(this,thread)).put("manualRevision",ManualTakeover.revision(this,thread)).put("profileRevision",profile.optLong("revision")).put("relationship",profile).put("draft",Store.get(this).draft(thread,base))
            .put("replyDecision",decision==null?JSONObject.NULL:decision);
        // Read markers are optional UI metadata. A busy carrier provider must not
        // discard a successfully loaded conversation; access failures still fail closed.
        try{Messages.read(this,thread);}catch(SecurityException revoked){throw revoked;}catch(RuntimeException unavailable){/* Retry when this chat refreshes. */}
        return page;
    }
    private JSONObject liveInbox(long expectedEpoch)throws JSONException{
        requireLiveHistory(expectedEpoch);
        boolean contacts=Messages.allowed(this,Manifest.permission.READ_CONTACTS);long contactRevision=ContactPhotos.revision();
        JSONArray inbox=ContactPhotos.annotate(this,annotateMms(Messages.recentInbox(this)),"address");
        requireLiveHistory(expectedEpoch);
        if(!LiveInboxWork.contactsCurrent(contacts,Messages.allowed(this,Manifest.permission.READ_CONTACTS),contactRevision,ContactPhotos.revision()))throw new IllegalStateException("Contact access changed. Refresh Messages again.");
        // This is a delta, never evidence to remove older chats or grant sending access.
        return new JSONObject().put("inbox",inbox).put("inboxComplete",false).put("contactsAllowed",contacts).put("contactPhotoRevision",contactRevision);
    }
    private JSONObject snapshot()throws JSONException{
        Sender.reconcile(this);Notices.refreshScheduled(this);
        JSONArray sims=Messages.sims(this);int sub=SimPolicy.effective(settings.getInt("sub",SimPolicy.NONE),Messages.simIds(sims));
        JSONArray inbox=ContactPhotos.annotate(this,annotateMms(Messages.inbox(this)),"address");
        JSONObject result=new JSONObject().put("defaultSms",Messages.role(this)).put("permissions",Messages.allowed(this,Manifest.permission.READ_SMS)&&Messages.allowed(this,Manifest.permission.SEND_SMS)).put("contactsAllowed",Messages.allowed(this,Manifest.permission.READ_CONTACTS)).put("notifications",Notices.canAlert(this)).put("exact",Sender.exact(this)).put("batteryUnrestricted",batteryUnrestricted()).put("ownerViews",OwnerViews.read(this)).put("sims",sims).put("sub",sub).put("phoneAccess",Messages.phoneAccess(this)).put("autoDraft",settings.getBoolean("autoDraft",true)).put("inAppSuggestions",settings.getBoolean("inAppSuggestions",true)).put("linkPreviews",settings.getBoolean("linkPreviews",true)).put("sleep",SleepSession.publicState(this)).put("location",LocationSharing.state(this)).put("matchMyStyle",settings.getBoolean("matchMyStyle",true)).put("tone",settings.getString("tone","Natural")).put("delay",settings.getInt("delay",300)).put("delayMode",settings.getString("delayMode","fixed")).put("delayMin",settings.getLong("delayMin",DelayPolicy.DEFAULT_MIN)).put("delayMax",settings.getLong("delayMax",DelayPolicy.DEFAULT_MAX)).put("theme",theme(settings.getString("theme","midnight"))).put("lockScreenPreviews",settings.getBoolean("lockScreenPreviews",true)).put("cloud",CloudConfig.publicState(this)).put("inbox",inbox).put("inboxComplete",true).put("contactPhotoRevision",ContactPhotos.revision()).put("jobs",Store.get(this).jobs());
        LaunchInboxCache.save(getApplicationContext(),inbox);LaunchHistoryCache.refresh(getApplicationContext(),inbox);HistoryArchive.refresh(getApplicationContext());return result.put("historyCache",HistoryArchive.status(getApplicationContext()));
    }
    private void requireLinkAccess(){
        if(isDestroyed()||isFinishing()||!PilotApp.foreground||!Messages.role(this)||!Messages.allowed(this,Manifest.permission.READ_SMS))throw new IllegalStateException("Open a conversation with message access to view its link preview.");
        if(!settings.getBoolean("linkPreviews",true))throw new IllegalStateException("Link previews are turned off.");
    }
    private String authorizedMessageLink(JSONObject request)throws JSONException{
        requireLinkAccess();long thread=MediaHistoryPolicy.integer(request.opt("thread"),true),id=MediaHistoryPolicy.integer(request.opt("id"),true);
        String kind=request.getString("kind");if(!"sms".equals(kind)&&!"mms".equals(kind))throw new IllegalArgumentException("This message cannot provide a link preview.");
        String url=LinkPreviewPolicy.openUrl(request.getString("url"));
        JSONObject row=MediaNavigation.exact(this,kind,id,thread);
        if(row==null)throw new IllegalStateException("This message is no longer available.");
        if("mms".equals(kind))MediaNavigation.parts(this,row);
        if(!LinkPreviewPolicy.links(row.optString("body")).contains(url))throw new IllegalStateException("This link is no longer in the message.");
        requireLinkAccess();return url;
    }
    private JSONObject messageLinkPreview(JSONObject request,long expectedEpoch)throws JSONException{
        if(linkRequestEpoch.get()!=expectedEpoch)throw new IllegalStateException("Link preview unavailable.");
        String url=authorizedMessageLink(request);
        if(linkRequestEpoch.get()!=expectedEpoch)throw new IllegalStateException("Link preview unavailable.");
        JSONObject result=LinkPreviews.preview(getApplicationContext(),url);
        // The provider may change during the network read. Never show another row's metadata.
        if(linkRequestEpoch.get()!=expectedEpoch||!url.equals(authorizedMessageLink(request)))throw new IllegalStateException("This message changed. Open it again.");
        return result;
    }
    private void finishLinkPreview(String id,JSONObject value,long expectedEpoch){
        String response="window.nativeResult("+JSONObject.quote(id)+","+value+",null)";
        String unavailable="window.nativeResult("+JSONObject.quote(id)+",null,\"Link preview unavailable.\")";
        runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript(linkRequestEpoch.get()==expectedEpoch&&LinkPreviews.canDeliver(getApplicationContext(),value)?response:unavailable,null);});
    }
    private void finishArchive(String id,JSONObject value){
        // Serialize on the archive worker; the UI only validates access and delivers.
        String response="window.nativeResult("+JSONObject.quote(id)+","+value+",null)";
        String invalidated="window.nativeResult("+JSONObject.quote(id)+",null,\"Saved messages are unavailable. Open the chat again.\")";
        runOnUiThread(()->{if(!isDestroyed()&&web!=null)web.evaluateJavascript(HistoryArchive.canDeliver(this,value)?response:invalidated,null);});
    }
    private void finishLaunchInbox(String id,JSONObject value){
        String response="window.nativeResult("+JSONObject.quote(id)+","+value+",null)";
        String invalidated="window.nativeResult("+JSONObject.quote(id)+",{inbox:[]},null)";
        runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript(LaunchInboxCache.canDeliver(getApplicationContext(),value)?response:invalidated,null);});
    }
    private void finishLaunchHistories(String id,JSONObject value){
        String response="window.nativeResult("+JSONObject.quote(id)+","+value+",null)";
        String invalidated="window.nativeResult("+JSONObject.quote(id)+",{conversations:[]},null)";
        runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript(LaunchHistoryCache.canDeliver(getApplicationContext(),value)?response:invalidated,null);});
    }
    private boolean liveHistoryAllowed(long expectedEpoch){
        return !isDestroyed()&&!isFinishing()&&LiveHistoryWork.canDeliver(expectedEpoch,liveHistoryEpoch.get(),PilotApp.foreground,Messages.role(this),Messages.allowed(this,Manifest.permission.READ_SMS));
    }
    private void requireLiveHistory(long expectedEpoch){
        if(!liveHistoryAllowed(expectedEpoch))throw new IllegalStateException("Open the chat with message access to update its history.");
    }
    private void finishLiveHistory(String id,JSONObject value,long expectedEpoch){
        // Keep large history serialization off the main thread; check access again
        // when the response actually reaches the WebView, not just when queued.
        String response="window.nativeResult("+JSONObject.quote(id)+","+value+",null)";
        String unavailable="window.nativeResult("+JSONObject.quote(id)+",null,\"Open the chat again to update its history.\")";
        runOnUiThread(()->{if(!isDestroyed()&&web!=null)web.evaluateJavascript(liveHistoryAllowed(expectedEpoch)?response:unavailable,null);});
    }
    private void finishLiveInbox(String id,JSONObject value,long expectedEpoch){
        String response="window.nativeResult("+JSONObject.quote(id)+","+value+",null)";
        String unavailable="window.nativeResult("+JSONObject.quote(id)+",null,\"Message access changed. Refresh Messages again.\")";
        runOnUiThread(()->{if(!isDestroyed()&&web!=null){
            boolean current=liveHistoryAllowed(expectedEpoch)&&LiveInboxWork.contactsCurrent(value.optBoolean("contactsAllowed"),Messages.allowed(this,Manifest.permission.READ_CONTACTS),value.optLong("contactPhotoRevision",-1),ContactPhotos.revision());
            web.evaluateJavascript(current?response:unavailable,null);
        }});
    }
    private void finishShare(String id,JSONObject value){
        String response="window.nativeResult("+JSONObject.quote(id)+","+value+",null)";
        String unavailable="window.nativeResult("+JSONObject.quote(id)+",null,\"Share access changed. Reopen the draft to check it.\")";
        runOnUiThread(()->{if(!isDestroyed()&&web!=null)web.evaluateJavascript(ShareInbox.canDeliver(this,value)?response:unavailable,null);});
    }
    private void finishHistoryPreview(String id,JSONObject value){
        String response="window.nativeResult("+JSONObject.quote(id)+","+value+",null)";
        String invalidated="window.nativeResult("+JSONObject.quote(id)+",{conversations:[]},null)";
        runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript(ConversationHistoryCache.canDeliver(getApplicationContext(),value)?response:invalidated,null);});
    }
    private void finishHomeCandidates(String id,JSONObject value,long expectedEpoch){
        String response="window.nativeResult("+JSONObject.quote(id)+","+value+",null)";
        String invalidated="window.nativeResult("+JSONObject.quote(id)+",null,\"Address search ended. Try again.\")";
        runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript(homeRequestEpoch.get()==expectedEpoch&&LocationSharing.homeCandidatesCanDeliver(getApplicationContext(),value)?response:invalidated,null);});
    }
    private void finish(String id,Object value,String error){
        // Serialize large history responses on their worker, before posting to the UI.
        String response="window.nativeResult("+JSONObject.quote(id)+","+(value==null?"null":value)+","+(error==null?"null":JSONObject.quote(error))+")";
        runOnUiThread(()->{if(!isDestroyed())web.evaluateJavascript(response,null);});
    }
    private void permissions(){if(pendingContactsRequest!=null)throw new IllegalStateException("Finish the open contacts permission request first.");if(!Messages.role(this))throw new IllegalStateException("Choose Reply Pilot as your default SMS app first.");ArrayList<String> p=new ArrayList<>(Arrays.asList(Manifest.permission.READ_SMS,Manifest.permission.SEND_SMS,Manifest.permission.RECEIVE_SMS,Manifest.permission.RECEIVE_MMS,Manifest.permission.RECEIVE_WAP_PUSH,Manifest.permission.READ_PHONE_STATE,Manifest.permission.READ_CONTACTS));if(Build.VERSION.SDK_INT>=33)p.add(Manifest.permission.POST_NOTIFICATIONS);requestPermissions(p.toArray(new String[0]),2);}
    private class Bridge {
        @JavascriptInterface public void call(String id,String action,String raw){
            // Saving an in-progress edit is allowed while the Activity is pausing. It
            // cannot authorize a send; all send and configuration actions stay gated.
            if(!PilotApp.foreground&&!"saveDraft".equals(action)){finish(id,null,"Open Reply Pilot to continue.");return;}
            if("analyzeChatLog".equals(action)&&(raw==null||raw.length()>1_600_000)){finish(id,null,"Choose a text chat log no larger than 256 KB.");return;}
            JSONObject p;try{p=new JSONObject(raw);}catch(JSONException e){finish(id,null,"Invalid request.");return;}
            final long requestLinkEpoch=linkRequestEpoch.get();
            final long requestLiveHistoryEpoch=liveHistoryEpoch.get();
            if(Arrays.asList("setHome","clearHome","saveLocation").contains(action)){homeRequestEpoch.incrementAndGet();LocationSharing.cancelHomeCandidates();}
            final long requestHomeEpoch=homeRequestEpoch.get();
            if(Arrays.asList("shareState","cancelShare","role","smsRoleStatus","defaultSettings","appSettings","notificationSettings","permissions","requestContacts","requestLocation","locationSettings","importChatLog","pickAttachments","openAttachment","openLink","cancelHomeCandidates","alarms","battery","approve","dismissSelection","focusEditor","close").contains(action)){
                runOnUiThread(()->{try{
                    switch(action){
                        case "dismissSelection" -> {dismissTextSelection(true);finish(id,new JSONObject(),null);}
                        case "focusEditor" -> requestEditorKeyboard(id,p.getString("id"));
                        case "shareState" -> finish(id,ShareInbox.state(MainActivity.this),null);
                        case "cancelShare" -> finish(id,ShareInbox.cancel(MainActivity.this,p.getString("id")),null);
                        case "cancelHomeCandidates" -> {homeRequestEpoch.incrementAndGet();LocationSharing.cancelHomeCandidates();finish(id,new JSONObject(),null);}
                        case "openLink" -> {
                            if(!PilotApp.foreground)throw new IllegalStateException("Open Reply Pilot to follow the link.");
                            String url=LinkPreviewPolicy.openUrl(p.getString("url"));
                            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}
                            catch(ActivityNotFoundException unavailable){throw new IllegalStateException("No app is available to open this link.");}
                            finish(id,new JSONObject().put("opened",true),null);
                        }
                        case "role" -> requestSmsRole(id);
                        case "smsRoleStatus" -> {reconcileRoleRequest(false);finish(id,roleResult(),null);}
                        case "defaultSettings" -> {finishRoleRequest("settings");startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));finish(id,new JSONObject(),null);}
                        case "appSettings" -> {finishRoleRequest("cancelled");startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));finish(id,new JSONObject(),null);}
                        case "requestContacts" -> requestContacts(id);
                        case "requestLocation" -> requestLocation(id);
                        case "locationSettings" -> {startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));finish(id,new JSONObject(),null);}
                        case "importChatLog" -> importChatLog(id);
                        case "pickAttachments" -> pickAttachments(id,MediaHistoryPolicy.integer(p.opt("thread"),true));
                        case "openAttachment" -> openAttachment(id,MediaHistoryPolicy.integer(p.opt("partId"),true));
                        case "notificationSettings" -> {
                            if(Build.VERSION.SDK_INT>=33&&!Messages.allowed(MainActivity.this,Manifest.permission.POST_NOTIFICATIONS))requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3);
                            else startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,Notices.draftChannel(MainActivity.this)));
                            finish(id,new JSONObject(),null);
                        }
                        case "permissions" -> {permissions();finish(id,new JSONObject(),null);}
                        case "alarms" -> {startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));finish(id,new JSONObject(),null);}
                        case "battery" -> {openBatterySettings();finish(id,new JSONObject(),null);}
                        case "close" -> {finish(id,new JSONObject(),null);MainActivity.this.finish();}
                        case "approve" -> {
                            String address=p.getString("address"),body=p.getString("body");
                            DelayPolicy.Choice timing=DelayOptions.request(p,DelayPolicy.seconds(p.opt("delay")),"delayMode","delayMin","delayMax");
                            SendPolicy.validate(address,body,timing.minimumSeconds()*1000);
                            long selectedSeconds=DelayOptions.choose(timing),delay=selectedSeconds*1000;
                            String timingMessage="range".equals(timing.mode())
                                ?"Random range: "+(timing.min()/60)+"–"+(timing.max()/60)+" minutes.\nChosen delay: "+DelayPolicy.duration(selectedSeconds)+" after you confirm. You can cancel in Queue before sending starts."
                                :delay==0?"Sends immediately after you accept.":"Sends automatically in "+selectedSeconds+" seconds. You can cancel in Queue before sending starts.";
                            ApprovalDialog dialog=new ApprovalDialog(MainActivity.this);
                            dialog.setTitle(delay==0?"Accept & send now?":"Accept & start timer?");
                            dialog.setMessage("To: "+address+"\n\n"+body+"\n\n"+timingMessage);
                            dialog.setOnCancelListener(d->finish(id,null,null));
                            dialog.setButton(DialogInterface.BUTTON_NEGATIVE,"Keep editing",(d,w)->finish(id,null,null));
                            dialog.setButton(DialogInterface.BUTTON_POSITIVE,"Accept",(d,w)->{
                                try{SendWork.EXECUTOR.execute(()->{try{
                                    long t=p.getLong("thread");
                                    long job=Sender.accept(MainActivity.this,t,address,body,p.getLong("base"),p.getInt("sub"),delay);finish(id,new JSONObject().put("id",job).put("delay",selectedSeconds),null);
                                }catch(Exception e){finish(id,null,safeError(e));}});}
                                catch(java.util.concurrent.RejectedExecutionException busy){finish(id,null,"Sending is busy. Review the reply and try again.");}
                            });
                            dialog.show();
                        }
                    }
                }catch(Exception e){finish(id,null,safeError(e));}});return;
            }
            try{("importShare".equals(action)?ShareInbox.EXECUTOR:"retryMms".equals(action)?ReceiveWork.MMS:LiveInboxWork.handles(action)?LiveInboxWork.EXECUTOR:LiveHistoryWork.handles(action)?LiveHistoryWork.EXECUTOR:Arrays.asList("trainPersona","forgetPersona").contains(action)?Personas.EXECUTOR:Arrays.asList("replyProfile","saveProfile").contains(action)?ReplyProfile.EXECUTOR:Arrays.asList("cacheInbox","cacheHistory").contains(action)?HistoryArchive.EXECUTOR:"setTheme".equals(action)?ThemePreferences.EXECUTOR:"pinChat".equals(action)?PinnedChats.EXECUTOR:Arrays.asList("homeCandidates","confirmHomeCandidate","setHome").contains(action)?LocationSharing.HOME_EXECUTOR:"linkPreview".equals(action)?LinkPreviews.EXECUTOR:"launchHistories".equals(action)?LaunchHistoryCache.EXECUTOR:"launchInbox".equals(action)?LaunchInboxCache.EXECUTOR:"prefetchHistory".equals(action)?ConversationHistoryCache.EXECUTOR:"sendMms".equals(action)?MmsAttachments.EXECUTOR:SendWork.handles(action)?SendWork.EXECUTOR:Arrays.asList("generate","draftMmsText","suggestReply","analyzeMedia","testOpenAI","checkCloud").contains(action)?PilotApp.AI:PilotApp.IO).execute(()->{try{
                Object result=new JSONObject();long thread=p.optLong("thread");
                if(LiveHistoryWork.handles(action)||LiveInboxWork.handles(action))requireLiveHistory(requestLiveHistoryEpoch);
                switch(action){
                    case "setTheme" -> {
                        if(!(p.opt("theme") instanceof String selected))throw new IllegalArgumentException("Choose a theme.");
                        result=new JSONObject().put("theme",ThemePreferences.save(settings,selected));
                        runOnUiThread(()->{if(!isDestroyed())applyNativeTheme();});
                    }
                    case "homeCandidates" -> {
                        if(homeRequestEpoch.get()!=requestHomeEpoch||!PilotApp.foreground)throw new IllegalStateException("Address search ended. Try again.");
                        result=LocationSharing.requestCurrentHomeCandidates(MainActivity.this);
                    }
                    case "confirmHomeCandidate" -> {
                        if(homeRequestEpoch.get()!=requestHomeEpoch||!PilotApp.foreground)throw new IllegalStateException("Address search ended. Try again.");
                        result=LocationSharing.confirmHomeCandidate(MainActivity.this,p);Sender.reconcileLocation(MainActivity.this);
                    }
                    case "linkPreview" -> result=messageLinkPreview(p,requestLinkEpoch);
                    case "snapshot" -> result=snapshot();
                    case "liveInbox" -> result=liveInbox(requestLiveHistoryEpoch);
                    case "importShare" -> result=ShareInbox.add(getApplicationContext(),p);
                    case "replyProfile" -> result=ReplyProfile.read(MainActivity.this,MediaHistoryPolicy.integer(p.opt("thread"),true),p.has("expectedAddress")?p.getString("expectedAddress"):null);
                    case "cacheInbox" -> result=HistoryArchive.inbox(getApplicationContext(),p);
                    case "cacheHistory" -> result=HistoryArchive.history(getApplicationContext(),p);
                    case "refreshHistoryCache" -> result=HistoryArchive.retry(getApplicationContext());
                    case "trainPersona","forgetPersona" -> {
                        // Explicit owner action for this one chat. Nothing is trained in the background.
                        ReplyProfile.validateWrite(MainActivity.this,thread,p,Store.get(MainActivity.this).relationship(thread));
                        JSONObject persona="trainPersona".equals(action)?Personas.train(MainActivity.this,thread):Personas.forget(MainActivity.this,thread);
                        result=new JSONObject().put("persona",persona).put("replyEligibility",ReplyReadiness.json(ReplyReadiness.current(MainActivity.this,thread,"")));
                    }
                    case "setPremiumReplies" -> {
                        // Explicit owner choice for this one chat; costs more per reply.
                        ReplyProfile.validateWrite(MainActivity.this,thread,p,Store.get(MainActivity.this).relationship(thread));
                        if(!(p.opt("premium") instanceof Boolean on))throw new IllegalArgumentException("Choose on or off.");
                        result=new JSONObject().put("premiumReplies",ReplyModels.setPremium(MainActivity.this,thread,on));
                    }
                    case "launchInbox" -> result=LaunchInboxCache.load(getApplicationContext());
                    case "launchHistories" -> result=LaunchHistoryCache.load(getApplicationContext());
                    case "prefetchHistory" -> result=ConversationHistoryCache.prefetch(getApplicationContext(),p.getJSONArray("threads"));
                    case "saveLocation" -> {
                        if(!(p.opt("enabled") instanceof Boolean enabled))throw new IllegalArgumentException("Choose whether to enable location replies.");
                        LocationSharing.save(MainActivity.this,enabled);Sender.reconcileLocation(MainActivity.this);result=LocationSharing.state(MainActivity.this);
                    }
                    case "refreshLocation" -> {LocationSharing.refresh(MainActivity.this);result=LocationSharing.state(MainActivity.this);}
                    case "setHome" -> {LocationSharing.setHome(MainActivity.this,p);Sender.reconcileLocation(MainActivity.this);result=LocationSharing.state(MainActivity.this);}
                    case "clearHome" -> {LocationSharing.clearHome(MainActivity.this);Sender.reconcileLocation(MainActivity.this);result=LocationSharing.state(MainActivity.this);}
                    case "mediaConversation" -> result=annotateMmsPage(MediaNavigation.around(MainActivity.this,MediaHistoryPolicy.integer(p.opt("mediaId"),true),p));
                    case "attachmentState" -> result=MmsAttachments.state(MainActivity.this,MediaHistoryPolicy.integer(p.opt("thread"),true));
                    case "removeAttachment" -> result=MmsAttachments.remove(MainActivity.this,MediaHistoryPolicy.integer(p.opt("thread"),true),p.getString("id"));
                    case "sendMms" -> result=MmsAttachments.send(MainActivity.this,MediaHistoryPolicy.integer(p.opt("thread"),true),p.getString("address"),p.getString("caption"),p.getInt("sub"),MediaHistoryPolicy.integer(p.opt("base"),false),p.getString("requestId"));
                    case "retryMms" -> result=MmsDownloads.retry(MainActivity.this,MediaHistoryPolicy.integer(p.opt("mediaId"),true));
                    case "pinChat" -> {
                        Object pinned=p.opt("pinned");
                        if(!(pinned instanceof Boolean desired))throw new IllegalArgumentException("Choose Pin or Unpin for this conversation.");
                        PinnedChats.set(MainActivity.this,thread,desired);
                        result=new JSONObject().put("thread",thread).put("pinned",desired);
                        MessageChanges.publish();
                    }
                    case "analyzeChatLog" -> result=ReplyReadiness.analyze(MainActivity.this,thread,p.getString("body"),p.optString("ownerLabel"));
                    case "contacts" -> {finishContacts(id,Contacts.search(MainActivity.this,p.optString("query","")));return;}
                    case "testOpenAI" -> result=CloudDrafts.test(MainActivity.this,p.getString("message"),p.optString("relationship"),p.optString("examples"),p.optString("tone","Natural"));
                    case "connectCloud" -> {synchronized(PilotApp.SEND_LOCK){CloudConfig.save(MainActivity.this,p.getString("pairing"));AttentionActions.cancelAutomaticPlans(MainActivity.this);Sender.pauseAutomatic(MainActivity.this,"AI connection changed. Review this reply before starting another timer.");}result=CloudConfig.publicState(MainActivity.this);}
                    case "disconnectCloud" -> {synchronized(PilotApp.SEND_LOCK){CloudConfig.clear(MainActivity.this);AttentionActions.cancelAutomaticPlans(MainActivity.this);Sender.pauseAutomatic(MainActivity.this,"AI disconnected. Automatic sending stopped.");}}
                    case "checkCloud" -> result=CloudClient.request(MainActivity.this,"/health",null);
                    case "saveProfile" -> {
                        synchronized(PilotApp.SEND_LOCK){
                            Store store=Store.get(MainActivity.this);JSONObject saved=store.relationship(thread);
                            ReplyProfile.validateWrite(MainActivity.this,thread,p,saved);
                            int humor=ContactHumor.level(p.has("humorLevel")?p.opt("humorLevel"):saved.opt("humorLevel"));
                            String jokes=ContactHumor.notes(p.has("insideJokes")?p.opt("insideJokes"):saved.opt("insideJokes"));
                            Object suppliedImportant=p.has("importantDetails")?p.opt("importantDetails"):saved.optString("importantDetails");
                            Object suppliedPlans=p.has("planHandling")?p.opt("planHandling"):saved.optString("planHandling","ask_me");
                            if(!(suppliedImportant instanceof String important)||!(suppliedPlans instanceof String plans))throw new IllegalArgumentException("Enter Important details as text and choose how to handle plans.");
                            String importantDetails=ContactGuidance.details(important),planHandling=ContactGuidance.planHandling(plans);
                            long fixedDelay=p.has("autoDelay")?DelayPolicy.seconds(p.opt("autoDelay")):0;
                            if(fixedDelay!=0&&fixedDelay!=60&&fixedDelay!=300)throw new IllegalArgumentException("Choose Instant, 1 minute, or 5 minutes.");
                            boolean autopilot=p.optBoolean("cloudEnabled")&&p.optBoolean("autoDraft")&&p.optBoolean("autoSend");
                            result=store.profile(thread,p.optString("body"),p.optString("samples"),autopilot,autopilot,"", "Use AI intuition",autopilot,fixedDelay,DelayPolicy.choice("fixed",fixedDelay,DelayPolicy.DEFAULT_MIN,DelayPolicy.DEFAULT_MAX),"always_reply",p.optBoolean("shareLocation",false),0,"",importantDetails,"delay_answer");
                        }
                        RecentContext.forget(thread);RecentContext.prepareEnabled(MainActivity.this);
                    }
                    case "conversation" -> result=conversation(thread,p.optJSONArray("receiptIds"));
                    case "historyPage" -> result=annotateMmsPage(MediaNavigation.latest(MainActivity.this,thread,p));
                    case "suggestReply" -> {ForegroundSuggestions.generate(MainActivity.this,thread,p.getLong("base"));result=conversation(thread,null);}
                    case "analyzeMedia" -> result=MediaAnalysis.analyze(MainActivity.this,thread,MediaHistoryPolicy.integer(p.opt("mediaId"),true),p.optBoolean("inApp",false));
                    case "clearApprovedLearning" -> result=ApprovedLearning.clear(MainActivity.this,thread);
                    case "saveOwnerViews" -> result=new JSONObject().put("ownerViews",OwnerViews.save(MainActivity.this,p.optString("text")));
                    case "saveRelationship" -> result=Store.get(MainActivity.this).relationship(thread,p.getString("body"));
                    case "compose" -> {String address=p.getString("address");if(!SendPolicy.validAddress(address))throw new IllegalArgumentException("Enter one phone number, including country code if needed.");if(!Messages.role(MainActivity.this))throw new IllegalStateException("Set up the default SMS app first.");result=new JSONObject().put("thread",Messages.thread(MainActivity.this,address)).put("name",Messages.name(MainActivity.this,address));}
                    case "sendNow" -> result=Sender.sendNow(MainActivity.this,p.getLong("thread"),p.getString("address"),p.getString("body"),p.optLong("base",0),p.getInt("sub"),p.getString("requestId"));
                    case "sendState" -> result=Sender.sendState(MainActivity.this,MediaHistoryPolicy.integer(p.opt("thread"),true));
                    case "draftMmsText" -> {
                        requireLiveHistory(requestLiveHistoryEpoch);
                        result=TextMmsDrafts.generate(MainActivity.this,MediaHistoryPolicy.integer(p.opt("thread"),true),MediaHistoryPolicy.integer(p.opt("base"),false),MediaHistoryPolicy.integer(p.opt("textMmsId"),true),p.optBoolean("inApp",false));
                    }
                    case "generate" -> {
                        String tone=ReplyPrompt.normalizeTone(p.optString("tone","Natural"));boolean automatic=p.optBoolean("automatic");long base=p.getLong("base");
                        boolean waiting=automatic&&!IncomingBurst.ready(MainActivity.this,thread,base,Messages.address(MainActivity.this,thread,base));
                        if(waiting)DraftJob.schedule(MainActivity.this,thread,base);
                        else{CloudDrafts.generate(MainActivity.this,thread,base,tone,automatic);waiting=automatic&&!IncomingBurst.ready(MainActivity.this,thread,base,Messages.address(MainActivity.this,thread,base));}
                        result=draftResult(thread,base,waiting,requestLiveHistoryEpoch);
                    }
                    case "saveDraft" -> {
                        String body=p.getString("body");if(body.length()>1600)throw new IllegalArgumentException("Draft is too long.");
                        synchronized(PilotApp.SEND_LOCK){
                            if(Messages.latest(MainActivity.this,thread)!=p.getLong("base"))throw new IllegalStateException("A new message arrived. Open the latest conversation before editing.");
                            if(Store.get(MainActivity.this).query("SELECT _id FROM jobs WHERE thread=? AND status='scheduled' AND auto_send=0",new String[]{""+thread}).length()>0)throw new IllegalStateException("Cancel the active timer before editing this reply.");
                            Sender.cancelAutomaticForThread(MainActivity.this,thread,"Automatic timer cancelled because you edited the reply.");
                            Store.get(MainActivity.this).draft(thread,p.getLong("base"),body,new JSONArray(),"Edited by you");
                            Store.get(MainActivity.this).clearReplyDecision(thread,p.getLong("base"));
                        }
                    }
                    case "cancel" -> {if(!Sender.cancel(MainActivity.this,p.getLong("id")))throw new IllegalStateException("Sending already started or this reply is no longer scheduled.");}
                    case "settings" -> {
                        synchronized(PilotApp.SEND_LOCK){
                            boolean automatic=p.optBoolean("autoDraft",settings.getBoolean("autoDraft",true));
                            int sub=p.optInt("sub",settings.getInt("sub",-1));
                            boolean previews=p.optBoolean("lockScreenPreviews",settings.getBoolean("lockScreenPreviews",true));
                            boolean previewChanged=previews!=settings.getBoolean("lockScreenPreviews",true);
                            boolean stopped=settings.getBoolean("autoDraft",true)&&!automatic;
                            boolean simChanged=p.has("sub")&&sub!=settings.getInt("sub",-1);
                            int fixedDelay=(int)DelayPolicy.fixedSeconds(p.has("delay")?DelayPolicy.seconds(p.opt("delay")):settings.getInt("delay",300),false);
                            boolean timingChanged=p.has("delay")||p.has("delayMode")||p.has("delayMin")||p.has("delayMax");
                            DelayPolicy.Choice timing=timingChanged?DelayOptions.request(p,fixedDelay,"delayMode","delayMin","delayMax")
                                :DelayPolicy.choice(settings.getString("delayMode","fixed"),fixedDelay,settings.getLong("delayMin",DelayPolicy.DEFAULT_MIN),settings.getLong("delayMax",DelayPolicy.DEFAULT_MAX));
                            settings.edit().putInt("sub",sub).putBoolean("autoDraft",automatic)
                                .putBoolean("matchMyStyle",p.optBoolean("matchMyStyle",settings.getBoolean("matchMyStyle",true)))
                                .putBoolean("inAppSuggestions",p.optBoolean("inAppSuggestions",settings.getBoolean("inAppSuggestions",true)))
                                .putBoolean("linkPreviews",p.optBoolean("linkPreviews",settings.getBoolean("linkPreviews",true)))
                                .putString("tone",ReplyPrompt.normalizeTone(p.optString("tone",settings.getString("tone","Natural"))))
                                .putInt("delay",fixedDelay).putString("delayMode",timing.mode()).putLong("delayMin",timing.min()).putLong("delayMax",timing.max())
                                
                                .putBoolean("lockScreenPreviews",previews).apply();
                            if(!settings.getBoolean("linkPreviews",true)){linkRequestEpoch.incrementAndGet();LinkPreviews.clear();}
                            if(!settings.getBoolean("matchMyStyle",true))RecentContext.clear();
                            if(stopped||simChanged){AttentionActions.cancelAutomaticPlans(MainActivity.this);Sender.pauseAutomatic(MainActivity.this,stopped?"Automatic replies turned off. Review this reply before sending.":"Sending SIM changed. Review this reply before sending.");}
                            if(previewChanged)Notices.refreshScheduled(MainActivity.this);
                            runOnUiThread(()->applyNativeTheme());
                        }
                    }
                    case "media" -> result=annotateMms(Messages.multimedia(MainActivity.this));
                    default -> throw new IllegalArgumentException("Unknown action.");
                }if("importShare".equals(action))finishShare(id,(JSONObject)result);else if(Arrays.asList("generate","draftMmsText").contains(action))finishDraftResult(id,(JSONObject)result,requestLiveHistoryEpoch);else if(LiveInboxWork.handles(action))finishLiveInbox(id,(JSONObject)result,requestLiveHistoryEpoch);else if(LiveHistoryWork.handles(action))finishLiveHistory(id,(JSONObject)result,requestLiveHistoryEpoch);else if(Arrays.asList("cacheInbox","cacheHistory").contains(action))finishArchive(id,(JSONObject)result);else if("replyProfile".equals(action))finishReplyProfile(id,(JSONObject)result);else if("homeCandidates".equals(action))finishHomeCandidates(id,(JSONObject)result,requestHomeEpoch);else if("linkPreview".equals(action))finishLinkPreview(id,(JSONObject)result,requestLinkEpoch);else if("launchHistories".equals(action))finishLaunchHistories(id,(JSONObject)result);else if("launchInbox".equals(action))finishLaunchInbox(id,(JSONObject)result);else if("prefetchHistory".equals(action))finishHistoryPreview(id,(JSONObject)result);else finish(id,result,null);
            }catch(Exception e){finish(id,null,safeError(e));}});}
            catch(java.util.concurrent.RejectedExecutionException busy){finish(id,null,LiveInboxWork.handles(action)?"Messages are still updating. Try again in a moment.":LiveHistoryWork.handles(action)?"This conversation is still updating. Try again in a moment.":"The app is busy. Wait a moment and try again.");}
        }
    }
    private static int themeBackground(String value){
        String color=ThemePreferences.background(value);
        return Color.parseColor(color);
    }
    private void applyNativeTheme(){
        String selected=theme(settings.getString("theme","midnight"));int background=themeBackground(selected);web.setBackgroundColor(background);
        if(contentFrame!=null)contentFrame.setBackgroundColor(background);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
        WindowInsetsController controller=getWindow().getInsetsController();
        if(controller!=null){int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(0,light);}
    }
    private static String theme(String value){return ThemePreferences.theme(value);}
    private static String safeError(Exception e){if(e instanceof IllegalArgumentException||e instanceof IllegalStateException)return e.getMessage();return "This action could not finish. Check permissions and try again.";}
}
