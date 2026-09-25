const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const base=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.snapshotReplies=[];window.liveReplies=[];window.holdSnapshot=false;window.holdLive=false;window.liveRows=null;window.pinReplies=[];
   window.makeRow=(thread,date,body='Fictional text',kind='sms',type=1)=>({_id:thread*100+date,thread_id:thread,name:'Friend '+thread,address:'+1202555010'+thread,body,date:1700000000000+date,type,kind,read:1,pinned:[2,4].includes(thread),photo:'https://appassets.androidplatform.net/contact-photo/'+thread,...(kind==='mms'?{m_type:132,msg_box:type}:{})});
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,contactPhotoRevision:1,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:false,inAppSuggestions:false,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:false},jobs:[],inbox:[makeRow(1,100),makeRow(2,50),makeRow(5,200),makeRow(4,80),makeRow(3,300)]};
   window.releaseSnapshot=()=>{const reply=snapshotReplies.shift();if(!reply)throw new Error('No pending snapshot');reply();};
   window.releaseLive=()=>{const reply=liveReplies.shift();if(!reply)throw new Error('No pending live read');reply();};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});const finish=result=>nativeResult(id,clone(result),null);
    if(action==='snapshot'){const value=clone(fixture),reply=()=>finish(value);if(holdSnapshot)snapshotReplies.push(reply);else setTimeout(reply,0);return;}
    if(action==='liveInbox'){const value={inbox:clone(liveRows||fixture.inbox),inboxComplete:false,contactsAllowed:fixture.contactsAllowed,contactPhotoRevision:fixture.contactPhotoRevision},reply=()=>finish(value);if(holdLive)liveReplies.push(reply);else setTimeout(reply,0);return;}
    if(action==='pinChat'){pinReplies.push(()=>{fixture.inbox.find(row=>row.thread_id===p.thread).pinned=p.pinned;finish({thread:p.thread,pinned:p.pinned});});return;}
    setTimeout(()=>finish({}),0);
   }};
  });
  const order=()=>page.locator('#conversation-list .row').evaluateAll(rows=>rows.map(row=>Number(row.dataset.thread)));
  const row=id=>page.locator(`.row[data-thread="${id}"]`);
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const notify=async()=>{await page.evaluate(()=>{void onMessagesChanged();});await page.waitForFunction(()=>liveInboxRequest===null&&!liveInboxPending);await settle();};
  const fresh=async()=>{await page.goto(base);await row(1).waitFor();await page.waitForFunction(()=>inboxBootstrap.authoritative&&!refreshing&&!liveInboxRequest);await page.evaluate(()=>{busy=true;});};
  await fresh();

  phase='out-of-order provider rows and launch cache sort latest first within pinned and normal groups';
  assert.deepEqual(await order(),[4,2,3,5,1]);
  assert.deepEqual(await page.evaluate(()=>launchInboxRows([makeRow(1,1),makeRow(3,4),makeRow(1,7),makeRow(5,3)],true,2).map(row=>[row.thread_id,row.date-1700000000000])),[[1,7],[3,4]]);

  phase='sent SMS moves immediately during AI busy, without waiting for a full snapshot';
  const snapshots=await page.evaluate(()=>calls.filter(call=>call.action==='snapshot').length);
  await page.evaluate(()=>{liveRows=[makeRow(1,400,'Just sent this','sms',2)];});await notify();
  assert.deepEqual(await order(),[4,2,1,3,5]);assert((await row(1).innerText()).includes('You: Just sent this'));
  assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='snapshot').length),snapshots);
  assert.equal(await page.evaluate(()=>busy),true);

  phase='incoming SMS, incoming MMS and outgoing MMS each reorder by latest actual timestamp';
  await page.evaluate(()=>{liveRows=[makeRow(3,500,'Incoming SMS')];});await notify();assert.deepEqual(await order(),[4,2,3,1,5]);
  await page.evaluate(()=>{liveRows=[makeRow(5,600,'Incoming MMS','mms')];});await notify();assert.deepEqual(await order(),[4,2,5,3,1]);
  await page.evaluate(()=>{liveRows=[makeRow(1,700,'Sent MMS text','mms',2)];});await notify();assert.deepEqual(await order(),[4,2,1,5,3]);assert((await row(1).innerText()).includes('You: Sent MMS text'));
  await page.evaluate(()=>{liveRows=[makeRow(2,800,'Pinned incoming')];});await notify();assert.deepEqual(await order(),[2,4,1,5,3]);

  phase='partial native reads add new threads and retain all absent chats, names and photos';
  await page.evaluate(()=>{liveRows=[makeRow(6,900,'Brand new conversation')];});await notify();assert.deepEqual(await order(),[2,4,6,1,5,3]);
  assert((await row(6).innerText()).includes('Friend 6'));assert.equal(await row(3).count(),1);

  phase='a full snapshot that started earlier cannot roll back a newer partial inbox result';
  await page.evaluate(()=>{fixture.inbox=JSON.parse(JSON.stringify(state.inbox));holdSnapshot=true;window.oldRead=refresh(true);});await page.waitForFunction(()=>snapshotReplies.length===1);
  await page.evaluate(()=>{liveRows=[makeRow(3,1000,'Newest while snapshot is stuck')];});await notify();assert.deepEqual(await order(),[2,4,3,6,1,5]);
  await page.evaluate(()=>{releaseSnapshot();});await page.evaluate(()=>oldRead);
  assert.deepEqual(await order(),[2,4,3,6,1,5]);assert((await row(3).innerText()).includes('Newest while snapshot is stuck'));

  phase='a later full snapshot can delete a thread or replace a deleted latest message with an older one';
  await page.evaluate(async()=>{holdSnapshot=false;fixture.inbox=[makeRow(1,100),makeRow(2,800),makeRow(5,600),makeRow(4,80),makeRow(3,20)];await refresh(true);});
  assert.deepEqual(await order(),[2,4,5,1,3]);assert.equal(await row(6).count(),0);

  phase='an older fast result arriving after a later authoritative snapshot is ignored';
  await page.evaluate(()=>{holdLive=true;liveRows=[makeRow(6,2000,'Obsolete fast result')];void refreshLiveInbox();});await page.waitForFunction(()=>liveReplies.length===1);
  await page.evaluate(async()=>{fixture.inbox=[makeRow(1,1500,'Newer authoritative'),makeRow(2,800),makeRow(5,600),makeRow(4,80),makeRow(3,20)];await refresh(true);});
  await page.evaluate(()=>{holdLive=false;releaseLive();});await page.waitForFunction(()=>!liveInboxRequest);
  assert.equal(await row(6).count(),0);assert((await row(1).innerText()).includes('Newer authoritative'));

  phase='bursts coalesce to one in-flight read and one follow-up for the newest event';
  await page.evaluate(()=>{holdLive=true;liveRows=[makeRow(5,1600,'First burst')];void onMessagesChanged();});await page.waitForFunction(()=>liveReplies.length===1);
  await page.evaluate(()=>{liveRows=[makeRow(3,1700,'Last burst')];void onMessagesChanged();void onMessagesChanged();void onMessagesChanged();});assert.equal(await page.evaluate(()=>liveReplies.length),1);
  await page.evaluate(()=>releaseLive());await page.waitForFunction(()=>liveReplies.length===1);
  await page.evaluate(()=>{holdLive=false;releaseLive();});await page.waitForFunction(()=>!liveInboxRequest&&!liveInboxPending);assert.deepEqual(await order(),[2,4,3,5,1]);

  phase='optimistic pin selection is kept across fast reads with older saved pin flags';
  await page.evaluate(()=>{holdLive=true;liveRows=[makeRow(1,1800,'Pending pin message')];void refreshLiveInbox();});await page.waitForFunction(()=>liveReplies.length===1);
  await row(1).click({button:'right'});await page.locator('[data-action="pin-chat"]').click();await page.waitForFunction(()=>pinReplies.length===1);
  await page.evaluate(()=>{holdLive=false;releaseLive();});await page.waitForFunction(()=>!liveInboxRequest);assert.equal(await row(1).getAttribute('data-pinned'),'true');assert.deepEqual(await order(),[1,2,4,3,5]);
  await page.evaluate(()=>pinReplies.shift()());await page.waitForFunction(()=>pinTasks.size===0);

  phase='search node and caret survive inbox changes';
  await page.locator('#search').fill('Friend');await page.evaluate(()=>{window.searchField=document.querySelector('#search');searchField.setSelectionRange(1,3);liveRows=[{...makeRow(5,1900,'Fresh filtered message'),name:'Friend 5'}];});await notify();
  assert(await page.evaluate(()=>document.querySelector('#search')===searchField&&document.activeElement===searchField&&searchField.selectionStart===1&&searchField.selectionEnd===3));

  phase='provider update preserves a pressed row and opens only its original chat';
  await page.locator('#search').fill('');const box=await row(3).boundingBox();await page.mouse.move(box.x+box.width/2,box.y+box.height/2);await page.mouse.down();
  await page.evaluate(()=>{window.pressedRow=document.querySelector('.row[data-thread="3"]');liveRows=[makeRow(5,2100,'Reorder under finger')];});await notify();assert(await page.evaluate(()=>pressedRow.isConnected));
  await page.mouse.up();await settle();assert.equal(await page.locator('.timeline').getAttribute('data-thread'),'3');

  phase='late contact and SMS permission results cannot restore private display data';
  await fresh();await page.evaluate(()=>{holdLive=true;liveRows=[makeRow(6,9999)];void refreshLiveInbox();});await page.waitForFunction(()=>liveReplies.length===1);
  await page.evaluate(()=>{onMessageAccessChanged({contacts:false});holdLive=false;releaseLive();});await settle();assert.equal(await row(6).count(),0);assert((await row(1).innerText()).includes('+12025550101'));assert.equal(await page.locator('.contact-avatar-photo').count(),0);
  await page.evaluate(()=>{holdLive=true;void refreshLiveInbox();});await page.waitForFunction(()=>liveReplies.length===1);
  await page.evaluate(()=>{onMessageAccessChanged({readSms:false,defaultSms:false});holdLive=false;releaseLive();});await settle();assert.equal(await page.locator('.row').count(),0);
  assert.deepEqual(await page.evaluate(()=>calls.filter(call=>['sendNow','sendMms','approve','generate','draftMmsText','suggestReply','analyzeMedia','saveDraft','saveProfile'].includes(call.action))),[]);
  assert.deepEqual(errors,[]);
  console.log('PASS: latest-first pinned/normal sorting, cache selection, immediate SMS/MMS send/receive changes during AI busy, partial new threads, stale snapshot and fast-read races, authoritative deletion, burst coalescing, optimistic pins, search/pressed-row preservation and permission clearing. Fictional bridge only; no SMS/AI calls.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
