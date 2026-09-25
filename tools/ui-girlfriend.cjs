const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
const url=process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769';
(async()=>{const browser=await chromium.launch({channel:'chrome',headless:true});let phase='setup';try{
 const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
 page.on('pageerror',error=>errors.push(error.message));
 // Fictional native bridge only: no phone, contact book, model or carrier access.
 await page.addInitScript(()=>{
  const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>ms===3500?0:interval(fn,ms,...args);
  const clone=value=>JSON.parse(JSON.stringify(value));window.calls=[];window.resumePending=[];window.holdResume=false;window.failResume=false;
  window.fixture={defaultSms:true,permissions:true,contactsAllowed:true,notifications:true,exact:true,sims:[{id:1,name:'Fictional SIM'}],sub:1,autoDraft:false,matchMyStyle:true,tone:'Natural',delay:300,theme:'midnight',cloud:{configured:true},jobs:[],inbox:[{thread_id:1,name:'Test partner',address:'+12025550101',body:'Fictional message',date:41000,type:1},{thread_id:2,name:'Other person',address:'+12025550102',body:'A different conversation',date:42000,type:1}]};
  window.chats=Object.fromEntries(fixture.inbox.map(row=>[row.thread_id,{base:40+row.thread_id,history:[{_id:40+row.thread_id,type:1,body:row.body,date:row.date}],hasMore:false,draft:{body:'Keep this manual draft',engine:'Edited by you',alternatives:'[]'},relationship:{body:'Personal notes',samples:'',cloudEnabled:false,autoDraft:false,autoSend:false,autoDelay:900,autoDelayMode:'range',autoDelayMin:300,autoDelayMax:1800,engagement:'natural',shareLocation:false,relationshipKind:'Friend',tone:'Myself (beta)'},replyEligibility:{eligible:false,total:1,owner:0,incoming:1},girlfriendPause:null}]));
  window.Native={call(id,action,raw){const p=JSON.parse(raw);calls.push({action,p});const done=(value,error=null)=>nativeResult(id,clone(value),error);const reply=()=>{
   if(action==='snapshot')done(fixture);
   else if(action==='conversation')done(chats[p.thread]);
   else if(action==='saveProfile'){chats[p.thread].relationship={...p,revision:(chats[p.thread].relationship.revision||0)+1};done(chats[p.thread].relationship);}
   else if(action==='saveDraft'){chats[p.thread].draft={body:p.body,engine:'Edited by you',alternatives:'[]'};done({});}
   else if(action==='resumeGirlfriend'){if(failResume){done(null,'Could not resume. Try again.');return;}chats[p.thread].girlfriendPause={paused:false,reason:''};done(chats[p.thread]);}
   else done({});
  };if(action==='resumeGirlfriend'&&holdResume)resumePending.push(reply);else setTimeout(reply,0);}};
 });
 const count=action=>page.evaluate(action=>calls.filter(call=>call.action===action).length,action);
 const noMessaging=async()=>assert.deepEqual(await page.evaluate(()=>calls.filter(call=>['generate','quick','approve','sendNow','testOpenAI'].includes(call.action))),[]);
 const open=async thread=>{await page.locator(`.row[data-thread="${thread}"]`).click();await page.locator('#draft').waitFor();};
 const setup=()=>page.locator('[data-action=toggle-profile]').click();
 const closeSetup=()=>page.locator('[data-action=close-setup]').click();
 const back=()=>page.locator('[data-action=back]').click();
 const save=async()=>{await page.locator('[data-action=save-relationship]').click();await page.waitForFunction(()=>document.querySelector('[data-action=save-relationship]')?.disabled);};
 await page.goto(url);await open(1);await setup();
 phase='history gate and viewing never opt a contact in';
 assert.equal(await page.locator('[name=person-mode][value=girlfriend]').count(),0);
 assert.equal(await page.locator('[name=person-engagement][value=girlfriend]').count(),0);
 assert.equal(await count('saveProfile'),0);await noMessaging();
 await closeSetup();await back();await page.evaluate(()=>{chats[1].replyEligibility={eligible:true,total:22,owner:11,incoming:11};});await open(1);await setup();
 assert(await page.locator('[name=person-mode][value=off]').isChecked());assert.equal(await count('saveProfile'),0);
 phase='preset is explicit, saved as one profile and keeps unrelated preferences';
 await page.locator('[name=person-mode][value=girlfriend]').check();
 assert(await page.locator('[name=person-engagement][value=girlfriend]').isChecked());assert(await page.locator('[name=relationship-kind][value=Partner]').isChecked());
 assert.match(await page.locator('.girlfriend-preset-note').textContent(),/real personality and stories/);
 assert.match(await page.locator('.girlfriend-preset-note').textContent(),/Stops at goodnight/);
 assert.match(await page.locator('.girlfriend-preset-note').textContent(),/Plans still wait/);
 assert.equal(await count('saveProfile'),0);assert.equal(await page.locator('#draft').inputValue(),'Keep this manual draft');
 for(const width of [412,320]){await page.setViewportSize({width,height:915});await page.locator('[name=person-mode][value=girlfriend]').scrollIntoViewIfNeeded();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:`dist/girlfriend-preset-${width}.png`});}
 await page.setViewportSize({width:412,height:915});
 await save();const saved=await page.evaluate(()=>calls.find(call=>call.action==='saveProfile').p);
 for(const [key,value] of Object.entries({thread:1,cloudEnabled:true,autoDraft:true,autoSend:true,autoDelay:0,autoDelayMode:'fixed',engagement:'girlfriend',relationshipKind:'Partner',tone:'Myself (beta)',shareLocation:false,body:'Personal notes'}))assert.equal(saved[key],value,key);
 assert.equal(await page.evaluate(()=>fixture.autoDraft),false,'Preset does not enable the global automation switch');
 assert.equal(await page.evaluate(()=>chats[2].relationship.engagement),'natural');await noMessaging();
 phase='switching away clears the preset without inventing new defaults';
 await page.locator('[name=person-mode][value=manual]').check();assert(await page.locator('[name=person-engagement][value=natural]').isChecked());await save();
 let payload=await page.evaluate(()=>calls.filter(call=>call.action==='saveProfile').at(-1).p);assert.equal(payload.autoSend,false);assert.equal(payload.autoDraft,false);assert.equal(payload.engagement,'natural');
 await page.locator('[name=person-engagement][value=girlfriend]').check();assert(await page.locator('[name=person-mode][value=girlfriend]').isChecked());
 await page.locator('[name=person-engagement][value=keep_going]').check();assert(await page.locator('[name=person-mode][value=instant]').isChecked());
 await page.locator('[name=person-mode][value=girlfriend]').check();await save();await closeSetup();await back();
 phase='night pause survives opening, repeated refresh and new incoming texts without automatic drafts';
 await page.evaluate(()=>{fixture.autoDraft=true;chats[1].draft=null;chats[1].girlfriendPause={paused:true,reason:'goodnight'};});await open(1);
 assert.equal(await page.locator('#girlfriend-pause strong').textContent(),'Paused for the night');assert.equal(await page.locator('.schedule-send').textContent(),'Paused for the night');assert(await page.locator('#draft').isEnabled());assert.equal(await page.locator('.local-quick').count(),0);
 for(let i=0;i<2;i++){await back();await open(1);await page.evaluate(()=>onMessagesChanged());}await noMessaging();
 await page.evaluate(async()=>{const chat=chats[1];chat.base++;chat.history.push({_id:chat.base,type:1,date:44000,body:'Another fictional message'});await onMessagesChanged();});assert(await page.locator('#girlfriend-pause').isVisible());await noMessaging();
 phase='pause updates preserve the live editor and manual send remains available';
 await page.locator('#draft').fill('I am still here');await page.locator('#draft').focus();
 await page.evaluate(()=>{window.editor=document.querySelector('#draft');editor.setSelectionRange(4,4);});
 await page.evaluate(async()=>{chats[1].girlfriendPause.reason='night_pause';await onMessagesChanged();});
 assert.equal(await page.evaluate(()=>document.querySelector('#draft')===editor&&document.activeElement===editor&&editor.selectionStart===4),true);assert(await page.locator('#accept').isEnabled());
 await setup();assert.match(await page.locator('#relationship-status').textContent(),/Paused for the night/);await closeSetup();
 for(const viewport of [{width:320,height:640},{width:412,height:470}]){await page.setViewportSize(viewport);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));const composer=await page.locator('.composer').boundingBox(),timeline=await page.locator('.timeline').boundingBox();assert(composer.y>=0&&composer.y+composer.height<=viewport.height+1,JSON.stringify({viewport,composer}));assert(timeline.height>40,JSON.stringify(timeline));await page.locator('#toast.show').waitFor({state:'hidden'});await page.screenshot({path:`dist/girlfriend-pause-${viewport.width}x${viewport.height}.png`});}
 phase='failed resume retains pause, and explicit resume preserves edits made while waiting';
 await page.evaluate(()=>{failResume=true;});await page.locator('[data-action=resume-girlfriend]').click();await page.waitForFunction(()=>document.querySelector('#toast').textContent.includes('Could not resume'));assert(await page.locator('#girlfriend-pause').isVisible());assert(await page.locator('[data-action=resume-girlfriend]').isEnabled());
 await page.evaluate(()=>{failResume=false;holdResume=true;});await page.locator('[data-action=resume-girlfriend]').click();await page.waitForFunction(()=>resumePending.length===1);assert(await page.locator('[data-action=resume-girlfriend]').isDisabled());
 await page.locator('#draft').fill('A newer manual reply');await page.locator('#draft').focus();await page.evaluate(()=>{window.resumingEditor=document.querySelector('#draft');resumingEditor.setSelectionRange(7,7);holdResume=false;resumePending.shift()();});
 await page.waitForFunction(()=>!document.querySelector('#girlfriend-pause'));assert.equal(await page.locator('#draft').inputValue(),'A newer manual reply');assert(await page.evaluate(()=>document.querySelector('#draft')===resumingEditor&&document.activeElement===resumingEditor&&resumingEditor.selectionStart===7));
 assert.deepEqual(await page.evaluate(()=>calls.filter(call=>call.action==='resumeGirlfriend').map(call=>call.p)),[{thread:1},{thread:1}]);await noMessaging();
 phase='a delayed resume cannot replace another conversation';
 await page.evaluate(async()=>{chats[1].girlfriendPause={paused:true,reason:'goodnight'};await onMessagesChanged();holdResume=true;});await page.locator('[data-action=resume-girlfriend]').click();await page.waitForFunction(()=>resumePending.length===1);await back();await open(2);await page.evaluate(()=>{holdResume=false;resumePending.shift()();});
 assert.equal(await page.locator('.chat-person h2').textContent(),'Other person');assert.equal(await page.locator('#draft').inputValue(),'Keep this manual draft');assert.equal(await page.locator('#girlfriend-pause').count(),0);await noMessaging();assert.deepEqual(errors,[]);
 console.log('PASS: explicit eligible Girlfriend Autopilot preset, exact saved configuration and isolation, switching away, persistent night pause, no automatic generation while paused, manual composer and narrow/keyboard layouts, failed/successful explicit resume, in-flight editing and stale conversation guards. Fictional bridge only.');
 }catch(error){throw new Error(`${phase}: ${error.message}`,{cause:error});}finally{await browser.close();}})().catch(error=>{console.error(error);process.exit(1);});
