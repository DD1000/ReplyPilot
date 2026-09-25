const {chromium}=require(process.env.PLAYWRIGHT_PATH||'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  // Fictional cleared-storage phone. No Android Settings, service, contacts or SMS is used.
  await page.addInitScript(()=>{
   window.calls=[];window.roleHeld=false;window.roleRequest=null;window.rolePending=false;
   window.holdSnapshots=false;window.snapshotRequests=[];window.holdStatus=false;window.statusRequests=[];
   window.roleStatusValue=()=>({defaultSms:roleHeld,permissions:roleHeld,pending:rolePending&&!roleHeld,message:roleHeld?'Reply Pilot is your default texting app.':rolePending?'Waiting for Android to confirm your default texting app…':''});
   window.finishRole=(result,error=null)=>{const id=roleRequest;rolePending=false;nativeResult(id,result,error);};
   window.Native={call(id,action,raw){
    const p=JSON.parse(raw);calls.push({action,p});let result={};
    if(action==='snapshot'){
     result={defaultSms:roleHeld,permissions:roleHeld,notifications:true,exact:true,sims:[],sub:-1,inbox:[],jobs:[],nano:'unchecked',tone:'Natural',delay:60,autoDraft:false,matchMyStyle:true,cloud:{configured:false}};
     if(holdSnapshots){snapshotRequests.push({id,result});return;}
    }
    if(action==='role'){roleRequest=id;rolePending=true;return;}
    if(action==='smsRoleStatus'){result=roleStatusValue();if(holdStatus){statusRequests.push({id,result});return;}}
    if(action==='defaultSettings'||action==='appSettings')rolePending=false;
    setTimeout(()=>nativeResult(id,result,null),0);
   }};
  });
  await page.goto(process.env.REPLY_PILOT_URL||'http://127.0.0.1:18769');
  await page.getByRole('button',{name:'Settings',exact:true}).click();
  const role=page.locator('[data-action=role]'),settings=page.getByRole('button',{name:'Open Android default apps'});
  await page.waitForFunction(()=>document.querySelectorAll('.setup-step.done').length===1);
  // Cancellation, denial and launch error release setup immediately.
  await role.click();await page.waitForFunction(()=>roleRequest!==null);
  assert(await role.isDisabled());assert(await settings.isEnabled());
  await page.evaluate(()=>finishRole({defaultSms:false,pending:false,message:'Reply Pilot is not selected yet.'}));
  await page.waitForFunction(()=>!document.querySelector('[data-action=role]').disabled);
  assert((await page.locator('.setup-status').textContent()).includes('not selected'));
  await role.click();await page.evaluate(()=>finishRole(null));
  await page.waitForFunction(()=>!document.querySelector('[data-action=role]').disabled);
  assert.equal(await page.locator('.setup-status').textContent(),'');
  await role.click();await page.evaluate(()=>finishRole(null,'Android could not open the chooser. Use Open Android default apps below.'));
  await page.waitForFunction(()=>document.querySelector('.setup-status').textContent.includes('could not open'));
  // The native timeout reports uncertainty, not a fabricated permission grant/denial.
  await role.click();await page.evaluate(()=>finishRole({defaultSms:false,pending:false,outcome:'timeout',message:'Android has not confirmed a selection. Open Android default apps.'}));
  await page.waitForFunction(()=>!document.querySelector('[data-action=role]').disabled);
  assert.equal(await page.locator('[data-action=role].done').count(),0);
  assert((await page.locator('.setup-status').textContent()).includes('not confirmed'));
  await page.locator('[data-phone-setup]').screenshot({path:'dist/setup-recovery-0.9.10.png'});
  // Keep the full snapshot blocked while the role result succeeds.
  await page.evaluate(()=>{holdSnapshots=true;refresh(true);});
  await page.waitForFunction(()=>snapshotRequests.length===1);
  await role.click();await page.evaluate(()=>{roleHeld=true;finishRole({...roleStatusValue(),pending:false});});
  await page.locator('[data-action=role].done').waitFor();assert(await role.isEnabled());
  assert.equal(await settings.count(),0);
  // The older snapshot cannot undo a fresh Android role/permission answer.
  await page.evaluate(()=>{holdSnapshots=false;const old=snapshotRequests.shift();nativeResult(old.id,old.result,null);});
  await page.waitForFunction(()=>refreshing===null);
  assert.equal(await page.locator('[data-action=role].done').count(),1);
  assert.equal(await page.locator('[data-action=permissions].done').count(),1);
  assert(await page.evaluate(()=>accessHint===true&&!previewDenied),'live role/read/send success restores inbox access without a resume callback');
  // Live access loss updates setup immediately, without waiting for a snapshot.
  await page.evaluate(()=>{roleHeld=false;onMessageAccessChanged({defaultSms:false,readSms:false,contacts:false});});
  assert.equal(await page.locator('[data-action=role].done').count(),0);
  // Manual Settings retires a hung attempt; late responses cannot change a new attempt.
  await role.click();const oldRequest=await page.evaluate(()=>roleRequest);
  await settings.click();assert(await role.isEnabled());
  await role.click();const newRequest=await page.evaluate(()=>roleRequest);assert.notEqual(newRequest,oldRequest);
  await page.evaluate(id=>nativeResult(id,{defaultSms:true,pending:false},null),oldRequest);
  assert(await role.isDisabled());assert.equal(await page.locator('[data-action=role].done').count(),0);
  await page.evaluate(()=>{rolePending=false;});
  await page.getByRole('button',{name:'Check status',exact:true}).click();
  await page.waitForFunction(()=>!document.querySelector('[data-action=role]').disabled);
  // Missing activity callback: resume uses the fast status lane while history is still blocked.
  await role.click();await page.evaluate(()=>{holdSnapshots=true;roleHeld=true;rolePending=false;onNativeResume();});
  await page.locator('[data-action=role].done').waitFor();assert(await role.isEnabled());
  assert.equal(await settings.count(),0);
  await page.waitForFunction(()=>snapshotRequests.length===1);
  // Native access change invalidates stale setup reads too.
  await page.evaluate(()=>{holdStatus=true;checkSmsRoleStatus();});
  await page.waitForFunction(()=>statusRequests.length===1);
  await page.evaluate(()=>{roleHeld=false;onMessageAccessChanged({defaultSms:false,readSms:false,contacts:false});const stale=statusRequests.shift();nativeResult(stale.id,stale.result,null);});
  await page.waitForFunction(()=>!roleChecking);
  assert.equal(await page.locator('[data-action=role].done').count(),0);
  // Role grant via an access event retires the old chooser even without its callback.
  await role.click();const accessRequest=await page.evaluate(()=>roleRequest);
  await page.evaluate(()=>{roleHeld=true;onMessageAccessChanged({defaultSms:true,readSms:true,contacts:false});});
  assert.equal(await page.locator('[data-action=role].done').count(),1);assert(await role.isEnabled());
  await page.evaluate(id=>nativeResult(id,{defaultSms:false,permissions:false,pending:false},null),accessRequest);
  assert.equal(await page.locator('[data-action=role].done').count(),1);
  // A current role does not imply permission to read/send; remove cached private UI on loss.
  await page.evaluate(()=>applySmsRoleStatus({defaultSms:true,permissions:false,pending:false}));
  assert(await page.evaluate(()=>previewDenied&&accessHint===false&&state.inbox.length===0));
  assert.equal(await page.locator('[data-action=permissions].done').count(),0);
  // Phone-setup actions never mutate a draft, enable AI or send a message.
  assert(!(await page.evaluate(()=>calls.some(c=>['approve','generate','sendNow','saveDraft','saveProfile','settings','connectCloud'].includes(c.action)))));
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  assert.deepEqual(errors,[]);
  console.log('PASS: cleared-storage setup, cancellation/error/timeout recovery, Settings fallback, retry/stale callback isolation, fast role gain/loss with blocked history, stale snapshot/status protection, no sends/AI mutations, mobile layout. Simulated bridge only.');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
