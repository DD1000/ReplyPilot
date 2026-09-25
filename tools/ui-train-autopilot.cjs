const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const localAssets=require('./ui-local-assets.cjs');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18770';

// Train Autopilot is per chat and explicit. Nothing trains in the background, training
// never turns Autopilot on by itself, and Autopilot stays off until the chat is trained.
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true}),errors=[];await localAssets(page,url);
  page.on('pageerror',error=>errors.push(error.message));page.setDefaultTimeout(8000);
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value)),access={readSms:true,defaultSms:true,contacts:true};
   window.calls=[];window.holds=new Set();window.held=new Map();
   const profile={cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:0,body:'',samples:'',importantDetails:'',engagement:'always_reply',planHandling:'delay_answer',revision:1};
   const message={_id:60,thread_id:1,kind:'sms',date:60000,type:1,body:'Fictional latest message',read:1};
   window.untrained={state:'untrained',trained:false,error:''};
   window.trained={state:'ready',trained:true,trainedAt:Date.UTC(2026,8,25),trainedMessages:42,newMessages:0,suggestRetrain:false,thin:true,model:'gpt-6-astra',
    persona:{writingStyle:'Short, lowercase, says "bet".',relationship:'Fictional close friend.',context:'Historical: fictional soccer games.',avoid:'No emojis.',examples:[{incoming:'you coming?',reply:'ya prob'},{incoming:'',reply:'haha bet'}]}};
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:true,inAppSuggestions:false,matchMyStyle:true,theme:'midnight',cloud:{configured:true},jobs:[],inbox:[{...message,address:'+12025550101',name:'Fictional Sam'}],delay:300};
   window.chat={thread:1,address:'+12025550101',name:'Fictional Sam',base:60,history:[message],smsLatest:message,latest:{key:'sms:60'},hasMore:false,hasOlder:false,draft:null,relationship:profile,attachments:{items:[],enabled:true,sending:false,maxItems:6},replyEligibility:{eligible:false,available:true,total:0,owner:0,incoming:0}};
   window.persona=untrained;
   const eligibility=()=>persona.trained?{eligible:true,available:true,total:persona.trainedMessages,owner:0,incoming:0}:{eligible:false,available:true,total:0,owner:0,incoming:0};
   window.release=(action,result,error=null)=>{const list=held.get(action)||[],item=list.shift();if(!item)throw new Error('No pending '+action);if(!list.length)held.delete(action);nativeResult(item.id,error?null:clone(result===undefined?item.result:result),error);};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={};
    if(action==='snapshot')result=fixture;
    else if(action==='liveInbox')result={inbox:fixture.inbox,inboxComplete:false,contactsAllowed:true,contactPhotoRevision:1};
    else if(action==='conversation')result=chat;
    else if(action==='sendState')result={thread:p.thread,latestBase:chat.base,jobs:fixture.jobs};
    else if(action==='replyProfile')result={thread:1,address:chat.address,name:chat.name,relationship:profile,replyEligibility:eligibility(),persona,readOnly:false,access};
    else if(action==='saveProfile'){if(p.autoSend&&!persona.trained){setTimeout(()=>nativeResult(id,null,'Train Autopilot for this chat before turning it on.'),0);return;}Object.assign(profile,p,{revision:profile.revision+1});result={...profile,replyEligibility:eligibility()};}
    else if(action==='trainPersona'){persona=trained;result={persona,replyEligibility:eligibility()};}
    else if(action==='forgetPersona'){persona=untrained;result={persona,replyEligibility:eligibility()};}
    else if(action==='cacheInbox'||action==='launchInbox')result={inbox:[],hasMore:false,access};
    else if(action==='cacheHistory')result={thread:1,address:chat.address,history:[],hasMore:false,access};
    else if(action==='launchHistories'||action==='prefetchHistory')result={conversations:[],access};
    if(holds.has(action)){if(!held.has(action))held.set(action,[]);held.get(action).push({id,p,result:clone(result)});return;}
    setTimeout(()=>nativeResult(id,clone(result),null),0);
   }};
  });
  const panel=()=>page.locator('#persona-panel');
  await page.goto(url);await page.locator('.row').first().waitFor();
  await page.locator('.row').first().tap();await page.waitForFunction(()=>chatReady());
  await page.locator('#reply-setup').tap();await page.waitForFunction(()=>profileReady());

  phase='untrained chat explains training and sends nothing until asked';
  await panel().waitFor();
  assert.match(await panel().innerText(),/most recent 1,000 texts/);
  assert.match(await panel().innerText(),/Nothing about this chat goes to your AI service until you tap Train Autopilot/);
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='trainPersona').length),0);
  assert.equal(await panel().locator('[data-action="forget-persona"]').count(),0);

  phase='Autopilot cannot be turned on before training';
  await page.locator('input[name="person-mode"][value="autopilot"]').tap();
  assert(await page.locator('input[name="person-mode"][value="off"]').isChecked(),'Autopilot stays off for an untrained chat');

  phase='training shows progress, then what was learned';
  await page.evaluate(()=>holds.add('trainPersona'));
  await panel().locator('[data-action="train-persona"]').tap();
  await page.waitForFunction(()=>held.has('trainPersona'));
  assert.match(await panel().innerText(),/Training Autopilot…/);
  assert.equal(await panel().locator('[data-action="train-persona"]').count(),0,'No second training while one runs');
  const sent=await page.evaluate(()=>calls.find(c=>c.action==='trainPersona').p);
  assert.deepEqual(sent,{thread:1,expectedAddress:'+12025550101'},'The phone reads the texts itself; the page sends only the chat identity');
  await page.evaluate(()=>{holds.delete('trainPersona');release('trainPersona');});
  await page.waitForFunction(()=>/Trained on 42 messages/.test(document.getElementById('persona-panel')?.innerText||''));
  assert.match(await panel().innerText(),/Only 42 messages were available/);
  await panel().locator('.persona-details summary').tap();
  const learned=await panel().locator('.persona-details').innerText();
  for(const text of ['How you text them','Short, lowercase, says "bet".','Fictional close friend.','No emojis.','Your example replies (2)','ya prob','haha bet'])assert(learned.includes(text),text);
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='saveProfile').length),0,'Training never saves or enables Autopilot by itself');

  phase='after training, the owner turns Autopilot on and saves';
  await page.locator('input[name="person-mode"][value="autopilot"]').check();
  await page.locator('[data-action="save-relationship"]').tap();await page.waitForFunction(()=>calls.some(c=>c.action==='saveProfile')&&!busy);
  const saved=await page.evaluate(()=>calls.find(c=>c.action==='saveProfile').p);
  assert.deepEqual([saved.cloudEnabled,saved.autoDraft,saved.autoSend],[true,true,true]);

  phase='a training error is shown and can be retried';
  await page.evaluate(()=>holds.add('trainPersona'));
  await panel().locator('[data-action="train-persona"]').tap();await page.waitForFunction(()=>held.has('trainPersona'));
  await page.evaluate(()=>{holds.delete('trainPersona');release('trainPersona',null,'Fictional connection problem. Try again.');});
  await page.waitForFunction(()=>/Fictional connection problem/.test(document.getElementById('persona-panel')?.innerText||''));
  assert.match(await panel().locator('[data-action="train-persona"]').innerText(),/Retrain/,'An old training stays in place after a failed retrain');

  phase='removing training returns the chat to untrained';
  await panel().locator('[data-action="forget-persona"]').tap();
  await page.waitForFunction(()=>/Train Autopilot/.test(document.querySelector('#persona-panel [data-action="train-persona"]')?.innerText||''));
  assert.equal(await panel().locator('[data-action="forget-persona"]').count(),0);
  assert.equal(await page.evaluate(()=>profileEligibility(relationshipEdit()).eligible),false);

  phase='no whole-phone history preparation remains';
  await page.locator('[data-action="close-setup"]').tap();
  await page.getByRole('button',{name:'Back to conversations',exact:true}).tap();
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Settings',exact:true}).tap();
  assert.equal(await page.locator('#history-learning-panel,#history-learning-notice').count(),0);
  assert.equal(await page.evaluate(()=>calls.filter(c=>/historyLearning/i.test(c.action)).length),0);
  assert.deepEqual(errors,[]);
  console.log('PASS: per-chat Train Autopilot: explicit start, progress, learned summary and examples, Autopilot locked until trained, error retry keeps old training, removal, no whole-phone preparation. Fictional native bridge only.');
 }catch(error){throw new Error(phase+': '+error.message,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
