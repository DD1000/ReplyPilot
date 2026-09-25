const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.lookupReplies=[];window.confirmReplies=[];window.manualReplies=[];window.snapshotReplies=[];window.holdSnapshot=false;window.injected=0;
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[],sub:-1,inbox:[],jobs:[],cloud:{configured:false},autoDraft:false,inAppSuggestions:false,theme:'midnight',location:{enabled:true,permission:true,precise:true,backgroundPermission:false,fresh:false,refreshing:false,label:'',homeSet:true,homeAddress:'12 Saved Street, Example City',status:'Location is out of date.'}};
   window.results=()=>({requestId:'fictional-search-token',candidates:Array.from({length:12},(_,i)=>({id:`candidate-${i+1}`,address:`${100+i} Example Lane, Example City, EX 12345`,distanceMeters:8+i*12})),accuracyMeters:12,expiresAt:Date.now()+120000,note:'Nearby addresses are estimates. Confirm the correct address, or type it yourself.'});
   window.releaseLookup=(value=results(),error=null)=>{const reply=lookupReplies.shift();if(!reply)throw new Error('No held address search');reply(value,error);};
   window.releaseConfirm=(error=null)=>{const reply=confirmReplies.shift();if(!reply)throw new Error('No held confirmation');reply(error);};
   window.releaseManual=(error=null)=>{const reply=manualReplies.shift();if(!reply)throw new Error('No held typed address');reply(error);};
   window.releaseSnapshot=()=>{const reply=snapshotReplies.shift();if(!reply)throw new Error('No held snapshot');reply();};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});const finish=(value,error=null)=>nativeResult(id,clone(value),error);
    if(action==='snapshot'){const captured=clone(fixture),reply=()=>finish(captured);if(holdSnapshot){holdSnapshot=false;snapshotReplies.push(reply);}else setTimeout(reply,0);return;}
    if(action==='homeCandidates'){lookupReplies.push((value,error)=>{window.lastCandidates=clone(value);finish(value,error);});return;}
    if(action==='confirmHomeCandidate'){confirmReplies.push(error=>{if(error){finish(null,error);return;}const item=lastCandidates.candidates.find(item=>item.id===p.candidateId);if(!item||p.requestId!==lastCandidates.requestId){finish(null,'Expired address search');return;}fixture.location.homeAddress=item.address;fixture.location.homeSet=true;finish(fixture.location);});return;}
    if(action==='setHome'){manualReplies.push(error=>{if(error){finish(null,error);return;}fixture.location.homeAddress=p.address.trim()+', Verified Region';fixture.location.homeSet=true;finish(fixture.location);});return;}
    if(action==='saveLocation'){fixture.location.enabled=p.enabled;setTimeout(()=>finish(fixture.location),0);return;}
    setTimeout(()=>finish(action==='prefetchHistory'?{conversations:[]}:{}),0);
   }};
  });
  const action=name=>page.locator(`[data-action="${name}"]`);
  const dialog=()=>page.locator('#home-candidate-dialog');
  const field=()=>page.locator('#home-address');
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const nav=name=>page.locator(`[data-action=nav][aria-label="${name}"]`);
  const open=async()=>{await action('home-here').click();await dialog().waitFor();await page.waitForFunction(()=>lookupReplies.length===1);};
  const savedCalls=()=>page.evaluate(()=>calls.filter(call=>['setHome','confirmHomeCandidate'].includes(call.action)));
  await page.goto(url);await nav('Settings').click();await field().waitFor();

  phase='current address search is explicit, permits a fresh search without an old location fix, and does not save';
  assert.equal(await field().inputValue(),'12 Saved Street, Example City');assert(await action('home-here').isEnabled());assert.equal((await savedCalls()).length,0);
  assert.equal(await page.evaluate(()=>calls.some(call=>call.action==='homeCandidates')),false);
  await open();assert((await dialog().innerText()).includes('Finding nearby addresses'));assert.equal((await savedCalls()).length,0);
  assert.equal(await page.evaluate(()=>fixture.location.homeAddress),'12 Saved Street, Example City');
  await page.evaluate(()=>releaseLookup());await page.locator('#home-candidate-address').waitFor();
  assert.equal(await page.locator('#home-candidate-count').innerText(),'Address 1 of 10');assert(await action('home-candidate-prev').isDisabled());
  assert.equal(await field().inputValue(),'12 Saved Street, Example City');
  const visited=[];for(let i=0;i<10;i++){visited.push(await page.locator('#home-candidate-address').innerText());if(i<9)await action('home-candidate-next').click();}
  assert.equal(new Set(visited).size,10);assert(await action('home-candidate-next').isDisabled());assert.equal((await savedCalls()).length,0);
  await action('home-candidate-prev').click();assert.equal(await page.locator('#home-candidate-count').innerText(),'Address 9 of 10');

  phase='Yes confirms exactly the displayed candidate and acknowledged address replaces the field';
  await page.evaluate(()=>{holdSnapshot=true;window.oldHomeRead=onMessagesChanged();});await page.waitForFunction(()=>snapshotReplies.length===1);
  await action('home-candidate-confirm').click();await page.waitForFunction(()=>confirmReplies.length===1);
  assert.deepEqual((await savedCalls()).at(-1).p,{requestId:'fictional-search-token',candidateId:'candidate-9'});
  assert(await action('home-candidate-confirm').isDisabled());await action('home-candidate-confirm').evaluate(button=>button.click());assert.equal(await page.evaluate(()=>confirmReplies.length),1);
  assert.equal(await field().inputValue(),'12 Saved Street, Example City');
  await page.evaluate(()=>releaseConfirm());await dialog().waitFor({state:'detached'});
  assert.equal(await field().inputValue(),visited[8]);assert.equal(await page.evaluate(()=>document.activeElement.id),'home-address');
  await page.evaluate(()=>releaseSnapshot());await page.evaluate(()=>oldHomeRead);assert.equal(await field().inputValue(),visited[8],'A read started before confirmation cannot restore the old saved address');
  assert.equal(await page.evaluate(()=>state.location.homeAddress),visited[8]);

  phase='confirmation failure and cancel leave the previous saved home intact';
  const before=await field().inputValue();await open();await page.evaluate(()=>releaseLookup());await action('home-candidate-confirm').waitFor();await action('home-candidate-confirm').click();await page.waitForFunction(()=>confirmReplies.length===1);
  await page.evaluate(()=>releaseConfirm('The home address could not be saved. Try again.'));await page.locator('.home-candidate-error').filter({hasText:'could not be saved'}).waitFor();
  assert.equal(await field().inputValue(),before);assert.equal(await page.evaluate(()=>fixture.location.homeAddress),before);
  await page.keyboard.press('Escape');assert.equal(await dialog().count(),0);assert.equal(await page.evaluate(()=>document.activeElement.dataset.action),'home-here');

  phase='empty or failed search keeps manual entry accessible with normal keyboard focus';
  await open();await page.evaluate(()=>releaseLookup({...results(),candidates:[]}));await action('home-candidate-manual').click();assert.equal(await page.evaluate(()=>document.activeElement.id),'home-address');
  await field().fill('44 Manual Road, Example City');await page.evaluate(()=>{window.homeField=document.querySelector('#home-address');homeField.setSelectionRange(3,8);});await page.evaluate(()=>onMessagesChanged());
  assert(await page.evaluate(()=>document.querySelector('#home-address')===homeField&&document.activeElement===homeField&&homeField.selectionStart===3&&homeField.selectionEnd===8));
  await action('save-home').click();await page.waitForFunction(()=>manualReplies.length===1);await field().fill('55 Newer Unsaved Road');await page.evaluate(()=>releaseManual());await page.waitForFunction(()=>!locationBusy);
  assert.equal(await field().inputValue(),'55 Newer Unsaved Road','An earlier manual save must not overwrite newer typing');
  await action('save-home').click();await page.waitForFunction(()=>manualReplies.length===1);await page.evaluate(()=>releaseManual());await page.waitForFunction(()=>!locationBusy);
  assert.equal(await field().inputValue(),'55 Newer Unsaved Road, Verified Region','The field shows the address acknowledged by native geocoding');
  await open();await page.evaluate(()=>releaseLookup(null,'Nearby address lookup is unavailable.'));await page.locator('.home-candidate-error').filter({hasText:'unavailable'}).waitFor();assert(await action('home-candidate-manual').isEnabled());await action('home-candidate-manual').click();

  phase='late GPS/search responses cannot overwrite manual typing, cancellation or another screen';
  await open();await action('home-candidate-manual').click();await field().fill('66 Keep My Typed Address');await page.evaluate(()=>releaseLookup());await settle();
  assert.equal(await dialog().count(),0);assert.equal(await field().inputValue(),'66 Keep My Typed Address');
  await open();await page.keyboard.press('Escape');await page.evaluate(()=>releaseLookup());await settle();assert.equal(await dialog().count(),0);assert.equal(await field().inputValue(),'66 Keep My Typed Address');
  await open();await nav('Messages').evaluate(button=>button.click());await page.evaluate(()=>releaseLookup());await settle();assert.equal(await dialog().count(),0);assert.equal(await field().count(),0);
  await nav('Settings').click();assert.equal(await field().inputValue(),'66 Keep My Typed Address');
  assert(await page.evaluate(()=>calls.filter(call=>call.action==='cancelHomeCandidates').length>=3));

  phase='untrusted address display is escaped, expired choice cannot confirm, and dialog is keyboard accessible';
  await open();await page.evaluate(()=>releaseLookup({...results(),candidates:[{id:'escaped',address:'123 <img src=x onerror="window.injected=1"> Road',distanceMeters:12}],expiresAt:Date.now()+120000}));
  await page.locator('#home-candidate-address').waitFor();assert.equal(await dialog().locator('img,[onerror]').count(),0);assert((await page.locator('#home-candidate-address').innerText()).includes('<img'));assert.equal(await page.evaluate(()=>injected),0);
  for(let i=0;i<8;i++){await page.keyboard.press('Tab');assert(await page.evaluate(()=>!!document.activeElement.closest('#home-candidate-dialog')));}
  await page.keyboard.press('Escape');await open();await page.evaluate(()=>releaseLookup({...results(),expiresAt:Date.now()-1}));await page.locator('#home-candidate-address').waitFor();assert(await action('home-candidate-confirm').isDisabled());await action('home-candidate-manual').click();

  phase='clean confirmation screenshots at both phone sizes';
  await open();await page.evaluate(()=>releaseLookup({...results(),candidates:results().candidates.slice(0,4)}));await page.locator('#home-candidate-address').waitFor();
  for(const viewport of [{width:412,height:915},{width:320,height:470}]){
   await page.setViewportSize(viewport);await settle();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   const box=await dialog().boundingBox();assert(box.x>=0&&box.y>=0&&box.x+box.width<=viewport.width+.5&&box.y+box.height<=viewport.height+.5);
   await page.screenshot({path:`dist/home-address-${viewport.width}x${viewport.height}.png`});
  }
  await page.keyboard.press('Escape');await page.setViewportSize({width:412,height:915});

  phase='home search never changes sharing opt-in or permissions, and opt-out blocks further GPS requests';
  assert.equal(await page.evaluate(()=>fixture.location.enabled),true);assert.equal(await page.evaluate(()=>fixture.location.permission),true);assert.equal(await page.evaluate(()=>fixture.location.backgroundPermission),false);
  assert.equal(await page.evaluate(()=>calls.some(call=>['requestLocation','locationSettings','refreshLocation'].includes(call.action))),false);
  await page.locator('#location-enabled').uncheck();await page.waitForFunction(()=>!locationBusy);assert(await action('home-here').isDisabled());
  assert.deepEqual(await page.evaluate(()=>calls.filter(call=>['generate','suggestReply','sendNow','approve','saveProfile'].includes(call.action))),[]);
  assert.deepEqual(errors,[]);
  console.log('PASS: explicit fresh address lookup, ten-choice bounded paging, exact-token Yes confirmation, acknowledged field update, stale snapshot guard, failure/cancel preservation, manual focus/save races, late search cancellation/navigation, escaped/expired choices, opt-in preservation and 412/320 layouts. Fictional bridge only; no GPS, geocoding, messages or AI.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
