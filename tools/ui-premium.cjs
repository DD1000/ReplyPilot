const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const localAssets=require('./ui-local-assets.cjs');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18773';

// "Use Astra for this chat": a per-chat switch that saves at once and explains the cost.
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
   window.persona=trained;window.premium=false;window.failPremium=false;
   const eligibility=()=>persona.trained?{eligible:true,available:true,total:persona.trainedMessages,owner:0,incoming:0}:{eligible:false,available:true,total:0,owner:0,incoming:0};
   window.release=(action,result,error=null)=>{const list=held.get(action)||[],item=list.shift();if(!item)throw new Error('No pending '+action);if(!list.length)held.delete(action);nativeResult(item.id,error?null:clone(result===undefined?item.result:result),error);};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={};
    if(action==='snapshot')result=fixture;
    else if(action==='liveInbox')result={inbox:fixture.inbox,inboxComplete:false,contactsAllowed:true,contactPhotoRevision:1};
    else if(action==='conversation')result=chat;
    else if(action==='sendState')result={thread:p.thread,latestBase:chat.base,jobs:fixture.jobs};
    else if(action==='replyProfile')result={thread:1,address:chat.address,name:chat.name,relationship:profile,replyEligibility:eligibility(),persona,premiumReplies:premium,readOnly:false,access};
    else if(action==='setPremiumReplies'){if(failPremium){setTimeout(()=>nativeResult(id,null,'Fictional save problem.'),0);return;}premium=p.premium;result={premiumReplies:premium};}
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
  const box=()=>page.locator('#premium-replies'),row=page.locator('#premium-setting');
  await page.goto(url);await page.locator('.row').first().waitFor();
  await page.locator('.row').first().tap();await page.waitForFunction(()=>chatReady());
  await page.locator('#reply-setup').tap();await page.waitForFunction(()=>profileReady());

  phase='the switch is off by default and explains the cost';
  await row.waitFor();
  assert.equal(await box().isChecked(),false);
  assert.match(await row.innerText(),/Use Astra for this chat/);assert.match(await row.innerText(),/about 5× the cost/);
  const bounds=await row.boundingBox();assert(bounds&&bounds.x>=0&&bounds.x+bounds.width<=412,'Fits a phone-width screen');

  phase='turning it on saves at once for this chat only';
  await box().tap();
  await page.waitForFunction(()=>document.querySelector('#toast')?.textContent.includes('Astra will write'));
  assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c.action==='setPremiumReplies').map(c=>c.p)),[{thread:1,expectedAddress:'+12025550101',premium:true}]);
  assert.equal(await box().isChecked(),true);
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='saveProfile').length),0,'Does not touch the rest of Reply setup');

  phase='a failed save shows the saved choice again';
  await page.evaluate(()=>{failPremium=true;});await box().tap();
  await page.waitForFunction(()=>document.querySelector('#toast')?.textContent.includes('Fictional save problem'));
  await page.waitForFunction(()=>document.getElementById('premium-replies')?.checked===true);

  phase='turning it off goes back to the standard model';
  await page.evaluate(()=>{failPremium=false;});await box().tap();
  await page.waitForFunction(()=>document.querySelector('#toast')?.textContent.includes('back on the standard model'));
  assert.equal(await box().isChecked(),false);
  assert.deepEqual(errors,[]);
  console.log('PASS: Use Astra for this chat: off by default, explains cost, saves immediately per chat, failed save restores the saved choice, turns back off. Fictional native bridge only.');
 }catch(error){throw new Error(phase+': '+error.message,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
