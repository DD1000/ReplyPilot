const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';
(async()=>{const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';try{
 const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];page.on('pageerror',error=>errors.push(error.message));
 await page.addInitScript(()=>{
  const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
  const clone=value=>JSON.parse(JSON.stringify(value));window.calls=[];window.actionReplies=[];window.holdAction=false;window.actionError='';
  window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:true,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:true},jobs:[],inbox:[{thread_id:1,name:'Test friend',address:'+12025550101',body:'Should we get dinner Friday?',date:41000,type:1},{thread_id:2,name:'Other person',address:'+12025550102',body:'Can you share some personal advice?',date:42000,type:1}]};
  window.chats=Object.fromEntries(fixture.inbox.map(row=>[row.thread_id,{base:40+row.thread_id,history:[{_id:40+row.thread_id,type:1,body:row.body,date:row.date}],hasMore:false,draft:null,relationship:{body:'',samples:'',cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:0,engagement:'natural'},replyEligibility:{eligible:true,total:20,owner:10,incoming:10},replyDecision:{decision:'no_reply',reason:row.thread_id===1?'plans_need_input':'needs_review',message:row.thread_id===1?'Plans need your input. Automatic replies are paused until you send a reply.':'This reply needs your own judgment.'},replyHold:row.thread_id===1?{reason:'plans_need_input',base:41,message:'Plans need your input. Automatic replies are paused until you send a reply.',incoming:row.body}:null,attentionActions:{jokeToken:'joke-'+row.thread_id,delayToken:row.thread_id===1?'delay-1':'',delayText:"I'll get back to you on that."}}]));
  window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});const done=(value,error=null)=>nativeResult(id,clone(value),error);const reply=()=>{
   if(action==='snapshot')done(fixture);else if(action==='conversation')done(chats[p.thread]);
   else if(action==='saveDraft'){chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};done({});}
   else if(action==='attentionAction'){
    if(actionError){done(null,actionError);return;}
    const entry=Object.entries(chats).find(([,chat])=>[chat.attentionActions?.jokeToken,chat.attentionActions?.delayToken].includes(p.token));
    if(!entry){done(null,'This action is no longer current. Open the conversation.');return;}
    entry[1].attentionActions={};done({status:'queued',message:p.token.startsWith('delay')?'Delay request queued. Check the conversation for its result.':'A revised draft was requested for your review.'});
   }else done({});
  };if(action==='attentionAction'&&holdAction)actionReplies.push(reply);else setTimeout(reply,0);}};
 });
 const button=kind=>page.locator(`[data-action=attention-action][data-attention-kind=${kind}]`);
 const open=async thread=>{await page.locator(`.row[data-thread="${thread}"]`).click();await page.locator('#draft').waitFor();};
 const count=action=>page.evaluate(action=>calls.filter(call=>call.action===action).length,action);
 const noMessaging=async()=>assert.deepEqual(await page.evaluate(()=>calls.filter(call=>['generate','quick','approve','sendNow','testOpenAI'].includes(call.action))),[]);
 await page.goto(url);await open(1);
 phase='held actions explain exact effects and never run on render or reopen';
 assert(await button('joke').isEnabled());assert(await button('delay').isEnabled());assert.match(await page.locator('.attention-help').textContent(),/Joke reconsiders this as a joke and prepares a reply for review/);assert((await page.locator('.attention-help').textContent()).includes('Delay sends “I\'ll get back to you on that.”'));
 assert.equal(await page.locator('[data-token]').count(),0);await noMessaging();assert.equal(await count('attentionAction'),0);
 await page.locator('[data-action=back]').click();await open(1);await page.evaluate(()=>onMessagesChanged());assert.equal(await count('attentionAction'),0);
 phase='phone and keyboard-sized actions remain visible with a usable composer';
 for(const viewport of [{width:320,height:640},{width:412,height:470}]){await page.setViewportSize(viewport);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));const composer=await page.locator('.composer').boundingBox(),timeline=await page.locator('.timeline').boundingBox();assert(composer.y>=0&&composer.y+composer.height<=viewport.height+1,JSON.stringify({viewport,composer}));assert(timeline.height>30,JSON.stringify(timeline));assert(await button('delay').isVisible());await page.screenshot({path:`dist/attention-actions-${viewport.width}x${viewport.height}.png`});}
 phase='manual text and focus survive attention-token-only updates';
 await page.locator('#draft').fill('My own reply stays here');await page.locator('#draft').focus();await page.evaluate(()=>{window.editor=document.querySelector('#draft');editor.setSelectionRange(6,6);});
 await page.evaluate(async()=>{chats[1].attentionActions={jokeToken:'joke-new',delayToken:'delay-new',delayText:"I'll get back to you on that."};await onMessagesChanged();});
 assert(await button('joke').isDisabled());assert(await button('delay').isDisabled());assert(await page.locator('#accept').isEnabled());assert.equal(await page.locator('#draft').inputValue(),'My own reply stays here');assert(await page.evaluate(()=>document.querySelector('#draft')===editor&&document.activeElement===editor&&editor.selectionStart===6));assert.match(await page.locator('.attention-help').textContent(),/Send or clear your draft/);
 phase='one tap sends only the opaque token and double taps cannot queue twice';
 await page.locator('#draft').fill('');await page.evaluate(()=>{holdAction=true;});await button('joke').click();await page.waitForFunction(()=>actionReplies.length===1);assert(await button('joke').isDisabled());assert(await button('delay').isDisabled());
 await page.evaluate(()=>document.querySelector('[data-attention-kind=joke]').dispatchEvent(new MouseEvent('click',{bubbles:true})));assert.equal(await count('attentionAction'),1);assert.deepEqual(await page.evaluate(()=>calls.find(call=>call.action==='attentionAction').p),{token:'joke-new'});
 await page.evaluate(()=>{holdAction=false;actionReplies.shift()();});await page.waitForFunction(()=>!document.querySelector('.attention-actions')&&!document.querySelector('#draft').disabled);assert.equal(await page.locator('#draft').inputValue(),'');assert.match(await page.locator('#toast').textContent(),/requested for your review/);await noMessaging();
 phase='a native draft arrives for review; UI does not send it';
 await page.evaluate(async()=>{chats[1].draft={body:'Okay, you got me with that one',engine:'OpenAI · awaiting your review',alternatives:'[]'};await onMessagesChanged();});await page.waitForFunction(()=>document.querySelector('#draft').value==='Okay, you got me with that one');assert(await page.locator('#accept').isEnabled());await noMessaging();
 phase='Delay acceptance is not presented as a sent message';
 await page.evaluate(async()=>{chats[1].draft=null;chats[1].attentionActions={jokeToken:'joke-next',delayToken:'delay-next'};await onMessagesChanged();});await page.locator('#draft').fill('');await button('delay').click();await page.waitForFunction(()=>calls.filter(call=>call.action==='attentionAction').length===2&&!document.querySelector('.attention-actions')&&!document.querySelector('#draft').disabled);
 assert.deepEqual(await page.evaluate(()=>calls.filter(call=>call.action==='attentionAction').at(-1).p),{token:'delay-next'});assert.match(await page.locator('#toast').textContent(),/queued/);assert.doesNotMatch(await page.locator('#toast').textContent(),/Message sent|reply timer has started/);assert.equal(await page.locator('#draft').inputValue(),'');assert(await page.locator('#reply-decision').isVisible());await noMessaging();
 phase='needs-review has Joke only; native errors stay visible without false success';
 await page.locator('[data-action=back]').click();await open(2);assert(await button('joke').isEnabled());assert.equal(await button('delay').count(),0);await page.evaluate(()=>{actionError='The action expired. Open the latest message.';});await button('joke').click();await page.waitForFunction(()=>document.querySelector('#toast').textContent.includes('action expired')&&!document.querySelector('#draft').disabled);assert(await button('joke').isEnabled());assert.equal(await page.locator('#draft').inputValue(),'');await noMessaging();
 phase='stale responses cannot navigate, replace text, or toast over another conversation';
 await page.evaluate(()=>{actionError='';holdAction=true;});await button('joke').click();await page.waitForFunction(()=>actionReplies.length===1);await page.locator('[data-action=back]').click();await open(1);await page.locator('#draft').fill('Unrelated draft');await page.evaluate(()=>{document.querySelector('#toast').textContent='Other conversation';holdAction=false;actionReplies.shift()();});await page.waitForFunction(()=>attentionRequest===null);
 assert.equal(await page.locator('.chat-person h2').textContent(),'Test friend');assert.equal(await page.locator('#draft').inputValue(),'Unrelated draft');assert.equal(await page.locator('#toast').textContent(),'Other conversation');await noMessaging();assert.deepEqual(errors,[]);
 console.log('PASS: held Joke/Delay actions and exact Delay wording; no render/reopen generation; token-only requests, double-tap guard, manual draft/focus preservation, queued-vs-sent honesty, review-only revised draft, needs-review Joke only, native errors, stale cross-thread results and320px/keyboard layouts. Fictional bridge only.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}})().catch(error=>{console.error(error);process.exit(1);});
