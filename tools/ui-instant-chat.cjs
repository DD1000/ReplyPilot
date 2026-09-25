const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const baseURL=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  // Every read/write finishes in this fictional bridge. No messages, media,
  // phone permissions, model requests or carrier operations are accessed.
  await page.addInitScript(()=>{
   const interval=window.setInterval;
   window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.chatReplies=[];window.previewReplies=[];window.saveReplies=[];window.generateReplies=[];
   window.holdChats=true;window.holdPreviews=false;window.holdSaves=false;window.holdGenerate=false;
   const now=Date.now();
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:false,matchMyStyle:true,inAppSuggestions:false,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:true},jobs:[],inbox:Array.from({length:40},(_,i)=>({thread_id:i+1,_id:(i+1)*100+4,kind:'sms',name:`Friend ${String(i+1).padStart(2,'0')}`,address:`+1202555${String(1000+i).padStart(4,'0')}`,body:`Newest incoming ${i+1}`,date:now-i*60000,type:1,read:0}))};
   window.chats=Object.fromEntries(fixture.inbox.map(row=>{
    const history=Array.from({length:4},(_,i)=>({_id:row.thread_id*100+i+1,kind:'sms',type:i===1?2:1,body:i===3?row.body:`Earlier message ${row.thread_id}.${i+1}`,date:row.date-(3-i)*1000}));
    return [row.thread_id,{base:row._id,history,hasMore:false,hasOlder:false,before:{date:history[0].date,id:history[0]._id},latest:{key:`sms:${row._id}`,kind:'sms',id:row._id,date:row.date},smsLatest:history[3],draft:{body:`Saved draft ${row.thread_id}`,engine:'Edited by you',alternatives:'[]'},relationship:{body:'Private fictional profile',samples:'',cloudEnabled:true,autoDraft:false,autoSend:false,autoDelay:300},learning:{messageCount:4,sentCount:1,limit:50},readOnly:false}];
   }));
   window.releaseChat=(thread,{error=null,data=null,last=false}={})=>{
    const indexes=chatReplies.map((reply,i)=>reply.thread===thread?i:-1).filter(i=>i>=0);
    const index=last?indexes.at(-1):indexes[0];if(index===undefined)throw new Error(`No held chat ${thread}`);
    chatReplies.splice(index,1)[0].reply(data,error);
   };
   window.releasePreviews=()=>previewReplies.splice(0).forEach(reply=>reply());
   window.releaseSaves=()=>saveReplies.splice(0).forEach(reply=>reply());
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});
    const finish=(result,error=null)=>nativeResult(id,clone(result),error);
    if(action==='snapshot'){setTimeout(()=>finish(fixture),0);return;}
    if(action==='prefetchHistory'){
     const result={conversations:p.threads.filter(thread=>thread!==2&&thread!==3).map(thread=>({thread,page:{history:clone(chats[thread].history),hasOlder:false,hasMore:false,latest:clone(chats[thread].latest),readOnly:false}})),revision:1,access:7};
     const reply=()=>finish(result);if(holdPreviews)previewReplies.push(reply);else setTimeout(reply,0);return;
    }
    if(action==='conversation'){
     const captured=clone(chats[p.thread]);const reply=(data,error)=>finish(data||captured,error);
     if(holdChats)chatReplies.push({thread:p.thread,reply});else setTimeout(()=>reply(),0);return;
    }
    if(action==='saveDraft'){
     const reply=()=>{chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};finish({});};
     if(holdSaves)saveReplies.push(reply);else setTimeout(reply,0);return;
    }
    if(action==='generate'){
     const reply=()=>{chats[p.thread].draft={body:`Fictional generated reply for ${p.thread}`,engine:'Fixture generator',alternatives:'[]'};finish({});};
     if(holdGenerate)generateReplies.push(reply);else setTimeout(reply,0);return;
    }
    if(action==='media'){setTimeout(()=>finish([]),0);return;}
    setTimeout(()=>finish({}),0);
   }};
  });
  const row=thread=>page.locator(`.row[data-thread="${thread}"]`);
  const nav=name=>page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name,exact:true});
  const count=action=>page.evaluate(action=>calls.filter(call=>call.action===action).length,action);
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const currentThread=()=>page.locator('.timeline').getAttribute('data-thread');
  const ready=async()=>{await page.locator('#chat-hydration').waitFor({state:'detached'});await page.waitForFunction(()=>chatReady());};
  const release=async(thread,options)=>{await page.evaluate(({thread,options})=>releaseChat(thread,options),{thread,options});await ready();};
  const back=()=>page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  const blocked=async()=>{
   for(const selector of ['#accept','[data-action="generate"]','#reply-setup','[data-action="pick-attachments"]','[data-action="toggle-timer"]']){
    assert(await page.locator(selector).isDisabled(),`${selector} waits for authoritative context`);
   }
   assert.equal(await page.locator('#draft').isDisabled(),false,'Typing remains available');
   assert.equal(await page.locator('.conversation .spinner').count(),0,'Conversation opening has no spinner');
  };
  await page.goto(baseURL);await row(1).waitFor();

  phase='idle warming is bounded and has no read, draft or send side effects';
  await page.waitForFunction(()=>calls.some(call=>call.action==='prefetchHistory'));
  await page.waitForFunction(()=>chatPreviews.has(1)&&chatPreviews.has(7));
  assert(await page.evaluate(()=>calls.filter(call=>call.action==='prefetchHistory').flatMap(call=>call.p.threads).filter(thread=>thread===2).length===1),'A missing preview is attempted once and does not starve later chats');
  assert.equal(await count('conversation'),0,'Preloading must not use the side-effectful live conversation action');
  for(const action of ['saveDraft','generate','suggestReply','analyzeMedia','sendNow','sendMms','approve'])assert.equal(await count(action),0,action);
  assert(await page.evaluate(()=>calls.filter(call=>call.action==='prefetchHistory').every(call=>call.p.threads.length<=3)));

  phase='cold open paints the actual inbox message before the live read resolves';
  await row(40).scrollIntoViewIfNeeded();
  const opening=await row(40).evaluate(async node=>{const started=performance.now();node.click();await new Promise(resolve=>requestAnimationFrame(resolve));return {elapsedMs:performance.now()-started,thread:document.querySelector('.timeline')?.dataset.thread,editor:!!document.querySelector('#draft')};});
  assert.equal(opening.thread,'40');assert.equal(opening.editor,true,'Chat/editor must exist at the first paint while its provider promise stays unresolved');
  console.log(`Synthetic cold opening: visible at first frame in ${opening.elapsedMs.toFixed(1)} ms (412×915 desktop Chrome, native reply still held).`);
  await page.locator('#draft').waitFor();
  assert.equal(await currentThread(),'40');
  assert((await page.locator('.timeline').innerText()).includes('Newest incoming 40'));
  assert.equal(await page.locator('.bubble-wrap').count(),1,'Cold history starts with the real inbox row');
  await blocked();
  await page.locator('#draft').fill('I can type before the phone finishes reading');
  await page.evaluate(()=>{window.savedEditor=document.querySelector('#draft');savedEditor.setSelectionRange(6,11);});
  await settle();
  assert.equal(await count('saveDraft'),0,'Provisional typing must never save against an inferred message base');
  await blocked();
  await page.evaluate(()=>{document.querySelector('#accept').click();document.querySelector('[data-action="generate"]').click();document.querySelector('#reply-setup').click();});
  assert.equal(await count('sendNow'),0);assert.equal(await count('generate'),0);assert.equal(await count('suggestReply'),0);
  await page.screenshot({path:'dist/instant-chat-pending.png'});
  await release(40);
  assert.equal(await page.locator('.bubble-wrap').count(),4);
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===savedEditor&&document.activeElement===savedEditor&&savedEditor.selectionStart===6&&savedEditor.selectionEnd===11),true,'Hydration preserves the focused editor and selection');
  assert.equal(await page.locator('#draft').inputValue(),'I can type before the phone finishes reading');
  await page.waitForFunction(()=>calls.some(call=>call.action==='saveDraft'&&call.p.thread===40));
  assert.deepEqual(await page.evaluate(()=>calls.find(call=>call.action==='saveDraft').p),{thread:40,base:4004,body:'I can type before the phone finishes reading'});
  assert.equal(await page.locator('#accept').isDisabled(),false);
  await page.screenshot({path:'dist/instant-chat-ready.png'});

  phase='warm opens show full cached history but still require live context';
  await back();await row(1).click();await page.locator('#draft').waitFor();
  assert.equal(await page.locator('.bubble-wrap').count(),4);
  assert((await page.locator('.timeline').innerText()).includes('Earlier message 1.1'));
  await blocked();await release(1);
  assert.equal(await page.locator('#draft').inputValue(),'Saved draft 1');

  phase='pending draft saves never delay back, other chats, Settings or Queue';
  await page.evaluate(()=>{holdSaves=true;});
  await page.locator('#draft').fill('Unfinished owner text stays with friend one');
  await page.waitForFunction(()=>saveReplies.length>0);
  await back();await row(2).click();await page.locator('#draft').waitFor();
  assert.equal(await currentThread(),'2');assert(await page.evaluate(()=>saveReplies.length>0));
  await page.setViewportSize({width:1100,height:800});await nav('Settings').click();await page.getByRole('heading',{name:'Settings',exact:true}).waitFor();
  await page.evaluate(()=>releaseChat(2));await settle();
  assert.equal(await page.getByRole('heading',{name:'Settings',exact:true}).count(),1,'Late hydration must not navigate away from Settings');
  await nav('Queue').click();await page.getByRole('heading',{name:'Your reply queue.',exact:true}).waitFor();
  assert(await page.evaluate(()=>saveReplies.length>0),'Navigation completed while the original save is unresolved');
  await nav('Messages').click();await page.setViewportSize({width:412,height:915});await back();await row(1).click();await page.locator('#draft').waitFor();
  assert.equal(await page.locator('#draft').inputValue(),'Unfinished owner text stays with friend one');
  await release(1);
  assert.equal(await page.locator('#draft').inputValue(),'Unfinished owner text stays with friend one','A stale native saved draft cannot overwrite the pending edit');
  await page.evaluate(()=>{holdSaves=false;releaseSaves();});

  phase='cross-chat late reads cannot replace the visible conversation or its typing';
  await back();await row(3).click();await back();await row(4).click();
  await page.locator('#draft').fill('Only for friend four');
  await page.evaluate(()=>releaseChat(3));await settle();
  assert.equal(await currentThread(),'4');assert.equal(await page.locator('#draft').inputValue(),'Only for friend four');
  await release(4);assert.equal(await page.locator('#draft').inputValue(),'Only for friend four');

  phase='a failed live read retains history and typing, disables actions and supports retry';
  await back();await row(5).click();await page.locator('#draft').fill('Keep this even if the provider is busy');
  await page.evaluate(()=>releaseChat(5,{error:'Fictional provider is busy'}));
  await page.getByRole('button',{name:'Retry',exact:true}).waitFor();
  assert.equal(await page.locator('#draft').inputValue(),'Keep this even if the provider is busy');
  assert((await page.locator('.timeline').innerText()).includes('Newest incoming 5'));await blocked();
  await page.getByRole('button',{name:'Retry',exact:true}).click();
  await release(5);assert.equal(await page.locator('#draft').inputValue(),'Keep this even if the provider is busy');

  phase='reopening the same chat rejects an older in-flight live response';
  await back();await row(6).click();await back();
  await page.evaluate(()=>{chats[6].history.at(-1).body='Newer authoritative version';chats[6].draft.body='Latest saved words';});
  await row(6).click();await release(6,{last:true});
  await page.evaluate(()=>releaseChat(6));await settle();
  assert((await page.locator('.timeline').innerText()).includes('Newer authoritative version'));
  assert.equal(await page.locator('#draft').inputValue(),'Latest saved words');

  phase='another busy chat never prevents opening or typing in a different conversation';
  await page.evaluate(()=>{holdGenerate=true;});
  await page.locator('[data-action="generate"]').click();
  await page.waitForFunction(()=>generateReplies.length===1&&busy&&busyThread===6);
  await back();await row(10).click();await page.locator('#draft').waitFor();
  assert.equal(await currentThread(),'10');assert(await page.locator('#draft').isEnabled());
  await page.locator('#draft').fill('Typing for ten while six is busy');await release(10);
  assert(await page.locator('#draft').isEnabled());assert(await page.locator('#accept').isDisabled());assert(await page.locator('[data-action="generate"]').isDisabled());
  await page.evaluate(()=>{window.busyOtherEditor=document.querySelector('#draft');busyOtherEditor.setSelectionRange(7,7);holdChats=false;holdGenerate=false;generateReplies.shift()();});
  await page.waitForFunction(()=>!busy&&document.querySelector('#accept')?.disabled===false);await settle();
  assert.equal(await currentThread(),'10');assert.equal(await page.locator('#draft').inputValue(),'Typing for ten while six is busy');
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===busyOtherEditor&&document.activeElement===busyOtherEditor&&busyOtherEditor.selectionStart===7),true);

  phase='a redraft waiting for an edit to save never retargets the newly opened chat';
  const generatedBefore=await count('generate');
  await page.evaluate(()=>{holdSaves=true;});
  await page.locator('#draft').fill('Save this for ten before the requested redraft');
  await page.waitForFunction(()=>saveReplies.length>0);
  await page.locator('[data-action="generate"]').click();
  assert.equal(await count('generate'),generatedBefore,'Generation waits for the original edit');
  await back();await row(11).click();await ready();
  assert.equal(await currentThread(),'11');const elevenDraft=await page.locator('#draft').inputValue();
  await page.evaluate(()=>{holdSaves=false;releaseSaves();});
  await settle();await settle();
  assert.equal(await count('generate'),generatedBefore,'The old redraft must cancel after navigation, never target friend eleven');
  assert.equal(await page.locator('#draft').inputValue(),elevenDraft);

  phase='confirmed MMS caption edits cannot resurrect when the SMS base stays unchanged';
  await page.locator('#draft').fill('Caption already accepted with my photo');
  await page.waitForFunction(()=>chats[11].draft?.body==='Caption already accepted with my photo');
  await back();await row(12).click();await ready();
  await page.evaluate(()=>{chats[11].draft=null;chats[11].attachments={items:[],sending:false,lastSend:{id:7701,status:'sent',base:1104,caption:'Caption already accepted with my photo'}};holdChats=true;});
  await back();await row(11).click();await release(11);
  assert.equal(await page.locator('#draft').inputValue(),'','The accepted MMS caption must not be restored from a same-base edit');
  assert.equal(await page.evaluate(()=>draftEdits.has('11:1104')),false);
  assert.equal(await page.evaluate(()=>dirty),false,'A confirmed cleared caption is not an unfinished owner edit');
  await back();await row(12).click();await release(12);
  await page.locator('#draft').fill('Older accepted caption for twelve');
  await page.waitForFunction(()=>chats[12].draft?.body==='Older accepted caption for twelve');
  await back();await page.evaluate(()=>{chats[12].draft=null;chats[12].attachments={items:[],sending:false,lastSend:{id:7702,status:'sent',base:1204,caption:'Older accepted caption for twelve'}};});
  await row(12).click();await page.locator('#draft').fill('New words typed after the accepted photo');await release(12);
  assert.equal(await page.locator('#draft').inputValue(),'New words typed after the accepted photo','A newly typed preview must survive the old MMS outcome');

  phase='display caching stays bounded after many real chat visits';
  await page.evaluate(()=>{holdChats=false;});
  for(let thread=7;thread<=36;thread++){
   await back();await row(thread).click();await ready();
  }
  const memory=await page.evaluate(()=>({count:chatPreviews.size,bytes:[...chatPreviews.values()].reduce((sum,item)=>sum+item.bytes,0),historyCount:threadHistories.size}));
  assert(memory.count<=24,JSON.stringify(memory));assert(memory.bytes<=4*1024*1024,JSON.stringify(memory));
  assert(memory.historyCount<=25,`Inactive loaded histories must be evicted with the bounded display cache: ${JSON.stringify(memory)}`);

  phase='SMS permission loss clears private previews and rejects late warm/live results';
  await back();await page.evaluate(()=>{holdChats=true;holdPreviews=true;invalidatePreviews();queuePreviewWarm();});
  await page.waitForFunction(()=>previewReplies.length>0);await row(37).click();
  await page.locator('#draft').fill('Private provisional words');
  await page.evaluate(()=>{fixture.permissions=false;fixture.defaultSms=false;fixture.inbox=[];onMessageAccessChanged({readSms:false,defaultSms:false,contacts:false});});
  await page.locator('.timeline').waitFor({state:'detached'});
  await page.evaluate(()=>{releaseChat(37);releasePreviews();});await settle();
  const cleared=await page.evaluate(()=>({previews:chatPreviews.size,histories:threadHistories.size,typing:previewDrafts.size,profiles:relationshipEdits.size,drafts:draftEdits.size,text:document.querySelector('#app').textContent}));
  for(const key of ['previews','histories','typing','profiles','drafts'])assert.equal(cleared[key],0,key);
  assert(!cleared.text.includes('Private provisional words'));assert(!cleared.text.includes('Newest incoming'));assert(!cleared.text.includes('Private fictional profile'));
  assert.deepEqual(errors,[]);
  console.log('PASS: cold/warm chats render before delayed live reads without a spinner; provisional editing is buffered and send/AI/profile actions stay gated; hydration preserves editor/caret; pending saves and another busy chat never block navigation/typing; delayed redrafts never retarget; confirmed MMS captions stay cleared while fresh typing survives; draft ownership and late-response isolation survive switches/retries; history cache is bounded; permission revocation clears caches and rejects late replies. Synthetic bridge only, no SMS or AI.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
