const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  const back=async()=>{await page.getByRole('button',{name:'Back to conversations'}).click();await page.locator('#search').waitFor({state:'visible'});await page.waitForFunction(()=>[...draftEdits.values()].every(edit=>edit.obsolete||edit.saved===edit.revision));};
  // Synthetic bridge with real asynchronous acknowledgements; no device or AI calls.
  await page.addInitScript(()=>{
   window.calls=[];window.failSaves=false;window.foreground=true;
   window.fixture={defaultSms:true,permissions:true,exact:true,notifications:true,sub:1,sims:[{id:1,name:'Test SIM'}],inbox:[{thread_id:1,name:'Friend',address:'+12025550147',body:'Coffee?',date:1,type:1},{thread_id:2,name:'Coworker',address:'+12025550148',body:'Hello',date:1,type:1}],jobs:[],tone:'Natural',delay:300,autoDraft:false,matchMyStyle:true,theme:'forest',cloud:{configured:false}};
   const profile={body:'',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300};
   window.chats={1:{base:60,history:Array.from({length:60},(_,i)=>({_id:i+1,type:i%2?1:2,body:i===59?'Coffee?':'Earlier sample message '+i,date:1000+i})),draft:{body:'initial reply',engine:'Test fixture',alternatives:'[]'},relationship:{...profile}},2:{base:70,history:[{_id:70,type:1,body:'Hello',date:1000}],draft:null,relationship:{...profile}}};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p,time:performance.now()});
    setTimeout(()=>{let value={},error=null;
     if(!foreground&&action!=='saveDraft')error='Open Reply Pilot to continue.';
     else if(action==='snapshot')value=fixture;
     else if(action==='conversation')value=chats[p.thread];
     else if(action==='saveDraft'){
      if(failSaves)error='Draft could not be saved.';
      else if(chats[p.thread].base!==p.base)error='A new message arrived.';
      else{chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};fixture.jobs.forEach(j=>{if(j.thread===p.thread&&j.auto_send&&j.status==='scheduled')j.status='cancelled';});}
     }else if(action==='approve')value=null;
     else if(action==='generate')error='No real model in this test.';
     nativeResult(id,JSON.parse(JSON.stringify(value)),error);
    },action==='saveDraft'?35:0);
   }};
  });
  await page.goto((process.env.REPLY_PILOT_URL||'http://127.0.0.1:8769'));await page.locator('.row[data-thread="1"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});
  await page.evaluate(()=>{calls=[];document.querySelector('#draft').focus();window.originalField=document.querySelector('#draft');});
  const text='this is a longer reply that should stay smooth while i type it';
  await page.locator('#draft').fill('');await page.locator('#draft').pressSequentially(text,{delay:3});
  await page.waitForFunction(expected=>chats[1].draft.body===expected,text);
  const saves=await page.evaluate(()=>calls.filter(c=>c.action==='saveDraft').length);
  assert(saves<=3,`Rapid typing must be batched, saw ${saves} writes`);
  assert.equal(await page.locator('#draft').inputValue(),text);
  await page.evaluate(async()=>{document.querySelector('#draft').setSelectionRange(8,8);fixture.inbox[1].body='Unrelated update';fixture.jobs=[{_id:99,thread:2,status:'sent'}];await onNativeResume();});
  assert(await page.evaluate(()=>originalField===document.querySelector('#draft')&&document.activeElement===originalField));
  assert.equal(await page.locator('#draft').evaluate(el=>el.selectionStart),8);
  await page.evaluate(async()=>{const f=document.querySelector('#draft');f.dispatchEvent(new CompositionEvent('compositionstart',{bubbles:true,data:'に'}));f.value+='に';f.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertCompositionText',data:'に',isComposing:true}));fixture.jobs.push({_id:100,thread:2,status:'sent'});await onNativeResume();});
  assert(await page.evaluate(()=>originalField===document.querySelector('#draft')),'Polling must preserve the IME target');
  await page.evaluate(()=>document.querySelector('#draft').dispatchEvent(new CompositionEvent('compositionend',{bubbles:true,data:'に'})));
  await back();
  assert.equal(await page.evaluate(()=>chats[1].draft.body),text+'に','Navigation must flush the final composing text');
  await page.locator('.row[data-thread="2"]').click();await page.locator('#draft').fill('for my coworker');await back();
  await page.locator('.row[data-thread="1"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});assert.equal(await page.locator('#draft').inputValue(),text+'に');
  assert.equal(await page.evaluate(()=>chats[2].draft.body),'for my coworker');
  // Even with batching, the first edit of a running automatic timer is sent immediately.
  await page.evaluate(async()=>{fixture.jobs=[{_id:44,thread:1,base:60,status:'scheduled',auto_send:1,due:Date.now()+300000}];await onNativeResume();calls=[];});
  await page.locator('#draft').fill('stop the automatic timer');
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='saveDraft').length),1,'First cancellation edit must not wait for debounce');
  await page.waitForFunction(()=>fixture.jobs[0].status==='cancelled');
  await page.locator('.scheduled-banner').waitFor({state:'detached'});
  // Pause must enqueue the newest text immediately, including while an older save is in flight.
  await page.evaluate(()=>{const f=document.querySelector('#draft');for(const body of ['pause first','pause latest']){f.value=body;f.dispatchEvent(new InputEvent('input',{bubbles:true}));}foreground=false;onNativePause();});
  await page.waitForFunction(()=>chats[1].draft.body==='pause latest');
  assert.equal(await page.evaluate(async()=>{try{await api('settings',{});return false;}catch{return true;}}),true);
  await page.evaluate(async()=>{foreground=true;await onNativeResume();});
  await page.locator('#draft').fill('the exact latest reply');await page.getByRole('button',{name:'Schedule send',exact:true}).click();await page.locator('[data-action=delay][data-delay="300"]').click();
  await page.waitForFunction(()=>calls.some(c=>c.action==='approve'));
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='approve').at(-1).p.body),'the exact latest reply');
  // Save failure must preserve local wording and prevent dependent sending/redrafting.
  await page.evaluate(()=>{failSaves=true;calls=[];});await page.locator('#draft').fill('keep this edit if saving fails');
  await page.locator('#accept').click();await page.waitForFunction(()=>document.querySelector('#toast').textContent.includes('could not be saved'));
  await page.getByRole('button',{name:'Redraft',exact:true}).click();
  await page.evaluate(async()=>{try{await flushDrafts();}catch{}});
  assert.equal(await page.evaluate(()=>calls.filter(c=>['approve','sendNow','generate'].includes(c.action)).length),0);
  assert.equal(await page.locator('#draft').inputValue(),'keep this edit if saving fails');
  await page.evaluate(()=>{failSaves=false;});await back();
  assert.equal(await page.evaluate(()=>chats[1].draft.body),'keep this edit if saving fails');
  await page.locator('.row[data-thread="1"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});
  // A new incoming base invalidates old writes, keeps owner text, and saves it at the new base.
  await page.evaluate(async()=>{const f=document.querySelector('#draft');f.value='old base edit';f.dispatchEvent(new InputEvent('input',{bubbles:true}));chats[1].base=61;chats[1].history.push({_id:61,type:1,body:'Actually, Friday?',date:99999});chats[1].draft={body:'fresh reply',engine:'Test fixture',alternatives:'[]'};await onNativeResume();});
  await page.waitForFunction(()=>document.querySelector('#draft').value==='old base edit');
  await page.waitForTimeout(300);assert.equal(await page.evaluate(()=>chats[1].draft.body),'old base edit');assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='saveDraft').at(-1).p.base),61);
  await page.evaluate(async()=>{fixture.jobs=[{_id:45,thread:1,status:'sending',auto_send:1}];await onNativeResume();});
  assert(await page.locator('#draft').isDisabled(),'Critical sending state must still update while editing');
  assert(!(await page.locator('body').innerText()).toLowerCase().includes('you have the final say'));
  assert.deepEqual(errors,[]);
  console.log(`PASS: ${text.length} rapid characters used ${saves} draft writes; stable cursor/IME through refresh, timer cancellation, pause/navigation flush, contact isolation, exact approval, save failure and stale-base guards. Simulated bridge; not a Pixel latency benchmark.`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
