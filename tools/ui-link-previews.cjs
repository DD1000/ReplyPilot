const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const baseURL=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';
const imageToken='12345678-1234-4234-8234-123456789abc';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[],externalRequests=[];
  page.on('pageerror',error=>errors.push(error.message));
  // The only image bytes below are a fictional local one-pixel fixture. All
  // remote URLs are blocked; Native never accesses messages or the network.
  await page.route('**/*',async route=>{
   const url=new URL(route.request().url());
   if(url.origin===new URL(baseURL).origin){
    if(url.pathname.startsWith('/link-preview/'))return route.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII=','base64')});
    return route.continue();
   }
   externalRequests.push(url.href);return route.abort();
  });
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const clone=value=>JSON.parse(JSON.stringify(value)),now=Date.now();
   const sms=(id,body,type=1)=>({_id:id,kind:'sms',body,type,date:now+id});
   window.histories={
    1:[sms(101,'Read https://example.test/story?part=1&x=2. Also https://other.test/extra!'),sms(102,'Here’s https://social.test/private'),sms(103,'Tell me what you think')],
    2:[sms(201,'Look at https://safety.test/picture'),sms(202,'No rush')],
    3:Array.from({length:94},(_,i)=>sms(3001+i,`Link ${i+1}: https://many.test/post/${i+1}`,i%3===0?2:1)),
    4:[sms(401,'Use http://plain.test/path. Not javascript:alert(1) data:text/html,hello ftp://files.test/a https://user:pass@private.test https:// https://bad.test\\route'),sms(402,'Thanks')],
    5:[{_id:501,kind:'mms',m_type:132,type:1,date:now+501,body:'Media message',parts:[{_id:9001,ct:'text/plain',text:'Caption: (https://caption.test/post). And https://second-caption.test/a'}]},sms(502,'Caption context')],
   };
   window.calls=[];window.previewReplies=[];window.chatReplies=[];window.saveReplies=[];window.snapshotReplies=[];
   window.holdPreviews=true;window.holdChats=location.search.includes('held-bootstrap');window.holdSaves=false;window.holdSnapshots=location.search.includes('held-bootstrap');window.previewInFlight=0;window.maxPreviewInFlight=0;
   window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:false,matchMyStyle:true,inAppSuggestions:false,linkPreviews:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:false},jobs:[],inbox:Object.entries(histories).map(([thread,history])=>({thread_id:Number(thread),...history.at(-1),name:`Link friend ${thread}`,address:`+1202555100${thread}`,read:1}))};
   window.chats=Object.fromEntries(Object.entries(histories).map(([thread,history])=>[thread,{base:history.at(-1)._id,history,smsLatest:history.at(-1),latest:{kind:'sms',id:history.at(-1)._id,key:`sms:${history.at(-1)._id}`,date:history.at(-1).date},hasMore:false,hasOlder:false,readOnly:false,draft:null,relationship:{cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300,body:'',samples:''}}]));
   window.releasePreview=(url,data={},error=null)=>{const index=previewReplies.findIndex(item=>item.url===url);if(index<0)throw new Error(`No held preview ${url}`);previewReplies.splice(index,1)[0].reply(data,error);};
   window.releaseAllPreviews=()=>previewReplies.splice(0).forEach(item=>item.reply({available:false}));
   window.releaseChat=thread=>{const index=chatReplies.findIndex(item=>item.thread===thread);if(index<0)throw new Error(`No held chat ${thread}`);chatReplies.splice(index,1)[0].reply();};
   window.releaseSaves=()=>saveReplies.splice(0).forEach(reply=>reply());
   window.releaseSnapshot=()=>snapshotReplies.shift()();
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);const call={action,p};calls.push(call);const finish=(result,error=null)=>nativeResult(id,clone(result),error);
    if(action==='launchInbox'){setTimeout(()=>finish({inbox:[{...fixture.inbox[0],_id:101,body:histories[1][0].body}],savedAt:Date.now(),revision:1,access:{readSms:true,defaultSms:true,contacts:true}}),0);return;}
    if(action==='snapshot'){const captured=clone(fixture),reply=()=>finish(captured);if(holdSnapshots)snapshotReplies.push(reply);else setTimeout(reply,0);return;}
    if(action==='conversation'){const captured=clone(chats[p.thread]),reply=()=>finish(captured);if(holdChats)chatReplies.push({thread:p.thread,reply});else setTimeout(reply,0);return;}
    if(action==='prefetchHistory'){setTimeout(()=>finish({conversations:[]}),0);return;}
    if(action==='saveDraft'){const reply=()=>{chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};finish({});};if(holdSaves)saveReplies.push(reply);else setTimeout(reply,0);return;}
    if(action==='linkPreview'){
     const card=[...document.querySelectorAll('.link-preview')].find(node=>Number(node.dataset.linkId)===p.id&&Number(node.dataset.linkThread)===p.thread);
     const r=card?.getBoundingClientRect(),pane=card?.closest('.timeline,.media-history')?.getBoundingClientRect();
     call.visible=!!r&&r.bottom>Math.max(0,pane?.top||0)&&r.top<Math.min(innerHeight,pane?.bottom||innerHeight);
     previewInFlight++;maxPreviewInFlight=Math.max(maxPreviewInFlight,previewInFlight);
     const reply=(data={},error=null)=>{previewInFlight--;finish({url:p.url,available:true,title:`Fictional page ${p.id}`,description:'A short public page description.',siteName:'Example site',host:new URL(p.url).host,revision:1,...data},error);};
     if(holdPreviews)previewReplies.push({url:p.url,reply});else setTimeout(()=>reply(),0);return;
    }
    if(action==='openLink'){setTimeout(()=>finish({opened:true}),0);return;}
    setTimeout(()=>finish({}),0);
   }};
  });
  const row=thread=>page.locator(`.row[data-thread="${thread}"]`);
  const card=id=>page.locator(`.link-preview[data-link-id="${id}"]`);
  const count=action=>page.evaluate(action=>calls.filter(call=>call.action===action).length,action);
  const settle=()=>page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const back=()=>page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  const open=async thread=>{await row(thread).click();await page.waitForFunction(()=>chatReady());};
  const held=url=>page.waitForFunction(url=>previewReplies.some(item=>item.url===url),url);
  const load=async()=>{await page.goto(baseURL);await row(1).waitFor();await page.waitForFunction(()=>inboxBootstrap.authoritative);};
  await load();assert.equal(await count('linkPreview'),0,'Inbox rows and history warming never fetch link metadata');

  phase='opening and typing never wait for visible link metadata';
  await open(1);await card(101).scrollIntoViewIfNeeded();await held('https://example.test/story?part=1&x=2');
  assert(await page.locator('#draft').isEnabled());assert.equal(await page.locator('.chat-load-state').count(),0);
  assert.equal(await page.locator('[data-message-key="sms:101"] .message-link').count(),2);
  assert.equal(await page.locator('[data-message-key="sms:101"] .link-preview').count(),1);
  assert.equal(await page.locator('.message-link[data-url="https://other.test/extra"]').count(),1);
  assert.equal(await page.evaluate(()=>calls.some(call=>call.action==='linkPreview'&&call.p.url==='https://other.test/extra')),false);
  await page.locator('#draft').fill('I’m still writing my own reply');
  await page.evaluate(()=>{window.savedEditor=document.querySelector('#draft');savedEditor.setSelectionRange(4,9);window.savedTimeline=document.querySelector('.timeline');window.savedScroll=savedTimeline.scrollTop;window.savedPosition=timelinePosition(savedTimeline);});
  await page.evaluate(({token})=>releasePreview('https://example.test/story?part=1&x=2',{title:'A small guide to weekend walks',description:'A fictional public page with a helpful summary.',siteName:'Example Journal',imageUrl:`https://app.replypilot.local/link-preview/${token}`}),{token:imageToken});
  await card(101).locator('.link-preview-title').waitFor();await settle();
  assert.equal(await page.evaluate(()=>document.querySelector('#draft')===savedEditor&&document.activeElement===savedEditor&&savedEditor.selectionStart===4&&savedEditor.selectionEnd===9),true);
  assert.equal(await page.locator('#draft').inputValue(),'I’m still writing my own reply');
  assert.equal(await page.evaluate(()=>document.querySelector('.timeline')===savedTimeline),true);
  assert.equal(await page.evaluate(()=>{const position=timelinePosition();return savedPosition.atBottom?position.atBottom:position.id===savedPosition.id&&Math.abs(position.offset-savedPosition.offset)<=1;}),true,'Metadata preserves the reading anchor or keeps a bottom-pinned conversation at the bottom');
  assert.equal(await card(101).locator('img').getAttribute('src'),`/link-preview/${imageToken}`);
  assert.equal(await card(101).locator('img').getAttribute('loading'),'lazy');
  await card(102).scrollIntoViewIfNeeded();await held('https://social.test/private');await page.evaluate(()=>releasePreview('https://social.test/private',{available:false,title:'',description:'',siteName:''}));await settle();
  assert((await card(102).innerText()).includes('social.test'));assert.equal(await card(102).locator('.link-preview-title').count(),0);
  assert.equal(await card(102).locator('.link-preview-site').count(),0,'Unavailable cards show the host once, without a duplicate site label');
  assert.equal((await card(102).innerText()).split('social.test').length-1,1);

  phase='inline links and cards open their exact original URLs without waiting for draft saves';
  await page.evaluate(()=>{holdSaves=true;});await page.locator('#draft').fill('Unfinished wording must not block opening a link');await page.waitForFunction(()=>saveReplies.length>0);
  await page.locator('.message-link[data-url="https://other.test/extra"]').click();
  await page.waitForFunction(()=>calls.some(call=>call.action==='openLink'&&call.p.url==='https://other.test/extra'));
  await card(101).click();await page.waitForFunction(()=>calls.some(call=>call.action==='openLink'&&call.p.url==='https://example.test/story?part=1&x=2'));
  assert(await page.evaluate(()=>saveReplies.length>0));await page.evaluate(()=>{holdSaves=false;releaseSaves();});
  for(const viewport of [{width:412,height:915},{width:320,height:470}]){
   await page.setViewportSize(viewport);await card(101).scrollIntoViewIfNeeded();await settle();
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   const composer=await page.locator('.composer').boundingBox();assert(composer.y+composer.height<=viewport.height+1);
   await page.screenshot({path:`dist/link-previews-${viewport.width}x${viewport.height}.png`});
  }

  phase='metadata is text, remote images are rejected and mismatched URLs cannot redirect cards';
  await page.setViewportSize({width:412,height:915});await back();await open(2);await held('https://safety.test/picture');
  await page.evaluate(()=>releasePreview('https://safety.test/picture',{title:'<img src=x onerror="window.injected=true">',description:'</button><script>window.injected=true</script>',siteName:'A & B <svg onload="window.injected=true">',imageUrl:'https://tracking.invalid/private.jpg'}));await card(201).locator('.link-preview-title').waitFor();
  assert.equal(await card(201).locator('.link-preview-title').innerText(),'<img src=x onerror="window.injected=true">');
  assert.equal(await card(201).locator('img,script').count(),0);assert.equal(await page.evaluate(()=>window.injected),undefined);
  assert.equal(await card(201).getAttribute('data-url'),'https://safety.test/picture');
  await page.evaluate(()=>{clearLinkPreviews();scheduleLinkPreviews();});await held('https://safety.test/picture');
  await page.evaluate(()=>releasePreview('https://safety.test/picture',{url:'https://wrong-destination.test/',title:'Wrong destination'}));await settle();
  assert.equal(await card(201).locator('.link-preview-title').count(),0);assert.equal(await card(201).getAttribute('data-url'),'https://safety.test/picture');

  phase='HTTP remains clickable without insecure preview fetching and invalid schemes stay plain';
  await back();await open(4);await settle();
  assert.deepEqual(await page.locator('.message-link').evaluateAll(nodes=>nodes.map(node=>node.dataset.url)),['http://plain.test/path']);
  assert.equal(await page.evaluate(()=>calls.some(call=>call.action==='linkPreview'&&call.p.thread===4)),false);
  await page.locator('.message-link').click();await page.waitForFunction(()=>calls.some(call=>call.action==='openLink'&&call.p.url==='http://plain.test/path'));
  phase='MMS text captions use the same first-link-only preview and native message binding';
  await back();await open(5);await card(501).scrollIntoViewIfNeeded();await held('https://caption.test/post');
  assert.deepEqual(await page.evaluate(()=>calls.find(call=>call.action==='linkPreview'&&call.p.thread===5).p),{thread:5,kind:'mms',id:501,url:'https://caption.test/post'});
  assert.equal(await page.locator('[data-message-key="mms:501"] .message-link').count(),2);

  phase='late results cannot insert content in another chat or survive access revocation';
  await back();await open(2);await page.evaluate(()=>releasePreview('https://caption.test/post',{title:'Stale private caption preview'}));await settle();
  assert(!(await page.locator('#app').innerText()).includes('Stale private caption preview'));
  await page.evaluate(()=>{clearLinkPreviews();scheduleLinkPreviews();});await held('https://safety.test/picture');
  await page.evaluate(()=>{fixture.permissions=false;fixture.defaultSms=false;onMessageAccessChanged({readSms:false,defaultSms:false,contacts:false});releasePreview('https://safety.test/picture',{title:'Must not return after permission loss'});});await settle();
  assert.equal(await page.locator('.link-preview').count(),0);assert.equal(await page.evaluate(()=>linkPreviewCache.size),0);

  phase='requests are visible-only, at most two active, and completed display cache stays bounded';
  await load();await open(3);await page.waitForFunction(()=>previewReplies.length===2);
  assert.equal(await page.evaluate(()=>maxPreviewInFlight),2);assert(await page.evaluate(()=>calls.filter(call=>call.action==='linkPreview').every(call=>call.visible)));
  assert((await count('linkPreview'))<10,'Opening a long chat cannot fetch every historical link');
  await page.evaluate(()=>{holdPreviews=false;releaseAllPreviews();});
  for(let id=3001;id<=3094;id++){
   await card(id).evaluate(node=>node.scrollIntoView({block:'center'}));
   await page.waitForFunction(id=>calls.some(call=>call.action==='linkPreview'&&call.p.id===id),id);
  }
  await page.waitForFunction(()=>linkPreviewActive===0&&linkPreviewQueue.length===0);await settle();
  assert(await page.evaluate(()=>maxPreviewInFlight<=2));assert(await page.evaluate(()=>linkPreviewCache.size<=80));
  assert(await page.evaluate(()=>calls.filter(call=>call.action==='linkPreview').every(call=>call.visible)));

  phase='disabling previews stops fetches while links stay usable';
  await page.evaluate(()=>{fixture.linkPreviews=false;clearLinkPreviews();});
  await page.evaluate(()=>onNativeResume());await back();const beforeDisabled=await count('linkPreview');await open(1);await settle();await page.waitForTimeout(100);
  assert.equal(await count('linkPreview'),beforeDisabled);assert.equal(await page.locator('.message-link').count(),3);

  phase='launch-cache and pending fresh conversations never trigger preview network work';
  await page.goto(baseURL+'?held-bootstrap=1');await row(1).waitFor();
  assert.equal(await page.evaluate(()=>inboxBootstrap.authoritative),false);await row(1).click();await page.locator('#draft').waitFor();await settle();assert.equal(await count('linkPreview'),0);
  await page.evaluate(()=>releaseChat(1));await settle();assert.equal(await count('linkPreview'),0,'A live chat still waits for the independent startup snapshot');
  assert.equal(await page.evaluate(()=>calls.some(call=>['generate','suggestReply','sendNow','sendMms','approve'].includes(call.action))),false);
  assert.deepEqual(externalRequests,[],'No remote request may escape the fictional native bridge');assert.deepEqual(errors,[]);
  console.log('PASS: visible-only bound link requests; click/card URL binding; first-link-only preview and multiple anchors; escaped metadata/local lazy images; unavailable social and HTTP fallback; no malformed links; MMS caption binding; typing/focus/scroll preserved; stale/access-revoked results discarded; concurrency2/cache80; preview opt-out and launch freshness gates; narrow/phone screenshots. Native network/SMS/AI fully mocked.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
