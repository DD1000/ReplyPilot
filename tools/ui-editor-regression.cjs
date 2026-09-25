const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let page,phase='launch';
 try{
  page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true});const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.route('http://127.0.0.1:18769/**',async route=>{
   const name=new URL(route.request().url()).pathname.slice(1)||'index.html';
   if(!['index.html','app.js','demo.js','app.css','theme.js'].includes(name))return route.fulfill({status:404,body:''});
   const contentType=name.endsWith('.js')?'application/javascript':name.endsWith('.css')?'text/css':'text/html';
   await route.fulfill({contentType,body:await fs.readFile(path.join(__dirname,'../app/src/main/assets',name))});
  });
  await page.addInitScript(()=>{
   const clone=value=>JSON.parse(JSON.stringify(value)),interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   window.calls=[];window.rows=[{thread_id:1,_id:101,name:'Fictional Friend',address:'+12025550101',body:'That was definitely a joke 😂',date:Date.now(),type:1,read:1}];
   window.profile={body:'We are old friends',importantDetails:'We both enjoy games',planHandling:'ask_me',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,engagement:'natural',revision:1};
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[],sub:-1,inbox:rows,inboxComplete:true,jobs:[],cloud:{configured:true},autoDraft:false,inAppSuggestions:false,theme:'midnight',delay:300};
   window.chat={thread:1,address:rows[0].address,name:rows[0].name,base:101,history:[{...rows[0],_id:100,type:2,kind:'sms',body:'Keep the actual conversation separate',date:Date.now()-5000},{...rows[0],kind:'sms',meaningToken:'sms-test'}],hasMore:false,draft:{body:'My actual reply stays safe',engine:'Edited by you',alternatives:'[]'},relationship:profile,profileRevision:1,replyEligibility:{eligible:true,total:20,owner:10,incoming:10},pilotTraining:{count:1,limit:24}};
   window.practice={sessionId:'fictional-session',turnId:'fictional-turn',contactName:'Fictional Friend',scenario:'A fictional game conversation',messages:[{role:'contact',text:'did you beat that level yet?'}],count:1,limit:24,maxTurns:8,turns:0,complete:false};
   const access=()=>({readSms:true,defaultSms:true,contacts:true});
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});setTimeout(()=>{let result={};
    if(action==='snapshot')result=fixture;
    else if(action==='conversation')result=chat;
    else if(action==='replyProfile')result={thread:1,address:rows[0].address,name:rows[0].name,relationship:profile,profileRevision:1,replyEligibility:chat.replyEligibility,pilotTraining:chat.pilotTraining,access:access()};
    else if(action==='launchInbox'||action==='cacheInbox')result={inbox:rows,hasMore:false,cacheOnly:true,readOnly:true,access:access()};
    else if(action==='launchHistories'||action==='prefetchHistory')result={conversations:[],access:access()};
    else if(action==='cacheHistory')result={thread:1,address:rows[0].address,history:[],hasMore:false,cacheOnly:true,readOnly:true,access:access()};
    else if(action==='trainPilotState')result=practice;
    else if(action==='saveDraft')chat.draft={body:p.body,engine:'Edited by you',alternatives:'[]'};
    else if(action==='focusEditor')result={requested:true};
    nativeResult(id,clone(result),null);
   },0);}};
  });
  const note=async(id,text)=>{await page.locator('#'+id).fill(text);await page.evaluate(id=>{window.keptEditor=document.getElementById(id);keptEditor.focus();keptEditor.setSelectionRange(3,9);},id);};
  const identity=async(id,label)=>{const result=await page.evaluate(id=>{const field=document.getElementById(id);return {same:field===keptEditor,connected:keptEditor.isConnected,active:document.activeElement===field,start:field.selectionStart,end:field.selectionEnd};},id);assert.deepEqual(result,{same:true,connected:true,active:true,start:3,end:9},label);};
  await page.goto('http://127.0.0.1:18769/');await page.locator('.row[data-thread="1"]').click();await page.waitForFunction(()=>chatReady());
  phase='composer stays connected across full background renders';await note('draft','Still writing my actual reply, do not replace me');await page.evaluate(()=>{render();render();});await identity('draft',phase);
  phase='relationship context keeps DOM identity and cursor';await page.locator('#reply-setup').click();await note('relationship-context','My unsaved relationship context stays here');await page.evaluate(()=>{render();syncHistoryLearning();render();});await identity('relationship-context',phase);
  phase='important details keep DOM identity and cursor';await note('relationship-important','My unsaved important details stay here');await page.evaluate(()=>{render();render();});await identity('relationship-important',phase);
  phase='input selection without a DOM range does not throw on outside tap';await page.evaluate(()=>{const original=window.getSelection;window.getSelection=()=>({rangeCount:0,toString:()=>'',removeAllRanges(){},getRangeAt(){throw new DOMException('No DOM selection range','IndexSizeError');}});try{document.querySelector('[data-action=close-setup]').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,button:0,pointerId:18,isPrimary:true}));}finally{window.getSelection=original;}});assert.deepEqual(errors,[]);await page.locator('[data-action=close-setup]').click();
  phase='plain message taps and keyboard gestures never open insight';const bubble=page.locator('[data-message-key="sms:101"] .bubble');assert.equal(await page.locator('[data-explain-key],[data-action=explain-meaning]').count(),0);await bubble.click();await bubble.dispatchEvent('keydown',{key:'Enter'});assert.equal(await page.locator('#message-meaning,#pilot-dialog').count(),0);
  phase='long press remains available for native text selection';await bubble.dispatchEvent('pointerdown',{button:0,pointerId:12,isPrimary:true,clientX:100,clientY:100});await page.waitForTimeout(420);await bubble.dispatchEvent('pointerup',{button:0,pointerId:12,isPrimary:true,clientX:100,clientY:100});await bubble.dispatchEvent('click',{button:0});assert.equal(await page.locator('#message-meaning,#pilot-dialog').count(),0);
  phase='native context menu stays unprevented';const menu=await bubble.evaluate(el=>{const event=new MouseEvent('contextmenu',{bubbles:true,cancelable:true,button:2});el.dispatchEvent(event);return event.defaultPrevented;});assert.equal(menu,false);await bubble.dispatchEvent('click',{button:0});assert.equal(await page.locator('#message-meaning').count(),0);
  phase='selected message bubble survives timeline and full renders';await page.evaluate(()=>{window.keptBubble=document.querySelector('[data-message-key="sms:101"] .bubble');const range=document.createRange();range.selectNodeContents(keptBubble);const selected=window.getSelection();selected.removeAllRanges();selected.addRange(range);window.messageSelection=String(selected);window.keptTimeline=document.querySelector('.timeline');updateTimeline();render();});assert(await page.evaluate(()=>keptBubble.isConnected&&document.querySelector('[data-message-key="sms:101"] .bubble')===keptBubble&&String(window.getSelection())===messageSelection));assert(await page.evaluate(()=>document.querySelector('.timeline')===keptTimeline&&keptTimeline.dataset.thread==='1'));
  phase='selection dismissal clears native action mode';await page.evaluate(()=>{window.dismissBefore=calls.filter(c=>c.action==='dismissSelection').length;dismissTextSelection();});assert.equal(await page.evaluate(()=>String(window.getSelection())), '');assert(await page.evaluate(()=>calls.filter(c=>c.action==='dismissSelection').length>dismissBefore));
  phase='practice answer survives modal refresh and full rendering';await page.locator('[data-action=train-pilot]').click();await page.locator('#pilot-reply').waitFor();await note('pilot-reply','My practice answer must keep its native input connection');await page.evaluate(()=>{syncPilotDialog();render();syncPilotDialog();});await identity('pilot-reply',phase);assert.equal(await page.locator('#draft').inputValue(),'Still writing my actual reply, do not replace me');
  phase='typing continues at the preserved selected range';await page.keyboard.type('TEST');assert.equal(await page.locator('#pilot-reply').inputValue(),'My TESTce answer must keep its native input connection');await page.keyboard.press('Escape');await page.locator('#pilot-dialog').waitFor({state:'detached'});
  assert.deepEqual(await page.evaluate(()=>calls.filter(c=>['generate','suggestReply','sendNow','sendMms','approve','testOpenAI','trainPilotStart','trainPilotReply','saveProfile','explainMessage'].includes(c.action))),[],'Fixture never sends, generates, saves profile/insights, or creates a practice turn');assert.deepEqual(errors,[]);
  console.log('PASS: connected composer/profile/practice nodes and selections survive redraws; no insight gestures; native long-press/context-menu remains available; selected bubble preservation, selection dismissal and empty-DOM-range safety. Fictional bridge, no AI or carrier calls.');
 }catch(error){if(page)console.log(await page.evaluate(()=>({text:document.body.innerText.slice(-1000),calls:calls.slice(-8)})));throw new Error(phase+': '+error.message,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
