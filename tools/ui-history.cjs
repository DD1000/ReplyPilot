const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  const assertWindowedHistory=async count=>{
   const expected=Array.from({length:count},(_,i)=>i+1);
   assert.deepEqual(await page.evaluate(()=>historyRecord(1).messages.map(row=>row._id)),expected,'The complete record must survive bounded rendering');
   assert((await page.locator('[data-message-id]').count())<=240,'Timeline DOM must remain bounded');
   if(await page.locator('[data-action="latest-history"]').count())await page.locator('[data-action="latest-history"]').dispatchEvent('click');
   await page.waitForFunction(last=>!!document.querySelector(`[data-message-id="${last}"]`),count);
   const seen=new Set(await page.locator('[data-message-id]').evaluateAll(nodes=>nodes.map(node=>Number(node.dataset.messageId))));
   // This message belongs to both windows: changing the rendered batch must
   // retain its visible position and the actual draft editor/caret.
   await page.locator('[data-message-id="100"]').evaluate(node=>{node.scrollIntoView({block:'center'});const timeline=document.querySelector('.timeline');window.windowAnchor={offset:node.getBoundingClientRect().top-timeline.getBoundingClientRect().top,editor:document.querySelector('#draft'),caret:document.querySelector('#draft').selectionStart};});
   await page.locator('[data-action="load-older"]').dispatchEvent('click');
   await page.waitForFunction(()=>!!document.querySelector('[data-message-id="1"]'));
   assert((await page.locator('[data-message-id]').count())<=240);
   for(const id of await page.locator('[data-message-id]').evaluateAll(nodes=>nodes.map(node=>Number(node.dataset.messageId))))seen.add(id);
   const assertAnchor=async()=>assert(await page.evaluate(()=>{const node=document.querySelector('[data-message-id="100"]'),timeline=document.querySelector('.timeline'),editor=document.querySelector('#draft');return editor===windowAnchor.editor&&editor.selectionStart===windowAnchor.caret&&Math.abs(node.getBoundingClientRect().top-timeline.getBoundingClientRect().top-windowAnchor.offset)<2;}),'Window navigation must preserve the overlapping message anchor and editor/caret');
   await assertAnchor();
   await page.getByRole('button',{name:'Newer messages',exact:true}).dispatchEvent('click');
   await page.waitForFunction(last=>!!document.querySelector(`[data-message-id="${last}"]`),count);
   await assertAnchor();
   assert((await page.locator('[data-message-id]').count())<=240);
   assert.deepEqual([...seen].sort((a,b)=>a-b),expected,'Earlier/newer controls must make every loaded message accessible');
  };
  await page.addInitScript(()=>{
   // Local fixture only: no SMS provider, network request or model is involved.
   const interval=window.setInterval.bind(window);window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   window.calls=[];window.holdHistory=false;window.failHistory=false;window.historyResponses=[];window.holdConversationThread=0;window.conversationResponses=[];
   const make=(count,start=0)=>Array.from({length:count},(_,i)=>({_id:start+i+1,type:i%2?2:1,body:`Text ${start+i+1}: a message from this conversation.`,date:100000+Math.floor(i/7)*1000}));
   window.sms={1:make(173),2:make(5,500),3:make(171,1000)};
   window.drafts={1:'current reply',2:'second contact reply',3:'third contact reply'};
   const profile={body:'',samples:'',cloudEnabled:true,autoDraft:false,autoSend:false,autoDelay:300,relationshipKind:'Friend',tone:'Natural',revision:0};
   window.fixture={defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:1,name:'Test SIM'}],sub:1,autoDraft:true,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:true},jobs:[],inbox:[1,2,3].map(thread=>({thread_id:thread,name:`Contact ${thread}`,address:`+1202555014${thread}`,body:'Recent text',date:200000,type:1}))};
   const pack=messages=>({history:messages.slice(-50),hasMore:messages.length>50,before:messages.length?{date:messages.slice(-50)[0].date,id:messages.slice(-50)[0]._id}:null});
   const conversation=thread=>({...pack(sms[thread]),base:sms[thread].at(-1)?._id||0,relationship:profile,draft:{body:drafts[thread],engine:'Fixture reply',alternatives:'[]'},learning:{messageCount:Math.min(50,sms[thread].length),sentCount:sms[thread].slice(-50).filter(m=>m.type===2).length,limit:50}});
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});let result={},error=null;
    if(action==='snapshot')result=fixture;
    if(action==='conversation')result=conversation(p.thread);
    if(action==='historyPage'){
     if(failHistory){failHistory=false;error='The SMS history is temporarily unavailable.';}
     else{const earlier=sms[p.thread].filter(m=>m.date<p.beforeDate||m.date===p.beforeDate&&m._id<p.beforeId);result=pack(earlier);
      // A duplicate from an overlapping provider page must not duplicate its bubble.
      const overlap=sms[p.thread].find(m=>m._id===p.beforeId);if(overlap)result.history.push(overlap);
     }
    }
    if(action==='saveDraft'){if(p.base!==sms[p.thread].at(-1)._id)error='A new message arrived.';else drafts[p.thread]=p.body;}
    if(action==='generate')drafts[p.thread]='newly generated fixture reply';
    const safe=JSON.parse(JSON.stringify(result)),respond=()=>nativeResult(id,safe,error);
    if(action==='historyPage'&&holdHistory)historyResponses.push(respond);
    else if(action==='conversation'&&p.thread===holdConversationThread)conversationResponses.push(respond);
    else setTimeout(respond,0);
   }};
  });
  await page.goto((process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769'));await page.locator('.row[data-thread="1"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});
  assert.equal(await page.locator('[data-message-id]').count(),50);
  await page.locator('#reply-setup').click();assert.match(await page.locator('#history-learning').textContent(),/Recent 50 messages ready · 25 sent by you/);await page.screenshot({path:'dist/history-context-preview.png'});
  await page.getByRole('button',{name:'Close reply setup',exact:true}).click();
  await page.locator('#draft').fill('a carefully edited personal reply');await page.locator('#draft').evaluate(el=>{el.setSelectionRange(8,8);window.originalEditor=el;});
  await page.evaluate(()=>{holdHistory=true;const timeline=document.querySelector('.timeline');timeline.scrollTop=0;const top=timeline.getBoundingClientRect().top,node=[...timeline.querySelectorAll('[data-message-id]')].find(n=>n.getBoundingClientRect().bottom>top+1);window.anchor={id:node.dataset.messageId,offset:node.getBoundingClientRect().top-top};});
  await page.waitForFunction(()=>historyResponses.length===1);await page.screenshot({path:'dist/history-older-preview.png'});
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===originalEditor),true);
  await page.evaluate(()=>{holdHistory=false;historyResponses.shift()();});await page.waitForFunction(()=>document.querySelectorAll('[data-message-id]').length===100);
  assert(await page.evaluate(()=>{const node=[...document.querySelectorAll('[data-message-id]')].find(n=>n.dataset.messageId===anchor.id);return Math.abs(node.getBoundingClientRect().top-document.querySelector('.timeline').getBoundingClientRect().top-anchor.offset)<2;}),'Prepending must keep the visible message anchored');
  assert.equal(await page.locator('#draft').inputValue(),'a carefully edited personal reply');assert.equal(await page.locator('#draft').evaluate(el=>el.selectionStart),8);
  await page.evaluate(()=>{failHistory=true;document.querySelector('.timeline').scrollTop=0;});await page.getByRole('button',{name:'Retry loading messages',exact:true}).waitFor();
  assert.equal(await page.locator('[data-message-id]').count(),100);
  await page.evaluate(()=>onNativeResume());assert.equal(await page.locator('[data-message-id]').count(),100);
  await page.getByRole('button',{name:'Retry loading messages',exact:true}).evaluate(el=>el.click());await page.waitForFunction(()=>document.querySelectorAll('[data-message-id]').length===150);
  await page.getByRole('button',{name:'Load older messages',exact:true}).evaluate(el=>el.click());await page.waitForFunction(()=>document.querySelectorAll('[data-message-id]').length===173);
  assert.equal(await page.locator('[data-action=load-older]').count(),0);
  assert.deepEqual(await page.locator('[data-message-id]').evaluateAll(nodes=>nodes.map(n=>Number(n.dataset.messageId))),Array.from({length:173},(_,i)=>i+1));
  const cursors=await page.evaluate(()=>calls.filter(c=>c.action==='historyPage').map(c=>c.p));assert.equal(cursors[0].beforeId,124);assert.equal(cursors[0].beforeDate,117000);assert.equal(cursors[1].beforeId,74);assert.equal(cursors[2].beforeId,74,'Retry must reuse the failed boundary');
  await page.evaluate(async()=>{sms[1][172].body='Updated latest text';await onNativeResume();});
  assert.equal(await page.locator('[data-message-id]').count(),173);assert.equal(await page.evaluate(()=>document.querySelector('#draft')===originalEditor),true);
  // A background burst can leave the newest provider window wholly separate.
  // Cached 1..173 plus newest 224..273 must bridge 174..223, including retry.
  await page.evaluate(async()=>{for(let id=174;id<=273;id++)sms[1].push({_id:id,type:id%2?1:2,body:`Away message ${id}`,date:1000000+Math.floor((id-174)/7)*1000});drafts[1]='reply after the background burst';holdHistory=true;failHistory=true;await onNativeResume();});
  await page.waitForFunction(()=>historyResponses.length===1);assert.equal(await page.locator('[data-message-id]').count(),223);assert.equal(await page.locator('.history-gap').count(),1);assert.equal(await page.evaluate(()=>document.querySelector('#draft')===originalEditor),true);
  await page.evaluate(()=>{holdHistory=false;historyResponses.shift()();});await page.getByRole('button',{name:'Retry missing messages',exact:true}).waitFor();
  assert.equal(await page.locator('[data-message-id]').count(),223);await page.getByRole('button',{name:'Retry missing messages',exact:true}).evaluate(el=>el.click());
  await page.waitForFunction(()=>historyRecord(1).messages.length===273&&!document.querySelector('.history-gap'));
  await assertWindowedHistory(273);
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===originalEditor),true);
  await page.evaluate(async()=>{sms[1].push({_id:274,type:1,body:'A new incoming text',date:1999999});drafts[1]='fresh reply for the new text';await onNativeResume();});
  await assertWindowedHistory(274);assert.equal(await page.evaluate(()=>document.querySelector('#draft')===originalEditor),true);assert.equal(await page.locator('#draft').inputValue(),'a carefully edited personal reply');
  await page.getByRole('button',{name:'Redraft',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#draft').value==='newly generated fixture reply'&&!document.querySelector('#draft').disabled);
  assert.equal(await page.evaluate(()=>historyRecord(1).messages.length),274);assert((await page.locator('[data-message-id]').count())<=240);assert.equal(await page.evaluate(()=>document.querySelector('#draft')===originalEditor),true);
  // An old-page response must never enter another contact, even after back/reopen.
  await page.getByRole('button',{name:'Back to conversations'}).click();await page.locator('.row[data-thread="3"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});
  await page.evaluate(()=>{holdHistory=true;document.querySelector('[data-action=load-older]').click();});await page.waitForFunction(()=>historyResponses.length===1);
  await page.getByRole('button',{name:'Back to conversations'}).click();await page.locator('.row[data-thread="2"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});
  await page.evaluate(()=>{holdHistory=false;historyResponses.shift()();});assert.equal(await page.locator('[data-message-id]').count(),5);assert.equal(await page.locator('#draft').inputValue(),'second contact reply');
  await page.getByRole('button',{name:'Back to conversations'}).click();await page.locator('.row[data-thread="3"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});assert.equal(await page.locator('[data-message-id]').count(),50);
  // Initial conversation calls can also finish out of order during a rapid switch.
  await page.setViewportSize({width:1100,height:900});await page.evaluate(()=>{holdConversationThread=1;});await page.locator('.row[data-thread="1"]').click();await page.waitForFunction(()=>conversationResponses.length===1);await page.locator('.row[data-thread="2"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});await page.evaluate(()=>{holdConversationThread=0;conversationResponses.shift()();});
  assert.equal(await page.locator('#draft').inputValue(),'second contact reply');assert.equal(await page.locator('[data-message-id]').count(),5);
  // An empty authoritative read (permission revoked or messages removed) clears
  // the visible cache and invalidates older-page responses already in flight.
  await page.locator('.row[data-thread="3"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});
  await page.evaluate(()=>{holdHistory=true;window.beforeEmptyEditor=document.querySelector('#draft');document.querySelector('[data-action=load-older]').click();});await page.waitForFunction(()=>historyResponses.length===1);
  await page.evaluate(async()=>{window.removedSms=sms[3];sms[3]=[];drafts[3]='';await onNativeResume();});assert.equal(await page.locator('[data-message-id]').count(),0);assert.equal(await page.evaluate(()=>document.querySelector('#draft')===beforeEmptyEditor),true);
  await page.evaluate(()=>{holdHistory=false;historyResponses.shift()();});assert.equal(await page.locator('[data-message-id]').count(),0);assert.equal(await page.locator('[data-action=load-older]').count(),0);assert.equal(await page.locator('.history-gap').count(),0);
  await page.locator('.row[data-thread="2"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});await page.locator('.row[data-thread="3"]').click();await page.locator('#draft').waitFor();await page.locator('#chat-hydration').waitFor({state:'detached'});assert.equal(await page.locator('[data-message-id]').count(),0);
  await page.evaluate(async()=>{sms[3]=removedSms;drafts[3]='restored contact reply';await onNativeResume();});await page.waitForFunction(()=>document.querySelectorAll('[data-message-id]').length===50);assert.equal(await page.locator('[data-action=load-older]').count(),1);await page.getByRole('button',{name:'Load older messages',exact:true}).evaluate(el=>el.click());await page.waitForFunction(()=>document.querySelectorAll('[data-message-id]').length===100);
  assert.equal(await page.evaluate(()=>calls.some(c=>c.action==='approve')),false);assert.deepEqual(errors,[]);
  console.log('PASS: all 173 SMS load across timestamp boundaries; complete 273/274-message records stay accessible through earlier/newer windows with <=240 DOM bubbles; duplicate pages deduplicate; prepend/window anchors/editor/cursor survive; errors retry the same cursor; refresh/new incoming/redraft preserve old pages and editor; stale page/conversation responses stay isolated; disjoint newest50 windows fill their missing bridge with visible retry; authoritative empty reads clear cached SMS and reject in-flight pages. Fixture only, no SMS or AI.');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
