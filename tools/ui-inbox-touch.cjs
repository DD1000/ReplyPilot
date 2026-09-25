const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const localAssets=require('./ui-local-assets.cjs');
const base=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true,isMobile:true}),errors=[];
  await localAssets(page,base);page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value));
   window.calls=[];window.chatReplies=[];
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:false,inAppSuggestions:false,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:false},jobs:[],inbox:[
    {_id:51,thread_id:1,name:'Fictional Alex',address:'+12025550101',body:'First fictional message',date:Date.now(),type:1},
    {_id:52,thread_id:2,name:'Fictional Casey',address:'+12025550102',body:'Second fictional message',date:Date.now()-1000,type:1},
    {_id:53,thread_id:3,name:'Fictional Jordan',address:'+12025550103',body:'Third fictional message',date:Date.now()-2000,type:1}
   ]};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});const finish=value=>nativeResult(id,clone(value),null);
    if(action==='conversation'){chatReplies.push({id,thread:p.thread});return;}
    setTimeout(()=>finish(action==='snapshot'?fixture:{}),0);
   }};
  });
  const row=id=>page.locator(`.row[data-thread="${id}"]`);
  const center=async id=>{const r=await row(id).boundingBox();return {x:r.x+r.width/2,y:r.y+r.height/2};};
  const cdp=await page.context().newCDPSession(page);
  const touch=async(type,point)=>cdp.send('Input.dispatchTouchEvent',{type,touchPoints:point?[{...point,id:1}]:[]});
  const frames=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const assertChat=async id=>{await frames();assert.equal(await page.locator('.timeline').getAttribute('data-thread'),String(id),'A chat shell must open while the native conversation promise is still pending');assert.equal(await page.locator('#draft').count(),1);assert.equal(await page.locator('.conversation .spinner').count(),0);};
  const fresh=async()=>{await page.goto(base);await row(1).waitFor();await page.waitForFunction(()=>inboxBootstrap.authoritative);};
  await fresh();

  phase='native inbox update between finger-down and finger-up preserves the original tap';
  const p=await center(2);await touch('touchStart',p);
  await page.evaluate(async()=>{window.touchedRow=document.querySelector('.row[data-thread="2"]');fixture.inbox[0].body='Updated in the background';await onMessagesChanged();});
  assert.equal(await page.evaluate(()=>touchedRow.isConnected),true,'A provider refresh must not remove the active touch target');
  await touch('touchEnd');await assertChat(2);
  assert.equal(await page.evaluate(()=>chatReplies.length),1);

  phase='full render while finger is down preserves its row until click is dispatched';
  await fresh();const full=await center(1);await touch('touchStart',full);
  await page.evaluate(()=>{window.touchedRow=document.querySelector('.row[data-thread="1"]');render();});
  assert.equal(await page.evaluate(()=>touchedRow.isConnected),true);
  await touch('touchEnd');await assertChat(1);

  phase='cancelled scroll does not swallow the next fresh tap on the same conversation';
  await fresh();const cancelled=await center(2);await touch('touchStart',cancelled);await touch('touchCancel');
  assert.equal(await page.locator('#draft').count(),0);
  await page.touchscreen.tap(cancelled.x,cancelled.y);await assertChat(2);

  phase='a scroll gesture never opens a chat, and the immediate next tap works';
  await fresh();const scrolled=await center(2);await touch('touchStart',scrolled);
  await page.locator('.conversation-list').dispatchEvent('scroll');await touch('touchEnd');await frames();
  assert.equal(await page.locator('#draft').count(),0);
  await page.touchscreen.tap(scrolled.x,scrolled.y);await assertChat(2);

  phase='long press still opens pin options and does not open the chat on release';
  await fresh();const held=await center(2);await touch('touchStart',held);
  await page.locator('#chat-pin-menu').waitFor();await touch('touchEnd');await frames();
  assert.equal(await page.locator('#draft').count(),0);assert.equal(await page.locator('#chat-pin-menu').count(),1);
  await page.locator('[data-action="close-chat-menu"]').last().tap();
  await row(2).tap();await assertChat(2);

  phase='access revocation while pressed immediately clears the private inbox and cannot open it';
  await fresh();const revoked=await center(1);await touch('touchStart',revoked);
  await page.evaluate(()=>onMessageAccessChanged({readSms:false,defaultSms:false,contacts:false}));
  assert.equal(await page.locator('.row').count(),0);await touch('touchEnd');await frames();assert.equal(await page.locator('#draft').count(),0);

  phase='keyboard opening and provider reads are independent of a pending previous conversation';
  await fresh();await row(1).focus();await page.keyboard.press('Enter');await assertChat(1);
  await page.getByRole('button',{name:'Back to conversations',exact:true}).tap();await row(3).tap();await assertChat(3);
  assert.deepEqual(await page.evaluate(()=>calls.filter(call=>['sendNow','sendMms','approve','generate','draftMmsText','suggestReply','analyzeMedia','saveDraft','saveProfile','pinChat'].includes(call.action))),[]);
  assert.deepEqual(errors,[]);
  console.log('PASS: real touch survives provider/full renders, immediate retap after cancellation/scroll, long-press pin release suppression, access revocation, keyboard opening and pending native reads. Fictional messages only; no SMS or AI calls.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
