const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  // The phone, carrier reports and multipart aggregates below are fictional.
  // Disable periodic refresh: only onMessagesChanged can update receipt labels.
  await page.addInitScript(()=>{
   const interval=window.setInterval.bind(window);
   window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const copy=value=>JSON.parse(JSON.stringify(value));
   const noon=new Date();noon.setHours(12,0,0,0);window.receiptTime=noon.getTime();
   window.calls=[];
   window.fixture={defaultSms:true,permissions:true,notifications:true,exact:true,contactsPermission:false,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:false,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:false},jobs:[],inbox:[]};
   const relationship={body:'',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300};
   window.chat={base:42,history:Array.from({length:20},(_,i)=>({_id:i+1,type:i%2?2:1,body:`Earlier fictional ${i%2?'reply':'message'} ${i+1}.`,date:receiptTime-(30-i)*60000,read:1})),hasMore:false,draft:{body:'An unfinished reply stays here.',engine:'Local fixture',alternatives:'[]'},relationship};
   chat.history.push({_id:40,type:1,body:'Let’s meet at the café at six.',date:receiptTime-180000,read:1});
   chat.history.push({_id:41,type:2,body:'Sounds good — see you there.',date:receiptTime-120000,read:1,status:0,delivery:'delivered',delivered_at:receiptTime-110000});
   chat.history.push({_id:42,type:2,body:'I’ll grab us a table.',date:receiptTime-60000,read:1,status:32,delivery:'pending',delivered_at:0});
   window.makeRow=(id,message)=>({thread_id:id,name:id===1?'Maya Chen':`Receipt friend ${id}`,address:`+1202555${String(1000+id).padStart(4,'0')}`,read:1,...copy(message)});
   fixture.inbox=[makeRow(1,chat.history.at(-1)),makeRow(2,{body:'The carrier accepted this text.',date:receiptTime-240000,type:2}),makeRow(3,{body:'Incoming text with local read flag.',date:receiptTime-300000,type:1,read:1,status:0,delivery:'delivered'})];
   fixture.jobs=[{_id:501,thread:1,address:fixture.inbox[0].address,body:chat.history.at(-1).body,base:40,status:'sent',note:'Carrier accepted the message.',auto_send:0,due:receiptTime-60000,delivery_status:'pending',delivered_at:0}];
   window.setLatestReceipt=fields=>{
    const message=chat.history.at(-1),row=fixture.inbox.find(item=>item.thread_id===1);
    for(const target of [message,row]){
     for(const key of ['status','delivery','delivered_at'])delete target[key];
     Object.assign(target,copy(fields));
    }
   };
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});let result={};
    if(action==='snapshot')result=fixture;
    else if(action==='conversation')result=p.thread===1?chat:{...chat,base:100+p.thread,history:[{_id:100+p.thread,...fixture.inbox.find(row=>row.thread_id===p.thread)}]};
    else if(action==='saveDraft')chat.draft={body:p.body,engine:'Edited by you',alternatives:'[]'};
    else if(action==='media')result=[];
    setTimeout(()=>nativeResult(id,copy(result),null),0);
   }};
  });
  const row=id=>page.locator(`.row[data-thread="${id}"]`);
  const bubble=id=>page.locator(`[data-message-id="${id}"]`);
  const nav=name=>page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name,exact:true});
  const latestLabel=()=>bubble(42).locator('.bubble-time').innerText();
  const update=fields=>page.evaluate(async fields=>{setLatestReceipt(fields);await onMessagesChanged();},fields);
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const assertNoDispatch=async()=>assert.equal(await page.evaluate(()=>calls.some(call=>['sendNow','approve','generate','quick','testOpenAI','testNano','contacts'].includes(call.action))),false,'Receipt reads must never send a message, draft with AI, or request contacts');
  await page.goto('http://127.0.0.1:8769');await row(1).waitFor();

  phase='carrier acceptance is Sent, not Delivered or Read';
  assert.equal(await row(1).locator('[data-receipt]').count(),0,'Ordinary Sent messages keep the inbox uncluttered');
  assert.doesNotMatch(await row(1).innerText(),/\bDelivered\b|\bRead\b/);
  assert.equal(await row(2).locator('[data-receipt]').count(),0);
  assert.doesNotMatch(await row(2).innerText(),/\bDelivered\b|\bRead\b/);
  assert.doesNotMatch(await row(3).innerText(),/\bDelivered\b|\bSent\b|\bRead\b/,'Incoming messages never receive outgoing receipt labels');
  await row(1).click();await page.locator('#draft').waitFor();
  assert.match(await bubble(41).locator('.bubble-time').innerText(),/\bDelivered\b/);
  assert.match(await latestLabel(),/\bSent\b/);
  assert.doesNotMatch(await latestLabel(),/\bDelivered\b|\bRead\b/);

  phase='missing and malformed provider data cannot manufacture delivery';
  const cases=[{}, {status:null},{status:false},{status:true},{status:''},{status:'0'},{status:{}},{status:-1},{status:32},{status:0,delivery:'none'},{status:0,delivery:'pending'},{status:0,delivery:'unknown'},{status:0,delivery:'unexpected'},{status:0,delivery:null},{delivered_at:123456}];
  for(const fields of cases){
   await update(fields);await settle();
   assert.match(await latestLabel(),/\bSent\b/,JSON.stringify(fields));
   assert.doesNotMatch(await latestLabel(),/\bDelivered\b|\bRead\b/,JSON.stringify(fields));
  }
  await update({status:0});await page.waitForFunction(()=>document.querySelector('[data-message-id="42"] .bubble-time')?.textContent.includes('Delivered'));
  await update({status:64});await page.waitForFunction(()=>document.querySelector('[data-message-id="42"] .bubble-time')?.textContent.includes('Delivery failed'));

  phase='authoritative receipt-only changes refresh history without replacing the editor or scroll anchor';
  await update({status:32,delivery:'pending',delivered_at:0});
  await page.locator('#draft').fill('my unreviewed words stay exactly where I left them');
  await page.evaluate(()=>{
   window.originalEditor=document.querySelector('#draft');originalEditor.setSelectionRange(8,12);
   const timeline=document.querySelector('.timeline');timeline.scrollTop=180;
   const top=timeline.getBoundingClientRect().top,first=[...timeline.querySelectorAll('[data-message-id]')].find(node=>node.getBoundingClientRect().bottom>top+1);
   window.receiptAnchor={id:first.dataset.messageId,offset:first.getBoundingClientRect().top-top,scrollTop:timeline.scrollTop};
  });
  assert((await page.evaluate(()=>receiptAnchor.scrollTop))>0);
  await update({status:32,delivery:'delivered',delivered_at:await page.evaluate(()=>receiptTime)});
  await page.waitForFunction(()=>document.querySelector('[data-message-id="42"] .bubble-time')?.textContent.includes('Delivered'));
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===originalEditor&&document.activeElement===originalEditor&&originalEditor.selectionStart===8&&originalEditor.selectionEnd===12),true);
  assert.equal(await page.locator('#draft').inputValue(),'my unreviewed words stay exactly where I left them');
  assert(await page.evaluate(()=>{const node=document.querySelector(`[data-message-id="${receiptAnchor.id}"]`);return Math.abs(node.getBoundingClientRect().top-document.querySelector('.timeline').getBoundingClientRect().top-receiptAnchor.offset)<2;}),'A receipt must not jump a scrolled conversation');
  const bubbleCount=await page.locator('[data-message-id]').count();
  await page.evaluate(async()=>{await Promise.all(Array.from({length:30},()=>onMessagesChanged()));});await settle();
  assert.equal(await page.locator('[data-message-id]').count(),bubbleCount,'Repeated reports cannot duplicate messages');
  await assertNoDispatch();

  phase='receipt refresh updates a visible cached message outside the newest provider page';
  await page.evaluate(()=>{
   const timeline=document.querySelector('.timeline');timeline.scrollTop=0;
   const top=timeline.getBoundingClientRect().top,first=[...timeline.querySelectorAll('[data-message-id]')].find(node=>node.getBoundingClientRect().bottom>top+1);
   window.cachedReceiptAnchor={id:first.dataset.messageId,offset:first.getBoundingClientRect().top-top};
   // Keep only the newest provider window. Message 2 remains in the UI cache,
   // so it can receive the late report only through targeted receiptUpdates.
   chat.history=chat.history.slice(-6);
   chat.hasMore=true;
   chat.before={date:chat.history[0].date,id:chat.history[0]._id};
   chat.receiptUpdates=[{_id:2,status:0,delivery:'delivered',delivered_at:receiptTime+5000}];
  });
  assert.equal(await page.evaluate(()=>chat.history.some(message=>message._id===2)),false);
  await page.evaluate(()=>onMessagesChanged());
  await page.waitForFunction(()=>document.querySelector('[data-message-id="2"] .message-receipt')?.textContent.includes('Delivered'));
  const receiptIds=await page.evaluate(()=>calls.filter(call=>call.action==='conversation').at(-1).p.receiptIds);
  assert(Array.isArray(receiptIds)&&receiptIds.includes(2),'The visible old SMS must be requested for a targeted receipt refresh');
  assert(receiptIds.length<=100&&receiptIds.every(id=>Number.isSafeInteger(id)&&id>0),'Targeted receipt reads have bounded valid SMS IDs');
  assert.equal(await page.locator('[data-message-id]').count(),bubbleCount,'The newest provider window must not erase older cached messages');
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===originalEditor&&document.activeElement===originalEditor&&originalEditor.selectionStart===8&&originalEditor.selectionEnd===12),true);
  assert(await page.evaluate(()=>{const node=document.querySelector(`[data-message-id="${cachedReceiptAnchor.id}"]`);return Math.abs(node.getBoundingClientRect().top-document.querySelector('.timeline').getBoundingClientRect().top-cachedReceiptAnchor.offset)<2;}),'A targeted older receipt update must preserve the visible anchor');

  phase='multipart aggregate remains pending until all parts are delivered';
  // Native code aggregates parts; the UI receives only its final aggregate.
  await update({status:32,delivery:'pending',delivered_at:0});
  await page.evaluate(async()=>{fixture.jobs[0].delivery_status='pending';await onMessagesChanged();});
  assert.match(await latestLabel(),/\bSent\b/);assert.doesNotMatch(await latestLabel(),/\bDelivered\b/);
  await update({status:0,delivery:'delivered',delivered_at:await page.evaluate(()=>receiptTime+10000)});
  await page.evaluate(async()=>{fixture.jobs[0].delivery_status='delivered';fixture.jobs[0].delivered_at=receiptTime+10000;await onMessagesChanged();});
  assert.match(await latestLabel(),/\bDelivered\b/);
  await page.evaluate(()=>{document.querySelector('#draft').blur();const timeline=document.querySelector('.timeline');timeline.scrollTop=timeline.scrollHeight;});
  await page.screenshot({path:'dist/sms-delivery-preview.png'});
  await page.setViewportSize({width:320,height:740});await settle();
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Receipt UI must fit a 320px screen');
  await page.screenshot({path:'dist/sms-delivery-small-preview.png'});
  await page.setViewportSize({width:412,height:915});await settle();

  phase='inbox and Queue update delivery independently from successful sending';
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  assert.match(await row(1).innerText(),/\bDelivered\b/);
  await nav('Queue').click();await page.locator('.queue-card').waitFor();
  assert.match(await page.locator('.queue-card').innerText(),/\bDelivered\b/);
  await page.evaluate(async()=>{fixture.jobs[0].delivery_status='failed';fixture.jobs[0].delivered_at=0;setLatestReceipt({status:64,delivery:'failed',delivered_at:0});await onMessagesChanged();});
  await page.waitForFunction(()=>document.querySelector('.queue-card')?.textContent.includes('Delivery failed'));
  assert.equal(await page.evaluate(()=>fixture.jobs[0].status),'sent','A delivery failure must not relabel a successful send');
  assert.equal(await page.locator('.queue-card [data-action=review]').count(),0,'Delivery failure must not offer an automatic resend/review path');
  assert.doesNotMatch(await page.locator('.queue-card').innerText(),/\bRead\b/);
  await nav('Messages').click();assert.match(await row(1).innerText(),/Delivery failed/);

  phase='incoming, outbox, failed-send and queued SMS keep their own statuses';
  for(const [type,label] of [[1,null],[4,'Sending'],[5,'Send failed'],[6,'Waiting to send']]){
   await page.evaluate(async({type})=>{setLatestReceipt(type===1?{status:0,delivery:'delivered'}:{status:-1});fixture.inbox[0].type=type;fixture.inbox[0].read=1;chat.history.at(-1).type=type;await onMessagesChanged();},{type});
   const text=await row(1).innerText();
   assert.doesNotMatch(text,/\bDelivered\b|\bRead\b/,String(type));
   if(label)assert(text.includes(label),`${type}: ${text}`);
   else assert.doesNotMatch(text,/\bSent\b|Delivery failed/);
  }

  phase='explicit delivery proof wins over delayed sent-callback failure';
  await page.evaluate(async()=>{fixture.inbox[0].type=5;chat.history.at(-1).type=5;setLatestReceipt({status:64,delivery:'delivered',delivered_at:receiptTime+20000});await onMessagesChanged();});
  assert.match(await row(1).innerText(),/\bDelivered\b/);
  assert.doesNotMatch(await row(1).innerText(),/Send failed/);

  phase='Settings explain SMS delivery reports and unavailable RCS/read receipts';
  await nav('Settings').click();const settings=await page.locator('main').innerText();
  assert.match(settings,/delivery/i);assert.match(settings,/read receipts/i);assert.match(settings,/RCS/);
  await assertNoDispatch();assert.deepEqual(errors,[]);
  console.log('PASS: SMS Sent/Delivered/Delivery failed labels use actual receipt fields; missing, malformed and pending data never imply delivery; local read flags never imply Read; status-only native events update chat/inbox/Queue without polling; bounded targeted receipt reads update visible older cached messages; editor, selection and scroll survive; duplicate reports do not dispatch; multipart aggregate remains pending until delivered; 412px/320px screenshots fit. Fictional bridge only, no SMS, AI or contacts.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
