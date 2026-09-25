const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  let allowApp;const appGate=new Promise(resolve=>allowApp=resolve);let appHeld=true;
  // Native serves the selected theme on the actual root HTML. This route only
  // supplies a fictional native document; it does not model Android disk I/O.
  await page.route(request=>request.pathname==='/',async route=>{
   const response=await route.fetch(),html=await response.text();
   await route.fulfill({response,body:html.replace('data-theme="midnight"','data-theme="forest"')});
  });
  await page.route('**/app.js',async route=>{if(appHeld)await appGate;await route.continue();});
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   localStorage.setItem('reply-pilot-demo-theme','rose');
   window.calls=[];window.snapshotReplies=[];window.themeReplies=[];window.saveReplies=[];window.holdSaves=false;
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[],sub:-1,inbox:[{thread_id:1,_id:10,name:'Alex Rivera',address:'+12025550101',body:'A fictional message',date:Date.now(),type:1}],jobs:[],cloud:{configured:false},autoDraft:false,inAppSuggestions:false,theme:'forest'};
   window.chat={base:10,history:[{_id:10,body:'A fictional message',date:Date.now(),type:1}],hasMore:false,draft:{body:'',engine:'Edited by you',alternatives:'[]'},relationship:{cloudEnabled:false,body:'',samples:''}};
   window.releaseSnapshot=()=>{const reply=snapshotReplies.shift();if(!reply)throw new Error('No held snapshot');reply();};
   window.releaseTheme=(error=null)=>{const reply=themeReplies.shift();if(!reply)throw new Error('No held theme');reply(error);};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});const finish=(value,error=null)=>nativeResult(id,clone(value),error);
    if(action==='snapshot'){const captured=clone(fixture);snapshotReplies.push(()=>finish(captured));return;}
    if(action==='setTheme'){themeReplies.push(error=>{if(!error)fixture.theme=p.theme;finish(error?null:{theme:p.theme},error);});return;}
    if(action==='saveDraft'){const reply=()=>{chat.draft={body:p.body,engine:'Edited by you',alternatives:'[]'};finish({});};if(holdSaves)saveReplies.push(reply);else setTimeout(reply,0);return;}
    setTimeout(()=>finish(action==='conversation'?chat:action==='prefetchHistory'?{conversations:[]}:{}),0);
   }};
  });
  phase='native root theme is applied before the app script or authoritative snapshot';
  await page.goto(url,{waitUntil:'commit'});
  await page.waitForFunction(()=>document.querySelector('link[rel=stylesheet]')?.sheet);
  const early=await page.evaluate(async()=>{const samples=[];for(let i=0;i<4;i++){await new Promise(requestAnimationFrame);samples.push({theme:document.documentElement.dataset.theme,bg:getComputedStyle(document.documentElement).getPropertyValue('--bg').trim()});}return {samples,calls:calls.length,app:document.querySelector('#app').childElementCount};});
  assert(early.samples.every(sample=>sample.theme==='forest'&&sample.bg),'The native-selected theme must be present on every sampled pre-app frame');
  assert.equal(early.calls,0);assert.equal(early.app,0);assert.notEqual(early.samples[0].bg,'#191F2C');
  appHeld=false;allowApp();await page.waitForFunction(()=>snapshotReplies.length===1);
  assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'forest','Initial app render must retain native bootstrap theme');
  await page.evaluate(()=>releaseSnapshot());await page.locator('.row').waitFor();

  phase='theme selection is immediate even with an old draft save and snapshot pending';
  await page.locator('.row').click();await page.locator('#draft').waitFor();
  await page.evaluate(()=>{holdSaves=true;});await page.locator('#draft').fill('Unsent text stays local until its save completes');
  await page.locator('[data-action=back]').click();await page.locator('[data-action=nav][aria-label=Settings]').click();await page.locator('.theme-grid').waitFor();
  await page.waitForFunction(()=>saveReplies.length===1);
  await page.evaluate(()=>{window.oldRefresh=onMessagesChanged();});await page.waitForFunction(()=>snapshotReplies.length===1);
  const immediate=await page.locator('[data-theme-choice=ocean]').evaluate(async button=>{button.click();await new Promise(requestAnimationFrame);return {theme:document.documentElement.dataset.theme,pressed:document.querySelector('[data-theme-choice=ocean]').getAttribute('aria-pressed'),pending:themeReplies.length};});
  assert.equal(immediate.theme,'ocean');assert.equal(immediate.pressed,'true');assert.equal(immediate.pending,1,'Theme save must not wait for draft persistence');
  assert.equal(await page.evaluate(()=>saveReplies.length),1);
  await page.evaluate(()=>releaseSnapshot());await page.waitForTimeout(30);
  assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'ocean','A snapshot begun before the selection cannot undo it');
  await page.evaluate(()=>releaseTheme());await page.evaluate(()=>themeTask);
  assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'ocean');
  assert.deepEqual(await page.evaluate(()=>calls.filter(call=>call.action==='setTheme').map(call=>call.p)),[{theme:'ocean'}]);
  assert.equal(await page.evaluate(()=>calls.some(call=>call.action==='settings')),false,'Theme uses its narrow native save instead of broad settings');
  await page.evaluate(()=>{holdSaves=false;saveReplies.shift()();});

  phase='an authoritative refresh resolving between pointer down and up cannot swallow a theme tap';
  await page.evaluate(()=>{fixture.nano='ready';window.gestureSnapshot=onMessagesChanged();});await page.waitForFunction(()=>snapshotReplies.length===1);
  const target=page.locator('[data-theme-choice=slate] .theme-preview');await target.scrollIntoViewIfNeeded();const targetBox=await target.boundingBox();
  await page.mouse.move(targetBox.x+targetBox.width/2,targetBox.y+targetBox.height/2);await page.mouse.down();
  await page.evaluate(()=>releaseSnapshot());await page.evaluate(()=>gestureSnapshot);await page.mouse.up();
  await page.waitForFunction(()=>themeReplies.length===1);assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'slate');
  await page.evaluate(()=>releaseTheme());await page.evaluate(()=>themeTask);await page.waitForTimeout(100);

  phase='a second pointerdown does not flush the previous gesture deferred render beneath the new target';
  // Deliver two queued pointer gestures in one event turn to cover the narrow
  // handoff before the prior pointer-up release timer runs. Physical touch taps
  // are covered independently below.
  await page.evaluate(()=>{const first=document.querySelector('[data-theme-choice=forest]');first.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerId:81,button:0,isPrimary:true,clientX:20,clientY:20}));fixture.nano='download';window.overlappingThemeRead=onMessagesChanged();});
  await page.waitForFunction(()=>snapshotReplies.length===1);await page.evaluate(()=>releaseSnapshot());await page.evaluate(()=>overlappingThemeRead);
  assert.equal(await page.evaluate(()=>themeRenderPending),true);
  const handoff=await page.evaluate(()=>{const first=document.querySelector('[data-theme-choice=forest]'),next=document.querySelector('[data-theme-choice=ocean]');first.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,pointerId:81,button:0,isPrimary:true}));next.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,pointerId:82,button:0,isPrimary:true,clientX:40,clientY:20}));const connected=next.isConnected,pendingRender=themeRenderPending;next.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,pointerId:82,button:0,isPrimary:true}));next.dispatchEvent(new MouseEvent('click',{bubbles:true,button:0,detail:1}));return {connected,pendingRender};});
  assert.equal(handoff.connected,true,'The new pointer target must not be replaced by an older deferred render');assert.equal(handoff.pendingRender,true);
  await page.waitForFunction(()=>themeReplies.length===1);assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'ocean');await page.evaluate(()=>releaseTheme());await page.evaluate(()=>themeTask);await page.waitForTimeout(100);

  phase='real rapid taps on nested swatches, titles and checkmarks coalesce to the last selected theme';
  const touch=async selector=>{const node=page.locator(selector);await node.scrollIntoViewIfNeeded();const box=await node.boundingBox();assert(box);await page.touchscreen.tap(box.x+box.width/2,box.y+box.height/2);};
  const rapidStart=await page.evaluate(()=>calls.filter(call=>call.action==='setTheme').length);
  await touch('[data-theme-choice=forest] .theme-preview b');await page.waitForFunction(()=>themeReplies.length===1);
  const firstColor=await page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--bg').trim());
  await touch('[data-theme-choice=lavender] > span:last-of-type');
  await touch('[data-theme-choice=sunset] .theme-preview i');
  await touch('[data-theme-choice=rose] .theme-preview');
  await touch('[data-theme-choice=rose] > svg');
  await touch('[data-theme-choice=plum] > span:last-of-type');
  await touch('[data-theme-choice=plum] > svg');
  assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'plum');
  assert.equal(await page.locator('[data-theme-choice=plum]').getAttribute('aria-pressed'),'true');
  assert.notEqual(await page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--bg').trim()),firstColor);
  assert.deepEqual(await page.evaluate(start=>calls.filter(call=>call.action==='setTheme').slice(start).map(call=>call.p.theme),rapidStart),['forest'],'Only the active save is sent while it is pending');
  await page.evaluate(()=>releaseTheme());await page.waitForFunction(()=>themeReplies.length===1);
  assert.deepEqual(await page.evaluate(start=>calls.filter(call=>call.action==='setTheme').slice(start).map(call=>call.p.theme),rapidStart),['forest','plum'],'Unsent intermediate colors are coalesced to the final intent');
  await page.evaluate(()=>releaseTheme());await page.evaluate(()=>themeTask);assert.equal(await page.evaluate(()=>fixture.theme),'plum');

  phase='matching failed save restores the prior acknowledged theme and reports the error';
  await page.locator('[data-theme-choice=rose]').click();assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'rose');
  await page.evaluate(()=>releaseTheme('The color theme could not be saved.'));
  await page.evaluate(()=>themeTask);
  assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'plum');
  assert((await page.locator('#toast').innerText()).includes('could not be saved'));
  await page.locator('[data-theme-choice=mint]').click();await page.waitForFunction(()=>themeReplies.length===1);
  await page.locator('[data-theme-choice=lavender]').click();assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'lavender');
  await page.evaluate(()=>releaseTheme('Earlier theme failed'));await page.waitForFunction(()=>themeReplies.length===1);
  assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'lavender','An earlier failed selection must not undo a later selection');
  await page.evaluate(()=>{window.pendingThemeRead=onMessagesChanged();});await page.waitForFunction(()=>snapshotReplies.length===1);
  await page.evaluate(()=>releaseTheme());await page.evaluate(()=>themeTask);await page.evaluate(()=>releaseSnapshot());await page.waitForTimeout(30);
  assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'lavender');
  for(const viewport of [{width:412,height:915},{width:320,height:470}]){
   await page.setViewportSize(viewport);await page.locator('.theme-panel').scrollIntoViewIfNeeded();
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   await page.evaluate(()=>document.querySelector('#toast').classList.remove('show'));
   await page.screenshot({path:`dist/launch-theme-${viewport.width}x${viewport.height}.png`});
  }
  assert.deepEqual(await page.evaluate(()=>calls.filter(call=>['generate','sendNow','approve','suggestReply','saveProfile','requestLocation','refreshLocation'].includes(call.action))),[]);
  assert.deepEqual(errors,[]);

  phase='browser-only stored theme is applied before app execution and invalid stored values are ignored';
  for(const [saved,expected] of [['mint','mint'],['<img src=x>','midnight']]){
   const demo=await browser.newPage();let release;const gate=new Promise(resolve=>release=resolve);
   await demo.addInitScript(value=>localStorage.setItem('reply-pilot-demo-theme',value),saved);
   await demo.route('**/app.js',async route=>{await gate;await route.continue();});
   await demo.goto(url,{waitUntil:'commit'});await demo.waitForFunction(()=>document.querySelector('link[rel=stylesheet]')?.sheet);
   await demo.evaluate(()=>new Promise(requestAnimationFrame));
   assert.equal(await demo.evaluate(()=>document.documentElement.dataset.theme),expected);
   release();await demo.close();
  }
  console.log('PASS: native theme before app/first sampled paint, conflicting demo storage ignored, optimistic same-frame theme with held draft save, real nested touch taps and last-intent coalescing, stale snapshot guard, acknowledged rollback/error, narrow palette screenshots and browser bootstrap validation. Fictional bridge only; no SMS, GPS or AI.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
