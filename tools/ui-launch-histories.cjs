const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  const access={readSms:true,defaultSms:true,contacts:true};
  const rows=Array.from({length:20},(_,i)=>({thread_id:i+1,_id:(i+1)*1000+30,kind:'sms',name:`Saved friend ${String(i+1).padStart(2,'0')}`,address:`+1202555${String(1000+i).padStart(4,'0')}`,body:`Saved last text for ${i+1}`,type:1,date:Date.now()-i*60000,read:0}));
  const history=(thread,count=30)=>Array.from({length:count},(_,i)=>({_id:thread*1000+i+1,thread_id:thread,key:`sms:${thread*1000+i+1}`,kind:'sms',date:Date.now()-600000+(i-count)*30000,type:i%2?2:1,read:1,body:`Saved ${i%2?'reply':'incoming'} ${i+1} for conversation ${thread}`}));
  const makeStore=(count=10,messages=30)=>({conversations:rows.slice(0,count).map(row=>({thread:row.thread_id,page:{thread:row.thread_id,address:row.address,name:row.name,readOnly:true,history:history(row.thread_id,messages).map((message,i)=>i===9||i===19?{...message,kind:'mms',key:`mms:${message._id}`,m_type:132,msg_box:1,type:1,body:i===9?'A saved photo caption':'Photo message'}:message),hasOlder:true,hasMore:true}})),savedAt:Date.now()-60000,revision:7,access:{...access}});
  // This external fictional store survives page reloads. It does not assert
  // Android disk/keystore behavior or measure native cold-start performance.
  let savedStore=makeStore(),reads=0;
  await page.exposeFunction('readSavedHistoryFixture',()=>{reads++;return JSON.parse(JSON.stringify(savedStore));});
  await page.addInitScript(({rows,access})=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);const clone=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.historyReplies=[];window.inboxReplies=[];window.snapshotReplies=[];window.chatReplies=[];window.holdChats=true;window.injected=0;
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[],sub:-1,inbox:rows.map(row=>({...row,_id:row.thread_id*1000+35,name:row.name.replace('Saved','Live'),body:`Fresh text for ${row.thread_id}`})),jobs:[],cloud:{configured:false},autoDraft:false,inAppSuggestions:false,linkPreviews:true,theme:'midnight'};
   window.chats=Object.fromEntries(fixture.inbox.map(row=>[row.thread_id,{base:row._id,history:[{...row}],smsLatest:{...row},latest:{kind:'sms',id:row._id,key:`sms:${row._id}`,date:row.date},hasMore:false,hasOlder:false,readOnly:false,draft:null,relationship:{cloudEnabled:false,autoDraft:false,autoSend:false,body:'',samples:''}}]));
   window.releaseHistory=(error=null)=>{const reply=historyReplies.shift();if(!reply)throw new Error('No held launch histories');reply(error);};
   window.releaseInbox=(override=null)=>{const reply=inboxReplies.shift();if(!reply)throw new Error('No held launch inbox');reply(override);};
   window.releaseSnapshot=(override=null,error=null)=>{const reply=snapshotReplies.shift();if(!reply)throw new Error('No held live snapshot');reply(override,error);};
   window.releaseChat=thread=>{const i=chatReplies.findIndex(item=>item.thread===thread);if(i<0)throw new Error('No held chat '+thread);chatReplies.splice(i,1)[0].reply();};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});const finish=(value,error=null)=>nativeResult(id,clone(value),error);
    if(action==='launchHistories'){readSavedHistoryFixture().then(captured=>historyReplies.push(error=>finish(error?null:captured,error)));return;}
    if(action==='launchInbox'){inboxReplies.push(override=>finish(override||{inbox:rows,savedAt:Date.now()-60000,revision:7,access}));return;}
    if(action==='snapshot'){const captured=clone(fixture);snapshotReplies.push((override,error)=>finish(override||captured,error));return;}
    if(action==='conversation'){const captured=clone(chats[p.thread]),reply=()=>finish(captured);if(holdChats)chatReplies.push({thread:p.thread,reply});else setTimeout(reply,0);return;}
    if(action==='saveDraft'){chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};setTimeout(()=>finish({}),0);return;}
    setTimeout(()=>finish(action==='prefetchHistory'?{conversations:[]}:{}),0);
   }};
  },{rows,access});
  const row=thread=>page.locator(`.row[data-thread="${thread}"]`);
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const boot=async()=>{await page.goto(url);await page.waitForFunction(()=>historyReplies.length===1&&inboxReplies.length===1&&snapshotReplies.length===1);};
  const guarded=async()=>{assert.equal(await page.evaluate(()=>chatReady()),false);for(const selector of ['#accept','[data-action=generate]','[data-action=pick-attachments]'])assert(await page.locator(selector).isDisabled(),selector);assert(await page.locator('#draft').isEnabled());};
  const noEffects=async()=>assert.deepEqual(await page.evaluate(()=>calls.filter(call=>['prefetchHistory','saveDraft','historyPage','generate','suggestReply','quick','analyzeMedia','linkPreview','sendNow','sendMms','approve','saveProfile'].includes(call.action))),[],'Saved display pages cannot enable drafting, sending, read side effects, link requests or history paging');

  phase='all three bootstrap reads start independently and saved histories can arrive before inbox rows';
  await boot();assert.deepEqual(await page.evaluate(()=>calls.filter(call=>['launchHistories','launchInbox','snapshot'].includes(call.action)).map(call=>call.action).sort()),['launchHistories','launchInbox','snapshot']);
  await page.evaluate(()=>releaseHistory());await settle();assert.equal(await page.locator('.row').count(),0);await noEffects();
  await page.evaluate(()=>releaseInbox());await row(1).waitFor();
  const paint=await row(1).evaluate(async button=>{const started=performance.now();button.click();await new Promise(requestAnimationFrame);return {ms:performance.now()-started,messages:document.querySelectorAll('.timeline [data-message-key]').length};});
  assert.equal(paint.messages,30,'Saved thirty-message history appears on the first open frame');await guarded();await noEffects();
  assert.equal(await page.locator('.timeline [data-message-key]').first().getAttribute('data-message-key'),'sms:1001');
  assert.equal(await page.locator('.timeline [data-message-key]').last().getAttribute('data-message-key'),'sms:1030');
  assert.equal(await page.locator('.timeline [data-message-kind=mms]').count(),2);assert((await page.locator('.timeline').innerText()).includes('A saved photo caption'));assert.equal(await page.locator('.timeline img,.timeline video,.timeline audio').count(),0);
  assert.equal(await page.locator('.spinner').count(),0);assert.equal(await page.locator('#draft').inputValue(),'');
  for(const viewport of [{width:412,height:915},{width:320,height:470}]){await page.setViewportSize(viewport);await settle();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:`dist/launch-histories-${viewport.width}x${viewport.height}.png`});}
  console.log(`Synthetic saved history: 30 messages visible at first open frame in ${paint.ms.toFixed(1)} ms while live reads remain held.`);

  phase='display-only histories never authorize a send and provisional typing waits for both live reads';
  await page.setViewportSize({width:412,height:915});await page.locator('#draft').fill('My new reply while saved history is visible');
  await page.evaluate(()=>{window.savedEditor=document.querySelector('#draft');savedEditor.setSelectionRange(4,12);releaseChat(1);});await settle();await guarded();await noEffects();
  assert.equal(await page.locator('#draft').inputValue(),'My new reply while saved history is visible');
  await page.evaluate(()=>{holdChats=false;releaseSnapshot();});await page.waitForFunction(()=>chatReady());
  assert(await page.evaluate(()=>document.querySelector('#draft')===savedEditor&&document.activeElement===savedEditor&&savedEditor.selectionStart===4&&savedEditor.selectionEnd===12));
  await page.waitForFunction(()=>calls.some(call=>call.action==='saveDraft'));
  assert((await page.evaluate(()=>calls.filter(call=>call.action==='saveDraft').map(call=>call.p))).every(p=>p.thread===1&&p.base===1035&&p.body==='My new reply while saved history is visible'));
  assert.equal(await page.locator('#accept').isDisabled(),false);

  phase='reloaded UI uses the same external saved store for ten recent chats without persisting draft or authority';
  await boot();await page.evaluate(()=>{releaseInbox();releaseHistory();});await row(1).waitFor();await settle();assert(reads>=2);
  for(let thread=1;thread<=10;thread++){await row(thread).click();await page.locator('#draft').waitFor();assert.equal(await page.locator('.timeline [data-message-key]').count(),30);assert.equal(await page.locator('#draft').inputValue(),'');await guarded();await page.locator('[data-action=back]').click();}
  await row(11).click();await page.locator('#draft').waitFor();assert.equal(await page.locator('.timeline [data-message-key]').count(),1,'Only the ten saved chats have persisted histories');await guarded();
  assert.equal(await page.evaluate(()=>localStorage.length+sessionStorage.length),0);await noEffects();

  phase='native page-finished update before the first live snapshot preserves a pending cold history read';
  await boot();await page.evaluate(()=>releaseInbox());await row(1).waitFor();
  await page.evaluate(()=>{window.startupSignal=onMessagesChanged();releaseHistory();});await settle();
  await row(1).click();await page.locator('#draft').waitFor();assert.equal(await page.locator('.timeline [data-message-key]').count(),30);await guarded();await noEffects();

  phase='provider changes after the first authoritative snapshot still invalidate a late saved-history callback';
  await boot();await page.evaluate(()=>releaseSnapshot());await page.waitForFunction(()=>inboxBootstrap.authoritative);
  await page.evaluate(()=>{window.runtimeSignal=onMessagesChanged();});await page.waitForFunction(()=>snapshotReplies.length===1);
  await page.evaluate(()=>{releaseHistory();releaseInbox();});await settle();await row(1).click();await page.locator('#draft').waitFor();
  assert.equal(await page.locator('.timeline [data-message-key]').count(),1,'A late disk result from before a runtime change cannot refill the preview');assert(!(await page.locator('.timeline').innerText()).includes('Saved incoming'));await guarded();

  phase='saved pages survive the first authoritative inbox refresh until a fresh conversation replaces them';
  await boot();await page.evaluate(()=>{releaseHistory();releaseInbox();});await row(1).waitFor();await settle();
  await page.evaluate(()=>releaseSnapshot());await page.waitForFunction(()=>inboxBootstrap.authoritative);await row(3).click();await page.locator('#draft').waitFor();
  assert.equal(await page.locator('.timeline [data-message-key]').count(),31,'Thirty saved messages remain alongside the newer authoritative inbox text');assert.equal(await page.locator('.timeline [data-message-key]').last().getAttribute('data-message-key'),'sms:3035');assert.equal(await page.evaluate(()=>chatPreviews.get(3).value.history.length),30);await guarded();
  await page.evaluate(()=>releaseChat(3));await page.waitForFunction(()=>chatReady());assert((await page.locator('.timeline').innerText()).includes('Fresh text for 3'));

  phase='a late history cache enriches an already-open provisional chat without replacing the editor';
  await boot();await page.evaluate(()=>releaseInbox());await row(2).click();await page.locator('#draft').fill('Keep my provisional text');
  await page.evaluate(()=>{window.lateEditor=document.querySelector('#draft');lateEditor.setSelectionRange(2,7);releaseHistory();});await settle();
  assert.equal(await page.locator('.timeline [data-message-key]').count(),30);
  assert(await page.evaluate(()=>document.querySelector('#draft')===lateEditor&&document.activeElement===lateEditor&&lateEditor.value==='Keep my provisional text'&&lateEditor.selectionStart===2&&lateEditor.selectionEnd===7));await guarded();await noEffects();

  phase='navigation races never paint another conversation or resurrect a removed thread';
  await boot();await page.evaluate(()=>releaseInbox());await row(1).click();await page.locator('[data-action=back]').click();await row(2).click();await page.locator('#draft').fill('Only for friend two');
  await page.evaluate(()=>{releaseHistory();releaseChat(1);});await settle();assert.equal(await page.evaluate(()=>current.thread_id),2);assert.equal(await page.locator('#draft').inputValue(),'Only for friend two');assert((await page.locator('.timeline').innerText()).includes('conversation 2'));assert(!(await page.locator('.timeline').innerText()).includes('conversation 1'));
  await page.evaluate(()=>releaseSnapshot({...fixture,inbox:[]}));await settle();assert.equal(await page.locator('#draft').count(),0);await page.evaluate(()=>releaseChat(2));await settle();assert.equal(await page.locator('#draft').count(),0);assert.equal(await page.locator('.row').count(),0);

  phase='a fresh live conversation wins over late saved history, including an authoritative empty inbox';
  await boot();await page.evaluate(()=>releaseSnapshot());await row(1).click();await page.evaluate(()=>releaseChat(1));await page.waitForFunction(()=>chatReady());
  await page.evaluate(()=>{releaseHistory();releaseInbox();});await settle();assert((await page.locator('.timeline').innerText()).includes('Fresh text for 1'));assert(!(await page.locator('.timeline').innerText()).includes('Saved incoming'));assert.equal(await page.locator('.timeline [data-message-key]').count(),1);
  await boot();await page.evaluate(()=>releaseSnapshot({...fixture,inbox:[]}));await settle();await page.evaluate(()=>{releaseHistory();releaseInbox();});await settle();assert.equal(await page.locator('.row').count(),0);assert.equal(await page.evaluate(()=>chatPreviews.size),0);

  phase='message-access loss clears loaded histories and rejects callbacks already in flight';
  await boot();await page.evaluate(()=>releaseInbox());await row(1).click();await page.evaluate(()=>onMessageAccessChanged({readSms:false,defaultSms:true,contacts:true}));
  await page.evaluate(()=>{releaseHistory();releaseChat(1);releaseSnapshot();});await settle();assert.equal(await page.locator('#draft').count(),0);assert.equal(await page.locator('.row').count(),0);assert.equal(await page.evaluate(()=>chatPreviews.size),0);await noEffects();
  await boot();await page.evaluate(()=>{releaseHistory();releaseInbox();});await row(1).click();await page.locator('#draft').waitFor();
  await page.evaluate(()=>onMessageAccessChanged({readSms:true,defaultSms:false,contacts:true}));await settle();assert.equal(await page.locator('#draft').count(),0);assert.equal(await page.evaluate(()=>chatPreviews.size+threadHistories.size),0);await noEffects();

  phase='contacts denial cannot restore cached names, and malformed caches never block the live path';
  savedStore={...makeStore(),access:{...access,contacts:false}};await boot();await page.evaluate(()=>releaseInbox({inbox:fixture.inbox,savedAt:Date.now(),revision:7,access:{readSms:true,defaultSms:true,contacts:false}}));await page.evaluate(()=>releaseHistory());await row(1).click();await page.locator('#draft').waitFor();assert.equal(await page.locator('.chat-header h2').innerText(),'+12025551000');await guarded();
  savedStore={conversations:'not-an-array',revision:7,access};await boot();await page.evaluate(()=>{releaseHistory();releaseInbox();});await row(1).click();await page.locator('#draft').waitFor();assert.equal(await page.locator('.timeline [data-message-key]').count(),1);await page.evaluate(()=>{holdChats=false;releaseChat(1);releaseSnapshot();});await page.waitForFunction(()=>chatReady());

  phase='cached payload is bounded and strips any injected approval, profile, media or draft fields';
  savedStore=makeStore(12,36);for(const {page:cached} of savedStore.conversations){cached.base=999999;cached.draft={body:'NEVER RESTORE THIS DRAFT'};cached.relationship={cloudEnabled:true,autoDraft:true,autoSend:true};cached.attachments={items:[{id:'not-real',uri:'https://example.invalid/private'}]};cached.history.at(-1).body='<img src=x onerror="window.injected=1">';cached.history.at(-1).parts=[{url:'https://example.invalid/private',mime:'image/jpeg'}];}
  await boot();await page.evaluate(()=>{releaseHistory();releaseInbox();});await row(1).click();await page.locator('#draft').waitFor();assert.equal(await page.locator('.timeline [data-message-key]').count(),30);assert.equal(await page.locator('#draft').inputValue(),'');assert.equal(await page.evaluate(()=>conversation.base||0),0);assert.equal(await page.locator('.timeline img,[onerror]').count(),0);assert.equal(await page.evaluate(()=>injected),0);assert((await page.locator('.timeline').innerText()).includes('<img src=x'));await guarded();await noEffects();
  await page.locator('[data-action=back]').click();await row(11).click();await page.locator('#draft').waitFor();assert.equal(await page.locator('.timeline [data-message-key]').count(),1);await guarded();
  assert.deepEqual(errors,[]);
  console.log('PASS: parallel persisted-history bootstrap, staged either-order delivery, first-frame30 messages across10 chats, display-only/no-side-effect gating, typed editor preservation, fresh/live and empty snapshot precedence, access/name revocation, corrupt cache fallback, bounded/sanitized pages and 412/320 layouts. Fictional external store and native bridge only; no real disk, SMS, AI or network.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
