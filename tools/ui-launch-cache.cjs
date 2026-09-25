const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const baseURL=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  const makeRows=(count=20,prefix='Saved friend')=>Array.from({length:count},(_,i)=>({thread_id:i+1,_id:100*(i+1)+4,kind:'sms',name:`${prefix} ${String(i+1).padStart(2,'0')}`,address:`+1202555${String(1000+i).padStart(4,'0')}`,body:`Saved message ${i+1}`,type:1,date:Date.now()-i*60000,read:0}));
  const makeCache=(rows=makeRows())=>({inbox:rows,savedAt:Date.now()-30000,revision:7,access:{readSms:true,defaultSms:true,contacts:true}});
  // This host-side object survives page reloads and represents a fictional
  // native cache. It is not Android storage or proof of on-device persistence.
  let launchStore=makeCache(),launchReads=0;
  await page.exposeFunction('readLaunchFixture',()=>{launchReads++;return JSON.parse(JSON.stringify(launchStore));});
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.launchReplies=[];window.snapshotReplies=[];window.chatReplies=[];window.holdChats=true;
   const rows=Array.from({length:24},(_,i)=>({thread_id:i+1,_id:100*(i+1)+9,kind:'sms',name:`Live friend ${String(i+1).padStart(2,'0')}`,address:`+1202555${String(1000+i).padStart(4,'0')}`,body:`Live incoming ${i+1}`,type:1,date:Date.now()-i*60000,read:0}));
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,inbox:rows,jobs:[],cloud:{configured:true},autoDraft:false,inAppSuggestions:false,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight'};
   window.chats=Object.fromEntries(rows.map(row=>[row.thread_id,{base:row._id,history:[{...row}],smsLatest:{...row},latest:{key:`sms:${row._id}`,kind:'sms',id:row._id,date:row.date},hasMore:false,hasOlder:false,readOnly:false,draft:{body:`Fresh saved reply ${row.thread_id}`,engine:'Edited by you',alternatives:'[]'},relationship:{cloudEnabled:true,autoDraft:false,autoSend:false,autoDelay:300,body:'Live private profile',samples:''}}]));
   window.releaseLaunch=(error=null)=>{const reply=launchReplies.shift();if(!reply)throw new Error('No held launch cache');reply(error);};
   window.releaseSnapshot=(replacement=null,error=null)=>{const reply=snapshotReplies.shift();if(!reply)throw new Error('No held snapshot');reply(replacement,error);};
   window.releaseChat=(thread,error=null)=>{const index=chatReplies.findIndex(reply=>reply.thread===thread);if(index<0)throw new Error(`No held chat ${thread}`);chatReplies.splice(index,1)[0].reply(error);};
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});const finish=(value,error=null)=>nativeResult(id,clone(value),error);
    if(action==='launchInbox'){readLaunchFixture().then(captured=>launchReplies.push(error=>finish(error?null:captured,error)));return;}
    if(action==='snapshot'){const captured=clone(fixture);snapshotReplies.push((replacement,error)=>finish(replacement||captured,error));return;}
    if(action==='conversation'){const captured=clone(chats[p.thread]);const reply=error=>finish(captured,error);if(holdChats)chatReplies.push({thread:p.thread,reply});else setTimeout(()=>reply(),0);return;}
    if(action==='saveDraft'){chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};setTimeout(()=>finish({}),0);return;}
    if(action==='prefetchHistory'){setTimeout(()=>finish({conversations:[]}),0);return;}
    setTimeout(()=>finish({}),0);
   }};
  });
  const row=thread=>page.locator(`.row[data-thread="${thread}"]`);
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const boot=async()=>{await page.goto(baseURL);await page.waitForFunction(()=>launchReplies.length===1&&snapshotReplies.length===1);};
  const noEffects=async()=>{
   const actions=await page.evaluate(()=>calls.filter(call=>['prefetchHistory','saveDraft','generate','suggestReply','quick','analyzeMedia','sendNow','sendMms','approve','saveRelationship'].includes(call.action)).map(call=>call.action));
   assert.deepEqual(actions,[],'Display-only launch data cannot enable background work or messaging');
  };
  const pending=async()=>{
   assert.equal(await page.locator('#inbox-bootstrap').count(),1);
   const text=await page.locator('#conversation-list').innerText();
   assert(!text.includes('Your conversations will appear here.'),'Pending reads must not falsely announce an empty inbox');
   assert(!text.includes('Complete Phone setup'),'Unknown permission state must not masquerade as missing setup');
   assert.equal(await page.locator('#conversation-list .spinner').count(),0);
  };
  const guarded=async()=>{
   for(const selector of ['#accept','[data-action="generate"]','[data-action="pick-attachments"]'])assert(await page.locator(selector).isDisabled(),selector);
   assert(await page.locator('#draft').isEnabled());
  };

  phase='bootstrap starts cache and live reads independently without a false empty inbox';
  await boot();await pending();await noEffects();
  assert.deepEqual(await page.evaluate(()=>calls.filter(call=>['launchInbox','snapshot'].includes(call.action)).map(call=>call.action).sort()),['launchInbox','snapshot']);

  phase='twenty persisted preview rows appear while the authoritative snapshot stays pending';
  const paint=await page.evaluate(async()=>{const start=performance.now();releaseLaunch();await new Promise(resolve=>requestAnimationFrame(resolve));return {elapsed:performance.now()-start,rows:document.querySelectorAll('.row').length};});
  assert.equal(paint.rows,20);assert.equal(await row(1).locator('.row-title span').innerText(),'Saved friend 01');
  assert.equal(await row(1).locator('.row-badge').innerText(),'Unread','A saved unread preview must not imply an AI reply is ready');
  assert(!(await page.locator('#conversation-list').innerText()).includes('Ready to review'));
  assert.equal(await page.evaluate(()=>snapshotReplies.length),1);
  await page.waitForTimeout(240);await noEffects();
  for(const viewport of [{width:412,height:915},{width:320,height:470}]){
   await page.setViewportSize(viewport);await settle();
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   assert(await page.getByRole('textbox',{name:'Find a conversation'}).isVisible());
   await page.screenshot({path:`dist/launch-cache-${viewport.width}x${viewport.height}.png`});
  }
  console.log(`Synthetic launch cache: 20 rows at first frame in ${paint.elapsed.toFixed(1)} ms; authoritative snapshot still held.`);

  phase='cached chat is display-only until both live conversation and snapshot are available';
  await page.setViewportSize({width:412,height:915});await row(1).click();await page.locator('#draft').waitFor();
  assert((await page.locator('.timeline').innerText()).includes('Saved message 1'));await guarded();
  await page.locator('#draft').fill('My words while the inbox finishes opening');
  await page.evaluate(()=>{window.launchEditor=document.querySelector('#draft');launchEditor.setSelectionRange(3,8);releaseChat(1);});await settle();
  await guarded();await noEffects();
  assert.equal(await page.locator('#draft').inputValue(),'My words while the inbox finishes opening');
  await page.evaluate(()=>{holdChats=false;releaseSnapshot();});
  await page.waitForFunction(()=>chatReady()&&!document.querySelector('#accept')?.disabled);
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===launchEditor&&document.activeElement===launchEditor&&launchEditor.selectionStart===3&&launchEditor.selectionEnd===8),true);
  assert.equal(await page.locator('#draft').inputValue(),'My words while the inbox finishes opening');
  const saves=await page.evaluate(()=>calls.filter(call=>call.action==='saveDraft').map(call=>call.p));
  assert(saves.length>0);assert(saves.every(save=>save.thread===1&&save.base===109&&save.body==='My words while the inbox finishes opening'));

  phase='a new page can show the same external native cache without retaining old page draft state';
  await boot();await page.evaluate(()=>releaseLaunch());await row(1).waitFor();
  assert.equal(await page.locator('.row').count(),20);assert(launchReads>=2);
  await row(1).click();await page.locator('#draft').waitFor();assert.equal(await page.locator('#draft').inputValue(),'');await guarded();
  assert.equal(await page.evaluate(()=>sessionStorage.length+localStorage.length),0,'Launch cache is supplied by the bridge, not browser storage');

  phase='a late cache cannot overwrite the current live inbox or restore a deleted row';
  await boot();await page.evaluate(()=>releaseSnapshot({...fixture,inbox:[{...fixture.inbox[1],name:'Current authoritative friend'}]}));await row(2).waitFor();
  await page.evaluate(()=>releaseLaunch());await settle();
  assert.equal(await page.locator('.row').count(),1);assert.equal(await row(1).count(),0);assert.equal(await row(2).locator('.row-title span').innerText(),'Current authoritative friend');
  assert.equal(await row(2).locator('.row-badge').innerText(),'Ready to review','Fresh unread rows retain their existing label');
  phase='an authoritative empty inbox wins over a late nonempty launch cache';
  await boot();await page.evaluate(()=>releaseSnapshot({...fixture,inbox:[]}));await settle();
  await page.evaluate(()=>releaseLaunch());await settle();assert.equal(await page.locator('.row').count(),0);

  phase='deletion invalidates a cache-opened chat and its outstanding live read';
  await boot();await page.evaluate(()=>releaseLaunch());await row(1).click();await page.locator('#draft').waitFor();
  await page.locator('#draft').fill('Provisional words for a removed conversation');
  await page.evaluate(()=>releaseSnapshot({...fixture,inbox:[]}));await settle();
  await page.evaluate(()=>releaseChat(1));await settle();
  assert.equal(await page.locator('.timeline').count(),0);assert.equal(await page.locator('.row').count(),0);
  assert.equal(await page.evaluate(()=>calls.some(call=>['saveDraft','generate','suggestReply','sendNow','approve'].includes(call.action))),false);

  phase='access loss invalidates both pending launch and snapshot callbacks';
  await boot();await page.evaluate(()=>onMessageAccessChanged({readSms:false,defaultSms:false,contacts:false}));
  await page.evaluate(()=>{releaseLaunch();releaseSnapshot();});await settle();
  assert.equal(await page.locator('.row').count(),0);assert.equal(await page.locator('.timeline').count(),0);await noEffects();
  phase='denied cache access never displays saved personal rows';
  launchStore={...makeCache(),access:{readSms:false,defaultSms:true,contacts:true}};
  await boot();await page.evaluate(()=>releaseLaunch());await settle();assert.equal(await page.locator('.row').count(),0);await noEffects();

  phase='empty, corrupt or failed cache reads keep an honest pending state until live data arrives';
  for(const candidate of [makeCache([]),null,{inbox:'invalid',access:{readSms:true,defaultSms:true,contacts:true}}]){
   launchStore=candidate;await boot();await page.evaluate(()=>releaseLaunch());await settle();await pending();
   assert.equal(await page.locator('.row').count(),0);await noEffects();
   await page.evaluate(()=>releaseSnapshot());await row(1).waitFor();assert.equal(await row(1).locator('.row-title span').innerText(),'Live friend 01');
  }
  launchStore=makeCache();await boot();await page.evaluate(()=>releaseLaunch('Fictional cache read failed'));await settle();await pending();
  await page.evaluate(()=>releaseSnapshot());await row(1).waitFor();assert.equal(await row(1).locator('.row-title span').innerText(),'Live friend 01');

  phase='only bounded escaped display fields are accepted from launch data';
  const untrustedRows=makeRows(28);untrustedRows[0].name='<img src=x onerror="window.injected=true">';untrustedRows[0].body='<script>window.injected=true</script>';
  untrustedRows[0].draft={body:'Unreviewed cached reply'};untrustedRows[0].relationship={cloudEnabled:true,autoSend:true};
  launchStore={...makeCache(untrustedRows),permissions:true,cloud:{configured:true},jobs:[{thread:1,status:'scheduled'}]};
  await boot();await page.evaluate(()=>releaseLaunch());await row(1).waitFor();assert((await page.locator('.row').count())<=20);
  assert.equal(await page.locator('#conversation-list img,#conversation-list script').count(),0);assert.equal(await page.evaluate(()=>window.injected),undefined);
  await row(1).click();await page.locator('#draft').waitFor();assert.equal(await page.locator('#draft').inputValue(),'');await guarded();await noEffects();
  assert.deepEqual(errors,[]);
  console.log('PASS: parallel cache/live bootstrap; persistent external fixture across page reload; twenty cached display rows with no permission/AI/send assumptions; current snapshot wins including deletion/empty; provisional typing uses fresh base only; late reads cannot revive deleted/access-revoked chats; failed/empty/corrupt cache stays honestly pending; bounded escaped fields; phone and narrow screenshots. Fictional bridge only; no claim of Android disk/keystore validation.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
