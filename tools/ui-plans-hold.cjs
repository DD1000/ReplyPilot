const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const baseURL=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true,isMobile:true}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  // All provider, model, notification and send operations terminate in this fake
  // bridge. The test never contacts a model, carrier, contact book or real phone.
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   const copy=value=>JSON.parse(JSON.stringify(value));window.calls=[];window.failSend=false;
   window.planText='Want to get dinner Friday? Does 7 work for you?';
   window.hold={reason:'plans_need_input',base:41,message:'Plans need your input. Automatic replies are paused until you send a reply.',incoming:planText};
   window.fixture={defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:true,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:true,url:'https://example.invalid'},jobs:[],inbox:[{thread_id:1,name:'Test friend',address:'+12025550147',body:'Let me know',date:45000,type:1,read:1},{thread_id:2,name:'Other friend',address:'+12025550148',body:'One more thing…',date:46000,type:1,read:1}]};
   const profile={body:'Old friends.',samples:'',cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:300,autoDelayMode:'fixed',autoDelayMin:300,autoDelayMax:1800,tone:'Natural'};
   window.chats={1:{replyEligibility:{eligible:true,total:20,owner:10,incoming:10},base:45,history:[{_id:41,type:1,body:planText,date:41000},{_id:45,type:1,body:'Let me know',date:45000}],hasMore:false,draft:null,replyDecision:null,replyHold:copy(hold),relationship:copy(profile)},2:{replyEligibility:{eligible:true,total:20,owner:10,incoming:10},base:46,history:[{_id:46,type:1,body:'One more thing…',date:46000}],hasMore:false,draft:null,replyDecision:null,replyHold:null,relationship:{...profile,autoDelay:0}}};
   window.incoming=(thread,body)=>{const chat=chats[thread],base=++chat.base;chat.history.push({_id:base,type:1,body,date:base*1000});chat.draft=null;chat.replyDecision=null;Object.assign(fixture.inbox.find(row=>row.thread_id===thread),{body,type:1,date:base*1000});};
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={},error=null;
    if(action==='snapshot')result=fixture;
    else if(action==='conversation')result=chats[p.thread];
    else if(action==='saveDraft')chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};
    else if(action==='saveProfile'){chats[p.thread].relationship={...p,revision:1};result=chats[p.thread].relationship;}
    else if(action==='generate')result={waiting:true};
    else if(action==='sendNow'){
     if(failSend)error='The carrier could not accept this reply. Your draft is saved.';
     else{const chat=chats[p.thread],base=++chat.base;chat.history.push({_id:base,type:2,body:p.body,date:base*1000});chat.draft=null;chat.replyDecision=null;Object.assign(fixture.inbox.find(row=>row.thread_id===p.thread),{body:p.body,type:2,date:base*1000});result={id:base,status:'sent'};}
     // Provider hold is deliberately left intact until the test releases it.
    }else if(action==='testOpenAI')result={decision:'no_reply',reason:'plans_need_input',body:'',engine:'Fixture',elapsedMs:20};
    setTimeout(()=>nativeResult(id,copy(result),error),0);
   }};
  });
  const count=action=>page.evaluate(action=>calls.filter(call=>call.action===action).length,action);
  const back=()=>page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  const open=async thread=>{await page.locator(`.row[data-thread="${thread}"]`).click();await page.locator('#draft').waitFor();};
  const noAutomatic=async()=>{assert.equal(await count('generate'),0);assert.equal(await count('quick'),0);assert.equal(await count('approve'),0);};
  const assertHold=async()=>{assert.equal(await page.locator('#reply-decision strong').textContent(),'Plans need you');assert.equal(await page.locator('#reply-decision').getAttribute('role'),'status');assert((await page.locator('#reply-decision').textContent()).includes('until you send a reply'));assert(await page.locator('#draft').isEnabled());assert.equal(await page.locator('.schedule-send').textContent(),'Plans need your reply');};
  await page.goto(baseURL);await page.locator('.row').first().waitFor();await open(1);

  phase='persistent hold overrides an absent per-message decision and stays across reopening';
  await assertHold();assert.equal(await page.locator('.held-message p').textContent(),await page.evaluate(()=>planText));assert.equal(await page.locator('.local-quick').count(),0);assert(await page.getByRole('button',{name:'Send message',exact:true}).isDisabled());await noAutomatic();
  for(let i=0;i<3;i++){await back();await open(1);await page.evaluate(()=>onMessagesChanged());await assertHold();}
  assert.equal(await count('sendNow'),0);await noAutomatic();
  await page.evaluate(async()=>{incoming(1,'Are you there?');chats[1].history=chats[1].history.filter(message=>message._id!==41);await onMessagesChanged();});
  await assertHold();assert.equal(await page.locator('.held-message p').textContent(),await page.evaluate(()=>planText),'Original planning message remains available outside the latest history page');await back();await open(1);await noAutomatic();
  await page.evaluate(async()=>{chats[1].replyHold.incoming='<img src=x onerror="window.injected=true">';await onMessagesChanged();});assert.equal(await page.locator('.held-message img').count(),0);assert.equal(await page.evaluate(()=>window.injected),undefined);assert.equal(await page.locator('.held-message p').textContent(),'<img src=x onerror="window.injected=true">');
  await page.evaluate(async()=>{chats[1].replyHold.incoming=planText;await onMessagesChanged();});

  phase='profile changes cannot dismiss the hold or opt another person in';
  await page.locator('#reply-setup').click();assert(await page.locator('#profile-reply-hold').isVisible());await page.locator('#toast.show').waitFor({state:'hidden'});await page.screenshot({path:'dist/plans-hold-profile-preview.png'});assert((await page.locator('#relationship-status').textContent()).includes('paused until you send a reply'));
  await page.locator('#relationship-context').fill('Old friends, no plans without checking with me');await page.locator('[name=person-mode][value=instant]').check();await page.locator('[data-action=save-relationship]').click();await page.waitForFunction(()=>document.querySelector('#relationship-status')?.textContent.includes('paused until you send a reply')&&document.querySelector('[data-action=save-relationship]')?.disabled);
  assert.equal(await page.evaluate(()=>chats[1].replyHold.reason),'plans_need_input');assert.equal(await page.evaluate(()=>chats[1].relationship.autoDelay),0);assert.equal(await page.locator('[data-action=resume-replies]').count(),0);await page.locator('[data-action=close-setup]').click();await noAutomatic();
  await page.locator('#toast.show').waitFor({state:'hidden'});await page.screenshot({path:'dist/plans-hold-preview.png'});

  phase='manual writing remains available, with stable focus and a keyboard-sized layout';
  await page.locator('#draft').fill('Friday at 7 works for me');await page.locator('#draft').focus();await page.evaluate(()=>{window.savedEditor=document.querySelector('#draft');savedEditor.setSelectionRange(7,7);});await page.evaluate(()=>onMessagesChanged());assert.equal(await page.evaluate(()=>savedEditor===document.querySelector('#draft')&&document.activeElement===savedEditor&&savedEditor.selectionStart===7),true);await assertHold();assert(await page.getByRole('button',{name:'Send message',exact:true}).isEnabled());
  for(const viewport of [{width:320,height:640},{width:412,height:470}]){
   await page.setViewportSize(viewport);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));const composer=await page.locator('.composer').boundingBox(),timeline=await page.locator('.timeline').boundingBox();assert(composer.y>=0&&composer.y+composer.height<=viewport.height+1,JSON.stringify({viewport,composer}));assert(timeline.height>45,JSON.stringify(timeline));await page.screenshot({path:`dist/plans-hold-${viewport.width}x${viewport.height}.png`});
  }

  phase='edits and rejected sends retain hold; only provider clearance removes it';
  await page.evaluate(()=>{failSend=true;});await page.getByRole('button',{name:'Send message',exact:true}).click();await page.waitForFunction(()=>calls.filter(call=>call.action==='sendNow').length===1&&!document.querySelector('#draft').disabled);await assertHold();assert.equal(await page.locator('#draft').inputValue(),'Friday at 7 works for me');
  await page.evaluate(()=>{failSend=false;});await page.getByRole('button',{name:'Send message',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#draft')?.value==='');await assertHold();assert.equal(await count('sendNow'),2);await noAutomatic();
  await page.evaluate(async()=>{chats[1].replyHold=null;await onMessagesChanged();});await page.waitForFunction(()=>!document.querySelector('#reply-decision'));assert.equal(await page.locator('.schedule-send').textContent(),'Autopilot enabled');assert.equal(await page.locator('#draft').inputValue(),'');await noAutomatic();

  phase='a hold-only update arrives and clears without replacing an edited composer';
  await page.evaluate(async()=>{incoming(1,'Can we move dinner to Saturday?');await onMessagesChanged();});await page.locator('#draft').fill('Let me check my calendar');await page.locator('#draft').focus();await page.evaluate(()=>{window.heldEditor=document.querySelector('#draft');heldEditor.setSelectionRange(5,5);});
  await page.evaluate(async()=>{chats[1].replyHold={...hold,base:chats[1].base,incoming:'Can we move dinner to Saturday?'};chats[1].replyDecision=null;await onMessagesChanged();});await assertHold();assert.equal(await page.evaluate(()=>heldEditor===document.querySelector('#draft')&&document.activeElement===heldEditor&&heldEditor.selectionStart===5),true);assert.equal(await page.locator('#draft').inputValue(),'Let me check my calendar');
  await page.locator('#reply-setup').click();await page.locator('#relationship-context').fill('Unsaved personal context');await page.locator('#relationship-context').focus();await page.evaluate(()=>{window.heldContext=document.querySelector('#relationship-context');});await page.evaluate(async()=>{chats[1].replyHold=null;await onMessagesChanged();});assert.equal(await page.locator('#profile-reply-hold').isVisible(),false);assert.equal(await page.evaluate(()=>heldContext===document.querySelector('#relationship-context')),true);assert.equal(await page.locator('#relationship-context').inputValue(),'Unsaved personal context');await page.locator('[data-action=close-setup]').click();await noAutomatic();

  phase='an automatic quiet-period response has no false error or fallback reply';
  await back();await open(2);await page.waitForFunction(()=>calls.filter(call=>call.action==='generate').length===1&&!document.querySelector('#draft').disabled);assert.equal(await page.locator('#draft').inputValue(),'');assert.doesNotMatch(await page.locator('#toast').textContent(),/No draft is available|No reply was returned/);assert.equal(await count('quick'),0);assert.equal(await count('approve'),0);
  await page.evaluate(async()=>{chats[2].replyWaiting=true;await onMessagesChanged();});for(let i=0;i<3;i++){await back();await open(2);await page.evaluate(()=>onMessagesChanged());}assert.equal(await count('generate'),1,'A waiting conversation never starts UI retries');assert.equal(await page.locator('#reply-decision').count(),0,'The other person has no planning hold');
  await page.evaluate(async()=>{incoming(2,'That was the last part.');chats[2].replyWaiting=true;await onMessagesChanged();chats[2].replyWaiting=false;chats[2].draft={body:'Thanks for sending both messages',engine:'Fixture background reply',alternatives:'[]'};await onMessagesChanged();});await page.waitForFunction(()=>document.querySelector('#draft')?.value==='Thanks for sending both messages');assert.equal(await count('generate'),1);assert.equal(await count('sendNow'),2);assert.equal(await count('approve'),0);
  phase='Try AI labels the planning decision clearly without scheduling anything';
  await back();await page.locator('[data-action=nav][data-page=test]').click();await page.locator('#test-message').fill('Can we meet Friday at seven?');await page.getByRole('button',{name:'Generate test reply',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.test-reply')?.textContent==='Plans need you');assert.equal(await page.locator('#test-error').textContent(),'');assert.equal(await count('testOpenAI'),1);assert.equal(await count('sendNow'),2);assert.equal(await count('approve'),0);assert.equal(await count('generate'),1);assert.deepEqual(errors,[]);
  console.log('PASS: persistent planning hold across newer messages/reopening/profile edits; original text escaped and retained; no automatic generate/send; manual editor and failed-send preservation; native-only hold clearing; hold-only live updates preserve editor/context; quiet-period waiting is silent and never retried by UI;320px/keyboard layouts. Fictional bridge only.');
 }catch(error){console.error('Phase:',phase);throw error;}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
