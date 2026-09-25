const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const localAssets=require('./ui-local-assets.cjs');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18771';

// Locked-phone replies: when Android limits Reply Pilot's battery use, Settings and an
// Autopilot chat both offer a one-time "Allow". Once allowed, the prompts go away.
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true}),errors=[];await localAssets(page,url);
  page.on('pageerror',error=>errors.push(error.message));page.setDefaultTimeout(8000);
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value)),access={readSms:true,defaultSms:true,contacts:true};
   window.calls=[];
   const profile={cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:0,body:'',samples:'',importantDetails:'',engagement:'always_reply',planHandling:'delay_answer',revision:1};
   const persona={state:'ready',trained:true,trainedAt:Date.UTC(2026,8,25),trainedMessages:400,newMessages:0,suggestRetrain:false,thin:false,model:'gpt-6-astra',persona:{writingStyle:'Short, lowercase.',relationship:'Fictional friend.',context:'',avoid:'',examples:[]}};
   const message={_id:60,thread_id:1,kind:'sms',date:60000,type:1,body:'Fictional latest message',read:1};
   window.fixture={defaultSms:true,permissions:true,phoneAccess:true,contactsAllowed:true,notifications:true,exact:true,batteryUnrestricted:false,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:true,inAppSuggestions:false,matchMyStyle:true,theme:'midnight',cloud:{configured:true},jobs:[],inbox:[{...message,address:'+12025550101',name:'Fictional Sam'}],delay:300};
   const chat={thread:1,address:'+12025550101',name:'Fictional Sam',base:60,history:[message],smsLatest:message,latest:{key:'sms:60'},hasMore:false,hasOlder:false,draft:null,relationship:profile,attachments:{items:[],enabled:true,sending:false,maxItems:6},replyEligibility:{eligible:true,available:true,total:400,owner:0,incoming:0}};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={};
    if(action==='snapshot')result=fixture;
    else if(action==='liveInbox')result={inbox:fixture.inbox,inboxComplete:false,contactsAllowed:true,contactPhotoRevision:1};
    else if(action==='conversation')result=chat;
    else if(action==='sendState')result={thread:p.thread,latestBase:chat.base,jobs:[]};
    else if(action==='replyProfile')result={thread:1,address:chat.address,name:chat.name,relationship:profile,replyEligibility:chat.replyEligibility,persona,readOnly:false,access};
    else if(action==='cacheInbox'||action==='launchInbox')result={inbox:[],hasMore:false,access};
    else if(action==='cacheHistory')result={thread:1,address:chat.address,history:[],hasMore:false,access};
    else if(action==='launchHistories'||action==='prefetchHistory')result={conversations:[],access};
    else if(action==='media')result=[];
    setTimeout(()=>nativeResult(id,clone(result),null),0);
   }};
  });
  await page.goto(url);await page.locator('.row').first().waitFor();
  const settings=()=>page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Settings',exact:true}).tap();
  const row=page.locator('#battery-setting'),note=page.locator('#battery-note');

  phase='an Autopilot chat warns that replies may wait while locked';
  await page.locator('.row').first().tap();await page.waitForFunction(()=>chatReady());
  await page.locator('#reply-setup').tap();await page.waitForFunction(()=>profileReady());
  assert(await page.locator('input[name="person-mode"][value="autopilot"]').isChecked(),'Fixture chat has Autopilot on');
  await note.waitFor();
  assert.match(await note.innerText(),/Replies may wait while your phone is locked/);
  const box=await note.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=412,'Note fits a phone-width screen');

  phase='the note only shows while Autopilot is selected';
  await page.locator('input[name="person-mode"][value="off"]').check();
  await page.waitForFunction(()=>!document.getElementById('battery-note'));
  await page.locator('input[name="person-mode"][value="autopilot"]').check();await note.waitFor();

  phase='Allow asks Android once from the chat';
  await note.getByRole('button',{name:'Allow',exact:true}).tap();
  await page.waitForFunction(()=>calls.some(call=>call.action==='battery'));

  phase='Settings shows the same choice';
  await page.locator('[data-action="close-setup"]').tap();
  await page.getByRole('button',{name:'Back to conversations',exact:true}).tap();
  await settings();await row.waitFor();
  assert.match(await row.innerText(),/Reply while locked/);
  assert.match(await row.innerText(),/replies can wait until you unlock/);
  await row.getByRole('button',{name:'Allow',exact:true}).tap();
  await page.waitForFunction(()=>calls.filter(call=>call.action==='battery').length===2);

  phase='after Android allows it, Settings says so and nothing asks again';
  await page.evaluate(()=>{fixture.batteryUnrestricted=true;return window.onNativeResume?.();});
  await page.waitForFunction(()=>/Allowed/.test(document.querySelector('#battery-setting button')?.textContent||''));
  assert(await row.getByRole('button',{name:'Allowed',exact:true}).isDisabled());
  assert.match(await row.innerText(),/reply right away while your phone is locked/);

  phase='an older phone build without the flag shows nothing';
  await page.evaluate(()=>{delete fixture.batteryUnrestricted;return window.onNativeResume?.();});
  await page.waitForFunction(()=>!document.getElementById('battery-setting'));
  assert.deepEqual(errors,[]);
  console.log('PASS: locked-phone replies: Autopilot chat note (only while Autopilot is selected), Settings row, one-time Android prompt, Allowed state, hidden without the native flag. Fictional native bridge only.');
 }catch(error){throw new Error(phase+': '+error.message,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
