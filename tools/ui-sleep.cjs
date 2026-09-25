const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const baseURL=process.env.REPLY_PILOT_URL||'http://127.0.0.1:8769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},hasTouch:true,isMobile:true}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  // All operations end in this fictional bridge. No real SMS, AI, alarms or contacts.
  await page.addInitScript(()=>{
   const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
   window.calls=[];window.fixture={defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,contactsAllowed:true,inbox:[{thread_id:1,name:'Fictional friend',address:'+12025550147',body:'Coffee tomorrow?',type:1,date:1000},{thread_id:2,name:'Fictional coworker',address:'+12025550148',body:'Thanks!',type:1,date:999}],jobs:[],cloud:{configured:true},tone:'Natural',delay:900,autoDraft:true,matchMyStyle:true,lockScreenPreviews:true,theme:'midnight',sleep:{mode:'off',until:0,delay:300,revision:0}};
   window.chats={1:{history:[{_id:41,type:1,body:'Coffee tomorrow?',date:1000}],base:41,hasMore:false,draft:{body:'what time were you thinking?',engine:'Fictional draft',alternatives:'[]'},replyEligibility:{eligible:true,total:20,owner:10,incoming:10},relationship:{body:'Old friends',samples:'',cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:600,revision:1}},2:{history:[{_id:51,type:1,body:'Thanks!',date:999}],base:51,hasMore:false,draft:{body:'you got it',engine:'Fictional draft',alternatives:'[]'},replyEligibility:{eligible:true,total:20,owner:10,incoming:10},relationship:{body:'Coworker',samples:'',cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:0,revision:1}}};
   function pauseAuto(){fixture.jobs.forEach(job=>{if(job.auto_send&&job.status==='scheduled')job.status='paused';});}
   window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});let result={},error=null;
    if(action==='snapshot')result=fixture;
    else if(action==='conversation')result=chats[p.thread];
    else if(action==='settings')Object.assign(fixture,p);
    else if(action==='alarms')fixture.exact=true;
    else if(action==='saveProfile'){chats[p.thread].relationship={...p,revision:chats[p.thread].relationship.revision+1};result=chats[p.thread].relationship;}
    else if(action==='saveDraft'){chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};fixture.jobs.forEach(job=>{if(job.thread===p.thread&&job.auto_send&&job.status==='scheduled')job.status='cancelled';});}
    else if(action==='sleep'){
     if(p.action==='start'){const [hours,minutes]=p.time.split(':').map(Number),until=new Date();until.setHours(hours,minutes,0,0);if(until<=Date.now())until.setDate(until.getDate()+1);fixture.sleep={mode:'active',until:until.getTime(),delay:p.delay,revision:fixture.sleep.revision+1};}
     if(p.action==='pause')fixture.sleep={...fixture.sleep,mode:'paused',reason:'manual',revision:fixture.sleep.revision+1};
     if(p.action==='resume')fixture.sleep={...fixture.sleep,mode:'off',revision:fixture.sleep.revision+1};
     pauseAuto();result=fixture.sleep;
    }else if(action==='generate'){chats[p.thread].draft={body:'Fictional fresh reply',engine:'Test',alternatives:'[]'};}
    else if(action==='sendNow'){const chat=chats[p.thread];chat.history.push({_id:++chat.base,type:2,body:p.body,date:2000});chat.draft=null;result={id:chat.base,status:'sent'};}
    else if(action==='approve')result=null;
    setTimeout(()=>nativeResult(id,JSON.parse(JSON.stringify(result)),error),0);
   }};
  });
  const count=action=>page.evaluate(a=>calls.filter(c=>c.action===a).length,action);
  const settings=()=>page.getByRole('button',{name:'Settings',exact:true}).click();
  const options=async id=>assert.deepEqual(await page.locator(id+' option').evaluateAll(nodes=>nodes.map(n=>n.value)),['60','300','1800','3600','custom','range']);
  const open=async thread=>{await page.getByRole('button',{name:'Messages',exact:true}).click();await page.locator(`.row[data-thread="${thread}"]`).click();await page.locator('#draft').waitFor();};
  await page.goto(baseURL);await page.locator('.row').first().waitFor();

  phase='legacy default and person delays remain Custom unchanged';
  await settings();await options('#delay-setting');await options('#sleep-delay');
  assert.equal(await page.locator('#delay-setting').inputValue(),'custom');assert.equal(await page.locator('#default-custom-delay').inputValue(),'15');
  assert.equal(await page.locator('#sleep-time').inputValue(),'00:00');assert.equal(await page.locator('#sleep-delay').inputValue(),'300');
  await page.locator('#tone-setting').selectOption('Warm');await page.locator('[data-action=save-settings]').click();
  await page.waitForFunction(()=>document.querySelector('#toast').textContent==='Preferences saved.');
  await page.locator('#delay-setting').selectOption('300');await page.locator('#delay-setting').selectOption('custom');
  assert.equal(await page.locator('#default-custom-delay').inputValue(),'5','Selecting Custom must retain the selected value');
  await page.locator('#default-custom-delay').fill('0');await page.locator('[data-action=save-settings]').click();assert.equal(await count('settings'),1);
  await page.locator('#default-custom-delay').fill('10');await page.locator('[data-action=save-settings]').click();await page.waitForFunction(()=>fixture.delay===600);
  await open(1);await page.locator('#reply-setup').click();await options('#auto-delay');
  assert.equal(await page.locator('#auto-delay').inputValue(),'custom');assert.equal(await page.locator('#person-custom-delay').inputValue(),'10');
  await page.locator('#relationship-context').fill('Old friends, no early-morning calls');await page.getByRole('button',{name:'Save profile',exact:true}).click();
  await page.waitForFunction(()=>calls.some(c=>c.action==='saveProfile'));
  assert.equal(await page.evaluate(()=>calls.find(c=>c.action==='saveProfile').p.autoDelay),600);
  await page.getByRole('button',{name:'Close reply setup',exact:true}).click();
  await page.getByRole('button',{name:'Schedule send',exact:true}).click();
  assert.deepEqual(await page.locator('[data-action=delay]').allTextContents(),['1 min','5 min','30 min','1 hour']);
  assert.equal(await page.locator('#custom-delay').inputValue(),'10','Legacy default remains Custom for manual scheduling');
  await page.getByRole('button',{name:'Close timer options',exact:true}).click();
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();await settings();

  phase='Sleep requires explicit start and leaves unrelated settings, people and manual timers alone';
  await page.locator('#tone-setting').selectOption('Brief');await page.locator('#default-custom-delay').fill('17');await page.locator('#sleep-delay').selectOption('custom');await page.locator('#sleep-custom-delay').fill('15');await page.locator('#sleep-time').fill('23:59');
  assert.equal(await count('sleep'),0);assert.equal(await count('approve'),0);assert.equal(await count('sendNow'),0);
  await page.evaluate(async()=>{fixture.autoDraft=false;fixture.jobs=[{_id:91,thread:9,status:'scheduled',auto_send:1,due:Date.now()+600000},{_id:92,thread:8,status:'scheduled',auto_send:0,due:Date.now()+600000}];await onNativeResume();});
  assert.equal(await page.locator('#default-custom-delay').inputValue(),'17');assert.equal(await page.locator('#tone-setting').inputValue(),'Brief');
  assert(await page.locator('.sleep-global-off').isVisible());
  await page.evaluate(async()=>{fixture.exact=false;await onNativeResume();});assert(await page.locator('.sleep-permission').isVisible());assert(await page.locator('[data-action=sleep-start]').isDisabled());assert.equal(await count('sleep'),0);
  await page.locator('.sleep-permission [data-action=alarms]').click();await page.waitForFunction(()=>!document.querySelector('[data-action=sleep-start]').disabled);assert.equal(await count('alarms'),1);
  await page.locator('[data-action=sleep-start]').click();await page.waitForFunction(()=>fixture.sleep.mode==='active');
  assert.deepEqual(await page.evaluate(()=>calls.find(c=>c.action==='sleep').p),{action:'start',time:'23:59',delay:900,delayMode:'fixed',delayMin:300,delayMax:1800});
  assert.equal(await page.evaluate(()=>fixture.autoDraft),false);assert.equal(await page.evaluate(()=>chats[1].relationship.autoDelay),600);assert.equal(await page.evaluate(()=>chats[2].relationship.autoDelay),0);
  assert.deepEqual(await page.evaluate(()=>fixture.jobs.map(j=>j.status)),['paused','scheduled']);
  assert.equal(await page.locator('#default-custom-delay').inputValue(),'17');assert.equal(await page.locator('#tone-setting').inputValue(),'Brief');
  await page.locator('#sleep-time').fill('06:30');await page.locator('#sleep-custom-delay').fill('10');await page.locator('#sleep-time').focus();
  await page.evaluate(()=>{window.timeElement=document.querySelector('#sleep-time');fixture.sleep={...fixture.sleep,mode:'paused',reason:'cutoff',revision:fixture.sleep.revision+1};});
  await page.evaluate(()=>onNativeResume());
  assert((await page.locator('#sleep-setting-status').textContent()).includes('paused'));assert.equal(await page.locator('#sleep-time').inputValue(),'06:30');assert.equal(await page.locator('#sleep-custom-delay').inputValue(),'10');
  assert.equal(await page.evaluate(()=>document.activeElement.id),'sleep-time');assert.equal(await page.evaluate(()=>timeElement===document.querySelector('#sleep-time')),true,'Status polling preserves the time input node');
  await page.locator('[data-action=sleep-resume]').click();await page.waitForFunction(()=>fixture.sleep.mode==='off');
  assert.equal(await page.evaluate(()=>fixture.autoDraft),false);assert.equal(await count('generate'),0);
  assert.equal(await page.locator('#default-custom-delay').inputValue(),'17');
  await page.locator('[data-action=sleep-start]').click();await page.waitForFunction(()=>fixture.sleep.mode==='active');
  assert.deepEqual(await page.evaluate(()=>calls.filter(c=>c.action==='sleep').at(-1).p),{action:'start',time:'06:30',delay:600,delayMode:'fixed',delayMin:300,delayMax:1800});
  await page.locator('[data-action=sleep-pause]').click();await page.waitForFunction(()=>fixture.sleep.mode==='paused');
  assert.equal(await page.evaluate(()=>fixture.jobs.find(j=>j._id===92).status),'scheduled');

  phase='expired session pauses status while preserving focused composer and send remains manual';
  await page.evaluate(()=>{fixture.autoDraft=true;fixture.sleep={mode:'active',until:Date.now()+60000,delay:300,revision:8};fixture.jobs=[];});
  await open(2);await page.evaluate(()=>onNativeResume());
  assert.equal(await page.locator('.schedule-send').textContent(),'Sleep timer · 5 min');
  await page.locator('#reply-setup').click();assert(await page.locator('[name=person-mode][value=instant]').isChecked());assert((await page.locator('[data-sleep-profile]').textContent()).includes('Sleep currently uses a 5 min timer'));await page.getByRole('button',{name:'Close reply setup',exact:true}).click();
  await page.locator('#draft').fill('my own unfinished message');await page.locator('#draft').focus();
  await page.evaluate(()=>{window.originalDraft=document.querySelector('#draft');originalDraft.setSelectionRange(5,5);fixture.sleep={...fixture.sleep,mode:'active',until:Date.now()-1};});
  await page.evaluate(()=>onNativeResume());
  assert.equal(await page.evaluate(()=>originalDraft===document.querySelector('#draft')),true);assert.equal(await page.evaluate(()=>document.activeElement===originalDraft),true);assert.equal(await page.locator('#draft').inputValue(),'my own unfinished message');
  assert.equal(await page.evaluate(()=>originalDraft.selectionStart),5);assert.equal(await page.locator('.schedule-send').textContent(),'Automatic replies paused');
  assert((await page.locator('.chat-card [data-sleep-status]').textContent()).includes('paused'));
  await page.setViewportSize({width:412,height:470});await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
  const composer=await page.locator('.composer').boundingBox(),timeline=await page.locator('.timeline').boundingBox();
  assert(composer.y>=0&&composer.y+composer.height<=471);assert(timeline.height>60);
  await page.screenshot({path:'dist/sleep-paused-keyboard-preview.png'});
  await page.setViewportSize({width:320,height:640});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.getByRole('button',{name:'Send message',exact:true}).click();await page.waitForFunction(()=>calls.some(c=>c.action==='sendNow'));assert.equal(await count('approve'),0);
  assert.equal(await page.evaluate(()=>calls.find(c=>c.action==='sendNow').p.body),'my own unfinished message');
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  await page.evaluate(()=>{chats[2].draft=null;});await page.locator('.row[data-thread="2"]').click();
  await page.locator('#draft').waitFor();assert.equal(await count('generate'),0,'Opening a chat during paused Sleep cannot auto-generate');

  phase='Sleep delays instant-person pending jobs and preserves their actual delay after cutoff';
  await page.evaluate(async()=>{fixture.sleep={mode:'active',until:Date.now()+60000,delay:300,revision:10};chats[2].draft={body:'already generated',engine:'Fixture',alternatives:'[]'};fixture.jobs=[{_id:93,thread:2,base:chats[2].base,status:'scheduled',body:'already generated',auto_send:1,auto_delay:300,due:Date.now()+300000}];await onMessagesChanged();});
  await page.locator('.scheduled-banner').waitFor();assert(await page.locator('#draft').isEnabled());assert(await page.locator('[data-action=cancel]').isVisible());
  assert(!(await page.locator('.scheduled-banner').textContent()).includes('Sending reply'));
  await page.evaluate(async()=>{fixture.sleep={...fixture.sleep,mode:'paused',reason:'cutoff'};await onMessagesChanged();});
  assert(await page.locator('#draft').isEnabled());assert(await page.locator('[data-action=cancel]').isVisible());
  assert(!(await page.locator('.scheduled-banner').textContent()).includes('Sending reply'));
  await page.locator('#draft').fill('manual override');await page.waitForFunction(()=>fixture.jobs[0].status==='cancelled');
  await page.waitForFunction(()=>!document.querySelector('.scheduled-banner'));
  assert.equal(await count('sendNow'),1);assert.equal(await count('approve'),0);
  await page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  await page.locator('.inbox-column [data-action=sleep-settings]').click();await page.locator('#sleep-time').waitFor();
  assert(await page.locator('#sleep-settings').isVisible());assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.setViewportSize({width:412,height:915});await page.locator('#sleep-settings').scrollIntoViewIfNeeded();await page.screenshot({path:'dist/sleep-settings-preview.png'});
  phase='active Sleep layouts at 412 and 320 pixels';
  await page.evaluate(async()=>{const until=new Date();until.setHours(6,30,0,0);if(until<=Date.now())until.setDate(until.getDate()+1);fixture.sleep={mode:'active',until:until.getTime(),delay:300,revision:11};await onNativeResume();});
  await page.locator('#sleep-delay').selectOption('300');await page.locator('#sleep-time').fill('06:30');
  await page.locator('#toast.show').waitFor({state:'hidden'});
  for(const width of [412,320]){
   await page.setViewportSize({width,height:915});await page.locator('#sleep-settings').scrollIntoViewIfNeeded();await page.locator('#sleep-time').blur();
   await page.screenshot({path:`dist/sleep-active-settings-${width}.png`});
   await page.getByRole('button',{name:'Messages',exact:true}).click();
   if(await page.getByRole('button',{name:'Back to conversations',exact:true}).count())await page.getByRole('button',{name:'Back to conversations',exact:true}).click();
   assert((await page.locator('.inbox-column [data-sleep-status]').textContent()).includes('Sleep schedule'));
   await page.screenshot({path:`dist/sleep-active-inbox-${width}.png`});
   await page.locator('.row[data-thread="2"]').click();await page.locator('#draft').waitFor();assert.equal(await page.locator('.schedule-send').textContent(),'Sleep timer · 5 min');
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:`dist/sleep-active-chat-${width}.png`});
   await page.getByRole('button',{name:'Back to conversations',exact:true}).click();await settings();
  }
  assert.deepEqual(errors,[]);
  console.log('PASS: four exact presets plus Custom, legacy 10/15-minute retention, explicit one-off Sleep controls, no contact/global opt-in, manual timer isolation, buffered preferences, pause/cutoff status, preserved composer and keyboard layout, delayed instant-profile jobs, manual send, and no automatic dispatch during pause. Fictional native bridge only.');
 }catch(error){console.error('Phase:',phase);throw error;}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1);});
