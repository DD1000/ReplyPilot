const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let page,phase='setup';
 try{
  page=await browser.newPage({viewport:{width:412,height:915}});const errors=[];await page.clock.install();page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   window.calls=[];window.snapshots=[];window.chats=[];window.cacheWait=[];
   window.holdSnapshot=true;window.holdChat=true;window.holdCache=true;window.failOlder=false;window.archivePending=false;window.generation='one';
   const rows=Array.from({length:180},(_,i)=>({_id:i+1,thread_id:1,kind:i%2?'mms':'sms',type:i%3?2:1,body:'Message '+(i+1),date:(i+1)*1000,read:1}));
   const person={thread_id:1,address:'+12025550143',name:'Fictional friend'},inbox=[{...rows.at(-1),...person}];
   window.fixture={inbox,defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[],jobs:[],cloud:{configured:false},autoDraft:false,inAppSuggestions:false,theme:'midnight'};
   const access={readSms:true,defaultSms:true,contacts:true};
   const pack=(p,limit,prefix)=>{const eligible=rows.filter(row=>!p.cursor||row.date<p.cursor.date),history=eligible.slice(-limit).map(row=>({...row,body:prefix+row.body}));return {history,hasMore:eligible.length>limit,hasOlder:eligible.length>limit,before:history[0]?{date:history[0].date,kind:history[0].kind,id:history[0]._id}:null};};
   window.release=(name)=>{const tasks=window[name].splice(0);tasks.forEach(fn=>fn());};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});const respond=()=>{
    let result={},error=null;
    if(action==='snapshot')result=fixture;
    else if(action==='launchInbox'||action==='cacheInbox')result={inbox,access,hasMore:false};
    else if(action==='launchHistories'||action==='prefetchHistory')result={conversations:[],access};
    else if(action==='replyProfile')result={...person,thread:1,relationship:{},readOnly:false,access};
    else if(action==='cacheHistory'){
     if(archivePending)result={archivePending:true,thread:1,address:'',name:'',history:[],hasMore:false,before:null,snapshot:'0',savedAt:0,access};
     else if(p.cursor&&failOlder){failOlder=false;error='Temporary archive read failure';}
     else if(p.snapshot&&p.snapshot!==generation)error='Saved conversations updated. Open the latest cached page.';
     else result={...person,thread:1,...pack(p,40,'Saved '),contactFingerprint:'person-one',recipientReadOnly:false,snapshot:generation,access};
    }else if(action==='conversation')result={...person,thread:1,...pack(p,50,'Live '),contactFingerprint:'person-one',base:179,relationship:{},draft:null,readOnly:false};
    else if(action==='historyPage')result={...pack(p,50,'Live ')};
    nativeResult(id,JSON.parse(JSON.stringify(result)),error);
   };if(action==='snapshot'&&holdSnapshot)snapshots.push(respond);else if(action==='conversation'&&holdChat)chats.push(respond);else if(action==='cacheHistory'&&holdCache)cacheWait.push(respond);else setTimeout(respond,0);}};
  });
  const boot=async()=>{await page.goto(process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769');await page.locator('.row').click();await page.locator('#draft').waitFor();};
  phase='live history finishes before saved history and the full inbox';
  await boot();await page.locator('#draft').fill('Keep my typed reply.');
  await page.evaluate(()=>{holdChat=false;release('chats');});
  await page.waitForFunction(()=>liveHistoryReady());
  assert.equal(await page.evaluate(()=>inboxBootstrap.authoritative),false);assert(await page.locator('#accept').isDisabled());
  await page.evaluate(()=>{holdCache=false;release('cacheWait');});
  await page.waitForFunction(()=>!archiveHistories.get(1)?.loading);
  assert.equal(await page.evaluate(()=>conversation.previewOnly===true),false,'late saved data cannot downgrade the live result');
  assert.match(await page.locator('[data-message-key="mms:180"] .bubble').innerText(),/Live Message 180/);
  await page.getByRole('button',{name:'Load older messages',exact:true}).click();
  await page.waitForFunction(()=>historyRecord(1).before.id===81);
  assert.equal(await page.evaluate(()=>inboxBootstrap.authoritative),false,'older live history works independently of full inbox');
  assert(await page.locator('#accept').isDisabled());
  await page.evaluate(()=>{holdSnapshot=false;release('snapshots');});await page.waitForFunction(()=>chatReady());
  assert.equal(await page.locator('#draft').inputValue(),'Keep my typed reply.');assert.equal(await page.locator('#chat-hydration').count(),0);

  phase='failed saved page retains exact boundary for retry';
  await boot();await page.evaluate(()=>{holdCache=false;release('cacheWait');});await page.waitForFunction(()=>historyRecord(1).messages.length===40);
  await page.evaluate(()=>{failOlder=true;loadArchiveHistory(1,true);});await page.getByRole('button',{name:'Retry saved history',exact:true}).waitFor();
  assert.equal(await page.evaluate(()=>archiveHistories.get(1).cursor.id),141);
  await page.getByRole('button',{name:'Retry saved history',exact:true}).click();await page.waitForFunction(()=>historyRecord(1).messages.length===80);
  assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c.action==='cacheHistory'&&c.p.cursor).map(c=>c.p.cursor.id)),[141,141]);
  phase='committed archive change retries once at same boundary';
  await page.evaluate(()=>{generation='two';loadArchiveHistory(1,true);});await page.waitForFunction(()=>historyRecord(1).messages.length===120);
  assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c.action==='cacheHistory').slice(-2).map(c=>[c.p.cursor.id,c.p.snapshot||null])),[[101,'one'],[101,null]]);
  assert.equal(await page.evaluate(()=>archiveHistories.get(1).snapshot),'two');
  assert.equal(await page.locator('.history-error').count(),0);

  phase='first archive rebuild is pending, not a false read error';
  await boot();await page.evaluate(()=>{archivePending=true;holdCache=false;release('cacheWait');});
  await page.getByRole('button',{name:'Check saved history',exact:true}).waitFor();assert.equal(await page.locator('.history-error').count(),0);
  assert.match(await page.locator('.timeline').innerText(),/Saving your chat history on this phone/);
  await page.evaluate(()=>{archivePending=false;onMessagesChanged();});await page.waitForFunction(()=>historyRecord(1).messages.length===40);
  assert.equal(await page.getByRole('button',{name:'Check saved history',exact:true}).count(),0);

  phase='slow live read has a recoverable retry and old answers are ignored';
  await page.clock.fastForward(12050);
  await page.locator('#chat-hydration [data-action=retry-conversation]').waitFor();
  await page.locator('#draft').fill('Still my words.');await page.locator('#chat-hydration [data-action=retry-conversation]').click();
  await page.waitForFunction(()=>chats.length===2);
  await page.evaluate(()=>{holdChat=false;const newest=chats.pop();newest();});await page.waitForFunction(()=>liveHistoryReady());
  await page.evaluate(()=>{release('chats');holdSnapshot=false;release('snapshots');});await page.waitForFunction(()=>chatReady());
  assert.equal(await page.locator('#draft').inputValue(),'Still my words.');assert.equal(await page.locator('#chat-hydration').count(),0);
  await page.screenshot({path:'dist/history-recovery-0.9.11.png'});
  assert(!(await page.evaluate(()=>calls.some(c=>['sendNow','sendMms','approve','generate','suggestReply','trainPilotStart'].includes(c.action)))));
  assert.deepEqual(errors,[]);
  console.log('PASS: late archive cannot downgrade live chat; older live pages work while inbox is pending; archive retry preserves failed boundary; generation refresh rebases once; cold rebuild is pending and refreshes on completion; slow-read retry retains typing and ignores stale results; no sends/AI. Fictional bridge only.');
 }catch(error){if(page){await page.screenshot({path:'dist/history-recovery-failure.png'});console.log(await page.evaluate(()=>({calls:calls.slice(-12),text:document.body.innerText.slice(-1600),current:current?.thread_id,chatLoadPending,chatLoadSlow,previewOnly:conversation?.previewOnly,archive:[...archiveHistories]})));}throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
