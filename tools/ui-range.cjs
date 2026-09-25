const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const baseURL=process.env.REPLY_PILOT_URL||'http://127.0.0.1:8769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true,isMobile:true}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  // Fake phone bridge only. No actual SMS, contact, notification, alarm or AI service.
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   window.calls=[];window.acceptNext=false;window.chosenDelays=[];window.fixture={defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,inbox:[{thread_id:1,name:'Test friend',address:'+12025550147',body:'Coffee tomorrow?',type:1,date:1000},{thread_id:2,name:'New contact',address:'+12025550148',body:'Hello',type:1,date:900}],jobs:[],cloud:{configured:true},tone:'Natural',delay:900,autoDraft:true,matchMyStyle:true,lockScreenPreviews:true,theme:'midnight',sleep:{mode:'off',until:0,delay:300,revision:0}};
   window.chats={1:{history:[{_id:41,type:1,body:'Coffee tomorrow?',date:1000}],base:41,hasMore:false,draft:{body:'what time were you thinking?',engine:'Fictional draft',alternatives:'[]'},replyEligibility:{eligible:true,total:20,owner:10,incoming:10},relationship:{body:'Old friends',samples:'',cloudEnabled:true,autoDraft:false,autoSend:false,autoDelay:600,revision:1}},2:{history:[{_id:51,type:1,body:'Hello',date:900}],base:51,hasMore:false,draft:{body:'hi!',engine:'Fictional draft',alternatives:'[]'},replyEligibility:{eligible:false,total:1,owner:0,incoming:1},relationship:{body:'',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300,revision:1}}};
   Object.assign(fixture,JSON.parse(sessionStorage.getItem('range-prefs')||'{}'));
   const clone=value=>JSON.parse(JSON.stringify(value));
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={};
    if(action==='snapshot')result=fixture;
    else if(action==='conversation')result=chats[p.thread];
    else if(action==='settings'){Object.assign(fixture,p);sessionStorage.setItem('range-prefs',JSON.stringify({delay:fixture.delay,delayMode:fixture.delayMode,delayMin:fixture.delayMin,delayMax:fixture.delayMax}));}
    else if(action==='saveProfile'){chats[p.thread].relationship={...p,revision:chats[p.thread].relationship.revision+1};result=chats[p.thread].relationship;}
    else if(action==='saveDraft'){chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};fixture.jobs.forEach(job=>{if(job.thread===p.thread&&job.auto_send&&job.status==='scheduled')job.status='cancelled';});}
    else if(action==='approve'){
     const chosen=p.delayMode==='range'?Math.min(p.delayMax,p.delayMin+37):p.delay;chosenDelays.push(chosen);
     if(acceptNext){acceptNext=false;const job={_id:91,thread:p.thread,base:p.base,body:p.body,address:p.address,status:'scheduled',auto_send:0,due:Date.now()+chosen*1000};fixture.jobs=[job];result={id:job._id};}else result=null;
    }else if(action==='cancel')fixture.jobs.forEach(job=>{if(job._id===p.id)job.status='cancelled';});
    else if(action==='sleep'){
     if(p.action==='start')fixture.sleep={...p,mode:'active',until:Date.now()+600000,revision:fixture.sleep.revision+1};
     else fixture.sleep={...fixture.sleep,mode:p.action==='pause'?'paused':'off',revision:fixture.sleep.revision+1};
     fixture.jobs.forEach(job=>{if(job.auto_send&&job.status==='scheduled')job.status='paused';});result=fixture.sleep;
    }else if(action==='sendNow'){chats[p.thread].history.push({_id:++chats[p.thread].base,type:2,body:p.body,date:2000});chats[p.thread].draft=null;result={id:chats[p.thread].base,status:'sent'};}
    setTimeout(()=>nativeResult(id,clone(result),null),0);
   }};
  });
  const count=action=>page.evaluate(a=>calls.filter(c=>c.action===a).length,action);
  const last=action=>page.evaluate(a=>calls.filter(c=>c.action===a).at(-1)?.p,action);
  const settings=()=>page.locator('[data-action=nav][data-page=settings]').click();
  const savePrefs=async()=>{await page.evaluate(()=>document.querySelector('#toast').textContent='');await page.locator('[data-action=save-settings]').click();await page.waitForFunction(()=>document.querySelector('#toast').textContent==='Preferences saved.');};
  const saveProfile=async()=>{await page.locator('[data-action=save-relationship]').click();await page.waitForFunction(()=>!document.querySelector('[data-action=save-relationship]')||document.querySelector('[data-action=save-relationship]').disabled);};
  const open=async thread=>{if(await page.locator('[data-action=back]').isVisible())await page.locator('[data-action=back]').click();await page.locator('[data-action=nav][data-page=inbox]').click();if(await page.locator('[data-action=back]').isVisible())await page.locator('[data-action=back]').click();await page.locator(`.row[data-thread="${thread}"]`).click();await page.locator('#draft').waitFor();};
  const selectRange=async(prefix,selector)=>{await page.locator(selector).selectOption('range');await page.locator(`#${prefix}-range-min`).waitFor();};
  const values=async(prefix,from,to)=>{await page.locator(`#${prefix}-range-min`).fill(String(from));await page.locator(`#${prefix}-range-max`).fill(String(to));};
  await page.goto(baseURL);await page.locator('.row').first().waitFor();await settings();

  phase='range defaults, inline validation and saved default prefills';
  assert.equal(await page.locator('#delay-setting').inputValue(),'custom');assert.equal(await page.locator('#default-custom-delay').inputValue(),'15');
  await selectRange('default','#delay-setting');assert.equal(await page.locator('#default-range-min').inputValue(),'5');assert.equal(await page.locator('#default-range-max').inputValue(),'30');
  for(const [from,to] of [['31','30'],['0','30'],['1.5','30'],['','30'],['5','10081']]){
   await values('default',from,to);assert((await page.locator('#default-range-error').textContent()).length>0);await page.locator('[data-action=save-settings]').click();assert.equal(await count('settings'),0);
  }
  await values('default',10080,10080);assert.equal(await page.locator('#default-range-error').textContent(),'');await savePrefs();
  assert.equal((await last('settings')).delayMin,604800);assert.equal((await last('settings')).delayMax,604800);
  await values('default',5,30);await savePrefs();assert.equal((await last('settings')).delayMode,'range');
  await page.locator('#delay-setting').selectOption('custom');assert.equal(await page.locator('#default-custom-delay').inputValue(),'15');await savePrefs();assert.equal((await last('settings')).delayMode,'fixed');assert.equal((await last('settings')).delay,900);
  await page.locator('#default-custom-delay').fill('');await selectRange('default','#delay-setting');await values('default',5,30);await savePrefs();assert.equal((await last('settings')).delay,300,'An inactive invalid Custom value cannot block a valid range');
  await selectRange('default','#delay-setting');await values('default',9,8);await page.locator('#delay-setting').selectOption('300');await savePrefs();assert.equal((await last('settings')).delayMode,'fixed');assert.equal((await last('settings')).delayMin,300);assert.equal((await last('settings')).delayMax,1800);
  await selectRange('default','#delay-setting');await values('default',5,30);await savePrefs();
  assert.equal(await page.evaluate(()=>chats[1].relationship.autoSend),false,'Selecting a default range is not per-contact opt-in');assert.equal(await count('generate'),0);assert.equal(await count('approve'),0);

  await page.reload();await page.locator('.row').first().waitFor();await settings();assert.equal(await page.locator('#delay-setting').inputValue(),'range');assert.equal(await page.locator('#default-range-min').inputValue(),'5');assert.equal(await page.locator('#default-range-max').inputValue(),'30');

  phase='per-person range saves explicitly, Autopilot clears mode, eligibility stays enforced';
  await open(1);await page.locator('#reply-setup').click();await page.locator('[name=person-mode][value=auto-send]').check();await selectRange('person','#auto-delay');
  assert.equal(await page.locator('#person-range-min').inputValue(),'5');assert.equal(await page.locator('#person-range-max').inputValue(),'30');
  await values('person',31,30);assert((await page.locator('#person-range-error').textContent()).includes('From'));await page.locator('[data-action=save-relationship]').click();assert.equal(await count('saveProfile'),0);
  await page.locator('[name=person-mode][value=instant]').check();await saveProfile();await page.waitForFunction(()=>calls.some(c=>c.action==='saveProfile'));
  let saved=await last('saveProfile');assert.equal(saved.autoDelay,0);assert.equal(saved.autoDelayMode,'fixed');assert.equal(saved.autoDelayMin,300);assert.equal(saved.autoDelayMax,1800);
  await page.waitForFunction(()=>!document.querySelector('[name=person-mode][value=auto-send]').disabled);
  await page.locator('[name=person-mode][value=auto-send]').check();await selectRange('person','#auto-delay');await values('person',5,30);await page.locator('#relationship-context').fill('Old friends, keep it casual');
  await page.locator('#person-range-max').focus();await page.evaluate(()=>{window.rangeInput=document.querySelector('#person-range-max');});await page.evaluate(()=>onMessagesChanged());
  assert.equal(await page.evaluate(()=>document.querySelector('#person-range-max')===rangeInput),true);assert.equal(await page.locator('#relationship-context').inputValue(),'Old friends, keep it casual');
  await saveProfile();await page.waitForFunction(()=>chats[1].relationship.autoDelayMode==='range');saved=await last('saveProfile');assert.equal(saved.thread,1);assert.equal(saved.autoDelayMin,300);assert.equal(saved.autoDelayMax,1800);
  await page.waitForFunction(()=>document.querySelector('#relationship-status').textContent.startsWith('Generated replies send after'));
  assert((await page.locator('#relationship-status').textContent()).includes('5–30 min'));await page.locator('#auto-delay').scrollIntoViewIfNeeded();await page.locator('#toast.show').waitFor({state:'hidden'});await page.screenshot({path:'dist/range-profile-preview.png'});
  await page.setViewportSize({width:412,height:470});await page.locator('#person-range-min').focus();await page.locator('#person-range-min').scrollIntoViewIfNeeded();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:'dist/range-profile-keyboard-preview.png'});await page.setViewportSize({width:412,height:915});
  await page.locator('[data-action=close-setup]').click();await open(2);await page.locator('#reply-setup').click();assert.equal(await page.locator('[name=person-mode][value=auto-send]').count(),0);assert.equal(await page.locator('#auto-delay').count(),0);assert.equal(await page.evaluate(()=>chats[2].relationship.cloudEnabled),false);await page.locator('[data-action=close-setup]').click();

  phase='manual range is native-selected once, confirmation separate, timer stable';
  await open(1);await page.locator('[data-action=toggle-timer]').click();assert(await page.locator('[data-action=range]').isVisible());assert.equal(await page.locator('#manual-range-min').inputValue(),'5');assert.equal(await page.locator('#manual-range-max').inputValue(),'30');
  await values('manual',7,11);await page.evaluate(()=>onMessagesChanged());assert.equal(await page.locator('#manual-range-min').inputValue(),'7');assert.equal(await page.locator('#manual-range-max').inputValue(),'11');
  await page.locator('[data-action=custom]').click();await page.locator('[data-action=range]').click();assert.equal(await page.locator('#manual-range-min').inputValue(),'7');
  await values('manual',10,9);await page.locator('[data-action=approve]').click();assert.equal(await count('approve'),0);assert((await page.locator('#manual-range-error').textContent()).includes('From'));
  await values('manual',5,30);await page.locator('[data-action=approve]').click();await page.waitForFunction(()=>calls.some(c=>c.action==='approve'));await page.locator('#timer-menu').waitFor({state:'detached'});
  const approval=await last('approve');assert.equal(approval.delayMode,'range');assert.equal(approval.delayMin,300);assert.equal(approval.delayMax,1800);assert.equal(approval.body,'what time were you thinking?');assert.equal(await page.evaluate(()=>fixture.jobs.length),0);assert.equal(await page.locator('#draft').inputValue(),'what time were you thinking?');
  await page.locator('[data-action=toggle-timer]').click();await page.evaluate(()=>{acceptNext=true;});await page.locator('[data-action=approve]').click();await page.locator('.scheduled-banner').waitFor();
  const scheduled=await page.evaluate(()=>({due:fixture.jobs[0].due,chosen:chosenDelays.at(-1)}));assert.equal(scheduled.chosen,337);assert.equal(await count('approve'),2);assert(await page.locator('#draft').isDisabled());
  for(let i=0;i<3;i++)await page.evaluate(()=>onMessagesChanged());assert.equal(await page.evaluate(()=>fixture.jobs[0].due),scheduled.due);assert.equal(await count('approve'),2);assert.equal(await page.evaluate(()=>chosenDelays.length),2);
  await page.locator('[data-action=back]').click();await settings();await selectRange('sleep','#sleep-delay');await values('sleep',7,15);await page.locator('#sleep-time').fill('23:59');
  await page.locator('#default-range-min').fill('6');await page.locator('#sleep-range-max').focus();await page.evaluate(()=>{window.sleepInput=document.querySelector('#sleep-range-max');});await page.evaluate(()=>onNativeResume());assert.equal(await page.evaluate(()=>sleepInput===document.querySelector('#sleep-range-max')),true);
  await page.locator('[data-action=sleep-start]').click();await page.waitForFunction(()=>fixture.sleep.mode==='active');assert.equal((await last('sleep')).delayMode,'range');assert.equal((await last('sleep')).delayMin,420);assert.equal((await last('sleep')).delayMax,900);
  assert.equal(await page.locator('#default-range-min').inputValue(),'6');assert.equal(await page.evaluate(()=>fixture.jobs[0].due),scheduled.due);assert.equal(await page.evaluate(()=>fixture.jobs[0].status),'scheduled');await page.waitForFunction(()=>document.querySelector('#sleep-setting-status').textContent.includes('7–15 min'));
  assert((await page.locator('.sleep-help').textContent()).includes('at or after the cutoff stay unsent'));await page.locator('#sleep-settings').scrollIntoViewIfNeeded();await page.locator('#toast.show').waitFor({state:'hidden'});await page.screenshot({path:'dist/range-sleep-preview.png'});

  phase='range timers remain usable at narrow and keyboard-sized viewports';
  await open(1);await page.locator('[data-action=cancel]').click();await page.locator('.scheduled-banner').waitFor({state:'detached'});await page.locator('[data-action=toggle-timer]').click();
  await page.locator('#toast.show').waitFor({state:'hidden'});
  for(const viewport of [{width:412,height:915},{width:320,height:640},{width:412,height:470}]){
   await page.setViewportSize(viewport);await page.locator('#manual-range-min').focus();await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));const timer=await page.locator('#timer-menu').boundingBox();assert(timer.y>=0&&timer.y+timer.height<=viewport.height+1,JSON.stringify(timer));
   await page.locator('[data-action=approve]').scrollIntoViewIfNeeded();const button=await page.locator('[data-action=approve]').boundingBox();assert(button.y>=0&&button.y+button.height<=viewport.height+1);
   await page.screenshot({path:`dist/range-manual-${viewport.width}x${viewport.height}.png`});
  }
  await page.locator('[data-action=close-timer]').click();assert.equal(await count('approve'),2);assert.equal(await count('sendNow'),0);
  await page.locator('#draft').fill('typed by me');await page.getByRole('button',{name:'Send message',exact:true}).click();await page.waitForFunction(()=>calls.some(c=>c.action==='sendNow'));assert.equal(await count('approve'),2);assert.equal((await last('sendNow')).body,'typed by me');
  assert.equal(await count('generate'),0);assert.deepEqual(errors,[]);
  console.log('PASS: default5–30-minute ranges in four locations; invalid/reversed bounds blocked inline; equal/max accepted; fixed/Autopilot mode reset; legacy custom retention; readiness/no opt-in; raw edit preservation; native-only selection and separate confirmation; stable pending due time; Sleep cutoff/manual timer isolation; direct send unchanged; 320px and keyboard viewport. Fictional native bridge only.');
 }catch(error){console.error('Phase:',phase);throw error;}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
