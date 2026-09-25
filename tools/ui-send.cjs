const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const localAssets=require('./ui-local-assets.cjs');
const baseURL=process.env.REPLY_PILOT_URL||'http://127.0.0.1:8769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true,isMobile:true}),errors=[],dialogs=[];
  const cdp=await page.context().newCDPSession(page);
  await localAssets(page,baseURL);
  page.on('pageerror',e=>errors.push(e.message));
  page.on('dialog',dialog=>{dialogs.push(dialog.message());dialog.dismiss();});
  // Every phone operation terminates in this fictional in-page bridge. No SMS or AI.
  await page.addInitScript(()=>{
   const interval=window.setInterval;
   window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   window.calls=[];window.holdSaves=false;window.holdSends=false;window.failSaves=false;window.failSend=false;window.sendStatus='sent';
   window.savedReplies=[];window.sentReplies=[];
   window.fixture={historyLearning:{phase:'ready',ready:true},defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:7,name:'Fictional SIM'}],sub:7,inbox:[{thread_id:1,name:'Test friend',address:'+12025550147',body:'Coffee tomorrow?',type:1,date:1000}],jobs:[],tone:'Natural',delay:300,autoDraft:false,matchMyStyle:true,theme:'midnight',cloud:{configured:false}};
   window.chat={history:[{_id:41,type:1,body:'Coffee tomorrow?',date:1000}],hasMore:false,before:{date:1000,id:41},base:41,draft:{body:'sure, what time?',engine:'Local fixture',alternatives:'[]'},relationship:{body:'',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300}};
   window.releaseSaves=()=>savedReplies.splice(0).forEach(reply=>reply());
   window.releaseSends=()=>sentReplies.splice(0).forEach(reply=>reply());
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});
    const reply=()=>{
     let result={},error=null;
     if(action==='snapshot')result=fixture;
     else if(action==='conversation')result=chat;
     else if(action==='saveDraft'){
      if(failSaves)error='Draft could not be saved.';
      else chat.draft={body:p.body,engine:'Edited by you',alternatives:'[]'};
     }else if(action==='sendNow'){
      if(failSend)error='The carrier could not accept this reply. Your draft is saved.';
      else if(sendStatus!=='sent')result={id:42,status:sendStatus,note:'Sending paused. Your draft is saved.'};
      else{
       const nextId=++chat.base;
       chat.history.push({_id:nextId,type:2,body:p.body,date:1000+nextId});
       chat.draft=null;fixture.inbox[0].body=p.body;fixture.inbox[0].type=2;
       result={id:nextId,status:'sent'};
      }
     }else if(action==='approve')result=null; // User declines the separate delayed-send confirmation.
     nativeResult(id,JSON.parse(JSON.stringify(result)),error);
    };
    if(action==='saveDraft'&&holdSaves)savedReplies.push(reply);
    else if(action==='sendNow'&&holdSends)sentReplies.push(reply);
    else setTimeout(reply,0);
   }};
  });
  const send=()=>page.getByRole('button',{name:'Send message',exact:true});
  const schedule=()=>page.getByRole('button',{name:'Schedule send',exact:true});
  const count=action=>page.evaluate(a=>calls.filter(c=>c.action===a).length,action);
  const open=async()=>{await page.goto(baseURL);await page.locator('.row[data-thread="1"]').click();await send().waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});};
  const center=async()=>{const b=await send().boundingBox();return {x:b.x+b.width/2,y:b.y+b.height/2};};
  const sent=async()=>{await page.waitForFunction(()=>calls.some(c=>c.action==='sendNow'));await page.waitForFunction(()=>document.querySelector('#draft')?.value==='');};
  const noSend=async()=>{assert.equal(await count('sendNow'),0);assert.equal(await count('approve'),0);};

  phase='quick send uses the exact current reply once';
  await open();
  const exactBody='sounds good — 6:30?\nI’ll bring coffee ☕';
  await page.locator('#draft').fill(exactBody);await send().click();await sent();
  const firstCall=await page.evaluate(()=>calls.find(c=>c.action==='sendNow').p);
  assert.match(firstCall.requestId,/^[0-9a-f-]{36}$/i);
  const {requestId,...message}=firstCall;
  assert.deepEqual(message,{thread:1,address:'+12025550147',body:exactBody,base:41,sub:7});
  assert.equal(await count('sendNow'),1);assert.equal(await count('approve'),0);
  assert.equal(await page.locator('#timer-menu').count(),0);assert.deepEqual(dialogs,[]);
  assert.equal(firstCall.body,exactBody,'The exact editor text is sent without waiting for draft persistence');

  phase='real touch tap sends while touch hold and cancellation never send';
  await open();await page.locator('#draft').focus();
  await page.screenshot({path:'dist/send-now-preview.png'});
  let touchPoint=await center();
  await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[touchPoint]});
  await page.waitForTimeout(50);await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await sent();
  assert.equal(await count('sendNow'),1);assert.equal(await count('approve'),0);assert.equal(await page.locator('#timer-menu').count(),0);
  await open();await page.locator('#draft').focus();touchPoint=await center();
  await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[touchPoint]});
  await page.locator('#timer-menu').waitFor({state:'visible'});await page.waitForTimeout(1200);
  await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await page.waitForTimeout(100);await noSend();
  await page.screenshot({path:'dist/send-hold-preview.png'});
  await open();touchPoint=await center();
  await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[touchPoint]});
  await cdp.send('Input.dispatchTouchEvent',{type:'touchCancel',touchPoints:[]});
  await page.waitForTimeout(550);await noSend();assert.equal(await page.locator('#timer-menu').count(),0);

  phase='hold opens scheduling and its release never sends';
  await open();let point=await center();await page.mouse.move(point.x,point.y);await page.mouse.down();
  await page.locator('#timer-menu').waitFor({state:'visible'});await page.waitForTimeout(1200);await page.mouse.up();await page.waitForTimeout(80);await noSend();
  assert.deepEqual(await page.locator('[data-action=delay]').allTextContents(),['1 minute','5 minutes']);
  await page.getByRole('button',{name:'Close timer options',exact:true}).click();
  await send().click();await sent();assert.equal(await count('sendNow'),1,'A fresh tap after a hold must still work');

  phase='drag and pointer cancellation suppress both send and hold';
  await open();point=await center();await page.mouse.move(point.x,point.y);await page.mouse.down();
  await page.mouse.move(point.x-30,point.y);await page.mouse.move(point.x,point.y);await page.waitForTimeout(1300);await page.mouse.up();
  await page.waitForTimeout(80);await noSend();assert.equal(await page.locator('#timer-menu').count(),0);
  point=await center();await page.mouse.move(point.x,point.y);await page.mouse.down();
  await send().dispatchEvent('pointercancel',{pointerId:1,pointerType:'mouse',isPrimary:true,bubbles:true});
  await page.waitForTimeout(550);await page.mouse.up();await page.waitForTimeout(80);await noSend();
  assert.equal(await page.locator('#timer-menu').count(),0);
  await send().click();await sent();assert.equal(await count('sendNow'),1,'Cancellation must not disable a later intentional tap');

  phase='blank replies cannot send; pending replies allow manual sending';
  await open();await page.locator('#draft').fill('   ');
  assert(await send().isDisabled());assert(await schedule().isDisabled());
  await send().evaluate(el=>el.click());await schedule().evaluate(el=>el.click());await noSend();
  await page.locator('#draft').fill('keep this reply');
  for(const status of ['scheduled','sending']){
   await open();await page.locator('#draft').fill('keep this reply');
   await page.evaluate(async status=>{fixture.jobs=[{_id:9,thread:1,base:41,body:'queued reply',status,auto_send:0,due:Date.now()+300000}];await onNativeResume();},status);
   assert(await send().isEnabled(),status);
   if(await schedule().count()){assert(await schedule().isDisabled(),status);await schedule().evaluate(el=>el.click());}
   await send().evaluate(el=>el.click());await sent();assert.equal(await count('sendNow'),1);
  }

  phase='Enter and Space activate immediate sending accessibly';
  for(const key of ['Enter','Space']){
   await open();await send().focus();await page.keyboard.press(key);await sent();
   assert.equal(await count('sendNow'),1,key);assert.equal(await count('approve'),0,key);assert.equal(await page.locator('#timer-menu').count(),0,key);
  }
  phase='keyboard scheduling and every timer keep delayed confirmation separate';
  await open();
  for(const seconds of [60,300]){
   phase=`keyboard scheduling preset ${seconds}`;
   await page.waitForFunction(()=>!document.querySelector('[data-action=toggle-timer]')?.disabled);
   await schedule().focus();await page.keyboard.press('Enter');await page.locator('#timer-menu').waitFor();
   assert.equal(await page.evaluate(()=>document.activeElement?.dataset.action),'delay');assert.deepEqual(await page.locator('#timer-menu .timer').allTextContents(),['Instant','1 minute','5 minutes']);
   await page.locator(`[data-action=delay][data-delay="${seconds}"]`).click();
   await page.waitForFunction(n=>calls.some(c=>c.action==='approve'&&c.p.delay===n),seconds);
   await page.locator('#timer-menu').waitFor({state:'detached'});
   assert.equal(await page.locator('#draft').inputValue(),'sure, what time?','Declining confirmation retains the draft');
  }
  assert.equal(await count('approve'),2);assert.equal(await count('sendNow'),0);
  assert(await page.evaluate(()=>calls.filter(c=>c.action==='approve').every(c=>c.p.body==='sure, what time?'&&c.p.base===41)));
  await page.locator('#timer-menu').waitFor({state:'detached'});
  phase='context-menu scheduling accessibility';
  await page.waitForFunction(()=>!document.querySelector('[data-action=toggle-timer]')?.disabled);
  await send().focus();await page.keyboard.press('Shift+F10');await page.locator('#timer-menu').waitFor();
  await page.getByRole('button',{name:'Close timer options',exact:true}).click();
  await send().click({button:'right'});await page.locator('#timer-menu').waitFor();assert.equal(await count('sendNow'),0);

  phase='duplicate activation during save or send has one dispatch';
  await open();await page.evaluate(()=>{holdSaves=true;holdSends=true;});
  await page.locator('#draft').fill('the latest wording, once');
  await page.waitForFunction(()=>savedReplies.length>0);
  await send().evaluate(el=>{el.click();el.click();});
  assert.equal(await count('sendNow'),1,'Sending does not wait for the edit to save');
  await page.evaluate(()=>{holdSaves=false;releaseSaves();});
  await page.waitForFunction(()=>sentReplies.length===1);
  assert(await send().isEnabled(),'Nonblank text keeps Send available during a request');
  await send().evaluate(el=>{el.click();el.click();});await page.waitForTimeout(80);
  assert.equal(await count('sendNow'),1);assert.equal(await count('approve'),0);
  assert.equal(await page.evaluate(()=>calls.find(c=>c.action==='sendNow').p.body),'the latest wording, once');
  await page.evaluate(()=>{holdSends=false;releaseSends();});await sent();assert.equal(await count('sendNow'),1);

  phase='draft-save failures do not block manual sending';
  await open();await page.evaluate(()=>{failSaves=true;});await page.locator('#draft').fill('keep my unsaved words');await send().click();
  await sent();assert.equal(await count('sendNow'),1);
  phase='send errors preserve the draft and allow explicit retry of the same request';
  await open();await page.evaluate(()=>{failSend=true;});await page.locator('#draft').fill('keep my unsaved words');await send().click();
  await page.waitForFunction(()=>document.querySelector('#toast').textContent.includes('carrier could not accept'));
  assert.equal(await page.locator('#draft').inputValue(),'keep my unsaved words');
  await page.waitForFunction(()=>!document.querySelector('#accept').disabled,null,{timeout:3000});
  assert.equal(await count('sendNow'),1);assert.equal(await count('approve'),0);
  await page.evaluate(()=>{failSend=false;});await send().click();await sent();assert.equal(await count('sendNow'),2);
  assert(await page.evaluate(()=>{const attempts=calls.filter(c=>c.action==='sendNow');return attempts[0].p.requestId===attempts[1].p.requestId;}));
  await open();await page.evaluate(()=>{sendStatus='paused';});await send().click();
  await page.waitForFunction(()=>document.querySelector('#toast').textContent.includes('Sending paused'));
  assert.equal(await page.locator('#draft').inputValue(),'sure, what time?');
  await page.waitForFunction(()=>!document.querySelector('#accept').disabled,null,{timeout:3000});
  assert.equal(await count('sendNow'),1);assert.equal(await count('approve'),0);

  assert.deepEqual(dialogs,[],'The immediate send path must not open a confirmation dialog');
  assert.deepEqual(errors,[]);
  console.log('PASS: exact-body UUID quick send without a timer or save wait; real touch tap/hold/cancel; mouse hold, drag and cancel suppression; blank guard and manual sends during pending jobs; Enter/Space send; accessible scheduling and delayed approval; duplicate request coalescing; failed/paused results retain wording and support idempotent explicit retry. Synthetic bridge only, no SMS or AI.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
