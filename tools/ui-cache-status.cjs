const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let page,phase='setup';
 try{
  page=await browser.newPage({viewport:{width:412,height:915}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{
   const interval=setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   window.calls=[];window.holdRetry=false;window.pendingRetry=null;
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[],sub:-1,inbox:[],jobs:[],cloud:{configured:true},autoDraft:false,inAppSuggestions:false,theme:'midnight',historyCache:{status:'ready',savedAt:Date.now(),conversations:220,messages:12345,error:''}};
   window.Native={call(id,action,raw){calls.push({action,p:JSON.parse(raw)});const finish=()=>{
    let result={};if(action==='snapshot')result=fixture;
    if(action==='refreshHistoryCache'){fixture.historyCache={...fixture.historyCache,status:'syncing',error:''};result=fixture.historyCache;}
    nativeResult(id,JSON.parse(JSON.stringify(result)),null);
   };if(action==='refreshHistoryCache'&&holdRetry)pendingRetry=finish;else setTimeout(finish,0);}};
  });
  await page.goto(process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769');await page.waitForFunction(()=>calls.some(c=>c.action==='snapshot'));
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Settings',exact:true}).click();
  const panel=page.locator('#history-cache-panel');await panel.waitFor();
  phase='shows actual saved counts';assert.match(await panel.innerText(),/220 conversations · 12,345 messages saved/);assert.match(await panel.innerText(),/Up to date/);
  phase='failed sync retains counts and exposes retry';await page.evaluate(async()=>{fixture.historyCache.status='error';fixture.historyCache.error='Saved history could not finish updating. Your last saved copy is kept.';await onMessagesChanged();});assert.match(await panel.innerText(),/last saved copy is kept/);assert.match(await panel.innerText(),/12,345/);
  phase='retry responds immediately and coalesces taps';await page.evaluate(()=>{holdRetry=true;});await panel.getByRole('button',{name:'Refresh saved history'}).click();assert(await panel.getByRole('button',{name:'Updating history…'}).isDisabled());assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='refreshHistoryCache').length),1);await page.evaluate(()=>{pendingRetry();pendingRetry=null;});assert.match(await panel.innerText(),/Updating your saved history/);
  phase='sync completion updates panel without changing setting being edited';await page.locator('#pairing-code').fill('unsaved pairing edit');await page.evaluate(async()=>{fixture.historyCache={status:'ready',savedAt:Date.now(),conversations:221,messages:12401,error:''};await onMessagesChanged();});assert.match(await panel.innerText(),/221 conversations · 12,401 messages saved/);assert.equal(await page.locator('#pairing-code').inputValue(),'unsaved pairing edit');
  phase='permission loss discards late status';await panel.getByRole('button',{name:'Refresh saved history'}).click();await page.evaluate(()=>onMessageAccessChanged({readSms:false,defaultSms:false,contacts:false}));await page.evaluate(()=>{pendingRetry();pendingRetry=null;});assert.match(await panel.innerText(),/0 conversations · 0 messages saved/);assert(await panel.getByRole('button',{name:'Refresh saved history'}).isDisabled());
  assert.deepEqual(errors,[]);assert.equal(await page.evaluate(()=>calls.some(c=>['sendNow','sendMms','approve','generate','trainPilotStart'].includes(c.action))),false);
  console.log('PASS: saved counts, honest sync errors with last saved copy, immediate/coalesced retry, completion while editing settings, permission-loss late-result guard. Fictional native bridge only.');
 }catch(e){throw new Error(`${phase}: ${e.message}`,{cause:e});}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
