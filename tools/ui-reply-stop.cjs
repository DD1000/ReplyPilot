const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  // Every phone, provider, model and carrier operation ends in this local fixture.
  // Keep the actual polling callback available, but never run a real model or SMS.
  await page.addInitScript(()=>{
   const interval=window.setInterval.bind(window);
   window.pollTick=null;
   window.setInterval=(fn,ms,...args)=>{if(ms===3500){pollTick=fn;return 0;}return interval(fn,ms,...args);};
   const copy=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.nextDecision='reply';window.holdDraftSaves=false;window.draftSaves=[];
   window.fixture={defaultSms:true,permissions:true,notifications:true,exact:true,contactsPermission:false,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:true,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:true,url:'https://example.invalid'},jobs:[],inbox:[{thread_id:1,name:'Maya Chen',address:'+12025550147',body:'Ok',date:42000,type:1,read:1}]};
   const silence={decision:'no_reply',reason:'conversation_complete',message:'No reply needed. You can still write a message.'};
   window.chat={replyEligibility:{eligible:true,total:20,owner:10,incoming:10},base:42,history:[{_id:40,type:1,body:'Can you let me know when you get there?',date:40000},{_id:41,type:2,body:'Yes, I’ll let you know when I arrive.',date:41000},{_id:42,type:1,body:'Ok',date:42000}],hasMore:false,draft:null,replyDecision:copy(silence),relationship:{body:'Close friend.',samples:'',cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:300,relationshipKind:'Friend',tone:'Natural'}};
   window.incoming=body=>{
    const id=++chat.base;chat.history.push({_id:id,type:1,body,date:id*1000});chat.draft=null;chat.replyDecision=null;
    Object.assign(fixture.inbox[0],{body,type:1,date:id*1000});
   };
   window.releaseDraftSaves=()=>{holdDraftSaves=false;draftSaves.splice(0).forEach(reply=>reply());};
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});
    const respond=()=>{
     let result={};
     if(action==='snapshot')result=fixture;
     else if(action==='conversation')result=chat;
     else if(action==='generate'){
      if(nextDecision==='no_reply'){chat.draft=null;chat.replyDecision=copy(silence);result={decision:'no_reply',body:'',reason:'conversation_complete'};}
      else{chat.replyDecision=null;chat.draft={body:'Yes, I’m on my way.',engine:'OpenAI fixture',alternatives:'[]'};result={decision:'reply',body:chat.draft.body};}
     }else if(action==='saveDraft'){chat.draft={body:p.body,engine:'Edited by you',alternatives:'[]'};}
     else if(action==='sendNow'){
      const sentId=++chat.base;chat.history.push({_id:sentId,type:2,body:p.body,date:sentId*1000,delivery:'pending'});chat.draft=null;chat.replyDecision=null;
      Object.assign(fixture.inbox[0],{body:p.body,type:2,date:sentId*1000});result={id:sentId,status:'sent'};
     }else if(action==='testOpenAI')result={decision:'no_reply',body:'',reason:'conversation_complete',engine:'OpenAI fixture',elapsedMs:40};
     else if(action==='media')result=[];
     nativeResult(id,copy(result),null);
    };
    if(action==='saveDraft'&&holdDraftSaves)draftSaves.push(respond);else setTimeout(respond,0);
   }};
  });
  const count=action=>page.evaluate(action=>calls.filter(call=>call.action===action).length,action);
  const send=()=>page.getByRole('button',{name:'Send message',exact:true});
  const schedule=()=>page.getByRole('button',{name:'Schedule send',exact:true});
  const back=()=>page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  const open=async()=>{await page.locator('.row[data-thread="1"]').click();await page.locator('#draft').waitFor();};
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const assertSilent=async()=>{
   await page.locator('#reply-decision').waitFor();
   assert.match(await page.locator('#reply-decision').innerText(),/No reply needed/);
   assert.equal(await page.locator('#reply-decision').getAttribute('role'),'status');
   assert.equal(await page.locator('#draft').inputValue(),'');
   assert.equal(await page.locator('#draft').isDisabled(),false,'Choosing silence does not lock manual writing');
   assert(await send().isDisabled());assert(await schedule().isDisabled());
   const toast=await page.locator('#toast').innerText();
   assert.doesNotMatch(toast,/No draft is available|No reply was returned|error|failed|unable|could not/i,'Silence should not become an error toast');
  };
  const assertNoOutgoing=async()=>{
   assert.equal(await count('sendNow'),0);assert.equal(await count('approve'),0);
   assert.equal(await page.evaluate(()=>fixture.jobs.length),0);
   assert.equal(await count('quick'),0,'A no-reply decision must not trigger fallback quick replies');
  };
  await page.goto('http://127.0.0.1:8769');await open();

  phase='a saved acknowledgement decision quietly stops the promise/Ok loop';
  await assertSilent();assert.equal(await count('generate'),0);await assertNoOutgoing();
  // Programmatic activation still cannot dispatch an empty composer.
  await send().evaluate(button=>button.click());await schedule().evaluate(button=>button.click());await assertNoOutgoing();
  await page.screenshot({path:'dist/no-reply-needed-preview.png'});

  phase='reopening, ordinary polling and repeated provider events do not regenerate the same inbound';
  for(let attempt=0;attempt<3;attempt++){await back();await open();await assertSilent();}
  const beforePoll=await count('snapshot');await page.evaluate(()=>pollTick());
  await page.waitForFunction(previous=>calls.filter(call=>call.action==='snapshot').length>previous,beforePoll);
  await page.evaluate(async()=>{await onMessagesChanged();await onNativeResume();await Promise.all(Array.from({length:12},()=>onMessagesChanged()));});await settle();
  await assertSilent();assert.equal(await count('generate'),0);await assertNoOutgoing();

  phase='a fresh incoming question clears the saved decision and can prepare a reply';
  await page.evaluate(async()=>{incoming('Are you still coming over?');nextDecision='reply';await onMessagesChanged();});
  assert.equal(await page.locator('#reply-decision').count(),0,'A previous acknowledgement decision cannot suppress a new message');
  assert.equal(await count('generate'),0,'Provider refresh leaves background generation to native code');
  await back();await open();
  await page.waitForFunction(()=>document.querySelector('#draft')?.value==='Yes, I’m on my way.'&&!document.querySelector('#draft').disabled);
  assert.equal(await count('generate'),1);
  assert.equal(await page.evaluate(()=>calls.find(call=>call.action==='generate').p.base),43);
  assert.equal(await page.evaluate(()=>calls.find(call=>call.action==='generate').p.automatic),true);
  await assertNoOutgoing();

  phase='a newly generated no_reply result stays empty instead of keeping or scheduling a stale promise';
  await page.evaluate(async()=>{incoming('Ok');nextDecision='no_reply';await onMessagesChanged();});
  await back();await open();await page.waitForFunction(()=>calls.filter(call=>call.action==='generate').length===2&&!document.querySelector('#draft')?.disabled);
  await assertSilent();await assertNoOutgoing();
  await back();await open();await page.evaluate(()=>onMessagesChanged());
  assert.equal(await count('generate'),2,'The newly saved no_reply decision must also survive reopening');
  await assertSilent();

  phase='the consecutive-reply limit pauses automation without blocking the composer';
  await page.evaluate(async()=>{chat.replyDecision={decision:'no_reply',reason:'automatic_limit',message:'Five automatic replies in a row. Send a message yourself to resume.'};await onMessagesChanged();});
  await page.waitForFunction(()=>document.querySelector('#reply-decision strong')?.textContent==='Auto-replies paused');
  assert.match(await page.locator('#reply-decision').innerText(),/Five automatic replies in a row/);
  assert.equal(await page.locator('#draft').isDisabled(),false);assert.equal(await page.locator('#draft').inputValue(),'');
  await back();await open();assert.equal(await count('generate'),2,'The saved reply limit also prevents generation on reopening');
  assert.equal(await page.locator('#reply-decision strong').innerText(),'Auto-replies paused');await assertNoOutgoing();

  phase='a keyboard-sized viewport keeps the paused composer and conversation usable';
  await page.setViewportSize({width:412,height:470});await page.locator('#draft').focus();await settle();
  const geometry=await page.evaluate(()=>{
   const rect=selector=>{const box=document.querySelector(selector).getBoundingClientRect();return {top:box.top,bottom:box.bottom,left:box.left,right:box.right,height:box.height};};
   return {height:innerHeight,width:innerWidth,composer:rect('.composer'),send:rect('#accept'),timeline:rect('.timeline')};
  });
  for(const key of ['composer','send']){const box=geometry[key];assert(box.top>=-1&&box.bottom<=geometry.height+1&&box.left>=-1&&box.right<=geometry.width+1,`${key} must stay fully visible: ${JSON.stringify(geometry)}`);}
  assert(geometry.timeline.height>=48&&geometry.timeline.top>=0&&geometry.timeline.bottom<=geometry.composer.top+1,`The conversation must remain visible above the composer: ${JSON.stringify(geometry)}`);
  assert.equal(await page.locator('#draft').isDisabled(),false);await page.locator('#toast.show').waitFor({state:'hidden',timeout:7000});await page.screenshot({path:'dist/no-reply-keyboard-preview.png'});
  await page.setViewportSize({width:412,height:915});await settle();

  phase='a quiet same-base decision update preserves an actively edited manual reply';
  const manual='I also found your charger — I’ll bring it along.';
  await page.evaluate(()=>{holdDraftSaves=true;});await page.locator('#draft').fill(manual);
  await page.evaluate(()=>{window.originalEditor=document.querySelector('#draft');originalEditor.setSelectionRange(7,11);});
  await page.evaluate(async()=>{chat.replyDecision={decision:'no_reply',reason:'repeated_reply',message:'No reply needed. You can still write a message.'};await onMessagesChanged();});
  assert.equal(await page.locator('#draft').inputValue(),manual);
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===originalEditor&&document.activeElement===originalEditor&&originalEditor.selectionStart===7&&originalEditor.selectionEnd===11),true);
  assert.match(await page.locator('#reply-decision').innerText(),/No reply needed/);
  assert.equal(await send().isDisabled(),false);assert.equal(await schedule().isDisabled(),false);
  await assertNoOutgoing();
  await page.evaluate(()=>releaseDraftSaves());

  phase='the user can explicitly send a manual message after an AI silence decision';
  await send().click();await page.waitForFunction(()=>calls.some(call=>call.action==='sendNow'));
  await page.waitForFunction(()=>document.querySelector('#draft')?.value==='');
  assert.equal(await count('sendNow'),1);assert.equal(await count('approve'),0);assert.equal(await count('generate'),2);
  assert.deepEqual(await page.evaluate(()=>calls.find(call=>call.action==='sendNow').p),{thread:1,address:'+12025550147',body:manual,base:44,sub:1});

  phase='Try AI renders no_reply as a successful decision rather than an empty result or error';
  await back();await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Try AI',exact:true}).click();
  await page.locator('#test-message').fill('Ok');await page.getByRole('button',{name:'Generate test reply',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('.test-reply')?.textContent.includes('No reply needed'));
  assert.equal(await page.locator('#test-error').innerText(),'');assert.equal(await page.locator('.test-results .test-reply').count(),1);
  assert.equal(await count('testOpenAI'),1);assert.equal(await count('sendNow'),1);assert.equal(await count('approve'),0);assert.equal(await count('generate'),2);
  assert.equal(await page.locator('.test-results [data-action=approve],.test-results [data-action=send-now]').count(),0);
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(errors,[]);
  console.log('PASS: acknowledgement no_reply decisions stop same-base automatic regeneration through reopening, polling and provider events; new incoming messages can generate; new silence clears stale promises without dispatch; the consecutive-reply cap keeps manual writing available; keyboard-sized layout retains composer and conversation; blank composers remain writable; quiet decision updates preserve editor/selection/manual text; explicit manual sending remains available; Try AI shows No reply needed as success. Fictional bridge only, no SMS or AI.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
