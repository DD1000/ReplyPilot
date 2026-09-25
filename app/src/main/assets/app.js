/* Packaged UI. All phone operations go through the restricted native bridge. */
const icons={chat:'<path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-2 2V11.5a9.5 9.5 0 0 1 19 0Z"/><path d="M7 10h8M7 14h5"/>',spark:'<path d="m12 2 2.5 6.5L21 11l-6.5 2.5L12 20l-2.5-6.5L3 11l6.5-2.5L12 2ZM20 2v4M18 4h4"/>',clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',settings:'<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="8" cy="6" r="2" fill="currentColor"/><circle cx="16" cy="12" r="2" fill="currentColor"/><circle cx="9" cy="18" r="2" fill="currentColor"/>',shield:'<path d="m12 2 8 3v6c0 5-4 8-8 11-4-3-8-6-8-11V5l8-3Z"/><path d="m8 12 3 3 5-6"/>',arrow:'<path d="M5 12h14m-5-5 5 5-5 5"/>',back:'<path d="m14 5-7 7 7 7"/>',plus:'<path d="M12 5v14M5 12h14"/>',check:'<path d="m5 12 4 4L19 6"/>',search:'<circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/>',refresh:'<path d="M20 7v5h-5M4 17v-5h5"/><path d="M19 8a8 8 0 0 0-14-2M5 16a8 8 0 0 0 14 2"/>',send:'<path d="m3 3 19 9-19 9 4-9-4-9Zm4 9h15"/>',media:'<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="8" cy="8" r="1"/><path d="m3 17 5-5 4 4 4-6 5 6"/>',close:'<path d="m6 6 12 12M18 6 6 18"/>',chevron:'<path d="m6 9 6 6 6-6"/>',lock:'<rect x="5" y="10" width="14" height="11" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>'};
icons.checks='<path d="m2 12 4 4L16 6M10 16l2 2L22 8"/>';
icons.attach='<path d="m9 17 8-8a3 3 0 0 0-4-4L4 14a5 5 0 0 0 7 7l9-9a7 7 0 0 0-10-10L2 10"/>';
icons.pin='<path d="m9 3 6 0-1 6 4 4v2H6v-2l4-4-1-6ZM12 15v7"/>';
icons.more='<circle cx="12" cy="5" r="1" fill="currentColor"/><circle cx="12" cy="12" r="1" fill="currentColor"/><circle cx="12" cy="19" r="1" fill="currentColor"/>';
const icon=n=>`<svg viewBox="0 0 24 24" aria-hidden="true">${icons[n]||icons.chat}</svg>`;
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const themes=[['forest','Forest'],['ocean','Ocean'],['lavender','Lavender'],['rose','Rose'],['sunset','Sunset'],['slate','Slate'],['midnight','Midnight'],['mocha','Mocha'],['mint','Mint'],['plum','Plum']];
const initialTheme=themes.some(([id])=>id===document.documentElement.dataset.theme)?document.documentElement.dataset.theme:'midnight';
const timerOptions=[[60,'1 minute'],[300,'5 minutes']];
const timerText=n=>n===0?'Immediately':n===1?'1 second':n>=3600&&n%3600===0?`${n/3600} ${n===3600?'hour':'hours'}`:`${Math.round(n/60)} min`;
const autopilotTimers=[[0,'Instant'],[60,'1 minute'],[300,'5 minutes']];
const native=!!window.Native, pending=new Map();let sequence=0;
window.nativeResult=(id,value,error)=>{const p=pending.get(id);if(!p)return;pending.delete(id);error?p.reject(new Error(error)):p.resolve(value);};
function api(action,p={}){if(!native)return window.demoApi(action,p);return new Promise((resolve,reject)=>{const id=String(++sequence);pending.set(id,{resolve,reject});Native.call(id,action,JSON.stringify(p));});}
let state={defaultSms:false,permissions:false,exact:false,notifications:false,sims:[],sub:-1,inbox:[],jobs:[],cloud:{configured:false},tone:'Natural',delay:300,autoDraft:true,matchMyStyle:true,theme:initialTheme,lockScreenPreviews:true,inAppSuggestions:true,linkPreviews:true};
const inboxBootstrap={authoritative:false,cached:false,error:'',retrying:false};
let launchAccessStamp=0,launchOpenedThread=null;
const launchThreads=new Set(),launchHistoryPages=new Map();let launchHistoryEpoch=0;
const archiveHistories=new Map(),archiveInbox={epoch:0,ready:false,loading:false,hasMore:true,cursor:null,snapshot:null,error:''};let inboxRenderLimit=120;
let busyThread=null;
let sendingNow=false,chatLoadPending=false,chatLoadSlow=false,chatLoadError='',attentionRequest=null;
let messageUpdatePending=false,messageUpdateRunning=null;
let inboxReadSequence=0,inboxFullRead=0,liveInboxRequest=null,liveInboxPending=false,liveInboxEpoch=0;
const liveInboxUpdates=new Map();
let pinMenu=null,pinPress=null,suppressedPinClick=null;
let inboxTouch=null,inboxRenderPending=false,inboxListPending=false;
const pinMutations=new Map(),pinTasks=new Map(),pinConfirmed=new Map(),pinFailures=new Map();
let mutationSequence=0,snapshotSequence=0,pinGeneration=0,themeMutation=null,themeTask=Promise.resolve(),themeSaved=initialTheme,themeQueued=null,themeWriting=false,themePointer=null,themeRenderPending=false;
const contactPicker={query:'',items:[],loading:false,error:'',hasMore:false,blocked:false,permissionBusy:false,opening:false,request:0,timer:null};
let sharedContent=null,shareCheck=0;const dismissedShares=new Set();
let mediaLoading=false,mediaLoaded=false,mediaError='',mediaLoadedAt=0,mediaRequest=null;
let mediaBrowse=null;
const attachmentRequests=new Map(),attachmentFeedback=new Map(),mmsAttempts=new Map(),downloadRequests=new Map();
const suggestionAttempts=new Set(),mediaInsights=new Map();let suggestionRequest=null,approvedLearningBusy=null;
let draftRequest=null,composerEditRevision=0;
const manualTakeovers=new Map(),manualSendAttempts=new Map(),manualSendRequests=new Map(),manualSendOutcomes=new Map();
const MEDIA_CACHE_MS=30000;
const linkPreviewCache=new Map(),linkPreviewQueue=[];
const LINK_PREVIEW_LIMIT=80;let linkPreviewActive=0,linkPreviewGeneration=0,linkPreviewFrame=0,linkPreviewObserver=null,linkPreviewPaused=false;
let page='inbox',current=null,conversation=null,draft='',dirty=false,tone='Natural',delay=300,delayMode='fixed',delayMin=300,delayMax=1800,custom=false,timerOpen=false,busy=false,search='',compose=false,media=[],lastSignature='',toastTimer;
const root=document.getElementById('app');
const relationshipEdits=new Map(),replyProfiles=new Map();
let pairingText='',cloudStatus='';
let roleMessage='',roleBusy=false,historyCacheRefreshing=false,historyCacheError='';
let roleAttempt=0,roleStatusRevision=0,roleCheckRequest=0,roleChecking=false;
let preferences=null;
let locationBusy=false,locationFeedback='',homeEdit=null,locationEnabledPending=null;
let homeCandidates=null,homeReceipt=null;
const draftEdits=new Map();let composerFrame=0,composing=false,refreshing=null;
const threadHistories=new Map();let threadEpoch=0,historyGapSequence=0;
const chatPreviews=new Map(),previewDrafts=new Map();
const PREVIEW_LIMIT=24,PREVIEW_BYTES=4*1024*1024;
let previewGeneration=0,privateGeneration=0,previewTimer=null,previewRequest=null,previewDenied=false,accessHint=null,contactsDenied=false;
const conversationReads=new Map(),previewWarmAttempts=new Set();
function nextConversationRead(thread){const value=(conversationReads.get(thread)||0)+1;conversationReads.set(thread,value);return value;}
function liveHistoryReady(){return !!conversation&&!conversation.previewOnly&&!chatLoadPending&&!chatLoadError&&!previewDenied;}
function chatReady(){return inboxBootstrap.authoritative&&liveHistoryReady();}
function previewSize(value){try{return JSON.stringify(value).length*2;}catch{return PREVIEW_BYTES+1;}}
function trimChatPreviews(){
 let bytes=0;for(const [thread,item] of chatPreviews){const history=threadHistories.get(thread)?.messages;item.bytes=previewSize(history&&thread!==current?.thread_id?{...item.value,history}:item.value);bytes+=item.bytes;}
 for(const [thread,item] of chatPreviews){if(chatPreviews.size<=PREVIEW_LIMIT&&bytes<=PREVIEW_BYTES)break;if(thread===current?.thread_id)continue;chatPreviews.delete(thread);threadHistories.delete(thread);bytes-=item.bytes;}
}
function rememberChat(thread,value,live=false,position=null){
 if(previewDenied||!Number.isSafeInteger(thread)||!value||!Array.isArray(value.history))return;
 const old=chatPreviews.get(thread),cached={...value,previewOnly:undefined};if(previewSize(cached)>PREVIEW_BYTES)cached.history=cached.history.slice(-40);const entry={value:cached,live,position:position||old?.position||null,generation:previewGeneration,bytes:previewSize(cached)};
 chatPreviews.delete(thread);chatPreviews.set(thread,entry);trimChatPreviews();
}
function captureChat(){if(!current||!conversation||previewDenied)return;if(!chatReady()&&dirty)previewDrafts.set(current.thread_id,{body:draft});if(chatReady())rememberChat(current.thread_id,{...conversation,draft:dirty?{...conversation.draft,body:draft,engine:'Edited by you'}:conversation.draft},true,timelinePosition());}
function invalidatePreviews(preserveSaved=false){previewGeneration++;previewWarmAttempts.clear();clearTimeout(previewTimer);previewTimer=null;for(const [thread,item] of chatPreviews)if(!item.live&&!(preserveSaved&&item.launchSaved)){chatPreviews.delete(thread);if(thread!==current?.thread_id)threadHistories.delete(thread);}}
function clearPrivateChats(){
 resetArchiveReads();state.historyCache=null;historyCacheError='';sendJobUpdates.clear();sendStateRevision++;sendStateRequest=null;
 liveInboxEpoch++;liveInboxPending=false;liveInboxRequest=null;liveInboxUpdates.clear();
 manualSendRequests.clear();manualSendAttempts.clear();manualTakeovers.clear();manualSendOutcomes.clear();composerEditRevision++;if(draftRequest){draftRequest=null;busy=false;busyThread=null;}
 clearLinkPreviews();pinGeneration++;pinMutations.clear();pinTasks.clear();pinConfirmed.clear();pinFailures.clear();closePinMenu(false);
 inboxBootstrap.cached=false;launchHistoryPages.clear();launchHistoryEpoch++;launchThreads.clear();launchOpenedThread=null;
 invalidatePreviews();privateGeneration++;previewDenied=true;threadEpoch++;stopHistoryRequest();chatPreviews.clear();threadHistories.clear();previewDrafts.clear();relationshipEdits.clear();replyProfiles.clear();suggestionAttempts.clear();mediaInsights.clear();attachmentFeedback.clear();attachmentRequests.clear();downloadRequests.clear();mmsAttempts.clear();
 for(const edit of draftEdits.values()){edit.obsolete=true;clearTimeout(edit.timer);}draftEdits.clear();
 media=[];mediaLoaded=false;mediaLoading=false;mediaError='';mediaBrowse=null;mediaRequest=null;current=null;conversation=null;draft='';dirty=false;chatLoadPending=false;chatLoadError='';timerOpen=false;state.inbox=[];state.jobs=[];closeContacts();if(page==='media-thread')page='inbox';render();
}
function syncChatHeader(){const node=document.querySelector('.conversation .chat-header');if(!node||!current)return;const template=document.createElement('template');template.innerHTML=chatHeaderView(relationshipEdit());node.replaceWith(template.content);}
function queuePreviewWarm(){
 if(previewTimer||previewRequest||previewDenied||state.permissions!==true||document.visibilityState==='hidden')return;
 previewTimer=setTimeout(()=>{previewTimer=null;warmChatPreviews();},180);
}
async function warmChatPreviews(){
 if(previewRequest||previewDenied||state.permissions!==true||document.visibilityState==='hidden')return;
 const visible=[...document.querySelectorAll('.row[data-thread]')].filter(node=>{const r=node.getBoundingClientRect();return r.bottom>0&&r.top<window.innerHeight;}).map(node=>Number(node.dataset.thread));
 const rows=state.inbox||[],ordered=[current?.thread_id,...visible,...rows.filter(isPinned).map(row=>row.thread_id),...rows.map(row=>row.thread_id)];
 const threads=[...new Set(ordered)].filter(Number.isSafeInteger).slice(0,PREVIEW_LIMIT).filter(thread=>thread!==current?.thread_id&&chatPreviews.get(thread)?.generation!==previewGeneration&&!previewWarmAttempts.has(thread)).slice(0,3);
 if(!threads.length)return;threads.forEach(thread=>previewWarmAttempts.add(thread));const request={generation:previewGeneration};previewRequest=request;
 try{const result=await api('prefetchHistory',{threads});if(request.generation!==previewGeneration||previewDenied)return;
  for(const item of result?.conversations||[]){if(!threads.includes(item.thread)||!state.inbox.some(row=>row.thread_id===item.thread)||!Array.isArray(item.page?.history))continue;const existing=chatPreviews.get(item.thread);if(existing?.live){existing.generation=previewGeneration;continue;}rememberChat(item.thread,item.page,false);}
 }catch{}finally{if(previewRequest===request)previewRequest=null;if(request.generation===previewGeneration)queuePreviewWarm();}
}
function provisionalChat(row){
 const entry=chatPreviews.get(row.thread_id);if(entry){chatPreviews.delete(row.thread_id);chatPreviews.set(row.thread_id,entry);}
 const stored=entry?.value;let history=threadHistories.get(row.thread_id)?.messages?.length?threadHistories.get(row.thread_id).messages:stored?.history?.length?stored.history:Number.isSafeInteger(Number(row._id))&&Number(row._id)>0?[{...row,previewSummary:true}]:[];
 if(history.length&&Number(row._id)>0&&row.date>=history.at(-1).date&&!history.some(message=>messageKey(message)===messageKey(row)))history=[...history,{...row,previewSummary:true}];
 return {...stored,history,base:stored?.base||0,relationship:stored?.relationship||{},draft:stored?.draft||null,previewOnly:true};
}
function hydrationNoticeView(){if(chatReady())return '';if(!inboxBootstrap.authoritative&&inboxBootstrap.error)return '<div id="chat-hydration" class="chat-hydration" role="status"><span>Couldn’t update messages. Your typing is kept.</span><button class="text-button" data-action="retry-inbox">Retry</button></div>';return `<div id="chat-hydration" class="chat-hydration" role="status">${chatLoadError||chatLoadSlow?`<span>${chatLoadSlow?'Your phone is taking longer to update this chat.':'Couldn’t update this conversation.'} Your typing is kept.</span><button class="text-button" data-action="retry-conversation">Retry</button>`:'<span>You can write while this conversation updates.</span>'}</div>`;}
function syncHydrationNotice(){const old=document.getElementById('chat-hydration');if(chatReady()){old?.remove();return;}const template=document.createElement('template');template.innerHTML=hydrationNoticeView();if(old)old.replaceWith(template.content);else document.querySelector('.composer')?.prepend(template.content);}
window.onMessageAccessChanged=access=>{
 launchAccessStamp++;
 if(typeof access?.defaultSms==='boolean')applySmsRoleStatus({defaultSms:access.defaultSms,...(access.readSms===false?{permissions:false}:{})});
 if(access?.readSms===false||access?.defaultSms===false){accessHint=false;if(!previewDenied)clearPrivateChats();return;}
 if(access?.readSms===true&&access?.defaultSms===true){accessHint=true;previewDenied=false;}
 if(access?.contacts===true)contactsDenied=false;
 if(access?.contacts===false){contactsDenied=true;clearContactPhotos();privateGeneration++;liveInboxEpoch++;liveInboxUpdates.clear();liveInboxPending=false;liveInboxRequest=null;invalidateLaunchHistories();invalidatePreviews();mediaRequest=null;mediaLoading=false;mediaLoadedAt=0;state.contactsAllowed=false;contactPicker.request++;contactPicker.items=[];contactPicker.loading=false;syncContactsView();for(const row of state.inbox)row.name=row.address;for(const record of replyProfiles.values())if(record.data){record.data.name=record.data.address;record.data.photo='';}for(const record of archiveHistories.values()){record.snapshot=null;record.error='';}for(const item of chatPreviews.values()){item.value.name=item.value.address;item.value.contactName='';}if(current){current={...current,name:current.address};syncChatHeader();}for(const row of media)row.name=row.address||'Conversation';if(mediaBrowse)mediaBrowse.name=mediaBrowse.address||'Conversation';syncInboxList();if(page==='media')syncMediaView(true);if(page==='media-thread')render();}
};
// The disk archive supplies display content only. Live history still owns sending.
function archiveAccess(result){return result?.access?.readSms===true&&result?.access?.defaultSms===true&&!previewDenied&&accessHint!==false;}
function resetArchiveReads(){archiveInbox.epoch++;archiveInbox.loading=false;archiveInbox.ready=false;archiveInbox.cursor=null;archiveInbox.snapshot=null;archiveInbox.hasMore=true;archiveInbox.error='';archiveHistories.clear();}
async function readSavedPage(action,request,isCurrent){
 try{return await api(action,request);}
 catch(error){
  // A committed refresh may replace the archive between pages. Retry once at
  // the same keyset boundary; the current address/fingerprint is still checked.
  if(!request.snapshot||error.message!=='Saved conversations updated. Open the latest cached page.'||!isCurrent())throw error;
  const retry={...request};delete retry.snapshot;return api(action,retry);
 }
}
async function loadArchiveInbox(){
 if(inboxBootstrap.authoritative||archiveInbox.loading||!archiveInbox.hasMore||previewDenied)return;
 const epoch=archiveInbox.epoch,generation=privateGeneration,access=launchAccessStamp,cursor=archiveInbox.cursor;archiveInbox.loading=true;archiveInbox.error='';syncInboxList();
 const isCurrent=()=>epoch===archiveInbox.epoch&&generation===privateGeneration&&access===launchAccessStamp&&!inboxBootstrap.authoritative&&!previewDenied;
 try{const result=await readSavedPage('cacheInbox',{limit:60,...(cursor?{cursor,snapshot:archiveInbox.snapshot}:{})},isCurrent);if(!isCurrent()||!archiveAccess(result))return;if(result.archivePending===true)return;
  const rows=launchInboxRows(result.inbox,result.access.contacts===true&&!contactsDenied,60);if(!rows.length&&!result.hasMore)return;
  if(result.access.contacts===false){contactsDenied=true;clearContactPhotos();}archiveInbox.ready=true;archiveInbox.snapshot=result.snapshot;archiveInbox.cursor=result.nextCursor;archiveInbox.hasMore=!!result.hasMore&&!!result.nextCursor;
  const byThread=new Map((cursor?state.inbox:[]).map(row=>[row.thread_id,row]));for(const row of rows){byThread.set(row.thread_id,row);launchThreads.add(row.thread_id);}state.inbox=[...byThread.values()];overlayLiveInbox(state,0);inboxBootstrap.cached=true;applyLaunchHistories();syncInboxBootstrap();
 }catch(error){if(isCurrent())archiveInbox.error='More saved chats couldn’t load. Try again.';}
 finally{if(epoch===archiveInbox.epoch){archiveInbox.loading=false;syncInboxList();}}
}
function matchingInboxRows(){return state.inbox.filter(row=>(row.name+' '+row.address+' '+row.body).toLowerCase().includes(search.toLowerCase()));}
function inboxPagingView(total){const more=total>inboxRenderLimit,archiveMore=!inboxBootstrap.authoritative&&archiveInbox.ready&&archiveInbox.hasMore;if(!more&&!archiveMore&&!archiveInbox.error)return '';return `<div class="inbox-page-control"><button class="text-button" data-action="more-chats" ${archiveInbox.loading?'disabled':''}>${more?'More conversations':archiveInbox.loading?'Opening saved chats…':archiveInbox.error?'Retry saved chats':search?'Search more saved chats':'More saved conversations'}</button>${archiveInbox.error?`<p role="status">${esc(archiveInbox.error)}</p>`:''}</div>`;}
function moreInboxRows(){const count=matchingInboxRows().length;if(count>inboxRenderLimit){inboxRenderLimit+=120;syncInboxList();}else loadArchiveInbox();}
function archivePage(thread,result,address){
 if(!archiveAccess(result)||result.thread!==thread||result.address!==address||!Array.isArray(result.history))return null;
 const history=[];for(const row of result.history.slice(0,80)){if(!row||row.thread_id!==thread||!Number.isSafeInteger(row._id)||row._id<=0||!['sms','mms'].includes(row.kind)||!Number.isFinite(row.date)||typeof row.body!=='string'||![1,2,4,5,6].includes(row.type))continue;
  const copy={_id:row._id,thread_id:thread,kind:row.kind,key:`${row.kind}:${row._id}`,type:row.type,date:row.date,body:row.body,read:row.read===0?0:1,cachedOnly:true,truncated:row.truncated===true,textUnavailable:row.textUnavailable===true};
  if(row.kind==='mms'){copy.m_type=row.m_type;copy.msg_box=row.msg_box;copy.parts=(Array.isArray(row.parts)?row.parts:[]).slice(0,50).filter(part=>part&&Number.isSafeInteger(Number(part._id))&&Number(part._id)>0&&typeof part.ct==='string').map(part=>({_id:Number(part._id),ct:part.ct,text:typeof part.text==='string'?part.text:'',textUnavailable:part.textUnavailable===true}));}history.push(copy);
 }
 return {thread,address,name:result.access.contacts===true&&!contactsDenied?result.name:address,photo:result.access.contacts===true?contactPhotoUrl(result.photo):'',history:history.sort(compareHistoryPoint),hasMore:!!result.hasMore||!!result.hasOlder,before:result.before,snapshot:result.snapshot,recipientReadOnly:result.recipientReadOnly===true,contactFingerprint:typeof result.contactFingerprint==='string'?result.contactFingerprint:''};
}
async function loadArchiveHistory(thread=current?.thread_id,older=false){
 if(!thread||thread!==current?.thread_id||previewDenied)return;const record=archiveHistories.get(thread)||{address:current.address||'',loading:false,hasMore:false,cursor:null,snapshot:null,error:''};if(record.loading||older&&(!record.hasMore||!record.cursor))return;archiveHistories.set(thread,record);
 const epoch=threadEpoch,generation=privateGeneration,access=launchAccessStamp,cursor=older?record.cursor:null;record.loading=true;record.error='';record.retryOlder=older;if(older)updateTimeline();
 const isCurrent=()=>current?.thread_id===thread&&epoch===threadEpoch&&generation===privateGeneration&&access===launchAccessStamp&&archiveHistories.get(thread)===record&&conversation?.previewOnly===true&&!previewDenied;
 try{const result=await readSavedPage('cacheHistory',{thread,expectedAddress:record.address,limit:40,...(cursor?{cursor,snapshot:record.snapshot}:{})},isCurrent);if(!isCurrent())return;
  if(result?.archivePending===true&&archiveAccess(result)){record.pending=true;record.hasMore=false;record.cursor=null;record.snapshot=null;return;}
  const saved=archivePage(thread,result,record.address);if(!saved)return;
  record.pending=false;
  if(cursor&&saved.history.some(row=>compareHistoryPoint(row,cursor)>=0)||saved.hasMore&&(!saved.before||cursor&&compareHistoryPoint(saved.before,cursor)>=0))throw new Error('Saved history did not advance.');
  if(record.contactFingerprint&&saved.contactFingerprint&&record.contactFingerprint!==saved.contactFingerprint||record.recipientReadOnly!==undefined&&record.recipientReadOnly!==saved.recipientReadOnly){const stale=historyRecord(thread);stale.messages=[];stale.gaps=[];stale.initialized=false;stale.windowEnd=null;chatPreviews.delete(thread);launchHistoryPages.delete(thread);}
  record.snapshot=saved.snapshot;record.recipientReadOnly=saved.recipientReadOnly;record.contactFingerprint=saved.contactFingerprint;record.hasMore=saved.hasMore;record.cursor=saved.before;
  const history=historyRecord(thread),existing=new Set(history.messages.map(messageKey));
  // A fresh archive page must not shrink an already-opened conversation back to
  // forty rows. Keep verified rows and merge display-only additions by identity.
  mergeHistory(history,saved.history.filter(row=>!existing.has(messageKey(row))));
  if(older)showOldestHistoryWindow(history);else{const row=current,latest=history.messages.at(-1);if(Number.isSafeInteger(row._id)&&(!latest||compareHistoryPoint(row,latest)>0)&&!history.messages.some(item=>messageKey(item)===messageKey(row)))mergeHistory(history,[{...row,cachedOnly:true,previewSummary:true}]);}
  conversation={...conversation,history:historyRecord(thread).messages,archiveOnly:true,archiveAddress:saved.address,previewOnly:true};if(saved.photo)current.photo=saved.photo;updateTimeline();syncChatHeader();
 }catch(error){if(isCurrent())record.error='Saved history couldn’t load. Try again.';}
 finally{if(archiveHistories.get(thread)===record){record.loading=false;if(current?.thread_id===thread&&epoch===threadEpoch&&!chatReady())updateTimeline();}}
}
function archiveHistoryControl(){const record=archiveHistories.get(current?.thread_id);if(!record||!record.hasMore&&!record.error&&!record.pending)return '';return `<div class="history-control"><button class="text-button" data-action="older-cached" ${record.loading?'disabled':''}>${record.loading?'Opening saved history…':record.error?'Retry saved history':record.pending?'Check saved history':'Earlier messages'}</button>${record.error?`<p class="history-error" role="status">${esc(record.error)}</p>`:record.pending?'<p role="status">Saving your chat history on this phone…</p>':''}</div>`;}
function dropWrongArchive(thread,remote){const cached=archiveHistories.get(thread);if(!cached||typeof remote.address!=='string')return;if(remote.address===cached.address&&!!remote.readOnly===!!cached.recipientReadOnly&&(!cached.contactFingerprint||!remote.contactFingerprint||cached.contactFingerprint===remote.contactFingerprint))return;archiveHistories.delete(thread);replyProfiles.delete(thread);relationshipEdits.delete(thread);const record=historyRecord(thread);record.messages=[];record.gaps=[];record.initialized=false;record.windowEnd=null;chatPreviews.delete(thread);launchHistoryPages.delete(thread);}
function historyWindow(messages=conversation?.history||[]){const record=historyRecord();let end=record.windowEnd?messages.findIndex(row=>messageKey(row)===record.windowEnd)+1:messages.length;if(end<=0)end=messages.length;return {start:Math.max(0,end-240),end,total:messages.length,rows:messages.slice(Math.max(0,end-240),end)};}
function showOldestHistoryWindow(record){record.windowEnd=record.messages.length>240?messageKey(record.messages[239]):null;}
function olderLocalWindow(){const window=historyWindow();if(!window.start)return false;const record=historyRecord(),end=Math.max(240,window.end-120);record.windowEnd=messageKey(conversation.history[end-1]);updateTimeline();return true;}
function newerLocalWindow(latest=false){const window=historyWindow(),record=historyRecord();const end=latest?window.total:Math.min(window.total,window.end+120);record.windowEnd=end===window.total?null:messageKey(conversation.history[end-1]);updateTimeline();if(latest){const timeline=document.querySelector('.timeline');if(timeline)timeline.scrollTop=timeline.scrollHeight;}}
function newerHistoryView(){const window=historyWindow();return window.end<window.total?'<div class="history-control history-newer"><button class="text-button" data-action="newer-history">Newer messages</button><button class="text-button" data-action="latest-history">Latest</button></div>':'';}

function compareInboxRecent(a,b){return Number(b.date||0)-Number(a.date||0)||Number(b._id||b.thread_id)-Number(a._id||a.thread_id)||String(b.kind||'sms').localeCompare(String(a.kind||'sms'))||Number(b.thread_id)-Number(a.thread_id);}
function orderInboxRows(rows){return [...rows].sort((a,b)=>Number(isPinned(b))-Number(isPinned(a))||compareInboxRecent(a,b));}
function launchInboxRows(values,contacts,limit=20,keepMetadata=false){
 const rows=new Map();for(const row of Array.isArray(values)?values:[]){
  if(!row||!Number.isSafeInteger(row.thread_id)||row.thread_id<=0||!Number.isSafeInteger(row._id)||row._id<=0||typeof row.address!=='string'||typeof row.body!=='string'||!Number.isFinite(row.date))continue;
  const kind=row.kind==='mms'?'mms':'sms',clean={...(keepMetadata?row:{}),_id:row._id,thread_id:row.thread_id,date:row.date,kind,key:`${kind}:${row._id}`,type:[1,2,4,5,6].includes(row.type)?row.type:1,read:row.read===0?0:1,address:row.address.slice(0,160),name:contacts&&typeof row.name==='string'?row.name.slice(0,240):row.address.slice(0,160),body:row.body.slice(0,240),photo:contacts?contactPhotoUrl(row.photo):'',pinned:row.pinned===true,readOnly:row.readOnly===true,...(kind==='mms'?{m_type:row.m_type,msg_box:row.msg_box}:{})};
  if(!rows.has(row.thread_id)||compareInboxRecent(clean,rows.get(row.thread_id))<0)rows.set(row.thread_id,clean);
 }return orderInboxRows(rows.values()).slice(0,limit);
}
async function loadLaunchInbox(){
 const generation=privateGeneration,access=launchAccessStamp;
 try{const result=await api('launchInbox');
  if(archiveInbox.ready||inboxBootstrap.authoritative||generation!==privateGeneration||access!==launchAccessStamp||previewDenied||accessHint===false)return;
  if(result?.access?.readSms!==true||result?.access?.defaultSms!==true)return;
  const rows=launchInboxRows(result.inbox,result.access.contacts===true&&!contactsDenied);if(!rows.length)return;
  if(result.access.contacts===false)contactsDenied=true;
  state.inbox=rows;overlayLiveInbox(state,0);applyLaunchHistories();inboxBootstrap.cached=true;rows.forEach(row=>launchThreads.add(row.thread_id));syncInboxList();syncInboxBootstrap();
 }catch{} // A missing/corrupt cache never replaces the independent live read.
}
function savedHistoryPage(thread,page,contacts){
 if(!page||!Array.isArray(page.history)||typeof page.address!=='string')return null;
 const messages=new Map();for(const row of page.history){if(!row||row.thread_id!==thread||!Number.isSafeInteger(row._id)||row._id<=0||!['sms','mms'].includes(row.kind)||!Number.isFinite(row.date)||row.date<0||![1,2,4,5,6].includes(row.type)||typeof row.body!=='string')continue;
  const message={_id:row._id,thread_id:thread,key:`${row.kind}:${row._id}`,kind:row.kind,date:row.date,type:row.type,read:row.read===0?0:1,body:row.body.slice(0,8000),truncated:row.truncated===true||row.body.length>8000,textUnavailable:row.textUnavailable===true};if(row.kind==='mms'){if(Number.isInteger(row.m_type))message.m_type=row.m_type;if(Number.isInteger(row.msg_box))message.msg_box=row.msg_box;}messages.set(message.key,message);
 }
 const history=[...messages.values()].sort(compareHistoryPoint).slice(-30);if(!history.length)return null;
 return {thread,address:page.address.slice(0,160),name:contacts&&typeof page.name==='string'?page.name.slice(0,240):page.address.slice(0,160),photo:contacts?contactPhotoUrl(page.photo):'',readOnly:true,history,hasOlder:true,hasMore:true};
}
function applyLaunchHistories(){
 if(previewDenied||accessHint===false)return;
 for(const [thread,savedPage] of launchHistoryPages){const row=state.inbox.find(item=>item.thread_id===thread);if(!row){if(inboxBootstrap.authoritative)launchHistoryPages.delete(thread);continue;}
  const existing=chatPreviews.get(thread);if(existing&&!existing.launchSaved||current?.thread_id===thread&&conversation&&!conversation.previewOnly){launchHistoryPages.delete(thread);continue;}
  if(contactsDenied)savedPage.name=savedPage.address;rememberChat(thread,savedPage,false);const saved=chatPreviews.get(thread);if(saved)saved.launchSaved=true;launchHistoryPages.delete(thread);
  if(current?.thread_id===thread&&conversation?.previewOnly){conversation=provisionalChat(current);if(page==='inbox')updateTimeline();}
 }
}
async function loadLaunchHistories(){
 const generation=privateGeneration,access=launchAccessStamp,epoch=launchHistoryEpoch;
 try{const result=await api('launchHistories');if(generation!==privateGeneration||access!==launchAccessStamp||epoch!==launchHistoryEpoch||previewDenied||accessHint===false||result?.access?.readSms!==true||result?.access?.defaultSms!==true)return;
  if(result.access.contacts===false)contactsDenied=true;const seen=new Set();for(const entry of Array.isArray(result.conversations)?result.conversations:[]){const thread=entry?.thread;if(!Number.isSafeInteger(thread)||thread<=0||seen.has(thread))continue;seen.add(thread);const value=savedHistoryPage(thread,entry.page,result.access.contacts===true&&!contactsDenied);if(value)launchHistoryPages.set(thread,value);if(seen.size>=10)break;}applyLaunchHistories();
 }catch{} // The independent live read remains authoritative when no saved history is available.
}
function invalidateLaunchHistories(){launchHistoryEpoch++;launchHistoryPages.clear();}

function inboxBootstrapView(){if(inboxBootstrap.authoritative)return '';return `<div class="inbox-bootstrap ${inboxBootstrap.error?'has-error':''}" id="inbox-bootstrap" role="status"><span>${inboxBootstrap.error?'Your conversation list couldn’t update.':inboxBootstrap.cached?'Recent chats · updating from your phone':'Your recent chats will be ready here.'}</span>${inboxBootstrap.error?`<button class="text-button" data-action="retry-inbox" ${inboxBootstrap.retrying?'disabled':''}>${inboxBootstrap.retrying?'Trying again…':'Retry'}</button>`:''}</div>`;}
function syncInboxBootstrap(){const old=document.getElementById('inbox-bootstrap'),html=inboxBootstrapView();if(old){if(html)old.outerHTML=html;else old.remove();}else if(html)document.querySelector('.inbox-search-row')?.insertAdjacentHTML('afterend',html);syncHydrationNotice();}
async function retryInbox(){if(inboxBootstrap.retrying)return;inboxBootstrap.retrying=true;syncInboxBootstrap();try{await refresh(true);}finally{inboxBootstrap.retrying=false;syncInboxBootstrap();}}
function acceptFirstInbox(){
 if(inboxBootstrap.authoritative)return;archiveInbox.epoch++;archiveInbox.loading=false;archiveInbox.hasMore=false;archiveInbox.error='';inboxBootstrap.authoritative=true;inboxBootstrap.cached=false;inboxBootstrap.error='';
 const present=state.inbox.some(row=>row.thread_id===launchOpenedThread);
 if(current?.thread_id===launchOpenedThread&&!present){const thread=current.thread_id;if(dirty)previewDrafts.set(thread,{body:draft});threadEpoch++;stopHistoryRequest();chatPreviews.delete(thread);threadHistories.delete(thread);current=null;conversation=null;draft='';dirty=false;chatLoadPending=false;chatLoadError='';timerOpen=false;if(page==='inbox')render();}
 launchOpenedThread=null;launchThreads.clear();syncInboxBootstrap();
 if(current&&conversation&&!conversation.previewOnly&&!chatLoadPending&&!chatLoadError&&!previewDenied){const buffered=previewDrafts.get(current.thread_id);if(buffered){draft=buffered.body;dirty=true;queueDraft(draft);}if(page==='inbox'){syncChatHeader();syncDraftField();syncLiveComposer();}queueMicrotask(()=>{fillHistoryGaps();maybeSuggestReply();});}
}
const naturalComposerSize=!!window.CSS?.supports('field-sizing','content');
function applyTheme(){const theme=themes.some(([id])=>id===state.theme)?state.theme:'midnight';if(document.documentElement.dataset.theme!==theme)document.documentElement.dataset.theme=theme;const browserColor=document.querySelector('meta[name="theme-color"]');if(browserColor)browserColor.content=getComputedStyle(document.documentElement).getPropertyValue('--bg').trim();}
function pendingReply(){return state.jobs.find(j=>j.thread===current?.thread_id&&j.delivery_status!=='delivered'&&['scheduled','sending'].includes(j.status));}
const sendJobUpdates=new Map();let sendStateRequest=null,sendStateRevision=0,lastSendStateCheck=0;
function normalizeSendJob(job){return job.delivery_status==='delivered'?{...job,status:'sent'}:job;}
function mergeSendOutcome(previous,next){
 if(!previous||previous._id!==next._id||previous.thread!==next.thread||previous.base!==next.base||previous.body!==next.body||previous.uri&&next.uri&&previous.uri!==next.uri)return normalizeSendJob(next);
 // Carrier outcomes are durable: an older read cannot undo confirmed delivery
 // or turn a terminal send back into an in-progress send.
 if(previous.delivery_status==='delivered')return {...next,...previous,status:'sent'};
 if(next.delivery_status==='delivered')return normalizeSendJob(next);
 if(['sent','failed','unknown'].includes(previous.status)&&['scheduled','sending'].includes(next.status))return {...next,...previous};
 return normalizeSendJob(next);
}
function overlaySendJobs(snapshot,read,previousState){
 const known=new Map(previousState.jobs.map(job=>[job._id,job]));
 for(const [thread,update] of sendJobUpdates){if(read<=update.snapshot){snapshot.jobs=[...(snapshot.jobs||[]).filter(job=>job.thread!==thread),...update.jobs];}else sendJobUpdates.delete(thread);}
 snapshot.jobs=(snapshot.jobs||[]).map(job=>mergeSendOutcome(known.get(job._id),job));
}
function acceptSendJobs(thread,jobs){
 const previous=new Map(state.jobs.filter(job=>job.thread===thread).map(job=>[job._id,job]));
 const fresh=jobs.filter(job=>job.thread===thread).map(job=>mergeSendOutcome(previous.get(job._id),job));
 state.jobs=[...fresh,...state.jobs.filter(job=>job.thread!==thread)];
 sendJobUpdates.set(thread,{snapshot:snapshotSequence,jobs:fresh});
 if(sendJobUpdates.size>24)sendJobUpdates.delete(sendJobUpdates.keys().next().value);
}
function observeCommittedSend(thread,job,latestBase,insert=false){
 if(!job||job.thread!==thread||current?.thread_id!==thread||!chatReady())return;
 const match=/^content:\/\/sms\/([1-9]\d*)$/.exec(job.uri||''),id=match?Number(match[1]):0;
 if(!Number.isSafeInteger(id)||id<=0||typeof job.body!=='string'||!Number.isFinite(Number(job.sms_date))||Number(job.sms_date)<=0)return;
 const existing=historyRecord(thread).messages.find(item=>messageKind(item)==='sms'&&Number(item._id)===id);
 if(!insert&&(!existing||existing.body!==job.body||Number(existing.date)!==Number(job.sms_date)||existing.type===1))return;
 if(!['sending','sent','failed'].includes(job.status))return;
 const row={...existing,_id:id,thread_id:thread,kind:'sms',type:job.status==='sending'?4:job.status==='failed'?5:2,body:job.body,date:Number(job.sms_date),read:1,delivery:job.delivery_status||existing?.delivery||'pending'},record=historyRecord(thread);
 const changed=!existing||existing.type!==row.type||existing.delivery!==row.delivery;
 if(changed){mergeHistory(record,[row]);conversation.history=record.messages;}
 let advanced=false;
 // Only our committed SMS can advance the sending base here. An unseen incoming
 // text must be loaded and checked by the ordinary conversation path.
 if(Number(latestBase)===id&&Number(conversation.base)===Number(job.base)&&Number(conversation.base)!==id){
  advanced=true;const oldBase=conversation.base;discardDraftEdits(thread,oldBase);nextConversationRead(thread);
  conversation.base=id;conversation.smsLatest=row;conversation.latest={key:`sms:${id}`,date:row.date,kind:'sms',id};conversation.draft=null;
  if(dirty&&draft.trim())queueDraft(draft);
 }
 const inbox=state.inbox.find(item=>item.thread_id===thread);
 if(inbox&&Number(inbox.date)<=row.date)Object.assign(inbox,{_id:id,body:row.body,date:row.date,type:row.type,kind:'sms'});
 if(changed||advanced){updateTimeline();rememberChat(thread,conversation,true);}
}
async function checkSendState(force=false){
 if(!native||!current||!chatReady()||previewDenied||document.visibilityState!=='visible'||sendStateRequest||sendingNow||!force&&(!pendingReply()||Date.now()-lastSendStateCheck<900))return;
 const request={thread:current.thread_id,epoch:threadEpoch,access:privateGeneration,revision:sendStateRevision};sendStateRequest=request;lastSendStateCheck=Date.now();
 try{const result=await api('sendState',{thread:request.thread});
  if(sendStateRequest!==request||request.access!==privateGeneration||request.revision!==sendStateRevision||request.epoch!==threadEpoch||current?.thread_id!==request.thread||previewDenied||!chatReady()||result?.thread!==request.thread||!Array.isArray(result.jobs))return;
  const before=JSON.stringify(state.jobs.filter(job=>job.thread===request.thread));acceptSendJobs(request.thread,result.jobs);
  const oldBase=conversation.base;for(const job of state.jobs.filter(job=>job.thread===request.thread))observeCommittedSend(request.thread,job,result.latestBase);
  if(oldBase!==conversation.base||before!==JSON.stringify(state.jobs.filter(job=>job.thread===request.thread))){syncLiveComposer();syncInboxList();}
 }catch{/* The main refresh remains the fallback; never retry a send here. */}
 finally{if(sendStateRequest===request)sendStateRequest=null;}
}
function isAutomaticJob(j){return !!j&&(j.auto_send===true||j.auto_send===1);}
function resizeComposer(){if(naturalComposerSize||composing||composerFrame)return;composerFrame=requestAnimationFrame(()=>{composerFrame=0;const field=document.getElementById('draft');if(field){field.style.height='auto';field.style.height=Math.min(field.scrollHeight,152)+'px';}});}
function draftKey(thread,base){return `${thread}:${base}`;}
function writeDraft(edit){
 clearTimeout(edit.timer);edit.timer=null;const revision=edit.revision;
 if(edit.sent===revision&&!edit.error)return edit.promise||Promise.resolve();
 edit.sent=revision;edit.error=null;
 const promise=api('saveDraft',{thread:edit.thread,base:edit.base,body:edit.body}).then(()=>{
  edit.saved=Math.max(edit.saved,revision);if(edit.errorRevision<=revision)edit.error=null;
  if(edit.obsolete)return;
  const job=state.jobs.find(j=>j.thread===edit.thread&&j.status==='scheduled'&&isAutomaticJob(j)&&(!j.base||j.base===edit.base));
  if(job){job.status='cancelled';if(current?.thread_id===edit.thread&&conversation?.base===edit.base)syncLiveComposer();toast('Automatic timer cancelled. Your edit is saved.');}
 }).catch(error=>{if(!edit.obsolete&&revision>=edit.saved){edit.error=error;edit.errorRevision=revision;toast(error.message);}throw error;});
 edit.promise=promise;return promise;
}
function queueDraft(body){
 if(current&&manualSending(current.thread_id)){previewDrafts.set(current.thread_id,{body});return;}
 if(!chatReady()){if(current)previewDrafts.set(current.thread_id,{body});return;}
 previewDrafts.delete(current.thread_id);
 const thread=current.thread_id,base=conversation.base,key=draftKey(thread,base);let edit=draftEdits.get(key),first=!edit;
 if(!edit){edit={thread,base,body,revision:0,saved:0,sent:0,errorRevision:0};draftEdits.set(key,edit);}
 edit.body=body;edit.revision++;clearTimeout(edit.timer);
 const automatic=pendingReply();const cancelNow=automatic&&isAutomaticJob(automatic)&&automatic.status==='scheduled'&&edit.cancelJob!==automatic._id;
 if(cancelNow)edit.cancelJob=automatic._id;
 if(first||cancelNow)writeDraft(edit).catch(()=>{});
 else edit.timer=setTimeout(()=>writeDraft(edit).catch(()=>{}),220);
}
async function flushDrafts(thread=null){
 for(const edit of draftEdits.values()){
  if(thread!==null&&edit.thread!==thread)continue;
  if(edit.obsolete||edit.saved===edit.revision&&!edit.error)continue;clearTimeout(edit.timer);edit.timer=null;
  do{await writeDraft(edit);}while(!edit.obsolete&&edit.saved<edit.revision);
 }
}
function flushDraftsOnPause(){for(const edit of draftEdits.values())if(!edit.obsolete&&edit.saved<edit.revision)writeDraft(edit).catch(()=>{});}
function discardDraftEdits(thread,base){const key=draftKey(thread,base),edit=draftEdits.get(key);if(edit){edit.obsolete=true;clearTimeout(edit.timer);draftEdits.delete(key);}}
function manualRevision(thread){return manualTakeovers.get(thread)||0;}
function manualSending(thread){return [...manualSendRequests.values()].some(request=>request.thread===thread);}
function manualSendEnabled(){return !!draft.trim()||hasAttachments();}
function takeManualControl(thread){
 manualTakeovers.set(thread,manualRevision(thread)+1);nextConversationRead(thread);sendStateRevision++;timerOpen=false;
 for(const edit of [...draftEdits.values()])if(edit.thread===thread)discardDraftEdits(thread,edit.base);
 if(draftRequest?.thread===thread){draftRequest=null;busy=false;busyThread=null;}
 if(suggestionRequest?.thread===thread)suggestionRequest=null;
 if(attentionRequest?.thread===thread)attentionRequest=null;
 for(const [key,item] of mediaInsights)if(item.thread===thread)mediaInsights.delete(key);
 if(current?.thread_id===thread){previewDrafts.set(thread,{body:draft});dirty=!!draft.trim();conversation.manualReplySuppressed=true;chatLoadPending=false;chatLoadSlow=false;}
}
function syncLiveComposer(){
 scheduleMessageRefresh();
 if(page!=='inbox'||!current||!conversation)return;
 const composer=document.querySelector('.composer');if(!composer)return;
 const template=document.createElement('template');template.innerHTML=chatView();const next=template.content.querySelector('.composer');
 const existing=composer.querySelector('.scheduled-banner'),banner=next.querySelector('.scheduled-banner');
 if(existing&&banner)existing.replaceWith(banner);else if(existing)existing.remove();else if(banner)composer.prepend(banner);
 for(const selector of ['#draft','#accept','[data-action=generate]','[data-action=pick-attachments]']){const live=composer.querySelector(selector),fresh=next.querySelector(selector);if(live&&fresh)live.disabled=fresh.disabled;}
 for(const selector of ['.composer-foot','.composer-heading','.alternatives']){const live=composer.querySelector(selector),fresh=next.querySelector(selector);if(live&&fresh){if(live.innerHTML!==fresh.innerHTML)live.innerHTML=fresh.innerHTML;}else if(live)live.remove();else if(fresh)composer.append(fresh);}
 const field=composer.querySelector('#draft'),freshField=next.querySelector('#draft');if(field&&freshField)field.placeholder=freshField.placeholder;
 syncAttachmentTray();
 if(!timerOpen||pendingReply()||hasAttachments()){timerOpen=false;composer.querySelector('#timer-menu')?.remove();}
 syncHydrationNotice();syncReplyHoldUI();syncShareReview();tick();
}
function messageKind(message){return message.kind==='mms'?'mms':'sms';}
function messageKey(message){return `${messageKind(message)}:${message._id??`${message.date}:${message.type}:${message.body}`}`;}
function historyPoint(message){return {date:message.date,kind:messageKind(message),id:message._id??message.id};}
function hasOlderHistory(result){return typeof result.hasOlder==='boolean'?result.hasOlder:!!result.hasMore;}
function historyRequest(thread,cursor){return {thread,cursor:{date:cursor.date,kind:cursor.kind||'sms',id:cursor.id},beforeDate:cursor.date,beforeId:cursor.id};}
function latestSms(record=conversation){return Object.prototype.hasOwnProperty.call(record||{},'smsLatest')?record.smsLatest:[...(record?.history||[])].reverse().find(message=>messageKind(message)==='sms')||null;}
function historyRecord(thread=current?.thread_id){let record=threadHistories.get(thread);if(!record){record={messages:[],hasMore:false,before:null,initialized:false,loading:false,error:'',request:0,gaps:[],bridgeRunning:false,bridgeRequest:0};threadHistories.set(thread,record);}return record;}
function mergeHistory(record,messages){const byId=new Map(record.messages.map(message=>[messageKey(message),message]));for(const message of messages||[])byId.set(messageKey(message),message);record.messages=[...byId.values()].sort(compareHistoryPoint);}
function compareHistoryPoint(a,b){return Number(a.date)-Number(b.date)||Number(messageKind(a)==='mms')-Number(messageKind(b)==='mms')||Number(a._id??a.id)-Number(b._id??b.id);}
function mergeLiveHistoryPage(record,result,cursor){
 const rows=result.history||[],first=rows[0],complete=!hasOlderHistory(result);
 // A successful provider page owns its whole interval, including deletions.
 // Older cache-only rows stay visible until their own interval is verified.
 record.messages=record.messages.filter(row=>!row.cachedOnly||compareHistoryPoint(row,cursor)>=0||!complete&&(!first||compareHistoryPoint(row,first)<0));
 mergeHistory(record,rows);
}
function mergeConversation(thread,remote){
 dropWrongArchive(thread,remote);
 if(thread===current?.thread_id&&!dirty&&!draft.trim()&&!remote.draft&&!remote.replyDecision&&conversation?.replyDecision?.decision==='no_reply'&&conversation.textMmsDecisionContext&&conversation.textMmsDecisionContext===textMmsDecisionContext(remote))remote={...remote,replyDecision:conversation.replyDecision,textMmsDecisionContext:conversation.textMmsDecisionContext};
 if(thread===current?.thread_id&&remote.relationship)acceptReplyProfile(thread,remote,threadEpoch,true);
 const record=historyRecord(thread),latest=remote.history||[],previousLast=record.messages.findLast(row=>!row.previewSummary);if(latest.length)record.messages=record.messages.filter(row=>!row.cachedOnly||compareHistoryPoint(row,latest[0])<0);
 // An empty provider result is authoritative: permission loss or removal must not
 // leave cached private SMS visible, nor allow an outstanding read to restore it.
 if(!latest.length||(remote.hasOlder===false||remote.hasOlder===undefined&&remote.hasMore===false)){record.request++;record.bridgeRequest++;record.messages=[];record.gaps=[];record.bridgeRunning=false;record.loading=false;record.error='';record.hasMore=false;record.before=remote.before||null;record.initialized=true;}
 else if(hasOlderHistory(remote)&&previousLast&&compareHistoryPoint(latest[0],previousLast)>0){
  record.gaps.push({id:++historyGapSequence,before:remote.before||historyPoint(latest[0]),edge:messageKey(latest[0]),target:historyPoint(previousLast),loading:false,error:''});
 }
 mergeHistory(record,latest);applyReceiptUpdates(record,remote.receiptUpdates);if(!record.initialized||!previousLast&&latest.length){record.hasMore=hasOlderHistory(remote);record.before=remote.before||null;record.initialized=true;}
 return {...remote,history:record.messages,hasMore:record.hasMore,before:record.before};
}
function stopHistoryRequest(){if(current){const record=threadHistories.get(current.thread_id);if(record){record.request++;record.loading=false;record.bridgeRequest++;record.bridgeRunning=false;record.gaps.forEach(gap=>gap.loading=false);}}}
function historyControlView(){const window=historyWindow();if(!liveHistoryReady())return (window.start?'<div class="history-control"><button class="text-button" data-action="load-older">Earlier messages</button></div>':archiveHistoryControl());const record=historyRecord();return `<div class="history-control" aria-live="polite">${window.start||record.hasMore||record.loading||record.error?`<button class="text-button" data-action="load-older" ${record.loading?'disabled':''}>${record.loading?'Loading older messages…':record.error?'Retry loading messages':'Load older messages'}</button>${record.error?`<p class="history-error">${esc(record.error)}</p>`:''}`:record.messages.length?'<span>Beginning of conversation</span>':''}</div>`;}
function historyGapView(gap){return `<div class="history-gap" data-history-gap="${gap.id}" role="status">${gap.error?`<p>${esc(gap.error)}</p><button class="text-button" data-action="retry-history-gap" data-gap="${gap.id}">Retry missing messages</button>`:'Loading messages from while you were away…'}</div>`;}
function deliveryState(message){
 if(Object.prototype.hasOwnProperty.call(message,'delivery'))return ['none','pending','delivered','failed','unknown'].includes(message.delivery)?message.delivery:'unknown';
 if(message.type===5)return 'none';
 return message.status===0?'delivered':message.status===32?'pending':message.status===64?'failed':'none';
}
function messageStatus(message){
 if(messageKind(message)==='mms'||![2,4,5,6].includes(message.type))return null;
 const delivery=deliveryState(message);
 if(delivery==='delivered')return {kind:'delivered',label:'Delivered',icon:'checks',note:'Your carrier confirmed delivery. This is not a read receipt.'};
 if(message.type===5)return {kind:'failed',label:'Send failed',icon:'chat',note:'Sending failed. Check the conversation before trying again.'};
 if(delivery==='failed')return {kind:'failed',label:'Delivery failed',icon:'chat',note:'The carrier reported a delivery failure. Some parts may have arrived.'};
 if(message.type===4)return {kind:'sending',label:'Sending…',icon:'clock',note:'Waiting for your carrier to accept the message.'};
 if(message.type===6)return {kind:'queued',label:'Waiting to send',icon:'clock',note:'This message has not been sent yet.'};
 return {kind:'sent',label:'Sent',icon:'check',note:delivery==='pending'?'Accepted by your carrier. Waiting for a delivery report, if supported.':'Accepted by your carrier. Delivery is not confirmed.'};
}
function receiptView(message){const status=messageStatus(message);return status?`<span class="message-receipt" data-receipt="${status.kind}" title="${esc(status.note)}">${icon(status.icon)}<span>${status.label}</span></span>`:'';}
function visibleReceiptIds(){
 const timeline=document.querySelector('.timeline');if(!timeline||page!=='inbox')return [];
 const bounds=timeline.getBoundingClientRect();return [...timeline.querySelectorAll('[data-message-id]')].filter(row=>{const box=row.getBoundingClientRect();return box.bottom>bounds.top&&box.top<bounds.bottom;}).map(row=>Number(row.dataset.messageId)).filter(id=>Number.isSafeInteger(id)&&id>0).slice(0,100);
}
function applyReceiptUpdates(record,updates){
 if(!Array.isArray(updates))return;const byId=new Map(record.messages.map(message=>[messageKey(message),message]));
 for(const update of updates){const message=byId.get(`sms:${update._id}`);if(!message)continue;for(const field of ['status','delivery','delivered_at'])if(Object.prototype.hasOwnProperty.call(update,field))message[field]=update[field];}
}
function safeMessageUrl(value){try{if(typeof value!=='string'||value.length>2048||value.includes('\\')||/[\u0000-\u0020\u007f-\u009f\u200b-\u200f\u202a-\u202e\u2060-\u206f]/.test(value))return null;const url=new URL(value);return ['http:','https:'].includes(url.protocol)&&url.hostname&&!url.username&&!url.password?url:null;}catch{return null;}}
function messageUrls(value){
 const text=String(value??''),found=[];for(const match of text.matchAll(/\bhttps?:\/\/[^\s<>"'\u0000-\u001f\u007f\u200b-\u200f\u202a-\u202e\u2060-\u206f]+/gi)){
  if(match.index>0&&/[\p{L}\p{N}_]/u.test(text[match.index-1]))continue;let raw=match[0].replace(/[.,!?;:]+$/,'');
  for(const [open,close] of [['(',')'],['[',']'],['{','}']])while(raw.endsWith(close)&&raw.split(close).length>raw.split(open).length)raw=raw.slice(0,-1);
  const url=safeMessageUrl(raw);if(url)found.push({start:match.index,end:match.index+raw.length,text:raw,url:raw,host:url.host,https:url.protocol==='https:'});
 }return found;
}
function linkifiedText(value){if(page==='inbox'&&!chatReady())return esc(value);const text=String(value??''),links=messageUrls(text);let end=0,html='';for(const link of links){html+=esc(text.slice(end,link.start))+`<a class="message-link" data-action="open-link" data-url="${esc(link.url)}" href="${esc(link.url)}" rel="noopener noreferrer">${esc(link.text)}</a>`;end=link.end;}return html+esc(text.slice(end));}
function messageLinkText(message){if(messageKind(message)!=='mms')return String(message.body||'');const parts=(Array.isArray(message.parts)?message.parts:[]).filter(part=>part.ct==='text/plain').map(part=>String(part.text||''));return parts.length?parts.join('\n'):String(message.body||'');}
function firstMessageLink(message){return messageUrls(messageLinkText(message))[0]||null;}
function linkPreviewKey(thread,message,url){return `${thread}:${messageKey(message)}:${url}`;}
function localLinkImage(value){if(typeof value!=='string')return '';let path=value;if(!value.startsWith('/')){try{const url=new URL(value);if(url.origin!=='https://app.replypilot.local'||url.username||url.password||url.search||url.hash)return '';path=url.pathname;}catch{return '';}}return /^\/link-preview\/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(path)?path:'';}
function linkPreviewsEnabled(){return (preferences||state).linkPreviews!==false;}
function linkPreviewView(message,thread=current?.thread_id){
 if(message.cachedOnly||!linkPreviewsEnabled()||page==='inbox'&&!chatReady())return '';
 const link=firstMessageLink(message);if(!link||!Number.isSafeInteger(thread)||!Number.isSafeInteger(Number(message._id)))return '';
 const key=linkPreviewKey(thread,message,link.url),cached=linkPreviewCache.get(key),data=cached?.result,available=data?.available===true,image=available?localLinkImage(data.imageUrl):'';
 return `<button class="link-preview" data-link-key="${esc(key)}" data-link-thread="${thread}" data-link-kind="${messageKind(message)}" data-link-id="${message._id}" data-action="open-link" data-url="${esc(link.url)}" aria-label="Open link from ${esc(link.host)}">${image?`<img class="link-preview-image" src="${esc(image)}" alt="" loading="lazy" decoding="async">`:''}<span class="link-preview-body">${available?`<span class="link-preview-site">${esc(data.siteName||link.host)}</span>`:''}${available&&data.title?`<strong class="link-preview-title">${esc(data.title)}</strong>`:''}${available&&data.description?`<span class="link-preview-description">${esc(data.description)}</span>`:''}<span class="link-preview-host">${esc(link.host)} ${icon('arrow')}</span></span></button>`;
}
function linkPreviewContext(){if(!linkPreviewsEnabled()||linkPreviewPaused||previewDenied||document.visibilityState!=='visible')return null;if(page==='inbox'&&chatReady()&&current)return {thread:current.thread_id,rows:conversation.history,epoch:threadEpoch,mode:'chat'};if(page==='media-thread'&&mediaBrowse?.thread&&inboxBootstrap.authoritative)return {thread:mediaBrowse.thread,rows:mediaBrowse.history,epoch:mediaBrowse,mode:'media'};return null;}
function currentLinkRequest(item){const context=linkPreviewContext();return context&&context.thread===item.thread&&context.mode===item.mode&&context.epoch===item.epoch&&context.rows.some(row=>messageKind(row)===item.kind&&Number(row._id)===item.id&&firstMessageLink(row)?.url===item.url);}
function trimLinkPreviews(){for(const [key,item] of linkPreviewCache){if(linkPreviewCache.size<=LINK_PREVIEW_LIMIT)break;if(item.status==='queued'||item.status==='loading')continue;linkPreviewCache.delete(key);}}
function pauseLinkPreviews(){linkPreviewPaused=true;linkPreviewGeneration++;linkPreviewQueue.length=0;for(const [key,item] of linkPreviewCache)if(item.status!=='done')linkPreviewCache.delete(key);linkPreviewObserver?.disconnect();if(linkPreviewFrame)cancelAnimationFrame(linkPreviewFrame);linkPreviewFrame=0;}
function clearLinkPreviews(){linkPreviewGeneration++;linkPreviewCache.clear();linkPreviewQueue.length=0;linkPreviewObserver?.disconnect();if(linkPreviewFrame)cancelAnimationFrame(linkPreviewFrame);linkPreviewFrame=0;}
function linkCardVisible(card){if(!card?.isConnected)return false;const rect=card.getBoundingClientRect(),pane=card.closest('.timeline,.media-history')?.getBoundingClientRect();return rect.width>0&&rect.height>0&&rect.bottom>Math.max(0,pane?.top||0)&&rect.top<Math.min(innerHeight,pane?.bottom||innerHeight);}
function queueLinkCard(card){
 const context=linkPreviewContext(),url=safeMessageUrl(card.dataset.url);const cached=linkPreviewCache.get(card.dataset.linkKey);if(cached?.status==='done'&&cached.expires<Date.now())linkPreviewCache.delete(card.dataset.linkKey);if(!context||context.thread!==Number(card.dataset.linkThread)||!url||url.protocol!=='https:'||!linkCardVisible(card)||linkPreviewCache.has(card.dataset.linkKey))return;
 const item={key:card.dataset.linkKey,thread:context.thread,kind:card.dataset.linkKind,id:Number(card.dataset.linkId),url:card.dataset.url,epoch:context.epoch,mode:context.mode,generation:linkPreviewGeneration,status:'queued'};
 if(!currentLinkRequest(item)||linkPreviewQueue.length>=24)return;linkPreviewCache.set(item.key,item);linkPreviewQueue.push(item);trimLinkPreviews();drainLinkPreviews();
}
function scheduleLinkPreviews(){if(linkPreviewFrame)return;linkPreviewFrame=requestAnimationFrame(()=>{linkPreviewFrame=0;linkPreviewObserver?.disconnect();if(!linkPreviewContext()){drainLinkPreviews();return;}const cards=[...document.querySelectorAll('.timeline .link-preview,.media-history .link-preview')];if('IntersectionObserver' in window){if(!linkPreviewObserver)linkPreviewObserver=new IntersectionObserver(entries=>{for(const entry of entries)if(entry.isIntersecting)queueLinkCard(entry.target);},{threshold:0.01});cards.forEach(card=>linkPreviewObserver.observe(card));}else cards.filter(linkCardVisible).forEach(queueLinkCard);drainLinkPreviews();});}
function refreshLinkCard(item){
 if(!currentLinkRequest(item))return;const context=linkPreviewContext(),message=context.rows.find(row=>messageKind(row)===item.kind&&Number(row._id)===item.id),card=[...document.querySelectorAll('.link-preview')].find(node=>node.dataset.linkKey===item.key);if(!message||!card)return;
 const timeline=card.closest('.timeline,.media-history'),position=item.mode==='chat'?timelinePosition(timeline):null,scroll=timeline?.scrollTop||0,oldHeight=card.getBoundingClientRect().height,above=timeline&&card.getBoundingClientRect().bottom<timeline.getBoundingClientRect().top;
 const template=document.createElement('template');template.innerHTML=linkPreviewView(message,item.thread);const fresh=template.content.firstElementChild;const focused=document.activeElement===card;card.replaceWith(fresh);if(focused)fresh.focus({preventScroll:true});
 if(position)restoreTimeline(timeline,position);else if(timeline)timeline.scrollTop=scroll+(above?fresh.getBoundingClientRect().height-oldHeight:0);scheduleLinkPreviews();
}
function normalizeLinkResult(result,item){const target=safeMessageUrl(result?.url),source=safeMessageUrl(item.url);if(!target||!source)return {available:false};target.hash='';source.hash='';if(target.href!==source.href||result.available!==true)return {available:false};return {available:true,title:typeof result.title==='string'?result.title.slice(0,240):'',description:typeof result.description==='string'?result.description.slice(0,320):'',siteName:typeof result.siteName==='string'?result.siteName.slice(0,120):'',imageUrl:localLinkImage(result.imageUrl)};}
function drainLinkPreviews(){
 while(linkPreviewActive<2&&linkPreviewQueue.length){const item=linkPreviewQueue.shift(),card=[...document.querySelectorAll('.link-preview')].find(node=>node.dataset.linkKey===item.key);
  if(item.generation!==linkPreviewGeneration||!currentLinkRequest(item)||!linkCardVisible(card)){if(linkPreviewCache.get(item.key)===item)linkPreviewCache.delete(item.key);continue;}
  item.status='loading';linkPreviewActive++;
  api('linkPreview',{thread:item.thread,kind:item.kind,id:item.id,url:item.url}).then(result=>{if(item.generation!==linkPreviewGeneration||!currentLinkRequest(item)){if(linkPreviewCache.get(item.key)===item)linkPreviewCache.delete(item.key);return;}item.result=normalizeLinkResult(result,item);item.status='done';item.expires=Date.now()+(item.result.available?600000:60000);refreshLinkCard(item);item.epoch=null;}).catch(()=>{if(item.generation===linkPreviewGeneration&&currentLinkRequest(item)){item.result={available:false};item.status='done';item.expires=Date.now()+(item.result.available?600000:60000);refreshLinkCard(item);item.epoch=null;}else if(linkPreviewCache.get(item.key)===item)linkPreviewCache.delete(item.key);}).finally(()=>{linkPreviewActive--;trimLinkPreviews();drainLinkPreviews();scheduleLinkPreviews();});
 }
}
function messageContentView(message){
 const mms=messageKind(message)==='mms',parts=Array.isArray(message.parts)?message.parts:[],text=message.body||parts.filter(part=>mediaMime(part.ct)==='text/plain').map(part=>part.text||'').join('\n'),hasMedia=parts.some(galleryAttachment),unavailable=message.textUnavailable===true&&!text.trim(),missing='<p>Message text is unavailable on this phone.</p>';
 if(message.cachedOnly||page==='inbox'&&!chatReady())return `${text?`<p>${esc(text)}</p>`:''}${unavailable?missing:''}${mms&&(hasMedia||!text&&!unavailable)?`<div class="cached-media-note">${icon('media')}<span>Media message · preview updates from your phone</span></div>`:''}`;
 if(!mms)return linkifiedText(message.body);
 const content=unavailable&&!hasMedia?missing:parts.map(mediaPartView).join('')||`<p>${linkifiedText(message.body||'Multimedia message')}</p>`;
 return `${content}${mmsDownloadView(message)}${message.truncated?'<p class="media-truncated">Some message content is shortened.</p>':''}`;
}
function mediaContextKey(){return conversation?.latest?.key||(conversation?.history||[]).map(messageKey).at(-1)||(conversation?.latestIncomingMediaId?`mms:${conversation.latestIncomingMediaId}`:'');}
function mediaInsightKey(thread,mediaId){return `${thread}:${mediaId}`;}
function hasMessageAttachments(message){return messageKind(message||{})==='mms'&&Array.isArray(message?.parts)&&message.parts.some(galleryAttachment);}
function latestTextMms(record=conversation){
 const id=Number(record?.latestIncomingTextMmsId);if(!Number.isSafeInteger(id)||id<=0||record?.latest?.key!==`mms:${id}`)return null;
 const row=record.history?.find(message=>messageKind(message)==='mms'&&Number(message._id)===id);
 // Only the native-verified latest text target can unlock drafting. Cached rows,
 // incomplete downloads and unrecognized parts never authorize a text request.
 if(!row||row.cachedOnly||row.type!==1||row.m_type!==132||row.truncated||row.textUnavailable||row.download&&row.download.status!=='downloaded'||!Array.isArray(row.parts)||row.parts.some(part=>part?.truncated||part?.textUnavailable||!['text/plain','application/smil','application/smil+xml'].includes(mediaMime(part?.ct))))return null;
 return row;
}
function sameDraftTarget(thread,epoch,base,context,textMmsId=0){return current?.thread_id===thread&&threadEpoch===epoch&&page==='inbox'&&conversation?.base===base&&mediaContextKey()===context&&(!textMmsId||Number(latestTextMms()?._id)===textMmsId);}
function textMmsDecisionContext(record=conversation){
 const target=latestTextMms(record);if(!target)return '';
 return JSON.stringify([record.base,record.latest?.key,record.address,record.contactFingerprint,record.profileRevision??record.relationship?.revision,[record.relationship?.cloudEnabled,record.relationship?.engagement,record.relationship?.body,record.relationship?.importantDetails,record.relationship?.samples,record.relationship?.planHandling],(record.history||[]).slice(-50).map(row=>[messageKey(row),row.type,row.date,row.body,row.m_type,row.parts,row.truncated,row.textUnavailable])]);
}
function rememberTextMmsDecision(){
 const id=Number(conversation?.replyDecision?.textMmsId);
 if(!dirty&&!draft.trim()&&conversation?.replyDecision?.decision==='no_reply'&&id>0&&id===Number(latestTextMms()?._id))conversation.textMmsDecisionContext=textMmsDecisionContext();
}
function mediaInsightView(message){
 if(message.cachedOnly||!chatReady()||!hasMessageAttachments(message)||message.type!==1||message.m_type===130||!/^\d+$/.test(String(message._id)))return '';
 const item=mediaInsights.get(mediaInsightKey(current.thread_id,message._id)),enabled=chatReady()&&conversation?.relationship?.cloudEnabled===true&&!conversation.readOnly;
 const result=item?.result,canUse=!!result?.suggestion&&item.base===conversation.base&&item.context===mediaContextKey()&&!conversation.readOnly&&!hasAttachments()&&!draft.trim()&&!pendingReply()&&!busy;
 return `<div class="media-insight" data-media-insight="${message._id}"><button class="text-button" data-action="analyze-media" data-media-id="${message._id}" ${item?.loading||!enabled?'disabled':''}>${item?.loading?'<span class="spinner"></span> Understanding…':icon('spark')+' Understand &amp; reply'}</button>${!enabled?`<small>${!chatReady()?'Available after this conversation updates.':conversation.readOnly?'This conversation is read-only.':'Enable AI for this person in Reply setup to understand this media.'}</small>`:''}${item?.error?`<p role="status">${esc(item.error)}</p>`:''}${result?`<details class="media-understanding" open><summary>Suggested interpretation</summary><p>${esc(result.summary||'No clear description is available.')}</p>${result.intent?`<p><strong>Possible intent:</strong> ${esc(result.intent.replace(/^Possible intent:\s*/i,''))}</p>`:''}${result.limitation?`<p class="media-limitation">${esc(result.limitation)}</p>`:''}${result.suggestion?`<blockquote>${esc(result.suggestion)}</blockquote><button class="secondary" data-action="use-media-reply" data-media-id="${message._id}" ${canUse?'':'disabled'}>${draft.trim()?'Clear your draft to use reply':'Use reply'}</button>`:`<p>${esc(result.reason||'Write a reply in your own words.')}</p>`}<small>${item.base!==conversation.base||item.context!==mediaContextKey()?'The conversation changed. Understand this media again before using the reply.':'AI can misread an image or joke. Review the interpretation and reply before sending.'}</small></details>`:''}</div>`;
}
function historyView(messages){messages=historyWindow(messages).rows;let previousDay='';const gaps=historyRecord().gaps;return historyControlView()+messages.map(message=>{const day=new Date(Number(message.date)).toLocaleDateString(),heading=day!==previousDay?`<div class="date-label">${date(message.date)}</div>`:'';previousDay=day;const gapMarkup=gaps.filter(gap=>gap.edge===messageKey(message)).map(historyGapView).join(''),kind=messageKind(message);return `${gapMarkup}${heading}<div class="bubble-wrap ${message.type!==1?'out':''} ${kind==='mms'&&hasMessageAttachments(message)?'multimedia-bubble':''}" data-message-id="${esc(kind==='sms'?message._id:messageKey(message))}" data-message-key="${esc(messageKey(message))}" data-message-kind="${kind}"><div class="bubble">${messageContentView(message)}</div>${linkPreviewView(message)}<div class="bubble-time"><time>${time(message.date)}</time>${kind==='mms'?mmsReceiptView(message):receiptView(message)}</div>${mediaInsightView(message)}</div>`;}).join('')+(!messages.length?'<div class="empty"><p>Start a new SMS conversation.</p></div>':'')+newerHistoryView();}
function historySignature(history){return JSON.stringify((history||[]).map(message=>[messageKey(message),message.type,message.body,message.date,message.status,message.delivery,message.delivered_at,message.parts,message.m_type,message.truncated,message.download,message.sendStatus,message.meaning,message.meaningToken]));}
function timelinePosition(timeline=document.querySelector('.timeline')){if(!timeline)return null;const top=timeline.getBoundingClientRect().top,node=[...timeline.querySelectorAll('[data-message-key]')].find(item=>item.getBoundingClientRect().bottom>top+1);return {thread:timeline.dataset.thread,id:node?.dataset.messageKey,offset:node?node.getBoundingClientRect().top-top:0,scrollTop:timeline.scrollTop,atBottom:timeline.scrollHeight-timeline.clientHeight-timeline.scrollTop<60};}
function restoreTimeline(timeline,position){if(!timeline)return;if(!position||position.atBottom){timeline.scrollTop=timeline.scrollHeight;return;}const node=[...timeline.querySelectorAll('[data-message-key]')].find(item=>item.dataset.messageKey===position.id);if(node)timeline.scrollTop+=node.getBoundingClientRect().top-timeline.getBoundingClientRect().top-position.offset;else timeline.scrollTop=position.scrollTop;}
function updateTimeline(){const timeline=document.querySelector('.timeline');if(!timeline||timeline.dataset.thread!==String(current?.thread_id))return;const position=timelinePosition(timeline),fresh=timeline.cloneNode(false);fresh.innerHTML=historyView(conversation?.history||[]);if(!updateFocusedTree(timeline,fresh)){if(window.getSelection()?.toString()&&timeline.contains(window.getSelection().anchorNode))dismissTextSelection();timeline.replaceChildren(...fresh.childNodes);}restoreTimeline(timeline,position);scheduleLinkPreviews();}
function syncDraftField(){const field=document.getElementById('draft');if(field&&field.value!==draft)field.value=draft;resizeComposer();}

function syncReplyModes(){
 const modes=document.getElementById('reply-modes');if(modes&&current&&conversation){const edit=relationshipEdit(),markup=replyModesView(edit);const ready=profileEligibility(edit),record=replyProfileRecord(),signature=JSON.stringify([ready,profileMode(edit),busy,record.loading,record.error,profileReady(),personaStatus(),personaTraining.get(current.thread_id)||null,!!state.cloud?.configured]);if(modes._readinessSignature!==signature){modes.outerHTML=markup;document.getElementById('reply-modes')._readinessSignature=signature;}const status=document.getElementById('relationship-status');if(status)status.textContent=relationshipStatus(edit);refreshProfileSave(edit);}}

async function loadOlderMessages(){
 if(olderLocalWindow())return;const thread=current?.thread_id;if(!thread||page!=='inbox')return;if(!liveHistoryReady()){if(!archiveHistories.get(thread)?.error)loadArchiveHistory(thread,true);return;}const record=historyRecord(thread);if(record.loading||!record.hasMore||!record.before)return;
 const epoch=threadEpoch,request=++record.request,cursor={...record.before},address=conversation.address??current.address??'',generation=privateGeneration,archive=archiveHistories.get(thread),cachedKeys=new Set();let liveFinished=false;
 record.loading=true;record.error='';updateTimeline();
 // Race the paged disk read against the provider. Cached rows remain display-only.
 api('cacheHistory',{thread,expectedAddress:address,limit:40,cursor,...(archive?.snapshot?{snapshot:archive.snapshot}:{})}).then(result=>{
  if(liveFinished||request!==record.request||epoch!==threadEpoch||current?.thread_id!==thread||generation!==privateGeneration||previewDenied)return;const cached=archivePage(thread,result,address);if(!cached||cached.history.some(row=>compareHistoryPoint(row,cursor)>=0))return;
  const existing=new Set(record.messages.map(messageKey));const additions=cached.history.filter(row=>!existing.has(messageKey(row)));additions.forEach(row=>cachedKeys.add(messageKey(row)));mergeHistory(record,additions);showOldestHistoryWindow(record);conversation={...conversation,history:record.messages,hasMore:record.hasMore,before:record.before};updateTimeline();
 }).catch(()=>{});
 try{const result=await api('historyPage',historyRequest(thread,cursor));liveFinished=true;if(request!==record.request||epoch!==threadEpoch||current?.thread_id!==thread)return;
  if(hasOlderHistory(result)&&(!result.before||compareHistoryPoint(result.before,cursor)>=0))throw new Error('Older messages could not be loaded. Please try again.');
  mergeLiveHistoryPage(record,result,cursor);record.hasMore=hasOlderHistory(result);record.before=result.before||record.before;showOldestHistoryWindow(record);conversation={...conversation,history:record.messages,hasMore:record.hasMore,before:record.before};
 }catch(error){liveFinished=true;if(request===record.request&&epoch===threadEpoch&&current?.thread_id===thread)record.error=cachedKeys.size?'Showing saved messages. Your phone’s history couldn’t update.':error.message||'Older messages could not be loaded.';}
 finally{if(request===record.request){record.loading=false;if(epoch===threadEpoch&&current?.thread_id===thread)updateTimeline();}}
}
async function fillHistoryGaps(){
 const thread=current?.thread_id;if(!thread||page!=='inbox'||!liveHistoryReady())return;const record=historyRecord(thread);if(record.bridgeRunning||!record.gaps.some(gap=>!gap.error))return;
 const epoch=threadEpoch,request=++record.bridgeRequest;record.bridgeRunning=true;
 try{while(request===record.bridgeRequest&&epoch===threadEpoch&&current?.thread_id===thread){
  const gap=record.gaps.find(item=>!item.error);if(!gap)break;gap.loading=true;updateTimeline();const cursor={...gap.before};
  try{const result=await api('historyPage',historyRequest(thread,cursor));if(request!==record.bridgeRequest||epoch!==threadEpoch||current?.thread_id!==thread)return;
   if(hasOlderHistory(result)&&(!result.before||compareHistoryPoint(result.before,cursor)>=0))throw new Error('Missing messages could not be loaded. Please try again.');
   const messages=result.history||[];mergeLiveHistoryPage(record,result,cursor);
   if(record.before&&compareHistoryPoint(record.before,cursor)>=0){record.hasMore=hasOlderHistory(result);record.before=result.before||record.before;}
   if(!hasOlderHistory(result)||messages.some(message=>compareHistoryPoint(message,gap.target)<=0))record.gaps=record.gaps.filter(item=>item!==gap);
   else{gap.before=result.before;gap.edge=messageKey(messages.reduce((oldest,message)=>compareHistoryPoint(message,oldest)<0?message:oldest,messages[0]));}
   gap.loading=false;conversation={...conversation,history:record.messages};updateTimeline();
  }catch(error){if(request!==record.bridgeRequest||epoch!==threadEpoch||current?.thread_id!==thread)return;gap.loading=false;gap.error=error.message||'Missing messages could not be loaded.';updateTimeline();}
 }}finally{if(request===record.bridgeRequest)record.bridgeRunning=false;}
}
function closeOverlays(focus=false){const wasTimer=timerOpen;let changed=timerOpen;timerOpen=false;if(current&&conversation){const edit=relationshipEdit();changed=changed||edit.open;edit.open=false;}if(changed){render();if(focus)document.getElementById(wasTimer?'accept':'reply-setup')?.focus({preventScroll:true});}return changed;}
let viewportFrame=0;
function syncViewport(){
 if(viewportFrame)return;
 viewportFrame=requestAnimationFrame(()=>{
  viewportFrame=0;const timeline=document.querySelector('.timeline'),position=timelinePosition(timeline);const viewport=window.visualViewport,layoutHeight=window.innerHeight||document.documentElement.clientHeight;
  // WebView can report the previous visual height while its native bounds resize.
  // Fit inside both viewports; native code has already removed the keyboard space.
  const height=Number.isFinite(viewport?.height)&&viewport.height>0?Math.min(layoutHeight,viewport.height):layoutHeight;
  const offset=Number.isFinite(viewport?.offsetTop)?Math.max(0,viewport.offsetTop):0;
  const top=Math.min(offset,Math.max(0,layoutHeight-height));
  const style=document.documentElement.style;
  for(const [key,value] of [['--viewport-height',`${height}px`],['--viewport-top',`${top}px`]])if(style.getPropertyValue(key)!==value)style.setProperty(key,value);
  restoreTimeline(timeline,position);
  const field=document.activeElement;if(field?.closest('.setup-popover,.timer-popover'))field.scrollIntoView({block:'nearest'});
 });
}
window.visualViewport?.addEventListener('resize',syncViewport);window.visualViewport?.addEventListener('scroll',syncViewport);window.addEventListener('resize',syncViewport);syncViewport();
let cloudTest={message:'',relationship:'',examples:'',tone:'Natural',results:[],error:''};
const time=n=>new Date(Number(n)).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'});
const date=n=>new Date(Number(n)).toLocaleDateString([],{month:'short',day:'numeric'});
const initials=n=>String(n||'?').split(' ').filter(Boolean).slice(0,2).map(s=>s[0]).join('').toUpperCase();
function contactPhotoUrl(value){return !contactsDenied&&state.contactsAllowed!==false&&typeof value==='string'&&/^\/contact-photo\/(?:%2B)?[0-9]{3,25}$/.test(value)?value:'';}
function clearContactPhotos(){root.querySelectorAll('.contact-avatar-photo').forEach(image=>image.remove());root.querySelectorAll('[data-contact-photo]').forEach(node=>node.removeAttribute('data-contact-photo'));for(const row of state.inbox||[])delete row.photo;for(const item of chatPreviews.values())delete item.value.photo;for(const item of replyProfiles.values())if(item.data)delete item.data.photo;for(const item of launchHistoryPages.values())delete item.photo;if(current)delete current.photo;if(conversation)delete conversation.photo;}
window.onContactPhotosChanged=()=>{if(contactsDenied||state.contactsAllowed===false){clearContactPhotos();return;}root.querySelectorAll('.avatar[data-contact-photo]').forEach(node=>{const url=contactPhotoUrl(node.dataset.contactPhoto);node.querySelector('.contact-avatar-photo')?.remove();if(!url)return;const image=document.createElement('img');image.className='contact-avatar-photo';image.alt='';image.loading=node.dataset.photoLoading==='eager'?'eager':'lazy';image.decoding='async';image.draggable=false;image.src=url;node.append(image);});};
root.addEventListener('error',event=>{if(event.target.matches?.('.contact-avatar-photo'))event.target.remove();},true);
const avatar=(name,i=0,photo='',eager=false)=>`<div class="avatar ${['','b','c','d'][i%4]}" ${contactPhotoUrl(photo)?`data-contact-photo="${esc(contactPhotoUrl(photo))}" data-photo-loading="${eager?'eager':'lazy'}"`:''}><span class="avatar-initials">${esc(initials(name))}</span>${contactPhotoUrl(photo)?`<img class="contact-avatar-photo" src="${esc(contactPhotoUrl(photo))}" alt="" loading="${eager?'eager':'lazy'}" decoding="async" draggable="false">`:''}</div>`;

function toast(msg){let el=document.getElementById('toast');el.textContent=msg;el.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('show'),5000);}
function nav(){const selected=page==='media-thread'?'media':page;return `<aside class="rail"><div class="brand">${icon('send')}</div><nav class="rail-nav" aria-label="Main navigation">${[['inbox','chat','Messages'],['queue','clock','Queue'],['media','media','Media'],['test','spark','Try AI'],['settings','settings','Settings']].map(([id,i,label])=>`<button class="nav-button ${selected===id?'active':''}" data-action="nav" data-page="${id}" aria-label="${label}" ${selected===id?'aria-current="page"':''}>${icon(i)}<span>${label}</span></button>`).join('')}</nav><div class="rail-footer">A LITTLE SPACE TO REPLY</div></aside>`;}
// Keep the connected editor and its ancestor path during background redraws.
// Replacing a focused textarea resets Android's input connection and selection UI.
function updateFocusedTree(oldRoot,newRoot){
 const active=document.activeElement,selection=window.getSelection(),anchor=selection?.anchorNode?.nodeType===1?selection.anchorNode:selection?.anchorNode?.parentElement;
 const selected=selection?.toString()?anchor?.closest('.bubble'):null;
 const field=active?.id&&oldRoot.contains(active)&&active.matches('textarea,input:not([type=radio]):not([type=checkbox]):not([type=range]),select')?active:null;
 const editor=field||selected;if(!editor||!oldRoot.contains(editor))return false;
 const key=editor.closest('.bubble-wrap')?.dataset.messageKey;
 const next=field?newRoot.querySelector('#'+CSS.escape(editor.id)):key?newRoot.querySelector('.bubble-wrap[data-message-key="'+CSS.escape(key)+'"] > .bubble'):null;
 if(!next||next.tagName!==editor.tagName||next.disabled||editor.disabled||!field&&editor.innerHTML!==next.innerHTML)return false;
 const oldPath=[],newPath=[];for(let node=editor;node!==oldRoot;node=node.parentNode){if(!node)return false;oldPath.unshift(node);}for(let node=next;node!==newRoot;node=node.parentNode){if(!node)return false;newPath.unshift(node);}
 if(oldPath.length!==newPath.length||oldPath.some((node,i)=>node.nodeName!==newPath[i].nodeName||node.id!==newPath[i].id))return false;
 const attrs=(a,b)=>{for(const attr of [...a.attributes])if(!b.hasAttribute(attr.name))a.removeAttribute(attr.name);for(const attr of b.attributes)if(a.getAttribute(attr.name)!==attr.value)a.setAttribute(attr.name,attr.value);};
 const visit=(a,b,depth)=>{
  if(a===editor){attrs(a,b);if(a.value!==b.value)a.value=b.value;return;}
  if(a.nodeType===1&&b.nodeType===1)attrs(a,b);
  const keep=oldPath[depth],fresh=newPath[depth];
  for(const child of [...a.childNodes])if(child!==keep)child.remove();
  let passed=false;for(const child of b.childNodes){if(child===fresh){passed=true;visit(keep,fresh,depth+1);}else if(passed)a.append(child.cloneNode(true));else a.insertBefore(child.cloneNode(true),keep);}
 };
 visit(oldRoot,newRoot,0);return true;
}
let renderedScope='';
function setScreenMarkup(markup){
 const scope=page+':'+(current?.thread_id||'');
 const template=document.createElement('template');template.innerHTML=markup;
 if(renderedScope!==scope||!updateFocusedTree(root,template.content)){if(window.getSelection()?.toString())dismissTextSelection();root.replaceChildren(template.content);}
 renderedScope=scope;
}
function dismissTextSelection(){
 const selection=window.getSelection();if(selection?.rangeCount)selection.removeAllRanges();
 if(native)api('dismissSelection').catch(()=>{});
}
function focusEditor(id){
 const field=document.getElementById(id);if(!field||field.disabled)return;
 dismissTextSelection();field.focus({preventScroll:true});
 if(native)api('focusEditor',{id}).catch(()=>{});
}
// Tapping outside selected text dismisses the Android toolbar. Preserve selection
// handles and copying within the selected text itself.
root.addEventListener('pointerdown',event=>{
 if(!selectedMessageText())return;
 const selection=window.getSelection(),editor=document.activeElement,rects=selection?.rangeCount?[...selection.getRangeAt(0).getClientRects()]:[],inside=editor?.matches('textarea,input')&&editor.selectionEnd>editor.selectionStart&&event.target===editor||rects.some(rect=>event.clientX>=rect.left&&event.clientX<=rect.right&&event.clientY>=rect.top&&event.clientY<=rect.bottom);
 if(!inside)dismissTextSelection();
},true);
root.addEventListener('click',event=>{
 const editor=event.target.closest?.('textarea,input:not([type=radio]):not([type=checkbox]):not([type=range])');
 if(editor?.id&&!editor.disabled&&native&&!selectedMessageText())api('focusEditor',{id:editor.id}).catch(()=>{});
});

function render(){
 if(themePointer&&page==='settings'){themeRenderPending=true;return;}
 if(retainInboxTouch()){inboxRenderPending=true;return;}
 const timelineState=timelinePosition();const panelScroll=document.querySelector('.relationship-body')?.scrollTop||0;const oldThread=current?.thread_id;const focused=document.activeElement?.id&&root.contains(document.activeElement)&&document.activeElement.matches('input,textarea,select')?{id:document.activeElement.id,thread:current?.thread_id,start:document.activeElement.selectionStart,end:document.activeElement.selectionEnd}:null;
 applyTheme();document.body.classList.toggle('chat-open',!!current&&page==='inbox'||page==='media-thread');
 setScreenMarkup(`<div class="app-shell">${nav()}<main class="main ${['inbox','media-thread'].includes(page)?'messages-main':''} ${current&&page==='inbox'||page==='media-thread'?'on-chat':''}">${!['inbox','media-thread'].includes(page)?`<header class="topbar"><div class="wordmark">Reply <span>Pilot</span>${!native?'<b class="demo-tag">DEMO</b>':''}</div></header>`:''}${page==='inbox'?inboxView():page==='queue'?queueView():page==='media'?mediaView():page==='media-thread'?mediaBrowseView():page==='test'?testView():settingsView()}</main></div>${pinMenuView()}${homeCandidatesView()}`);
 if(current&&page==='inbox')restoreTimeline(document.querySelector('.timeline'),timelineState?.thread===String(current.thread_id)?timelineState:null);
 const panelBody=document.querySelector('.relationship-body');if(panelBody&&oldThread===current?.thread_id)panelBody.scrollTop=panelScroll;resizeComposer();if(focused&&focused.thread===current?.thread_id){const field=document.getElementById(focused.id);if(field&&!field.disabled){if(document.activeElement!==field)field.focus({preventScroll:true});if(field.setSelectionRange&&focused.start!=null)field.setSelectionRange(focused.start,focused.end);}}tick();queueMicrotask(fillHistoryGaps);scheduleMessageRefresh();queuePreviewWarm();scheduleLinkPreviews();
}
function inboxView(){const queued=state.jobs.filter(j=>j.status==='scheduled').length;
return `<div class="workspace"><section class="inbox-column ${current?'hidden-mobile':''}">${compose?contactsView():`<header class="inbox-header"><h1>Messages</h1><div class="inbox-header-actions"><span class="inbox-queue-count" ${queued?'':'hidden'}>${queued?`${queued} scheduled`:''}</span><button class="icon-button new-conversation" aria-label="New text message" aria-expanded="false" aria-controls="new-message-form" data-action="new">${icon('plus')}</button></div></header><div class="inbox-search-row"><label class="search">${icon('search')}<input id="search" placeholder="Find a conversation" aria-label="Find a conversation" value="${esc(search)}"></label></div>${inboxBootstrapView()}${pinFeedbackView()}<div class="conversation-list" id="conversation-list">${rowsView()}</div>`}</section><section class="conversation ${current?'open':''}">${current?chatView():`<div class="chat-card"><div class="empty chat-empty">${icon('chat')}<h3>Your conversations, in focus.</h3><p>Choose a conversation to write a reply or set a timer.</p></div></div>`}</section></div>`;}
// Android shares stay separate from the composer until the owner chooses a recipient
// and explicitly adds them. Only native code handles external content URI grants.
function shareForChat(){return !!sharedContent&&Number(sharedContent.thread)===current?.thread_id;}
function shareSummaryView(){const item=sharedContent;if(!item)return '';return `<div class="share-summary"><div class="share-label">${icon(item.attachmentCount?'attach':'send')}<strong>Shared with Reply Pilot</strong></div>${item.text?`<p class="share-preview">${esc(item.text)}</p>`:''}${item.attachmentCount?`<p>${item.attachmentCount} attachment${item.attachmentCount===1?'':'s'} · added after you choose a person</p>`:''}${item.error?`<p class="share-error" role="alert">${esc(item.error)}</p>`:''}</div>`;}
function sharePickerView(){if(!sharedContent)return '';const rows=(state.inbox||[]).filter(row=>row.address&&!row.readOnly).slice(0,8);return `${shareSummaryView()}<p class="share-help">Choose who to share with. You can review everything before sending.</p>${rows.length?`<div class="share-recents"><div class="contacts-caption">RECENT CONVERSATIONS</div>${rows.map((row,i)=>`<button class="contact-row" data-action="share-recent" data-thread="${Number(row.thread_id)}" ${contactPicker.opening?'disabled':''}>${avatar(row.name,i,row.photo)}<span class="contact-details"><strong>${esc(row.name||row.address)}</strong><span>${esc(row.address)}</span></span>${icon('arrow')}</button>`).join('')}</div>`:''}`;}
function shareReviewView(){if(!shareForChat())return '';const item=sharedContent,waiting=item.status==='importing';return `<section id="share-review" class="share-review" aria-label="Review shared content" aria-busy="${waiting}"><div class="share-label">${icon('send')}<strong>Review share</strong></div>${item.text?`<p class="share-preview">${esc(item.text)}</p>`:''}${item.attachmentCount?`<p>${item.attachmentCount} attachment${item.attachmentCount===1?'':'s'}</p>`:''}<p class="share-help">Add this to ${esc(current.name||current.address)}’s draft. Your existing text will be kept.</p>${item.error?`<p class="share-error" role="alert">${esc(item.error)}</p>`:''}<div class="share-actions"><button class="primary" data-action="share-add" ${waiting||!chatReady()||conversation?.readOnly||busy||item.canImport===false?'disabled':''}>${waiting?'<span class="spinner"></span> Adding…':'Add to draft'}</button><button class="text-button" data-action="share-choose" ${waiting?'disabled':''}>Change person</button><button class="text-button" data-action="share-cancel">Cancel</button></div></section>`;}
function syncShareReview(){const old=document.getElementById('share-review'),html=shareReviewView();if(old){if(html)old.outerHTML=html;else old.remove();}else if(html)document.querySelector('.composer')?.insertAdjacentHTML('afterbegin',html);}
function discardShare(){const item=sharedContent;if(!item)return;dismissedShares.add(item.id);sharedContent=null;shareCheck++;api('cancelShare',{id:item.id}).catch(error=>toast(error.message));syncShareReview();}
function chooseShareRecipient(){if(!sharedContent||sharedContent.status==='importing')return;sharedContent.thread=null;closeHomeCandidates(false);leaveChat();page='inbox';openContacts();document.getElementById('recipient')?.blur();}
async function reconcileImportedShare(item,result){
 if(sharedContent!==item)return;
 const saved=result.result||result,thread=item.thread||saved.thread,address=saved.address;
 dismissedShares.add(item.id);sharedContent=null;
 if(current?.thread_id===thread&&page==='inbox'){
  // An acknowledgment can be lost during pause. Re-read the saved draft;
  // never restore the original import result over a newer owner edit.
  if(item.importBody!==undefined&&draft===item.importBody&&conversation?.base===item.importBase){discardDraftEdits(thread,item.importBase);previewDrafts.delete(thread);dirty=false;}
  syncLiveComposer();if(!previewDenied)await openThread(thread,current,{automatic:false});
 }else if(address&&!previewDenied){const check=shareCheck,r=await api('compose',{address});if(check!==shareCheck||sharedContent)return;await openThread(r.thread,{thread_id:r.thread,address,name:r.name},{automatic:false});}
 toast('Your shared draft is ready to review.');
}
async function checkIncomingShare(){
 if(!native)return;const check=++shareCheck;
 try{const result=await api('shareState');if(check!==shareCheck||!result?.id||dismissedShares.has(result.id))return;
  if(sharedContent?.id===result.id){if(result.status==='imported'){await reconcileImportedShare(sharedContent,result);return;}if(sharedContent.status!=='importing'&&result.status==='error'){sharedContent.error=result.error;sharedContent.status=result.canImport===false?'error':'pending';sharedContent.canImport=result.canImport;syncShareReview();}return;}
  sharedContent={...result};timerOpen=false;cancelSendPress();
  if(result.status==='imported'){await reconcileImportedShare(sharedContent,result);return;}
  chooseShareRecipient();
 }catch(error){if(check===shareCheck)toast(error.message||'Shared content could not be opened.');}
}
async function addShareToDraft(){
 const item=sharedContent;if(!shareForChat()||!chatReady()||busy||item.status==='importing'||item.canImport===false||conversation.readOnly)return;
 const epoch=threadEpoch,thread=current.thread_id,base=conversation.base,address=current.address,body=draft;
 item.status='importing';item.error='';item.importBody=body;item.importBase=base;timerOpen=false;cancelSendPress();syncLiveComposer();
 try{await flushDrafts();if(sharedContent!==item||epoch!==threadEpoch||current?.thread_id!==thread||!chatReady()||base!==conversation.base||draft!==body)throw new Error('The conversation changed. Review it and try again.');
  const result=await api('importShare',{id:item.id,thread,expectedAddress:address,expectedBase:base,expectedDraftBody:body});
  if(result.alreadyImported){await reconcileImportedShare(item,result);return;}dismissedShares.add(item.id);if(sharedContent!==item)return;sharedContent=null;
  if(current?.thread_id===thread&&epoch===threadEpoch){nextConversationRead(thread);discardDraftEdits(thread,base);previewDrafts.delete(thread);draft=result.text??body;dirty=!!draft.trim();conversation.draft={body:draft,engine:'Edited by you',base:result.base??base};if(result.attachments)conversation.attachments=result.attachments;for(const job of state.jobs)if(job.thread===thread&&job.status==='scheduled'&&isAutomaticJob(job))job.status='cancelled';rememberChat(thread,conversation,true);syncDraftField();syncLiveComposer();}
  toast('Added to your draft. Review it, then tap Send.');
 }catch(error){if(sharedContent===item){item.status='pending';item.error=error.message||'Couldn’t add this share. Try again.';syncLiveComposer();}}
}
window.onShareReceived=()=>checkIncomingShare();
function recipientNumber(){const value=contactPicker.query.trim();if(!/^\+?[0-9][0-9 ().-]{2,30}$/.test(value))return '';const number=value.replace(/[ ().-]/g,'');return /^\+?[0-9]{3,25}$/.test(number)?number:'';}
function contactsView(){return `<div class="contacts-picker" id="new-message-form"><header class="inbox-header contacts-header"><button class="icon-button" data-action="close-contacts" aria-label="Back to conversations">${icon('back')}</button><h1>${sharedContent?'Share with…':'New message'}</h1></header><div id="share-picker-details">${sharePickerView()}</div><label class="search recipient-search">${icon('search')}<input id="recipient" type="text" inputmode="text" autocomplete="off" autocapitalize="none" spellcheck="false" maxlength="120" aria-label="Name or phone number" placeholder="Name or phone number" value="${esc(contactPicker.query)}"></label><button class="contact-manual secondary" data-action="compose" ${!recipientNumber()||contactPicker.opening?'disabled':''}>${icon('chat')}<span>${sharedContent?'Choose this number':'Start conversation'}</span>${icon('arrow')}</button><div class="contacts-caption"><span>CONTACTS</span><small>${native?'From your phone':'Sample contacts'}</small></div><div class="contact-results" id="contact-results" aria-busy="${contactPicker.loading||contactPicker.opening}">${contactResultsView()}</div></div>`;}
function contactResultsView(){
 if(contactPicker.error)return `<div class="contacts-state" role="status"><p>${esc(contactPicker.error)}</p><button class="secondary" data-action="load-contacts">Retry contacts</button></div>`;
 if(!state.contactsAllowed)return `<div class="contacts-state">${icon('chat')}<h3>Message someone you know</h3><p>Allow contacts to find people by name. You can also enter a phone number above.</p><button class="primary" data-action="${contactPicker.blocked?'appSettings':'request-contacts'}" ${contactPicker.permissionBusy?'disabled':''}>${contactPicker.permissionBusy?'Waiting for Android…':contactPicker.blocked?'Open app settings':'Allow contacts'}</button>${contactPicker.blocked?'<small>In App info, choose Permissions → Contacts → Allow.</small>':''}</div>`;
 if(contactPicker.loading)return `<div class="contacts-state" role="status"><span class="spinner"></span><p>Loading contacts…</p></div>`;
 if(!contactPicker.items.length)return `<div class="contacts-state" role="status"><h3>${contactPicker.query?'No matching contacts':'No contacts with phone numbers'}</h3><p>${contactPicker.query?'Try another name or number, or enter a phone number to start a conversation.':'Add a number in your Contacts app, or enter one above.'}</p><button class="text-button" data-action="load-contacts">Refresh contacts</button></div>`;
 return `${contactPicker.opening?'<p class="contact-opening" role="status">Opening conversation…</p>':''}${contactPicker.items.map((contact,i)=>`<button class="contact-row" data-action="choose-contact" data-contact="${i}" ${contactPicker.opening?'disabled':''}>${avatar(contact.name,i,contact.photo)}<span class="contact-details"><strong>${esc(contact.name||contact.number)}</strong><span>${esc(contact.label||'Phone')} · ${esc(contact.number)}</span></span>${icon('chat')}</button>`).join('')}${contactPicker.hasMore?'<p class="contacts-more">Type more of a name or number to find more contacts.</p>':''}`;
}
function syncSharePicker(){const node=document.getElementById('share-picker-details');if(node)node.innerHTML=sharePickerView();}
function syncContactsView(){syncSharePicker();const results=document.getElementById('contact-results');if(!results)return;results.innerHTML=contactResultsView();results.setAttribute('aria-busy',String(contactPicker.loading||contactPicker.opening));const manual=document.querySelector('[data-action=compose]');if(manual)manual.disabled=!recipientNumber()||contactPicker.opening;const field=document.getElementById('recipient');if(field)field.disabled=contactPicker.opening;}
function closeContacts(){compose=false;clearTimeout(contactPicker.timer);contactPicker.request++;contactPicker.items=[];contactPicker.query='';contactPicker.loading=false;contactPicker.error='';contactPicker.opening=false;}
function openContacts(){blurSearch();compose=true;contactPicker.query='';contactPicker.items=[];contactPicker.error='';contactPicker.loading=!!state.contactsAllowed;render();document.getElementById('recipient')?.focus();flushDraftsOnPause();loadContacts();}
async function loadContacts(){
 if(contactPicker.opening&&state.contactsAllowed)return;
 if(!state.contactsAllowed)contactPicker.opening=false;
 clearTimeout(contactPicker.timer);const request=++contactPicker.request,query=contactPicker.query;
 if(!compose||page!=='inbox')return;
 contactPicker.error='';contactPicker.items=[];contactPicker.hasMore=false;
 if(!state.contactsAllowed){contactPicker.loading=false;syncContactsView();return;}
 contactPicker.loading=true;syncContactsView();
 try{const result=await api('contacts',{query});if(request!==contactPicker.request||!compose||page!=='inbox')return;state.contactsAllowed=!!result.allowed;contactPicker.items=result.allowed?result.contacts||[]:[];contactPicker.hasMore=!!result.allowed&&!!result.hasMore;}
 catch(error){if(request===contactPicker.request)contactPicker.error=error.message||'Contacts could not be loaded.';}
 finally{if(request===contactPicker.request){contactPicker.loading=false;syncContactsView();syncContactSettings();}}
}
async function allowContacts(){
 if(contactPicker.permissionBusy)return;contactPicker.permissionBusy=true;contactPicker.error='';syncContactsView();syncContactSettings();
 try{const result=await api('requestContacts');state.contactsAllowed=!!result.allowed;contactPicker.blocked=!!result.blocked;if(!result.allowed){contactPicker.request++;contactPicker.items=[];contactPicker.loading=false;}if(compose&&page==='inbox')loadContacts();}
 catch(error){contactPicker.error=error.message;toast(error.message);}
 finally{contactPicker.permissionBusy=false;syncContactsView();syncContactSettings();}
}
function contactSettingsView(){return `<div><div class="label">Contacts</div><p>${state.contactsAllowed?'Find saved people by name when starting a message.':'Allow access to choose people from your phone’s contacts.'}</p></div><button class="secondary" data-action="${state.contactsAllowed||contactPicker.blocked?'appSettings':'request-contacts'}" ${contactPicker.permissionBusy?'disabled':''}>${contactPicker.permissionBusy?'Waiting…':state.contactsAllowed?'Manage':contactPicker.blocked?'Open app settings':'Allow contacts'}</button>`;}
function syncContactSettings(){const row=document.getElementById('contacts-setting'),html=contactSettingsView();if(row&&row._contactsMarkup!==html){row.innerHTML=html;row._contactsMarkup=html;}}
async function startContactChat(contact){
 if(busy){toast('Wait for the current reply to finish preparing.');return;}
 if(contactPicker.opening)return;const address=contact?.number||recipientNumber();if(!address){toast('Enter a phone number or choose a contact.');return;}
 const request=++contactPicker.request;clearTimeout(contactPicker.timer);contactPicker.loading=false;contactPicker.opening=true;syncContactsView();
 try{flushDraftsOnPause();if(!compose||page!=='inbox'||request!==contactPicker.request)return;const result=await api('compose',{address});if(!compose||page!=='inbox'||request!==contactPicker.request)return;const name=contact?.name||result.name||address,photo=contact?.photo||result.photo;if(sharedContent)sharedContent.thread=result.thread;closeContacts();await openThread(result.thread,{thread_id:result.thread,address,name,photo},{automatic:!sharedContent});}
 finally{if(request===contactPicker.request){contactPicker.opening=false;syncContactsView();}}
}
function isPinned(row){return row?.pinned===true;}
function rowsView(){if(!inboxBootstrap.authoritative&&!state.inbox.length)return '<div class="inbox-start-placeholder" aria-hidden="true"></div>';const rows=orderInboxRows(matchingInboxRows());if(!rows.length)return `<div class="empty">${icon('chat')}<h3>${search?'No matches':'Your conversations will appear here.'}</h3><p>${search?'Try another name or number.':'Complete Phone setup in Settings to see your texts.'}</p></div>${inboxPagingView(0)}`;const hasPins=rows.some(isPinned),total=rows.length;return rows.slice(0,inboxRenderLimit).map((x,i)=>{const queued=state.jobs.find(j=>j.thread===x.thread_id&&j.status==='scheduled'),receipt=messageStatus(x),pinned=isPinned(x),group=hasPins&&(i===0||pinned!==isPinned(rows[i-1]))?`<div class="conversation-group">${pinned?'Pinned':'All messages'}</div>`:'';return `${group}<div class="conversation-row" data-row-thread="${x.thread_id}"><button class="row ${current?.thread_id===x.thread_id?'selected':''}" data-action="open" data-thread="${x.thread_id}" data-pinned="${pinned}" aria-description="Press and hold for pin options." aria-keyshortcuts="Shift+F10">${avatar(x.name,i,x.photo,i<12)}<div class="row-main"><div class="row-title"><span>${esc(x.name||x.address)}</span><time>${date(x.date)===date(Date.now())?time(x.date):date(x.date)}</time></div><p>${[2,4,5,6].includes(x.type)?'You: ':''}${esc(x.body)}</p>${pinned?`<span class="row-pin" aria-label="Pinned conversation" title="Pinned">${icon('pin')}</span>`:''}${queued?`<span class="row-badge">${icon('clock')} Reply scheduled</span>`:receipt&&receipt.kind!=='sent'?`<span class="row-badge" data-receipt="${receipt.kind}" title="${esc(receipt.note)}">${icon(receipt.icon)} ${receipt.label}</span>`:x.type===1&&x.read===0?`<span class="row-badge">${inboxBootstrap.authoritative?icon('spark'):''} ${inboxBootstrap.authoritative?'Ready to review':'Unread'}</span>`:''}</div></button></div>`;}).join('')+inboxPagingView(total);}
function pinMenuView(){if(!pinMenu)return '';const row=state.inbox.find(item=>item.thread_id===pinMenu.thread);if(!row)return '';return `<div class="chat-menu-layer" id="chat-menu-layer"><div class="chat-menu-backdrop" data-action="close-chat-menu" aria-hidden="true"></div><section class="chat-pin-menu" id="chat-pin-menu" role="dialog" aria-modal="true" aria-labelledby="chat-pin-title"><h2 id="chat-pin-title">${esc(row.name||row.address)}</h2><p>Keep favorite conversations close.</p><button class="chat-pin-action" data-action="pin-chat" ${pinMenu.saving?'disabled':''}>${icon('pin')}<span>${pinMenu.saving?'Saving…':isPinned(row)?'Unpin conversation':'Pin conversation'}</span></button><p class="chat-pin-error" role="alert">${esc(pinMenu.error||'')}</p><button class="text-button chat-menu-cancel" data-action="close-chat-menu">Cancel</button></section></div>`;}
function syncPinMenu(){const layer=document.getElementById('chat-menu-layer');if(layer){const template=document.createElement('template');template.innerHTML=pinMenuView();const next=template.content.firstElementChild;if(next)layer.replaceWith(next);else layer.remove();}else if(pinMenu)root.insertAdjacentHTML('beforeend',pinMenuView());}
function openPinMenu(thread,trigger){if(pinMenu?.thread===thread)return;if(!Number.isSafeInteger(thread)||thread<=0||!state.inbox.some(row=>row.thread_id===thread))return;if(!pinPress?.held)cancelPinPress();pinMenu={thread,focus:document.activeElement,trigger:trigger?.dataset.action||'open',saving:false,error:''};syncPinMenu();document.querySelector('#chat-pin-menu [data-action=pin-chat]')?.focus({preventScroll:true});}
function closePinMenu(restore=true){if(!pinMenu)return;const previous=pinMenu;pinMenu=null;syncPinMenu();if(restore){const target=previous.focus?.isConnected?previous.focus:document.querySelector(`[data-action="${previous.trigger}"][data-thread="${previous.thread}"]`);target?.focus({preventScroll:true});}}
function pinFeedbackView(){const failure=[...pinFailures.values()].at(-1);return failure?`<div class="pin-feedback" id="pin-feedback" role="alert"><span>${esc(failure.error)}</span><button class="text-button" data-action="retry-pin" data-thread="${failure.thread}">Retry</button><button class="icon-button" data-action="dismiss-pin-error" aria-label="Dismiss pin error">${icon('close')}</button></div>`:'';}
function syncPinFeedback(){const old=document.getElementById('pin-feedback'),html=pinFeedbackView();if(old){if(html)old.outerHTML=html;else old.remove();}else if(html)document.querySelector('.inbox-search-row')?.insertAdjacentHTML('afterend',html);}
function overlayMutations(snapshot,read,newerInbox=new Set()){
 if(themeMutation){if(themeMutation.phase==='ack'&&read>themeMutation.ackRead){themeMutation=null;themeSaved=snapshot.theme;}else snapshot.theme=themeMutation.value;}else if(themes.some(([id])=>id===snapshot.theme))themeSaved=snapshot.theme;
 snapshot.inbox=snapshot.inbox.map(row=>{const item=pinMutations.get(row.thread_id);if(!item){pinConfirmed.set(row.thread_id,isPinned(row));return row;}if(item.phase==='ack'&&read>item.ackRead&&!newerInbox.has(row.thread_id)){pinMutations.delete(row.thread_id);pinConfirmed.set(row.thread_id,isPinned(row));return row;}return {...row,pinned:item.pinned};});
 if(homeReceipt){if(read>homeReceipt.ackRead)homeReceipt=null;else snapshot.location={...snapshot.location,...homeReceipt.value};}
}
function syncThemeUI(){applyTheme();document.querySelectorAll('.theme-choice').forEach(button=>{const selected=button.dataset.themeChoice===state.theme;button.classList.toggle('active',selected);button.setAttribute('aria-pressed',String(selected));const check=button.querySelector(':scope > svg');if(check)check.toggleAttribute('hidden',!selected);});}
function changeTheme(value){
 if(!themes.some(([id])=>id===value))return;if(state.theme===value){syncThemeUI();return themeTask;}
 const item={token:++mutationSequence,value,phase:'pending'};themeMutation=item;themeQueued=item;state.theme=value;syncThemeUI();
 if(!themeWriting)themeTask=writeLatestTheme();return themeTask;
}
async function writeLatestTheme(){
 themeWriting=true;
 try{while(themeQueued){const item=themeQueued;themeQueued=null;try{
   if(item.value!==themeSaved){const result=await api('setTheme',{theme:item.value});if(result?.theme!==item.value)throw new Error('The theme could not be saved.');themeSaved=item.value;if(!native)try{localStorage.setItem('reply-pilot-demo-theme',item.value);}catch{}}
   if(themeMutation===item){item.phase='ack';item.ackRead=snapshotSequence;toast('Theme saved.');}
  }catch(error){if(themeMutation===item){themeMutation={token:item.token,value:themeSaved,phase:'ack',ackRead:snapshotSequence};state.theme=themeSaved;syncThemeUI();toast(error.message||'The theme could not be saved. Try again.');}}
 }}finally{themeWriting=false;}
}
function releaseThemePointer(){if(!themePointer)return;clearTimeout(themePointer.timer);themePointer=null;if(themeRenderPending){themeRenderPending=false;render();}}
root.addEventListener('pointerdown',event=>{const button=event.target.closest?.('.theme-choice');if(!button||event.button!==0||event.isPrimary===false)return;if(themePointer)clearTimeout(themePointer.timer);themePointer={id:event.pointerId,x:event.clientX,y:event.clientY,timer:null};},true);
document.addEventListener('pointermove',event=>{if(themePointer&&event.pointerId===themePointer.id&&Math.hypot(event.clientX-themePointer.x,event.clientY-themePointer.y)>12)releaseThemePointer();},true);
document.addEventListener('pointerup',event=>{if(themePointer&&event.pointerId===themePointer.id)themePointer.timer=setTimeout(releaseThemePointer,350);},true);
document.addEventListener('pointercancel',event=>{if(themePointer&&event.pointerId===themePointer.id)releaseThemePointer();},true);
window.addEventListener('blur',releaseThemePointer);

function pinChat(thread=pinMenu?.thread,desired){
 const row=state.inbox.find(item=>item.thread_id===thread);if(!row)return Promise.resolve();
 const pinned=typeof desired==='boolean'?desired:!isPinned(row),generation=pinGeneration,restoreFocus=pinMenu?.focus||document.activeElement;
 if(!pinConfirmed.has(thread))pinConfirmed.set(thread,isPinned(row));
 const item={thread,pinned,token:++mutationSequence,phase:'pending'};pinMutations.set(thread,item);pinFailures.delete(thread);row.pinned=pinned;closePinMenu(false);syncInboxList();syncPinFeedback();
 (restoreFocus?.isConnected?restoreFocus:document.querySelector(`[data-action="open"][data-thread="${thread}"]`))?.focus({preventScroll:true});
 const task=(pinTasks.get(thread)||Promise.resolve()).catch(()=>{}).then(async()=>{try{if(generation!==pinGeneration)return;const result=await api('pinChat',{thread,pinned});if(result?.thread!==thread||result?.pinned!==pinned)throw new Error('The pin could not be saved. Try again.');if(generation!==pinGeneration)return;pinConfirmed.set(thread,pinned);if(pinMutations.get(thread)===item){item.phase='ack';item.ackRead=snapshotSequence;}}catch(error){if(generation!==pinGeneration||pinMutations.get(thread)!==item)return;pinMutations.set(thread,{...item,pinned:pinConfirmed.get(thread)===true,phase:'ack',ackRead:snapshotSequence});const currentRow=state.inbox.find(row=>row.thread_id===thread);if(currentRow)currentRow.pinned=pinConfirmed.get(thread)===true;pinFailures.set(thread,{thread,pinned,error:error.message||'The pin could not be saved. Try again.'});syncInboxList();syncPinFeedback();toast(error.message||'The pin could not be saved. Try again.');}finally{if(pinTasks.get(thread)===task)pinTasks.delete(thread);}});
 pinTasks.set(thread,task);return task;
}
// Keep the actual pressed row until the browser dispatches its click. Replacing
// it during an inbox refresh loses the tap, including touch's implicit capture.
function retainInboxTouch(){return !!inboxTouch&&!previewDenied&&inboxTouch.generation===privateGeneration&&page==='inbox'&&!compose&&inboxTouch.scope===page+':'+(current?.thread_id||'');}
function releaseInboxTouch(){
 if(!inboxTouch)return;
 const flush=retainInboxTouch(),renderPending=inboxRenderPending,listPending=inboxListPending;
 clearTimeout(inboxTouch.timer);inboxTouch=null;inboxRenderPending=false;inboxListPending=false;
 if(flush){if(renderPending)render();else if(listPending)syncInboxList();}
}
function finishInboxTouch(delay=0){if(!inboxTouch)return;clearTimeout(inboxTouch.timer);inboxTouch.timer=setTimeout(releaseInboxTouch,delay);}
function cancelPinPress(suppress=false){if(!pinPress)return;if(suppress)suppressedPinClick={thread:pinPress.thread,held:pinPress.held,until:Date.now()+1000};clearTimeout(pinPress.timer);pinPress=null;}
root.addEventListener('pointerdown',event=>{if(event.button===0&&event.isPrimary!==false)suppressedPinClick=null;},true);
root.addEventListener('pointerdown',event=>{const row=event.target.closest('.row');if(!row||event.button!==0||event.isPrimary===false||pinMenu)return;cancelPinPress();suppressedPinClick=null;if(inboxTouch)clearTimeout(inboxTouch.timer);inboxTouch={pointer:event.pointerId,scope:page+':'+(current?.thread_id||''),generation:privateGeneration,timer:null};const press={pointer:event.pointerId,thread:Number(row.dataset.thread),row,x:event.clientX,y:event.clientY,held:false};pinPress=press;press.timer=setTimeout(()=>{if(pinPress!==press||!row.isConnected||page!=='inbox'||compose)return;press.held=true;openPinMenu(press.thread,row);},500);});
document.addEventListener('pointermove',event=>{if(pinPress&&event.pointerId===pinPress.pointer&&Math.hypot(event.clientX-pinPress.x,event.clientY-pinPress.y)>12)cancelPinPress(true);});
document.addEventListener('pointerup',event=>{if(inboxTouch?.pointer===event.pointerId)finishInboxTouch(350);if(!pinPress||event.pointerId!==pinPress.pointer)return;const held=pinPress.held;cancelPinPress(held);if(held){event.preventDefault();event.stopPropagation();}},true);
document.addEventListener('pointercancel',event=>{if(inboxTouch?.pointer===event.pointerId)finishInboxTouch();if(pinPress&&event.pointerId===pinPress.pointer)cancelPinPress(true);});
root.addEventListener('scroll',event=>{if(event.target.matches?.('.conversation-list'))cancelPinPress(true);},true);
root.addEventListener('click',event=>{const row=event.target.closest('.row');if(row||suppressedPinClick?.held)finishInboxTouch();if(event.detail!==0&&suppressedPinClick&&(suppressedPinClick.held||row&&suppressedPinClick.thread===Number(row.dataset.thread))&&Date.now()<suppressedPinClick.until){suppressedPinClick=null;event.preventDefault();event.stopImmediatePropagation();}},true);
root.addEventListener('contextmenu',event=>{const row=event.target.closest('.row');if(row){event.preventDefault();if(pinPress?.thread===Number(row.dataset.thread)){pinPress.held=true;}else suppressedPinClick={thread:Number(row.dataset.thread),until:Date.now()+1000};openPinMenu(Number(row.dataset.thread),row);}});
root.addEventListener('keydown',event=>{const row=event.target.closest('.row');if(row&&(event.key==='ContextMenu'||event.shiftKey&&event.key==='F10')){event.preventDefault();openPinMenu(Number(row.dataset.thread),row);}});
document.addEventListener('keydown',event=>{if(!pinMenu)return;if(event.key==='Escape'){event.preventDefault();event.stopImmediatePropagation();closePinMenu();}else if(event.key==='Tab'){const buttons=[...document.querySelectorAll('#chat-pin-menu button:not(:disabled)')],index=buttons.indexOf(document.activeElement),next=event.shiftKey?(index<=0?buttons.length-1:index-1):(index+1)%buttons.length;event.preventDefault();buttons[next]?.focus({preventScroll:true});}},true);
window.addEventListener('blur',()=>{cancelPinPress();releaseInboxTouch();});
document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='hidden'){cancelPinPress();releaseInboxTouch();}});
// Sends use the saved SIM, so a new choice is saved at once instead of waiting for Save preferences.
async function saveSendingSim(sub){if(!native||!Number.isInteger(sub)||sub<0)return;try{await api('settings',{sub});state.sub=sub;if(preferences)preferences.sub=sub;await refresh(true);toast('Sending SIM saved.');}catch(error){toast(error.message);}}
// Android shares SIMs only with Phone access; an empty menu must say why and how to fix it.
function simHelpView(){if(!native||state.sims?.length)return '';const needsPhone=state.phoneAccess===false;return `<div class="sim-help" data-sim-help><p class="setup-help" role="status">${needsPhone?'Reply Pilot needs Phone access to find your SIM. Texts can’t send until it’s allowed.':'No active SIM found. Check Android Settings → Network &amp; internet → SIMs and turn off airplane mode.'}</p>${needsPhone?`<div class="connection-actions"><button class="secondary" data-action="permissions">Allow Phone access</button><button class="text-button" data-action="appSettings">Open app permissions ${icon('arrow')}</button></div><p class="setup-help">If nothing pops up, tap Open app permissions → Permissions → Phone → Allow.</p>`:''}</div>`;}
function setupView(){return `<div data-phone-setup>${setupContent()}</div>`;}
function syncInboxList(){
 if(retainInboxTouch()){inboxListPending=true;return;}
 syncSharePicker();
 const list=document.getElementById('conversation-list');if(!list)return;
 const oldScroll=list.scrollTop,rect=list.getBoundingClientRect();
 const anchor=oldScroll>1?[...list.querySelectorAll('.row')].find(row=>row.getBoundingClientRect().bottom>rect.top+1):null;
 const position=anchor?{thread:anchor.dataset.thread,offset:anchor.getBoundingClientRect().top-rect.top}:null;
 const focused=document.activeElement?.closest?.('.row');const focusedThread=focused&&list.contains(focused)?focused.dataset.thread:null,focusedAction=focused?.dataset.action;
 list.innerHTML=rowsView();
 if(position){const row=[...list.querySelectorAll('.row')].find(item=>item.dataset.thread===position.thread);list.scrollTop=row?list.scrollTop+row.getBoundingClientRect().top-list.getBoundingClientRect().top-position.offset:oldScroll;}
 else list.scrollTop=oldScroll;
 if(focusedThread)[...list.querySelectorAll('.row')].find(row=>row.dataset.thread===focusedThread&&row.dataset.action===focusedAction)?.focus({preventScroll:true});
 const counter=document.querySelector('.inbox-queue-count'),queued=state.jobs.filter(job=>job.status==='scheduled').length;
 if(counter){counter.textContent=queued?`${queued} scheduled`:'';counter.hidden=!queued;}
}
function setupContent(){return `<div class="notice">Send texts and attachments to one person at a time. MMS uses your carrier’s mobile data and size limits. RCS and group replies are not supported.</div><div class="setup-grid">${[['role',state.defaultSms,'Default texting app','Choose Reply Pilot for SMS'],['permissions',state.permissions,'Allow message access','Read and send your texts'],['alarms',state.exact,'Enable precise timers','Send accepted replies after your delay']].map(([action,done,title,desc],i)=>`<button data-action="${action}" class="setup-step ${done?'done':''}" ${action==='role'&&roleBusy?'disabled':''}><span class="step-count">${done?icon('check'):i+1}</span><div><strong>${title}</strong><small>${action==='role'&&roleBusy?'Waiting for Android…':desc}</small></div>${icon('arrow')}</button>`).join('')}</div><p class="setup-status" role="status">${esc(state.defaultSms?'Reply Pilot is your default texting app.':roleMessage)}</p>${!state.defaultSms?`<div class="setup-recovery-actions"><button class="secondary" data-action="defaultSettings">Open Android default apps ${icon('arrow')}</button><button class="text-button" data-action="check-role" ${roleChecking?'disabled':''}>${roleChecking?'Checking…':'Check status'}</button></div><p class="setup-help">Choose SMS app → Reply Pilot. If Android denies access, open Reply Pilot’s App info → ⋮ → Allow restricted settings, then try again.</p>`:''}`;}
function syncPhoneSetup(){const html=setupContent();document.querySelectorAll('[data-phone-setup]').forEach(el=>{if(el._setupMarkup!==html){el.innerHTML=html;el._setupMarkup=html;}});}

// Default-app checks never wait behind inbox, photo or saved-history work.
function applySmsRoleStatus(result,showMessage=false){
 if(typeof result?.defaultSms!=='boolean')return false;
 roleStatusRevision++;state.defaultSms=result.defaultSms;
 if(typeof result.permissions==='boolean')state.permissions=result.permissions;
 if(result.defaultSms){roleMessage='';if(roleBusy){roleAttempt++;roleBusy=false;}}
 else if(showMessage)roleMessage=result.message||'';
 if(!result.defaultSms||result.permissions===false){accessHint=false;if(!previewDenied)clearPrivateChats();}
 else if(result.defaultSms&&result.permissions===true){accessHint=true;previewDenied=false;}
 syncPhoneSetup();return true;
}
async function chooseDefaultSms(){
 if(roleBusy)return;const attempt=++roleAttempt;roleCheckRequest++;roleChecking=false;roleBusy=true;roleMessage='';syncPhoneSetup();
 try{const result=await api('role');if(attempt!==roleAttempt)return;roleBusy=false;if(result)applySmsRoleStatus(result,true);}
 catch(error){if(attempt===roleAttempt)roleMessage=error.message||'Android could not open the chooser. Use Open Android default apps.';}
 finally{if(attempt===roleAttempt){roleBusy=false;syncPhoneSetup();}}
}
async function checkSmsRoleStatus(showMessage=false){
 const request=++roleCheckRequest,attempt=roleAttempt,statusRevision=roleStatusRevision;let deadline;roleChecking=true;syncPhoneSetup();
 try{
  const result=await Promise.race([api('smsRoleStatus'),new Promise((_,reject)=>{deadline=setTimeout(()=>reject(new Error('Android’s status is unavailable. Open Android default apps to check your SMS app.')),5000);})]);
  if(request!==roleCheckRequest||attempt!==roleAttempt||statusRevision!==roleStatusRevision)return;
  if(applySmsRoleStatus(result,showMessage||roleBusy)){if(result.pending===false&&roleBusy){roleBusy=false;roleAttempt++;}else if(result.pending===true)roleBusy=true;}
 }catch(error){if(request===roleCheckRequest&&attempt===roleAttempt&&statusRevision===roleStatusRevision&&showMessage)roleMessage=error.message;}
 finally{clearTimeout(deadline);if(request===roleCheckRequest){roleChecking=false;syncPhoneSetup();}}
}
async function openSmsSettings(action){
 // Retire this UI attempt before native cancels its matching Android request.
 roleAttempt++;roleCheckRequest++;roleBusy=false;roleChecking=false;roleMessage=action==='defaultSettings'?'Choose SMS app → Reply Pilot in Android Settings, then return here.':'Check Reply Pilot’s permissions in Android Settings, then return here.';syncPhoneSetup();
 try{await api(action);}catch(error){roleMessage=error.message;syncPhoneSetup();}
}

function replyProfileRecord(thread=current?.thread_id){let record=replyProfiles.get(thread);if(!record){record={data:null,verified:false,loading:false,error:'',request:0};replyProfiles.set(thread,record);}return record;}
function profileContext(thread=current?.thread_id){return replyProfiles.get(thread)?.data||(thread===current?.thread_id?conversation:null)||{};}
function profileReady(){const record=replyProfiles.get(current?.thread_id);return !!current&&!previewDenied&&accessHint!==false&&(record?.verified&&record.epoch===threadEpoch||chatReady())&&profileContext().readOnly!==true;}
function profileLoadView(){const record=replyProfileRecord(),context=profileContext();if(record.verified||chatReady())return context.readOnly?'<p class="profile-load-status">Reply settings are available for individual contacts. This conversation is read-only.</p>':'';return `<div class="profile-load-status" role="status"><span>${record.error?'Couldn’t check these reply settings. Your edits are kept.':'You can adjust these settings while your contact loads.'}</span>${record.error?'<button class="text-button" data-action="retry-profile">Try again</button>':''}</div>`;}
function acceptReplyProfile(thread,result,epoch,authoritative=false){
 if(!result?.relationship||thread!==current?.thread_id||epoch!==threadEpoch||previewDenied)return false;
 const oldName=current.name,oldPhoto=current.photo,record=replyProfileRecord(thread),revision=Number(result.profileRevision??result.relationship.profileRevision??result.relationship.revision),oldRevision=Number(record.data?.profileRevision);
 if(record.data&&Number.isFinite(revision)&&Number.isFinite(oldRevision)&&revision<oldRevision)return false;
 record.data={...record.data,...result,thread,profileRevision:result.profileRevision??result.relationship.profileRevision??result.relationship.revision};record.epoch=epoch;record.verified=authoritative;record.error='';
 if(contactsDenied){record.data.name=record.data.address;record.data.photo='';}
 relationshipEdit(thread,record.data.relationship);
 if(!contactsDenied&&result.name)current.name=result.name;if(Object.prototype.hasOwnProperty.call(result,'photo'))current.photo=contactsDenied?'':contactPhotoUrl(result.photo);
 if(oldName!==current.name||oldPhoto!==current.photo)queueMicrotask(()=>{if(current?.thread_id===thread&&threadEpoch===epoch)syncChatHeader();});return true;
}
async function loadReplyProfile(thread=current?.thread_id){
 if(!thread||thread!==current?.thread_id||previewDenied)return;const record=replyProfileRecord(thread),epoch=threadEpoch,request=++record.request,generation=privateGeneration,address=current.address;record.loading=true;record.error='';syncReplyModes();
 try{const result=await api('replyProfile',{thread,expectedAddress:address||''});if(current?.thread_id!==thread||epoch!==threadEpoch||request!==record.request||generation!==privateGeneration||previewDenied)return;
  if(!result?.relationship||result.thread!==thread||result.access?.readSms!==true||result.access?.defaultSms!==true)throw new Error('Reply settings are still loading. Try again in a moment.');
  if(result.access.contacts===false){contactsDenied=true;clearContactPhotos();}acceptReplyProfile(thread,result,epoch,true);
 }catch(error){if(current?.thread_id===thread&&epoch===threadEpoch&&request===record.request)record.error=error.message||'Reply settings could not be checked.';}
 finally{if(current?.thread_id===thread&&epoch===threadEpoch&&request===record.request){record.loading=false;if(relationshipEdits.get(thread)?.open)render();else syncChatHeader();}}
}
async function recheckReplyHistory(){
 if(!current||!conversation||previewDenied)return;
 const thread=current.thread_id,epoch=threadEpoch,generation=privateGeneration,edit=relationshipEdit(),record=replyProfileRecord(thread);if(record.loading||edit.logBusy)return;
 if(edit.samples===edit.saved.samples){await loadReplyProfile(thread);return;}
 // Recheck already parsed, unsaved examples without replacing any typed log or settings.
 const samples=edit.samples,request=++record.request;record.loading=true;record.error='';syncReplyModes();
 try{const result=await api('analyzeChatLog',{thread,body:samples,ownerLabel:''});if(current?.thread_id!==thread||epoch!==threadEpoch||generation!==privateGeneration||request!==record.request||previewDenied||edit.samples!==samples)return;
  if(typeof result?.replyEligibility?.eligible!=='boolean')throw new Error('History could not be checked. Try again.');
  edit.checkedSamples=samples;edit.checkedEligibility=result.replyEligibility;edit.checkedBase=conversation.base;
 }catch(error){if(current?.thread_id===thread&&epoch===threadEpoch&&request===record.request)record.error=error.message||'History could not be checked. Try again.';}
 finally{if(current?.thread_id===thread&&epoch===threadEpoch&&request===record.request){record.loading=false;syncReplyModes();}}
}
function toggleReplySetup(){if(!current||!conversation)return;const edit=relationshipEdit();edit.open=!edit.open;timerOpen=false;render();if(edit.open){if(!profileReady()&&!replyProfileRecord().loading)loadReplyProfile();root.querySelector('#relationship-panel input')?.focus({preventScroll:true});}}

function profileValue(remote){const enabled=remote?.cloudEnabled===true&&remote?.autoDraft===true&&remote?.autoSend===true;return {text:remote?.body||'',samples:remote?.samples||'',cloudEnabled:enabled,autoDraft:enabled,autoSend:enabled,autoDelay:remote?.autoDelayMode!=='range'&&autopilotTimers.some(([n])=>n===remote?.autoDelay)?remote.autoDelay:0,autoDelayMode:'fixed',autoDelayMin:300,autoDelayMax:1800,engagement:'always_reply',shareLocation:remote?.shareLocation===true,importantDetails:typeof remote?.importantDetails==='string'?remote.importantDetails:'',planHandling:'delay_answer'};}
function profileDirty(edit){return ['text','samples','cloudEnabled','autoDraft','autoSend','autoDelay','autoDelayMode','autoDelayMin','autoDelayMax','engagement','shareLocation','importantDetails','planHandling'].some(k=>edit[k]!==edit.saved[k]);}
function relationshipEdit(thread=current.thread_id,remote=profileContext(thread).relationship){
 const saved=profileValue(remote);let edit=relationshipEdits.get(thread);
 if(!edit){edit={...saved,saved,open:false,samplesOpen:false};relationshipEdits.set(thread,edit);}
 else if(JSON.stringify(edit.saved)!==JSON.stringify(saved)){const previous=edit.saved;for(const key of Object.keys(saved))if(edit[key]===previous[key])edit[key]=saved[key];if(edit.logText===previous.samples)edit.logText=saved.samples;edit.saved=saved;}
 return edit;
}

function profileMode(edit){return edit.cloudEnabled&&edit.autoDraft&&edit.autoSend?'autopilot':'off';}
function relationshipStatus(edit){if(!profileReady())return profileContext().readOnly?'Autopilot is unavailable for this conversation.':profileDirty(edit)?'Your changes are kept here while reply settings are checked.':'Checking saved reply settings…';if(edit.autoSend&&profileEligibility(edit).available===false)return 'History couldn’t be checked. Recheck to try again.';if(edit.autoSend&&!profileEligibility(edit).eligible)return 'Train Autopilot for this chat before turning it on. You can still write and send messages.';return profileDirty(edit)?'Unsaved changes. Save to apply these reply settings.':edit.autoSend?`Autopilot sends ${edit.autoDelay===0?'as soon as a reply is ready':`after ${timerText(edit.autoDelay)}`}.${state.autoDraft?'':' Autopilot is paused in Settings.'}`:'Autopilot is off for this person.';}
function profileBadge(edit){return profileDirty(edit)?'Unsaved':edit.autoSend?'Autopilot':'Off';}
function radioGroup(name,title,options,value){return `<fieldset class="profile-group" ${busy||name==='person-mode'&&profileContext().readOnly?'disabled':''}><legend>${title}</legend><div class="radio-options">${options.map(([v,label,note],i)=>`<label class="radio-choice" for="${name}-${i}"><input type="radio" id="${name}-${i}" name="${name}" value="${esc(v)}" ${value===v?'checked':''}><span><strong>${esc(label)}</strong>${note?`<small>${esc(note)}</small>`:''}</span></label>`).join('')}</div></fieldset>`;}

function profileEligibility(edit){
 if(edit?.samples===edit?.saved?.samples)return profileContext().replyEligibility||profileContext().relationship?.replyEligibility||{eligible:false,total:0,owner:0,incoming:0};
 if(edit?.checkedSamples===edit?.samples&&edit.checkedEligibility&&edit.checkedBase===conversation?.base)return edit.checkedEligibility;
 return {eligible:false,total:0,owner:0,incoming:0};
}
// Train Autopilot: one persona per chat, built only when the owner taps Train.
const personaTraining=new Map();
function personaStatus(thread=current?.thread_id){const value=profileContext(thread).persona;return value&&typeof value==='object'?value:{state:'untrained',trained:false};}
function personaDate(ms){const date=new Date(Number(ms)||0);return Number(ms)>0&&!Number.isNaN(date.getTime())?date.toLocaleDateString(undefined,{month:'short',day:'numeric',year:'numeric'}):'';}
function personaDetailsView(info){
 const p=info.persona||{},rows=[['How you text them',p.writingStyle],['Your relationship',p.relationship],['Ongoing context',p.context],['Avoid',p.avoid]].filter(([,value])=>typeof value==='string'&&value.trim()),examples=Array.isArray(p.examples)?p.examples.filter(e=>typeof e?.reply==='string'):[];
 if(!rows.length&&!examples.length)return '';
 return `<details class="persona-details"><summary>What Autopilot learned</summary>${rows.map(([title,value])=>`<h5>${title}</h5><p>${esc(value)}</p>`).join('')}${examples.length?`<h5>Your example replies (${examples.length})</h5><ul class="persona-examples">${examples.map(e=>`<li>${e.incoming?`<span>${esc(e.incoming)}</span>`:''}<strong>${esc(e.reply)}</strong></li>`).join('')}</ul>`:''}<small>If something is wrong, correct it in Relationship Dynamic or Important Details below. Those always take priority.</small></details>`;
}
function trainPanelView(){
 if(!current||profileContext().readOnly)return '';
 const info=personaStatus(),local=personaTraining.get(current.thread_id)||{},training=!!local.busy||info.state==='training',name=esc(current.name||current.address||'this person'),count=value=>(Number(value)||0).toLocaleString();
 const error=local.error||(!training&&info.state==='error'?info.error||'':'');
 let body;
 if(training)body=`<div class="persona-status" role="status" aria-busy="true"><span class="spinner"></span><div><strong>Training Autopilot…</strong><small>Reading your recent texts with ${name}. This usually takes under a minute. You can keep using the app.</small></div></div>`;
 else if(info.trained)body=`<div class="persona-status" role="status"><span class="persona-check">${icon('check')}</span><div><strong>Trained on ${count(info.trainedMessages)} messages</strong><small>${personaDate(info.trainedAt)}${Number(info.newMessages)>0?` · ${count(info.newMessages)} new since`:''}</small></div></div>${info.suggestRetrain?`<p class="persona-note">You and ${name} have exchanged ${count(info.newMessages)} messages since training. Retrain to keep Autopilot current.</p>`:''}${info.thin?`<p class="persona-note">Only ${count(info.trainedMessages)} messages were available, so Autopilot knows less about how you text ${name}.</p>`:''}${personaDetailsView(info)}`;
 else body=`<p>Autopilot learns how you text ${name} from your most recent 1,000 texts together, then replies in your voice. Nothing about this chat goes to your AI service until you tap Train Autopilot.</p>`;
 const actions=training?'':`<div class="persona-actions"><button class="${info.trained?'secondary':'primary'}" data-action="train-persona" ${!native||!state.cloud?.configured||busy?'disabled':''}>${info.trained?'Retrain':'Train Autopilot'}</button>${info.trained?`<button class="text-button danger" data-action="forget-persona" ${busy?'disabled':''}>Remove training</button>`:''}</div>${!state.cloud?.configured&&native?'<small>Connect your AI service in Settings first.</small>':''}${!native?'<small>Browser preview: training uses fictional data.</small>':''}`;
 return `<section class="persona-panel" id="persona-panel" data-thread="${current.thread_id}"><h4>Train Autopilot</h4>${body}${error?`<p class="chat-log-error" role="alert">${esc(error)}</p>`:''}${actions}</section>`;
}
function applyPersona(thread,result){
 if(!result?.persona||typeof result.persona!=='object')return;const record=replyProfileRecord(thread);
 if(record.data){record.data.persona=result.persona;if(result.replyEligibility)record.data.replyEligibility=result.replyEligibility;}
 if(current?.thread_id===thread&&conversation&&result.replyEligibility)conversation.replyEligibility=result.replyEligibility;
}
async function trainPersona(){
 if(!current||!conversation)return;const thread=current.thread_id;if(personaTraining.get(thread)?.busy)return;
 if(native&&!state.cloud?.configured){toast('Connect your AI service in Settings first.');return;}
 personaTraining.set(thread,{busy:true,error:''});syncReplyModes();
 try{const result=await api('trainPersona',{thread,expectedAddress:profileContext(thread).address||current.address||''});applyPersona(thread,result);personaTraining.set(thread,{busy:false,error:''});if(current?.thread_id===thread)toast(personaStatus(thread).trained?'Autopilot is trained for this chat.':'Training finished.');}
 catch(error){personaTraining.set(thread,{busy:false,error:error.message||'Training could not finish. Try again.'});}
 finally{if(current?.thread_id===thread)syncReplyModes();}
}
async function forgetPersona(){
 if(!current||!conversation||busy)return;const thread=current.thread_id;if(personaTraining.get(thread)?.busy)return;
 try{const result=await api('forgetPersona',{thread,expectedAddress:profileContext(thread).address||current.address||''});applyPersona(thread,result);personaTraining.delete(thread);toast('Training removed. Autopilot stays off for this chat until you train it again.');}
 catch(error){toast(error.message);}
 finally{if(current?.thread_id===thread)syncReplyModes();}
}
function replyModesView(edit){
 const record=replyProfileRecord(),checked=profileReady(),checking=record.loading||!checked&&!record.error;
 const options=[['off','Off','Write your own replies.'],['autopilot','Autopilot','Reply automatically in your trained voice for this chat.']];
 const modes=radioGroup('person-mode','AI replies',options,profileMode(edit));
 const status=checking?'<div id="reply-readiness" class="reply-readiness" role="status" aria-busy="true"><strong>Checking reply settings…</strong></div>':record.error?`<div id="reply-readiness" class="reply-readiness" role="status"><strong>Reply settings couldn’t load</strong><p class="chat-log-error">${esc(record.error)}</p><button class="text-button" data-action="retry-profile">Try again</button></div>`:'';
 return `<div id="reply-modes">${checked?trainPanelView():''}${modes}${status}</div>`;
}
function chatLogView(edit){
 const raw=edit.logText??edit.samples,changed=raw!==edit.samples,analysis=edit.logAnalysis;
 return `<details class="sample-options chat-log" id="profile-samples" data-thread="${current.thread_id}" ${edit.samplesOpen?'open':''}><summary>Chat log for this person${edit.samples?' · added':''}</summary><p>Use a conversation with ${esc(current.name||current.address)} to guide your voice and relationship context. It stays separate from the SMS history shown in this chat.</p><div class="chat-log-actions"><button class="secondary" data-action="import-chat-log" ${!native||edit.logBusy?'disabled':''}>Import .txt file</button><button class="text-button" data-action="clear-chat-log" ${edit.logBusy||!raw&&!edit.samples?'disabled':''}>Remove log</button></div><label for="chat-log-owner">Your name in the log</label><input id="chat-log-owner" maxlength="120" value="${esc(edit.logOwner||'')}" ${edit.logBusy?'disabled':''} placeholder="For example: Alex"><small>Needed for a named WhatsApp export. Leave blank for Me: / Them: labels.</small><label for="relationship-samples">Paste a chat log</label><textarea id="relationship-samples" maxlength="262144" ${edit.logBusy?'disabled':''} placeholder="Them: how was your day?&#10;Me: pretty good, how about you?">${esc(raw)}</textarea><small id="samples-count">${raw.length.toLocaleString()} characters${edit.logFile?' · '+esc(edit.logFile):''}</small><div class="chat-log-actions"><button class="secondary" data-action="analyze-chat-log" ${edit.logBusy||!raw.trim()?'disabled':''}>${edit.logBusy?'Checking…':'Check chat log'}</button></div><p class="chat-log-error" id="chat-log-error" role="alert">${esc(edit.logError||'')}</p>${analysis?`<div class="chat-log-preview" id="chat-log-preview"><strong>${analysis.messageCount} messages ready · ${analysis.ownerCount} yours · ${analysis.incomingCount} theirs</strong><p>${analysis.truncated?'Only the latest complete messages that fit were kept. ':''}Review the speaker labels below, then save the profile.</p><pre>${esc(edit.samples)}</pre></div>`:''}<p id="chat-log-status">${changed?'Check this log before saving.':analysis?'Ready to save with this profile.':edit.samples?'Saved examples stay in use until you save a replacement.':'Import a plain-text file or paste a conversation, then check its speaker labels.'}</p><small>Up to 50 complete messages and 8,000 characters are used. Group logs are not supported. The log guides future replies; it does not train a permanent model. Saving is local; enabled AI requests share the selected log with your service and OpenAI.</small></details>`;
}
function refreshProfileSave(edit){const save=root.querySelector('[data-action="save-relationship"]');if(save)save.disabled=!profileReady()||busy||!!edit.logBusy||!profileDirty(edit)||(edit.logText??edit.samples)!==edit.samples;}
async function importChatLog(){
 if(!native||!current||!conversation)return;const thread=current.thread_id,epoch=threadEpoch,edit=relationshipEdit();if(edit.logBusy)return;
 edit.logBusy=true;edit.logError='';render();
 try{const result=await api('importChatLog',{thread});if(current?.thread_id!==thread||threadEpoch!==epoch)return;if(result?.cancelled||!result)return;
  if(typeof result.body!=='string'||result.body.length>262144)throw new Error('Choose a plain-text log smaller than 256 KB.');
  edit.logText=result.body;edit.logFile=result.fileName||'';edit.logAnalysis=null;edit.samplesOpen=true;
 }catch(error){if(current?.thread_id===thread&&threadEpoch===epoch)edit.logError=error.message;}
 finally{edit.logBusy=false;if(current?.thread_id===thread&&threadEpoch===epoch)render();}
}
async function analyzeChatLog(clear=false){
 if(!current||!conversation)return;const thread=current.thread_id,epoch=threadEpoch,edit=relationshipEdit();if(edit.logBusy)return;
 const raw=clear?'':edit.logText??edit.samples,ownerLabel=edit.logOwner||'';edit.logBusy=true;edit.logError='';render();
 try{const result=await api('analyzeChatLog',{thread,body:raw,ownerLabel});if(current?.thread_id!==thread||threadEpoch!==epoch)return;
  if(!result||typeof result.samples!=='string'||result.samples.length>8000||typeof result.replyEligibility?.eligible!=='boolean')throw new Error('The chat log could not be checked. Try again.');
  edit.samples=result.samples;edit.logText=result.samples;edit.logAnalysis=clear?null:result;edit.checkedSamples=result.samples;edit.checkedEligibility=result.replyEligibility;edit.checkedBase=conversation.base;if(edit.samples===edit.saved.samples){const record=replyProfileRecord(thread);if(record.data)record.data.replyEligibility=result.replyEligibility;if(conversation)conversation.replyEligibility=result.replyEligibility;}
  if(clear)edit.logFile='';if(!result.replyEligibility.eligible){edit.cloudEnabled=false;edit.autoDraft=false;edit.autoSend=false;}
  edit.samplesOpen=true;
 }catch(error){if(current?.thread_id===thread&&threadEpoch===epoch)edit.logError=error.message;}
 finally{edit.logBusy=false;if(current?.thread_id===thread&&threadEpoch===epoch)render();}
}

function relationshipView(){const edit=relationshipEdit();if(!edit.open)return '';return `<section class="relationship setup-popover" id="relationship-panel" role="dialog" aria-labelledby="reply-setup-title" data-thread="${current.thread_id}"><div class="popover-heading"><div><h3 id="reply-setup-title">Reply setup</h3><p>${esc(current.name||current.address)}</p></div><button class="icon-button" data-action="close-setup" aria-label="Close reply setup">${icon('close')}</button></div><div class="relationship-body">${profileLoadView()}

${replyModesView(edit)}

${automaticDelayView(edit)}
${approvedLearningView()}
<p class="profile-help">Autopilot also needs its main switch enabled in Settings.</p>

<div class="person-location setting"><div><label for="person-share-location">Allow location replies (including at home)</label><p>Only for this person’s location questions, using a recent general place. Turn on location in Settings too.</p><button class="text-button" data-action="location-settings-view">Location settings ${icon('arrow')}</button></div><input type="checkbox" id="person-share-location" ${edit.shareLocation?'checked':''}></div>
<label for="relationship-context">Relationship Dynamic</label><textarea id="relationship-context" aria-describedby="relationship-count" maxlength="1500" ${busy?'disabled':''} placeholder="Explain who they are, how long you've known them, the relationship dynamic, and how to respond; i.e. strictly business, casual, flirty and friendly">${esc(edit.text)}</textarea><small id="relationship-count">${edit.text.length}/1500</small>
<label for="relationship-important">Important Details</label><p id="important-help">Things to never say, sensitive topics, or boundaries to never cross.</p><textarea id="relationship-important" aria-describedby="important-help important-count" maxlength="2000" ${busy?'disabled':''} placeholder="For example: Don’t joke about their family. Never share my address. Don’t make promises on my behalf.">${esc(edit.importantDetails)}</textarea><small id="important-count">${edit.importantDetails.length}/2000</small>
${chatLogView(edit)}
<p>Enabled AI drafts share selected messages, these choices, and optional notes and samples with your hosted service and OpenAI.</p>${!state.cloud?.configured?'<p>Connect OpenAI in Settings before generating cloud replies.</p>':''}<div class="relationship-actions"><button class="primary" data-action="save-relationship" ${!profileReady()||busy||edit.logBusy||!profileDirty(edit)||(edit.logText??edit.samples)!==edit.samples?'disabled':''}>Save profile</button><button class="text-button" data-action="clear-relationship" ${busy||(!edit.text&&!edit.saved.text)?'disabled':''}>Clear dynamics</button></div><p id="relationship-status" role="status">${relationshipStatus(edit)}</p><p class="relationship-private">${native?'Saving applies to future drafts. It does not send an existing draft.':'Browser preview only. No real text messages are sent.'}</p></div></section>`;}
async function saveRelationship(clear=false){
 if(!current||!conversation||busy)return;
 const thread=current.thread_id,edit=relationshipEdit();if(!clear&&!profileReady()){loadReplyProfile();return;}if(edit.logBusy)return;if(clear){edit.text='';render();toast('Dynamics cleared. Save profile to keep this change.');return;}
 if((edit.logText??edit.samples)!==edit.samples){toast('Check the chat log before saving this profile.');return;}
 if(edit.importantDetails.length>2000){toast('Keep Important Details within 2,000 characters.');return;}
 if(edit.autoSend&&!profileEligibility(edit).eligible){toast('Train Autopilot for this chat before turning it on.');return;}
 if(!autopilotTimers.some(([n])=>n===edit.autoDelay)){toast('Choose Instant, 1 minute, or 5 minutes.');return;}
 busy=true;busyThread=current?.thread_id??null;render();
 try{const saved=await api('saveProfile',{thread,expectedAddress:profileContext().address||current.address||'',...(profileContext().profileRevision!=null?{expectedRevision:profileContext().profileRevision}:{}),body:edit.text,samples:edit.samples,cloudEnabled:edit.cloudEnabled,autoDraft:edit.autoDraft,autoSend:edit.autoSend,autoDelay:edit.autoDelay,autoDelayMode:'fixed',autoDelayMin:300,autoDelayMax:1800,engagement:'always_reply',shareLocation:edit.shareLocation,importantDetails:edit.importantDetails,planHandling:'delay_answer'});Object.assign(edit,profileValue(saved));edit.saved=profileValue(saved);if(current?.thread_id===thread&&conversation){acceptReplyProfile(thread,{...profileContext(thread),relationship:saved,profileRevision:saved.profileRevision??saved.revision??profileContext(thread).profileRevision,replyEligibility:saved.replyEligibility||profileContext(thread).replyEligibility},threadEpoch,true);conversation.relationship=saved;if(saved.replyEligibility)conversation.replyEligibility=saved.replyEligibility;edit.checkedEligibility=null;edit.logAnalysis=null;edit.logText=edit.samples;tone='Natural';}toast(edit.autoSend?(isInstantProfile(edit)?'Saved. Future AI replies to this person will send without review.':'Saved. Future automatic replies will start a send timer.'):'Profile saved. Redraft to use your changes.');}
 catch(e){toast(e.message);loadReplyProfile(thread);}finally{busy=false;render();refresh();}
}
root.addEventListener('toggle',e=>{if(e.target.id==='profile-samples'){const edit=relationshipEdits.get(Number(e.target.dataset.thread));if(edit)edit.samplesOpen=e.target.open;}},true);
function timerView(){return timerOpen?`<section class="timer-popover" id="timer-menu" role="dialog" aria-labelledby="timer-title"><div class="popover-heading"><div><h3 id="timer-title">Send message</h3><p>Choose when to send.</p></div><button class="icon-button" data-action="close-timer" aria-label="Close timer options">${icon('close')}</button></div><div class="timers compact-timers">${autopilotTimers.map(([n,l])=>`<button class="timer" data-action="${n===0?'send-now':'delay'}" ${n===0?'':`data-delay="${n}"`}>${l}</button>`).join('')}</div></section>`:'';}
function replyHold(record=conversation){return record?.replyHold?.reason==='plans_need_input'?record.replyHold:null;}
function syncReplyHoldUI(){const edit=relationshipEdits.get(current?.thread_id);if(edit){const status=document.getElementById('relationship-status');if(status)status.textContent=relationshipStatus(edit);const badge=document.getElementById('relationship-badge');if(badge)badge.textContent=profileBadge(edit);refreshProfileSave(edit);}}
function girlfriendPaused(record=conversation){return record?.girlfriendPause?.paused===true;}
function automaticReplyBlocked(){return girlfriendPaused()||!!replyHold()||conversation?.replyWaiting===true||sleepMode()==='paused'||conversation?.replyDecision?.decision==='no_reply';}
function chatHeaderView(edit=current?relationshipEdit():null){return `<header class="chat-header"><button class="back" data-action="back" aria-label="Back to conversations">${icon('back')}</button>${avatar(current.name,0,current.photo||profileContext().photo||conversation?.photo,true)}<div class="chat-person"><div class="chat-name-line"><h2>${esc(current.name||current.address)}</h2></div><p>${esc(current.address)}</p></div><div class="chat-actions"><span class="sms-badge">SMS</span><button class="reply-setup-button ${edit?.open?'active':''}" id="reply-setup" data-action="toggle-profile" aria-haspopup="dialog" aria-expanded="${!!edit?.open}" aria-controls="relationship-panel">${icon('settings')}<span>Reply setup</span>${icon('chevron')}<span class="sr-only" id="relationship-badge">${edit?profileBadge(edit):'Available when updated'}</span></button></div></header>`;}
function chatLoadView(){return `<div class="chat-card chat-load-card">${chatHeaderView()}<section class="chat-load-state" aria-busy="${chatLoadPending}" aria-label="Conversation loading status">${chatLoadError?`<div class="chat-load-message" role="status">${icon('chat')}<h3>Couldn’t load this conversation</h3><p>Try again, or return to your conversations.</p><button class="primary" data-action="retry-conversation">${icon('refresh')} Retry</button><details class="chat-load-details"><summary>Error details</summary><p>${esc(chatLoadError)}</p></details></div>`:`<div class="chat-load-message" role="status"><span class="spinner" aria-hidden="true"></span><p>Loading conversation…</p></div>`}</section></div>`;}
function attachmentState(record=conversation){const value=record?.attachments||{};return {...value,items:Array.isArray(value.items)?value.items:[],sending:value.sending===true,blocked:value.blocked===true,enabled:value.enabled!==false,maxItems:Number.isInteger(value.maxItems)&&value.maxItems>0?value.maxItems:6,maxBytes:Number(value.maxBytes)>0?Number(value.maxBytes):0};}
function hasAttachments(record=conversation){return attachmentState(record).items.length>0;}
function fileSize(bytes){const value=Math.max(0,Number(bytes)||0);return value>=1048576?`${(value/1048576).toFixed(value%1048576?1:0)} MB`:value>=1024?`${Math.ceil(value/1024)} KB`:`${value} B`;}
function attachmentPreview(item){return typeof item.previewUrl==='string'&&/^\/attachment\/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(item.previewUrl)&&String(item.mime||'').startsWith('image/')?item.previewUrl:'';}
function attachmentTrayView(){
 const files=attachmentState(),request=attachmentRequests.get(current?.thread_id),feedback=attachmentFeedback.get(current?.thread_id)||'',last=files.lastSend;
 const status=files.sending?'Sending attachments. Keep this conversation open to check the result.':files.blocked?'Sending status is unknown. Check the conversation before sending anything again. Remove these attachments to discard this attempt.':feedback||(((!files.items.length&&!draft.trim())||last?.status==='failed')?last?.note||'':'')||((files.items.length||files.enabled===false)?files.note||'':'');
 if(!files.items.length&&!request&&!status)return '';
 return `<section class="attachment-tray" id="attachment-tray" aria-label="Selected attachments" aria-busy="${!!request||files.sending}">${files.items.length?`<div class="attachment-items">${files.items.map(item=>{const preview=attachmentPreview(item);return `<article class="attachment-chip" data-attachment-id="${esc(item.id)}">${preview?`<img src="${esc(preview)}" class="attachment-preview" alt="Selected ${esc(item.name||'image')}" loading="lazy">`:`<span class="attachment-file-icon">${icon('media')}</span>`}<div><strong>${esc(item.name||'Attachment')}</strong><small>${fileSize(item.bytes)}${item.mime?' · '+esc(item.mime):''}</small></div><button class="icon-button" data-action="remove-attachment" data-attachment-id="${esc(item.id)}" aria-label="Remove ${esc(item.name||'attachment')}" ${!chatReady()||busy||files.sending||request?'disabled':''}>${icon('close')}</button></article>`;}).join('')}</div><p class="attachment-limit">${files.items.length}/${files.maxItems} attachments · ${files.maxBytes?`up to ${fileSize(files.maxBytes)} total`:'carrier size limits apply'}</p>`:''}<p class="attachment-status" role="status">${esc(request?.kind==='pick'?'Choose a file on your phone…':request?.kind==='remove'?'Removing attachment…':status)}</p></section>`;
}
function syncAttachmentTray(){const composer=document.querySelector('.composer');if(!composer||page!=='inbox'||!conversation)return;const old=composer.querySelector('#attachment-tray'),html=attachmentTrayView();if(old&&old.outerHTML===html)return;const template=document.createElement('template');template.innerHTML=html;const next=template.content.firstElementChild;if(old){if(next)old.replaceWith(next);else old.remove();}else if(next)composer.querySelector('.composer-input')?.before(next);const accept=composer.querySelector('#accept'),files=hasAttachments();if(accept){accept.title=files?'Send attachments now':'Send now · hold to schedule';accept.setAttribute('aria-description',files?'Send attachments and caption now. Attachment timers are unavailable.':'Tap to send now. Hold to schedule, or use the Schedule send button.');}}
function currentAttachmentRequest(request){return current?.thread_id===request.thread&&threadEpoch===request.epoch&&page==='inbox'&&!!conversation;}
async function changeAttachments(action,id){
 if(!current||!chatReady()||conversation.readOnly||busy||attachmentRequests.has(current.thread_id)||attachmentState().sending)return;
 const files=attachmentState();if(action==='pickAttachments'&&(files.blocked||files.enabled===false||files.items.length>=files.maxItems||pendingReply()))return;if(action==='removeAttachment'&&!files.items.some(item=>String(item.id)===String(id)))return;
 const request={thread:current.thread_id,epoch:threadEpoch,kind:action==='pickAttachments'?'pick':'remove'};attachmentRequests.set(request.thread,request);attachmentFeedback.delete(request.thread);timerOpen=false;syncLiveComposer();
 try{await flushDrafts();if(!currentAttachmentRequest(request))return;const result=await api(action,{thread:request.thread,...(id?{id}:{})});if(!currentAttachmentRequest(request))return;if(refreshing)await refreshing;if(!currentAttachmentRequest(request))return;if(!result?.cancelled){if(!result||!Array.isArray(result.items))throw new Error('Attachments could not be updated. Try again.');conversation.attachments=result;}syncLiveComposer();await refresh(true);}
 catch(error){if(currentAttachmentRequest(request)){attachmentFeedback.set(request.thread,error.message||'Attachments could not be updated.');toast(error.message);}}
 finally{if(attachmentRequests.get(request.thread)===request)attachmentRequests.delete(request.thread);if(currentAttachmentRequest(request))syncLiveComposer();}
}
function pickAttachments(){return changeAttachments('pickAttachments');}
function removeAttachment(id){return changeAttachments('removeAttachment',id);}
function newRequestId(){if(crypto.randomUUID)return crypto.randomUUID();const bytes=crypto.getRandomValues(new Uint8Array(16));bytes[6]=bytes[6]&15|64;bytes[8]=bytes[8]&63|128;const hex=[...bytes].map(n=>n.toString(16).padStart(2,'0')).join('');return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;}
function applyAttachmentOutcome(previous,next){const last=next.attachments?.lastSend,old=previous.attachments?.lastSend,attempt=mmsAttempts.get(current?.thread_id);if(last&&attempt?.id===last.id)attempt.status=last.status;if(!last||last.status!=='sent'||last.id===old?.id&&old.status==='sent')return;suggestionAttempts.add(`${current.thread_id}:sms:${last.base}`);if(typeof last.caption==='string'&&draft===last.caption&&Number(last.base)===Number(previous.base)){discardDraftEdits(current.thread_id,previous.base);draft='';dirty=false;syncDraftField();}attachmentFeedback.delete(current.thread_id);}
async function sendAttachmentsNow(){
 const files=attachmentState();if(!current||!chatReady())throw new Error('Your attachments are still being checked. Try sending again when this chat finishes updating.');
 if(conversation.readOnly)throw new Error('Attachments can only be sent to one confirmed contact.');
 if(!files.items.length||files.enabled===false||files.blocked)throw new Error(files.note||'Check the selected attachments before sending.');
 if(files.sending||sendingNow||attachmentRequests.has(current.thread_id))throw new Error('Your attachments are already being prepared or sent. Check their status before trying again.');
 if(pendingReply())throw new Error('Cancel the pending reply before sending these attachments. Your files and caption are kept.');
 const thread=current.thread_id,epoch=threadEpoch,edit=composerEditRevision,base=conversation.base,caption=draft,signature=JSON.stringify([thread,current.address,base,caption,state.sub,files.items.map(item=>item.id)]),previous=mmsAttempts.get(thread);
 const attempt=previous?.signature===signature&&!['failed','sent'].includes(previous.status)?previous:{requestId:newRequestId(),signature,status:'new'};mmsAttempts.set(thread,attempt);
 const payload={thread,address:current.address,caption,sub:state.sub,base,requestId:attempt.requestId};takeManualControl(thread);busy=true;busyThread=current?.thread_id??null;sendingNow=true;timerOpen=false;attachmentFeedback.delete(thread);syncLiveComposer();
 try{const result=await api('sendMms',payload);if(!result||!['sending','sent','failed','unknown'].includes(result.status))throw new Error('The attachment sending status could not be confirmed.');attempt.status=result.status;attempt.id=result.id;if(current?.thread_id!==thread||threadEpoch!==epoch)return;
  if(result.status==='failed'||result.status==='unknown'){attachmentFeedback.set(thread,result.note||'Attachments were not confirmed sent. Check the conversation before trying again.');toast(result.note||'Check the attachment sending status.');}
  else{conversation.attachments={...files,sending:result.status==='sending',lastSend:{...result,caption,base}};if(result.status==='sent'){suggestionAttempts.add(`${thread}:sms:${base}`);discardDraftEdits(thread,base);if(edit===composerEditRevision&&draft===caption){draft='';dirty=false;conversation.draft=null;}conversation.attachments.items=[];syncDraftField();}toast(result.note||(result.status==='sent'?'MMS accepted by your carrier.':'Sending attachments…'));}
 }catch(error){if(current?.thread_id===thread&&threadEpoch===epoch){attachmentFeedback.set(thread,error.message||'Attachments could not be sent.');toast(error.message);}}
 finally{sendingNow=false;busy=false;busyThread=null;syncLiveComposer();void refresh();}
}
function mmsReceiptView(message){const send=message.sendStatus;if(!send)return '<span>MMS</span>';const status=send.status,label=status==='sent'?'Sent':status==='failed'?'Send failed':status==='unknown'?'Send status unknown':['sending','preparing'].includes(status)?'Sending…':'MMS';return `<span class="mms-receipt" data-mms-status="${esc(status||'')}" title="${esc(send.note||'')}">${esc(label)}</span>`;}
function mmsDownloadView(message){const info=message.download;if(message.m_type!==130&&!info?.retryAllowed&&!['downloading','failed','interrupted'].includes(info?.status))return '';const id=Number(message._id),working=downloadRequests.get(id)?.loading===true||info?.status==='downloading',error=downloadRequests.get(id)?.error;return `<div class="mms-download" data-download-id="${id}"><p role="status">${esc(error||info?.note||(working?'Downloading attachment…':'This attachment has not downloaded yet. MMS may need mobile data.'))}</p>${info?.retryAllowed===true?`<button class="secondary" data-action="retry-mms" data-media-id="${id}" ${working?'disabled':''}>${working?'Downloading…':error||['failed','interrupted'].includes(info.status)?'Retry download':'Download attachment'}</button>`:''}</div>`;}
function updateDownloadUI(id,info){if(conversation){conversation.history=(conversation.history||[]).map(row=>messageKind(row)==='mms'&&Number(row._id)===id?{...row,download:info}:row);updateTimeline();}if(page==='media'){media=media.map(row=>Number(row._id)===id?{...row,download:info}:row);syncMediaView(true);}if(page==='media-thread'&&mediaBrowse){mediaBrowse.history=mediaBrowse.history.map(row=>row.kind==='mms'&&Number(row._id)===id?{...row,download:info}:row);syncMediaBrowse();}}
async function retryMms(mediaId){if(!Number.isSafeInteger(mediaId)||mediaId<=0||downloadRequests.get(mediaId)?.loading)return;const request={loading:true,error:'',epoch:threadEpoch,thread:current?.thread_id};downloadRequests.set(mediaId,request);const source=page==='media'?media:page==='media-thread'?mediaBrowse?.history:conversation?.history,row=source?.find(item=>Number(item._id)===mediaId&&(page==='media'||messageKind(item)==='mms'));if(!row?.download?.retryAllowed){downloadRequests.delete(mediaId);return;}updateDownloadUI(mediaId,row.download);try{const result=await api('retryMms',{mediaId});if(request.epoch!==threadEpoch||request.thread!==current?.thread_id)return;downloadRequests.delete(mediaId);updateDownloadUI(mediaId,result);if(page==='inbox')await refresh(true);else if(page==='media')await loadMedia(true);}catch(error){request.loading=false;request.error=error.message||'The attachment could not be downloaded.';if(request.epoch===threadEpoch&&request.thread===current?.thread_id)updateDownloadUI(mediaId,row.download);}finally{if(downloadRequests.get(mediaId)===request&&!request.error)downloadRequests.delete(mediaId);}}

function chatView(){
 if(!conversation)return chatLoadView();const ready=chatReady(),messages=conversation.history||[],textMms=latestTextMms(),last=textMms||latestSms(),hasMedia=Number(conversation.latestIncomingMediaId)>0&&!textMms,files=attachmentState(),attaching=attachmentRequests.has(current.thread_id),attached=hasAttachments(),cloud=conversation.relationship?.cloudEnabled,pendingJob=pendingReply(),automatic=isAutomaticJob(pendingJob),instant=automatic&&automaticJobDelay(pendingJob)===0,blocked=ready&&(conversation.readOnly===true||files.sending||pendingJob?.status==='scheduled'&&(!automatic||instant)),edit=relationshipEdit();let alternatives=[];try{alternatives=JSON.parse(conversation.draft?.alternatives||'[]');}catch{}if(!Array.isArray(alternatives))alternatives=[];
 return `<div class="chat-card">${chatHeaderView(edit)}${relationshipView()}<div class="timeline" aria-label="Messages" data-thread="${current.thread_id}">${historyView(messages)}</div><section class="composer" aria-label="Reply composer">${shareReviewView()}${hydrationNoticeView()}${ready&&pendingJob?`<div class="scheduled-banner"><div>${icon('clock')}<span>${pendingJob.status==='sending'||instant?'Sending reply…':`<strong data-due="${pendingJob.due}"></strong><small>${automatic?'Automatic reply · edit to stop timer':'Scheduled reply · cancel to edit'}</small>`}</span></div>${pendingJob.status==='scheduled'&&!instant?`<button class="text-button" data-action="cancel" data-id="${pendingJob._id}">Cancel</button>`:''}</div>`:''}${ready?timerView():''}<div class="composer-heading"><span class="draft-label" role="status">${icon(attached?'attach':'spark')} ${ready&&conversation.readOnly?'Read-only conversation':files.sending?'Sending attachments…':attached?'MMS · add a caption if you like':busy&&(busyThread===null||busyThread===current.thread_id)?(sendingNow?'Sending message…':'Preparing your reply…'):suggestionRequest?.thread===current.thread_id&&!draft?'Preparing suggestion…':draft?(dirty||conversation.draft?.engine==='Edited by you'?'Your reply':'Suggested reply'):'Your reply'}</span><button class="text-button" data-action="generate" ${shareForChat()||!ready||busy||conversation.readOnly||attached||attaching||hasMedia||!last||suggestionRequest?.thread===current.thread_id||!!pendingJob||attentionRequest?.thread===current.thread_id?'disabled':''}>${(busy&&!sendingNow)||suggestionRequest?.thread===current.thread_id?'<span class="spinner"></span> Drafting…':icon('refresh')+' '+(draft?'Redraft':'Draft reply')}</button></div>${attachmentTrayView()}<div class="composer-input"><button class="attachment-trigger" data-action="pick-attachments" aria-label="Add attachment" title="Add a photo, video, audio or file" ${shareForChat()||!ready||busy||blocked||attaching||files.blocked||files.enabled===false||files.items.length>=files.maxItems||!!pendingJob?'disabled':''}>${icon('attach')}</button><textarea id="draft" rows="1" ${sharedContent?.status==='importing'?'disabled':''} aria-label="Edit your reply" maxlength="1600" placeholder="${attached?'Add a caption…':last?.type===1?'Write a reply…':'Text message…'}">${esc(draft)}</textarea><button class="send-trigger" id="accept" data-action="send-now" aria-label="Send message" aria-description="${attached?'Send attachments and caption now. Attachment timers are unavailable.':'Tap to send now. Hold to schedule, or use the Schedule send button.'}" title="${attached?'Send attachments now':'Send now · hold to schedule'}" ${manualSendEnabled()?'':'disabled'}>${icon('send')}</button></div><div class="composer-foot"><span id="engine">${esc(ready&&conversation.readOnly?'Replies to group or unconfirmed recipients are unavailable.':attached?'MMS · mobile data may be needed':hasMedia&&!draft?'Use Understand & reply on the media above.':dirty?'Edited by you':conversation.draft?.engine||(cloud?'OpenAI drafts':'SMS'))}</span>${pendingJob?'':attached?'<span>Attachments send now</span>':`<button class="schedule-send" data-action="toggle-timer" aria-label="Schedule send" aria-haspopup="dialog" aria-expanded="${timerOpen}" aria-controls="timer-menu" ${shareForChat()||!ready||busy||conversation.readOnly||!draft.trim()||attentionRequest?.thread===current.thread_id?'disabled':''}>${esc(composerTimerLabel())}</button>`}</div>${ready&&!attached&&alternatives.length>1?`<div class="alternatives">${alternatives.map((a,i)=>`<button data-action="alternative" data-index="${i}" ${blocked?'disabled':''}>${esc(a)}</button>`).join('')}</div>`:''}</section></div>`;
}
function jobReceipt(job){return messageStatus({type:job.status==='sending'?4:job.status==='failed'?5:2,delivery:job.delivery_status||'none'});}
function jobStatusLabel(job){return ['delivered','failed'].includes(job.delivery_status)?jobReceipt(job).label:job.status;}
function jobStatusNote(job){
 if(job.delivery_status==='delivered')return `Delivery confirmed${Number(job.delivered_at)>0?' · '+date(job.delivered_at)+' at '+time(job.delivered_at):''}. This does not mean the message was read.`;
 if(job.delivery_status==='failed')return 'The carrier reported a delivery failure. Some parts may have arrived; check before sending again.';
 if(job.status==='sent'&&job.delivery_status==='pending')return 'Sent to your carrier. Waiting for a delivery report; some carriers do not provide one.';
 if(job.status==='sent'&&job.delivery_status==='unknown')return 'Sent to your carrier. Delivery could not be confirmed.';
 return job.note||'The timer was cancelled.';
}
function queueView(){const count=state.jobs.filter(j=>j.status==='scheduled').length;return `<div class="heading"><div><div class="eyebrow">A MOMENT BEFORE SEND</div><h1>Your reply queue.</h1><p class="subheading">${count?count+' scheduled '+(count===1?'reply is':'replies are')+' waiting for the right moment.':'Scheduled replies and their sending status.'}</p></div>${icon('clock')}</div>${!state.jobs.length?`<div class="panel empty">${icon('clock')}<h3>Nothing on the clock.</h3><p>Choose a reply’s timer, or enable automatic sending in Reply setup.</p></div>`:state.jobs.map(j=>`<article class="queue-card"><div class="queue-top">${avatar(state.inbox.find(x=>x.thread_id===j.thread)?.name||j.address)}<div><h3>${esc(state.inbox.find(x=>x.thread_id===j.thread)?.name||j.address)}</h3><small class="queue-note">${esc(j.address)}</small></div><span class="pill status-label" data-receipt="${['delivered','failed'].includes(j.delivery_status)?jobReceipt(j).kind:esc(j.status)}">${j.delivery_status==='delivered'?icon('checks'):j.status==='scheduled'?icon('clock'):icon(j.status==='sent'?'check':'chat')}${esc(jobStatusLabel(j))}</span></div><p>${esc(j.body)}</p><div class="queue-bottom"><div>${j.status==='scheduled'?`<div class="countdown" data-due="${j.due}"></div><div class="queue-note">${date(j.due)} at ${time(j.due)}</div>`:`<div class="queue-note">${esc(jobStatusNote(j))}</div>`}</div>${j.status==='scheduled'?`<button class="text-button danger" data-action="cancel" data-id="${j._id}">Cancel send</button>`:['paused','failed','unknown'].includes(j.status)&&j.delivery_status!=='delivered'?`<button class="text-button" data-action="review" data-id="${j._id}">Review ${icon('arrow')}</button>`:''}</div></article>`).join('')}<div class="privacy-note">${icon('shield')} A new incoming message pauses the reply for review.</div>`;}
function isInstantProfile(profile){return profile?.autoDelayMode!=='range'&&profile?.autoDelay===0;}
function validRange(min,max){return validTimer(min)&&validTimer(max)&&min<=max;}
function rangeText(min,max){return min===max?timerText(min):`${min/60}–${max/60} min`;}
function delayDescription(policy,prefix='delay'){return policy?.[prefix+'Mode']==='range'?`${rangeText(policy[prefix+'Min'],policy[prefix+'Max'])} (random)`:timerText(policy?.[prefix]);}
function rangeError(min,max){return !validTimer(min)||!validTimer(max)?'Use whole minutes from 1 to 10,080.':min>max?'From must be no later than To.':'';}
function syncRangeError(prefix,min,max){const field=document.getElementById(prefix+'-range-error');if(field)field.textContent=rangeError(min,max);}
function delayPayload(policy,prefix='delay'){const mode=policy[prefix+'Mode']==='range'?'range':'fixed',min=policy[prefix+'Min']??300,max=policy[prefix+'Max']??1800,keepRange=mode==='range'||validRange(min,max);return {[prefix]:mode==='range'&&!validTimer(policy[prefix])?300:policy[prefix],[prefix+'Mode']:mode,[prefix+'Min']:keepRange?min:300,[prefix+'Max']:keepRange?max:1800};}

function automaticDelayView(edit){return edit.autoSend?`<div class="autopilot-timer"><label for="autopilot-timer">Timer</label><select id="autopilot-timer">${autopilotTimers.map(([n,label])=>`<option value="${n}" ${edit.autoDelay===n?'selected':''}>${label}</option>`).join('')}</select><small>${edit.autoDelay===0?'Sends when the reply is ready.':`Starts a ${timerText(edit.autoDelay)} timer when the reply is ready.`}</small></div>`:'';}

function hasTimerPreset(seconds){return timerOptions.some(([n])=>n===seconds);}

function validFixedTimer(seconds){return seconds===1||validTimer(seconds);}
function validTimer(seconds){return Number.isInteger(seconds)&&seconds>=60&&seconds<=604800&&seconds%60===0;}

function readPreferences(){const previous=preferences||state;return {...previous,autoDraft:document.getElementById('auto-draft').checked,matchMyStyle:document.getElementById('match-style').checked,inAppSuggestions:document.getElementById('in-app-suggestions').checked,linkPreviews:document.getElementById('link-previews').checked,lockScreenPreviews:document.getElementById('lock-screen-previews').checked,tone:'Natural',sub:Number(document.getElementById('sim').value)};}
function sleepMode(){const session=state.sleep;if(session?.mode==='active')return Number(session.until)>Date.now()?'active':'paused';return session?.mode==='paused'?'paused':'off';}
function automaticJobDelay(job){if(Number.isInteger(job?.auto_delay)&&job.auto_delay>=0)return job.auto_delay;return conversation.relationship?.autoDelay||0;}
function composerTimerLabel(){const profile=conversation?.relationship;return profile?.cloudEnabled&&profile.autoDraft&&profile.autoSend?'Autopilot '+(state.autoDraft&&sleepMode()!=='paused'?'on':'paused'):'Hold to schedule';}

function historyCacheView(){
 const info=state.historyCache||{},status=info.status||'waiting',updating=historyCacheRefreshing||status==='syncing';
 const chats=Math.max(0,Number(info.conversations)||0),messages=Math.max(0,Number(info.messages)||0);
 const note=historyCacheError||(status==='error'?info.error||'Saved history couldn’t finish updating. Your last saved copy is kept.':updating?'Updating your saved history from this phone…':status==='ready'?'Up to date'+(info.savedAt?' · '+date(info.savedAt)+' at '+time(info.savedAt):''):'Open Reply Pilot with message access to save your history.');
 return `<h3>Saved chat history</h3><p class="history-cache-count">${chats.toLocaleString()} ${chats===1?'conversation':'conversations'} · ${messages.toLocaleString()} ${messages===1?'message':'messages'} saved</p><p class="history-cache-status" role="status">${esc(note)}</p><button class="secondary" data-action="refresh-history-cache" ${!native||updating||previewDenied||state.permissions===false||state.defaultSms===false?'disabled':''}>${updating?'Updating history…':'Refresh saved history'}</button><p>Includes SMS and MMS available on this phone. History kept privately inside another messaging app may not be available here.</p>`;
}
function syncHistoryCacheUI(){const panel=document.getElementById('history-cache-panel');if(panel)panel.innerHTML=historyCacheView();}
async function refreshSavedHistory(){
 if(historyCacheRefreshing||previewDenied||state.permissions===false||state.defaultSms===false)return;
 const access=privateGeneration;historyCacheRefreshing=true;historyCacheError='';syncHistoryCacheUI();
 try{const result=await api('refreshHistoryCache');if(access!==privateGeneration||previewDenied)return;state.historyCache=result;}
 catch{if(access===privateGeneration)historyCacheError='History refresh couldn’t start. Try again.';}
 finally{historyCacheRefreshing=false;syncHistoryCacheUI();}
}
function settingsView(){const prefs=preferences||state;return `<div class="heading"><div><div class="eyebrow">REPLY PILOT · VERSION 0.12.0</div><h1>Settings</h1><p class="subheading">Make it feel like you.</p></div></div><section class="panel theme-panel"><h3>Color theme</h3><p>Softer colors, with a feel of their own.</p><div class="theme-grid" role="group" aria-label="Color theme">${themes.map(([id,name])=>`<button class="theme-choice ${state.theme===id?'active':''}" data-action="theme" data-theme-choice="${id}" data-theme="${id}" aria-pressed="${state.theme===id}"><span class="theme-preview" data-theme="${id}"><i></i><b></b></span><span>${name}</span>${icon('check').replace('<svg ',`<svg ${state.theme===id?'':'hidden '}data-theme-check `)}</button>`).join('')}</div></section><div class="two-col"><section>${locationView()}<div class="panel connection-panel"><span class="pill">${state.cloud?.configured?'Connection saved':'Not connected'}</span><h3>Connect OpenAI</h3><p>Your API key belongs in your hosted service’s private settings. Paste the phone pairing code here after the service is ready. This code gives the phone access to drafting only.</p>${state.cloud?.configured?`<p class="service-url">${esc(state.cloud.url)}</p>`:''}<label for="pairing-code">Phone pairing code</label><textarea id="pairing-code" maxlength="4096" autocomplete="off" spellcheck="false" ${busy?'disabled':''} placeholder="Paste your phone pairing code, not your OpenAI API key">${esc(pairingText)}</textarea><div class="connection-actions"><button class="secondary" data-action="connect-cloud" ${busy||!native||!pairingText.trim()?'disabled':''}>Save connection</button><button class="text-button" data-action="check-cloud" ${busy||!native||!state.cloud?.configured?'disabled':''}>Check connection</button>${state.cloud?.configured?`<button class="text-button danger" data-action="disconnect-cloud" ${busy?'disabled':''}>Disconnect phone</button>`:''}</div><p id="cloud-status" role="status">${esc(cloudStatus)}</p><p>Disconnecting stops future drafts from this phone. Revoke its pairing token in your hosted service to invalidate copied codes. Automatic timers pause when disconnected. Manually scheduled replies remain in Queue until you cancel them.</p><button class="text-button" data-action="nav" data-page="test">Try a test reply ${icon('arrow')}</button></div><div class="panel"><h3>Reply preferences</h3><div class="setting"><div><label for="link-previews">Link previews</label><p>Show images and details for links. Previews connect directly to the linked websites.</p></div><input type="checkbox" id="link-previews" ${prefs.linkPreviews!==false?'checked':''}></div><div class="setting"><div><label for="in-app-suggestions">Suggest replies while chatting</label><p>Prepare a reviewable suggestion for new texts and incoming media while their chat is open. Uses only people you have enabled for AI. Does not send the suggestion.</p></div><input type="checkbox" id="in-app-suggestions" ${prefs.inAppSuggestions!==false?'checked':''}></div><div class="setting"><div><div class="label">Match my recent texts</div><p>Use the latest 50 incoming and sent SMS in this chat as context for drafts. Recent history is prepared locally when you open the app or chat and refreshed as messages arrive. Enabled AI drafts share that context with your service and OpenAI; this does not train a model.</p></div><input type="checkbox" id="match-style" aria-label="Match my recent texts" ${prefs.matchMyStyle?'checked':''}></div><div class="setting"><div><div class="label">Autopilot</div><p>Allow Autopilot for the people you choose in Reply setup. Turning this off pauses all automatic replies.</p></div><input type="checkbox" id="auto-draft" aria-label="Autopilot" ${prefs.autoDraft?'checked':''}></div><div class="setting"><div><label for="lock-screen-previews">Show message previews on lock screen</label><p>Show the incoming message and suggested reply in expanded notifications. Android controls the space available, notification sound and lock-screen visibility.</p></div><input type="checkbox" id="lock-screen-previews" aria-label="Show message previews on lock screen" ${prefs.lockScreenPreviews!==false?'checked':''}></div><button class="text-button" data-action="save-settings">Save preferences ${icon('check')}</button></div><div class="panel"><h3>Phone setup</h3>${setupView()}<div class="setting" id="contacts-setting">${contactSettingsView()}</div><div class="setting"><div><div class="label">Notifications</div><p>Tiny Blast is the default for messages and reply alerts. Your Android sound and mute choices still apply.</p></div><button class="secondary" data-action="permissions">${state.notifications?'Allowed':'Enable'}</button></div><button class="text-button" data-action="notificationSettings">Notification sound &amp; lock screen ${icon('arrow')}</button><div class="setting"><label for="sim">Sending SIM</label><select id="sim"><option value="-1">Choose SIM</option>${state.sims.map(s=>`<option value="${s.id}" ${s.id===prefs.sub?'selected':''}>${esc(s.name)}</option>`).join('')}</select></div>${simHelpView()}</div></section><section><div class="panel" id="messaging-receipts"><h3>Messaging &amp; receipts</h3><p><strong>SMS delivery reports</strong> are requested automatically for new messages. Sent means your carrier accepted the message; Delivered appears only after confirmation. Some carriers do not provide reports, so a missing receipt does not mean delivery failed.</p><p><strong>Read receipts</strong> are not available for SMS. Delivered does not mean someone opened your message.</p><p><strong>RCS is unavailable in Reply Pilot.</strong> Android does not give this installation access to the carrier RCS service used by approved messaging apps. Texts use SMS; attachments use carrier MMS.</p></div><div class="panel" id="history-cache-panel">${historyCacheView()}</div><div class="panel"><h3>Your data</h3><p>Initial preparation sends all available SMS and MMS text history through your connected service to OpenAI. Autopilot stays off for each person until you enable it. Later AI requests use selected message context, learned guidance, images or sampled video frames for media understanding, relationship notes, samples, and approved reply examples. The service can read those inputs while processing them.</p><p>The app keeps profiles and drafts in private phone storage with backups disabled. The service does not write conversation content to logs or a database; it briefly keeps recent requests in memory to prevent duplicate charges.</p><p>OpenAI API data is not used for training by default. Requests disable response storage, but provider abuse-monitoring logs can retain content for up to 30 days, with legal and safety exceptions.</p><p>Internet access and separate OpenAI API billing are required. Drafts are not guaranteed to be instant. Your SMS carrier may also charge for texts.</p></div><div class="panel"><h3>Before making the switch</h3><p>Send individual SMS and carrier MMS with photos, videos or audio. Attachments send when you tap the arrow; timers apply to text messages only. RCS and group replies are not supported.</p><p>Turn off RCS in Google Messages before switching. Keep mobile data available for MMS, choose your sending SIM, and stay within the attachment limit shown in the composer. Carrier support and charges vary.</p><p>New messages, restarting, changing the clock, lost permissions, or a timer more than two minutes late pause sending for review. Carrier acceptance does not confirm delivery.</p></div></section></div>`;}
function selectedMessageText(){const editor=document.activeElement;return !!window.getSelection()?.toString()||!!(editor?.matches('textarea,input')&&editor.selectionEnd>editor.selectionStart);}


function locationState(){return {enabled:false,permission:false,backgroundPermission:false,refreshing:false,label:'',updatedAt:0,fresh:false,homeSet:false,homeAddress:'',status:'Location replies are off.',...state.location};}
function homeForm(){if(!homeEdit)homeEdit={address:locationState().homeAddress||'',dirty:false};return homeEdit;}
function locationView(){const value=locationState();return `<section class="panel location-panel" id="location-panel"><h3>Location replies</h3><p>Let selected people get a general answer when they ask where you are.</p><div class="setting"><div><label for="location-enabled">Use location for replies</label><p>Off until you turn it on. Also allow each person in Reply setup.</p></div><input id="location-enabled" type="checkbox" ${value.enabled?'checked':''} ${locationBusy?'disabled':''}></div><div id="location-live">${locationLiveView()}</div><div class="location-actions"><button class="secondary" data-action="request-location" ${value.permission?'hidden':''} ${locationBusy||!value.enabled||!native?'disabled':''}>Allow location</button><button class="text-button" data-action="location-permissions" ${!value.enabled||!value.permission||value.backgroundPermission?'hidden':''}>Allow background access</button><button class="text-button" data-action="refresh-location" ${locationBusy||value.refreshing||!value.enabled||!value.permission||!native?'disabled':''}>Refresh location</button></div><p class="location-help">Checks roughly every 20 minutes. Android can delay updates. If the location is missing or stale, the reply waits for your input. Background access is managed in Android’s Location permission settings.</p><div class="home-settings"><h4>Home</h4><p>Save home locally so permitted replies can say “at home.” Your address is not included in the location context sent to AI.</p><label for="home-address">Home address</label><input id="home-address" maxlength="300" autocomplete="street-address" value="${esc(homeForm().address)}" placeholder="Street address, city and region"><div class="location-actions"><button class="secondary" data-action="save-home" ${locationBusy||!homeForm().address.trim()||!native?'disabled':''}>Save home</button><button class="text-button" data-action="home-here" ${locationBusy||!!homeCandidates||!value.enabled||!value.precise||!value.permission||!native?'disabled':''}>Use current place</button><button class="text-button danger" data-action="clear-home" ${!value.homeSet?'hidden':''} ${locationBusy||!native?'disabled':''}>Remove home</button></div><small>Use a fresh, precise location, then confirm the address or choose a nearby one.</small><p id="home-status">${value.homeSet?'Home is saved on this phone.':'No home saved.'}</p></div><p id="location-feedback" role="status">${esc(locationFeedback)}</p><small>Android’s geocoding service and OpenStreetMap receive coordinates to identify places. Typed addresses use Android’s geocoding service. Only a general place or “at home” is sent to OpenAI for location questions from people you explicitly allow.</small><p class="location-attribution">© OpenStreetMap contributors</p></section>`;}
function locationLiveView(){const value=locationState();return `<p class="location-status" role="status">${esc(value.status)}</p>${!value.enabled?'':value.label?`<div class="location-preview"><strong>${esc(value.label)}</strong><span>${value.fresh?'Recent location':'Last known location · out of date'}${value.updatedAt?' · '+date(value.updatedAt)+' '+time(value.updatedAt):''}</span></div>`:'<p class="location-preview">No recent place available.</p>'}`;}
function syncLocationUI(){if(!document.getElementById('location-panel'))return;const value=locationState(),edit=homeForm();if(!edit.dirty&&document.activeElement?.id!=='home-address'){edit.address=value.homeAddress||'';const field=document.getElementById('home-address');if(field)field.value=edit.address;}const live=document.getElementById('location-live'),markup=locationLiveView();if(live&&live.innerHTML!==markup)live.innerHTML=markup;const enabled=document.getElementById('location-enabled');if(enabled){enabled.checked=typeof locationEnabledPending==='boolean'?locationEnabledPending:value.enabled;enabled.disabled=locationBusy;}for(const [action,disabled,hidden] of [['request-location',locationBusy||!value.enabled||!native,value.permission],['location-permissions',locationBusy,!value.enabled||!value.permission||value.backgroundPermission],['refresh-location',locationBusy||value.refreshing||!value.enabled||!value.permission||!native,false],['save-home',locationBusy||!edit.address.trim()||!native,false],['home-here',locationBusy||!!homeCandidates||!value.enabled||!value.precise||!value.permission||!native,false],['clear-home',locationBusy||!native,!value.homeSet]]){const button=document.querySelector(`[data-action="${action}"]`);if(button){button.disabled=disabled;button.hidden=hidden;}}document.getElementById('location-feedback').textContent=locationFeedback;document.getElementById('home-status').textContent=value.homeSet?'Home is saved on this phone.':'No home saved.';}
async function changeLocation(action,payload={}){if(locationBusy)return;if(action==='setHome'&&typeof payload.address==='string'&&(!payload.address.trim()||payload.address.length>300)){locationFeedback='Enter a home address up to 300 characters.';syncLocationUI();return;}const previousAddress=homeForm().address;locationBusy=true;locationEnabledPending=action==='saveLocation'?payload.enabled:null;locationFeedback=action==='saveLocation'?'Saving location settings…':'';syncLocationUI();try{state.location=await api(action,payload);if(['setHome','clearHome'].includes(action))homeReceipt={ackRead:snapshotSequence,value:{homeSet:state.location.homeSet===true,homeAddress:state.location.homeAddress||''}};if(['setHome','clearHome'].includes(action)&&homeForm().address===previousAddress){homeEdit={address:state.location.homeAddress||'',dirty:false};const field=document.getElementById('home-address');if(field)field.value=homeEdit.address;}locationFeedback=action==='setHome'?'Home saved on this phone.':action==='clearHome'?'Home removed.':state.location.status||'Location settings updated.';}catch(error){locationFeedback=error.message||'Location settings could not be updated.';}finally{locationBusy=false;locationEnabledPending=null;syncLocationUI();}}

function homeCandidatesView(){
 const view=homeCandidates;if(!view)return '';const candidate=view.candidates[view.index],expired=!!view.expiresAt&&Date.now()>=view.expiresAt;
 return `<div class="home-candidate-layer" id="home-candidate-layer"><div class="chat-menu-backdrop" data-action="home-candidate-cancel" aria-hidden="true"></div><section class="home-candidate-dialog" id="home-candidate-dialog" role="dialog" aria-modal="true" aria-labelledby="home-candidate-title"><div class="home-candidate-heading"><h2 id="home-candidate-title">Is this your home?</h2><button class="icon-button" data-action="home-candidate-cancel" aria-label="Close address confirmation">${icon('close')}</button></div><p>Location is an estimate. Check the full address before saving.</p>${view.loading?'<p class="home-candidate-note" role="status">Finding nearby addresses…</p>':candidate?`<div class="home-candidate-place"><span id="home-candidate-count">Address ${view.index+1} of ${view.candidates.length}</span><p id="home-candidate-address">${esc(candidate.address)}</p>${Number.isFinite(candidate.distanceMeters)?`<small>About ${Math.round(candidate.distanceMeters)} m from your current position</small>`:''}</div><div class="home-candidate-paging"><button class="secondary" data-action="home-candidate-prev" ${view.confirming||view.index===0?'disabled':''}>Previous</button><button class="secondary" data-action="home-candidate-next" ${view.confirming||view.index>=view.candidates.length-1?'disabled':''}>Next address</button></div><button class="primary home-candidate-confirm" data-action="home-candidate-confirm" ${view.confirming||expired?'disabled':''}>${view.confirming?'Saving home…':'Yes, save this address'}</button>`:'<p class="home-candidate-note">No nearby street address is available. You can enter your address below.</p>'}${view.note?`<p class="home-candidate-note">${esc(view.note)}</p>`:''}${Number.isFinite(view.accuracyMeters)?`<p class="home-candidate-accuracy">Location accuracy: about ${Math.round(view.accuracyMeters)} m.</p>`:''}<p class="home-candidate-error" role="alert">${esc(view.error||(expired?'This location estimate expired. Find nearby addresses again.':''))}</p>${!view.loading&&!view.confirming&&(view.error||expired||!candidate)?'<button class="secondary" data-action="home-candidate-search">Find addresses again</button>':''}<button class="text-button home-candidate-manual" data-action="home-candidate-manual" ${view.confirming?'disabled':''}>Enter address manually</button></section></div>`;
}
function syncHomeCandidates(){const old=document.getElementById('home-candidate-layer'),active=document.activeElement?.closest?.('#home-candidate-dialog [data-action]')?.dataset.action;const html=homeCandidatesView();if(old){if(html)old.outerHTML=html;else old.remove();}else if(html)root.insertAdjacentHTML('beforeend',html);if(active)document.querySelector(`#home-candidate-dialog [data-action="${active}"]:not(:disabled)`)?.focus({preventScroll:true});}
function closeHomeCandidates(restore=true,manual=false){const view=homeCandidates;if(!view)return;clearTimeout(view.expiryTimer);homeCandidates=null;if(native)api('cancelHomeCandidates').catch(()=>{});syncHomeCandidates();syncLocationUI();if(restore){const target=manual?document.getElementById('home-address'):document.querySelector('[data-action=home-here]');target?.focus({preventScroll:!manual});}}
async function requestHomeCandidates(){
 if(locationBusy)return;closeHomeCandidates(false);const view={candidates:[],index:0,loading:true,confirming:false,error:'',address:homeForm().address};homeCandidates=view;syncHomeCandidates();document.querySelector('#home-candidate-dialog [data-action=home-candidate-cancel]')?.focus({preventScroll:true});syncLocationUI();
 try{const result=await api('homeCandidates');if(homeCandidates!==view||page!=='settings'||homeForm().address!==view.address)return;
  if(!result||typeof result.requestId!=='string'||!result.requestId||!Array.isArray(result.candidates))throw new Error('Nearby addresses are unavailable. Enter your home address manually.');
  view.requestId=result.requestId;view.candidates=result.candidates.filter(item=>item&&typeof item.id==='string'&&item.id&&typeof item.address==='string'&&item.address.trim()).slice(0,10).map(item=>({id:item.id,address:item.address,distanceMeters:typeof item.distanceMeters==='number'&&item.distanceMeters>=0?item.distanceMeters:undefined}));view.note=typeof result.note==='string'?result.note:'';view.accuracyMeters=typeof result.accuracyMeters==='number'?result.accuracyMeters:undefined;view.expiresAt=Number.isFinite(result.expiresAt)?result.expiresAt:0;
  if(view.expiresAt)view.expiryTimer=setTimeout(()=>{if(homeCandidates===view)syncHomeCandidates();},Math.max(0,Math.min(view.expiresAt-Date.now()+10,2147483647)));
 }catch(error){if(homeCandidates===view)view.error=error.message||'Nearby addresses are unavailable. You can enter your address manually.';}
 finally{if(homeCandidates===view){view.loading=false;syncHomeCandidates();document.querySelector('#home-candidate-dialog [data-action=home-candidate-confirm]:not(:disabled),#home-candidate-dialog [data-action=home-candidate-manual]')?.focus({preventScroll:true});syncLocationUI();}}
}
async function confirmHomeCandidate(){
 const view=homeCandidates,candidate=view?.candidates[view.index];if(!view||view.loading||view.confirming||!candidate)return;if(view.expiresAt&&Date.now()>=view.expiresAt){syncHomeCandidates();return;}
 const addressBefore=homeForm().address;view.confirming=true;view.error='';locationBusy=true;syncHomeCandidates();syncLocationUI();
 try{const result=await api('confirmHomeCandidate',{requestId:view.requestId,candidateId:candidate.id});if(!result||result.homeSet!==true||typeof result.homeAddress!=='string'||!result.homeAddress.trim())throw new Error('The home address could not be confirmed. Try again.');state.location=result;homeReceipt={ackRead:snapshotSequence,value:{homeSet:true,homeAddress:result.homeAddress}};if(homeForm().address===addressBefore){homeEdit={address:result.homeAddress,dirty:false};const field=document.getElementById('home-address');if(field)field.value=result.homeAddress;}locationFeedback=homeForm().dirty?'Home saved. Your newer address edit is not saved.':'Home saved on this phone.';if(homeCandidates===view)closeHomeCandidates(true,true);else toast(locationFeedback);
 }catch(error){if(homeCandidates===view)view.error=error.message||'The address could not be saved.';else{locationFeedback=error.message||'The address could not be saved.';toast(locationFeedback);}}
 finally{locationBusy=false;view.confirming=false;if(homeCandidates===view)syncHomeCandidates();syncLocationUI();}
}
document.addEventListener('keydown',event=>{if(!homeCandidates)return;if(event.key==='Escape'){event.preventDefault();event.stopImmediatePropagation();closeHomeCandidates();}else if(event.key==='Tab'){const buttons=[...document.querySelectorAll('#home-candidate-dialog button:not(:disabled)')],index=buttons.indexOf(document.activeElement),next=event.shiftKey?(index<=0?buttons.length-1:index-1):(index+1)%buttons.length;event.preventDefault();buttons[next]?.focus({preventScroll:true});}},true);

function decisionLabel(reason){return reason==='plans_need_input'?'Write your own answer to these plans':reason==='insufficient_history'?'More context needed':reason==='needs_review'?'Please write your own reply':'No reply needed';}
function testView(){
 const status=!native?'Requires the Android app':state.cloud?.configured?'Connection saved · try a draft to verify':'Connect OpenAI in Settings';
 return `<div class="heading"><div><div class="eyebrow">TRY IT BEFORE YOU SEND</div><h1>Find your voice.</h1><p class="subheading">Give your AI a test message and see what it writes.</p></div></div><div class="notice">${native?'This test has no recipient or send button. Use it without changing your default texting app.':'This browser is an interface preview. Real OpenAI replies are available in the Android app; this screen never fabricates a test reply.'}</div><div class="two-col test-layout"><section class="panel test-form"><div class="test-model"><div><h3>OpenAI</h3><p>${status}</p></div>${native?`<button class="secondary" data-action="test-check" ${busy||!state.cloud?.configured?'disabled':''}>Check connection</button>`:''}</div>${native&&!state.cloud?.configured?'<button class="secondary" data-action="nav" data-page="settings">Connect OpenAI</button>':''}<p>Start with your own message or try a scenario:</p><div class="test-scenarios">${['Friend','Work','Tricky conversation'].map((name,i)=>`<button class="chip" data-action="test-scenario" data-scenario="${i}" ${busy?'disabled':''}>${name}</button>`).join('')}</div><label for="test-message">Incoming test message</label><textarea id="test-message" maxlength="1600" ${busy?'disabled':''} placeholder="Hey, want to grab coffee tomorrow?">${esc(cloudTest.message)}</textarea><small>Up to 1,600 characters.</small><label for="test-relationship">Relationship dynamic (optional)</label><textarea id="test-relationship" maxlength="1500" ${busy?'disabled':''} placeholder="We’re close friends. Keep it casual and supportive.">${esc(cloudTest.relationship)}</textarea><label for="test-examples">Conversation samples (optional)</label><textarea id="test-examples" maxlength="8000" ${busy?'disabled':''} placeholder="Them: how’s your day?&#10;Me: pretty good, how about you?">${esc(cloudTest.examples)}</textarea><small>Label your lines “Me:” and the other person’s “Them:”. Up to 8,000 characters. This standalone test does not read live chats or location. Replies adapt to your examples and context.</small><p class="test-error" id="test-error" role="alert">${esc(cloudTest.error)}</p><button class="primary" data-action="test-run" ${!canTest()?'disabled':''}>${busy?'<span class="spinner"></span>':''}${!native?'Run on your Pixel':busy?'Preparing a reply…':'Generate test reply'}</button><button class="text-button" data-action="test-clear" ${busy?'disabled':''}>Clear test session</button><p class="test-privacy">Generating shares these test inputs with your hosted service and OpenAI. This screen keeps the last three replies temporarily. Nothing is added to a chat or send queue. Clearing removes this screen’s copies.</p></section><section class="test-results" aria-live="polite">${cloudTest.results.length?cloudTest.results.map((r,i)=>`<article class="panel"><span class="pill">${esc(r.engine)}</span><h3>${i===0?'Latest reply':'Earlier reply'}</h3><p class="test-reply">${r.decision==='no_reply'?decisionLabel(r.reason):esc(r.body)}</p><small>${(r.elapsedMs/1000).toFixed(1)} seconds for this request</small><details><summary>Input used for this reply</summary><p><strong>Message:</strong> ${esc(r.input.message)}</p><p><strong>Relationship:</strong> ${esc(r.input.relationship||'None supplied')}</p><p><strong>Style examples:</strong> ${esc(r.input.examples||'None supplied')}</p></details></article>`).join(''):`<div class="panel empty">${icon('spark')}<h3>Your test replies will appear here.</h3><p>Try different context and writing samples. The last three replies stay here for comparison.</p></div>`}</section></div>`;
}
function canTest(){return native&&!busy&&!!cloudTest.message.trim()&&state.cloud?.configured;}
async function runCloudTest(){
 if(!canTest())return;
 const input={message:cloudTest.message,relationship:cloudTest.relationship,examples:cloudTest.examples,tone:cloudTest.tone};
 busy=true;busyThread=current?.thread_id??null;cloudTest.error='';render();
 try{const result=await api('testOpenAI',input);if(!result||typeof result.body!=='string'||(result.decision==='no_reply'?result.body!=='':!result.body.trim()))throw new Error('No reply was returned. Try another message.');cloudTest.results=[{...result,input},...cloudTest.results].slice(0,3);}
 catch(e){cloudTest.error=e.message;}
 finally{busy=false;render();}
}
async function checkCloud(){const result=await api('checkCloud');if(!result.ready)throw new Error('Add your OpenAI API key in the hosted service’s private settings.');cloudStatus='Service reached. A test reply will verify your API key and billing.';return result;}
function pilotLoader(){return `<svg class="pilot-loader" viewBox="0 0 40 40" aria-hidden="true"><circle class="pilot-orbit" cx="20" cy="20" r="15"/><g class="pilot-flight"><path d="m11 11 21 9-21 9 4-9-4-9Z"/><path class="pilot-fold" d="m15 20 10 0"/></g><circle class="pilot-spark" cx="32" cy="8" r="2"/></svg>`;}
function mediaMime(value){const type=String(value||'').split(';',1)[0].trim().toLowerCase();return /^[a-z0-9!#$%&'+.^_`|~-]+\/[a-z0-9!#$%&'+.^_`|~-]+$/.test(type)?type:'';}
function mediaPartId(value){return /^\d+$/.test(String(value))&&Number.isSafeInteger(Number(value))&&Number(value)>0;}
function galleryAttachment(part){const type=mediaMime(part?.ct);return !!type&&mediaPartId(part?._id)&&!['text/plain','text/html','application/smil','application/smil+xml'].includes(type)&&!type.startsWith('multipart/');}
function galleryRows(rows){return rows.filter(row=>row&&(!Object.hasOwn(row,'m_type')||[128,132].includes(Number(row.m_type)))&&(!Object.hasOwn(row,'msg_box')||[1,2,4,5].includes(Number(row.msg_box)))&&Array.isArray(row.parts)&&row.parts.some(galleryAttachment));}
function galleryParts(row){return row.parts.filter(part=>mediaMime(part?.ct)==='text/plain'||galleryAttachment(part));}
function mediaPartView(part){
 const type=mediaMime(part?.ct),safeId=mediaPartId(part?._id);
 if(['application/smil','application/smil+xml'].includes(type)||type.startsWith('multipart/'))return '';
 if(type==='text/plain'||type==='text/html')return part.text?`<p>${linkifiedText(part.text)}</p>`:'';
 if(type.startsWith('image/')&&safeId)return `<div class="media-image-frame"><div class="media-photo-placeholder">${pilotLoader()}<span>Opening photo…</span></div><img class="media-image" src="/part/${part._id}" loading="lazy" decoding="async" alt="MMS photo"><p class="media-photo-error" role="status" hidden>Photo unavailable. The attachment may have been removed.</p></div>`;
 if(safeId&&(type.startsWith('audio/')||type.startsWith('video/'))){const kind=type.startsWith('audio/')?'audio':'video';return `<div class="media-playback"><${kind} class="media-player" controls preload="none" ${kind==='video'?'playsinline':''} src="/part/${part._id}" aria-label="MMS ${kind}">This ${kind} cannot be played on this phone.</${kind}><small>${kind==='video'?'Video':'Audio'} attachment · tap play</small><p class="media-playback-error" role="status" hidden>This ${kind} cannot be played. The format may be unsupported or the attachment is unavailable.</p></div>`;}
 return `<p>Attachment: ${esc(type)} (preview unavailable)</p>${safeId?`<button class="secondary" data-action="open-attachment" data-part-id="${part._id}">Open attachment</button>`:''}`;
}
function mediaRowKey(row){return `${row.kind}:${row._id}`;}
function mediaBrowseView(){const view=mediaBrowse;if(!view)return '';const loaded=!!view.thread;return `<div class="chat-card media-browse-card"><header class="chat-header"><button class="back" data-action="media-context-back" aria-label="Back to media">${icon('back')}</button><div class="chat-person"><h2>${esc(view.name||'Conversation')}</h2><p>${loaded?'Browsing message history':'Finding this message…'}</p></div>${loaded&&!view.readOnly?'<button class="secondary media-latest" data-action="media-context-latest">Latest conversation</button>':''}</header>${!loaded?`<div class="chat-load-state"><div class="chat-load-message" role="status">${view.error?`<h3>Couldn’t open this message</h3><p>${esc(view.error)}</p><button class="primary" data-action="media-context-retry">Try again</button>`:'<span class="spinner"></span><p>Loading conversation…</p>'}</div></div>`:`<p class="media-browse-note">${view.readOnly?'Read-only history. This conversation cannot be safely addressed as a single SMS.':'You’re viewing the conversation around this photo. Open the latest conversation to reply.'}</p><div class="media-history" aria-label="Conversation around selected media">${mediaBrowseRows(view)}</div>`}</div>`;}
function mediaBrowseRows(view){return `<div class="media-page-control">${view.hasOlder?`<button class="text-button" data-action="media-context-older" ${view.loading?'disabled':''}>Load older messages</button>`:'<span>Beginning of available history</span>'}</div>${view.history.map(row=>`<article class="media-history-row ${row.type===2?'out':''} ${mediaRowKey(row)===view.anchor?'media-anchor':''}" data-media-key="${esc(mediaRowKey(row))}">${mediaRowKey(row)===view.anchor?'<span class="media-anchor-label">Selected media</span>':''}<div class="bubble">${row.kind==='mms'?(Array.isArray(row.parts)?row.parts:[]).map(mediaPartView).join('')||`<p>${linkifiedText(row.body||'Multimedia message')}</p>`:linkifiedText(row.body)}${row.kind==='mms'?mmsDownloadView(row):''}${row.kind==='mms'&&row.truncated?'<p class="media-truncated">Some message content is shortened.</p>':''}</div>${linkPreviewView(row,view.thread)}<time>${date(row.date)} · ${time(row.date)}</time></article>`).join('')}<div class="media-page-control">${view.hasNewer?`<button class="text-button" data-action="media-context-newer" ${view.loading?'disabled':''}>Load newer messages</button>`:'<span>End of available history</span>'}${view.loading?'<span class="spinner" role="status" aria-label="Loading messages"></span>':''}${view.error?`<p role="alert">${esc(view.error)}</p><button class="text-button" data-action="media-context-retry">Retry messages</button>`:''}</div>`;}
function syncMediaBrowse(anchor=false){if(page!=='media-thread'||!mediaBrowse)return;const timeline=document.querySelector('.media-history');if(!timeline){render();if(anchor)requestAnimationFrame(()=>document.querySelector('.media-anchor')?.scrollIntoView({block:'center'}));return;}const top=timeline.getBoundingClientRect().top,visible=[...timeline.querySelectorAll('[data-media-key]')].find(row=>row.getBoundingClientRect().bottom>top+1),position={key:visible?.dataset.mediaKey,offset:visible?visible.getBoundingClientRect().top-top:0,scroll:timeline.scrollTop};timeline.innerHTML=mediaBrowseRows(mediaBrowse);const restored=[...timeline.querySelectorAll('[data-media-key]')].find(row=>row.dataset.mediaKey===position.key);timeline.scrollTop=restored?timeline.scrollTop+restored.getBoundingClientRect().top-timeline.getBoundingClientRect().top-position.offset:position.scroll;scheduleLinkPreviews();}
function openMediaConversation(mediaId){if(!Number.isSafeInteger(mediaId)||mediaId<=0)return;mediaBrowse={mediaId,history:[],loading:false,error:'',request:0};page='media-thread';render();loadMediaContext();}
async function loadMediaContext(direction=null){const view=mediaBrowse;if(!view||view.loading||page!=='media-thread')return;const request=++view.request,cursor=direction==='older'?view.before:direction==='newer'?view.after:null;if(direction&&!cursor)return;view.loading=true;view.error='';syncMediaBrowse();try{const result=await api('mediaConversation',{mediaId:view.mediaId,...(direction?{direction,cursor}:{})});if(mediaBrowse!==view||request!==view.request||page!=='media-thread')return;if(!result||!Array.isArray(result.history)||!Number.isSafeInteger(result.thread)||result.thread<=0||view.thread&&result.thread!==view.thread)throw new Error('This media conversation could not be confirmed.');if(!direction&&!result.history.some(row=>mediaRowKey(row)===result.anchor))throw new Error('This message is no longer available.');const rows=new Map(view.history.map(row=>[mediaRowKey(row),row]));for(const row of result.history)if(['sms','mms'].includes(row.kind)&&Number.isSafeInteger(row._id)&&row._id>0)rows.set(mediaRowKey(row),row);view.history=[...rows.values()].sort((a,b)=>a.date-b.date||Number(a.kind==='mms')-Number(b.kind==='mms')||a._id-b._id);if(!direction){Object.assign(view,{thread:result.thread,address:result.address,name:result.name,readOnly:result.readOnly!==false,anchor:result.anchor});}if(!direction||direction==='older'){view.before=result.before;view.hasOlder=!!result.hasOlder;}if(!direction||direction==='newer'){view.after=result.after;view.hasNewer=!!result.hasNewer;}}catch(error){if(mediaBrowse===view&&request===view.request){view.error=error.message;view.failedDirection=direction;}}finally{if(mediaBrowse===view&&request===view.request){view.loading=false;syncMediaBrowse(!direction&&!view.error);}}}
async function openMediaLatest(){const view=mediaBrowse;if(!view||view.readOnly||!view.thread||typeof view.address!=='string'||!view.address)return;await openThread(view.thread,{thread_id:view.thread,address:view.address,name:view.name||view.address},{automatic:false});}
function mediaFeedbackView(){return `${mediaLoading?`<div class="media-loading" role="status">${pilotLoader()}<span>${mediaLoaded?'Refreshing media…':'Finding your media…'}</span></div>`:''}${mediaError?`<div id="media-error" class="notice media-error" role="alert"><p>Media couldn’t load. ${esc(mediaError)}</p><button class="secondary" data-action="load-media">Retry media</button></div>`:''}${mediaLoaded&&!galleryRows(media).length&&!mediaLoading&&!mediaError?`<div class="panel empty">${icon('media')}<h3>No attachments yet.</h3><p>Photos, videos, audio and other attachments will appear here.</p></div>`:''}`;}
function mediaCardsView(){return galleryRows(media).map(m=>`<article class="panel media-card"><h3>${esc(m.name)}</h3><small>${date(m.date*1000)} · ${time(m.date*1000)}</small>${galleryParts(m).map(mediaPartView).join('')}${mediaPartId(m._id)?`<button class="text-button media-take-there" data-action="media-context" data-media-id="${m._id}">Take me there ${icon('arrow')}</button>`:''}</article>`).join('');}
function mediaView(){return `<div class="heading"><div><div class="eyebrow">PICTURES & MULTIMEDIA</div><h1>A little more than words.</h1><p class="subheading">Attachments saved in your conversations.</p></div><button class="icon-button" data-action="load-media" aria-label="Refresh multimedia" ${mediaLoading?'disabled':''}>${icon('refresh')}</button></div><div class="notice">Photos, videos and audio sent and received through your carrier. MMS may need mobile data even while Wi-Fi is connected. Open a conversation to send attachments.</div><section id="media-content" aria-busy="${mediaLoading}"><div id="media-feedback">${mediaFeedbackView()}</div><div id="media-cards">${mediaCardsView()}</div></section>`;}
function syncMediaView(replaceCards=false){
 if(page!=='media')return;
 const content=document.getElementById('media-content');if(!content)return;
 content.setAttribute('aria-busy',String(mediaLoading));
 document.querySelector('[aria-label="Refresh multimedia"]').disabled=mediaLoading;
 document.getElementById('media-feedback').innerHTML=mediaFeedbackView();
 if(replaceCards)document.getElementById('media-cards').innerHTML=mediaCardsView();
}
function loadMedia(force=false){
 if(previewDenied)return Promise.resolve();const accessVersion=privateGeneration;
 if(mediaRequest)return mediaRequest;
 if(!force&&mediaLoaded&&!mediaError&&Date.now()-mediaLoadedAt<MEDIA_CACHE_MS)return Promise.resolve();
 mediaLoading=true;mediaError='';syncMediaView();
 mediaRequest=(async()=>{
  try{
   const result=await api('media');if(accessVersion!==privateGeneration||previewDenied)return;if(!Array.isArray(result))throw new Error('Please try again.');
   const attachments=galleryRows(result),changed=JSON.stringify(attachments)!==JSON.stringify(media);media=attachments;mediaLoaded=true;mediaLoadedAt=Date.now();syncMediaView(changed||force);
  }catch(error){if(accessVersion===privateGeneration)mediaError=error.message||'Please try again.';}
  finally{if(accessVersion===privateGeneration){mediaLoading=false;mediaRequest=null;syncMediaView();}}
 })();return mediaRequest;
}
function blurSearch(){const field=document.activeElement;if(field?.id==='search')field.blur();}
function openMedia(){
 captureChat();blurSearch();closeContacts();timerOpen=false;page='media';
 // Show the destination before either the provider read or a pending draft save.
 render();flushDraftsOnPause();loadMedia();
}
root.addEventListener('load',event=>{if(event.target.matches?.('.media-image'))event.target.closest('.media-image-frame')?.classList.add('is-loaded');},true);
root.addEventListener('error',event=>{if(event.target.matches?.('.link-preview-image')){const timeline=event.target.closest('.timeline'),position=timelinePosition(timeline);const failed=event.target.getAttribute('src');for(const item of linkPreviewCache.values())if(item.result?.imageUrl===failed)item.result.imageUrl='';event.target.remove();if(timeline)restoreTimeline(timeline,position);}if(event.target.matches?.('.media-player')){const error=event.target.closest('.media-playback')?.querySelector('.media-playback-error');if(error)error.hidden=false;}if(event.target.matches?.('.media-image')){const frame=event.target.closest('.media-image-frame');frame?.classList.add('is-error');const error=frame?.querySelector('.media-photo-error');if(error)error.hidden=false;}},true);
document.addEventListener('pointerdown',event=>{if(document.activeElement?.id==='search'&&!event.target.closest?.('.search'))blurSearch();},true);
root.addEventListener('keydown',event=>{if(event.target.id==='recipient'&&event.key==='Enter'&&!event.isComposing){event.preventDefault();startContactChat().catch(error=>toast(error.message));}if(event.target.id==='search'&&['Escape','Enter'].includes(event.key)){event.preventDefault();blurSearch();}});
window.onKeyboardHidden=blurSearch;

// A bounded provider read updates the inbox without waiting for AI, full history
// or the settings snapshot. Missing rows in this partial result are not deletions.
function overlayLiveInbox(snapshot,read){
 const rows=new Map(snapshot.inbox.map(row=>[row.thread_id,row])),newer=new Set();
 for(const [thread,item] of liveInboxUpdates){
  if(item.read>read){rows.set(thread,item.row);newer.add(thread);}
  else if(read>0)liveInboxUpdates.delete(thread);
 }
 snapshot.inbox=orderInboxRows(rows.values());return newer;
}
function applyLiveInbox(result,request){
 if(result?.inboxComplete!==false||!Array.isArray(result.inbox)||typeof result.contactsAllowed!=='boolean'||request.read<=inboxFullRead)return;
 const contacts=result.contactsAllowed&&!contactsDenied,rows=launchInboxRows(result.inbox,contacts,40,true),byThread=new Map(state.inbox.map(row=>[row.thread_id,row]));
 for(const row of rows){
  liveInboxUpdates.set(row.thread_id,{read:request.read,row});
  const mutation=pinMutations.get(row.thread_id);if(!mutation)pinConfirmed.set(row.thread_id,isPinned(row));
  byThread.set(row.thread_id,mutation?{...row,pinned:mutation.pinned}:row);
 }
 state.contactsAllowed=contacts;
 if(!contacts){clearContactPhotos();for(const [thread,row] of byThread)byThread.set(thread,{...row,name:row.address,photo:''});}
 state.inbox=orderInboxRows(byThread.values());
 if(result.contactPhotoRevision!==undefined&&state.contactPhotoRevision!==result.contactPhotoRevision){state.contactPhotoRevision=result.contactPhotoRevision;onContactPhotosChanged();}
 if(!inboxBootstrap.authoritative&&state.inbox.length)inboxBootstrap.cached=true;
 syncInboxList();syncInboxBootstrap();queuePreviewWarm();
}
function refreshLiveInbox(){
 if(!native)return Promise.resolve();liveInboxPending=true;
 if(liveInboxRequest)return liveInboxRequest.promise;
 if(document.visibilityState!=='visible'||previewDenied||accessHint===false)return Promise.resolve();
 const request={read:++inboxReadSequence,generation:privateGeneration,epoch:liveInboxEpoch,promise:null};liveInboxPending=false;liveInboxRequest=request;
 request.promise=(async()=>{try{
  const result=await api('liveInbox');
  if(liveInboxRequest!==request||request.generation!==privateGeneration||request.epoch!==liveInboxEpoch||previewDenied||accessHint===false)return;
  applyLiveInbox(result,request);
 }catch{/* The independent full snapshot remains the fallback. */}
 finally{if(liveInboxRequest===request){liveInboxRequest=null;if(liveInboxPending&&document.visibilityState==='visible'&&!previewDenied&&accessHint!==false)void refreshLiveInbox();}}})();
 return request.promise;
}
function scheduleMessageRefresh(){
 if(messageUpdatePending&&!busy&&document.visibilityState==='visible')queueMicrotask(drainMessageUpdates);
}
function drainMessageUpdates(){
 if(!messageUpdatePending||messageUpdateRunning||busy||document.visibilityState!=='visible')return messageUpdateRunning||Promise.resolve();
 messageUpdateRunning=(async()=>{
  try{
   while(messageUpdatePending&&!busy&&document.visibilityState==='visible'){
    // An older snapshot may have started before the send. Wait, then re-read;
    // never let its completion consume the newer event.
    if(refreshing)await refreshing;
    if(busy||document.visibilityState!=='visible')break;
    messageUpdatePending=false;await refresh(true);if(busy)messageUpdatePending=true;
   }
  }finally{messageUpdateRunning=null;scheduleMessageRefresh();}
 })();return messageUpdateRunning;
}
window.onMessagesChanged=()=>{void refreshLiveInbox();void checkSendState(true);if(current&&conversation?.previewOnly&&archiveHistories.get(current.thread_id)?.pending)loadArchiveHistory(current.thread_id);const starting=!inboxBootstrap.authoritative;if(!starting)invalidateLaunchHistories();invalidatePreviews(starting);messageUpdatePending=true;return drainMessageUpdates();};
async function refresh(force=false){
 if(refreshing)return refreshing;
 refreshing=(async()=>{try{
  const setupRevision=roleStatusRevision,accessVersion=privateGeneration,snapshotRead=++snapshotSequence,inboxRead=++inboxReadSequence,s=await api('snapshot');if(accessVersion!==privateGeneration)return;if(!s||!Array.isArray(s.inbox))throw new Error('Your conversations could not be updated. Try again.');if(setupRevision!==roleStatusRevision){s.defaultSms=state.defaultSms;s.permissions=state.permissions;}const signature=JSON.stringify(s),previous=state,firstInbox=!inboxBootstrap.authoritative,newerInbox=overlayLiveInbox(s,inboxRead);inboxFullRead=inboxRead;
  const oldJob=JSON.stringify(pendingReply());state={theme:themeSaved,lockScreenPreviews:true,inAppSuggestions:true,linkPreviews:true,...s};overlayMutations(state,snapshotRead,newerInbox);state.inbox=orderInboxRows(state.inbox);overlaySendJobs(state,snapshotRead,previous);if(accessHint===false||state.permissions===false||state.defaultSms===false){if(!previewDenied)clearPrivateChats();state.inbox=[];state.jobs=[];}else if(state.permissions===true&&state.defaultSms===true)previewDenied=false;if(contactsDenied||state.contactsAllowed===false){clearContactPhotos();state.inbox=state.inbox.map(row=>({...row,name:row.address,photo:''}));if(contactsDenied)state.contactsAllowed=false;}acceptFirstInbox();applyLaunchHistories();if(previous.contactPhotoRevision!==state.contactPhotoRevision)onContactPhotosChanged();if((previous.linkPreviews!==false)!==(state.linkPreviews!==false)){clearLinkPreviews();if(page==='inbox'&&conversation)updateTimeline();else if(page==='media-thread')syncMediaBrowse();}const present=new Set(state.inbox.map(row=>row.thread_id));for(const thread of chatPreviews.keys())if(!present.has(thread)){chatPreviews.delete(thread);if(thread!==current?.thread_id)threadHistories.delete(thread);}if(JSON.stringify(previous.inbox)!==JSON.stringify(state.inbox))invalidatePreviews(firstInbox);queuePreviewWarm();syncThemeUI();syncPinFeedback();syncPhoneSetup();syncContactSettings();syncLocationUI();syncHistoryCacheUI();
  if(previous.contactsAllowed!==state.contactsAllowed&&compose&&!contactPicker.permissionBusy){loadContacts();}
  const changed=signature!==lastSignature;lastSignature=signature;
  if(changed||force){
   if(page==='inbox'){
    if(force||JSON.stringify(previous.inbox)!==JSON.stringify(state.inbox)||JSON.stringify(previous.jobs)!==JSON.stringify(state.jobs))syncInboxList();
    if(!busy&&current&&conversation&&(oldJob!==JSON.stringify(pendingReply())||previous.autoDraft!==state.autoDraft||JSON.stringify(previous.sleep)!==JSON.stringify(state.sleep)||force))syncLiveComposer();
   }else if(!busy&&!homeCandidates&&!['media','media-thread'].includes(page)&&(!root.contains(document.activeElement)||!document.activeElement.matches('input,textarea,select')))render();
  }
  const thread=current?.thread_id,epoch=threadEpoch;if(['media','media-thread'].includes(page)||!thread||busy||!chatReady())return;
  const read=nextConversationRead(thread),remote=await api('conversation',{thread,receiptIds:visibleReceiptIds()});if(current?.thread_id!==thread||epoch!==threadEpoch||busy||read!==conversationReads.get(thread)||previewDenied)return;
  const previousConversation=conversation,oldHistory=historySignature(conversation.history),c=mergeConversation(thread,remote);applyAttachmentOutcome(previousConversation,c);
  if(c.base!==previousConversation.base){const keepTyped=(dirty||hasAttachments(previousConversation)||conversation.draft?.engine==='Edited by you')&&!!draft.trim(),typed=draft;discardDraftEdits(thread,previousConversation.base);conversation=c;draft=keepTyped?typed:c.draft?.body||'';dirty=!!keepTyped;if(keepTyped)queueDraft(draft);timerOpen=false;syncDraftField();syncLiveComposer();toast(keepTyped?'New message received. Your draft is kept.':'Conversation updated.');}
  else{const decisionChanged=JSON.stringify([c.replyDecision,c.replyHold,c.replyWaiting,c.girlfriendPause,c.attentionActions,c.readOnly,c.attachments,c.latestIncomingTextMmsId,c.latestIncomingMediaId,c.latest?.key])!==JSON.stringify([previousConversation.replyDecision,previousConversation.replyHold,previousConversation.replyWaiting,previousConversation.girlfriendPause,previousConversation.attentionActions,previousConversation.readOnly,previousConversation.attachments,previousConversation.latestIncomingTextMmsId,previousConversation.latestIncomingMediaId,previousConversation.latest?.key]),newDraft=c.draft&&!dirty&&!hasAttachments(c)&&c.draft.body!==previousConversation.draft?.body,expiredMmsDraft=!c.draft&&!dirty&&!hasAttachments(c)&&Number(previousConversation.draft?.source_mms||previousConversation.textMmsId)>0&&previousConversation.draft?.engine?.startsWith('OpenAI')&&draft===previousConversation.draft.body;conversation=c;if(newDraft){draft=c.draft.body;syncDraftField();}else if(expiredMmsDraft||decisionChanged&&(c.replyDecision?.decision==='no_reply'||replyHold(c))&&!dirty&&!hasAttachments(c)&&!c.draft){draft='';syncDraftField();}if(newDraft||decisionChanged||expiredMmsDraft)syncLiveComposer();}
  if((oldHistory!==historySignature(c.history)||c.readOnly!==previousConversation.readOnly||c.relationship?.cloudEnabled!==previousConversation.relationship?.cloudEnabled)&&page==='inbox')updateTimeline();syncReplyModes();syncApprovedLearning();fillHistoryGaps();rememberChat(thread,conversation,true);queueMicrotask(maybeSuggestReply);
 }catch(error){if(!inboxBootstrap.authoritative){inboxBootstrap.error=error.message||'Your conversations could not be updated.';syncInboxBootstrap();}if(force)toast(error.message);}})();
 try{await refreshing;}finally{refreshing=null;scheduleMessageRefresh();}
}
function approvedLearningView(){const value=profileContext().approvedLearning||conversation?.approvedLearning||{count:0,limit:12},count=Number.isSafeInteger(value.count)?value.count:0;return `<section class="approved-learning" id="approved-learning"><strong>${count} approved ${count===1?'reply':'replies'} remembered</strong><p>Final replies you choose and successfully send guide future drafts for this person. Up to ${Number(value.limit)||12} examples are kept; this does not train a model.</p><button class="text-button" data-action="clear-approved-learning" ${!chatReady()||!count||approvedLearningBusy===current?.thread_id?'disabled':''}>${approvedLearningBusy===current?.thread_id?'Forgetting…':'Forget these examples'}</button><small>Your sent messages stay in the conversation.</small></section>`;}
function syncApprovedLearning(){const node=document.getElementById('approved-learning');if(!node)return;const template=document.createElement('template');template.innerHTML=approvedLearningView();const fresh=template.content.firstElementChild;if(node.innerHTML!==fresh.innerHTML)node.replaceWith(fresh);}
async function clearApprovedLearning(){if(!current||!chatReady()||approvedLearningBusy!==null)return;const thread=current.thread_id,epoch=threadEpoch;approvedLearningBusy=thread;syncApprovedLearning();try{const result=await api('clearApprovedLearning',{thread});if(current?.thread_id!==thread||threadEpoch!==epoch)return;conversation.approvedLearning=result;const profile=replyProfiles.get(thread);if(profile){profile.request++;profile.loading=false;if(profile.data)profile.data.approvedLearning=result;}nextConversationRead(thread);toast('Approved reply examples forgotten. Your message history is unchanged.');}finally{approvedLearningBusy=null;syncApprovedLearning();}}
function suggestionAllowed(forMedia=false){return !(conversation?.relationship?.cloudEnabled&&conversation.relationship.autoDraft&&conversation.relationship.autoSend)&&!conversation?.manualReplySuppressed&&!sharedContent&&native&&page==='inbox'&&!!current&&chatReady()&&document.visibilityState==='visible'&&state.inAppSuggestions!==false&&state.cloud?.configured===true&&conversation.relationship?.cloudEnabled===true&&!conversation.readOnly&&!busy&&!suggestionRequest&&!attentionRequest&&!pendingReply()&&!hasAttachments()&&!attachmentRequests.has(current?.thread_id)&&!draft.trim()&&!dirty&&!(forMedia?(girlfriendPaused()||!!replyHold()||sleepMode()==='paused'):automaticReplyBlocked())&&!profileDirty(relationshipEdit());}
async function maybeSuggestReply(){
 const textMms=latestTextMms(),textMmsId=textMms?Number(textMms._id):0,mediaId=Number(conversation?.latestIncomingMediaId);if(!suggestionAllowed(!!textMmsId||Number.isSafeInteger(mediaId)&&mediaId>0))return;
 const sms=latestSms(),thread=current.thread_id,base=conversation.base,epoch=threadEpoch,context=mediaContextKey();
 if(!textMmsId&&Number.isSafeInteger(mediaId)&&mediaId>0){const row=conversation.history.find(item=>messageKind(item)==='mms'&&Number(item._id)===mediaId);if(!row||!hasMessageAttachments(row)||row.m_type===130||row.download&&row.download.status!=='downloaded')return;const key=`${thread}:media:${mediaId}`;if(suggestionAttempts.has(key))return;suggestionAttempts.add(key);await analyzeMedia(mediaId,true);return;}
 if(!textMmsId){if(conversation.latest?.key&&conversation.latest.key!==`sms:${base}`)return;if(sms?.type!==1||!Number.isSafeInteger(base)||base<=0||Number(sms._id)!==base)return;}
 const key=textMmsId?`${thread}:text-mms:${textMmsId}`:`${thread}:sms:${base}`;if(suggestionAttempts.has(key))return;suggestionAttempts.add(key);
 const request={thread,base,epoch,context,textMmsId,manual:manualRevision(thread)};suggestionRequest=request;syncLiveComposer();
 try{const result=await api(textMmsId?'draftMmsText':'suggestReply',textMmsId?{thread,base,textMmsId,inApp:true}:{thread,base});
  if(suggestionRequest!==request||request.manual!==manualRevision(thread)||!sameDraftTarget(thread,epoch,base,context,textMmsId)||hasAttachments()||attachmentRequests.has(thread))return;
  if(textMmsId&&(result?.draftState!==true||result.thread!==thread||result.base!==base||result.textMmsId!==textMmsId))throw new Error('The conversation changed. Open its latest message, then tap Draft reply again.');
  if(result?.waiting===true||result?.replyWaiting===true){suggestionAttempts.delete(key);conversation.replyWaiting=true;return;}
  if(!result||result.base!==base||!textMmsId&&!Array.isArray(result.history))return;
  // The owner can continue typing while the service runs. Never replace that text.
  const previousHistory=historySignature(conversation.history);conversation=textMmsId?{...conversation,...result}:mergeConversation(thread,result);
  if(!dirty&&!hasAttachments()&&!draft.trim()){draft=result.draft?.body||'';syncDraftField();}
  if(textMmsId)rememberTextMmsDecision();
  if(previousHistory!==historySignature(conversation.history))updateTimeline();
 }catch(error){if(suggestionRequest===request&&request.manual===manualRevision(thread)&&current?.thread_id===thread&&threadEpoch===epoch&&page==='inbox')toast(error.message||'A suggestion is unavailable. You can still write your reply.');}
 finally{if(suggestionRequest===request)suggestionRequest=null;syncLiveComposer();syncReplyModes();}
}
function syncMediaInsight(mediaId){const row=conversation?.history?.find(message=>messageKind(message)==='mms'&&Number(message._id)===mediaId),node=document.querySelector(`[data-media-insight="${mediaId}"]`);if(!row||!node)return;const position=timelinePosition();const template=document.createElement('template');template.innerHTML=mediaInsightView(row);node.replaceWith(template.content);restoreTimeline(document.querySelector('.timeline'),position);}
async function analyzeMedia(mediaId,automatic=false){
 if(!current||!chatReady()||page!=='inbox'||!Number.isSafeInteger(mediaId)||mediaId<=0||conversation.readOnly||!conversation.relationship?.cloudEnabled)return;
 const row=conversation.history?.find(message=>messageKind(message)==='mms'&&Number(message._id)===mediaId&&message.type===1);if(!row||!hasMessageAttachments(row)||row.m_type===130)return;
 const thread=current.thread_id,base=conversation.base,epoch=threadEpoch,key=mediaInsightKey(thread,mediaId);if(mediaInsights.get(key)?.loading)return;
 if(automatic&&!suggestionAllowed(true))return;
 const item={thread,base,epoch,manual:manualRevision(thread),context:mediaContextKey(),loading:true,error:'',result:null};mediaInsights.set(key,item);syncMediaInsight(mediaId);
 try{const result=await api('analyzeMedia',{thread,mediaId,inApp:automatic});
  if(item.manual!==manualRevision(thread)||mediaInsights.get(key)!==item||current?.thread_id!==thread||threadEpoch!==epoch||page!=='inbox'||conversation?.base!==base||item.context!==mediaContextKey()||!hasMessageAttachments(conversation.history?.find(message=>messageKind(message)==='mms'&&Number(message._id)===mediaId))){if(mediaInsights.get(key)===item)mediaInsights.delete(key);return;}
  if(!result||Number(result.mediaId)!==mediaId)throw new Error('This media could not be understood. Try again.');
  item.result={summary:String(result.summary||''),intent:String(result.intent||''),confidence:result.confidence,limitation:String(result.limitation||''),suggestion:typeof result.suggestion==='string'?result.suggestion.slice(0,1600):'',reason:String(result.reason||'')};
 }catch(error){if(item.manual===manualRevision(thread)&&mediaInsights.get(key)===item&&current?.thread_id===thread&&threadEpoch===epoch)item.error=error.message||'This media could not be understood. Try again.';else if(mediaInsights.get(key)===item)mediaInsights.delete(key);}
 finally{item.loading=false;if(current?.thread_id===thread&&threadEpoch===epoch&&page==='inbox')syncMediaInsight(mediaId);}
}
async function useMediaReply(mediaId){
 if(!current||!chatReady()||conversation.readOnly||hasAttachments()||busy||pendingReply()||draft.trim())return;
 if(!hasMessageAttachments(conversation.history?.find(message=>messageKind(message)==='mms'&&Number(message._id)===mediaId)))return;
 const item=mediaInsights.get(mediaInsightKey(current.thread_id,mediaId));if(!item?.result?.suggestion||item.loading||conversation.readOnly||item.base!==conversation.base||item.context!==mediaContextKey())return;
 draft=item.result.suggestion;dirty=true;syncDraftField();queueDraft(draft);syncLiveComposer();syncMediaInsight(mediaId);document.getElementById('draft')?.focus({preventScroll:true});
}

async function openThread(thread,override,options={}){
 
 const row=override||state.inbox.find(item=>item.thread_id===thread);if(!row||previewDenied){if(!previewDenied)toast('This conversation is not in the recent inbox.');return false;}
 captureChat();stopHistoryRequest();const epoch=++threadEpoch,read=nextConversationRead(thread);flushDraftsOnPause();
 current={...row,name:contactsDenied?row.address:row.name};if(!inboxBootstrap.authoritative&&launchThreads.has(thread))launchOpenedThread=thread;trimChatPreviews();page='inbox';closeContacts();conversation=provisionalChat(row);chatLoadPending=true;chatLoadSlow=false;chatLoadError='';const buffered=previewDrafts.get(thread);draft=buffered?.body??conversation.draft?.body??'';dirty=!!buffered;tone='Natural';delay=300;delayMode='fixed';delayMin=300;delayMax=1800;custom=false;timerOpen=false;const profile=replyProfileRecord(thread);profile.verified=false;profile.loading=false;profile.epoch=epoch;render();restoreTimeline(document.querySelector('.timeline'),chatPreviews.get(thread)?.position);loadReplyProfile(thread);loadArchiveHistory(thread);
 const slowNotice=setTimeout(()=>{if(current?.thread_id===thread&&epoch===threadEpoch&&read===conversationReads.get(thread)&&chatLoadPending&&!previewDenied){chatLoadSlow=true;syncHydrationNotice();}},12000);
 try{
  const loaded=await api('conversation',{thread});if(current?.thread_id!==thread||epoch!==threadEpoch||read!==conversationReads.get(thread)||previewDenied)return false;
  const typed=previewDrafts.get(thread),sent=loaded.attachments?.lastSend;let edit=draftEdits.get(draftKey(thread,loaded.base));
  if(!typed&&!loaded.draft&&sent?.status==='sent'&&Number(sent.base)===Number(loaded.base)&&edit?.body===sent.caption){discardDraftEdits(thread,loaded.base);edit=null;}
  const keep=typed||edit&&!edit.obsolete&&{body:edit.body};

  conversation=mergeConversation(thread,loaded);chatLoadPending=false;chatLoadSlow=false;chatLoadError='';const savedRecord=archiveHistories.get(thread);if(savedRecord){savedRecord.error='';savedRecord.pending=false;}draft=keep?.body??conversation.draft?.body??'';dirty=!!keep&&(!!typed||!!String(keep.body||'').trim()||edit?.saved<edit?.revision||!!edit?.error);tone='Natural';
  if(typed){previewDrafts.delete(thread);queueDraft(draft);}
  rememberChat(thread,conversation,true);if(page==='inbox'){syncChatHeader();updateTimeline();syncDraftField();syncLiveComposer();}queuePreviewWarm();fillHistoryGaps();
 }catch(error){
  if(current?.thread_id!==thread||epoch!==threadEpoch||read!==conversationReads.get(thread)||previewDenied)return false;
  chatLoadPending=false;chatLoadSlow=false;chatLoadError=String(error?.message||'The conversation could not be read.').slice(0,1000);if(page==='inbox'){syncHydrationNotice();syncLiveComposer();}return false;
 }finally{clearTimeout(slowNotice);}
 if(options.automatic!==false)queueMicrotask(maybeSuggestReply);
 return current?.thread_id===thread&&epoch===threadEpoch&&page==='inbox'&&chatReady();
}
function leaveChat(){captureChat();threadEpoch++;stopHistoryRequest();flushDraftsOnPause();timerOpen=false;current=null;conversation=null;chatLoadPending=false;chatLoadError='';dirty=false;trimChatPreviews();render();}
function navigateTo(destination){closeHomeCandidates(false);captureChat();closeContacts();timerOpen=false;page=destination;render();flushDraftsOnPause();queueMicrotask(maybeSuggestReply);}

async function generate(unused=false,automatic=false){
 if(!chatReady())return;
 if(!conversation.relationship?.cloudEnabled){if(!automatic)toast('Enable AI for this person in Reply setup first.');return;}
 if(hasAttachments()||attachmentRequests.has(current?.thread_id)){if(!automatic)toast('Finish or remove the attachments before drafting a text reply.');return;}
 const textMms=latestTextMms(),textMmsId=textMms?Number(textMms._id):0;
 if(Number(conversation?.latestIncomingMediaId)>0&&!textMmsId){if(!automatic)toast('Use Understand & reply on the media above.');return;}
 if(!current||conversation.readOnly||busy||pendingReply()||suggestionRequest?.thread===current.thread_id||automatic&&automaticReplyBlocked())return;
 if(profileDirty(relationshipEdit())){if(!automatic)toast('Save or clear your profile changes before redrafting.');return;}
 const thread=current.thread_id,epoch=threadEpoch,base=conversation.base,context=mediaContextKey(),request={thread,manual:manualRevision(thread),edit:composerEditRevision};
 draftRequest=request;busy=true;busyThread=thread;syncLiveComposer();
 try{
  await flushDrafts(thread);
  if(draftRequest!==request||request.manual!==manualRevision(thread)||request.edit!==composerEditRevision||!sameDraftTarget(thread,epoch,base,context,textMmsId)||!chatReady()||automatic&&automaticReplyBlocked())return;
  discardDraftEdits(thread,base);nextConversationRead(thread);
  const generationResult=await api(textMmsId?'draftMmsText':'generate',textMmsId?{thread,base,textMmsId,inApp:false}:{thread,base,tone:'Natural',automatic});
  if(draftRequest!==request||request.manual!==manualRevision(thread)||request.edit!==composerEditRevision)return;
  const fast=generationResult?.draftState===true;
  if(textMmsId&&(!fast||generationResult.textMmsId!==textMmsId)||fast&&(generationResult.thread!==thread||generationResult.base!==base))throw new Error('The conversation changed. Open its latest message, then tap Draft reply again.');
  const result=fast?{...conversation,...generationResult}:await api('conversation',{thread});
  if(draftRequest===request&&request.manual===manualRevision(thread)&&request.edit===composerEditRevision&&sameDraftTarget(thread,epoch,base,context,textMmsId)&&!previewDenied){
   conversation=fast?result:mergeConversation(thread,result);draft=result.draft?.body||(result.base===base&&!replyHold(result)&&result.replyDecision?.decision!=='no_reply'?draft:'');dirty=!!draft&&!result.draft;syncDraftField();updateTimeline();
   if(textMmsId)rememberTextMmsDecision();
   if(!result.draft&&result.base===base&&!replyHold(result)&&result.replyDecision?.decision!=='no_reply'&&!(automatic&&(generationResult?.waiting===true||result.replyWaiting===true)))toast('No draft is available for this message. You can write your own.');
  }
 }catch(error){if(draftRequest===request&&request.manual===manualRevision(thread))toast(error.message||'Could not draft a reply. Try again.');}
 finally{if(draftRequest===request){draftRequest=null;busy=false;busyThread=null;syncLiveComposer();syncReplyModes();fillHistoryGaps();void refresh();}}
}

function tick(){void checkSendState();document.querySelectorAll('[data-due]').forEach(el=>{const seconds=Math.max(0,Math.ceil((Number(el.dataset.due)-Date.now())/1000));el.textContent=seconds?`Sends in ${Math.floor(seconds/60)}:${String(seconds%60).padStart(2,'0')}`:'Timer finished · awaiting send status';});}
root.addEventListener('input',e=>{if(e.target.id==='home-address'){homeForm().address=e.target.value;homeForm().dirty=true;closeHomeCandidates(false);syncLocationUI();return;}if(e.target.id==='location-enabled'){changeLocation('saveLocation',{enabled:e.target.checked});return;}if(e.target.id==='pairing-code'){pairingText=e.target.value;root.querySelector('[data-action="connect-cloud"]').disabled=busy||!native||!pairingText.trim();}const testKey={'test-message':'message','test-relationship':'relationship','test-examples':'examples'}[e.target.id];if(testKey){cloudTest[testKey]=e.target.value;cloudTest.error='';document.getElementById('test-error').textContent='';root.querySelector('[data-action="test-run"]').disabled=!canTest();}if(e.target.name==='person-mode'){
 const edit=relationshipEdit(),enabled=e.target.value==='autopilot';if(enabled&&!profileEligibility(edit).eligible){toast('Autopilot needs more history. Recheck or add a chat log below.');render();return;}
 Object.assign(edit,{cloudEnabled:enabled,autoDraft:enabled,autoSend:enabled,engagement:'always_reply',planHandling:'delay_answer',autoDelayMode:'fixed'});render();return;
 }if(e.target.id==='person-share-location'){const edit=relationshipEdit();edit.shareLocation=e.target.checked;document.getElementById('relationship-status').textContent=relationshipStatus(edit);refreshProfileSave(edit);return;}if(e.target.id==='chat-log-owner'){
 const edit=relationshipEdit();edit.logOwner=e.target.value;edit.logError='';
 }if(e.target.id==='relationship-samples'){
 const edit=relationshipEdit();edit.logText=e.target.value;edit.logAnalysis=null;edit.logError='';
 const preview=document.getElementById('chat-log-preview');if(preview)preview.remove();document.getElementById('chat-log-error').textContent='';document.getElementById('samples-count').textContent=edit.logText.length.toLocaleString()+' characters';document.getElementById('chat-log-status').textContent=edit.logText===edit.samples?'Saved examples remain in use.':'Check this log before saving.';
 root.querySelector('[data-action="analyze-chat-log"]').disabled=edit.logBusy||!edit.logText.trim();root.querySelector('[data-action="clear-chat-log"]').disabled=edit.logBusy||!edit.logText&&!edit.samples;refreshProfileSave(edit);
 }if(e.target.id==='relationship-important'){const edit=relationshipEdit();edit.importantDetails=e.target.value;document.getElementById('important-count').textContent=edit.importantDetails.length+'/2000';document.getElementById('relationship-status').textContent=relationshipStatus(edit);refreshProfileSave(edit);return;}if(e.target.id==='relationship-context'){
 const edit=relationshipEdit();edit.text=e.target.value;document.getElementById('relationship-count').textContent=edit.text.length+'/1500';document.getElementById('relationship-status').textContent=relationshipStatus(edit);document.getElementById('relationship-badge').textContent=profileBadge(edit);refreshProfileSave(edit);root.querySelector('[data-action="clear-relationship"]').disabled=busy||(!edit.text&&!edit.saved.text);
 }if(e.target.id==='autopilot-timer'){const edit=relationshipEdit(),value=Number(e.target.value);if(autopilotTimers.some(([n])=>n===value))edit.autoDelay=value;render();return;}
 if(['auto-draft','match-style','in-app-suggestions','link-previews','lock-screen-previews','sim'].includes(e.target.id))preferences=readPreferences();if(e.target.id==='sim')void saveSendingSim(Number(e.target.value));if(e.target.id==='link-previews')clearLinkPreviews();
 if(e.target.id==='draft'){composerEditRevision++;draft=e.target.value;dirty=true;const engine=document.getElementById('engine');if(engine.textContent!=='Edited by you')engine.textContent='Edited by you';resizeComposer();queueDraft(draft);document.querySelectorAll('[data-media-insight]').forEach(node=>syncMediaInsight(Number(node.dataset.mediaInsight)));document.getElementById('accept').disabled=!manualSendEnabled();const schedule=document.querySelector('.schedule-send');if(schedule)schedule.disabled=shareForChat()||!chatReady()||hasAttachments()||!draft.trim()||!!pendingReply()||busy;}
 if(e.target.id==='recipient'){contactPicker.query=e.target.value;contactPicker.request++;clearTimeout(contactPicker.timer);contactPicker.items=[];contactPicker.error='';contactPicker.loading=!!state.contactsAllowed;syncContactsView();contactPicker.timer=setTimeout(loadContacts,180);}
 if(e.target.id==='search'){inboxRenderLimit=120;queuePreviewWarm();search=e.target.value;document.getElementById('conversation-list').innerHTML=rowsView();}if(e.target.id==='custom-delay')delay=Number(e.target.value)*60;
});
function showSendTimer(open=true){if(shareForChat())return;
 if(hasAttachments()){toast('Attachments send now. Timers are available for text messages only.');return;}
 if(!current||!chatReady()||conversation.readOnly||busy||pendingReply()||!draft.trim())return;
 timerOpen=open;relationshipEdit().open=false;render();
 if(open)root.querySelector('#timer-menu button[data-action=delay]')?.focus({preventScroll:true});
}
async function refreshManualConversation(thread,epoch){
 const read=nextConversationRead(thread),manual=manualRevision(thread),generation=privateGeneration;
 try{
  const loaded=await api('conversation',{thread});
  if(current?.thread_id!==thread||threadEpoch!==epoch||generation!==privateGeneration||read!==conversationReads.get(thread)||manual!==manualRevision(thread)||previewDenied)return;
  // This refresh belongs to a manual send. The current editor, including an
  // intentionally empty editor, wins over any older saved/model draft.
  const body=draft;conversation=mergeConversation(thread,loaded);conversation.draft=body?{body,engine:'Edited by you',alternatives:'[]'}:null;
  chatLoadPending=false;chatLoadSlow=false;chatLoadError='';previewDrafts.delete(thread);dirty=!!body.trim();
  if(dirty)queueDraft(body);rememberChat(thread,conversation,true);syncChatHeader();updateTimeline();syncDraftField();syncLiveComposer();
 }catch(error){if(current?.thread_id===thread&&threadEpoch===epoch&&read===conversationReads.get(thread)&&conversation?.previewOnly){chatLoadPending=false;chatLoadSlow=false;chatLoadError=error.message||'Couldn’t update this conversation.';syncHydrationNotice();syncLiveComposer();}}
}
async function sendDraftNow(){
 if(shareForChat())throw new Error('Add or cancel the shared content before sending your message.');
 if(hasAttachments()){await sendAttachmentsNow();return;}
 if(!current||!conversation)throw new Error('Open a conversation before sending.');
 if(!draft.trim())return;
 if(attachmentRequests.has(current.thread_id))throw new Error('Finish choosing or removing your attachment before sending.');
 const thread=current.thread_id,signature=JSON.stringify([thread,current.address,draft,state.sub]);
 if(manualSendRequests.has(signature))return manualSendRequests.get(signature).promise;
 const previous=manualSendAttempts.get(thread),attempt=previous?.signature===signature?previous:{signature,requestId:newRequestId()};manualSendAttempts.set(thread,attempt);
 const payload={thread,address:current.address,body:draft,base:Number(conversation.base)||0,sub:state.sub,requestId:attempt.requestId};
 const request={thread,epoch:threadEpoch,generation:privateGeneration,edit:composerEditRevision,promise:null};manualSendRequests.set(signature,request);
 // The explicit tap owns this text. Native code takes over the newest incoming
 // message and validates permissions/recipient; no draft-save or AI queue blocks it.
 takeManualControl(thread);request.manual=manualRevision(thread);sendingNow=true;syncLiveComposer();
 request.promise=(async()=>{try{
  const result=await api('sendNow',payload);
  if(!result||!['sending','sent'].includes(result.status))throw new Error(result?.note||'The message was not confirmed sent. Check this conversation before retrying.');
  if(manualSendAttempts.get(thread)===attempt)manualSendAttempts.delete(thread);sendStateRevision++;
  if(request.generation!==privateGeneration||previewDenied)return;
  const outcome=Number(result.manualRevision)||request.manual,newest=manualSendOutcomes.get(thread)||0;manualSendOutcomes.set(thread,Math.max(newest,outcome));
  for(const job of state.jobs)if(job.thread===thread&&(isAutomaticJob(job)||job.attention_kind==='delay')&&['scheduled','awaiting_alert'].includes(job.status))job.status='paused';
  if(result.job?.thread===thread)acceptSendJobs(thread,[result.job,...state.jobs.filter(job=>job.thread===thread&&job._id!==result.job._id)]);
  if(request.epoch===threadEpoch&&current?.thread_id===thread){
   if(request.edit===composerEditRevision&&draft===payload.body){draft='';dirty=false;conversation.draft=null;}
   else conversation.draft=draft?{body:draft,engine:'Edited by you',alternatives:'[]'}:null;
   previewDrafts.set(thread,{body:draft});syncDraftField();
   if(result.job?.body===payload.body)observeCommittedSend(thread,normalizeSendJob(result.job),outcome>=newest?result.latestBase:0,true);
  }
  toast(native?(result.status==='sent'?'Message sent.':'Sending message…'):'Demo only · no real message was sent.');
 }finally{
  if(manualSendRequests.get(signature)===request)manualSendRequests.delete(signature);
  sendingNow=manualSendRequests.size>0;syncLiveComposer();void refreshLiveInbox();
  // Re-read this chat directly. An older history/AI result was invalidated at
  // the tap; an unrelated slow snapshot must not delay the new sending state.
  if(request.epoch===threadEpoch&&current?.thread_id===thread&&!manualSending(thread)&&!previewDenied){previewDrafts.set(thread,{body:draft});void refreshManualConversation(thread,request.epoch);}
  void checkSendState(true);
 }})();return request.promise;
}
let sendPress=null,suppressSendClick=false;
function cancelSendPress(){
 if(!sendPress)return;clearTimeout(sendPress.timer);sendPress=null;suppressSendClick=true;
}
function currentSendPress(press){return press.button.isConnected&&!press.button.disabled&&current?.thread_id===press.thread&&draft===press.body;}
// Consume the release click from a hold/cancel, even if the menu replaced its target.
root.addEventListener('click',e=>{if(e.detail!==0&&suppressSendClick){e.preventDefault();e.stopImmediatePropagation();}},true);
document.addEventListener('pointerdown',e=>{
 if(sendPress){cancelSendPress();return;}suppressSendClick=false;
 const button=e.target.closest?.('#accept');if(!button||button.disabled||e.button!==0||e.isPrimary===false)return;
 // Leave the reply editor and keyboard in place until the gesture is resolved.
 e.preventDefault();
 const press=sendPress={button,pointer:e.pointerId,x:e.clientX,y:e.clientY,thread:current?.thread_id,base:conversation?.base,body:draft,held:false};
 press.timer=setTimeout(()=>{
  if(sendPress!==press)return;
  if(!currentSendPress(press)){cancelSendPress();return;}
  press.held=true;suppressSendClick=true;showSendTimer();
 },500);
});
document.addEventListener('pointermove',e=>{if(sendPress&&e.pointerId===sendPress.pointer&&Math.hypot(e.clientX-sendPress.x,e.clientY-sendPress.y)>12)cancelSendPress();});
document.addEventListener('pointerup',e=>{
 const press=sendPress;if(!press||e.pointerId!==press.pointer)return;
 const suppress=press.held||!currentSendPress(press)||Math.hypot(e.clientX-press.x,e.clientY-press.y)>12;
 clearTimeout(press.timer);sendPress=null;
 if(suppress){suppressSendClick=true;e.preventDefault();e.stopPropagation();}
},true);
document.addEventListener('pointercancel',e=>{if(sendPress&&e.pointerId===sendPress.pointer)cancelSendPress();});
root.addEventListener('contextmenu',e=>{if(e.target.closest('#accept')){e.preventDefault();cancelSendPress();showSendTimer();}});
root.addEventListener('keydown',e=>{
 if(!e.target.closest('#accept'))return;
 if(e.key==='ContextMenu'||e.shiftKey&&e.key==='F10'||e.key==='ArrowDown'){e.preventDefault();cancelSendPress();showSendTimer();}
 else if(e.repeat&&(e.key==='Enter'||e.key===' '))e.preventDefault();
});
window.addEventListener('blur',cancelSendPress);
document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='hidden')cancelSendPress();});
async function approveDraft(){if(shareForChat())return;if(hasAttachments()){toast('Attachments send now. Timers are available for text messages only.');return;}if(!current||!chatReady()||conversation.readOnly||busy||pendingReply())return;const approvalEpoch=threadEpoch,approvalThread=current.thread_id;await flushDrafts();if(approvalEpoch!==threadEpoch||current?.thread_id!==approvalThread||!chatReady())return;if(delayMode==='range'?!validRange(delayMin,delayMax):!validFixedTimer(delay))throw new Error('Choose whole minutes from 1 to 10,080, with From no later than To.');if(!draft.trim())return;busy=true;busyThread=current?.thread_id??null;timerOpen=false;render();try{const r=await api('approve',{thread:current.thread_id,address:current.address,body:draft,base:conversation.base,sub:state.sub,...delayPayload({delay,delayMode,delayMin,delayMax})});if(r){dirty=false;await refresh();toast('Your reply timer has started.');}}finally{busy=false;render();}}
root.addEventListener('click',async e=>{const b=e.target.closest('[data-action]');if(!b||b.disabled)return;const action=b.dataset.action;try{if(action==='send-now'){await sendDraftNow();return;}if(action==='share-add'){await addShareToDraft();return;}if(action==='share-choose'){chooseShareRecipient();return;}if(action==='share-cancel'){discardShare();syncLiveComposer();return;}if(action==='share-recent'){const row=state.inbox.find(r=>r.thread_id===Number(b.dataset.thread));if(row)await startContactChat({number:row.address,name:row.name,photo:row.photo});return;}if(sharedContent?.status==='importing'&&!['nav','back','new','close-contacts'].includes(action))return;if(['nav','back','new','close-contacts'].includes(action)&&sharedContent)discardShare();if(action==='role'){await chooseDefaultSms();return;}if(action==='check-role'){await checkSmsRoleStatus(true);return;}if(action==='defaultSettings'||action==='appSettings'){await openSmsSettings(action);return;}if(action==='refresh-history-cache'){await refreshSavedHistory();return;}if(action==='more-chats'){moreInboxRows();return;}if(action==='older-cached'){const cached=archiveHistories.get(current?.thread_id);loadArchiveHistory(current?.thread_id,cached?.error?cached.retryOlder:!!cached?.cursor);return;}if(action==='load-older'){await loadOlderMessages();return;}if(action==='newer-history'||action==='latest-history'){newerLocalWindow(action==='latest-history');return;}if(action==='toggle-profile'){toggleReplySetup();return;}if(action==='close-setup'){closeOverlays(true);return;}if(action==='train-persona'){await trainPersona();return;}if(action==='forget-persona'){await forgetPersona();return;}if(action==='retry-profile'){if(!replyProfileRecord().loading)await loadReplyProfile();return;}if(action==='recheck-history'){await recheckReplyHistory();return;}if(action==='nav'&&homeCandidates)closeHomeCandidates(false);if(action==='theme'){changeTheme(b.dataset.themeChoice);if(themePointer){clearTimeout(themePointer.timer);themePointer.timer=setTimeout(releaseThemePointer,0);}return;}if(action==='retry-pin'){const failure=pinFailures.get(Number(b.dataset.thread));if(failure)pinChat(failure.thread,failure.pinned);return;}if(action==='dismiss-pin-error'){pinFailures.clear();syncPinFeedback();return;}if(action==='open-link'){e.preventDefault();const url=safeMessageUrl(b.dataset.url);if(url)await api('openLink',{url:b.dataset.url});return;}if(action==='retry-inbox'){await retryInbox();return;}if(page==='inbox'&&current&&!chatReady()&&!['open','back','nav','retry-conversation','new','close-chat-menu','pin-chat','close-contacts','choose-contact','compose','toggle-profile','close-setup','retry-profile','save-relationship','clear-relationship','import-chat-log','analyze-chat-log','clear-chat-log'].includes(action))return;if(action==='open-attachment'){await api('openAttachment',{partId:Number(b.dataset.partId)});return;}if(action==='pick-attachments'){await pickAttachments();return;}if(action==='remove-attachment'){await removeAttachment(b.dataset.attachmentId);return;}if(action==='retry-mms'){await retryMms(Number(b.dataset.mediaId));return;}if(action==='analyze-media'){await analyzeMedia(Number(b.dataset.mediaId));return;}if(action==='use-media-reply'){await useMediaReply(Number(b.dataset.mediaId));return;}if(action==='clear-approved-learning'){await clearApprovedLearning();return;}if(action==='request-location'){await changeLocation('requestLocation');return;}if(action==='location-permissions'){await api('locationSettings');return;}if(action==='refresh-location'){await changeLocation('refreshLocation');return;}if(action==='save-home'){await changeLocation('setHome',{address:homeForm().address});return;}if(action==='home-here'||action==='home-candidate-search'){await requestHomeCandidates();return;}if(action==='home-candidate-cancel'){closeHomeCandidates();return;}if(action==='home-candidate-manual'){closeHomeCandidates(true,true);return;}if(action==='home-candidate-prev'||action==='home-candidate-next'){if(homeCandidates&&!homeCandidates.confirming){homeCandidates.index=Math.max(0,Math.min(homeCandidates.candidates.length-1,homeCandidates.index+(action.endsWith('next')?1:-1)));syncHomeCandidates();}return;}if(action==='home-candidate-confirm'){await confirmHomeCandidate();return;}if(action==='clear-home'){await changeLocation('clearHome');return;}if(action==='location-settings-view'){navigateTo('settings');document.getElementById('location-panel')?.scrollIntoView({block:'start'});return;}if(action==='media-context'){openMediaConversation(Number(b.dataset.mediaId));return;}if(action==='media-context-back'){openMedia();return;}if(action==='media-context-older'||action==='media-context-newer'||action==='media-context-retry'){await loadMediaContext(action==='media-context-retry'?mediaBrowse?.failedDirection:action.endsWith('older')?'older':'newer');return;}if(action==='media-context-latest'){await openMediaLatest();return;}if(action==='close-chat-menu'){closePinMenu();return;}if(action==='pin-chat'){await pinChat();return;}if(pinMenu)closePinMenu(false);if(action==='new'){openContacts();return;}if(action==='choose-contact'){const contact=contactPicker.items[Number(b.dataset.contact)];if(contact)await startContactChat(contact);return;}if(action==='compose'){await startContactChat();return;}if(action==='nav'&&b.dataset.page==='media'){openMedia();return;}if(action==='load-media'){loadMedia(true);return;}if(action==='nav'||action==='open')blurSearch();if(action==='send-now'){await sendDraftNow();return;}if(action==='generate'){await generate();return;}if(action==='open'){await openThread(Number(b.dataset.thread));return;}if(action==='back'){leaveChat();return;}if(action==='nav'){navigateTo(b.dataset.page);return;}if(action==='retry-conversation'){if(current&&(!chatLoadPending||chatLoadSlow))await openThread(current.thread_id,current,{automatic:false});return;}const actionEpoch=threadEpoch,actionThread=current?.thread_id;await flushDrafts();if(actionThread&&(actionEpoch!==threadEpoch||actionThread!==current?.thread_id))return;switch(action){
case 'connect-cloud': busy=true;busyThread=current?.thread_id??null;render();try{state.cloud=await api('connectCloud',{pairing:pairingText});pairingText='';cloudStatus='Connection saved. Check it or try a test reply.';await refresh();}catch(e){cloudStatus=e.message;}finally{busy=false;render();}break;
case 'disconnect-cloud': busy=true;busyThread=current?.thread_id??null;render();try{await api('disconnectCloud');state.cloud={configured:false};pairingText='';cloudStatus='Phone disconnected. Automatic timers are paused; cancel any manually scheduled replies in Queue.';await refresh();}finally{busy=false;render();}break;
case 'check-cloud': busy=true;busyThread=current?.thread_id??null;render();try{await checkCloud();}catch(e){cloudStatus=e.message;}finally{busy=false;render();}break;
case 'test-run': await runCloudTest();break;
case 'test-clear': cloudTest={message:'',relationship:'',examples:'',tone:'Natural',results:[],error:''};render();break;
case 'test-check': busy=true;busyThread=current?.thread_id??null;render();try{await checkCloud();toast(cloudStatus);}catch(e){cloudTest.error=e.message;}finally{busy=false;render();}break;
case 'test-scenario': {const examples=[['Hey, want to grab coffee tomorrow morning?','We are old friends. Casual and friendly. I have not checked my schedule yet.'],['Can you cover my shift this Saturday?','This is my coworker. Be friendly and professional. I do not know my availability yet.'],['I feel like you never make time for me anymore.','This is a close friend. Be considerate and avoid dismissing their feelings.']];[cloudTest.message,cloudTest.relationship]=examples[Number(b.dataset.scenario)];cloudTest.error='';render();break;}
case 'nav': closeContacts();timerOpen=false;page=b.dataset.page;render();queueMicrotask(maybeSuggestReply);break;
case 'open': if(!busy)await openThread(Number(b.dataset.thread));break;
case 'retry-conversation': if(current&&!conversation&&!chatLoadPending)await openThread(current.thread_id,current,{automatic:false});break;
case 'back': threadEpoch++;stopHistoryRequest();timerOpen=false;current=null;conversation=null;chatLoadPending=false;chatLoadError='';dirty=false;render();break;
case 'load-older': await loadOlderMessages();break;
case 'retry-history-gap': {const gap=historyRecord().gaps.find(item=>item.id===Number(b.dataset.gap));if(gap){gap.error='';await fillHistoryGaps();}break;}
case 'close-contacts': closeContacts();render();document.querySelector('[data-action=new]')?.focus({preventScroll:true});break;
case 'load-contacts': await loadContacts();break;
case 'request-contacts': await allowContacts();break;
case 'tone': tone=b.dataset.tone;render();break;
case 'delay': delay=Number(b.dataset.delay);delayMode='fixed';custom=false;await approveDraft();break;


case 'toggle-timer': showSendTimer(!timerOpen);break;
case 'close-timer': timerOpen=false;render();document.getElementById('accept')?.focus({preventScroll:true});break;
case 'toggle-profile': {const edit=relationshipEdit();edit.open=!edit.open;timerOpen=false;render();if(edit.open)root.querySelector('#relationship-panel input')?.focus({preventScroll:true});break;}
case 'close-setup': closeOverlays(true);break;
case 'alternative': draft=JSON.parse(conversation.draft.alternatives)[Number(b.dataset.index)];dirty=true;await api('saveDraft',{thread:current.thread_id,base:conversation.base,body:draft});await refresh();render();break;
case 'import-chat-log': await importChatLog();break;
case 'analyze-chat-log': await analyzeChatLog();break;
case 'clear-chat-log': await analyzeChatLog(true);break;
case 'save-relationship': await saveRelationship();break;
case 'clear-relationship': await saveRelationship(true);break;
case 'generate': await generate();break;
case 'approve': await approveDraft();break;
case 'cancel': await api('cancel',{id:Number(b.dataset.id)});await refresh(true);toast('Cancelled. This reply will not be sent.');break;
case 'review': {const j=state.jobs.find(j=>j._id===Number(b.dataset.id));if(await openThread(j.thread,{thread_id:j.thread,address:j.address,name:state.inbox.find(x=>x.thread_id===j.thread)?.name||j.address})){draft=j.body;dirty=true;render();}break;}
case 'save-settings': {const prefs=readPreferences();await api('settings',{sub:prefs.sub,autoDraft:prefs.autoDraft,matchMyStyle:prefs.matchMyStyle,inAppSuggestions:prefs.inAppSuggestions,linkPreviews:prefs.linkPreviews,lockScreenPreviews:prefs.lockScreenPreviews,tone:prefs.tone});preferences=null;await refresh(true);toast('Preferences saved.');break;}




case 'notificationSettings': await api(action);break;
case 'permissions':case 'alarms': await api(action);await refresh(true);break;
}}catch(err){toast(err.message);}});
root.addEventListener('scroll',event=>{if(event.target.matches?.('.conversation-list')&&event.target.scrollHeight-event.target.scrollTop-event.target.clientHeight<300)moreInboxRows();if(event.target.matches?.('.timeline')&&event.target.scrollTop<160){const record=historyRecord();if(!record.error)loadOlderMessages();}},true);
root.addEventListener('compositionstart',()=>{composing=true;});
root.addEventListener('compositionend',()=>{composing=false;resizeComposer();});
root.addEventListener('focusout',e=>{if(e.target.id==='draft')flushDraftsOnPause();});
root.addEventListener('focusin',syncViewport);
window.onNativePause=()=>{closeHomeCandidates(false);pauseLinkPreviews();cancelSendPress();blurSearch();flushDraftsOnPause();};
document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='hidden'){closeHomeCandidates(false);flushDraftsOnPause();pauseLinkPreviews();}else{if(!native)linkPreviewPaused=false;if(liveInboxPending)void refreshLiveInbox();syncViewport();scheduleLinkPreviews();}});
window.onNativeResume=async()=>{void refreshLiveInbox();checkIncomingShare();checkSmsRoleStatus();linkPreviewPaused=false;invalidateLaunchHistories();invalidatePreviews();syncViewport();if(!busy)await refresh();if(compose&&!contactPicker.permissionBusy)loadContacts();scheduleLinkPreviews();};
window.openIntent=async p=>{await refresh();if(p.address){const r=await api('compose',{address:p.address});const opened=await openThread(r.thread,{thread_id:r.thread,address:p.address,name:r.name});if(opened&&p.body){draft=p.body;dirty=true;render();}}else if(p.thread)await openThread(p.thread);};
window.goBack=async()=>{if(sharedContent)discardShare();if(page==='media-thread'){openMedia();return;}if(pinMenu){closePinMenu();return;}if(document.activeElement?.id==='search'){blurSearch();return;}flushDraftsOnPause();if(compose){closeContacts();render();return;}if(closeOverlays(true))return;if(current&&page==='inbox'){leaveChat();}else if(page!=='inbox'){navigateTo('inbox');}else api('close');};
document.addEventListener('keydown',e=>{if(e.key!=='Escape')return;if(sharedContent){discardShare();syncLiveComposer();}if(compose){closeContacts();render();e.preventDefault();}else if(closeOverlays(true))e.preventDefault();});
document.addEventListener('click',e=>{if(e.composedPath().some(node=>node.matches?.('#relationship-panel,#reply-setup,#timer-menu,#accept,.schedule-send,#chat-menu-layer')))return;if(timerOpen||current&&conversation&&relationshipEdit().open)closeOverlays(false);});
render();checkIncomingShare();void refreshLiveInbox();loadLaunchInbox();loadLaunchHistories();loadArchiveInbox();refresh(true).then(()=>{if(!native)openThread(1);});setInterval(()=>{if(document.visibilityState==='visible'&&!busy)refresh();},3500);setInterval(tick,1000);
