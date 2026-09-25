const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const baseURL=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true,isMobile:true}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  // Every phone call ends here. Replies are released explicitly; there are no
  // actual texts, contacts, models, send timers or live account operations.
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));window.calls=[];window.reads=[];
   window.fixture={defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,inbox:[{thread_id:1,_id:41,name:'Test friend',address:'+12025550147',body:'Coffee tomorrow?',type:1,date:1000},{thread_id:2,_id:51,name:'Second friend',address:'+12025550148',body:'See you soon',type:1,date:900}],jobs:[],cloud:{configured:true},tone:'Natural',delay:300,autoDraft:true,inAppSuggestions:false,matchMyStyle:true,theme:'midnight'};
   window.chats={1:{history:[{_id:41,type:1,body:'Coffee tomorrow?',date:1000}],base:41,hasMore:false,draft:null,replyEligibility:{eligible:true,total:20,owner:10,incoming:10},relationship:{body:'Old friends',samples:'',cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:300,revision:1}},2:{history:[{_id:51,type:1,body:'See you soon',date:900}],base:51,hasMore:false,draft:{body:'Draft for the second friend',engine:'Fixture',alternatives:'[]'},replyEligibility:{eligible:true,total:20,owner:10,incoming:10},relationship:{body:'',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300,revision:1}}};
   window.releaseRead=(thread,error=null)=>{const index=reads.findIndex(read=>read.thread===thread);if(index<0)throw new Error('No pending read for this test thread');const read=reads.splice(index,1)[0];nativeResult(read.id,error?null:clone(chats[thread]),error);};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});if(action==='conversation'){reads.push({id,thread:p.thread});return;}let result={};if(action==='snapshot')result=fixture;else if(action==='saveDraft')chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};setTimeout(()=>nativeResult(id,clone(result),null),0);}};
  });
  const open=async thread=>{await page.locator(`.row[data-thread="${thread}"]`).click();await page.waitForFunction(thread=>reads.some(read=>read.thread===thread),thread);};
  const release=async(thread,error=null)=>page.evaluate(({thread,error})=>releaseRead(thread,error),{thread,error});
  const back=()=>page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  const readCount=()=>page.evaluate(()=>calls.filter(call=>call.action==='conversation').length);
  const noMessaging=async()=>assert.equal(await page.evaluate(()=>calls.some(call=>['generate','suggestReply','analyzeMedia','quick','sendNow','approve','testOpenAI','testNano'].includes(call.action))),false);
  await page.goto(baseURL);await page.locator('.row').first().waitFor();

  phase='immediate history/editor has header and back, then failure remains inline';
  await open(1);assert(await page.getByRole('button',{name:'Back to conversations',exact:true}).isVisible());assert.equal(await page.locator('.chat-person h2').textContent(),'Test friend');assert(await page.locator('#reply-setup').isDisabled());assert(await page.locator('#draft').isEnabled());assert.equal(await page.locator('.conversation .spinner').count(),0);assert.equal(await page.locator('#chat-hydration').count(),1);assert((await page.locator('.timeline').innerText()).includes('Coffee tomorrow?'));assert(await page.locator('#accept').isDisabled());
  const nativeError='Syntax error in regexp near index 3: (?U)\\s+ <img src=x onerror="window.injected=true">';
  await release(1,nativeError);await page.getByRole('button',{name:'Retry',exact:true}).waitFor();assert.equal(await page.locator('.conversation .spinner').count(),0);assert(await page.locator('#draft').isEnabled());assert(await page.locator('#accept').isDisabled());assert(await page.getByRole('button',{name:'Retry',exact:true}).isEnabled());
  assert(!(await page.locator('#chat-hydration').textContent()).includes(nativeError),'Raw native errors stay out of the compact conversation status');assert.equal(await page.locator('#chat-hydration img').count(),0);assert.equal(await page.evaluate(()=>window.injected),undefined);
  const readsAfterError=await readCount();for(let i=0;i<3;i++)await page.evaluate(()=>onMessagesChanged());assert.equal(await readCount(),readsAfterError,'Snapshot/provider refreshes cannot retry or generate for an unloaded chat');await noMessaging();
  for(const viewport of [{width:320,height:470},{width:412,height:915},{width:412,height:470},{width:1100,height:800}]){
   await page.setViewportSize(viewport);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
   const header=await page.locator('.chat-header').boundingBox(),retry=await page.getByRole('button',{name:'Retry',exact:true}).boundingBox();assert(header.y>=0&&retry.y>=header.y+header.height&&retry.y+retry.height<=viewport.height+1,JSON.stringify({viewport,header,retry}));if(viewport.width<900)assert(await page.getByRole('button',{name:'Back to conversations',exact:true}).isVisible());else assert(await page.locator('#conversation-list').isVisible());assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   if(viewport.width<500)await page.screenshot({path:`dist/chat-load-error-${viewport.width}x${viewport.height}.png`});
  }

  phase='explicit Retry reads once and restores an editable chat without automatic generation';
  await page.setViewportSize({width:412,height:470});await page.getByRole('button',{name:'Retry',exact:true}).click();await page.waitForFunction(()=>reads.length===1);assert.equal(await readCount(),readsAfterError+1);assert.equal(await page.locator('[data-action=retry-conversation]').count(),0);assert(await page.getByRole('button',{name:'Back to conversations',exact:true}).isVisible());
  await release(1);await page.locator('#chat-hydration').waitFor({state:'detached'});assert(await page.locator('#draft').isEnabled());assert.equal(await page.locator('#draft').inputValue(),'');assert.equal(await page.locator('.chat-load-state').count(),0);await noMessaging();
  await page.locator('#draft').fill('My own unfinished reply');await page.locator('#draft').focus();await page.evaluate(()=>{window.savedEditor=document.querySelector('#draft');savedEditor.setSelectionRange(7,7);window.providerRefresh=onMessagesChanged();});await page.waitForFunction(()=>reads.length===1);await release(1);await page.evaluate(()=>providerRefresh);
  assert.equal(await page.evaluate(()=>savedEditor===document.querySelector('#draft')&&document.activeElement===savedEditor&&savedEditor.selectionStart===7),true);assert.equal(await page.locator('#draft').inputValue(),'My own unfinished reply');const composer=await page.locator('.composer').boundingBox();assert(composer.y>=0&&composer.y+composer.height<=471);await page.screenshot({path:'dist/chat-load-recovered-keyboard.png'});

  phase='a pending rejection after leaving cannot replace Settings or show a stale error';
  await back();await open(1);await back();await page.locator('[data-action=nav][data-page=settings]').click();await page.locator('#pairing-code').fill('Unsent settings text');await page.locator('#pairing-code').focus();await page.evaluate(()=>{window.savedSetting=document.querySelector('#pairing-code');savedSetting.setSelectionRange(3,3);});
  await release(1,'Stale rejected conversation');await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));assert.equal(await page.locator('[data-action=nav][data-page=settings]').getAttribute('aria-current'),'page');assert.equal(await page.evaluate(()=>savedSetting===document.querySelector('#pairing-code')&&document.activeElement===savedSetting&&savedSetting.selectionStart===3),true);assert.equal(await page.locator('#pairing-code').inputValue(),'Unsent settings text');assert.equal(await page.locator('.chat-load-state').count(),0);assert(!(await page.locator('#toast').textContent()).includes('Stale rejected'));

  phase='a pending rejection while viewing Settings cannot replace its active fields';
  await page.locator('[data-action=nav][data-page=inbox]').click();await page.setViewportSize({width:1100,height:800});await open(1);await page.locator('[data-action=nav][data-page=settings]').click();await page.locator('#pairing-code').fill('Another unsent settings edit');await page.locator('#pairing-code').focus();await page.evaluate(()=>{window.directSetting=document.querySelector('#pairing-code');directSetting.setSelectionRange(4,4);});
  await release(1,'Background read failed');await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));assert.equal(await page.evaluate(()=>directSetting===document.querySelector('#pairing-code')&&document.activeElement===directSetting&&directSetting.selectionStart===4),true);assert.equal(await page.locator('#pairing-code').inputValue(),'Another unsent settings edit');assert(!(await page.locator('#toast').textContent()).includes('Background read failed'));
  await page.locator('[data-action=nav][data-page=inbox]').click();await page.getByRole('button',{name:'Retry',exact:true}).waitFor();assert(await page.getByRole('button',{name:'Retry',exact:true}).isEnabled());await page.setViewportSize({width:412,height:470});await back();

  phase='a superseded success cannot overwrite a later chat or its edited reply';
  await page.locator('[data-action=nav][data-page=inbox]').click();await open(1);await back();await open(2);await release(2);await page.locator('#chat-hydration').waitFor({state:'detached'});await page.locator('#draft').fill('Only for the second friend');await page.locator('#draft').focus();await page.evaluate(()=>{window.secondEditor=document.querySelector('#draft');secondEditor.setSelectionRange(8,8);});await release(1);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  assert.equal(await page.locator('.chat-person h2').textContent(),'Second friend');assert.equal(await page.locator('#draft').inputValue(),'Only for the second friend');assert.equal(await page.evaluate(()=>secondEditor===document.querySelector('#draft')&&document.activeElement===secondEditor&&secondEditor.selectionStart===8),true);assert.equal(await page.locator('.chat-load-state').count(),0);
  await noMessaging();assert.deepEqual(errors,[]);
  console.log('PASS: immediate inbox history and editable composer without spinner; failed reads keep inline Retry with safe generic details; header/back always usable; explicit single read-only Retry; no hidden retries or AI/send actions; recovered draft/focus preserved; stale rejection and superseded success ignored; phone/keyboard/desktop layouts. Fictional native bridge only.');
 }catch(error){console.error('Phase:',phase);throw error;}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
