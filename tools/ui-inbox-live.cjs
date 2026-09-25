const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  // All provider events and replies are fictional. Interval polling is disabled so
  // only the native event callback can satisfy the live-update assertions.
  await page.addInitScript(()=>{
   const interval=window.setInterval;
   window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   const noon=new Date();noon.setHours(12,0,0,0);window.fixtureTime=noon.getTime();
   window.calls=[];window.snapshotReplies=[];window.generateReplies=[];window.holdSnapshots=false;window.holdGenerate=false;
   window.fixture={defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:false,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:false},jobs:[{_id:91,thread:12,body:'automatic reply',base:12,status:'scheduled',auto_send:1,due:fixtureTime+3600000}],inbox:Array.from({length:40},(_,i)=>({thread_id:i+1,name:`Friend ${String(i+1).padStart(2,'0')}`,address:`+1202555${String(1000+i).padStart(4,'0')}`,body:`Incoming message ${i+1}`,date:fixtureTime-(i+1)*60000,type:1,read:1}))};
   window.chats=Object.fromEntries(fixture.inbox.map(row=>[row.thread_id,{base:row.thread_id,history:[{_id:row.thread_id,type:1,body:row.body,date:row.date}],hasMore:false,before:{date:row.date,id:row.thread_id},draft:{body:`Draft for friend ${row.thread_id}`,engine:'Local fixture',alternatives:'[]'},relationship:{body:'',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300}}]));
   window.updateRow=(thread,body,type,date)=>{Object.assign(fixture.inbox.find(row=>row.thread_id===thread),{body,type,date});fixture.inbox.sort((a,b)=>b.date-a.date);};
   window.releaseSnapshot=()=>{const reply=snapshotReplies.shift();if(!reply)throw new Error('No held snapshot');reply();};
   window.releaseGenerate=()=>generateReplies.splice(0).forEach(reply=>reply());
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});
    const finish=value=>nativeResult(id,clone(value),null);
    if(action==='snapshot'){
     const captured=clone(fixture); // Provider data is fixed when this request starts.
     if(holdSnapshots)snapshotReplies.push(()=>finish(captured));else setTimeout(()=>finish(captured),0);
     return;
    }
    const reply=()=>{
     if(action==='conversation')finish(chats[p.thread]);
     else if(action==='saveDraft'){chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};finish({});}
     else if(action==='generate'){chats[p.thread].draft={body:'A newly prepared fictional reply',engine:'Fixture generator',alternatives:'[]'};finish({});}
     else if(action==='media')finish([]);
     else finish({});
    };
    if(action==='generate'&&holdGenerate)generateReplies.push(reply);else setTimeout(reply,0);
   }};
  });
  const row=id=>page.locator(`.row[data-thread="${id}"]`);
  const nav=name=>page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name,exact:true});
  const snapshotCount=()=>page.evaluate(()=>calls.filter(call=>call.action==='snapshot').length);
  const anchor=()=>page.locator('#conversation-list').evaluate(list=>{const top=list.getBoundingClientRect().top,first=[...list.querySelectorAll('.row')].find(row=>row.getBoundingClientRect().bottom>top+1);return {id:first?.dataset.thread,offset:first?.getBoundingClientRect().top-top,scrollTop:list.scrollTop};});
  const assertSearch=async()=>assert.equal(await page.evaluate(()=>document.querySelector('#search')===savedSearch&&document.activeElement===savedSearch&&savedSearch.value==='friend'&&savedSearch.selectionStart===2&&savedSearch.selectionEnd===4),true,'Search identity, query, focus and selection survive provider updates');
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  await page.goto(process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769');await row(12).waitFor();

  phase='automatic sending immediately updates the active filtered inbox';
  assert((await page.locator('.inbox-queue-count').innerText()).includes('1 scheduled'));
  assert((await row(12).locator('.row-badge').innerText()).includes('Reply scheduled'));
  const previousTime=await row(12).locator('time').innerText();
  await page.locator('#search').fill('friend');
  await page.evaluate(()=>{window.savedSearch=document.querySelector('#search');savedSearch.setSelectionRange(2,4);document.querySelector('#conversation-list').scrollTop=250;});
  const before=await anchor();assert(before.scrollTop>0);
  await page.evaluate(async()=>{fixture.jobs[0].status='sending';updateRow(12,'automatic reply reached the outbox',4,fixtureTime+1800000);await onMessagesChanged();});
  await page.waitForFunction(()=>document.querySelector('.row[data-thread="12"] p')?.textContent==='You: automatic reply reached the outbox');
  assert.equal(await page.locator('.row').first().getAttribute('data-thread'),'12');
  assert.equal(await row(12).locator('.row-badge').innerText(),'Sending…');
  assert.notEqual(await row(12).locator('time').innerText(),previousTime);
  const expectedTime=await page.evaluate(()=>new Date(fixtureTime+1800000).toLocaleTimeString([],{hour:'numeric',minute:'2-digit'}));
  assert.equal(await row(12).locator('time').innerText(),expectedTime);
  assert.equal(await page.locator('.inbox-queue-count:visible').count(),0);
  await assertSearch();const after=await anchor();assert.equal(after.id,before.id);assert(Math.abs(after.offset-before.offset)<=1,JSON.stringify({before,after}));
  await page.evaluate(async()=>{fixture.jobs[0].status='sent';updateRow(12,'automatic reply was sent',2,fixtureTime+1860000);await onMessagesChanged();});
  await page.waitForFunction(()=>document.querySelector('.row[data-thread="12"] p')?.textContent==='You: automatic reply was sent');
  assert.equal(await row(12).locator('.row-badge').count(),0);await assertSearch();

  phase='late events during a snapshot cannot be dropped and bursts coalesce';
  const beforeBurst=await snapshotCount();
  await page.evaluate(()=>{holdSnapshots=true;updateRow(20,'first captured version',4,fixtureTime+1900000);window.firstEvent=onMessagesChanged();});
  await page.waitForFunction(()=>snapshotReplies.length===1);
  await page.evaluate(()=>{updateRow(20,'latest version arriving during the request',2,fixtureTime+1960000);for(let i=0;i<100;i++)onMessagesChanged();releaseSnapshot();});
  await page.waitForFunction(()=>snapshotReplies.length===1);
  await page.evaluate(()=>{holdSnapshots=false;releaseSnapshot();});
  await page.waitForFunction(()=>document.querySelector('.row[data-thread="20"] p')?.textContent==='You: latest version arriving during the request');
  await settle();
  const burstRequests=await snapshotCount()-beforeBurst;assert(burstRequests>=2&&burstRequests<=3,`A held request plus 100 events should coalesce, saw ${burstRequests} snapshots`);
  assert.equal(await page.locator('.row').first().getAttribute('data-thread'),'20');await assertSearch();

  phase='failed and waiting outgoing states keep honest previews';
  for(const [type,badge] of [[5,'Send failed'],[6,'Waiting to send']]){
   await page.evaluate(async({type})=>{updateRow(20,'outgoing provider status',type,fixtureTime+1960000);await onMessagesChanged();},{type});
   await page.waitForFunction(expected=>document.querySelector('.row[data-thread="20"] .row-badge')?.textContent.trim()===expected,badge);
   assert.equal(await row(20).locator('p').innerText(),'You: outgoing provider status');await assertSearch();
  }

  phase='provider events do not replace a focused typed reply';
  await row(1).click();await page.locator('#draft').waitFor();await page.locator('#draft').fill('my unfinished reply must stay right here');
  await page.evaluate(()=>{window.savedEditor=document.querySelector('#draft');savedEditor.setSelectionRange(6,6);});
  await page.evaluate(async()=>{updateRow(30,'unrelated automatic reply',2,fixtureTime+2000000);await onMessagesChanged();});
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===savedEditor&&document.activeElement===savedEditor&&savedEditor.selectionStart===6),true);
  assert.equal(await page.locator('#draft').inputValue(),'my unfinished reply must stay right here');

  phase='events received while busy refresh when the operation finishes';
  await page.evaluate(()=>{holdGenerate=true;});await page.getByRole('button',{name:'Redraft',exact:true}).click();
  await page.waitForFunction(()=>generateReplies.length===1);
  const beforeBusy=await snapshotCount();
  await page.evaluate(()=>{updateRow(25,'reply delivered during generation',2,fixtureTime+2100000);for(let i=0;i<20;i++)onMessagesChanged();});
  await settle();assert.equal(await snapshotCount(),beforeBusy,'Busy notifications are retained rather than starting overlapping reads');
  await page.evaluate(()=>{holdGenerate=false;releaseGenerate();});
  await page.waitForFunction(()=>document.querySelector('#draft')?.value==='A newly prepared fictional reply');
  await page.waitForFunction(()=>document.querySelector('.row[data-thread="25"] p')?.textContent==='You: reply delivered during generation');
  assert.equal(await page.locator('.row').first().getAttribute('data-thread'),'25');

  phase='a delayed native event cannot navigate the user back to Messages';
  await page.evaluate(()=>{holdSnapshots=true;window.pendingAwayEvent=onMessagesChanged();});await page.waitForFunction(()=>snapshotReplies.length===1);
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();await nav('Settings').click();
  await page.getByRole('heading',{name:'Settings',exact:true}).waitFor();await page.locator('#pairing-code').fill('private local draft only');
  await page.evaluate(()=>{window.savedSetting=document.querySelector('#pairing-code');savedSetting.setSelectionRange(3,3);holdSnapshots=false;releaseSnapshot();});
  await settle();assert.equal(await nav('Settings').getAttribute('aria-current'),'page');
  assert.equal(await page.evaluate(()=>document.activeElement?.id==='pairing-code'&&document.activeElement.selectionStart===3&&document.activeElement.value==='private local draft only'),true);
  assert.equal(await page.locator('.row:visible').count(),0);
  assert.equal(await page.evaluate(()=>calls.some(call=>['sendNow','approve','testOpenAI','testNano'].includes(call.action))),false);
  assert.deepEqual(errors,[]);
  console.log('PASS: provider events refresh outgoing inbox preview/time/order and queue badges without polling; active search selection and list anchor survive; late snapshots drain fresh updates and bursts coalesce; outgoing status badges stay accurate; typed replies and Settings focus survive; busy events refresh after completion. Fictional bridge only, no SMS or AI.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
