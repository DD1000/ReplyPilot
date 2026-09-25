const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const baseURL=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';

(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  // Explicit, local phone-bridge simulation. No SMS, model request or Android alarm runs.
  await page.addInitScript(()=>{
   window.calls=[];window.failTimer=false;window.jobSequence=0;
   window.prefs={defaultSms:true,permissions:true,notifications:true,exact:true,sims:[{id:1,name:'Test SIM'}],sub:1,inbox:[{thread_id:1,name:'Test friend',address:'+12025550147',body:'Coffee tomorrow?',type:1,date:Date.now()}],jobs:[],nano:'unchecked',tone:'Natural',delay:300,autoDraft:true,matchMyStyle:true,theme:localStorage.getItem('test-theme')||'forest',lockScreenPreviews:true,cloud:{configured:true}};
   window.chat={replyEligibility:{eligible:true,total:20,owner:10,incoming:10},history:[{_id:1,type:1,body:'Coffee tomorrow?',date:Date.now()}],base:1,draft:{body:'what time were you thinking?',engine:'Test AI stub',alternatives:'[]'},relationship:{body:'',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:300,revision:0}};
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});let result={},error=null;
    if(action==='snapshot')result=prefs;
    if(action==='conversation')result=chat;
    if(action==='setTheme'){prefs.theme=p.theme;localStorage.setItem('test-theme',p.theme);result={theme:p.theme};}
    if(action==='settings'){Object.assign(prefs,p);if(p.theme)localStorage.setItem('test-theme',p.theme);if(p.autoDraft===false)prefs.jobs.forEach(j=>{if(j.auto_send&&j.status==='scheduled')j.status='paused';});}
    if(action==='saveProfile'){chat.relationship={...p,revision:chat.relationship.revision+1};prefs.jobs.forEach(j=>{if(j.auto_send&&j.status==='scheduled')j.status='paused';});result=chat.relationship;}
    if(action==='saveDraft'){chat.draft={body:p.body,engine:'Edited by you',alternatives:'[]'};prefs.jobs.forEach(j=>{if(j.auto_send&&j.status==='scheduled')j.status='paused';});}
    if(action==='generate'){
     chat.draft={body:failTimer?'new draft saved despite timer failure':'sure, what time?',engine:'Test AI stub',alternatives:'[]'};
     if(failTimer){error='Your draft is saved. Turn on notifications, then choose a timer.';failTimer=false;}
     else if(chat.relationship.autoSend&&prefs.autoDraft)prefs.jobs=[{_id:++jobSequence,thread:1,address:'+12025550147',body:chat.draft.body,base:1,due:Date.now()+chat.relationship.autoDelay*1000,status:'scheduled',auto_send:1}];
    }
    if(action==='cancel')prefs.jobs.forEach(j=>{if(j._id===p.id)j.status='cancelled';});
    if(action==='approve')result=null; // Models a rejected native confirmation.
    const safe=JSON.parse(JSON.stringify(result));setTimeout(()=>nativeResult(id,safe,error),0);
   }};
  });
  await page.goto(baseURL);await page.locator('.row').waitFor();
  assert.equal(await page.locator('.messages-main > .topbar').count(),0);
  assert.equal(await page.locator('.inbox-header h1').textContent(),'Messages');
  assert.equal(await page.locator('.inbox-column .list-heading').count(),0,'The inbox should have only one Messages heading');
  const header=await page.locator('.inbox-header').boundingBox(),search=await page.locator('.inbox-search-row').boundingBox(),newMessage=await page.getByRole('button',{name:'New text message',exact:true}).boundingBox();
  assert(header.height>=48&&header.height<=64,'Keep the Messages header compact');
  assert(search.y>=header.y+header.height&&search.y-header.y-header.height<=20,'Search belongs directly below the header');
  assert(newMessage.y>=header.y&&newMessage.y+newMessage.height<=header.y+header.height+1,'Compose belongs in the header');
  await page.getByRole('button',{name:'New text message',exact:true}).click();
  await page.getByRole('textbox',{name:'Name or phone number',exact:true}).waitFor({state:'visible'});
  await page.locator('[data-action=close-contacts]').click();
  await page.screenshot({path:'dist/refined-inbox-preview.png',fullPage:true});
  await page.getByRole('button',{name:'Settings',exact:true}).click();
  const ids=await page.locator('[data-action=theme]').evaluateAll(nodes=>nodes.map(n=>n.dataset.themeChoice));
  assert.equal(ids.length,10);assert.equal(new Set(ids).size,10);
  for(const theme of ids){
   await page.locator(`[data-action=theme][data-theme-choice=${theme}]`).click();
   await page.waitForFunction(id=>document.documentElement.dataset.theme===id&&document.querySelector(`[data-action=theme][data-theme-choice=${id}]`).disabled===false,theme);
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   assert.equal(await page.locator(`[data-action=theme][data-theme-choice=${theme}]`).getAttribute('aria-pressed'),'true');
  }
  await page.locator('[data-action=theme][data-theme-choice=midnight]').click();
  await page.waitForFunction(()=>localStorage.getItem('test-theme')==='midnight');
  assert.equal(await page.locator('html').evaluate(el=>getComputedStyle(el).colorScheme),'dark');
  const contrasts=await page.locator('.theme-choice').evaluateAll(nodes=>nodes.map(el=>{
   const lum=s=>{const c=s.match(/[\d.]+/g).slice(0,3).map(Number).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;});return c[0]*.2126+c[1]*.7152+c[2]*.0722;};
   const css=getComputedStyle(el),a=lum(css.color),b=lum(css.backgroundColor);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);
  }));
  assert(contrasts.every(n=>n>=4.5),'Theme names must remain legible in dark mode');
  await page.locator('#toast.show').waitFor({state:'hidden'});
  await page.screenshot({path:'dist/themes-preview.png'});
  assert(await page.evaluate(()=>calls.some(c=>c.action==='setTheme')&&calls.filter(c=>c.action==='setTheme').every(c=>Object.keys(c.p).join(',')==='theme')),'Theme updates use the narrow save and preserve other settings');
  await page.reload();await page.locator('.row').waitFor();
  assert.equal(await page.locator('html').getAttribute('data-theme'),'midnight');
  await page.locator('.row').click();await page.locator('#draft').waitFor();await page.waitForFunction(()=>chatReady());
  assert.equal(await page.locator('#draft').inputValue(),'what time were you thinking?');
  const position=await page.locator('#reply-setup').boundingBox(),sms=await page.locator('.sms-badge').boundingBox();
  assert(position.x>sms.x&&Math.abs(position.y-sms.y)<20,'Reply setup belongs beside SMS');
  await page.locator('#reply-setup').click();
  assert(await page.locator('[name=person-mode][value=off]').isChecked());
  await page.locator('#relationship-context').fill('Old friends; be casual and honest.');
  await page.locator('[name=person-mode][value=auto-send]').check();
  assert.equal(await page.locator('#auto-delay').inputValue(),'300');
  await page.locator('#auto-delay').selectOption('custom');
  await page.locator('#person-custom-delay').fill('10081');
  await page.getByRole('button',{name:'Save profile',exact:true}).click();
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='saveProfile').length),0);
  await page.locator('#person-custom-delay').fill('7');
  await page.keyboard.press('Escape');assert.equal(await page.locator('#relationship-panel').count(),0);
  await page.locator('#reply-setup').click();
  assert.equal(await page.locator('#relationship-context').inputValue(),'Old friends; be casual and honest.');
  assert.equal(await page.locator('#person-custom-delay').inputValue(),'7');
  await page.getByRole('button',{name:'Save profile',exact:true}).click();
  await page.waitForFunction(()=>chat.relationship.autoDelay===420);
  assert.equal(await page.evaluate(()=>chat.relationship.autoSend),true);
  assert.equal(await page.evaluate(()=>prefs.jobs.length),0,'Saving profile must never schedule an existing draft');
  assert.equal(await page.evaluate(()=>calls.filter(c=>['approve','generate'].includes(c.action)).length),0);
  await page.waitForFunction(()=>document.querySelector('#relationship-status').textContent.startsWith('Generated replies send after'));
  await page.locator('#toast.show').waitFor({state:'hidden'});
  await page.locator('.relationship-body').evaluate(el=>el.scrollTop=0);
  await page.screenshot({path:'dist/reply-setup-preview.png',fullPage:true});
  await page.getByRole('button',{name:'Close reply setup',exact:true}).click();
  await page.getByRole('button',{name:'Redraft',exact:true}).click();
  await page.locator('.scheduled-banner').waitFor();
  assert.equal(await page.locator('#draft').inputValue(),'sure, what time?');
  assert(await page.locator('#draft').isEnabled(),'Automatic timer permits editing to cancel');
  assert(await page.locator('#accept').isDisabled(),'No second timer while automatic timer pending');
  assert.equal(await page.evaluate(()=>calls.filter(c=>c.action==='approve').length),0);
  await page.locator('#toast.show').waitFor({state:'hidden'});
  await page.screenshot({path:'dist/automatic-timer-preview.png',fullPage:true});
  await page.locator('#draft').fill('actually can we do friday?');
  await page.waitForFunction(()=>prefs.jobs.every(j=>j.status!=='scheduled'));
  await page.locator('.scheduled-banner').waitFor({state:'detached'});
  assert(await page.locator('#accept').isEnabled());
  await page.evaluate(async()=>{prefs.jobs.push({_id:99,thread:9,status:'sent'});await onNativeResume();});
  assert.equal(await page.locator('#draft').inputValue(),'actually can we do friday?','Refresh must preserve edited wording');
  await page.getByRole('button',{name:'Schedule send',exact:true}).click();
  assert.deepEqual(await page.locator('[data-action=delay]').allTextContents(),['1 min','5 min','30 min','1 hour']);
  await page.locator('#toast.show').waitFor({state:'hidden'});
  await page.screenshot({path:'dist/timer-options-preview.png',fullPage:true});
  await page.locator('[data-action=delay][data-delay="60"]').click();
  await page.waitForFunction(()=>calls.some(c=>c.action==='approve'));
  const approval=await page.evaluate(()=>calls.find(c=>c.action==='approve').p);
  assert.equal(approval.body,'actually can we do friday?');assert.equal(approval.delay,60);
  assert.equal(await page.locator('.scheduled-banner').count(),0,'Rejected manual confirmation must not create timer');
  await page.evaluate(()=>{failTimer=true;});
  await page.getByRole('button',{name:'Redraft',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('#draft').value==='new draft saved despite timer failure');
  assert.equal(await page.locator('.scheduled-banner').count(),0);
  assert((await page.locator('#toast').textContent()).includes('Your draft is saved'));
  // A pending manual send and any send already claimed by the carrier must block editing.
  await page.evaluate(async()=>{prefs.jobs=[{_id:3,thread:1,auto_send:0,status:'scheduled',due:Date.now()+300000}];await onNativeResume();});
  assert(await page.locator('#draft').isDisabled());
  await page.evaluate(async()=>{prefs.jobs[0].auto_send=1;prefs.jobs[0].status='sending';await onNativeResume();});
  assert(await page.locator('#draft').isDisabled());
  await page.evaluate(async()=>{prefs.jobs=[];await onNativeResume();});
  for(const viewport of [{width:320,height:640},{width:412,height:430}]){
   await page.setViewportSize(viewport);await page.locator('#draft').focus();
   await page.waitForFunction(v=>innerWidth===v.width&&innerHeight===v.height,viewport);
   // Let the viewport resize and frame-scheduled composer sizing reach layout.
   await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
   const composer=await page.locator('.composer').boundingBox();assert(composer.y>=0&&composer.y+composer.height<=viewport.height+1,JSON.stringify({viewport,composer}));
  }
  await page.screenshot({path:'dist/keyboard-layout-preview.png',fullPage:true});
  assert.deepEqual(errors,[]);
  console.log('PASS: clean inbox, ten persistent themes, phone/keyboard layout, setup dropdown and saved context, custom bounds, opt-in auto-send contract, immediate timer status, edit cancellation, manual confirmation isolation, saved draft after scheduling failure, refresh and sending-state guards. Simulated bridge only.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
