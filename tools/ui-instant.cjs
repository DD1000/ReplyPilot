const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  // This local bridge fixture never calls an AI provider, Android alarm or SMS carrier.
  await page.addInitScript(()=>{
   const profile={body:'',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300,revision:0};
   window.fixture=JSON.parse(localStorage.getItem('instant-fixture')||'null')||{
    prefs:{defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:1,name:'Test SIM'}],sub:1,inbox:[{thread_id:1,name:'Test friend',address:'+12025550147',body:'Coffee tomorrow?',type:1,date:Date.now()},{thread_id:2,name:'Other contact',address:'+12025550185',body:'Thanks!',type:1,date:Date.now()}],jobs:[],nano:'unchecked',tone:'Natural',delay:300,autoDraft:true,matchMyStyle:true,theme:'forest',lockScreenPreviews:true,cloud:{configured:true}},
    chats:{1:{history:[{_id:1,type:1,body:'Coffee tomorrow?',date:Date.now()}],base:1,draft:{body:'existing draft stays here',engine:'Test fixture',alternatives:'[]'},relationship:{...profile}},2:{history:[{_id:2,type:1,body:'Thanks!',date:Date.now()}],base:2,draft:{body:'you’re welcome',engine:'Test fixture',alternatives:'[]'},relationship:{...profile}}}
   };
   window.calls=[];
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});let result={};
    if(action==='snapshot')result=fixture.prefs;
    if(action==='conversation')result=fixture.chats[p.thread];
    if(action==='saveProfile'){
     const chat=fixture.chats[p.thread];chat.relationship={...p,revision:chat.relationship.revision+1};result=chat.relationship;
     fixture.prefs.jobs.forEach(j=>{if(j.thread===p.thread&&j.auto_send&&j.status==='scheduled')j.status='paused';});
    }
    if(action==='settings'){
     Object.assign(fixture.prefs,p);
     if(p.autoDraft===false)fixture.prefs.jobs.forEach(j=>{if(j.auto_send&&j.status==='scheduled')j.status='paused';});
    }
    if(action==='generate'){
     const chat=fixture.chats[p.thread];chat.draft={body:'sure, what time?',engine:'Test AI stub',alternatives:'[]'};
     if(chat.relationship.autoSend&&fixture.prefs.autoDraft){
      fixture.prefs.jobs=[{_id:91,thread:p.thread,body:chat.draft.body,base:chat.base,address:'+12025550147',auto_send:1,due:Date.now()+chat.relationship.autoDelay*1000,status:chat.relationship.autoDelay===0?'sending':'scheduled'}];
      // Native immediate dispatch inserts an outgoing SMS and advances the conversation base.
      if(chat.relationship.autoDelay===0){chat.history.push({_id:3,type:4,body:chat.draft.body,date:Date.now()});chat.base=3;chat.draft=null;}
     }
    }
    if(action==='testOpenAI')result={body:'test reply only',engine:'Test AI stub',elapsedMs:10};
    localStorage.setItem('instant-fixture',JSON.stringify(fixture));
    const safe=JSON.parse(JSON.stringify(result));setTimeout(()=>nativeResult(id,safe,null),0);
   }};
  });
  const openThread=async id=>{await page.locator(`.row[data-thread="${id}"]`).click();await page.locator('#draft').waitFor();};
  const openProfile=async()=>{await page.locator('#reply-setup').click();await page.locator('#relationship-panel').waitFor();};
  const saveProfile=async()=>{await page.locator('[data-action=save-relationship]').click();await page.waitForFunction(()=>document.querySelector('[data-action=save-relationship]')?.disabled&&document.querySelector('#relationship-status')?.textContent!=='Unsaved changes. Save to apply these reply settings.');};
  const closeProfile=()=>page.getByRole('button',{name:'Close reply setup',exact:true}).click();
  const back=()=>page.getByRole('button',{name:'Back to conversations',exact:true}).click();
  await page.goto('http://127.0.0.1:8769');await openThread(1);await openProfile();
  assert.deepEqual(await page.evaluate(()=>[undefined,null,'0',false,-1,30,604801].map(autoDelay=>profileValue({autoDelay}).autoDelay)),[300,300,300,300,300,300,300],'Only an explicit valid numeric zero may restore instant mode');
  assert(await page.locator('[name=person-mode][value=off]').isChecked());
  await page.locator('[name=person-mode][value=instant]').check();
  assert.equal(await page.locator('#auto-delay').count(),0,'Instant mode must not show a zero-minute timer');
  assert((await page.locator('.auto-send-notice').innerText()).includes('No review or cancellation window.'));
  await saveProfile();
  assert.deepEqual(await page.evaluate(()=>[fixture.chats[1].relationship.cloudEnabled,fixture.chats[1].relationship.autoDraft,fixture.chats[1].relationship.autoSend,fixture.chats[1].relationship.autoDelay]),[true,true,true,0]);
  assert.equal(await page.evaluate(()=>fixture.prefs.jobs.length),0,'Saving must not send an existing draft');
  assert.equal(await page.evaluate(()=>calls.filter(c=>['approve','generate'].includes(c.action)).length),0);
  assert.equal(await page.evaluate(()=>fixture.chats[1].draft.body),'existing draft stays here');
  assert.equal(await page.evaluate(()=>fixture.chats[2].relationship.autoSend),false,'Other contacts stay off');
  await page.locator('#toast.show').waitFor({state:'hidden'});
  await page.locator('.relationship-body').evaluate(el=>el.scrollTop=0);
  await page.screenshot({path:'dist/instant-reply-setup-preview.png'});
  await closeProfile();assert((await page.locator('.composer-foot').innerText()).includes('Autopilot enabled'));
  await back();await openThread(2);await openProfile();assert(await page.locator('[name=person-mode][value=off]').isChecked());await closeProfile();await back();
  await page.reload();await openThread(1);await openProfile();
  assert(await page.locator('[name=person-mode][value=instant]').isChecked(),'Instant selection restores after reload');
  assert.equal(await page.evaluate(()=>fixture.chats[1].relationship.autoDelay),0);
  assert(!/after 0|0 min|timer enabled/i.test(await page.locator('#relationship-status').innerText()));
  await page.locator('[name=person-mode][value=auto-send]').check();
  assert.equal(await page.locator('#auto-delay').inputValue(),'300','Changing back to delayed sending defaults to five minutes');
  await page.locator('#auto-delay').selectOption('custom');await page.locator('#person-custom-delay').fill('0');
  const savesBeforeInvalid=await page.evaluate(()=>calls.filter(c=>c.action==='saveProfile').length);
  await page.locator('[data-action=save-relationship]').click();
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='saveProfile').length),savesBeforeInvalid,'Typing zero into a delayed timer must not enable instant sending');
  assert(await page.locator('[name=person-mode][value=auto-send]').isChecked());
  assert((await page.locator('#toast').innerText()).includes('1 to 10,080'));
  await page.locator('#auto-delay').selectOption('300');
  await saveProfile();assert.equal(await page.evaluate(()=>fixture.chats[1].relationship.autoDelay),300);
  await page.locator('[name=person-mode][value=off]').check();await saveProfile();
  assert.equal(await page.evaluate(()=>fixture.chats[1].relationship.autoSend),false);
  await page.locator('[name=person-mode][value=instant]').check();await saveProfile();await closeProfile();await back();
  await page.getByRole('button',{name:'Settings',exact:true}).click();await page.locator('#auto-draft').uncheck();await page.locator('[data-action=save-settings]').click();
  await page.waitForFunction(()=>fixture.prefs.autoDraft===false);
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Messages',exact:true}).click();await openThread(1);
  assert((await page.locator('.composer-foot').innerText()).includes('Automatic replies paused'));
  await openProfile();assert((await page.locator('#relationship-status').innerText()).includes('Automation is paused in Settings.'));await closeProfile();
  assert.equal(await page.evaluate(()=>calls.filter(c=>['approve','generate'].includes(c.action)).length),0,'Settings and navigation must not send the existing draft');
  await page.getByRole('button',{name:'Redraft',exact:true}).click();await page.waitForFunction(()=>document.querySelector('#draft').value==='sure, what time?');
  assert.equal(await page.evaluate(()=>fixture.prefs.jobs.length),0,'Global automatic-off must keep manual redrafting unsent');
  await back();await page.getByRole('button',{name:'Settings',exact:true}).click();await page.locator('#auto-draft').check();await page.locator('[data-action=save-settings]').click();await page.waitForFunction(()=>fixture.prefs.autoDraft===true);
  await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Messages',exact:true}).click();await openThread(1);await page.getByRole('button',{name:'Redraft',exact:true}).click();
  await page.locator('.scheduled-banner').waitFor();assert.equal(await page.evaluate(()=>fixture.prefs.jobs[0].status),'sending');
  assert((await page.locator('.scheduled-banner').innerText()).includes('Sending reply…'));
  assert.equal(await page.locator('.scheduled-banner [data-action=cancel]').count(),0,'Instant send must not promise a cancellation window');
  assert.equal(await page.locator('.scheduled-banner [data-due]').count(),0);
  assert(await page.locator('#draft').isDisabled());assert(await page.locator('#accept').isDisabled());
  assert.equal(await page.locator('#draft').inputValue(),'','A dispatched reply must clear the composer instead of retaining the old draft');
  assert.equal(await page.locator('.bubble-wrap.out .bubble').last().innerText(),'sure, what time?');
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='approve').length),0,'Instant mode does not open manual approval');
  await page.locator('#toast.show').waitFor({state:'hidden'});
  await page.screenshot({path:'dist/instant-sending-preview.png'});
  await page.evaluate(async()=>{fixture.prefs.jobs[0].status='sent';fixture.chats[1].history.at(-1).type=2;await onNativeResume();});
  assert.equal(await page.locator('#draft').inputValue(),'','Receipt refresh must not restore a stale draft');
  assert.equal(await page.locator('.scheduled-banner').count(),0);
  assert(!(await page.locator('.bubble-wrap.out .bubble-time').last().innerText()).includes('Sending'),'Carrier status changes must refresh the outgoing bubble');
  await back();await page.getByRole('button',{name:'Try AI',exact:true}).click();await page.locator('#test-message').fill('A test message only');await page.locator('[data-action=test-run]').click();await page.locator('.test-reply').waitFor();
  assert.equal(await page.evaluate(()=>fixture.prefs.jobs.length),1,'Test replies never add send jobs');
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='approve').length),0);
  assert.deepEqual(errors,[]);

  // The shipped browser preview persists zero without pretending to call AI or send SMS.
  const demo=await browser.newPage({viewport:{width:412,height:915}});
  await demo.goto('http://127.0.0.1:8769');await demo.locator('#draft').waitFor();await demo.locator('#reply-setup').click();await demo.locator('[name=person-mode][value=instant]').check();await demo.locator('[data-action=save-relationship]').click();
  await demo.waitForFunction(()=>JSON.parse(localStorage.getItem('reply-pilot-demo-relationships')||'{}')[1]?.autoDelay===0);
  await demo.reload();await demo.locator('#draft').waitFor();await demo.locator('#reply-setup').click();assert(await demo.locator('[name=person-mode][value=instant]').isChecked());
  assert.equal(await demo.evaluate(async()=>(await demoApi('snapshot')).jobs.length),0);
  assert.equal(await demo.locator('#draft').inputValue(),'what time were you thinking?');
  console.log('PASS: per-contact instant opt-in, explicit-zero restore, invalid custom-zero rejection, delayed fallback, Off mode, contact isolation, existing-draft preservation, global pause, no implicit approval/generation on save, immediate-send UI, composer clearing after dispatch, outgoing receipt status, Try AI isolation and honest browser persistence. Simulated bridge only.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
