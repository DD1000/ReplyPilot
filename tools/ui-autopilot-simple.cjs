const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const localAssets=require('./ui-local-assets.cjs');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true}),errors=[];await localAssets(page,url);
  page.on('pageerror',error=>errors.push(error.message));page.setDefaultTimeout(8000);
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
    else if(action==='saveProfile'){Object.assign(profile,p,{revision:profile.revision+1});result={...profile,replyEligibility:chat.replyEligibility};}
    else if(action==='historyLearningStatus'||action==='retryHistoryLearning')result=fixture.historyLearning;
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

  const open=async()=>{await page.locator('.row').first().tap();await page.waitForFunction(()=>chatReady()&&profileReady());};
  await page.goto(url);await page.locator('.row').first().waitFor();
  await page.evaluate(()=>Object.assign(chat.relationship,{cloudEnabled:true,autoDraft:false,autoSend:false,autoDelay:1800,autoDelayMode:'range',engagement:'girlfriend'}));
  await open();
  phase='retired practice, attention, style and timer modes are absent';
  await page.evaluate(()=>{conversation.replyHold={reason:'plans_need_input',message:'Old hold'};conversation.replyDecision={decision:'no_reply',reason:'needs_review'};conversation.attentionActions={jokeToken:'fictional',delayToken:'fictional'};syncLiveComposer();});
  assert.equal(await page.locator('[data-action="train-pilot"],#pilot-dialog,#reply-decision,[data-action="attention-action"],#girlfriend-pause').count(),0);
  await page.locator('#reply-setup').tap();
  assert.deepEqual(await page.locator('input[name="person-mode"]').evaluateAll(nodes=>nodes.map(n=>n.value)),['off','autopilot']);
  assert(await page.locator('input[name="person-mode"][value="off"]').isChecked(),'Old nonautomatic profile must remain Off');
  assert.equal(await page.locator('#reply-readiness').count(),0,'Ready history should leave no readiness block');
  assert.equal(await page.locator('input[name="person-engagement"],input[name="person-plans"],#auto-delay,#person-custom-delay').count(),0);
  assert.equal(await page.locator('#autopilot-timer').count(),0);
  assert.equal(await page.evaluate(()=>calls.filter(x=>x.action==='saveProfile').length),0);

  phase='one compact timer defaults Instant and profile save uses normalized Autopilot contract';
  await page.locator('input[name="person-mode"][value="autopilot"]').check();
  assert.deepEqual(await page.locator('#autopilot-timer option').evaluateAll(nodes=>nodes.map(n=>[n.value,n.textContent])),[['0','Instant'],['60','1 minute'],['300','5 minutes']]);
  assert.equal(await page.locator('#autopilot-timer').inputValue(),'0');
  await page.locator('#relationship-important').fill('Keep personal addresses private.');
  await page.locator('#autopilot-timer').selectOption('60');
  await page.locator('[data-action="save-relationship"]').tap();await page.waitForFunction(()=>calls.some(x=>x.action==='saveProfile')&&!busy);
  const saved=await page.evaluate(()=>calls.find(x=>x.action==='saveProfile').p);
  assert.deepEqual([saved.cloudEnabled,saved.autoDraft,saved.autoSend,saved.autoDelay,saved.autoDelayMode,saved.engagement,saved.planHandling],[true,true,true,60,'fixed','always_reply','delay_answer']);
  assert.equal(saved.importantDetails,'Keep personal addresses private.');
  await page.locator('input[name="person-mode"][value="off"]').check();await page.locator('[data-action="save-relationship"]').tap();await page.waitForFunction(()=>calls.filter(x=>x.action==='saveProfile').length===2&&!busy);
  assert.deepEqual(await page.evaluate(()=>{const p=calls.filter(x=>x.action==='saveProfile').at(-1).p;return[p.cloudEnabled,p.autoDraft,p.autoSend];}),[false,false,false]);

  phase='insufficient history can be rechecked and disappears without turning on Autopilot';
  await page.evaluate(()=>{chat.replyEligibility={eligible:false,available:true,total:4,owner:1,incoming:3};});
  await page.evaluate(()=>loadReplyProfile());await page.locator('#reply-readiness').waitFor();
  await page.locator('input[name="person-mode"][value="autopilot"]').tap();assert(await page.locator('input[name="person-mode"][value="off"]').isChecked());
  await page.evaluate(()=>{chat.replyEligibility={eligible:true,available:true,total:25,owner:10,incoming:15};});
  await page.locator('[data-action="recheck-history"]').tap();await page.waitForFunction(()=>!document.getElementById('reply-readiness'));
  assert(await page.locator('input[name="person-mode"][value="off"]').isChecked());
  assert.equal(await page.evaluate(()=>calls.filter(x=>x.action==='saveProfile').length),2);

  phase='foreground suggestions cannot claim an Autopilot turn first';
  await page.locator('[data-action="close-setup"]').tap();
  await page.evaluate(async()=>{const prior={...conversation.relationship};state.inAppSuggestions=true;Object.assign(conversation.relationship,{cloudEnabled:true,autoDraft:true,autoSend:true});window.beforeSuggestions=calls.filter(x=>['suggestReply','draftMmsText','analyzeMedia'].includes(x.action)).length;await maybeSuggestReply();window.afterSuggestions=calls.filter(x=>['suggestReply','draftMmsText','analyzeMedia'].includes(x.action)).length;conversation.relationship=prior;state.inAppSuggestions=false;});
  assert.equal(await page.evaluate(()=>afterSuggestions),await page.evaluate(()=>beforeSuggestions),'Autopilot owns the automatic turn; foreground should not create a competing review draft');

  phase='manual hold timer has exactly Instant/1 minute/5 minutes';
  await page.locator('#draft').fill('Fictional manual reply');
  await page.locator('[data-action="toggle-timer"]').tap();
  assert.deepEqual(await page.locator('#timer-menu .timer').allTextContents(),['Instant','1 minute','5 minutes']);
  await page.locator('#timer-menu [data-delay="60"]').tap();await page.waitForFunction(()=>calls.some(x=>x.action==='approve')&&!busy);
  assert.equal(await page.evaluate(()=>calls.find(x=>x.action==='approve').p.delay),60);
  await page.locator('#draft').fill('Fictional immediate reply');await page.locator('[data-action="toggle-timer"]').tap();
  await page.evaluate(()=>holds.add('sendNow'));await page.locator('#timer-menu [data-action="send-now"]').tap();await page.waitForFunction(()=>held.has('sendNow'));
  assert.equal(await page.evaluate(()=>calls.filter(x=>x.action==='approve').length),1);
  await page.evaluate(()=>acceptSend());await page.waitForFunction(()=>manualSendRequests.size===0);

  phase='full-history preparation shows progress, blocks only AI, and ignores an older snapshot';
  await page.evaluate(()=>{fixture.historyLearning={phase:'analyzing',ready:false,processedMessages:30,totalMessages:200,completedContacts:1,totalContacts:5};state.historyLearning=fixture.historyLearning;holds.add('historyLearningStatus');syncLiveComposer();});
  await page.locator('#history-learning-notice').waitFor();assert(await page.locator('[data-action="generate"]').isDisabled());
  await page.locator('#draft').fill('I can still send this');assert(await page.locator('#accept').isEnabled());assert(await page.locator('#draft').isEnabled());
  await page.locator('[data-action="history-learning-settings"]').tap();
  assert.match(await page.locator('#history-learning-panel').innerText(),/all available SMS and MMS text history/);
  assert.match(await page.locator('#history-learning-panel').innerText(),/30 \/ 200 messages/);
  assert.equal(await page.locator('#sleep-settings,#delay-setting,#default-custom-delay').count(),0);
  await page.waitForFunction(()=>held.has('historyLearningStatus'));
  await page.evaluate(()=>{holds.add('snapshot');void refresh(true);});await page.waitForFunction(()=>held.has('snapshot'));
  await page.evaluate(()=>{fixture.historyLearning={phase:'ready',ready:true,processedMessages:200,totalMessages:200,completedContacts:5,totalContacts:5};release('historyLearningStatus',fixture.historyLearning);});
  await page.waitForFunction(()=>historyLearningReady());await page.evaluate(()=>release('snapshot'));await page.waitForFunction(()=>!refreshing);
  assert(await page.evaluate(()=>historyLearningReady()),'Old snapshot must not replace newly completed preparation');
  const polls=await page.evaluate(()=>calls.filter(x=>x.action==='historyLearningStatus').length);await page.waitForTimeout(1800);
  assert.equal(await page.evaluate(()=>calls.filter(x=>x.action==='historyLearningStatus').length),polls,'Ready state must stop polling');

  phase='a newer ready snapshot rejects an older in-flight progress result';
  await page.evaluate(()=>{holds.delete('snapshot');state.historyLearning={phase:'analyzing',ready:false,processedMessages:80,totalMessages:200};historyLearningNextCheck=0;void checkHistoryLearning();});
  await page.waitForFunction(()=>held.has('historyLearningStatus'));
  await page.evaluate(()=>refresh(true));assert(await page.evaluate(()=>historyLearningReady()));
  await page.evaluate(()=>release('historyLearningStatus',{phase:'analyzing',ready:false,processedMessages:80,totalMessages:200}));
  await page.waitForFunction(()=>!historyLearningRequest);assert(await page.evaluate(()=>historyLearningReady()));

  phase='retry is explicit and a late response cannot survive permission clearing';
  await page.evaluate(()=>{state.historyLearning={phase:'error',ready:false,error:'Fictional interrupted preparation'};fixture.historyLearning={phase:'preparing',ready:false,processedMessages:0,totalMessages:200};syncHistoryLearningStatus();});
  await page.locator('[data-action="retry-history-learning"]').tap();await page.waitForFunction(()=>calls.some(x=>x.action==='retryHistoryLearning'));
  await page.waitForFunction(()=>held.has('historyLearningStatus'));
  await page.evaluate(()=>{accessHint=false;clearPrivateChats();release('historyLearningStatus',{phase:'ready',ready:true});});
  assert.equal(await page.evaluate(()=>historyLearningReady()),false);
  assert.equal(await page.evaluate(()=>calls.filter(x=>/^trainPilot|attentionAction|resumeGirlfriend|^sleep$/.test(x.action)).length),0);
  assert.deepEqual(errors,[]);
  console.log('PASS: Off/Autopilot-only setup, compact timers, no practice/attention/sleep UI, eligibility recheck disappearance, normalized saves, full-history progress/retry and stale/private-access guards; manual sending remains active. Fictional native bridge only.');
 }catch(error){throw new Error(phase+': '+error.message,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
