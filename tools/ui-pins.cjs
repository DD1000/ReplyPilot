const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const base=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   const pins=JSON.parse(localStorage.getItem('fictional-pins')||'[]');
   window.calls=[];window.holdPin=false;window.pinReplies=[];window.holdSnapshot=false;window.snapshotReplies=[];window.injected=0;
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:false,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:false},jobs:[],inbox:[
    {_id:51,thread_id:1,name:'Alex Rivera',address:'+12025550101',body:'The latest fictional message',date:3000,type:1},
    {_id:52,thread_id:2,name:'Casey Morgan',address:'+12025550102',body:'An older fictional message',date:2000,type:1},
    {_id:53,thread_id:3,name:'<img src=x onerror="window.injected=1">',address:'+12025550103',body:'A third fictional message',date:1000,type:1},
   ].map(row=>({...row,...(pins.includes(row.thread_id)?{pinned:true}:{})}))};
   window.chats=Object.fromEntries(fixture.inbox.map(row=>[row.thread_id,{base:row._id,history:[{_id:row._id,type:1,body:row.body,date:row.date}],hasMore:false,draft:{body:'Keep this manual reply',engine:'Edited by you',alternatives:'[]'},relationship:{body:'Original context',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300},replyEligibility:{eligible:false,total:1,owner:0,incoming:1}}]));
   window.resolvePin=(error=null)=>{const pending=pinReplies.shift();if(!pending)throw new Error('No pending pin');pending(error);};
   window.releaseSnapshot=()=>{const reply=snapshotReplies.shift();if(!reply)throw new Error('No pending snapshot');reply();};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});const finish=(value,error=null)=>nativeResult(id,clone(value),error);
    if(action==='snapshot'){const captured=clone(fixture),reply=()=>finish(captured);if(holdSnapshot){holdSnapshot=false;snapshotReplies.push(reply);}else setTimeout(reply,0);return;}
    if(action==='pinChat'){const reply=error=>{if(error){finish(null,error);return;}fixture.inbox.find(row=>row.thread_id===p.thread).pinned=p.pinned;localStorage.setItem('fictional-pins',JSON.stringify(fixture.inbox.filter(row=>row.pinned).map(row=>row.thread_id)));finish({thread:p.thread,pinned:p.pinned});};if(holdPin)pinReplies.push(reply);else setTimeout(reply,0);return;}
    setTimeout(()=>{if(action==='conversation')finish(chats[p.thread]);else if(action==='saveDraft'){chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};finish({});}else finish({});},0);
   }};
  });
  const row=id=>page.locator(`.row[data-thread="${id}"]`);
  const menu=()=>page.locator('#chat-pin-menu');
  const order=()=>page.locator('.conversation-list .row').evaluateAll(rows=>rows.map(row=>Number(row.dataset.thread)));
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const choose=async id=>{await row(id).focus();await row(id).press('Shift+F10');await menu().waitFor();};
  const settledPins=()=>page.evaluate(()=>Promise.all([...pinTasks.values()]));
  const apply=async()=>{await page.locator('[data-action=pin-chat]').click();await menu().waitFor({state:'detached'});await settledPins();};
  const noMessaging=async(start=0)=>assert.deepEqual(await page.evaluate(start=>calls.slice(start).filter(call=>['sendNow','approve','generate','quick','testOpenAI','testNano','saveDraft','saveProfile'].includes(call.action)),start),[],'Pinning must never send, generate, save a draft or change a profile');
  await page.goto(base);await row(1).waitFor();

  phase='conversation rows have no three-dot buttons and pin reorders in the first frame while its save is pending';
  assert.deepEqual(await order(),[1,2,3]);assert.equal(await page.locator('.row button,.row a').count(),0);assert.equal(await page.locator('.conversation-more,[data-action=chat-menu]').count(),0);
  await page.evaluate(()=>{holdPin=true;});await choose(2);
  assert.equal(await page.locator('#draft').count(),0);assert.equal(await page.evaluate(()=>calls.filter(call=>call.action==='conversation').length),0);
  assert.equal(await page.locator('[data-action=pin-chat]').innerText(),'Pin conversation');
  const immediate=await page.locator('[data-action=pin-chat]').evaluate(async button=>{button.click();await new Promise(requestAnimationFrame);return {order:[...document.querySelectorAll('.conversation-list .row')].map(row=>Number(row.dataset.thread)),menu:!!document.querySelector('#chat-pin-menu'),pending:pinReplies.length};});
  assert.deepEqual(immediate.order,[2,1,3]);assert.equal(immediate.menu,false);assert.equal(immediate.pending,1);assert.equal(await row(2).getAttribute('data-pinned'),'true');
  assert.deepEqual(await page.evaluate(()=>calls.find(call=>call.action==='pinChat').p),{thread:2,pinned:true});
  await page.evaluate(()=>{holdPin=false;resolvePin();});await settledPins();assert.deepEqual(await order(),[2,1,3]);assert.equal(await row(2).locator('.row-pin').count(),1);
  assert.equal(await page.evaluate(()=>document.activeElement?.dataset.action),'open');assert.equal(await page.evaluate(()=>document.activeElement?.dataset.thread),'2');await noMessaging();
  await page.reload();await row(2).waitFor();assert.deepEqual(await order(),[2,1,3]);assert.equal(await row(2).getAttribute('data-pinned'),'true');
  await choose(2);assert.equal(await page.locator('[data-action=pin-chat]').innerText(),'Unpin conversation');await apply();assert.deepEqual(await order(),[1,2,3]);

  phase='rapid same-thread toggles remain responsive and earlier failures cannot undo a newer intent';
  await page.evaluate(()=>{holdPin=true;});await choose(2);await page.locator('[data-action=pin-chat]').click();await page.waitForFunction(()=>pinReplies.length===1);
  await choose(2);await page.locator('[data-action=pin-chat]').click();assert.equal(await row(2).getAttribute('data-pinned'),'false');assert.equal(await page.evaluate(()=>pinReplies.length),1,'Same-thread writes are serialized');
  await page.evaluate(()=>resolvePin('Earlier pin failed'));await page.waitForFunction(()=>pinReplies.length===1);
  assert.equal(await row(2).getAttribute('data-pinned'),'false');assert.equal(await page.locator('#pin-feedback').count(),0,'An obsolete error cannot overwrite the latest intent');
  await page.evaluate(()=>resolvePin());await settledPins();assert.equal(await page.evaluate(()=>fixture.inbox.find(row=>row.thread_id===2).pinned),false);

  phase='latest failure rolls back to the most recent acknowledged pin and exposes explicit retry';
  await choose(2);await page.locator('[data-action=pin-chat]').click();await page.waitForFunction(()=>pinReplies.length===1);
  await choose(2);await page.locator('[data-action=pin-chat]').click();assert.equal(await row(2).getAttribute('data-pinned'),'false');
  await page.evaluate(()=>resolvePin());await page.waitForFunction(()=>pinReplies.length===1);
  await page.evaluate(()=>resolvePin('The pin could not be saved. Try again.'));await settledPins();
  await page.locator('#pin-feedback').filter({hasText:'could not be saved'}).waitFor();assert.equal(await row(2).getAttribute('data-pinned'),'true');
  await page.evaluate(()=>{holdPin=false;});await page.locator('[data-action=retry-pin]').click();await settledPins();assert.equal(await row(2).getAttribute('data-pinned'),'false');assert.equal(await page.locator('#pin-feedback').count(),0);

  phase='different threads can save independently while every visible position updates immediately';
  await page.evaluate(()=>{holdPin=true;});await choose(2);await page.locator('[data-action=pin-chat]').click();await choose(3);await page.locator('[data-action=pin-chat]').click();
  await page.waitForFunction(()=>pinReplies.length===2);assert.deepEqual(await order(),[2,3,1]);
  await page.evaluate(()=>{resolvePin();resolvePin();holdPin=false;});await settledPins();
  await choose(2);await apply();await choose(3);await apply();assert.deepEqual(await order(),[1,2,3]);

  phase='single failed pin restores the saved state without changing unrelated rows';
  await page.evaluate(()=>{holdPin=true;});await choose(1);await page.locator('[data-action=pin-chat]').click();await page.waitForFunction(()=>pinReplies.length===1);
  await page.evaluate(()=>resolvePin('The pin could not be saved. Try again.'));await settledPins();await page.locator('#pin-feedback').filter({hasText:'could not be saved'}).waitFor();
  assert.equal(await row(1).getAttribute('data-pinned'),'false');assert(await page.locator('[data-action=retry-pin]').isEnabled());
  await page.evaluate(()=>{holdPin=false;});await page.locator('[data-action=retry-pin]').click();await settledPins();assert.equal(await row(1).getAttribute('data-pinned'),'true');

  phase='search, escaping, sorted pinned group and provider updates';
  await choose(3);assert((await page.locator('#chat-pin-title').innerText()).includes('<img src=x'));assert.equal(await menu().locator('img,[onerror]').count(),0);await apply();
  assert.deepEqual(await order(),[1,3,2]);await page.locator('#search').fill('third');assert.deepEqual(await order(),[3]);
  await page.evaluate(()=>{window.searchNode=document.querySelector('#search');searchNode.setSelectionRange(2,2);fixture.inbox.find(row=>row.thread_id===3).date=4000;});
  await page.evaluate(()=>onMessagesChanged());assert.deepEqual(await order(),[3]);
  assert.equal(await page.evaluate(()=>document.querySelector('#search')===searchNode&&document.activeElement===searchNode&&searchNode.selectionStart===2),true);
  await page.locator('#search').fill('');assert.deepEqual(await order(),[3,1,2]);
  await page.evaluate(async()=>{fixture.inbox[0].date=4000;fixture.inbox[0]._id=60;await onMessagesChanged();});assert.deepEqual(await order(),[1,3,2],'Equal dates follow newest SMS id, not thread id');
  assert.equal(await page.evaluate(()=>injected),0);await noMessaging();

  phase='a snapshot started before pinning cannot settle the mutation with old data';
  await page.evaluate(()=>{holdSnapshot=true;window.oldRefresh=onMessagesChanged();});await page.waitForFunction(()=>snapshotReplies.length===1);
  await choose(2);await page.locator('[data-action=pin-chat]').click();await page.waitForFunction(()=>fixture.inbox.find(row=>row.thread_id===2).pinned===true);
  assert.equal(await row(2).getAttribute('data-pinned'),'true');await page.evaluate(()=>releaseSnapshot());await page.evaluate(()=>oldRefresh);assert.equal(await row(2).getAttribute('data-pinned'),'true');await settledPins();

  phase='keyboard context menu, focus return and focus trap';
  await row(2).focus();await row(2).press('Shift+F10');await menu().waitFor();assert.equal(await page.evaluate(()=>document.activeElement?.dataset.action),'pin-chat');
  await page.keyboard.press('Shift+Tab');assert.equal(await page.evaluate(()=>document.activeElement?.dataset.action),'close-chat-menu');
  await page.keyboard.press('Tab');assert.equal(await page.evaluate(()=>document.activeElement?.dataset.action),'pin-chat');
  await page.keyboard.press('Escape');assert.equal(await menu().count(),0);assert.equal(await page.evaluate(()=>document.activeElement?.classList.contains('row')&&document.activeElement?.dataset.thread==='2'),true);
  await row(2).click({button:'right'});await menu().waitFor();await page.evaluate(()=>goBack());assert.equal(await menu().count(),0);assert.equal(await page.locator('#draft').count(),0);

  phase='long press suppresses release click, and scrolling/moving cancels';
  const box=await row(2).boundingBox();await page.mouse.move(box.x+30,box.y+25);await page.mouse.down();await menu().waitFor();await page.mouse.up();await settle();
  assert(await menu().isVisible());assert.equal(await page.locator('#draft').count(),0);await page.keyboard.press('Escape');
  await page.mouse.move(box.x+30,box.y+25);await page.mouse.down();await page.mouse.move(box.x+60,box.y+25);await page.waitForTimeout(550);await page.mouse.up();await settle();assert.equal(await menu().count(),0);assert.equal(await page.locator('#draft').count(),0);
  await page.mouse.move(box.x+30,box.y+25);await page.mouse.down();await page.locator('.conversation-list').dispatchEvent('scroll');await page.waitForTimeout(550);await page.mouse.up();await settle();assert.equal(await menu().count(),0);assert.equal(await page.locator('#draft').count(),0);await noMessaging();
  // Android can deliver its native context menu before our hold timer fires.
  await row(2).dispatchEvent('pointerdown',{pointerId:71,pointerType:'touch',isPrimary:true,button:0,clientX:box.x+30,clientY:box.y+25});
  await row(2).dispatchEvent('contextmenu');await menu().waitFor();
  await row(2).dispatchEvent('pointerup',{pointerId:71,pointerType:'touch',isPrimary:true,button:0,clientX:box.x+30,clientY:box.y+25});
  await row(2).dispatchEvent('click',{detail:1});assert.equal(await page.locator('#draft').count(),0);assert(await menu().isVisible());await page.keyboard.press('Escape');

  phase='desktop pinning preserves active reply, recipient, timer and unsaved profile';
  await page.setViewportSize({width:1280,height:915});await row(2).press('Enter');await page.locator('#draft').waitFor();await page.locator('#draft').fill('my unsent edited reply');
  await page.waitForFunction(()=>chats[2].draft.body==='my unsent edited reply');
  await page.evaluate(()=>{window.draftNode=document.querySelector('#draft');draftNode.focus();draftNode.setSelectionRange(4,9);window.callStart=calls.length;window.addressBefore=current.address;window.threadBefore=current.thread_id;window.dueBefore=JSON.stringify(state.jobs);});
  await row(1).dispatchEvent('contextmenu');await apply();
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===draftNode&&draftNode.value==='my unsent edited reply'&&document.activeElement===draftNode&&draftNode.selectionStart===4&&draftNode.selectionEnd===9&&current.address===addressBefore&&current.thread_id===threadBefore&&JSON.stringify(state.jobs)===dueBefore),true);
  await noMessaging(await page.evaluate(()=>callStart));
  await page.locator('[data-action=toggle-timer]').click();await page.locator('[data-action=custom]').click();await page.locator('#custom-delay').fill('17');
  await page.evaluate(()=>{window.timerNode=document.querySelector('#custom-delay');window.callStart=calls.length;});await row(1).dispatchEvent('contextmenu');await apply();
  assert.equal(await page.evaluate(()=>document.querySelector('#custom-delay')===timerNode&&timerNode.value==='17'),true);await noMessaging(await page.evaluate(()=>callStart));
  await page.locator('[data-action=close-timer]').click();await page.locator('[data-action=toggle-profile]').click();await page.locator('#relationship-context').fill('Keep my unsaved context');
  await page.evaluate(()=>{window.profileNode=document.querySelector('#relationship-context');profileNode.setSelectionRange(3,6);window.callStart=calls.length;});await row(1).dispatchEvent('contextmenu');await apply();
  assert.equal(await page.evaluate(()=>document.querySelector('#relationship-context')===profileNode&&document.activeElement===profileNode&&profileNode.value==='Keep my unsaved context'&&profileNode.selectionStart===3),true);await noMessaging(await page.evaluate(()=>callStart));

  phase='pinning preserves a scrolled list anchor and active search query/caret';
  await page.locator('[data-action=close-setup]').click();await page.setViewportSize({width:412,height:915});await page.locator('[data-action=back]').click();
  await page.evaluate(async()=>{window.originalRows=fixture.inbox.map(row=>({...row}));for(let id=4;id<=29;id++)fixture.inbox.push({_id:100+id,thread_id:id,name:'Fictional friend '+id,address:'+1202555'+String(1000+id),body:'A fictional message '+id,date:100-id,type:1});await onMessagesChanged();});
  await page.locator('#search').fill('fictional');
  await page.evaluate(()=>{const list=document.querySelector('#conversation-list');list.scrollTop=420;window.pinSearch=document.querySelector('#search');pinSearch.focus();pinSearch.setSelectionRange(2,6);const rect=list.getBoundingClientRect(),row=[...list.querySelectorAll('.row')].find(row=>row.getBoundingClientRect().bottom>rect.top+1);window.pinAnchor={thread:row.dataset.thread,offset:row.getBoundingClientRect().top-rect.top};});
  await row(29).dispatchEvent('contextmenu');await apply();
  assert(await page.evaluate(()=>document.querySelector('#search')===pinSearch&&document.activeElement===pinSearch&&pinSearch.value==='fictional'&&pinSearch.selectionStart===2&&pinSearch.selectionEnd===6));
  assert(await page.evaluate(()=>{const list=document.querySelector('#conversation-list'),row=list.querySelector(`.row[data-thread="${pinAnchor.thread}"]`);return Math.abs(row.getBoundingClientRect().top-list.getBoundingClientRect().top-pinAnchor.offset)<2;}),'The same visible row keeps its scroll offset after another row is pinned');
  await page.locator('#search').fill('');await page.evaluate(async()=>{fixture.inbox=originalRows;await onMessagesChanged();document.querySelector('#conversation-list').scrollTop=0;});

  phase='narrow layout and clean screenshots';
  await page.evaluate(async()=>{fixture.inbox.find(row=>row.thread_id===3).name='Jordan Ellis';fixture.inbox.forEach((row,index)=>row.date=Date.now()-index*300000);await onMessagesChanged();});
  await page.evaluate(()=>document.getElementById('toast').classList.remove('show'));await page.screenshot({path:'dist/pins-inbox-preview.png'});
  await choose(2);await page.screenshot({path:'dist/pins-menu-preview.png'});await page.setViewportSize({width:320,height:470});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);const menuBox=await menu().boundingBox();assert(menuBox.x>=0&&menuBox.y>=0&&menuBox.x+menuBox.width<=320&&menuBox.y+menuBox.height<=470);const rowBox=await row(2).boundingBox();assert(rowBox.width>=44&&rowBox.height>=44);
  await page.screenshot({path:'dist/pins-menu-320x470.png'});await page.keyboard.press('Escape');await page.evaluate(()=>document.activeElement?.blur());await page.screenshot({path:'dist/pins-inbox-320x470.png'});assert.equal(await page.locator('.row button,.conversation-more,[data-action=chat-menu]').count(),0);assert.deepEqual(errors,[]);

  phase='browser demo persists pins locally without messaging';
  const demo=await browser.newPage({viewport:{width:412,height:915}});await demo.goto(base);await demo.locator('#draft').waitFor();await demo.locator('[data-action=back]').click();
  await demo.locator('.row[data-thread="4"]').click({button:'right'});await demo.locator('[data-action=pin-chat]').click();await demo.locator('#chat-pin-menu').waitFor({state:'detached'});
  await demo.waitForFunction(()=>localStorage.getItem('reply-pilot-demo-pins')==='[4]');assert.deepEqual(await demo.evaluate(()=>JSON.parse(localStorage.getItem('reply-pilot-demo-pins'))),[4]);await demo.reload();await demo.locator('#draft').waitFor();await demo.locator('[data-action=back]').click();assert.equal(await demo.locator('.conversation-list .row').first().getAttribute('data-thread'),'4');await demo.close();
  console.log('PASS: first-frame optimistic pin/unpin, rapid serialized toggles, stale failure isolation, independent threads, persistence, acknowledged-state rollback/retry, fresh snapshot after stale reads, recent ordering within pinned group, search/focus, escaped names, no three-dot buttons, keyboard/context menu/long press/scroll cancellation, draft/profile/timer/recipient preservation, narrow layout and local demo pins. Fictional data only; no SMS or AI calls.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
