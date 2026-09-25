const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const localAssets=require('./ui-local-assets.cjs');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true}),errors=[];await localAssets(page,url);
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value)),access={readSms:true,defaultSms:true,contacts:true};
   window.calls=[];window.holds=new Set();window.held=new Map();window.sendCount=0;
   const profile={cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:300,body:'Friends',samples:'',importantDetails:'',engagement:'natural',planHandling:'ask_me',revision:1};
   const message={_id:60,thread_id:1,kind:'sms',date:60000,type:1,body:'Fictional latest message',read:1};
   window.fixture={historyLearning:{phase:'ready',ready:true},defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:true,inAppSuggestions:false,matchMyStyle:true,theme:'midnight',cloud:{configured:true},jobs:[],inbox:[{...message,address:'+12025550101',name:'Fictional Alex'}],delay:300};
   window.chat={thread:1,address:'+12025550101',name:'Fictional Alex',base:60,history:[message],smsLatest:message,latest:{key:'sms:60'},hasMore:false,hasOlder:false,draft:null,relationship:profile,attachments:{items:[],enabled:true,sending:false,maxItems:6},replyEligibility:{eligible:true,available:true,total:30,owner:10,incoming:20}};
   window.release=(action,result,error=null)=>{const list=held.get(action)||[],item=list.shift();if(!item)throw new Error('No pending '+action);if(!list.length)held.delete(action);nativeResult(item.id,clone(result===undefined?item.result:result),error);};
   window.acceptSend=()=>{const item=held.get('sendNow')?.[0];if(!item)throw new Error('No pending send');const p=item.p,id=61+sendCount++,row={_id:id,thread_id:1,kind:'sms',type:4,read:1,date:id*1000,body:p.body,delivery:'pending'};
    const job={_id:id,thread:1,base:chat.base,status:'sending',delivery_status:'pending',auto_send:0,body:p.body,address:p.address,uri:'content://sms/'+id,sms_date:row.date,parts:1};
    fixture.jobs=fixture.jobs.map(job=>({...job,status:job.status==='scheduled'?'cancelled':job.status}));fixture.jobs.push(job);chat.base=id;chat.smsLatest=row;chat.latest={key:'sms:'+id};chat.history.push(row);chat.draft=null;
    release('sendNow',{id,status:'sending',job,latestBase:id,base:job.base,manualRevision:sendCount});
   };
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={};
    if(action==='snapshot')result=fixture;
    else if(action==='liveInbox')result={inbox:fixture.inbox,inboxComplete:false,contactsAllowed:true,contactPhotoRevision:1};
    else if(action==='conversation')result=chat;
    else if(action==='sendState')result={thread:p.thread,latestBase:chat.base,jobs:fixture.jobs};
    else if(action==='replyProfile')result={thread:1,address:chat.address,name:chat.name,relationship:profile,replyEligibility:chat.replyEligibility,readOnly:false,access};
    else if(action==='cacheInbox'||action==='launchInbox')result={inbox:[],hasMore:false,access};
    else if(action==='cacheHistory')result={thread:1,address:chat.address,history:[],hasMore:false,access};
    else if(action==='launchHistories'||action==='prefetchHistory')result={conversations:[],access};
    else if(action==='generate')result={draftState:true,thread:1,base:chat.base,draft:{body:'Late generated answer',engine:'OpenAI',alternatives:'[]'}};
    else if(action==='suggestReply')result={...chat,draft:{body:'Late automatic suggestion',engine:'OpenAI',alternatives:'[]'}};
    if(holds.has(action)){if(!held.has(action))held.set(action,[]);held.get(action).push({id,p,result:clone(result)});return;}
    setTimeout(()=>nativeResult(id,clone(result),null),0);
   }};
  });
  const send=()=>page.locator('#accept'),field=()=>page.locator('#draft');
  const fresh=async loading=>{await page.goto(url);await page.locator('.row').waitFor();if(loading)await page.evaluate(()=>holds.add('conversation'));await page.locator('.row').tap();await field().waitFor();if(!loading)await page.waitForFunction(()=>chatReady());};
  await fresh(false);

  phase='typing and real pointer sending override an unfinished AI and draft-save request';
  await page.evaluate(()=>{holds.add('generate');holds.add('saveDraft');holds.add('sendNow');});
  await page.locator('[data-action="generate"]').tap();await page.waitForFunction(()=>held.has('generate'));
  assert(await field().isEnabled());await field().fill('My manual answer');assert(await send().isEnabled());await send().tap();
  await page.waitForFunction(()=>held.has('sendNow'),{},{timeout:1500});assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='sendNow').length),1);
  assert(await page.evaluate(()=>held.has('saveDraft')&&held.has('generate')),'Manual Send must not wait for either queue');
  assert.match(await page.evaluate(()=>calls.find(call=>call.action==='sendNow').p.requestId),/^[0-9a-f]{8}-[0-9a-f-]{27}$/i);
  assert(await field().isEnabled());assert(await send().isEnabled());

  phase='same-body duplicate taps coalesce and older send success keeps newer typing';
  await send().tap();assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='sendNow').length),1);
  await field().fill('Next reply typed before handoff');await page.evaluate(()=>{holds.add('conversation');acceptSend();});
  await page.waitForFunction(()=>manualSendRequests.size===0);assert.equal(await field().inputValue(),'Next reply typed before handoff');assert(await send().isEnabled());

  phase='a follow-up sends while the first carrier delivery and background history remain pending';
  await send().tap();await page.waitForFunction(()=>calls.filter(call=>call.action==='sendNow').length===2);
  const ids=await page.evaluate(()=>calls.filter(call=>call.action==='sendNow').map(call=>call.p.requestId));assert.notEqual(ids[0],ids[1]);
  await page.evaluate(()=>acceptSend());await page.waitForFunction(()=>manualSendRequests.size===0);assert.equal(await field().inputValue(),'');

  phase='detached AI and obsolete saves cannot restore a sent draft or clear follow-up typing';
  await field().fill('Keep this newest text');
  await page.evaluate(()=>{release('generate');while(held.has('saveDraft'))release('saveDraft');while(held.has('conversation'))release('conversation');});
  await page.waitForFunction(()=>!draftRequest);assert.equal(await field().inputValue(),'Keep this newest text');assert(await field().isEnabled());assert(await send().isEnabled());

  phase='failed send keeps text, shows native error and retries with the same idempotency key';
  await send().tap();await page.waitForFunction(()=>held.has('sendNow'));const failedId=await page.evaluate(()=>held.get('sendNow')[0].p.requestId);
  await page.evaluate(()=>release('sendNow',{},'Choose a SIM in Settings before sending.'));await page.waitForFunction(()=>manualSendRequests.size===0);
  assert.equal(await field().inputValue(),'Keep this newest text');assert.match(await page.locator('#toast').innerText(),/Choose a SIM/);assert(await send().isEnabled());
  await send().tap();await page.waitForFunction(()=>held.has('sendNow'));assert.equal(await page.evaluate(()=>held.get('sendNow')[0].p.requestId),failedId);
  await page.evaluate(()=>acceptSend());await page.waitForFunction(()=>manualSendRequests.size===0);

  phase='a scheduled automatic timer and a manual timer do not disable owner input or Send';
  await fresh(false);
  for(const automatic of [1,0]){
   await page.evaluate(auto=>{state.jobs=[{_id:10+auto,thread:1,base:60,body:'Queued answer',status:'scheduled',auto_send:auto,auto_delay:300,due:Date.now()+300000}];holds.add('sendNow');syncLiveComposer();},automatic);
   assert(await field().isEnabled());await field().fill('Owner takes over '+automatic);assert(await send().isEnabled());await send().tap();await page.waitForFunction(()=>held.has('sendNow'));
   await page.evaluate(()=>acceptSend());await page.waitForFunction(()=>manualSendRequests.size===0);await page.waitForFunction(()=>chatReady());assert.equal(await field().inputValue(),'');
  }

  phase='manual Send works before history hydration and forwards unknown base to native verification';
  await fresh(true);await page.evaluate(()=>holds.add('sendNow'));assert(await field().isEnabled());await field().fill('Send while history is loading');assert(await send().isEnabled());await send().tap();await page.waitForFunction(()=>held.has('sendNow'),{},{timeout:1500});
  assert.equal(await page.evaluate(()=>calls.find(call=>call.action==='sendNow').p.base),0);await page.evaluate(()=>acceptSend());await page.waitForFunction(()=>manualSendRequests.size===0);
  await page.evaluate(()=>{while(held.has('conversation'))release('conversation');});assert.equal(await field().inputValue(),'');

  phase='out-of-order manual handoff responses cannot advance to an older outgoing cursor';
  await fresh(false);await page.evaluate(()=>{holds.add('sendNow');holds.add('conversation');holds.add('sendState');});
  await field().fill('First quick message');await send().tap();await field().fill('Second quick message');await send().tap();await page.waitForFunction(()=>held.get('sendNow')?.length===2);
  await page.evaluate(()=>{const second=held.get('sendNow').splice(1,1)[0];nativeResult(second.id,{id:62,status:'sending',manualRevision:2,latestBase:62,base:61,job:{_id:62,thread:1,base:61,status:'sending',body:second.p.body,address:chat.address,uri:'content://sms/62',sms_date:62000}},null);});
  await page.waitForFunction(()=>manualSendRequests.size===1);
  await page.evaluate(()=>release('sendNow',{id:61,status:'sending',manualRevision:1,latestBase:61,base:60,job:{_id:61,thread:1,base:60,status:'sending',body:'First quick message',address:chat.address,uri:'content://sms/61',sms_date:61000}}));
  await page.waitForFunction(()=>manualSendRequests.size===0);assert.equal(await page.evaluate(()=>conversation.base),60,'Wait for the fresh provider read instead of advancing to an obsolete earlier handoff');
  assert.equal(await field().inputValue(),'');assert.equal(await page.locator('[data-message-key="sms:62"]').count(),1);

  phase='late automatic suggestions are detached by manual send';
  // Simulate an already-started legacy review-only request; new Autopilot no longer starts these.
  await fresh(false);await page.evaluate(()=>{conversation.relationship.autoSend=false;state.inAppSuggestions=true;holds.add('suggestReply');holds.add('sendNow');void maybeSuggestReply();});await page.waitForFunction(()=>held.has('suggestReply'));
  await field().fill('I am handling this myself');await send().tap();await page.waitForFunction(()=>held.has('sendNow'));await page.evaluate(()=>acceptSend());await page.waitForFunction(()=>manualSendRequests.size===0);
  await field().fill('A further thought');await page.evaluate(()=>release('suggestReply'));assert.equal(await field().inputValue(),'A further thought');

  phase='selected attachments are retained and never silently sent as SMS';
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  await page.evaluate(()=>{chat.attachments=conversation.attachments={items:[{id:'file-1',name:'Fictional photo',mime:'image/jpeg',bytes:400}],enabled:false,note:'Choose a SIM before sending attachments.',maxItems:6};syncLiveComposer();});
  const before=await page.evaluate(()=>calls.filter(call=>call.action==='sendNow').length);await send().tap();
  assert.match(await page.locator('#toast').innerText(),/Choose a SIM/);assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='sendNow').length),before);assert.equal(await page.locator('.attachment-chip').count(),1);assert.equal(await field().inputValue(),'A further thought');
  assert.deepEqual(errors,[]);
  console.log('PASS: manual send overrides AI/save/history/timer queues; editor stays active; duplicate taps coalesce; follow-ups do not wait for delivery; newer typing and failed sends are preserved; retries reuse UUID; late model results are ignored; attachments never fall back to SMS. Fictional bridge only.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
